package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "guide_publication_settings")
public class GuidePublicationSettingsEntity extends BaseEntity {

    private Long ownerUserId;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(nullable = false, length = 32)
    private String mode = "TDLIB_ACCOUNT";

    private Long targetGroupId;

    private Long targetTelegramChatId;

    private Long targetTopicId;

    @Column(nullable = false)
    private Boolean appendSourceLink = true;

    @Column(nullable = false)
    private Double minConfidence = 0.85;

    @Column(nullable = false, length = 256)
    private String sendOnlyStatuses = "DRAFT,APPROVED";

    @Column(nullable = false, length = 32)
    private String format = "PLAIN_TEXT";
}
