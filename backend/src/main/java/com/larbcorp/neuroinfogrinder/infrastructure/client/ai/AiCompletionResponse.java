package com.larbcorp.neuroinfogrinder.infrastructure.client.ai;

public record AiCompletionResponse(
    String content,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    String model,
    boolean success,
    String error
) {}
