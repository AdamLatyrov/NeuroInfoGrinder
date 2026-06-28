package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class ModelWorkerClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    public ModelWorkerClient(
            ObjectMapper objectMapper,
            @Value("${neuroinfogrinder.model-worker.base-url}") String baseUrl,
            @Value("${neuroinfogrinder.model-worker.timeout-ms}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public WorkerHealth health() {
        try {
            JsonNode json = sendJson("GET", "/health", null, Duration.ofSeconds(5));
            return new WorkerHealth(
                    true,
                    text(json, "status", "UNKNOWN"),
                    json.path("classifier").path("status").asText("UNKNOWN"),
                    json.path("classifier").path("name").asText("UNKNOWN"),
                    json.path("embeddings").path("status").asText("UNKNOWN"),
                    json.path("embeddings").path("name").asText("UNKNOWN"),
                    json.path("embeddings").path("dimension").asInt(0),
                    json
            );
        } catch (RuntimeException error) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("error", error.getClass().getSimpleName());
            return new WorkerHealth(false, "MODEL_WORKER_DOWN", "MODEL_WORKER_DOWN", "UNKNOWN",
                    "MODEL_WORKER_DOWN", "UNKNOWN", 0, details);
        }
    }

    public JsonNode classify(List<WorkerItem> items) {
        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode array = request.putArray("items");
        for (WorkerItem item : items) {
            ObjectNode node = array.addObject();
            node.put("id", item.id());
            node.put("text", item.text());
            node.set("features", item.features() == null ? objectMapper.createObjectNode() : item.features());
        }
        return sendJson("POST", "/classify", request, timeout);
    }

    public JsonNode embedBatch(List<WorkerItem> items) {
        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode array = request.putArray("items");
        for (WorkerItem item : items) {
            ObjectNode node = array.addObject();
            node.put("id", item.id());
            node.put("text", item.text());
            node.set("features", item.features() == null ? objectMapper.createObjectNode() : item.features());
        }
        return sendJson("POST", "/embed-batch", request, timeout);
    }

    public JsonNode topics(List<WorkerItem> items, int maxTopics) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("max_topics", maxTopics);
        ArrayNode array = request.putArray("items");
        for (WorkerItem item : items) {
            ObjectNode node = array.addObject();
            node.put("id", item.id());
            node.put("text", item.text());
            node.set("features", item.features() == null ? objectMapper.createObjectNode() : item.features());
        }
        return sendJson("POST", "/topics", request, timeout);
    }

    public JsonNode postJson(String path, JsonNode body) {
        return sendJson("POST", path, body, timeout);
    }

    public JsonNode getJson(String path) {
        return sendJson("GET", path, null, timeout);
    }

    private JsonNode sendJson(String method, String path, JsonNode body, Duration requestTimeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json");
            if ("POST".equals(method)) {
                String requestBody = json(body);
                builder.header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody.getBytes(StandardCharsets.UTF_8)));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Model worker HTTP " + response.statusCode() + " for " + path
                        + " request=" + summarize(body) + ": " + truncate(response.body(), 600));
            }
            return objectMapper.readTree(response.body());
        } catch (IOException error) {
            throw new IllegalStateException("Model worker request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model worker request interrupted", error);
        }
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot serialize model worker request", error);
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : fallback;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8095";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ");
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }

    private String summarize(JsonNode body) {
        if (body == null || body.isNull()) {
            return "null";
        }
        String firstField = body.fieldNames().hasNext() ? body.fieldNames().next() : "<none>";
        return "fields=" + firstField + ",items=" + body.path("items").size();
    }

    public record WorkerHealth(
            boolean reachable,
            String status,
            String classifierStatus,
            String classifierName,
            String embeddingStatus,
            String embeddingName,
            int embeddingDimension,
            JsonNode raw
    ) {
        public boolean classifierReady() {
            return reachable && "OK".equalsIgnoreCase(classifierStatus);
        }

        public boolean embeddingsReady() {
            return reachable && "OK".equalsIgnoreCase(embeddingStatus);
        }
    }

    public record WorkerItem(String id, String text, JsonNode features) {
    }
}
