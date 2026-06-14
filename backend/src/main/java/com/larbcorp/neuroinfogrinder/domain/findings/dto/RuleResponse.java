package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.util.List;

public record RuleResponse(
    Long id,
    String name,
    String description,
    String actionType,
    int order,
    List<RuleConditionDto> conditions,
    List<RuleActionDto> actions,
    String status
) {}
