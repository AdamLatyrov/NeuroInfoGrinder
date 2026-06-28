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
@Table(name = "telegram_accounts")
public class TelegramAccountEntity extends BaseEntity {

    private Long ownerUserId;

    private Long telegramUserId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 128)
    private String firstName;

    @Column(length = 128)
    private String lastName;

    @Column(nullable = false, length = 32)
    private String status = "DISCONNECTED";

    @Column(length = 16)
    private String proxyType;

    @Column(length = 256)
    private String proxyHost;

    private Integer proxyPort;

    @Column(length = 128)
    private String proxyUsername;

    @Column(length = 256)
    private String proxyPasswordEncrypted;

    private Instant lastSyncAt;

    @Column(length = 512)
    private String lastError;
}
