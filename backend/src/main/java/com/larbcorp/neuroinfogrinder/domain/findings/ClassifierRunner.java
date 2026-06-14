package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        "cursor", "codex", "openai", "anthropic", "model", "модел", "api", "чат гпт", "клауд", "клод"
    );
    private static final List<String> VALUE_MARKERS = List.of(
        "free", "бесплат", "халява", "$", "скидк", "credits", "кредит", "доллар",
        "лимит", "доступ", "today", "сегодня", "завтра", "until", "до завтра"
    );
    private static final List<String> GUIDE_MARKERS = List.of(
        "guide", "гайд", "tutorial", "туториал", "how to", "как сделать", "шаг",
        "инструкц", "step 1", "1.", "2.", "3."
    );
    private static final List<String> TOOL_MARKERS = List.of(
        "docker", "n8n", "comfyui", "cursor", "codex", "repo", "github", "sdk"
    );
    private static final List<String> RELEASE_MARKERS = List.of(
        "release", "релиз", "launched", "launch", "update", "обновление", "new model", "новая модель"
    );

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
        AiProviderEntity provider = resolveProvider(classifier).orElse(null);
        if (provider == null) {
            if (classifier.getProviderId() == null) {
                return new ClassifierResult(0.0, false, "Classifier has no provider configured and no active provider available");
            }
            return new ClassifierResult(0.0, false, "Provider not found: " + classifier.getProviderId() + "; no active provider fallback available");
        }

        AiCompletionResponse response = aiClientService.complete(
            provider.getEndpointUrl(),
            provider.getApiKeyEncrypted(),
            provider.getModel() != null ? provider.getModel() : "gpt-4o-mini",
            buildClassifierSystemPrompt(classifier),
            buildChainContext(chain),
            0.3,
            1024
        );

        if (!response.success()) {
            return new ClassifierResult(0.0, false, "AI request failed: " + response.error());
        }

        return parseClassifierResponse(response.content(), chain);
    }

    private Optional<AiProviderEntity> resolveProvider(ClassifierEntity classifier) {
        if (classifier.getProviderId() != null) {
            Optional<AiProviderEntity> configuredProvider = aiProviderRepository.findById(classifier.getProviderId());
            if (configuredProvider.isPresent()) {
                return configuredProvider;
            }
            log.warn("Classifier {} references missing providerId={}; falling back to first active provider",
                classifier.getId(), classifier.getProviderId());
        } else {
            log.warn("Classifier {} has no providerId; falling back to first active provider", classifier.getId());
        }
        return findActiveProvider();
    }

    private Optional<AiProviderEntity> findActiveProvider() {
        return aiProviderRepository.findAll().stream()
            .filter(provider -> {
                String status = provider.getStatus();
                return "ACTIVE".equalsIgnoreCase(status)
                    || "HEALTHY".equalsIgnoreCase(status)
                    || "WARNING".equalsIgnoreCase(status);
            })
            .sorted(Comparator
                .comparing((AiProviderEntity provider) -> "MOCK".equalsIgnoreCase(provider.getProtocol()))
                .thenComparing(AiProviderEntity::getId))
            .findFirst();
    }

    private ClassifierResult classifyWithKeywords(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getKeywords() == null || classifier.getKeywords().isBlank()) {
            return new ClassifierResult(0.0, false, "No keywords defined for classifier");
        }

        String combinedText = chain.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null)
            .collect(Collectors.joining(" "))
            .toLowerCase();

        int matchCount = 0;
        List<String> matchedKeywords = new ArrayList<>();
        for (String keyword : classifier.getKeywords().split(",")) {
            String trimmed = keyword.trim().toLowerCase();
            if (!trimmed.isEmpty() && combinedText.contains(trimmed)) {
                matchCount++;
                matchedKeywords.add(trimmed);
            }
        }

        double score = matchCount == 0 ? 0.0 : Math.min(1.0, 0.55 + Math.max(0, matchCount - 1) * 0.12);
        return enrichDeterministicResult(
            score,
            matchCount > 0,
            matchCount > 0 ? "Matched keywords: " + String.join(", ", matchedKeywords) : "No keywords matched",
            chain
        );
    }

    private ClassifierResult classifyWithRegex(List<MessageEntity> chain, ClassifierEntity classifier) {
        if (classifier.getRegexPattern() == null || classifier.getRegexPattern().isBlank()) {
            return new ClassifierResult(0.0, false, "No regex pattern defined for classifier");
        }

        try {
            Pattern pattern = Pattern.compile(classifier.getRegexPattern(), Pattern.CASE_INSENSITIVE);
            String combinedText = chain.stream()
                .map(MessageEntity::getText)
                .filter(text -> text != null)
                .collect(Collectors.joining(" "));
            boolean matched = pattern.matcher(combinedText).find();
            return enrichDeterministicResult(
                matched ? 1.0 : 0.0,
                matched,
                matched ? "Regex pattern matched" : "Regex pattern did not match",
                chain
            );
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

            double score = "clamp".equalsIgnoreCase(normalization) ? clamp01(rawScore) : sigmoid(rawScore);
            boolean matched = score >= threshold;
            String topContributions = contributions.entrySet().stream()
                .sorted((left, right) -> Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue())))
                .limit(5)
                .map(entry -> {
                    double value = featureValues.getOrDefault(entry.getKey(), 0.0);
                    return entry.getKey() + "=" + formatDouble(value) + " -> " + formatDouble(entry.getValue());
                })
                .collect(Collectors.joining(", "));

            return enrichDeterministicResult(
                score,
                matched,
                "LINEAR_MODEL score=" + formatDouble(score)
                    + " threshold=" + formatDouble(threshold)
                    + " raw=" + formatDouble(rawScore)
                    + (topContributions.isBlank() ? "" : " | top: " + topContributions),
                chain
            );
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
        LeadSignalAnalysis leadSignals = LeadSignalAnalyzer.analyze(combinedText);

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
        features.put("has_deadline_marker", containsAny(combinedText, List.of("today", "сегодня", "tomorrow", "завтра", "limited")) ? 1.0 : 0.0);
        features.put("has_provider_mention", leadSignals.labels().contains("PROVIDER_MENTION") ? 1.0 : 0.0);
        features.put("has_access_demand", leadSignals.labels().contains("AI_ACCESS_DEMAND") ? 1.0 : 0.0);
        features.put("has_payment_workaround", leadSignals.labels().contains("PAYMENT_WORKAROUND") ? 1.0 : 0.0);
        features.put("has_pain_limits", leadSignals.labels().contains("PAIN_LIMITS") ? 1.0 : 0.0);
        features.put("has_offer_or_spam", leadSignals.labels().contains("OFFER_OR_SPAM") ? 1.0 : 0.0);
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
        if (classifier.getPromptId() != null) {
            PromptEntity prompt = promptRepository.findById(classifier.getPromptId()).orElse(null);
            if (prompt != null) {
                promptContent = prompt.getContent();
            }
        }
        if (promptContent != null) {
            return promptContent;
        }

        return """
            You are a context-aware content classifier for a knowledge extraction system.
            Analyze the provided Telegram context bundle and determine whether it contains useful information.

            Return strict JSON:
            {
              "score": 0.0,
              "matched": false,
              "labels": ["DEMAND_SIGNAL"],
              "guide_candidate": false,
              "evidence_message_ids": [123],
              "reasoning": "Короткое объяснение на русском"
            }

            Allowed labels only:
            DEMAND_SIGNAL, SOLUTION_MENTION, VENDOR_OR_SOURCE, BUG_OR_LIMITATION,
            PAYMENT_WORKAROUND, AI_TOOL_OR_PROVIDER, PRACTICAL_GUIDE_CANDIDATE,
            OPPORTUNITY, SPAM_OR_AD, NOT_USEFUL.

            Rules:
            - matched=false means guide_candidate=false.
            - A single unanswered demand can still be matched=true, but usually guide_candidate=false.
            - Concrete vendor/source/offer messages can be matched=true, but usually not full guide candidates.
            - Use only internalMessageId values from the provided context in evidence_message_ids.
            - Do not invent labels outside the allowlist.
            - Preserve useful discussions about AI access, providers, payments, limits, bugs, workarounds, and sources.
            """;
    }

    private String buildChainContext(List<MessageEntity> chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context bundle to classify:\n\n");
        for (MessageEntity msg : chain) {
            String sender = msg.getSenderName() != null ? msg.getSenderName() : "Unknown";
            String botTag = Boolean.TRUE.equals(msg.getIsBot()) ? " [BOT]" : "";
            sb.append("- internalMessageId: ").append(msg.getId()).append("\n");
            sb.append("  telegramMessageId: ").append(msg.getTelegramMessageId()).append("\n");
            sb.append("  topicId: ").append(msg.getTopicId()).append("\n");
            sb.append("  replyToTelegramMessageId: ").append(msg.getReplyToMessageId()).append("\n");
            sb.append("  sender: ").append(sender).append(botTag).append("\n");
            sb.append("  text: ").append(msg.getText() != null ? msg.getText() : "").append("\n\n");
        }
        return sb.toString();
    }

    private ClassifierResult parseClassifierResponse(String content, List<MessageEntity> chain) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            double score = clamp01(node.path("score").asDouble(0.0));
            boolean matched = node.has("matched") ? node.path("matched").asBoolean(score >= 0.55) : score >= 0.55;
            List<String> labels = parseLabels(node.path("labels"));
            boolean guideCandidate = node.has("guide_candidate")
                ? node.path("guide_candidate").asBoolean(false)
                : matched && score >= 0.75;
            List<Long> evidenceIds = parseEvidenceMessageIds(node.path("evidence_message_ids"), chain);
            String reasoning = node.path("reasoning").asText("No reasoning provided");

            if (matched != (score >= 0.55)) {
                log.warn("Classifier matched/score mismatch normalized: matched={} score={}", matched, score);
                matched = score >= 0.55;
            }
            if (!matched) {
                guideCandidate = false;
            }
            if (matched && labels.isEmpty()) {
                labels = List.of(guideCandidate
                    ? ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE
                    : ClassificationLabels.DEMAND_SIGNAL);
            }
            if (guideCandidate && !labels.contains(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE)) {
                List<String> augmented = new ArrayList<>(labels);
                augmented.add(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE);
                labels = ClassificationLabels.sanitize(augmented);
            }

            return new ClassifierResult(score, matched, labels, guideCandidate, evidenceIds, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse classifier LLM response as JSON, using fallback: {}", e.getMessage());
            return new ClassifierResult(0.30, false, "Fallback parsing (JSON failed): " + truncate(content, 200));
        }
    }

    private ClassifierResult enrichDeterministicResult(double score, boolean matched, String reasoning, List<MessageEntity> chain) {
        LeadSignalAnalysis leadSignals = LeadSignalAnalyzer.analyze(chain.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null && !text.isBlank())
            .collect(Collectors.joining(" \n ")));

        List<String> labels = new ArrayList<>();
        if (leadSignals.leadCandidate()) {
            labels.add(ClassificationLabels.DEMAND_SIGNAL);
        }
        if (leadSignals.labels().contains("PAYMENT_WORKAROUND")) {
            labels.add(ClassificationLabels.PAYMENT_WORKAROUND);
        }
        if (leadSignals.labels().contains("PROVIDER_MENTION")) {
            labels.add(ClassificationLabels.AI_TOOL_OR_PROVIDER);
        }
        if (leadSignals.labels().contains("PAIN_LIMITS")) {
            labels.add(ClassificationLabels.BUG_OR_LIMITATION);
            labels.add(ClassificationLabels.OPPORTUNITY);
        }
        if (leadSignals.labels().contains("OFFER_OR_SPAM")) {
            labels.add(ClassificationLabels.SPAM_OR_AD);
            labels.add(ClassificationLabels.VENDOR_OR_SOURCE);
        }

        boolean guideCandidate = matched && score >= 0.75
            && (labels.contains(ClassificationLabels.VENDOR_OR_SOURCE)
            || labels.contains(ClassificationLabels.SOLUTION_MENTION)
            || labels.contains(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE));

        return new ClassifierResult(
            score,
            matched,
            ClassificationLabels.sanitize(labels),
            guideCandidate,
            chain.stream().map(MessageEntity::getId).limit(3).toList(),
            reasoning
        );
    }

    private String extractJson(String content) {
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

        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return json.substring(braceStart, braceEnd + 1);
        }
        return json;
    }

    private List<String> parseLabels(JsonNode labelsNode) {
        if (!labelsNode.isArray()) {
            return List.of();
        }

        List<String> raw = new ArrayList<>();
        labelsNode.forEach(item -> raw.add(item.asText("")));
        List<String> sanitized = ClassificationLabels.sanitize(raw);
        if (sanitized.size() != raw.stream().filter(value -> value != null && !value.isBlank()).count()) {
            log.warn("Classifier returned unknown labels: {}", raw);
        }
        return sanitized;
    }

    private List<Long> parseEvidenceMessageIds(JsonNode evidenceNode, List<MessageEntity> chain) {
        if (!evidenceNode.isArray()) {
            return List.of();
        }

        List<Long> allowed = chain.stream().map(MessageEntity::getId).toList();
        List<Long> result = new ArrayList<>();
        evidenceNode.forEach(item -> {
            long value = item.asLong(-1L);
            if (allowed.contains(value) && !result.contains(value)) {
                result.add(value);
            }
        });
        return List.copyOf(result);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
