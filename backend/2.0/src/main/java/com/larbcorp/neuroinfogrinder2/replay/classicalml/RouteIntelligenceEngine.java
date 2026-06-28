package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;

public interface RouteIntelligenceEngine {
    RouteIntelligenceDecision decide(String targetType, String targetId, JsonNode featureStore, JsonNode stageResults, JsonNode heuristics);
}
