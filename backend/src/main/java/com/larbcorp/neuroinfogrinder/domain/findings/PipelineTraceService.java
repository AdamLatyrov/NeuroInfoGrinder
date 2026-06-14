package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.FlowMetricsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTraceResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PipelineTraceEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PipelineTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PipelineTraceService {

    private final PipelineTraceRepository pipelineTraceRepository;
    private final PipelineEventBus pipelineEventBus;

    @Transactional(readOnly = true)
    public List<PipelineTraceResponse> getTrace(String traceId) {
        return pipelineTraceRepository.findByTraceIdOrderByStartedAtAsc(traceId).stream()
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
    public Page<PipelineTraceEntity> getTraces(Instant from, Instant to, Pageable pageable) {
        return pipelineTraceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable);
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
        PipelineTraceEntity entity = new PipelineTraceEntity();
        entity.setTraceId(traceId);
        entity.setMessageId(messageId);
        entity.setGroupId(groupId);
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
            entity.getReason()
        );
    }
}
