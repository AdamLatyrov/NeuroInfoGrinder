package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRouteIntelligenceEngineTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DefaultRouteIntelligenceEngine engine = new DefaultRouteIntelligenceEngine(json);

    @Test
    void blocksQuestionOnlyMessageWithoutAnswer() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "101",
            featureStore(true, false, false),
            triage("QUESTION", "NOT_GARBAGE_NO_MATERIAL", "CONTEXTUAL", "INSUFFICIENT_CONTEXT", "REJECT", "GUIDE", "LOW_CONFIDENCE_REVIEW", 0.62, 0.31),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.recommendedRoute()).isEqualTo("NO_MATERIAL");
        assertThat(decision.uiReason()).isEqualTo("QUESTION_WITHOUT_ANSWER");
    }

    @Test
    void blocksLinkOnlyMessageUntilEnrichment() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "102",
            featureStore(false, true, false),
            triage("RESOURCE_REFERENCE", "MATERIAL_CANDIDATE", "REFERENCE", "NEEDS_LINK_ENRICHMENT", "LINK_ENRICHED_SINGLE", "REFERENCE", "LINK_NEEDS_ENRICHMENT", 0.83, 0.86),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.uiReason()).isEqualTo("LINK_NEEDS_ENRICHMENT");
        assertThat(decision.policyFlags().toString()).contains("LINK_NEEDS_ENRICHMENT");
    }

    @Test
    void blocksClusterGuideWithoutActionableEvidence() {
        RouteIntelligenceDecision decision = engine.decide(
            "CLUSTER",
            "103",
            featureStore(false, false, false),
            triage("RESOURCE_REFERENCE", "MATERIAL_CANDIDATE", "REFERENCE", "NEEDS_CLUSTER_CONTEXT", "CLUSTER", "GUIDE", "MODEL_DISAGREEMENT_REVIEW", 0.74, 0.63),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.policyFlags().toString()).contains("CLUSTER_GUIDE_NEEDS_ACTIONABLE_EVIDENCE");
    }

    @Test
    void allowsReferenceCandidateToReachJudge() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "104",
            featureStore(false, true, true),
            triage("RESOURCE_REFERENCE", "MATERIAL_CANDIDATE", "REFERENCE", "ENOUGH_SINGLE_MESSAGE", "SINGLE_MESSAGE", "REFERENCE", "ENOUGH_FOR_DRAFT", 0.81, 0.72),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.requiresJudge()).isTrue();
        assertThat(decision.recommendedRoute()).isEqualTo("REFERENCE");
    }

    private ObjectNode featureStore(boolean question, boolean link, boolean answerLike) {
        ObjectNode root = json.createObjectNode();
        ObjectNode lexical = root.putObject("lexical");
        lexical.put("hasLink", link);
        ObjectNode structural = root.putObject("structural");
        structural.put("isQuestion", question);
        structural.put("linkCount", link ? 1 : 0);
        structural.put("isAnswerLike", answerLike);
        return root;
    }

    private ObjectNode triage(
        String meaning,
        String value,
        String usefulness,
        String evidence,
        String assembly,
        String route,
        String uiReason,
        double routeProbability,
        double evidenceProbability
    ) {
        ObjectNode triage = json.createObjectNode();
        ObjectNode stageResults = json.createObjectNode();
        stageResults.set("triage", triage);
        ObjectNode votes = triage.putObject("modelVotes");
        votes.put("meaningTop", meaning);
        votes.put("valueTop", value);
        votes.put("usefulnessTop", usefulness);
        votes.put("evidenceTop", evidence);
        votes.put("assemblyTop", assembly);
        votes.put("routeTop", route);
        votes.put("uiReasonTop", uiReason);
        ObjectNode probabilities = triage.putObject("calibratedProbabilities");
        probabilities.put("materialRoute", routeProbability);
        probabilities.put("evidenceSufficiency", evidenceProbability);
        ObjectNode modelResults = triage.putObject("modelResults");
        ArrayNode routes = modelResults.putArray("materialRoute");
        routes.add(routeNode(route, routeProbability, 1));
        routes.add(routeNode("NO_MATERIAL", 0.18, 2));
        triage.put("confidenceBand", "HIGH");
        triage.put("abstained", false);
        return stageResults;
    }

    private ObjectNode routeNode(String label, double probability, int rank) {
        ObjectNode node = json.createObjectNode();
        node.put("label", label);
        node.put("probability", probability);
        node.put("rank", rank);
        return node;
    }
}
