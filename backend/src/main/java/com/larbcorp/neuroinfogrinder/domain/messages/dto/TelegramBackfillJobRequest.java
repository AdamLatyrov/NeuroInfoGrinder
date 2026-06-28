package com.larbcorp.neuroinfogrinder.domain.messages.dto;

import java.time.Instant;

public record TelegramBackfillJobRequest(
        Long accountId,
        Long chatId,
        Long topicId,
        Long fromMessageId,
        Instant fromDate,
        Instant toDate,
        Long maxMessages,
        Integer batchSize
) {
}
