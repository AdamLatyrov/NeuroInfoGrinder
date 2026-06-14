package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.time.Instant;
import java.util.List;

public record GuideDetailResponse(
    Long id,
    String title,
    Long groupId,
    String groupTitle,
    Long rootMessageId,
    Long providerId,
    String model,
    Long classifierId,
    Long promptId,
    String promptVersion,
    String status,
    Long duplicateOfId,
    Double duplicateScore,
    Double confidence,
    Integer totalTokens,
    Double estimatedCostUsd,
    List<String> tags,
    String generationError,
    Instant publishedAt,
    Instant createdAt,
    String content,
    String contentMarkdown,
    List<SourceMessageDto> sourceMessages,
    LlmRequestDto llmRequest,
    List<Long> relatedGuideIds,
    List<Long> possibleDuplicateIds
) {}
