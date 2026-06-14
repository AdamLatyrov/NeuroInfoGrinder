package com.larbcorp.neuroinfogrinder.domain.messages.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long telegramMessageId,
        Long groupId,
        String senderName,
        Long senderTelegramUserId,
        boolean isBot,
        String text,
        Long replyToMessageId,
        int replyCount,
        String processingStatus,
        Long guideId,
        String topicName,
        Long topicId,
        Instant date,
        Double signalScore,
        Double classifierScore,
        String classifierReason,
        String signalBreakdown,
        String ruleResultJson
) {
}
