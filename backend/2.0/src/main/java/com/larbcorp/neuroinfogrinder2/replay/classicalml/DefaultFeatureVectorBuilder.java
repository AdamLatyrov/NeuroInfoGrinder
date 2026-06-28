package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultFeatureVectorBuilder implements FeatureVectorBuilder {
    private final ObjectMapper json;

    public DefaultFeatureVectorBuilder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ObjectNode buildMessageFeatures(
        long runId,
        long datasetMessageId,
        String normalizedText,
        JsonNode structuralFeatures,
        boolean hardSignal,
        String ruleDecision,
        JsonNode weakLabels,
        JsonNode classifierLabels
    ) {
        ObjectNode root = json.createObjectNode();
        root.put("featureVersion", FEATURE_VERSION);
        root.put("targetType", "MESSAGE");
        root.put("runId", runId);
        root.put("datasetMessageId", datasetMessageId);
        root.put("textLength", normalizedText == null ? 0 : normalizedText.length());
        root.put("tokenEstimate", Math.max(1, (normalizedText == null ? 0 : normalizedText.length()) / 4));
        root.put("hardSignal", hardSignal);
        root.put("ruleDecision", ruleDecision == null ? "" : ruleDecision);
        root.set("lexical", lexical(normalizedText));
        root.set("structural", structuralFeatures == null || structuralFeatures.isNull() ? json.createObjectNode() : structuralFeatures.deepCopy());
        root.set("weakLabels", weakLabels == null || weakLabels.isNull() ? json.createArrayNode() : weakLabels.deepCopy());
        root.set("classifierLabels", classifierLabels == null || classifierLabels.isNull() ? json.createArrayNode() : classifierLabels.deepCopy());
        return root;
    }

    @Override
    public ObjectNode buildDiscussionSegmentFeatures(
        long runId,
        long segmentId,
        String segmentText,
        List<Long> sourceMessageIds,
        JsonNode signals
    ) {
        ObjectNode root = json.createObjectNode();
        root.put("featureVersion", FEATURE_VERSION);
        root.put("targetType", "DISCUSSION_SEGMENT");
        root.put("runId", runId);
        root.put("segmentId", segmentId);
        root.put("textLength", segmentText == null ? 0 : segmentText.length());
        root.put("sourceMessageCount", sourceMessageIds == null ? 0 : sourceMessageIds.size());
        root.set("lexical", lexical(segmentText));
        root.set("signals", signals == null || signals.isNull() ? json.createArrayNode() : signals.deepCopy());
        ArrayNode ids = root.putArray("sourceMessageIds");
        if (sourceMessageIds != null) {
            sourceMessageIds.forEach(ids::add);
        }
        return root;
    }

    @Override
    public ObjectNode buildClusterFeatures(
        long runId,
        long clusterId,
        String title,
        double score,
        List<Long> memberIds,
        JsonNode topicAnalytics,
        JsonNode clusterScore
    ) {
        ObjectNode root = json.createObjectNode();
        root.put("featureVersion", FEATURE_VERSION);
        root.put("targetType", "CLUSTER");
        root.put("runId", runId);
        root.put("clusterId", clusterId);
        root.put("clusterScore", score);
        root.put("memberCount", memberIds == null ? 0 : memberIds.size());
        root.set("lexical", lexical(title));
        root.set("topicAnalytics", topicAnalytics == null || topicAnalytics.isNull() ? json.createObjectNode() : topicAnalytics.deepCopy());
        root.set("clusterScoreCard", clusterScore == null || clusterScore.isNull() ? json.createObjectNode() : clusterScore.deepCopy());
        ArrayNode ids = root.putArray("memberIds");
        if (memberIds != null) {
            memberIds.forEach(ids::add);
        }
        return root;
    }

    private ObjectNode lexical(String text) {
        String safe = text == null ? "" : text;
        ObjectNode lexical = json.createObjectNode();
        lexical.put("hasQuestionMark", safe.contains("?"));
        lexical.put("hasLink", safe.contains("http://") || safe.contains("https://") || safe.contains("www."));
        lexical.put("hasCodeFence", safe.contains("```"));
        lexical.put("lineCount", safe.isBlank() ? 0 : safe.split("\\R").length);
        lexical.put("uppercaseRatio", uppercaseRatio(safe));
        lexical.put("digitCount", safe.replaceAll("\\D", "").length());
        return lexical;
    }

    private double uppercaseRatio(String value) {
        int letters = 0;
        int upper = 0;
        for (char ch : value.toCharArray()) {
            if (Character.isLetter(ch)) {
                letters++;
                if (Character.isUpperCase(ch)) {
                    upper++;
                }
            }
        }
        return letters == 0 ? 0.0 : ((double) upper) / letters;
    }
}
