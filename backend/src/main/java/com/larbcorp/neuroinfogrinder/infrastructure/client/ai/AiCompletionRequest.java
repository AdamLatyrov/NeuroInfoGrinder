package com.larbcorp.neuroinfogrinder.infrastructure.client.ai;

import java.util.List;

public record AiCompletionRequest(
    String endpointUrl,
    String apiKey,
    String model,
    List<AiMessage> messages,
    double temperature,
    int maxTokens
) {}
