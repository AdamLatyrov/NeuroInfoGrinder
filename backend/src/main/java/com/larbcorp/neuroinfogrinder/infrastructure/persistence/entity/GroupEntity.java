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
@Table(name = "groups")
public class GroupEntity extends BaseEntity {

    @Column(nullable = false)
    private Long telegramChatId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(length = 64)
    private String username;

    @Column(length = 64)
    private String category;

 @Column(nullable = false)
 private Boolean forum = false;

 @Column(nullable = false)
 private Boolean enabled = true;

    private Long accountId;

    private Long ownerUserId;

    private Long lastReadMessageId = 0L;

    private Instant lastReadAt;
}
