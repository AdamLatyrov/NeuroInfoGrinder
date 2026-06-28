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
@Table(name = "telegram_backfill_jobs")
public class TelegramBackfillJobEntity extends BaseEntity {

    private Long telegramAccountId;

    private Long ownerUserId;

    @Column(name = "chat_id", nullable = false)
    private Long telegramChatId;

    private Long topicId;

    private Long fromMessageId;

    private Instant fromDate;

    private Instant toDate;

    private Long maxMessages;

    @Column(nullable = false)
    private Integer batchSize = 100;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    private Instant pausedUntil;

    private Integer floodWaitSeconds;

    @Column(nullable = false)
    private Long messagesFetched = 0L;

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
