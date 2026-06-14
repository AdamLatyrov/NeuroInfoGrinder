package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineService;
import com.larbcorp.neuroinfogrinder.domain.findings.QueueProcessor;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * REST API for pipeline control and results inspection.
 */
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final QueueProcessor queueProcessor;
    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final PipelineTraceRepository pipelineTraceRepository;

    // ── Queue control ──

    /** Process a single message immediately by ID. */
    @PostMapping("/process/{messageId}")
    @ResponseStatus(HttpStatus.OK)
    public void processMessage(@PathVariable Long messageId) {
        pipelineService.processMessage(messageId);
    }

    /** Manually drain up to N messages from the queue. */
    @PostMapping("/process-queue")
    @ResponseStatus(HttpStatus.OK)
    public void processQueue(@RequestParam(defaultValue = "10") int limit) {
        List<Long> enabledGroupIds = enabledGroupIds();
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

    @PostMapping("/requeue")
    @ResponseStatus(HttpStatus.OK)
    public RequeueResponse requeue(
        @RequestParam(required = false) List<String> statuses
    ) {
        List<Long> enabledGroupIds = enabledGroupIds();
        if (enabledGroupIds.isEmpty()) {
            return new RequeueResponse(0);
        }

        List<String> effectiveStatuses = (statuses == null || statuses.isEmpty())
            ? List.of("SKIPPED", "CLASSIFIED", "CLEARED")
            : statuses;

        List<MessageEntity> messages = messageRepository.findByGroupIdInAndProcessingStatusIn(
            enabledGroupIds,
            effectiveStatuses
        );

        messages.forEach(message -> {
            message.setProcessingStatus("UNPROCESSED");
            message.setSignalScore(null);
            message.setClassifierScore(null);
            message.setClassifierReason(null);
            message.setSignalBreakdown(null);
            message.setRuleResultJson(null);
            message.setGuideId(null);
        });
        messageRepository.saveAll(messages);
        return new RequeueResponse(messages.size());
    }

    @PostMapping("/dev/smoke-guide")
    public SmokeGuideResponse smokeGuide() {
        GroupEntity group = groupRepository.findByEnabledTrue().stream()
            .findFirst()
            .orElseGet(() -> {
                GroupEntity created = new GroupEntity();
                created.setTelegramChatId(-3001L);
                created.setTitle("Dev Smoke Group");
                created.setUsername("dev_smoke");
                created.setCategory("dev");
                created.setForum(false);
                created.setEnabled(true);
                created.setLastReadMessageId(0L);
                return groupRepository.save(created);
            });

        long telegramMessageId = System.currentTimeMillis();
        MessageEntity message = new MessageEntity();
        message.setTelegramMessageId(telegramMessageId);
        message.setGroupId(group.getId());
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
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        List<Long> enabledGroupIds = enabledGroupIds();
        Instant fromInstant = parseDateOrInstant(from, true);
        Instant toInstant = parseDateOrInstant(to, false);

        long unprocessed = countByStatus(enabledGroupIds, "UNPROCESSED");
        long queued = countByStatus(enabledGroupIds, "QUEUED");
        long processing = countByStatus(enabledGroupIds, "PROCESSING");
        long classified = countByStatusInWindow(enabledGroupIds, "CLASSIFIED", fromInstant, toInstant);
        long skipped = countByStatusInWindow(enabledGroupIds, "SKIPPED", fromInstant, toInstant);
        long guideFound = countByStatusInWindow(enabledGroupIds, "GUIDE_FOUND", fromInstant, toInstant);

        return new PipelineStatusResponse(
            queueProcessor.isEnabled(),
            unprocessed, queued, processing, classified, skipped, guideFound
        );
    }

    @GetMapping("/queue")
    public PageResponse<PipelineQueueItem> getQueue(
        @RequestParam(required = false) List<String> statuses,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Pageable pageable
    ) {
        List<Long> enabledGroupIds = enabledGroupIds();
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
        @RequestParam(required = false) List<String> statuses
    ) {
        List<Long> enabledGroupIds = enabledGroupIds();
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
    public ClearResultsResponse clearResults() {
        List<MessageEntity> processed = messageRepository.findAll().stream()
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
            message.setClassifierScore(null);
            message.setClassifierReason(null);
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
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        Pageable pageable
    ) {
        List<Long> enabledGroupIds = enabledGroupIds();
        if (enabledGroupIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        List<String> pipelineStatuses = status != null
            ? List.of(status)
            : List.of("UNPROCESSED", "QUEUED", "PROCESSING", "CLASSIFIED", "SKIPPED", "GUIDE_FOUND");

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
            msg.getGuideId(),
            msg.getMessageDate(),
            msg.getSignalBreakdown(),
            msg.getRuleResultJson()
        )));
    }

    // ── DTOs ──

    public record PipelineStatusResponse(
        boolean processorEnabled,
        long unprocessed,
        long queued,
        long processing,
        long classified,
        long skipped,
        long guideFound
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
        Long guideId,
        java.time.Instant messageDate,
        String signalBreakdown,
        String ruleResultJson
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

    private List<Long> enabledGroupIds() {
        return groupRepository.findByEnabledTrue().stream()
            .map(GroupEntity::getId)
            .toList();
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
