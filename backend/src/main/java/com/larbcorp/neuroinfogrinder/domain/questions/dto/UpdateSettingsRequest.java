package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record UpdateSettingsRequest(
    Long publicationTargetGroupId,
    String publicationMode,
    String processingMode,
    Integer pollIntervalSeconds,
    Boolean chainIncludeReplies,
    Integer chainTimeWindowMinutes,
    Integer chainMinMessages,
    Integer chainMaxMessages,
    Boolean filterSkipBots,
    Integer filterMinMessageLength,
    String filterBlacklistWords,
    Long limitDailyTokenLimit,
    Long limitMonthlyTokenLimit,
    Integer limitAlertThresholdPct,
    String notificationTelegramChat,
    String notificationWebhookUrl
) {}
