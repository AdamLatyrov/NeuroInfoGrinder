package com.larbcorp.neuroinfogrinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI neuroInfoGrinderOpenApi(
            @Value("${spring.application.name}") String appName,
            @Value("${app.api-version}") String apiVersion
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(appName)
                        .version(apiVersion)
                        .description("REST API for NeuroInfoGrinder")
                        .contact(new Contact().name("LarbCorp")))
                .servers(List.of(new Server().url("/")));
    }
}
