package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.FlowMetricsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTraceResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTuningCaseResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PipelineTraceEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.MessageRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTraceService {

    private static final int PREVIEW_MAX_LEN = 600;
    private static final int EXPORT_MAX_LIMIT = 500;

    private final PipelineTraceRepository pipelineTraceRepository;
    private final PipelineEventBus pipelineEventBus;
    private final MessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    @Value("${neuroinfogrinder.pipeline.trace-retention-days:45}")
    private int traceRetentionDays;

    @Transactional(readOnly = true)
    public List<PipelineTraceResponse> getTrace(String traceId) {
        return pipelineTraceRepository.findByTraceIdOrderByStartedAtAsc(traceId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineTraceResponse> getTrace(Long ownerUserId, String traceId) {
        if (ownerUserId == null) {
            return getTrace(traceId);
        }
        return pipelineTraceRepository.findByTraceIdAndOwnerUserIdOrderByStartedAtAsc(traceId, ownerUserId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineTraceResponse> getTraceByMessage(Long messageId) {
        return pipelineTraceRepository.findByMessageIdOrderByStartedAtAsc(messageId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineTraceResponse> getTraceByMessage(Long ownerUserId, Long messageId) {
        if (ownerUserId == null) {
            return getTraceByMessage(messageId);
        }
        return pipelineTraceRepository.findByMessageIdAndOwnerUserIdOrderByStartedAtAsc(messageId, ownerUserId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<PipelineTraceEntity> getTraces(Instant from, Instant to, Pageable pageable) {
        return pipelineTraceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PipelineTraceEntity> getTraces(Long ownerUserId, Instant from, Instant to, Pageable pageable) {
        if (ownerUserId == null) {
            return getTraces(from, to, pageable);
        }
        return pipelineTraceRepository.findByOwnerUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(ownerUserId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PipelineTuningCaseResponse> getTuningCases(
        Long ownerUserId,
        Instant from,
        Instant to,
        String stage,
        String status,
        Long classifierId,
        Long promptId,
        Long ruleId,
        Long groupId,
        boolean problemOnly,
        Pageable pageable
    ) {
        if (ownerUserId == null) {
            return getTuningCases(from, to, stage, status, classifierId, promptId, ruleId, groupId, problemOnly, pageable);
        }
        Page<PipelineTraceEntity> page = pipelineTraceRepository.findTuningCasesForOwner(
            ownerUserId,
            from,
            to,
            blankToNull(stage),
            blankToNull(status),
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            pageable
        );
        Map<Long, MessageEntity> messages = loadMessages(ownerUserId, page.getContent());
        List<PipelineTuningCaseResponse> content = page.getContent().stream()
            .map(trace -> toTuningCase(trace, trace.getMessageId() != null ? messages.get(trace.getMessageId()) : null))
            .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public String exportTuningCases(
        Long ownerUserId,
        Instant from,
        Instant to,
        String stage,
        String status,
        Long classifierId,
        Long promptId,
        Long ruleId,
        Long groupId,
        boolean problemOnly,
        int limit
    ) {
        if (ownerUserId == null) {
            return exportTuningCases(from, to, stage, status, classifierId, promptId, ruleId, groupId, problemOnly, limit);
        }
        int safeLimit = Math.max(1, Math.min(limit, EXPORT_MAX_LIMIT));
        Page<PipelineTuningCaseResponse> page = getTuningCases(
            ownerUserId,
            from,
            to,
            stage,
            status,
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            PageRequest.of(0, safeLimit)
        );
        return page.getContent().stream()
            .map(this::toJsonLine)
            .collect(Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public FlowMetricsResponse getFlowMetrics(Long ownerUserId) {
        if (ownerUserId == null) {
            return getFlowMetrics();
        }
        return new FlowMetricsResponse(
            pipelineTraceRepository.countByOwnerUserIdAndStage(ownerUserId, "TELEGRAM_READ"),
            pipelineTraceRepository.countByOwnerUserIdAndStageAndStatus(ownerUserId, "RULES", "PASSED"),
            pipelineTraceRepository.countByOwnerUserIdAndStage(ownerUserId, "CLASSIFICATION"),
            pipelineTraceRepository.countByOwnerUserIdAndStage(ownerUserId, "GUIDE_GENERATION"),
            pipelineTraceRepository.countByOwnerUserIdAndStageAndStatus(ownerUserId, "GUIDE_GENERATION", "COMPLETED"),
            pipelineTraceRepository.countByOwnerUserIdAndStage(ownerUserId, "MODERATION"),
            pipelineTraceRepository.countByOwnerUserIdAndStageAndStatus(ownerUserId, "AUTO_PUBLISH", "COMPLETED"),
            pipelineTraceRepository.countByOwnerUserIdAndStageAndStatus(ownerUserId, "AUTO_PUBLISH", "REJECTED"),
            pipelineTraceRepository.countByOwnerUserIdAndStatus(ownerUserId, "FAILED"),
            pipelineTraceRepository.sumInputTokensForOwner(ownerUserId),
            pipelineTraceRepository.sumOutputTokensForOwner(ownerUserId),
            pipelineTraceRepository.sumCostUsdForOwner(ownerUserId)
        );
    }

    @Transactional(readOnly = true)
    public Page<PipelineTuningCaseResponse> getTuningCases(
        Instant from,
        Instant to,
        String stage,
        String status,
        Long classifierId,
        Long promptId,
        Long ruleId,
        Long groupId,
        boolean problemOnly,
        Pageable pageable
    ) {
        Page<PipelineTraceEntity> page = pipelineTraceRepository.findTuningCases(
            from,
            to,
            blankToNull(stage),
            blankToNull(status),
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            pageable
        );
        Map<Long, MessageEntity> messages = loadMessages(page.getContent());
        List<PipelineTuningCaseResponse> content = page.getContent().stream()
            .map(trace -> toTuningCase(trace, trace.getMessageId() != null ? messages.get(trace.getMessageId()) : null))
            .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public String exportTuningCases(
        Instant from,
        Instant to,
        String stage,
        String status,
        Long classifierId,
        Long promptId,
        Long ruleId,
        Long groupId,
        boolean problemOnly,
        int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, EXPORT_MAX_LIMIT));
        Page<PipelineTuningCaseResponse> page = getTuningCases(
            from,
            to,
            stage,
            status,
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            PageRequest.of(0, safeLimit)
        );
        return page.getContent().stream()
            .map(this::toJsonLine)
            .collect(Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public FlowMetricsResponse getFlowMetrics() {
        return new FlowMetricsResponse(
            pipelineTraceRepository.countByStage("TELEGRAM_READ"),
            pipelineTraceRepository.countByStageAndStatus("RULES", "PASSED"),
            pipelineTraceRepository.countByStage("CLASSIFICATION"),
            pipelineTraceRepository.countByStage("GUIDE_GENERATION"),
            pipelineTraceRepository.countByStageAndStatus("GUIDE_GENERATION", "COMPLETED"),
            pipelineTraceRepository.countByStage("MODERATION"),
            pipelineTraceRepository.countByStageAndStatus("AUTO_PUBLISH", "COMPLETED"),
            pipelineTraceRepository.countByStageAndStatus("AUTO_PUBLISH", "REJECTED"),
            pipelineTraceRepository.countByStatus("FAILED"),
            pipelineTraceRepository.sumInputTokens(),
            pipelineTraceRepository.sumOutputTokens(),
            pipelineTraceRepository.sumCostUsd()
        );
    }

    @Transactional
    public PipelineTraceEntity createTrace(
            String traceId, Long messageId, Long groupId,
            String stage, String status,
            Instant startedAt, Instant finishedAt, Long durationMs,
            String inputData, String outputData, String errorMessage,
            Long ruleId, Long classifierId, Long promptId, Long providerId,
            String model, Integer inputTokens, Integer outputTokens,
            Double costUsd, Double score, Double confidence, String reason) {
        return createTrace(
            traceId,
            messageId,
            groupId,
            stage,
            status,
            startedAt,
            finishedAt,
            durationMs,
            inputData,
            outputData,
            errorMessage,
            ruleId,
            classifierId,
            promptId,
            providerId,
            model,
            inputTokens,
            outputTokens,
            costUsd,
            score,
            confidence,
            reason,
            null
        );
    }

    @Transactional
    public PipelineTraceEntity createTrace(
            String traceId, Long messageId, Long groupId,
            String stage, String status,
            Instant startedAt, Instant finishedAt, Long durationMs,
            String inputData, String outputData, String errorMessage,
            Long ruleId, Long classifierId, Long promptId, Long providerId,
            String model, Integer inputTokens, Integer outputTokens,
            Double costUsd, Double score, Double confidence, String reason,
            TraceMetadata metadata) {
        PipelineTraceEntity entity = new PipelineTraceEntity();
        entity.setTraceId(traceId);
        entity.setMessageId(messageId);
        entity.setGroupId(groupId);
        entity.setOwnerUserId(resolveOwnerUserId(messageId, groupId));
        entity.setStage(stage);
        entity.setStatus(status);
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(finishedAt);
        entity.setDurationMs(durationMs);
        entity.setInputData(inputData);
        entity.setOutputData(outputData);
        entity.setErrorMessage(errorMessage);
        entity.setRuleId(ruleId);
        entity.setClassifierId(classifierId);
        entity.setPromptId(promptId);
        entity.setProviderId(providerId);
        entity.setModel(model);
        entity.setInputTokens(inputTokens != null ? inputTokens : 0);
        entity.setOutputTokens(outputTokens != null ? outputTokens : 0);
        entity.setCostUsd(costUsd != null ? costUsd : 0.0);
        entity.setScore(score);
        entity.setConfidence(confidence);
        entity.setReason(reason);
        applyMetadata(entity, metadata);
        PipelineTraceEntity saved = pipelineTraceRepository.save(entity);
        PipelineEventBus.PipelineEvent event = new PipelineEventBus.PipelineEvent(
            "TRACE_CREATED",
            messageId,
            groupId,
            stage,
            status
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pipelineEventBus.publish(event);
                }
            });
        } else {
            pipelineEventBus.publish(event);
        }
        return saved;
    }

    @Scheduled(cron = "${neuroinfogrinder.pipeline.trace-retention-cleanup-cron:0 20 3 * * *}")
    @Transactional
    public void cleanupOldTraces() {
        if (traceRetentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(traceRetentionDays));
        long deleted = pipelineTraceRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} pipeline traces older than {} days", deleted, traceRetentionDays);
        }
    }

    public PipelineTraceResponse toResponse(PipelineTraceEntity entity) {
        return new PipelineTraceResponse(
            entity.getId(),
            entity.getTraceId(),
            entity.getMessageId(),
            entity.getGroupId(),
            entity.getStage(),
            entity.getStatus(),
            entity.getInputData(),
            entity.getOutputData(),
            entity.getErrorMessage(),
            entity.getStartedAt(),
            entity.getFinishedAt(),
            entity.getDurationMs(),
            entity.getRuleId(),
            entity.getClassifierId(),
            entity.getPromptId(),
            entity.getProviderId(),
            entity.getModel(),
            entity.getInputTokens(),
            entity.getOutputTokens(),
            entity.getCostUsd(),
            entity.getScore(),
            entity.getConfidence(),
            entity.getReason(),
            entity.getEntityType(),
            entity.getEntityName(),
            entity.getEntityVersion(),
            entity.getConfigSnapshotJson(),
            entity.getTuningHint()
        );
    }

    public record TraceMetadata(
        String entityType,
        String entityName,
        String entityVersion,
        String configSnapshotJson,
        String tuningHint
    ) {}

    private Map<Long, MessageEntity> loadMessages(List<PipelineTraceEntity> traces) {
        List<Long> messageIds = traces.stream()
            .map(PipelineTraceEntity::getMessageId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findAllById(messageIds).stream()
            .collect(Collectors.toMap(MessageEntity::getId, Function.identity()));
    }

    private Map<Long, MessageEntity> loadMessages(Long ownerUserId, List<PipelineTraceEntity> traces) {
        if (ownerUserId == null) {
            return loadMessages(traces);
        }
        List<Long> messageIds = traces.stream()
            .map(PipelineTraceEntity::getMessageId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findAllById(messageIds).stream()
            .filter(message -> Objects.equals(ownerUserId, message.getOwnerUserId()))
            .collect(Collectors.toMap(MessageEntity::getId, Function.identity()));
    }

    private Long resolveOwnerUserId(Long messageId, Long groupId) {
        if (messageId != null) {
            Long ownerUserId = messageRepository.findById(messageId)
                .map(MessageEntity::getOwnerUserId)
                .orElse(null);
            if (ownerUserId != null) {
                return ownerUserId;
            }
        }
        if (groupId != null) {
            return groupRepository.findById(groupId)
                .map(group -> group.getOwnerUserId())
                .orElse(null);
        }
        return null;
    }

    private PipelineTuningCaseResponse toTuningCase(PipelineTraceEntity trace, MessageEntity message) {
        String tuningHint = firstNonBlank(trace.getTuningHint(), inferTuningHint(trace, message));
        return new PipelineTuningCaseResponse(
            trace.getId(),
            trace.getTraceId(),
            trace.getCreatedAt(),
            trace.getStage(),
            trace.getStatus(),
            trace.getEntityType(),
            trace.getEntityName(),
            trace.getEntityVersion(),
            trace.getMessageId(),
            trace.getGroupId(),
            message != null ? message.getMessageDate() : null,
            message != null ? message.getProcessingStatus() : null,
            message != null ? message.getGuideId() : null,
            message != null ? preview(message.getText()) : null,
            message != null ? message.getText() : null,
            trace.getRuleId(),
            trace.getClassifierId(),
            trace.getPromptId(),
            trace.getProviderId(),
            trace.getModel(),
            trace.getInputTokens(),
            trace.getOutputTokens(),
            trace.getCostUsd(),
            trace.getScore(),
            trace.getConfidence(),
            message != null ? message.getGuidePotentialScore() : null,
            message != null ? message.getProblemSignalScore() : null,
            message != null ? message.getPainScore() : null,
            message != null ? message.getUrgencyScore() : null,
            message != null ? message.getWillingnessToPayScore() : null,
            message != null ? message.getTechnicalDepthScore() : null,
            message != null ? message.getSpamScore() : null,
            trace.getReason(),
            trace.getErrorMessage(),
            tuningHint,
            buildAgentFocus(trace, message, tuningHint),
            trace.getInputData(),
            trace.getOutputData(),
            trace.getConfigSnapshotJson(),
            message != null ? message.getRuleResultJson() : null,
            message != null ? message.getSignalBreakdown() : null,
            message != null ? message.getClassifierResultJson() : null,
            message != null ? message.getMessageIntelligenceJson() : null
        );
    }

    private List<String> buildAgentFocus(PipelineTraceEntity trace, MessageEntity message, String tuningHint) {
        List<String> focus = new ArrayList<>();
        focus.add("stage:" + trace.getStage());
        if (trace.getStatus() != null) {
            focus.add("status:" + trace.getStatus());
        }
        if (trace.getEntityType() != null) {
            focus.add("entity:" + trace.getEntityType());
        }
        if (trace.getClassifierId() != null) {
            focus.add("classifier:" + trace.getClassifierId());
        }
        if (trace.getPromptId() != null) {
            focus.add("prompt:" + trace.getPromptId());
        }
        if (trace.getRuleId() != null) {
            focus.add("rule:" + trace.getRuleId());
        }
        if (trace.getErrorMessage() != null && !trace.getErrorMessage().isBlank()) {
            focus.add("inspect:error_message");
        }
        if (trace.getScore() != null && trace.getScore() >= 0.20 && trace.getScore() <= 0.85) {
            focus.add("inspect:borderline_score");
        }
        if (message != null && message.getSpamScore() != null && message.getSpamScore() >= 70) {
            focus.add("inspect:spam_score");
        }
        if (message != null && message.getGuidePotentialScore() != null && message.getGuidePotentialScore() < 60) {
            focus.add("inspect:guide_potential");
        }
        if (tuningHint != null && !tuningHint.isBlank()) {
            focus.add("inspect:tuning_hint");
        }
        if (trace.getConfigSnapshotJson() != null && !trace.getConfigSnapshotJson().isBlank()) {
            focus.add("inspect:config_snapshot");
        }
        return List.copyOf(focus);
    }

    private String inferTuningHint(PipelineTraceEntity trace, MessageEntity message) {
        if ("RULES".equals(trace.getStage()) && ("REJECTED".equals(trace.getStatus()) || "SKIPPED".equals(trace.getStatus()))) {
            return "Review active rule conditions/actionType if this was a false rejection.";
        }
        if ("SIGNAL_SCORING".equals(trace.getStage()) && "SKIPPED".equals(trace.getStatus())) {
            return "If the message is useful, tune signal threshold or scorer features; inspect signalBreakdown.";
        }
        if ("CLASSIFICATION".equals(trace.getStage()) && "SKIPPED".equals(trace.getStatus())) {
            return "Inspect classifier output and current config snapshot; tune keywords, regex, linear weights, or prompt.";
        }
        if ("GUIDE_GENERATION".equals(trace.getStage()) && "FAILED".equals(trace.getStatus())) {
            return "Inspect provider/model, prompt snapshot, raw output, and error before retrying generation.";
        }
        if (message != null && "SKIPPED".equals(message.getProcessingStatus())) {
            return "Skipped message: inspect ruleResultJson, signalBreakdown, and classifierResultJson before changing thresholds.";
        }
        if (trace.getScore() != null && trace.getScore() >= 0.20 && trace.getScore() <= 0.85) {
            return "Borderline score: useful sample for threshold, prompt, or linear model calibration.";
        }
        return null;
    }

    private void applyMetadata(PipelineTraceEntity entity, TraceMetadata metadata) {
        if (metadata == null) {
            return;
        }
        entity.setEntityType(truncate(metadata.entityType(), 32));
        entity.setEntityName(truncate(metadata.entityName(), 256));
        entity.setEntityVersion(truncate(metadata.entityVersion(), 32));
        entity.setConfigSnapshotJson(metadata.configSnapshotJson());
        entity.setTuningHint(metadata.tuningHint());
    }

    private String toJsonLine(PipelineTuningCaseResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize tuning case", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String preview(String text) {
        if (text == null) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return truncate(compact, PREVIEW_MAX_LEN);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
