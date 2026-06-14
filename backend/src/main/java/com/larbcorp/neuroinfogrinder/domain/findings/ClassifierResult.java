package com.larbcorp.neuroinfogrinder.domain.findings;

public record ClassifierResult(double score, boolean matched, String reasoning) {}
