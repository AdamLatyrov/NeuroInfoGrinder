package com.larbcorp.neuroinfogrinder.domain.findings.dto;

import java.util.List;

public record GuidePublicationSendSelectedRequest(
    List<Long> guideIds,
    Boolean force,
    Boolean dryRun
) {}
