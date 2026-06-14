package com.larbcorp.neuroinfogrinder.telegram.model;

public record TelegramChatDto(
        long id,
        String title,
        String username,
        String type,
        String sourceType,
        boolean forum,
        long lastMessageId
) {
}
