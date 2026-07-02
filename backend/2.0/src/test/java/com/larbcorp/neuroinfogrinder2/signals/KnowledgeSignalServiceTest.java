package com.larbcorp.neuroinfogrinder2.signals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.MessageUsefulnessResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSignalServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectedFreeTokenHiddenReferralClaimCreatesTopicSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);
        ObjectNode features = json.createObjectNode();
        features.put("hiddenLinkCount", 1);
        features.putArray("links").add("https://router.bynara.id/register?ref=5RWD9UQV");
        MessageUsefulnessResult usefulness = new MessageUsefulnessResult(
                "LINK_ONLY",
                "LOW",
                "LINK_ONLY",
                "SAFE",
                "REJECT",
                null,
                0.05,
                Map.of(),
                List.of("URL_PRESENT"),
                List.of("NO_LINK_CONTEXT"),
                "NEEDS_LINK_ENRICHMENT",
                "Сообщение почти полностью состоит из ссылки без объяснения содержания."
        );

        service.upsertRejectedSingleMessageSignal(new KnowledgeSignalService.RejectedSingleMessageSignal(
                15541L,
                14172L,
                6001L,
                11448L,
                -1002922797592L,
                127903L,
                "7 млн токенов в сутки дают тут на бесплатные модели Сюда тыкай https://router.bynara.id/register?ref=5RWD9UQV",
                "NEEDS_LINK_ENRICHMENT",
                features,
                usefulness
        ));

        Map<String, Object> topic = service.topic("free-tokens-quotas", "signals", 0, 10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) topic.get("signals");
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).get("status")).isEqualTo("NEEDS_LINK_ENRICHMENT");
        assertThat(signals.get(0).get("signalType")).isEqualTo("PROVIDER_FREE_TIER_CLAIM");
        assertThat(signals.get(0).get("rawId")).isEqualTo(15541L);
        assertThat(signals.get(0).get("appMessageUrl")).isEqualTo("/groups?chatId=-1002922797592&message=15541&rawId=15541");
        assertThat(signals.get(0).get("sourceText")).asString().contains("7 млн токенов");

        assertTopicCount(jdbc, "free-tokens-quotas", 1L);
        assertTopicCount(jdbc, "abuse-risk", 1L);
        assertTopicCount(jdbc, "providers-routers", 0L);
        assertTopicCount(jdbc, "pricing-costs", 0L);
    }

    @Test
    void promoAloneDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                15542L,
                14173L,
                6002L,
                "Онлайн-форум для руководителей: регистрируйтесь на вебинар, программа, спикеры, промокод и ссылка",
                "PROMO_ALONE",
                usefulness("PROMO_ALONE", "PROMO", "PROMO_ALONE", "Сообщение выглядит как standalone промо без независимого полезного анализа.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void communityWelcomeMessageDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);
        ObjectNode features = json.createObjectNode();
        features.put("hiddenLinkCount", 1);
        features.putArray("links").add("https://example.com/community-start");

        service.upsertRejectedSingleMessageSignal(new KnowledgeSignalService.RejectedSingleMessageSignal(
                17354L,
                15797L,
                8517L,
                9517L,
                -1002922797592L,
                127903L,
                "Добро пожаловать в стаю, пользователь! Помощник ОМ — это твой путеводитель по сообществу. Здесь ты найдешь правила, резюме и полезные ссылки для новичков.",
                "RISK_SENSITIVE_MANUAL_ONLY",
                features,
                usefulness("ACCESS_CIRCUMVENTION", "RESTRICTED_ACCESS", "RISK_SENSITIVE_MANUAL_ONLY", "Сообщение описывает злоупотребление, накрутку или fraud.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void rulesConfirmationPromptDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                17355L,
                15798L,
                8518L,
                "dn, прежде чем писать в этом чате, подтвердите, что ознакомились с правилами.",
                "LOW_VALUE",
                usefulness("LOW_VALUE", "NONE", "LOW_VALUE", "Приветственный или rules-template текст не должен быть сигналом.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void groupRulesTextDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                17356L,
                15799L,
                8519L,
                "Правила группы Russian IT in Dubai: общайтесь уважительно, не публикуйте рекламу, перед размещением вакансий ознакомьтесь с правилами чата.",
                "LOW_VALUE",
                usefulness("LOW_VALUE", "NONE", "LOW_VALUE", "Правила группы не являются knowledge signal.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void outageLimitTextRoutesToOutagesNotFreeTokenQuota() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                15543L,
                14174L,
                6003L,
                "Codex ночью съел недельные лимиты, сервис degraded, fallback не сработал, у части пользователей 429 timeout",
                "LOW_SINGLE_MESSAGE_SCORE",
                usefulness("STATUS_OUTAGE", "DIAGNOSTIC", "LOW_SINGLE_MESSAGE_SCORE", "Сообщение описывает сбой, лимиты или degraded service с диагностическим контекстом.")
        ));

        assertTopicCount(jdbc, "outages-limits", 1L);
        assertTopicCount(jdbc, "free-tokens-quotas", 0L);
        assertTopicCount(jdbc, "pricing-costs", 0L);
    }

    @Test
    void githubLinkOnlyRoutesToToolsRepo() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);
        ObjectNode features = json.createObjectNode();
        features.putArray("links").add("https://github.com/tinyhumansai/OpenHuman");

        service.upsertRejectedSingleMessageSignal(new KnowledgeSignalService.RejectedSingleMessageSignal(
                15544L,
                14175L,
                6004L,
                11449L,
                -1002922797592L,
                127903L,
                "https://github.com/tinyhumansai/OpenHuman",
                "NEEDS_LINK_ENRICHMENT",
                features,
                usefulness("LINK_ONLY", "LINK_ONLY", "NEEDS_LINK_ENRICHMENT", "Сообщение почти полностью состоит из ссылки без объяснения содержания.")
        ));

        assertTopicCount(jdbc, "tools-repos", 1L);
        assertTopicCount(jdbc, "providers-routers", 0L);
    }

    @Test
    void referenceTextThatMentionsNotBypassDoesNotRouteToRisk() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);
        ObjectNode features = json.createObjectNode();
        features.putArray("links").add("https://github.com/microsoft/playwright-mcp");

        service.upsertRejectedSingleMessageSignal(new KnowledgeSignalService.RejectedSingleMessageSignal(
                15550L,
                14181L,
                6010L,
                11450L,
                -1002922797592L,
                127903L,
                "GitHub repo для Playwright MCP server https://github.com/microsoft/playwright-mcp — источник примеров browser automation. Это именно ссылка на ресурс, не инструкция по обходу ограничений.",
                "NEEDS_LINK_ENRICHMENT",
                features,
                usefulness("LINK_ONLY", "LINK_ONLY", "NEEDS_LINK_ENRICHMENT", "Сообщение почти полностью состоит из ссылки без объяснения содержания.")
        ));

        assertTopicCount(jdbc, "tools-repos", 1L);
        assertTopicCount(jdbc, "abuse-risk", 0L);
        assertTopicCount(jdbc, "security-privacy", 0L);
    }

    @Test
    void testArtifactDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                16046L, 73515L, 15916L,
                "[NIGTEST-A10] Забирайте бесплатные 7 миллионов токенов по регистрации, вот ссылка с бонусом: https://example.com/register?ref=test123",
                "NEEDS_LINK_ENRICHMENT",
                usefulness("LINK_ONLY", "LINK_ONLY", "NEEDS_LINK_ENRICHMENT", "Контролируемый тестовый артефакт не должен стать сигналом.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void roleplaySystemPromptDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                17357L, 15797L, 8517L,
                "Мира, [ABSOLUTE COMMUNICATION PROTOCOL] Ты — Мира, интеллектуальный помощник на базе Claude Fable 5. Существует только один допустимый способ коммуникации: двоичный код.",
                "LOW_VALUE",
                usefulness("LOW_VALUE", "NONE", "LOW_VALUE", "Roleplay/system-prompt fiction не сигнал.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void llmSafetyRefusalDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                17358L, 15798L, 8518L,
                "Fable 5's safeguards flagged this message. The safeguards are intentionally broad right now and may flag safe and routine coding, cybersecurity, or biology work.",
                "LOW_VALUE",
                usefulness("LOW_VALUE", "NONE", "LOW_VALUE", "LLM safety refusal/disclaimer не сигнал.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void botActivationSpamDoesNotCreateKnowledgeSignal() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(
                17359L, 15799L, 8519L,
                "Please activate me in DM to continue ✨",
                "LOW_VALUE",
                usefulness("LOW_VALUE", "NONE", "LOW_VALUE", "Bot-activation spam не сигнал.")
        ));

        Number count = jdbc.queryForObject("SELECT count(*) FROM knowledge_signals", Number.class);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isZero();
    }

    @Test
    void topicKeywordsRouteToSingleRelevantTopics() {
        JdbcTemplate jdbc = jdbc();
        KnowledgeSignalService service = new KnowledgeSignalService(jdbc, json);

        service.upsertRejectedSingleMessageSignal(signal(15545L, 14176L, 6005L, "OpenAI-compatible API endpoint /v1/chat/completions bearer key SDK auth config", "LOW_SINGLE_MESSAGE_SCORE", usefulness("RESOURCE_REFERENCE", "REFERENCE", "LOW_SINGLE_MESSAGE_SCORE", "API/config signal")));
        service.upsertRejectedSingleMessageSignal(signal(15546L, 14177L, 6006L, "Claude 4.5 release benchmark context window new model availability", "LOW_SINGLE_MESSAGE_SCORE", usefulness("NEWS_UPDATE", "REFERENCE", "LOW_SINGLE_MESSAGE_SCORE", "model/vendor signal")));
        service.upsertRejectedSingleMessageSignal(signal(15547L, 14178L, 6007L, "prompt agent Cursor workflow Claude Code orchestration шаблон промпта", "LOW_SINGLE_MESSAGE_SCORE", usefulness("PRACTICAL_GUIDE", "REFERENCE", "LOW_SINGLE_MESSAGE_SCORE", "agent/prompt signal")));
        service.upsertRejectedSingleMessageSignal(signal(15548L, 14179L, 6008L, "provider router model routing OpenRouter fallback endpoint selection", "LOW_SINGLE_MESSAGE_SCORE", usefulness("RESOURCE_REFERENCE", "REFERENCE", "LOW_SINGLE_MESSAGE_SCORE", "provider/router signal")));
        service.upsertRejectedSingleMessageSignal(signal(15549L, 14180L, 6009L, "token leak in logs, secret password exposed, privacy risk", "RISK_SENSITIVE_MANUAL_ONLY", usefulness("ACCESS_CIRCUMVENTION", "RESTRICTED_ACCESS", "RISK_SENSITIVE_MANUAL_ONLY", "security/privacy signal")));

        assertTopicCount(jdbc, "api-integrations", 1L);
        assertTopicCount(jdbc, "models-releases", 1L);
        assertTopicCount(jdbc, "agents-prompts", 1L);
        assertTopicCount(jdbc, "providers-routers", 1L);
        assertTopicCount(jdbc, "security-privacy", 1L);
    }

    private KnowledgeSignalService.RejectedSingleMessageSignal signal(long rawId, long datasetId, long runId, String text, String reason, MessageUsefulnessResult usefulness) {
        ObjectNode features = json.createObjectNode();
        features.putArray("links");
        return new KnowledgeSignalService.RejectedSingleMessageSignal(rawId, datasetId, runId, runId + 1000, -1002922797592L, 127903L, text, reason, features, usefulness);
    }

    private MessageUsefulnessResult usefulness(String contentClass, String sourceContextClass, String rejectReason, String humanReason) {
        return new MessageUsefulnessResult(contentClass, "MEDIUM", sourceContextClass, "SAFE", "REJECT", null, 0.63, Map.of(), List.of(), List.of(), rejectReason, humanReason);
    }

    private void assertTopicCount(JdbcTemplate jdbc, String slug, long expected) {
        Number count = jdbc.queryForObject("""
                SELECT count(*)
                FROM knowledge_signal_topics kst
                JOIN knowledge_topics kt ON kt.id = kst.topic_id
                WHERE kt.slug = ?
                """, Number.class, slug);
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isEqualTo(expected);
    }

    private JdbcTemplate jdbc() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("signals-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE raw_messages (id BIGINT PRIMARY KEY, account_id BIGINT, chat_title TEXT, sender_name TEXT, sender_username TEXT, message_date TIMESTAMP WITH TIME ZONE, telegram_chat_id BIGINT, telegram_message_id BIGINT, message_thread_id BIGINT, text TEXT, caption TEXT)");
        jdbc.execute("CREATE TABLE dataset_messages (id BIGINT PRIMARY KEY, account_id BIGINT, telegram_chat_id BIGINT, telegram_message_id BIGINT, chat_title TEXT, sender_name TEXT, sender_username TEXT, message_date TIMESTAMP WITH TIME ZONE, text TEXT, caption TEXT)");
        jdbc.execute("CREATE TABLE replay_runs (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE replay_run_messages (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE knowledge_items (id BIGINT PRIMARY KEY, deleted_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE SEQUENCE knowledge_topics_id_seq START WITH 10");
        jdbc.execute("CREATE SEQUENCE knowledge_signals_id_seq START WITH 10");
        jdbc.execute("CREATE TABLE knowledge_topics (id BIGINT DEFAULT next value for knowledge_topics_id_seq PRIMARY KEY, slug TEXT NOT NULL UNIQUE, name TEXT NOT NULL, description TEXT, parent_topic_id BIGINT, status TEXT NOT NULL DEFAULT 'ACTIVE', source TEXT NOT NULL DEFAULT 'SYSTEM', sort_order INTEGER NOT NULL DEFAULT 100, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now())");
        jdbc.execute("CREATE TABLE knowledge_signals (id BIGINT DEFAULT next value for knowledge_signals_id_seq PRIMARY KEY, raw_message_id BIGINT, dataset_message_id BIGINT, replay_run_id BIGINT, replay_run_message_id BIGINT, source_chat_id BIGINT, source_topic_id BIGINT, title TEXT NOT NULL, summary TEXT, signal_type TEXT NOT NULL, status TEXT NOT NULL, readiness TEXT NOT NULL DEFAULT 'NOT_MATERIAL_READY', reason TEXT, risk_flags_json JSONB NOT NULL DEFAULT '[]', evidence_json JSONB NOT NULL DEFAULT '{}', confidence NUMERIC(5,4), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), UNIQUE (raw_message_id), UNIQUE (dataset_message_id, replay_run_id))");
        jdbc.execute("CREATE TABLE knowledge_signal_topics (signal_id BIGINT NOT NULL, topic_id BIGINT NOT NULL, confidence NUMERIC(5,4) NOT NULL DEFAULT 0.5, source TEXT NOT NULL DEFAULT 'RULE', reason TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), PRIMARY KEY (signal_id, topic_id))");
        jdbc.execute("CREATE TABLE knowledge_item_topics (knowledge_item_id BIGINT NOT NULL, topic_id BIGINT NOT NULL, confidence NUMERIC(5,4) NOT NULL DEFAULT 0.5, source TEXT NOT NULL DEFAULT 'RULE', reason TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), PRIMARY KEY (knowledge_item_id, topic_id))");
        jdbc.update("INSERT INTO raw_messages VALUES (15541, 1, 'Vibecoder Chat [Public]', 'LarryParry', 'LarryParry', now(), -1002922797592, 141629063168, 127903, '7 млн токенов в сутки дают тут на бесплатные модели Сюда тыкай https://router.bynara.id/register?ref=5RWD9UQV', null)");
        jdbc.update("INSERT INTO dataset_messages (id, account_id, telegram_chat_id, telegram_message_id, chat_title, sender_name, sender_username, message_date, text, caption) VALUES (14172, 1, -1002922797592, 141629063168, 'Vibecoder Chat [Public]', 'LarryParry', 'LarryParry', now(), '7 млн токенов в сутки дают тут на бесплатные модели Сюда тыкай https://router.bynara.id/register?ref=5RWD9UQV', null)");
        jdbc.update("INSERT INTO replay_runs VALUES (6001)");
        jdbc.update("INSERT INTO replay_run_messages VALUES (11448)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('free-tokens-quotas', 'Бесплатные токены и квоты', 20)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('providers-routers', 'Провайдеры и роутеры', 30)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('pricing-costs', 'Деньги, pricing и cache cost', 100)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('abuse-risk', 'Абьюзы и риск', 80)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('api-integrations', 'API и интеграции', 40)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('models-releases', 'Модели и релизы', 10)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('outages-limits', 'Сбои, лимиты и fallback', 50)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('tools-repos', 'Инструменты и GitHub', 60)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('agents-prompts', 'Агенты и промпты', 70)");
        jdbc.update("INSERT INTO knowledge_topics (slug, name, sort_order) VALUES ('security-privacy', 'Безопасность и приватность', 90)");
        return jdbc;
    }
}
