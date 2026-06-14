package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkDeleteGuidesRequest(
    @NotEmpty List<Long> ids
) {}
