package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassifierRunnerTest {

    private AiClientService aiClientService;
    private AiProviderRepository aiProviderRepository;
    private PromptRepository promptRepository;
    private ClassifierRunner runner;

    @BeforeEach
    void setUp() {
        aiClientService = mock(AiClientService.class);
        aiProviderRepository = mock(AiProviderRepository.class);
        promptRepository = mock(PromptRepository.class);
        runner = new ClassifierRunner(aiClientService, aiProviderRepository, promptRepository, new ObjectMapper());
    }

    @Test
    void parsesNewJsonAndFiltersUnknownLabels() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(2L);
        provider.setEndpointUrl("mock://local");
        provider.setApiKeyEncrypted("test");
        provider.setModel("mock-model");
        when(aiProviderRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(aiClientService.complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble(), anyInt()))
            .thenReturn(new AiCompletionResponse("""
                {
                  "score": 0.81,
                  "matched": true,
                  "labels": ["DEMAND_SIGNAL", "UNKNOWN_TAG", "PAYMENT_WORKAROUND"],
                  "guide_candidate": false,
                  "evidence_message_ids": [10, 99],
                  "reasoning": "Есть спрос и обсуждение оплаты"
                }
                """, 1, 1, 2, "mock-model", true, null));

        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setType("LLM");
        classifier.setProviderId(2L);
        MessageEntity message = message(10L, "никто не знает где можно безлимитный апи клауда купить?");

        ClassifierResult result = runner.classify(List.of(message), classifier);

        assertThat(result.matched()).isTrue();
        assertThat(result.labels()).containsExactly("DEMAND_SIGNAL", "PAYMENT_WORKAROUND");
        assertThat(result.guideCandidate()).isFalse();
        assertThat(result.evidenceMessageIds()).containsExactly(10L);
    }

    @Test
    void toleratesOldJsonWithoutNewFields() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(2L);
        provider.setEndpointUrl("mock://local");
        provider.setApiKeyEncrypted("test");
        provider.setModel("mock-model");
        when(aiProviderRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(aiClientService.complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble(), anyInt()))
            .thenReturn(new AiCompletionResponse("""
                {
                  "score": 0.88,
                  "matched": true,
                  "reasoning": "Похоже на практический гайд"
                }
                """, 1, 1, 2, "mock-model", true, null));

        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setType("LLM");
        classifier.setProviderId(2L);
        MessageEntity message = message(11L, "1. Открой openrouter 2. добавь endpoint 3. проверь curl");

        ClassifierResult result = runner.classify(List.of(message), classifier);

        assertThat(result.matched()).isTrue();
        assertThat(result.guideCandidate()).isTrue();
        assertThat(result.labels()).contains("PRACTICAL_GUIDE_CANDIDATE");
    }

    @Test
    void fallsBackToFirstActiveProviderWhenConfiguredProviderMissing() {
        AiProviderEntity activeProvider = new AiProviderEntity();
        activeProvider.setId(7L);
        activeProvider.setProtocol("OPENAI");
        activeProvider.setStatus("ACTIVE");
        activeProvider.setEndpointUrl("https://api.example.test");
        activeProvider.setApiKeyEncrypted("active-key");
        activeProvider.setModel("gpt-4.1-mini");

        when(aiProviderRepository.findById(2L)).thenReturn(Optional.empty());
        when(aiProviderRepository.findAll()).thenReturn(List.of(activeProvider));
        when(aiClientService.complete(
            eq("https://api.example.test"),
            eq("active-key"),
            eq("gpt-4.1-mini"),
            anyString(),
            anyString(),
            anyDouble(),
            anyInt()))
            .thenReturn(new AiCompletionResponse("""
                {
                  "score": 0.77,
                  "matched": true,
                  "labels": ["AI_ACCESS_DEMAND", "PROVIDER_MENTION"],
                  "guide_candidate": false,
                  "reasoning": "Fallback provider handled classification"
                }
                """, 1, 1, 2, "gpt-4.1-mini", true, null));

        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setId(15L);
        classifier.setType("LLM");
        classifier.setProviderId(2L);
        MessageEntity message = message(12L, "где покупать гифты клода по норм цене");

        ClassifierResult result = runner.classify(List.of(message), classifier);

        assertThat(result.matched()).isTrue();
        assertThat(result.labels()).contains("DEMAND_SIGNAL");
    }

    @Test
    void fallsBackToFirstActiveProviderWhenClassifierHasNoProviderId() {
        AiProviderEntity activeProvider = new AiProviderEntity();
        activeProvider.setId(3L);
        activeProvider.setProtocol("OPENAI");
        activeProvider.setStatus("ACTIVE");
        activeProvider.setEndpointUrl("https://api.example.test");
        activeProvider.setApiKeyEncrypted("active-key");
        activeProvider.setModel("gpt-4o-mini");

        when(aiProviderRepository.findAll()).thenReturn(List.of(activeProvider));
        when(aiClientService.complete(
            eq("https://api.example.test"),
            eq("active-key"),
            eq("gpt-4o-mini"),
            anyString(),
            anyString(),
            anyDouble(),
            anyInt()))
            .thenReturn(new AiCompletionResponse("""
                {
                  "score": 0.66,
                  "matched": true,
                  "labels": ["PROVIDER_MENTION"],
                  "guide_candidate": false,
                  "reasoning": "Active provider selected automatically"
                }
                """, 1, 1, 2, "gpt-4o-mini", true, null));

        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setId(16L);
        classifier.setType("LLM");
        MessageEntity message = message(13L, "openrouter provider options");

        ClassifierResult result = runner.classify(List.of(message), classifier);

        assertThat(result.matched()).isTrue();
        assertThat(result.labels()).contains("DEMAND_SIGNAL");
    }

    private MessageEntity message(Long id, String text) {
        MessageEntity entity = new MessageEntity();
        entity.setId(id);
        entity.setTelegramMessageId(id);
        entity.setGroupId(1L);
        entity.setText(text);
        entity.setIsBot(false);
        entity.setMessageDate(Instant.now());
        return entity;
    }
}
