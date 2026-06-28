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
@Table(name = "guide_publication_log")
public class GuidePublicationLogEntity extends BaseEntity {

    private Long ownerUserId;

    @Column(nullable = false)
    private Long guideId;

    @Column(nullable = false, length = 32)
    private String targetMode = "TDLIB_ACCOUNT";

    private Long targetGroupId;

    private Long targetTelegramChatId;

    private Long targetTopicId;

    @Column(nullable = false, length = 16)
    private String status;

    private Long telegramMessageId;

    @Column(columnDefinition = "TEXT")
    private String error;

    private Instant sentAt;
}
