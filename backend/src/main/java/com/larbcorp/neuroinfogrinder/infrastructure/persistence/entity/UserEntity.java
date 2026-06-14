package com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(unique = true)
    private Long externalTelegramUserId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 8)
    private String languageCode = "ru";

    @Column(nullable = false, length = 64)
    private String timezone = "Europe/Moscow";

    @Column(length = 256)
    private String password;

    @Column(nullable = false, length = 32)
    private String role = "ADMIN";

    @Column(nullable = false)
    private Boolean hidden = false;
}
