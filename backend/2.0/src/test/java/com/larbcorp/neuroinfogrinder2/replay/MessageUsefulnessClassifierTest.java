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
    void PromptGenerationPatternClassifiesAsGenerationMaterial() {
        String text = "Шаблон для генерации release notes: сначала собери список merged PR, затем сгруппируй изменения по Features, Fixes и Risks, "
                + "после этого сгенерируй короткий changelog без маркетинга и добавь блок rollback notes.";

        MessageUsefulnessResult result = classifier.classify(text, "PROMPT_OR_AGENT_PATTERN", 0.72, true);

        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("GENERATION");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void DirectQuestionAnswerClassifiesAsAnswerMaterial() {
        String text = "Что делать, если API возвращает 429? Ответ: сначала проверь rate limit и quota, затем включи exponential backoff, "
                + "после этого логируй retry-after и отдельно проверь, не шарится ли один ключ между несколькими сервисами.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.72, true);

        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("ANSWER");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void HowToCheckOpenAiCompatibleApiClassifiesAsGuideMaterial() {
        String text = "Как проверить OpenAI-compatible API в Cursor: 1) base URL должен заканчиваться на /v1; "
                + "2) auth header Bearer должен брать ключ из переменной окружения; "
                + "3) model id нужно сверить с ответом GET /models у провайдера; "
                + "4) если API возвращает 401 — проблема почти всегда в ключе или формате Authorization header; "
                + "5) если API возвращает 404 — чаще всего неверный model id или base URL; "
                + "6) если API возвращает 429 — проверь quota/rate limit и включи fallback provider. "
                + "После изменений перезапусти connector и сделай короткий smoke request через /chat/completions.";

        MessageUsefulnessResult result = classifier.classify(text, "API_OR_CONFIG_SNIPPET", 0.72, true);

        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("GUIDE");
        assertThat(result.rejectReason()).isNull();
    }

    @Test
    void StatusDigestClassifiesAsSummaryMaterial() {
        String text = "Итог по утреннему инциденту: API latency выросла в EU регионе, часть запросов ушла на fallback, "
                + "ошибок авторизации нет, основной вывод — проблема похожа на degraded provider routing, следующий шаг — мониторить status page.";

        MessageUsefulnessResult result = classifier.classify(text, "RAW_NEWS_LOW_ACTIONABILITY", 0.70, true);

        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("SUMMARY");
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
        String text = "Краткая сводка: Codex снова failing, status page показывает degraded service, GitHub issues растут, "
                + "у части аккаунтов quota exhausted. Проверь official status, account limits и fallback.";

        MessageUsefulnessResult result = classifier.classify(text, "ERROR_LOG_WITH_FIX", 0.68, true);

        assertThat(result.contentClass()).isIn("STATUS_SUMMARY", "STATUS_OUTAGE");
        assertThat(result.candidateRoute()).isEqualTo("SINGLE_MESSAGE");
        assertThat(result.proposedMaterialType()).isEqualTo("SUMMARY");
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
    void ReferralFreeTokenClaimRejectsWithNeedsLinkEnrichment() {
        String text = "7 млн токенов в сутки дают тут на бесплатные модели Сюда тыкай "
                + "https://router.bynara.id/register?ref=5RWD9UQV";

        MessageUsefulnessResult result = classifier.classify(text, "NOISE_OR_CHAT", 0.40, true);

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
    void RateLimitBypassAndAccountFarmRejectsAsFraud() {
        String text = "Обсуждают bypass rate limit через фарм аккаунтов и invite= referral цепочки, "
                + "плюс советуют обходить антиабьюз gateway.";

        MessageUsefulnessResult result = classifier.classify(text, "NOISE_OR_CHAT", 0.20, false);

        assertThat(result.contentClass()).isIn("ABUSE_OR_FRAUD", "ACCESS_CIRCUMVENTION");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isIn("ABUSE_OR_FRAUD", "RISK_SENSITIVE_MANUAL_ONLY");
    }

    @Test
    void TrialBypassSmsVpnTokenFlowRejectsAsManualOnlyRisk() {
        String text = "You must use SMS service to receive sms, then use vpn proxy to japan get trial, "
                + "take access token and paste it into upi qr generator.";

        MessageUsefulnessResult result = classifier.classify(text, "PRICING_OR_ACCESS_SIGNAL", 0.75, true);

        assertThat(result.contentClass()).isEqualTo("ACCESS_CIRCUMVENTION");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("RISK_SENSITIVE_MANUAL_ONLY");
    }

    @Test
    void BinAndCvvDumpRejectsAsFraud() {
        String text = "Spotify BIN BIN: 4512106824xxxxxx EXP: 03|27 CVV: 000/GEN virtual card";

        MessageUsefulnessResult result = classifier.classify(text, "NOISE_OR_CHAT", 0.20, false);

        assertThat(result.contentClass()).isEqualTo("ABUSE_OR_FRAUD");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("ABUSE_OR_FRAUD");
    }

    @Test
    void EntityOnlyRejectsWithoutGenericTooShort() {
        MessageUsefulnessResult result = classifier.classify("sensors_discord_bot", "NOISE_OR_CHAT", 0.20, false);

        assertThat(result.contentClass()).isEqualTo("ENTITY_ONLY");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("ENTITY_ONLY");
    }

    @Test
    void CommunityWelcomeTemplateRejectsAsLowValue() {
        String text = "Добро пожаловать в стаю! Помощник ОМ — это твой путеводитель по сообществу: "
                + "здесь ты найдешь базу знаний, список доступных чатов, контакты поддержки и карту контента. "
                + "Обязательно пройди онбординг для новичков.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void RulesConfirmationPromptRejectsAsLowValue() {
        String text = "dn, прежде чем писать в этом чате, подтвердите, что ознакомились с правилами.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void GroupRulesTextRejectsAsLowValue() {
        String text = "Правила группы Russian IT in Dubai: общайтесь уважительно, не публикуйте рекламу, "
                + "перед размещением вакансий ознакомьтесь с правилами чата и закрепленными сообщениями.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void ChatKeeperRussianItDubaiWelcomeRejectsAsLowValue() {
        String text = "Fayida, приветствуем вас в группе Russian IT in Dubai. ВАЖНО: при добавлении в группу "
                + "напишите о себе, компании, проектах или интересах в IT с тегом #whois в раздел General. "
                + "ТОЖЕ ВАЖНО: просим не размещать объявления и ознакомиться с правилами группы.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void ModerationBotBanMessageRejectsAsLowValue() {
        String text = "LolsBot: Злата, тебя заблокировали (antifolder). Сообщение содержит эмодзи и ссылку без контекста, "
                + "пользователь попытался вступить через общую папку с чатами.";

        MessageUsefulnessResult result = classifier.classify(text, "ERROR_LOG_WITH_FIX", 0.80, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void SafetyRefusalRejectsAsLowValue() {
        String text = "Я не могу выполнить данный запрос или участвовать в обсуждении подобных тем. "
                + "Если у вас возникли технические неполадки, предоставьте код обработчика сообщений.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }

    @Test
    void UnverifiedModelPricingClaimRejectsUntilOfficialSource() {
        String text = "Sonnet 5 выходит сегодня: цена $2 за входящий и $10 за исходящий на 1M токенов, "
                + "контекст 1M токенов, GPT-5.5 дороже, по OpenRouter те же цены.";

        MessageUsefulnessResult result = classifier.classify(text, "PRICING_OR_ACCESS_SIGNAL", 0.80, true);

        assertThat(result.contentClass()).isEqualTo("UNVERIFIED_MODEL_CLAIM");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("UNVERIFIED_MODEL_CLAIM");
    }

    @Test
    void SingleSourceJobPostRejectsAsLowValue() {
        String text = "Вакансия: директор по маркетингу в Estee Clinic, Москва. Вилка 300-500к, нужен опыт SMM, "
                + "перформанс-маркетинга и управления командой.";

        MessageUsefulnessResult result = classifier.classify(text, "QUESTION_WITH_VALUABLE_ANSWER", 0.85, true);

        assertThat(result.contentClass()).isEqualTo("LOW_VALUE");
        assertThat(result.candidateRoute()).isEqualTo("REJECT");
        assertThat(result.rejectReason()).isEqualTo("LOW_VALUE");
    }
}
