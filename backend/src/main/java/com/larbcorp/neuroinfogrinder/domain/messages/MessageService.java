package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.messages.dto.EnqueueRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageChainResponse;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int INITIAL_SYNC_BATCH_SIZE = 20;
    private static final int INCREMENTAL_SYNC_BATCH_SIZE = 100;
    private static final AtomicLong TDLIB_TIMEOUT_COUNT = new AtomicLong(0);

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final TelegramTdlibService telegramTdlibService;
    private final MessageSyncPersistenceService messageSyncPersistenceService;

    public MessageService(MessageRepository messageRepository,
                          GroupRepository groupRepository,
                          TelegramTdlibService telegramTdlibService,
                          MessageSyncPersistenceService messageSyncPersistenceService) {
        this.messageRepository = messageRepository;
        this.groupRepository = groupRepository;
        this.telegramTdlibService = telegramTdlibService;
        this.messageSyncPersistenceService = messageSyncPersistenceService;
    }

    @Transactional
    public PageResponse<MessageResponse> getMessages(Long groupId, String status, Long topicId, Pageable pageable) {
        return getMessages(null, groupId, status, topicId, pageable);
    }

    @Transactional
    public PageResponse<MessageResponse> getMessages(Long ownerUserId, Long groupId, String status, Long topicId, Pageable pageable) {
        requireGroupForOwner(ownerUserId, groupId);
        Page<MessageEntity> page;

        if (topicId != null) {
            if (status != null && !status.isBlank()) {
                page = ownerUserId != null
                    ? messageRepository.findByOwnerUserIdAndGroupIdAndTopicIdAndProcessingStatus(ownerUserId, groupId, topicId, status, pageable)
                    : messageRepository.findByGroupIdAndTopicIdAndProcessingStatus(groupId, topicId, status, pageable);
            } else {
                page = ownerUserId != null
                    ? messageRepository.findByOwnerUserIdAndGroupIdAndTopicId(ownerUserId, groupId, topicId, pageable)
                    : messageRepository.findByGroupIdAndTopicId(groupId, topicId, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            page = ownerUserId != null
                ? messageRepository.findByOwnerUserIdAndGroupIdAndProcessingStatus(ownerUserId, groupId, status, pageable)
                : messageRepository.findByGroupIdAndProcessingStatus(groupId, status, pageable);
        } else {
            page = ownerUserId != null
                ? messageRepository.findByOwnerUserIdAndGroupId(ownerUserId, groupId, pageable)
                : messageRepository.findByGroupId(groupId, pageable);
        }

        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public MessageChainResponse getMessageChain(Long groupId, Long messageId) {
        return getMessageChain(null, groupId, messageId);
    }

    @Transactional
    public MessageChainResponse getMessageChain(Long ownerUserId, Long groupId, Long messageId) {
        requireGroupForOwner(ownerUserId, groupId);
        MessageEntity root = (ownerUserId != null
                ? messageRepository.findByIdAndGroupIdAndOwnerUserId(messageId, groupId, ownerUserId)
                : messageRepository.findByIdAndGroupId(messageId, groupId))
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        List<MessageEntity> chainEntities = new ArrayList<>();

        // If root is a reply, find the parent and prepend it
        if (root.getReplyToMessageId() != null) {
            messageRepository.findByGroupIdAndTelegramMessageId(groupId, root.getReplyToMessageId())
                    .ifPresent(chainEntities::add);
        }

        chainEntities.add(root);

        // Collect replies: find messages that reply to root's telegramMessageId
        List<Long> replyTargets = List.of(root.getTelegramMessageId());
        while (!replyTargets.isEmpty()) {
            List<MessageEntity> replies = messageRepository.findByGroupIdAndReplyToMessageIdIn(groupId, replyTargets);
            if (replies.isEmpty()) break;

            // Avoid infinite loops: filter out messages already in chain
            var existingIds = chainEntities.stream()
                    .map(MessageEntity::getTelegramMessageId)
                    .collect(java.util.stream.Collectors.toSet());
            List<MessageEntity> newReplies = replies.stream()
                    .filter(r -> !existingIds.contains(r.getTelegramMessageId()))
                    .toList();

            if (newReplies.isEmpty()) break;

            chainEntities.addAll(newReplies);

            // Next iteration: find replies to these new replies
            replyTargets = newReplies.stream().map(MessageEntity::getTelegramMessageId).toList();
        }

        List<MessageResponse> chain = chainEntities.stream()
                .map(this::toResponse)
                .toList();

        String chainType = root.getReplyToMessageId() != null ? "reply_branch" : "thread_root";

        return new MessageChainResponse(
                root.getId(),
                groupId,
                root.getTelegramMessageId(),
                chain,
                chainType
        );
    }

    @Transactional
    public MessageResponse getMessage(Long groupId, Long messageId) {
        return getMessage(null, groupId, messageId);
    }

    @Transactional
    public MessageResponse getMessage(Long ownerUserId, Long groupId, Long messageId) {
        requireGroupForOwner(ownerUserId, groupId);
        MessageEntity message = (ownerUserId != null
                ? messageRepository.findByIdAndGroupIdAndOwnerUserId(messageId, groupId, ownerUserId)
                : messageRepository.findByIdAndGroupId(messageId, groupId))
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        return toResponse(message);
    }

    public MessageSyncResult syncMessagesFromTelegram(Long groupId) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        MessageEntity latestBefore = messageRepository.findFirstByGroupIdOrderByMessageDateDesc(groupId).orElse(null);

        long fetchStartedAt = System.currentTimeMillis();
        List<TelegramMessageDto> messages;
        boolean historyTimeout = false;
        boolean fallbackUsed = false;
        String reason = "completed";
        try {
            if (group.getLastReadMessageId() == null || group.getLastReadMessageId() == 0L) {
                messages = group.getAccountId() != null
                    ? telegramTdlibService.getMessages(group.getAccountId(), group.getTelegramChatId(), 0, INITIAL_SYNC_BATCH_SIZE)
                    : telegramTdlibService.getMessages(group.getTelegramChatId(), 0, INITIAL_SYNC_BATCH_SIZE);
            } else {
                messages = group.getAccountId() != null
                    ? telegramTdlibService.getMessages(group.getAccountId(), group.getTelegramChatId(), 0, INCREMENTAL_SYNC_BATCH_SIZE)
                    : telegramTdlibService.getMessages(group.getTelegramChatId(), 0, INCREMENTAL_SYNC_BATCH_SIZE);
            }
        } catch (Exception e) {
            if (isTdlibTimeout(e)) {
                historyTimeout = true;
                long timeoutCount = TDLIB_TIMEOUT_COUNT.incrementAndGet();
                log.warn("TDLib timeout during sync for group {} (timeoutCount={}): {}", groupId, timeoutCount, e.getMessage());
            }
            log.warn("Primary sync for group {} failed, falling back to latest message: {}", groupId, e.getMessage());
            fallbackUsed = true;
            reason = historyTimeout ? "history_timeout" : "history_error";
            try {
                messages = (group.getAccountId() != null
                        ? telegramTdlibService.getLatestMessage(group.getAccountId(), group.getTelegramChatId())
                        : telegramTdlibService.getLatestMessage(group.getTelegramChatId()))
                        .map(List::of)
                        .orElseGet(List::of);
            } catch (Exception fallbackException) {
                log.warn("Latest-message fallback failed for group {}: {}", groupId, fallbackException.getMessage());
                messages = List.of();
            }
        }

        TelegramMessageDto newestTdlibMessage = newestMessage(messages);
        log.info(
                "Sync for group {}: TDLib returned {} messages in {} ms (historyTimeout={}, fallbackUsed={}, newestTdlibMessageId={}, newestTdlibDate={}, newestTdlibTopicId={})",
                groupId,
                messages.size(),
                System.currentTimeMillis() - fetchStartedAt,
                historyTimeout,
                fallbackUsed,
                newestTdlibMessage == null ? null : newestTdlibMessage.id(),
                newestTdlibMessage == null ? null : Instant.ofEpochSecond(newestTdlibMessage.date()),
                newestTdlibMessage == null ? null : newestTdlibMessage.messageThreadId()
        );
        MessageSyncPersistenceResult persistenceResult =
                messageSyncPersistenceService.persistSyncedMessagesDetailed(groupId, messages);
        MessageEntity latestAfter = messageRepository.findFirstByGroupIdOrderByMessageDateDesc(groupId).orElse(null);
        MessageSyncResult result = new MessageSyncResult(
                groupId,
                group.getTelegramChatId(),
                group.getTitle(),
                messages.size(),
                persistenceResult.changed(),
                persistenceResult.created(),
                persistenceResult.repaired(),
                persistenceResult.skipped(),
                persistenceResult.tooOld(),
                historyTimeout,
                fallbackUsed,
                reason,
                latestBefore == null ? null : latestBefore.getTelegramMessageId(),
                latestBefore == null ? null : latestBefore.getMessageDate(),
                latestBefore == null ? null : latestBefore.getTopicId(),
                newestTdlibMessage == null ? null : newestTdlibMessage.id(),
                newestTdlibMessage == null ? null : Instant.ofEpochSecond(newestTdlibMessage.date()),
                newestTdlibMessage == null ? null : newestTdlibMessage.messageThreadId(),
                latestAfter == null ? null : latestAfter.getTelegramMessageId(),
                latestAfter == null ? null : latestAfter.getMessageDate(),
                latestAfter == null ? null : latestAfter.getTopicId()
        );
        log.info(
                "Sync summary groupId={} telegramChatId={} title=\"{}\" latestDbBefore={}/{}/topic{} tdlibReturned={} changed={} created={} repaired={} skipped={} tooOld={} latestDbAfter={}/{}/topic{} reason={} historyTimeout={} fallbackUsed={}",
                result.groupId(),
                result.telegramChatId(),
                result.title(),
                result.latestDbMessageIdBefore(),
                result.latestDbMessageDateBefore(),
                result.latestDbTopicIdBefore(),
                result.tdlibReturned(),
                result.changed(),
                result.created(),
                result.repaired(),
                result.skipped(),
                result.tooOld(),
                result.latestDbMessageIdAfter(),
                result.latestDbMessageDateAfter(),
                result.latestDbTopicIdAfter(),
                result.reason(),
                result.historyTimeout(),
                result.fallbackUsed()
        );
        return result;
    }

    @Transactional
    public void enqueueForProcessing(Long groupId, Long messageId, EnqueueRequest request) {
        enqueueForProcessing(null, groupId, messageId, request);
    }

    @Transactional
    public void enqueueForProcessing(Long ownerUserId, Long groupId, Long messageId, EnqueueRequest request) {
        GroupEntity group = requireGroupForOwner(ownerUserId, groupId);
        if (!Boolean.TRUE.equals(group.getEnabled())) {
            throw new IllegalStateException("Group is disabled: " + groupId);
        }

        MessageEntity message = (ownerUserId != null
                ? messageRepository.findByIdAndGroupIdAndOwnerUserId(messageId, groupId, ownerUserId)
                : messageRepository.findByIdAndGroupId(messageId, groupId))
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if ("QUEUED".equals(message.getProcessingStatus()) && !request.force()) {
            return;
        }

        message.setProcessingStatus("QUEUED");
        messageRepository.save(message);
    }

    private MessageResponse toResponse(MessageEntity message) {
        String senderName = message.getSenderName();
        if (senderName == null || senderName.isBlank()) {
            senderName = "Unknown";
        }
        GroupEntity group = groupRepository.findById(message.getGroupId()).orElse(null);
        String topicName = TelegramTopicNames.visibleTopicName(group, message.getTopicId(), message.getTopicName());
        return new MessageResponse(
                message.getId(),
                message.getTelegramMessageId(),
                message.getGroupId(),
                senderName,
                message.getSenderTelegramUserId(),
                message.getIsBot(),
                message.getText(),
                message.getReplyToMessageId(),
                message.getReplyCount(),
                message.getProcessingStatus(),
                message.getGuideId(),
                topicName,
                message.getTopicId(),
                TelegramMessageLinkBuilder.build(group, message),
                message.getMessageDate(),
                message.getSignalScore(),
                message.getGuidePotentialScore(),
                message.getProblemSignalScore(),
                message.getPainScore(),
                message.getUrgencyScore(),
                message.getWillingnessToPayScore(),
                message.getTechnicalDepthScore(),
                message.getSpamScore(),
                message.getMeaningSummary(),
                message.getProblemStatement(),
                message.getSolutionHint(),
                message.getMentionedToolsJson(),
                message.getMentionedPricesJson(),
                message.getMentionedErrorsJson(),
                message.getIntelligenceReason(),
                message.getClusterCandidate(),
                message.getEmbeddingStatus(),
                message.getMessageIntelligenceJson(),
                message.getClassifierScore(),
                message.getClassifierReason(),
                message.getClassifierResultJson(),
                message.getClassificationContextHash(),
                message.getSignalBreakdown(),
                message.getRuleResultJson()
        );
    }

    private TelegramMessageDto newestMessage(List<TelegramMessageDto> messages) {
        return messages.stream()
                .max((left, right) -> Long.compare(left.date(), right.date()))
                .orElse(null);
    }

    private GroupEntity requireGroupForOwner(Long ownerUserId, Long groupId) {
        return ownerUserId == null
            ? groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId))
            : groupRepository.findByIdAndOwnerUserId(groupId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    private boolean isPlaceholderSenderName(String senderName) {
        return senderName == null || senderName.isBlank() || senderName.equals("Unknown") || senderName.startsWith("User ");
    }

    private boolean shouldRefreshText(String currentText, String refreshedText) {
        if (refreshedText == null || refreshedText.isBlank()) {
            return false;
        }
        if (currentText == null || currentText.isBlank()) {
            return true;
        }
        if (currentText.equals(refreshedText)) {
            return false;
        }

        boolean refreshedHasLink = refreshedText.contains("](")
                || refreshedText.contains("http://")
                || refreshedText.contains("https://");
        boolean currentHasLink = currentText.contains("](")
                || currentText.contains("http://")
                || currentText.contains("https://");

        return refreshedHasLink && !currentHasLink;
    }

    private void repairVisibleMetadata(Long groupId, List<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        GroupEntity group = groupRepository.findById(groupId).orElse(null);
        Map<Long, String> topicNamesById = loadTopicNames(group, messages);
        Map<Long, String> senderNamesByUserId = loadSenderNames(messages);
        List<MessageEntity> changed = new ArrayList<>();

        for (MessageEntity message : messages) {
            boolean updated = false;

            if (message.getSenderTelegramUserId() != null && isPlaceholderSenderName(message.getSenderName())) {
                String resolvedName = senderNamesByUserId.get(message.getSenderTelegramUserId());
                if (!isPlaceholderSenderName(resolvedName)) {
                    message.setSenderName(resolvedName);
                    updated = true;
                }
            }

            if ((message.getTopicName() == null || message.getTopicName().isBlank())
                    && message.getTopicId() != null) {
                String topicName = topicNamesById.get(message.getTopicId());
                if (topicName != null && !topicName.isBlank()) {
                    message.setTopicName(topicName);
                    updated = true;
                }
            }

            if (updated) {
                changed.add(message);
            }
        }

        if (!changed.isEmpty()) {
            messageRepository.saveAll(changed);
        }
    }

    private Map<Long, String> loadSenderNames(List<MessageEntity> messages) {
        List<Long> senderIds = messages.stream()
                .filter(message -> message.getSenderTelegramUserId() != null)
                .filter(message -> isPlaceholderSenderName(message.getSenderName()))
                .map(MessageEntity::getSenderTelegramUserId)
                .distinct()
                .toList();

        if (senderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> senderNamesByUserId = new HashMap<>();
        for (Long senderId : senderIds) {
            try {
                String resolvedName = telegramTdlibService.resolveUserDisplayName(senderId);
                if (!isPlaceholderSenderName(resolvedName)) {
                    senderNamesByUserId.put(senderId, resolvedName);
                }
            } catch (RuntimeException ignored) {
                // Keep the stored placeholder and try again on a later refresh.
            }
        }

        return senderNamesByUserId;
    }

    private Map<Long, String> loadTopicNames(GroupEntity group, List<MessageEntity> messages) {
        if (group == null || !Boolean.TRUE.equals(group.getForum())) {
            return Map.of();
        }

        boolean hasMissingTopics = messages.stream()
                .anyMatch(message -> message.getTopicId() != null
                        && (message.getTopicName() == null || message.getTopicName().isBlank()));
        if (!hasMissingTopics) {
            return Map.of();
        }

        Map<Long, String> topicNames = new HashMap<>();
        mergeTopicNames(topicNames, group.getAccountId() != null
            ? telegramTdlibService.getCachedTopics(group.getAccountId(), group.getTelegramChatId(), 100)
            : telegramTdlibService.getCachedTopics(group.getTelegramChatId(), 100));

        boolean stillMissing = messages.stream()
                .map(MessageEntity::getTopicId)
                .filter(Objects::nonNull)
                .anyMatch(topicId -> !topicNames.containsKey(topicId));

        if (stillMissing) {
            try {
                mergeTopicNames(topicNames, group.getAccountId() != null
                    ? telegramTdlibService.getTopics(group.getAccountId(), group.getTelegramChatId(), 100)
                    : telegramTdlibService.getTopics(group.getTelegramChatId(), 100));
            } catch (RuntimeException ignored) {
                // Use whatever was already cached.
            }
        }

        return topicNames;
    }

    private void mergeTopicNames(Map<Long, String> topicNames, List<com.larbcorp.neuroinfogrinder.telegram.model.TelegramTopicDto> topics) {
        for (com.larbcorp.neuroinfogrinder.telegram.model.TelegramTopicDto topic : topics) {
            if (topic == null || topic.name() == null || topic.name().isBlank()) {
                continue;
            }
            topicNames.putIfAbsent(topic.forumTopicId(), topic.name());
            topicNames.putIfAbsent(topic.messageThreadId(), topic.name());
        }
    }

    private boolean isTdlibTimeout(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }
}
