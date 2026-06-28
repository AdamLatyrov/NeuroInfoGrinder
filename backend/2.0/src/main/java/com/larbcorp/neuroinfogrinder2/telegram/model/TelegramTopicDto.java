package com.larbcorp.neuroinfogrinder2.telegram.model;

public record TelegramTopicDto(
        long chatId,
        long forumTopicId,
        long messageThreadId,
        String name,
        boolean general
) {
}
