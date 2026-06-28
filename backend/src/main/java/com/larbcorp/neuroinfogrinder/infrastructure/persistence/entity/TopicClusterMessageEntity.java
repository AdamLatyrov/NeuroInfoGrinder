package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "topic_cluster_messages")
public class TopicClusterMessageEntity extends BaseEntity {

    @Column(nullable = false)
    private Long clusterId;

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false, length = 32)
    private String role = "evidence";

    private Double contributionScore;
}
