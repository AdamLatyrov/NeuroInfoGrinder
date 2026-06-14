package com.larbcorp.neuroinfogrinder.domain.questions;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.CreateProviderRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestProviderResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdateProviderRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiMessage;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiModelsService;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {

    private final AiProviderRepository aiProviderRepository;
    private final AiClientService aiClientService;
    private final AiModelsService aiModelsService;

    @Transactional(readOnly = true)
    public List<AiProviderEntity> getAll() {
        return aiProviderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AiProviderEntity getById(Long id) {
        return aiProviderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
    }

    @Transactional
    public AiProviderEntity create(CreateProviderRequest request) {
        AiProviderEntity entity = new AiProviderEntity();
        entity.setName(request.name());
        entity.setProtocol(request.protocol());
        entity.setEndpointUrl(request.endpointUrl());
        entity.setApiKeyEncrypted(request.apiKey());
        entity.setModel(request.model());
        entity.setStatus("DISABLED");
        entity.setLastError(null);
        return aiProviderRepository.save(entity);
    }

    @Transactional
    public AiProviderEntity update(Long id, UpdateProviderRequest request) {
        AiProviderEntity entity = getById(id);
        if (request.name() != null) entity.setName(request.name());
        if (request.protocol() != null) entity.setProtocol(request.protocol());
        if (request.endpointUrl() != null) entity.setEndpointUrl(request.endpointUrl());
        if (request.apiKey() != null) entity.setApiKeyEncrypted(request.apiKey());
        if (request.model() != null) entity.setModel(request.model());
        return aiProviderRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!aiProviderRepository.existsById(id)) {
            throw new IllegalArgumentException("Provider not found: " + id);
        }
        aiProviderRepository.deleteById(id);
    }

    @Transactional
    public TestProviderResponse testConnection(Long id) {
        AiProviderEntity entity = getById(id);
        entity.setLastTestedAt(Instant.now());

        String model = entity.getModel() != null ? entity.getModel() : "gpt-4o-mini";
        List<AiMessage> messages = List.of(
            new AiMessage("user", "Say \"Connection test successful\" and nothing else.")
        );

        AiCompletionRequest request = new AiCompletionRequest(
            entity.getEndpointUrl(),
            entity.getApiKeyEncrypted(),
            model,
            messages,
            0.0,
            32
        );

        long startTime = System.currentTimeMillis();
        AiCompletionResponse response = aiClientService.complete(request);
        long latencyMs = System.currentTimeMillis() - startTime;

        boolean success = response.success();
        String error = response.error();

        if (success) {
            entity.setLastTestResult("OK");
            entity.setStatus("ACTIVE");
            entity.setLastError(null);
            aiModelsService.invalidateCache();
            log.info("Provider {} connection test succeeded ({}ms, {} tokens)", id, latencyMs, response.totalTokens());
        } else {
            entity.setLastTestResult("CONNECTION_FAILED");
            entity.setStatus("ERROR");
            entity.setLastError(error);
            log.warn("Provider {} connection test failed: {}", id, error);
        }

        aiProviderRepository.save(entity);

        return new TestProviderResponse(success, latencyMs, error);
    }
}
