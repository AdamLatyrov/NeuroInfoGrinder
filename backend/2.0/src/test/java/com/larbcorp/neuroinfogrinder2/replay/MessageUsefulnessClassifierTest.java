package com.larbcorp.neuroinfogrinder2.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageUsefulnessClassifierTest {
    private final MessageUsefulnessClassifier classifier = new MessageUsefulnessClassifier();

    @Test
    void GptModelReleaseClassifiesAsNewsUpdateCandidate() {
        String text = "OpenAI выпустила preview GPT-5.6 Sol: Sol Ultra показала 91.9% в benchmark, "
                + "Terra и Luna выйдут позже, публичный доступ обещают после тестирования. "
                + "Источник: https://openai.com/index/previewing-gpt-5-6-sol/";

        MessageUsefulnessResult result = classifier.classify(text, "PRICING_OR_ACCESS_SIGNAL", 0.70, true);

        assertThat(result.contentClass()).isEqualTo("NEWS_UPDATE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isIn("SUMMARY", "REFERENCE");
        assertThat(result.rejectReason()).isNull();
        assertThat(result.overallScore()).isGreaterThanOrEqualTo(0.55);
    }

    @Test
    void GithubResourceClassifiesAsResourceReferenceCandidate() {
        String text = "OpenMontage на GitHub: open-source video-agent pipeline для AI production. "
                + "Есть 12 пайплайнов, 52 tools, интеграции Kling, Runway, FLUX, ElevenLabs и Suno, "
                + "можно оценить license, activity и security перед внедрением. https://github.com/example/openmontage";

        MessageUsefulnessResult result = classifier.classify(text, "RESOURCE_LINK_COLLECTION", 0.72, true);

        assertThat(result.contentClass()).isEqualTo("RESOURCE_REFERENCE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("REFERENCE");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void GithubResourceWithHiddenLinkContextClassifiesAsResourceReferenceCandidate() {
        String text = "На GitHub вирусится open-source система из ИИ-агентов для полного production видео. "
                + "Есть 12 пайплайнов, 52 инструмента и 500 скиллов, поддерживает Kling, Runway, FLUX, "
                + "ElevenLabs и Suno, открытый исходный код и много звезд.";

        MessageUsefulnessResult result = classifier.classify(text, "RESOURCE_LINK_COLLECTION", 0.72, true);

        assertThat(result.contentClass()).isEqualTo("RESOURCE_REFERENCE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("REFERENCE");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void CodexOutageClassifiesAsStatusOutageCandidate() {
        String text = "Codex снова failing: status page показывает degraded service, GitHub issues растут, "
                + "у части аккаунтов quota exhausted. Проверь official status, account limits и fallback.";

        MessageUsefulnessResult result = classifier.classify(text, "ERROR_LOG_WITH_FIX", 0.68, true);

        assertThat(result.contentClass()).isEqualTo("STATUS_OUTAGE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isIn("GUIDE", "SUMMARY");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void ProxyRiskPostClassifiesAsPracticalGuideCandidate() {
        String text = "AI API proxy может заявлять Gemini-2.5, но маршрутизировать на другую модель. "
                + "Сравни benchmark/quality, проверь privacy логов, не отправляй medical/legal/private data, "
                + "и сверяй claims с official provider docs.";

        MessageUsefulnessResult result = classifier.classify(text, "API_OR_CONFIG_SNIPPET", 0.70, true);

        assertThat(result.contentClass()).isIn("PRACTICAL_GUIDE", "RESOURCE_REFERENCE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isIn("GUIDE", "REFERENCE");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void LinkOnlyMessageRejectsWithNeedsLinkEnrichment() {
        String text = "https://lolz.live/threads/10020722/\n\nПочитайте на досуге, полезно";

        MessageUsefulnessResult result = classifier.classify(text, "RESOURCE_LINK_COLLECTION", 0.60, true);

        assertThat(result.contentClass()).isEqualTo("LINK_ONLY");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("NEEDS_LINK_ENRICHMENT");
    }

    @Test
    void PromoAloneRejectsWithoutCandidate() {
        String text = "PlusVibeAPI - платформа для работы с Claude Opus, GPT-5.5 и Gemini. "
                + "Регистрация по ссылке, бонус на баланс, скидка и быстрый старт.";

        MessageUsefulnessResult result = classifier.classify(text, "PRICING_OR_ACCESS_SIGNAL", 0.60, true);

        assertThat(result.contentClass()).isEqualTo("PROMO_ALONE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("PROMO_ALONE");
    }

    @Test
    void ReferralBotAbuseRejectsAsFraud() {
        String text = "Можно купить 500 ботов за 8 рублей и накрутить рефералку, чтобы фармить бонусы.";

        MessageUsefulnessResult result = classifier.classify(text, "NOISE_OR_CHAT", 0.40, false);

        assertThat(result.contentClass()).isEqualTo("ABUSE_OR_FRAUD");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("ABUSE_OR_FRAUD");
    }

    @Test
    void AccessCircumventionRejectsAsManualOnlyRisk() {
        String text = "В Claude iOS есть loophole: можно bypass restricted access к Fable 5 через Claude Code, "
                + "дальше пошагово запустить remote control и получить доступ.";

        MessageUsefulnessResult result = classifier.classify(text, "PRICING_OR_ACCESS_SIGNAL", 0.65, true);

        assertThat(result.contentClass()).isEqualTo("ACCESS_CIRCUMVENTION");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("RISK_SENSITIVE_MANUAL_ONLY");
    }

    @Test
    void EntityOnlyRejectsWithoutGenericTooShort() {
        MessageUsefulnessResult result = classifier.classify("sensors_discord_bot", "NOISE_OR_CHAT", 0.20, false);

        assertThat(result.contentClass()).isEqualTo("ENTITY_ONLY");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("ENTITY_ONLY");
    }
}
