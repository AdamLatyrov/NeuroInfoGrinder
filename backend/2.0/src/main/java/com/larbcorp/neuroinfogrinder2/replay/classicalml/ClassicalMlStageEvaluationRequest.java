package com.larbcorp.neuroinfogrinder2.replay.classicalml;

public record ClassicalMlStageEvaluationRequest(
    String stage,
    String datasetVersion,
    String featureVersion,
    Boolean includeWeakLabels,
    Double minConfidence,
    String splitStrategy
) {
}
