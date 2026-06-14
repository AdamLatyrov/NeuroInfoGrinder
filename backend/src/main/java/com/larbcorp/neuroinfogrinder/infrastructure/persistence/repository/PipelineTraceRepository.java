package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PipelineTraceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PipelineTraceRepository extends JpaRepository<PipelineTraceEntity, Long> {

    List<PipelineTraceEntity> findByTraceIdOrderByStartedAtAsc(String traceId);

    List<PipelineTraceEntity> findByMessageIdOrderByStartedAtAsc(Long messageId);

    List<PipelineTraceEntity> findByGroupIdOrderByStartedAtDesc(Long groupId);

    Page<PipelineTraceEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to, Pageable pageable);

    long countByStageAndStatus(String stage, String status);

    long countByStage(String stage);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(e.inputTokens), 0) FROM PipelineTraceEntity e")
    long sumInputTokens();

    @Query("SELECT COALESCE(SUM(e.outputTokens), 0) FROM PipelineTraceEntity e")
    long sumOutputTokens();

    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM PipelineTraceEntity e")
    double sumCostUsd();

    void deleteByMessageIdIn(List<Long> messageIds);
}
