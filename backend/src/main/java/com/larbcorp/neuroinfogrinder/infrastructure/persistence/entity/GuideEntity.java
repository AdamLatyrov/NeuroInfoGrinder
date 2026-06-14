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
@Table(name = "guides")
public class GuideEntity extends BaseEntity {

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(nullable = false)
    private Long groupId;

    private Long rootMessageId;

    private Long providerId;

    @Column(length = 64)
    private String model;

    private Long classifierId;

    private Long promptId;

    @Column(length = 16)
    private String promptVersion;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    private Long duplicateOfId;

    private Double duplicateScore;

    private Double confidence;

    @Column(nullable = false)
    private Integer inputTokens = 0;

    @Column(nullable = false)
    private Integer outputTokens = 0;

    @Column(nullable = false)
    private Integer totalTokens = 0;

    @Column(nullable = false)
    private Double estimatedCostUsd = 0.0;

    @Column(columnDefinition = "TEXT")
    private String tagsJson;

    @Column(columnDefinition = "TEXT")
    private String generationError;

    private Instant publishedAt;
}
