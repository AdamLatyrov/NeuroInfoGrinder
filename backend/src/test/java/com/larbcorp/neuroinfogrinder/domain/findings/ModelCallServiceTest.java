package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelCallServiceTest {

    @Test
    void doesNotCallAiClientWhenNoUsableActiveProviderExists() {
        AiClientService aiClientService = mock(AiClientService.class);
        ActiveProviderResolver resolver = mock(ActiveProviderResolver.class);
        ModelCallService service = new ModelCallService(aiClientService, resolver);
        when(resolver.resolve(ModelCallPurpose.GUIDE_GENERATION)).thenReturn(Optional.empty());

        ModelCallService.ModelCallResult result = service.complete(
            ModelCallPurpose.GUIDE_GENERATION,
            "system",
            "user",
            0.2,
            100
        );

        assertThat(result.response().success()).isFalse();
        assertThat(result.response().error()).contains("No usable active provider");
        verifyNoInteractions(aiClientService);
    }

    @Test
    void callsAiClientWithResolvedActiveProvider() {
        AiClientService aiClientService = mock(AiClientService.class);
        ActiveProviderResolver resolver = mock(ActiveProviderResolver.class);
        ModelCallService service = new ModelCallService(aiClientService, resolver);
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(7L);
        provider.setEndpointUrl("https://api.example.test");
        provider.setApiKeyEncrypted("test-key");
        provider.setModel("gpt-test");

        when(resolver.resolve(ModelCallPurpose.CLASSIFICATION))
            .thenReturn(Optional.of(new ActiveProviderResolver.ResolvedProvider(
                provider,
                ModelCallPurpose.CLASSIFICATION,
                "settings.activeProviderId"
            )));
        when(aiClientService.complete(any()))
            .thenReturn(new AiCompletionResponse("{}", 1, 2, 3, "gpt-test", true, null));

        ModelCallService.ModelCallResult result = service.complete(
            ModelCallPurpose.CLASSIFICATION,
            "system",
            "user",
            0.3,
            1024
        );

        assertThat(result.providerId()).isEqualTo(7L);
        assertThat(result.model()).isEqualTo("gpt-test");
        assertThat(result.response().success()).isTrue();
        verify(aiClientService).complete(any());
    }
}
