package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record ClassifierResult(
    double score,
    boolean matched,
    List<String> labels,
    boolean guideCandidate,
    List<Long> evidenceMessageIds,
    String reasoning,
    Integer problemSignalScore,
    Integer painScore,
    Integer willingnessToPayScore,
    Integer guidePotentialScore,
    Integer urgencyScore,
    Integer technicalDepthScore,
    Integer spamScore,
    String meaningSummary,
    String problemStatement,
    String solutionHint,
    List<String> mentionedTools,
    List<String> mentionedPrices,
    List<String> mentionedErrors,
    List<String> categories,
    Long providerId,
    String model
) {
    public ClassifierResult {
        labels = labels == null ? List.of() : List.copyOf(labels);
        evidenceMessageIds = evidenceMessageIds == null ? List.of() : List.copyOf(evidenceMessageIds);
        mentionedTools = mentionedTools == null ? List.of() : List.copyOf(mentionedTools);
        mentionedPrices = mentionedPrices == null ? List.of() : List.copyOf(mentionedPrices);
        mentionedErrors = mentionedErrors == null ? List.of() : List.copyOf(mentionedErrors);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public ClassifierResult(
        double score,
        boolean matched,
        List<String> labels,
        boolean guideCandidate,
        List<Long> evidenceMessageIds,
        String reasoning,
        Integer problemSignalScore,
        Integer painScore,
        Integer willingnessToPayScore,
        Integer guidePotentialScore,
        Integer urgencyScore,
        Integer technicalDepthScore,
        Integer spamScore,
        String meaningSummary,
        String problemStatement,
        String solutionHint,
        List<String> mentionedTools,
        List<String> mentionedPrices,
        List<String> mentionedErrors,
        List<String> categories
    ) {
        this(
            score, matched, labels, guideCandidate, evidenceMessageIds, reasoning,
            problemSignalScore, painScore, willingnessToPayScore, guidePotentialScore,
            urgencyScore, technicalDepthScore, spamScore,
            meaningSummary, problemStatement, solutionHint,
            mentionedTools, mentionedPrices, mentionedErrors, categories,
            null, null
        );
    }

    public ClassifierResult(
        double score,
        boolean matched,
        List<String> labels,
        boolean guideCandidate,
        List<Long> evidenceMessageIds,
        String reasoning
    ) {
        this(
            score, matched, labels, guideCandidate, evidenceMessageIds, reasoning,
            null, null, null, null, null, null, null,
            null, null, null,
            List.of(), List.of(), List.of(), List.of(),
            null, null
        );
    }

    public ClassifierResult(double score, boolean matched, String reasoning) {
        this(score, matched, List.of(), false, List.of(), reasoning);
    }
}
