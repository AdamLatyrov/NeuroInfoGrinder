package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePromptRequest(
    @NotBlank String name,
    @NotBlank String type,
    @NotBlank String content,
    String variables,
    String version
) {}
