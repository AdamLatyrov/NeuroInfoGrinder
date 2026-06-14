package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record ClassifierResult(
    double score,
    boolean matched,
    List<String> labels,
    boolean guideCandidate,
    List<Long> evidenceMessageIds,
    String reasoning
) {
    public ClassifierResult(double score, boolean matched, String reasoning) {
        this(score, matched, List.of(), false, List.of(), reasoning);
    }
}
