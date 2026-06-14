package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record UpdateProviderRequest(
    String name,
    String protocol,
    String endpointUrl,
    String apiKey,
    String model
) {}
