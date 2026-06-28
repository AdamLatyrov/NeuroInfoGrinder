package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramRefreshCoordinator {

    private static final long MESSAGE_SYNC_COOLDOWN_MS = 5_000L;
    private static final long TOPIC_WARMUP_COOLDOWN_MS = 15_000L;
    private static final int MIN_SCHEDULED_BATCH_SIZE = 4;
    private static final Duration RECENT_GROUP_WINDOW = Duration.ofHours(12);

    private final MessageService messageService;
    private final TelegramTdlibService telegramTdlibService;
    private final PipelineEventBus pipelineEventBus;
    private final GroupRepository groupRepository;
    private final TelegramSyncTaskExecutor syncTaskExecutor;

    @Value("${telegram.tdlib.sync.enabled:true}")
    private boolean scheduledSyncEnabled;
    @Value("${telegram.tdlib.sync.batch-size:2}")
    private int scheduledBatchSize;

    private final Set<Long> syncingGroups = ConcurrentHashMap.newKeySet();
    private final Set<String> syncingChats = ConcurrentHashMap.newKeySet();
    private final Set<String> syncingTelegramChats = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> lastGroupSyncAt = new ConcurrentHashMap<>();
    private final Set<String> warmingTopicChats = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Long> lastTopicWarmupAt = new ConcurrentHashMap<>();

    public MessageSyncRequestResponse requestMessageSync(Long groupId, String reason) {
        return requestMessageSync(null, groupId, reason);
    }

    public MessageSyncRequestResponse requestMessageSync(Long ownerUserId, Long groupId, String reason) {
        Long telegramChatId = null;
        String chatSyncKey = null;
        boolean groupMarked = false;
        boolean chatMarked = false;
        try {
            var groupOptional = ownerUserId == null
                    ? groupRepository.findById(groupId)
                    : groupRepository.findByIdAndOwnerUserId(groupId, ownerUserId);
            if (groupOptional.isEmpty()) {
                return MessageSyncRequestResponse.rejected(groupId, "group_not_found");
            }

            var group = groupOptional.get();
            telegramChatId = group.getTelegramChatId();
            chatSyncKey = chatSyncKey(group.getAccountId(), telegramChatId);
            if (!Boolean.TRUE.equals(group.getEnabled())) {
                return MessageSyncRequestResponse.rejected(groupId, "disabled_group");
            }
            if (syncingGroups.contains(groupId) || syncingChats.contains(chatSyncKey)) {
                log.debug("Skipping Telegram sync for group {} (reason={}, already in-flight)", groupId, reason);
                return MessageSyncRequestResponse.rejected(groupId, "already_running");
            }
            if (syncingTelegramChats.contains(chatSyncKey)) {
                log.debug("Skipping Telegram sync for group {} (reason={}, telegram chat already in-flight)", groupId, reason);
                return MessageSyncRequestResponse.rejected(groupId, "already_running");
            }
            if (!shouldRun(groupId, lastGroupSyncAt, MESSAGE_SYNC_COOLDOWN_MS)) {
                log.debug("Skipping Telegram sync for group {} (reason={}, cooldown active)", groupId, reason);
                return MessageSyncRequestResponse.rejected(groupId, "cooldown");
            }
            if (!isTdlibReady(group.getAccountId())) {
                return MessageSyncRequestResponse.rejected(groupId, "telegram_not_ready");
            }
            if (!syncingGroups.add(groupId)) {
                return MessageSyncRequestResponse.rejected(groupId, "already_running");
            }
            groupMarked = true;
            if (!syncingChats.add(chatSyncKey)) {
                syncingGroups.remove(groupId);
                groupMarked = false;
                return MessageSyncRequestResponse.rejected(groupId, "already_running");
            }
            chatMarked = true;
            if (!syncingTelegramChats.add(chatSyncKey)) {
                syncingGroups.remove(groupId);
                syncingChats.remove(chatSyncKey);
                groupMarked = false;
                chatMarked = false;
                return MessageSyncRequestResponse.rejected(groupId, "already_running");
            }

            boolean accepted = syncTaskExecutor.execute("message-sync:" + groupId, () -> runMessageSync(groupId));
            if (!accepted) {
                syncingGroups.remove(groupId);
                syncingChats.remove(chatSyncKey);
                syncingTelegramChats.remove(chatSyncKey);
                log.debug("Skipping Telegram sync for group {} (reason={}, executor saturated)", groupId, reason);
                return MessageSyncRequestResponse.rejected(groupId, "executor_saturated");
            }
            log.debug(
                    "Scheduled Telegram sync for group {} ({}) (activeSyncs={}, queuedTasks={})",
                    groupId,
                    reason,
                    syncTaskExecutor.getActiveCount(),
                    syncTaskExecutor.getQueueSize()
            );
            return MessageSyncRequestResponse.scheduled(groupId);
        } catch (Exception exception) {
            if (groupMarked) {
                syncingGroups.remove(groupId);
            }
            if (chatMarked && chatSyncKey != null) {
                syncingChats.remove(chatSyncKey);
            }
            if (chatMarked && telegramChatId != null) {
                syncingTelegramChats.remove(chatSyncKey);
            }
            log.warn("Failed to schedule Telegram sync for group {} (reason={}): {}", groupId, reason, exception.getMessage());
            return MessageSyncRequestResponse.rejected(groupId, "error");
        }
    }

    public boolean requestTopicWarmup(long chatId, int limit, String reason) {
        return requestTopicWarmup(null, chatId, limit, reason);
    }

    public boolean requestTopicWarmup(Long accountId, long chatId, int limit, String reason) {
        String warmupKey = chatSyncKey(accountId, chatId);
        if (!shouldRun(warmupKey, lastTopicWarmupAt, TOPIC_WARMUP_COOLDOWN_MS)) {
            log.debug("Skipping topic warmup for account/chat {} (reason={}, cooldown active)", warmupKey, reason);
            return false;
        }
        if (!warmingTopicChats.add(warmupKey)) {
            log.debug("Skipping topic warmup for account/chat {} (reason={}, already in-flight)", warmupKey, reason);
            return false;
        }

        boolean accepted = syncTaskExecutor.execute("topic-warmup:" + warmupKey, () -> runTopicWarmup(accountId, chatId, limit));
        if (!accepted) {
            warmingTopicChats.remove(warmupKey);
            log.debug("Skipping topic warmup for account/chat {} (reason={}, executor saturated)", warmupKey, reason);
            return false;
        }
        log.debug("Scheduled topic warmup for account/chat {} ({})", warmupKey, reason);
        return true;
    }

    @Scheduled(
            fixedDelayString = "${telegram.tdlib.sync.fixed-delay-ms:15000}",
            initialDelayString = "${telegram.tdlib.sync.initial-delay-ms:5000}"
    )
    @Deprecated
    public void runScheduledCatchUp() {
        log.debug("Automatic Telegram catch-up is disabled; use explicit telegram_backfill_jobs instead");
    }

    private void runMessageSync(Long groupId) {
        Long telegramChatId = null;
        Long accountId = null;
        try {
            var group = groupRepository.findById(groupId).orElse(null);
            telegramChatId = group != null ? group.getTelegramChatId() : null;
            accountId = group != null ? group.getAccountId() : null;
            MessageSyncResult result = messageService.syncMessagesFromTelegram(groupId);
            pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
                    result.fullSuccess() ? "MESSAGE_SYNC_COMPLETED" : "MESSAGE_SYNC_FAILED",
                    null,
                    groupId,
                    "TELEGRAM_SYNC",
                    result.historyTimeout() ? "DEGRADED" : result.changed() > 0 ? "UPDATED" : "COMPLETED"
            ));
        } catch (Exception exception) {
            log.warn("Background Telegram sync failed for group {}: {}", groupId, exception.getMessage());
            pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
                    "MESSAGE_SYNC_FAILED",
                    null,
                    groupId,
                    "TELEGRAM_SYNC",
                    "FAILED"
            ));
        } finally {
            syncingGroups.remove(groupId);
            if (telegramChatId != null) {
                String syncKey = chatSyncKey(accountId, telegramChatId);
                syncingChats.remove(syncKey);
                syncingTelegramChats.remove(syncKey);
            } else {
                groupRepository.findById(groupId).ifPresent(group -> {
                    syncingChats.remove(chatSyncKey(group.getAccountId(), group.getTelegramChatId()));
                syncingTelegramChats.remove(chatSyncKey(group.getAccountId(), group.getTelegramChatId()));
                });
            }
        }
    }

    private void runTopicWarmup(Long accountId, long chatId, int limit) {
        String warmupKey = chatSyncKey(accountId, chatId);
        try {
            if (accountId != null) {
                telegramTdlibService.getTopics(accountId, chatId, limit);
            } else {
                telegramTdlibService.getTopics(chatId, limit);
            }
            pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
                    "TOPICS_REFRESHED",
                    null,
                    null,
                    "TELEGRAM_TOPICS",
                    "COMPLETED"
            ));
        } catch (Exception exception) {
            log.debug("Background topic warmup failed for chat {}: {}", chatId, exception.getMessage());
        } finally {
            warmingTopicChats.remove(warmupKey);
        }
    }

    private boolean isTdlibReady() {
        return isTdlibReady(null);
    }

    private boolean isTdlibReady(Long accountId) {
        try {
            return accountId != null
                    ? telegramTdlibService.getAuthorizationState(accountId).ready()
                    : telegramTdlibService.getAuthorizationState().ready();
        } catch (Exception exception) {
            log.debug("Skipping scheduled Telegram catch-up until TDLib is ready: {}", exception.getMessage());
            return false;
        }
    }

    private String chatSyncKey(Long accountId, Long telegramChatId) {
        return (accountId != null ? accountId : 0L) + ":" + telegramChatId;
    }

    private boolean shouldRun(long key, ConcurrentMap<Long, Long> lastRunAt, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastRun = lastRunAt.get(key);
        if (lastRun != null && now - lastRun < cooldownMs) {
            return false;
        }
        lastRunAt.put(key, now);
        return true;
    }

    private boolean shouldRun(String key, ConcurrentMap<String, Long> lastRunAt, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastRun = lastRunAt.get(key);
        if (lastRun != null && now - lastRun < cooldownMs) {
            return false;
        }
        lastRunAt.put(key, now);
        return true;
    }

    private List<Long> selectBatch(List<GroupEntity> enabledGroups) {
        int batchSize = Math.max(MIN_SCHEDULED_BATCH_SIZE, scheduledBatchSize);
        Set<String> selectedTelegramChats = ConcurrentHashMap.newKeySet();
        List<Long> selected = new java.util.ArrayList<>();
        List<GroupEntity> sortedGroups = enabledGroups.stream()
                .sorted(groupPriority())
                .toList();
        Instant recentThreshold = recentThreshold(sortedGroups);
        List<GroupEntity> recentGroups = new ArrayList<>();
        List<GroupEntity> coldGroups = new ArrayList<>();
        for (GroupEntity group : sortedGroups) {
            if (isRecentGroup(group, recentThreshold)) {
                recentGroups.add(group);
            } else {
                coldGroups.add(group);
            }
        }

        int recentSlots = batchSize <= 1 ? batchSize : Math.max(1, batchSize - 1);
        fillSelected(recentGroups, selected, selectedTelegramChats, recentSlots);
        fillSelected(coldGroups, selected, selectedTelegramChats, batchSize);
        fillSelected(recentGroups, selected, selectedTelegramChats, batchSize);
        return selected;
    }

    private Comparator<GroupEntity> groupPriority() {
        return Comparator
                .comparingLong((GroupEntity group) -> lastGroupSyncAt.getOrDefault(group.getId(), 0L))
                .thenComparing(GroupEntity::getLastReadAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GroupEntity::getOwnerUserId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GroupEntity::getId);
    }

    private void fillSelected(
            List<GroupEntity> groups,
            List<Long> selected,
            Set<String> selectedTelegramChats,
            int targetSize
    ) {
        for (GroupEntity group : groups) {
            if (selected.size() >= targetSize) {
                return;
            }
            Long groupId = group.getId();
            Long telegramChatId = group.getTelegramChatId();
            if (groupId == null || telegramChatId == null) {
                continue;
            }
            if (selected.contains(groupId)) {
                continue;
            }
            String syncKey = chatSyncKey(group.getAccountId(), telegramChatId);
            if (syncingGroups.contains(groupId) || syncingTelegramChats.contains(syncKey)) {
                continue;
            }
            if (!selectedTelegramChats.add(syncKey)) {
                continue;
            }
            selected.add(groupId);
        }
    }

    private Instant recentThreshold(List<GroupEntity> enabledGroups) {
        return enabledGroups.stream()
                .map(GroupEntity::getLastReadAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .map(value -> value.minus(RECENT_GROUP_WINDOW))
                .orElse(Instant.EPOCH);
    }

    private boolean isRecentGroup(GroupEntity group, Instant recentThreshold) {
        Instant lastReadAt = group.getLastReadAt();
        return lastReadAt != null && !lastReadAt.isBefore(recentThreshold);
    }
}
