package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.shared.dto.SystemInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.api-version}")
    private String apiVersion;

    @GetMapping("/info")
    public SystemInfoResponse info() {
        return new SystemInfoResponse(applicationName, apiVersion, "ok");
    }
}
