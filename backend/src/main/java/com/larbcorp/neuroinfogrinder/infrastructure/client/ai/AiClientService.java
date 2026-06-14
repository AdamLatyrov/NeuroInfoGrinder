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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiClientService {

    private static final String DEFAULT_ENDPOINT = "https://papus.net/v1/chat/completions";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_PATTERN = Pattern.compile("```|`[^`\\n]+`|\\b(curl|npm|pnpm|yarn|docker|git|mvn|gradle|python|java)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_PATTERN = Pattern.compile("(?m)^\\s*(\\d+[.)]\\s+|[-*•]\\s+).+");
    private static final Pattern GUIDE_PATTERN = Pattern.compile("\\b(guide|tutorial|how\\s+to|how-to|checklist|step\\s+\\d+|\\u0433\\u0430\\u0439\\u0434|\\u0438\\u043d\\u0441\\u0442\\u0440\\u0443\\u043a\\u0446|\\u0448\\u0430\\u0433\\s+\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASE_PATTERN = Pattern.compile("\\b(release|launched|launch|update|pricing|credits?|limits?|quota|access|api|endpoint|model|benchmark|tokens?|context|\\u0440\\u0435\\u043b\\u0438\\u0437|\\u043e\\u0431\\u043d\\u043e\\u0432\\u043b\\u0435\\u043d\\u0438\\u0435|\\u0446\\u0435\\u043d\\u0430|\\u043b\\u0438\\u043c\\u0438\\u0442|\\u0434\\u043e\\u0441\\u0442\\u0443\\u043f|\\u0442\\u043e\\u043a\\u0435\\u043d)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_PATTERN = Pattern.compile("(\\?|\\bhow\\b|\\bwhere\\b|\\bwhy\\b|\\banyone\\b|\\bhelp\\b|\\b\\u043a\\u0430\\u043a\\b|\\b\\u0433\\u0434\\u0435\\b|\\b\\u043f\\u043e\\u0447\\u0435\\u043c\\u0443\\b|\\b\\u043a\\u0442\\u043e\\s+\\u043d\\u0438\\u0431\\u0443\\u0434\\u044c\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN = Pattern.compile("\\b(do|use|setup|configure|connect|run|install|register|check|get|take|switch|compare|test|deploy|enable|\\u0441\\u0434\\u0435\\u043b\\u0430\\u0439|\\u043d\\u0443\\u0436\\u043d\\u043e|\\u043d\\u0430\\u0434\\u043e|\\u0437\\u0430\\u043f\\u0443\\u0441\\u0442\\u0438\\u0442\\u044c|\\u043d\\u0430\\u0441\\u0442\\u0440\\u043e\\u0438\\u0442\\u044c|\\u043f\\u043e\\u0434\\u043a\\u043b\\u044e\\u0447\\u0438\\u0442\\u044c|\\u0440\\u0435\\u0433\\u0438\\u0441\\u0442\\u0440\\u0438\\u0440\\u0443\\u0435\\u043c\\u0441\\u044f|\\u0437\\u0430\\u0445\\u043e\\u0434\\u0438\\u043c)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESS_DEMAND_PATTERN = Pattern.compile("\\b(where|buy|gift|gifts|account|accounts|provider|proxy|endpoint|plus|access|unlimited|quota|limit|fanpay|payment|card|sbp|\\u0433\\u0434\\u0435|\\u043a\\u0443\\u043f\\u0438\\u0442\\u044c|\\u043f\\u043e\\u043a\\u0443\\u043f\\u0430\\u0442\\u044c|\\u0433\\u0438\\u0444\\u0442|\\u0430\\u043a\\u043a|\\u0430\\u043a\\u043a\\u0430\\u0443\\u043d\\u0442|\\u0431\\u0435\\u0437\\u043b\\u0438\\u043c\\u0438\\u0442|\\u043b\\u0438\\u043c\\u0438\\u0442|\\u0434\\u043e\\u0440\\u043e\\u0433|\\u0444\\u0430\\u043d\\u043f\\u0435\\u0439|\\u043a\\u0430\\u0440\\u0442\\u0430|\\u0441\\u0431\\u043f|\\u043e\\u043f\\u043b\\u0430\\u0442|\\u043f\\u043e\\u043f\\u043e\\u043b\\u043d|\\u043a\\u0438\\u0442\\u0430\\u0439\\u0446\\u0435\\u0432)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHATTER_PATTERN = Pattern.compile("\\b(lol|lmao|bro|dude|thanks|thank\\s+you|okay|ok|sure|got\\s+it|nice|cool|hype|trash|sucks|\\u043b\\u043e\\u043b|\\u0430\\u0445\\u0430\\u0445|\\u0430\\u0433\\u0430|\\u043f\\u043e\\u043d\\u044f\\u043b|\\u0441\\u043f\\u0430\\u0441\\u0438\\u0431\\u043e|\\u0436\\u0435\\u0441\\u0442\\u044c|\\u043a\\u0440\\u0438\\u043d\\u0436)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BENCHMARK_PATTERN = Pattern.compile("\\b(benchmark|latency|pricing|tokens?|context|window|sdk|endpoint|quota|credits?|rate\\s*limit|release\\s*notes?|\\u0431\\u0435\\u043d\\u0447|\\u0442\\u043e\\u043a\\u0435\\u043d|\\u043a\\u043e\\u043d\\u0442\\u0435\\u043a\\u0441\\u0442|\\u043f\\u0440\\u0430\\u0439\\u0441)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMO_PATTERN = Pattern.compile("\\b(ref|referral|affiliate|subscribe|bonus|promo|coupon|discount|\\u0440\\u0435\\u0444|\\u0431\\u043e\\u043d\\u0443\\u0441|\\u0431\\u0435\\u0441\\u043f\\u043b\\u0430\\u0442|\\u0441\\u043a\\u0438\\u0434\\u043a)\\b|t\\.me/|youtube\\.com|youtu\\.be", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASUAL_REPLY_PATTERN = Pattern.compile("^\\s*(ok|okay|yeah|yep|nah|bro|dude|thanks|thank\\s+you|sure|got\\s+it|\\u043e\\u043a|\\u0430\\u0433\\u0430|\\u0441\\u043f\\u0430\\u0441\\u0438\\u0431\\u043e|\\u043f\\u043e\\u043d\\u044f\\u043b)\\b", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public AiCompletionResponse complete(AiCompletionRequest request) {
        String endpoint = normalizeEndpoint(request.endpointUrl());

        try {
            if (isMockEndpoint(endpoint, request.model())) {
                return mockCompletion(request);
            }

            String requestBody = buildRequestBody(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

            log.debug("Sending AI completion request to endpoint: {}, model: {}", endpoint, request.model());

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("AI API returned status {}: {}", response.statusCode(), response.body());
                return new AiCompletionResponse(
                    null, 0, 0, 0, request.model(), false,
                    "API returned status " + response.statusCode() + ": " + truncate(response.body(), 500)
                );
            }

            return parseResponse(response.body(), request.model());

        } catch (Exception e) {
            log.error("Error calling AI API: {}", e.getMessage(), e);
            return new AiCompletionResponse(
                null, 0, 0, 0, request.model(), false,
                "Request failed: " + e.getMessage()
            );
        }
    }

    private String normalizeEndpoint(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return DEFAULT_ENDPOINT;
        }
        String url = endpointUrl.trim();
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        if (url.endsWith("/v1") || url.endsWith("/v1/")) {
            return url.replaceAll("/v1/?$", "/v1/chat/completions");
        }
        return url.replaceAll("/+$", "") + "/chat/completions";
    }

    private boolean isMockEndpoint(String endpoint, String model) {
        return (endpoint != null && endpoint.startsWith("mock://"))
            || (model != null && model.toLowerCase(Locale.ROOT).contains("mock"));
    }

    private AiCompletionResponse mockCompletion(AiCompletionRequest request) {
        String prompt = request.messages().stream()
            .map(AiMessage::content)
            .collect(Collectors.joining("\n"));

        String content;
        if (prompt.contains("\"title\"") || prompt.toLowerCase(Locale.ROOT).contains("contentmarkdown")) {
            content = buildMockGuideJson(prompt);
        } else if (prompt.contains("Connection test successful")) {
            content = "Connection test successful";
        } else {
            content = buildMockClassificationJson(prompt);
        }

        return new AiCompletionResponse(content, 32, 64, 96, request.model(), true, null);
    }

    private String buildMockClassificationJson(String prompt) {
        String relevantText = extractClassificationText(prompt);
        String normalized = relevantText.toLowerCase(Locale.ROOT);
        int textLength = normalized.length();

        boolean hasAiTopic = containsAny(normalized,
            "gpt", "claude", "anthropic", "openai", "api", "model", "codex", "cursor", "kimi", "gemini", "deepseek", "mistral",
            "\u043c\u043e\u0434\u0435\u043b\u044c", "\u043d\u0435\u0439\u0440\u043e\u0441\u0435\u0442", "\u043a\u043b\u043e\u0434");
        boolean hasUrl = URL_PATTERN.matcher(normalized).find();
        boolean hasCode = CODE_PATTERN.matcher(normalized).find();
        boolean hasList = LIST_PATTERN.matcher(relevantText).find();
        boolean hasGuide = GUIDE_PATTERN.matcher(normalized).find();
        boolean hasRelease = RELEASE_PATTERN.matcher(normalized).find();
        boolean hasQuestion = QUESTION_PATTERN.matcher(normalized).find();
        boolean hasAction = ACTION_PATTERN.matcher(normalized).find();
        boolean looksChatty = CHATTER_PATTERN.matcher(normalized).find();
        boolean hasBenchmark = BENCHMARK_PATTERN.matcher(normalized).find();
        boolean looksPromotional = PROMO_PATTERN.matcher(normalized).find();
        boolean startsCasual = CASUAL_REPLY_PATTERN.matcher(normalized).find();
        boolean hasAccessDemand = ACCESS_DEMAND_PATTERN.matcher(normalized).find();
        boolean hasPaymentWorkaround = containsAny(normalized,
            "fanpay", "\u0444\u0430\u043d\u043f\u0435", "\u043a\u0438\u0442\u0430\u0439", "\u044e\u0430\u043d",
            "\u043e\u043f\u043b\u0430\u0442", "\u043f\u043e\u043f\u043e\u043b\u043d", "\u0441\u0431\u043f");
        boolean hasLeadIntent = hasAccessDemand && (
            hasAiTopic
                || containsAny(normalized,
                "\u0430\u043f\u0438", "claude", "chatgpt", "chat gpt", "gpt", "openrouter",
                "\u0444\u0430\u043d\u043f\u0435", "fanpay", "\u043a\u0438\u0442\u0430\u0439", "\u0441\u0431\u043f")
        );
        int questionMarks = countMatches(relevantText, '?');

        int strongSignalCount = 0;
        if (hasGuide) strongSignalCount++;
        if (hasCode) strongSignalCount++;
        if (hasList) strongSignalCount++;
        if (hasUrl && (hasAction || hasRelease || hasBenchmark)) strongSignalCount++;
        if (hasRelease && (hasBenchmark || textLength >= 420)) strongSignalCount++;
        if (textLength >= 520 && (hasRelease || hasGuide || hasBenchmark)) strongSignalCount++;

        double score = 0.0;
        if (hasAiTopic) score += 0.12;
        if (hasGuide) score += 0.26;
        if (hasRelease) score += 0.14;
        if (hasUrl) score += 0.10;
        if (hasCode) score += 0.22;
        if (hasList) score += 0.18;
        if (hasAction) score += 0.12;
        if (hasBenchmark) score += 0.10;
        if (hasAccessDemand) score += 0.30;
        if (hasPaymentWorkaround) score += 0.42;
        if (hasLeadIntent) score += 0.52;
        if (textLength >= 420) score += 0.12;
        else if (textLength >= 240) score += 0.05;
        if (strongSignalCount >= 3) score += 0.12;
        if (hasQuestion && !hasAction && !hasGuide && !hasCode && !hasList) score -= 0.22;
        if (questionMarks >= 2 && strongSignalCount < 2) score -= 0.12;
        if (looksChatty && !hasUrl && !hasCode && !hasGuide) score -= 0.28;
        if (looksPromotional && !hasCode && !hasGuide && !hasList) score -= 0.28;
        if (startsCasual && !hasCode && !hasGuide && !hasList) score -= 0.12;
        if (textLength < 160 && !hasCode && !hasList && !hasGuide && !hasUrl && !hasPaymentWorkaround) score -= 0.30;

        score = Math.max(0.0, Math.min(1.0, score));
        boolean matched = (
            ((hasLeadIntent || hasAccessDemand || hasPaymentWorkaround) && score >= 0.30)
                || (
                    score >= 0.80
                        && (
                        hasGuide
                            || hasCode
                            || hasList
                            || (hasUrl && (hasAction || hasRelease || hasBenchmark))
                            || (hasRelease && hasBenchmark && textLength >= 420)
                            || hasLeadIntent
                    )
                )
            )
            && !(looksChatty && !hasCode && !hasGuide && !hasList)
            && !(looksPromotional && !hasCode && !hasGuide && !hasList && !hasAccessDemand);
        boolean guideCandidate = matched && score >= 0.75
            && (hasGuide || hasCode || hasList || (hasUrl && hasAction) || (hasRelease && hasBenchmark));

        List<String> labels = new java.util.ArrayList<>();
        if (hasAccessDemand) labels.add("DEMAND_SIGNAL");
        if (hasPaymentWorkaround) labels.add("PAYMENT_WORKAROUND");
        if (hasAiTopic) labels.add("AI_TOOL_OR_PROVIDER");
        if (hasUrl || hasAction) labels.add("SOLUTION_MENTION");
        if (hasUrl || looksPromotional) labels.add("VENDOR_OR_SOURCE");
        if (looksPromotional) labels.add("SPAM_OR_AD");
        if (normalized.contains("лимит") || normalized.contains("quota") || normalized.contains("error")) {
            labels.add("BUG_OR_LIMITATION");
        }
        if (guideCandidate) labels.add("PRACTICAL_GUIDE_CANDIDATE");
        if ((hasAccessDemand || hasPaymentWorkaround) && !guideCandidate) labels.add("OPPORTUNITY");
        if (!matched && labels.isEmpty()) labels.add("NOT_USEFUL");
        labels = labels.stream().distinct().toList();
        List<Long> evidenceMessageIds = extractInternalMessageIds(prompt);

        String reasoning = "mock heuristic | aiTopic=" + hasAiTopic
            + ", guide=" + hasGuide
            + ", release=" + hasRelease
            + ", action=" + hasAction
            + ", accessDemand=" + hasAccessDemand
            + ", paymentWorkaround=" + hasPaymentWorkaround
            + ", url=" + hasUrl
            + ", code=" + hasCode
            + ", list=" + hasList
            + ", benchmark=" + hasBenchmark
            + ", chatty=" + looksChatty
            + ", promo=" + looksPromotional
            + ", strongSignals=" + strongSignalCount
            + ", len=" + textLength;

        return "{\n"
            + "  \"score\": " + formatDecimal(score) + ",\n"
            + "  \"matched\": " + matched + ",\n"
            + "  \"labels\": " + toJsonArray(labels) + ",\n"
            + "  \"guide_candidate\": " + guideCandidate + ",\n"
            + "  \"evidence_message_ids\": " + toJsonNumberArray(evidenceMessageIds) + ",\n"
            + "  \"reasoning\": " + escapeJsonString(reasoning) + "\n"
            + "}";
    }

    private List<Long> extractInternalMessageIds(String prompt) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("internalMessageId:\\s*(\\d+)").matcher(prompt);
        List<Long> ids = new java.util.ArrayList<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids.stream().limit(3).toList();
    }

    private String toJsonArray(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(escapeJsonString(values.get(i)));
        }
        json.append(']');
        return json.toString();
    }

    private String toJsonNumberArray(List<Long> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(values.get(i));
        }
        json.append(']');
        return json.toString();
    }

    private String extractClassificationText(String prompt) {
        String[] markers = {
            "Context bundle to classify:",
            "Message chain to classify:",
            "Source message chain:",
            "Messages to classify:"
        };
        for (String marker : markers) {
            int index = prompt.indexOf(marker);
            if (index >= 0) {
                return prompt.substring(index + marker.length()).trim();
            }
        }
        return prompt;
    }

    private String buildMockGuideJson(String prompt) {
        String source = extractSourceChain(prompt);
        String title = extractGuideTitle(source);
        String summary = buildGuideSummary(source);
        String highlights = buildHighlights(source);
        String tagsJson = buildMockTagsJson(source);
        double confidence = source.length() >= 280 ? 0.78 : 0.66;

        String markdown = "# " + title + "\n\n"
            + summary + "\n\n"
            + "## Ключевые сообщения\n\n"
            + highlights;

        return "{\n"
            + "  \"title\": " + escapeJsonString(title) + ",\n"
            + "  \"content\": " + escapeJsonString(summary + "\n\n" + highlights) + ",\n"
            + "  \"contentMarkdown\": " + escapeJsonString(markdown) + ",\n"
            + "  \"confidence\": " + formatDecimal(confidence) + ",\n"
            + "  \"tags\": " + tagsJson + "\n"
            + "}";
    }

    private String extractSourceChain(String prompt) {
        int idx = prompt.indexOf("Source message chain:");
        if (idx >= 0) {
            return prompt.substring(idx + "Source message chain:".length()).trim();
        }
        return prompt;
    }

    private String extractGuideTitle(String source) {
        for (String line : source.split("\\R")) {
            String cleaned = line.replaceFirst("^\\[[^\\]]+\\]:\\s*", "").trim();
            if (cleaned.length() < 12) {
                continue;
            }
            return cleaned.length() > 90 ? cleaned.substring(0, 90) + "..." : cleaned;
        }
        return "Практическая заметка из обсуждения";
    }

    private String buildGuideSummary(String source) {
        String normalized = source.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 420) {
            normalized = normalized.substring(0, 420) + "...";
        }
        return "Краткая выжимка из обсуждения: " + normalized;
    }

    private String buildHighlights(String source) {
        StringBuilder result = new StringBuilder();
        int added = 0;
        for (String line : source.split("\\R")) {
            String cleaned = line.replaceFirst("^\\[[^\\]]+\\]:\\s*", "").trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.length() > 160) {
                cleaned = cleaned.substring(0, 160) + "...";
            }
            result.append("- ").append(cleaned).append("\n");
            added++;
            if (added >= 5) {
                break;
            }
        }
        if (added == 0) {
            result.append("- Не удалось извлечь ключевые сообщения");
        }
        return result.toString().trim();
    }

    private String buildMockTagsJson(String source) {
        List<String> tags = new java.util.ArrayList<>();
        String normalized = source.toLowerCase(Locale.ROOT);

        if (normalized.contains("api")) tags.add("API");
        if (normalized.contains("онбординг")) tags.add("онбординг");
        if (normalized.contains("paywall") || normalized.contains("пейвол")) tags.add("пейвол");
        if (normalized.contains("подпис")) tags.add("подписка");
        if (normalized.contains("игр")) tags.add("игры");
        if (normalized.contains("прилож")) tags.add("приложение");
        if (normalized.contains("google play")) tags.add("Google Play");
        if (tags.isEmpty()) tags.add("гайд");

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(escapeJsonString(tags.get(i)));
        }
        json.append(']');
        return json.toString();
    }

    private String buildRequestBody(AiCompletionRequest request) {
        try {
            var sb = new StringBuilder("[");
            for (int i = 0; i < request.messages().size(); i++) {
                AiMessage msg = request.messages().get(i);
                if (i > 0) sb.append(",");
                sb.append(objectMapper.writeValueAsString(
                    java.util.Map.of("role", msg.role(), "content", msg.content())
                ));
            }
            sb.append("]");

            return "{\"model\":" + escapeJsonString(request.model())
                + ",\"messages\":" + sb
                + ",\"temperature\":" + request.temperature()
                + ",\"max_tokens\":" + request.maxTokens()
                + "}";
        } catch (Exception e) {
            throw new RuntimeException("Failed to build AI request body", e);
        }
    }

    private AiCompletionResponse parseResponse(String responseBody, String requestedModel) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String content = "";
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                content = choices.get(0).path("message").path("content").asText("");
            }

            String model = root.path("model").asText(requestedModel);

            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;

            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                promptTokens = usage.path("prompt_tokens").asInt(0);
                completionTokens = usage.path("completion_tokens").asInt(0);
                totalTokens = usage.path("total_tokens").asInt(0);
            }

            log.debug("AI completion response: model={}, promptTokens={}, completionTokens={}, totalTokens={}",
                model, promptTokens, completionTokens, totalTokens);

            return new AiCompletionResponse(content, promptTokens, completionTokens, totalTokens, model, true, null);

        } catch (Exception e) {
            log.error("Error parsing AI API response: {}", e.getMessage(), e);
            return new AiCompletionResponse(
                null, 0, 0, 0, requestedModel, false,
                "Failed to parse response: " + e.getMessage()
            );
        }
    }

    private String escapeJsonString(String value) {
        if (value == null) return "null";
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private int countMatches(String value, char needle) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    public AiCompletionResponse complete(String endpointUrl, String apiKey, String model,
                                         String systemPrompt, String userPrompt,
                                         double temperature, int maxTokens) {
        List<AiMessage> messages = List.of(
            new AiMessage("system", systemPrompt),
            new AiMessage("user", userPrompt)
        );

        AiCompletionRequest request = new AiCompletionRequest(
            endpointUrl, apiKey, model, messages, temperature, maxTokens
        );

        return complete(request);
    }
}
