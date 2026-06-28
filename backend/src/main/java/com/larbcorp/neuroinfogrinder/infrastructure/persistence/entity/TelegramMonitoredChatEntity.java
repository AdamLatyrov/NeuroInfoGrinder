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
@Table(name = "telegram_monitored_chats")
public class TelegramMonitoredChatEntity extends BaseEntity {

    private Long telegramAccountId;

    private Long ownerUserId;

    @Column(name = "chat_id", nullable = false)
    private Long telegramChatId;

    @Column(nullable = false, length = 256)
    private String chatTitle;

    @Column(length = 64)
    private String chatType;

    private Long topicId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Boolean liveIngestionEnabled = true;

    @Column(nullable = false)
    private Boolean backfillEnabled = false;

    private Long lastLiveMessageId;

    private Instant lastLiveMessageAt;

    private Long lastBackfillMessageId;

    @Column(nullable = false, length = 32)
    private String backfillStatus = "IDLE";

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
