package com.larbcorp.neuroinfogrinder2.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PipelineSettingsService {
    private static final Logger log = LoggerFactory.getLogger(PipelineSettingsService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PipelineSettingsService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostConstruct
    void ensureDefaults() {
        upsertSetting("classificationConfidenceThreshold", "0.65", "DOUBLE",
            "Minimum confidence from the local bootstrap classifier before we trust the label.",
            "0.0", "1.0", "0.65", "threshold");
        upsertSetting("noiseSuppressThreshold", "0.75", "DOUBLE",
            "Confidence threshold for suppressing obvious noise and chatter.",
            "0.0", "1.0", "0.75", "threshold");
        upsertSetting("clusterCandidateThreshold", "0.55", "DOUBLE",
            "Minimum local score for a cluster candidate before it can move forward.",
            "0.0", "1.0", "0.55", "threshold");
        upsertSetting("singleMessageCandidateThreshold", "0.55", "DOUBLE",
            "Minimum score for a single message before it becomes an LLM candidate.",
            "0.0", "1.0", "0.55", "threshold");
        upsertSetting("directMaterialReadyThreshold", "0.72", "DOUBLE",
            "Score where a single message is strong enough to be treated as direct material ready.",
            "0.0", "1.0", "0.72", "threshold");
        upsertSetting("semanticSimilarityThreshold", "0.62", "DOUBLE",
            "Cosine similarity threshold for semantic grouping and neighbor search.",
            "0.0", "1.0", "0.62", "threshold");
        upsertSetting("minClusterScoreForJudge", "0.65", "DOUBLE",
            "Minimum effective cluster score before LLM Judge.",
            "0.0", "1.0", "0.65", "threshold");
        upsertSetting("minJudgeConfidenceForGeneration", "0.72", "DOUBLE",
            "Minimum accepted LLM Judge confidence before generation.",
            "0.0", "1.0", "0.72", "threshold");
        upsertSetting("minMicroclusterSize", "2", "INTEGER",
            "Minimum number of messages inside a microcluster.",
            "1", "100", "2", "threshold");
        upsertSetting("minMacroclusterSize", "2", "INTEGER",
            "Minimum number of messages inside a macrocluster before LLM routing.",
            "1", "100", "2", "threshold");
        upsertSetting("clusterGenerationThreshold", "0.60", "DOUBLE",
            "Final cluster score required before the cluster can enter LLM judge.",
            "0.0", "1.0", "0.60", "threshold");
        upsertSetting("maxNoiseRatio", "0.35", "DOUBLE",
            "Maximum allowed share of noise-labelled messages inside a cluster.",
            "0.0", "1.0", "0.35", "threshold");
        upsertSetting("minSingleMessageTextLength", "500", "INTEGER",
            "Minimum text length for a serious single-message material candidate.",
            "100", "5000", "500", "threshold");
        upsertSetting("maxProviderCallsPerRun", "30", "INTEGER",
            "Upper bound for provider calls during a single replay or live run.",
            "1", "500", "30", "budget");
        upsertSetting("maxCostUsdPerRun", "2.0", "DOUBLE",
            "Maximum estimated provider spend allowed for a single run.",
            "0.1", "50.0", "2.0", "budget");
        upsertSetting("discussionSegmentGenerationEnabled", "0", "INTEGER",
            "Enables discussion-segment material generation only when controlled mode gates also pass.",
            "0", "1", "0", "discussion_segment");
        upsertSetting("discussionSegmentGenerationMode", "OFF", "STRING",
            "Discussion-segment generation mode. Only CONTROLLED is allowed for automatic generation.",
            null, null, "OFF", "discussion_segment");
        upsertSetting("discussionSegmentMaxMaterialsPerDay", "3", "INTEGER",
            "Maximum DISCUSSION_SEGMENT DRAFT materials created per UTC day in controlled mode.",
            "0", "20", "3", "discussion_segment");
        upsertSetting("discussionSegmentMaxMaterialsPerChatTopicPerDay", "1", "INTEGER",
            "Maximum DISCUSSION_SEGMENT DRAFT materials per chat/topic/thread per UTC day in controlled mode.",
            "0", "10", "1", "discussion_segment");
        upsertSetting("discussionSegmentRequireLlmAccepted", "1", "INTEGER",
            "Requires accepted DISCUSSION_SEGMENT_JUDGE decision before generation.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentDraftOnly", "1", "INTEGER",
            "Forces generated discussion-segment materials to remain DRAFT.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentSkipRiskSensitive", "1", "INTEGER",
            "Skips automatic generation for health, security, legal, financial, employment, or safety-sensitive discussion segments.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentFreshOnly", "1", "INTEGER",
            "Allows only discussion segments ending after discussionSegmentControlledEnableTime.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentStopOnProviderError", "1", "INTEGER",
            "Stops controlled generation loop on judge provider errors.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentStopOnGenerationError", "1", "INTEGER",
            "Stops controlled generation loop on generation provider errors.",
            "0", "1", "1", "discussion_segment");
        upsertSetting("discussionSegmentControlledEnableTime", "", "STRING",
            "UTC ISO timestamp from which fresh/live discussion-segment generation may begin.",
            null, null, "", "discussion_segment");
        upsertSetting("semanticTraceShadowEnabled", "1", "INTEGER",
            "Writes conservative SemanticDecisionObject shadow traces without changing routing or material generation.",
            "0", "1", "1", "semantic_trace");
        upsertSetting("semanticTraceSoftFail", "1", "INTEGER",
            "Keeps the replay/live pipeline running even when semantic shadow trace persistence fails.",
            "0", "1", "1", "semantic_trace");
        upsertSetting("classicalMlShadowEnabled", "1", "INTEGER",
            "Runs the classical ML stage scaffold in shadow mode and persists feature/stage traces without changing final materialization authority.",
            "0", "1", "1", "classical_ml");
        upsertSetting("classicalMlRoutingEnabled", "0", "INTEGER",
            "Allows validated classical ML decisions to affect Judge routing. Keep disabled until training governance is ready.",
            "0", "1", "0", "classical_ml");
        upsertSetting("classicalMlSoftFail", "1", "INTEGER",
            "Keeps the pipeline running when internal classical ML inference contracts fail.",
            "0", "1", "1", "classical_ml");
        upsertSetting("decisionCoreShadowEnabled", "0", "INTEGER",
            "Writes normalized decision-core shadow objects/observations/ledger rows without changing existing routing or material generation.",
            "0", "1", "0", "decision_core");
        upsertSetting("materialEligibilityGateShadowEnabled", "0", "INTEGER",
            "Computes the v2 MaterialEligibilityGate verdict (route, tier, technical-entity, evidence sufficiency) for every message/cluster in shadow mode and writes it to semantic_decision_objects. Does NOT change materialization or routing; the legacy MessageUsefulnessClassifier + score thresholds remain authoritative. Enable decisionCoreShadowEnabled too.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreFailOpen", "1", "INTEGER",
            "Keeps the replay/live pipeline running if decision-core shadow persistence fails.",
            "0", "1", "1", "decision_core");
        upsertSetting("decisionCoreRankingShadowEnabled", "0", "INTEGER",
            "Computes candidate rankings in shadow mode without controlling LLM selection.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreGraphShadowEnabled", "0", "INTEGER",
            "Computes graph discussion observations in shadow mode without replacing sliding-window discussion segments.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreLinkEnrichmentEnabled", "0", "INTEGER",
            "Enables link enrichment jobs/results for decision-core shadow processing; material routing remains unchanged unless controlled routing is separately enabled.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreControlledRoutingEnabled", "0", "INTEGER",
            "Allows decision-core routes to affect bounded allowlisted runs. Keep disabled until shadow validation passes.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreLlmRankedSelectionEnabled", "0", "INTEGER",
            "Uses CandidateRanker top-N as LLM input for allowlisted scopes. Keep disabled until ranking validation passes.",
            "0", "1", "0", "decision_core");
        upsertSetting("decisionCoreMode", "SHADOW", "STRING",
            "Decision core mode. Valid operational values are SHADOW, CONTROLLED, and ACTIVE; ACTIVE must not be used before quality gates pass.",
            null, null, "SHADOW", "decision_core");
        upsertSetting("preferDiscussionOverSingleMessage", "1", "INTEGER",
            "Skips single-message LLM/materialization for messages already covered by a discussion-segment candidate, so multi-message evidence is preferred over one-message drafts.",
            "0", "1", "1", "ranking");
        upsertSetting("allowOtherSingleMessageMaterials", "0", "INTEGER",
            "Allows OTHER artifact materials from a single source message. Keep disabled to prevent rules/onboarding/weak notes from becoming drafts.",
            "0", "1", "0", "ranking");
    }

    public record SettingDto(
        long id, String settingKey, String settingValue, String settingType,
        String description, String safeMin, String safeMax, String defaultValue,
        String category, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public Map<String, Object> getAllSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", listAll());
        result.put("categories", categories());
        return result;
    }

    public List<SettingDto> listAll() {
        return jdbc.query("SELECT * FROM pipeline_settings ORDER BY category, setting_key", this::mapSetting);
    }

    public List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM pipeline_settings ORDER BY category", String.class);
    }

    public SettingDto getSetting(String key) {
        return jdbc.queryForObject("SELECT * FROM pipeline_settings WHERE setting_key = ?", this::mapSetting, key);
    }

    public SettingDto updateSetting(String key, String value, String updatedBy) {
        jdbc.update("UPDATE pipeline_settings SET setting_value = ?, updated_at = now() WHERE setting_key = ?", value, key);
        log.info("[SETTING_UPDATED] key={} value={} by={}", key, value, updatedBy);
        return getSetting(key);
    }

    public SettingDto resetSetting(String key) {
        SettingDto s = getSetting(key);
        if (s.defaultValue() != null) {
            jdbc.update("UPDATE pipeline_settings SET setting_value = ?, updated_at = now() WHERE setting_key = ?", s.defaultValue(), key);
        }
        return getSetting(key);
    }

    public List<Map<String, Object>> getThresholds() {
        List<Map<String, Object>> result = new ArrayList<>();
        jdbc.query("SELECT * FROM pipeline_settings WHERE category IN ('threshold', 'budget') ORDER BY category, setting_key", (RowCallbackHandler) rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("key", rs.getString("setting_key"));
            row.put("value", rs.getString("setting_value"));
            row.put("type", rs.getString("setting_type"));
            row.put("description", rs.getString("description"));
            row.put("safeMin", rs.getString("safe_min"));
            row.put("safeMax", rs.getString("safe_max"));
            row.put("defaultValue", rs.getString("default_value"));
            result.add(row);
        });
        return result;
    }

    public double getDouble(String key, double fallback) {
        try {
            String val = jdbc.queryForObject("SELECT setting_value FROM pipeline_settings WHERE setting_key = ?", String.class, key);
            return val != null ? Double.parseDouble(val) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback) {
        try {
            String val = jdbc.queryForObject("SELECT setting_value FROM pipeline_settings WHERE setting_key = ?", String.class, key);
            return val != null ? Integer.parseInt(val) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public String getString(String key, String fallback) {
        try {
            String val = jdbc.queryForObject("SELECT setting_value FROM pipeline_settings WHERE setting_key = ?", String.class, key);
            return val != null ? val : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public ObjectNode getAllAsObjectNode() {
        ObjectNode node = json.createObjectNode();
        jdbc.query("SELECT setting_key, setting_value, setting_type FROM pipeline_settings", (RowCallbackHandler) rs -> {
            String k = rs.getString("setting_key");
            String v = rs.getString("setting_value");
            String t = rs.getString("setting_type");
            if ("INTEGER".equals(t)) node.put(k, v != null ? Integer.parseInt(v) : 0);
            else if ("DOUBLE".equals(t)) node.put(k, v != null ? Double.parseDouble(v) : 0.0);
            else node.put(k, v != null ? v : "");
        });
        return node;
    }

    private SettingDto mapSetting(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SettingDto(
            rs.getLong("id"), rs.getString("setting_key"), rs.getString("setting_value"),
            rs.getString("setting_type"), rs.getString("description"),
            rs.getString("safe_min"), rs.getString("safe_max"), rs.getString("default_value"),
            rs.getString("category"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private void upsertSetting(
        String key,
        String value,
        String type,
        String description,
        String safeMin,
        String safeMax,
        String defaultValue,
        String category
    ) {
        jdbc.update("""
            INSERT INTO pipeline_settings (setting_key, setting_value, setting_type, description, safe_min, safe_max, default_value, category)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (setting_key) DO UPDATE SET
                description = EXCLUDED.description,
                safe_min = EXCLUDED.safe_min,
                safe_max = EXCLUDED.safe_max,
                default_value = EXCLUDED.default_value,
                category = EXCLUDED.category
            """,
            key, value, type, description, safeMin, safeMax, defaultValue, category
        );
    }
}
