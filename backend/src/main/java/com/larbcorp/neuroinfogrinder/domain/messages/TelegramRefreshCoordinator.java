package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramRefreshCoordinator {

    private static final long MESSAGE_SYNC_COOLDOWN_MS = 5_000L;
    private static final long TOPIC_WARMUP_COOLDOWN_MS = 15_000L;

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
    private final ConcurrentMap<Long, Long> lastGroupSyncAt = new ConcurrentHashMap<>();
    private final Set<Long> warmingTopicChats = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> lastTopicWarmupAt = new ConcurrentHashMap<>();
    private final AtomicInteger scheduledCursor = new AtomicInteger(0);

    public boolean requestMessageSync(Long groupId, String reason) {
        if (!shouldRun(groupId, lastGroupSyncAt, MESSAGE_SYNC_COOLDOWN_MS)) {
            log.debug("Skipping Telegram sync for group {} (reason={}, cooldown active)", groupId, reason);
            return false;
        }
        if (!syncingGroups.add(groupId)) {
            log.debug("Skipping Telegram sync for group {} (reason={}, already in-flight)", groupId, reason);
            return false;
        }

        boolean accepted = syncTaskExecutor.execute("message-sync:" + groupId, () -> runMessageSync(groupId));
        if (!accepted) {
            syncingGroups.remove(groupId);
            log.debug("Skipping Telegram sync for group {} (reason={}, executor saturated)", groupId, reason);
            return false;
        }
        log.debug(
                "Scheduled Telegram sync for group {} ({}) (activeSyncs={}, queuedTasks={})",
                groupId,
                reason,
                syncTaskExecutor.getActiveCount(),
                syncTaskExecutor.getQueueSize()
        );
        return true;
    }

    public boolean requestTopicWarmup(long chatId, int limit, String reason) {
        if (!shouldRun(chatId, lastTopicWarmupAt, TOPIC_WARMUP_COOLDOWN_MS)) {
            log.debug("Skipping topic warmup for chat {} (reason={}, cooldown active)", chatId, reason);
            return false;
        }
        if (!warmingTopicChats.add(chatId)) {
            log.debug("Skipping topic warmup for chat {} (reason={}, already in-flight)", chatId, reason);
            return false;
        }

        boolean accepted = syncTaskExecutor.execute("topic-warmup:" + chatId, () -> runTopicWarmup(chatId, limit));
        if (!accepted) {
            warmingTopicChats.remove(chatId);
            log.debug("Skipping topic warmup for chat {} (reason={}, executor saturated)", chatId, reason);
            return false;
        }
        log.debug("Scheduled topic warmup for chat {} ({})", chatId, reason);
        return true;
    }

    @Scheduled(
            fixedDelayString = "${telegram.tdlib.sync.fixed-delay-ms:15000}",
            initialDelayString = "${telegram.tdlib.sync.initial-delay-ms:5000}"
    )
    public void runScheduledCatchUp() {
        if (!scheduledSyncEnabled || !isTdlibReady()) {
            return;
        }

        List<Long> enabledGroupIds = groupRepository.findByEnabledTrue().stream()
                .map(group -> group.getId())
                .toList();
        if (enabledGroupIds.isEmpty()) {
            return;
        }

        List<Long> selectedGroupIds = selectBatch(enabledGroupIds);
        log.info(
                "Telegram sync batch started: selected={} totalEnabled={} activeSyncs={} queuedTasks={}",
                selectedGroupIds.size(),
                enabledGroupIds.size(),
                syncTaskExecutor.getActiveCount(),
                syncTaskExecutor.getQueueSize()
        );

        int scheduled = 0;
        for (Long groupId : selectedGroupIds) {
            if (requestMessageSync(groupId, "scheduled_catchup")) {
                scheduled++;
            }
        }

        log.info(
                "Telegram sync batch completed: scheduled={} selected={} activeSyncs={} queuedTasks={}",
                scheduled,
                selectedGroupIds.size(),
                syncTaskExecutor.getActiveCount(),
                syncTaskExecutor.getQueueSize()
        );
    }

    private void runMessageSync(Long groupId) {
        try {
            int changed = messageService.syncMessagesFromTelegram(groupId);
            pipelineEventBus.publish(new PipelineEventBus.PipelineEvent(
                    "MESSAGE_SYNC_COMPLETED",
                    null,
                    groupId,
                    "TELEGRAM_SYNC",
                    changed > 0 ? "UPDATED" : "COMPLETED"
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
        }
    }

    private void runTopicWarmup(long chatId, int limit) {
        try {
            telegramTdlibService.getTopics(chatId, limit);
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
            warmingTopicChats.remove(chatId);
        }
    }

    private boolean isTdlibReady() {
        try {
            return telegramTdlibService.getAuthorizationState().ready();
        } catch (Exception exception) {
            log.debug("Skipping scheduled Telegram catch-up until TDLib is ready: {}", exception.getMessage());
            return false;
        }
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

    private List<Long> selectBatch(List<Long> enabledGroupIds) {
        int batchSize = Math.max(1, scheduledBatchSize);
        if (enabledGroupIds.size() <= batchSize) {
            return enabledGroupIds;
        }

        int startIndex = Math.floorMod(scheduledCursor.getAndAdd(batchSize), enabledGroupIds.size());
        List<Long> selected = new ArrayList<>(batchSize);
        for (int offset = 0; offset < batchSize; offset++) {
            selected.add(enabledGroupIds.get((startIndex + offset) % enabledGroupIds.size()));
        }
        return selected;
    }
}
