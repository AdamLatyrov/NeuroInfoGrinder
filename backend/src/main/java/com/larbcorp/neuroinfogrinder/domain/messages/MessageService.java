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

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int INITIAL_SYNC_BATCH_SIZE = 20;
    private static final int INCREMENTAL_SYNC_BATCH_SIZE = 100;

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final TelegramTdlibService telegramTdlibService;

    public MessageService(MessageRepository messageRepository,
                          GroupRepository groupRepository,
                          TelegramTdlibService telegramTdlibService) {
        this.messageRepository = messageRepository;
        this.groupRepository = groupRepository;
        this.telegramTdlibService = telegramTdlibService;
    }

    @Transactional
    public PageResponse<MessageResponse> getMessages(Long groupId, String status, Long topicId, Pageable pageable) {
        Page<MessageEntity> page;

        if (topicId != null) {
            if (status != null && !status.isBlank()) {
                page = messageRepository.findByGroupIdAndTopicIdAndProcessingStatus(groupId, topicId, status, pageable);
            } else {
                page = messageRepository.findByGroupIdAndTopicId(groupId, topicId, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            page = messageRepository.findByGroupIdAndProcessingStatus(groupId, status, pageable);
        } else {
            page = messageRepository.findByGroupId(groupId, pageable);
        }

        repairVisibleMetadata(groupId, page.getContent());
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public MessageChainResponse getMessageChain(Long groupId, Long messageId) {
        MessageEntity root = messageRepository.findById(messageId)
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

        repairVisibleMetadata(groupId, chainEntities);
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
        MessageEntity message = messageRepository.findByIdAndGroupId(messageId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        repairVisibleMetadata(groupId, List.of(message));
        return toResponse(message);
    }

    @Transactional
    public int syncMessagesFromTelegram(Long groupId) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        List<TelegramMessageDto> messages;
        try {
            if (group.getLastReadMessageId() == null || group.getLastReadMessageId() == 0L) {
                messages = telegramTdlibService.getMessages(group.getTelegramChatId(), 0, INITIAL_SYNC_BATCH_SIZE);
            } else {
                messages = telegramTdlibService.getMessages(group.getTelegramChatId(), 0, INCREMENTAL_SYNC_BATCH_SIZE);
            }
        } catch (Exception e) {
            log.warn("Primary sync for group {} failed, falling back to latest message: {}", groupId, e.getMessage());
            messages = telegramTdlibService.getLatestMessage(group.getTelegramChatId())
                    .map(List::of)
                    .orElseGet(List::of);
        }

        log.info("Sync for group {}: TDLib returned {} messages", groupId, messages.size());

        Instant monthAgo = Instant.now().minusSeconds(30 * 24 * 3600);
        Map<Long, MessageEntity> existingByTelegramMessageId = loadExistingMessages(groupId, messages);
        List<MessageEntity> newEntities = new ArrayList<>();
        List<MessageEntity> repairedEntities = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int repaired = 0;
        int tooOld = 0;

        for (TelegramMessageDto msg : messages) {
            Instant msgDate = Instant.ofEpochSecond(msg.date());
            if (msgDate.isBefore(monthAgo)) {
                tooOld++;
                continue;
            }
            group.setLastReadMessageId(Math.max(group.getLastReadMessageId() == null ? 0L : group.getLastReadMessageId(), msg.id()));
            if (group.getLastReadAt() == null || msgDate.isAfter(group.getLastReadAt())) {
                group.setLastReadAt(msgDate);
            }
            MessageEntity existing = existingByTelegramMessageId.get(msg.id());
            if (existing != null) {
                if (repairMessageMetadata(existing, msg, msgDate)) {
                    repairedEntities.add(existing);
                    repaired++;
                } else {
                    skipped++;
                }
                continue;
            }
            MessageEntity entity = new MessageEntity();
            entity.setTelegramMessageId(msg.id());
            entity.setGroupId(groupId);
            entity.setText(msg.text());
            entity.setSenderName(msg.senderName());
            entity.setSenderTelegramUserId(msg.senderTelegramUserId());
            entity.setIsBot(msg.isBot());
            entity.setReplyToMessageId(msg.replyToMessageId() > 0 ? msg.replyToMessageId() : null);
            entity.setTopicName(msg.topicName());
            entity.setTopicId(msg.messageThreadId() > 0 ? msg.messageThreadId() : null);
            entity.setReplyCount(0);
            entity.setProcessingStatus("UNPROCESSED");
            entity.setMessageDate(msgDate);
            newEntities.add(entity);
            created++;
        }

        if (!repairedEntities.isEmpty()) {
            messageRepository.saveAll(repairedEntities);
        }
        if (!newEntities.isEmpty()) {
            messageRepository.saveAll(newEntities);
        }
        if (created > 0 || repaired > 0 || !messages.isEmpty()) {
            groupRepository.save(group);
        }

        log.info("Message sync for group {}: {} new, {} repaired, {} skipped (existing), {} too old, from {} TDLib messages",
                groupId, created, repaired, skipped, tooOld, messages.size());
        return created + repaired;
    }

    @Transactional
    public void enqueueForProcessing(Long groupId, Long messageId, EnqueueRequest request) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (!Boolean.TRUE.equals(group.getEnabled())) {
            throw new IllegalStateException("Group is disabled: " + groupId);
        }

        MessageEntity message = messageRepository.findById(messageId)
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
                message.getTopicName(),
                message.getTopicId(),
                message.getMessageDate(),
                message.getSignalScore(),
                message.getClassifierScore(),
                message.getClassifierReason(),
                message.getSignalBreakdown(),
                message.getRuleResultJson()
        );
    }

    private boolean repairMessageMetadata(MessageEntity entity, TelegramMessageDto msg, Instant msgDate) {
        boolean changed = false;

        if (isPlaceholderSenderName(entity.getSenderName()) && !isPlaceholderSenderName(msg.senderName())) {
            entity.setSenderName(msg.senderName());
            changed = true;
        }
        if (entity.getSenderTelegramUserId() == null && msg.senderTelegramUserId() != null) {
            entity.setSenderTelegramUserId(msg.senderTelegramUserId());
            changed = true;
        }
        if ((entity.getTopicName() == null || entity.getTopicName().isBlank()) && msg.topicName() != null && !msg.topicName().isBlank()) {
            entity.setTopicName(msg.topicName());
            changed = true;
        }
        if (entity.getTopicId() == null && msg.messageThreadId() > 0) {
            entity.setTopicId(msg.messageThreadId());
            changed = true;
        }
        if (entity.getReplyToMessageId() == null && msg.replyToMessageId() > 0) {
            entity.setReplyToMessageId(msg.replyToMessageId());
            changed = true;
        }
        if (shouldRefreshText(entity.getText(), msg.text())) {
            entity.setText(msg.text());
            changed = true;
        }
        if (entity.getMessageDate() == null) {
            entity.setMessageDate(msgDate);
            changed = true;
        }
        if (!Boolean.TRUE.equals(entity.getIsBot()) && msg.isBot()) {
            entity.setIsBot(true);
            changed = true;
        }

        return changed;
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
        mergeTopicNames(topicNames, telegramTdlibService.getCachedTopics(group.getTelegramChatId(), 100));

        boolean stillMissing = messages.stream()
                .map(MessageEntity::getTopicId)
                .filter(Objects::nonNull)
                .anyMatch(topicId -> !topicNames.containsKey(topicId));

        if (stillMissing) {
            try {
                mergeTopicNames(topicNames, telegramTdlibService.getTopics(group.getTelegramChatId(), 100));
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

    private Map<Long, MessageEntity> loadExistingMessages(Long groupId, List<TelegramMessageDto> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }

        List<Long> telegramMessageIds = messages.stream()
                .map(TelegramMessageDto::id)
                .distinct()
                .toList();

        Map<Long, MessageEntity> existingByTelegramMessageId = new HashMap<>();
        for (MessageEntity entity : messageRepository.findByGroupIdAndTelegramMessageIdIn(groupId, telegramMessageIds)) {
            existingByTelegramMessageId.put(entity.getTelegramMessageId(), entity);
        }
        return existingByTelegramMessageId;
    }
}
