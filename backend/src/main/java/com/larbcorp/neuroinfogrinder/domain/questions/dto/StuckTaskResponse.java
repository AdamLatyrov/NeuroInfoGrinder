package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.time.Instant;

public record StuckTaskResponse(
    Long id,
    String taskType,
    String status,
    Integer retryCount,
    String errorMessage,
    Instant createdAt
) {}
