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
    private static final Pattern PAIN_PATTERN = Pattern.compile(
        "(?iu)\\b(лимит\\w*|дорог\\w*|убиваю\\s+лимит\\w*|быстро\\s+.*лимит\\w*|нужен\\s+безлимит|"
            + "безлимит\\w*|неудобн\\w*|альтернатив\\w*|официальн\\w*\\s+.*не\\w*|не\\s+подходит|"
            + "заканчива\\w*|конча\\w*|too\\s+expensive|rate\\s*limit|quota)\\b"
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
        boolean painMention = findMatches(normalized, matchedSignals,
            "PAIN_LIMITS", PAIN_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "PAIN_LIMITS",
            "лимит", "безлимит", "дорого", "дорог", "неудоб", "альтернатив", "не подходит");
        boolean offerMention = findMatches(normalized, matchedSignals,
            "OFFER_OR_SPAM", OFFER_PATTERN, labels)
            || addContainsLabel(normalized, matchedSignals, labels, "OFFER_OR_SPAM",
            "бесплат", "free", "промокод", "скидк", "bonus", "за 300", "за $", "на год");

        if ((providerMention && demandMention)
            || (paymentMention && demandMention)
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
            || (labels.contains("PROVIDER_MENTION") && !labels.contains("NOT_USEFUL"))
            || (labels.contains("OFFER_OR_SPAM") && (providerMention || paymentMention));

        String reason = labels.isEmpty()
            ? "No AI access/payment/provider demand signals"
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
