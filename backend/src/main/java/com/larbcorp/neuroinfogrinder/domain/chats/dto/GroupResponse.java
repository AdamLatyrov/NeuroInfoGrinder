package com.larbcorp.neuroinfogrinder.domain.chats.dto;

import java.time.Instant;

public record GroupResponse(
        Long id,
        Long telegramChatId,
        String title,
        String username,
        String sourceType,
        String category,
        Boolean forum,
        Boolean enabled,
        Long accountId,
        long messagesPerDay,
        long guidesFound,
        Instant lastReadAt,
        Long lastReadMessageId
) {
}
