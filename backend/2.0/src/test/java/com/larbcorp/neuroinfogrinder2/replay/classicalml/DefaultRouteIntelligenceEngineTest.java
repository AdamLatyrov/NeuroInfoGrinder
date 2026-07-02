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

    @Test
    void blocksNearDuplicateCandidateBeforeJudge() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "105",
            featureStore(false, false, true),
            triage("PRACTICAL_INSTRUCTION", "MATERIAL_CANDIDATE", "ACTIONABLE", "ENOUGH_SINGLE_MESSAGE", "SINGLE_MESSAGE", "GUIDE", "ENOUGH_FOR_DRAFT", 0.86, 0.78,
                "CLEAN", "NEAR_DUPLICATE", "APPROVE_FOR_JUDGE"),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.recommendedRoute()).isEqualTo("NO_MATERIAL");
        assertThat(decision.uiReason()).isEqualTo("DUPLICATE_SUPPRESSED");
        assertThat(decision.policyFlags().toString()).contains("NEAR_DUPLICATE_SUPPRESSED");
    }

    @Test
    void blocksRiskSensitiveCandidateBeforeJudge() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "106",
            featureStore(false, false, true),
            triage("PRACTICAL_INSTRUCTION", "MATERIAL_CANDIDATE", "ACTIONABLE", "ENOUGH_SINGLE_MESSAGE", "SINGLE_MESSAGE", "GUIDE", "ENOUGH_FOR_DRAFT", 0.86, 0.78,
                "RISK_SENSITIVE", "UNIQUE", "APPROVE_FOR_JUDGE"),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.recommendedRoute()).isEqualTo("NO_MATERIAL");
        assertThat(decision.uiReason()).isEqualTo("RISK_REQUIRES_MANUAL_REVIEW");
        assertThat(decision.policyFlags().toString()).contains("RISK_SENSITIVE_MANUAL_REVIEW");
    }

    @Test
    void blocksCandidateWhenClassicalFinalGateFails() {
        RouteIntelligenceDecision decision = engine.decide(
            "MESSAGE",
            "107",
            featureStore(false, false, true),
            triage("PRACTICAL_INSTRUCTION", "MATERIAL_CANDIDATE", "ACTIONABLE", "ENOUGH_SINGLE_MESSAGE", "SINGLE_MESSAGE", "GUIDE", "ENOUGH_FOR_DRAFT", 0.86, 0.78,
                "CLEAN", "UNIQUE", "REJECT_BEFORE_JUDGE"),
            json.createObjectNode()
        );

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.recommendedRoute()).isEqualTo("NO_MATERIAL");
        assertThat(decision.uiReason()).isEqualTo("CLASSICAL_FINAL_GATE_REJECTED");
        assertThat(decision.policyFlags().toString()).contains("CLASSICAL_FINAL_GATE_REJECTED");
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
        return triage(meaning, value, usefulness, evidence, assembly, route, uiReason, routeProbability, evidenceProbability,
            "CLEAN", "UNIQUE", "APPROVE_FOR_JUDGE");
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
        double evidenceProbability,
        String preprocessing,
        String dedupeCluster,
        String llmJudge
    ) {
        ObjectNode triage = json.createObjectNode();
        ObjectNode stageResults = json.createObjectNode();
        stageResults.set("triage", triage);
        ObjectNode votes = triage.putObject("modelVotes");
        votes.put("preprocessingTop", preprocessing);
        votes.put("meaningTop", meaning);
        votes.put("valueTop", value);
        votes.put("usefulnessTop", usefulness);
        votes.put("evidenceTop", evidence);
        votes.put("assemblyTop", assembly);
        votes.put("routeTop", route);
        votes.put("uiReasonTop", uiReason);
        votes.put("dedupeClusterTop", dedupeCluster);
        votes.put("llmJudgeTop", llmJudge);
        ObjectNode probabilities = triage.putObject("calibratedProbabilities");
        probabilities.put("preprocessing", 0.91);
        probabilities.put("materialRoute", routeProbability);
        probabilities.put("evidenceSufficiency", evidenceProbability);
        probabilities.put("dedupeCluster", 0.89);
        probabilities.put("llmJudge", 0.88);
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
