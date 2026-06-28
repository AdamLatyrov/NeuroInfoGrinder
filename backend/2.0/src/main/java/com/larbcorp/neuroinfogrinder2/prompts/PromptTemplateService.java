package com.larbcorp.neuroinfogrinder2.prompts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.ModelhubProviderGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptTemplateService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModelhubProviderGateway providerGateway;

    public PromptTemplateService(JdbcTemplate jdbc, ObjectMapper json, ModelhubProviderGateway providerGateway) {
        this.jdbc = jdbc;
        this.json = json;
        this.providerGateway = providerGateway;
    }

    public record PromptDto(
        long id,
        String code,
        String name,
        String description,
        String stage,
        List<String> supportedModesJson,
        String systemPrompt,
        String userPromptTemplate,
        JsonNode outputSchemaJson,
        String providerRoute,
        String modelName,
        String fallbackModel,
        int version,
        boolean isActive,
        String status,
        List<String> variablesJson,
        JsonNode metadataJson,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}

    public record PromptVersionDto(long id, long promptTemplateId, int version, String snapshotJson, String changeReason, OffsetDateTime createdAt) {}

    public record CreatePromptRequest(
        String code,
        String name,
        String description,
        String stage,
        String promptMode,
        String systemPrompt,
        String userPromptTemplate,
        String outputSchema,
        String providerRoute,
        String modelName,
        String fallbackModel
    ) {}

    public record UpdatePromptRequest(
        String name,
        String description,
        String userPromptTemplate,
        String systemPrompt,
        String outputSchema,
        String providerRoute,
        String modelName,
        String fallbackModel,
        String changeReason
    ) {}

    public record TestPromptRequest(Map<String, String> variables, String mode) {}

    public record TestPromptResult(
        boolean success,
        String status,
        String output,
        String decision,
        double confidence,
        int inputTokens,
        int outputTokens,
        BigDecimal estimatedCostUsd
    ) {}

    public List<PromptDto> listAll() {
        return jdbc.query("SELECT * FROM prompt_templates ORDER BY stage, is_active DESC, code, version DESC", this::mapPrompt);
    }

    public List<PromptDto> listByStage(String stage) {
        return jdbc.query("SELECT * FROM prompt_templates WHERE stage = ? ORDER BY is_active DESC, code, version DESC", this::mapPrompt, stage);
    }

    public PromptDto getById(long id) {
        return jdbc.queryForObject("SELECT * FROM prompt_templates WHERE id = ?", this::mapPrompt, id);
    }

    public PromptDto getByCode(String code) {
        return jdbc.queryForObject("SELECT * FROM prompt_templates WHERE code = ?", this::mapPrompt, code);
    }

    public PromptDto createPrompt(CreatePromptRequest request) {
        String code = blankToDefault(request.code(), slug(request.name()));
        String stage = blankToDefault(request.stage(), "LLM_CLUSTER_JUDGE_AND_ROUTING");
        String modes = modesJson(request.promptMode());
        String outputSchema = normalizeJsonObject(request.outputSchema());
        long id = insertPrompt("""
            INSERT INTO prompt_templates (
                code, name, description, stage, supported_modes_json, system_prompt, user_prompt_template,
                output_schema_json, provider_route, model_name, fallback_model, version, is_active, status, variables_json
            )
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, 1, false, 'DRAFT', ?::jsonb)
            """,
            code,
            request.name(),
            request.description(),
            stage,
            modes,
            blankToDefault(request.systemPrompt(), ""),
            blankToDefault(request.userPromptTemplate(), ""),
            outputSchema,
            blankToDefault(request.providerRoute(), "ModelHub"),
            blankToDefault(request.modelName(), "gpt-5.5"),
            blankToDefault(request.fallbackModel(), "claude-sonnet-4-6"),
            variablesJson(request.userPromptTemplate())
        );
        saveVersion(id, 1, "created", "system");
        return getById(id);
    }

    public PromptDto updatePrompt(long id, UpdatePromptRequest request) {
        PromptDto current = getById(id);
        int nextVersion = current.version() + 1;
        jdbc.update("""
            UPDATE prompt_templates
            SET name = COALESCE(?, name),
                description = COALESCE(?, description),
                system_prompt = COALESCE(?, system_prompt),
                user_prompt_template = COALESCE(?, user_prompt_template),
                output_schema_json = COALESCE(?::jsonb, output_schema_json),
                provider_route = COALESCE(?, provider_route),
                model_name = COALESCE(?, model_name),
                fallback_model = COALESCE(?, fallback_model),
                variables_json = COALESCE(?::jsonb, variables_json),
                version = ?,
                status = CASE WHEN is_active THEN status ELSE 'DRAFT' END,
                updated_at = now()
            WHERE id = ?
            """,
            request.name(),
            request.description(),
            request.systemPrompt(),
            request.userPromptTemplate(),
            request.outputSchema() == null ? null : normalizeJsonObject(request.outputSchema()),
            request.providerRoute(),
            request.modelName(),
            request.fallbackModel(),
            request.userPromptTemplate() == null ? null : variablesJson(request.userPromptTemplate()),
            nextVersion,
            id
        );
        saveVersion(id, nextVersion, blankToDefault(request.changeReason(), "updated"), "system");
        return getById(id);
    }

    public PromptDto activatePrompt(long id, String updatedBy) {
        PromptDto prompt = getById(id);
        jdbc.update("UPDATE prompt_templates SET is_active = false, status = 'DRAFT', updated_at = now() WHERE stage = ? AND id <> ?", prompt.stage(), id);
        jdbc.update("UPDATE prompt_templates SET is_active = true, status = 'ACTIVE', updated_at = now() WHERE id = ?", id);
        saveVersion(id, prompt.version(), "activated", updatedBy);
        return getById(id);
    }

    public List<PromptVersionDto> listVersions(long id) {
        return jdbc.query("""
            SELECT id, prompt_template_id, version, snapshot_json::text AS snapshot_json, change_reason, created_at
            FROM prompt_template_versions
            WHERE prompt_template_id = ?
            ORDER BY version DESC, id DESC
            """, (rs, rowNum) -> new PromptVersionDto(
            rs.getLong("id"),
            rs.getLong("prompt_template_id"),
            rs.getInt("version"),
            rs.getString("snapshot_json"),
            rs.getString("change_reason"),
            rs.getObject("created_at", OffsetDateTime.class)
        ), id);
    }

    public PromptDto rollbackPrompt(long id, int version, String updatedBy) {
        String snapshot = jdbc.queryForObject("""
            SELECT snapshot_json::text
            FROM prompt_template_versions
            WHERE prompt_template_id = ? AND version = ?
            ORDER BY id DESC
            LIMIT 1
            """, String.class, id, version);
        JsonNode node = readTree(snapshot);
        int nextVersion = getById(id).version() + 1;
        jdbc.update("""
            UPDATE prompt_templates
            SET name = ?, description = ?, stage = ?, supported_modes_json = ?::jsonb,
                system_prompt = ?, user_prompt_template = ?, output_schema_json = ?::jsonb,
                provider_route = ?, model_name = ?, fallback_model = ?, variables_json = ?::jsonb,
                version = ?, updated_at = now()
            WHERE id = ?
            """,
            text(node, "name"),
            text(node, "description"),
            text(node, "stage"),
            node.path("supportedModesJson").toString(),
            text(node, "systemPrompt"),
            text(node, "userPromptTemplate"),
            node.path("outputSchemaJson").toString(),
            text(node, "providerRoute"),
            text(node, "modelName"),
            text(node, "fallbackModel"),
            node.path("variablesJson").toString(),
            nextVersion,
            id
        );
        saveVersion(id, nextVersion, "rollback to version " + version, updatedBy);
        return getById(id);
    }

    public TestPromptResult testPrompt(long id, TestPromptRequest request) {
        PromptDto prompt = getById(id);
        String mode = blankToDefault(request.mode(), "CLUSTER");
        String rendered = render(prompt, request.variables(), mode);
        try {
            ModelhubProviderGateway.GatewayResult result = providerGateway.callJson(
                null,
                prompt.stage(),
                rendered,
                new ModelhubProviderGateway.ReplayBudget(1, BigDecimal.valueOf(0.25)),
                false
            );
            if (result.providerCallId() != null) {
                jdbc.update("UPDATE provider_calls SET prompt_template_id = ?, prompt_version = ?, prompt_mode = ? WHERE id = ?",
                    prompt.id(), prompt.version(), mode, result.providerCallId());
            }
            JsonNode response = result.responseJson() == null ? json.createObjectNode() : result.responseJson();
            return new TestPromptResult(
                result.success(),
                result.status(),
                response.toString(),
                response.path("decision").asText(""),
                response.path("confidence").asDouble(0.0),
                result.inputTokens(),
                result.outputTokens(),
                result.estimatedCostUsd()
            );
        } catch (RuntimeException error) {
            ObjectNode response = json.createObjectNode();
            response.put("error", error.getClass().getSimpleName());
            response.put("message", "Prompt test failed before provider response");
            return new TestPromptResult(false, "FAILED", response.toString(), "", 0.0, 0, 0, BigDecimal.ZERO);
        }
    }

    public Map<String, Object> routeMapping() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routes", jdbc.query("""
            SELECT r.stage,
                   p.name AS provider_name,
                   p.enabled AS provider_enabled,
                   p.api_key_ref,
                   p.health_status,
                   m.model_name AS model_name,
                   fm.model_name AS fallback_model_name,
                   r.enabled,
                   r.updated_at,
                   COALESCE(sum(pc.estimated_cost_usd), 0) AS total_cost_usd,
                   max(pc.created_at) AS last_route_test,
                   (array_remove(array_agg(pc.error_message ORDER BY pc.created_at DESC), NULL))[1] AS last_error
            FROM pipeline_model_routes r
            LEFT JOIN ai_models m ON m.id = r.primary_model_id
            LEFT JOIN ai_models fm ON fm.id = r.fallback_model_id
            LEFT JOIN ai_providers p ON p.id = m.provider_id
            LEFT JOIN provider_calls pc ON pc.stage = r.stage
            WHERE r.stage IN ('LLM_CLUSTER_JUDGE_AND_ROUTING', 'KNOWLEDGE_GENERATION')
            GROUP BY r.stage, p.name, p.enabled, p.api_key_ref, p.health_status, m.model_name, fm.model_name, r.enabled, r.updated_at
            ORDER BY r.stage
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", rs.getString("stage"));
            row.put("provider", rs.getString("provider_name"));
            row.put("providerEnabled", rs.getBoolean("provider_enabled"));
            row.put("envKeyPresent", rs.getString("api_key_ref") != null && !rs.getString("api_key_ref").isBlank());
            row.put("providerStatus", rs.getString("health_status"));
            row.put("model", rs.getString("model_name"));
            row.put("fallback", rs.getString("fallback_model_name"));
            row.put("routeEnabled", rs.getBoolean("enabled"));
            row.put("lastRouteTest", rs.getObject("last_route_test", OffsetDateTime.class));
            row.put("lastError", rs.getString("last_error"));
            row.put("cost", rs.getBigDecimal("total_cost_usd"));
            return row;
        }));
        return result;
    }

    private PromptDto mapPrompt(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PromptDto(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("stage"),
            readStringList(rs.getString("supported_modes_json")),
            rs.getString("system_prompt"),
            rs.getString("user_prompt_template"),
            readTree(rs.getString("output_schema_json")),
            rs.getString("provider_route"),
            rs.getString("model_name"),
            rs.getString("fallback_model"),
            rs.getInt("version"),
            rs.getBoolean("is_active"),
            rs.getString("status"),
            readStringList(rs.getString("variables_json")),
            readTree(rs.getString("metadata_json")),
            rs.getString("created_by"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private void saveVersion(long promptId, int version, String reason, String createdBy) {
        PromptDto prompt = getById(promptId);
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("name", prompt.name());
        snapshot.put("description", prompt.description());
        snapshot.put("stage", prompt.stage());
        snapshot.set("supportedModesJson", json.valueToTree(prompt.supportedModesJson()));
        snapshot.put("systemPrompt", prompt.systemPrompt());
        snapshot.put("userPromptTemplate", prompt.userPromptTemplate());
        snapshot.set("outputSchemaJson", prompt.outputSchemaJson());
        snapshot.put("providerRoute", prompt.providerRoute());
        snapshot.put("modelName", prompt.modelName());
        snapshot.put("fallbackModel", prompt.fallbackModel());
        snapshot.set("variablesJson", json.valueToTree(prompt.variablesJson()));
        jdbc.update("""
            INSERT INTO prompt_template_versions (prompt_template_id, version, snapshot_json, change_reason, created_by)
            VALUES (?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (prompt_template_id, version) DO UPDATE SET
                snapshot_json = EXCLUDED.snapshot_json,
                change_reason = EXCLUDED.change_reason,
                created_by = EXCLUDED.created_by
            """, promptId, version, snapshot.toString(), reason, createdBy);
    }

    private String render(PromptDto prompt, Map<String, String> variables, String mode) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("text", "Mini-guide: how to fix BYOK provider failed. Cause: missing env key. Steps: check provider route, set env key, run test call, retry generation.");
        values.put("sourceType", mode);
        values.put("messageCount", "1");
        values.put("language", "ru");
        values.put("topics", "BYOK, provider, troubleshooting");
        values.put("generationInstructions", "Create concise troubleshooting material.");
        if (variables != null) {
            values.putAll(variables);
        }
        String rendered = prompt.systemPrompt() + "\n\n" + prompt.userPromptTemplate();
        rendered = rendered.replace("{{mode}}", mode);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered + "\n\nOutput schema:\n" + prompt.outputSchemaJson();
    }

    private List<String> readStringList(String value) {
        JsonNode node = readTree(value);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException error) {
            return json.createObjectNode();
        }
    }

    private String modesJson(String mode) {
        if ("CLUSTER".equals(mode)) {
            return "[\"CLUSTER\"]";
        }
        if ("SINGLE_MESSAGE".equals(mode)) {
            return "[\"SINGLE_MESSAGE\"]";
        }
        return "[\"CLUSTER\",\"SINGLE_MESSAGE\"]";
    }

    private String normalizeJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        JsonNode node = readTree(value);
        return node.isMissingNode() ? "{}" : node.toString();
    }

    private String variablesJson(String template) {
        List<String> variables = new ArrayList<>();
        if (template != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}").matcher(template);
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (!variables.contains(variable)) {
                    variables.add(variable);
                }
            }
        }
        try {
            return json.writeValueAsString(variables);
        } catch (JsonProcessingException error) {
            return "[]";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String slug(String value) {
        return blankToDefault(value, "PROMPT").toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private long insertPrompt(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Prompt insert did not return id");
        }
        return key.longValue();
    }
}
