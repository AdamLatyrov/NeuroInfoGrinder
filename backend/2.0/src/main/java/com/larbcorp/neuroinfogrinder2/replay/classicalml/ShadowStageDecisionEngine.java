package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ShadowStageDecisionEngine implements StageDecisionEngine {
    private final ClassicalMlInferenceClient inferenceClient;
    private final ObjectMapper json;

    public ShadowStageDecisionEngine(ClassicalMlInferenceClient inferenceClient, ObjectMapper json) {
        this.inferenceClient = inferenceClient;
        this.json = json;
    }

    @Override
    public StageDecisionSnapshot decideMessage(String targetId, String text, ObjectNode featureStore) {
        ClassicalMlTriageResult result = inferenceClient.inferMessage(targetId, text, featureStore);
        return snapshot("MESSAGE", targetId, featureStore, result);
    }

    @Override
    public StageDecisionSnapshot decideDiscussionSegment(String targetId, String text, ObjectNode featureStore) {
        ClassicalMlTriageResult result = inferenceClient.inferSegment(targetId, text, featureStore);
        return snapshot("DISCUSSION_SEGMENT", targetId, featureStore, result);
    }

    @Override
    public StageDecisionSnapshot decideCluster(String targetId, String text, ObjectNode featureStore) {
        ClassicalMlTriageResult result = inferenceClient.inferCluster(targetId, text, featureStore);
        return snapshot("CLUSTER", targetId, featureStore, result);
    }

    private StageDecisionSnapshot snapshot(String targetType, String targetId, ObjectNode featureStore, ClassicalMlTriageResult result) {
        ObjectNode stageResults = json.createObjectNode();
        stageResults.set("triage", json.valueToTree(result));
        return new StageDecisionSnapshot(
            targetType,
            targetId,
            result.featureVersion(),
            featureStore,
            stageResults,
            result.recommendedAction(),
            result.confidenceBand(),
            result.abstained(),
            OffsetDateTime.now()
        );
    }
}
