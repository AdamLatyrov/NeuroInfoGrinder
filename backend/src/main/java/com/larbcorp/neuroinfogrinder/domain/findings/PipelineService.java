package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiUsageLogEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideSourceMessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiUsageLogRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ClassifierRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideSourceMessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TopicClusterGuideCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
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
    private static final int TRACE_CONFIG_MAX_LEN = 12000;
    private static final int MAX_REASON_LEN = 4000;
    private static final int PREVIEW_LEN = 160;
    private static final int CROSS_CLUSTER_SOURCE_OVERLAP_MIN_INTERSECTION = 2;
    private static final double CROSS_CLUSTER_SOURCE_OVERLAP_MIN_JACCARD = 0.30;
    private static final Set<String> NON_REUSABLE_GUIDE_STATUSES = Set.of(
        "FAILED", "ERROR", "DELETED", "MERGED"
    );
    private static final Set<String> MEANINGFUL_SIGNAL_LABELS = Set.of(
        "AI_ACCESS_DEMAND",
        "PAYMENT_WORKAROUND",
        "PAIN_LIMITS",
        ClassificationLabels.PRACTICAL_PROBLEM,
        ClassificationLabels.WORKFLOW_LIFEHACK,
        ClassificationLabels.BUSINESS_PROCESS,
        ClassificationLabels.PRODUCT_FEEDBACK,
        ClassificationLabels.DISCUSSION_INSIGHT
    );
    private static final Set<String> MEANINGFUL_CLASSIFIER_LABELS = Set.of(
        ClassificationLabels.DEMAND_SIGNAL,
        ClassificationLabels.SOLUTION_MENTION,
        ClassificationLabels.VENDOR_OR_SOURCE,
        ClassificationLabels.BUG_OR_LIMITATION,
        ClassificationLabels.PAYMENT_WORKAROUND,
        ClassificationLabels.PRACTICAL_PROBLEM,
        ClassificationLabels.WORKFLOW_LIFEHACK,
        ClassificationLabels.BUSINESS_PROCESS,
        ClassificationLabels.PRODUCT_FEEDBACK,
        ClassificationLabels.DISCUSSION_INSIGHT,
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
    private final TopicClusterGuideCandidateRepository topicClusterGuideCandidateRepository;

    private final SignalScorer signalScorer;
    private final RuleRunner ruleRunner;
    private final MessageContextBuilder messageContextBuilder;
    private final ClassifierRunner classifierRunner;
    private final GuideGenerator guideGenerator;
    private final ContentRoutingService contentRoutingService;
    private final ContentQualityGate contentQualityGate;
    private final MaterialGenerator materialGenerator;
    private final GuideUsefulnessScorer guideUsefulnessScorer;
    private final GuidePublicationService guidePublicationService;
    private final TopicClusterService topicClusterService;
    private final ActiveProviderResolver activeProviderResolver;
    private final PipelineTraceService pipelineTraceService;
    private final ObjectMapper objectMapper;

    private final List<PipelineProgress> progressLog = new ArrayList<>();

    @Value("${neuroinfogrinder.pipeline.signal-threshold:0.30}")
    private double signalThreshold;

    @Value("${neuroinfogrinder.pipeline.local-guide-fallback:false}")
    private boolean localGuideFallback;

    @Value("${neuroinfogrinder.pipeline.classification-context-dedup-ttl-hours:24}")
    private long classificationContextDedupTtlHours;

    @Value("${neuroinfogrinder.pipeline.guide-potential-threshold:60}")
    private int guidePotentialThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.problem-signal-threshold:50}")
    private int clusterProblemSignalThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.pain-threshold:60}")
    private int clusterPainThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.willingness-to-pay-threshold:50}")
    private int clusterWillingnessToPayThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.guide-potential-threshold:60}")
    private int clusterGuidePotentialThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.technical-depth-threshold:70}")
    private int clusterTechnicalDepthThreshold;

    @Value("${neuroinfogrinder.pipeline.cluster.spam-threshold:70}")
    private int spamBlockThreshold;

    @Value("${neuroinfogrinder.pipeline.cross-cluster-dedup-window-minutes:60}")
    private long crossClusterDedupWindowMinutes;

    @Value("${pipeline.processing.enabled:true}")
    private boolean pipelineProcessingEnabled = true;

    @Value("${material.generation.enabled:true}")
    private boolean materialGenerationEnabled = true;

    @Value("${problem.extraction.enabled:true}")
    private boolean problemExtractionEnabled = true;

    public GuideEntity processMessage(Long messageId) {
        if (!pipelineProcessingEnabled) {
            log.debug("Pipeline processing disabled by config; skipping message {}", messageId);
            return null;
        }
        progressLog.clear();
        String traceId = UUID.randomUUID().toString();
        log.info("Pipeline started for message {} (traceId={})", messageId, traceId);

        MessageEntity message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        GroupEntity group = groupRepository.findById(message.getGroupId())
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + message.getGroupId()));
        if (message.getOwnerUserId() == null && group.getOwnerUserId() != null) {
            message.setOwnerUserId(group.getOwnerUserId());
            saveMessage(message, "owner-repaired-from-group");
        }

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
            ruleResult.passes() ? 1.0 : 0.0, null, ruleResult.reason(),
            ruleTraceMetadata(ruleResult, ruleResultJson)
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
                message.setClassifierScore(Math.max(signalScore.score(), CLASSIFIER_THRESHOLD));
                message.setClassifierReason(buildLeadReason(signalScore, "No active classifiers found"));
                message.setClassifierResultJson(buildClassifierFallbackJson(signalScore, chain));
                ClassifierResult fallbackResult = parseClassifierResult(message);
                applyMessageIntelligence(message, fallbackResult, signalScore);
                saveMessage(message, "no-active-classifiers-internal-signal");
                return classifyAndPersistCluster(
                    message,
                    chain,
                    List.of(),
                    fallbackResult,
                    signalScore,
                    traceId,
                    "Lead preserved by signal classifier"
                );
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
                null, null, "No active classifiers found",
                classifierSetTraceMetadata("No active classifiers found")
            );
            return null;
        }

        message.setClassifierScore(Math.max(signalScore.score(), CLASSIFIER_THRESHOLD));
        message.setClassifierReason(buildLeadReason(signalScore, "Anchor selected for cluster classification"));
        message.setClassifierResultJson(buildClassifierFallbackJson(signalScore, chain));
        ClassifierResult fallbackResult = parseClassifierResult(message);
        applyMessageIntelligence(message, fallbackResult, signalScore);
        saveMessage(message, "anchor-ready-for-cluster");

        return classifyAndPersistCluster(
            message,
            chain,
            classifiers,
            fallbackResult,
            signalScore,
            traceId,
            "Matched useful context; final classification moves to topic cluster"
        );
    }

    public List<PipelineProgress> getProgressLog() {
        return new ArrayList<>(progressLog);
    }

    @Transactional
    public GenerateMissingGuidesResponse generateMissingGuides(int limit) {
        return generateMissingGuides(null, limit);
    }

    @Transactional
    public GenerateMissingGuidesResponse generateMissingGuides(Long ownerUserId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<MessageEntity> candidates = findMessagesForOwnerAndStatuses(
            ownerUserId,
            List.of("CLASSIFIED"),
            boundedLimit
        );

        int generated = 0;
        int skipped = 0;
        int failed = 0;

        for (MessageEntity message : candidates) {
            if (!isMissingGuideCandidate(message)) {
                skipped++;
                continue;
            }

            try {
                GuideEntity guide = generateGuideForClassifiedMessage(message);
                if (guide != null && guide.getGenerationError() == null) {
                    generated++;
                } else {
                    failed++;
                }
            } catch (Exception exception) {
                failed++;
                log.warn("Failed to generate missing guide for message {}: {}", message.getId(), exception.getMessage());
            }
        }

        return new GenerateMissingGuidesResponse(candidates.size(), generated, skipped, failed);
    }

    @Transactional
    public RetryApiErrorsResponse retryApiErrors(int limit) {
        return retryApiErrors(null, limit);
    }

    @Transactional
    public RetryApiErrorsResponse retryApiErrors(Long ownerUserId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<MessageEntity> candidates = findMessagesForOwnerAndStatuses(
            ownerUserId,
            List.of("ERROR"),
            boundedLimit
        );

        int retried = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        for (MessageEntity message : candidates) {
            if (!isRetryableApiError(message)) {
                skipped++;
                continue;
            }

            retried++;
            try {
                GuideEntity guide = generateGuideForClassifiedMessage(message);
                if (guide != null && guide.getGenerationError() == null) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (Exception exception) {
                failed++;
                log.warn("Failed to retry API error for message {}: {}", message.getId(), exception.getMessage());
            }
        }

        return new RetryApiErrorsResponse(candidates.size(), retried, succeeded, failed, skipped);
    }

    public record GenerateMissingGuidesResponse(int scanned, int generated, int skipped, int failed) {}

    public record RetryApiErrorsResponse(int scanned, int retried, int succeeded, int failed, int skipped) {}

    private List<MessageEntity> findMessagesForOwnerAndStatuses(Long ownerUserId, List<String> statuses, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        if (ownerUserId == null) {
            return messageRepository.findByProcessingStatusIn(statuses, page).getContent();
        }

        List<Long> ownerGroupIds = groupRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId).stream()
            .map(GroupEntity::getId)
            .toList();
        if (ownerGroupIds.isEmpty()) {
            return List.of();
        }

        return messageRepository.findByOwnerUserIdAndGroupIdInAndProcessingStatusIn(
            ownerUserId,
            ownerGroupIds,
            statuses,
            page
        ).getContent();
    }

    private GuideEntity classifyAndPersistCluster(
        MessageEntity triggerMessage,
        List<MessageEntity> sourceMessages,
        List<ClassifierEntity> classifiers,
        ClassifierResult messageClassifierResult,
        SignalScore signalScore,
        String traceId,
        String reason
    ) {
        TopicClusterService.ClusterUpdateResult clusterUpdate = topicClusterService.upsertFromMessage(
            triggerMessage,
            sourceMessages,
            messageClassifierResult,
            signalScore
        );
        recordProgress(
            "CLUSTER_UPDATE",
            clusterUpdate.created() ? "CREATED" : "UPDATED",
            "Cluster " + clusterUpdate.cluster().getId()
                + " linkedMessages=" + clusterUpdate.linkedMessages()
                + " | " + clusterUpdate.groupingReason()
        );

        List<MessageEntity> clusterMessages = clusterUpdate.sourceMessages();
        ClusterClassifierSelection clusterSelection = classifyCluster(
            clusterUpdate.cluster(),
            clusterMessages,
            classifiers,
            messageClassifierResult,
            traceId
        );
        ClassifierEntity classifier = clusterSelection.classifier();
        ClassifierResult clusterClassifierResult = clusterSelection.result();
        if (classifier != null) {
            applyClusterIntelligence(clusterMessages, clusterClassifierResult, signalScore);
        }
        TopicClusterService.ClusterClassificationResult clusterClassification =
            topicClusterService.applyClusterClassification(
                clusterUpdate.cluster(),
                clusterClassifierResult,
                classifier != null ? classifier.getId() : null,
                clusterMessages
            );
        TopicDiscussionClusterEntity cluster = clusterClassification.cluster();
        List<TopicClusterGuideCandidateEntity> guideCandidates = clusterClassification.guideCandidates();

        if (classifier != null && !hasMeaningfulClassifierResult(clusterClassifierResult, signalScore)) {
            topicClusterService.markGuideSkipped(cluster, "NONE");
            markSourceMessagesSkipped(
                clusterMessages,
                buildSkippedReason(clusterClassifierResult, signalScore),
                "cluster-classification-not-meaningful"
            );
            recordProgress(
                "CLUSTER_CLASSIFICATION",
                "SKIPPED",
                "Cluster classifier output lacked meaningful useful-content signal"
            );
            return null;
        }

        markSourceMessagesClustered(clusterMessages, null, "cluster-classified");
        recordProgress(
            "CLUSTER_CLASSIFICATION",
            cluster.getClassificationStatus(),
            reason + "; clusterId=" + cluster.getId()
                + " score=" + clusterClassifierResult.score()
                + " guideCandidate=" + clusterClassifierResult.guideCandidate()
        );

        RoutedContentSelection contentSelection = selectRoutedContent(
            triggerMessage,
            cluster,
            guideCandidates,
            clusterMessages,
            clusterClassifierResult,
            traceId
        );
        TopicClusterGuideCandidateEntity guideCandidate = contentSelection != null ? contentSelection.candidate() : null;
        ContentRoutingDecision routingDecision = contentSelection != null ? contentSelection.decision() : null;
        ContentQualityGate.GateResult gateResult = contentSelection != null
            ? contentSelection.gateResult()
            : ContentQualityGate.GateResult.blocked("content routing did not return a decision");
        if (!gateResult.allowed()) {
            ContentRoutingDecision blockedDecision = routingDecision != null
                ? routingDecision.withGateBlock(gateResult.reason())
                : null;
            applyContentRoutingToCandidate(guideCandidate, blockedDecision);
            topicClusterService.markGuideSkipped(
                cluster,
                routingDecision != null ? routingDecision.contentType().name() : "QUALITY_BLOCKED"
            );
            markSourceMessagesClustered(clusterMessages, null, "content-quality-gate-blocked");
            recordProgress(
                "CONTENT_QUALITY_GATE",
                "BLOCKED",
                gateResult.reason()
            );
            return null;
        }

        return generateMaterialForCluster(
            triggerMessage,
            cluster,
            guideCandidate,
            clusterMessages,
            classifier,
            clusterClassifierResult,
            routingDecision,
            traceId
        );
    }

    private ClusterClassifierSelection classifyCluster(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> clusterMessages,
        List<ClassifierEntity> classifiers,
        ClassifierResult fallbackResult,
        String traceId
    ) {
        if (classifiers == null || classifiers.isEmpty()) {
            return new ClusterClassifierSelection(null, fallbackResult);
        }

        ClassifierEntity selectedClassifier = null;
        ClassifierResult selectedResult = null;
        ClassifierEntity bestObservedClassifier = null;
        ClassifierResult bestObservedResult = null;

        for (ClassifierEntity classifier : classifiers) {
            Instant clsStart = Instant.now();
            ClassifierResult clusterResult = classifierRunner.classify(
                clusterMessages,
                classifier,
                ModelCallPurpose.CLUSTER_CLASSIFICATION
            );
            Instant clsEnd = Instant.now();

            if (bestObservedResult == null || clusterResult.score() > bestObservedResult.score()) {
                bestObservedClassifier = classifier;
                bestObservedResult = clusterResult;
            }

            boolean classifierPassed = clusterResult.matched();
            if (classifierPassed && (selectedResult == null || clusterResult.score() > selectedResult.score())) {
                selectedClassifier = classifier;
                selectedResult = clusterResult;
            }

            pipelineTraceService.createTrace(
                traceId,
                clusterMessages.isEmpty() ? null : clusterMessages.get(0).getId(),
                cluster.getGroupId(),
                "CLUSTER_CLASSIFICATION",
                classifierPassed ? "PASSED" : "SKIPPED",
                clsStart,
                clsEnd,
                Duration.between(clsStart, clsEnd).toMillis(),
                truncate(
                    "clusterId=" + cluster.getId()
                        + " | classifierId=" + classifier.getId()
                        + " | classifierType=" + classifier.getType()
                        + " | sourceMessageCount=" + clusterMessages.size()
                        + " | configuredProviderId=" + classifier.getProviderId()
                        + " | routedProviderId=" + clusterResult.providerId()
                        + " | routedModel=" + clusterResult.model(),
                    TRACE_DATA_MAX_LEN
                ),
                truncate(safeToJson(clusterResult), TRACE_DATA_MAX_LEN),
                null,
                null,
                classifier.getId(),
                classifier.getPromptId(),
                clusterResult.providerId(),
                clusterResult.model(),
                null,
                null,
                null,
                clusterResult.score(),
                null,
                "Cluster-level " + classifier.getName() + ": " + clusterResult.reasoning(),
                classifierTraceMetadata(classifier, clusterResult)
            );

            if (classifierPassed && clusterResult.guideCandidate() && "LLM".equalsIgnoreCase(classifier.getType())) {
                recordProgress("CLUSTER_CLASSIFICATION", "EARLY_STOP",
                    "Stopped after passing cluster-level LLM classifier " + classifier.getName());
                break;
            }

            if (classifierPassed
                && clusterResult.guideCandidate()
                && !"LLM".equalsIgnoreCase(classifier.getType())
                && clusterResult.score() >= 0.90) {
                recordProgress("CLUSTER_CLASSIFICATION", "EARLY_STOP",
                    "Stopped after strong cluster-level deterministic classifier " + classifier.getName());
                break;
            }
        }

        ClassifierEntity classifier = selectedClassifier != null ? selectedClassifier : bestObservedClassifier;
        ClassifierResult result = selectedResult != null
            ? selectedResult
            : bestObservedResult != null ? bestObservedResult : fallbackResult;
        return new ClusterClassifierSelection(classifier, result);
    }

    private record ClusterClassifierSelection(ClassifierEntity classifier, ClassifierResult result) {}

    private record RoutedContentSelection(
        TopicClusterGuideCandidateEntity candidate,
        ContentRoutingDecision decision,
        ContentQualityGate.GateResult gateResult
    ) {}

    private RoutedContentSelection selectRoutedContent(
        MessageEntity triggerMessage,
        TopicDiscussionClusterEntity cluster,
        List<TopicClusterGuideCandidateEntity> guideCandidates,
        List<MessageEntity> clusterMessages,
        ClassifierResult classifierResult,
        String traceId
    ) {
        List<TopicClusterGuideCandidateEntity> candidates = guideCandidates != null && !guideCandidates.isEmpty()
            ? guideCandidates
            : new ArrayList<>();
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>();
            candidates.add(null);
        }

        RoutedContentSelection firstAllowedMaterial = null;
        RoutedContentSelection firstBlocked = null;
        for (TopicClusterGuideCandidateEntity candidate : candidates) {
            ContentRoutingDecision decision = routeClusterContent(
                triggerMessage,
                cluster,
                candidate,
                clusterMessages,
                classifierResult,
                traceId
            );
            ContentQualityGate.GateResult gateResult = contentQualityGate.evaluate(
                decision,
                cluster,
                candidate,
                clusterMessages
            );
            RoutedContentSelection selection = new RoutedContentSelection(candidate, decision, gateResult);
            if (!gateResult.allowed()) {
                if (decision != null) {
                    applyContentRoutingToCandidate(candidate, decision.withGateBlock(gateResult.reason()));
                }
                if (firstBlocked == null) {
                    firstBlocked = selection;
                }
                continue;
            }
            if (decision.contentType() == ContentType.GUIDE) {
                return selection;
            }
            if (firstAllowedMaterial == null) {
                firstAllowedMaterial = selection;
            }
        }
        return firstAllowedMaterial != null ? firstAllowedMaterial : firstBlocked;
    }

    private ContentRoutingDecision routeClusterContent(
        MessageEntity triggerMessage,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity guideCandidate,
        List<MessageEntity> clusterMessages,
        ClassifierResult classifierResult,
        String traceId
    ) {
        Instant start = Instant.now();
        ContentRoutingDecision decision = contentRoutingService.route(
            cluster,
            guideCandidate,
            clusterMessages,
            classifierResult
        );
        Instant end = Instant.now();
        applyContentRoutingToCandidate(guideCandidate, decision);
        recordProgress(
            "CLUSTER_CONTENT_ROUTING",
            decision.shouldCreateMaterial() ? decision.contentType().name() : "BLOCKED",
            "contentType=" + decision.contentType()
                + " | label=" + decision.topicLabel()
                + " | confidence=" + decision.confidence()
                + " | reason=" + decision.reason()
        );
        pipelineTraceService.createTrace(
            traceId,
            triggerMessage.getId(),
            triggerMessage.getGroupId(),
            "CLUSTER_CONTENT_ROUTING",
            decision.shouldCreateMaterial() ? "ROUTED" : "SKIPPED",
            start,
            end,
            Duration.between(start, end).toMillis(),
            truncate(
                "clusterId=" + (cluster != null ? cluster.getId() : "null")
                    + " | candidateId=" + (guideCandidate != null ? guideCandidate.getId() : "null")
                    + " | sourceMessageCount=" + (clusterMessages != null ? clusterMessages.size() : 0),
                TRACE_DATA_MAX_LEN
            ),
            truncate(safeToJson(decision), TRACE_DATA_MAX_LEN),
            null,
            null,
            null,
            null,
            decision.providerId(),
            decision.model(),
            null,
            null,
            0.0,
            classifierResult != null ? classifierResult.score() : null,
            decision.confidence(),
            decision.reason()
        );
        return decision;
    }

    private void applyContentRoutingToCandidate(
        TopicClusterGuideCandidateEntity candidate,
        ContentRoutingDecision decision
    ) {
        if (candidate == null || decision == null) {
            return;
        }
        candidate.setContentType(decision.contentType().name());
        candidate.setContentSubtype(decision.contentSubtype());
        candidate.setTopicLabel(decision.topicLabel());
        candidate.setTopicSummary(decision.topicSummary());
        candidate.setContentTitle(decision.contentTitle());
        candidate.setContentSummary(decision.contentSummary());
        candidate.setNormalizedTopicKey(decision.normalizedTopicKey());
        candidate.setContentQualityScore(decision.contentQualityScore());
        candidate.setImportanceScore(decision.importanceScore());
        candidate.setActionabilityScore(decision.actionabilityScore());
        candidate.setNoveltyScore(decision.noveltyScore());
        candidate.setEvidenceScore(decision.evidenceScore());
        candidate.setRiskScore(decision.riskScore());
        candidate.setConfidenceScore(decision.confidenceScore());
        candidate.setNoiseScore(decision.noiseScore());
        candidate.setRoutingReason(truncate(decision.reason(), MAX_REASON_LEN));
        candidate.setPublicationKind(decision.publicationKind());
        candidate.setShouldCreateMaterial(decision.shouldCreateMaterial());
        candidate.setShouldGenerateFullGuide(decision.shouldGenerateFullGuide());
        candidate.setSafetyCategory(firstNonBlank(decision.safetyCategory(), candidate.getSafetyCategory()));
        if (decision.specificAngle() != null && !decision.specificAngle().isBlank()) {
            candidate.setGuideAngle(truncate(decision.specificAngle(), 512));
        }
        topicClusterGuideCandidateRepository.save(candidate);
    }

    private void applyContentRoutingToGuide(GuideEntity guide, ContentRoutingDecision decision) {
        if (guide == null) {
            return;
        }
        if (decision == null) {
            guide.setContentType("GUIDE");
            guide.setPublicationKind("GUIDE");
            return;
        }
        guide.setContentType(decision.contentType().name());
        guide.setContentSubtype(decision.contentSubtype());
        guide.setTopicLabel(decision.topicLabel());
        guide.setTopicSummary(decision.topicSummary());
        guide.setContentTitle(decision.contentTitle());
        guide.setContentSummary(decision.contentSummary());
        guide.setNormalizedTopicKey(decision.normalizedTopicKey());
        guide.setContentQualityScore(decision.contentQualityScore());
        guide.setImportanceScore(decision.importanceScore());
        guide.setActionabilityScore(decision.actionabilityScore());
        guide.setNoveltyScore(decision.noveltyScore());
        guide.setEvidenceScore(decision.evidenceScore());
        guide.setRiskScore(decision.riskScore());
        guide.setConfidenceScore(decision.confidenceScore());
        guide.setNoiseScore(decision.noiseScore());
        guide.setRoutingReason(truncate(decision.reason(), MAX_REASON_LEN));
        guide.setSafetyCategory(decision.safetyCategory());
        guide.setPublicationKind(decision.publicationKind());
    }

    private GuideEntity generateMaterialForCluster(
        MessageEntity triggerMessage,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity guideCandidate,
        List<MessageEntity> clusterMessages,
        ClassifierEntity classifier,
        ClassifierResult classifierResult,
        ContentRoutingDecision routingDecision,
        String traceId
    ) {
        ContentType contentType = routingDecision != null ? routingDecision.contentType() : ContentType.GUIDE;
        if (!materialGenerationEnabled) {
            topicClusterService.markGuideSkipped(cluster, "GENERATION_DISABLED");
            markSourceMessagesClustered(clusterMessages, null, "material-generation-disabled");
            recordProgress(
                contentType.generatesFullGuide() ? "CLUSTER_GUIDE_GENERATION" : "CLUSTER_MATERIAL_GENERATION",
                "SKIPPED",
                "material.generation.enabled=false"
            );
            return null;
        }
        ReusableGuideMatch existingGuide = findReusableGuideForCluster(cluster, guideCandidate, clusterMessages, contentType);
        if (existingGuide != null) {
            return mergeClusterIntoExistingGuide(
                triggerMessage,
                cluster,
                guideCandidate,
                clusterMessages,
                classifier,
                classifierResult,
                traceId,
                routingDecision,
                existingGuide.guide(),
                existingGuide.reason()
            );
        }

        AiProviderEntity provider = contentType.generatesFullGuide()
            ? activeProviderResolver.resolve(ModelCallPurpose.GUIDE_GENERATION)
                .map(ActiveProviderResolver.ResolvedProvider::provider)
                .orElse(null)
            : null;
        boolean usingLocalFallback = provider == null && localGuideFallback;
        PromptEntity guidePrompt = findGuidePrompt();

        if (contentType.generatesFullGuide() && provider == null && !usingLocalFallback) {
            recordProgress("CLUSTER_GUIDE_GENERATION", "FAILED", "No active AI provider found");
            GuideContent failedContent = new GuideContent(
                "Гайд не создан: недоступен AI-провайдер",
                "No active AI provider found",
                "## Гайд не создан\n\nПричина: недоступен AI-провайдер.",
                0.0,
                List.of("AI", "ошибка", "провайдер"),
                "No active AI provider found",
                null
            );
            GuideEntity failedGuide = saveGuideAndSources(
                triggerMessage,
                clusterMessages,
                classifier,
                guidePrompt,
                null,
                failedContent,
                0,
                0,
                0,
                0.0,
                false,
                cluster.getId(),
                guideCandidate != null ? guideCandidate.getId() : null,
                routingDecision
            );
            markSourceMessagesClustered(clusterMessages, failedGuide.getId(), "cluster-guide-provider-missing");
            topicClusterService.markGuideGenerated(cluster, guideCandidate, failedGuide.getId(), true);
            return failedGuide;
        }

        if (contentType.generatesFullGuide() && usingLocalFallback) {
            recordProgress("CLUSTER_GUIDE_GENERATION", "LOCAL_FALLBACK",
                "No active AI provider found; created local draft guide from cluster");
        }

        Long rootMessageId = triggerMessage.getId();
        Instant guideStart = Instant.now();
        GuideContent guideContent = !contentType.generatesFullGuide()
            ? materialGenerator.generate(routingDecision, clusterMessages)
            : usingLocalFallback
                ? buildLocalFallbackGuide(clusterMessages, classifierResult)
                : guideGenerator.generate(
                    clusterMessages,
                    classifierResult,
                    guidePrompt != null ? guidePrompt.getId() : null,
                    rootMessageId
                );
        Instant guideEnd = Instant.now();
        if (!usingLocalFallback && guideContent.providerId() != null) {
            provider = aiProviderRepository.findById(guideContent.providerId()).orElse(provider);
        }

        int estInputTokens = contentType.generatesFullGuide() ? estimateTokens(clusterMessages, classifier, guidePrompt) : 0;
        int estOutputTokens = contentType.generatesFullGuide() ? estimateOutputTokens(guideContent) : 0;
        int estTotalTokens = estInputTokens + estOutputTokens;
        double estCost = estTotalTokens * COST_PER_TOKEN_USD;
        boolean guideFailed = guideContent.generationError() != null && !guideContent.generationError().isBlank();

        recordProgress(
            contentType.generatesFullGuide() ? "CLUSTER_GUIDE_GENERATION" : "CLUSTER_MATERIAL_GENERATION",
            guideFailed ? "FAILED" : "COMPLETED",
            "clusterId=" + cluster.getId() + " | contentType=" + contentType + " | Title: " + guideContent.title()
        );

        pipelineTraceService.createTrace(
            traceId,
            rootMessageId,
            triggerMessage.getGroupId(),
            contentType.generatesFullGuide() ? "CLUSTER_GUIDE_GENERATION" : "CLUSTER_MATERIAL_GENERATION",
            guideFailed ? "FAILED" : "COMPLETED",
            guideStart,
            guideEnd,
            Duration.between(guideStart, guideEnd).toMillis(),
            truncate(
                "clusterId=" + cluster.getId()
                    + " | contentType=" + contentType
                    + " | providerId=" + (guideContent.providerId() != null ? guideContent.providerId() : provider != null ? provider.getId() : "LOCAL_FALLBACK")
                    + " | promptId=" + (contentType.generatesFullGuide() && guidePrompt != null ? guidePrompt.getId() : "null")
                    + " | sourceMessageCount=" + clusterMessages.size()
                    + " | classifierScore=" + classifierResult.score(),
                TRACE_DATA_MAX_LEN
            ),
            truncate(safeToJson(guideContent), TRACE_DATA_MAX_LEN),
            guideFailed ? truncate(guideContent.generationError(), TRACE_DATA_MAX_LEN) : null,
            null,
            classifier != null ? classifier.getId() : null,
            contentType.generatesFullGuide() && guidePrompt != null ? guidePrompt.getId() : null,
            guideContent.providerId() != null ? guideContent.providerId() : provider != null ? provider.getId() : null,
            guideContent.model() != null ? guideContent.model() : provider != null ? provider.getModel() : "local-fallback",
            estInputTokens,
            estOutputTokens,
            estCost,
            null,
            guideContent.confidence(),
            (usingLocalFallback ? "LOCAL_FALLBACK | " : "") + "Cluster " + contentType + ": " + guideContent.title(),
            guideTraceMetadata(classifier, contentType.generatesFullGuide() ? guidePrompt : null, provider, guideContent, usingLocalFallback)
        );

        GuideEntity guide = saveGuideAndSources(
            triggerMessage,
            clusterMessages,
            classifier,
            contentType.generatesFullGuide() ? guidePrompt : null,
            provider,
            guideContent,
            estInputTokens,
            estOutputTokens,
            estTotalTokens,
            estCost,
            usingLocalFallback,
            cluster.getId(),
            guideCandidate != null ? guideCandidate.getId() : null,
            routingDecision
        );

        if (provider != null) {
            if (guideFailed) {
                markProviderError(provider, guideContent.generationError());
            } else {
                markProviderHealthy(provider);
            }
        }

        AiUsageLogEntity usageLog = new AiUsageLogEntity();
        usageLog.setTaskType(usingLocalFallback
            ? "CLUSTER_LOCAL_FALLBACK_GUIDE"
            : contentType.generatesFullGuide() ? "CLUSTER_CLASSIFY_AND_GENERATE" : "CLUSTER_CLASSIFY_AND_MATERIAL");
        usageLog.setProviderId(guideContent.providerId() != null ? guideContent.providerId() : provider != null ? provider.getId() : null);
        usageLog.setModel(guideContent.model() != null ? guideContent.model() : provider != null ? provider.getModel() : "local-fallback");
        usageLog.setGuideId(guide.getId());
        usageLog.setInputTokens(estInputTokens);
        usageLog.setOutputTokens(estOutputTokens);
        usageLog.setTotalTokens(estTotalTokens);
        usageLog.setEstimatedCostUsd(estCost);
        aiUsageLogRepository.save(usageLog);

        markSourceMessagesClustered(clusterMessages, guide.getId(), guideFailed ? "cluster-guide-failed" : "cluster-guide-created");
        topicClusterService.markGuideGenerated(cluster, guideCandidate, guide.getId(), guideFailed);

        if (!guideFailed) {
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
            if (contentType == ContentType.GUIDE) {
                guidePublicationService.autoSendAfterGuideCreated(guide);
            }
        }

        log.info("Cluster-first pipeline completed for message {} -> cluster {} -> {} {} (traceId={})",
            triggerMessage.getId(), cluster.getId(), contentType, guide.getId(), traceId);
        return guide;
    }

    private GuideEntity mergeClusterIntoExistingGuide(
        MessageEntity triggerMessage,
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity guideCandidate,
        List<MessageEntity> clusterMessages,
        ClassifierEntity classifier,
        ClassifierResult classifierResult,
        String traceId,
        ContentRoutingDecision routingDecision,
        GuideEntity existingGuide,
        String mergeReason
    ) {
        applyContentRoutingToCandidate(guideCandidate, routingDecision);
        linkMissingSourceMessages(existingGuide.getId(), clusterMessages);
        markSourceMessagesClustered(clusterMessages, existingGuide.getId(), "cluster-guide-merged");
        topicClusterService.markGuideMerged(cluster, guideCandidate, existingGuide.getId());

        Instant mergedAt = Instant.now();
        pipelineTraceService.createTrace(
            traceId,
            triggerMessage.getId(),
            triggerMessage.getGroupId(),
            "CLUSTER_GUIDE_GENERATION",
            "MERGED",
            mergedAt,
            mergedAt,
            0L,
            truncate(
                "clusterId=" + cluster.getId()
                    + " | existingGuideId=" + existingGuide.getId()
                    + " | candidateId=" + (guideCandidate != null ? guideCandidate.getId() : "null")
                    + " | sourceMessageCount=" + clusterMessages.size(),
                TRACE_DATA_MAX_LEN
            ),
            null,
            null,
            null,
            classifier != null ? classifier.getId() : null,
            null,
            existingGuide.getProviderId(),
            existingGuide.getModel(),
            0,
            0,
            0.0,
            classifierResult != null ? classifierResult.score() : null,
            existingGuide.getConfidence(),
            mergeReason
        );
        recordProgress(
            "CLUSTER_GUIDE_GENERATION",
            "MERGED",
            "clusterId=" + cluster.getId() + " reused existing guide " + existingGuide.getId()
                + " | " + mergeReason
        );
        return existingGuide;
    }

    private ReusableGuideMatch findReusableGuideForCluster(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity guideCandidate,
        List<MessageEntity> clusterMessages,
        ContentType contentType
    ) {
        if (guideCandidate != null) {
            GuideEntity candidateGuide = findReusableGuideById(guideCandidate.getGuideId(), contentType);
            if (candidateGuide != null) {
                return new ReusableGuideMatch(
                    candidateGuide,
                    "Merged cluster/source window into existing guide before generation"
                );
            }
            if (guideCandidate.getId() != null) {
                GuideEntity byCandidate = firstReusableGuide(
                    guideRepository.findByTopicClusterGuideCandidateId(guideCandidate.getId()),
                    contentType
                );
                if (byCandidate != null) {
                    return new ReusableGuideMatch(
                        byCandidate,
                        "Merged cluster/source window into existing guide before generation"
                    );
                }
            }
        }

        GuideEntity clusterGuide = findReusableGuideById(cluster.getGuideId(), contentType);
        if (clusterGuide != null) {
            return new ReusableGuideMatch(
                clusterGuide,
                "Merged cluster/source window into existing guide before generation"
            );
        }
        if (cluster.getId() != null) {
            GuideEntity byCluster = firstReusableGuide(guideRepository.findByTopicClusterId(cluster.getId()), contentType);
            if (byCluster != null) {
                return new ReusableGuideMatch(
                    byCluster,
                    "Merged cluster/source window into existing guide before generation"
                );
            }
        }
        return findReusableGuideByCrossClusterOverlap(cluster, guideCandidate, clusterMessages, contentType);
    }

    private GuideEntity findReusableGuideById(Long guideId, ContentType contentType) {
        if (guideId == null) {
            return null;
        }
        return guideRepository.findById(guideId)
            .filter(this::isReusableExistingGuide)
            .filter(guide -> contentTypeMatches(guide, contentType))
            .orElse(null);
    }

    private GuideEntity firstReusableGuide(List<GuideEntity> guides, ContentType contentType) {
        if (guides == null) {
            return null;
        }
        return guides.stream()
            .filter(this::isReusableExistingGuide)
            .filter(guide -> contentTypeMatches(guide, contentType))
            .findFirst()
            .orElse(null);
    }

    private boolean isReusableExistingGuide(GuideEntity guide) {
        if (guide == null || guide.getId() == null) {
            return false;
        }
        if (guide.getStatus() != null && NON_REUSABLE_GUIDE_STATUSES.contains(guide.getStatus().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return guide.getGenerationError() == null || guide.getGenerationError().isBlank();
    }

    private boolean contentTypeMatches(GuideEntity guide, ContentType contentType) {
        ContentType expected = contentType != null ? contentType : ContentType.GUIDE;
        return ContentType.from(guide != null ? guide.getContentType() : null) == expected;
    }

    private ReusableGuideMatch findReusableGuideByCrossClusterOverlap(
        TopicDiscussionClusterEntity cluster,
        TopicClusterGuideCandidateEntity guideCandidate,
        List<MessageEntity> clusterMessages,
        ContentType contentType
    ) {
        if (cluster == null || cluster.getGroupId() == null) {
            return null;
        }

        Set<Long> currentSourceIds = sourceIds(clusterMessages);
        currentSourceIds.addAll(parseSourceMessageIds(guideCandidate != null ? guideCandidate.getSourceMessageIdsJson() : null));
        if (currentSourceIds.isEmpty()) {
            return null;
        }

        Instant searchStart = sourceWindowStart(cluster, clusterMessages)
            .minus(Duration.ofMinutes(Math.max(1L, crossClusterDedupWindowMinutes)));
        List<GuideEntity> recentGuides = guideRepository
            .findTop100ByGroupIdAndCreatedAtAfterOrderByCreatedAtDesc(cluster.getGroupId(), searchStart);
        if (recentGuides == null || recentGuides.isEmpty()) {
            return null;
        }

        List<MessageEntity> currentMessages = clusterMessages != null ? clusterMessages : List.of();
        String currentAngle = guideCandidate != null ? guideCandidate.getGuideAngle() : null;
        for (GuideEntity guide : recentGuides) {
            if (!isReusableExistingGuide(guide)
                || guide.getId() == null
            || !sameId(guide.getGroupId(), cluster.getGroupId())
            || !sameId(guide.getOwnerUserId(), cluster.getOwnerUserId())
            || !contentTypeMatches(guide, contentType)
            || sameId(guide.getTopicClusterId(), cluster.getId())
                || guideCandidate != null && sameId(guide.getTopicClusterGuideCandidateId(), guideCandidate.getId())) {
                continue;
            }

            TopicClusterGuideCandidateEntity existingCandidate = findGuideCandidate(guide.getTopicClusterGuideCandidateId());
            if (!guideAnglesCompatible(currentAngle, existingCandidate != null ? existingCandidate.getGuideAngle() : null)) {
                continue;
            }

            Set<Long> existingSourceIds = sourceIdsFromGuide(guide);
            if (existingCandidate != null) {
                existingSourceIds.addAll(parseSourceMessageIds(existingCandidate.getSourceMessageIdsJson()));
            }
            if (!hasReusableSourceOverlap(currentSourceIds, existingSourceIds)) {
                continue;
            }

            List<MessageEntity> existingMessages = loadMessages(existingSourceIds);
            if (!topicsCompatible(cluster, currentMessages, existingMessages)) {
                continue;
            }
            if (!timeWindowsNear(cluster, currentMessages, guide, existingMessages)) {
                continue;
            }

            return new ReusableGuideMatch(
                guide,
                "merged with existing guide by cross-cluster source overlap"
            );
        }
        return null;
    }

    private TopicClusterGuideCandidateEntity findGuideCandidate(Long candidateId) {
        if (candidateId == null) {
            return null;
        }
        return topicClusterGuideCandidateRepository.findById(candidateId).orElse(null);
    }

    private Set<Long> sourceIdsFromGuide(GuideEntity guide) {
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        List<GuideSourceMessageEntity> sourceLinks = guideSourceMessageRepository.findByGuideId(guide.getId());
        if (sourceLinks != null) {
            sourceLinks.stream()
                .map(GuideSourceMessageEntity::getMessageId)
                .filter(id -> id != null)
                .forEach(sourceIds::add);
        }
        if (guide.getRootMessageId() != null) {
            sourceIds.add(guide.getRootMessageId());
        }
        return sourceIds;
    }

    private Set<Long> sourceIds(List<MessageEntity> messages) {
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        if (messages != null) {
            messages.stream()
                .map(MessageEntity::getId)
                .filter(id -> id != null)
                .forEach(sourceIds::add);
        }
        return sourceIds;
    }

    private Set<Long> parseSourceMessageIds(String sourceMessageIdsJson) {
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        if (sourceMessageIdsJson == null || sourceMessageIdsJson.isBlank()) {
            return sourceIds;
        }
        try {
            List<Long> parsed = objectMapper.readValue(sourceMessageIdsJson, new TypeReference<List<Long>>() {});
            parsed.stream()
                .filter(id -> id != null)
                .forEach(sourceIds::add);
        } catch (Exception exception) {
            log.debug("Failed to parse topic candidate source ids json: {}", exception.getMessage());
        }
        return sourceIds;
    }

    private List<MessageEntity> loadMessages(Set<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return messageRepository.findByIdInOrderByMessageDateAsc(List.copyOf(messageIds));
    }

    private boolean hasReusableSourceOverlap(Set<Long> currentSourceIds, Set<Long> existingSourceIds) {
        if (currentSourceIds == null || existingSourceIds == null || currentSourceIds.isEmpty() || existingSourceIds.isEmpty()) {
            return false;
        }
        Set<Long> intersection = new HashSet<>(currentSourceIds);
        intersection.retainAll(existingSourceIds);
        if (intersection.size() >= CROSS_CLUSTER_SOURCE_OVERLAP_MIN_INTERSECTION) {
            return true;
        }
        Set<Long> union = new HashSet<>(currentSourceIds);
        union.addAll(existingSourceIds);
        return !union.isEmpty()
            && (double) intersection.size() / union.size() >= CROSS_CLUSTER_SOURCE_OVERLAP_MIN_JACCARD;
    }

    private boolean topicsCompatible(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> currentMessages,
        List<MessageEntity> existingMessages
    ) {
        Set<Long> currentTopics = topicIds(currentMessages);
        if (cluster.getTelegramTopicId() != null) {
            currentTopics.add(cluster.getTelegramTopicId());
        }
        Set<Long> existingTopics = topicIds(existingMessages);
        if (!currentTopics.isEmpty() && !existingTopics.isEmpty()) {
            Set<Long> intersection = new HashSet<>(currentTopics);
            intersection.retainAll(existingTopics);
            return !intersection.isEmpty();
        }

        String currentTitle = firstNonBlank(
            cluster.getTopicTitle(),
            currentMessages.stream()
                .map(MessageEntity::getTopicName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null)
        );
        String existingTitle = existingMessages.stream()
            .map(MessageEntity::getTopicName)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
        return currentTitle == null
            || existingTitle == null
            || normalizeGuideAngle(currentTitle).equals(normalizeGuideAngle(existingTitle));
    }

    private Set<Long> topicIds(List<MessageEntity> messages) {
        LinkedHashSet<Long> topicIds = new LinkedHashSet<>();
        if (messages != null) {
            messages.stream()
                .map(MessageEntity::getTopicId)
                .filter(id -> id != null)
                .forEach(topicIds::add);
        }
        return topicIds;
    }

    private boolean timeWindowsNear(
        TopicDiscussionClusterEntity cluster,
        List<MessageEntity> currentMessages,
        GuideEntity existingGuide,
        List<MessageEntity> existingMessages
    ) {
        Instant currentStart = sourceWindowStart(cluster, currentMessages);
        Instant currentEnd = sourceWindowEnd(cluster, currentMessages);
        Instant existingStart = sourceWindowStart(existingGuide, existingMessages);
        Instant existingEnd = sourceWindowEnd(existingGuide, existingMessages);
        Duration proximity = Duration.ofMinutes(Math.max(1L, crossClusterDedupWindowMinutes));
        return !currentEnd.plus(proximity).isBefore(existingStart)
            && !existingEnd.plus(proximity).isBefore(currentStart);
    }

    private Instant sourceWindowStart(TopicDiscussionClusterEntity cluster, List<MessageEntity> messages) {
        return messages != null && !messages.isEmpty()
            ? messages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(date -> date != null)
                .min(Instant::compareTo)
                .orElse(firstNonNull(cluster.getStartAt(), Instant.now()))
            : firstNonNull(cluster.getStartAt(), Instant.now());
    }

    private Instant sourceWindowEnd(TopicDiscussionClusterEntity cluster, List<MessageEntity> messages) {
        return messages != null && !messages.isEmpty()
            ? messages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(date -> date != null)
                .max(Instant::compareTo)
                .orElse(firstNonNull(cluster.getEndAt(), Instant.now()))
            : firstNonNull(cluster.getEndAt(), Instant.now());
    }

    private Instant sourceWindowStart(GuideEntity guide, List<MessageEntity> messages) {
        return messages != null && !messages.isEmpty()
            ? messages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(date -> date != null)
                .min(Instant::compareTo)
                .orElse(firstNonNull(guide.getCreatedAt(), Instant.now()))
            : firstNonNull(guide.getCreatedAt(), Instant.now());
    }

    private Instant sourceWindowEnd(GuideEntity guide, List<MessageEntity> messages) {
        return messages != null && !messages.isEmpty()
            ? messages.stream()
                .map(MessageEntity::getMessageDate)
                .filter(date -> date != null)
                .max(Instant::compareTo)
                .orElse(firstNonNull(guide.getCreatedAt(), Instant.now()))
            : firstNonNull(guide.getCreatedAt(), Instant.now());
    }

    private boolean guideAnglesCompatible(String currentAngle, String existingAngle) {
        String current = normalizeGuideAngle(currentAngle);
        String existing = normalizeGuideAngle(existingAngle);
        if (current.isBlank() || existing.isBlank()) {
            return true;
        }
        if (current.equals(existing) || isGenericGuideAngle(current) || isGenericGuideAngle(existing)) {
            return true;
        }
        Set<String> currentTokens = tokenSet(current);
        Set<String> existingTokens = tokenSet(existing);
        if (currentTokens.isEmpty() || existingTokens.isEmpty()) {
            return false;
        }
        Set<String> intersection = new HashSet<>(currentTokens);
        intersection.retainAll(existingTokens);
        Set<String> union = new HashSet<>(currentTokens);
        union.addAll(existingTokens);
        return !union.isEmpty() && (double) intersection.size() / union.size() >= 0.5;
    }

    private boolean isGenericGuideAngle(String normalizedAngle) {
        return normalizedAngle.equals("practical guide")
            || normalizedAngle.equals("topic summary")
            || normalizedAngle.contains("guide by cluster topic")
            || normalizedAngle.contains("practical guide by cluster topic");
    }

    private Set<String> tokenSet(String value) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (value != null) {
            for (String token : value.split(" ")) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private String normalizeGuideAngle(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    private boolean sameId(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    private Instant firstNonNull(Instant first, Instant second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record ReusableGuideMatch(GuideEntity guide, String reason) {}

    private void linkMissingSourceMessages(Long guideId, List<MessageEntity> sourceMessages) {
        if (guideId == null || sourceMessages == null) {
            return;
        }
        for (MessageEntity sourceMessage : sourceMessages) {
            if (sourceMessage == null || sourceMessage.getId() == null) {
                continue;
            }
            if (guideSourceMessageRepository.existsByGuideIdAndMessageId(guideId, sourceMessage.getId())) {
                continue;
            }
            GuideSourceMessageEntity sourceMsg = new GuideSourceMessageEntity();
            sourceMsg.setGuideId(guideId);
            sourceMsg.setMessageId(sourceMessage.getId());
            sourceMsg.setUsedInPrompt(true);
            guideSourceMessageRepository.save(sourceMsg);
        }
    }

    private void markSourceMessagesClustered(List<MessageEntity> sourceMessages, Long guideId, String stage) {
        for (MessageEntity sourceMessage : sourceMessages) {
            sourceMessage.setProcessingStatus("CLUSTERED");
            if (guideId != null) {
                sourceMessage.setGuideId(guideId);
            }
            saveMessage(sourceMessage, stage);
        }
    }

    private void markSourceMessagesSkipped(List<MessageEntity> sourceMessages, String reason, String stage) {
        for (MessageEntity sourceMessage : sourceMessages) {
            sourceMessage.setProcessingStatus("SKIPPED");
            sourceMessage.setClassifierReason(truncate(reason, MAX_REASON_LEN));
            saveMessage(sourceMessage, stage);
        }
    }

    private void applyClusterIntelligence(
        List<MessageEntity> sourceMessages,
        ClassifierResult clusterResult,
        SignalScore signalScore
    ) {
        if (sourceMessages == null || clusterResult == null) {
            return;
        }
        for (MessageEntity sourceMessage : sourceMessages) {
            if (sourceMessage == null) {
                continue;
            }
            sourceMessage.setClassifierScore(clusterResult.score());
            sourceMessage.setClassifierReason("Cluster-level classification: " + clusterResult.reasoning());
            sourceMessage.setClassifierResultJson(safeToJson(clusterResult));
            applyMessageIntelligence(sourceMessage, clusterResult, signalScore);
        }
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
        return saveGuideAndSources(
            message,
            chain,
            classifier,
            guidePrompt,
            provider,
            guideContent,
            inputTokens,
            outputTokens,
            totalTokens,
            estimatedCostUsd,
            usingLocalFallback,
            null,
            null,
            null
        );
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
        boolean usingLocalFallback,
        Long topicClusterId,
        Long topicClusterGuideCandidateId,
        ContentRoutingDecision routingDecision
    ) {
        GuideEntity guide = new GuideEntity();
        guide.setTitle(guideContent.title());
        guide.setContent(guideContent.content());
        guide.setContentMarkdown(guideContent.contentMarkdown());
        guide.setRawResponse(guideContent.rawResponse());
        guide.setGroupId(message.getGroupId());
        guide.setOwnerUserId(message.getOwnerUserId());
        guide.setRootMessageId(message.getId());
        guide.setTopicClusterId(topicClusterId);
        guide.setTopicClusterGuideCandidateId(topicClusterGuideCandidateId);
        applyContentRoutingToGuide(guide, routingDecision);
        guide.setProviderId(guideContent.providerId() != null ? guideContent.providerId() : provider != null ? provider.getId() : null);
        guide.setModel(guideContent.model() != null ? guideContent.model() : provider != null ? provider.getModel() : (usingLocalFallback ? "local-fallback" : null));
        guide.setClassifierId(classifier != null ? classifier.getId() : null);
        guide.setPromptId(guidePrompt != null ? guidePrompt.getId() : null);
        guide.setPromptVersion(guidePrompt != null ? guidePrompt.getVersion() : null);
        guide.setStatus(guideContent.generationError() != null ? "FAILED" : "DRAFT");
        guide.setConfidence(guideContent.confidence());
        guide.setUsefulnessScore(guideUsefulnessScorer.score(guide, message, parseClassifierResult(message)));
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

    private GuideEntity generateGuideForClassifiedMessage(MessageEntity message) {
        ClassifierResult classifierResult = parseClassifierResult(message);
        TopicClusterService.ClusterUpdateResult clusterUpdate = topicClusterService.upsertFromMessage(
            message,
            List.of(message),
            classifierResult,
            null
        );
        TopicClusterService.ClusterClassificationResult clusterClassification =
            topicClusterService.applyClusterClassification(
                clusterUpdate.cluster(),
                classifierResult,
                null,
                clusterUpdate.sourceMessages()
            );
        String traceId = UUID.randomUUID().toString();
        RoutedContentSelection contentSelection = selectRoutedContent(
            message,
            clusterClassification.cluster(),
            clusterClassification.guideCandidates(),
            clusterUpdate.sourceMessages(),
            classifierResult,
            traceId
        );
        TopicClusterGuideCandidateEntity guideCandidate = contentSelection != null ? contentSelection.candidate() : null;
        ContentRoutingDecision routingDecision = contentSelection != null ? contentSelection.decision() : null;
        ContentQualityGate.GateResult gateResult = contentSelection != null
            ? contentSelection.gateResult()
            : ContentQualityGate.GateResult.blocked("content routing did not return a decision");
        if (!gateResult.allowed()) {
            applyContentRoutingToCandidate(
                guideCandidate,
                routingDecision != null ? routingDecision.withGateBlock(gateResult.reason()) : null
            );
            topicClusterService.markGuideSkipped(clusterClassification.cluster(), "QUALITY_BLOCKED");
            markSourceMessagesClustered(
                clusterUpdate.sourceMessages(),
                null,
                "content-quality-gate-blocked"
            );
            return null;
        }
        return generateMaterialForCluster(
            message,
            clusterClassification.cluster(),
            guideCandidate,
            clusterUpdate.sourceMessages(),
            null,
            classifierResult,
            routingDecision,
            traceId
        );
    }

    private boolean isMissingGuideCandidate(MessageEntity message) {
        if (message.getGuideId() != null || message.getClassifierScore() == null) {
            return false;
        }
        if (message.getClassifierScore() < CLASSIFIER_THRESHOLD) {
            return false;
        }
        ClassifierResult classifierResult = parseClassifierResult(message);
        return classifierResult.guideCandidate()
            || message.getClassifierResultJson() == null
            || message.getClassifierResultJson().isBlank();
    }

    private boolean isRetryableApiError(MessageEntity message) {
        if (!"ERROR".equals(message.getProcessingStatus())) {
            return false;
        }
        if (looksLikeIngestionError(message.getClassifierReason())) {
            return false;
        }
        if (message.getGuideId() != null) {
            GuideEntity guide = guideRepository.findById(message.getGuideId()).orElse(null);
            if (guide == null) {
                return hasStoredClassification(message);
            }
            return guide.getGenerationError() != null
                && !guide.getGenerationError().isBlank()
                && !looksLikeIngestionError(guide.getGenerationError());
        }
        return hasStoredClassification(message);
    }

    private boolean hasStoredClassification(MessageEntity message) {
        return message.getClassifierScore() != null
            || (message.getClassifierReason() != null && !message.getClassifierReason().isBlank())
            || (message.getClassifierResultJson() != null && !message.getClassifierResultJson().isBlank());
    }

    private boolean looksLikeIngestionError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String normalized = error.toLowerCase();
        return normalized.contains("tdlib")
            || normalized.contains("telegram")
            || normalized.contains("auth")
            || normalized.contains("ingestion")
            || normalized.contains("login")
            || normalized.contains("phone")
            || normalized.contains("code");
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

    private PipelineTraceService.TraceMetadata ruleTraceMetadata(RuleResult ruleResult, String ruleResultJson) {
        return new PipelineTraceService.TraceMetadata(
            "RULE_SET",
            "active-rules",
            null,
            truncate(ruleResultJson, TRACE_CONFIG_MAX_LEN),
            ruleResult.passes()
                ? "Rules passed. If bad content continues, tighten INCLUDE/EXCLUDE conditions."
                : "Rules rejected this message. If this was useful content, inspect ruleId and conditionsJson in ruleResultJson."
        );
    }

    private PipelineTraceService.TraceMetadata classifierSetTraceMetadata(String hint) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("activeClassifiers", List.of());
        snapshot.put("hint", hint);
        return new PipelineTraceService.TraceMetadata(
            "CLASSIFIER_SET",
            "active-classifiers",
            null,
            truncate(safeToJson(snapshot), TRACE_CONFIG_MAX_LEN),
            "No active classifiers were available. Enable or create at least one classifier before tuning thresholds."
        );
    }

    private PipelineTraceService.TraceMetadata classifierTraceMetadata(
        ClassifierEntity classifier,
        ClassifierResult classifierResult
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classifier", classifierSnapshot(classifier));
        snapshot.put("prompt", promptSnapshot(findPromptById(classifier.getPromptId())));
        snapshot.put("configuredProvider", providerSnapshot(findProviderById(classifier.getProviderId())));
        snapshot.put("routedProviderId", classifierResult.providerId());
        snapshot.put("routedModel", classifierResult.model());
        snapshot.put("observedResult", classifierResult);
        return new PipelineTraceService.TraceMetadata(
            "CLASSIFIER",
            classifier.getName(),
            classifier.getVersion(),
            truncate(safeToJson(snapshot), TRACE_CONFIG_MAX_LEN),
            classifierTuningHint(classifier, classifierResult)
        );
    }

    private PipelineTraceService.TraceMetadata guideTraceMetadata(
        ClassifierEntity classifier,
        PromptEntity guidePrompt,
        AiProviderEntity provider,
        GuideContent guideContent,
        boolean usingLocalFallback
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("prompt", promptSnapshot(guidePrompt));
        snapshot.put("provider", providerSnapshot(provider));
        snapshot.put("classifier", classifierSnapshot(classifier));
        snapshot.put("localFallback", usingLocalFallback);
        snapshot.put("guideConfidence", guideContent != null ? guideContent.confidence() : null);
        snapshot.put("generationError", guideContent != null ? guideContent.generationError() : null);
        return new PipelineTraceService.TraceMetadata(
            "PROMPT",
            guidePrompt != null ? guidePrompt.getName() : usingLocalFallback ? "local-fallback" : "missing-guide-prompt",
            guidePrompt != null ? guidePrompt.getVersion() : null,
            truncate(safeToJson(snapshot), TRACE_CONFIG_MAX_LEN),
            guideTuningHint(guideContent, usingLocalFallback)
        );
    }

    private String classifierTuningHint(ClassifierEntity classifier, ClassifierResult classifierResult) {
        String type = classifier.getType() != null ? classifier.getType().toUpperCase() : "";
        if ("LINEAR_MODEL".equals(type)) {
            return "Tune modelConfigJson weights/threshold. Compare outputData reasoning and score against expected decision.";
        }
        if ("LLM".equals(type)) {
            return classifierResult.matched()
                ? "Review prompt snapshot if labels, guideCandidate, or evidenceMessageIds are wrong."
                : "LLM classifier skipped. Inspect prompt snapshot, evidence text, and score before changing thresholds.";
        }
        if ("KEYWORD".equals(type)) {
            return classifierResult.matched()
                ? "Keyword classifier matched. Remove noisy keywords if this is a false positive."
                : "Keyword classifier skipped. Add domain keywords or lower reliance on this classifier if this is a false negative.";
        }
        if ("REGEX".equals(type)) {
            return classifierResult.matched()
                ? "Regex classifier matched. Tighten regexPattern if this is a false positive."
                : "Regex classifier skipped. Test regexPattern against messageText if this is a false negative.";
        }
        return "Unknown classifier type. Inspect classifier config snapshot.";
    }

    private String guideTuningHint(GuideContent guideContent, boolean usingLocalFallback) {
        if (usingLocalFallback) {
            return "Local fallback guide was used. Configure an active provider before judging generation quality.";
        }
        if (guideContent == null) {
            return "Guide generation produced no content. Inspect provider and prompt snapshot.";
        }
        if (guideContent.generationError() != null && !guideContent.generationError().isBlank()) {
            return "Guide generation failed. Inspect prompt constraints, provider/model, raw output, and generationError.";
        }
        if (guideContent.confidence() < 0.5) {
            return "Low-confidence guide. Improve generation prompt or upstream classifier evidence selection.";
        }
        return "Guide generated. Use confidence, title, tags, and source messages to refine the generation prompt.";
    }

    private Map<String, Object> classifierSnapshot(ClassifierEntity classifier) {
        if (classifier == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", classifier.getId());
        snapshot.put("name", classifier.getName());
        snapshot.put("type", classifier.getType());
        snapshot.put("version", classifier.getVersion());
        snapshot.put("status", classifier.getStatus());
        snapshot.put("classifierOrder", classifier.getClassifierOrder());
        snapshot.put("providerId", classifier.getProviderId());
        snapshot.put("promptId", classifier.getPromptId());
        snapshot.put("keywords", classifier.getKeywords());
        snapshot.put("regexPattern", classifier.getRegexPattern());
        snapshot.put("modelConfigJson", parseJsonOrRaw(classifier.getModelConfigJson()));
        return snapshot;
    }

    private Map<String, Object> promptSnapshot(PromptEntity prompt) {
        if (prompt == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", prompt.getId());
        snapshot.put("name", prompt.getName());
        snapshot.put("type", prompt.getType());
        snapshot.put("version", prompt.getVersion());
        snapshot.put("status", prompt.getStatus());
        snapshot.put("variablesJson", parseJsonOrRaw(prompt.getVariablesJson()));
        snapshot.put("content", prompt.getContent());
        return snapshot;
    }

    private Map<String, Object> providerSnapshot(AiProviderEntity provider) {
        if (provider == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", provider.getId());
        snapshot.put("name", provider.getName());
        snapshot.put("protocol", provider.getProtocol());
        snapshot.put("endpointUrl", provider.getEndpointUrl());
        snapshot.put("model", provider.getModel());
        snapshot.put("status", provider.getStatus());
        snapshot.put("lastTestResult", provider.getLastTestResult());
        snapshot.put("lastError", provider.getLastError());
        snapshot.put("hasApiKey", provider.getApiKeyEncrypted() != null && !provider.getApiKeyEncrypted().isBlank());
        return snapshot;
    }

    private PromptEntity findPromptById(Long promptId) {
        return promptId == null ? null : promptRepository.findById(promptId).orElse(null);
    }

    private AiProviderEntity findProviderById(Long providerId) {
        return providerId == null ? null : aiProviderRepository.findById(providerId).orElse(null);
    }

    private Object parseJsonOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return raw;
        }
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

    private ClassifierResult parseClassifierResult(MessageEntity message) {
        if (message.getClassifierResultJson() != null && !message.getClassifierResultJson().isBlank()) {
            try {
                return objectMapper.readValue(message.getClassifierResultJson(), ClassifierResult.class);
            } catch (Exception exception) {
                log.debug("Failed to parse classifier result for message {}: {}", message.getId(), exception.getMessage());
            }
        }
        return new ClassifierResult(
            message.getClassifierScore() != null ? message.getClassifierScore() : 0.0,
            message.getClassifierScore() == null || message.getClassifierScore() >= CLASSIFIER_THRESHOLD,
            List.of(),
            true,
            List.of(message.getId()),
            message.getClassifierReason() != null
                ? message.getClassifierReason()
                : "Guide generated from stored classification"
        );
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
        target.setGuidePotentialScore(source.getGuidePotentialScore());
        target.setProblemSignalScore(source.getProblemSignalScore());
        target.setPainScore(source.getPainScore());
        target.setUrgencyScore(source.getUrgencyScore());
        target.setWillingnessToPayScore(source.getWillingnessToPayScore());
        target.setTechnicalDepthScore(source.getTechnicalDepthScore());
        target.setSpamScore(source.getSpamScore());
        target.setMeaningSummary(source.getMeaningSummary());
        target.setProblemStatement(source.getProblemStatement());
        target.setSolutionHint(source.getSolutionHint());
        target.setMentionedToolsJson(source.getMentionedToolsJson());
        target.setMentionedPricesJson(source.getMentionedPricesJson());
        target.setMentionedErrorsJson(source.getMentionedErrorsJson());
        target.setIntelligenceReason(source.getIntelligenceReason());
        target.setClusterCandidate(source.getClusterCandidate());
        target.setEmbeddingStatus(source.getEmbeddingStatus());
        target.setMessageIntelligenceJson(source.getMessageIntelligenceJson());
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
        if (signalScore.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM)) {
            labels.add(ClassificationLabels.PRACTICAL_PROBLEM);
            labels.add(ClassificationLabels.OPPORTUNITY);
        }
        if (signalScore.labels().contains(ClassificationLabels.WORKFLOW_LIFEHACK)) {
            labels.add(ClassificationLabels.WORKFLOW_LIFEHACK);
            labels.add(ClassificationLabels.SOLUTION_MENTION);
        }
        if (signalScore.labels().contains(ClassificationLabels.BUSINESS_PROCESS)) {
            labels.add(ClassificationLabels.BUSINESS_PROCESS);
        }
        if (signalScore.labels().contains(ClassificationLabels.PRODUCT_FEEDBACK)) {
            labels.add(ClassificationLabels.PRODUCT_FEEDBACK);
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
            signalScore.classificationReason(),
            signalScore.labels().contains("AI_ACCESS_DEMAND")
                || signalScore.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM) ? 70 : 0,
            signalScore.labels().contains("PAIN_LIMITS")
                || signalScore.labels().contains(ClassificationLabels.PRACTICAL_PROBLEM) ? 70 : 0,
            signalScore.labels().contains("PAYMENT_WORKAROUND") ? 70 : 0,
            0,
            0,
            signalScore.labels().contains("PROVIDER_MENTION")
                || signalScore.labels().contains(ClassificationLabels.WORKFLOW_LIFEHACK) ? 45 : 0,
            signalScore.labels().contains("OFFER_OR_SPAM") ? 80 : 0,
            chain.stream()
                .map(MessageEntity::getText)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .map(text -> truncate(text, 300))
                .orElse(null),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
        return safeToJson(fallback);
    }

    private void applyMessageIntelligence(MessageEntity message, ClassifierResult classifierResult, SignalScore signalScore) {
        if (message == null) {
            return;
        }
        int guidePotentialScore = classifierResult != null
            ? normalizedGuidePotentialScore(classifierResult)
            : scoreFromRatio(message.getClassifierScore());
        message.setGuidePotentialScore(guidePotentialScore);

        if (!problemExtractionEnabled) {
            message.setClusterCandidate(false);
            message.setEmbeddingStatus("NONE");
            message.setIntelligenceReason("problem.extraction.enabled=false");
            Map<String, Object> intelligence = new LinkedHashMap<>();
            intelligence.put("problemExtractionEnabled", false);
            intelligence.put("guidePotentialScore", guidePotentialScore);
            message.setMessageIntelligenceJson(safeToJson(intelligence));
            return;
        }

        int problemSignalScore = scoreOrDefault(
            classifierResult != null ? classifierResult.problemSignalScore() : null,
            signalScore != null && hasMeaningfulSignal(signalScore) ? scoreFromRatio(signalScore.score()) : 0
        );
        int painScore = scoreOrDefault(classifierResult != null ? classifierResult.painScore() : null, 0);
        int willingnessToPayScore = scoreOrDefault(classifierResult != null ? classifierResult.willingnessToPayScore() : null, 0);
        int urgencyScore = scoreOrDefault(classifierResult != null ? classifierResult.urgencyScore() : null, 0);
        int technicalDepthScore = scoreOrDefault(classifierResult != null ? classifierResult.technicalDepthScore() : null, 0);
        int spamScore = scoreOrDefault(classifierResult != null ? classifierResult.spamScore() : null, 0);

        message.setProblemSignalScore(problemSignalScore);
        message.setPainScore(painScore);
        message.setWillingnessToPayScore(willingnessToPayScore);
        message.setUrgencyScore(urgencyScore);
        message.setTechnicalDepthScore(technicalDepthScore);
        message.setSpamScore(spamScore);
        message.setMeaningSummary(classifierResult != null ? truncate(classifierResult.meaningSummary(), 1000) : null);
        message.setProblemStatement(classifierResult != null ? truncate(classifierResult.problemStatement(), 1000) : null);
        message.setSolutionHint(classifierResult != null ? truncate(classifierResult.solutionHint(), 1000) : null);
        message.setMentionedToolsJson(classifierResult != null ? safeToJson(classifierResult.mentionedTools()) : "[]");
        message.setMentionedPricesJson(classifierResult != null ? safeToJson(classifierResult.mentionedPrices()) : "[]");
        message.setMentionedErrorsJson(classifierResult != null ? safeToJson(classifierResult.mentionedErrors()) : "[]");
        message.setIntelligenceReason(classifierResult != null
            ? truncate(classifierResult.reasoning(), MAX_REASON_LEN)
            : message.getClassifierReason());

        boolean clusterCandidate = isClusterCandidate(
            problemSignalScore,
            painScore,
            willingnessToPayScore,
            guidePotentialScore,
            technicalDepthScore,
            spamScore
        );
        message.setClusterCandidate(clusterCandidate);
        message.setEmbeddingStatus(clusterCandidate ? "REQUIRED" : "NONE");

        Map<String, Object> intelligence = new LinkedHashMap<>();
        intelligence.put("labels", classifierResult != null ? classifierResult.labels() : List.of());
        intelligence.put("categories", classifierResult != null ? classifierResult.categories() : List.of());
        intelligence.put("evidenceMessageIds", classifierResult != null ? classifierResult.evidenceMessageIds() : List.of());
        intelligence.put("clusterCandidate", clusterCandidate);
        intelligence.put("embeddingStatus", message.getEmbeddingStatus());
        intelligence.put("reason", message.getIntelligenceReason() != null ? message.getIntelligenceReason() : "");
        message.setMessageIntelligenceJson(truncate(safeToJson(intelligence), 4000));
    }

    private boolean shouldCreateGuide(ClassifierResult classifierResult) {
        return classifierResult != null
            && classifierResult.guideCandidate()
            && scoreOrDefault(classifierResult.spamScore(), 0) < spamBlockThreshold
            && normalizedGuidePotentialScore(classifierResult) >= guidePotentialThreshold;
    }

    private int normalizedGuidePotentialScore(ClassifierResult classifierResult) {
        if (classifierResult == null) {
            return 0;
        }
        return scoreOrDefault(
            classifierResult.guidePotentialScore(),
            classifierResult.guideCandidate() ? scoreFromRatio(classifierResult.score()) : 0
        );
    }

    private boolean isClusterCandidate(
        int problemSignalScore,
        int painScore,
        int willingnessToPayScore,
        int guidePotentialScore,
        int technicalDepthScore,
        int spamScore
    ) {
        if (spamScore >= spamBlockThreshold) {
            return false;
        }
        return problemSignalScore >= clusterProblemSignalThreshold
            || painScore >= clusterPainThreshold
            || willingnessToPayScore >= clusterWillingnessToPayThreshold
            || guidePotentialScore >= clusterGuidePotentialThreshold
            || technicalDepthScore >= clusterTechnicalDepthThreshold;
    }

    private int scoreOrDefault(Integer value, int defaultValue) {
        return clampScore(value != null ? value : defaultValue);
    }

    private int scoreFromRatio(Double value) {
        if (value == null) {
            return 0;
        }
        return clampScore((int) Math.round(value * 100));
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
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
        return "Matched output had no meaningful useful-content signal for CLASSIFIED";
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
