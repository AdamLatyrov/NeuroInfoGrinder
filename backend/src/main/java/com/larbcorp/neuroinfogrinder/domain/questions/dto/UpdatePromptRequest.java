package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record UpdatePromptRequest(
    String name,
    String type,
    String content,
    String variables,
    String version,
    String status
) {}
