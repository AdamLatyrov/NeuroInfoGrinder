package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClassifierRunnerTest {

    private ModelCallService modelCallService;
    private PromptRepository promptRepository;
    private ClassifierRunner runner;

    @BeforeEach
    void setUp() {
        modelCallService = mock(ModelCallService.class);
        promptRepository = mock(PromptRepository.class);
        runner = new ClassifierRunner(modelCallService, promptRepository, new ObjectMapper());
    }

    @Test
    void parsesNewJsonAndFiltersUnknownLabels() {
        whenModelCall(new AiCompletionResponse("""
            {
              "score": 0.81,
              "matched": true,
              "labels": ["DEMAND_SIGNAL", "UNKNOWN_TAG", "PAYMENT_WORKAROUND"],
              "guide_candidate": false,
              "evidence_message_ids": [10, 99],
              "reasoning": "Есть спрос и обсуждение оплаты"
            }
            """, 1, 1, 2, "gpt-test", true, null));

        ClassifierEntity classifier = llmClassifier();
        MessageEntity message = message(10L, "никто не знает где можно безлимитный апи клауда купить?");

        ClassifierResult result = runner.classify(List.of(message), classifier);

        assertThat(result.matched()).isTrue();
        assertThat(result.labels()).containsExactly("DEMAND_SIGNAL", "PAYMENT_WORKAROUND");
        assertThat(result.guideCandidate()).isFalse();
        assertThat(result.evidenceMessageIds()).containsExactly(10L);
        assertThat(result.providerId()).isEqualTo(7L);
        assertThat(result.model()).isEqualTo("gpt-test");
    }

    @Test
    void clampsIntelligenceScoresFromLlmJson() {
        whenModelCall(new AiCompletionResponse("""
            {
              "score": 0.91,
              "matched": true,
              "labels": ["BUG_OR_LIMITATION"],
              "guide_candidate": false,
              "problem_signal_score": 150,
              "pain_score": -5,
              "willingness_to_pay_score": 61,
              "spam_score": 101,
              "meaning_summary": "API падает",
              "mentioned_errors": ["500"],
              "reasoning": "Есть проблема"
            }
            """, 1, 1, 2, "gpt-test", true, null));

        ClassifierResult result = runner.classify(
            List.of(message(14L, "api падает с 500")),
            llmClassifier()
        );

        assertThat(result.problemSignalScore()).isEqualTo(100);
        assertThat(result.painScore()).isZero();
        assertThat(result.willingnessToPayScore()).isEqualTo(61);
        assertThat(result.spamScore()).isEqualTo(100);
        assertThat(result.meaningSummary()).isEqualTo("API падает");
        assertThat(result.mentionedErrors()).containsExactly("500");
    }

    @Test
    void toleratesOldJsonWithoutNewFields() {
        whenModelCall(new AiCompletionResponse("""
            {
              "score": 0.88,
              "matched": true,
              "reasoning": "Похоже на практический гайд"
            }
            """, 1, 1, 2, "gpt-test", true, null));

        ClassifierResult result = runner.classify(
            List.of(message(11L, "1. Открой openrouter 2. добавь endpoint 3. проверь curl")),
            llmClassifier()
        );

        assertThat(result.matched()).isTrue();
        assertThat(result.guideCandidate()).isTrue();
        assertThat(result.labels()).contains("PRACTICAL_GUIDE_CANDIDATE");
        assertThat(result.guidePotentialScore()).isEqualTo(88);
        assertThat(result.problemSignalScore()).isZero();
        assertThat(result.spamScore()).isZero();
    }

    @Test
    void parsesGeneralUtilityLabelsFromLlmJson() {
        whenModelCall(new AiCompletionResponse("""
            {
              "score": 0.82,
              "matched": true,
              "labels": ["PRACTICAL_PROBLEM", "WORKFLOW_LIFEHACK", "BUSINESS_PROCESS"],
              "guide_candidate": true,
              "problem_signal_score": 75,
              "guide_potential_score": 82,
              "meaning_summary": "Пользователи теряются в CRM",
              "problem_statement": "Нужен понятный онбординг и сбор обратной связи",
              "reasoning": "Практическая продуктовая проблема"
            }
            """, 1, 1, 2, "gpt-test", true, null));

        ClassifierResult result = runner.classify(
            List.of(message(17L, "Показал CRM программу друзьям: все теряются на первом экране. Кто как решал онбординг?")),
            llmClassifier()
        );

        assertThat(result.matched()).isTrue();
        assertThat(result.labels()).containsExactly("PRACTICAL_PROBLEM", "WORKFLOW_LIFEHACK", "BUSINESS_PROCESS", "PRACTICAL_GUIDE_CANDIDATE");
        assertThat(result.guideCandidate()).isTrue();
        assertThat(result.problemSignalScore()).isEqualTo(75);
    }

    @Test
    void defaultPromptTargetsGeneralUsefulProblemsNotOnlyAiTools() {
        whenModelCall(new AiCompletionResponse("""
            {"score":0.1,"matched":false,"reasoning":"not relevant"}
            """, 1, 1, 2, "gpt-test", true, null));

        runner.classify(
            List.of(message(18L, "Как лучше собирать обратную связь после демо CRM?")),
            llmClassifier()
        );

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(modelCallService).complete(eq(ModelCallPurpose.CLASSIFICATION), systemPrompt.capture(), any(), anyDouble(), anyInt());
        assertThat(systemPrompt.getValue()).contains("CRM");
        assertThat(systemPrompt.getValue()).contains("лайфхаки");
        assertThat(systemPrompt.getValue()).contains("не только AI");
    }

    @Test
    void malformedClassifierJsonFallsBackWithoutThrowing() {
        whenModelCall(new AiCompletionResponse("{broken", 1, 1, 2, "gpt-test", true, null));

        ClassifierResult result = runner.classify(
            List.of(message(15L, "api падает с 500")),
            llmClassifier()
        );

        assertThat(result.matched()).isFalse();
        assertThat(result.score()).isEqualTo(0.30);
        assertThat(result.reasoning()).contains("Fallback parsing");
        assertThat(result.problemSignalScore()).isNull();
        assertThat(result.providerId()).isEqualTo(7L);
    }

    @Test
    void returnsControlledErrorWhenRouterHasNoUsableProvider() {
        whenModelCall(new AiCompletionResponse(null, 0, 0, 0, null, false,
            "No usable active provider available for CLASSIFICATION"));

        ClassifierResult result = runner.classify(
            List.of(message(13L, "openrouter provider options")),
            llmClassifier()
        );

        assertThat(result.matched()).isFalse();
        assertThat(result.reasoning()).contains("No usable active provider available");
    }

    @Test
    void keywordClassifierDoesNotCallModelRouter() {
        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setType("KEYWORD");
        classifier.setKeywords("claude,api");

        ClassifierResult result = runner.classify(
            List.of(message(16L, "claude api упал")),
            classifier
        );

        assertThat(result.matched()).isTrue();
        verifyNoInteractions(modelCallService);
    }

    private void whenModelCall(AiCompletionResponse response) {
        when(modelCallService.complete(eq(ModelCallPurpose.CLASSIFICATION), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new ModelCallService.ModelCallResult(
                ModelCallPurpose.CLASSIFICATION,
                7L,
                "gpt-test",
                "test router",
                response
            ));
    }

    private ClassifierEntity llmClassifier() {
        ClassifierEntity classifier = new ClassifierEntity();
        classifier.setId(15L);
        classifier.setType("LLM");
        classifier.setProviderId(3001L);
        return classifier;
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
