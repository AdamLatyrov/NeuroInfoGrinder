package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiUsageLogEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiUsageLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ClassifierRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private static final double CLASSIFIER_THRESHOLD = 0.75;
    private static final double AUTO_PUBLISH_CONFIDENCE = 0.85;
    private static final double COST_PER_TOKEN_USD = 0.00002;
    private static final int TRACE_DATA_MAX_LEN = 2000;

    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final GuideRepository guideRepository;
    private final GuideSourceMessageRepository guideSourceMessageRepository;
    private final AiProviderRepository aiProviderRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final ClassifierRepository classifierRepository;
    private final PromptRepository promptRepository;
    private final SettingsRepository settingsRepository;

    private final SignalScorer signalScorer;
    private final RuleRunner ruleRunner;
    private final MessageChainBuilder messageChainBuilder;
    private final ClassifierRunner classifierRunner;
    private final GuideGenerator guideGenerator;
    private final PipelineTraceService pipelineTraceService;
    private final ObjectMapper objectMapper;

    private final List<PipelineProgress> progressLog = new ArrayList<>();

    @Value("${neuroinfogrinder.pipeline.signal-threshold:0.30}")
    private double signalThreshold;

    @Value("${neuroinfogrinder.pipeline.local-guide-fallback:false}")
    private boolean localGuideFallback;

    public GuideEntity processMessage(Long messageId) {
        progressLog.clear();
        String traceId = UUID.randomUUID().toString();
        log.info("Pipeline started for message {} (traceId={})", messageId, traceId);

        MessageEntity message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        GroupEntity group = groupRepository.findById(message.getGroupId())
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + message.getGroupId()));

        if (!"UNPROCESSED".equals(message.getProcessingStatus()) && !"QUEUED".equals(message.getProcessingStatus())) {
            log.info("Message {} already has status {}, skipping", messageId, message.getProcessingStatus());
            return null;
        }

        if (!Boolean.TRUE.equals(group.getEnabled())) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            recordProgress("PROCESSING", "SKIPPED", "Group " + group.getId() + " is disabled");
            return null;
        }

        message.setProcessingStatus("PROCESSING");
        messageRepository.save(message);
        recordProgress("PROCESSING", "STARTED", "Message " + messageId + " entered pipeline");

        SettingsEntity settings = getSettings();
        String text = message.getText() != null ? message.getText() : "";

        if (Boolean.TRUE.equals(settings.getFilterSkipBots()) && Boolean.TRUE.equals(message.getIsBot())) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            recordProgress("PRE_FILTER", "SKIPPED", "Bot message filtered");
            return null;
        }

        if (settings.getFilterMinMessageLength() != null && text.length() < settings.getFilterMinMessageLength()) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            recordProgress("PRE_FILTER", "SKIPPED", "Message too short");
            return null;
        }

        if (settings.getFilterBlacklistWords() != null && !settings.getFilterBlacklistWords().isBlank()) {
            String lowerText = text.toLowerCase();
            for (String word : settings.getFilterBlacklistWords().split(",")) {
                String trimmed = word.trim().toLowerCase();
                if (!trimmed.isEmpty() && lowerText.contains(trimmed)) {
                    message.setProcessingStatus("SKIPPED");
                    messageRepository.save(message);
                    recordProgress("PRE_FILTER", "SKIPPED", "Blacklisted word: " + trimmed);
                    return null;
                }
            }
        }

        Instant rulesStart = Instant.now();
        RuleResult ruleResult = ruleRunner.evaluate(message);
        Instant rulesEnd = Instant.now();
        recordProgress("RULES", ruleResult.passes() ? "PASSED" : "REJECTED", ruleResult.reason());

        String ruleResultJson = safeToJson(ruleResult);
        message.setRuleResultJson(truncate(ruleResultJson, 4000));

        String rulesInput = ruleResult.checks() != null
            ? ruleResult.checks().stream()
                .map(c -> c.ruleName() + ":" + c.actionType() + " [" + c.detail() + "]")
                .reduce((a, b) -> a + "; " + b)
                .orElse("no checks")
            : "no checks";
        pipelineTraceService.createTrace(
            traceId, messageId, message.getGroupId(),
            "RULES", ruleResult.passes() ? "PASSED" : "REJECTED",
            rulesStart, rulesEnd, Duration.between(rulesStart, rulesEnd).toMillis(),
            truncate(rulesInput, TRACE_DATA_MAX_LEN),
            truncate(ruleResultJson, TRACE_DATA_MAX_LEN),
            null, null, null, null, null,
            null, null, null, null,
            ruleResult.passes() ? 1.0 : 0.0, null, ruleResult.reason()
        );

        if (!ruleResult.passes()) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            return null;
        }

        Instant scoreStart = Instant.now();
        SignalScore signalScore = signalScorer.score(message);
        Instant scoreEnd = Instant.now();
        message.setSignalScore(signalScore.score());
        recordProgress("SIGNAL_SCORING", "COMPLETED",
            "Score: " + signalScore.score() + " | Breakdown: " + signalScore.breakdown());

        String breakdownJson = safeToJson(signalScore.breakdown());
        message.setSignalBreakdown(truncate(breakdownJson, 4000));

        String scoringStatusLabel = signalScore.score() >= signalThreshold ? "PASSED" : "SKIPPED";
        pipelineTraceService.createTrace(
            traceId, messageId, message.getGroupId(),
            "SIGNAL_SCORING", scoringStatusLabel,
            scoreStart, scoreEnd, Duration.between(scoreStart, scoreEnd).toMillis(),
            truncate(breakdownJson, TRACE_DATA_MAX_LEN),
            truncate("totalScore=" + signalScore.score() + " threshold=" + signalThreshold, TRACE_DATA_MAX_LEN),
            null, null, null, null, null,
            null, null, null, null,
            signalScore.score(), null,
            signalScore.score() >= signalThreshold
                ? "Score " + signalScore.score() + " above threshold " + signalThreshold
                : "Score " + signalScore.score() + " below threshold " + signalThreshold
        );

        if (signalScore.score() < signalThreshold) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            return null;
        }

        message.setProcessingStatus("CLASSIFIED");
        messageRepository.save(message);
        recordProgress("SIGNAL_SCORING", "PASSED",
            "Score " + signalScore.score() + " above threshold " + signalThreshold);

        List<MessageEntity> chain = messageChainBuilder.buildChain(messageId);
        recordProgress("CHAIN_BUILDING", "COMPLETED", "Chain size: " + chain.size() + " messages");

        List<ClassifierEntity> classifiers = findActiveClassifiers();
        if (classifiers.isEmpty()) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            recordProgress("CLASSIFICATION", "SKIPPED", "No active classifiers found");
            pipelineTraceService.createTrace(
                traceId, messageId, message.getGroupId(),
                "CLASSIFICATION", "SKIPPED",
                Instant.now(), Instant.now(), 0L,
                null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, "No active classifiers found"
            );
            return null;
        }

        ClassifierEntity selectedClassifier = null;
        ClassifierResult selectedClassifierResult = null;
        ClassifierEntity bestObservedClassifier = null;
        ClassifierResult bestObservedResult = null;

        for (ClassifierEntity classifier : classifiers) {
            Instant clsStart = Instant.now();
            ClassifierResult classifierResult = classifierRunner.classify(chain, classifier);
            Instant clsEnd = Instant.now();

            if (bestObservedResult == null || classifierResult.score() > bestObservedResult.score()) {
                bestObservedClassifier = classifier;
                bestObservedResult = classifierResult;
            }

            boolean classifierPassed = classifierResult.matched() && classifierResult.score() >= CLASSIFIER_THRESHOLD;
            if (classifierPassed
                && (selectedClassifierResult == null || classifierResult.score() > selectedClassifierResult.score())) {
                selectedClassifier = classifier;
                selectedClassifierResult = classifierResult;
            }

            pipelineTraceService.createTrace(
                traceId, messageId, message.getGroupId(),
                "CLASSIFICATION", classifierPassed ? "PASSED" : "SKIPPED",
                clsStart, clsEnd, Duration.between(clsStart, clsEnd).toMillis(),
                truncate(
                    "classifierType=" + classifier.getType()
                        + " | classifierId=" + classifier.getId()
                        + " | classifierName=" + classifier.getName()
                        + " | chainSize=" + chain.size()
                        + " | providerId=" + classifier.getProviderId(),
                    TRACE_DATA_MAX_LEN
                ),
                truncate(safeToJson(classifierResult), TRACE_DATA_MAX_LEN),
                null, null, classifier.getId(), classifier.getPromptId(), classifier.getProviderId(),
                null, null, null, null,
                classifierResult.score(), null,
                classifier.getName() + ": " + classifierResult.reasoning()
            );

            if (classifierPassed && "LLM".equalsIgnoreCase(classifier.getType())) {
                recordProgress("CLASSIFICATION", "EARLY_STOP",
                    "Stopped after passing LLM classifier " + classifier.getName());
                break;
            }

            if (classifierPassed
                && !"LLM".equalsIgnoreCase(classifier.getType())
                && classifierResult.score() >= 0.90) {
                recordProgress("CLASSIFICATION", "EARLY_STOP",
                    "Stopped after strong deterministic classifier " + classifier.getName());
                break;
            }
        }

        ClassifierEntity classifier = selectedClassifier != null ? selectedClassifier : bestObservedClassifier;
        ClassifierResult classifierResult = selectedClassifierResult != null ? selectedClassifierResult : bestObservedResult;

        message.setClassifierScore(classifierResult != null ? classifierResult.score() : null);
        message.setClassifierReason(classifierResult != null
            ? (classifier != null ? classifier.getName() + ": " : "") + classifierResult.reasoning()
            : null);

        if (selectedClassifierResult == null || selectedClassifier == null) {
            message.setProcessingStatus("SKIPPED");
            messageRepository.save(message);
            recordProgress("CLASSIFICATION", "SKIPPED",
                "No classifier passed threshold; best score="
                    + (bestObservedResult != null ? bestObservedResult.score() : 0.0));
            return null;
        }

        recordProgress("CLASSIFICATION", "COMPLETED",
            "Classifier=" + classifier.getName()
                + " | Type=" + classifier.getType()
                + " | Score=" + classifierResult.score()
                + " | Matched=" + classifierResult.matched());

        AiProviderEntity provider = findActiveProvider();
        boolean usingLocalFallback = provider == null && localGuideFallback;
        PromptEntity guidePrompt = findGuidePrompt();

        if (provider == null && !usingLocalFallback) {
            recordProgress("GUIDE_GENERATION", "FAILED", "No active AI provider found");
            GuideContent failedContent = new GuideContent(
                "Гайд не создан: недоступен AI-провайдер",
                "No active AI provider found",
                "## Гайд не создан\n\nПричина: недоступен AI-провайдер.",
                0.0,
                List.of("AI", "ошибка", "провайдер"),
                "No active AI provider found"
            );

            pipelineTraceService.createTrace(
                traceId, messageId, message.getGroupId(),
                "GUIDE_GENERATION", "FAILED",
                Instant.now(), Instant.now(), 0L,
                null, null, "No active AI provider found",
                null, classifier.getId(),
                guidePrompt != null ? guidePrompt.getId() : null,
                null, null,
                null, null, null,
                null, failedContent.confidence(),
                "No active AI provider found"
            );

            GuideEntity failedGuide = saveGuideAndSources(
                message,
                chain,
                classifier,
                guidePrompt,
                null,
                failedContent,
                0,
                0,
                0,
                0.0,
                false
            );
            message.setProcessingStatus("ERROR");
            message.setGuideId(failedGuide.getId());
            messageRepository.save(message);
            return failedGuide;
        }

        if (usingLocalFallback) {
            recordProgress("GUIDE_GENERATION", "LOCAL_FALLBACK",
                "No active AI provider found; created local draft guide");
        }

        Instant guideStart = Instant.now();
        GuideContent guideContent = usingLocalFallback
            ? buildLocalFallbackGuide(chain, classifierResult)
            : guideGenerator.generate(
                chain,
                classifierResult,
                provider.getId(),
                guidePrompt != null ? guidePrompt.getId() : null,
                message.getId()
            );
        Instant guideEnd = Instant.now();

        int estInputTokens = estimateTokens(chain, classifier, guidePrompt);
        int estOutputTokens = estimateOutputTokens(guideContent);
        int estTotalTokens = estInputTokens + estOutputTokens;
        double estCost = estTotalTokens * COST_PER_TOKEN_USD;
        boolean guideFailed = guideContent.generationError() != null && !guideContent.generationError().isBlank();

        recordProgress(
            "GUIDE_GENERATION",
            guideFailed ? "FAILED" : "COMPLETED",
            "Title: " + guideContent.title() + " | Confidence: " + guideContent.confidence()
        );

        pipelineTraceService.createTrace(
            traceId, messageId, message.getGroupId(),
            "GUIDE_GENERATION", guideFailed ? "FAILED" : "COMPLETED",
            guideStart, guideEnd, Duration.between(guideStart, guideEnd).toMillis(),
            truncate(
                "providerId=" + (provider != null ? provider.getId() : "LOCAL_FALLBACK")
                    + " | model=" + (provider != null ? provider.getModel() : "local-fallback")
                    + " | promptId=" + (guidePrompt != null ? guidePrompt.getId() : "null")
                    + " | chainSize=" + chain.size()
                    + " | classifierScore=" + classifierResult.score(),
                TRACE_DATA_MAX_LEN
            ),
            truncate(safeToJson(guideContent), TRACE_DATA_MAX_LEN),
            guideFailed ? truncate(guideContent.generationError(), TRACE_DATA_MAX_LEN) : null,
            null, classifier.getId(),
            guidePrompt != null ? guidePrompt.getId() : null,
            provider != null ? provider.getId() : null,
            provider != null ? provider.getModel() : "local-fallback",
            estInputTokens, estOutputTokens, estCost,
            null, guideContent.confidence(),
            (usingLocalFallback ? "LOCAL_FALLBACK | " : "") + "Title: " + guideContent.title()
        );

        GuideEntity guide = saveGuideAndSources(
            message,
            chain,
            classifier,
            guidePrompt,
            provider,
            guideContent,
            estInputTokens,
            estOutputTokens,
            estTotalTokens,
            estCost,
            usingLocalFallback
        );

        if (provider != null) {
            if (guideFailed) {
                markProviderError(provider, guideContent.generationError());
            } else {
                markProviderHealthy(provider);
            }
        }

        if (guideFailed) {
            message.setProcessingStatus("ERROR");
            message.setGuideId(guide.getId());
            messageRepository.save(message);
            recordProgress("MESSAGE_STATUS_UPDATED", "COMPLETED", "Root message set to ERROR");
            log.info("Pipeline completed with failure for message {} -> guide {} (traceId={})",
                messageId, guide.getId(), traceId);
            return guide;
        }

        AiUsageLogEntity usageLog = new AiUsageLogEntity();
        usageLog.setTaskType(usingLocalFallback ? "PIPELINE_LOCAL_FALLBACK_GUIDE" : "PIPELINE_CLASSIFY_AND_GENERATE");
        usageLog.setProviderId(provider != null ? provider.getId() : null);
        usageLog.setModel(provider != null ? provider.getModel() : "local-fallback");
        usageLog.setGuideId(guide.getId());
        usageLog.setInputTokens(estInputTokens);
        usageLog.setOutputTokens(estOutputTokens);
        usageLog.setTotalTokens(estTotalTokens);
        usageLog.setEstimatedCostUsd(estCost);
        aiUsageLogRepository.save(usageLog);
        recordProgress("AI_USAGE_LOGGED", "COMPLETED",
            "Tokens: " + estTotalTokens + " | Cost: $" + String.format("%.6f", estCost));

        message.setProcessingStatus("GUIDE_FOUND");
        message.setGuideId(guide.getId());
        messageRepository.save(message);

        for (MessageEntity chainMsg : chain) {
            if ("UNPROCESSED".equals(chainMsg.getProcessingStatus())) {
                chainMsg.setProcessingStatus("GUIDE_FOUND");
                chainMsg.setGuideId(guide.getId());
                messageRepository.save(chainMsg);
            }
        }
        recordProgress("MESSAGE_STATUS_UPDATED", "COMPLETED",
            "Root message and chain messages set to GUIDE_FOUND");

        SettingsEntity publishSettings = getSettings();
        if ("AUTO_PUBLISH".equals(publishSettings.getPublicationMode())
            && guideContent.confidence() > AUTO_PUBLISH_CONFIDENCE) {
            guide.setStatus("PUBLISHED");
            guide.setPublishedAt(Instant.now());
            guide = guideRepository.save(guide);
            recordProgress("AUTO_PUBLISH", "COMPLETED",
                "Guide auto-published with confidence " + guideContent.confidence());
        } else {
            recordProgress("AUTO_PUBLISH", "SKIPPED",
                "Mode=" + publishSettings.getPublicationMode()
                    + " | Confidence=" + guideContent.confidence()
                    + " (threshold=" + AUTO_PUBLISH_CONFIDENCE + ")");
        }

        log.info("Pipeline completed for message {} -> guide {} (traceId={})", messageId, guide.getId(), traceId);
        return guide;
    }

    public List<PipelineProgress> getProgressLog() {
        return new ArrayList<>(progressLog);
    }

    private GuideEntity saveGuideAndSources(
        MessageEntity message,
        List<MessageEntity> chain,
        ClassifierEntity classifier,
        PromptEntity guidePrompt,
        AiProviderEntity provider,
        GuideContent guideContent,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        double estimatedCostUsd,
        boolean usingLocalFallback
    ) {
        GuideEntity guide = new GuideEntity();
        guide.setTitle(guideContent.title());
        guide.setContent(guideContent.content());
        guide.setContentMarkdown(guideContent.contentMarkdown());
        guide.setGroupId(message.getGroupId());
        guide.setRootMessageId(message.getId());
        guide.setProviderId(provider != null ? provider.getId() : null);
        guide.setModel(provider != null ? provider.getModel() : (usingLocalFallback ? "local-fallback" : null));
        guide.setClassifierId(classifier.getId());
        guide.setPromptId(guidePrompt != null ? guidePrompt.getId() : null);
        guide.setPromptVersion(guidePrompt != null ? guidePrompt.getVersion() : null);
        guide.setStatus(guideContent.generationError() != null ? "FAILED" : "DRAFT");
        guide.setConfidence(guideContent.confidence());
        guide.setInputTokens(inputTokens);
        guide.setOutputTokens(outputTokens);
        guide.setTotalTokens(totalTokens);
        guide.setEstimatedCostUsd(estimatedCostUsd);
        guide.setTagsJson(safeToJson(guideContent.tags()));
        guide.setGenerationError(guideContent.generationError());

        guide = guideRepository.save(guide);
        recordProgress("GUIDE_SAVED", "COMPLETED", "Guide ID: " + guide.getId());

        for (MessageEntity chainMsg : chain) {
            GuideSourceMessageEntity sourceMsg = new GuideSourceMessageEntity();
            sourceMsg.setGuideId(guide.getId());
            sourceMsg.setMessageId(chainMsg.getId());
            sourceMsg.setUsedInPrompt(true);
            guideSourceMessageRepository.save(sourceMsg);
        }
        recordProgress("SOURCE_MESSAGES", "COMPLETED",
            "Created " + chain.size() + " source message links");
        return guide;
    }

    private GuideContent buildLocalFallbackGuide(List<MessageEntity> chain, ClassifierResult classifierResult) {
        String title = chain.stream()
            .map(MessageEntity::getText)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .findFirst()
            .map(value -> value.length() > 90 ? value.substring(0, 90) + "..." : value)
            .orElse("Локальный тестовый гайд");

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("> Тестовый fallback-гайд: AI-провайдер выключен, поэтому текст собран локально без LLM.\n\n");
        markdown.append("## Почему сообщение прошло\n\n");
        markdown.append("- Score классификатора: ").append(classifierResult.score()).append("\n");
        markdown.append("- Matched: ").append(classifierResult.matched()).append("\n");
        markdown.append("- Reasoning: ").append(classifierResult.reasoning()).append("\n\n");
        markdown.append("## Исходные сообщения\n\n");

        for (MessageEntity item : chain) {
            String sender = item.getSenderName() != null && !item.getSenderName().isBlank()
                ? item.getSenderName()
                : "Unknown";
            String content = item.getText() != null && !item.getText().isBlank()
                ? item.getText().trim()
                : "[пустое сообщение]";
            markdown.append("- **").append(sender).append("**: ").append(content).append("\n");
        }

        String content = markdown.toString();
        return new GuideContent(
            title,
            content,
            content,
            0.55,
            List.of("локальный fallback", "AI", "черновик"),
            null
        );
    }

    private void markProviderHealthy(AiProviderEntity provider) {
        provider.setStatus("ACTIVE");
        provider.setLastTestResult("PIPELINE_OK");
        provider.setLastError(null);
        aiProviderRepository.save(provider);
    }

    private void markProviderError(AiProviderEntity provider, String error) {
        provider.setStatus("ERROR");
        provider.setLastTestResult("PIPELINE_FAILED");
        provider.setLastError(truncate(error, 4000));
        aiProviderRepository.save(provider);
    }

    private void recordProgress(String stage, String status, String details) {
        progressLog.add(new PipelineProgress(stage, status, details));
        log.debug("Pipeline progress: {} - {} | {}", stage, status, details);
    }

    private List<ClassifierEntity> findActiveClassifiers() {
        return classifierRepository.findByStatusOrderByClassifierOrderAsc("ACTIVE").stream()
            .sorted(Comparator
                .comparingInt((ClassifierEntity item) -> classifierCostRank(item.getType()))
                .thenComparing(item -> item.getClassifierOrder() != null ? item.getClassifierOrder() : 100))
            .toList();
    }

    private int classifierCostRank(String type) {
        if (type == null) {
            return 99;
        }
        return switch (type.toUpperCase()) {
            case "KEYWORD" -> 0;
            case "REGEX" -> 1;
            case "LINEAR_MODEL" -> 2;
            case "LLM" -> 3;
            default -> 10;
        };
    }

    private AiProviderEntity findActiveProvider() {
        return aiProviderRepository.findAll().stream()
            .filter(provider -> {
                String status = provider.getStatus();
                return "ACTIVE".equalsIgnoreCase(status)
                    || "HEALTHY".equalsIgnoreCase(status)
                    || "WARNING".equalsIgnoreCase(status);
            })
            .sorted(Comparator
                .comparing((AiProviderEntity provider) -> "MOCK".equalsIgnoreCase(provider.getProtocol()))
                .thenComparing(AiProviderEntity::getId))
            .findFirst()
            .orElse(null);
    }

    private PromptEntity findGuidePrompt() {
        List<PromptEntity> activeGeneration = promptRepository.findByStatus("ACTIVE").stream()
            .filter(prompt -> "GENERATION".equalsIgnoreCase(prompt.getType()))
            .toList();
        if (!activeGeneration.isEmpty()) {
            return activeGeneration.get(0);
        }

        List<PromptEntity> generation = promptRepository.findByType("GENERATION");
        if (!generation.isEmpty()) {
            return generation.get(0);
        }

        List<PromptEntity> active = promptRepository.findByStatus("ACTIVE");
        return active.isEmpty() ? null : active.get(0);
    }

    private SettingsEntity getSettings() {
        return settingsRepository.findFirstByOrderByIdAsc()
            .orElseGet(() -> {
                SettingsEntity defaults = new SettingsEntity();
                defaults.setPublicationMode("WITH_MODERATION");
                return defaults;
            });
    }

    private int estimateTokens(List<MessageEntity> chain, ClassifierEntity classifier, PromptEntity prompt) {
        int chars = chain.stream()
            .mapToInt(item -> item.getText() != null ? item.getText().length() : 0)
            .sum();
        if (prompt != null && prompt.getContent() != null) {
            chars += prompt.getContent().length();
        }
        return chars / 3;
    }

    private int estimateOutputTokens(GuideContent guideContent) {
        int chars = 0;
        if (guideContent.content() != null) {
            chars += guideContent.content().length();
        }
        if (guideContent.title() != null) {
            chars += guideContent.title().length();
        }
        return Math.max(chars / 3, 200);
    }

    private String safeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
