package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.messages.MessageTextFormatter;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentRoutingService {

    private static final int ROUTING_MAX_TOKENS = 1600;
    private static final int MAX_MESSAGES_IN_PROMPT = 18;
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|authorization|bearer|token|secret|password|jwt|card|cvv)\\s*[:=]\\s*[^\\s,}]+"
    );

    private final ModelCallService modelCallService;
    private final ObjectMapper objectMapper;

    public ContentRoutingDecision route(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult
    ) {
        ModelCallService.ModelCallResult modelCall = modelCallService.complete(
            ModelCallPurpose.CLUSTER_CONTENT_ROUTING,
            routingSystemPrompt(),
            routingUserPrompt(cluster, candidate, sourceMessages, classifierResult),
            0.2,
            ROUTING_MAX_TOKENS
        );
        AiCompletionResponse response = modelCall.response();
        if (response.success()) {
            try {
                return parseRoutingResponse(response.content(), modelCall, cluster, candidate, sourceMessages, classifierResult);
            } catch (Exception exception) {
                log.warn("Content routing returned invalid JSON, using deterministic fallback: {}", exception.getMessage());
            }
        }

        ContentRoutingDecision fallback = deterministicRoute(cluster, candidate, sourceMessages, classifierResult);
        List<String> warnings = new ArrayList<>(fallback.warnings());
        warnings.add(response.error() != null ? response.error() : "LLM routing unavailable");
        return new ContentRoutingDecision(
            fallback.contentType(),
            fallback.contentSubtype(),
            fallback.topicLabel(),
            fallback.topicSummary(),
            fallback.contentTitle(),
            fallback.contentSummary(),
            fallback.normalizedTopicKey(),
            fallback.specificAngle(),
            fallback.shouldCreateMaterial(),
            fallback.shouldGenerateFullGuide(),
            fallback.confidence(),
            fallback.importanceScore(),
            fallback.actionabilityScore(),
            fallback.noveltyScore(),
            fallback.evidenceScore(),
            fallback.riskScore(),
            fallback.noiseScore(),
            fallback.safetyCategory(),
            fallback.publicationKind(),
            fallback.reason(),
            warnings,
            fallback.suggestedSections(),
            null,
            null
        );
    }

    ContentRoutingDecision deterministicRoute(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult
    ) {
        List<MessageEntity> messages = sourceMessages == null ? List.of() : sourceMessages;
        String combined = combinedText(messages, classifierResult);
        String normalized = normalize(combined);
        String safetyCategory = inferSafetyCategory(normalized);
        boolean hasQuestion = combined.contains("?") || containsAny(normalized, List.of("как ", "можно ли", "почему", "где ", "что делать"));
        boolean hasSteps = containsAny(normalized, List.of("step", "шаг", "инструкция", "гайд", "как настроить", "как установить", "команда", "docker", "npm ", "git ", "curl ", "logs_2", "sqlite", "termux"));
        boolean hasNews = containsAny(normalized, List.of("новость", "анонс", "релиз", "ожидается", "слух", "rumor", "released", "launch", "добавили", "появилась", "новая модель"));
        boolean hasProductUpdate = containsAny(normalized, List.of("обновление", "добавили", "фича", "персонаж", "алиса", "cursor", "copilot", "codex", "claude", "gemini"));
        boolean hasReference = containsAny(normalized, List.of("список", "таблица", "тариф", "модель", "ссылк", "конфиг", "параметр"));
        boolean hasPromo = containsAny(normalized, List.of("реферал", "referral", "promo", "промо", "скидк", "credits", "кредит", "free credits", "бесплатн", "byesu", "gigacoder", "evomap"));
        boolean hasAbuseOrLimitExploit = hasAbuseOrLimitExploit(normalized);
        boolean broad = looksBroadOrTemporal(cluster, messages, normalized);
        boolean subjectiveAdvice = looksLikeSubjectiveAdviceRequest(normalized);
        boolean concreteReference = hasConcreteReferenceValue(normalized, classifierResult);
        boolean concreteAnswer = hasConcreteAnswerValue(normalized, classifierResult);
        boolean useful = classifierResult != null
            && (score(classifierResult.problemSignalScore()) >= 50
                || score(classifierResult.guidePotentialScore()) >= 45
                || score(classifierResult.technicalDepthScore()) >= 45);

        ContentType type = ContentType.USEFUL_INFO;
        String subtype = null;
        boolean shouldCreateMaterial = true;
        if ("BYPASS".equals(safetyCategory) || "HARMFUL".equals(safetyCategory)) {
            subtype = safetyCategory;
        } else if ("DRM_COPYRIGHT".equals(safetyCategory)
            || "ACCOUNT_RESALE".equals(safetyCategory)
            || "PAYMENT_RISK".equals(safetyCategory)) {
            subtype = safetyCategory;
        } else if (broad) {
            subtype = "BROAD_OR_TEMPORAL";
            shouldCreateMaterial = false;
        } else if (hasAbuseOrLimitExploit) {
            type = ContentType.GUIDE;
            subtype = "ABUSE_OR_LIMIT_EXPLOIT";
        } else if (hasPromo) {
            subtype = "PROMO_OR_PROVIDER_OFFER";
        } else if (hasNews && hasProductUpdate) {
            subtype = normalized.contains("слух") || normalized.contains("rumor") ? "RUMOR_MONITORING" : "PRODUCT_CHANGE";
        } else if (hasNews) {
            subtype = normalized.contains("слух") || normalized.contains("rumor") ? "RUMOR_MONITORING" : "ANNOUNCEMENT";
        } else if (hasQuestion && !hasSteps) {
            subtype = subjectiveAdvice || !concreteAnswer ? "DISCUSSION_ONLY" : "QUESTION_ANSWER";
            shouldCreateMaterial = concreteAnswer && !subjectiveAdvice;
        } else if (hasReference && !hasSteps) {
            subtype = "REFERENCE_CARD";
            shouldCreateMaterial = concreteReference;
        } else if (hasSteps && useful) {
            type = ContentType.GUIDE;
            subtype = "PRACTICAL_WORKFLOW";
        } else if (useful) {
            subtype = "SHORT_INSIGHT";
            shouldCreateMaterial = concreteReference || concreteAnswer || hasProductUpdate || hasPromo;
        } else {
            subtype = messages.size() <= 1 ? "LOW_VALUE" : "WEAK_EVIDENCE";
            shouldCreateMaterial = false;
        }

        int riskScore = riskScore(safetyCategory, normalized);
        int noveltyScore = hasNews || hasProductUpdate ? 80 : hasAbuseOrLimitExploit ? 65 : hasPromo ? 55 : 35;
        int actionabilityScore = type == ContentType.GUIDE ? (hasSteps ? 82 : 55) : hasQuestion ? 45 : 30;
        int evidenceScore = hasAbuseOrLimitExploit ? Math.max(45, evidenceScore(messages, classifierResult)) : evidenceScore(messages, classifierResult);
        int noiseScore = noiseScore(messages, classifierResult, broad);
        int confidenceScore = confidenceScore(type, evidenceScore, noiseScore, classifierResult);
        int importanceScore = importanceScore(type, riskScore, noveltyScore, actionabilityScore, classifierResult);
        String label = synthesizeLabel(type, safetyCategory, normalized, cluster, candidate, classifierResult, messages);
        String summary = synthesizeSummary(type, label, messages, classifierResult);
        String angle = synthesizeAngle(type, label, normalized, candidate);

        return new ContentRoutingDecision(
            type,
            subtype,
            label,
            summary,
            titleFor(type, label),
            summary,
            normalizedTopicKey(label),
            angle,
            shouldCreateMaterial,
            type.generatesFullGuide(),
            confidenceScore / 100.0,
            importanceScore,
            actionabilityScore,
            noveltyScore,
            evidenceScore,
            riskScore,
            noiseScore,
            safetyCategory,
            type == ContentType.GUIDE ? "GUIDE" : "MATERIAL",
            "deterministic content routing fallback",
            List.of(),
            sectionsFor(type),
            null,
            null
        );
    }

    private ContentRoutingDecision parseRoutingResponse(
        String content,
        ModelCallService.ModelCallResult modelCall,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult
    ) throws Exception {
        ContentRoutingDecision fallback = deterministicRoute(cluster, candidate, sourceMessages, classifierResult);
        JsonNode node = objectMapper.readTree(extractJson(content));
        ContentType requestedType = node.hasNonNull("contentType")
            ? ContentType.from(node.path("contentType").asText(null))
            : fallback.contentType();
        ContentType type = outputType(requestedType);
        String topicLabel = nonBlank(node.path("topicLabel").asText(null), fallback.topicLabel());
        String topicSummary = nonBlank(node.path("topicSummary").asText(null), fallback.topicSummary());
        String contentTitle = nonBlank(node.path("contentTitle").asText(null), titleFor(type, topicLabel));
        String contentSummary = nonBlank(node.path("contentSummary").asText(null), topicSummary);
        double confidence = node.path("confidence").asDouble(fallback.confidence());
        String safetyCategory = nonBlank(node.path("safetyCategory").asText(null), fallback.safetyCategory());
        boolean shouldCreateMaterial = node.has("shouldCreateMaterial")
            ? node.path("shouldCreateMaterial").asBoolean(fallback.shouldCreateMaterial())
            : fallback.shouldCreateMaterial();
        if (requestedType == ContentType.DEFERRED || requestedType == ContentType.DISCUSSION_ONLY) {
            shouldCreateMaterial = false;
        }
        String normalizedSource = normalize(combinedText(sourceMessages == null ? List.of() : sourceMessages, classifierResult));
        boolean subjectiveAdvice = looksLikeSubjectiveAdviceRequest(normalizedSource);
        boolean abuseOrLimitExploit = hasAbuseOrLimitExploit(normalizedSource)
            || "ABUSE_OR_LIMIT_EXPLOIT".equalsIgnoreCase(safetyCategory);
        String contentSubtype = nonBlank(
            node.path("contentSubtype").asText(null),
            requestedType != type ? requestedType.name() : fallback.contentSubtype()
        );
        boolean genericFaq = requestedType == ContentType.FAQ
            || looksGenericFaqMaterial(topicLabel, contentTitle, contentSummary, node.path("specificAngle").asText(null));
        boolean weakQuestionAnswer = "QUESTION_ANSWER".equalsIgnoreCase(contentSubtype)
            && !abuseOrLimitExploit
            && !hasConcreteAnswerValue(normalizedSource, classifierResult);
        boolean weakFallback = !fallback.shouldCreateMaterial()
            && !abuseOrLimitExploit
            && requestedType != ContentType.GUIDE
            && requestedType != ContentType.NEWS
            && requestedType != ContentType.PRODUCT_UPDATE
            && requestedType != ContentType.WARNING
            && requestedType != ContentType.RISK_INSIGHT
            && requestedType != ContentType.REFERENCE
            && !hasConcreteReferenceValue(normalizedSource, classifierResult)
            && !hasConcreteAnswerValue(normalizedSource, classifierResult);
        boolean concreteUsefulInfo = type == ContentType.USEFUL_INFO
            && fallback.shouldCreateMaterial()
            && !genericFaq
            && !subjectiveAdvice
            && !weakQuestionAnswer
            && (hasConcreteReferenceValue(normalizedSource, classifierResult)
                || hasConcreteAnswerValue(normalizedSource, classifierResult)
                || isStrongUsefulSubtype(fallback.contentSubtype()));
        if (abuseOrLimitExploit) {
            type = ContentType.GUIDE;
            contentSubtype = "ABUSE_OR_LIMIT_EXPLOIT";
            shouldCreateMaterial = true;
            safetyCategory = "ABUSE_OR_LIMIT_EXPLOIT";
            if (genericFaq) {
                topicLabel = fallback.topicLabel();
                topicSummary = fallback.topicSummary();
                contentTitle = fallback.contentTitle();
                contentSummary = fallback.contentSummary();
            }
        }
        if ((genericFaq && !abuseOrLimitExploit)
            || (subjectiveAdvice && !abuseOrLimitExploit && requestedType != ContentType.GUIDE)) {
            shouldCreateMaterial = false;
        }
        if (weakQuestionAnswer || weakFallback) {
            shouldCreateMaterial = false;
        }
        if (concreteUsefulInfo) {
            shouldCreateMaterial = true;
            if (isNoMaterialSubtype(contentSubtype)) {
                contentSubtype = nonBlank(fallback.contentSubtype(), "SHORT_INSIGHT");
            }
        }
        if ((genericFaq && !abuseOrLimitExploit)
            || (subjectiveAdvice && !abuseOrLimitExploit)
            || weakQuestionAnswer) {
            contentSubtype = "DISCUSSION_ONLY";
        }
        int importanceScore = score(node.path("importanceScore").isMissingNode() ? null : node.path("importanceScore").asInt());
        int actionabilityScore = score(node.path("actionabilityScore").isMissingNode() ? null : node.path("actionabilityScore").asInt());
        int noveltyScore = score(node.path("noveltyScore").isMissingNode() ? null : node.path("noveltyScore").asInt());
        int evidenceScore = score(node.path("evidenceScore").isMissingNode() ? null : node.path("evidenceScore").asInt());
        int riskScore = score(node.path("riskScore").isMissingNode() ? null : node.path("riskScore").asInt());
        int noiseScore = score(node.path("noiseScore").isMissingNode() ? null : node.path("noiseScore").asInt());
        if (abuseOrLimitExploit) {
            importanceScore = Math.max(importanceScore, fallback.importanceScore());
            actionabilityScore = Math.max(actionabilityScore, 60);
            noveltyScore = Math.max(noveltyScore, 65);
            evidenceScore = Math.max(evidenceScore, 45);
            riskScore = Math.max(riskScore, 65);
        }
        return new ContentRoutingDecision(
            type,
            contentSubtype,
            trimTo(topicLabel, 180),
            trimTo(topicSummary, 1200),
            trimTo(contentTitle, 180),
            trimTo(contentSummary, 1200),
            nonBlank(node.path("normalizedTopicKey").asText(null), normalizedTopicKey(topicLabel)),
            nonBlank(node.path("specificAngle").asText(null), fallback.specificAngle()),
            shouldCreateMaterial,
            type.generatesFullGuide(),
            confidence,
            importanceScore,
            actionabilityScore,
            noveltyScore,
            evidenceScore,
            riskScore,
            noiseScore,
            safetyCategory,
            type == ContentType.GUIDE ? "GUIDE" : "MATERIAL",
            nonBlank(node.path("reason").asText(null), "LLM content routing"),
            textArray(node.path("warnings")),
            textArray(node.path("suggestedSections")),
            modelCall.providerId(),
            modelCall.response().model() != null ? modelCall.response().model() : modelCall.model()
        );
    }

    private String routingSystemPrompt() {
        return """
            You classify one Telegram discussion cluster into one of two product buckets.
            Do not generate the final article.
            Do not copy one message as a title.
            Summarize the whole discussion as a topic.
            Choose exactly one contentType: GUIDE or USEFUL_INFO.
            GUIDE means a real step-by-step workflow with validation and actionable steps.
            USEFUL_INFO means concrete facts worth keeping: news, risk note, reference, warning, provider offer, verified comparison, or short insight.
            Collect "абуз", abuse, trial-limit, quota, free-trial, and loophole discussions as GUIDE when they are concrete enough to identify the product/scheme.
            For abuse/loophole guides, do not provide operational exploitation steps; structure the guide around signal verification, prerequisites to inspect, risks, sustainability, and what to monitor.
            Do not create FAQ/Q&A materials for one-off advice, opinions, model recommendations, tool preferences, or vague "what should I try" discussions.
            If the cluster only contains subjective advice without verifiable facts, set shouldCreateMaterial=false.
            If the cluster is too broad, too weak, spammy, or duplicate-like, still return USEFUL_INFO but set shouldCreateMaterial=false.
            If unsafe or policy-risk content appears, return USEFUL_INFO and avoid operational instructions.
            User-facing fields must be in Russian: topicLabel, topicSummary, contentTitle, contentSummary, specificAngle, suggestedSections.
            Avoid generic labels such as Codex, Cursor, DROID, Offtopic, Main, Flood, Verified guide, FAQ, or Practical guide by cluster topic.
            Return strict JSON only.
            """;
    }

    private String routingUserPrompt(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        List<MessageEntity> sourceMessages,
        ClassifierResult classifierResult
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cluster:\n");
        sb.append("- id: ").append(cluster != null ? cluster.getId() : null).append("\n");
        sb.append("- groupId: ").append(cluster != null ? cluster.getGroupId() : null).append("\n");
        sb.append("- topicTitle: ").append(cluster != null ? redact(cluster.getTopicTitle()) : null).append("\n");
        sb.append("- currentTopicLabel: ").append(cluster != null ? redact(cluster.getTopicLabel()) : null).append("\n");
        sb.append("- currentTopicSummary: ").append(cluster != null ? redact(cluster.getTopicSummary()) : null).append("\n");
        sb.append("- safetyCategory: ").append(cluster != null ? cluster.getSafetyCategory() : null).append("\n");
        sb.append("- candidateAngle: ").append(candidate != null ? redact(candidate.getGuideAngle()) : null).append("\n");
        sb.append("- classifierResult: ").append(classifierResult != null ? redact(classifierResult.toString()) : null).append("\n\n");
        sb.append("Return JSON fields: contentType, contentSubtype, topicLabel, topicSummary, contentTitle, contentSummary, ");
        sb.append("normalizedTopicKey, specificAngle, shouldCreateMaterial, shouldGenerateFullGuide, confidence, ");
        sb.append("importanceScore, actionabilityScore, noveltyScore, evidenceScore, riskScore, noiseScore, ");
        sb.append("safetyCategory, reason, warnings, suggestedSections.\n\n");
        sb.append("Source messages:\n");
        List<MessageEntity> messages = sourceMessages == null ? List.of() : sourceMessages;
        for (MessageEntity message : messages.stream().limit(MAX_MESSAGES_IN_PROMPT).toList()) {
            sb.append("- id=").append(message.getId())
                .append(" date=").append(message.getMessageDate())
                .append(" topic=").append(redact(message.getTopicName()))
                .append(" text=").append(redact(trimTo(MessageTextFormatter.promptText(message), 600)))
                .append("\n");
        }
        return sb.toString();
    }

    private String combinedText(List<MessageEntity> messages, ClassifierResult classifierResult) {
        String classifierText = classifierResult == null ? "" : String.join(" ",
            nonBlank(classifierResult.meaningSummary(), ""),
            nonBlank(classifierResult.problemStatement(), ""),
            nonBlank(classifierResult.solutionHint(), ""),
            String.join(" ", classifierResult.labels()),
            String.join(" ", classifierResult.categories()),
            String.join(" ", classifierResult.mentionedTools()),
            String.join(" ", classifierResult.mentionedErrors())
        );
        String messageText = messages.stream()
            .map(message -> String.join(" ",
                nonBlank(message.getTopicName(), ""),
                nonBlank(message.getMeaningSummary(), ""),
                nonBlank(message.getProblemStatement(), ""),
                nonBlank(message.getSolutionHint(), ""),
                nonBlank(MessageTextFormatter.promptText(message), "")
            ))
            .collect(Collectors.joining(" "));
        return redact(classifierText + " " + messageText);
    }

    private String inferSafetyCategory(String normalizedText) {
        if (containsAny(normalizedText, List.of("jailbreak", "bypass", "обход", "джейлбрейк", "claude code bypass"))) {
            return "BYPASS";
        }
        if (containsAny(normalizedText, List.of("drm", "widevine", "protected video", "защищенное видео", "ключи drm"))) {
            return "DRM_COPYRIGHT";
        }
        if (containsAny(normalizedText, List.of("account resale", "перепродажа аккаун", "купить аккаун", "аккаунты дешево"))) {
            return "ACCOUNT_RESALE";
        }
        if (hasAbuseOrLimitExploit(normalizedText)) {
            return "ABUSE_OR_LIMIT_EXPLOIT";
        }
        if (containsAny(normalizedText, List.of("card", "карта", "cvv", "оплата", "payment", "gift", "гифт"))) {
            return "PAYMENT_RISK";
        }
        if (containsAny(normalizedText, List.of("crack", "backdoor", "hack", "кряк", "бекдор", "взлом"))) {
            return "HARMFUL";
        }
        return "NORMAL";
    }

    private String synthesizeLabel(
        ContentType type,
        String safetyCategory,
        String normalizedText,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity candidate,
        ClassifierResult classifierResult,
        List<MessageEntity> messages
    ) {
        if ("BYPASS".equals(safetyCategory)) {
            return "Риски обхода ограничений Claude Code";
        }
        if ("DRM_COPYRIGHT".equals(safetyCategory)) {
            return "Риски обхода DRM и защищенного видео";
        }
        if ("ACCOUNT_RESALE".equals(safetyCategory) || "PAYMENT_RISK".equals(safetyCategory)) {
            return "Риски оплаты и покупки аккаунтов";
        }
        if ("ABUSE_OR_LIMIT_EXPLOIT".equals(safetyCategory) || hasAbuseOrLimitExploit(normalizedText)) {
            if (normalizedText.contains("canva") || normalizedText.contains("канв")) {
                return "Canva Business: абуз trial и лимитов";
            }
            return "Абуз trial, лимитов или условий сервиса";
        }
        if (normalizedText.contains("logs_2.sqlite") || normalizedText.contains("logs 2 sqlite") || normalizedText.contains("logs 2.sqlite")) {
            return "Проблемы с базой logs_2.sqlite в Codex";
        }
        if (normalizedText.contains("termux") && normalizedText.contains("opencode")) {
            return "Запуск Opencode в Termux";
        }
        if (normalizedText.contains("алис") && normalizedText.contains("персонаж")) {
            return "Обновление персонажей в Алисе AI";
        }
        if (normalizedText.contains("gpt 5.6") || normalizedText.contains("gpt-5.6")) {
            return "Слухи и ожидания вокруг GPT-5.6";
        }
        if (normalizedText.contains("cursor") && normalizedText.contains("контекст")) {
            return "Контекст Cursor и ограничения чата";
        }
        if (normalizedText.contains("droid")) {
            return "Настройка Droid-конфига и доступов";
        }
        if (normalizedText.contains("byesu") || normalizedText.contains("gigacoder")) {
            return "Риски провайдеров и реферальных офферов";
        }
        if (normalizedText.contains("glm")) {
            return type == ContentType.DEFERRED
                ? "Смешанное обсуждение GLM и AI-сервисов"
                : "Практические выводы по GLM и AI-сервисам";
        }
        String specificLabel = specificLabelFromText(normalizedText);
        if (specificLabel != null) {
            return specificLabel;
        }
        String classifierLabel = firstUsefulText(
            classifierResult != null ? classifierResult.problemStatement() : null,
            classifierResult != null ? classifierResult.meaningSummary() : null,
            candidate != null ? candidate.getGuideAngle() : null,
            cluster != null ? cluster.getTopicLabel() : null
        );
        if (classifierLabel != null) {
            return clampWords(trimTo(classifierLabel, 120), 9);
        }
        return topTokenLabel(type, messages);
    }

    private String synthesizeSummary(ContentType type, String label, List<MessageEntity> messages, ClassifierResult classifierResult) {
        String fromClassifier = firstUsefulText(
            classifierResult != null ? classifierResult.meaningSummary() : null,
            classifierResult != null ? classifierResult.problemStatement() : null
        );
        if (fromClassifier != null) {
            return trimTo(redact(fromClassifier), 700);
        }
        String evidence = messages.stream()
            .map(MessageEntity::getText)
            .filter(value -> value != null && !value.isBlank())
            .map(value -> redact(value.replaceAll("\\s+", " ").trim()))
            .limit(2)
            .collect(Collectors.joining(" / "));
        if (!evidence.isBlank()) {
            return trimTo(label + ": " + evidence, 700);
        }
        return type + " material for " + label;
    }

    private String specificLabelFromText(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return null;
        }
        if (normalizedText.contains("verdent.ai") && normalizedText.contains("100 кредит")) {
            return "verdent.ai: расход кредитов на Claude Opus";
        }
        if (normalizedText.contains("runic") && (normalizedText.contains("генерац") || normalizedText.contains("изображ") || normalizedText.contains("фото"))) {
            return "Runic: пополнение в рублях и генерация изображений";
        }
        if (normalizedText.contains("runic") && (normalizedText.contains("новые модели") || normalizedText.contains("цены") || normalizedText.contains("openrouter"))) {
            return "Runic: новые модели и цены API";
        }
        if (normalizedText.contains("ramteamai")) {
            return "RamTeamAi: desktop-клиент для AI-агентов";
        }
        if (normalizedText.contains("подписк") && normalizedText.contains("api") && normalizedText.contains("код")) {
            return "Подписка vs API для кодинга";
        }
        if (normalizedText.contains("итератив") && normalizedText.contains("планирован") && normalizedText.contains("gemini")) {
            return "Выбор AI-модели для стратегического планирования";
        }
        if (normalizedText.contains("smmplanner")) {
            return "SMMplanner для автопостинга Instagram";
        }
        if (normalizedText.contains("hermes-patchkit")) {
            return "hermes-patchkit: reanchor на последнюю версию";
        }
        if ((normalizedText.contains("canva") || normalizedText.contains("канв")) && hasAbuseOrLimitExploit(normalizedText)) {
            return "Canva Business: абуз trial и лимитов";
        }
        return null;
    }

    private String synthesizeAngle(
        ContentType type,
        String label,
        String normalizedText,
        TopicClusterGuideCandidateEntity candidate
    ) {
        if (type == ContentType.GUIDE) {
            if ("ABUSE_OR_LIMIT_EXPLOIT".equals(inferSafetyCategory(normalizedText)) || hasAbuseOrLimitExploit(normalizedText)) {
                return "Как разобрать абуз trial/лимитов и оценить риски";
            }
            if (normalizedText.contains("logs_2.sqlite") || normalizedText.contains("logs 2 sqlite") || normalizedText.contains("logs 2.sqlite")) {
                return "Как временно остановить рост logs_2.sqlite в Codex";
            }
            if (normalizedText.contains("termux") && normalizedText.contains("opencode")) {
                return "Как подготовить Opencode к запуску в Termux";
            }
            String candidateAngle = candidate != null ? candidate.getGuideAngle() : null;
            if (candidateAngle != null && !candidateAngle.isBlank()) {
                return clampWords(candidateAngle, 12);
            }
            return "Практический workflow: " + label;
        }
        return switch (type) {
            case USEFUL_INFO -> "Ключевой вывод из обсуждения";
            default -> "Ключевой вывод из обсуждения";
        };
    }

    private String titleFor(ContentType type, String label) {
        return switch (type) {
            case GUIDE -> label;
            case NEWS -> "Новость: " + label;
            case PRODUCT_UPDATE -> "Обновление: " + label;
            case FAQ -> "FAQ: " + label;
            case WARNING -> "Предупреждение: " + label;
            case RISK_INSIGHT -> "Риск: " + label;
            case REFERENCE -> "Справка: " + label;
            case USEFUL_INFO -> "Полезное: " + label;
            case DISCUSSION_ONLY -> "Обсуждение: " + label;
            case DEFERRED -> "Отложено: " + label;
        };
    }

    private List<String> sectionsFor(ContentType type) {
        return switch (type) {
            case GUIDE -> List.of("Задача", "Когда использовать", "Шаги", "Проверка", "Риски", "Источники");
            case USEFUL_INFO -> List.of("Главный вывод", "Контекст", "Когда полезно", "Что проверить");
            default -> List.of();
        };
    }

    private ContentType outputType(ContentType type) {
        return type == ContentType.GUIDE ? ContentType.GUIDE : ContentType.USEFUL_INFO;
    }

    private boolean looksBroadOrTemporal(TopicDiscussionClusterEntity cluster, List<MessageEntity> messages, String normalizedText) {
        if (cluster != null && cluster.getStartAt() != null && cluster.getEndAt() != null
            && Duration.between(cluster.getStartAt(), cluster.getEndAt()).toHours() > 8) {
            return true;
        }
        if (!messages.isEmpty()) {
            Instant start = messages.stream().map(MessageEntity::getMessageDate).filter(value -> value != null).min(Instant::compareTo).orElse(null);
            Instant end = messages.stream().map(MessageEntity::getMessageDate).filter(value -> value != null).max(Instant::compareTo).orElse(null);
            if (start != null && end != null && Duration.between(start, end).toHours() > 8) {
                return true;
            }
        }
        int topicMarkers = 0;
        for (String marker : List.of("glm", "evomap", "credits", "cursor", "codex", "claude", "gemini", "api", "payment", "referral")) {
            if (normalizedText.contains(marker)) {
                topicMarkers++;
            }
        }
        return messages.size() >= 12 && topicMarkers >= 4;
    }

    private boolean looksLikeSubjectiveAdviceRequest(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        boolean asksForChoice = containsAny(normalizedText, List.of(
            "что еще можно попробовать",
            "что попробовать",
            "есть у кого идеи",
            "может есть идеи",
            "подобрать лучш",
            "лучшая модель",
            "лучший инструмент",
            "лучший сервис",
            "какую модель",
            "какой инструмент",
            "какой сервис",
            "что выбрать",
            "посоветуйте",
            "посоветуй",
            "recommend",
            "best tool",
            "best service",
            "what should i try",
            "what to try",
            "нравится как пишет",
            "холодно отвечал",
            "стоит того чтобы подождать"
        ));
        boolean choiceContext = containsAny(normalizedText, List.of(
            "gemini",
            "sonnet",
            "opus",
            "claude",
            "openai",
            "flash",
            "модель",
            "модели",
            "tool",
            "service",
            "инструмент",
            "сервис",
            "провайдер"
        ));
        boolean hasStructuredComparison = containsAny(normalizedText, List.of(
            "сравн",
            "таблица",
            "benchmark",
            "бенчмарк",
            "тестировал",
            "проверил"
        )) && hasConcreteReferenceValue(normalizedText, null);
        return asksForChoice && choiceContext && !hasStructuredComparison;
    }

    private boolean hasConcreteReferenceValue(String normalizedText, ClassifierResult classifierResult) {
        if (normalizedText == null) {
            normalizedText = "";
        }
        boolean hasLink = looksLikeUrlOrDomain(normalizedText);
        boolean hasMetric = Pattern.compile("\\d+\\s*(%|/|₽|руб|usd|\\$|credit|credits|кредит|токен|token|запрос)").matcher(normalizedText).find();
        boolean hasVersion = Pattern.compile("\\b\\d+(\\.\\d+){1,3}\\b").matcher(normalizedText).find()
            || containsAny(normalizedText, List.of("версия", "релиз", "released", "changelog"));
        boolean classifierHasConcreteValue = classifierResult != null
            && (!classifierResult.mentionedPrices().isEmpty()
                || !classifierResult.mentionedErrors().isEmpty()
                || score(classifierResult.technicalDepthScore()) >= 60);
        return hasLink || hasMetric || hasVersion || classifierHasConcreteValue;
    }

    private boolean looksLikeUrlOrDomain(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        if (normalizedText.contains("http://") || normalizedText.contains("https://")) {
            return true;
        }
        if (Pattern.compile("\\bhttps?\\b.*\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)+\\b")
            .matcher(normalizedText)
            .find()) {
            return true;
        }
        return Pattern.compile("\\b[a-z0-9][a-z0-9-]*\\.(?:ai|app|cloud|com|dev|gg|io|net|org|ru|sh|so|tools|xyz)\\b")
            .matcher(normalizedText)
            .find();
    }

    private boolean hasConcreteAnswerValue(String normalizedText, ClassifierResult classifierResult) {
        if (hasConcreteReferenceValue(normalizedText, classifierResult)) {
            return true;
        }
        boolean hasReproducibleAction = containsAny(normalizedText, List.of(
            "команда",
            "настрой",
            "конфиг",
            "ошибка",
            "провер",
            "шаг",
            "docker",
            "npm",
            "git",
            "curl",
            "sqlite",
            "logs_2"
        ));
        return hasReproducibleAction && classifierResult != null && score(classifierResult.technicalDepthScore()) >= 45;
    }

    private boolean looksGenericFaqMaterial(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.contains("faq")
                || normalized.contains("question short answer")
                || normalized.contains("short answer explanation caveats")
                || normalized.contains("source context")
                || normalized.contains("практический гайд по теме кластера")) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrongUsefulSubtype(String subtype) {
        String normalized = normalize(subtype);
        return normalized.equals("product change")
            || normalized.equals("announcement")
            || normalized.equals("rumor monitoring")
            || normalized.equals("promo or provider offer")
            || normalized.equals("reference card")
            || normalized.equals("short insight");
    }

    private boolean isNoMaterialSubtype(String subtype) {
        String normalized = normalize(subtype);
        return normalized.equals("discussion only")
            || normalized.equals("low value")
            || normalized.equals("weak evidence")
            || normalized.equals("broad or temporal")
            || normalized.equals("question answer")
            || normalized.equals("faq");
    }

    private int evidenceScore(List<MessageEntity> messages, ClassifierResult classifierResult) {
        int messageScore = Math.min(80, messages.size() * 18);
        int classifierScore = classifierResult != null ? score(classifierResult.guidePotentialScore()) / 3 : 0;
        return score(Math.max(25, messageScore + classifierScore));
    }

    private int noiseScore(List<MessageEntity> messages, ClassifierResult classifierResult, boolean broad) {
        int score = broad ? 70 : 20;
        int spam = classifierResult != null ? score(classifierResult.spamScore()) : 0;
        return score(score + spam / 2 + Math.max(0, messages.size() - 8) * 4);
    }

    private int confidenceScore(ContentType type, int evidenceScore, int noiseScore, ClassifierResult classifierResult) {
        int base = type == ContentType.DEFERRED || type == ContentType.DISCUSSION_ONLY ? 45 : 62;
        int classifier = classifierResult != null ? (int) Math.round(classifierResult.score() * 30.0) : 10;
        return score(base + classifier + evidenceScore / 10 - noiseScore / 5);
    }

    private int importanceScore(
        ContentType type,
        int riskScore,
        int noveltyScore,
        int actionabilityScore,
        ClassifierResult classifierResult
    ) {
        int signal = classifierResult != null
            ? Math.max(score(classifierResult.problemSignalScore()), score(classifierResult.urgencyScore()))
            : 0;
        return switch (type) {
            case WARNING, RISK_INSIGHT -> score(60 + riskScore / 3 + signal / 5);
            case NEWS, PRODUCT_UPDATE -> score(45 + noveltyScore / 2 + signal / 5);
            case GUIDE -> score(45 + actionabilityScore / 2 + signal / 5);
            case FAQ, USEFUL_INFO, REFERENCE -> score(40 + Math.max(actionabilityScore, noveltyScore) / 3 + signal / 6);
            default -> score(25 + signal / 5);
        };
    }

    private int riskScore(String safetyCategory, String normalizedText) {
        if ("HARMFUL".equals(safetyCategory) || "BYPASS".equals(safetyCategory)) {
            return 90;
        }
        if ("DRM_COPYRIGHT".equals(safetyCategory)
            || "ACCOUNT_RESALE".equals(safetyCategory)
            || "PAYMENT_RISK".equals(safetyCategory)) {
            return 78;
        }
        if ("ABUSE_OR_LIMIT_EXPLOIT".equals(safetyCategory) || hasAbuseOrLimitExploit(normalizedText)) {
            return 65;
        }
        if (containsAny(normalizedText, List.of("risk", "риск", "бан", "tos", "серый"))) {
            return 55;
        }
        return 20;
    }

    private boolean hasAbuseOrLimitExploit(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        boolean explicitAbuse = containsAny(normalizedText, List.of(
            "абуз",
            "abuse",
            "exploit",
            "loophole",
            "free trial",
            "триал",
            "фри триал"
        ));
        boolean trialOrQuota = containsAny(normalizedText, List.of("trial", "лимит", "quota", "credits", "кредит"));
        boolean serviceContext = containsAny(normalizedText, List.of(
            "canva",
            "канв",
            "business",
            "claude",
            "openai",
            "gemini",
            "api",
            "provider",
            "провайдер",
            "сервис"
        ));
        return explicitAbuse || trialOrQuota && serviceContext && normalizedText.contains("обход");
    }

    private String topTokenLabel(ContentType type, List<MessageEntity> messages) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (MessageEntity message : messages) {
            for (String token : normalize(nonBlank(message.getText(), "")).split(" ")) {
                if (token.length() >= 4 && tokens.size() < 5 && !List.of("можно", "нужно", "через", "будет", "такой").contains(token)) {
                    tokens.add(token);
                }
            }
        }
        if (tokens.size() >= 2) {
            return clampWords(String.join(" ", tokens), 9);
        }
        return switch (type) {
            case NEWS -> "Новость из обсуждения AI-инструментов";
            case PRODUCT_UPDATE -> "Обновление AI-продукта из обсуждения";
            case FAQ -> "Вопрос по AI-инструменту";
            case WARNING -> "Предупреждение по рискованной практике";
            case RISK_INSIGHT -> "Анализ риска в обсуждении";
            case REFERENCE -> "Справочная информация из обсуждения";
            case USEFUL_INFO -> "Полезный вывод из обсуждения";
            case GUIDE -> "Практическая задача из обсуждения";
            default -> "Слабый или смешанный кластер";
        };
    }

    private String normalizedTopicKey(String label) {
        String normalized = normalize(label);
        if (normalized.isBlank()) {
            return "unknown_topic";
        }
        return normalized.replace(' ', '_');
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty routing response");
        }
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("routing response does not contain a JSON object");
        }
        return trimmed.substring(start, end + 1);
    }

    private List<String> textArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            String value = child.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private boolean containsAny(String text, List<String> markers) {
        return markers.stream().anyMatch(text::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}.+-]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        return SENSITIVE_VALUE_PATTERN.matcher(value).replaceAll("$1=<redacted>");
    }

    private int score(Integer value) {
        return value == null ? 0 : score(value.intValue());
    }

    private int score(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }

    private String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private String firstUsefulText(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = redact(value.replaceAll("\\s+", " ").trim());
            if (trimmed.length() >= 8) {
                String normalized = normalize(trimmed);
                if (looksGenericFaqMaterial(normalized) || normalized.contains("практический гайд по теме кластера")) {
                    continue;
                }
                return trimmed;
            }
        }
        return null;
    }

    private String clampWords(String value, int maxWords) {
        if (value == null) {
            return null;
        }
        String[] words = value.trim().split("\\s+");
        if (words.length <= maxWords) {
            return value.trim();
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < maxWords; i++) {
            selected.add(words[i]);
        }
        return String.join(" ", selected);
    }
}
