package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "classifiers")
public class ClassifierEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String type;

    private Long providerId;

    private Long promptId;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String regexPattern;

    @Column(columnDefinition = "TEXT")
    private String modelConfigJson;

    @Column(nullable = false, length = 16)
    private String version = "1.0";

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(nullable = false)
    private Integer classifierOrder = 100;
}
