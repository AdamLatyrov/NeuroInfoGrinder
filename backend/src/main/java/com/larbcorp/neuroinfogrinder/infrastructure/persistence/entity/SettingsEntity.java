package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "settings")
public class SettingsEntity extends BaseEntity {

    private Long publicationTargetGroupId;

    private Long activeProviderId;

    @Column(nullable = false, length = 32)
    private String publicationMode = "WITH_MODERATION";

    @Column(nullable = false, length = 32)
    private String processingMode = "NEW_ONLY";

    @Column(nullable = false)
    private Integer pollIntervalSeconds = 30;

    @Column(nullable = false)
    private Boolean chainIncludeReplies = true;

    @Column(nullable = false)
    private Integer chainTimeWindowMinutes = 5;

    @Column(nullable = false)
    private Integer chainMinMessages = 2;

    @Column(nullable = false)
    private Integer chainMaxMessages = 20;

    @Column(nullable = false)
    private Boolean filterSkipBots = true;

    @Column(nullable = false)
    private Integer filterMinMessageLength = 12;

    @Column(columnDefinition = "TEXT")
    private String filterBlacklistWords;

    @Column(nullable = false)
    private Long limitDailyTokenLimit = 50000000L;

    @Column(nullable = false)
    private Long limitMonthlyTokenLimit = 500000000L;

    @Column(nullable = false)
    private Integer limitAlertThresholdPct = 80;

    @Column(length = 128)
    private String notificationTelegramChat;

    @Column(length = 512)
    private String notificationWebhookUrl;
}
