package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class ModelhubProviderGateway {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final HttpClient httpClient;

    public ModelhubProviderGateway(JdbcTemplate jdbc, ObjectMapper objectMapper, Environment environment) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public ProviderHealth healthCheck(long providerId) {
        Provider provider = provider(providerId);
        String key = environment.getProperty(provider.apiKeyRef());
        if (key == null || key.isBlank()) {
            updateProviderHealth(providerId, "MISSING_API_KEY");
            return new ProviderHealth(providerId, provider.name(), provider.baseUrl(), provider.apiKeyRef(), "MISSING_API_KEY", false);
        }
        updateProviderHealth(providerId, "OK");
        return new ProviderHealth(providerId, provider.name(), provider.baseUrl(), provider.apiKeyRef(), "OK", true);
    }

    public ProviderHealth modelhubHealth() {
        Provider provider = modelhubProvider();
        return healthCheck(provider.id());
    }

    public GatewayResult callJson(
            Long runId,
            String stage,
            String prompt,
            ReplayBudget budget,
            boolean allowCache
    ) {
        Route route = route(stage);
        if (route == null || route.primaryModelId() == null || !route.enabled()) {
            long callId = recordCall(runId, stage, null, null, null, 1, Hashing.sha256(stage + prompt),
                    prompt, null, objectMapper.createObjectNode(), "PROVIDER_NOT_CONFIGURED",
                    0, 0, 0, BigDecimal.ZERO, null, null, "ROUTE_DISABLED", "Route is disabled or not configured");
            return GatewayResult.failed(callId, "PROVIDER_NOT_CONFIGURED", "Route is disabled or not configured");
        }

        Model model = model(route.primaryModelId());
        Provider provider = provider(model.providerId());
        String key = environment.getProperty(provider.apiKeyRef());
        if (key == null || key.isBlank()) {
            long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), 1,
                    Hashing.sha256(stage + model.modelName() + prompt), prompt, null, objectMapper.createObjectNode(),
                    "PROVIDER_NOT_CONFIGURED", 0, 0, 0, BigDecimal.ZERO, null, null,
                    "MISSING_API_KEY", "API key is not configured in local env");
            updateProviderHealth(provider.id(), "MISSING_API_KEY");
            return GatewayResult.failed(callId, "PROVIDER_NOT_CONFIGURED", "API key is not configured in local env");
        }

        String requestHash = Hashing.sha256(stage + "|" + model.modelName() + "|" + prompt + "|" + route.temperature() + "|" + route.maxOutputTokens());
        if (allowCache) {
            CachedCall cached = cachedCall(requestHash);
            if (cached != null) {
                long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), 1,
                        requestHash, prompt, cached.responsePreview(), cached.responseJson(), "CACHE_HIT",
                        cached.inputTokens(), cached.outputTokens(), cached.inputTokens(), BigDecimal.ZERO,
                        0L, cached.httpStatus(), null, null);
                return GatewayResult.success(callId, true, cached.responseJson(), BigDecimal.ZERO, cached.inputTokens(), cached.outputTokens());
            }
        }

        int usedCalls = realCalls(runId);
        BigDecimal usedCost = usedCost(runId);
        if (usedCalls >= budget.maxProviderCalls() || usedCost.compareTo(budget.maxEstimatedCostUsd()) >= 0) {
            long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), 1,
                    requestHash, prompt, null, objectMapper.createObjectNode(), "BUDGET_BLOCKED",
                    0, 0, 0, BigDecimal.ZERO, null, null, "BUDGET_LIMIT_REACHED", "Replay provider budget reached");
            return GatewayResult.failed(callId, "BUDGET_BLOCKED", "Replay provider budget reached");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model.modelName());
        request.put("temperature", route.temperature() == null ? 0.0 : route.temperature().doubleValue());
        request.put("max_tokens", route.maxOutputTokens() == null ? 2048 : route.maxOutputTokens());
        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "json_object");
        var messages = request.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", "Return strict JSON only. Do not include secrets.");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        int attempts = Math.max(1, route.retryCount());
        GatewayResult last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long started = System.nanoTime();
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(stripTrailingSlash(provider.baseUrl()) + "/chat/completions"))
                        .timeout(Duration.ofMillis(route.timeoutMs()))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + key)
                        .POST(HttpRequest.BodyPublishers.ofString(json(request)))
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long latencyMs = elapsedMs(started);
                int inputTokens = estimateTokens(prompt);
                int outputTokens = estimateTokens(response.body());
                BigDecimal cost = estimateCost(model, inputTokens, outputTokens);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode parsed = objectMapper.readTree(response.body());
                    JsonNode content = parsed.path("choices").path(0).path("message").path("content");
                    JsonNode responseJson = content.isTextual() ? objectMapper.readTree(content.asText()) : parsed;
                    long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), attempt,
                            requestHash, prompt, response.body(), responseJson, "SUCCESS",
                            inputTokens, outputTokens, 0, cost, latencyMs, response.statusCode(), null, null);
                    updateProviderHealth(provider.id(), "OK");
                    return GatewayResult.success(callId, false, responseJson, cost, inputTokens, outputTokens);
                }

                String status = response.statusCode() == 429 ? "RATE_LIMITED" : "FAILED";
                long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), attempt,
                        requestHash, prompt, response.body(), objectMapper.createObjectNode(), status,
                        inputTokens, outputTokens, 0, cost, latencyMs, response.statusCode(),
                        "HTTP_" + response.statusCode(), "Provider returned HTTP " + response.statusCode());
                last = GatewayResult.failed(callId, status, "Provider returned HTTP " + response.statusCode());
                if (!retryable(response.statusCode())) {
                    break;
                }
                sleepBackoff(attempt);
            } catch (java.net.http.HttpTimeoutException error) {
                long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), attempt,
                        requestHash, prompt, null, objectMapper.createObjectNode(), "TIMEOUT",
                        estimateTokens(prompt), 0, 0, BigDecimal.ZERO, elapsedMs(started), null,
                        "TIMEOUT", "Provider request timed out");
                last = GatewayResult.failed(callId, "TIMEOUT", "Provider request timed out");
                sleepBackoff(attempt);
            } catch (IOException | InterruptedException | RuntimeException error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                long callId = recordCall(runId, stage, provider.id(), model.id(), model.modelName(), attempt,
                        requestHash, prompt, null, objectMapper.createObjectNode(), "FAILED",
                        estimateTokens(prompt), 0, 0, BigDecimal.ZERO, elapsedMs(started), null,
                        error.getClass().getSimpleName(), "Provider request failed");
                last = GatewayResult.failed(callId, "FAILED", "Provider request failed");
                break;
            }
        }
        updateProviderHealth(provider.id(), "DEGRADED");
        return last == null ? GatewayResult.failed(null, "FAILED", "Provider request failed") : last;
    }

    private boolean retryable(int statusCode) {
        return List.of(429, 500, 502, 503, 504).contains(statusCode);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(5000, 1000L * attempt));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private Provider modelhubProvider() {
        return jdbc.queryForObject("""
                SELECT id, name, type, base_url, api_key_ref
                FROM ai_providers
                WHERE name = 'modelhub'
                """, (rs, rowNum) -> new Provider(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("base_url"),
                rs.getString("api_key_ref")
        ));
    }

    private Provider provider(long id) {
        return jdbc.queryForObject("""
                SELECT id, name, type, base_url, api_key_ref
                FROM ai_providers
                WHERE id = ?
                """, (rs, rowNum) -> new Provider(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("base_url"),
                rs.getString("api_key_ref")
        ), id);
    }

    private Model model(long id) {
        return jdbc.queryForObject("""
                SELECT id, provider_id, model_name, input_price_per_million, output_price_per_million
                FROM ai_models
                WHERE id = ?
                """, (rs, rowNum) -> new Model(
                rs.getLong("id"),
                rs.getLong("provider_id"),
                rs.getString("model_name"),
                rs.getBigDecimal("input_price_per_million"),
                rs.getBigDecimal("output_price_per_million")
        ), id);
    }

    private Route route(String stage) {
        List<Route> routes = jdbc.query("""
                SELECT id, stage, primary_model_id, fallback_model_id, enabled,
                       timeout_ms, retry_count, temperature, max_output_tokens
                FROM pipeline_model_routes
                WHERE stage = ?
                """, (rs, rowNum) -> new Route(
                rs.getLong("id"),
                rs.getString("stage"),
                nullableLong(rs.getLong("primary_model_id"), rs.wasNull()),
                nullableLong(rs.getLong("fallback_model_id"), rs.wasNull()),
                rs.getBoolean("enabled"),
                rs.getInt("timeout_ms"),
                rs.getInt("retry_count"),
                rs.getBigDecimal("temperature"),
                nullableInt(rs.getInt("max_output_tokens"), rs.wasNull())
        ), stage);
        return routes.isEmpty() ? null : routes.get(0);
    }

    private CachedCall cachedCall(String requestHash) {
        List<CachedCall> calls = jdbc.query("""
                SELECT response_preview, response_json::text AS response_json, input_tokens, output_tokens, http_status
                FROM provider_calls
                WHERE request_hash = ? AND status = 'SUCCESS'
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> new CachedCall(
                rs.getString("response_preview"),
                readJson(rs.getString("response_json")),
                rs.getInt("input_tokens"),
                rs.getInt("output_tokens"),
                rs.getInt("http_status")
        ), requestHash);
        return calls.isEmpty() ? null : calls.get(0);
    }

    private long recordCall(
            long runId,
            String stage,
            Long providerId,
            Long modelId,
            String modelName,
            int attemptNumber,
            String requestHash,
            String requestPreview,
            String responsePreview,
            JsonNode responseJson,
            String status,
            int inputTokens,
            int outputTokens,
            int cachedTokens,
            BigDecimal estimatedCostUsd,
            Long latencyMs,
            Integer httpStatus,
            String errorCode,
            String errorMessage
    ) {
        return JdbcIds.insertReturningId(jdbc, """
                INSERT INTO provider_calls (
                    run_id, stage, provider_id, model_id, model_name, attempt_number, request_hash,
                    request_preview, response_preview, response_json, status, input_tokens, output_tokens,
                    cached_tokens, estimated_cost_usd, latency_ms, http_status, error_code, error_message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                stage,
                providerId,
                modelId,
                modelName,
                attemptNumber,
                requestHash,
                sanitizePreview(requestPreview),
                sanitizePreview(responsePreview),
                json(responseJson),
                status,
                inputTokens,
                outputTokens,
                cachedTokens,
                estimatedCostUsd,
                latencyMs,
                httpStatus,
                errorCode,
                errorMessage);
    }

    private void updateProviderHealth(long providerId, String status) {
        jdbc.update("""
                UPDATE ai_providers
                SET health_status = ?, last_health_check_at = now(), updated_at = now()
                WHERE id = ?
                """, status, providerId);
    }

    private int realCalls(Long runId) {
        if (runId == null) {
            return 0;
        }
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM provider_calls
                WHERE run_id = ? AND status NOT IN ('CACHE_HIT', 'BUDGET_BLOCKED', 'PROVIDER_NOT_CONFIGURED')
                """, Integer.class, runId);
        return count == null ? 0 : count;
    }

    private BigDecimal usedCost(Long runId) {
        if (runId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = jdbc.queryForObject("""
                SELECT COALESCE(sum(estimated_cost_usd), 0)
                FROM provider_calls
                WHERE run_id = ?
                """, BigDecimal.class, runId);
        return cost == null ? BigDecimal.ZERO : cost;
    }

    private BigDecimal estimateCost(Model model, int inputTokens, int outputTokens) {
        BigDecimal input = model.inputPricePerMillion() == null ? BigDecimal.ZERO : model.inputPricePerMillion();
        BigDecimal output = model.outputPricePerMillion() == null ? BigDecimal.ZERO : model.outputPricePerMillion();
        BigDecimal inputCost = input.multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = output.multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, value.length() / 4);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private boolean hasSensitivePattern(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("authorization:")
                || lower.contains("bearer ")
                || lower.contains("api_key")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.startsWith("sk-");
    }

    private String sanitizePreview(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\"']+", "$1***")
                .replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s\"']+", "$1***")
                .replaceAll("(?i)(password\\s*[:=]\\s*)[^\\s\"']+", "$1***");
        if (hasSensitivePattern(sanitized)) {
            sanitized = "[sanitized]";
        }
        return sanitized.length() <= 2000 ? sanitized : sanitized.substring(0, 2000);
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException error) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot serialize JSON", error);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private Integer nullableInt(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    public record ReplayBudget(int maxProviderCalls, BigDecimal maxEstimatedCostUsd) {
    }

    public record ProviderHealth(long providerId, String name, String baseUrl, String apiKeyRef, String status, boolean configured) {
    }

    public record GatewayResult(
            Long providerCallId,
            boolean success,
            boolean cacheHit,
            String status,
            String error,
            JsonNode responseJson,
            BigDecimal estimatedCostUsd,
            int inputTokens,
            int outputTokens
    ) {
        static GatewayResult success(long callId, boolean cacheHit, JsonNode responseJson, BigDecimal cost, int inputTokens, int outputTokens) {
            return new GatewayResult(callId, true, cacheHit, cacheHit ? "CACHE_HIT" : "SUCCESS", null, responseJson, cost, inputTokens, outputTokens);
        }

        static GatewayResult failed(Long callId, String status, String error) {
            return new GatewayResult(callId, false, false, status, error, null, BigDecimal.ZERO, 0, 0);
        }
    }

    private record Provider(long id, String name, String type, String baseUrl, String apiKeyRef) {
    }

    private record Model(long id, long providerId, String modelName, BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion) {
    }

    private record Route(
            long id,
            String stage,
            Long primaryModelId,
            Long fallbackModelId,
            boolean enabled,
            int timeoutMs,
            int retryCount,
            BigDecimal temperature,
            Integer maxOutputTokens
    ) {
    }

    private record CachedCall(String responsePreview, JsonNode responseJson, int inputTokens, int outputTokens, int httpStatus) {
    }
}
