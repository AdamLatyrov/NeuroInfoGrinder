package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiMessage;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifierRunner {

    private final AiClientService aiClientService;
    private final AiProviderRepository aiProviderRepository;
    private final PromptRepository promptRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> AI_MARKERS = List.of(
        "gpt", "claude", "opus", "sonnet", "llm", "ai", "нейросет", "prompt",
        "cursor", "codex", "openai", "anthropic", "model", "модель", "api"
    );
    private static final List<String> VALUE_MARKERS = List.of(
        "free", "бесплат", "халява", "$", "скидк", "credits", "кредит", "дают",
        "лимит", "доступ", "today", "сегодня", "завтра", "until", "до завтра"
    );
    private static final List<String> GUIDE_MARKERS = List.of(
        "guide", "гайд", "tutorial", "туториал", "how to", "как сделать", "шаг",
        "инструкция", "step 1", "1.", "2.", "3."
    );
    private static final List<String> TOOL_MARKERS = List.of(
        "docker", "n8n", "comfyui", "cursor", "codex", "repo", "github", "sdk"
    );
    private static final List<String> RELEASE_MARKERS = List.of(
        "release", "релиз", "launched", "launch", "update", "обновление", "new model", "новая модель"
    );

    /**
     * Run a classifier against a chain of messages.
     */
    public ClassifierResult classify(List<MessageEntity> chain, ClassifierEntity classifier) {
        return switch (classifier.getType().toUpperCase()) {
            case "LLM" -> classifyWithLlm(chain, classifier);
            case "KEYWORD" -> classifyWithKeywords(chain, classifier);
            case "REGEX" -> classifyWithRegex(chain, classifier);
            case "LINEAR_MODEL" -> classifyWithLinearModel(chain, classifier);
            default -> {
                log.warn("Unknown classifier type: {}", classifier.getType());
                yield new ClassifierResult(0.0, false, "Unknown classifier type: " + classifier.getType());
            }
        };
    }

    private ClassifierResult classifyWithLlm(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getProviderId() == null) {
            return new ClassifierResult(0.0, false, "Classifier has no provider configured");
        }

        AiProviderEntity provider = aiProviderRepository.findById(classifier.getProviderId())
            .orElse(null);
        if (provider == null) {
            return new ClassifierResult(0.0, false, "Provider not found: " + classifier.getProviderId());
        }

        String systemPrompt = buildClassifierSystemPrompt(classifier);
        String userPrompt = buildChainContext(chain);

        AiCompletionResponse response = aiClientService.complete(
            provider.getEndpointUrl(),
            provider.getApiKeyEncrypted(),
            provider.getModel() != null ? provider.getModel() : "gpt-4o-mini",
            systemPrompt,
            userPrompt,
            0.3,
            1024
        );

        if (!response.success()) {
            return new ClassifierResult(0.0, false, "AI request failed: " + response.error());
        }

        return parseClassifierResponse(response.content());
    }

    private ClassifierResult classifyWithKeywords(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getKeywords() == null || classifier.getKeywords().isBlank()) {
            return new ClassifierResult(0.0, false, "No keywords defined for classifier");
        }

        String[] keywords = classifier.getKeywords().split(",");
        String combinedText = chain.stream()
            .map(MessageEntity::getText)
            .filter(t -> t != null)
            .collect(Collectors.joining(" "))
            .toLowerCase();

        int matchCount = 0;
        List<String> matchedKeywords = new ArrayList<>();

        for (String keyword : keywords) {
            String trimmed = keyword.trim().toLowerCase();
            if (!trimmed.isEmpty() && combinedText.contains(trimmed)) {
                matchCount++;
                matchedKeywords.add(trimmed);
            }
        }

        double score = matchCount == 0 ? 0.0 : Math.min(1.0, 0.55 + Math.max(0, matchCount - 1) * 0.12);
        boolean matched = matchCount > 0;
        String reasoning = matched
            ? "Matched keywords: " + String.join(", ", matchedKeywords)
            : "No keywords matched";

        return new ClassifierResult(score, matched, reasoning);
    }

    private ClassifierResult classifyWithRegex(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getRegexPattern() == null || classifier.getRegexPattern().isBlank()) {
            return new ClassifierResult(0.0, false, "No regex pattern defined for classifier");
        }

        try {
            Pattern pattern = Pattern.compile(classifier.getRegexPattern(), Pattern.CASE_INSENSITIVE);

            String combinedText = chain.stream()
                .map(MessageEntity::getText)
                .filter(t -> t != null)
                .collect(Collectors.joining(" "));

            boolean matched = pattern.matcher(combinedText).find();
            double score = matched ? 1.0 : 0.0;
            String reasoning = matched
                ? "Regex pattern matched"
                : "Regex pattern did not match";

            return new ClassifierResult(score, matched, reasoning);

        } catch (Exception e) {
            log.error("Invalid regex pattern in classifier {}: {}", classifier.getId(), e.getMessage());
            return new ClassifierResult(0.0, false, "Invalid regex: " + e.getMessage());
        }
    }

    private ClassifierResult classifyWithLinearModel(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getModelConfigJson() == null || classifier.getModelConfigJson().isBlank()) {
            return new ClassifierResult(0.0, false, "No modelConfig defined for LINEAR_MODEL classifier");
        }

        try {
            JsonNode config = objectMapper.readTree(classifier.getModelConfigJson());
            double bias = config.path("bias").asDouble(0.0);
            double threshold = config.path("threshold").asDouble(0.58);
            String normalization = config.path("normalization").asText("sigmoid");
            JsonNode featuresNode = config.path("features");
            if (!featuresNode.isObject()) {
                return new ClassifierResult(0.0, false, "modelConfig.features must be a JSON object");
            }

            Map<String, Double> featureValues = extractFeatureValues(chain);
            double rawScore = bias;
            Map<String, Double> contributions = new LinkedHashMap<>();

            featuresNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                double weight = entry.getValue().asDouble(0.0);
                double value = featureValues.getOrDefault(key, 0.0);
                contributions.put(key, weight * value);
            });

            for (double contribution : contributions.values()) {
                rawScore += contribution;
            }

            double score = "clamp".equalsIgnoreCase(normalization)
                ? clamp01(rawScore)
                : sigmoid(rawScore);
            boolean matched = score >= threshold;

            String topContributions = contributions.entrySet().stream()
                .sorted((left, right) -> Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue())))
                .limit(5)
                .map(entry -> {
                    double value = featureValues.getOrDefault(entry.getKey(), 0.0);
                    return entry.getKey() + "=" + formatDouble(value) + " -> " + formatDouble(entry.getValue());
                })
                .collect(Collectors.joining(", "));

            String reasoning = "LINEAR_MODEL score=" + formatDouble(score)
                + " threshold=" + formatDouble(threshold)
                + " raw=" + formatDouble(rawScore)
                + (topContributions.isBlank() ? "" : " | top: " + topContributions);

            return new ClassifierResult(score, matched, reasoning);
        } catch (Exception e) {
            log.warn("Failed to evaluate LINEAR_MODEL classifier {}: {}", classifier.getId(), e.getMessage());
            return new ClassifierResult(0.0, false, "Invalid LINEAR_MODEL config: " + e.getMessage());
        }
    }

    private Map<String, Double> extractFeatureValues(List<MessageEntity> chain) {
        String combinedText = chain.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null && !text.isBlank())
            .collect(Collectors.joining(" \n "))
            .toLowerCase();

        int replyCount = chain.stream()
            .map(MessageEntity::getReplyCount)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
        boolean hasTopic = chain.stream().anyMatch(message -> message.getTopicId() != null);
        boolean hasUrl = combinedText.contains("http://") || combinedText.contains("https://");
        boolean hasCodeMarker = combinedText.contains("```") || combinedText.contains("`")
            || containsAny(combinedText, List.of("npm ", "docker ", "git ", "curl ", "mvn "));
        boolean hasQuestionMarker = combinedText.contains("?")
            || containsAny(combinedText, List.of("как ", "почему", "где ", "зачем", "когда "));

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("text_length_norm", Math.min(1.0, combinedText.length() / 500.0));
        features.put("reply_count_norm", Math.min(1.0, replyCount / 5.0));
        features.put("has_url", hasUrl ? 1.0 : 0.0);
        features.put("has_topic", hasTopic ? 1.0 : 0.0);
        features.put("is_not_bot", chain.stream().anyMatch(message -> !Boolean.TRUE.equals(message.getIsBot())) ? 1.0 : 0.0);
        features.put("has_ai_marker", containsAny(combinedText, AI_MARKERS) ? 1.0 : 0.0);
        features.put("has_value_marker", containsAny(combinedText, VALUE_MARKERS) ? 1.0 : 0.0);
        features.put("has_guide_marker", containsAny(combinedText, GUIDE_MARKERS) ? 1.0 : 0.0);
        features.put("has_tool_marker", containsAny(combinedText, TOOL_MARKERS) ? 1.0 : 0.0);
        features.put("has_release_marker", containsAny(combinedText, RELEASE_MARKERS) ? 1.0 : 0.0);
        features.put("has_question_marker", hasQuestionMarker ? 1.0 : 0.0);
        features.put("has_code_marker", hasCodeMarker ? 1.0 : 0.0);
        features.put("has_price_marker", combinedText.contains("$") || combinedText.contains("usd") ? 1.0 : 0.0);
        features.put("has_deadline_marker", containsAny(combinedText, List.of("today", "сегодня", "tomorrow", "завтра", "до завтра", "limited")) ? 1.0 : 0.0);
        return features;
    }

    private boolean containsAny(String text, List<String> markers) {
        return markers.stream().anyMatch(text::contains);
    }

    private double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String formatDouble(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private String buildClassifierSystemPrompt(ClassifierEntity classifier) {
        String promptContent = null;

        // Use the classifier's associated prompt if available
        if (classifier.getPromptId() != null) {
            PromptEntity prompt = promptRepository.findById(classifier.getPromptId()).orElse(null);
            if (prompt != null) {
                promptContent = prompt.getContent();
            }
        }

        if (promptContent != null) {
            return promptContent;
        }

        // Default classifier system prompt
        return """
            You are a content classifier for a knowledge extraction system.
            Analyze the provided message chain and determine if it contains useful instructional or guide-like content.
            
            Respond with a JSON object containing:
            - "score": a float between 0.0 and 1.0 indicating how likely this is a guide-worthy conversation
            - "matched": a boolean indicating whether this content should be processed further
            - "reasoning": a brief explanation of your classification decision
            
            A score above 0.75 means the content is likely a useful guide or instruction.
            Look for: step-by-step instructions, problem-solution pairs, technical explanations, how-to content.
            """;
    }

    private String buildChainContext(List<MessageEntity> chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("Message chain to classify:\n\n");

        for (MessageEntity msg : chain) {
            String sender = msg.getSenderName() != null ? msg.getSenderName() : "Unknown";
            String botTag = Boolean.TRUE.equals(msg.getIsBot()) ? " [BOT]" : "";
            sb.append("[").append(sender).append(botTag).append("]: ");
            sb.append(msg.getText() != null ? msg.getText() : "");
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private ClassifierResult parseClassifierResponse(String content) {
        try {
            // Try to extract JSON from the response (may be wrapped in markdown code block)
            String json = content;
            if (content.contains("```json")) {
                int start = content.indexOf("```json") + 7;
                int end = content.indexOf("```", start);
                if (end > start) {
                    json = content.substring(start, end).trim();
                }
            } else if (content.contains("```")) {
                int start = content.indexOf("```") + 3;
                int end = content.indexOf("```", start);
                if (end > start) {
                    json = content.substring(start, end).trim();
                }
            }

            JsonNode node = objectMapper.readTree(json);
            double score = node.path("score").asDouble(0.0);
            boolean matched = node.path("matched").asBoolean(score >= 0.75);
            String reasoning = node.path("reasoning").asText("No reasoning provided");

            return new ClassifierResult(score, matched, reasoning);

        } catch (Exception e) {
            log.warn("Failed to parse classifier LLM response as JSON, using fallback: {}", e.getMessage());

            // Fallback: never set matched=true — rely on score threshold only
            // If JSON can't be parsed, the response is unreliable and should not pass
            double score = 0.30;
            return new ClassifierResult(score, false, "Fallback parsing (JSON failed): " + truncate(content, 200));
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
