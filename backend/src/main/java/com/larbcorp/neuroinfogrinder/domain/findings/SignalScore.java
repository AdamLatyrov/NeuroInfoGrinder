package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.Map;

public record SignalScore(double score, Map<String, Double> breakdown) {}
