package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record TestProviderResponse(
    boolean success,
    Long latencyMs,
    String error
) {}
