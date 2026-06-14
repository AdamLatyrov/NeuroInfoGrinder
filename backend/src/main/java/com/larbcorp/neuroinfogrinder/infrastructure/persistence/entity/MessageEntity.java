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
@Table(name = "messages")
public class MessageEntity extends BaseEntity {

    @Column(nullable = false)
    private Long telegramMessageId;

    @Column(nullable = false)
    private Long groupId;

    @Column(length = 128)
    private String senderName;

    private Long senderTelegramUserId;

    @Column(nullable = false)
    private Boolean isBot = false;

    @Column(columnDefinition = "TEXT")
    private String text;

    private Long replyToMessageId;

    @Column(nullable = false)
    private Integer replyCount = 0;

    @Column(nullable = false, length = 32)
    private String processingStatus = "UNPROCESSED";

    @Column(length = 256)
    private String topicName;

    private Long topicId;

    private Long guideId;

    /** Signal scorer result (heuristic), 0.0–1.0. Null if not yet scored. */
    private Double signalScore;

    /** Classifier result (LLM/keyword), 0.0–1.0. Null if not yet classified. */
    private Double classifierScore;

    /** Reason from classifier (why it passed/failed). */
    @Column(length = 1024)
    private String classifierReason;

    /** Signal scorer breakdown as JSON: {"length":0.15,"replies":0.0,...}. */
    @Column(columnDefinition = "TEXT")
    private String signalBreakdown;

    /** Rule evaluation result as JSON. */
    @Column(columnDefinition = "TEXT")
    private String ruleResultJson;

    @Column(nullable = false)
    private Instant messageDate;
}
