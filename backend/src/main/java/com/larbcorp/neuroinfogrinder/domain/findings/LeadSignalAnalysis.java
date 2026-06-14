package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record LeadSignalAnalysis(
    List<String> labels,
    List<String> matchedSignals,
    String classificationReason,
    boolean leadCandidate
) {}
