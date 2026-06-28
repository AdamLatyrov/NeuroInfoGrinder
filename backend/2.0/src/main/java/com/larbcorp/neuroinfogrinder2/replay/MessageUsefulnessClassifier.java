package com.larbcorp.neuroinfogrinder2.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MessageUsefulnessClassifier {
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+|www\\.\\S+");
    private static final Pattern ENTITY_ONLY = Pattern.compile("(?i)^@?[a-z0-9_]{3,40}(?:_bot)?$");

    public MessageUsefulnessResult classify(String text, String topLabel, double classifierConfidence, boolean hardSignal) {
        String value = text == null ? "" : text.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();
        Map<String, Double> dimensions = new LinkedHashMap<>();

        boolean hasUrl = URL.matcher(value).find();
        boolean enoughText = meaningfulTextLength(value) >= 120;
        boolean genericLinkPhrase = containsAny(lower, "полезно", "почитайте", "на досуге", "тут", "ссылка", "интересно");
        boolean mostlyUrl = value.replaceAll("(?i)https?://\\S+|www\\.\\S+", "").trim().length() <= 45;

        if (hasUrl && (mostlyUrl || (genericLinkPhrase && !enoughText))) {
            positive.add("URL_PRESENT");
            negative.add("NO_LINK_CONTEXT");
            return result("LINK_ONLY", "LOW", "LINK_ONLY", "SAFE", "REJECT", null, 0.05, dimensions, positive, negative, "NEEDS_LINK_ENRICHMENT", "Сообщение почти полностью состоит из ссылки без объяснения содержания.");
        }
        if (ENTITY_ONLY.matcher(lower).matches()) {
            negative.add("ENTITY_ONLY_TEXT");
            return result("ENTITY_ONLY", "LOW", "NONE", "SAFE", "REJECT", null, 0.0, dimensions, positive, negative, "ENTITY_ONLY", "Сообщение содержит только имя сущности или handle.");
        }
        if (containsAny(lower, "500 ботов", "ботов за", "накрут", "реферал", "рефераль", "account farming", "фармить бонус")) {
            negative.add("ABUSE_SIGNAL");
            return result("ABUSE_OR_FRAUD", "REJECT", "ABUSE", "UNSAFE", "REJECT", null, 0.0, dimensions, positive, negative, "ABUSE_OR_FRAUD", "Сообщение описывает злоупотребление, накрутку или fraud.");
        }
        if (containsAny(lower, "loophole", "bypass", "обход", "restricted access", "эксплойт", "step-by-step", "пошагово")
            && containsAny(lower, "claude", "fable", "model", "доступ")) {
            negative.add("ACCESS_CIRCUMVENTION_SIGNAL");
            return result("ACCESS_CIRCUMVENTION", "MANUAL_ONLY", "RESTRICTED_ACCESS", "RISK_SENSITIVE", "REJECT", null, 0.0, dimensions, positive, negative, "RISK_SENSITIVE_MANUAL_ONLY", "Сообщение похоже на обход ограничений доступа и не должно становиться how-to материалом.");
        }
        if (containsAny(lower, "бонус", "регистрац", "скидк", "промокод", "реф ссыл", "платформа для работы", "пополн", "баланс")
            && !containsAny(lower, "риск", "privacy", "логи", "подмен", "проверь", "benchmark")) {
            negative.add("PROMO_MARKETING_SIGNAL");
            return result("PROMO_ALONE", "LOW", "PROMO", "SAFE", "REJECT", null, 0.0, dimensions, positive, negative, "PROMO_ALONE", "Сообщение выглядит как standalone промо без независимого полезного анализа.");
        }

        boolean githubContext = containsAny(lower, "github", "гитхаб", "open-source", "open source", "открытый исходный код");
        boolean resource = (hasUrl || githubContext) && containsAny(lower, "github", "repo", "repository", "open-source", "open source", "library", "tool", "pipeline", "пайплайн", "инструмент", "скилл", "интеграц", "tools", "skills");
        boolean featureList = containsAny(lower, "интеграц", "features", "use cases", "tools", "инструмент", "skills", "скилл", "pipelines", "пайплайн", "поддерж", "license", "security", "activity", "звезд", "звёзд", "исходный код");
        if (resource && enoughText && featureList) {
            positive.add("RESOURCE_LINK_WITH_CONTEXT");
            positive.add("FEATURE_LIST");
            dimensions.put("resourceValue", 0.26);
            dimensions.put("sourceContext", 0.20);
            return result("RESOURCE_REFERENCE", "REFERENCE_VALUE", "LINK_WITH_CONTEXT", "SAFE", "SINGLE_MESSAGE", "REFERENCE", 0.66 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Ресурс или репозиторий описан с контекстом и критериями оценки.");
        }

        boolean outage = containsAny(lower, "outage", "failing", "degraded", "status", "quota", "limit", "лимит", "квот", "сбой", "не работает", "ошиб", "exhausted");
        boolean serviceNamed = containsAny(lower, "codex", "openai", "anthropic", "claude", "github", "api", "service", "provider");
        boolean fallback = containsAny(lower, "fallback", "status page", "official status", "account limits", "проверь", "issue", "github issues");
        if (outage && serviceNamed && (fallback || enoughText)) {
            positive.add("STATUS_OR_OUTAGE_SIGNAL");
            if (fallback) positive.add("FALLBACK_OR_DIAGNOSTIC_HINT");
            dimensions.put("operationalValue", 0.24);
            dimensions.put("actionability", fallback ? 0.20 : 0.08);
            return result("STATUS_OUTAGE", "OPERATIONAL_VALUE", "STATUS_CONTEXT", "SAFE", "SINGLE_MESSAGE", fallback ? "GUIDE" : "SUMMARY", 0.65 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Сообщение описывает сбой, лимиты или degraded service с диагностическим контекстом.");
        }

        boolean proxyRisk = containsAny(lower, "proxy", "прокси", "подмен", "model substitution", "privacy", "логи", "logs", "medical", "legal", "benchmark")
            && containsAny(lower, "api", "model", "provider", "official", "docs", "проверь");
        if (proxyRisk) {
            positive.add("API_RISK_SIGNAL");
            positive.add("VERIFICATION_CHECKLIST_SIGNAL");
            dimensions.put("riskValue", 0.24);
            dimensions.put("actionability", 0.18);
            return result("PRACTICAL_GUIDE", "PRACTICAL_VALUE", "ANALYSIS", "SAFE", "SINGLE_MESSAGE", "GUIDE", 0.68 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Сообщение содержит проверяемые риски и практические критерии оценки API/proxy.");
        }

        boolean namedModel = containsAny(lower, "gpt-", "claude", "fable", "mythos", "gemini", "openai", "anthropic", "deepseek", "codex");
        boolean release = containsAny(lower, "выш", "preview", "release", "анонс", "обнов", "доступ", "policy", "утвержд", "status", "модель");
        boolean sourceMention = hasUrl || containsAny(lower, "source", "источник", "reuters", "bloomberg", "axios", "the information", "github");
        boolean benchmark = containsAny(lower, "benchmark", "бенчмарк", "%", "сравн", "против", "leaderboard");
        if (namedModel && release && enoughText && (sourceMention || benchmark)) {
            positive.add("NAMED_MODEL_OR_VENDOR");
            positive.add("RELEASE_OR_ACCESS_SIGNAL");
            if (sourceMention) positive.add("SOURCE_CONTEXT");
            if (benchmark) positive.add("COMPARISON_OR_BENCHMARK");
            dimensions.put("referenceValue", 0.22);
            dimensions.put("sourceContext", sourceMention ? 0.18 : 0.08);
            dimensions.put("specificity", benchmark ? 0.16 : 0.10);
            return result("NEWS_UPDATE", "REFERENCE_VALUE", sourceMention ? "SOURCED" : "MENTIONED", "SAFE", "SINGLE_MESSAGE", sourceMention ? "REFERENCE" : "SUMMARY", 0.64 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Новость или обновление с конкретным продуктом, источником и деталями.");
        }

        boolean questionAnswer = containsAny(lower, "?", "как ", "почему", "что делать") && containsAny(lower, "ответ", "проверь", "значит", "решение");
        if (questionAnswer && enoughText) {
            positive.add("QNA_SIGNAL");
            dimensions.put("answerValue", 0.22);
            return result("QNA", "ANSWER_VALUE", "EXPLANATION", "SAFE", "SINGLE_MESSAGE", "ANSWER", 0.60 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Сообщение похоже на полезный вопрос-ответ.");
        }

        if (hasUrl && enoughText) {
            positive.add("LINK_WITH_CONTEXT");
            dimensions.put("sourceContext", 0.16);
            return result("LINK_WITH_CONTEXT", "REFERENCE_VALUE", "LINK_WITH_CONTEXT", "SAFE", "SINGLE_MESSAGE", "REFERENCE", 0.56 + classifierBonus(classifierConfidence, hardSignal), dimensions, positive, negative, null, "Ссылка сопровождается контекстом, которого достаточно для reference/summary candidate.");
        }

        negative.add("NO_USEFULNESS_CLASS_MATCH");
        return result("LOW_VALUE", "LOW", "NONE", "SAFE", "REJECT", null, 0.0, dimensions, positive, negative, "LOW_VALUE", "Не найдено достаточно признаков полезного материала.");
    }

    private double classifierBonus(double confidence, boolean hardSignal) {
        return (confidence >= 0.65 ? 0.04 : 0.0) + (hardSignal ? 0.03 : 0.0);
    }

    private int meaningfulTextLength(String text) {
        return text.replaceAll("(?i)https?://\\S+|www\\.\\S+", "").trim().length();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private MessageUsefulnessResult result(String contentClass, String usefulnessClass, String sourceContextClass,
                                           String safetyClass, String candidateRoute, String proposedMaterialType,
                                           double overallScore, Map<String, Double> dimensions,
                                           List<String> positive, List<String> negative, String rejectReason,
                                           String humanReason) {
        return new MessageUsefulnessResult(contentClass, usefulnessClass, sourceContextClass, safetyClass,
                candidateRoute, proposedMaterialType, Math.min(1.0, Math.max(0.0, overallScore)),
                Map.copyOf(dimensions), List.copyOf(positive), List.copyOf(negative), rejectReason, humanReason);
    }
}
