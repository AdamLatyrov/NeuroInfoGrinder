package com.larbcorp.neuroinfogrinder.telegram.model;

public record TelegramMessageDto(
        long id,
        long chatId,
        long messageThreadId,
        String topicName,
        String contentType,
        String text,
        String senderName,
        Long senderTelegramUserId,
        boolean isBot,
        long replyToMessageId,
        long date
) {
}
