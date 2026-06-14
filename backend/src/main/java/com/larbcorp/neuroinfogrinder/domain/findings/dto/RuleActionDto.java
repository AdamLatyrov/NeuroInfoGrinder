package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record RuleActionDto(
    String type,
    Long classifierId,
    String classifierName,
    Double threshold
) {}
