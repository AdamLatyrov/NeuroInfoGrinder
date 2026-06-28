package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.messages.MessageTextFormatter;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
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

    private final ModelCallService modelCallService;
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

    private static final List<String> GENERAL_UTILITY_MARKERS = List.of(
        "crm", "onboarding", "feedback", "workflow", "lifehack", "process", "retention", "churn",
        "\u043b\u0430\u0439\u0444\u0445\u0430\u043a", "\u043f\u0440\u043e\u0446\u0435\u0441\u0441", "\u043e\u043d\u0431\u043e\u0440\u0434\u0438\u043d\u0433",
        "\u043e\u0431\u0440\u0430\u0442\u043d", "\u0441\u0432\u044f\u0437", "\u043a\u043b\u0438\u0435\u043d\u0442", "\u043f\u0440\u043e\u0434\u0430\u0436",
        "\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b", "\u043f\u0440\u043e\u0431\u043b\u0435\u043c", "\u043d\u0435\u043f\u043e\u043d\u044f\u0442\u043d", "\u0442\u0435\u0440\u044f"
    );

    public ClassifierResult classify(List<MessageEntity> chain, ClassifierEntity classifier) {
        return classify(chain, classifier, ModelCallPurpose.CLASSIFICATION);
    }

    public ClassifierResult classify(
        List<MessageEntity> chain,
        ClassifierEntity classifier,
        ModelCallPurpose purpose
    ) {
        return switch (classifier.getType().toUpperCase()) {
            case "LLM" -> classifyWithLlm(chain, classifier, purpose);
            case "KEYWORD" -> classifyWithKeywords(chain, classifier);
            case "REGEX" -> classifyWithRegex(chain, classifier);
            case "LINEAR_MODEL" -> classifyWithLinearModel(chain, classifier);
            default -> {
                log.warn("Unknown classifier type: {}", classifier.getType());
                yield new ClassifierResult(0.0, false, "Unknown classifier type: " + classifier.getType());
            }
        };
    }

    private ClassifierResult classifyWithLlm(
        List<MessageEntity> chain,
        ClassifierEntity classifier,
        ModelCallPurpose purpose
    ) {
        ModelCallService.ModelCallResult modelCall = modelCallService.complete(
            purpose,
            buildClassifierSystemPrompt(classifier),
            buildChainContext(chain),
            0.3,
            1024
        );
        AiCompletionResponse response = modelCall.response();

        if (!response.success()) {
            return withModelCallMetadata(
                new ClassifierResult(0.0, false, "AI request failed: " + response.error()),
                modelCall
            );
        }

        return withModelCallMetadata(parseClassifierResponse(response.content(), chain), modelCall);
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
        features.put("has_general_utility_marker", containsAny(combinedText, GENERAL_UTILITY_MARKERS) ? 1.0 : 0.0);
        features.put("has_question_marker", hasQuestionMarker ? 1.0 : 0.0);
        features.put("has_code_marker", hasCodeMarker ? 1.0 : 0.0);
        features.put("has_price_marker", combinedText.contains("$") || combinedText.contains("usd") ? 1.0 : 0.0);
        features.put("has_deadline_marker", containsAny(combinedText, List.of("today", "сегодня", "tomorrow", "завтра", "limited")) ? 1.0 : 0.0);
        features.put("has_provider_mention", leadSignals.labels().contains("PROVIDER_MENTION") ? 1.0 : 0.0);
        features.put("has_access_demand", leadSignals.labels().contains("AI_ACCESS_DEMAND") ? 1.0 : 0.0);
        features.put("has_payment_workaround", leadSignals.labels().contains("PAYMENT_WORKAROUND") ? 1.0 : 0.0);
        features.put("has_pain_limits", leadSignals.labels().contains("PAIN_LIMITS") ? 1.0 : 0.0);
        features.put("has_practical_problem", leadSignals.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM) ? 1.0 : 0.0);
        features.put("has_workflow_lifehack", leadSignals.labels().contains(ClassificationLabels.WORKFLOW_LIFEHACK) ? 1.0 : 0.0);
        features.put("has_business_process", leadSignals.labels().contains(ClassificationLabels.BUSINESS_PROCESS) ? 1.0 : 0.0);
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
            Analyze the provided Telegram context bundle and determine whether it contains generally useful information, не только AI.
            Useful content includes practical problems, CRM/product feedback, workflow discussions, business/process lessons, tools, bugs, лайфхаки, questions, and actionable answers.
            AI/tools/providers/payments remain useful categories, but they are not the whole scope.

            Return strict JSON:
            {
              "score": 0.0,
              "matched": false,
              "labels": ["DEMAND_SIGNAL"],
              "guide_candidate": false,
              "problem_signal_score": 0,
              "pain_score": 0,
              "willingness_to_pay_score": 0,
              "guide_potential_score": 0,
              "urgency_score": 0,
              "technical_depth_score": 0,
              "spam_score": 0,
              "meaning_summary": "Короткая суть сообщения",
              "problem_statement": "Какая проблема или боль выражена",
              "solution_hint": "Упомянутый обходной путь или решение, если есть",
              "mentioned_tools": ["Claude", "API"],
              "mentioned_prices": ["$20"],
              "mentioned_errors": ["429"],
              "categories": ["api_access"],
              "evidence_message_ids": [123],
              "reasoning": "Короткое объяснение на русском"
            }

            Allowed labels only:
            DEMAND_SIGNAL, SOLUTION_MENTION, VENDOR_OR_SOURCE, BUG_OR_LIMITATION,
            PAYMENT_WORKAROUND, AI_TOOL_OR_PROVIDER, PRACTICAL_PROBLEM,
            WORKFLOW_LIFEHACK, BUSINESS_PROCESS, PRODUCT_FEEDBACK,
            DISCUSSION_INSIGHT, PRACTICAL_GUIDE_CANDIDATE, OPPORTUNITY,
            SPAM_OR_AD, NOT_USEFUL.

            Rules:
            - matched=false means guide_candidate=false.
            - A single unanswered demand can still be matched=true, but usually guide_candidate=false.
            - Concrete vendor/source/offer messages can be matched=true, but usually not full guide candidates.
            - Use only internalMessageId values from the provided context in evidence_message_ids.
            - All *_score values are integers from 0 to 100.
            - problem_signal_score/pain_score/willingness_to_pay_score can be high even when guide_candidate=false.
            - spam_score is high for ads, irrelevant offers, scams, and repeated promo posts.
            - Do not invent labels outside the allowlist.
            - Preserve useful discussions about practical problems, CRM, onboarding, customer feedback, business processes, workflow лайфхаки, bugs, workarounds, and sources.
            - Preserve AI access, providers, payments, limits, and model/tool updates as one useful category, not as the only category.
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
            sb.append("  text: ").append(MessageTextFormatter.promptText(msg)).append("\n\n");
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
            int problemSignalScore = parseScore(node, "problem_signal_score", "problemSignalScore", labels.contains(ClassificationLabels.DEMAND_SIGNAL) ? 70 : 0);
            int painScore = parseScore(node, "pain_score", "painScore", labels.contains(ClassificationLabels.BUG_OR_LIMITATION) ? 65 : 0);
            int willingnessToPayScore = parseScore(node, "willingness_to_pay_score", "willingnessToPayScore", labels.contains(ClassificationLabels.PAYMENT_WORKAROUND) ? 65 : 0);
            int guidePotentialScore = parseScore(node, "guide_potential_score", "guidePotentialScore", guideCandidate ? (int) Math.round(score * 100) : 0);
            int urgencyScore = parseScore(node, "urgency_score", "urgencyScore", 0);
            int technicalDepthScore = parseScore(node, "technical_depth_score", "technicalDepthScore", 0);
            int spamScore = parseScore(node, "spam_score", "spamScore", labels.contains(ClassificationLabels.SPAM_OR_AD) ? 90 : 0);
            List<Long> evidenceIds = parseEvidenceMessageIds(node.path("evidence_message_ids"), chain);
            String reasoning = node.path("reasoning").asText("No reasoning provided");
            String meaningSummary = parseText(node, "meaning_summary", "meaningSummary");
            String problemStatement = parseText(node, "problem_statement", "problemStatement");
            String solutionHint = parseText(node, "solution_hint", "solutionHint");
            List<String> mentionedTools = parseStringList(node, "mentioned_tools", "mentionedTools");
            List<String> mentionedPrices = parseStringList(node, "mentioned_prices", "mentionedPrices");
            List<String> mentionedErrors = parseStringList(node, "mentioned_errors", "mentionedErrors");
            List<String> categories = parseStringList(node, "categories", "tags");

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

            return new ClassifierResult(
                score,
                matched,
                labels,
                guideCandidate,
                evidenceIds,
                reasoning,
                problemSignalScore,
                painScore,
                willingnessToPayScore,
                guidePotentialScore,
                urgencyScore,
                technicalDepthScore,
                spamScore,
                meaningSummary,
                problemStatement,
                solutionHint,
                mentionedTools,
                mentionedPrices,
                mentionedErrors,
                categories
            );
        } catch (Exception e) {
            log.warn("Failed to parse classifier LLM response as JSON, using fallback: {}", e.getMessage());
            return new ClassifierResult(0.30, false, "Fallback parsing (JSON failed): " + truncate(content, 200));
        }
    }

    private ClassifierResult withModelCallMetadata(
        ClassifierResult result,
        ModelCallService.ModelCallResult modelCall
    ) {
        if (result == null) {
            return null;
        }
        return new ClassifierResult(
            result.score(),
            result.matched(),
            result.labels(),
            result.guideCandidate(),
            result.evidenceMessageIds(),
            result.reasoning(),
            result.problemSignalScore(),
            result.painScore(),
            result.willingnessToPayScore(),
            result.guidePotentialScore(),
            result.urgencyScore(),
            result.technicalDepthScore(),
            result.spamScore(),
            result.meaningSummary(),
            result.problemStatement(),
            result.solutionHint(),
            result.mentionedTools(),
            result.mentionedPrices(),
            result.mentionedErrors(),
            result.categories(),
            modelCall.providerId(),
            modelCall.response().model() != null ? modelCall.response().model() : modelCall.model()
        );
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
        if (leadSignals.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM)) {
            labels.add(ClassificationLabels.PRACTICAL_PROBLEM);
            labels.add(ClassificationLabels.OPPORTUNITY);
        }
        if (leadSignals.labels().contains(ClassificationLabels.WORKFLOW_LIFEHACK)) {
            labels.add(ClassificationLabels.WORKFLOW_LIFEHACK);
            labels.add(ClassificationLabels.SOLUTION_MENTION);
        }
        if (leadSignals.labels().contains(ClassificationLabels.BUSINESS_PROCESS)) {
            labels.add(ClassificationLabels.BUSINESS_PROCESS);
        }
        if (leadSignals.labels().contains(ClassificationLabels.PRODUCT_FEEDBACK)) {
            labels.add(ClassificationLabels.PRODUCT_FEEDBACK);
        }
        if (leadSignals.labels().contains("OFFER_OR_SPAM")) {
            labels.add(ClassificationLabels.SPAM_OR_AD);
            labels.add(ClassificationLabels.VENDOR_OR_SOURCE);
        }

        boolean guideCandidate = matched && score >= 0.75
            && (labels.contains(ClassificationLabels.VENDOR_OR_SOURCE)
            || labels.contains(ClassificationLabels.SOLUTION_MENTION)
            || labels.contains(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE));

        int guidePotentialScore = guideCandidate ? (int) Math.round(score * 100) : 0;
        boolean hasPracticalProblem = leadSignals.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM);
        boolean hasWorkflowLifehack = leadSignals.labels().contains(ClassificationLabels.WORKFLOW_LIFEHACK);
        int problemSignalScore = leadSignals.leadCandidate() ? Math.max(50, (int) Math.round(score * 100)) : 0;
        int painScore = leadSignals.labels().contains("PAIN_LIMITS") || hasPracticalProblem ? 70 : 0;
        int willingnessToPayScore = leadSignals.labels().contains("PAYMENT_WORKAROUND") ? 70 : 0;
        int spamScore = leadSignals.labels().contains("OFFER_OR_SPAM") ? 80 : 0;
        int technicalDepthScore = extractMentions(chain, TOOL_MARKERS).isEmpty() && !hasWorkflowLifehack ? 0 : 70;

        return new ClassifierResult(
            score,
            matched,
            ClassificationLabels.sanitize(labels),
            guideCandidate,
            chain.stream().map(MessageEntity::getId).limit(3).toList(),
            reasoning,
            problemSignalScore,
            painScore,
            willingnessToPayScore,
            guidePotentialScore,
            0,
            technicalDepthScore,
            spamScore,
            summarize(chain),
            null,
            null,
            extractMentions(chain, TOOL_MARKERS),
            extractPriceMentions(chain),
            extractErrorMentions(chain),
            List.of()
        );
    }

    private int parseScore(JsonNode node, String snakeName, String camelName, int defaultValue) {
        JsonNode value = node.has(snakeName) ? node.path(snakeName) : node.path(camelName);
        return clampScore(value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue));
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String parseText(JsonNode node, String snakeName, String camelName) {
        JsonNode value = node.has(snakeName) ? node.path(snakeName) : node.path(camelName);
        String text = value.asText("");
        return text.isBlank() ? null : truncate(text, 1000);
    }

    private List<String> parseStringList(JsonNode node, String snakeName, String camelName) {
        JsonNode value = node.has(snakeName) ? node.path(snakeName) : node.path(camelName);
        if (!value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            String text = item.asText("");
            if (!text.isBlank() && !result.contains(text)) {
                result.add(truncate(text, 160));
            }
        });
        return List.copyOf(result);
    }

    private String summarize(List<MessageEntity> chain) {
        String text = chain.stream()
            .map(MessageEntity::getText)
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(" "))
            .trim();
        return truncate(text, 300);
    }

    private List<String> extractMentions(List<MessageEntity> chain, List<String> markers) {
        String combinedText = chain.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null)
            .collect(Collectors.joining(" "))
            .toLowerCase();
        return markers.stream()
            .filter(combinedText::contains)
            .distinct()
            .limit(12)
            .toList();
    }

    private List<String> extractPriceMentions(List<MessageEntity> chain) {
        Pattern pricePattern = Pattern.compile("(\\$\\s?\\d+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?\\s?(?:usd|eur|rub|₽|руб))", Pattern.CASE_INSENSITIVE);
        return extractPatternMentions(chain, pricePattern);
    }

    private List<String> extractErrorMentions(List<MessageEntity> chain) {
        Pattern errorPattern = Pattern.compile("\\b(?:error|ошибка|exception|timeout|429|401|403|500|502|503)\\b", Pattern.CASE_INSENSITIVE);
        return extractPatternMentions(chain, errorPattern);
    }

    private List<String> extractPatternMentions(List<MessageEntity> chain, Pattern pattern) {
        String combinedText = chain.stream()
            .map(MessageEntity::getText)
            .filter(text -> text != null)
            .collect(Collectors.joining(" "));
        java.util.regex.Matcher matcher = pattern.matcher(combinedText);
        List<String> result = new ArrayList<>();
        while (matcher.find() && result.size() < 12) {
            String value = matcher.group();
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
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
