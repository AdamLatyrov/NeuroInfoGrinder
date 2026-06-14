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
@Table(name = "ai_providers")
public class AiProviderEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String protocol;

    @Column(nullable = false, length = 512)
    private String endpointUrl;

    @Column(length = 512)
    private String apiKeyEncrypted;

    @Column(length = 64)
    private String model;

    @Column(nullable = false, length = 16)
    private String status = "DISABLED";

    private Instant lastTestedAt;

    @Column(length = 256)
    private String lastTestResult;

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
