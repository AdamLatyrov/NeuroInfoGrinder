package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record CreateRuleRequest(
    String name,
    String description,
    String actionType,
    String status,
    int order,
    String conditions,
    String actions
) {}
