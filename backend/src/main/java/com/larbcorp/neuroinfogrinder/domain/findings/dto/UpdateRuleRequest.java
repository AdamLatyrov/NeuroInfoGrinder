package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record UpdateRuleRequest(
    String name,
    String description,
    String actionType,
    String status,
    int order,
    String conditions,
    String actions
) {}
