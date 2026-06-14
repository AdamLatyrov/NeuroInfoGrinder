package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record UpdateChainConfigRequest(
    Boolean includeReplies,
    Integer timeWindowMinutes,
    Integer minMessagesForProcessing,
    Integer maxMessagesPerChain
) {}
