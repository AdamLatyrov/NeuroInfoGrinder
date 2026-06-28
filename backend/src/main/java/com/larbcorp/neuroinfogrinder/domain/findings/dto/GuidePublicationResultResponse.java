package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record GuidePublicationResultResponse(
    int requested,
    int sent,
    int skipped,
    int failed
) {}
