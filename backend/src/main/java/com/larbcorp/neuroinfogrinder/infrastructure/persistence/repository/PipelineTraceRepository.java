package com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PipelineTraceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PipelineTraceRepository extends JpaRepository<PipelineTraceEntity, Long> {

    List<PipelineTraceEntity> findByTraceIdOrderByStartedAtAsc(String traceId);

    List<PipelineTraceEntity> findByTraceIdAndOwnerUserIdOrderByStartedAtAsc(String traceId, Long ownerUserId);

    List<PipelineTraceEntity> findByMessageIdOrderByStartedAtAsc(Long messageId);

    List<PipelineTraceEntity> findByMessageIdAndOwnerUserIdOrderByStartedAtAsc(Long messageId, Long ownerUserId);

    List<PipelineTraceEntity> findByGroupIdOrderByStartedAtDesc(Long groupId);

    Page<PipelineTraceEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to, Pageable pageable);

    Page<PipelineTraceEntity> findByOwnerUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        Long ownerUserId,
        Instant from,
        Instant to,
        Pageable pageable
    );

    @Query("""
        select trace from PipelineTraceEntity trace
        where trace.createdAt between :from and :to
          and (:stage is null or trace.stage = :stage)
          and (:status is null or trace.status = :status)
          and (:classifierId is null or trace.classifierId = :classifierId)
          and (:promptId is null or trace.promptId = :promptId)
          and (:ruleId is null or trace.ruleId = :ruleId)
          and (:groupId is null or trace.groupId = :groupId)
          and (
            :problemOnly = false
            or trace.status in ('SKIPPED', 'REJECTED', 'FAILED')
            or (trace.score is not null and trace.score >= 0.20 and trace.score <= 0.85)
            or trace.errorMessage is not null
          )
        order by trace.createdAt desc
        """)
    Page<PipelineTraceEntity> findTuningCases(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("stage") String stage,
        @Param("status") String status,
        @Param("classifierId") Long classifierId,
        @Param("promptId") Long promptId,
        @Param("ruleId") Long ruleId,
        @Param("groupId") Long groupId,
        @Param("problemOnly") boolean problemOnly,
        Pageable pageable
    );

    @Query("""
        select trace from PipelineTraceEntity trace
        where trace.ownerUserId = :ownerUserId
          and trace.createdAt between :from and :to
          and (:stage is null or trace.stage = :stage)
          and (:status is null or trace.status = :status)
          and (:classifierId is null or trace.classifierId = :classifierId)
          and (:promptId is null or trace.promptId = :promptId)
          and (:ruleId is null or trace.ruleId = :ruleId)
          and (:groupId is null or trace.groupId = :groupId)
          and (
            :problemOnly = false
            or trace.status in ('SKIPPED', 'REJECTED', 'FAILED')
            or (trace.score is not null and trace.score >= 0.20 and trace.score <= 0.85)
            or trace.errorMessage is not null
          )
        order by trace.createdAt desc
        """)
    Page<PipelineTraceEntity> findTuningCasesForOwner(
        @Param("ownerUserId") Long ownerUserId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("stage") String stage,
        @Param("status") String status,
        @Param("classifierId") Long classifierId,
        @Param("promptId") Long promptId,
        @Param("ruleId") Long ruleId,
        @Param("groupId") Long groupId,
        @Param("problemOnly") boolean problemOnly,
        Pageable pageable
    );

    long countByStageAndStatus(String stage, String status);

    long countByOwnerUserIdAndStageAndStatus(Long ownerUserId, String stage, String status);

    long countByStage(String stage);

    long countByOwnerUserIdAndStage(Long ownerUserId, String stage);

    long countByStatus(String status);

    long countByOwnerUserIdAndStatus(Long ownerUserId, String status);

    @Query("SELECT COALESCE(SUM(e.inputTokens), 0) FROM PipelineTraceEntity e")
    long sumInputTokens();

    @Query("SELECT COALESCE(SUM(e.inputTokens), 0) FROM PipelineTraceEntity e WHERE e.ownerUserId = :ownerUserId")
    long sumInputTokensForOwner(@Param("ownerUserId") Long ownerUserId);

    @Query("SELECT COALESCE(SUM(e.outputTokens), 0) FROM PipelineTraceEntity e")
    long sumOutputTokens();

    @Query("SELECT COALESCE(SUM(e.outputTokens), 0) FROM PipelineTraceEntity e WHERE e.ownerUserId = :ownerUserId")
    long sumOutputTokensForOwner(@Param("ownerUserId") Long ownerUserId);

    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM PipelineTraceEntity e")
    double sumCostUsd();

    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM PipelineTraceEntity e WHERE e.ownerUserId = :ownerUserId")
    double sumCostUsdForOwner(@Param("ownerUserId") Long ownerUserId);

    void deleteByMessageIdIn(List<Long> messageIds);

    long deleteByCreatedAtBefore(Instant cutoff);

    long countByOwnerUserId(Long ownerUserId);

    @Modifying
    @Query("delete from PipelineTraceEntity trace where trace.ownerUserId = :ownerUserId")
    int deleteOwnedTraces(@Param("ownerUserId") Long ownerUserId);
}
