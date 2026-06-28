package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface SemanticAggregationEngine {
    ObjectNode aggregateDiscussionSignals(List<Long> sourceMessageIds, JsonNode rawSignals);
}
