package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ContentQualityGate {

    private static final double MIN_CONFIDENCE = 0.45;
    private static final int MIN_GUIDE_ACTIONABILITY = 50;
    private static final int MIN_EVIDENCE_SCORE = 35;
    private static final int MAX_NOISE_SCORE = 75;
    private static final int MAX_MATERIAL_SOURCE_MESSAGES = 12;
    private static final long MAX_CONTINUOUS_WINDOW_HOURS = 8;

    private static final Set<String> GENERIC_LABELS = Set.of(
        "offtopic",
        "off topic",
        "main",
        "general",
        "flood",
        "verified guide",
        "useful links",
        "schemes watermelons",
        "codex",
        "api services",
        "droid",
        "cursor",
        "ai news"
    );

    private static final Set<String> GENERIC_ANGLES = Set.of(
        "practical guide by cluster topic",
        "guide by topic",
        "useful information",
        "topic note",
        "discussion overview",
        "practical guide",
        "topic summary"
    );

    private static final Set<String> GUIDE_BLOCKING_SAFETY = Set.of(
        "BYPASS",
        "DRM_COPYRIGHT",
        "ACCOUNT_RESALE",
        "PAYMENT_RISK",
        "HARMFUL",
        "risk_abuse_cyber_safety"
    );
    private static final Set<String> RISK_MATERIAL_SAFETY = Set.of(
        "bypass",
        "drm copyright",
        "account resale",
        "payment risk",
        "harmful",
        "abuse or limit exploit",
        "abuse_or_limit_exploit",
        "risk abuse cyber safety"
    );

    public GateResult evaluate(
        ContentRoutingDecision decision,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        List<MessageEntity> sourceMessages
    ) {
        if (decision == null) {
            return GateResult.blocked("content routing did not return a decision");
        }
        if (!decision.shouldCreateMaterial() || !decision.contentType().createsMaterial()) {
            return GateResult.blocked("content type is " + decision.contentType() + "; no material should be created");
        }
        if (isGenericLabel(decision.topicLabel())) {
            return GateResult.blocked("generic topic label: " + decision.topicLabel());
        }
        if (decision.confidence() < MIN_CONFIDENCE) {
            return GateResult.blocked("routing confidence below threshold: " + decision.confidence());
        }
        if (decision.noiseScore() > MAX_NOISE_SCORE) {
            return GateResult.blocked("cluster is too noisy: noiseScore=" + decision.noiseScore());
        }
        if (decision.evidenceScore() < MIN_EVIDENCE_SCORE && !isAbuseOrLimitExploit(decision)) {
            return GateResult.blocked("cluster has weak evidence: evidenceScore=" + decision.evidenceScore());
        }
        if (isBlockedMaterialSubtype(decision.contentSubtype())) {
            return GateResult.blocked("content subtype should not create a material: " + decision.contentSubtype());
        }
        if (hasSuspiciousTimeSpan(cluster, sourceMessages)) {
            return GateResult.blocked("source messages span too much time for one material");
        }
        if (decision.contentType() != ContentType.GUIDE
            && sourceMessages != null
            && sourceMessages.size() > MAX_MATERIAL_SOURCE_MESSAGES) {
            return GateResult.blocked("too many source messages for one material: " + sourceMessages.size());
        }
        if (decision.contentType() == ContentType.GUIDE) {
            if (isGenericAngle(decision.specificAngle())
                || candidate != null && isGenericAngle(candidate.getGuideAngle())) {
                return GateResult.blocked("generic guide angle");
            }
            if (decision.actionabilityScore() < MIN_GUIDE_ACTIONABILITY) {
                return GateResult.blocked("guide actionability below threshold: " + decision.actionabilityScore());
            }
            String safety = decision.safetyCategory();
            if (safety != null && GUIDE_BLOCKING_SAFETY.contains(safety)) {
                return GateResult.blocked("safety category is not compatible with guide format: " + safety);
            }
        }
        if (hasGenericMaterialTopic(decision)) {
            return GateResult.blocked("generic material topic");
        }
        if (hasLegacyFaqShape(decision)) {
            return GateResult.blocked("legacy FAQ/Q&A material shape");
        }
        if (decision.contentType() != ContentType.GUIDE
            && isRiskMaterial(decision)
            && !hasRiskSourceEvidence(sourceMessages)) {
            return GateResult.blocked("risk material lacks matching source evidence");
        }
        return GateResult.passed();
    }

    public boolean isGenericLabel(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return true;
        }
        return GENERIC_LABELS.contains(normalized)
            || normalized.equals("оффтоп")
            || normalized.equals("основной")
            || normalized.equals("флудилка")
            || normalized.equals("полезные ссылки")
            || normalized.equals("схемы арбузы")
            || normalized.equals("api сервисы")
            || normalized.equals("ai новости");
    }

    public boolean isGenericAngle(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return true;
        }
        return GENERIC_ANGLES.contains(normalized)
            || normalized.contains("practical guide by cluster topic")
            || normalized.contains("практический гайд по теме кластера")
            || normalized.equals("гайд по теме")
            || normalized.equals("полезная информация")
            || normalized.equals("заметка по теме")
            || normalized.equals("обзор обсуждения");
    }

    private boolean hasGenericMaterialTopic(ContentRoutingDecision decision) {
        return isNonBlankGenericAngle(decision.topicLabel())
            || isNonBlankGenericAngle(decision.contentTitle())
            || isNonBlankGenericAngle(decision.specificAngle());
    }

    private boolean isBlockedMaterialSubtype(String subtype) {
        String normalized = normalize(subtype);
        return normalized.equals("discussion only")
            || normalized.equals("low value")
            || normalized.equals("weak evidence")
            || normalized.equals("broad or temporal")
            || normalized.equals("question answer")
            || normalized.equals("faq");
    }

    private boolean isAbuseOrLimitExploit(ContentRoutingDecision decision) {
        String subtype = normalize(decision.contentSubtype());
        String safety = normalize(decision.safetyCategory());
        String text = normalize(String.join(" ",
            decision.topicLabel() == null ? "" : decision.topicLabel(),
            decision.contentTitle() == null ? "" : decision.contentTitle(),
            decision.topicSummary() == null ? "" : decision.topicSummary(),
            decision.contentSummary() == null ? "" : decision.contentSummary()
        ));
        return subtype.equals("abuse or limit exploit")
            || subtype.equals("abuse_or_limit_exploit")
            || safety.equals("abuse or limit exploit")
            || safety.equals("abuse_or_limit_exploit")
            || text.contains("абуз")
            || text.contains("abuse")
            || text.contains("free trial")
            || text.contains("trial")
            || text.contains("триал");
    }

    private boolean isRiskMaterial(ContentRoutingDecision decision) {
        String subtype = normalize(decision.contentSubtype());
        String safety = normalize(decision.safetyCategory());
        return decision.contentType() == ContentType.RISK_INSIGHT
            || RISK_MATERIAL_SAFETY.contains(subtype)
            || RISK_MATERIAL_SAFETY.contains(safety);
    }

    private boolean hasRiskSourceEvidence(List<MessageEntity> sourceMessages) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return false;
        }
        for (MessageEntity message : sourceMessages) {
            String normalized = normalize(message.getText());
            if (normalized.contains("bypass")
                || normalized.contains("jailbreak")
                || normalized.contains("abuse")
                || normalized.contains("exploit")
                || normalized.contains("free trial")
                || normalized.contains("trial")
                || normalized.contains("temporary mail")
                || normalized.contains("sms activation")
                || normalized.contains("card generator")
                || normalized.contains("обход")
                || normalized.contains("джейлбрейк")
                || normalized.contains("абуз")
                || normalized.contains("триал")
                || normalized.contains("временн почт")
                || normalized.contains("sms активац")
                || normalized.contains("генератор карт")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLegacyFaqShape(ContentRoutingDecision decision) {
        return hasLegacyFaqShape(
            decision.topicLabel(),
            decision.topicSummary(),
            decision.contentTitle(),
            decision.contentSummary(),
            decision.specificAngle()
        );
    }

    private boolean hasLegacyFaqShape(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.startsWith("faq ")
                || normalized.contains("faq практический гайд")
                || normalized.contains("question short answer")
                || normalized.contains("short answer explanation caveats")
                || normalized.contains("source context")
                || normalized.contains("практический гайд по теме кластера")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNonBlankGenericAngle(String value) {
        return value != null && !value.isBlank() && isGenericAngle(value);
    }

    private boolean hasSuspiciousTimeSpan(TopicDiscussionClusterEntity cluster, List<MessageEntity> sourceMessages) {
        Instant start = cluster != null ? cluster.getStartAt() : null;
        Instant end = cluster != null ? cluster.getEndAt() : null;
        if (sourceMessages != null && !sourceMessages.isEmpty()) {
            start = sourceMessages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(value -> value != null)
                .min(Instant::compareTo)
                .orElse(start);
            end = sourceMessages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(end);
        }
        return start != null
            && end != null
            && Duration.between(start, end).toHours() > MAX_CONTINUOUS_WINDOW_HOURS;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    public record GateResult(boolean allowed, String reason) {
        static GateResult passed() {
            return new GateResult(true, "quality gate passed");
        }

        static GateResult blocked(String reason) {
            return new GateResult(false, reason);
        }
    }
}
