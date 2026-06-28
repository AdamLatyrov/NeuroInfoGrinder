package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "topic_cluster_guide_candidates")
public class TopicClusterGuideCandidateEntity extends BaseEntity {

    @Column(nullable = false)
    private Long clusterId;

    private Long guideId;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(nullable = false, length = 512)
    private String guideAngle;

    @Column(nullable = false, length = 32)
    private String contentType = "GUIDE";

    @Column(length = 64)
    private String contentSubtype;

    @Column(length = 512)
    private String topicLabel;

    @Column(columnDefinition = "TEXT")
    private String topicSummary;

    @Column(length = 512)
    private String contentTitle;

    @Column(columnDefinition = "TEXT")
    private String contentSummary;

    @Column(length = 256)
    private String normalizedTopicKey;

    private Integer contentQualityScore;

    private Integer importanceScore;

    private Integer actionabilityScore;

    private Integer noveltyScore;

    private Integer evidenceScore;

    private Integer riskScore;

    private Integer confidenceScore;

    private Integer noiseScore;

    @Column(columnDefinition = "TEXT")
    private String routingReason;

    @Column(length = 32)
    private String publicationKind;

    @Column(nullable = false)
    private Boolean shouldCreateMaterial = true;

    @Column(nullable = false)
    private Boolean shouldGenerateFullGuide = true;

    @Column(nullable = false, length = 64)
    private String safetyCategory = "normal";

    @Column(columnDefinition = "TEXT")
    private String whyThisCluster;

    @Column(columnDefinition = "TEXT")
    private String sourceMessageIdsJson;
}
