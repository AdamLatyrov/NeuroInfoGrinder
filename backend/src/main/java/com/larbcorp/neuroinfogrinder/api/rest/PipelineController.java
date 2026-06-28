package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineService;
import com.larbcorp.neuroinfogrinder.domain.findings.QueueProcessor;
import com.larbcorp.neuroinfogrinder.domain.findings.TopicClusterService;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GuideRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * REST API for pipeline control and results inspection.
 */
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private static final int TOPIC_CANDIDATES_DEFAULT_LIMIT = 500;

    private final PipelineService pipelineService;
    private final TopicClusterService topicClusterService;
    private final QueueProcessor queueProcessor;
    private final MessageRepository messageRepository;
    private final GuideRepository guideRepository;
    private final GroupRepository groupRepository;
    private final PipelineTraceRepository pipelineTraceRepository;

    // ── Queue control ──

    /** Process a single message immediately by ID. */
    @PostMapping("/process/{messageId}")
    @ResponseStatus(HttpStatus.OK)
    public void processMessage(@AuthenticationPrincipal Long ownerUserId, @PathVariable Long messageId) {
        requireMessageForOwner(ownerUserId, messageId);
        pipelineService.processMessage(messageId);
    }

    /** Manually drain up to N messages from the queue. */
    @PostMapping("/process-queue")
    @ResponseStatus(HttpStatus.OK)
    public void processQueue(@AuthenticationPrincipal Long ownerUserId,
                             @RequestParam(defaultValue = "10") int limit) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        if (enabledGroupIds.isEmpty()) {
            return;
        }

        List<MessageEntity> queued = messageRepository
            .findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
                enabledGroupIds,
                List.of("QUEUED", "UNPROCESSED"),
                org.springframework.data.domain.Pageable.ofSize(limit)
            ).getContent();

        for (MessageEntity msg : queued) {
            try {
                pipelineService.processMessage(msg.getId());
            } catch (Exception e) {
                // Continue processing remaining messages
            }
        }
    }

    @PostMapping("/generate-missing-guides")
    public PipelineService.GenerateMissingGuidesResponse generateMissingGuides(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return pipelineService.generateMissingGuides(ownerUserId, limit);
    }

    @PostMapping("/retry-api-errors")
    public PipelineService.RetryApiErrorsResponse retryApiErrors(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return pipelineService.retryApiErrors(ownerUserId, limit);
    }

    @PostMapping("/requeue")
    @ResponseStatus(HttpStatus.OK)
    public RequeueResponse requeue(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) List<String> statuses
    ) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        if (enabledGroupIds.isEmpty()) {
            return new RequeueResponse(0);
        }

        List<String> effectiveStatuses = (statuses == null || statuses.isEmpty())
            ? List.of("SKIPPED", "CLASSIFIED", "CLUSTERED", "CLEARED")
            : statuses;

        List<MessageEntity> messages = messageRepository.findByGroupIdInAndProcessingStatusIn(
            enabledGroupIds,
            effectiveStatuses
        );

        messages.forEach(message -> {
            resetForRequeue(message);
        });
        messageRepository.saveAll(messages);
        return new RequeueResponse(messages.size());
    }

    @PostMapping("/requeue/{messageId}")
    @ResponseStatus(HttpStatus.OK)
    public RequeueResponse requeueMessage(@AuthenticationPrincipal Long ownerUserId,
                                          @PathVariable Long messageId) {
        MessageEntity message = requireMessageForOwner(ownerUserId, messageId);

        GroupEntity group = groupRepository.findByIdAndOwnerUserId(message.getGroupId(), ownerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + message.getGroupId()));

        if (!Boolean.TRUE.equals(group.getEnabled())) {
            return new RequeueResponse(0);
        }

        resetForRequeue(message);
        messageRepository.save(message);
        return new RequeueResponse(1);
    }

    @PostMapping("/dev/smoke-guide")
    public SmokeGuideResponse smokeGuide(@AuthenticationPrincipal Long ownerUserId) {
        GroupEntity group = groupRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId).stream()
            .findFirst()
            .orElseGet(() -> {
                GroupEntity created = new GroupEntity();
                created.setTelegramChatId(-3001L);
                created.setTitle("Dev Smoke Group");
                created.setUsername("dev_smoke");
                created.setCategory("dev");
                created.setForum(false);
                created.setEnabled(true);
                created.setOwnerUserId(ownerUserId);
                created.setLastReadMessageId(0L);
                return groupRepository.save(created);
            });

        long telegramMessageId = System.currentTimeMillis();
        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(telegramMessageId);
        message.setGroupId(group.getId());
        message.setOwnerUserId(ownerUserId);
        message.setSenderName("Dev Smoke");
        message.setSenderTelegramUserId(-3001L);
        message.setIsBot(false);
        message.setText("GPT Claude Codex free credits: тестовое AI сообщение для проверки правил, классификатора и генерации гайда.");
        message.setReplyCount(1);
        message.setProcessingStatus("UNPROCESSED");
        message.setTopicName("Smoke");
        message.setTopicId(3001L);
        message.setMessageDate(java.time.Instant.now());
        message = messageRepository.save(message);

        GuideEntity guide = pipelineService.processMessage(message.getId());
        MessageEntity processedMessage = messageRepository.findById(message.getId()).orElse(message);
        return new SmokeGuideResponse(
            message.getId(),
            processedMessage.getProcessingStatus(),
            guide != null ? guide.getId() : null,
            guide != null ? guide.getTitle() : null
        );
    }

    /** Pause the automatic queue processor. */
    @PostMapping("/pause")
    @ResponseStatus(HttpStatus.OK)
    public void pause() {
        queueProcessor.setEnabled(false);
    }

    /** Resume the automatic queue processor. */
    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.OK)
    public void resume() {
        queueProcessor.setEnabled(true);
    }

    /** Get queue processor status. */
    @GetMapping("/status")
    public PipelineStatusResponse getStatus(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);

        long unprocessed = countByStatus(enabledGroupIds, "UNPROCESSED");
        long queued = countByStatus(enabledGroupIds, "QUEUED");
        long processing = countByStatus(enabledGroupIds, "PROCESSING");
        long classified = countByStatusInWindow(enabledGroupIds, "CLASSIFIED", fromInstant, toInstant);
        long skipped = countByStatusInWindow(enabledGroupIds, "SKIPPED", fromInstant, toInstant);
        long guideFound = countByStatusInWindow(enabledGroupIds, "GUIDE_FOUND", fromInstant, toInstant);
        long errors = countByStatusInWindow(enabledGroupIds, "ERROR", fromInstant, toInstant);
        long guidesTotal = guideRepository.countByOwnerUserId(ownerUserId);
        long messagesWithGuide = countMessagesWithGuideInWindow(enabledGroupIds, fromInstant, toInstant);
        long guideGenerationErrors = guideRepository.countGenerationErrors();

        return new PipelineStatusResponse(
            queueProcessor.isEnabled(),
            unprocessed, queued, processing, classified, skipped, guideFound, errors,
            guidesTotal, messagesWithGuide, guideGenerationErrors,
            classified, skipped, errors
        );
    }

    @GetMapping("/queue")
    public PageResponse<PipelineQueueItem> getQueue(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) List<String> statuses,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Pageable pageable
    ) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        if (enabledGroupIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        List<String> effectiveStatuses = (statuses == null || statuses.isEmpty())
            ? List.of("QUEUED", "UNPROCESSED", "PROCESSING")
            : statuses;

        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);

        Page<MessageEntity> page;
        if (fromInstant != null && toInstant != null) {
            page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
                enabledGroupIds, effectiveStatuses, fromInstant, toInstant, pageable);
        } else {
            page = messageRepository.findByGroupIdInAndProcessingStatusInOrderByMessageDateAsc(
                enabledGroupIds, effectiveStatuses, pageable);
        }

        java.util.Map<Long, String> groupTitles = groupRepository.findAllById(enabledGroupIds).stream()
            .collect(java.util.stream.Collectors.toMap(GroupEntity::getId, GroupEntity::getTitle));

        return PageResponse.from(page.map(message -> new PipelineQueueItem(
            message.getId(),
            message.getGroupId(),
            groupTitles.getOrDefault(message.getGroupId(), "Unknown group"),
            message.getSenderName(),
            message.getText() != null && message.getText().length() > 240
                ? message.getText().substring(0, 240) + "..."
                : message.getText(),
            message.getProcessingStatus(),
            message.getTopicName(),
            message.getMessageDate()
        )));
    }

    @DeleteMapping("/queue")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public ClearQueueResponse clearQueue(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) List<String> statuses
    ) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        if (enabledGroupIds.isEmpty()) {
            return new ClearQueueResponse(0);
        }

        List<String> effectiveStatuses = (statuses == null || statuses.isEmpty())
            ? List.of("QUEUED")
            : statuses;

        List<MessageEntity> messages = messageRepository.findByGroupIdInAndProcessingStatusIn(
            enabledGroupIds,
            effectiveStatuses
        );

        messages.forEach(message -> message.setProcessingStatus("SKIPPED"));
        messageRepository.saveAll(messages);
        return new ClearQueueResponse(messages.size());
    }

    @DeleteMapping("/results")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public ClearResultsResponse clearResults(@AuthenticationPrincipal Long ownerUserId) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        List<MessageEntity> processed = messageRepository.findByGroupIdInAndProcessingStatusIn(
            enabledGroupIds,
            List.of("UNPROCESSED", "QUEUED", "PROCESSING", "CLUSTERED", "CLASSIFIED", "SKIPPED", "GUIDE_FOUND", "ERROR", "CLEARED")
        ).stream()
            .filter(message -> message.getSignalScore() != null
                || message.getClassifierScore() != null
                || message.getGuideId() != null
                || message.getClassifierReason() != null
                || message.getSignalBreakdown() != null
                || message.getRuleResultJson() != null)
            .toList();

        if (processed.isEmpty()) {
            return new ClearResultsResponse(0, 0);
        }

        List<Long> messageIds = processed.stream().map(MessageEntity::getId).toList();
        pipelineTraceRepository.deleteByMessageIdIn(messageIds);

        processed.forEach(message -> {
            message.setSignalScore(null);
            clearIntelligence(message);
            message.setClassifierScore(null);
            message.setClassifierReason(null);
            message.setClassifierResultJson(null);
            message.setClassificationContextHash(null);
            message.setSignalBreakdown(null);
            message.setRuleResultJson(null);
            message.setGuideId(null);
            if (!"PROCESSING".equals(message.getProcessingStatus())) {
                message.setProcessingStatus("CLEARED");
            }
        });
        messageRepository.saveAll(processed);

        return new ClearResultsResponse(processed.size(), messageIds.size());
    }

    // ── Pipeline results (for tuning prompts & classifiers) ──

    /**
     * Get messages that went through the pipeline (have scores),
     * optionally filtered by status and date range.
     * Date parameters accept ISO-8601 date-time (2026-06-08T14:30:00Z)
     * or date-only (2026-06-08) which is interpreted as start-of-day for 'from'
     * and end-of-day for 'to'.
     */
    @GetMapping("/results")
    public PageResponse<PipelineResultItem> getResults(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) List<String> statuses,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Pageable pageable
    ) {
        List<Long> enabledGroupIds = enabledGroupIds(ownerUserId);
        if (enabledGroupIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        List<String> pipelineStatuses = statuses != null && !statuses.isEmpty()
            ? statuses
            : status != null
                ? List.of(status)
                : List.of("UNPROCESSED", "QUEUED", "PROCESSING", "CLUSTERED", "CLASSIFIED", "SKIPPED", "GUIDE_FOUND", "ERROR");

        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);

        Page<MessageEntity> page;
        if (fromInstant != null && toInstant != null) {
            page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateBetween(
                enabledGroupIds, pipelineStatuses, fromInstant, toInstant, pageable);
        } else if (fromInstant != null) {
            page = messageRepository.findByGroupIdInAndProcessingStatusInAndMessageDateAfter(
                enabledGroupIds, pipelineStatuses, fromInstant, pageable);
        } else {
            page = messageRepository.findByGroupIdInAndProcessingStatusIn(
                enabledGroupIds, pipelineStatuses, pageable);
        }

        return PageResponse.from(page.map(msg -> new PipelineResultItem(
            msg.getId(),
            msg.getGroupId(),
            msg.getSenderName(),
            msg.getText() != null ? (msg.getText().length() > 500 ? msg.getText().substring(0, 500) + "..." : msg.getText()) : "",
            msg.getProcessingStatus(),
            msg.getSignalScore(),
            msg.getClassifierScore(),
            msg.getClassifierReason(),
            msg.getClassifierResultJson(),
            msg.getClassificationContextHash(),
            msg.getGuideId(),
            msg.getMessageDate(),
            msg.getSignalBreakdown(),
            msg.getRuleResultJson(),
            msg.getGuidePotentialScore(),
            msg.getProblemSignalScore(),
            msg.getPainScore(),
            msg.getUrgencyScore(),
            msg.getWillingnessToPayScore(),
            msg.getTechnicalDepthScore(),
            msg.getSpamScore(),
            msg.getMeaningSummary(),
            msg.getProblemStatement(),
            msg.getSolutionHint(),
            msg.getMentionedToolsJson(),
            msg.getMentionedPricesJson(),
            msg.getMentionedErrorsJson(),
            msg.getIntelligenceReason(),
            msg.getClusterCandidate(),
            msg.getEmbeddingStatus(),
            msg.getMessageIntelligenceJson()
        )));
    }

    @GetMapping("/topic-candidates")
    public TopicClusterService.TopicCandidatesResponse getTopicCandidates(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) String group,
        @RequestParam(required = false) String topic,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "20") int windowMinutes,
        @RequestParam(defaultValue = "false") boolean clusterCandidateOnly,
        @RequestParam(required = false) Integer minProblemSignalScore,
        @RequestParam(required = false) Integer minPainScore,
        @RequestParam(required = false) Integer minWillingnessToPayScore,
        @RequestParam(required = false) Integer minGuidePotentialScore,
        @RequestParam(defaultValue = "70") int maxSpamScore,
        @RequestParam(defaultValue = "" + TOPIC_CANDIDATES_DEFAULT_LIMIT) int limit
    ) {
        return topicClusterService.getTopicCandidates(
            ownerUserId,
            group,
            topic,
            from,
            to,
            windowMinutes,
            clusterCandidateOnly,
            minProblemSignalScore,
            minPainScore,
            minWillingnessToPayScore,
            minGuidePotentialScore,
            maxSpamScore,
            limit
        );
    }

    @GetMapping("/topics/explain")
    public TopicClusterService.TopicExplainResponse explainTopics(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) String group,
        @RequestParam(required = false) String topic,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) Long messageId,
        @RequestParam(defaultValue = "20") int windowMinutes
    ) {
        return topicClusterService.explainTopics(ownerUserId, group, topic, from, to, messageId, windowMinutes);
    }

    @GetMapping("/topic-clusters")
    public PageResponse<TopicClusterService.TopicClusterItem> getTopicClusters(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(required = false) Long groupId,
        @RequestParam(required = false) List<String> statuses,
        Pageable pageable
    ) {
        return PageResponse.from(topicClusterService.getTopicClusters(ownerUserId, groupId, statuses, pageable));
    }

    // DTOs

    public record PipelineStatusResponse(
        boolean processorEnabled,
        long unprocessed,
        long queued,
        long processing,
        long classified,
        long skipped,
        long guideFound,
        long errors,
        long guidesTotal,
        long messagesWithGuide,
        long guideGenerationErrors,
        long classifiedTotal,
        long skippedTotal,
        long errorsTotal
    ) {}

    public record PipelineResultItem(
        Long id,
        Long groupId,
        String author,
        String text,
        String status,
        Double signalScore,
        Double classifierScore,
        String classifierReason,
        String classifierResultJson,
        String classificationContextHash,
        Long guideId,
        java.time.Instant messageDate,
        String signalBreakdown,
        String ruleResultJson,
        Integer guidePotentialScore,
        Integer problemSignalScore,
        Integer painScore,
        Integer urgencyScore,
        Integer willingnessToPayScore,
        Integer technicalDepthScore,
        Integer spamScore,
        String meaningSummary,
        String problemStatement,
        String solutionHint,
        String mentionedToolsJson,
        String mentionedPricesJson,
        String mentionedErrorsJson,
        String intelligenceReason,
        Boolean clusterCandidate,
        String embeddingStatus,
        String messageIntelligenceJson
    ) {}

    public record PipelineQueueItem(
        Long id,
        Long groupId,
        String groupTitle,
        String author,
        String text,
        String status,
        String topicName,
        java.time.Instant messageDate
    ) {}

    public record ClearQueueResponse(int cleared) {}

    public record ClearResultsResponse(int messagesReset, int traceMessagesCleared) {}

    public record RequeueResponse(int requeued) {}

    public record SmokeGuideResponse(Long messageId, String status, Long guideId, String guideTitle) {}

    private void resetForRequeue(MessageEntity message) {
        message.setProcessingStatus("UNPROCESSED");
        message.setSignalScore(null);
        clearIntelligence(message);
        message.setClassifierScore(null);
        message.setClassifierReason(null);
        message.setClassifierResultJson(null);
        message.setClassificationContextHash(null);
        message.setSignalBreakdown(null);
        message.setRuleResultJson(null);
        message.setGuideId(null);
    }

    private void clearIntelligence(MessageEntity message) {
        message.setGuidePotentialScore(null);
        message.setProblemSignalScore(null);
        message.setPainScore(null);
        message.setUrgencyScore(null);
        message.setWillingnessToPayScore(null);
        message.setTechnicalDepthScore(null);
        message.setSpamScore(null);
        message.setMeaningSummary(null);
        message.setProblemStatement(null);
        message.setSolutionHint(null);
        message.setMentionedToolsJson(null);
        message.setMentionedPricesJson(null);
        message.setMentionedErrorsJson(null);
        message.setIntelligenceReason(null);
        message.setClusterCandidate(false);
        message.setEmbeddingStatus("NONE");
        message.setMessageIntelligenceJson(null);
    }

    private List<Long> enabledGroupIds() {
        return enabledGroupIds(null);
    }

    private List<Long> enabledGroupIds(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return groupRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId).stream()
            .map(GroupEntity::getId)
            .toList();
    }

    private MessageEntity requireMessageForOwner(Long ownerUserId, Long messageId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return messageRepository.findByIdAndOwnerUserId(messageId, ownerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
    }

    private long countByStatus(List<Long> enabledGroupIds, String status) {
        if (enabledGroupIds.isEmpty()) {
            return 0;
        }
        return messageRepository.countByGroupIdInAndProcessingStatus(enabledGroupIds, status);
    }

    private long countByStatusInWindow(List<Long> enabledGroupIds, String status, Instant from, Instant to) {
        if (enabledGroupIds.isEmpty()) {
            return 0;
        }
        if (from != null && to != null) {
            return messageRepository.countByGroupIdInAndProcessingStatusAndMessageDateBetween(
                enabledGroupIds, status, from, to);
        }
        if (from != null) {
            return messageRepository.countByGroupIdInAndProcessingStatusAndMessageDateAfter(
                enabledGroupIds, status, from);
        }
        return messageRepository.countByGroupIdInAndProcessingStatus(enabledGroupIds, status);
    }

    private long countMessagesWithGuideInWindow(List<Long> enabledGroupIds, Instant from, Instant to) {
        if (enabledGroupIds.isEmpty()) {
            return 0;
        }
        if (from != null && to != null) {
            return messageRepository.countByGroupIdInAndGuideIdIsNotNullAndMessageDateBetween(
                enabledGroupIds, from, to);
        }
        if (from != null) {
            return messageRepository.countByGroupIdInAndGuideIdIsNotNullAndMessageDateAfter(
                enabledGroupIds, from);
        }
        return messageRepository.countByGroupIdInAndGuideIdIsNotNull(enabledGroupIds);
    }

    /**
     * Parse a date string as either a full ISO-8601 instant or a date-only value.
     * Date-only (e.g. "2026-06-08") is interpreted as start-of-day UTC for 'from'
     * and end-of-day UTC for 'to'.
     * Returns null if the input is null or blank.
     */
    private Instant parseDateOrInstant(String value, boolean isFrom) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        // Try full ISO-8601 instant first (e.g. 2026-06-08T14:30:00Z)
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {}

        // Try date-only (e.g. 2026-06-08)
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return isFrom
                ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                : date.atTime(23, 59, 59, 999999999).atZone(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {}

        throw new IllegalArgumentException(
            "Invalid date format: '" + trimmed + "'. Expected ISO-8601 date-time (2026-06-08T14:30:00Z) or date-only (2026-06-08).");
    }
}
