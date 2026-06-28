package com.larbcorp.neuroinfogrinder.domain.findings;

/**
 * Single rule check result.
 */
public record RuleCheck(
    Long ruleId,
    String ruleName,
    String actionType,
    boolean matched,
    String detail,
    String conditionsJson,
    String description
) {
    public RuleCheck(String ruleName, String actionType, boolean matched, String detail) {
        this(null, ruleName, actionType, matched, detail, null, null);
    }
}
