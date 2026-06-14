package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record TestPromptResponse(
    String output,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    double estimatedCostUsd
) {}
