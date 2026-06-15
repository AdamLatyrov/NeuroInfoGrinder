package com.larbcorp.neuroinfogrinder.telegram.model;

public record TelegramMessageDto(
        long id,
        long chatId,
        long messageThreadId,
        String topicName,
        String contentType,
        String text,
        String senderName,
        String senderUsername,
        Long senderTelegramUserId,
        boolean isBot,
        String textEntitiesJson,
        long replyToMessageId,
        long date
) {
}
