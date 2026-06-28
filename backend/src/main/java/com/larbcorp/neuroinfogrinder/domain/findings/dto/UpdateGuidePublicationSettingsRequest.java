package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.util.List;

public record UpdateGuidePublicationSettingsRequest(
    Boolean enabled,
    Long targetGroupId,
    Long targetTelegramChatId,
    Long targetTopicId,
    Boolean appendSourceLink,
    Double minConfidence,
    List<String> sendOnlyStatuses
) {}
