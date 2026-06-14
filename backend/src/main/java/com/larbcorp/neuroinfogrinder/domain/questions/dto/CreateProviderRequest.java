package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProviderRequest(
    @NotBlank String name,
    @NotBlank String protocol,
    @NotBlank String endpointUrl,
    String apiKey,
    String model
) {}
