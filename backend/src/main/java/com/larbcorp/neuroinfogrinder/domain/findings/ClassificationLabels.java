package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClassificationLabels {

    public static final String DEMAND_SIGNAL = "DEMAND_SIGNAL";
    public static final String SOLUTION_MENTION = "SOLUTION_MENTION";
    public static final String VENDOR_OR_SOURCE = "VENDOR_OR_SOURCE";
    public static final String BUG_OR_LIMITATION = "BUG_OR_LIMITATION";
    public static final String PAYMENT_WORKAROUND = "PAYMENT_WORKAROUND";
    public static final String AI_TOOL_OR_PROVIDER = "AI_TOOL_OR_PROVIDER";
    public static final String PRACTICAL_PROBLEM = "PRACTICAL_PROBLEM";
    public static final String WORKFLOW_LIFEHACK = "WORKFLOW_LIFEHACK";
    public static final String BUSINESS_PROCESS = "BUSINESS_PROCESS";
    public static final String PRODUCT_FEEDBACK = "PRODUCT_FEEDBACK";
    public static final String DISCUSSION_INSIGHT = "DISCUSSION_INSIGHT";
    public static final String PRACTICAL_GUIDE_CANDIDATE = "PRACTICAL_GUIDE_CANDIDATE";
    public static final String OPPORTUNITY = "OPPORTUNITY";
    public static final String SPAM_OR_AD = "SPAM_OR_AD";
    public static final String NOT_USEFUL = "NOT_USEFUL";

    public static final Set<String> ALLOWLIST = Set.of(
        DEMAND_SIGNAL,
        SOLUTION_MENTION,
        VENDOR_OR_SOURCE,
        BUG_OR_LIMITATION,
        PAYMENT_WORKAROUND,
        AI_TOOL_OR_PROVIDER,
        PRACTICAL_PROBLEM,
        WORKFLOW_LIFEHACK,
        BUSINESS_PROCESS,
        PRODUCT_FEEDBACK,
        DISCUSSION_INSIGHT,
        PRACTICAL_GUIDE_CANDIDATE,
        OPPORTUNITY,
        SPAM_OR_AD,
        NOT_USEFUL
    );

    private ClassificationLabels() {
    }

    public static List<String> sanitize(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String normalized = label.trim().toUpperCase();
            if (ALLOWLIST.contains(normalized)) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }
}
