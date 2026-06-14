package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateGuideContentRequest(
    @NotBlank String title,
    @NotBlank String content,
    String contentMarkdown
) {}
