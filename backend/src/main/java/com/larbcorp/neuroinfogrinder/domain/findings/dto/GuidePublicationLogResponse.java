package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.time.Instant;

public record GuidePublicationLogResponse(
    Long id,
    Long guideId,
    String targetMode,
    Long targetGroupId,
    Long targetTelegramChatId,
    Long targetTopicId,
    String status,
    Long telegramMessageId,
    String error,
    Instant createdAt,
    Instant sentAt
) {}
