package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class GuideUsefulnessScorer {

    private static final List<String> TECHNICAL_MARKERS = List.of(
        "how-to", "guide", "setup", "api", "/api", "proxy", "vpn", "github", "repo",
        "codex", "claude", "cloudflare", "worker", "endpoint", "token", "deploy",
        "инструкц", "настро", "запуст", "решен", "гайд", "прокси", "оплат", "лимит"
    );

    private static final List<String> PRACTICAL_MARKERS = List.of(
        "step", "шаг", "source", "источник", "link", "ссылка", "http://", "https://",
        "install", "configure", "настро", "добав", "запуст"
    );

    private static final List<String> GENERIC_LOW_VALUE_MARKERS = List.of(
        "event", "news", "fundraising", "donation", "promo", "sale",
        "ивент", "новост", "сбор", "донат", "реклам", "акци"
    );

    public int score(GuideEntity guide, MessageEntity sourceMessage, ClassifierResult classification) {
        int score = guide != null && guide.getConfidence() != null
            ? (int) Math.round(guide.getConfidence() * 100.0)
            : 50;
        int max = 100;
        int min = 0;

        String title = guide != null ? guide.getTitle() : null;
        String content = combinedGuideContent(guide);
        String sourceText = sourceMessage != null ? sourceMessage.getText() : null;
        String combinedText = normalize((title == null ? "" : title) + "\n" + content + "\n" + (sourceText == null ? "" : sourceText));
        List<String> labels = classification != null && classification.labels() != null
            ? classification.labels()
            : List.of();

        if (isBadTitle(title)) {
            max = Math.min(max, 20);
        }
        if (content.isBlank() || content.trim().length() < 40) {
            max = Math.min(max, 20);
        }
        if (guide != null && guide.getGenerationError() != null && !guide.getGenerationError().isBlank()) {
            max = Math.min(max, 15);
        }
        if (classification != null && !classification.guideCandidate()) {
            max = Math.min(max, 30);
        }

        boolean practicalGuide = containsLabel(labels, ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE);
        boolean technical = containsAny(combinedText, TECHNICAL_MARKERS);
        boolean practicalDetails = containsAny(combinedText, PRACTICAL_MARKERS);
        boolean concreteAccessOrSourceDetail = combinedText.contains("http://")
            || combinedText.contains("https://")
            || combinedText.contains("/api")
            || combinedText.contains("github")
            || combinedText.contains("proxy")
            || combinedText.contains("payment")
            || combinedText.contains("source:")
            || combinedText.contains("оплат")
            || combinedText.contains("источник");
        boolean spamOrOffer = containsLabel(labels, ClassificationLabels.SPAM_OR_AD)
            || containsLabel(labels, "OFFER_OR_SPAM")
            || combinedText.contains("offer_or_spam");
        boolean genericLowValue = containsAny(combinedText, GENERIC_LOW_VALUE_MARKERS);

        if (practicalGuide) {
            score += 15;
        }
        if (technical) {
            score += 10;
        }
        if (spamOrOffer && !practicalGuide && !concreteAccessOrSourceDetail) {
            max = Math.min(max, 35);
        }
        if (genericLowValue && !(technical || practicalDetails)) {
            max = Math.min(max, 40);
        }
        if (classification != null
            && classification.guideCandidate()
            && technical
            && practicalDetails
            && content.trim().length() >= 120) {
            min = Math.max(min, 70);
        }

        return clamp(Math.max(min, Math.min(score, max)));
    }

    public int fallbackScore(GuideEntity guide) {
        return score(guide, null, null);
    }

    private String combinedGuideContent(GuideEntity guide) {
        if (guide == null) {
            return "";
        }
        String content = guide.getContent() != null ? guide.getContent() : "";
        String markdown = guide.getContentMarkdown() != null ? guide.getContentMarkdown() : "";
        return content.isBlank() ? markdown : content + "\n" + markdown;
    }

    private boolean isBadTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String trimmed = title.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean containsLabel(List<String> labels, String expected) {
        return labels.stream().anyMatch(label -> expected.equalsIgnoreCase(label));
    }

    private boolean containsAny(String text, List<String> markers) {
        return markers.stream().anyMatch(text::contains);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
