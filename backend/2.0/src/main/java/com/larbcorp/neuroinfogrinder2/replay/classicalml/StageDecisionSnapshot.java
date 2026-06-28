package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record StageDecisionSnapshot(
    String targetType,
    String targetId,
    String featureVersion,
    JsonNode featureStore,
    JsonNode stageResults,
    String chosenDecision,
    String confidenceBand,
    boolean abstained,
    OffsetDateTime createdAt
) {
}
