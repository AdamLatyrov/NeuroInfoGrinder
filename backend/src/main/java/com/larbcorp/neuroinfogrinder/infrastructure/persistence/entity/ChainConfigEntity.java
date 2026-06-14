package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "chain_config")
public class ChainConfigEntity extends BaseEntity {

    @Column(nullable = false)
    private Boolean includeReplies = true;

    @Column(nullable = false)
    private Integer timeWindowMinutes = 5;

    @Column(nullable = false)
    private Integer minMessagesForProcessing = 2;

    @Column(nullable = false)
    private Integer maxMessagesPerChain = 20;
}
