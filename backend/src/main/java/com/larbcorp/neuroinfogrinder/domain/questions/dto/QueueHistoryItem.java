package com.larbcorp.neuroinfogrinder.domain.questions.dto;

import java.time.Instant;

public record QueueHistoryItem(
    Long id,
    String taskType,
    String status,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    String errorMessage
) {}
