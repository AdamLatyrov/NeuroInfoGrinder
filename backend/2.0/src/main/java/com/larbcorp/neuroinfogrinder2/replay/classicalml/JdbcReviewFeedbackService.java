package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class JdbcReviewFeedbackService implements ReviewFeedbackService {
    private final ObjectMapper json;

    public JdbcReviewFeedbackService(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public JsonNode buildStageFeedbackContext(String targetType, long targetId, JsonNode stageResults) {
        ObjectNode root = json.createObjectNode();
        root.put("targetType", targetType);
        root.put("targetId", targetId);
        root.set("stageResults", stageResults == null || stageResults.isNull() ? json.createObjectNode() : stageResults.deepCopy());
        return root;
    }
}
