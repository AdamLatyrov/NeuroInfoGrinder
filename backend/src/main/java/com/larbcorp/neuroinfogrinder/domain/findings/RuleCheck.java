package com.larbcorp.neuroinfogrinder.domain.findings;

/**
 * Single rule check result.
 */
public record RuleCheck(
    String ruleName,
    String actionType,
    boolean matched,
    String detail
) {}
