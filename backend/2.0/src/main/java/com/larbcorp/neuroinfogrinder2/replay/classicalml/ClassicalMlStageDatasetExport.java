package com.larbcorp.neuroinfogrinder2.replay.classicalml;

public record ClassicalMlStageDatasetExport(
    String stage,
    String format,
    Double minConfidence,
    Boolean includeWeakLabels,
    String splitStrategy,
    String outputPath
) {
}
