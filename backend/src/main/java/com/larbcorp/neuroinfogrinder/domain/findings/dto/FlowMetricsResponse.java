package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record FlowMetricsResponse(
    long messagesRead,
    long rulesPassed,
    long classified,
    long sentToLlm,
    long guidesCreated,
    long sentToModeration,
    long approved,
    long rejected,
    long errors,
    long totalInputTokens,
    long totalOutputTokens,
    double totalCostUsd
) {}
