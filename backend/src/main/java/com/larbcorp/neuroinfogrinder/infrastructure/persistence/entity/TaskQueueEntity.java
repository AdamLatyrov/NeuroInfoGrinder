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
@Table(name = "task_queue")
public class TaskQueueEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String taskType;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false, length = 16)
    private String status = "QUEUED";

    @Column(length = 32)
    private String inputRefType;

    private Long inputRefId;

    private Long resultRefId;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false)
    private Integer maxRetries = 3;

    @Column(length = 1024)
    private String errorMessage;

    private Instant startedAt;

    private Instant completedAt;
}
