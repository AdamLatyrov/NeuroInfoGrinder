package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record GuidesByGroupResponse(
    Long groupId,
    String groupTitle,
    long guidesCount,
    double avgConfidence
) {}
