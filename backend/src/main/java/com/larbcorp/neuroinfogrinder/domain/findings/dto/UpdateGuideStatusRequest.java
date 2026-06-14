package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateGuideStatusRequest(
    @NotBlank String status
) {}
