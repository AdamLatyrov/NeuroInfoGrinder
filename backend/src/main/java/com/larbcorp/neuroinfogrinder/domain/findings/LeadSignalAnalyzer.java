package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class LeadSignalAnalyzer {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
        "(?iu)\\b(claude(?:\\s+code)?|chat\\s?gpt|gpt|openrouter|anthropic|codex|trae|antigravity|gemini|api|apis|token(?:s)?|model(?:s)?|plus|"
            + "клод(?:а|у|ом|е)?|клауд(?:а|у|ом|е)?|чат\\s?гпт|гпт|опенроутер|антропик|кодекс|трае|гемини|апи|токен\\w*|модел\\w*|плюс)\\b"
    );
    private static final Pattern DEMAND_PATTERN = Pattern.compile(
        "(?iu)\\b(где|подскажите|кто\\s+знает|кто\\s+наш[её]л|есть\\s+у\\s+кого|how|where|need|нужен|нужно|"
            + "купить|покупать|взять|доступ\\w*|безлимит\\w*|gift(?:s)?|гифт\\w*|акк\\w*|аккаунт\\w*|"
            + "подписк\\w*|provider|endpoint|proxy|ключ|тариф\\w*|провайдер\\w*|эндпоинт\\w*|прокси|"
            + "дешевле|cheap|cheaper|оплат\\w*|пополн\\w*)\\b"
    );
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(
        "(?iu)\\b(карт\\w*|card(?:s)?|сбп|fanpay|фанпей|юан\\w*|китай\\w*|оплат\\w*|пополн\\w*|"
            + "virtual|gift\\s+card|unionpay|регион\\w*|посредник\\w*|paypal|visa|mastercard|"
            + "китайц\\w*|китайск\\w*|через\\s+китайц\\w*|пополняем\\w*\\s+через\\s+сбп|фанпе\\w*)\\b"
    );
    private static final Pattern ABUSE_PATTERN = Pattern.compile(
        "(?iu)\\b(абуз\\w*|abuse|exploit|free\\s+trial|trial|триал\\w*|canva|канв\\w*)\\b"
    );
    private static final Pattern PAIN_PATTERN = Pattern.compile(
        "(?iu)\\b(лимит\\w*|дорог\\w*|убиваю\\s+лимит\\w*|быстро\\s+.*лимит\\w*|нужен\\s+безлимит|"
            + "безлимит\\w*|неудобн\\w*|альтернатив\\w*|официальн\\w*\\s+.*не\\w*|не\\s+подходит|"
            + "заканчива\\w*|конча\\w*|too\\s+expensive|rate\\s*limit|quota)\\b"
    );
    private static final Pattern PRACTICAL_PROBLEM_PATTERN = Pattern.compile(
        "(?iu)(crm|onboarding|feedback|retention|churn|conversion|support|sales|"
            + "\\u043f\\u0440\\u043e\\u0431\\u043b\\u0435\\u043c\\w*|\\u0431\\u043e\\u043b\\u044c|\\u0431\\u043e\\u043b\\u0438|"
            + "\\u0442\\u0435\\u0440\\u044f\\w*|\\u043d\\u0435\\u043f\\u043e\\u043d\\u044f\\u0442\\u043d\\w*|\\u043d\\u0435\\u0443\\u0434\\u043e\\u0431\\u043d\\w*|"
            + "\\u043e\\u0448\\u0438\\u0431\\u043a\\w*|\\u0441\\u0431\\u043e\\w*|\\u043b\\u043e\\u043c\\u0430\\w*|"
            + "\\u043d\\u0435\\s+\\u0440\\u0430\\u0431\\u043e\\u0442\\u0430\\w*|\\u043d\\u0435\\s+\\u043a\\u043e\\u043d\\u0432\\u0435\\u0440\\u0442\\w*|"
            + "\\u043f\\u043e\\u043b\\u044c\\u0437\\u043e\\u0432\\u0430\\u0442\\u0435\\u043b\\w*\\s+\\u0442\\u0435\\u0440\\u044f\\w*)"
    );
    private static final Pattern WORKFLOW_LIFEHACK_PATTERN = Pattern.compile(
        "(?iu)(\\u043b\\u0430\\u0439\\u0444\\u0445\\u0430\\u043a\\w*|workflow|\\u0432\\u043e\\u0440\\u043a\\u0444\\u043b\\u043e\\u0443|"
            + "\\u043f\\u0440\\u043e\\u0446\\u0435\\u0441\\u0441\\w*|\\u043e\\u043d\\u0431\\u043e\\u0440\\u0434\\u0438\\u043d\\u0433\\w*|\\u0440\\u0435\\u0433\\u043b\\u0430\\u043c\\u0435\\u043d\\u0442\\w*|"
            + "\\u0430\\u0432\\u0442\\u043e\\u043c\\u0430\\u0442\\u0438\\u0437\\w*|\\u0432\\u043e\\u0440\\u043e\\u043d\\u043a\\w*|"
            + "\\u043a\\u0430\\u043a\\s+\\u0440\\u0435\\u0448\\u0430\\u043b\\w*|\\u043a\\u0430\\u043a\\s+\\u0434\\u0435\\u043b\\u0430\\u0435\\u0442\\u0435|"
            + "\\u0441\\u0431\\u043e\\u0440\\s+\\u043e\\u0431\\u0440\\u0430\\u0442\\u043d\\w*\\s+\\u0441\\u0432\\u044f\\u0437\\w*|"
            + "\\u043e\\u0431\\u0440\\u0430\\u0442\\u043d\\w*\\s+\\u0441\\u0432\\u044f\\u0437\\w*)"
    );
    private static final Pattern BUSINESS_PROCESS_PATTERN = Pattern.compile(
        "(?iu)(crm|\\u043f\\u0440\\u043e\\u0434\\u0430\\u0436\\w*|\\u043a\\u043b\\u0438\\u0435\\u043d\\u0442\\w*|"
            + "\\u043b\\u0438\\u0434\\w*|\\u0437\\u0430\\u044f\\u0432\\u043a\\w*|\\u0434\\u0435\\u043c\\u043e|\\u043f\\u0440\\u043e\\u0434\\u0443\\u043a\\u0442\\w*|"
            + "\\u043f\\u043e\\u043b\\u044c\\u0437\\u043e\\u0432\\u0430\\u0442\\u0435\\u043b\\w*|\\u043a\\u043e\\u043c\\u0430\\u043d\\u0434\\w*|"
            + "\\u043c\\u0430\\u0440\\u043a\\u0435\\u0442\\u0438\\u043d\\u0433|\\u0432\\u043e\\u0440\\u043e\\u043d\\u043a\\w*)"
    );
    private static final Pattern OFFER_PATTERN = Pattern.compile(
        "(?iu)\\b(розыгрыш|promo(?:code)?|промокод\\w*|реф\\w*|referral|стартов\\w*\\s+баланс|"
            + "subscribe|подписывайтесь|покупайте\\s+тут|скидк\\w*|sale|bonus|бонус\\w*|"
            + "бесплатн\\w*|за\\s*\\$|за\\s*\\d+\\s*(дол|usd|юан|руб)|free)\\b"
    );
    private static final Pattern NOT_USEFUL_PATTERN = Pattern.compile(
        "(?iu)^\\s*(всем|да|ок|окей|ага|понял|ясно|сайт|lol|lmao|ok|okay|thanks|thx|nice)\\s*$"
    );

    private LeadSignalAnalyzer() {
    }

    static LeadSignalAnalysis analyze(String text) {
        String safeText = text == null ? "" : text.trim();
        String normalized = normalize(safeText);

        Set<String> labels = new LinkedHashSet<>();
        List<String> matchedSignals = new ArrayList<>();

        boolean providerMention = findMatches(normalized, matchedSignals,
            "PROVIDER_MENTION", PROVIDER_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "PROVIDER_MENTION",
            "claude", "chatgpt", "chat gpt", "gpt", "openrouter", "anthropic", "codex", "gemini",
            "клауд", "клод", "чат гпт", "гпт", "опенроутер", "антропик", "апи");
        boolean demandMention = DEMAND_PATTERN.matcher(normalized).find()
            || containsAny(normalized,
            "где", "подскажите", "кто знает", "купить", "покупать", "взять", "доступ", "безлимит",
            "гифт", "акк", "аккаунт", "подписк", "дешевле", "оплата", "пополн");
        if (demandMention) {
            matchedSignals.add("access-demand");
        }
        boolean paymentMention = findMatches(normalized, matchedSignals,
            "PAYMENT_WORKAROUND", PAYMENT_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "PAYMENT_WORKAROUND",
            "сбп", "fanpay", "фанпей", "фанпе", "юан", "китай", "китайск", "карта", "оплата", "пополн");
        boolean abuseMention = findMatches(normalized, matchedSignals,
            "PAYMENT_WORKAROUND", ABUSE_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "PAYMENT_WORKAROUND",
            "абуз", "free trial", "trial", "триал", "canva", "канв");
        boolean painMention = findMatches(normalized, matchedSignals,
            "PAIN_LIMITS", PAIN_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "PAIN_LIMITS",
            "лимит", "безлимит", "дорого", "дорог", "неудоб", "альтернатив", "не подходит");
        boolean practicalProblem = findMatches(normalized, matchedSignals,
            ClassificationLabels.PRACTICAL_PROBLEM, PRACTICAL_PROBLEM_PATTERN, labels);
        boolean workflowLifehack = findMatches(normalized, matchedSignals,
            ClassificationLabels.WORKFLOW_LIFEHACK, WORKFLOW_LIFEHACK_PATTERN, labels);
        boolean businessProcess = findMatches(normalized, matchedSignals,
            ClassificationLabels.BUSINESS_PROCESS, BUSINESS_PROCESS_PATTERN, labels);
        boolean productFeedback = businessProcess
            && normalized.contains("\u043e\u0431\u0440\u0430\u0442\u043d")
            && normalized.contains("\u0441\u0432\u044f\u0437");
        if (productFeedback) {
            labels.add(ClassificationLabels.PRODUCT_FEEDBACK);
            if (!matchedSignals.contains("product-feedback")) {
                matchedSignals.add("product-feedback");
            }
        }
        boolean offerMention = findMatches(normalized, matchedSignals,
            "OFFER_OR_SPAM", OFFER_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "OFFER_OR_SPAM",
            "бесплат", "free", "промокод", "скидк", "bonus", "за 300", "за $", "на год");

        if ((providerMention && demandMention)
            || (paymentMention && demandMention)
            || abuseMention
            || (providerMention && painMention)
            || (providerMention && offerMention)) {
            labels.add("AI_ACCESS_DEMAND");
            if (!matchedSignals.contains("access-demand")) {
                matchedSignals.add("access-demand");
            }
        }

        if (labels.isEmpty() && NOT_USEFUL_PATTERN.matcher(normalized).matches()) {
            labels.add("NOT_USEFUL");
            matchedSignals.add("short-noise");
        }

        boolean leadCandidate = labels.contains("AI_ACCESS_DEMAND")
            || labels.contains("PAYMENT_WORKAROUND")
            || labels.contains("PAIN_LIMITS")
            || practicalProblem
            || workflowLifehack
            || productFeedback
            || (businessProcess && demandMention)
            || (labels.contains("OFFER_OR_SPAM") && (providerMention || paymentMention));

        String reason = labels.isEmpty()
            ? "No useful demand/problem signals"
            : "Matched labels=" + String.join(", ", labels) + " signals=" + String.join(", ", matchedSignals);

        return new LeadSignalAnalysis(List.copyOf(labels), List.copyOf(matchedSignals), reason, leadCandidate);
    }

    private static boolean findMatches(String text, List<String> matchedSignals, String label,
                                       Pattern pattern, Set<String> labels) {
        var matcher = pattern.matcher(text);
        boolean found = false;
        while (matcher.find()) {
            labels.add(label);
            String matched = matcher.group().trim();
            if (!matched.isBlank() && !matchedSignals.contains(matched)) {
                matchedSignals.add(matched);
            }
            found = true;
        }
        return found;
    }

    private static boolean addContainsLabel(String text, List<String> matchedSignals, Set<String> labels,
                                            String label, String... markers) {
        boolean found = false;
        for (String marker : markers) {
            if (text.contains(marker)) {
                labels.add(label);
                if (!matchedSignals.contains(marker)) {
                    matchedSignals.add(marker);
                }
                found = true;
            }
        }
        return found;
    }

    private static boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replaceAll("\\s+", " ")
            .trim();
    }
}
