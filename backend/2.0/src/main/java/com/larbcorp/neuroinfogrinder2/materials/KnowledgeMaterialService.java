package com.larbcorp.neuroinfogrinder2.materials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeMaterialService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public KnowledgeMaterialService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> list(String status, String contentType, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String statusFilter = normalizeStatus(status);
        String contentFilter = normalizeContentType(contentType);
        List<Object> args = new ArrayList<>();
        String where = materialWhere(statusFilter, contentFilter, args);
        long total = jdbc.queryForObject("SELECT count(*) FROM knowledge_items ki" + where, Long.class, args.toArray());
        Map<String, Object> counts = counts(where, args);
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add(safePage * safeSize);
        List<Map<String, Object>> content = jdbc.query("""
                SELECT ki.*, COALESCE(src.source_count, 0) AS source_count
                FROM knowledge_items ki
                LEFT JOIN (
                    SELECT knowledge_item_id, count(*) AS source_count
                    FROM knowledge_item_sources
                    GROUP BY knowledge_item_id
                ) src ON src.knowledge_item_id = ki.id
                """ + where + " " + """
                ORDER BY ki.created_at DESC, ki.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> summary(rs), listArgs.toArray());
        return Map.of(
                "content", content,
                "totalElements", total,
                "totalPages", (long) Math.ceil(total / (double) safeSize),
                "counts", counts,
                "size", safeSize,
                "number", safePage,
                "first", safePage == 0,
                "last", (safePage + 1L) * safeSize >= total,
                "empty", content.isEmpty()
        );
    }

    public Map<String, Object> detail(long id) {
        return jdbc.query("""
                SELECT ki.*, COALESCE(src.source_count, 0) AS source_count
                FROM knowledge_items ki
                LEFT JOIN (
                    SELECT knowledge_item_id, count(*) AS source_count
                    FROM knowledge_item_sources
                    GROUP BY knowledge_item_id
                ) src ON src.knowledge_item_id = ki.id
                WHERE ki.id = ? AND ki.deleted_at IS NULL
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Material not found: " + id);
            Map<String, Object> row = summary(rs);
            row.put("content", contentText(rs));
            row.put("contentMarkdown", contentText(rs));
            List<Map<String, Object>> sourceMessages = sources(id);
            long runId = rs.getLong("run_id");
            row.put("sourceMessages", sourceMessages);
            row.put("llmRequest", llmRequest(runId));
            row.put("providerCalls", providerCalls(runId));
            row.put("traceStages", traceStages(sourceMessages));
            row.put("run", run(runId));
            String candidateType = coalesce(rs.getString("source_cluster_type"), "UNKNOWN");
            row.put("candidate", Map.of("type", candidateType));
            row.put("candidateType", candidateType);
            row.put("materialId", id);
            row.put("type", row.get("contentType"));
            row.put("quality", row.get("contentQualityScore"));
            row.put("clusterId", rs.getObject("cluster_id"));
            if ("DISCUSSION_SEGMENT".equals(candidateType)) {
                Map<String, Object> segment = discussionSegment(rs.getLong("source_cluster_id"));
                row.put("segmentId", segment.get("id"));
                row.put("segmentScore", segment.get("combinedScore"));
                row.put("segmentDecision", segment.get("decision"));
                row.put("segmentSignals", segment.get("signals"));
                row.put("segmentSuppressionReasons", segment.get("suppressionReasons"));
                row.put("segmentTimeWindow", Map.of("start", segment.get("startMessageDate"), "end", segment.get("endMessageDate")));
                row.put("generationSkipReason", segment.get("rejectionReason"));
            }
            row.put("howBuiltSteps", howBuiltSteps(row, sourceMessages));
            row.put("relatedGuideIds", List.of());
            row.put("possibleDuplicateIds", List.of());
            return row;
        }, id);
    }

    public void softDelete(long id, long userId) {
        int updated = jdbc.update("""
                UPDATE knowledge_items
                SET deleted_at = now(), deleted_by = ?, deleted_reason = 'USER_DELETED', status = 'DELETED'
                WHERE id = ? AND deleted_at IS NULL
                """, userId, id);
        if (updated == 0) {
            throw new IllegalArgumentException("Material not found: " + id);
        }
    }

    private Map<String, Object> summary(ResultSet rs) throws SQLException {
        String artifactType = normalizeArtifact(rs.getString("artifact_type"), rs.getString("item_type"));
        ObjectNode body = parse(rs.getString("body_json"));
        String title = coalesce(rs.getString("title"), body.path("title").asText(null), body.path("recommendedTitle").asText(null));
        String summary = coalesce(rs.getString("summary"), body.path("summary").asText(null));
        // Extract strong entities (models/tools/apis/domains) from title + body text so the materials
        // page can filter by entity (e.g. "show materials about Claude").
        String entityText = (title == null ? "" : title + " ") + (summary == null ? "" : summary + " ") + body.path("text").asText("");
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("entities", com.larbcorp.neuroinfogrinder2.decisioncore.MaterialEligibilityGate.strongEntities(entityText));
        row.put("title", title);
        row.put("groupId", null);
        row.put("groupTitle", null);
        row.put("rootMessageId", null);
        row.put("contentType", artifactType);
        row.put("contentSubtype", rs.getString("item_type"));
        row.put("topicLabel", title);
        row.put("topicSummary", summary);
        row.put("contentTitle", title);
        row.put("contentSummary", summary);
        row.put("normalizedTopicKey", null);
        row.put("contentQualityScore", score(rs, "knowledge_value_score"));
        row.put("importanceScore", score(rs, "usefulness_score"));
        row.put("actionabilityScore", score(rs, "publishability_score"));
        row.put("noveltyScore", null);
        row.put("evidenceScore", null);
        row.put("riskScore", null);
        row.put("confidenceScore", score(rs, "confidence"));
        row.put("noiseScore", null);
        row.put("routingReason", "knowledge_items");
        row.put("safetyCategory", null);
        row.put("publicationKind", rs.getString("source_cluster_type"));
        row.put("providerId", null);
        row.put("model", null);
        row.put("classifierId", null);
        row.put("promptId", null);
        row.put("promptVersion", null);
        row.put("status", rs.getString("status"));
        row.put("duplicateOfId", null);
        row.put("duplicateScore", null);
        row.put("confidence", rs.getBigDecimal("confidence"));
        row.put("usefulnessScore", score(rs, "usefulness_score"));
        row.put("totalTokens", null);
        row.put("estimatedCostUsd", null);
        row.put("tags", List.of());
        row.put("generationError", null);
        row.put("sourceCount", rs.getLong("source_count"));
        row.put("publicationStatus", "NOT_SENT");
        row.put("publishedAt", null);
        row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
        return row;
    }

    private List<Map<String, Object>> sources(long id) {
        return jdbc.query("""
                SELECT dm.id AS dataset_message_id, dm.account_id, dm.telegram_chat_id, dm.telegram_message_id,
                       dm.text AS dataset_text, dm.caption AS dataset_caption, dm.message_date AS dataset_message_date,
                       rm.id AS raw_message_id, rm.sender_name, rm.sender_username, rm.reply_to_message_id, rm.message_thread_id,
                       rm.topic_title, rm.text AS raw_text, rm.caption AS raw_caption, rm.content_type, rm.raw_json::text AS raw_json,
                       rm.message_date AS raw_message_date, rm.chat_title, c.username AS chat_username, c.type AS chat_type, c.tdlib_chat_type,
                       COALESCE(dss.order_index, kis.id - first_value(kis.id) OVER (PARTITION BY kis.knowledge_item_id ORDER BY kis.id)) AS order_index,
                       dss.replay_run_message_id, COALESCE(dss.role, kis.source_role, 'unknown') AS source_role
                FROM knowledge_item_sources kis
                JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id
                JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                LEFT JOIN telegram_chats c ON c.account_id = dm.account_id AND c.telegram_chat_id = dm.telegram_chat_id
                LEFT JOIN discussion_segment_sources dss ON ki.source_cluster_type = 'DISCUSSION_SEGMENT'
                    AND dss.discussion_segment_id = ki.source_cluster_id
                    AND dss.dataset_message_id = dm.id
                WHERE kis.knowledge_item_id = ?
                ORDER BY COALESCE(dss.order_index, kis.id), kis.id
                """, (rs, rowNum) -> {
            Long rawId = nullableLong(rs, "raw_message_id");
            Long telegramChatId = nullableLong(rs, "telegram_chat_id");
            Long telegramMessageId = nullableLong(rs, "telegram_message_id");
            String text = sourceText(rs);
            String textReason = sourceTextReason(rs, text);
            String username = rs.getString("chat_username");
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("orderIndex", rs.getInt("order_index"));
            row.put("groupId", null);
            row.put("rawId", rawId);
            row.put("datasetMessageId", rs.getLong("dataset_message_id"));
            row.put("replayRunMessageId", nullableLong(rs, "replay_run_message_id"));
            row.put("telegramChatId", telegramChatId);
            row.put("messageId", rs.getLong("dataset_message_id"));
            row.put("telegramMessageId", telegramMessageId);
            row.put("chatTitle", rs.getString("chat_title"));
            row.put("messageDate", iso(coalesceDate(rs.getObject("raw_message_date", OffsetDateTime.class), rs.getObject("dataset_message_date", OffsetDateTime.class))));
            row.put("senderDisplayName", rs.getString("sender_name"));
            row.put("senderUsername", rs.getString("sender_username"));
            row.put("author", coalesce(rs.getString("sender_name"), rs.getString("sender_username")));
            row.put("senderTelegramUserId", null);
            row.put("senderNameSource", rawId == null ? "dataset_messages" : "raw_messages");
            row.put("role", rs.getString("source_role"));
            row.put("text", text);
            row.put("textUnavailableReason", textReason);
            row.put("preview", text == null ? null : text.substring(0, Math.min(240, text.length())));
            row.put("textEntities", List.of());
            row.put("usedInPrompt", true);
            row.put("relation", "EVIDENCE");
            row.put("replyToTelegramMessageId", nullableLong(rs, "reply_to_message_id"));
            row.put("topicId", nullableLong(rs, "message_thread_id"));
            row.put("threadId", nullableLong(rs, "message_thread_id"));
            row.put("topicName", rs.getString("topic_title"));
            row.put("internalMessageUrl", appMessageUrl(telegramChatId, telegramMessageId, rawId));
            row.put("appMessageUrl", appMessageUrl(telegramChatId, telegramMessageId, rawId));
            String telegramUrl = telegramUrl(username, telegramMessageId, rs.getString("chat_type"), rs.getString("tdlib_chat_type"));
            row.put("telegramMessageUrl", telegramUrl);
            row.put("telegramLinkAvailable", telegramUrl != null);
            row.put("telegramLinkReason", telegramLinkReason(username, telegramMessageId, rs.getString("chat_type"), rs.getString("tdlib_chat_type")));
            return row;
        }, id);
    }

    private Map<String, Object> discussionSegment(long id) {
        if (id <= 0) return Map.of();
        return jdbc.query("""
                SELECT id, combined_score, decision, rejection_reason, signals_json::text AS signals_json,
                       suppression_reasons_json::text AS suppression_reasons_json, proposed_material_type, source_count,
                       start_message_date, end_message_date, telegram_chat_id, forum_topic_id, message_thread_id
                FROM discussion_segments
                WHERE id = ?
                """, rs -> {
            if (!rs.next()) return Map.of();
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("combinedScore", rs.getBigDecimal("combined_score"));
            row.put("decision", rs.getString("decision"));
            row.put("rejectionReason", rs.getString("rejection_reason"));
            row.put("signals", parseJson(rs.getString("signals_json")));
            row.put("suppressionReasons", parseJson(rs.getString("suppression_reasons_json")));
            row.put("proposedMaterialType", rs.getString("proposed_material_type"));
            row.put("sourceCount", rs.getInt("source_count"));
            row.put("startMessageDate", iso(rs.getObject("start_message_date", OffsetDateTime.class)));
            row.put("endMessageDate", iso(rs.getObject("end_message_date", OffsetDateTime.class)));
            row.put("telegramChatId", rs.getObject("telegram_chat_id"));
            row.put("topicId", rs.getObject("forum_topic_id"));
            row.put("threadId", rs.getObject("message_thread_id"));
            return row;
        }, id);
    }

    private List<Map<String, Object>> traceStages(List<Map<String, Object>> sources) {
        List<Long> rawIds = sources.stream().map(source -> (Long) source.get("rawId")).filter(java.util.Objects::nonNull).toList();
        if (rawIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(rawIds.size(), "?"));
        return jdbc.query("""
                SELECT raw_message_id, replay_run_id, stage_id, stage_name, status, error_code, error_message,
                       started_at, finished_at, duration_ms, created_at, updated_at, output_json::text AS output_json
                FROM pipeline_message_trace
                WHERE raw_message_id IN (""" + placeholders + ") " + """
                ORDER BY raw_message_id, created_at, id
                """, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", rs.getLong("raw_message_id") + ":" + rs.getString("stage_id"));
            row.put("rawId", rs.getLong("raw_message_id"));
            row.put("runId", nullableLong(rs, "replay_run_id"));
            row.put("stage", rs.getString("stage_id"));
            row.put("stageName", rs.getString("stage_name"));
            row.put("status", rs.getString("status"));
            row.put("reason", rs.getString("error_code"));
            row.put("errorCode", rs.getString("error_code"));
            row.put("errorMessage", rs.getString("error_message"));
            row.put("startedAt", iso(rs.getObject("started_at", OffsetDateTime.class)));
            row.put("finishedAt", iso(rs.getObject("finished_at", OffsetDateTime.class)));
            row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
            row.put("updatedAt", iso(rs.getObject("updated_at", OffsetDateTime.class)));
            row.put("durationMs", nullableLong(rs, "duration_ms"));
            row.put("outputJson", rs.getString("output_json"));
            return row;
        }, rawIds.toArray());
    }

    private List<Map<String, Object>> providerCalls(long runId) {
        return jdbc.query("""
                SELECT id, stage, provider_id, model_name, status, input_tokens, output_tokens, cached_tokens,
                       estimated_cost_usd, latency_ms, http_status, error_code, error_message,
                       request_preview, response_preview, response_json::text AS response_json, created_at
                FROM provider_calls
                WHERE run_id = ?
                ORDER BY id
                """, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("stage", rs.getString("stage"));
            row.put("providerId", rs.getObject("provider_id"));
            row.put("model", rs.getString("model_name"));
            row.put("status", rs.getString("status"));
            row.put("inputTokens", rs.getInt("input_tokens"));
            row.put("outputTokens", rs.getInt("output_tokens"));
            row.put("cachedTokens", rs.getInt("cached_tokens"));
            row.put("estimatedCostUsd", rs.getBigDecimal("estimated_cost_usd"));
            row.put("latencyMs", nullableLong(rs, "latency_ms"));
            row.put("httpStatus", rs.getObject("http_status"));
            row.put("errorCode", rs.getString("error_code"));
            row.put("errorMessage", rs.getString("error_message"));
            row.put("requestPreview", redact(rs.getString("request_preview")));
            row.put("responsePreview", redact(rs.getString("response_preview")));
            row.put("responseJson", redact(rs.getString("response_json")));
            row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
            return row;
        }, runId);
    }

    private List<Map<String, Object>> howBuiltSteps(Map<String, Object> material, List<Map<String, Object>> sources) {
        String candidateType = String.valueOf(material.getOrDefault("candidateType", "UNKNOWN"));
        List<Map<String, Object>> steps = new ArrayList<>();
        if ("DISCUSSION_SEGMENT".equals(candidateType)) {
            steps.add(step("Сообщения собраны в цепочку", "same chat/topic/thread", details("sourceCount", sources.size(), "segmentId", material.get("segmentId"), "timeWindow", material.get("segmentTimeWindow"))));
            steps.add(step("Сегмент прошёл скоринг", "discussion segment scoring", details("segmentScore", material.get("segmentScore"), "signals", material.get("segmentSignals"), "suppressionReasons", material.get("segmentSuppressionReasons"))));
            steps.add(step("LLM Judge подтвердил ценность", "judge/provider decision", details("decision", material.get("segmentDecision"), "generationSkipReason", material.get("generationSkipReason"))));
        } else if ("SINGLE_MESSAGE".equals(candidateType)) {
            steps.add(step("Одиночное сообщение прошло отбор", "single message path", details("sourceCount", sources.size())));
        } else {
            steps.add(step("Материал собран из кластера", "cluster path", details("candidateType", candidateType, "sourceCount", sources.size(), "clusterId", material.get("clusterId"))));
        }
        steps.add(step("Материал сгенерирован", "knowledge generation", details("runId", material.get("run") instanceof Map<?, ?> run ? run.get("id") : null, "providerCalls", material.get("providerCalls") instanceof List<?> calls ? calls.size() : 0)));
        steps.add(step("Материал сохранён как DRAFT", "draft material", details("materialId", material.get("materialId"), "createdAt", material.get("createdAt"), "status", material.get("status"))));
        return steps;
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        return row;
    }

    private Map<String, Object> step(String title, String status, Map<String, Object> details) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("title", title);
        row.put("status", status);
        row.put("details", details);
        return row;
    }

    private Map<String, Object> run(long runId) {
        return jdbc.query("SELECT id, status, error, total_messages, processed_messages, provider_calls_total, created_at, finished_at FROM replay_runs WHERE id = ?", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("status", rs.getString("status"));
            row.put("terminalReason", rs.getString("error"));
            row.put("totalMessages", rs.getInt("total_messages"));
            row.put("processedMessages", rs.getInt("processed_messages"));
            row.put("providerCallsTotal", rs.getInt("provider_calls_total"));
            row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
            row.put("finishedAt", iso(rs.getObject("finished_at", OffsetDateTime.class)));
            return row;
        }, runId);
    }

    private Map<String, Object> llmRequest(long runId) {
        return jdbc.query("SELECT provider_id, model_name, input_tokens, output_tokens, estimated_cost_usd FROM provider_calls WHERE run_id = ? ORDER BY id DESC LIMIT 1", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("providerId", rs.getObject("provider_id"));
            row.put("model", rs.getString("model_name"));
            row.put("promptId", null);
            row.put("promptVersion", null);
            row.put("inputTokens", rs.getInt("input_tokens"));
            row.put("outputTokens", rs.getInt("output_tokens"));
            row.put("totalTokens", rs.getInt("input_tokens") + rs.getInt("output_tokens"));
            row.put("estimatedCostUsd", rs.getBigDecimal("estimated_cost_usd"));
            return row;
        }, runId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) return null;
        return status.trim().toUpperCase();
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) return null;
        String first = value.split(",")[0].trim().toUpperCase();
        return "GUIDE".equals(first) ? "GUIDE" : first;
    }

    private String materialWhere(String statusFilter, String contentFilter, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        conditions.add("ki.deleted_at IS NULL");
        if (statusFilter != null) {
            conditions.add("ki.status = ?");
            args.add(statusFilter);
        }
        if (contentFilter != null) {
            if ("OTHER".equals(contentFilter)) {
                conditions.add(normalizedArtifactSql() + " = 'OTHER'");
            } else {
                conditions.add(normalizedArtifactSql() + " = ?");
                args.add(contentFilter);
            }
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private String normalizeArtifact(String artifactType, String itemType) {
        String value = coalesce(artifactType, itemType, "").toUpperCase();
        if (value.contains("GUIDE")) return "GUIDE";
        return switch (value) {
            case "GENERATION", "ANSWER", "SUMMARY" -> value;
            default -> "OTHER";
        };
    }

    private Map<String, Object> counts(String where, List<Object> args) {
        Map<String, Long> byType = new java.util.LinkedHashMap<>();
        for (String type : List.of("GUIDE", "GENERATION", "ANSWER", "SUMMARY", "OTHER")) {
            byType.put(type, 0L);
        }
        jdbc.query("""
                SELECT normalized_type, count(*) AS item_count
                FROM (
                    SELECT %s AS normalized_type
                    FROM knowledge_items ki
                    %s
                ) typed
                GROUP BY normalized_type
                ORDER BY normalized_type
                """.formatted(normalizedArtifactSql(), where), rs -> {
            byType.put(rs.getString("normalized_type"), rs.getLong("item_count"));
        }, args.toArray());

        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT COALESCE(ki.status, 'UNKNOWN') AS status, count(*) AS item_count
                FROM knowledge_items ki
                %s
                GROUP BY COALESCE(ki.status, 'UNKNOWN')
                ORDER BY status
                """.formatted(where), rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("item_count"));
        }, args.toArray());

        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return Map.of(
                "total", total,
                "byType", byType,
                "byStatus", byStatus
        );
    }

    private String normalizedArtifactSql() {
        return """
                CASE
                    WHEN upper(coalesce(ki.artifact_type, ki.item_type, '')) LIKE '%GUIDE%' THEN 'GUIDE'
                    WHEN upper(coalesce(ki.artifact_type, ki.item_type, '')) IN ('GENERATION', 'ANSWER', 'SUMMARY') THEN upper(coalesce(ki.artifact_type, ki.item_type, ''))
                    ELSE 'OTHER'
                END
                """;
    }

    private ObjectNode parse(String value) {
        try { JsonNode n = json.readTree(value == null || value.isBlank() ? "{}" : value); return n.isObject() ? (ObjectNode) n : json.createObjectNode(); }
        catch (Exception ignored) { return json.createObjectNode(); }
    }

    private JsonNode parseJson(String value) {
        try { return json.readTree(value == null || value.isBlank() ? "[]" : value); }
        catch (Exception ignored) { return json.createArrayNode(); }
    }

    private String contentText(ResultSet rs) throws SQLException {
        ObjectNode body = parse(rs.getString("body_json"));
        JsonNode bodyNode = body.path("body");
        if (bodyNode.isTextual()) return bodyNode.asText();
        if (!bodyNode.isMissingNode()) return bodyNode.toPrettyString();
        return body.toPrettyString();
    }

    private String sourceText(ResultSet rs) throws SQLException {
        String text = coalesce(rs.getString("raw_text"), rs.getString("raw_caption"), rs.getString("dataset_text"), rs.getString("dataset_caption"));
        if (!text.isBlank()) return text;
        JsonNode raw = parse(rs.getString("raw_json"));
        text = coalesce(
                raw.path("text").path("text").asText(null),
                raw.path("content").path("text").path("text").asText(null),
                raw.path("caption").path("text").asText(null),
                raw.path("content").path("caption").path("text").asText(null)
        );
        return text.isBlank() ? null : text;
    }

    private String sourceTextReason(ResultSet rs, String text) throws SQLException {
        if (text != null && !text.isBlank()) return null;
        if (nullableLong(rs, "raw_message_id") == null) return "source_raw_message_missing";
        String contentType = rs.getString("content_type");
        if (contentType != null && !contentType.equalsIgnoreCase("messageText")) return "media_without_caption";
        return "text_not_found";
    }

    private String appMessageUrl(Long telegramChatId, Long telegramMessageId, Long rawId) {
        if (telegramChatId != null && rawId != null) {
            return "/groups?chatId=" + telegramChatId + "&message=" + rawId + "&rawId=" + rawId;
        }
        if (telegramChatId == null) return rawId == null ? null : "/messages?rawId=" + rawId;
        return "/groups?chatId=" + telegramChatId;
    }

    private String telegramUrl(String username, Long telegramMessageId, String chatType, String tdlibChatType) {
        if (username == null || username.isBlank() || telegramMessageId == null) return null;
        String normalizedType = coalesce(chatType, tdlibChatType, "").toUpperCase();
        if (normalizedType.contains("DIRECT") || normalizedType.contains("PRIVATE")) return null;
        return "https://t.me/" + username + "/" + telegramMessageId;
    }

    private String telegramLinkReason(String username, Long telegramMessageId, String chatType, String tdlibChatType) {
        if (telegramUrl(username, telegramMessageId, chatType, tdlibChatType) != null) return null;
        if (telegramMessageId == null) return "telegram_message_id_missing";
        String normalizedType = coalesce(chatType, tdlibChatType, "").toUpperCase();
        if (normalizedType.contains("DIRECT") || normalizedType.contains("PRIVATE")) {
            return "Telegram-ссылка недоступна для приватного чата";
        }
        if (username == null || username.isBlank()) {
            String type = coalesce(chatType, tdlibChatType, "chat");
            return "Нет публичной ссылки Telegram для этого источника (" + type + ")";
        }
        return "telegram_link_unavailable";
    }

    private String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)(api[_-]?key|token|password|secret)(\\s*[:=]\\s*)([^\\s,;\\\"]+)", "$1$2[REDACTED]");
    }

    private OffsetDateTime coalesceDate(OffsetDateTime... values) {
        for (OffsetDateTime value : values) if (value != null) return value;
        return null;
    }

    private Integer score(ResultSet rs, String column) throws SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Math.max(0, Math.min(100, value.multiply(java.math.BigDecimal.valueOf(100)).intValue()));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String coalesce(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String iso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
