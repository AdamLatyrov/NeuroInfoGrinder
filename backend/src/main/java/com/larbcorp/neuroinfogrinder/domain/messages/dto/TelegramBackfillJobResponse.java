package com.larbcorp.neuroinfogrinder.domain.messages.dto;

import java.time.Instant;

public record TelegramBackfillJobResponse(
        Long id,
        Long accountId,
        Long ownerUserId,
        Long chatId,
        Long topicId,
        Long fromMessageId,
        Instant fromDate,
        Instant toDate,
        Long maxMessages,
        Integer batchSize,
        String status,
        Instant pausedUntil,
        Integer floodWaitSeconds,
        Long messagesFetched,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
