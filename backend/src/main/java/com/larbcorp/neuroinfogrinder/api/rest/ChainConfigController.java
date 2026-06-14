package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.ChainConfigService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.ChainConfigResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateChainConfigRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ChainConfigEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chain-config")
@RequiredArgsConstructor
public class ChainConfigController {

    private final ChainConfigService chainConfigService;

    @GetMapping
    public ChainConfigResponse getConfig() {
        return toResponse(chainConfigService.getConfig());
    }

    @PutMapping
    public ChainConfigResponse updateConfig(@RequestBody UpdateChainConfigRequest request) {
        return toResponse(chainConfigService.updateConfig(request));
    }

    private ChainConfigResponse toResponse(ChainConfigEntity entity) {
        return new ChainConfigResponse(
            entity.getIncludeReplies(),
            entity.getTimeWindowMinutes(),
            entity.getMinMessagesForProcessing(),
            entity.getMaxMessagesPerChain()
        );
    }
}
