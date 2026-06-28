package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.messages.MessageTextFormatter;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class MaterialGenerator {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> RISK_SAFETY_CATEGORIES = Set.of(
        "ABUSE_OR_LIMIT_EXPLOIT",
        "BYPASS",
        "DRM_COPYRIGHT",
        "ACCOUNT_RESALE",
        "PAYMENT_RISK",
        "HARMFUL",
        "RISK_ABUSE_CYBER_SAFETY"
    );

    public GuideContent generate(ContentRoutingDecision decision, List<MessageEntity> sourceMessages) {
        boolean riskMaterial = isRiskMaterial(decision);
        String rawTitle = firstNonBlank(decision.contentTitle(), decision.topicLabel(), "Полезная заметка");
        String title = normalizeTitle(rawTitle, riskMaterial);
        String summary = firstNonBlank(decision.contentSummary(), decision.topicSummary(), "Короткое описание пока не заполнено.");
        String evidence = evidenceMarkdown(sourceMessages, riskMaterial);
        String links = linksMarkdown(sourceMessages, riskMaterial);
        String markdown = riskMaterial
            ? riskInfo(title, evidence)
            : usefulInfo(title, summary, links, evidence);

        return new GuideContent(
            title,
            markdown,
            markdown,
            decision.confidence(),
            tags(decision),
            null,
            null,
            decision.providerId(),
            decision.model()
        );
    }

    private String usefulInfo(String title, String summary, String links, String evidence) {
        String cleaned = stripTitlePrefix(stripMaterialPrefix(summary), title);
        String conclusion = firstSentence(cleaned, "Короткий полезный вывод из обсуждения.");
        List<String> details = detailBullets(cleaned);
        String linksSection = links.isBlank() ? "" : "\n## Ссылки\n" + links + "\n";
        return """
            # %s

            ## Коротко
            %s

            ## Детали
            %s

            ## Когда полезно
            Используй как короткую заметку, если снова появится похожий вопрос, ссылка, цена, ограничение, решение или обсуждение проблемы.

            ## Что проверить
            - Актуальность деталей, если они зависят от продукта, лимитов, цены, региона, провайдера или даты.
            - Есть ли подтверждение в соседних сообщениях, документации или публичном источнике.
            %s

            ## Источники
            %s
            """.formatted(title, conclusion, bullets(details), linksSection, evidence);
    }

    private String riskInfo(String title, String evidence) {
        return """
            # %s

            ## Коротко
            В обсуждении обнаружен риск-сигнал: возможный обход ограничений, проверок, trial-лимитов или правил сервиса. Это не инструкция и не подборка полезных сервисов.

            ## Почему важно
            - Такие схемы могут нарушать правила сервиса и приводить к блокировкам аккаунтов.
            - Временные номера, карты, почты и обходные регистрации часто быстро перестают работать.
            - Операционные ссылки и шаги злоупотребления скрываются, чтобы материал не превращался в инструкцию по обходу.

            ## Что проверить
            - Какой продукт и какой тип ограничения обсуждали.
            - Есть ли официальные правила, риск бана, региональные ограничения или признаки мошенничества.
            - Нужно ли просто наблюдать за сигналом, а не превращать его в гайд.

            ## Источники
            %s
            """.formatted(title, evidence);
    }

    private String evidenceMarkdown(List<MessageEntity> sourceMessages, boolean redactOperationalDetails) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return "- Источники не привязаны.";
        }
        StringBuilder builder = new StringBuilder();
        for (MessageEntity message : sourceMessages.stream().limit(6).toList()) {
            if (redactOperationalDetails) {
                builder.append("- #").append(message.getId())
                    .append(": источник содержит операционные детали; они скрыты, сохранён только риск-сигнал.\n");
                continue;
            }
            String text = MessageTextFormatter.promptText(message).replaceAll("\\s+", " ").trim();
            if (text.length() > 240) {
                text = text.substring(0, 240).trim() + "...";
            }
            builder.append("- #").append(message.getId()).append(": ").append(text).append("\n");
        }
        return builder.toString().trim();
    }

    private String linksMarkdown(List<MessageEntity> sourceMessages, boolean redactOperationalDetails) {
        if (redactOperationalDetails || sourceMessages == null || sourceMessages.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> links = new LinkedHashSet<>();
        for (MessageEntity message : sourceMessages) {
            String text = MessageTextFormatter.promptText(message);
            var matcher = URL_PATTERN.matcher(text);
            while (matcher.find() && links.size() < 12) {
                links.add(cleanLink(matcher.group()));
            }
            for (String link : MessageTextFormatter.links(message.getText(), message.getTextEntitiesJson())) {
                if (links.size() >= 12) {
                    break;
                }
                links.add(cleanLink(link));
            }
        }
        if (links.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String link : links) {
            builder.append("- ").append(link).append("\n");
        }
        return builder.toString().trim();
    }

    private String cleanLink(String value) {
        return value == null ? "" : value.replaceAll("[)\\].,;:!?]+$", "");
    }

    private List<String> tags(ContentRoutingDecision decision) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add(decision.contentType().name().toLowerCase(Locale.ROOT));
        if (isRiskMaterial(decision)) {
            tags.add("abuse");
            tags.add("risk");
        }
        if (decision.safetyCategory() != null) {
            tags.add(decision.safetyCategory().toLowerCase(Locale.ROOT));
        }
        if (decision.topicLabel() != null) {
            for (String token : decision.topicLabel().split("\\s+")) {
                if (token.length() >= 4) {
                    tags.add(token);
                }
                if (tags.size() >= 6) {
                    break;
                }
            }
        }
        return List.copyOf(tags);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeTitle(String value, boolean riskMaterial) {
        String stripped = stripMaterialPrefix(value);
        if (riskMaterial) {
            stripped = stripped.replaceFirst("(?iu)^риск\\s*[:：]\\s*", "").trim();
            return "Риск: " + stripped;
        }
        return stripped;
    }

    private String stripMaterialPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replaceFirst("(?iu)^(полезное|полезно знать|полезно|материал)\\s*[:：]\\s*", "")
            .trim();
    }

    private String stripTitlePrefix(String summary, String title) {
        String cleaned = URL_PATTERN.matcher(summary == null ? "" : summary.trim()).replaceAll("")
            .replaceAll("\\s+", " ")
            .trim();
        String bareTitle = stripMaterialPrefix(title);
        if (!bareTitle.isBlank()) {
            cleaned = cleaned.replaceFirst("(?iu)^" + Pattern.quote(bareTitle) + "\\s*[:：-]\\s*", "");
        }
        return cleaned.trim();
    }

    private String firstSentence(String text, String fallback) {
        List<String> sentences = sentences(text);
        if (sentences.isEmpty()) {
            return fallback;
        }
        String first = ensurePeriod(sentences.get(0));
        return first.length() <= 260 ? first : first.substring(0, 257).trim() + "...";
    }

    private List<String> detailBullets(String text) {
        List<String> sentences = sentences(text);
        List<String> result = new ArrayList<>();
        for (int i = 1; i < sentences.size() && result.size() < 4; i++) {
            String sentence = ensurePeriod(sentences.get(i));
            if (sentence.length() >= 12) {
                result.add(sentence);
            }
        }
        if (result.isEmpty()) {
            result.add("Сохрани как короткий сигнал из обсуждения, а не как полноценный пошаговый гайд.");
        }
        return result;
    }

    private List<String> sentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : text.split("(?<=[.!?])\\s+")) {
            String cleaned = part.trim();
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String ensurePeriod(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank() || trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }

    private String bullets(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append("- ").append(value).append("\n");
        }
        return builder.toString().trim();
    }

    private boolean isRiskMaterial(ContentRoutingDecision decision) {
        String subtype = upper(decision.contentSubtype());
        String safetyCategory = upper(decision.safetyCategory());
        return decision.contentType() == ContentType.RISK_INSIGHT
            || RISK_SAFETY_CATEGORIES.contains(subtype)
            || RISK_SAFETY_CATEGORIES.contains(safetyCategory);
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
