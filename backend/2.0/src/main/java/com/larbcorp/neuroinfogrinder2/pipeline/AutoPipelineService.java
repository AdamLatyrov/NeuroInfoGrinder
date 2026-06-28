package com.larbcorp.neuroinfogrinder2.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AutoPipelineService {
    private static final Logger log = LoggerFactory.getLogger(AutoPipelineService.class);

    private final JdbcTemplate jdbc;
    private final PipelineLiveService pipelineLiveService;
    private final ObjectMapper objectMapper;
    private static volatile OffsetDateTime lastSchedulerTickAt;

    public AutoPipelineService(JdbcTemplate jdbc, PipelineLiveService pipelineLiveService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.pipelineLiveService = pipelineLiveService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void onRawMessageStored(long rawMessageId, long accountId, long telegramChatId, Long topicId) {
        Long messageThreadId = jdbc.query("SELECT message_thread_id FROM raw_messages WHERE id = ?", rs -> rs.next() ? nullableLong(rs, "message_thread_id") : null, rawMessageId);
        pipelineLiveService.ensureLiveState(rawMessageId, accountId, telegramChatId, topicId, messageThreadId, "TELEGRAM_LIVE");
        if (!pipelineLiveService.canProcessChat(accountId, telegramChatId, topicId, "TELEGRAM_LIVE")) {
            return;
        }
        AutoPipelineSetting setting = effectiveSetting(accountId, telegramChatId, topicId);
        long batchId = collectingBatch(accountId, telegramChatId, topicId, setting);
        jdbc.update("""
                INSERT INTO auto_pipeline_queue (raw_message_id, account_id, telegram_chat_id, topic_id, batch_id, status, reason)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 'AUTO_PIPELINE_ENABLED')
                ON CONFLICT (raw_message_id) DO NOTHING
                """, rawMessageId, accountId, telegramChatId, topicId, batchId);
        jdbc.update("""
                UPDATE pipeline_message_intake
                SET status = 'QUEUED', reason = 'AUTO_PIPELINE_ENABLED', updated_at = now()
                WHERE raw_message_id = ? AND status NOT IN ('PROCESSED', 'FAILED', 'SKIPPED')
                """, rawMessageId);
        jdbc.update("""
                UPDATE auto_pipeline_batches
                SET message_count = (SELECT count(*) FROM auto_pipeline_queue WHERE batch_id = ?), updated_at = now()
                WHERE id = ?
                """, batchId, batchId);
        Integer count = jdbc.queryForObject("SELECT message_count FROM auto_pipeline_batches WHERE id = ?", Integer.class, batchId);
        if (count != null && count >= setting.batchSize()) {
            createRun(batchId, setting, "BATCH_SIZE_REACHED");
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void flushReadyBatches() {
        lastSchedulerTickAt = OffsetDateTime.now();
        List<Long> batchIds = jdbc.queryForList("""
                SELECT b.id
                FROM auto_pipeline_batches b
                LEFT JOIN LATERAL (
                    SELECT s.debounce_seconds, s.batch_size
                    FROM auto_pipeline_settings s
                    WHERE s.account_id = b.account_id
                      AND s.enabled = true
                      AND s.telegram_chat_id = b.telegram_chat_id
                      AND (s.topic_id IS NOT DISTINCT FROM b.topic_id OR s.topic_id IS NULL)
                    ORDER BY CASE
                        WHEN s.telegram_chat_id = b.telegram_chat_id AND s.topic_id IS NOT DISTINCT FROM b.topic_id THEN 1
                        WHEN s.telegram_chat_id = b.telegram_chat_id AND s.topic_id IS NULL THEN 2
                        ELSE 3
                    END
                    LIMIT 1
                ) s ON true
                WHERE b.status = 'COLLECTING'
                  AND b.message_count > 0
                  AND EXISTS (
                      SELECT 1
                      FROM telegram_chats c
                      WHERE c.account_id = b.account_id
                        AND c.telegram_chat_id = b.telegram_chat_id
                        AND c.is_enabled = true
                        AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                  )
                  AND (b.message_count >= COALESCE(s.batch_size, 10) OR b.created_at <= now() - make_interval(secs => GREATEST(10, LEAST(COALESCE(s.debounce_seconds, 30), 300))))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM auto_pipeline_batches active_batch
                      JOIN replay_runs active_run ON active_run.id = active_batch.replay_run_id
                      WHERE active_batch.account_id = b.account_id
                        AND active_batch.telegram_chat_id IS NOT DISTINCT FROM b.telegram_chat_id
                        AND active_batch.topic_id IS NOT DISTINCT FROM b.topic_id
                        AND active_run.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                        AND active_run.status IN ('PENDING', 'RUNNING')
                  )
                ORDER BY b.created_at
                LIMIT 10
                """, Long.class);
        for (Long batchId : batchIds) {
            AutoPipelineSetting setting = settingForBatch(batchId);
            if (setting != null) {
                createRun(batchId, setting, "DEBOUNCE_EXPIRED");
            }
        }
    }

    @Scheduled(fixedDelay = 7000)
    @Transactional
    public void startPendingLiveAutoRuns() {
        List<Long> runIds = jdbc.queryForList("""
                SELECT rr.id
                FROM replay_runs rr
                JOIN auto_pipeline_batches b ON b.replay_run_id = rr.id
                WHERE rr.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                  AND rr.status = 'PENDING'
                  AND b.status = 'RUN_CREATED'
                ORDER BY rr.id
                LIMIT 5
                """, Long.class);
        for (Long runId : runIds) {
            try {
                pipelineLiveService.startLiveAutoRun(runId);
            } catch (RuntimeException exception) {
                log.warn("Failed to start live auto replay run {}", runId, exception);
            }
        }
    }

    private AutoPipelineSetting explicitSetting(long accountId, long telegramChatId, Long topicId) {
        List<AutoPipelineSetting> rows = jdbc.query("""
                SELECT account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd
                    FROM auto_pipeline_settings
                    WHERE account_id = ?
                      AND enabled = true
                  AND telegram_chat_id = ?
                  AND (topic_id IS NOT DISTINCT FROM ? OR topic_id IS NULL)
                ORDER BY CASE
                    WHEN telegram_chat_id = ? AND topic_id IS NOT DISTINCT FROM ? THEN 1
                    WHEN telegram_chat_id = ? AND topic_id IS NULL THEN 2
                    ELSE 3
                END
                LIMIT 1
                """, (rs, rowNum) -> new AutoPipelineSetting(
                rs.getLong("account_id"),
                nullableLong(rs, "telegram_chat_id"),
                nullableLong(rs, "topic_id"),
                rs.getBoolean("enabled"),
                rs.getInt("debounce_seconds"),
                rs.getInt("batch_size"),
                rs.getInt("max_provider_calls"),
                rs.getBigDecimal("max_cost_usd")
        ), accountId, telegramChatId, topicId, telegramChatId, topicId, telegramChatId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AutoPipelineSetting effectiveSetting(long accountId, long telegramChatId, Long topicId) {
        AutoPipelineSetting setting = explicitSetting(accountId, telegramChatId, topicId);
        return setting != null ? setting : new AutoPipelineSetting(accountId, telegramChatId, topicId, true, 30, 10, 10, BigDecimal.ONE);
    }

    private AutoPipelineSetting settingForBatch(long batchId) {
        List<AutoPipelineSetting> rows = jdbc.query("""
                SELECT b.account_id,
                       b.telegram_chat_id,
                       b.topic_id,
                       COALESCE(s.enabled, true) AS enabled,
                       COALESCE(s.debounce_seconds, 30) AS debounce_seconds,
                       COALESCE(s.batch_size, 10) AS batch_size,
                       COALESCE(s.max_provider_calls, 10) AS max_provider_calls,
                       COALESCE(s.max_cost_usd, 1.0) AS max_cost_usd
                FROM auto_pipeline_batches b
                LEFT JOIN LATERAL (
                    SELECT s.enabled, s.debounce_seconds, s.batch_size, s.max_provider_calls, s.max_cost_usd
                    FROM auto_pipeline_settings s
                    WHERE s.account_id = b.account_id
                      AND s.enabled = true
                      AND s.telegram_chat_id = b.telegram_chat_id
                      AND (s.topic_id IS NOT DISTINCT FROM b.topic_id OR s.topic_id IS NULL)
                    ORDER BY CASE
                        WHEN s.telegram_chat_id = b.telegram_chat_id AND s.topic_id IS NOT DISTINCT FROM b.topic_id THEN 1
                        WHEN s.telegram_chat_id = b.telegram_chat_id AND s.topic_id IS NULL THEN 2
                        ELSE 3
                    END
                    LIMIT 1
                ) s ON true
                WHERE b.id = ?
                LIMIT 1
                """, (rs, rowNum) -> new AutoPipelineSetting(
                rs.getLong("account_id"),
                nullableLong(rs, "telegram_chat_id"),
                nullableLong(rs, "topic_id"),
                rs.getBoolean("enabled"),
                rs.getInt("debounce_seconds"),
                rs.getInt("batch_size"),
                rs.getInt("max_provider_calls"),
                rs.getBigDecimal("max_cost_usd")
        ), batchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long collectingBatch(long accountId, Long telegramChatId, Long topicId, AutoPipelineSetting setting) {
        List<Long> existing = jdbc.queryForList("""
                SELECT id
                FROM auto_pipeline_batches
                WHERE account_id = ?
                  AND telegram_chat_id IS NOT DISTINCT FROM ?
                  AND topic_id IS NOT DISTINCT FROM ?
                  AND status = 'COLLECTING'
                ORDER BY created_at DESC
                LIMIT 1
                """, Long.class, accountId, telegramChatId, topicId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO auto_pipeline_batches (account_id, telegram_chat_id, topic_id, status, ready_at)
                VALUES (?, ?, ?, 'COLLECTING', now() + make_interval(secs => ?))
                RETURNING id
                """, Long.class, accountId, telegramChatId, topicId, setting.debounceSeconds()));
    }

    private void createRun(long batchId, AutoPipelineSetting setting, String reason) {
        Long currentRunId = jdbc.query("SELECT replay_run_id FROM auto_pipeline_batches WHERE id = ?", rs -> rs.next() ? nullableLong(rs, "replay_run_id") : null, batchId);
        if (currentRunId != null) {
            return;
        }
        Boolean hasActiveRun = jdbc.query("""
                SELECT EXISTS (
                    SELECT 1
                    FROM auto_pipeline_batches b
                    JOIN auto_pipeline_batches active_batch ON active_batch.account_id = b.account_id
                        AND active_batch.telegram_chat_id IS NOT DISTINCT FROM b.telegram_chat_id
                        AND active_batch.topic_id IS NOT DISTINCT FROM b.topic_id
                    JOIN replay_runs active_run ON active_run.id = active_batch.replay_run_id
                    WHERE b.id = ?
                      AND active_batch.id <> b.id
                      AND active_run.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                      AND active_run.status IN ('PENDING', 'RUNNING')
                )
                """, rs -> rs.next() && rs.getBoolean(1), batchId);
        if (Boolean.TRUE.equals(hasActiveRun)) {
            return;
        }
        String metadataJson = metadataJson(batchId, setting.accountId(), setting.telegramChatId(), setting.topicId(), reason);
        long datasetId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO datasets (name, source, source_kind, description, created_by, metadata_json, message_count)
                SELECT 'live auto raw messages batch ' || ?, 'RAW_MESSAGES', 'LIVE_AUTO_RAW_MESSAGES', ?, 'auto-pipeline', ?::jsonb,
                       count(*)
                FROM auto_pipeline_queue
                WHERE batch_id = ?
                RETURNING id
                """, Long.class, batchId, "Auto-created from new Telegram raw messages", metadataJson, batchId));
        jdbc.update("""
                INSERT INTO dataset_messages (dataset_id, source_message_id, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id, chat_title, sender_id, sender_name, sender_username, message_date, reply_to_message_id, text, caption, content_type, raw_json, entities_json, media_json)
                SELECT ?, m.account_id || ':' || m.telegram_chat_id || ':' || m.telegram_message_id,
                       m.account_id, m.telegram_chat_id, m.telegram_message_id, m.telegram_topic_id,
                       c.title, m.sender_id, m.sender_name, m.sender_username, m.message_date, m.reply_to_message_id,
                       m.text, m.caption, m.content_type, m.raw_json, '[]'::jsonb, '[]'::jsonb
                FROM auto_pipeline_queue q
                JOIN raw_messages m ON m.id = q.raw_message_id
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                WHERE q.batch_id = ?
                ON CONFLICT DO NOTHING
                """, datasetId, batchId);
        long runId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO replay_runs (dataset_id, run_name, pipeline_version, config_snapshot_json, status, metrics_json)
                VALUES (?, ?, 'LIVE_AUTO_RAW_MESSAGES', jsonb_build_object('autoPipelineBatchId', ?, 'maxProviderCalls', ?, 'maxCostUsd', ?, 'autoPublish', false), 'PENDING', '{}'::jsonb)
                RETURNING id
                """, Long.class, datasetId, "live auto raw messages batch " + batchId, batchId, setting.maxProviderCalls(), setting.maxCostUsd()));
        jdbc.update("UPDATE auto_pipeline_batches SET status = 'RUN_CREATED', replay_run_id = ?, updated_at = now() WHERE id = ?", runId, batchId);
        jdbc.update("UPDATE auto_pipeline_queue SET status = 'RUN_CREATED', updated_at = now() WHERE batch_id = ?", batchId);
        jdbc.update("""
                UPDATE pipeline_message_intake i
                SET status = 'QUEUED', reason = 'RUN_CREATED', replay_run_id = ?, updated_at = now()
                FROM auto_pipeline_queue q
                WHERE q.raw_message_id = i.raw_message_id AND q.batch_id = ?
                """, runId, batchId);
        jdbc.update("""
                UPDATE pipeline_message_trace tr
                SET replay_run_id = ?, updated_at = now()
                FROM auto_pipeline_queue q
                WHERE q.raw_message_id = tr.raw_message_id AND q.batch_id = ?
                """, runId, batchId);
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    String metadataJson(long batchId, long accountId, Long telegramChatId, Long topicId, String reason) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("batchId", batchId);
        metadata.put("accountId", accountId);
        if (telegramChatId == null) {
            metadata.putNull("telegramChatId");
        } else {
            metadata.put("telegramChatId", telegramChatId);
        }
        if (topicId == null) {
            metadata.putNull("topicId");
        } else {
            metadata.put("topicId", topicId);
        }
        metadata.put("reason", reason);
        return metadata.toString();
    }

    public static OffsetDateTime lastSchedulerTickAt() {
        return lastSchedulerTickAt;
    }

    private record AutoPipelineSetting(long accountId, Long telegramChatId, Long topicId, boolean enabled, int debounceSeconds, int batchSize, int maxProviderCalls, BigDecimal maxCostUsd) {
    }
}
