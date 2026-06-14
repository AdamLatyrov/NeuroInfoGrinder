package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record SettingsResponse(
    Publication publication,
    Processing processing,
    Filters filters,
    Limits limits,
    Notifications notifications
) {

    public record Publication(
        Long targetGroupId,
        String mode
    ) {}

    public record Processing(
        String mode,
        Integer pollIntervalSeconds,
        ChainWindow chainWindow
    ) {}

    public record ChainWindow(
        Boolean includeReplies,
        Integer timeWindowMinutes,
        Integer minMessagesForProcessing,
        Integer maxMessagesPerChain
    ) {}

    public record Filters(
        Boolean skipBots,
        Integer minMessageLength,
        String blacklistWords
    ) {}

    public record Limits(
        Long dailyTokenLimit,
        Long monthlyTokenLimit,
        Integer alertThresholdPercent
    ) {}

    public record Notifications(
        String telegramChat,
        String webhookUrl
    ) {}
}
