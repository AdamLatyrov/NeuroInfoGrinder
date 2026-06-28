package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record GuideContent(
    String title,
    String content,
    String contentMarkdown,
    double confidence,
    List<String> tags,
    String generationError,
    String rawResponse,
    Long providerId,
    String model
) {
    public GuideContent(
        String title,
        String content,
        String contentMarkdown,
        double confidence,
        List<String> tags,
        String generationError,
        String rawResponse
    ) {
        this(title, content, contentMarkdown, confidence, tags, generationError, rawResponse, null, null);
    }
}
