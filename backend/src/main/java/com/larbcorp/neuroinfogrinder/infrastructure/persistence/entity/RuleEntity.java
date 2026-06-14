package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rules")
public class RuleEntity extends BaseEntity {

    @Column(nullable = false)
    private Integer ruleOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conditionsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String actionsJson;

    @Column(length = 256)
    private String name = "Unnamed rule";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 32)
    private String actionType = "INCLUDE";

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";
}
