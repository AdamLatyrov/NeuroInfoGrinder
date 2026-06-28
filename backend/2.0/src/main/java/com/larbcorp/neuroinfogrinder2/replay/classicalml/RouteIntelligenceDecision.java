package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RouteIntelligenceDecision(
    String targetType,
    String targetId,
    String recommendedRoute,
    String assemblyStrategy,
    String uiReason,
    String confidenceBand,
    boolean requiresJudge,
    boolean blocked,
    JsonNode shortlist,
    JsonNode policyFlags,
    JsonNode reasonBundle
) {
}
