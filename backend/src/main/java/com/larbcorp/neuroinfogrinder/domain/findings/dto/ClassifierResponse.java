package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record ClassifierResponse(
    Long id,
    String name,
    String type,
    Long providerId,
    String providerName,
    Long promptId,
    String keywords,
    String regex,
    String modelConfig,
    String version,
    String status,
    Integer order,
    int rulesCount
) {}
