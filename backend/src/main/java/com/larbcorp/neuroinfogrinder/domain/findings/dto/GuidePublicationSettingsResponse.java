package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.time.Instant;
import java.util.List;

public record GuidePublicationSettingsResponse(
    Boolean enabled,
    String mode,
    Long targetGroupId,
    Long targetTelegramChatId,
    Long targetTopicId,
    Boolean appendSourceLink,
    Double minConfidence,
    List<String> sendOnlyStatuses,
    String format,
    Instant createdAt,
    Instant updatedAt
) {}
