package com.larbcorp.neuroinfogrinder.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret = "neuroinfogrinder-default-secret-key-must-be-at-least-256-bits-long-for-hs256";
    private long expirationMs = 86400000; // 24 hours
}
