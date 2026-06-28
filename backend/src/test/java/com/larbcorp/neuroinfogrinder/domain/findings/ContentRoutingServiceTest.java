package com.larbcorp.neuroinfogrinder.domain.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.infrastructure.client.ai.AiCompletionResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentRoutingServiceTest {

    private final ModelCallService modelCallService = mock(ModelCallService.class);
    private final ContentRoutingService routingService = new ContentRoutingService(modelCallService, new ObjectMapper());

    @Test
    void productUpdateRoutesToUsefulInfo() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(1L, "В Алису AI добавили новых персонажей и режим общения.")),
            classifier(0.82, true, 30, 40, 20, "Анонс продуктового обновления")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("PRODUCT_CHANGE");
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
        assertThat(decision.topicLabel()).contains("Алисе AI");
    }

    @Test
    void rumorRoutesToUsefulInfoWithLowerGuideActionability() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(2L, "Пишут слухи, что GPT-5.6 ожидается скоро, но подтверждения нет.")),
            classifier(0.72, true, 20, 70, 20, "Rumor monitoring")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("RUMOR_MONITORING");
        assertThat(decision.actionabilityScore()).isLessThan(50);
    }

    @Test
    void cursorQuestionWithoutConcreteAnswerDoesNotCreateMaterial() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(3L, "Контекст Cursor считается на сутки или на один чат?")),
            classifier(0.8, true, 20, 35, 20, "Short Q/A")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("DISCUSSION_ONLY");
        assertThat(decision.shouldCreateMaterial()).isFalse();
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
    }

    @Test
    void subjectiveAiModelAdviceDoesNotCreateGuideOrUsefulMaterial() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(
                message(30L, "Я пытаюсь подобрать лучшую модель для итеративного планирования и бизнес-стратегии. Gemini нравится, но на длинных диалогах галюны. Sonnet холодно отвечал. Может есть у кого идеи что еще попробовать?"),
                message(31L, "Для начала: не соннет, а опус. Это решит 99% твоих проблем :)"),
                message(32L, "Опус тормоз, но может стоит того, чтобы подождать."),
                message(33L, "Быстро отвечает 3.5 Flash, которая наваливает галюнов.")
            ),
            classifier(0.9, true, 70, 35, 20, "Subjective model advice")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("DISCUSSION_ONLY");
        assertThat(decision.shouldCreateMaterial()).isFalse();
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
    }

    @Test
    void oneOffToolRecommendationWithLinkDoesNotCreateMaterial() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(34L, "what's the best tool for automating instagram posts? / https://smmplanner.io :)")),
            classifier(0.86, true, 45, 35, 20, "One-off tool recommendation")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("DISCUSSION_ONLY");
        assertThat(decision.shouldCreateMaterial()).isFalse();
    }

    @Test
    void concreteRepositoryLinkCreatesUsefulMaterial() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(35L, "reanchor на последнюю версию, кстати сделан https://github.com/Anry777/hermes-patchkit")),
            classifier(0.84, true, 80, 45, 20, "Concrete dev-tool update with repository link")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("SHORT_INSIGHT");
        assertThat(decision.shouldCreateMaterial()).isTrue();
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
        assertThat(decision.topicLabel()).contains("hermes-patchkit");
    }

    @Test
    void concreteRepositoryLinkOverridesOvercautiousLlmRouting() {
        when(modelCallService.complete(eq(ModelCallPurpose.CLUSTER_CONTENT_ROUTING), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new ModelCallService.ModelCallResult(
                ModelCallPurpose.CLUSTER_CONTENT_ROUTING,
                7L,
                "router-model",
                "test",
                new AiCompletionResponse(
                    """
                    {
                      "contentType": "USEFUL_INFO",
                      "contentSubtype": "DISCUSSION_ONLY",
                      "topicLabel": "hermes-patchkit reanchor",
                      "topicSummary": "Короткая находка по обновлению dev-tool.",
                      "contentTitle": "hermes-patchkit reanchor",
                      "contentSummary": "Есть ссылка на репозиторий с обновлением.",
                      "normalizedTopicKey": "hermes_patchkit_reanchor",
                      "specificAngle": "Ключевой вывод из обсуждения",
                      "shouldCreateMaterial": false,
                      "shouldGenerateFullGuide": false,
                      "confidence": 0.74,
                      "importanceScore": 52,
                      "actionabilityScore": 30,
                      "noveltyScore": 45,
                      "evidenceScore": 44,
                      "riskScore": 20,
                      "noiseScore": 20,
                      "safetyCategory": "NORMAL",
                      "reason": "too short",
                      "warnings": [],
                      "suggestedSections": []
                    }
                    """,
                    10,
                    20,
                    30,
                    "router-model",
                    true,
                    null
                )
            ));

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(35L, "reanchor на последнюю версию, кстати сделан https://github.com/Anry777/hermes-patchkit")),
            classifier(0.84, true, 80, 45, 20, "Concrete dev-tool update with repository link")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("SHORT_INSIGHT");
        assertThat(decision.shouldCreateMaterial()).isTrue();
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
    }

    @Test
    void abuseQuestionCreatesGuide() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(36L, "есть абуз канвы бизнес?")),
            classifier(0.78, true, 20, 55, 65, "Abuse signal")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.GUIDE);
        assertThat(decision.contentSubtype()).isEqualTo("ABUSE_OR_LIMIT_EXPLOIT");
        assertThat(decision.safetyCategory()).isEqualTo("ABUSE_OR_LIMIT_EXPLOIT");
        assertThat(decision.shouldCreateMaterial()).isTrue();
        assertThat(decision.shouldGenerateFullGuide()).isTrue();
        assertThat(decision.actionabilityScore()).isGreaterThanOrEqualTo(50);
        assertThat(decision.evidenceScore()).isGreaterThanOrEqualTo(45);
        assertThat(decision.topicLabel()).contains("Canva");
    }

    @Test
    void abuseSignalOverridesGenericFaqShapeFromLlm() {
        when(modelCallService.complete(eq(ModelCallPurpose.CLUSTER_CONTENT_ROUTING), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new ModelCallService.ModelCallResult(
                ModelCallPurpose.CLUSTER_CONTENT_ROUTING,
                7L,
                "router-model",
                "test",
                new AiCompletionResponse(
                    """
                    {
                      "contentType": "FAQ",
                      "contentSubtype": "QUESTION_ANSWER",
                      "topicLabel": "FAQ: Практический гайд по теме кластера",
                      "topicSummary": "Question / Short answer / Caveats",
                      "contentTitle": "FAQ: Практический гайд по теме кластера",
                      "contentSummary": "Question Short answer Explanation Caveats Source context",
                      "normalizedTopicKey": "generic",
                      "specificAngle": "Практический гайд по теме кластера",
                      "shouldCreateMaterial": true,
                      "shouldGenerateFullGuide": false,
                      "confidence": 0.81,
                      "importanceScore": 55,
                      "actionabilityScore": 45,
                      "noveltyScore": 35,
                      "evidenceScore": 25,
                      "riskScore": 20,
                      "noiseScore": 20,
                      "safetyCategory": "NORMAL",
                      "reason": "generic faq",
                      "warnings": [],
                      "suggestedSections": []
                    }
                    """,
                    10,
                    20,
                    30,
                    "router-model",
                    true,
                    null
                )
            ));

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(37L, "есть абуз канвы бизнес?")),
            classifier(0.78, true, 20, 55, 65, "Abuse signal")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.GUIDE);
        assertThat(decision.contentSubtype()).isEqualTo("ABUSE_OR_LIMIT_EXPLOIT");
        assertThat(decision.shouldCreateMaterial()).isTrue();
        assertThat(decision.shouldGenerateFullGuide()).isTrue();
        assertThat(decision.actionabilityScore()).isGreaterThanOrEqualTo(60);
        assertThat(decision.evidenceScore()).isGreaterThanOrEqualTo(45);
        assertThat(decision.contentTitle()).contains("Canva");
        assertThat(decision.contentTitle()).doesNotContain("FAQ");
        assertThat(decision.contentTitle()).doesNotContain("Практический гайд по теме кластера");
    }

    @Test
    void providerOfferGetsSpecificTitleFromMessageContents() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(35L, "кстати в verdent.ai увеличили лимиты и 100 кредитов хватает на 84 запроса к лод опусу 4.8")),
            classifier(0.86, true, 40, 55, 20, "Практический гайд по теме кластера")
        );

        assertThat(decision.topicLabel()).isEqualTo("verdent.ai: расход кредитов на Claude Opus");
        assertThat(decision.contentTitle()).contains("verdent.ai");
        assertThat(decision.contentTitle()).doesNotContain("Практический гайд по теме кластера");
    }

    @Test
    void codexSqliteFixRoutesToGuide() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(4L, "Как временно остановить рост logs_2.sqlite в Codex: закрыть процесс, очистить WAL и перезапустить.")),
            classifier(0.9, true, 85, 20, 10, "Actionable Codex troubleshooting")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.GUIDE);
        assertThat(decision.shouldGenerateFullGuide()).isTrue();
        assertThat(decision.specificAngle()).contains("logs_2.sqlite");
    }

    @Test
    void bypassRoutesToUsefulRiskNote() {
        stubUnavailableLlm();

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(5L, "Claude Code bypass и обход фильтров через jailbreak prompt.")),
            classifier(0.9, true, 80, 20, 95, "Unsafe bypass discussion")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("BYPASS");
        assertThat(decision.safetyCategory()).isEqualTo("BYPASS");
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
    }

    @Test
    void broadTemporalClusterIsDeferred() {
        stubUnavailableLlm();
        TopicDiscussionClusterEntity cluster = cluster();
        cluster.setStartAt(Instant.parse("2026-06-19T08:00:00Z"));
        cluster.setEndAt(Instant.parse("2026-06-19T20:00:00Z"));

        ContentRoutingDecision decision = routingService.route(
            cluster,
            null,
            List.of(
                message(6L, "GLM, EvoMap credits, free API, Cursor и Claude обсуждали весь день."),
                message(7L, "Вечером снова вернулись к GLM и реферальным офферам.")
            ),
            classifier(0.7, true, 50, 55, 30, "Mixed broad cluster")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("BROAD_OR_TEMPORAL");
        assertThat(decision.shouldCreateMaterial()).isFalse();
    }

    @Test
    void legacyLlmContentTypesAreCollapsedToUsefulInfo() {
        when(modelCallService.complete(eq(ModelCallPurpose.CLUSTER_CONTENT_ROUTING), any(), any(), anyDouble(), anyInt()))
            .thenReturn(new ModelCallService.ModelCallResult(
                ModelCallPurpose.CLUSTER_CONTENT_ROUTING,
                7L,
                "router-model",
                "test",
                new AiCompletionResponse(
                    """
                    {
                      "contentType": "NEWS",
                      "contentSubtype": "ANNOUNCEMENT",
                      "topicLabel": "Новые лимиты AI-сервиса",
                      "topicSummary": "В обсуждении заметили изменение лимитов.",
                      "contentTitle": "Новые лимиты AI-сервиса",
                      "contentSummary": "Нужно проверить лимиты перед использованием.",
                      "normalizedTopicKey": "novye_limity_ai_servisa",
                      "specificAngle": "Что изменилось и что проверить",
                      "shouldCreateMaterial": true,
                      "shouldGenerateFullGuide": false,
                      "confidence": 0.87,
                      "importanceScore": 70,
                      "actionabilityScore": 30,
                      "noveltyScore": 80,
                      "evidenceScore": 65,
                      "riskScore": 20,
                      "noiseScore": 15,
                      "safetyCategory": "NORMAL",
                      "reason": "news-like useful item",
                      "warnings": [],
                      "suggestedSections": ["Главный вывод"]
                    }
                    """,
                    10,
                    20,
                    30,
                    "router-model",
                    true,
                    null
                )
            ));

        ContentRoutingDecision decision = routingService.route(
            cluster(),
            null,
            List.of(message(8L, "В сервисе поменялись лимиты.")),
            classifier(0.8, true, 30, 80, 10, "News-like update")
        );

        assertThat(decision.contentType()).isEqualTo(ContentType.USEFUL_INFO);
        assertThat(decision.contentSubtype()).isEqualTo("ANNOUNCEMENT");
        assertThat(decision.shouldCreateMaterial()).isTrue();
        assertThat(decision.shouldGenerateFullGuide()).isFalse();
        assertThat(decision.providerId()).isEqualTo(7L);
    }

    private void stubUnavailableLlm() {
        when(modelCallService.complete(eq(ModelCallPurpose.CLUSTER_CONTENT_ROUTING), any(), any(), anyDouble(), anyInt()))
            .thenReturn(ModelCallService.ModelCallResult.failed(ModelCallPurpose.CLUSTER_CONTENT_ROUTING, "no provider"));
    }

    private TopicDiscussionClusterEntity cluster() {
        TopicDiscussionClusterEntity cluster = new TopicDiscussionClusterEntity();
        cluster.setId(10L);
        cluster.setGroupId(20L);
        cluster.setStartAt(Instant.parse("2026-06-19T10:00:00Z"));
        cluster.setEndAt(Instant.parse("2026-06-19T10:05:00Z"));
        cluster.setSafetyCategory("normal");
        return cluster;
    }

    private MessageEntity message(Long id, String text) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setGroupId(20L);
        message.setMessageDate(Instant.parse("2026-06-19T10:00:00Z").plusSeconds(id));
        message.setText(text);
        return message;
    }

    private ClassifierResult classifier(
        double score,
        boolean guideCandidate,
        int guidePotential,
        int novelty,
        int risk,
        String reason
    ) {
        return new ClassifierResult(
            score,
            true,
            List.of(),
            guideCandidate,
            List.of(),
            reason,
            50,
            40,
            20,
            guidePotential,
            novelty,
            50,
            0,
            reason,
            reason,
            null,
            List.of(),
            List.of(),
            List.of(),
            risk > 70 ? List.of("risk") : List.of()
        );
    }
}
