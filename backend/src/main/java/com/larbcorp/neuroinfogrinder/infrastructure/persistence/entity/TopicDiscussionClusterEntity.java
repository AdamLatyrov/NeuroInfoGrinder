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
@Table(name = "topic_discussion_clusters")
public class TopicDiscussionClusterEntity extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    private Long ownerUserId;

    private Long telegramTopicId;

    @Column(length = 256)
    private String topicTitle;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false, length = 32)
    private String status = "OPEN";

    @Column(length = 512)
    private String topicLabel;

    @Column(columnDefinition = "TEXT")
    private String topicSummary;

    @Column(nullable = false, length = 128)
    private String semanticHash;

    private Integer guidePotentialScore;

    private Integer problemSignalScore;

    @Column(length = 64)
    private String safetyCategory = "normal";

    @Column(nullable = false, length = 32)
    private String classificationStatus = "PENDING";

    @Column(nullable = false, length = 32)
    private String guideGenerationStatus = "NONE";

    private Long classifierId;

    private Double classifierScore;

    @Column(columnDefinition = "TEXT")
    private String classifierResultJson;

    private Long guideId;
}
