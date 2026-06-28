package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record ContentRoutingDecision(
    ContentType contentType,
    String contentSubtype,
    String topicLabel,
    String topicSummary,
    String contentTitle,
    String contentSummary,
    String normalizedTopicKey,
    String specificAngle,
    boolean shouldCreateMaterial,
    boolean shouldGenerateFullGuide,
    double confidence,
    int importanceScore,
    int actionabilityScore,
    int noveltyScore,
    int evidenceScore,
    int riskScore,
    int noiseScore,
    String safetyCategory,
    String publicationKind,
    String reason,
    List<String> warnings,
    List<String> suggestedSections,
    Long providerId,
    String model
) {
    public ContentRoutingDecision {
        contentType = contentType == null ? ContentType.DEFERRED : contentType;
        contentSubtype = normalizeBlank(contentSubtype);
        topicLabel = normalizeBlank(topicLabel);
        topicSummary = normalizeBlank(topicSummary);
        contentTitle = normalizeBlank(contentTitle);
        contentSummary = normalizeBlank(contentSummary);
        normalizedTopicKey = normalizeBlank(normalizedTopicKey);
        specificAngle = normalizeBlank(specificAngle);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        importanceScore = clampScore(importanceScore);
        actionabilityScore = clampScore(actionabilityScore);
        noveltyScore = clampScore(noveltyScore);
        evidenceScore = clampScore(evidenceScore);
        riskScore = clampScore(riskScore);
        noiseScore = clampScore(noiseScore);
        safetyCategory = normalizeBlank(safetyCategory);
        publicationKind = normalizeBlank(publicationKind);
        reason = normalizeBlank(reason);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        suggestedSections = suggestedSections == null ? List.of() : List.copyOf(suggestedSections);
    }

    public int confidenceScore() {
        return clampScore((int) Math.round(confidence * 100.0));
    }

    public int contentQualityScore() {
        int value = (int) Math.round(
            evidenceScore * 0.25
                + confidenceScore() * 0.25
                + importanceScore * 0.20
                + Math.max(actionabilityScore, noveltyScore) * 0.20
                + Math.max(0, 100 - noiseScore) * 0.10
        );
        return clampScore(value);
    }

    public ContentRoutingDecision withGateBlock(String reason) {
        return new ContentRoutingDecision(
            contentType,
            contentSubtype,
            topicLabel,
            topicSummary,
            contentTitle,
            contentSummary,
            normalizedTopicKey,
            specificAngle,
            false,
            false,
            confidence,
            importanceScore,
            actionabilityScore,
            noveltyScore,
            evidenceScore,
            riskScore,
            noiseScore,
            safetyCategory,
            publicationKind,
            reason,
            warnings,
            suggestedSections,
            providerId,
            model
        );
    }

    private static int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
