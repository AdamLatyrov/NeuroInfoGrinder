package com.larbcorp.neuroinfogrinder.domain.messages.dto;

public record TelegramMonitoredChatRequest(
        Long accountId,
        Long chatId,
        String chatTitle,
        String chatType,
        Long topicId,
        Boolean enabled,
        Boolean liveIngestionEnabled,
        Boolean backfillEnabled
) {
}
