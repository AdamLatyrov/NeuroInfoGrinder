package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.time.Instant;

public record PipelineTraceResponse(
    Long id,
    String traceId,
    Long messageId,
    Long groupId,
    String stage,
    String status,
    String inputData,
    String outputData,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs,
    Long ruleId,
    Long classifierId,
    Long promptId,
    Long providerId,
    String model,
    Integer inputTokens,
    Integer outputTokens,
    Double costUsd,
    Double score,
    Double confidence,
    String reason,
    String entityType,
    String entityName,
    String entityVersion,
    String configSnapshotJson,
    String tuningHint
) {}
