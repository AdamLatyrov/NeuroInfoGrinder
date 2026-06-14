package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record LlmRequestDto(
    Long providerId,
    String model,
    Long promptId,
    String promptVersion,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Double estimatedCostUsd
) {}
