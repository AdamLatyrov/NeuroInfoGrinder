package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideGeneratorTest {

    @Test
    void parsesPlainValidJsonWithoutRetry() {
        TestHarness harness = harness(10L);
        whenModelCall(harness, new AiCompletionResponse("""
                {"title":"Гайд","content":"Шаги","contentMarkdown":"# Гайд","confidence":0.8,"tags":["api","гайд","Claude"]}
                """, 1, 1, 2, "gpt-test", true, null));

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Гайд");
        assertThat(result.generationError()).isNull();
        verify(harness.modelCallService, times(1)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void parsesJsonInsideMarkdownFence() {
        TestHarness harness = harness(10L);
        whenModelCall(harness, new AiCompletionResponse("""
                ```json
                {"title":"Гайд","content":"Шаги","contentMarkdown":"# Гайд\\n\\nШаги","confidence":0.8,"tags":["api","гайд","Claude"]}
                ```
                """, 1, 1, 2, "gpt-test", true, null));

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Гайд");
        assertThat(result.contentMarkdown()).contains("# Гайд");
        assertThat(result.generationError()).isNull();
        verify(harness.modelCallService, times(1)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void extractsJsonInsideUnclosedMarkdownFence() {
        TestHarness harness = harness(10L);
        whenModelCall(harness, new AiCompletionResponse("""
                ```json
                {"title":"Р“Р°Р№Рґ","content":"РЁР°РіРё","contentMarkdown":"# Р“Р°Р№Рґ","confidence":0.8,"tags":["api","РіР°Р№Рґ","Claude"]}
                """, 1, 1, 2, "gpt-test", true, null));

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Р“Р°Р№Рґ");
        assertThat(result.generationError()).isNull();
        verify(harness.modelCallService, times(1)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void extractsJsonObjectFromTextBeforeAndAfter() {
        TestHarness harness = harness(10L);
        whenModelCall(harness, new AiCompletionResponse("""
                Вот JSON:
                {"title":"Гайд","content":"Шаги","contentMarkdown":"# Гайд","confidence":0.8,"tags":["api","гайд","Claude"]}
                Готово.
                """, 1, 1, 2, "gpt-test", true, null));

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Гайд");
        assertThat(result.generationError()).isNull();
    }

    @Test
    void invalidJsonTriggersOneRetryAndSuccessCreatesGuide() {
        TestHarness harness = harness(10L);
        whenModelCall(harness,
            new AiCompletionResponse("{broken", 1, 1, 2, "gpt-test", true, null),
            new AiCompletionResponse("""
                {"title":"Повторный гайд","content":"Шаги","contentMarkdown":"# Повторный гайд","confidence":0.9,"tags":["api","гайд","Claude"]}
                """, 1, 1, 2, "gpt-test", true, null)
        );

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Повторный гайд");
        assertThat(result.generationError()).isNull();
        verify(harness.modelCallService, times(2)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void storesRawResponseButDoesNotRenderMalformedJsonAsGuideAfterRetryFailure() {
        TestHarness harness = harness(10L);
        whenModelCall(harness,
            new AiCompletionResponse("{broken", 1, 1, 2, "gpt-test", true, null),
            new AiCompletionResponse("{still broken", 1, 1, 2, "gpt-test", true, null)
        );

        GuideContent result = harness.generate();

        assertThat(result.content()).isNull();
        assertThat(result.contentMarkdown()).isNull();
        assertThat(result.rawResponse()).isEqualTo("{still broken");
        assertThat(result.generationError()).isEqualTo(GuideGenerator.READABLE_INVALID_JSON_ERROR);
        verify(harness.modelCallService, times(2)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void truncatedJsonDoesNotThrowAndStoresGenerationErrorAfterRetryFailure() {
        TestHarness harness = harness(10L);
        whenModelCall(harness,
            new AiCompletionResponse("{\"title\":\"Р“Р°Р№Рґ\",\"content\":\"unterminated", 1, 1, 2, "gpt-test", true, null),
            new AiCompletionResponse("not json", 1, 1, 2, "gpt-test", true, null)
        );

        GuideContent result = harness.generate();

        assertThat(result.content()).isNull();
        assertThat(result.contentMarkdown()).isNull();
        assertThat(result.generationError()).isEqualTo(GuideGenerator.READABLE_INVALID_JSON_ERROR);
        assertThat(result.tags()).isNotEmpty();
        verify(harness.modelCallService, times(2)).complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void keepsParsedContentAndSanitizesJsonLikeTitle() {
        TestHarness harness = harness(11L);
        whenModelCall(harness, new AiCompletionResponse("""
                {"title":"{\\"bad\\":true}","content":"Шаги","contentMarkdown":"# Нормальный заголовок\\n\\nТекст","confidence":0.91,"tags":["Claude","доступ","оплата"]}
                """, 1, 1, 2, "gpt-test", true, null));

        GuideContent result = harness.generate();

        assertThat(result.title()).isEqualTo("Нормальный заголовок");
        assertThat(result.content()).isEqualTo("Шаги");
        assertThat(result.rawResponse()).contains("\"contentMarkdown\"");
        assertThat(result.generationError()).isNull();
    }

    @Test
    void requestFramesGenerationAsSingleClusterWithSafetyConstraints() {
        TestHarness harness = harness(12L);
        whenModelCall(harness, new AiCompletionResponse("""
                {"title":"Гайд","content":"Шаги","contentMarkdown":"# Гайд","confidence":0.8,"tags":["api","гайд","Claude"]}
                """, 1, 1, 2, "gpt-test", true, null));

        harness.generate();

        verify(harness.modelCallService).complete(
            eq(ModelCallPurpose.GUIDE_GENERATION),
            argThat(systemPrompt -> systemPrompt.contains("one discussion cluster")
                && systemPrompt.contains("0..N guide angles")
                && systemPrompt.contains("payment/card data")),
            argThat(userPrompt -> userPrompt.contains("Единица генерации: один discussion cluster")
                && userPrompt.contains("source messages/evidence")
                && userPrompt.contains("0..N guide angles")
                && userPrompt.contains("defensive/risk/compliance guidance")),
            anyDouble(),
            anyInt()
        );
    }

    @Test
    void generationPromptIncludesTelegramTextUrlEntitiesAsMarkdownLinks() {
        TestHarness harness = harness(12L);
        whenModelCall(harness, new AiCompletionResponse("""
                {"title":"Guide","content":"Steps","contentMarkdown":"# Guide","confidence":0.8,"tags":["api"]}
                """, 1, 1, 2, "gpt-test", true, null));

        MessageEntity root = new MessageEntity();
        root.setId(100L);
        root.setTelegramMessageId(200L);
        root.setText("Open here.");
        root.setTextEntitiesJson("""
            [{"type":"textUrl","offset":5,"length":4,"url":"https://example.com","text":"here"}]
            """);

        harness.guideGenerator.generate(
            List.of(root),
            new ClassifierResult(0.9, true, List.of("PRACTICAL_GUIDE_CANDIDATE"), true, List.of(100L), "reason"),
            null,
            100L
        );

        verify(harness.modelCallService).complete(
            eq(ModelCallPurpose.GUIDE_GENERATION),
            any(),
            argThat(userPrompt -> userPrompt.contains("text: Open [here](https://example.com).")),
            anyDouble(),
            anyInt()
        );
    }

    @Test
    void customDbPromptIsAugmentedWithRuntimeGuideConstraints() {
        TestHarness harness = harness(13L);
        PromptEntity prompt = new PromptEntity();
        prompt.setContent("Сделай материал на {{language}} в формате {{format}}.");
        when(harness.promptRepository.findById(77L)).thenReturn(Optional.of(prompt));
        whenModelCall(harness, new AiCompletionResponse("""
                {"title":"Гайд","content":"Шаги","contentMarkdown":"# Гайд","confidence":0.8,"tags":["api","гайд","Claude"]}
                """, 1, 1, 2, "gpt-test", true, null));

        harness.generate(77L);

        verify(harness.modelCallService).complete(
            eq(ModelCallPurpose.GUIDE_GENERATION),
            argThat(systemPrompt -> systemPrompt.contains("Сделай материал на Russian")
                && systemPrompt.contains("Return only valid JSON")
                && systemPrompt.contains("source messages/evidence")
                && systemPrompt.contains("defensive/risk/compliance guidance")),
            any(),
            anyDouble(),
            anyInt()
        );
    }

    private TestHarness harness(Long providerId) {
        ModelCallService modelCallService = mock(ModelCallService.class);
        PromptRepository promptRepository = mock(PromptRepository.class);
        GuideGenerator guideGenerator = new GuideGenerator(
            modelCallService,
            promptRepository,
            new ObjectMapper()
        );

        return new TestHarness(modelCallService, promptRepository, guideGenerator, providerId);
    }

    private void whenModelCall(TestHarness harness, AiCompletionResponse... responses) {
        var stubbing = when(harness.modelCallService.complete(eq(ModelCallPurpose.GUIDE_GENERATION), any(), any(), anyDouble(), anyInt()));
        for (AiCompletionResponse response : responses) {
            stubbing = stubbing.thenReturn(new ModelCallService.ModelCallResult(
                ModelCallPurpose.GUIDE_GENERATION,
                harness.providerId,
                response.model(),
                "test router",
                response
            ));
        }
    }

    private record TestHarness(
        ModelCallService modelCallService,
        PromptRepository promptRepository,
        GuideGenerator guideGenerator,
        Long providerId
    ) {
        GuideContent generate() {
            return generate(null);
        }

        GuideContent generate(Long promptId) {
            MessageEntity root = new MessageEntity();
            root.setId(100L);
            root.setTelegramMessageId(200L);
            root.setText("Полезное сообщение");

            return guideGenerator.generate(
                List.of(root),
                new ClassifierResult(0.9, true, List.of("PRACTICAL_GUIDE_CANDIDATE"), true, List.of(100L), "reason"),
                promptId,
                100L
            );
        }
    }
}
