package com.larbcorp.neuroinfogrinder2.replay.classicalml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultRouteIntelligenceEngine implements RouteIntelligenceEngine {
    private final ObjectMapper json;

    public DefaultRouteIntelligenceEngine(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public RouteIntelligenceDecision decide(String targetType, String targetId, JsonNode featureStore, JsonNode stageResults, JsonNode heuristics) {
        JsonNode triage = stageResults == null ? json.createObjectNode() : stageResults.path("triage");
        JsonNode modelVotes = triage.path("modelVotes");
        JsonNode modelResults = triage.path("modelResults");
        JsonNode calibrated = triage.path("calibratedProbabilities");

        String meaningTop = coalesce(modelVotes.path("meaningTop").asText(null), topLabel(triage.path("predictions").path("meaning")));
        String valueTop = coalesce(modelVotes.path("valueTop").asText(null), topLabel(triage.path("predictions").path("valueLevel")));
        String usefulnessTop = coalesce(modelVotes.path("usefulnessTop").asText(null), topLabel(triage.path("predictions").path("usefulnessKind")));
        String evidenceTop = coalesce(modelVotes.path("evidenceTop").asText(null), topLabel(triage.path("predictions").path("evidenceSufficiency")));
        String assemblyTop = coalesce(modelVotes.path("assemblyTop").asText(null), topLabel(triage.path("predictions").path("assemblyStrategy")));
        String routeTop = coalesce(modelVotes.path("routeTop").asText(null), topLabel(triage.path("predictions").path("materialRoute")), "NO_MATERIAL");
        String uiReasonTop = coalesce(modelVotes.path("uiReasonTop").asText(null), topLabel(triage.path("predictions").path("uiReason")), "LOW_CONFIDENCE_REVIEW");
        String preprocessingTop = coalesce(modelVotes.path("preprocessingTop").asText(null), topLabel(triage.path("predictions").path("preprocessing")), "CLEAN");
        String dedupeClusterTop = coalesce(modelVotes.path("dedupeClusterTop").asText(null), topLabel(triage.path("predictions").path("dedupeCluster")), "UNIQUE");
        String llmJudgeTop = coalesce(modelVotes.path("llmJudgeTop").asText(null), topLabel(triage.path("predictions").path("llmJudge")), "APPROVE_FOR_JUDGE");

        ArrayNode shortlist = topPredictions(modelResults.path("materialRoute"), 3);
        ArrayNode policyFlags = json.createArrayNode();
        ArrayNode routeReasons = json.createArrayNode();

        boolean answerLike = featureStore.path("structural").path("isAnswerLike").asBoolean(false);
        boolean hasLink = featureStore.path("structural").path("linkCount").asInt(0) > 0
            || featureStore.path("lexical").path("hasLink").asBoolean(false);
        boolean blocked = false;
        String recommendedRoute = routeTop;
        String finalUiReason = uiReasonTop;

        if ("NEEDS_LINK_ENRICHMENT".equalsIgnoreCase(evidenceTop) && hasLink) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "LINK_NEEDS_ENRICHMENT";
            policyFlags.add("LINK_NEEDS_ENRICHMENT");
            routeReasons.add("Link-only or weak-link evidence must be enriched before materialization.");
        }
        if ("QUESTION".equalsIgnoreCase(meaningTop) && !answerLike) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "QUESTION_WITHOUT_ANSWER";
            policyFlags.add("QUESTION_WITHOUT_ANSWER");
            routeReasons.add("Question-only content cannot become material without an answer.");
        }
        if ("ACCESS_CIRCUMVENTION".equalsIgnoreCase(meaningTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "ACCESS_CIRCUMVENTION_BLOCKED";
            policyFlags.add("ACCESS_CIRCUMVENTION_BLOCKED");
            routeReasons.add("Access-circumvention content is blocked from auto-materialization.");
        }
        if ("ABUSE_FRAUD".equalsIgnoreCase(meaningTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "ABUSE_FRAUD_REJECTED";
            policyFlags.add("ABUSE_FRAUD_REJECTED");
            routeReasons.add("Abuse/fraud content is blocked from auto-materialization.");
        }
        if ("RISK_SENSITIVE".equalsIgnoreCase(preprocessingTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "RISK_REQUIRES_MANUAL_REVIEW";
            policyFlags.add("RISK_SENSITIVE_MANUAL_REVIEW");
            routeReasons.add("Risk-sensitive content requires manual review before any judge or generation step.");
        }
        if ("NEAR_DUPLICATE".equalsIgnoreCase(dedupeClusterTop) || "DUPLICATE".equalsIgnoreCase(dedupeClusterTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "DUPLICATE_SUPPRESSED";
            policyFlags.add("NEAR_DUPLICATE_SUPPRESSED");
            routeReasons.add("Classical dedupe/cluster stage suppressed this candidate as duplicate or near-duplicate.");
        }
        if ("REJECT_BEFORE_JUDGE".equalsIgnoreCase(llmJudgeTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "CLASSICAL_FINAL_GATE_REJECTED";
            policyFlags.add("CLASSICAL_FINAL_GATE_REJECTED");
            routeReasons.add("Classical final gate rejected this candidate before LLM Judge.");
        }
        if ("PROMO_AD".equalsIgnoreCase(meaningTop) && !"REFERENCE".equalsIgnoreCase(recommendedRoute) && !"WARNING".equalsIgnoreCase(recommendedRoute)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "PROMO_NO_MATERIAL";
            policyFlags.add("PROMO_NON_REFERENCE_BLOCK");
            routeReasons.add("Promo content may survive as reference/warning only, not as guide/answer.");
        }
        if ("CLUSTER".equalsIgnoreCase(assemblyTop) && "GUIDE".equalsIgnoreCase(recommendedRoute) && !"ACTIONABLE".equalsIgnoreCase(usefulnessTop)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "MODEL_DISAGREEMENT_REVIEW";
            policyFlags.add("CLUSTER_GUIDE_NEEDS_ACTIONABLE_EVIDENCE");
            routeReasons.add("Cluster-level guide requires actionable evidence, not only broad topic signal.");
        }
        if (!"MATERIAL_CANDIDATE".equalsIgnoreCase(valueTop) && "MESSAGE".equalsIgnoreCase(targetType) && !"NO_MATERIAL".equalsIgnoreCase(recommendedRoute)) {
            blocked = true;
            recommendedRoute = "NO_MATERIAL";
            finalUiReason = "SIGNAL_ONLY_STATUS";
            policyFlags.add("MESSAGE_VALUE_BELOW_MATERIAL");
            routeReasons.add("Single-message route is suppressed because value level is below material candidate.");
        }

        double routeConfidence = calibrated.path("materialRoute").asDouble(0.0);
        double evidenceConfidence = calibrated.path("evidenceSufficiency").asDouble(0.0);
        boolean abstained = triage.path("abstained").asBoolean(false);
        boolean requiresJudge = !blocked
            && !abstained
            && !"NO_MATERIAL".equalsIgnoreCase(recommendedRoute)
            && routeConfidence >= 0.45
            && evidenceConfidence >= 0.35;

        ObjectNode reasonBundle = json.createObjectNode();
        reasonBundle.put("preprocessing", preprocessingTop);
        reasonBundle.put("meaning", meaningTop);
        reasonBundle.put("valueLevel", valueTop);
        reasonBundle.put("usefulnessKind", usefulnessTop);
        reasonBundle.put("evidenceSufficiency", evidenceTop);
        reasonBundle.put("assemblyStrategy", assemblyTop);
        reasonBundle.put("materialRoute", recommendedRoute);
        reasonBundle.put("uiReason", finalUiReason);
        reasonBundle.put("dedupeCluster", dedupeClusterTop);
        reasonBundle.put("llmJudge", llmJudgeTop);
        reasonBundle.put("requiresJudge", requiresJudge);
        reasonBundle.put("blocked", blocked);
        reasonBundle.put("routeConfidence", routeConfidence);
        reasonBundle.put("evidenceConfidence", evidenceConfidence);
        reasonBundle.set("shortlist", shortlist.deepCopy());
        reasonBundle.set("policyFlags", policyFlags.deepCopy());
        reasonBundle.set("stageScores", calibrated.isObject() ? calibrated.deepCopy() : json.createObjectNode());
        reasonBundle.set("heuristics", heuristics != null && heuristics.isObject() ? heuristics.deepCopy() : json.createObjectNode());
        reasonBundle.set("reasons", routeReasons);
        reasonBundle.put("promptHint", promptHint(recommendedRoute, shortlist, finalUiReason, policyFlags));

        return new RouteIntelligenceDecision(
            targetType,
            targetId,
            recommendedRoute,
            assemblyTop,
            finalUiReason,
            triage.path("confidenceBand").asText("LOW"),
            requiresJudge,
            blocked,
            shortlist,
            policyFlags,
            reasonBundle
        );
    }

    private ArrayNode topPredictions(JsonNode predictions, int limit) {
        ArrayNode result = json.createArrayNode();
        if (!predictions.isArray()) {
            return result;
        }
        List<JsonNode> sorted = new ArrayList<>();
        predictions.forEach(sorted::add);
        sorted.stream()
            .sorted(Comparator.comparingDouble((JsonNode node) -> node.path("probability").asDouble(0.0)).reversed())
            .limit(limit)
            .forEach(node -> {
                ObjectNode item = json.createObjectNode();
                item.put("label", node.path("label").asText("UNKNOWN"));
                item.put("probability", node.path("probability").asDouble(0.0));
                item.put("rank", node.path("rank").asInt(0));
                result.add(item);
            });
        return result;
    }

    private String topLabel(JsonNode predictions) {
        if (!predictions.isArray()) {
            return null;
        }
        String label = null;
        double best = -1.0;
        for (JsonNode node : predictions) {
            double probability = node.path("probability").asDouble(0.0);
            if (probability > best) {
                best = probability;
                label = node.path("label").asText(null);
            }
        }
        return label;
    }

    private String promptHint(String recommendedRoute, ArrayNode shortlist, String uiReason, ArrayNode policyFlags) {
        List<String> alternatives = new ArrayList<>();
        for (JsonNode node : shortlist) {
            String label = node.path("label").asText("");
            if (!label.isBlank() && !label.equalsIgnoreCase(recommendedRoute)) {
                alternatives.add(label);
            }
        }
        StringBuilder hint = new StringBuilder("Preferred route=").append(recommendedRoute).append("; uiReason=").append(uiReason);
        if (!alternatives.isEmpty()) {
            hint.append("; alternatives=").append(String.join(", ", alternatives));
        }
        if (!policyFlags.isEmpty()) {
            List<String> flags = new ArrayList<>();
            policyFlags.forEach(flag -> flags.add(flag.asText("")));
            hint.append("; policyFlags=").append(String.join(", ", flags));
        }
        return hint.toString();
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
