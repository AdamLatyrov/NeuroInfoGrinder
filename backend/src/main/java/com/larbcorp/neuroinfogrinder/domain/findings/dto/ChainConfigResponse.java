package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record ChainConfigResponse(
    Boolean includeReplies,
    Integer timeWindowMinutes,
    Integer minMessagesForProcessing,
    Integer maxMessagesPerChain
) {}
