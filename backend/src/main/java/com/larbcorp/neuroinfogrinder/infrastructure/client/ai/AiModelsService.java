package com.larbcorp.neuroinfogrinder.infrastructure.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiModelsService {

    private static final String DEFAULT_MODELS_ENDPOINT = "https://papus.net/v1/models";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private List<String> cachedModels = null;
    private Instant cacheTimestamp = null;

    public AiModelsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public List<String> getAvailableModels(String endpointUrl, String apiKey) {
        if (isCacheValid()) {
            return new ArrayList<>(cachedModels);
        }

        String endpoint = (endpointUrl != null && !endpointUrl.isBlank())
            ? endpointUrl
            : DEFAULT_MODELS_ENDPOINT;

        // If a completions endpoint is provided, derive the models endpoint
        if (endpoint.contains("/chat/completions")) {
            endpoint = endpoint.replace("/chat/completions", "/models");
        } else if (!endpoint.endsWith("/models")) {
            endpoint = endpoint.replaceAll("/$", "") + "/models";
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .GET()
                .timeout(Duration.ofSeconds(15));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch models from {}: status {}", endpoint, response.statusCode());
                return List.of();
            }

            List<String> models = parseModelsResponse(response.body());

            this.cachedModels = models;
            this.cacheTimestamp = Instant.now();

            log.info("Fetched {} available models from {}", models.size(), endpoint);
            return new ArrayList<>(models);

        } catch (Exception e) {
            log.error("Error fetching models from {}: {}", endpoint, e.getMessage());
            return List.of();
        }
    }

    /**
     * Convenience method using default Papus endpoint and no API key.
     */
    public List<String> getAvailableModels() {
        return getAvailableModels(null, null);
    }

    private List<String> parseModelsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                log.warn("Unexpected models response format: 'data' is not an array");
                return List.of();
            }

            List<String> models = new ArrayList<>();
            for (JsonNode modelNode : data) {
                String id = modelNode.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(id);
                }
            }

            models.sort(String::compareTo);
            return models;

        } catch (Exception e) {
            log.error("Error parsing models response: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isCacheValid() {
        if (cachedModels == null || cacheTimestamp == null) {
            return false;
        }
        return Instant.now().isBefore(cacheTimestamp.plusSeconds(CACHE_TTL_SECONDS));
    }

    /**
     * Force-clears the cache, useful after provider changes.
     */
    public void invalidateCache() {
        this.cachedModels = null;
        this.cacheTimestamp = null;
    }
}
