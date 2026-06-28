package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface FeatureVectorBuilder {
    String FEATURE_VERSION = "classical-ml-v1";

    ObjectNode buildMessageFeatures(
        long runId,
        long datasetMessageId,
        String normalizedText,
        JsonNode structuralFeatures,
        boolean hardSignal,
        String ruleDecision,
        JsonNode weakLabels,
        JsonNode classifierLabels
    );

    ObjectNode buildDiscussionSegmentFeatures(
        long runId,
        long segmentId,
        String segmentText,
        List<Long> sourceMessageIds,
        JsonNode signals
    );

    ObjectNode buildClusterFeatures(
        long runId,
        long clusterId,
        String title,
        double score,
        List<Long> memberIds,
        JsonNode topicAnalytics,
        JsonNode clusterScore
    );
}
