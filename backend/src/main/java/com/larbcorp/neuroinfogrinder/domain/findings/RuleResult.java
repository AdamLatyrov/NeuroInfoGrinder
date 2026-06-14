package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

/**
 * Result of rule evaluation against a message.
 */
public record RuleResult(
    boolean passes,
    String reason,
    List<RuleCheck> checks
) {}
