package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.SettingsEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveProviderResolverTest {

    private AiProviderRepository aiProviderRepository;
    private SettingsRepository settingsRepository;
    private ActiveProviderResolver resolver;

    @BeforeEach
    void setUp() {
        aiProviderRepository = mock(AiProviderRepository.class);
        settingsRepository = mock(SettingsRepository.class);
        resolver = new ActiveProviderResolver(aiProviderRepository, settingsRepository);
    }

    @Test
    void resolvesConfiguredActiveProviderWhenUsable() {
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(7L);
        AiProviderEntity active = provider(7L, "OPENAI_COMPATIBLE", "ACTIVE", "https://api.example.test", "gpt-test");

        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(aiProviderRepository.findById(7L)).thenReturn(Optional.of(active));

        Optional<ActiveProviderResolver.ResolvedProvider> result =
            resolver.resolve(ModelCallPurpose.CLASSIFICATION);

        assertThat(result).isPresent();
        assertThat(result.get().provider().getId()).isEqualTo(7L);
        assertThat(result.get().routingReason()).isEqualTo("settings.activeProviderId");
    }

    @Test
    void skipsDisabledActiveProviderAndFallsBackToUsableProvider() {
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(3001L);
        AiProviderEntity disabled = provider(3001L, "OPENAI_COMPATIBLE", "DISABLED", "https://disabled.example.test", "gpt-disabled");
        AiProviderEntity usable = provider(8L, "OPENAI_COMPATIBLE", "ACTIVE", "https://api.example.test", "gpt-test");

        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(aiProviderRepository.findById(3001L)).thenReturn(Optional.of(disabled));
        when(aiProviderRepository.findAll()).thenReturn(List.of(disabled, usable));

        Optional<ActiveProviderResolver.ResolvedProvider> result =
            resolver.resolve(ModelCallPurpose.CLASSIFICATION);

        assertThat(result).isPresent();
        assertThat(result.get().provider().getId()).isEqualTo(8L);
    }

    @Test
    void rejectsMockProviderInProdLikeMode() {
        SettingsEntity settings = new SettingsEntity();
        settings.setActiveProviderId(3001L);
        AiProviderEntity mockProvider = provider(3001L, "MOCK", "ACTIVE", "mock://local", "mock-guide-model");

        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(aiProviderRepository.findById(3001L)).thenReturn(Optional.of(mockProvider));
        when(aiProviderRepository.findAll()).thenReturn(List.of(mockProvider));

        Optional<ActiveProviderResolver.ResolvedProvider> result =
            resolver.resolve(ModelCallPurpose.GUIDE_GENERATION);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsErrorProviderAndMissingModel() {
        AiProviderEntity errorProvider = provider(1L, "OPENAI_COMPATIBLE", "ERROR", "https://api.example.test", "gpt-test");
        AiProviderEntity noModel = provider(2L, "OPENAI_COMPATIBLE", "ACTIVE", "https://api.example.test", null);

        when(settingsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(aiProviderRepository.findAll()).thenReturn(List.of(errorProvider, noModel));

        Optional<ActiveProviderResolver.ResolvedProvider> result =
            resolver.resolve(ModelCallPurpose.CLUSTER_CLASSIFICATION);

        assertThat(result).isEmpty();
    }

    private AiProviderEntity provider(Long id, String protocol, String status, String endpointUrl, String model) {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(id);
        provider.setName("Provider " + id);
        provider.setProtocol(protocol);
        provider.setStatus(status);
        provider.setEndpointUrl(endpointUrl);
        provider.setApiKeyEncrypted("test-key");
        provider.setModel(model);
        return provider;
    }
}
