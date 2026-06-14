package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateChainConfigRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ChainConfigEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ChainConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChainConfigService {

    private final ChainConfigRepository chainConfigRepository;

    @Transactional(readOnly = true)
    public ChainConfigEntity getConfig() {
        return chainConfigRepository.findAll().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Chain config not found"));
    }

    @Transactional
    public ChainConfigEntity updateConfig(UpdateChainConfigRequest request) {
        ChainConfigEntity entity = getConfig();

        if (request.includeReplies() != null) {
            entity.setIncludeReplies(request.includeReplies());
        }
        if (request.timeWindowMinutes() != null) {
            entity.setTimeWindowMinutes(request.timeWindowMinutes());
        }
        if (request.minMessagesForProcessing() != null) {
            entity.setMinMessagesForProcessing(request.minMessagesForProcessing());
        }
        if (request.maxMessagesPerChain() != null) {
            entity.setMaxMessagesPerChain(request.maxMessagesPerChain());
        }

        return chainConfigRepository.save(entity);
    }
}
