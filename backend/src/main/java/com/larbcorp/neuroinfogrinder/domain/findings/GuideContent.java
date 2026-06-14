package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.List;

public record GuideContent(
    String title,
    String content,
    String contentMarkdown,
    double confidence,
    List<String> tags,
    String generationError
) {}
