package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record CreateClassifierRequest(
    String name,
    String type,
    Long providerId,
    Long promptId,
    String keywords,
    String regex,
    String modelConfig,
    String version,
    Integer order
) {}
