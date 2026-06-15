package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiClientService;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.AiProviderRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuideGeneratorTest {

    @Test
    void storesRawResponseButDoesNotRenderMalformedJsonAsGuide() {
        AiClientService aiClientService = mock(AiClientService.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        PromptRepository promptRepository = mock(PromptRepository.class);
        GuideGenerator guideGenerator = new GuideGenerator(
            aiClientService,
            aiProviderRepository,
            promptRepository,
            new ObjectMapper()
        );

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(10L);
        provider.setEndpointUrl("http://example");
        provider.setApiKeyEncrypted("secret");
        provider.setModel("gpt-test");
        when(aiProviderRepository.findById(10L)).thenReturn(Optional.of(provider));
        when(aiClientService.complete(any(), any(), any(), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new AiCompletionResponse("{broken", 1, 1, 2, "gpt-test", true, null));

        MessageEntity root = new MessageEntity();
        root.setId(100L);
        root.setTelegramMessageId(200L);
        root.setText("Полезное сообщение");

        GuideContent result = guideGenerator.generate(
            List.of(root),
            new ClassifierResult(0.9, true, List.of("PRACTICAL_GUIDE_CANDIDATE"), true, List.of(100L), "reason"),
            10L,
            null,
            100L
        );

        assertThat(result.content()).isNull();
        assertThat(result.contentMarkdown()).isNull();
        assertThat(result.rawResponse()).isEqualTo("{broken");
        assertThat(result.generationError()).contains("Failed to parse model response");
    }

    @Test
    void keepsParsedContentAndSanitizesJsonLikeTitle() {
        AiClientService aiClientService = mock(AiClientService.class);
        AiProviderRepository aiProviderRepository = mock(AiProviderRepository.class);
        PromptRepository promptRepository = mock(PromptRepository.class);
        GuideGenerator guideGenerator = new GuideGenerator(
            aiClientService,
            aiProviderRepository,
            promptRepository,
            new ObjectMapper()
        );

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(11L);
        provider.setEndpointUrl("http://example");
        provider.setApiKeyEncrypted("secret");
        provider.setModel("gpt-test");
        when(aiProviderRepository.findById(11L)).thenReturn(Optional.of(provider));
        when(aiClientService.complete(any(), any(), any(), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new AiCompletionResponse("""
                {"title":"{\\"bad\\":true}","content":"Шаги","contentMarkdown":"# Нормальный заголовок\\n\\nТекст","confidence":0.91,"tags":["Claude","доступ","оплата"]}
                """, 1, 1, 2, "gpt-test", true, null));

        MessageEntity root = new MessageEntity();
        root.setId(101L);
        root.setTelegramMessageId(201L);
        root.setText("Полезное сообщение");

        GuideContent result = guideGenerator.generate(
            List.of(root),
            new ClassifierResult(0.9, true, List.of("PRACTICAL_GUIDE_CANDIDATE"), true, List.of(101L), "reason"),
            11L,
            null,
            101L
        );

        assertThat(result.title()).isEqualTo("Нормальный заголовок");
        assertThat(result.content()).isEqualTo("Шаги");
        assertThat(result.rawResponse()).contains("\"contentMarkdown\"");
        assertThat(result.generationError()).isNull();
    }
}
