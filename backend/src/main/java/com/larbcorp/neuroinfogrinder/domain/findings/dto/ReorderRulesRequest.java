package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.util.List;

public record ReorderRulesRequest(
    List<Long> ruleIds
) {}
