package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.time.Instant;

public record ProviderResponse(
    Long id,
    String name,
    String protocol,
    String endpointUrl,
    boolean hasApiKey,
    String model,
    String status,
    boolean active,
    Instant lastTestedAt,
    String lastTestResult,
    String lastError
) {}
