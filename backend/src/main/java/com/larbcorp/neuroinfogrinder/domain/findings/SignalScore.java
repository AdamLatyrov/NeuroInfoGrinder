package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.Map;
import java.util.List;

public record SignalScore(
    double score,
    Map<String, Double> breakdown,
    List<String> labels,
    List<String> matchedSignals,
    String classificationReason
) {}
