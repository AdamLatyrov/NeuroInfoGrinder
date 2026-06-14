package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineEventBus;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramRefreshCoordinator {

    private static final long MESSAGE_SYNC_COOLDOWN_MS = 5_000L;
    private static final long TOPIC_WARMUP_COOLDOWN_MS = 15_000L;

    private final MessageService messageService;
    private final TelegramTdlibService telegramTdlibService;
    private final PipelineEventBus pipelineEventBus;

    private final Set<Long> syncingGroups = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> lastGroupSyncAt = new ConcurrentHashMap<>();
    private final Set<Long> warmingTopicChats = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> lastTopicWarmupAt = new ConcurrentHashMap<>();

    public boolean requestMessageSync(Long groupId, String reason) {
        if (!shouldRun(groupId, lastGroupSyncAt, MESSAGE_SYNC_COOLDOWN_MS) || !syncingGroups.add(groupId)) {
            return false;
        }

        log.debug("Scheduling Telegram sync for group {} ({})", groupId, reason);
        CompletableFuture.runAsync(() -> runMessageSync(groupId));
        return true;
    }

    public boolean requestTopicWarmup(long chatId, int limit, String reason) {
        if (!shouldRun(chatId, lastTopicWarmupAt, TOPIC_WARMUP_COOLDOWN_MS) || !warmingTopicChats.add(chatId)) {
            return false;
        }

        log.debug("Scheduling topic warmup for chat {} ({})", chatId, reason);
        CompletableFuture.runAsync(() -> runTopicWarmup(chatId, limit));
        return true;
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

    private boolean shouldRun(long key, ConcurrentMap<Long, Long> lastRunAt, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastRun = lastRunAt.get(key);
        if (lastRun != null && now - lastRun < cooldownMs) {
            return false;
        }
        lastRunAt.put(key, now);
        return true;
    }
}
