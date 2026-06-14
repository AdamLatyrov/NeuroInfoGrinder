package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record TokenByProviderDto(
    Long providerId,
    String providerName,
    long tokens,
    double cost
) {}
