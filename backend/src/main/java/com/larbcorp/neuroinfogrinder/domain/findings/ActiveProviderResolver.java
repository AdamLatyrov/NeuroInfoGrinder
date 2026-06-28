package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.questions.ProviderService;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActiveProviderResolver {

    private final AiProviderRepository aiProviderRepository;
    private final SettingsRepository settingsRepository;

    @Value("${neuroinfogrinder.provider-router.allow-mock-provider:false}")
    private boolean allowMockProvider;

    public Optional<ResolvedProvider> resolve(ModelCallPurpose purpose) {
        SettingsEntity settings = settingsRepository.findFirstByOrderByIdAsc().orElse(null);
        Long activeProviderId = settings != null ? settings.getActiveProviderId() : null;
        if (activeProviderId != null) {
            AiProviderEntity activeProvider = aiProviderRepository.findById(activeProviderId).orElse(null);
            if (isUsable(activeProvider)) {
                return Optional.of(new ResolvedProvider(activeProvider, purpose, "settings.activeProviderId"));
            }
        }

        return aiProviderRepository.findAll().stream()
            .filter(this::isUsable)
            .sorted(Comparator.comparing(AiProviderEntity::getId))
            .findFirst()
            .map(provider -> new ResolvedProvider(provider, purpose, "first usable active provider"));
    }

    public boolean isUsable(AiProviderEntity provider) {
        if (provider == null || !ProviderService.isUsableStatus(provider.getStatus())) {
            return false;
        }
        if (isMockProvider(provider) && !allowMockProvider) {
            return false;
        }
        return provider.getEndpointUrl() != null && !provider.getEndpointUrl().isBlank()
            && provider.getModel() != null && !provider.getModel().isBlank();
    }

    public boolean isMockProvider(AiProviderEntity provider) {
        if (provider == null) {
            return false;
        }
        String protocol = provider.getProtocol();
        String endpoint = provider.getEndpointUrl();
        String model = provider.getModel();
        return "MOCK".equalsIgnoreCase(protocol)
            || endpoint != null && endpoint.trim().toLowerCase(java.util.Locale.ROOT).startsWith("mock://")
            || model != null && model.toLowerCase(java.util.Locale.ROOT).contains("mock");
    }

    public record ResolvedProvider(
        AiProviderEntity provider,
        ModelCallPurpose purpose,
        String routingReason
    ) {}
}
