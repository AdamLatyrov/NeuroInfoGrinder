package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SignalScorer {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-._~:/?#@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CODE_PATTERN = Pattern.compile("```|`[^`\\n]+`|(?:^|\\n)\\s{2,}\\S");
    private static final Pattern GUIDE_PATTERN = Pattern.compile(
        "(?iu)\\b(guide|tutorial|how[- ]?to|step\\s+\\d+|prompt|setup|install|instruction|faq|"
            + "\\u0433\\u0430\\u0439\\u0434|\\u0438\\u043d\\u0441\\u0442\\u0440\\u0443\\u043a\\u0446\\u0438\\w*|"
            + "\\u0442\\u0443\\u0442\\u043e\\u0440\\u0438\\u0430\\u043b|\\u043f\\u0440\\u043e\\u043c\\u043f\\u0442|"
            + "\\u043d\\u0430\\u0441\\u0442\\u0440\\u043e\\u0438\\u0442\\u044c|\\u043d\\u0430\\u0441\\u0442\\u0440\\u043e\\u0439\\u043a\\w*|"
            + "\\u043f\\u043e\\u0434\\u043a\\u043b\\u044e\\u0447\\u0438\\u0442\\u044c|\\u0441\\u0445\\u0435\\u043c\\w*|"
            + "\\u0448\\u0430\\u0433\\s+\\d+)\\b"
    );
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "(?iu)(\\?|\\bhow\\b|\\bwhere\\b|\\bwho\\b|\\bcan\\b|"
            + "\\b\\u043a\\u0430\\u043a\\b|\\b\\u043f\\u043e\\u0447\\u0435\\u043c\\u0443\\b|\\b\\u0433\\u0434\\u0435\\b|"
            + "\\b\\u043a\\u0442\\u043e\\s+\\u0437\\u043d\\u0430\\u0435\\u0442\\b|"
            + "\\b\\u043a\\u0442\\u043e\\s+\\u043d\\u0430\\u0448\\u0435\\u043b\\b|"
            + "\\b\\u043f\\u043e\\u0434\\u0441\\u043a\\u0430\\u0436\\u0438\\u0442\\u0435\\b|"
            + "\\b\\u0435\\u0441\\u0442\\u044c\\s+\\u0443\\s+\\u043a\\u043e\\u0433\\u043e\\b)"
    );
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
        "(?iu)\\b(link|source|site|endpoint|provider|proxy|account|accounts|prompt|repo|github|shop|seller|"
            + "\\u0441\\u0441\\u044b\\u043b\\u043a\\w*|\\u0441\\u0430\\u0439\\u0442|"
            + "\\u0433\\u0434\\u0435\\s+\\u0432\\u0437\\u044f\\u0442\\u044c|"
            + "\\u0433\\u0434\\u0435\\s+\\u043a\\u0443\\u043f\\u0438\\u0442\\u044c|"
            + "\\u043f\\u0440\\u043e\\u0432\\u0430\\u0439\\u0434\\u0435\\u0440|"
            + "\\u043f\\u0440\\u043e\\u043c\\u043f\\u0442|\\u0430\\u043a\\u043a|\\u0430\\u043a\\u043a\\u0438|"
            + "\\u0430\\u043a\\u043a\\u0430\\u0443\\u043d\\u0442\\w*|\\u043a\\u043b\\u044e\\u0447|"
            + "\\u044d\\u043d\\u0434\\u043f\\u043e\\u0438\\u043d\\u0442|\\u043f\\u0440\\u043e\\u043a\\u0441\\u0438|"
            + "\\u043a\\u0438\\u0442\\u0430\\u0439\\u0441\\u043a\\w*|\\u043f\\u0440\\u043e\\u0434\\u0430\\u0432\\u0435\\u0446|"
            + "\\u043a\\u0443\\u043f\\u0438\\u0442\\u044c|\\u043f\\u043e\\u043a\\u0443\\u043f\\u0430\\u0442\\u044c)\\b"
    );
    private static final Pattern LIST_PATTERN = Pattern.compile("(?m)^\\s*(\\d+[.)]\\s+|[-*•]\\s+).+");

    private static final Set<String> ACK_MESSAGES = Set.of(
        "ok", "okay", "thanks", "thx", "+", "+1",
        "\u043e\u043a", "\u043e\u043a\u0435\u0439", "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
        "\u0430\u0433\u0430", "\u044f\u0441\u043d\u043e", "\u043f\u043e\u043d\u044f\u0442\u043d\u043e"
    );

    public SignalScore score(MessageEntity message) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        String text = message.getText() != null ? message.getText().trim() : "";
        String lowerText = text.toLowerCase();

        boolean hasGuideMarkers = GUIDE_PATTERN.matcher(text).find();
        boolean hasQuestionMarkers = QUESTION_PATTERN.matcher(text).find();
        boolean hasSourceMarkers = SOURCE_PATTERN.matcher(text).find();
        boolean hasLink = URL_PATTERN.matcher(text).find();
        boolean hasShortValuableSignal = hasGuideMarkers || hasQuestionMarkers || hasSourceMarkers || hasLink;

        double total = 0.0;
        total += add(breakdown, "guideMarkers", hasGuideMarkers ? 0.24 : 0.0);
        total += add(breakdown, "questionMarkers", hasQuestionMarkers ? 0.22 : 0.0);
        total += add(breakdown, "sourceMarkers", hasSourceMarkers ? 0.24 : 0.0);
        total += add(breakdown, "links", hasLink ? 0.18 : 0.0);
        total += add(breakdown, "code", CODE_PATTERN.matcher(text).find() ? 0.22 : 0.0);
        total += add(breakdown, "listStructure", LIST_PATTERN.matcher(text).find() ? 0.16 : 0.0);
        total += add(breakdown, "replyContext", message.getReplyToMessageId() != null ? 0.16 : 0.0);
        total += add(breakdown, "activeDiscussion", message.getReplyCount() != null && message.getReplyCount() > 0 ? 0.14 : 0.0);
        total += add(breakdown, "topicContext", message.getTopicId() != null ? 0.08 : 0.0);
        total += add(breakdown, "length", scoreLength(text.length()));
        total += add(breakdown, "humanAuthored", !Boolean.TRUE.equals(message.getIsBot()) ? 0.04 : -0.18);

        if (ACK_MESSAGES.contains(lowerText)) {
            total += add(breakdown, "ackPenalty", -0.60);
        } else if (text.length() > 0 && text.length() < 8 && !hasShortValuableSignal) {
            total += add(breakdown, "shortPenalty", -0.24);
        } else if (text.isBlank()) {
            total += add(breakdown, "emptyPenalty", -0.40);
        }

        return new SignalScore(clamp01(total), breakdown);
    }

    private double scoreLength(int length) {
        if (length >= 500) {
            return 0.16;
        }
        if (length >= 250) {
            return 0.12;
        }
        if (length >= 120) {
            return 0.08;
        }
        if (length >= 32) {
            return 0.04;
        }
        return 0.0;
    }

    private double add(Map<String, Double> breakdown, String key, double value) {
        breakdown.put(key, value);
        return value;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
