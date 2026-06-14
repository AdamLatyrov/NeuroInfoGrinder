package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuideGenerator {

    private static final Pattern PRODUCT_TAG_PATTERN = Pattern.compile(
        "\\b(Telegram|Google Play|App Store|Android|iOS|OpenAI|Anthropic|Claude|Gemini|DeepSeek|Cursor|Codex|GPT-[\\w.-]+)\\b"
    );
    private static final Pattern CAPITALIZED_TAG_PATTERN = Pattern.compile(
        "(?<!\\p{L})([A-ZА-Я][\\p{L}\\d.+-]{2,}(?:\\s+[A-ZА-Я][\\p{L}\\d.+-]{2,}){0,2})"
    );

    private final AiClientService aiClientService;
    private final AiProviderRepository aiProviderRepository;
    private final PromptRepository promptRepository;
    private final ObjectMapper objectMapper;

    public GuideContent generate(
        List<MessageEntity> chain,
        ClassifierResult classifierResult,
        Long providerId,
        Long promptId,
        Long rootMessageId
    ) {
        AiProviderEntity provider = aiProviderRepository.findById(providerId)
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        String systemPrompt = buildGuideSystemPrompt(promptId);
        String userPrompt = buildGuideUserPrompt(chain, classifierResult, rootMessageId);

        AiCompletionResponse response = aiClientService.complete(
            provider.getEndpointUrl(),
            provider.getApiKeyEncrypted(),
            provider.getModel() != null ? provider.getModel() : "gpt-4o-mini",
            systemPrompt,
            userPrompt,
            0.4,
            4096
        );

        if (!response.success()) {
            log.error("Guide generation AI request failed: {}", response.error());
            String error = response.error() != null ? response.error() : "Unknown AI error";
            return new GuideContent(
                "Ошибка генерации гайда",
                error,
                "## Ошибка генерации\n\n" + error,
                0.0,
                extractFallbackTags(userPrompt),
                error
            );
        }

        return parseGuideResponse(response.content(), userPrompt);
    }

    private String buildGuideSystemPrompt(Long promptId) {
        if (promptId != null) {
            PromptEntity prompt = promptRepository.findById(promptId).orElse(null);
            if (prompt != null && prompt.getContent() != null) {
                return applyGuideConstraints(prompt.getContent());
            }
        }

        return """
            Ты извлекаешь знания из Telegram-диалогов и превращаешь их в понятные практические гайды.

            Верни строго JSON-объект:
            {
              "title": "краткий заголовок",
              "content": "гайд простым текстом",
              "contentMarkdown": "# Гайд в Markdown",
              "confidence": 0.0,
              "tags": ["тег 1", "тег 2", "название продукта"]
            }

            Обязательные правила:
            - Пиши title, content, contentMarkdown и tags только на русском языке.
            - Явно называй продукт, приложение, игру, сервис или платформу, если это можно уверенно понять из контекста.
            - Если продукт неочевиден, не выдумывай его, а компенсируй это точными тегами по теме.
            - Сохраняй исходные ссылки, команды, версии, цены и дедлайны без искажений.
            - Сообщения с метками ROOT, PARENT_REPLY, DIRECT_REPLY и THREAD_REPLY считай основным контекстом.
            - Сообщения с метками SAME_TOPIC_NEARBY, SAME_AUTHOR_NEARBY и TIMELINE_NEARBY используй только если они реально уточняют основную мысль.
            - Если соседние сообщения выглядят шумом, игнорируй их.
            - Если это how-to, оформи как пошаговую инструкцию.
            - Если это полезный фидбек по продукту, сгруппируй его по темам и явно выдели проблемы и предложения.
            - Верни от 3 до 6 коротких тегов. Среди них должен быть хотя бы один тег про предмет обсуждения и, если возможно, один тег с названием продукта или платформы.
            - Если полезного гайда из контекста не получается, поставь confidence ниже 0.5.
            """;
    }

    private String applyGuideConstraints(String promptContent) {
        String normalized = promptContent
            .replace("{{language}}", "Russian")
            .replace("{{format}}", "markdown");

        return normalized + """

            Mandatory output rules:
            - Return title, content, contentMarkdown, and tags only in Russian.
            - Mention the product, application, game, or service explicitly if the context makes it identifiable.
            - Preserve original URLs exactly. Do not drop hidden or inline links.
            - Treat ROOT, PARENT_REPLY, DIRECT_REPLY, and THREAD_REPLY as the primary context.
            - Use SAME_TOPIC_NEARBY, SAME_AUTHOR_NEARBY, and TIMELINE_NEARBY only as optional supporting context.
            - Ignore nearby messages if they look off-topic.
            - Return 3 to 6 concise tags.
            """;
    }

    private String buildGuideUserPrompt(
        List<MessageEntity> chain,
        ClassifierResult classifierResult,
        Long rootMessageId
    ) {
        StringBuilder sb = new StringBuilder();
        MessageEntity root = chain.stream()
            .filter(message -> message.getId().equals(rootMessageId))
            .findFirst()
            .orElseGet(() -> chain.isEmpty() ? null : chain.get(chain.size() - 1));

        sb.append("Классификация сообщения:\n");
        sb.append("- score: ").append(String.format(Locale.US, "%.3f", classifierResult.score())).append("\n");
        sb.append("- matched: ").append(classifierResult.matched()).append("\n");
        sb.append("- labels: ").append(classifierResult.labels()).append("\n");
        sb.append("- guide_candidate: ").append(classifierResult.guideCandidate()).append("\n");
        sb.append("- evidence_message_ids: ").append(classifierResult.evidenceMessageIds()).append("\n");
        sb.append("- reasoning: ").append(classifierResult.reasoning()).append("\n\n");

        if (root != null) {
            sb.append("Главное сообщение:\n");
            sb.append("- internalId: ").append(root.getId()).append("\n");
            sb.append("- telegramMessageId: ").append(root.getTelegramMessageId()).append("\n");
            sb.append("- topicId: ").append(root.getTopicId()).append("\n");
            sb.append("- topicName: ").append(root.getTopicName()).append("\n");
            sb.append("- senderName: ").append(root.getSenderName()).append("\n");
            sb.append("- replyToTelegramMessageId: ").append(root.getReplyToMessageId()).append("\n\n");
        }

        sb.append("Цепочка сообщений:\n\n");
        for (MessageEntity msg : chain) {
            String sender = msg.getSenderName() != null ? msg.getSenderName() : "Unknown";
            String botTag = Boolean.TRUE.equals(msg.getIsBot()) ? " [BOT]" : "";
            sb.append("- relation: ").append(resolveRelationLabel(root, msg)).append("\n");
            sb.append("  sender: ").append(sender).append(botTag).append("\n");
            sb.append("  telegramMessageId: ").append(msg.getTelegramMessageId()).append("\n");
            sb.append("  replyToTelegramMessageId: ").append(msg.getReplyToMessageId()).append("\n");
            sb.append("  topicId: ").append(msg.getTopicId()).append("\n");
            sb.append("  topicName: ").append(msg.getTopicName()).append("\n");
            sb.append("  text: ").append(msg.getText() != null ? msg.getText() : "").append("\n\n");
        }

        return sb.toString();
    }

    private String resolveRelationLabel(MessageEntity root, MessageEntity candidate) {
        if (root == null || candidate == null) {
            return "UNKNOWN";
        }
        if (root.getId().equals(candidate.getId())) {
            return "ROOT";
        }
        if (root.getReplyToMessageId() != null && root.getReplyToMessageId().equals(candidate.getTelegramMessageId())) {
            return "PARENT_REPLY";
        }
        if (candidate.getReplyToMessageId() != null && candidate.getReplyToMessageId().equals(root.getTelegramMessageId())) {
            return "DIRECT_REPLY";
        }
        if (candidate.getReplyToMessageId() != null || root.getReplyToMessageId() != null) {
            return "THREAD_REPLY";
        }
        if (root.getTopicId() != null && root.getTopicId().equals(candidate.getTopicId())) {
            return "SAME_TOPIC_NEARBY";
        }
        if (root.getSenderTelegramUserId() != null
            && root.getSenderTelegramUserId().equals(candidate.getSenderTelegramUserId())) {
            return "SAME_AUTHOR_NEARBY";
        }
        return "TIMELINE_NEARBY";
    }

    private GuideContent parseGuideResponse(String content, String fallbackSource) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);

            String title = node.path("title").asText("Untitled Guide");
            String guideContent = node.path("content").asText("");
            String contentMarkdown = node.path("contentMarkdown").asText("");
            double confidence = node.path("confidence").asDouble(0.5);
            List<String> tags = normalizeTags(parseTags(node), fallbackSource);

            if ((contentMarkdown == null || contentMarkdown.isBlank()) && guideContent != null) {
                contentMarkdown = guideContent;
            }

            return new GuideContent(title, guideContent, contentMarkdown, confidence, tags, null);
        } catch (Exception e) {
            log.warn("Failed to parse guide response as JSON, using raw content: {}", e.getMessage());

            String rawContent = content != null ? content : "";
            String title = extractTitle(rawContent);
            double confidence = 0.5;

            return new GuideContent(
                title,
                rawContent,
                rawContent,
                confidence,
                extractFallbackTags(fallbackSource + "\n" + rawContent),
                null
            );
        }
    }

    private List<String> parseTags(JsonNode node) {
        JsonNode tagsNode = node.path("tags");
        if (!tagsNode.isArray()) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        for (JsonNode child : tagsNode) {
            String tag = child.asText(null);
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private String extractJson(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }

        int braceStart = content.indexOf('{');
        int braceEnd = content.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return content.substring(braceStart, braceEnd + 1);
        }

        return content;
    }

    private String extractTitle(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            }
        }
        return "Untitled Guide";
    }

    private List<String> normalizeTags(List<String> candidateTags, String fallbackSource) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String candidate : candidateTags) {
            if (candidate == null) {
                continue;
            }
            String cleaned = candidate.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            normalized.add(cleaned.length() > 40 ? cleaned.substring(0, 40).trim() : cleaned);
            if (normalized.size() >= 6) {
                break;
            }
        }

        if (normalized.size() < 3) {
            for (String fallbackTag : extractFallbackTags(fallbackSource)) {
                normalized.add(fallbackTag);
                if (normalized.size() >= 6) {
                    break;
                }
            }
        }

        return List.copyOf(normalized);
    }

    private List<String> extractFallbackTags(String source) {
        if (source == null || source.isBlank()) {
            return List.of("гайд");
        }

        String lowered = Normalizer.normalize(source, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tags = new LinkedHashSet<>();

        if (lowered.contains("онбординг")) tags.add("онбординг");
        if (lowered.contains("paywall") || lowered.contains("пейвол")) tags.add("пейвол");
        if (lowered.contains("подпис")) tags.add("подписка");
        if (lowered.contains("оплат") || lowered.contains("карт")) tags.add("оплата");
        if (lowered.contains("google play")) tags.add("Google Play");
        if (lowered.contains("app store")) tags.add("App Store");
        if (lowered.contains("android")) tags.add("Android");
        if (lowered.contains("ios")) tags.add("iOS");
        if (lowered.contains("игр")) tags.add("игры");
        if (lowered.contains("прилож")) tags.add("приложение");
        if (lowered.contains("api")) tags.add("API");
        if (lowered.contains("монетиза")) tags.add("монетизация");
        if (lowered.contains("обновлен")) tags.add("обновление");
        if (lowered.contains("фидбек")) tags.add("фидбек");

        Matcher productMatcher = PRODUCT_TAG_PATTERN.matcher(source);
        while (productMatcher.find() && tags.size() < 6) {
            tags.add(productMatcher.group(1));
        }

        Matcher capitalizedMatcher = CAPITALIZED_TAG_PATTERN.matcher(source);
        while (capitalizedMatcher.find() && tags.size() < 6) {
            String value = capitalizedMatcher.group(1).trim();
            if (value.length() >= 3 && !value.startsWith("ROOT") && !value.startsWith("THREAD")) {
                tags.add(value);
            }
        }

        if (tags.isEmpty()) {
            tags.add("гайд");
        }

        return List.copyOf(tags);
    }
}
