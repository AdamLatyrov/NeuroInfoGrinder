package com.larbcorp.neuroinfogrinder.domain.messages;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;

public final class TelegramMessageLinkBuilder {

    private TelegramMessageLinkBuilder() {
    }

    public static String build(GroupEntity group, MessageEntity message) {
        if (group == null || message == null || message.getTelegramMessageId() == null) {
            return null;
        }

        if (group.getUsername() != null && !group.getUsername().isBlank()) {
            return "https://t.me/" + group.getUsername().trim() + "/" + message.getTelegramMessageId();
        }

        String rawChatId = String.valueOf(group.getTelegramChatId());
        if (!rawChatId.startsWith("-100")) {
            return null;
        }

        String normalizedChatId = rawChatId.substring(4);
        if (normalizedChatId.isBlank()) {
            return null;
        }

        return "https://t.me/c/" + normalizedChatId + "/" + message.getTelegramMessageId();
    }
}
