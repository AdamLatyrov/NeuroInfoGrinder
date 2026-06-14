package com.larbcorp.neuroinfogrinder.telegram.model;

public record TelegramTopicDto(
        long chatId,
        long forumTopicId,
        long messageThreadId,
        String name,
        boolean general
) {
}
