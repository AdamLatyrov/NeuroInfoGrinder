package com.larbcorp.neuroinfogrinder2.ingest;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LocalMessageInput(
        Long accountId,
        Long telegramChatId,
        Long telegramMessageId,
        Long telegramTopicId,
        Long senderId,
        String senderName,
        String senderUsername,
        Boolean senderIsBot,
        Long replyToMessageId,
        String text,
        String caption,
        String messageDate,
        String editDate,
        String contentType,
        List<JsonNode> entities,
        List<JsonNode> captionEntities,
        JsonNode media,
        JsonNode rawJson
) {
}
