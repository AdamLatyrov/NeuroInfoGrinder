package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.ModelWorkerClient;
import org.springframework.stereotype.Service;

@Service
public class WorkerBackedClassicalMlInferenceClient implements ClassicalMlInferenceClient {
    private final ModelWorkerClient worker;
    private final ObjectMapper json;

    public WorkerBackedClassicalMlInferenceClient(ModelWorkerClient worker, ObjectMapper json) {
        this.worker = worker;
        this.json = json;
    }

    @Override
    public ClassicalMlTriageResult inferMessage(String targetId, String text, ObjectNode featureStore) {
        return infer("/classical-ml/infer/message", targetId, text, featureStore);
    }

    @Override
    public ClassicalMlTriageResult inferSegment(String targetId, String text, ObjectNode featureStore) {
        return infer("/classical-ml/infer/segment", targetId, text, featureStore);
    }

    @Override
    public ClassicalMlTriageResult inferCluster(String targetId, String text, ObjectNode featureStore) {
        return infer("/classical-ml/infer/cluster", targetId, text, featureStore);
    }

    private ClassicalMlTriageResult infer(String path, String targetId, String text, ObjectNode featureStore) {
        ObjectNode request = json.createObjectNode();
        ArrayNode items = request.putArray("items");
        ObjectNode item = items.addObject();
        item.put("targetId", targetId);
        item.put("text", text == null ? "" : text);
        item.set("features", featureStore == null ? json.createObjectNode() : featureStore);
        JsonNode response = worker.postJson(path, request);
        JsonNode result = response.path("results").isArray() && !response.path("results").isEmpty()
            ? response.path("results").get(0)
            : json.createObjectNode();
        return new ClassicalMlTriageResult(
            result.path("stage").asText("CLASSICAL_ML"),
            result.path("targetId").asText(targetId),
            result.path("modelResults"),
            result.path("modelVotes"),
            result.path("calibratedProbabilities"),
            result.path("confidenceBand").asText("LOW"),
            result.path("abstained").asBoolean(false),
            result.path("abstentionReason").asText(null),
            result.path("recommendedAction").asText("TRACE_ONLY"),
            result.path("modelVersion").asText("classical-ml-bootstrap-v1"),
            result.path("featureVersion").asText(FeatureVectorBuilder.FEATURE_VERSION),
            result.path("trainingDatasetVersion").asText("bootstrap-reviewed-v0"),
            result.path("inferenceLatencyMs").asLong(response.path("latencyMs").asLong(0)),
            result.path("fallbackUsed").asBoolean(false),
            result.path("disagreementRate").asDouble(0.0),
            result.path("predictions"),
            result.path("reasons"),
            result.path("uncertainty"),
            result.path("modelAgreement").asDouble(0.0)
        );
    }
}
