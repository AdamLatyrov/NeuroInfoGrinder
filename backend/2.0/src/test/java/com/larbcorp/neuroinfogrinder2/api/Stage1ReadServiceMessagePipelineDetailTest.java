package com.larbcorp.neuroinfogrinder2.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Stage1ReadServiceMessagePipelineDetailTest {
    @Test
    void resolvesReplayRunMessageThroughDatasetMessageWhenRawMessageIdIsNull() {
        Stage1ReadService service = serviceWithFixture("DIRECT_CHAT", null, true);

        Map<String, Object> detail = service.messagePipelineDetail(2902L);

        assertThat(detail.get("rawId")).isEqualTo(2902L);
        assertThat(detail.get("datasetMessageId")).isEqualTo(1001L);
        assertThat(detail.get("replayRunMessageId")).isEqualTo(456L);
        assertThat(detail.get("pipelineStatus")).isEqualTo("MATERIAL_CREATED");
        assertThat(detail.get("appMessageUrl")).isEqualTo("/groups?chatId=866341216&message=2902&rawId=2902");
        assertThat(((Map<?, ?>) detail.get("material")).get("materialId")).isEqualTo(23L);
    }

    @Test
    void privateChatReturnsTelegramLinkReasonInsteadOfFakeLink() {
        Stage1ReadService service = serviceWithFixture("DIRECT_CHAT", null, false);

        Map<String, Object> detail = service.messagePipelineDetail(2902L);

        assertThat(detail.get("telegramMessageUrl")).isNull();
        assertThat(detail.get("telegramLinkReason")).isEqualTo("Telegram-ссылка недоступна для приватного чата");
    }

    @Test
    void providerPreviewsRedactSecrets() {
        Stage1ReadService service = serviceWithFixture("GROUP", "vibemode", true);

        Map<String, Object> detail = service.messagePipelineDetail(2902L);

        List<?> providerCalls = (List<?>) detail.get("providerCalls");

        assertThat(providerCalls).hasSize(1);
        Map<?, ?> call = (Map<?, ?>) providerCalls.get(0);
        assertThat((String) call.get("promptPreview")).contains("Bearer [REDACTED]").doesNotContain("secret-token");
    }

    private Stage1ReadService serviceWithFixture(String chatType, String username, boolean material) {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("pipeline-detail-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        schema(jdbc);
        seed(jdbc, chatType, username, material);
        return new Stage1ReadService(jdbc, new ObjectMapper());
    }

    private void schema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE telegram_chats (id BIGINT, account_id BIGINT, telegram_chat_id BIGINT, title TEXT, username TEXT, type TEXT, tdlib_chat_type TEXT, is_enabled BOOLEAN, chat_list TEXT, position_order BIGINT)");
        jdbc.execute("CREATE TABLE raw_messages (id BIGINT, account_id BIGINT, telegram_chat_id BIGINT, telegram_message_id BIGINT, telegram_topic_id BIGINT, message_thread_id BIGINT, topic_title TEXT, message_date TIMESTAMP WITH TIME ZONE, ingested_at TIMESTAMP WITH TIME ZONE, text TEXT, caption TEXT, chat_title TEXT)");
        jdbc.execute("CREATE TABLE dataset_messages (id BIGINT, account_id BIGINT, telegram_chat_id BIGINT, telegram_message_id BIGINT)");
        jdbc.execute("CREATE TABLE replay_run_messages (id BIGINT, run_id BIGINT, dataset_message_id BIGINT, raw_message_id BIGINT, status TEXT, decision TEXT, final_decision TEXT, rule_decision TEXT, scores_json JSON, labels_json JSON, rule_labels_json JSON, single_message_rejection_reason TEXT, single_message_score_breakdown_json JSON, knowledge_item_id BIGINT, microcluster_id BIGINT, macrocluster_id BIGINT, llm_used BOOLEAN, llm_skip_reason TEXT, embedding_id BIGINT, created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE discussion_segments (id BIGINT, source_count INTEGER, combined_score NUMERIC, decision TEXT, rejection_reason TEXT, proposed_material_type TEXT, signals_json JSON, suppression_reasons_json JSON, start_message_date TIMESTAMP WITH TIME ZONE, end_message_date TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE discussion_segment_sources (discussion_segment_id BIGINT, raw_message_id BIGINT, dataset_message_id BIGINT, replay_run_message_id BIGINT, order_index INTEGER, role TEXT, text_preview TEXT, message_date TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE knowledge_items (id BIGINT, title TEXT, artifact_type TEXT, item_type TEXT, status TEXT, deleted_at TIMESTAMP WITH TIME ZONE, source_cluster_type TEXT, source_cluster_id BIGINT)");
        jdbc.execute("CREATE TABLE knowledge_item_sources (knowledge_item_id BIGINT, dataset_message_id BIGINT)");
        jdbc.execute("CREATE TABLE provider_calls (id BIGINT, run_id BIGINT, stage TEXT, model_name TEXT, status TEXT, latency_ms BIGINT, error_code TEXT, error_message TEXT, request_preview TEXT, response_preview TEXT, response_json JSON, created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE pipeline_message_trace (id BIGINT, raw_message_id BIGINT, stage_id TEXT, stage_name TEXT, status TEXT, error_code TEXT, error_message TEXT, started_at TIMESTAMP WITH TIME ZONE, finished_at TIMESTAMP WITH TIME ZONE, duration_ms BIGINT, created_at TIMESTAMP WITH TIME ZONE)");
    }

    private void seed(JdbcTemplate jdbc, String chatType, String username, boolean material) {
        jdbc.update("INSERT INTO telegram_chats VALUES (29, 1, 866341216, 'Vibemode', ?, ?, ?, TRUE, 'MAIN', 1)", username, chatType, chatType.equals("DIRECT_CHAT") ? "PRIVATE" : "SUPERGROUP");
        jdbc.update("INSERT INTO raw_messages VALUES (2902, 1, 866341216, 537027149824, NULL, NULL, 'API / Cursor', now(), now(), 'message text', NULL, 'Vibemode')");
        jdbc.update("INSERT INTO dataset_messages VALUES (1001, 1, 866341216, 537027149824)");
        jdbc.update("INSERT INTO replay_run_messages VALUES (456, 247, 1001, NULL, 'PROCESSED', 'SINGLE_MESSAGE_MATERIAL_CANDIDATE', NULL, NULL, '{}', '[]', '[\"API_SIGNAL\"]', NULL, '{\"totalScore\":0.91}', NULL, NULL, NULL, TRUE, NULL, 77, now(), now())");
        jdbc.update("INSERT INTO discussion_segments VALUES (118, 6, 0.82, 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE', NULL, 'GUIDE', '[\"api_provider\"]', '[]', now(), now(), now())");
        jdbc.update("INSERT INTO discussion_segment_sources VALUES (118, 2902, 1001, 456, 1, 'question', 'message text', now())");
        if (material) {
            jdbc.update("INSERT INTO knowledge_items VALUES (23, 'Title', 'GUIDE', 'GUIDE', 'DRAFT', NULL, 'DISCUSSION_SEGMENT', 118)");
            jdbc.update("INSERT INTO knowledge_item_sources VALUES (23, 1001)");
        }
        jdbc.update("INSERT INTO provider_calls VALUES (107, 247, 'LLM_CLUSTER_JUDGE_AND_ROUTING', 'gpt-5.5', 'SUCCESS', 3200, NULL, NULL, 'Authorization: Bearer secret-token', 'ok', '{\"decision\":\"DISCUSSION_SEGMENT_MATERIAL_CANDIDATE\",\"confidence\":0.82,\"reason\":\"Useful troubleshooting guide\"}', now())");
    }
}
