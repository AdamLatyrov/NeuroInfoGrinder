package com.larbcorp.neuroinfogrinder2.replay;

import java.util.List;
import java.util.Map;

public record MessageUsefulnessResult(
    String contentClass,
    String usefulnessClass,
    String sourceContextClass,
    String safetyClass,
    String candidateRoute,
    String proposedMaterialType,
    double overallScore,
    Map<String, Double> scoreDimensions,
    List<String> positiveSignals,
    List<String> negativeSignals,
    String rejectReason,
    String humanReason
) {
    public boolean rejected() {
        return rejectReason != null && !rejectReason.isBlank();
    }
}
