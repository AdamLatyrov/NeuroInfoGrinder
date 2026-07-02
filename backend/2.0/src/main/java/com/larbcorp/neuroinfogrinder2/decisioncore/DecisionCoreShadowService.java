package com.larbcorp.neuroinfogrinder2.decisioncore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DecisionCoreShadowService {
    private static final Logger log = LoggerFactory.getLogger(DecisionCoreShadowService.class);
    private static final Set<String> RISK_REASONS = Set.of(
        "ABUSE_OR_FRAUD",
        "RISK_SENSITIVE_MANUAL_ONLY",
        "RISK_SENSITIVE_MANUAL_REVIEW",
        "ACCESS_CIRCUMVENTION",
        "PROMO_ALONE"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PipelineSettingsService settings;

    public DecisionCoreShadowService(JdbcTemplate jdbc, ObjectMapper json, PipelineSettingsService settings) {
        this.jdbc = jdbc;
        this.json = json;
        this.settings = settings;
    }

    public boolean shadowEnabled() {
        return settings != null && settings.getInt("decisionCoreShadowEnabled", 0) == 1;
    }

    public boolean materialEligibilityGateShadowEnabled() {
        return settings != null && settings.getInt("materialEligibilityGateShadowEnabled", 0) == 1;
    }

    public boolean failOpen() {
        return settings == null || settings.getInt("decisionCoreFailOpen", 1) == 1;
    }

    public Long upsertMessageDecision(long runId, long datasetMessageId, JsonNode features, String normalizedText, String ruleDecision, boolean hardSignal) {
        if (!shadowEnabled()) return null;
        try {
            ObjectNode ruleOutputs = object(
                "ruleDecision", text(ruleDecision),
                "hardSignal", hardSignal,
                "textLength", normalizedText == null ? 0 : normalizedText.length()
            );
            if (features != null) ruleOutputs.set("features", features);
            // v2 MaterialEligibilityGate verdict (shadow only — does not change materialization).
            MaterialEligibilityGate.MessageVerdict gateVerdict = null;
            boolean gateOn = materialEligibilityGateShadowEnabled();
            if (gateOn) {
                int links = linkCount(features);
                // Pass hidden/caption URLs (from raw_json entities) into the gate so a technical
                // anchor only present as a caption URL (e.g. a GitHub repo behind a link button) is
                // detected, matching the offline v2 lab which parses raw_json entities.
                java.util.List<String> extraUrls = new java.util.ArrayList<>();
                if (features != null) {
                    for (JsonNode u : features.path("links")) if (u.isTextual()) extraUrls.add(u.asText());
                    for (JsonNode u : features.path("rawJsonUrls")) if (u.isTextual()) extraUrls.add(u.asText());
                    for (JsonNode u : features.path("hiddenUrls")) if (u.isTextual()) extraUrls.add(u.asText());
                }
                gateVerdict = MaterialEligibilityGate.evaluateMessage(normalizedText, normalizedText, links, null, null, null, extraUrls);
                ObjectNode gate = object(
                    "route", gateVerdict.route(),
                    "reason", gateVerdict.reason(),
                    "tier", gateVerdict.tier(),
                    "eligible", gateVerdict.eligible(),
                    "technicalEntity", gateVerdict.technicalEntity(),
                    "strongEntities", jsonArray(gateVerdict.strongEntities())
                );
                ruleOutputs.set("materialEligibilityGate", gate);
            }
            String finalRoute = gateOn && gateVerdict != null ? gateVerdict.route() : finalRouteForRule(ruleDecision, hardSignal, features);
            String materialElig = gateOn && gateVerdict != null
                ? (gateVerdict.eligible() ? "ELIGIBLE" : "INELIGIBLE")
                : materialEligibility(ruleDecision, features);
            String reasonCode = gateOn && gateVerdict != null ? gateVerdict.reason() : reasonForRule(ruleDecision, features);
            return jdbc.queryForObject("""
                INSERT INTO semantic_decision_objects (
                    trace_version, decision_version, mode, object_type, object_id, run_id, dataset_message_id,
                    source_message_ids, value_score, readiness_score, context_need_score, evidence_score, risk_score,
                    final_route, context_eligibility, material_eligibility, signal_eligibility, enrichment_eligibility,
                    llm_eligibility, manual_review_eligibility, reason_codes, rule_outputs
                )
                VALUES (?, ?, 'SHADOW', 'message', ?, ?, ?, ARRAY[?]::bigint[], ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (decision_version, object_type, (COALESCE(object_id, -1)), (COALESCE(run_id, -1)), (COALESCE(cluster_level, '')), (COALESCE(cluster_id, -1)))
                DO UPDATE SET
                    source_message_ids = EXCLUDED.source_message_ids,
                    value_score = EXCLUDED.value_score,
                    readiness_score = EXCLUDED.readiness_score,
                    context_need_score = EXCLUDED.context_need_score,
                    evidence_score = EXCLUDED.evidence_score,
                    risk_score = EXCLUDED.risk_score,
                    final_route = EXCLUDED.final_route,
                    context_eligibility = EXCLUDED.context_eligibility,
                    material_eligibility = EXCLUDED.material_eligibility,
                    signal_eligibility = EXCLUDED.signal_eligibility,
                    enrichment_eligibility = EXCLUDED.enrichment_eligibility,
                    llm_eligibility = EXCLUDED.llm_eligibility,
                    manual_review_eligibility = EXCLUDED.manual_review_eligibility,
                    reason_codes = EXCLUDED.reason_codes,
                    rule_outputs = EXCLUDED.rule_outputs,
                    updated_at = now()
                RETURNING id
                """,
                Long.class,
                SemanticDecisionCoreObject.TRACE_VERSION,
                SemanticDecisionCoreObject.DECISION_VERSION,
                datasetMessageId,
                runId,
                datasetMessageId,
                datasetMessageId,
                hardSignal ? new BigDecimal("0.30") : BigDecimal.ZERO,
                "CANDIDATE".equals(ruleDecision) ? new BigDecimal("0.30") : new BigDecimal("0.05"),
                "ACCUMULATE".equals(ruleDecision) ? new BigDecimal("0.70") : new BigDecimal("0.20"),
                hardSignal ? new BigDecimal("0.40") : new BigDecimal("0.05"),
                riskScore(features),
                finalRoute,
                contextEligibility(ruleDecision, features),
                materialElig,
                hardSignal ? "ELIGIBLE" : "PENDING",
                linkCount(features) > 0 ? "ELIGIBLE" : "UNKNOWN",
                "INELIGIBLE",
                riskScore(features).compareTo(BigDecimal.ZERO) > 0 ? "PENDING" : "UNKNOWN",
                write(array(reasonCode)),
                write(ruleOutputs)
            );
        } catch (RuntimeException ex) {
            return handleFailure("upsertMessageDecision", ex);
        }
    }

    public void observe(Long decisionObjectId, long runId, String stageName, String observerName, String observationType, JsonNode payload, Collection<String> reasons, Double confidence) {
        if (!shadowEnabled() || decisionObjectId == null) return;
        try {
            jdbc.update("""
                INSERT INTO semantic_decision_observations (decision_object_id, run_id, stage_name, observer_name, observation_type, confidence, payload_json, reason_codes)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, decisionObjectId, runId, stageName, observerName, observationType, confidence, write(payload == null ? object() : payload), write(array(reasons)));
        } catch (RuntimeException ex) {
            handleFailure("observe", ex);
        }
    }

    public Long messageDecisionObjectId(long runId, long datasetMessageId) {
        if (!shadowEnabled()) return null;
        try {
            return jdbc.query("""
                SELECT id FROM semantic_decision_objects
                WHERE decision_version = ? AND object_type = 'message' AND object_id = ? AND run_id = ?
                ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, SemanticDecisionCoreObject.DECISION_VERSION, datasetMessageId, runId);
        } catch (RuntimeException ex) {
            return handleFailure("messageDecisionObjectId", ex);
        }
    }

    public Long upsertClusterDecision(long runId, String level, long clusterId, Collection<Long> members, String artifactType, BigDecimal score, JsonNode report) {
        if (!shadowEnabled()) return null;
        try {
            List<Long> sources = members == null ? List.of() : List.copyOf(members);
            return jdbc.queryForObject("""
                INSERT INTO semantic_decision_objects (
                    trace_version, decision_version, mode, object_type, object_id, run_id, cluster_level, cluster_id,
                    source_message_ids, value_score, readiness_score, evidence_score, material_route_candidates,
                    final_route, required_artifact_type, cluster_identity, context_eligibility, material_eligibility,
                    signal_eligibility, enrichment_eligibility, llm_eligibility, manual_review_eligibility,
                    reason_codes, rule_outputs
                )
                VALUES (?, ?, 'SHADOW', 'cluster', ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'ROUTE_TO_CLUSTER_CANDIDATE', ?, ?,
                    'ELIGIBLE', 'PENDING', 'PENDING', 'UNKNOWN', 'PENDING', 'UNKNOWN', ?::jsonb, ?::jsonb)
                ON CONFLICT (decision_version, object_type, (COALESCE(object_id, -1)), (COALESCE(run_id, -1)), (COALESCE(cluster_level, '')), (COALESCE(cluster_id, -1)))
                DO UPDATE SET source_message_ids = EXCLUDED.source_message_ids, value_score = EXCLUDED.value_score,
                    readiness_score = EXCLUDED.readiness_score, evidence_score = EXCLUDED.evidence_score,
                    material_route_candidates = EXCLUDED.material_route_candidates, required_artifact_type = EXCLUDED.required_artifact_type,
                    cluster_identity = EXCLUDED.cluster_identity, rule_outputs = EXCLUDED.rule_outputs, updated_at = now()
                RETURNING id
                """, Long.class,
                SemanticDecisionCoreObject.TRACE_VERSION, SemanticDecisionCoreObject.DECISION_VERSION, clusterId, runId, level, clusterId,
                toLongArray(sources), score == null ? BigDecimal.ZERO : score, score == null ? BigDecimal.ZERO : score,
                sources.size() >= 2 ? new BigDecimal("0.50") : new BigDecimal("0.10"), write(array(artifactType)), artifactType,
                clusterIdentity(level, artifactType, sources), write(array("CLUSTER_CANDIDATE_SHADOW")), write(report == null ? object() : report));
        } catch (RuntimeException ex) {
            return handleFailure("upsertClusterDecision", ex);
        }
    }

    public Long upsertDiscussionDecision(long runId, Long segmentId, Collection<Long> members, String proposedMaterialType, BigDecimal score, String decision, String rejectionReason, JsonNode signals) {
        if (!shadowEnabled() || segmentId == null) return null;
        try {
            List<Long> sources = members == null ? List.of() : List.copyOf(members);
            String finalRoute = rejectionReason == null ? "ROUTE_TO_DISCUSSION_ASSEMBLY" : "REJECT_FOR_MATERIAL_NOW";
            return jdbc.queryForObject("""
                INSERT INTO semantic_decision_objects (
                    trace_version, decision_version, mode, object_type, object_id, run_id, discussion_segment_id,
                    source_message_ids, value_score, readiness_score, evidence_score, material_route_candidates,
                    final_route, required_artifact_type, discussion_identity, context_eligibility, material_eligibility,
                    signal_eligibility, enrichment_eligibility, llm_eligibility, manual_review_eligibility,
                    reason_codes, rule_outputs
                )
                VALUES (?, ?, 'SHADOW', 'discussion_segment', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?,
                    'ELIGIBLE', ?, 'PENDING', 'UNKNOWN', ?, 'UNKNOWN', ?::jsonb, ?::jsonb)
                ON CONFLICT (decision_version, object_type, (COALESCE(object_id, -1)), (COALESCE(run_id, -1)), (COALESCE(cluster_level, '')), (COALESCE(cluster_id, -1)))
                DO UPDATE SET source_message_ids = EXCLUDED.source_message_ids, value_score = EXCLUDED.value_score,
                    readiness_score = EXCLUDED.readiness_score, evidence_score = EXCLUDED.evidence_score,
                    material_route_candidates = EXCLUDED.material_route_candidates, final_route = EXCLUDED.final_route,
                    required_artifact_type = EXCLUDED.required_artifact_type, discussion_identity = EXCLUDED.discussion_identity,
                    material_eligibility = EXCLUDED.material_eligibility, llm_eligibility = EXCLUDED.llm_eligibility,
                    reason_codes = EXCLUDED.reason_codes, rule_outputs = EXCLUDED.rule_outputs, updated_at = now()
                RETURNING id
                """, Long.class,
                SemanticDecisionCoreObject.TRACE_VERSION, SemanticDecisionCoreObject.DECISION_VERSION, segmentId, runId, segmentId,
                toLongArray(sources), score == null ? BigDecimal.ZERO : score, score == null ? BigDecimal.ZERO : score,
                sources.size() >= 2 ? new BigDecimal("0.60") : new BigDecimal("0.10"), write(array(proposedMaterialType)), finalRoute,
                proposedMaterialType, "discussion:" + segmentId, rejectionReason == null ? "PENDING" : "INELIGIBLE",
                rejectionReason == null ? "PENDING" : "INELIGIBLE", write(array(rejectionReason == null ? decision : rejectionReason)),
                write(object("decision", decision, "rejectionReason", rejectionReason, "signals", signals == null ? json.createArrayNode() : signals)));
        } catch (RuntimeException ex) {
            return handleFailure("upsertDiscussionDecision", ex);
        }
    }

    public void relationEdge(long runId, long sourceDatasetMessageId, long targetDatasetMessageId, String edgeType, BigDecimal weight, boolean negativeConstraint, JsonNode evidence) {
        if (!shadowEnabled()) return;
        try {
            jdbc.update("""
                INSERT INTO message_relation_edges (run_id, source_dataset_message_id, target_dataset_message_id, edge_type, weight, negative_constraint, evidence_json)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, runId, sourceDatasetMessageId, targetDatasetMessageId, edgeType, weight == null ? BigDecimal.ZERO : weight, negativeConstraint, write(evidence == null ? object() : evidence));
        } catch (RuntimeException ex) {
            handleFailure("relationEdge", ex);
        }
    }

    public void dedupeIdentity(String type, String value, BigDecimal confidence, Long anchorDecisionObjectId, Long anchorKnowledgeItemId, JsonNode metadata) {
        if (!shadowEnabled() || value == null || value.isBlank()) return;
        try {
            jdbc.update("""
                INSERT INTO dedupe_identities (identity_type, identity_value, confidence, anchor_decision_object_id, anchor_knowledge_item_id, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (identity_type, identity_value) DO UPDATE SET
                    confidence = GREATEST(dedupe_identities.confidence, EXCLUDED.confidence),
                    metadata_json = EXCLUDED.metadata_json,
                    updated_at = now()
                """, type, value, confidence == null ? BigDecimal.ONE : confidence, anchorDecisionObjectId, anchorKnowledgeItemId, write(metadata == null ? object() : metadata));
        } catch (RuntimeException ex) {
            handleFailure("dedupeIdentity", ex);
        }
    }

    public void retainContext(Long decisionObjectId, long runId, long datasetMessageId, String role, String reason, Duration ttl) {
        if (!shadowEnabled() || decisionObjectId == null) return;
        try {
            Timestamp expiresAt = ttl == null ? null : Timestamp.from(Instant.now().plus(ttl));
            jdbc.update("""
                INSERT INTO context_retained_nodes (decision_object_id, dataset_message_id, run_id, retention_reason, node_role, eligibility_json, expires_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, decisionObjectId, datasetMessageId, runId, reason, role, write(object("reason", reason)), expiresAt);
            ledger(decisionObjectId, runId, "message:" + datasetMessageId, "message:" + datasetMessageId,
                DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.CONTEXT_NODE_RETAINED,
                false, List.of(), null, null, null, null, DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT,
                null, null, List.of(reason), List.of(datasetMessageId), List.of(), object("nodeRole", role));
        } catch (RuntimeException ex) {
            handleFailure("retainContext", ex);
        }
    }

    public void ledger(Long decisionObjectId, long runId, String candidateGroupId, String candidateId,
                       DecisionCoreEnums.CandidateType candidateType, DecisionCoreEnums.LedgerEventType eventType,
                       boolean winner, List<String> competitors, String duplicateAnchorId, Long providerCallId,
                       BigDecimal rankScore, DecisionCoreEnums.FinalRoute routeBefore, DecisionCoreEnums.FinalRoute routeAfter,
                       String artifactTypeBefore, String artifactTypeAfter, Collection<String> reasonCodes,
                       Collection<Long> contextNodesRetained, Collection<Long> excludedMessageIds, JsonNode details) {
        if (!shadowEnabled()) return;
        try {
            jdbc.update("""
                INSERT INTO candidate_decision_ledger (
                    decision_object_id, run_id, candidate_group_id, candidate_id, candidate_type, event_type, event_status,
                    winner, competing_candidate_ids, duplicate_anchor_id, provider_call_id, rank_score, route_before, route_after,
                    artifact_type_before, artifact_type_after, reason_codes, context_nodes_retained, excluded_message_ids, details_json
                )
                VALUES (?, ?, ?, ?, ?, ?, 'RECORDED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb)
                """,
                decisionObjectId, runId, candidateGroupId, candidateId, dbCandidateType(candidateType), eventType.name(), winner,
                competitors == null ? new String[0] : competitors.toArray(String[]::new), duplicateAnchorId, providerCallId, rankScore,
                routeBefore == null ? null : routeBefore.name(), routeAfter == null ? null : routeAfter.name(), artifactTypeBefore, artifactTypeAfter,
                write(array(reasonCodes)), toLongArray(contextNodesRetained), toLongArray(excludedMessageIds), write(details == null ? object() : details));
        } catch (RuntimeException ex) {
            handleFailure("ledger", ex);
        }
    }

    public void recordSafety(Long decisionObjectId, long runId, String safetyClass, boolean hardBlock, boolean manualReview, boolean riskSignal, boolean warningAllowed, BigDecimal riskScore, Collection<String> riskFlags, JsonNode observations) {
        if (!shadowEnabled() || decisionObjectId == null) return;
        try {
            jdbc.update("""
                INSERT INTO safety_gate_results (decision_object_id, run_id, safety_class, hard_block, manual_review, risk_signal, warning_material_allowed, risk_score, risk_flags, observations_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, decisionObjectId, runId, safetyClass, hardBlock, manualReview, riskSignal, warningAllowed, riskScore, write(array(riskFlags)), write(observations == null ? object() : observations));
        } catch (RuntimeException ex) {
            handleFailure("recordSafety", ex);
        }
    }

    public void rankCandidate(Long decisionObjectId, long runId, String candidateGroupId, String candidateType, int rankPosition, BigDecimal rankScore, JsonNode components, boolean selectedForLlm, Collection<String> reasons) {
        if (!shadowEnabled() || decisionObjectId == null) return;
        try {
            jdbc.update("""
                INSERT INTO candidate_rankings (
                    run_id, decision_object_id, candidate_group_id, candidate_type, rank_position, rank_score,
                    value_score, evidence_score, coherence_score, source_quality_score, risk_penalty, promo_penalty,
                    duplicate_penalty, freshness_score, novelty_score, actionability_score, material_readiness_score,
                    already_covered_penalty, contradiction_penalty, selected_for_llm, reason_codes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, runId, decisionObjectId, candidateGroupId, candidateType, rankPosition, rankScore,
                decimal(components, "valueScore"), decimal(components, "evidenceScore"), decimal(components, "coherenceScore"), decimal(components, "sourceQualityScore"),
                decimal(components, "riskPenalty"), decimal(components, "promoPenalty"), decimal(components, "duplicatePenalty"), decimal(components, "freshnessScore"),
                decimal(components, "noveltyScore"), decimal(components, "actionabilityScore"), decimal(components, "materialReadinessScore"),
                decimal(components, "alreadyCoveredPenalty"), decimal(components, "contradictionPenalty"), selectedForLlm, write(array(reasons)));
        } catch (RuntimeException ex) {
            handleFailure("rankCandidate", ex);
        }
    }

    public void enqueueLink(Long decisionObjectId, long datasetMessageId, String url, int priority) {
        if (!shadowEnabled() || decisionObjectId == null || url == null || url.isBlank()) return;
        try {
            jdbc.update("""
                INSERT INTO link_enrichment_jobs (decision_object_id, dataset_message_id, url, canonical_url, priority)
                VALUES (?, ?, ?, ?, ?)
                """, decisionObjectId, datasetMessageId, url, canonicalUrl(url), priority);
        } catch (RuntimeException ex) {
            handleFailure("enqueueLink", ex);
        }
    }

    public ObjectNode object(Object... fields) {
        ObjectNode node = json.createObjectNode();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            String key = String.valueOf(fields[i]);
            Object value = fields[i + 1];
            if (value == null) node.putNull(key);
            else if (value instanceof Boolean b) node.put(key, b);
            else if (value instanceof Integer n) node.put(key, n);
            else if (value instanceof Long n) node.put(key, n);
            else if (value instanceof Double n) node.put(key, n);
            else if (value instanceof BigDecimal n) node.put(key, n);
            else if (value instanceof JsonNode j) node.set(key, j);
            else node.put(key, String.valueOf(value));
        }
        return node;
    }

    private Long handleFailure(String operation, RuntimeException ex) {
        if (!failOpen()) throw ex;
        log.debug("Decision core shadow write skipped operation={} error={}", operation, ex.getMessage());
        return null;
    }

    private String finalRouteForRule(String ruleDecision, boolean hardSignal, JsonNode features) {
        if (linkCount(features) > 0) return DecisionCoreEnums.FinalRoute.ROUTE_TO_LINK_ENRICHMENT.name();
        if ("SUPPRESS".equals(ruleDecision) && !hardSignal) return DecisionCoreEnums.FinalRoute.REJECT_FOR_MATERIAL_NOW.name();
        if ("ACCUMULATE".equals(ruleDecision)) return DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT.name();
        return DecisionCoreEnums.FinalRoute.NO_DECISION.name();
    }

    private String contextEligibility(String ruleDecision, JsonNode features) {
        if (linkCount(features) > 0 || "ACCUMULATE".equals(ruleDecision)) return "ELIGIBLE";
        if ("SUPPRESS".equals(ruleDecision)) return "PENDING";
        return "ELIGIBLE";
    }

    private String materialEligibility(String ruleDecision, JsonNode features) {
        if (linkCount(features) > 0) return "INELIGIBLE";
        if ("CANDIDATE".equals(ruleDecision)) return "PENDING";
        return "INELIGIBLE";
    }

    private String reasonForRule(String ruleDecision, JsonNode features) {
        if (linkCount(features) > 0) return "LINK_NEEDS_ENRICHMENT";
        if ("SUPPRESS".equals(ruleDecision)) return "RULE_SUPPRESSED_NOT_MATERIAL";
        if ("ACCUMULATE".equals(ruleDecision)) return "RETAIN_FOR_CONTEXT";
        return "RULE_CANDIDATE_OBSERVED";
    }

    private BigDecimal riskScore(JsonNode features) {
        if (features == null) return BigDecimal.ZERO;
        String text = features.toString().toLowerCase(Locale.ROOT);
        return RISK_REASONS.stream().anyMatch(text::contains) ? new BigDecimal("0.80") : BigDecimal.ZERO;
    }

    private int linkCount(JsonNode features) {
        return features == null ? 0 : features.path("linkCount").asInt(0) + features.path("hiddenLinkCount").asInt(0);
    }

    private ArrayNode array(Collection<?> values) {
        ArrayNode array = json.createArrayNode();
        if (values != null) values.forEach(value -> array.add(String.valueOf(value)));
        return array;
    }

    private ArrayNode array(String value) {
        ArrayNode array = json.createArrayNode();
        if (value != null && !value.isBlank()) array.add(value);
        return array;
    }

    private String write(JsonNode node) {
        try {
            return json.writeValueAsString(node == null ? object() : node);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String dbCandidateType(DecisionCoreEnums.CandidateType type) {
        return type == null ? "material_candidate" : type.name().toLowerCase(Locale.ROOT);
    }

    private Long[] toLongArray(Collection<Long> values) {
        return values == null ? new Long[0] : values.toArray(Long[]::new);
    }

    public ArrayNode jsonArray(Collection<?> values) {
        return array(values);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.path(field).isNumber()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(node.path(field).asDouble());
    }

    private String canonicalUrl(String url) {
        String value = url.trim();
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        return value;
    }

    private String clusterIdentity(String level, String artifactType, List<Long> sources) {
        return "cluster:" + level + ":" + (artifactType == null ? "UNKNOWN" : artifactType) + ":" + sources.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }
}
