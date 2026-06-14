package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "pipeline_traces")
public class PipelineTraceEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String traceId;

    private Long messageId;

    private Long groupId;

    @Column(nullable = false, length = 64)
    private String stage;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String inputData;

    @Column(columnDefinition = "TEXT")
    private String outputData;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    private Long durationMs;

    private Long ruleId;

    private Long classifierId;

    private Long promptId;

    private Long providerId;

    @Column(length = 64)
    private String model;

    private Integer inputTokens = 0;

    private Integer outputTokens = 0;

    private Double costUsd = 0.0;

    private Double score;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
