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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private static final double CLASSIFIER_THRESHOLD = 0.75;
    private static final double AUTO_PUBLISH_CONFIDENCE = 0.85;
    private static final double COST_PER_TOKEN_USD = 0.00002;
    private static final int TRACE_DATA_MAX_LEN = 2000;
    private static final int MAX_REASON_LEN = 4000;
    private static final int PREVIEW_LEN = 160;
    private static final Set<String> MEANINGFUL_SIGNAL_LABELS = Set.of(
        "AI_ACCESS_DEMAND",
        "PAYMENT_WORKAROUND",
        "PAIN_LIMITS",
        "OFFER_OR_SPAM"
    );
    private static final Set<String> MEANINGFUL_CLASSIFIER_LABELS = Set.of(
        ClassificationLabels.DEMAND_SIGNAL,
        ClassificationLabels.SOLUTION_MENTION,
        ClassificationLabels.VENDOR_OR_SOURCE,
        ClassificationLabels.BUG_OR_LIMITATION,
        ClassificationLabels.PAYMENT_WORKAROUND,
        ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE,
        ClassificationLabels.OPPORTUNITY,
        ClassificationLabels.SPAM_OR_AD
    );

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
    private final MessageContextBuilder messageContextBuilder;
    private final ClassifierRunner classifierRunner;
    private final GuideGenerator guideGenerator;
    private final PipelineTraceService pipelineTraceService;
    private final ObjectMapper objectMapper;

    private final List<PipelineProgress> progressLog = new ArrayList<>();

    @Value("${neuroinfogrinder.pipeline.signal-threshold:0.30}")
    private double signalThreshold;

    @Value("${neuroinfogrinder.pipeline.local-guide-fallback:false}")
    private boolean localGuideFallback;

    @Value("${neuroinfogrinder.pipeline.classification-context-dedup-ttl-hours:24}")
    private long classificationContextDedupTtlHours;

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
            saveMessage(message, "group-disabled");
            recordProgress("PROCESSING", "SKIPPED", "Group " + group.getId() + " is disabled");
            return null;
        }

        message.setProcessingStatus("PROCESSING");
        saveMessage(message, "processing-start");
        recordProgress("PROCESSING", "STARTED", "Message " + messageId + " entered pipeline");

        SettingsEntity settings = getSettings();
        String text = message.getText() != null ? message.getText() : "";

        if (Boolean.TRUE.equals(settings.getFilterSkipBots()) && Boolean.TRUE.equals(message.getIsBot())) {
            message.setProcessingStatus("SKIPPED");
            saveMessage(message, "pre-filter-bot");
            recordProgress("PRE_FILTER", "SKIPPED", "Bot message filtered");
            return null;
        }

        if (settings.getFilterMinMessageLength() != null && text.length() < settings.getFilterMinMessageLength()) {
            message.setProcessingStatus("SKIPPED");
            saveMessage(message, "pre-filter-short");
            recordProgress("PRE_FILTER", "SKIPPED", "Message too short");
            return null;
        }

        if (settings.getFilterBlacklistWords() != null && !settings.getFilterBlacklistWords().isBlank()) {
            String lowerText = text.toLowerCase();
            for (String word : settings.getFilterBlacklistWords().split(",")) {
                String trimmed = word.trim().toLowerCase();
                if (!trimmed.isEmpty() && lowerText.contains(trimmed)) {
                    message.setProcessingStatus("SKIPPED");
                    saveMessage(message, "pre-filter-blacklist");
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
            saveMessage(message, "rules-rejected");
            return null;
        }

        Instant scoreStart = Instant.now();
        SignalScore signalScore = signalScorer.score(message);
        Instant scoreEnd = Instant.now();
        message.setSignalScore(signalScore.score());
        recordProgress("SIGNAL_SCORING", "COMPLETED",
            "Score: " + signalScore.score() + " | Breakdown: " + signalScore.breakdown());

        String breakdownJson = safeToJson(signalScore);
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
            saveMessage(message, "signal-below-threshold");
            return null;
        }
        recordProgress("SIGNAL_SCORING", "PASSED",
            "Score " + signalScore.score() + " above threshold " + signalThreshold);

        MessageContextBundle contextBundle = messageContextBuilder.buildContext(message);
        if (!shouldSelectAnchor(signalScore, contextBundle)) {
            log.info("Classification anchor rejected: messageId={} score={} signals={}",
                messageId, contextBundle.anchorScore(), contextBundle.anchorSignals());
            message.setProcessingStatus("SKIPPED");
            message.setClassifierReason("Anchor score below threshold; anchorScore=" + contextBundle.anchorScore());
            saveMessage(message, "anchor-rejected");
            return null;
        }

        log.info("Classification anchor selected: messageId={} groupId={} topicId={} score={} matchedSignals={}",
            messageId, message.getGroupId(), message.getTopicId(), contextBundle.anchorScore(), contextBundle.anchorSignals());

        List<MessageEntity> chain = contextBundle.messages();
        message.setClassificationContextHash(contextBundle.contextHash());
        recordProgress("CHAIN_BUILDING", "COMPLETED",
            "Context size: " + chain.size() + " messages; anchorScore=" + contextBundle.anchorScore());

        MessageEntity dedupMessage = findRecentClassificationByContextHash(contextBundle.contextHash(), messageId);
        if (dedupMessage != null) {
            log.info("Classification context dedup skipped: messageId={} existingMessageId={} contextHash={}",
                messageId, dedupMessage.getId(), contextBundle.contextHash());
            copyClassificationResult(message, dedupMessage, "Context dedup reused previous classification");
            saveMessage(message, "context-dedup");
            return null;
        }

        List<ClassifierEntity> classifiers = findActiveClassifiers();
        if (classifiers.isEmpty()) {
            if (shouldPreserveAsLead(signalScore)) {
                message.setProcessingStatus("CLASSIFIED");
                message.setClassifierScore(Math.max(signalScore.score(), CLASSIFIER_THRESHOLD));
                message.setClassifierReason(buildLeadReason(signalScore, "No active classifiers found"));
                message.setClassifierResultJson(buildClassifierFallbackJson(signalScore, chain));
                saveMessage(message, "no-active-classifiers-preserve");
                recordProgress("CLASSIFICATION", "COMPLETED", "Lead preserved by signal classifier");
                return null;
            }

            message.setProcessingStatus("SKIPPED");
            message.setClassifierReason("No active classifiers found");
            saveMessage(message, "no-active-classifiers-skip");
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

            boolean classifierPassed = classifierResult.matched();
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

            if (classifierPassed && classifierResult.guideCandidate() && "LLM".equalsIgnoreCase(classifier.getType())) {
                recordProgress("CLASSIFICATION", "EARLY_STOP",
                    "Stopped after passing LLM classifier " + classifier.getName());
                break;
            }

            if (classifierPassed
                && classifierResult.guideCandidate()
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
                + (signalScore.labels().isEmpty() ? "" : " | Signal labels=" + String.join(", ", signalScore.labels()))
            : null);
        message.setClassifierResultJson(classifierResult != null ? safeToJson(classifierResult) : null);

        if (selectedClassifierResult == null || selectedClassifier == null) {
            if (shouldPreserveAsLead(signalScore)) {
                message.setProcessingStatus("CLASSIFIED");
                message.setClassifierScore(Math.max(signalScore.score(), CLASSIFIER_THRESHOLD));
                message.setClassifierReason(buildLeadReason(signalScore,
                    "No classifier passed threshold; best score="
                        + (bestObservedResult != null ? bestObservedResult.score() : 0.0)));
                message.setClassifierResultJson(buildClassifierFallbackJson(signalScore, chain));
                saveMessage(message, "no-classifier-passed-preserve");
                recordProgress("CLASSIFICATION", "COMPLETED",
                    "Lead preserved by signal classifier; best classifier score="
                        + (bestObservedResult != null ? bestObservedResult.score() : 0.0));
                return null;
            }

            message.setProcessingStatus("SKIPPED");
            message.setClassifierReason("No classifier passed threshold; " + signalScore.classificationReason());
            saveMessage(message, "no-classifier-passed-skip");
            recordProgress("CLASSIFICATION", "SKIPPED",
                "No classifier passed threshold; best score="
                    + (bestObservedResult != null ? bestObservedResult.score() : 0.0));
            return null;
        }

        if (!hasMeaningfulClassifierResult(classifierResult, signalScore)) {
            message.setProcessingStatus("SKIPPED");
            message.setClassifierReason(buildSkippedReason(classifierResult, signalScore));
            saveMessage(message, "classifier-not-meaningful");
            recordProgress("CLASSIFICATION", "SKIPPED",
                "Matched classifier output lacked meaningful AI/access/payment signal");
            return null;
        }

        if (!classifierResult.guideCandidate()) {
            message.setProcessingStatus("CLASSIFIED");
            saveMessage(message, "classified-non-guide");
            recordProgress("CLASSIFICATION", "COMPLETED",
                "Matched useful context without guide candidate; labels=" + classifierResult.labels());
            return null;
        }

        recordProgress("CLASSIFICATION", "COMPLETED",
            "Classifier=" + classifier.getName()
                + " | Type=" + classifier.getType()
                + " | Score=" + classifierResult.score()
                + " | Matched=" + classifierResult.matched()
                + " | GuideCandidate=" + classifierResult.guideCandidate());

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
                "No active AI provider found",
                null
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
            saveMessage(message, "guide-provider-missing");
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
            saveMessage(message, "guide-generation-failed");
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
        saveMessage(message, "guide-found-root");

        for (MessageEntity chainMsg : chain) {
            if ("UNPROCESSED".equals(chainMsg.getProcessingStatus())) {
                chainMsg.setProcessingStatus("GUIDE_FOUND");
                chainMsg.setGuideId(guide.getId());
                saveMessage(chainMsg, "guide-found-chain");
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
        guide.setRawResponse(guideContent.rawResponse());
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
            null,
            content
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

    private boolean shouldPreserveAsLead(SignalScore signalScore) {
        if (signalScore == null) {
            return false;
        }
        return signalScore.score() >= signalThreshold && hasMeaningfulSignal(signalScore);
    }

    private String buildLeadReason(SignalScore signalScore, String prefix) {
        String labels = signalScore.labels().isEmpty()
            ? ""
            : " | labels=" + String.join(", ", signalScore.labels());
        String matchedSignals = signalScore.matchedSignals().isEmpty()
            ? ""
            : " | matchedSignals=" + String.join(", ", signalScore.matchedSignals());
        return truncate(prefix + " | " + signalScore.classificationReason() + labels + matchedSignals, MAX_REASON_LEN);
    }

    private boolean shouldSelectAnchor(SignalScore signalScore, MessageContextBundle contextBundle) {
        return signalScore.score() >= signalThreshold || messageContextBuilder.isAnchorCandidate(contextBundle.anchorMessage());
    }

    private MessageEntity findRecentClassificationByContextHash(String contextHash, Long currentMessageId) {
        if (contextHash == null || contextHash.isBlank()) {
            return null;
        }
        Instant updatedAfter = Instant.now().minus(Duration.ofHours(classificationContextDedupTtlHours));
        return messageRepository
            .findFirstByClassificationContextHashAndUpdatedAtAfterOrderByUpdatedAtDesc(contextHash, updatedAfter)
            .filter(existing -> !existing.getId().equals(currentMessageId))
            .orElse(null);
    }

    private void copyClassificationResult(MessageEntity target, MessageEntity source, String reasonPrefix) {
        target.setClassifierScore(source.getClassifierScore());
        target.setClassifierReason(truncate(reasonPrefix + "; " + source.getClassifierReason(), MAX_REASON_LEN));
        target.setClassifierResultJson(source.getClassifierResultJson());
        target.setClassificationContextHash(source.getClassificationContextHash());
        target.setGuideId(source.getGuideId());
        if (source.getGuideId() != null && "GUIDE_FOUND".equals(source.getProcessingStatus())) {
            target.setProcessingStatus("GUIDE_FOUND");
        } else {
            target.setProcessingStatus("CLASSIFIED");
        }
    }

    private String buildClassifierFallbackJson(SignalScore signalScore, List<MessageEntity> chain) {
        List<String> labels = new ArrayList<>();
        if (signalScore.labels().contains("AI_ACCESS_DEMAND")) {
            labels.add(ClassificationLabels.DEMAND_SIGNAL);
        }
        if (signalScore.labels().contains("PAYMENT_WORKAROUND")) {
            labels.add(ClassificationLabels.PAYMENT_WORKAROUND);
        }
        if (signalScore.labels().contains("PROVIDER_MENTION")) {
            labels.add(ClassificationLabels.AI_TOOL_OR_PROVIDER);
        }
        if (signalScore.labels().contains("PAIN_LIMITS")) {
            labels.add(ClassificationLabels.BUG_OR_LIMITATION);
            labels.add(ClassificationLabels.OPPORTUNITY);
        }
        if (signalScore.labels().contains("OFFER_OR_SPAM")) {
            labels.add(ClassificationLabels.SPAM_OR_AD);
            labels.add(ClassificationLabels.VENDOR_OR_SOURCE);
        }
        ClassifierResult fallback = new ClassifierResult(
            Math.max(signalScore.score(), CLASSIFIER_THRESHOLD),
            true,
            ClassificationLabels.sanitize(labels),
            false,
            chain.stream().map(MessageEntity::getId).limit(3).toList(),
            signalScore.classificationReason()
        );
        return safeToJson(fallback);
    }

    private boolean hasMeaningfulSignal(SignalScore signalScore) {
        if (signalScore == null || signalScore.labels().isEmpty()) {
            return false;
        }
        Set<String> labels = new LinkedHashSet<>(signalScore.labels());
        if (labels.stream().anyMatch(MEANINGFUL_SIGNAL_LABELS::contains)) {
            return true;
        }
        return labels.contains("PROVIDER_MENTION") && labels.size() > 1;
    }

    private boolean hasMeaningfulClassifierResult(ClassifierResult classifierResult, SignalScore signalScore) {
        if (classifierResult == null || !classifierResult.matched()) {
            return false;
        }
        if (classifierResult.labels().stream().anyMatch(MEANINGFUL_CLASSIFIER_LABELS::contains)) {
            return true;
        }
        if (classifierResult.labels().contains(ClassificationLabels.AI_TOOL_OR_PROVIDER)) {
            return hasMeaningfulSignal(signalScore);
        }
        return false;
    }

    private String buildSkippedReason(ClassifierResult classifierResult, SignalScore signalScore) {
        if (classifierResult != null
            && classifierResult.labels().equals(List.of(ClassificationLabels.AI_TOOL_OR_PROVIDER))
            && !hasMeaningfulSignal(signalScore)) {
            return "Bare provider mention without demand, payment, pain, offer, link, or practical context";
        }
        return "Matched output had no meaningful AI/access/payment signal for CLASSIFIED";
    }

    private MessageEntity saveMessage(MessageEntity message, String stage) {
        compactMessageForPersistence(message);
        try {
            return messageRepository.save(message);
        } catch (DataIntegrityViolationException exception) {
            logMessagePersistenceFailure(message, stage, exception);
            compactMessageForFallback(message, stage);
            return messageRepository.save(message);
        }
    }

    private void compactMessageForPersistence(MessageEntity message) {
        message.setClassifierReason(truncate(message.getClassifierReason(), MAX_REASON_LEN));
        message.setSenderName(truncate(message.getSenderName(), 128));
        message.setTopicName(truncate(message.getTopicName(), 256));
    }

    private void compactMessageForFallback(MessageEntity message, String stage) {
        compactMessageForPersistence(message);
        message.setClassifierReason(truncate("Persistence fallback after " + stage + " | status="
            + message.getProcessingStatus(), 512));
        message.setClassifierResultJson(truncate(message.getClassifierResultJson(), 2000));
        message.setRuleResultJson(truncate(message.getRuleResultJson(), 2000));
        message.setSignalBreakdown(truncate(message.getSignalBreakdown(), 2000));
    }

    private void logMessagePersistenceFailure(MessageEntity message, String stage, DataIntegrityViolationException exception) {
        log.warn(
            "Message persistence failed at stage={} messageId={} status={} lengths={{reason={}, resultJson={}, ruleJson={}, breakdown={}, text={}, topicName={}, senderName={}}} previews={{reason='{}', topic='{}', text='{}'}} cause={}",
            stage,
            message.getId(),
            message.getProcessingStatus(),
            lengthOf(message.getClassifierReason()),
            lengthOf(message.getClassifierResultJson()),
            lengthOf(message.getRuleResultJson()),
            lengthOf(message.getSignalBreakdown()),
            lengthOf(message.getText()),
            lengthOf(message.getTopicName()),
            lengthOf(message.getSenderName()),
            preview(message.getClassifierReason()),
            preview(message.getTopicName()),
            preview(message.getText()),
            exception.getMostSpecificCause() != null ? exception.getMostSpecificCause().getMessage() : exception.getMessage()
        );
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private String preview(String value) {
        return truncate(value, PREVIEW_LEN);
    }
}
