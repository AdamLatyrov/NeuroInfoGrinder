package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record GroupStatsResponse(
    Long groupId,
    String groupTitle,
    long messagesRead,
    long chainsBuilt,
    long guidesGenerated,
    long guidesPublished,
    long errorCount
) {}
