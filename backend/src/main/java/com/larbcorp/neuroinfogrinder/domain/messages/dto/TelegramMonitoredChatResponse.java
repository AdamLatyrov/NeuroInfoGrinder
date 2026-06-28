package com.larbcorp.neuroinfogrinder.domain.messages.dto;

import java.time.Instant;

public record TelegramMonitoredChatResponse(
        Long id,
        Long accountId,
        Long ownerUserId,
        Long chatId,
        String chatTitle,
        String chatType,
        Long topicId,
        Boolean enabled,
        Boolean liveIngestionEnabled,
        Boolean backfillEnabled,
        Long lastLiveMessageId,
        Instant lastLiveMessageAt,
        Long lastBackfillMessageId,
        String backfillStatus,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
