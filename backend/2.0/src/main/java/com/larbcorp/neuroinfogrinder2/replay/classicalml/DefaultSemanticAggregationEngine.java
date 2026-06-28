package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultSemanticAggregationEngine implements SemanticAggregationEngine {
    private final ObjectMapper json;

    public DefaultSemanticAggregationEngine(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ObjectNode aggregateDiscussionSignals(List<Long> sourceMessageIds, JsonNode rawSignals) {
        ObjectNode root = json.createObjectNode();
        root.put("sourceMessageCount", sourceMessageIds == null ? 0 : sourceMessageIds.size());
        root.put("signalCount", rawSignals == null || rawSignals.isNull() ? 0 : rawSignals.size());
        root.put("hasRepeatedDiscussion", sourceMessageIds != null && sourceMessageIds.size() > 1);
        root.set("rawSignals", rawSignals == null || rawSignals.isNull() ? json.createArrayNode() : rawSignals.deepCopy());
        return root;
    }
}
