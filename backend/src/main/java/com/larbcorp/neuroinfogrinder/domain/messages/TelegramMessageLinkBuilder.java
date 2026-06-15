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
            return new TelegramMessageLink(null, false, "Ссылка недоступна: отсутствует группа или message metadata");
        }

        if (group.getUsername() != null && !group.getUsername().isBlank()) {
            return new TelegramMessageLink(
                "https://t.me/" + group.getUsername().trim() + "/" + message.getTelegramMessageId(),
                true,
                null
            );
        }

        String rawChatId = String.valueOf(group.getTelegramChatId());
        if (!rawChatId.startsWith("-100")) {
            return new TelegramMessageLink(
                null,
                false,
                "Ссылка недоступна: нет public username или недостаточно Telegram metadata"
            );
        }

        String normalizedChatId = rawChatId.substring(4);
        if (normalizedChatId.isBlank()) {
            return new TelegramMessageLink(
                null,
                false,
                "Ссылка недоступна: не удалось вычислить internal chat id"
            );
        }

        return new TelegramMessageLink(
            "https://t.me/c/" + normalizedChatId + "/" + message.getTelegramMessageId(),
            true,
            null
        );
    }
}
