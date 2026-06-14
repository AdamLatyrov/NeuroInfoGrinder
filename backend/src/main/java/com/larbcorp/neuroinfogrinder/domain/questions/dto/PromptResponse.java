package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.util.List;

public record PromptResponse(
    Long id,
    String name,
    String type,
    String version,
    String content,
    List<String> variables,
    String status,
    List<String> linkedClassifiers,
    List<String> linkedRules
) {}
