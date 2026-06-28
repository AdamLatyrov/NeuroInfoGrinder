package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ClassicalMlTriageResult(
    String stage,
    String targetId,
    JsonNode modelResults,
    JsonNode modelVotes,
    JsonNode calibratedProbabilities,
    String confidenceBand,
    boolean abstained,
    String abstentionReason,
    String recommendedAction,
    String modelVersion,
    String featureVersion,
    String trainingDatasetVersion,
    long inferenceLatencyMs,
    boolean fallbackUsed,
    double disagreementRate,
    JsonNode predictions,
    JsonNode reasons
) {
}
