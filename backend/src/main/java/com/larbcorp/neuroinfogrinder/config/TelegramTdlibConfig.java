package com.larbcorp.neuroinfogrinder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TdlibProperties.class)
public class TelegramTdlibConfig {
}
