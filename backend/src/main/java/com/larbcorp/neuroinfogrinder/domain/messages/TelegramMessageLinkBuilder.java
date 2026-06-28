package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;

public final class TelegramMessageLinkBuilder {

    private TelegramMessageLinkBuilder() {
    }

    public static String build(GroupEntity group, MessageEntity message) {
        return buildLink(group, message).url();
    }

    public static TelegramMessageLink buildLink(GroupEntity group, MessageEntity message) {
        if (group == null || message == null || message.getTelegramMessageId() == null) {
            return unavailable("Link unavailable: missing group or message metadata");
        }

        String topicSegment = topicSegment(group, message);
        String messageSegment = String.valueOf(message.getTelegramMessageId());

        if (group.getUsername() != null && !group.getUsername().isBlank()) {
            return new TelegramMessageLink(
                "https://t.me/" + group.getUsername().trim() + "/" + topicSegment + messageSegment,
                true,
                null
            );
        }

        String normalizedChatId = normalizeInternalChatId(group.getTelegramChatId());
        if (normalizedChatId == null || normalizedChatId.isBlank()) {
            return unavailable("Link unavailable: missing public username or Telegram chat metadata");
        }

        return new TelegramMessageLink(
            "https://t.me/c/" + normalizedChatId + "/" + topicSegment + messageSegment,
            true,
            null
        );
    }

    private static TelegramMessageLink unavailable(String reason) {
        return new TelegramMessageLink(null, false, reason);
    }

    private static String topicSegment(GroupEntity group, MessageEntity message) {
        return Boolean.TRUE.equals(group.getForum()) && message.getTopicId() != null
            ? message.getTopicId() + "/"
            : "";
    }

    private static String normalizeInternalChatId(Long telegramChatId) {
        if (telegramChatId == null) {
            return null;
        }

        String rawChatId = String.valueOf(telegramChatId).trim();
        if (rawChatId.startsWith("-100") && rawChatId.length() > 4) {
            return rawChatId.substring(4);
        }
        if (rawChatId.matches("\\d+")) {
            return rawChatId;
        }
        return null;
    }
}
