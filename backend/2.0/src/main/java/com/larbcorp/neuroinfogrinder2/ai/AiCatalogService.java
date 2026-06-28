package com.larbcorp.neuroinfogrinder2.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AiCatalogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiCatalogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<AiProviderDto> providers() {
        return jdbc.query("""
                SELECT id, name, type, base_url, api_key_ref, enabled, priority, health_status,
                       last_health_check_at, created_at, updated_at
                FROM ai_providers
                ORDER BY priority, name
                """, (rs, rowNum) -> providerDto(rs));
    }

    public AiProviderDto createProvider(ProviderRequest request) {
        long id = insertReturningId("""
                INSERT INTO ai_providers (name, type, base_url, api_key_ref, enabled, priority, health_status)
                VALUES (?, ?, ?, ?, ?, ?, 'UNKNOWN')
                """,
                request.name(),
                defaultValue(request.type(), "OPENAI_COMPATIBLE"),
                request.baseUrl(),
                safeApiKeyRef(request.apiKeyRef()),
                request.enabled() == null || request.enabled(),
                request.priority() == null ? 100 : request.priority()
        );
        return provider(id);
    }

    public AiProviderDto updateProvider(long id, ProviderRequest request) {
        AiProviderDto current = provider(id);
        jdbc.update("""
                UPDATE ai_providers
                SET name = ?,
                    type = ?,
                    base_url = ?,
                    api_key_ref = ?,
                    enabled = ?,
                    priority = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                coalesce(request.name(), current.name()),
                coalesce(request.type(), current.type()),
                coalesce(request.baseUrl(), current.baseUrl()),
                request.apiKeyRef() == null ? current.apiKeyRef() : safeApiKeyRef(request.apiKeyRef()),
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.priority() == null ? current.priority() : request.priority(),
                id);
        return provider(id);
    }

    public void deleteProvider(long id) {
        jdbc.update("DELETE FROM ai_providers WHERE id = ?", id);
    }

    public List<AiModelDto> models() {
        return jdbc.query("""
                SELECT m.id, m.provider_id, p.name AS provider_name, m.model_name, m.display_name,
                       m.input_price_per_million, m.output_price_per_million, m.cache_price_per_million,
                       m.context_window, m.supports_json, m.supports_tools, m.supports_streaming,
                       m.enabled, m.metadata_json::text AS metadata_json, m.created_at, m.updated_at
                FROM ai_models m
                JOIN ai_providers p ON p.id = m.provider_id
                ORDER BY p.priority, p.name, m.model_name
                """, (rs, rowNum) -> modelDto(rs));
    }

    public AiModelDto createModel(ModelRequest request) {
        long id = insertReturningId("""
                INSERT INTO ai_models (
                    provider_id, model_name, display_name, input_price_per_million,
                    output_price_per_million, cache_price_per_million, context_window,
                    supports_json, supports_tools, supports_streaming, enabled, metadata_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                request.providerId(),
                request.modelName(),
                defaultValue(request.displayName(), request.modelName()),
                request.inputPricePerMillion(),
                request.outputPricePerMillion(),
                request.cachePricePerMillion(),
                request.contextWindow(),
                request.supportsJson() == null || request.supportsJson(),
                Boolean.TRUE.equals(request.supportsTools()),
                Boolean.TRUE.equals(request.supportsStreaming()),
                request.enabled() == null || request.enabled(),
                defaultJson(request.metadataJson())
        );
        return model(id);
    }

    public AiModelDto updateModel(long id, ModelRequest request) {
        AiModelDto current = model(id);
        jdbc.update("""
                UPDATE ai_models
                SET provider_id = ?,
                    model_name = ?,
                    display_name = ?,
                    input_price_per_million = ?,
                    output_price_per_million = ?,
                    cache_price_per_million = ?,
                    context_window = ?,
                    supports_json = ?,
                    supports_tools = ?,
                    supports_streaming = ?,
                    enabled = ?,
                    metadata_json = ?::jsonb,
                    updated_at = now()
                WHERE id = ?
                """,
                request.providerId() == null ? current.providerId() : request.providerId(),
                coalesce(request.modelName(), current.modelName()),
                coalesce(request.displayName(), current.displayName()),
                request.inputPricePerMillion() == null ? current.inputPricePerMillion() : request.inputPricePerMillion(),
                request.outputPricePerMillion() == null ? current.outputPricePerMillion() : request.outputPricePerMillion(),
                request.cachePricePerMillion() == null ? current.cachePricePerMillion() : request.cachePricePerMillion(),
                request.contextWindow() == null ? current.contextWindow() : request.contextWindow(),
                request.supportsJson() == null ? current.supportsJson() : request.supportsJson(),
                request.supportsTools() == null ? current.supportsTools() : request.supportsTools(),
                request.supportsStreaming() == null ? current.supportsStreaming() : request.supportsStreaming(),
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.metadataJson() == null ? current.metadataJson() : defaultJson(request.metadataJson()),
                id);
        return model(id);
    }

    public void deleteModel(long id) {
        jdbc.update("DELETE FROM ai_models WHERE id = ?", id);
    }

    public List<ModelRouteDto> routes() {
        return jdbc.query("""
                SELECT r.id, r.stage, r.primary_model_id, pm.model_name AS primary_model_name,
                       r.fallback_model_id, fm.model_name AS fallback_model_name, r.enabled,
                       r.max_calls_per_day, r.max_calls_per_1000_messages, r.max_cost_per_day,
                       r.timeout_ms, r.retry_count, r.temperature, r.max_output_tokens,
                       r.routing_policy_json::text AS routing_policy_json, r.created_at, r.updated_at
                FROM pipeline_model_routes r
                LEFT JOIN ai_models pm ON pm.id = r.primary_model_id
                LEFT JOIN ai_models fm ON fm.id = r.fallback_model_id
                ORDER BY r.id
                """, (rs, rowNum) -> routeDto(rs));
    }

    public ModelRouteDto updateRoute(long id, RouteRequest request) {
        ModelRouteDto current = route(id);
        jdbc.update("""
                UPDATE pipeline_model_routes
                SET primary_model_id = ?,
                    fallback_model_id = ?,
                    enabled = ?,
                    max_calls_per_day = ?,
                    max_calls_per_1000_messages = ?,
                    max_cost_per_day = ?,
                    timeout_ms = ?,
                    retry_count = ?,
                    temperature = ?,
                    max_output_tokens = ?,
                    routing_policy_json = ?::jsonb,
                    updated_at = now()
                WHERE id = ?
                """,
                request.primaryModelId() == null ? current.primaryModelId() : request.primaryModelId(),
                request.fallbackModelId() == null ? current.fallbackModelId() : request.fallbackModelId(),
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.maxCallsPerDay() == null ? current.maxCallsPerDay() : request.maxCallsPerDay(),
                request.maxCallsPer1000Messages() == null ? current.maxCallsPer1000Messages() : request.maxCallsPer1000Messages(),
                request.maxCostPerDay() == null ? current.maxCostPerDay() : request.maxCostPerDay(),
                request.timeoutMs() == null ? current.timeoutMs() : request.timeoutMs(),
                request.retryCount() == null ? current.retryCount() : request.retryCount(),
                request.temperature() == null ? current.temperature() : request.temperature(),
                request.maxOutputTokens() == null ? current.maxOutputTokens() : request.maxOutputTokens(),
                request.routingPolicyJson() == null ? current.routingPolicyJson() : defaultJson(request.routingPolicyJson()),
                id);
        return route(id);
    }

    private AiProviderDto provider(long id) {
        return jdbc.queryForObject("""
                SELECT id, name, type, base_url, api_key_ref, enabled, priority, health_status,
                       last_health_check_at, created_at, updated_at
                FROM ai_providers
                WHERE id = ?
                """, (rs, rowNum) -> providerDto(rs), id);
    }

    private AiModelDto model(long id) {
        return jdbc.queryForObject("""
                SELECT m.id, m.provider_id, p.name AS provider_name, m.model_name, m.display_name,
                       m.input_price_per_million, m.output_price_per_million, m.cache_price_per_million,
                       m.context_window, m.supports_json, m.supports_tools, m.supports_streaming,
                       m.enabled, m.metadata_json::text AS metadata_json, m.created_at, m.updated_at
                FROM ai_models m
                JOIN ai_providers p ON p.id = m.provider_id
                WHERE m.id = ?
                """, (rs, rowNum) -> modelDto(rs), id);
    }

    private ModelRouteDto route(long id) {
        return jdbc.queryForObject("""
                SELECT r.id, r.stage, r.primary_model_id, pm.model_name AS primary_model_name,
                       r.fallback_model_id, fm.model_name AS fallback_model_name, r.enabled,
                       r.max_calls_per_day, r.max_calls_per_1000_messages, r.max_cost_per_day,
                       r.timeout_ms, r.retry_count, r.temperature, r.max_output_tokens,
                       r.routing_policy_json::text AS routing_policy_json, r.created_at, r.updated_at
                FROM pipeline_model_routes r
                LEFT JOIN ai_models pm ON pm.id = r.primary_model_id
                LEFT JOIN ai_models fm ON fm.id = r.fallback_model_id
                WHERE r.id = ?
                """, (rs, rowNum) -> routeDto(rs), id);
    }

    private AiProviderDto providerDto(ResultSet rs) throws SQLException {
        return new AiProviderDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("base_url"),
                rs.getString("api_key_ref"),
                rs.getBoolean("enabled"),
                rs.getInt("priority"),
                rs.getString("health_status"),
                iso(rs, "last_health_check_at"),
                iso(rs, "created_at"),
                iso(rs, "updated_at")
        );
    }

    private AiModelDto modelDto(ResultSet rs) throws SQLException {
        return new AiModelDto(
                rs.getLong("id"),
                rs.getLong("provider_id"),
                rs.getString("provider_name"),
                rs.getString("model_name"),
                rs.getString("display_name"),
                rs.getBigDecimal("input_price_per_million"),
                rs.getBigDecimal("output_price_per_million"),
                rs.getBigDecimal("cache_price_per_million"),
                nullableInt(rs, "context_window"),
                rs.getBoolean("supports_json"),
                rs.getBoolean("supports_tools"),
                rs.getBoolean("supports_streaming"),
                rs.getBoolean("enabled"),
                rs.getString("metadata_json"),
                iso(rs, "created_at"),
                iso(rs, "updated_at")
        );
    }

    private ModelRouteDto routeDto(ResultSet rs) throws SQLException {
        return new ModelRouteDto(
                rs.getLong("id"),
                rs.getString("stage"),
                nullableLong(rs, "primary_model_id"),
                rs.getString("primary_model_name"),
                nullableLong(rs, "fallback_model_id"),
                rs.getString("fallback_model_name"),
                rs.getBoolean("enabled"),
                nullableInt(rs, "max_calls_per_day"),
                nullableInt(rs, "max_calls_per_1000_messages"),
                rs.getBigDecimal("max_cost_per_day"),
                rs.getInt("timeout_ms"),
                rs.getInt("retry_count"),
                rs.getBigDecimal("temperature"),
                nullableInt(rs, "max_output_tokens"),
                rs.getString("routing_policy_json"),
                iso(rs, "created_at"),
                iso(rs, "updated_at")
        );
    }

    private String defaultJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (JsonProcessingException error) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("value", value);
            try {
                return objectMapper.writeValueAsString(node);
            } catch (JsonProcessingException impossible) {
                return "{}";
            }
        }
    }

    private long insertReturningId(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeApiKeyRef(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("sk-")
                || normalized.startsWith("sk_")
                || normalized.contains("begin ")
                || normalized.contains("api_key=")
                || normalized.contains("secret=")
                || value.length() > 120) {
            throw new IllegalArgumentException("apiKeyRef must reference a local secret name, not contain a raw secret value");
        }
        return value;
    }

    private String coalesce(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String field) throws SQLException {
        int value = rs.getInt(field);
        return rs.wasNull() ? null : value;
    }

    private String iso(ResultSet rs, String field) throws SQLException {
        OffsetDateTime value = rs.getObject(field, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    public record ProviderRequest(
            String name,
            String type,
            String baseUrl,
            String apiKeyRef,
            Boolean enabled,
            Integer priority
    ) {
    }

    public record ModelRequest(
            Long providerId,
            String modelName,
            String displayName,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            BigDecimal cachePricePerMillion,
            Integer contextWindow,
            Boolean supportsJson,
            Boolean supportsTools,
            Boolean supportsStreaming,
            Boolean enabled,
            String metadataJson
    ) {
    }

    public record RouteRequest(
            Long primaryModelId,
            Long fallbackModelId,
            Boolean enabled,
            Integer maxCallsPerDay,
            Integer maxCallsPer1000Messages,
            BigDecimal maxCostPerDay,
            Integer timeoutMs,
            Integer retryCount,
            BigDecimal temperature,
            Integer maxOutputTokens,
            String routingPolicyJson
    ) {
    }

    public record AiProviderDto(
            long id,
            String name,
            String type,
            String baseUrl,
            String apiKeyRef,
            boolean enabled,
            int priority,
            String healthStatus,
            String lastHealthCheckAt,
            String createdAt,
            String updatedAt
    ) {
    }

    public record AiModelDto(
            long id,
            long providerId,
            String providerName,
            String modelName,
            String displayName,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            BigDecimal cachePricePerMillion,
            Integer contextWindow,
            boolean supportsJson,
            boolean supportsTools,
            boolean supportsStreaming,
            boolean enabled,
            String metadataJson,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ModelRouteDto(
            long id,
            String stage,
            Long primaryModelId,
            String primaryModelName,
            Long fallbackModelId,
            String fallbackModelName,
            boolean enabled,
            Integer maxCallsPerDay,
            Integer maxCallsPer1000Messages,
            BigDecimal maxCostPerDay,
            int timeoutMs,
            int retryCount,
            BigDecimal temperature,
            Integer maxOutputTokens,
            String routingPolicyJson,
            String createdAt,
            String updatedAt
    ) {
    }
}
