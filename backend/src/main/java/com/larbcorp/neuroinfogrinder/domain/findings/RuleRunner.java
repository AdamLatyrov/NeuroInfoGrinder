package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.RuleEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Evaluates active rules against a message.
 * Rules run first in the pipeline as cheap keyword and regex checks
 * before more expensive downstream stages.
 *
 * EXCLUDE rule matched -> message is rejected immediately.
 * INCLUDE rule matched -> message passes with a positive signal.
 * No rule matched -> message still continues downstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleRunner {

    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    /**
     * Evaluate all active rules against a message.
     * Returns a RuleResult with the decision and details.
     */
    public RuleResult evaluate(MessageEntity message) {
        List<RuleEntity> activeRules = ruleRepository.findByStatusOrderByRuleOrderAsc("ACTIVE");

        if (activeRules.isEmpty()) {
            return new RuleResult(true, "Нет активных правил - пропускаем дальше", List.of());
        }

        String text = message.getText() != null ? message.getText() : "";
        String lowerText = text.toLowerCase();
        long groupId = message.getGroupId();
        boolean isBot = Boolean.TRUE.equals(message.getIsBot());
        boolean hasTopic = message.getTopicId() != null;

        List<RuleCheck> checks = new ArrayList<>();
        boolean excluded = false;
        boolean included = false;
        boolean hasIncludeRules = false;
        String excludeReason = "";
        String includeReason = "";

        for (RuleEntity rule : activeRules) {
            if ("INCLUDE".equalsIgnoreCase(rule.getActionType())) {
                hasIncludeRules = true;
            }

            List<ConditionMatch> matches = evaluateConditions(rule, text, lowerText, groupId, isBot, hasTopic);
            boolean allConditionsMet = !matches.isEmpty() && matches.stream().allMatch(ConditionMatch::matched);

            String checkDetail = formatCheckDetail(matches);
            checks.add(new RuleCheck(
                rule.getId(),
                rule.getName(),
                rule.getActionType(),
                allConditionsMet,
                checkDetail,
                rule.getConditionsJson(),
                rule.getDescription()
            ));

            if (!allConditionsMet) {
                continue;
            }

            if ("EXCLUDE".equalsIgnoreCase(rule.getActionType())) {
                excluded = true;
                excludeReason = "Правило \"" + rule.getName() + "\": " + checkDetail;
                break;
            }

            included = true;
            includeReason = "Правило \"" + rule.getName() + "\": " + checkDetail;
        }

        if (excluded) {
            return new RuleResult(false, "ОТКЛОНЕНО: " + excludeReason, checks);
        }

        if (included) {
            return new RuleResult(true, "ПРОШЕЛ: " + includeReason, checks);
        }

        if (hasIncludeRules) {
            return new RuleResult(true, "Ни одно INCLUDE-правило не сработало - передаем в downstream scoring", checks);
        }

        return new RuleResult(true, "Активны только EXCLUDE-правила, блокировок нет - пропускаем дальше", checks);
    }

    private List<ConditionMatch> evaluateConditions(RuleEntity rule, String text, String lowerText,
                                                     long groupId, boolean isBot, boolean hasTopic) {
        List<ConditionMatch> results = new ArrayList<>();

        try {
            JsonNode conditions = objectMapper.readTree(rule.getConditionsJson());
            if (!conditions.isArray()) {
                return results;
            }

            for (JsonNode cond : conditions) {
                String type = cond.path("type").asText("");
                String value = cond.path("value").asText("");

                boolean matched = switch (type.toUpperCase()) {
                    case "TEXT_CONTAINS" -> !value.isEmpty() && lowerText.contains(value.toLowerCase());
                    case "KEYWORD_MATCH" -> keywordMatch(lowerText, value);
                    case "REGEX_MATCH" -> regexMatch(text, value);
                    case "LENGTH_GT" -> text.length() > parseIntSafe(value, 0);
                    case "LENGTH_LT" -> text.length() < parseIntSafe(value, Integer.MAX_VALUE);
                    case "SENDER_IS_BOT" -> isBot == Boolean.parseBoolean(value);
                    case "HAS_TOPIC" -> hasTopic;
                    case "GROUP_MATCH" -> groupId == parseLongSafe(value, -1);
                    default -> false;
                };

                results.add(new ConditionMatch(type, value, matched));
            }
        } catch (Exception e) {
            log.warn("Failed to parse conditions for rule {}: {}", rule.getId(), e.getMessage());
        }

        return results;
    }

    private boolean keywordMatch(String text, String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return false;
        }
        String[] parts = keywords.split("[,;]");
        for (String kw : parts) {
            String trimmed = kw.trim().toLowerCase();
            if (!trimmed.isEmpty() && text.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private boolean regexMatch(String text, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
        } catch (Exception e) {
            log.warn("Invalid regex pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    private int parseIntSafe(String value, int defaultVal) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private long parseLongSafe(String value, long defaultVal) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String formatCheckDetail(List<ConditionMatch> matches) {
        StringBuilder sb = new StringBuilder();
        for (ConditionMatch cm : matches) {
            if (!sb.isEmpty()) {
                sb.append(" + ");
            }
            sb.append(cm.type())
                    .append("(")
                    .append(cm.value())
                    .append(")=")
                    .append(cm.matched() ? "Да" : "Нет");
        }
        return sb.toString();
    }

    private record ConditionMatch(String type, String value, boolean matched) {}
}
