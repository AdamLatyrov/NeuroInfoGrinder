package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiMessage;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCallService {

    private final AiClientService aiClientService;
    private final ActiveProviderResolver activeProviderResolver;

    public ModelCallResult complete(
        ModelCallPurpose purpose,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens
    ) {
        ActiveProviderResolver.ResolvedProvider resolvedProvider = activeProviderResolver
            .resolve(purpose)
            .orElse(null);
        if (resolvedProvider == null) {
            String error = "No usable active provider available for " + purpose;
            log.warn(error);
            return ModelCallResult.failed(purpose, error);
        }

        AiProviderEntity provider = resolvedProvider.provider();
        AiCompletionRequest request = new AiCompletionRequest(
            provider.getEndpointUrl(),
            provider.getApiKeyEncrypted(),
            provider.getModel(),
            List.of(
                new AiMessage("system", systemPrompt),
                new AiMessage("user", userPrompt)
            ),
            temperature,
            maxTokens
        );
        AiCompletionResponse response = aiClientService.complete(request);
        return new ModelCallResult(
            purpose,
            provider.getId(),
            provider.getModel(),
            resolvedProvider.routingReason(),
            response
        );
    }

    public record ModelCallResult(
        ModelCallPurpose purpose,
        Long providerId,
        String model,
        String routingReason,
        AiCompletionResponse response
    ) {
        static ModelCallResult failed(ModelCallPurpose purpose, String error) {
            return new ModelCallResult(
                purpose,
                null,
                null,
                "no usable active provider",
                new AiCompletionResponse(null, 0, 0, 0, null, false, error)
            );
        }
    }
}
