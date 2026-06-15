package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record GuideRegenerateResponse(
    Long oldGuideId,
    Long newGuideId,
    String status
) {
}
