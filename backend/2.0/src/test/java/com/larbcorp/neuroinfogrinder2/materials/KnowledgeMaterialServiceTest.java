package com.larbcorp.neuroinfogrinder2.materials;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeMaterialServiceTest {
    @Test
    void listCountsUnknownAndLegacyTypesAsOther() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("material-counts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        createSchema(jdbc);
        seedMaterialCounts(jdbc);
        KnowledgeMaterialService service = new KnowledgeMaterialService(jdbc, new ObjectMapper());

        Map<String, Object> result = service.list(null, null, 0, 50);

        assertThat(result.get("totalElements")).isEqualTo(7L);
        Map<?, ?> counts = (Map<?, ?>) result.get("counts");
        assertThat(counts.get("total")).isEqualTo(7L);
        Map<String, Long> byType = typedLongMap(counts.get("byType"));
        assertThat(byType).containsEntry("GUIDE", 1L)
                .containsEntry("GENERATION", 1L)
                .containsEntry("ANSWER", 1L)
                .containsEntry("SUMMARY", 1L)
                .containsEntry("OTHER", 3L);
        assertThat(byType.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(7L);
        assertThat(typedLongMap(counts.get("byStatus"))).containsEntry("DRAFT", 6L).containsEntry("PUBLISHED", 1L);
    }

    @Test
    void listCanFilterOtherWithoutDroppingLegacyRows() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("material-other-filter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        createSchema(jdbc);
        seedMaterialCounts(jdbc);
        KnowledgeMaterialService service = new KnowledgeMaterialService(jdbc, new ObjectMapper());

        Map<String, Object> result = service.list(null, "OTHER", 0, 50);

        assertThat(result.get("totalElements")).isEqualTo(3L);
        assertThat((List<?>) result.get("content")).hasSize(3);
        Map<String, Long> byType = typedLongMap(((Map<?, ?>) result.get("counts")).get("byType"));
        assertThat(byType).containsEntry("OTHER", 3L);
        assertThat(byType.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(3L);
    }

    @Test
    void statusFilterUsesSameBaseForTypeAndStatusCounts() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("material-status-counts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        createSchema(jdbc);
        seedMaterialCounts(jdbc);
        KnowledgeMaterialService service = new KnowledgeMaterialService(jdbc, new ObjectMapper());

        Map<String, Object> result = service.list("DRAFT", null, 0, 50);

        assertThat(result.get("totalElements")).isEqualTo(6L);
        Map<?, ?> counts = (Map<?, ?>) result.get("counts");
        assertThat(counts.get("total")).isEqualTo(6L);
        assertThat(typedLongMap(counts.get("byStatus"))).containsEntry("DRAFT", 6L).doesNotContainKey("PUBLISHED");
        Map<String, Long> byType = typedLongMap(counts.get("byType"));
        assertThat(byType.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(6L);
    }

    @Test
    void discussionSegmentDetailExposesOrderedSourcesAndHowBuiltSteps() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("material-detail;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        createSchema(jdbc);
        seedDiscussionMaterial(jdbc);
        KnowledgeMaterialService service = new KnowledgeMaterialService(jdbc, new ObjectMapper());

        Map<String, Object> detail = service.detail(23L);

        assertThat(detail.get("candidateType")).isEqualTo("DISCUSSION_SEGMENT");
        assertThat(detail.get("sourceCount")).isEqualTo(2L);
        assertThat(detail.get("segmentId")).isEqualTo(118L);
        assertThat(detail.get("clusterId")).isNull();
        assertThat((List<?>) detail.get("sourceMessages")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) detail.get("sourceMessages")).get(0)).get("orderIndex")).isEqualTo(0);
        assertThat(((Map<?, ?>) ((List<?>) detail.get("sourceMessages")).get(1)).get("orderIndex")).isEqualTo(1);
        assertThat(((Map<?, ?>) ((List<?>) detail.get("sourceMessages")).get(0)).get("replayRunMessageId")).isEqualTo(501L);
        assertThat(((Map<?, ?>) ((List<?>) detail.get("sourceMessages")).get(0)).get("role")).isEqualTo("question");
        assertThat((List<?>) detail.get("howBuiltSteps")).hasSizeGreaterThanOrEqualTo(5);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE knowledge_items (
                  id BIGINT PRIMARY KEY, run_id BIGINT NOT NULL, cluster_id BIGINT, item_type TEXT,
                  title TEXT, summary TEXT, content_json JSONB DEFAULT '{}', confidence NUMERIC,
                  source_message_ids JSONB DEFAULT '[]', provider_call_id BIGINT, created_at TIMESTAMP WITH TIME ZONE,
                  source_cluster_type TEXT, source_cluster_id BIGINT, artifact_type TEXT, vertical TEXT,
                  body_json JSONB DEFAULT '{}', usefulness_score NUMERIC, pain_score NUMERIC,
                  wtp_score NUMERIC, publishability_score NUMERIC, knowledge_value_score NUMERIC,
                  status TEXT, deleted_at TIMESTAMP WITH TIME ZONE, deleted_by BIGINT, deleted_reason TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE knowledge_item_sources (
                  id BIGINT PRIMARY KEY, knowledge_item_id BIGINT, dataset_message_id BIGINT,
                  source_role TEXT, quote TEXT, confidence NUMERIC, created_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute("""
                CREATE TABLE dataset_messages (
                  id BIGINT PRIMARY KEY, account_id BIGINT, telegram_chat_id BIGINT,
                  telegram_message_id BIGINT, text TEXT, caption TEXT, message_date TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute("""
                CREATE TABLE raw_messages (
                  id BIGINT PRIMARY KEY, account_id BIGINT, telegram_chat_id BIGINT,
                  telegram_message_id BIGINT, sender_name TEXT, sender_username TEXT,
                  reply_to_message_id BIGINT, message_thread_id BIGINT, topic_title TEXT,
                  text TEXT, caption TEXT, content_type TEXT, raw_json JSONB DEFAULT '{}',
                  message_date TIMESTAMP WITH TIME ZONE, chat_title TEXT
                )
                """);
        jdbc.execute("CREATE TABLE telegram_chats (account_id BIGINT, telegram_chat_id BIGINT, username TEXT, type TEXT, tdlib_chat_type TEXT)");
        jdbc.execute("""
                CREATE TABLE discussion_segments (
                  id BIGINT PRIMARY KEY, combined_score NUMERIC, decision TEXT, rejection_reason TEXT,
                  signals_json JSONB DEFAULT '[]', suppression_reasons_json JSONB DEFAULT '[]',
                  proposed_material_type TEXT, source_count INT, start_message_date TIMESTAMP WITH TIME ZONE,
                  end_message_date TIMESTAMP WITH TIME ZONE, telegram_chat_id BIGINT, forum_topic_id BIGINT,
                  message_thread_id BIGINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE discussion_segment_sources (
                  id BIGINT PRIMARY KEY, discussion_segment_id BIGINT, raw_message_id BIGINT,
                  dataset_message_id BIGINT, replay_run_message_id BIGINT, order_index INT,
                  role TEXT, text_preview TEXT, message_date TIMESTAMP WITH TIME ZONE,
                  created_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute("CREATE TABLE provider_calls (id BIGINT PRIMARY KEY, run_id BIGINT, stage TEXT, provider_id BIGINT, model_name TEXT, status TEXT, input_tokens INT, output_tokens INT, cached_tokens INT, estimated_cost_usd NUMERIC, latency_ms BIGINT, http_status INT, error_code TEXT, error_message TEXT, request_preview TEXT, response_preview TEXT, response_json JSONB DEFAULT '{}', created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE replay_runs (id BIGINT PRIMARY KEY, status TEXT, error TEXT, total_messages INT, processed_messages INT, provider_calls_total INT, created_at TIMESTAMP WITH TIME ZONE, finished_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE replay_run_messages (id BIGINT PRIMARY KEY, run_id BIGINT, dataset_message_id BIGINT)");
        jdbc.execute("CREATE TABLE pipeline_message_trace (id BIGINT PRIMARY KEY, raw_message_id BIGINT, replay_run_id BIGINT, stage_id TEXT, stage_name TEXT, status TEXT, error_code TEXT, error_message TEXT, started_at TIMESTAMP WITH TIME ZONE, finished_at TIMESTAMP WITH TIME ZONE, duration_ms BIGINT, created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE, output_json JSONB DEFAULT '{}')");
    }

    private static void seedDiscussionMaterial(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO replay_runs VALUES (2914, 'COMPLETED', NULL, 2, 2, 1, '2026-06-26T15:19:00Z', '2026-06-26T15:19:04Z')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, item_type, title, summary, confidence, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, knowledge_value_score, status) VALUES (23, 2914, 'GUIDE', 'API fallback', 'summary', 0.91, '2026-06-26T15:19:04Z', 'DISCUSSION_SEGMENT', 118, 'GUIDE', '{\"body\":\"Проверьте base_url.\"}', 0.95, 'DRAFT')");
        jdbc.update("INSERT INTO dataset_messages VALUES (1001, 1, -100, 7001, 'api тупит', NULL, '2026-06-26T10:00:00Z')");
        jdbc.update("INSERT INTO dataset_messages VALUES (1002, 1, -100, 7002, 'проверь fallback', NULL, '2026-06-26T10:01:00Z')");
        jdbc.update("INSERT INTO raw_messages VALUES (3001, 1, -100, 7001, 'Adam', 'adam', NULL, 11, 'Topic', 'api тупит', NULL, 'messageText', '{}', '2026-06-26T10:00:00Z', 'Chat')");
        jdbc.update("INSERT INTO raw_messages VALUES (3002, 1, -100, 7002, 'Pasha', 'pasha', NULL, 11, 'Topic', 'проверь fallback', NULL, 'messageText', '{}', '2026-06-26T10:01:00Z', 'Chat')");
        jdbc.update("INSERT INTO telegram_chats VALUES (1, -100, NULL, 'SUPERGROUP', 'SUPERGROUP')");
        jdbc.update("INSERT INTO knowledge_item_sources VALUES (1, 23, 1001, 'question', NULL, 0.9, '2026-06-26T15:19:00Z')");
        jdbc.update("INSERT INTO knowledge_item_sources VALUES (2, 23, 1002, 'solution', NULL, 0.9, '2026-06-26T15:19:00Z')");
        jdbc.update("INSERT INTO replay_run_messages VALUES (501, 2914, 1001)");
        jdbc.update("INSERT INTO replay_run_messages VALUES (502, 2914, 1002)");
        jdbc.update("INSERT INTO discussion_segments VALUES (118, 0.82, 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE', NULL, '[\"PROBLEM_SOLUTION\"]', '[]', 'GUIDE', 2, '2026-06-26T10:00:00Z', '2026-06-26T10:01:00Z', -100, 11, 11)");
        jdbc.update("INSERT INTO discussion_segment_sources VALUES (1, 118, 3001, 1001, 501, 0, 'question', 'api тупит', '2026-06-26T10:00:00Z', '2026-06-26T15:19:00Z')");
        jdbc.update("INSERT INTO discussion_segment_sources VALUES (2, 118, 3002, 1002, 502, 1, 'solution', 'проверь fallback', '2026-06-26T10:01:00Z', '2026-06-26T15:19:00Z')");
        jdbc.update("INSERT INTO provider_calls VALUES (107, 2914, 'KNOWLEDGE_GENERATION', 1, 'gpt-5.5', 'SUCCESS', 100, 50, 0, 0.01, 1200, 200, NULL, NULL, 'prompt', 'response', '{\"decision\":\"DISCUSSION_SEGMENT_MATERIAL_CANDIDATE\",\"confidence\":0.9,\"reason\":\"useful\"}', '2026-06-26T15:19:04Z')");
        jdbc.update("INSERT INTO pipeline_message_trace VALUES (1, 3001, 2914, 'material_generation', 'Material generation', 'PROCESSED', NULL, NULL, '2026-06-26T15:19:00Z', '2026-06-26T15:19:04Z', NULL, '2026-06-26T15:19:00Z', '2026-06-26T15:19:04Z', '{}')");
    }

    private static void seedMaterialCounts(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (1, 1, 'Guide', '2026-06-27T00:00:00Z', 'SINGLE_MESSAGE', 1, 'guide', '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (2, 1, 'Generation', '2026-06-27T00:01:00Z', 'SINGLE_MESSAGE', 2, 'generation', '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (3, 1, 'Answer', '2026-06-27T00:02:00Z', 'SINGLE_MESSAGE', 3, 'answer', '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (4, 1, 'Summary', '2026-06-27T00:03:00Z', 'SINGLE_MESSAGE', 4, 'SUMMARY', '{}', 'PUBLISHED')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (5, 1, 'Legacy summary', '2026-06-27T00:04:00Z', 'MACRO', 5, 'cluster_summary', '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (6, 1, 'Reference', '2026-06-27T00:05:00Z', 'SINGLE_MESSAGE', 6, 'REFERENCE', '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status) VALUES (7, 1, 'Null type', '2026-06-27T00:06:00Z', 'SINGLE_MESSAGE', 7, NULL, '{}', 'DRAFT')");
        jdbc.update("INSERT INTO knowledge_items (id, run_id, title, created_at, source_cluster_type, source_cluster_id, artifact_type, body_json, status, deleted_at) VALUES (8, 1, 'Deleted', '2026-06-27T00:07:00Z', 'SINGLE_MESSAGE', 8, 'GUIDE', '{}', 'DRAFT', '2026-06-27T00:08:00Z')");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> typedLongMap(Object value) {
        return (Map<String, Long>) value;
    }
}
