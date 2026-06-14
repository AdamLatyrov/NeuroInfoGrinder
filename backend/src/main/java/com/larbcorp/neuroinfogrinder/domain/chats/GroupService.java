package com.larbcorp.neuroinfogrinder.domain.chats;

import com.larbcorp.neuroinfogrinder.domain.chats.dto.BulkAssignRequest;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.BulkToggleRequest;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.CreateGroupRequest;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.GroupResponse;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.UpdateGroupRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramSyncTaskExecutor;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static final int INITIAL_SYNC_BATCH_SIZE = 20;
    private static final int INCREMENTAL_SYNC_BATCH_SIZE = 100;

    private final GroupRepository groupRepository;
    private final TelegramAccountRepository accountRepository;
    private final TelegramTdlibService telegramTdlibService;
    private final MessageRepository messageRepository;
    private final GuideRepository guideRepository;
    private final TelegramSyncTaskExecutor telegramSyncTaskExecutor;
    private final Set<Long> duplicateWarningChatIds = ConcurrentHashMap.newKeySet();

    public GroupService(GroupRepository groupRepository,
                        TelegramAccountRepository accountRepository,
                        TelegramTdlibService telegramTdlibService,
                        MessageRepository messageRepository,
                        GuideRepository guideRepository,
                        TelegramSyncTaskExecutor telegramSyncTaskExecutor) {
        this.groupRepository = groupRepository;
        this.accountRepository = accountRepository;
        this.telegramTdlibService = telegramTdlibService;
        this.messageRepository = messageRepository;
        this.guideRepository = guideRepository;
        this.telegramSyncTaskExecutor = telegramSyncTaskExecutor;
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupResponse> getGroups(String search, String status, Boolean enabled, Long accountId, Pageable pageable) {
        Specification<GroupEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(root.get("telegramChatId").as(String.class), pattern),
                        cb.like(cb.lower(root.get("username")), pattern)
                ));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<GroupEntity> page = groupRepository.findAll(spec, pageable);
        List<GroupEntity> dedupedGroups = deduplicateGroups(page.getContent());
        return new PageResponse<>(
                dedupedGroups.stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                dedupedGroups.size(),
                dedupedGroups.isEmpty() ? 0 : 1
        );
    }

    @Transactional
    public void syncGroups() {
        List<TelegramChatDto> telegramChats = deduplicateTelegramChats(telegramTdlibService.getChats(100));
        if (telegramChats.isEmpty()) {
            log.warn("No Telegram chats returned from TDLib");
            return;
        }

        Map<Long, GroupEntity> existingByChatId = groupRepository
                .findByTelegramChatIdIn(telegramChats.stream().map(TelegramChatDto::id).toList())
                .stream()
                .collect(Collectors.toMap(
                        GroupEntity::getTelegramChatId,
                        Function.identity(),
                        this::preferCanonicalGroup,
                        LinkedHashMap::new
                ));

        int created = 0;
        int updated = 0;
        int messagesCreated = 0;

        for (TelegramChatDto chat : telegramChats) {
            GroupEntity entity = existingByChatId.get(chat.id());
            if (entity == null) {
                entity = new GroupEntity();
                entity.setTelegramChatId(chat.id());
                entity.setTitle(chat.title());
                entity.setUsername(chat.username());
                entity.setForum(chat.forum());
                entity.setEnabled(true);
                entity.setLastReadMessageId(0L);
                groupRepository.save(entity);
                created++;
            } else {
                boolean changed = false;
                if (!chat.title().equals(entity.getTitle())) {
                    entity.setTitle(chat.title());
                    changed = true;
                }
                if (chat.username() != null ? !chat.username().equals(entity.getUsername()) : entity.getUsername() != null) {
                    entity.setUsername(chat.username());
                    changed = true;
                }
                if (entity.getForum() != chat.forum()) {
                    entity.setForum(chat.forum());
                    changed = true;
                }
                if (changed) {
                    updated++;
                }
            }

            messagesCreated += syncRecentMessages(entity, chat.id());
        }

        log.info("Group sync completed: {} chats from Telegram, {} created, {} updated, {} messages created",
                telegramChats.size(), created, updated, messagesCreated);
    }

    @Transactional
    public GroupResponse updateGroup(Long id, UpdateGroupRequest request) {
        long startedAt = System.currentTimeMillis();
        GroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
        boolean wasEnabled = Boolean.TRUE.equals(group.getEnabled());

        if (request.enabled() != null) {
            group.setEnabled(request.enabled());
        }
        if (request.category() != null) {
            group.setCategory(request.category());
        }
        if (request.accountId() != null) {
            TelegramAccountEntity account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.accountId()));
            group.setAccountId(account.getId());
        }
        if (request.forum() != null) {
            group.setForum(request.forum());
        }

        group = groupRepository.save(group);
        boolean syncRequested = false;
        if (!wasEnabled && Boolean.TRUE.equals(group.getEnabled())) {
            schedulePostCommitWarmup(group.getId(), group.getTelegramChatId());
            syncRequested = true;
        }
        log.info(
                "Group enable/update completed: groupId={} telegramChatId={} enabled={} syncRequested={} durationMs={}",
                group.getId(),
                group.getTelegramChatId(),
                group.getEnabled(),
                syncRequested,
                System.currentTimeMillis() - startedAt
        );
        return toResponse(group);
    }

    @Transactional
    public void bulkToggle(BulkToggleRequest request) {
        long startedAt = System.currentTimeMillis();
        List<GroupEntity> groups = groupRepository.findAllById(request.groupIds());
        List<GroupEntity> groupsToWarmUp = new ArrayList<>();
        for (GroupEntity group : groups) {
            boolean wasEnabled = Boolean.TRUE.equals(group.getEnabled());
            group.setEnabled(request.enabled());
            if (!wasEnabled && request.enabled()) {
                groupsToWarmUp.add(group);
            }
        }
        groupRepository.saveAll(groups);
        groupsToWarmUp.forEach(group -> schedulePostCommitWarmup(group.getId(), group.getTelegramChatId()));
        log.info(
                "Bulk group toggle completed: groups={} enabled={} syncRequested={} durationMs={}",
                groups.size(),
                request.enabled(),
                groupsToWarmUp.size(),
                System.currentTimeMillis() - startedAt
        );
    }

    private int syncRecentMessages(GroupEntity group, Long telegramChatId) {
        try {
            List<TelegramMessageDto> messages;
            if (group.getLastReadMessageId() == null || group.getLastReadMessageId() == 0L) {
                messages = telegramTdlibService.getMessages(telegramChatId, 0, INITIAL_SYNC_BATCH_SIZE);
            } else {
                messages = telegramTdlibService.getMessages(telegramChatId, 0, INCREMENTAL_SYNC_BATCH_SIZE);
            }
            Instant monthAgo = Instant.now().minusSeconds(30L * 24 * 3600);
            Map<Long, MessageEntity> existingByTelegramMessageId = loadExistingMessages(group.getId(), messages);
            List<MessageEntity> newEntities = new ArrayList<>();
            List<MessageEntity> repairedEntities = new ArrayList<>();
            int created = 0;
            long latestMessageId = group.getLastReadMessageId() == null ? 0L : group.getLastReadMessageId();
            Instant latestReadAt = group.getLastReadAt();

            for (TelegramMessageDto msg : messages) {
                Instant msgDate = Instant.ofEpochSecond(msg.date());
                if (msgDate.isBefore(monthAgo)) {
                    continue;
                }

                latestMessageId = Math.max(latestMessageId, msg.id());
                if (latestReadAt == null || msgDate.isAfter(latestReadAt)) {
                    latestReadAt = msgDate;
                }

                MessageEntity existing = existingByTelegramMessageId.get(msg.id());
                if (existing != null) {
                    if (repairMessageMetadata(existing, msg, msgDate)) {
                        repairedEntities.add(existing);
                    }
                    continue;
                }

                MessageEntity msgEntity = new MessageEntity();
                msgEntity.setTelegramMessageId(msg.id());
                msgEntity.setGroupId(group.getId());
                msgEntity.setText(msg.text());
                msgEntity.setSenderName(msg.senderName());
                msgEntity.setSenderTelegramUserId(msg.senderTelegramUserId());
                msgEntity.setIsBot(msg.isBot());
                msgEntity.setReplyToMessageId(msg.replyToMessageId() > 0 ? msg.replyToMessageId() : null);
                msgEntity.setTopicName(msg.topicName());
                msgEntity.setTopicId(msg.messageThreadId() > 0 ? msg.messageThreadId() : null);
                msgEntity.setReplyCount(0);
                msgEntity.setProcessingStatus("UNPROCESSED");
                msgEntity.setMessageDate(msgDate);
                newEntities.add(msgEntity);
                created++;
            }

            if (!repairedEntities.isEmpty()) {
                messageRepository.saveAll(repairedEntities);
            }
            if (!newEntities.isEmpty()) {
                messageRepository.saveAll(newEntities);
            }
            if (latestMessageId > 0) {
                group.setLastReadMessageId(latestMessageId);
                group.setLastReadAt(latestReadAt != null ? latestReadAt : Instant.now());
                groupRepository.save(group);
            }

            return created;
        } catch (Exception e) {
            log.warn("Could not fetch messages for chat {}: {}", telegramChatId, e.getMessage());
            return telegramTdlibService.getLatestMessage(telegramChatId)
                    .map(message -> storeSingleMessage(group, message))
                    .orElse(0);
        }
    }

    private int storeSingleMessage(GroupEntity group, TelegramMessageDto msg) {
        Instant monthAgo = Instant.now().minusSeconds(30L * 24 * 3600);
        Instant msgDate = Instant.ofEpochSecond(msg.date());
        if (msgDate.isBefore(monthAgo)) {
            return 0;
        }
        if (messageRepository.existsByGroupIdAndTelegramMessageId(group.getId(), msg.id())) {
            return 0;
        }

        MessageEntity entity = new MessageEntity();
        entity.setTelegramMessageId(msg.id());
        entity.setGroupId(group.getId());
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
        messageRepository.save(entity);

        group.setLastReadMessageId(Math.max(group.getLastReadMessageId() == null ? 0L : group.getLastReadMessageId(), msg.id()));
        group.setLastReadAt(msgDate);
        groupRepository.save(group);
        return 1;
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

    @Transactional
    public void bulkAssign(BulkAssignRequest request) {
        TelegramAccountEntity account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.accountId()));
        List<GroupEntity> groups = groupRepository.findAllById(request.groupIds());
        for (GroupEntity group : groups) {
            group.setAccountId(account.getId());
        }
        groupRepository.saveAll(groups);
    }

    private GroupResponse toResponse(GroupEntity group) {
        Instant dayAgo = Instant.now().minusSeconds(24 * 60 * 60L);
        long messagesPerDay = messageRepository.countByGroupIdAndMessageDateAfter(group.getId(), dayAgo);
        long guidesFound = guideRepository.countByGroupId(group.getId());

        return new GroupResponse(
                group.getId(),
                group.getTelegramChatId(),
                group.getTitle(),
                group.getUsername(),
                resolveSourceType(group),
                group.getCategory(),
                group.getForum(),
                group.getEnabled(),
                group.getAccountId(),
                messagesPerDay,
                guidesFound,
                group.getLastReadAt(),
                group.getLastReadMessageId()
        );
    }

    private String resolveSourceType(GroupEntity group) {
        String title = group.getTitle() == null ? "" : group.getTitle();
        if (title.startsWith("Telegram chat ") || title.startsWith("User ")) {
            return "DIRECT_CHAT";
        }
        if (group.getTelegramChatId() != null && group.getTelegramChatId() > 0) {
            return "DIRECT_CHAT";
        }
        return "GROUP";
    }

    private List<TelegramChatDto> deduplicateTelegramChats(List<TelegramChatDto> telegramChats) {
        LinkedHashMap<Long, TelegramChatDto> uniqueByChatId = new LinkedHashMap<>();
        for (TelegramChatDto chat : telegramChats) {
            uniqueByChatId.putIfAbsent(chat.id(), chat);
        }
        return new ArrayList<>(uniqueByChatId.values());
    }

    private List<GroupEntity> deduplicateGroups(List<GroupEntity> groups) {
        LinkedHashMap<Long, GroupEntity> uniqueByChatId = new LinkedHashMap<>();
        int duplicatesHidden = 0;
        for (GroupEntity group : groups) {
            GroupEntity existing = uniqueByChatId.get(group.getTelegramChatId());
            if (existing == null) {
                uniqueByChatId.put(group.getTelegramChatId(), group);
                continue;
            }
            duplicatesHidden++;
            GroupEntity canonical = preferCanonicalGroup(existing, group);
            uniqueByChatId.put(group.getTelegramChatId(), canonical);
            GroupEntity duplicate = canonical == existing ? group : existing;
            if (duplicateWarningChatIds.add(group.getTelegramChatId())) {
                log.warn(
                        "Duplicate group rows detected for telegramChatId={}: keeping id={}, hiding id={}",
                        group.getTelegramChatId(),
                        canonical.getId(),
                        duplicate.getId()
                );
            }
        }
        if (duplicatesHidden > 0) {
            log.info("Group response deduplicated: hiddenDuplicates={} uniqueGroups={}", duplicatesHidden, uniqueByChatId.size());
        }
        return uniqueByChatId.values().stream()
                .sorted(Comparator.comparing(GroupEntity::getId))
                .toList();
    }

    private GroupEntity preferCanonicalGroup(GroupEntity left, GroupEntity right) {
        return Comparator
                .comparing((GroupEntity group) -> Boolean.TRUE.equals(group.getEnabled()))
                .thenComparing(group -> group.getAccountId() != null)
                .thenComparing(group -> group.getLastReadMessageId() != null ? group.getLastReadMessageId() : 0L)
                .thenComparing(GroupEntity::getUpdatedAt)
                .thenComparing(GroupEntity::getId)
                .compare(left, right) >= 0 ? left : right;
    }

    private void schedulePostCommitWarmup(Long groupId, Long telegramChatId) {
        Runnable task = () -> {
            try {
                int created = syncRecentMessagesAfterEnable(groupId, telegramChatId);
                log.info("Post-enable message warmup completed for group {}: {} new messages", groupId, created);
            } catch (RuntimeException exception) {
                log.warn("Post-enable message warmup failed for group {}: {}", groupId, exception.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boolean scheduled = telegramSyncTaskExecutor.execute("group-enable-sync:" + groupId, task);
                    log.info(
                            "Post-enable sync dispatch: groupId={} telegramChatId={} scheduled={} activeSyncs={} queuedTasks={}",
                            groupId,
                            telegramChatId,
                            scheduled,
                            telegramSyncTaskExecutor.getActiveCount(),
                            telegramSyncTaskExecutor.getQueueSize()
                    );
                }
            });
        } else {
            boolean scheduled = telegramSyncTaskExecutor.execute("group-enable-sync:" + groupId, task);
            log.info(
                    "Post-enable sync dispatch (no tx): groupId={} telegramChatId={} scheduled={} activeSyncs={} queuedTasks={}",
                    groupId,
                    telegramChatId,
                    scheduled,
                    telegramSyncTaskExecutor.getActiveCount(),
                    telegramSyncTaskExecutor.getQueueSize()
            );
        }
    }

    private int syncRecentMessagesAfterEnable(Long groupId, Long telegramChatId) {
        GroupEntity persistedGroup = groupRepository.findById(groupId).orElse(null);
        if (persistedGroup == null || !Boolean.TRUE.equals(persistedGroup.getEnabled())) {
            return 0;
        }
        return syncRecentMessages(persistedGroup, telegramChatId);
    }
}
