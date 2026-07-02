package com.larbcorp.neuroinfogrinder2.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.ModelWorkerClient;
import com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

@Service
public class PipelineLiveService {
    private static final List<StageSpec> STAGES = List.of(
            new StageSpec(1, "telegram_ingest", "Сбор из Telegram / Форумов", "Получение Telegram update и сохранение сообщения"),
            new StageSpec(2, "db_cache", "DB-cache", "Durable raw_messages cache для UI и pipeline"),
            new StageSpec(3, "normalization", "Нормализация", "Подготовка текста и признаков"),
            new StageSpec(4, "cleanup", "Очистка", "Фильтрация шума и пустых payload"),
            new StageSpec(5, "dedupe", "Дедупликация", "Поиск дублей и canonical message"),
            new StageSpec(6, "rule_signals", "Rule-сигналы", "Правила, ссылки, ошибки, цены и tool-сигналы"),
            new StageSpec(7, "bootstrap_classification", "Bootstrap-классификация", "Локальный bootstrap classifier"),
            new StageSpec(8, "embeddings", "Embeddings", "BGE/embedding worker"),
            new StageSpec(9, "clustering", "Кластеризация", "Semantic clustering"),
            new StageSpec(10, "single_message_detection", "Single-message детекция", "Выявление одиночных сообщений-кандидатов для материала"),
            new StageSpec(11, "llm_judge", "LLM Judge", "LLM оценка кластеров и single-message candidates"),
            new StageSpec(12, "material_generation", "Генерация материалов", "Создание draft knowledge items из кластеров и single-message"),
            new StageSpec(13, "materials_publish", "Публикация в Материалы", "Публикация готовых материалов")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModelWorkerClient workerClient;
    private final Environment environment;
    private final ReplayV2Service replayV2Service;

    public PipelineLiveService(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient workerClient, Environment environment, ReplayV2Service replayV2Service) {
        this.jdbc = jdbc;
        this.json = json;
        this.workerClient = workerClient;
        this.environment = environment;
        this.replayV2Service = replayV2Service;
    }

    @Transactional
    public void ensureLiveState(long rawMessageId, long accountId, long telegramChatId, Long topicId, Long messageThreadId, String intakeSource) {
        boolean hasText = Boolean.TRUE.equals(jdbc.query("""
                SELECT COALESCE(NULLIF(btrim(COALESCE(text, caption, '')), ''), '') <> ''
                FROM raw_messages
                WHERE id = ?
                """, rs -> rs.next() && rs.getBoolean(1), rawMessageId));
        String guardRejection = processingRejection(accountId, telegramChatId, topicId, intakeSource);
        boolean autoEnabled = guardRejection == null;
        WorkerSnapshot worker = workerSnapshot();
        String status = hasText ? (autoEnabled ? "QUEUED" : "PENDING") : "SKIPPED";
        String reason = hasText ? (autoEnabled ? "AUTO_PIPELINE_ENABLED" : guardRejection) : "NO_TEXT";
        jdbc.update("""
                INSERT INTO pipeline_message_intake (raw_message_id, account_id, telegram_chat_id, topic_id, message_thread_id, intake_source, status, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (raw_message_id) DO UPDATE SET
                    account_id = EXCLUDED.account_id,
                    telegram_chat_id = EXCLUDED.telegram_chat_id,
                    topic_id = EXCLUDED.topic_id,
                    message_thread_id = EXCLUDED.message_thread_id,
                    status = CASE
                        WHEN pipeline_message_intake.status IN ('PROCESSED', 'FAILED') THEN pipeline_message_intake.status
                        ELSE EXCLUDED.status
                    END,
                    reason = CASE
                        WHEN pipeline_message_intake.status IN ('PROCESSED', 'FAILED') THEN pipeline_message_intake.reason
                        ELSE EXCLUDED.reason
                    END,
                    updated_at = now()
                """, rawMessageId, accountId, telegramChatId, topicId, messageThreadId, intakeSource, status, reason);
        for (StageSpec stage : STAGES) {
            String stageStatus;
            String errorCode = null;
            String errorMessage = null;
            if (stage.ordinal() <= 2) {
                stageStatus = "PROCESSED";
            } else if (!hasText) {
                stageStatus = "SKIPPED";
                errorCode = "NO_TEXT";
                errorMessage = "Сообщение не содержит текста или подписи";
            } else if (autoEnabled && stage.ordinal() <= 7) {
                stageStatus = "PENDING";
                errorCode = "WAITING_FOR_BATCH";
                errorMessage = "Сообщение ждёт debounce/batch обработку";
            } else if (stage.ordinal() >= 8) {
                stageStatus = "WAITING_FOR_WORKER";
                errorCode = worker.reachable() ? "REPLAY_ARTIFACTS_NOT_CREATED" : "MODEL_WORKER_DOWN";
                errorMessage = worker.reachable()
                        ? "Воркер доступен, но replay artifacts для сообщения ещё не созданы"
                        : "Стадии нужен локальный model worker, но worker недоступен";
            } else {
                stageStatus = "PENDING";
                errorCode = autoEnabled ? null : guardRejection;
                errorMessage = autoEnabled ? null : labelForCode(guardRejection);
            }
            jdbc.update("""
                    INSERT INTO pipeline_message_trace (raw_message_id, stage_id, stage_name, status, input_json, output_json, error_code, error_message, started_at, finished_at, duration_ms)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, CASE WHEN ? THEN now() ELSE NULL END, CASE WHEN ? THEN now() ELSE NULL END, CASE WHEN ? THEN 0 ELSE NULL END)
                    ON CONFLICT (raw_message_id, stage_id) DO UPDATE SET
                        stage_name = EXCLUDED.stage_name,
                        status = CASE
                            WHEN pipeline_message_trace.status IN ('PROCESSED', 'FAILED') THEN pipeline_message_trace.status
                            ELSE EXCLUDED.status
                        END,
                        error_code = CASE
                            WHEN pipeline_message_trace.status IN ('PROCESSED', 'FAILED') THEN pipeline_message_trace.error_code
                            ELSE EXCLUDED.error_code
                        END,
                        error_message = CASE
                            WHEN pipeline_message_trace.status IN ('PROCESSED', 'FAILED') THEN pipeline_message_trace.error_message
                            ELSE EXCLUDED.error_message
                        END,
                        updated_at = now()
                    """, rawMessageId, stage.id(), stage.name(), stageStatus,
                    write(object("rawMessageId", rawMessageId, "accountId", accountId, "telegramChatId", telegramChatId)),
                    stage.ordinal() <= 2 ? write(object("stored", true)) : "{}",
                    errorCode, errorMessage, stage.ordinal() <= 2, stage.ordinal() <= 2, stage.ordinal() <= 2);
        }
    }

    public Summary summary() {
        WorkerSnapshot worker = workerSnapshot();
        WhyEmpty why = whyEmpty();
        long intakePendingTotal = countStatus("pipeline_message_intake", "PENDING");
        long pendingInsideEnabledScopes = countQuery("""
                WITH pending_scoped AS (
                  SELECT i.raw_message_id,
                          EXISTS (
                            SELECT 1
                            FROM telegram_chats c
                            WHERE c.account_id = i.account_id
                              AND c.telegram_chat_id = i.telegram_chat_id
                              AND c.is_enabled = true
                              AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                          ) AS auto_enabled_effective
                  FROM pipeline_message_intake i
                  WHERE i.status = 'PENDING'
                )
                SELECT count(*) FROM pending_scoped WHERE auto_enabled_effective = true
                """);
        long pendingOutsideEnabledScopes = Math.max(0, intakePendingTotal - pendingInsideEnabledScopes);
        long collectingBatches = countStatus("auto_pipeline_batches", "COLLECTING");
        long collectingMessages = countQuery("SELECT COALESCE(sum(message_count), 0) FROM auto_pipeline_batches WHERE status = 'COLLECTING'");
        return new Summary(
                count("raw_messages"),
                intakePendingTotal,
                countStatus("pipeline_message_intake", "QUEUED"),
                countStatus("pipeline_message_intake", "PROCESSING"),
                countStatus("pipeline_message_intake", "PROCESSED"),
                countStatus("pipeline_message_intake", "SKIPPED"),
                countStatus("pipeline_message_intake", "FAILED"),
                countStatus("pipeline_message_intake", "WAITING_FOR_WORKER"),
                countRealKnowledgeItems(),
                latestRunStatus(),
                worker.status(),
                countEnabledAutoPipeline(),
                warnings(worker),
                why.mainBlocker(),
                why.secondaryBlocker(),
                why.explanation(),
                why.nextActions(),
                countQuery("SELECT count(*) FROM raw_messages WHERE ingested_at >= now() - interval '1 minute'"),
                intakePendingTotal,
                pendingInsideEnabledScopes,
                pendingOutsideEnabledScopes,
                countStatus("auto_pipeline_queue", "PENDING"),
                collectingBatches,
                collectingMessages,
                countQuery("SELECT count(*) FROM replay_runs WHERE pipeline_version = 'LIVE_AUTO_RAW_MESSAGES' AND status = 'PENDING'"),
                countQuery("SELECT count(*) FROM replay_runs WHERE pipeline_version = 'LIVE_AUTO_RAW_MESSAGES' AND status = 'RUNNING'"),
                countQuery("SELECT count(*) FROM replay_runs WHERE pipeline_version = 'LIVE_AUTO_RAW_MESSAGES' AND status = 'COMPLETED'"),
                latestRunId(),
                latestTerminalReason(),
                AutoPipelineService.lastSchedulerTickAt(),
                maxTime("raw_messages", "ingested_at"),
                maxTimeWhere("auto_pipeline_batches", "updated_at", "status IN ('RUN_CREATED', 'PROCESSED')"),
                maxTimeWhere("replay_runs", "started_at", "pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'"),
                maxTimeWhere("replay_runs", "finished_at", "pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'")
        );
    }

    public LiveAcceptance liveAcceptance() {
        return jdbc.query("""
                WITH latest_raw AS (
                    SELECT id, ingested_at FROM raw_messages ORDER BY id DESC LIMIT 1
                ), latest_processed AS (
                    SELECT q.raw_message_id AS id, q.updated_at
                    FROM auto_pipeline_queue q
                    WHERE q.status = 'PROCESSED'
                    ORDER BY q.raw_message_id DESC
                    LIMIT 1
                ), latest_run AS (
                    SELECT rr.id, rr.status, rr.error, rr.total_messages, rr.created_at, rr.started_at, rr.finished_at
                    FROM replay_runs rr
                    WHERE rr.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                    ORDER BY rr.id DESC
                    LIMIT 1
                )
                SELECT
                    (SELECT id FROM latest_raw) AS latest_raw_id,
                    (SELECT ingested_at FROM latest_raw) AS latest_raw_at,
                    (SELECT id FROM latest_processed) AS latest_processed_raw_id,
                    (SELECT updated_at FROM latest_processed) AS latest_processed_at,
                    (SELECT count(*) FROM raw_messages WHERE id > COALESCE((SELECT id FROM latest_processed), 0)) AS raw_gap,
                    lr.id AS run_id,
                    lr.status AS run_status,
                    lr.error AS terminal_reason,
                    lr.total_messages AS message_count,
                    lr.created_at AS run_created_at,
                    lr.started_at AS run_started_at,
                    lr.finished_at AS run_finished_at,
                    COALESCE((SELECT count(*) FROM replay_run_messages rrm WHERE rrm.run_id = lr.id), 0) AS replay_run_messages,
                    COALESCE((SELECT count(*) FROM message_embeddings me WHERE me.run_id = lr.id), 0) AS message_embeddings,
                    COALESCE((SELECT count(DISTINCT rrm.microcluster_id) FROM replay_run_messages rrm WHERE rrm.run_id = lr.id AND rrm.microcluster_id IS NOT NULL), 0) AS microclusters,
                    COALESCE((SELECT count(DISTINCT rrm.macrocluster_id) FROM replay_run_messages rrm WHERE rrm.run_id = lr.id AND rrm.macrocluster_id IS NOT NULL), 0) AS macroclusters,
                    COALESCE((SELECT count(*) FROM replay_run_messages rrm WHERE rrm.run_id = lr.id AND rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE','DIRECT_MATERIAL_READY')), 0) AS single_candidates,
                    COALESCE((SELECT count(*) FROM provider_calls pc WHERE pc.run_id = lr.id), 0) AS provider_calls,
                    COALESCE((SELECT count(*) FROM knowledge_items ki WHERE ki.run_id = lr.id), 0) AS materials
                FROM latest_run lr
                """, rs -> rs.next() ? new LiveAcceptance(
                rs.getLong("latest_raw_id"),
                rs.getObject("latest_raw_at", OffsetDateTime.class),
                nullableLong(rs, "latest_processed_raw_id"),
                rs.getObject("latest_processed_at", OffsetDateTime.class),
                rs.getLong("raw_gap"),
                nullableLong(rs, "run_id"),
                rs.getString("run_status"),
                rs.getString("terminal_reason"),
                rs.getLong("message_count"),
                rs.getObject("run_created_at", OffsetDateTime.class),
                rs.getObject("run_started_at", OffsetDateTime.class),
                rs.getObject("run_finished_at", OffsetDateTime.class),
                rs.getLong("replay_run_messages"),
                rs.getLong("message_embeddings"),
                rs.getLong("microclusters"),
                rs.getLong("macroclusters"),
                rs.getLong("single_candidates"),
                rs.getLong("provider_calls"),
                rs.getLong("materials")
        ) : new LiveAcceptance(0, null, null, null, 0, null, null, null, 0, null, null, null, 0, 0, 0, 0, 0, 0, 0));
    }

    public List<LiveEvent> liveEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 200));
        return jdbc.query("""
                SELECT ('msg_' || m.id) AS event_id,
                       'MESSAGE_RECEIVED' AS type,
                       m.ingested_at AS timestamp,
                       m.account_id,
                       m.telegram_chat_id,
                       m.telegram_topic_id AS topic_id,
                       NULL::bigint AS batch_id,
                       NULL::bigint AS run_id,
                       m.id AS raw_message_id,
                       NULL::text AS stage,
                       NULL::text AS from_stage,
                       'telegram_ingest' AS to_stage,
                       'OK' AS status,
                       1::bigint AS count,
                       '+1 message received from ' || COALESCE(c.title, m.telegram_chat_id::text) AS message
                FROM raw_messages m
                LEFT JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                UNION ALL
                SELECT ('batch_' || b.id || '_' || b.status) AS event_id,
                       CASE WHEN b.status = 'COLLECTING' THEN 'BATCH_COLLECTING' WHEN b.status = 'RUN_CREATED' THEN 'BATCH_FLUSHED' ELSE 'RUN_COMPLETED' END AS type,
                       b.updated_at AS timestamp,
                       b.account_id,
                       b.telegram_chat_id,
                       b.topic_id,
                       b.id AS batch_id,
                       b.replay_run_id AS run_id,
                       NULL::bigint AS raw_message_id,
                       'batch' AS stage,
                       'db_cache' AS from_stage,
                       'normalization' AS to_stage,
                       b.status,
                       b.message_count::bigint AS count,
                       'Batch #' || b.id || ' ' || lower(b.status) || ': ' || b.message_count || ' messages' AS message
                FROM auto_pipeline_batches b
                UNION ALL
                SELECT ('run_' || rr.id || '_' || rr.status) AS event_id,
                       CASE WHEN rr.status = 'RUNNING' THEN 'RUN_STARTED' WHEN rr.status = 'COMPLETED' THEN 'RUN_COMPLETED' ELSE 'RUN_CREATED' END AS type,
                       COALESCE(rr.finished_at, rr.started_at, rr.created_at) AS timestamp,
                       b.account_id,
                       b.telegram_chat_id,
                       b.topic_id,
                       b.id AS batch_id,
                       rr.id AS run_id,
                       NULL::bigint AS raw_message_id,
                       'run' AS stage,
                       'bootstrap_classification' AS from_stage,
                       CASE WHEN rr.status = 'COMPLETED' THEN 'material_generation' ELSE 'embeddings' END AS to_stage,
                       rr.status,
                       COALESCE(rr.total_messages, b.message_count, 0)::bigint AS count,
                       'Run #' || rr.id || ' ' || lower(rr.status) || COALESCE(': ' || rr.error, '') AS message
                FROM replay_runs rr
                LEFT JOIN auto_pipeline_batches b ON b.replay_run_id = rr.id
                WHERE rr.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                ORDER BY timestamp DESC NULLS LAST
                LIMIT ?
                """, this::liveEvent, safeLimit);
    }

    public List<LiveBatch> liveBatches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        return jdbc.query("""
                SELECT b.id AS batch_id,
                       b.replay_run_id AS run_id,
                       b.account_id,
                       b.telegram_chat_id,
                       b.topic_id,
                       b.status,
                       b.message_count,
                       b.created_at,
                       b.updated_at,
                       rr.started_at,
                       rr.finished_at,
                       rr.error AS terminal_reason,
                       COALESCE((SELECT count(*) FROM knowledge_items ki WHERE ki.run_id = rr.id), 0) AS material_count,
                       COALESCE((SELECT count(*) FROM replay_run_messages rrm WHERE rrm.run_id = rr.id AND rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE', 'DIRECT_MATERIAL_READY')), 0) AS candidate_count,
                       b.error
                FROM auto_pipeline_batches b
                LEFT JOIN replay_runs rr ON rr.id = b.replay_run_id
                ORDER BY b.updated_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new LiveBatch(
                rs.getLong("batch_id"),
                nullableLong(rs, "run_id"),
                rs.getLong("account_id"),
                nullableLong(rs, "telegram_chat_id"),
                nullableLong(rs, "topic_id"),
                rs.getString("status"),
                rs.getInt("message_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getString("terminal_reason"),
                rs.getInt("material_count"),
                rs.getInt("candidate_count"),
                rs.getString("error"),
                waterfall(rs.getString("status"), rs.getString("terminal_reason"), rs.getInt("material_count"), rs.getInt("candidate_count"))
        ), safeLimit);
    }

    public List<PendingBreakdownRow> pendingBreakdown() {
        return jdbc.query("""
                SELECT i.account_id,
                       i.telegram_chat_id,
                       i.topic_id,
                       c.title AS chat_title,
                       t.title AS topic_title,
                       count(*) FILTER (WHERE i.status = 'PENDING') AS pending,
                       count(*) FILTER (WHERE q.status = 'PENDING') AS queued,
                       COALESCE((SELECT count(*) FROM auto_pipeline_batches b WHERE b.account_id = i.account_id AND b.telegram_chat_id IS NOT DISTINCT FROM i.telegram_chat_id AND b.topic_id IS NOT DISTINCT FROM i.topic_id AND b.status = 'COLLECTING'), 0) AS collecting_batches,
                       COALESCE((SELECT count(*) FROM auto_pipeline_batches b JOIN replay_runs rr ON rr.id = b.replay_run_id WHERE b.account_id = i.account_id AND b.telegram_chat_id IS NOT DISTINCT FROM i.telegram_chat_id AND b.topic_id IS NOT DISTINCT FROM i.topic_id AND rr.status = 'RUNNING'), 0) AS running_runs,
                       count(*) FILTER (WHERE i.status = 'PROCESSED') AS processed,
                       max(m.ingested_at) AS latest_raw_message_at,
                       max(q.updated_at) AS latest_queued_at,
                        EXISTS (
                            SELECT 1
                            FROM telegram_chats c2
                            WHERE c2.account_id = i.account_id
                              AND c2.telegram_chat_id = i.telegram_chat_id
                              AND c2.is_enabled = true
                              AND (c2.chat_list IN ('MAIN', 'ARCHIVE') OR c2.position_order IS NOT NULL)
                        ) AS auto_enabled_effective
                FROM pipeline_message_intake i
                LEFT JOIN raw_messages m ON m.id = i.raw_message_id
                LEFT JOIN auto_pipeline_queue q ON q.raw_message_id = i.raw_message_id
                LEFT JOIN telegram_chats c ON c.account_id = i.account_id AND c.telegram_chat_id = i.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = i.account_id AND t.telegram_chat_id = i.telegram_chat_id
                    AND t.telegram_topic_id IS NOT DISTINCT FROM i.topic_id
                GROUP BY i.account_id, i.telegram_chat_id, i.topic_id, c.title, t.title
                HAVING count(*) FILTER (WHERE i.status = 'PENDING') > 0 OR count(*) FILTER (WHERE q.status = 'PENDING') > 0
                ORDER BY pending DESC, queued DESC
                LIMIT 200
                """, (rs, rowNum) -> new PendingBreakdownRow(
                rs.getLong("account_id"),
                rs.getLong("telegram_chat_id"),
                nullableLong(rs, "topic_id"),
                rs.getString("chat_title"),
                rs.getString("topic_title"),
                rs.getBoolean("auto_enabled_effective"),
                rs.getLong("pending"),
                rs.getLong("queued"),
                rs.getLong("collecting_batches"),
                rs.getLong("running_runs"),
                rs.getLong("processed"),
                rs.getObject("latest_raw_message_at", OffsetDateTime.class),
                rs.getObject("latest_queued_at", OffsetDateTime.class)
        ));
    }

    public List<StageSummary> stages() {
        List<StageSummary> result = new ArrayList<>();
        WorkerSnapshot worker = workerSnapshot();
        long clusters = countRealClusters();
        long singleCandidates = countRealSingleMessageCandidates();
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT stage_id, status, count(*) AS c
                FROM pipeline_message_trace
                GROUP BY stage_id, status
                """, (RowCallbackHandler) rs -> {
            counts.computeIfAbsent(rs.getString("stage_id"), ignored -> new LinkedHashMap<>())
                    .put(rs.getString("status"), rs.getLong("c"));
        });
        Map<String, Long> latency = new LinkedHashMap<>();
        jdbc.query("""
                SELECT stage_id, avg(duration_ms)::BIGINT AS avg_ms
                FROM pipeline_message_trace
                WHERE duration_ms IS NOT NULL
                GROUP BY stage_id
                """, (RowCallbackHandler) rs -> latency.put(rs.getString("stage_id"), rs.getLong("avg_ms")));
        for (StageSpec spec : STAGES) {
            Map<String, Long> stageCounts = counts.getOrDefault(spec.id(), Map.of());
            long processed = stageCounts.getOrDefault("PROCESSED", 0L) + stageCounts.getOrDefault("PROCESSED_DEGRADED", 0L);
            long failed = stageCounts.getOrDefault("FAILED", 0L);
            long skipped = stageCounts.getOrDefault("SKIPPED", 0L);
            long waiting = stageCounts.getOrDefault("WAITING_FOR_WORKER", 0L) + stageCounts.getOrDefault("PENDING", 0L);
            long active = stageCounts.getOrDefault("PROCESSING", 0L);
            String status = failed > 0 ? "error" : active > 0 ? "active" : processed > 0 && waiting == 0 ? "completed" : "waiting";
            String warning = warningForStage(spec.id(), failed, waiting, processed, skipped, worker.reachable(), worker.embeddingStatus(), clusters, singleCandidates, providerConfigured());
            result.add(new StageSummary(spec.ordinal(), spec.id(), spec.name(), spec.description(), status, active, processed, failed, skipped, waiting, latency.get(spec.id()), lastUpdated(spec.id()), warning));
        }
        return result;
    }

    public StageDetails stageDetails(String stageId) {
        StageSummary stage = stages().stream()
                .filter(item -> item.id().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline stage: " + stageId));
        WorkerSnapshot worker = workerSnapshot();
        List<LiveEvent> relatedEvents = liveEvents(100).stream()
                .filter(event -> stageId.equals(event.stage()) || stageId.equals(event.fromStage()) || stageId.equals(event.toStage()))
                .limit(20)
                .toList();
        List<StageMessage> waitingMessages = stageMessages(stageId, "waiting", 20);
        return new StageDetails(
                stage,
                stage.warning(),
                stage.warning() == null ? "Этап сейчас без blocker warning" : stage.warning(),
                relatedEvents,
                waitingMessages,
                worker.status(),
                worker.embeddingStatus(),
                worker.classifierStatus(),
                countRealClusters(),
                countRealSingleMessageCandidates(),
                countRealProviderCalls(),
                countRealKnowledgeItems()
        );
    }

    public Map<String, Object> liveRunDetails(long runId) {
        Map<String, Object> run = jdbc.query("""
                SELECT rr.id,
                       rr.dataset_id,
                       rr.run_name,
                       rr.pipeline_version,
                       rr.status,
                       rr.started_at,
                       rr.finished_at,
                       rr.error,
                       rr.total_messages,
                       rr.processed_messages,
                       b.id AS batch_id,
                       b.message_count AS batch_messages,
                       b.status AS batch_status,
                       b.error AS batch_error
                FROM replay_runs rr
                LEFT JOIN auto_pipeline_batches b ON b.replay_run_id = rr.id
                WHERE rr.id = ?
                """, rs -> rs.next() ? row(
                "runId", rs.getLong("id"),
                "datasetId", rs.getLong("dataset_id"),
                "runName", rs.getString("run_name"),
                "pipelineVersion", rs.getString("pipeline_version"),
                "status", rs.getString("status"),
                "startedAt", rs.getObject("started_at", OffsetDateTime.class),
                "finishedAt", rs.getObject("finished_at", OffsetDateTime.class),
                "terminalReason", rs.getString("error"),
                "totalMessages", nullableLong(rs, "total_messages"),
                "processedMessages", nullableLong(rs, "processed_messages"),
                "batchId", nullableLong(rs, "batch_id"),
                "batchMessages", nullableLong(rs, "batch_messages"),
                "batchStatus", rs.getString("batch_status"),
                "batchError", rs.getString("batch_error")
        ) : null, runId);
        if (run == null) {
            return row("runId", runId, "found", false, "reason", "RUN_NOT_FOUND");
        }
        run.put("found", true);
        run.put("stages", jdbc.queryForList("SELECT stage, status, input_count, output_count, skipped_count, error_count, latency_ms, error FROM replay_run_stages WHERE run_id = ? ORDER BY id", runId));
        run.put("clusters", countWhere("replay_clusters", "run_id = " + runId));
        run.put("embeddings", countWhere("message_embeddings", "run_id = " + runId));
        run.put("providerCalls", countWhere("provider_calls", "run_id = " + runId));
        run.put("materials", countWhere("knowledge_items", "run_id = " + runId));
        return run;
    }

    public Map<String, Object> liveClusters(Long runId, int limit) {
        Long resolvedRunId = resolveRunId(runId);
        int safeLimit = safeLimit(limit, 50, 100);
        if (resolvedRunId == null) {
            return emptyDetail("clusters", "RUN_NOT_FOUND", "Нет live run для просмотра кластеров");
        }
        List<Map<String, Object>> clusters = jdbc.query("""
                SELECT rc.id,
                       rc.cluster_type,
                       rc.cluster_key,
                       rc.title,
                       rc.score,
                       rc.message_count,
                       rc.status,
                       rc.judge_json::text AS judge_json,
                       rc.created_at,
                       rc.updated_at,
                       COALESCE(pc.status, 'NOT_STARTED') AS judge_status,
                       pc.model_name,
                       pc.estimated_cost_usd,
                       pc.error_code,
                       pc.error_message,
                       left(COALESCE(dm.text, dm.caption, ''), 240) AS representative_message
                FROM replay_clusters rc
                LEFT JOIN provider_calls pc ON pc.id = rc.judge_provider_call_id
                LEFT JOIN replay_run_messages rrm ON rrm.run_id = rc.run_id AND (rrm.microcluster_id = rc.id OR rrm.macrocluster_id = rc.id)
                LEFT JOIN dataset_messages dm ON dm.id = rrm.dataset_message_id
                WHERE rc.run_id = ?
                ORDER BY rc.score DESC, rc.id DESC
                LIMIT ?
                """, (rs, rowNum) -> row(
                "id", rs.getLong("id"),
                "type", rs.getString("cluster_type"),
                "key", rs.getString("cluster_key"),
                "title", rs.getString("title"),
                "score", rs.getBigDecimal("score"),
                "messageCount", rs.getInt("message_count"),
                "status", rs.getString("status"),
                "judgeStatus", rs.getString("judge_status"),
                "model", rs.getString("model_name"),
                "cost", rs.getBigDecimal("estimated_cost_usd"),
                "errorCode", rs.getString("error_code"),
                "errorMessage", rs.getString("error_message"),
                "representativeMessage", rs.getString("representative_message"),
                "judge", parseJson(rs.getString("judge_json")),
                "createdAt", rs.getObject("created_at", OffsetDateTime.class),
                "updatedAt", rs.getObject("updated_at", OffsetDateTime.class)
        ), resolvedRunId, safeLimit);
        Map<String, Object> response = detailBase("clusters", resolvedRunId);
        response.put("items", clusters);
        response.put("total", countWhere("replay_clusters", "run_id = " + resolvedRunId));
        response.put("reason", clusters.isEmpty() ? clusterEmptyReason(resolvedRunId) : "CLUSTERS_FOUND");
        return response;
    }

    public Map<String, Object> liveEmbeddings(Long runId, int limit) {
        Long resolvedRunId = resolveRunId(runId);
        int safeLimit = safeLimit(limit, 50, 100);
        WorkerSnapshot worker = workerSnapshot();
        Map<String, Object> response = detailBase("embeddings", resolvedRunId);
        response.put("workerStatus", worker.status());
        response.put("embeddingStatus", worker.embeddingStatus());
        response.put("classifierStatus", worker.classifierStatus());
        if (resolvedRunId == null) {
            response.put("reason", "RUN_NOT_FOUND");
            response.put("items", List.of());
            return response;
        }
        long runMessages = countWhere("replay_run_messages", "run_id = " + resolvedRunId);
        long created = countWhere("message_embeddings", "run_id = " + resolvedRunId);
        response.put("runMessages", runMessages);
        response.put("embeddingsCreated", created);
        response.put("messagesWithoutEmbeddings", Math.max(0, runMessages - created));
        response.put("reason", embeddingReason(worker, runMessages, created));
        response.put("items", jdbc.query("""
                SELECT me.id,
                       me.raw_message_id,
                       me.dataset_message_id,
                       me.embedding_kind,
                       me.embedding_hash,
                       em.name AS model_name,
                       em.dimension,
                       left(COALESCE(me.embedding_text, dm.text, dm.caption, ''), 240) AS text_preview,
                       me.created_at
                FROM message_embeddings me
                LEFT JOIN embedding_models em ON em.id = me.model_id
                LEFT JOIN dataset_messages dm ON dm.id = me.dataset_message_id
                WHERE me.run_id = ?
                ORDER BY me.id DESC
                LIMIT ?
                """, (rs, rowNum) -> row(
                "id", rs.getLong("id"),
                "rawMessageId", nullableLong(rs, "raw_message_id"),
                "datasetMessageId", rs.getLong("dataset_message_id"),
                "kind", rs.getString("embedding_kind"),
                "hash", rs.getString("embedding_hash"),
                "model", rs.getString("model_name"),
                "dimension", nullableLong(rs, "dimension"),
                "textPreview", rs.getString("text_preview"),
                "createdAt", rs.getObject("created_at", OffsetDateTime.class)
        ), resolvedRunId, safeLimit));
        return response;
    }

    public Map<String, Object> liveLlmJudge(Long runId, int limit) {
        Long resolvedRunId = resolveRunId(runId);
        int safeLimit = safeLimit(limit, 50, 100);
        Map<String, Object> response = detailBase("llmJudge", resolvedRunId);
        response.put("providerConfigured", providerConfigured());
        if (resolvedRunId == null) {
            response.put("reason", "RUN_NOT_FOUND");
            response.put("items", List.of());
            return response;
        }
        long clusters = countWhere("replay_clusters", "run_id = " + resolvedRunId);
        long singleCandidates = countQuery("SELECT count(*) FROM replay_run_messages WHERE run_id = " + resolvedRunId + " AND final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE', 'DIRECT_MATERIAL_READY')");
        response.put("clusters", clusters);
        response.put("singleCandidates", singleCandidates);
        response.put("accepted", countWhere("replay_clusters", "run_id = " + resolvedRunId + " AND status IN ('ACCEPTED', 'READY', 'APPROVED')"));
        response.put("rejected", countWhere("replay_clusters", "run_id = " + resolvedRunId + " AND status IN ('REJECTED', 'SUPPRESSED', 'SKIPPED')"));
        response.put("reason", llmReason(resolvedRunId, clusters, singleCandidates));
        response.put("items", jdbc.query("""
                SELECT pc.id,
                       pc.stage,
                       pc.model_name,
                       pc.status,
                       pc.request_preview,
                       pc.response_preview,
                       pc.input_tokens,
                       pc.output_tokens,
                       pc.estimated_cost_usd,
                       pc.latency_ms,
                       pc.error_code,
                       pc.error_message,
                       pc.created_at
                FROM provider_calls pc
                WHERE pc.run_id = ?
                  AND (pc.stage ILIKE '%judge%' OR pc.stage ILIKE '%llm%' OR pc.stage ILIKE '%routing%')
                ORDER BY pc.id DESC
                LIMIT ?
                """, this::providerCallRow, resolvedRunId, safeLimit));
        return response;
    }

    public Map<String, Object> liveMaterialGeneration(Long runId, int limit) {
        Long resolvedRunId = resolveRunId(runId);
        int safeLimit = safeLimit(limit, 50, 100);
        Map<String, Object> response = detailBase("materialGeneration", resolvedRunId);
        response.put("providerConfigured", providerConfigured());
        if (resolvedRunId == null) {
            response.put("reason", "RUN_NOT_FOUND");
            response.put("items", List.of());
            response.put("providerCalls", List.of());
            return response;
        }
        long candidates = countQuery("SELECT count(*) FROM replay_run_messages WHERE run_id = " + resolvedRunId + " AND final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE', 'DIRECT_MATERIAL_READY')");
        long generated = countWhere("knowledge_items", "run_id = " + resolvedRunId);
        response.put("candidates", candidates);
        response.put("generatedKnowledgeItems", generated);
        response.put("generatedMaterials", generated);
        response.put("reason", materialReason(resolvedRunId, candidates, generated));
        response.put("items", jdbc.query("""
                SELECT ki.id,
                       ki.item_type,
                       ki.artifact_type,
                       ki.title,
                       ki.summary,
                       ki.confidence,
                       ki.source_cluster_type,
                       ki.source_cluster_id,
                       ki.created_at,
                       COALESCE(pc.status, 'NO_PROVIDER_CALL') AS provider_status,
                       pc.model_name,
                       pc.error_code,
                       pc.error_message
                FROM knowledge_items ki
                LEFT JOIN provider_calls pc ON pc.id = ki.provider_call_id
                WHERE ki.run_id = ?
                ORDER BY ki.id DESC
                LIMIT ?
                """, (rs, rowNum) -> row(
                "id", rs.getLong("id"),
                "itemType", rs.getString("item_type"),
                "artifactType", rs.getString("artifact_type"),
                "title", rs.getString("title"),
                "summary", rs.getString("summary"),
                "confidence", rs.getBigDecimal("confidence"),
                "sourceClusterType", rs.getString("source_cluster_type"),
                "sourceClusterId", nullableLong(rs, "source_cluster_id"),
                "providerStatus", rs.getString("provider_status"),
                "model", rs.getString("model_name"),
                "errorCode", rs.getString("error_code"),
                "errorMessage", rs.getString("error_message"),
                "createdAt", rs.getObject("created_at", OffsetDateTime.class)
        ), resolvedRunId, safeLimit));
        response.put("providerCalls", jdbc.query("""
                SELECT pc.id,
                       pc.stage,
                       pc.model_name,
                       pc.status,
                       pc.request_preview,
                       pc.response_preview,
                       pc.input_tokens,
                       pc.output_tokens,
                       pc.estimated_cost_usd,
                       pc.latency_ms,
                       pc.error_code,
                       pc.error_message,
                       pc.created_at
                FROM provider_calls pc
                WHERE pc.run_id = ?
                  AND (pc.stage ILIKE '%generation%' OR pc.stage ILIKE '%material%' OR pc.stage ILIKE '%knowledge%')
                ORDER BY pc.id DESC
                LIMIT ?
                """, this::providerCallRow, resolvedRunId, safeLimit));
        return response;
    }

    public List<StageMessage> stageMessages(String stageId, String status, int limit) {
        String traceStatus = switch (status == null ? "active" : status) {
            case "all" -> "ALL";
            case "processed" -> "PROCESSED";
            case "failed" -> "FAILED";
            case "skipped" -> "SKIPPED";
            case "waiting" -> "WAITING";
            default -> "PROCESSING";
        };
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 200));
        String statusPredicate = "ALL".equals(traceStatus)
                ? "TRUE"
                : "WAITING".equals(traceStatus)
                ? "tr.status IN ('PENDING', 'WAITING_FOR_WORKER')"
                : "PROCESSED".equals(traceStatus)
                ? "tr.status IN ('PROCESSED', 'PROCESSED_DEGRADED')"
                : "tr.status = ?";
        Object[] args = "ALL".equals(traceStatus) || "WAITING".equals(traceStatus) || "PROCESSED".equals(traceStatus)
                ? new Object[]{stageId, safeLimit}
                : new Object[]{stageId, traceStatus, safeLimit};
        List<StageMessage> rows = jdbc.query("""
                SELECT m.id,
                       m.account_id,
                       m.telegram_chat_id,
                       m.telegram_message_id,
                       c.title AS chat_title,
                       COALESCE(t.title, m.topic_title) AS topic_title,
                       m.content_type,
                       COALESCE(m.text, m.caption) AS text,
                       tr.status,
                       tr.error_code,
                       tr.error_message,
                       tr.stage_id,
                       tr.stage_name,
                       m.ingested_at AS created_at,
                       tr.updated_at
                FROM pipeline_message_trace tr
                JOIN raw_messages m ON m.id = tr.raw_message_id
                LEFT JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id AND t.telegram_chat_id = m.telegram_chat_id
                    AND (t.telegram_topic_id IS NOT DISTINCT FROM m.telegram_topic_id OR t.message_thread_id IS NOT DISTINCT FROM m.message_thread_id)
                WHERE tr.stage_id = ? AND %s
                ORDER BY tr.updated_at DESC
                LIMIT ?
                """.formatted(statusPredicate), this::stageMessage, args);
        if (rows.isEmpty() && "active".equals(status)) {
            rows = jdbc.query("""
                    SELECT m.id,
                           m.account_id,
                           m.telegram_chat_id,
                           m.telegram_message_id,
                           c.title AS chat_title,
                           COALESCE(t.title, m.topic_title) AS topic_title,
                           m.content_type,
                           COALESCE(m.text, m.caption) AS text,
                           tr.status,
                            tr.error_code,
                            tr.error_message,
                            tr.stage_id,
                            tr.stage_name,
                            m.ingested_at AS created_at,
                            tr.updated_at
                    FROM pipeline_message_trace tr
                    JOIN raw_messages m ON m.id = tr.raw_message_id
                    LEFT JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                    LEFT JOIN telegram_topics t ON t.account_id = m.account_id AND t.telegram_chat_id = m.telegram_chat_id
                        AND (t.telegram_topic_id IS NOT DISTINCT FROM m.telegram_topic_id OR t.message_thread_id IS NOT DISTINCT FROM m.message_thread_id)
                    WHERE tr.stage_id = ? AND tr.status IN ('PENDING', 'WAITING_FOR_WORKER')
                    ORDER BY tr.updated_at DESC
                    LIMIT ?
                    """, this::stageMessage, stageId, safeLimit);
        }
        return rows;
    }

    public MessageTrace messageTrace(long rawMessageId) {
        RawMessage raw = jdbc.query("""
                SELECT m.id,
                       m.account_id,
                       m.telegram_chat_id,
                       m.telegram_message_id,
                       c.title AS chat_title,
                       COALESCE(t.title, m.topic_title) AS topic_title,
                       m.content_type,
                       COALESCE(m.text, m.caption) AS text,
                       m.raw_json
                FROM raw_messages m
                LEFT JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id AND t.telegram_chat_id = m.telegram_chat_id
                    AND (t.telegram_topic_id IS NOT DISTINCT FROM m.telegram_topic_id OR t.message_thread_id IS NOT DISTINCT FROM m.message_thread_id)
                WHERE m.id = ?
                """, rs -> rs.next() ? rawMessage(rs) : null, rawMessageId);
        if (raw == null) {
            throw new IllegalArgumentException("raw_message not found: " + rawMessageId);
        }
        Intake intake = jdbc.query("""
                SELECT raw_message_id, status, reason, intake_source, replay_run_id, created_at, updated_at
                FROM pipeline_message_intake
                WHERE raw_message_id = ?
                """, rs -> rs.next() ? intake(rs) : null, rawMessageId);
        List<TraceRow> traces = jdbc.query("""
                SELECT stage_id, stage_name, status, input_json, output_json, error_code, error_message, started_at, finished_at, duration_ms, updated_at
                FROM pipeline_message_trace
                WHERE raw_message_id = ?
                ORDER BY array_position(ARRAY['telegram_ingest','db_cache','normalization','cleanup','dedupe','rule_signals','bootstrap_classification','embeddings','clustering','llm_judge','material_generation','materials_publish'], stage_id)
                """, this::traceRow, rawMessageId);
        Downstream downstream = downstream(rawMessageId, intake == null ? null : intake.replayRunId());
        Blocker blocker = blocker(intake, traces, downstream);
        TraceRow current = currentTrace(traces);
        return new MessageTrace(raw, intake, traces, downstream, blocker.reason(), blocker.explanation(), blocker.nextAction(), current == null ? null : current.stageId(), current == null ? null : current.status(), retryActions(false, "Повтор пока отключён: live stage worker orchestration ещё не реализован"));
    }

    public WhyEmpty whyEmpty() {
        long raw = count("raw_messages");
        long intakeRows = count("pipeline_message_intake");
        long pending = countStatus("pipeline_message_intake", "PENDING");
        long queued = countStatus("pipeline_message_intake", "QUEUED");
        long processed = countStatus("pipeline_message_intake", "PROCESSED");
        long clusters = countRealClusters();
        long judged = countRealJudgedClusters();
        long materials = countRealKnowledgeItems();
        long singleCandidates = countRealSingleMessageCandidates();
        long directReady = countRealDirectMaterialReady();
        WorkerSnapshot worker = workerSnapshot();
        String blocker;
        String secondaryBlocker = null;
        if (countEnabledAutoPipeline() == 0) {
            blocker = "AUTO_PIPELINE_DISABLED";
            if (!worker.reachable()) secondaryBlocker = "MODEL_WORKER_DOWN";
        } else if (!worker.reachable()) {
            blocker = "MODEL_WORKER_DOWN";
        } else if (!providerConfigured()) {
            blocker = "PROVIDER_DISABLED";
        } else if (clusters == 0 && singleCandidates == 0) {
            blocker = "NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES";
        } else if (clusters == 0 && singleCandidates > 0 && !providerConfigured()) {
            blocker = "SINGLE_MESSAGE_CANDIDATES_WAITING_FOR_PROVIDER";
            secondaryBlocker = "PROVIDER_DISABLED_OR_KEY_MISSING";
        } else if (clusters == 0 && singleCandidates > 0) {
            blocker = "SINGLE_MESSAGE_CANDIDATES_WAITING_FOR_LLM";
            secondaryBlocker = "PROVIDER_READY";
        } else if (judged == 0 && singleCandidates == 0) {
            blocker = "THRESHOLD_NOT_MET";
        } else {
            blocker = materials == 0 ? "THRESHOLD_NOT_MET" : "NONE";
        }
        return new WhyEmpty(raw, intakeRows, pending, queued, processed, clusters, judged, materials, singleCandidates, directReady, blocker, secondaryBlocker, explanation(blocker, secondaryBlocker), nextActions(blocker, secondaryBlocker));
    }

    public List<AutoPipelineSettingDto> autoSettings() {
        return jdbc.query("""
                SELECT s.id, s.account_id, s.telegram_chat_id, s.topic_id, s.enabled, s.debounce_seconds, s.batch_size, s.max_provider_calls, s.max_cost_usd, s.created_at, s.updated_at
                FROM auto_pipeline_settings s
                JOIN telegram_chats c ON c.account_id = s.account_id AND c.telegram_chat_id = s.telegram_chat_id
                WHERE s.telegram_chat_id IS NOT NULL
                  AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                ORDER BY s.enabled DESC, s.updated_at DESC, s.id DESC
                """, this::autoSetting);
    }

    public AutoPipelineStatusDto autoStatus(long accountId, long chatId, Long topicId) {
        AutoPipelineSettingDto topicSetting = topicId == null ? null : findAutoSetting(accountId, chatId, topicId);
        AutoPipelineSettingDto chatSetting = findAutoSetting(accountId, chatId, null);
        AutoPipelineSettingDto setting = topicSetting != null ? topicSetting : chatSetting;
        String source = topicSetting != null ? "TOPIC_SETTING" : chatSetting != null ? "CHAT_SETTING" : "NONE";
        String scope = topicId == null ? "CHAT" : "TOPIC";
        boolean configured = setting != null;
        boolean enabled = setting != null && setting.enabled();
        boolean manuallyDisabled = setting != null && !setting.enabled();
        boolean activeDialog = activeMemberDialog(accountId, chatId);
        int debounceSeconds = setting == null ? 30 : setting.debounceSeconds();
        int batchSize = setting == null ? 10 : setting.batchSize();
        int maxProviderCalls = setting == null ? 10 : setting.maxProviderCalls();
        BigDecimal maxCostUsd = setting == null ? BigDecimal.ONE : setting.maxCostUsd();
        return new AutoPipelineStatusDto(accountId, chatId, topicId, scope, configured, enabled, manuallyDisabled, enabled && activeDialog, source, debounceSeconds, batchSize, maxProviderCalls, maxCostUsd);
    }

    @Transactional
    public AutoPipelineSettingDto enableAuto(AutoPipelineRequest request) {
        validateScoped(request);
        if (!activeMemberDialog(request.accountId(), request.chatId())) {
            throw new IllegalArgumentException("CHAT_NOT_ACTIVE_MEMBER");
        }
        long id;
        if (request.topicId() == null) {
            id = Objects.requireNonNull(jdbc.queryForObject("""
                    INSERT INTO auto_pipeline_settings (account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, source, created_by, disabled_reason, disabled_at)
                    VALUES (?, ?, NULL, true, ?, ?, ?, ?, 'USER_EXPLICIT', 'ui', NULL, NULL)
                    ON CONFLICT (account_id, telegram_chat_id) WHERE telegram_chat_id IS NOT NULL AND topic_id IS NULL
                    DO UPDATE SET enabled = true, debounce_seconds = EXCLUDED.debounce_seconds, batch_size = EXCLUDED.batch_size, max_provider_calls = EXCLUDED.max_provider_calls, max_cost_usd = EXCLUDED.max_cost_usd, source = 'USER_EXPLICIT', created_by = 'ui', disabled_reason = NULL, disabled_at = NULL, updated_at = now()
                    RETURNING id
                    """, Long.class, request.accountId(), request.chatId(), request.debounceSecondsOrDefault(), request.batchSizeOrDefault(), request.maxProviderCallsOrDefault(), request.maxCostUsdOrDefault()));
        } else {
            id = Objects.requireNonNull(jdbc.queryForObject("""
                    INSERT INTO auto_pipeline_settings (account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, source, created_by, disabled_reason, disabled_at)
                    VALUES (?, ?, ?, true, ?, ?, ?, ?, 'USER_EXPLICIT', 'ui', NULL, NULL)
                    ON CONFLICT (account_id, telegram_chat_id, topic_id) WHERE telegram_chat_id IS NOT NULL AND topic_id IS NOT NULL
                    DO UPDATE SET enabled = true, debounce_seconds = EXCLUDED.debounce_seconds, batch_size = EXCLUDED.batch_size, max_provider_calls = EXCLUDED.max_provider_calls, max_cost_usd = EXCLUDED.max_cost_usd, source = 'USER_EXPLICIT', created_by = 'ui', disabled_reason = NULL, disabled_at = NULL, updated_at = now()
                    RETURNING id
                    """, Long.class, request.accountId(), request.chatId(), request.topicId(), request.debounceSecondsOrDefault(), request.batchSizeOrDefault(), request.maxProviderCallsOrDefault(), request.maxCostUsdOrDefault()));
        }
        Long finalId = id;
        return jdbc.query("""
                SELECT id, account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, created_at, updated_at
                FROM auto_pipeline_settings
                WHERE id = ?
                """, rs -> rs.next() ? autoSetting(rs, 0) : null, finalId);
    }

    @Transactional
    public AutoPipelineSettingDto disableAuto(AutoPipelineRequest request) {
        validateScoped(request);
        long id;
        if (request.topicId() == null) {
            id = Objects.requireNonNull(jdbc.queryForObject("""
                    INSERT INTO auto_pipeline_settings (account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, source, created_by, disabled_reason, disabled_at)
                    VALUES (?, ?, NULL, false, ?, ?, ?, ?, 'USER_EXPLICIT', 'ui', 'USER_DISABLED', now())
                    ON CONFLICT (account_id, telegram_chat_id) WHERE telegram_chat_id IS NOT NULL AND topic_id IS NULL
                    DO UPDATE SET enabled = false, debounce_seconds = EXCLUDED.debounce_seconds, batch_size = EXCLUDED.batch_size, max_provider_calls = EXCLUDED.max_provider_calls, max_cost_usd = EXCLUDED.max_cost_usd, disabled_reason = 'USER_DISABLED', disabled_at = now(), updated_at = now()
                    RETURNING id
                    """, Long.class, request.accountId(), request.chatId(), request.debounceSecondsOrDefault(), request.batchSizeOrDefault(), request.maxProviderCallsOrDefault(), request.maxCostUsdOrDefault()));
        } else {
            id = Objects.requireNonNull(jdbc.queryForObject("""
                    INSERT INTO auto_pipeline_settings (account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, source, created_by, disabled_reason, disabled_at)
                    VALUES (?, ?, ?, false, ?, ?, ?, ?, 'USER_EXPLICIT', 'ui', 'USER_DISABLED', now())
                    ON CONFLICT (account_id, telegram_chat_id, topic_id) WHERE telegram_chat_id IS NOT NULL AND topic_id IS NOT NULL
                    DO UPDATE SET enabled = false, debounce_seconds = EXCLUDED.debounce_seconds, batch_size = EXCLUDED.batch_size, max_provider_calls = EXCLUDED.max_provider_calls, max_cost_usd = EXCLUDED.max_cost_usd, disabled_reason = 'USER_DISABLED', disabled_at = now(), updated_at = now()
                    RETURNING id
                    """, Long.class, request.accountId(), request.chatId(), request.topicId(), request.debounceSecondsOrDefault(), request.batchSizeOrDefault(), request.maxProviderCallsOrDefault(), request.maxCostUsdOrDefault()));
        }
        Long finalId = id;
        return jdbc.query("""
                SELECT id, account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, created_at, updated_at
                FROM auto_pipeline_settings
                WHERE id = ?
                """, rs -> rs.next() ? autoSetting(rs, 0) : null, finalId);
    }

    @Transactional
    public IntakeBackfillResult backfillFromRaw(IntakeBackfillRequest request) {
        validateScoped(request);
        int limit = Math.max(1, Math.min(request.limit() == null ? 50 : request.limit(), 50));
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        String topicFilter = request.topicId() == null ? "" : " AND (m.telegram_topic_id = ? OR m.message_thread_id = ?)";
        List<Object> args = new ArrayList<>();
        args.add(request.accountId());
        args.add(request.chatId());
        if (request.topicId() != null) {
            args.add(request.topicId());
            args.add(request.topicId());
        }
        args.add(limit);
        List<RawCandidate> candidates = jdbc.query("""
                SELECT m.id, m.account_id, m.telegram_chat_id, m.telegram_topic_id, m.message_thread_id,
                       COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') <> '' AS has_text,
                       i.raw_message_id AS intake_id,
                       q.raw_message_id AS queue_id
                FROM raw_messages m
                LEFT JOIN pipeline_message_intake i ON i.raw_message_id = m.id
                LEFT JOIN auto_pipeline_queue q ON q.raw_message_id = m.id
                WHERE m.account_id = ? AND m.telegram_chat_id = ?
                """ + topicFilter + " " + """
                ORDER BY m.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new RawCandidate(rs.getLong("id"), rs.getLong("account_id"), rs.getLong("telegram_chat_id"), nullableLong(rs, "telegram_topic_id"), nullableLong(rs, "message_thread_id"), rs.getBoolean("has_text"), nullableLong(rs, "intake_id") != null, nullableLong(rs, "queue_id") != null), args.toArray());
        int intakeCreated = 0;
        int queued = 0;
        int skippedExisting = 0;
        boolean enabled = canProcessChat(request.accountId(), request.chatId(), request.topicId(), "TELEGRAM_BACKFILL");
        for (RawCandidate candidate : candidates) {
            if (candidate.hasIntake()) skippedExisting++;
            if (!dryRun) ensureLiveState(candidate.rawMessageId(), candidate.accountId(), candidate.telegramChatId(), candidate.topicId(), candidate.messageThreadId(), "TELEGRAM_LIVE");
            if (!candidate.hasIntake()) intakeCreated++;
            if (enabled && candidate.hasText() && !candidate.hasQueue()) {
                if (!dryRun) {
                    long batchId = ensureCollectingBatch(candidate.accountId(), candidate.telegramChatId(), effectiveBatchTopicId(candidate.accountId(), candidate.telegramChatId(), candidate.topicId()));
                    jdbc.update("""
                            INSERT INTO auto_pipeline_queue (raw_message_id, account_id, telegram_chat_id, topic_id, batch_id, status, reason)
                            VALUES (?, ?, ?, ?, ?, 'PENDING', 'AUTO_PIPELINE_SCOPE_BACKFILL')
                            ON CONFLICT (raw_message_id) DO NOTHING
                            """, candidate.rawMessageId(), candidate.accountId(), candidate.telegramChatId(), candidate.topicId(), batchId);
                    jdbc.update("UPDATE pipeline_message_intake SET status = 'QUEUED', reason = 'AUTO_PIPELINE_SCOPE_BACKFILL', updated_at = now() WHERE raw_message_id = ? AND status <> 'SKIPPED'", candidate.rawMessageId());
                    jdbc.update("UPDATE auto_pipeline_batches SET message_count = (SELECT count(*) FROM auto_pipeline_queue WHERE batch_id = ?), updated_at = now() WHERE id = ?", batchId, batchId);
                }
                queued++;
            }
        }
        return new IntakeBackfillResult(candidates.size(), intakeCreated, queued, skippedExisting, 0, dryRun, enabled);
    }

    @Transactional
    public QueuePendingResult queuePendingEnabledScopes(QueuePendingRequest request) {
        int limit = Math.max(1, Math.min(request == null || request.limit() == null ? 200 : request.limit(), 500));
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        List<RawCandidate> candidates = jdbc.query("""
                SELECT i.raw_message_id AS id,
                       i.account_id,
                       i.telegram_chat_id,
                       i.topic_id AS telegram_topic_id,
                       i.message_thread_id,
                       true AS has_text,
                       i.raw_message_id AS intake_id,
                       q.raw_message_id AS queue_id
                FROM pipeline_message_intake i
                LEFT JOIN auto_pipeline_queue q ON q.raw_message_id = i.raw_message_id
                WHERE i.status = 'PENDING'
                  AND q.raw_message_id IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM telegram_chats c
                      WHERE c.account_id = i.account_id
                        AND c.telegram_chat_id = i.telegram_chat_id
                        AND c.is_enabled = true
                        AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                  )
                ORDER BY i.updated_at ASC, i.raw_message_id ASC
                LIMIT ?
                """, (rs, rowNum) -> new RawCandidate(rs.getLong("id"), rs.getLong("account_id"), rs.getLong("telegram_chat_id"), nullableLong(rs, "telegram_topic_id"), nullableLong(rs, "message_thread_id"), rs.getBoolean("has_text"), nullableLong(rs, "intake_id") != null, nullableLong(rs, "queue_id") != null), limit);
        if (!dryRun) {
            for (RawCandidate candidate : candidates) {
                long batchId = ensureCollectingBatch(candidate.accountId(), candidate.telegramChatId(), effectiveBatchTopicId(candidate.accountId(), candidate.telegramChatId(), candidate.topicId()));
                jdbc.update("""
                        INSERT INTO auto_pipeline_queue (raw_message_id, account_id, telegram_chat_id, topic_id, batch_id, status, reason)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', 'AUTO_PIPELINE_ENABLED_REQUEUE')
                        ON CONFLICT (raw_message_id) DO NOTHING
                        """, candidate.rawMessageId(), candidate.accountId(), candidate.telegramChatId(), candidate.topicId(), batchId);
                jdbc.update("UPDATE pipeline_message_intake SET status = 'QUEUED', reason = 'AUTO_PIPELINE_ENABLED_REQUEUE', updated_at = now() WHERE raw_message_id = ? AND status = 'PENDING'", candidate.rawMessageId());
                jdbc.update("UPDATE auto_pipeline_batches SET message_count = (SELECT count(*) FROM auto_pipeline_queue WHERE batch_id = ?), updated_at = now() WHERE id = ?", batchId, batchId);
            }
        }
        long remaining = countQuery("""
                SELECT count(*)
                FROM pipeline_message_intake i
                WHERE i.status = 'PENDING'
                  AND EXISTS (
                      SELECT 1
                      FROM telegram_chats c
                      WHERE c.account_id = i.account_id
                        AND c.telegram_chat_id = i.telegram_chat_id
                        AND c.is_enabled = true
                        AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                  )
                """);
        return new QueuePendingResult(candidates.size(), dryRun ? 0 : candidates.size(), remaining, dryRun);
    }

    @Transactional
    public LiveAutoRunStartResult startLiveAutoRun(long runId) {
        LiveRun run = jdbc.query("""
                SELECT rr.id, rr.dataset_id, b.id AS batch_id
                FROM replay_runs rr
                JOIN auto_pipeline_batches b ON b.replay_run_id = rr.id
                WHERE rr.id = ? AND rr.pipeline_version = 'LIVE_AUTO_RAW_MESSAGES'
                """, rs -> rs.next() ? new LiveRun(rs.getLong("id"), rs.getLong("dataset_id"), rs.getLong("batch_id")) : null, runId);
        if (run == null) {
            return new LiveAutoRunStartResult(runId, false, "RUN_NOT_FOUND_OR_NOT_LIVE_AUTO", 0, "NONE", "NONE");
        }
        String rejection = jdbc.query("""
                SELECT i.raw_message_id, i.account_id, i.telegram_chat_id, i.topic_id, i.intake_source
                FROM auto_pipeline_queue q
                JOIN pipeline_message_intake i ON i.raw_message_id = q.raw_message_id
                WHERE q.batch_id = ?
                ORDER BY i.raw_message_id
                """, rs -> {
            while (rs.next()) {
                String reason = processingRejection(rs.getLong("account_id"), rs.getLong("telegram_chat_id"), nullableLong(rs, "topic_id"), rs.getString("intake_source"));
                if (reason != null) {
                    return reason;
                }
            }
            return null;
        }, run.batchId());
        if (rejection != null) {
            jdbc.update("UPDATE replay_runs SET status = 'FAILED', error = ?, finished_at = now() WHERE id = ?", rejection, runId);
            jdbc.update("UPDATE auto_pipeline_batches SET status = 'FAILED', error = ?, updated_at = now() WHERE id = ?", rejection, run.batchId());
            jdbc.update("UPDATE auto_pipeline_queue SET status = 'FAILED', reason = ?, updated_at = now() WHERE batch_id = ?", rejection, run.batchId());
            jdbc.update("UPDATE pipeline_message_intake SET status = 'PENDING', reason = ?, updated_at = now() WHERE replay_run_id = ?", rejection, runId);
            return new LiveAutoRunStartResult(runId, false, rejection, 0, "SECURITY_GUARD", "SECURITY_GUARD");
        }
        List<Long> rawIds = jdbc.queryForList("SELECT raw_message_id FROM auto_pipeline_queue WHERE batch_id = ? ORDER BY raw_message_id", Long.class, run.batchId());
        ReplayV2Service.ReplayRun result = replayV2Service.runExistingLiveAutoRun(runId);
        String terminalReason = result.error() == null || result.error().isBlank() ? "SUCCESS_WITH_MATERIALS" : result.error();
        String batchStatus = "COMPLETED".equals(result.status()) ? "PROCESSED" : "FAILED";
        jdbc.update("UPDATE pipeline_message_intake SET status = ?, reason = ?, updated_at = now() WHERE replay_run_id = ?", batchStatus, terminalReason, runId);
        jdbc.update("UPDATE auto_pipeline_queue SET status = ?, reason = ?, updated_at = now() WHERE batch_id = ?", batchStatus, terminalReason, run.batchId());
        jdbc.update("UPDATE auto_pipeline_batches SET status = ?, error = ?, updated_at = now() WHERE id = ?", batchStatus, terminalReason, run.batchId());
        return new LiveAutoRunStartResult(runId, true, terminalReason, rawIds.size(), "REAL_REPLAY", "REAL_REPLAY");
    }

    public RunHealth runHealth(long runId) {
        Map<String, Object> run = jdbc.query("""
                SELECT id, status, error, processed_messages, total_messages, started_at, finished_at
                FROM replay_runs
                WHERE id = ?
                """, rs -> rs.next() ? row(
                "id", rs.getLong("id"),
                "status", rs.getString("status"),
                "error", rs.getString("error"),
                "processed", rs.getLong("processed_messages"),
                "total", rs.getLong("total_messages"),
                "startedAt", rs.getObject("started_at", OffsetDateTime.class),
                "finishedAt", rs.getObject("finished_at", OffsetDateTime.class),
                "updatedAt", rs.getObject("finished_at", OffsetDateTime.class) == null ? rs.getObject("started_at", OffsetDateTime.class) : rs.getObject("finished_at", OffsetDateTime.class)
        ) : null, runId);
        if (run == null) {
            return new RunHealth(runId, "NOT_FOUND", "Запуск не найден", null, null, 0, 0, 0, 0, "RUN_NOT_FOUND", "Откройте список запусков и выберите существующий run");
        }
        String status = Objects.toString(run.get("status"), "UNKNOWN");
        String error = (String) run.get("error");
        WorkerSnapshot worker = workerSnapshot();
        long queued = countStatus("auto_pipeline_queue", "PENDING");
        long processing = countStatus("pipeline_message_intake", "PROCESSING");
        long failed = countStatus("pipeline_message_intake", "FAILED");
        long processed = ((Number) run.getOrDefault("processed", 0L)).longValue();
        String blocker = error != null && !error.isBlank() ? error : whyEmpty().mainBlocker();
        String verdict;
        String label;
        String currentStage;
        String nextAction;
        if (!worker.reachable()) {
            verdict = "BLOCKED";
            label = labelForCode("MODEL_WORKER_DOWN");
            currentStage = "embeddings";
            blocker = "MODEL_WORKER_DOWN";
            nextAction = "Проверьте model-worker";
        } else if ("FAILED".equals(status) || failed > 0) {
            verdict = "FAILED";
            label = "Есть ошибки на стадии pipeline";
            currentStage = latestFailedStage(runId);
            nextAction = "Откройте trace ошибки";
        } else if ("RUNNING".equals(status) && (queued > 0 || processing > 0)) {
            verdict = "OK";
            label = "Пайплайн идёт нормально";
            currentStage = "live_auto_batch";
            nextAction = "Дождитесь завершения текущей очереди";
        } else if ("COMPLETED".equals(status) && ("NO_CLUSTERS".equals(error) || "THRESHOLD_NOT_MET".equals(error))) {
            verdict = "NO_MATERIALS";
            label = labelForCode(error);
            currentStage = "material_generation";
            nextAction = "Проверьте пороги и классификаторы либо дождитесь новых сообщений";
        } else if (queued == 0 && processing == 0) {
            verdict = "WAITING";
            label = "Ждём новые сообщения";
            currentStage = "telegram_ingest";
            nextAction = "Откройте /groups и проверьте синхронизацию выбранного чата";
        } else {
            verdict = "DELAYED";
            label = "Есть задержка в обработке";
            currentStage = "pipeline";
            nextAction = "Проверьте очередь и trace стадий";
        }
        return new RunHealth(runId, verdict, label, currentStage, (OffsetDateTime) run.get("updatedAt"), queued, processing, processed, failed, blocker, nextAction);
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rawMessages", count("raw_messages"));
        result.put("replayRuns", count("replay_runs"));
        result.put("knowledgeItems", countRealKnowledgeItems());
        result.put("providerCalls", countRealProviderCalls());
        result.put("intakeRows", count("pipeline_message_intake"));
        result.put("queueRows", count("auto_pipeline_queue"));
        result.put("traceRows", count("pipeline_message_trace"));
        result.put("latestRawMessages", jdbc.query("""
                SELECT id, account_id, telegram_chat_id, telegram_message_id, topic_id, message_thread_id, topic_title, content_type, left(COALESCE(text, caption, ''), 200) AS text_preview, created_at
                FROM (
                    SELECT id, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id AS topic_id, message_thread_id, topic_title, content_type, text, caption, ingested_at AS created_at
                    FROM raw_messages
                    ORDER BY id DESC
                    LIMIT 50
                ) x
                """, (rs, rowNum) -> row(
                "id", rs.getLong("id"),
                "accountId", rs.getLong("account_id"),
                "telegramChatId", rs.getLong("telegram_chat_id"),
                "telegramMessageId", rs.getLong("telegram_message_id"),
                "contentType", rs.getString("content_type"),
                "textPreview", rs.getString("text_preview")
        )));
        result.put("latestRuns", jdbc.query("""
                SELECT id, run_name, status, error, started_at, finished_at
                FROM replay_runs
                ORDER BY id DESC
                LIMIT 20
                """, (rs, rowNum) -> row(
                "id", rs.getLong("id"),
                "runName", rs.getString("run_name"),
                "status", rs.getString("status"),
                "error", rs.getString("error")
        )));
        return result;
    }

    public Map<String, Object> retryDisabled(long rawMessageId, String stageId) {
        return Map.of("accepted", false, "rawMessageId", rawMessageId, "stageId", stageId == null ? "ALL" : stageId, "reason", "Повтор пока отключён: live stage worker orchestration ещё не реализован безопасно");
    }

    public boolean canProcessChat(long accountId, long telegramChatId, Long topicId, String intakeSource) {
        return processingRejection(accountId, telegramChatId, topicId, intakeSource) == null;
    }

    public String processingRejection(long accountId, long telegramChatId, Long topicId, String intakeSource) {
        if (intakeSource != null) {
            String normalized = intakeSource.trim().toUpperCase();
            if (normalized.contains("PUBLIC_SEARCH") || normalized.contains("DISCOVERY")) {
                return "PUBLIC_DISCOVERY_NOT_ALLOWED";
            }
        }
        if (!activeMemberDialog(accountId, telegramChatId)) {
            return "CHAT_NOT_ACTIVE_MEMBER";
        }
        return null;
    }

    private boolean autoPipelineEnabled(long accountId, long telegramChatId, Long topicId) {
        return canProcessChat(accountId, telegramChatId, topicId, "TELEGRAM_LIVE");
    }

    private boolean activeMemberDialog(long accountId, long telegramChatId) {
        Boolean active = jdbc.query("""
                SELECT EXISTS (
                    SELECT 1
                    FROM telegram_chats c
                    WHERE c.account_id = ?
                      AND c.telegram_chat_id = ?
                      AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                )
                """, rs -> rs.next() && rs.getBoolean(1), accountId, telegramChatId);
        return Boolean.TRUE.equals(active);
    }

    private AutoPipelineSettingDto findAutoSetting(long accountId, long chatId, Long topicId) {
        return jdbc.query("""
                SELECT id, account_id, telegram_chat_id, topic_id, enabled, debounce_seconds, batch_size, max_provider_calls, max_cost_usd, created_at, updated_at
                FROM auto_pipeline_settings
                WHERE account_id = ? AND telegram_chat_id = ? AND topic_id IS NOT DISTINCT FROM ?
                ORDER BY id DESC
                LIMIT 1
                """, rs -> rs.next() ? autoSetting(rs, 0) : null, accountId, chatId, topicId);
    }

    private Long effectiveBatchTopicId(long accountId, long chatId, Long topicId) {
        AutoPipelineSettingDto topicSetting = topicId == null ? null : findAutoSetting(accountId, chatId, topicId);
        return topicSetting == null ? null : topicId;
    }

    private WorkerSnapshot workerSnapshot() {
        ModelWorkerClient.WorkerHealth health = workerClient.health();
        return new WorkerSnapshot(health.reachable(), health.status(), health.classifierStatus(), health.embeddingStatus());
    }

    private void completeTraceStages(long runId, List<Long> rawIds) {
        for (Long rawId : rawIds) {
            jdbc.update("""
                    UPDATE pipeline_message_trace
                    SET status = 'PROCESSED', error_code = NULL, error_message = NULL,
                        output_json = jsonb_build_object('processed', true),
                        started_at = COALESCE(started_at, now()), finished_at = COALESCE(finished_at, now()), duration_ms = COALESCE(duration_ms, 0), updated_at = now()
                    WHERE replay_run_id = ?
                      AND raw_message_id = ?
                      AND stage_id IN ('normalization', 'cleanup', 'dedupe', 'rule_signals', 'bootstrap_classification')
                    """, runId, rawId);
        }
    }

    private void updateTraceStage(long runId, List<Long> rawIds, String stageId, String status, String errorCode, String errorMessage, JsonNode output) {
        for (Long rawId : rawIds) {
            jdbc.update("""
                    UPDATE pipeline_message_trace
                    SET status = ?, error_code = ?, error_message = ?, output_json = ?::jsonb,
                        started_at = COALESCE(started_at, now()), finished_at = COALESCE(finished_at, now()), duration_ms = COALESCE(duration_ms, 0), updated_at = now()
                    WHERE replay_run_id = ? AND raw_message_id = ? AND stage_id = ?
                    """, status, errorCode, errorMessage, write(output), runId, rawId, stageId);
        }
    }

    private void insertRunStage(long runId, String stage, String status, long input, long output, long skipped, String error) {
        jdbc.update("""
                INSERT INTO replay_run_stages (run_id, stage, status, started_at, finished_at, input_count, output_count, skipped_count, error_count, local_model_call_count, latency_ms, metrics_json, error)
                VALUES (?, ?, ?, now(), now(), ?, ?, ?, 0, 0, 0, '{}'::jsonb, ?)
                """, runId, stage, status, input, output, skipped, error);
    }

    private List<WarningItem> warnings(WorkerSnapshot worker) {
        List<WarningItem> warnings = new ArrayList<>();
        if (!worker.reachable()) warnings.add(new WarningItem("MODEL_WORKER_DOWN", "Воркер моделей недоступен"));
        else if ("MODEL_NOT_CONFIGURED".equals(worker.embeddingStatus())) warnings.add(new WarningItem("DEGRADED_EMBEDDINGS", "Воркер запущен, но BGE-M3 не настроен; embeddings работают в degraded mode"));
        if (!providerConfigured()) warnings.add(new WarningItem("PROVIDER_DISABLED", "Провайдер LLM не настроен или выключен"));
        if (countEnabledAutoPipeline() == 0) warnings.add(new WarningItem("AUTO_PIPELINE_DISABLED", "Автообработка выключена"));
        WhyEmpty why = whyEmptyWithoutWorker(worker);
        if (why.knowledgeItems() == 0) warnings.add(new WarningItem("NO_MATERIALS", why.explanation()));
        return warnings;
    }

    private WhyEmpty whyEmptyWithoutWorker(WorkerSnapshot worker) {
        long raw = count("raw_messages");
        long intakeRows = count("pipeline_message_intake");
        long pending = countStatus("pipeline_message_intake", "PENDING");
        long queued = countStatus("pipeline_message_intake", "QUEUED");
        long processed = countStatus("pipeline_message_intake", "PROCESSED");
        long clusters = countRealClusters();
        long judged = countRealJudgedClusters();
        long materials = countRealKnowledgeItems();
        long singleCandidates = countRealSingleMessageCandidates();
        long directReady = countRealDirectMaterialReady();
        String blocker = countEnabledAutoPipeline() == 0 ? "AUTO_PIPELINE_DISABLED" : !worker.reachable() ? "MODEL_WORKER_DOWN" : (clusters == 0 && singleCandidates == 0) ? "NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES" : "THRESHOLD_NOT_MET";
        String secondary = "AUTO_PIPELINE_DISABLED".equals(blocker) && !worker.reachable() ? "MODEL_WORKER_DOWN" : null;
        return new WhyEmpty(raw, intakeRows, pending, queued, processed, clusters, judged, materials, singleCandidates, directReady, blocker, secondary, explanation(blocker, secondary), nextActions(blocker, secondary));
    }

    private String warningForStage(String stageId, long failed, long waiting, long processed, long skipped, boolean workerReachable, String embeddingStatus) {
        return warningForStage(stageId, failed, waiting, processed, skipped, workerReachable, embeddingStatus, countRealClusters(), countRealSingleMessageCandidates(), providerConfigured());
    }

    private String warningForStage(String stageId, long failed, long waiting, long processed, long skipped, boolean workerReachable, String embeddingStatus, long clusters, long singleCandidates, boolean providerReady) {
        if (failed > 0) return "Есть ошибки stage trace";
        if (waiting <= 0) return null;
        if ("embeddings".equals(stageId)) {
            if (!workerReachable) return "Worker недоступен";
            if ("MODEL_NOT_CONFIGURED".equals(embeddingStatus)) return "BGE-M3 не настроен: degraded embeddings";
            if (processed == 0) return "До embeddings не дошли сообщения";
            return "Нет embedding artifacts для части сообщений";
        }
        if ("clustering".equals(stageId)) {
            if (processed == 0 && skipped == 0) return "Ждёт embeddings";
            if (clusters == 0) return "Нет кластеров";
            return "Кластеризация запускалась не для всех сообщений";
        }
        if ("single_message_detection".equals(stageId)) {
            if (clusters == 0 && singleCandidates == 0) return "Нет single-message кандидатов";
            return "Single-message проверка запускалась не для всех сообщений";
        }
        if ("llm_judge".equals(stageId)) {
            if (!providerReady) return "LLM provider не настроен";
            if (clusters == 0 && singleCandidates == 0) return "LLM не запускался: нет кластеров/кандидатов";
            return "LLM запускался не для всех кандидатов";
        }
        if ("material_generation".equals(stageId)) {
            if (clusters == 0 && singleCandidates == 0) return "Генерация не запускалась: нет кластеров/кандидатов";
            if (!providerReady) return "Генерация не запускалась: LLM provider не настроен";
            return "Материал не создан: пороги/решение не пройдены";
        }
        if ("materials_publish".equals(stageId)) {
            return "Публикация не запускалась: нет материала";
        }
        return null;
    }

    private OffsetDateTime lastUpdated(String stageId) {
        return jdbc.query("SELECT max(updated_at) FROM pipeline_message_trace WHERE stage_id = ?", rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null, stageId);
    }

    private String latestRunStatus() {
        return jdbc.query("SELECT status FROM replay_runs ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : null);
    }

    private long countEnabledAutoPipeline() {
        return countQuery("""
                SELECT count(*)
                FROM telegram_chats c
                WHERE c.is_enabled = true
                  AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)
                """);
    }

    private Long latestRunId() {
        return jdbc.query("SELECT id FROM replay_runs WHERE pipeline_version = 'LIVE_AUTO_RAW_MESSAGES' ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getLong(1) : null);
    }

    private String latestTerminalReason() {
        return jdbc.query("SELECT error FROM replay_runs WHERE pipeline_version = 'LIVE_AUTO_RAW_MESSAGES' ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : null);
    }

    private Long resolveRunId(Long runId) {
        if (runId != null) return runId;
        return latestRunId();
    }

    private int safeLimit(int limit, int fallback, int max) {
        return Math.max(1, Math.min(limit <= 0 ? fallback : limit, max));
    }

    private Map<String, Object> detailBase(String kind, Long runId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kind", kind);
        response.put("runId", runId);
        response.put("latestRunId", latestRunId());
        response.put("latestTerminalReason", latestTerminalReason());
        return response;
    }

    private Map<String, Object> emptyDetail(String kind, String reason, String explanation) {
        Map<String, Object> response = detailBase(kind, null);
        response.put("reason", reason);
        response.put("explanation", explanation);
        response.put("items", List.of());
        return response;
    }

    private String clusterEmptyReason(long runId) {
        long embeddings = countWhere("message_embeddings", "run_id = " + runId);
        long runMessages = countWhere("replay_run_messages", "run_id = " + runId);
        if (runMessages == 0) return "NO_MESSAGES_REACHED_RUN";
        if (embeddings == 0) return "NO_EMBEDDINGS";
        if (embeddings < 2) return "TOO_FEW_EMBEDDINGS";
        return "SIMILARITY_BELOW_THRESHOLD_OR_NO_MICROCLUSTERS";
    }

    private String embeddingReason(WorkerSnapshot worker, long runMessages, long created) {
        if (!worker.reachable()) return "WORKER_DOWN";
        if (runMessages == 0) return "NO_MESSAGES_REACHED_EMBEDDINGS";
        if (created == 0) return "NO_EMBEDDING_ARTIFACTS";
        if (created < runMessages) return "SOME_MESSAGES_WITHOUT_EMBEDDINGS";
        if ("MODEL_NOT_CONFIGURED".equals(worker.embeddingStatus())) return "DEGRADED_VECTOR";
        return "EMBEDDINGS_CREATED";
    }

    private String llmReason(long runId, long clusters, long singleCandidates) {
        if (!providerConfigured()) return "PROVIDER_NOT_CONFIGURED";
        if (clusters == 0 && singleCandidates == 0) return "NOT_STARTED_NO_CLUSTERS_OR_CANDIDATES";
        long calls = countQuery("SELECT count(*) FROM provider_calls WHERE run_id = " + runId + " AND (stage ILIKE '%judge%' OR stage ILIKE '%llm%' OR stage ILIKE '%routing%')");
        if (calls == 0) return "NO_LLM_JUDGE_CALLS";
        long failed = countQuery("SELECT count(*) FROM provider_calls WHERE run_id = " + runId + " AND status <> 'SUCCESS' AND (stage ILIKE '%judge%' OR stage ILIKE '%llm%' OR stage ILIKE '%routing%')");
        return failed > 0 ? "LLM_JUDGE_HAS_ERRORS" : "LLM_JUDGE_RAN";
    }

    private String materialReason(long runId, long candidates, long generated) {
        if (generated > 0) return "MATERIAL_CREATED";
        if (!providerConfigured()) return "PROVIDER_NOT_CONFIGURED";
        if (candidates == 0) return "NOT_STARTED_NO_CANDIDATES";
        long calls = countQuery("SELECT count(*) FROM provider_calls WHERE run_id = " + runId + " AND (stage ILIKE '%generation%' OR stage ILIKE '%material%' OR stage ILIKE '%knowledge%')");
        if (calls == 0) return "GENERATION_NOT_STARTED";
        long failed = countQuery("SELECT count(*) FROM provider_calls WHERE run_id = " + runId + " AND status <> 'SUCCESS' AND (stage ILIKE '%generation%' OR stage ILIKE '%material%' OR stage ILIKE '%knowledge%')");
        return failed > 0 ? "GENERATION_FAILED" : "NO_MATERIAL_AFTER_GENERATION";
    }

    private Map<String, Object> providerCallRow(ResultSet rs, int rowNum) throws SQLException {
        return row(
                "id", rs.getLong("id"),
                "stage", rs.getString("stage"),
                "model", rs.getString("model_name"),
                "status", rs.getString("status"),
                "requestPreview", rs.getString("request_preview"),
                "responsePreview", rs.getString("response_preview"),
                "inputTokens", rs.getInt("input_tokens"),
                "outputTokens", rs.getInt("output_tokens"),
                "cost", rs.getBigDecimal("estimated_cost_usd"),
                "latencyMs", nullableLong(rs, "latency_ms"),
                "errorCode", rs.getString("error_code"),
                "errorMessage", rs.getString("error_message"),
                "createdAt", rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private OffsetDateTime maxTime(String table, String column) {
        return jdbc.query("SELECT max(" + column + ") FROM " + table, rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null);
    }

    private OffsetDateTime maxTimeWhere(String table, String column, String where) {
        return jdbc.query("SELECT max(" + column + ") FROM " + table + " WHERE " + where, rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null);
    }

    private LiveEvent liveEvent(ResultSet rs, int rowNum) throws SQLException {
        return new LiveEvent(
                rs.getString("event_id"),
                rs.getString("type"),
                rs.getObject("timestamp", OffsetDateTime.class),
                rs.getLong("account_id"),
                nullableLong(rs, "telegram_chat_id"),
                nullableLong(rs, "topic_id"),
                nullableLong(rs, "batch_id"),
                nullableLong(rs, "run_id"),
                nullableLong(rs, "raw_message_id"),
                rs.getString("stage"),
                rs.getString("from_stage"),
                rs.getString("to_stage"),
                rs.getString("status"),
                rs.getLong("count"),
                rs.getString("message")
        );
    }

    private List<String> waterfall(String status, String terminalReason, int materialCount, int candidateCount) {
        List<String> steps = new ArrayList<>(List.of("intake", "normalize", "cleanup", "rules", "classifier", "embeddings"));
        if ("NO_CLUSTERS".equals(terminalReason)) {
            steps.add("clustering:NO_CLUSTERS");
        } else {
            steps.add("clustering");
        }
        if (candidateCount > 0) steps.add("single-message:" + candidateCount);
        else steps.add("single-message");
        steps.add("llm");
        steps.add(materialCount > 0 ? "material:" + materialCount : "material:NONE");
        steps.add("status:" + status);
        return steps;
    }

    private String latestFailedStage(long runId) {
        return jdbc.query("""
                SELECT stage
                FROM replay_run_stages
                WHERE run_id = ? AND (status = 'FAILED' OR error IS NOT NULL)
                ORDER BY id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : "pipeline", runId);
    }

    private long countRealKnowledgeItems() {
        return countQuery("""
                SELECT count(*)
                FROM knowledge_items ki
                JOIN replay_runs rr ON rr.id = ki.run_id
                JOIN datasets d ON d.id = rr.dataset_id
                WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')
                """);
    }

    private long countRealProviderCalls() {
        return countQuery("""
                SELECT count(*)
                FROM provider_calls pc
                JOIN replay_runs rr ON rr.id = pc.run_id
                JOIN datasets d ON d.id = rr.dataset_id
                WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')
                """);
    }

    private long countRealClusters() {
        return countQuery("""
                SELECT
                    (SELECT count(*) FROM microclusters mc JOIN replay_runs rr ON rr.id = mc.run_id JOIN datasets d ON d.id = rr.dataset_id WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')) +
                    (SELECT count(*) FROM macroclusters mac JOIN replay_runs rr ON rr.id = mac.run_id JOIN datasets d ON d.id = rr.dataset_id WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')) +
                    (SELECT count(*) FROM replay_clusters rc JOIN replay_runs rr ON rr.id = rc.run_id JOIN datasets d ON d.id = rr.dataset_id WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY'))
                """);
    }

    private long countRealJudgedClusters() {
        return countQuery("""
                SELECT
                    (SELECT count(*) FROM cluster_scores cs JOIN replay_runs rr ON rr.id = cs.run_id JOIN datasets d ON d.id = rr.dataset_id WHERE d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')) +
                    (SELECT count(*) FROM provider_calls pc JOIN replay_runs rr ON rr.id = pc.run_id JOIN datasets d ON d.id = rr.dataset_id WHERE (d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY')) AND pc.stage IN ('LLM_CLUSTER_JUDGE_AND_ROUTING', 'LLM Judge') AND pc.status = 'SUCCESS')
                """);
    }

    private long countRealSingleMessageCandidates() {
        return countQuery("""
                SELECT count(*) FROM replay_run_messages rrm
                JOIN replay_runs rr ON rr.id = rrm.run_id
                JOIN datasets d ON d.id = rr.dataset_id
                WHERE (d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY'))
                  AND rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE', 'DIRECT_MATERIAL_READY')
                """);
    }
    private long countRealDirectMaterialReady() {
        return countQuery("""
                SELECT count(*) FROM replay_run_messages rrm
                JOIN replay_runs rr ON rr.id = rrm.run_id
                JOIN datasets d ON d.id = rr.dataset_id
                WHERE (d.source = 'RAW_MESSAGES' OR d.source_kind IN ('LIVE_AUTO_RAW_MESSAGES', 'RAW_MESSAGES_REPLAY'))
                  AND rrm.final_decision = 'DIRECT_MATERIAL_READY'
                """);
    }
    private boolean providerConfigured() {
        String key = environment.getProperty("MODELHUB_API_KEY");
        if (key != null && !key.isBlank()) return true;
        return countWhere("ai_providers", "enabled = true AND api_key_ref IS NOT NULL AND btrim(api_key_ref) <> ''") > 0;
    }

    private Downstream downstream(long rawMessageId, Long replayRunId) {
        Long datasetMessageId = jdbc.query("""
                SELECT dm.id
                FROM dataset_messages dm
                JOIN raw_messages m ON dm.account_id = m.account_id AND dm.telegram_chat_id = m.telegram_chat_id AND dm.telegram_message_id = m.telegram_message_id
                WHERE m.id = ?
                ORDER BY dm.id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, rawMessageId);
        if (datasetMessageId == null) {
            return new Downstream(null, null, null, null, null, List.of());
        }
        Long runId = replayRunId == null ? jdbc.query("SELECT run_id FROM replay_run_messages WHERE dataset_message_id = ? ORDER BY run_id DESC LIMIT 1", rs -> rs.next() ? rs.getLong(1) : null, datasetMessageId) : replayRunId;
        Map<String, Object> classification = runId == null ? null : jdbc.query("SELECT top_label, confidence, classification_source FROM message_classifications WHERE run_id = ? AND dataset_message_id = ? ORDER BY id DESC LIMIT 1", rs -> rs.next() ? row("topLabel", rs.getString("top_label"), "confidence", rs.getBigDecimal("confidence"), "source", rs.getString("classification_source")) : null, runId, datasetMessageId);
        Map<String, Object> embedding = runId == null ? null : jdbc.query("SELECT embedding_kind, embedding_hash FROM message_embeddings WHERE run_id = ? AND dataset_message_id = ? ORDER BY id DESC LIMIT 1", rs -> rs.next() ? row("kind", rs.getString("embedding_kind"), "hash", rs.getString("embedding_hash")) : null, runId, datasetMessageId);
        Map<String, Object> cluster = runId == null ? null : jdbc.query("SELECT microcluster_id, macrocluster_id FROM replay_run_messages WHERE run_id = ? AND dataset_message_id = ?", rs -> rs.next() ? row("microclusterId", nullableLong(rs, "microcluster_id"), "macroclusterId", nullableLong(rs, "macrocluster_id")) : null, runId, datasetMessageId);
        List<Map<String, Object>> materials = runId == null ? List.of() : jdbc.query("""
                SELECT ki.id, ki.title, ki.status
                FROM knowledge_items ki
                LEFT JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
                WHERE ki.run_id = ? AND (kis.dataset_message_id = ? OR ki.source_message_ids @> to_jsonb(ARRAY[?]::bigint[]))
                ORDER BY ki.id DESC
                LIMIT 20
                """, (rs, rowNum) -> row("id", rs.getLong("id"), "title", rs.getString("title"), "status", Objects.toString(rs.getString("status"), "DRAFT")), runId, datasetMessageId, datasetMessageId);
        return new Downstream(datasetMessageId, runId, classification, embedding, cluster, materials);
    }

    private String exactBlocker(Intake intake, List<TraceRow> traces, Downstream downstream) {
        if (intake == null) return "NO_INTAKE";
        if ("AUTO_PIPELINE_DISABLED".equals(intake.reason())) return "AUTO_PIPELINE_DISABLED";
        if ("NO_TEXT".equals(intake.reason())) return "NO_TEXT";
        for (TraceRow trace : traces) {
            if ("FAILED".equals(trace.status())) return nullTo(trace.errorCode(), "STAGE_FAILED");
            if ("WAITING_FOR_WORKER".equals(trace.status())) return nullTo(trace.errorCode(), "MODEL_WORKER_DOWN");
        }
        if (downstream.materials().isEmpty()) return whyEmpty().mainBlocker();
        return "NONE";
    }

    private List<ActionState> retryActions(boolean enabled, String reason) {
        return List.of(new ActionState("retryStage", enabled, reason), new ActionState("retryPipeline", enabled, reason));
    }

    private RawMessage rawMessage(ResultSet rs) throws SQLException {
        return new RawMessage(rs.getLong("id"), rs.getLong("account_id"), rs.getLong("telegram_chat_id"), rs.getLong("telegram_message_id"), rs.getString("chat_title"), rs.getString("topic_title"), rs.getString("content_type"), rs.getString("text"), parseJson(rs.getString("raw_json")));
    }

    private StageMessage stageMessage(ResultSet rs, int rowNum) throws SQLException {
        String reason = rs.getString("error_code");
        if (reason == null || reason.isBlank()) {
            reason = switch (rs.getString("status")) {
                case "PENDING" -> "AUTO_PIPELINE_DISABLED";
                case "WAITING_FOR_WORKER" -> "MODEL_WORKER_DOWN";
                default -> null;
            };
        }
        return new StageMessage(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getLong("telegram_chat_id"),
                rs.getLong("telegram_message_id"),
                rs.getString("chat_title"),
                rs.getString("topic_title"),
                rs.getString("content_type"),
                rs.getString("text"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getString("stage_id"),
                reason,
                actionHint(reason),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Intake intake(ResultSet rs) throws SQLException {
        return new Intake(rs.getLong("raw_message_id"), rs.getString("status"), rs.getString("reason"), rs.getString("intake_source"), nullableLong(rs, "replay_run_id"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private TraceRow traceRow(ResultSet rs, int rowNum) throws SQLException {
        return new TraceRow(rs.getString("stage_id"), rs.getString("stage_name"), rs.getString("status"), parseJson(rs.getString("input_json")), parseJson(rs.getString("output_json")), rs.getString("error_code"), rs.getString("error_message"), rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finished_at", OffsetDateTime.class), nullableLong(rs, "duration_ms"), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private long count(String table) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM " + table, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private long countStatus(String table, String status) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE status = ?", Number.class, status);
        return n == null ? 0 : n.longValue();
    }

    private long countWhere(String table, String where) {
        Number n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private long countQuery(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private String explanation(String blocker) {
        return switch (blocker) {
            case "MODEL_WORKER_DOWN" -> "Материалы не созданы, потому что worker для классификации/embeddings недоступен.";
            case "AUTO_PIPELINE_DISABLED" -> "Сообщения получены и находятся в intake, но автообработка выключена для production scope.";
            case "NO_CLUSTERS" -> "Материалы не созданы, потому что пока нет кластеров-кандидатов.";
            case "NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES" -> "Материалы не созданы: нет ни кластеров, ни single-message кандидатов.";
            case "SINGLE_MESSAGE_CANDIDATES_NOT_GENERATED" -> "Кластеров нет, но найдены одиночные сообщения-кандидаты. Генерация не выполнена из-за provider blocker.";
            case "PROVIDER_DISABLED_OR_KEY_MISSING" -> "Кластеров нет, но найдены одиночные сообщения, готовые для материала. Генерация не выполнена из-за provider blocker.";
            case "PROVIDER_DISABLED" -> "Материалы не созданы, потому что LLM provider не настроен или выключен.";
            case "BUDGET_BLOCKED" -> "Материалы не созданы из-за бюджетного ограничения.";
            case "THRESHOLD_NOT_MET" -> "Материалы не созданы: сообщения/кластеры не прошли пороги генерации.";
            default -> "Материалы есть или blocker не обнаружен.";
        };
    }

    private ObjectNode object(Object... keyValues) {
        ObjectNode node = json.createObjectNode();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object value = keyValues[i + 1];
            if (value == null) node.putNull(String.valueOf(keyValues[i]));
            else if (value instanceof Number number) node.put(String.valueOf(keyValues[i]), number.longValue());
            else if (value instanceof Boolean bool) node.put(String.valueOf(keyValues[i]), bool);
            else node.put(String.valueOf(keyValues[i]), String.valueOf(value));
        }
        return node;
    }

    private String write(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot serialize json", error);
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return value == null ? json.createObjectNode() : json.readTree(value);
        } catch (Exception error) {
            return json.createObjectNode();
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String actionHint(String reason) {
        if ("AUTO_PIPELINE_DISABLED".equals(reason)) return "Включите автообработку только для выбранного чата или темы";
        if ("MODEL_WORKER_DOWN".equals(reason) || "MODEL_WORKER_NOT_CONFIRMED".equals(reason)) return "Поднимите model worker; обработка дойдёт только до стадий без воркера";
        if ("WAITING_FOR_BATCH".equals(reason)) return "Дождитесь debounce/batch или проверьте очередь";
        if ("NO_TEXT".equals(reason)) return "Действие не требуется: нет текста для обработки";
        return "Откройте trace сообщения";
    }

    private String labelForCode(String code) {
        return switch (code == null ? "" : code) {
            case "AUTO_PIPELINE_DISABLED" -> "Автообработка выключена для этого чата или темы";
            case "CHAT_NOT_ACTIVE_MEMBER" -> "Чат не является активным TDLib dialog для аккаунта";
            case "PUBLIC_DISCOVERY_NOT_ALLOWED" -> "Public discovery/search не может запускать auto pipeline";
            case "MODEL_WORKER_DOWN" -> "Модельный воркер недоступен";
            case "MODEL_NOT_CONFIGURED" -> "Модель embeddings недоступна или не настроена";
            case "PROVIDER_DISABLED", "PROVIDER_DISABLED_OR_KEY_MISSING" -> "AI-провайдер не настроен или выключен";
            case "NO_CLUSTERS" -> "Недостаточно похожих сообщений для кластера";
            case "NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES" -> "Недостаточно сильных сообщений для материала";
            case "SINGLE_MESSAGE_CANDIDATES_WAITING_FOR_LLM" -> "Одиночные кандидаты ждут LLM-оценку";
            case "THRESHOLD_NOT_MET" -> "Порог качества материала не пройден";
            case "NO_MATERIALS" -> "Материалы пока не сформированы";
            case "TDLIB_UPDATE_NOT_RECEIVED" -> "Telegram-обновления не приходят";
            default -> code == null || code.isBlank() ? "Нет blocker" : code;
        };
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    private String explanation(String blocker, String secondaryBlocker) {
        if ("AUTO_PIPELINE_DISABLED".equals(blocker) && "MODEL_WORKER_DOWN".equals(secondaryBlocker)) {
            return "Сообщения получены и находятся в intake, но автообработка выключена. Даже после включения часть стадий будет ждать model worker.";
        }
        return explanation(blocker);
    }

    private List<String> nextActions(String blocker, String secondaryBlocker) {
        List<String> actions = new ArrayList<>();
        if ("AUTO_PIPELINE_DISABLED".equals(blocker)) {
            actions.add("ENABLE_AUTO_PIPELINE_FOR_CHAT_OR_TOPIC");
        }
        if ("MODEL_WORKER_DOWN".equals(blocker) || "MODEL_WORKER_DOWN".equals(secondaryBlocker)) {
            actions.add("START_MODEL_WORKER");
        }
        if ("PROVIDER_DISABLED".equals(blocker)) {
            actions.add("CONFIGURE_PROVIDER");
        }
        if ("NO_CLUSTERS".equals(blocker) || "NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES".equals(blocker)) {
            actions.add("RUN_SCOPED_REPLAY_OR_WAIT_FOR_MORE_MESSAGES");
        }
        if ("SINGLE_MESSAGE_CANDIDATES_NOT_GENERATED".equals(blocker) || "PROVIDER_DISABLED_OR_KEY_MISSING".equals(blocker)) {
            actions.add("GENERATE_FROM_SINGLE_MESSAGE_CANDIDATES");
            actions.add("FIX_PROVIDER");
        }
        if (actions.isEmpty()) {
            actions.add("OPEN_PIPELINE_TRACE");
        }
        return actions;
    }

    private Blocker blocker(Intake intake, List<TraceRow> traces, Downstream downstream) {
        if (intake == null) return new Blocker("NO_INTAKE", "У сообщения нет intake row.", "CREATE_INTAKE_FROM_RAW");
        if ("AUTO_PIPELINE_DISABLED".equals(intake.reason())) return new Blocker("AUTO_PIPELINE_DISABLED", "Сообщение принято, но автообработка выключена для этого чата/темы.", "ENABLE_AUTO_PIPELINE_FOR_SCOPE");
        if ("NO_TEXT".equals(intake.reason())) return new Blocker("NO_TEXT", "Сообщение не содержит текста или подписи, поэтому полезная обработка пропущена.", "NO_ACTION_REQUIRED");
        for (TraceRow trace : traces) {
            if ("FAILED".equals(trace.status())) return new Blocker(nullTo(trace.errorCode(), "STAGE_FAILED"), nullTo(trace.errorMessage(), "Стадия завершилась ошибкой."), "OPEN_STAGE_ERROR");
            if ("WAITING_FOR_WORKER".equals(trace.status())) return new Blocker(nullTo(trace.errorCode(), "MODEL_WORKER_DOWN"), nullTo(trace.errorMessage(), "Стадия требует локальный model worker, но worker недоступен."), "START_MODEL_WORKER");
            if ("PENDING".equals(trace.status()) && trace.errorCode() != null) return new Blocker(trace.errorCode(), nullTo(trace.errorMessage(), "Сообщение ожидает обработки."), "WAIT_OR_ENABLE_SCOPE");
        }
        if (downstream.materials().isEmpty()) {
            WhyEmpty why = whyEmpty();
            return new Blocker(why.mainBlocker(), why.explanation(), why.nextActions().isEmpty() ? "OPEN_PIPELINE_TRACE" : why.nextActions().get(0));
        }
        return new Blocker("NONE", "Сообщение прошло доступные стадии.", "NO_ACTION_REQUIRED");
    }

    private TraceRow currentTrace(List<TraceRow> traces) {
        for (TraceRow trace : traces) {
            if ("FAILED".equals(trace.status()) || "PENDING".equals(trace.status()) || "WAITING_FOR_WORKER".equals(trace.status()) || "PROCESSING".equals(trace.status())) {
                return trace;
            }
        }
        return traces.isEmpty() ? null : traces.get(traces.size() - 1);
    }

    private void validateScoped(AutoPipelineRequest request) {
        if (request == null || request.accountId() == null || request.chatId() == null) {
            throw new IllegalArgumentException("accountId and chatId are required; global auto pipeline is not allowed");
        }
    }

    private void validateScoped(IntakeBackfillRequest request) {
        if (request == null || request.accountId() == null || request.chatId() == null) {
            throw new IllegalArgumentException("accountId and chatId are required; global intake backfill is not allowed");
        }
    }

    private long ensureCollectingBatch(long accountId, long telegramChatId, Long topicId) {
        List<Long> existing = jdbc.queryForList("""
                SELECT id
                FROM auto_pipeline_batches
                WHERE account_id = ? AND telegram_chat_id IS NOT DISTINCT FROM ? AND topic_id IS NOT DISTINCT FROM ? AND status = 'COLLECTING'
                ORDER BY created_at DESC
                LIMIT 1
                """, Long.class, accountId, telegramChatId, topicId);
        if (!existing.isEmpty()) return existing.get(0);
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO auto_pipeline_batches (account_id, telegram_chat_id, topic_id, status, ready_at)
                SELECT ?, ?, ?, 'COLLECTING', now() + make_interval(secs => COALESCE((
                    SELECT debounce_seconds FROM auto_pipeline_settings
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
                ), 30))
                RETURNING id
                """, Long.class, accountId, telegramChatId, topicId, accountId, telegramChatId, topicId, telegramChatId, topicId, telegramChatId));
    }

    private AutoPipelineSettingDto autoSetting(ResultSet rs, int rowNum) throws SQLException {
        return new AutoPipelineSettingDto(nullableLong(rs, "id"), rs.getLong("account_id"), nullableLong(rs, "telegram_chat_id"), nullableLong(rs, "topic_id"), rs.getBoolean("enabled"), rs.getInt("debounce_seconds"), rs.getInt("batch_size"), rs.getInt("max_provider_calls"), rs.getBigDecimal("max_cost_usd"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private record StageSpec(int ordinal, String id, String name, String description) {}
    private record WorkerSnapshot(boolean reachable, String status, String classifierStatus, String embeddingStatus) {}
    private record RawCandidate(long rawMessageId, long accountId, long telegramChatId, Long topicId, Long messageThreadId, boolean hasText, boolean hasIntake, boolean hasQueue) {}
    private record Blocker(String reason, String explanation, String nextAction) {}
    private record LiveRun(long runId, long datasetId, long batchId) {}

    public record Summary(long rawMessages, long intakePending, long intakeQueued, long processing, long processed, long skipped, long failed, long waitingForWorker, long generatedMaterials, String latestRunStatus, String workerStatus, long autoPipelineEnabledCount, List<WarningItem> warnings, String mainBlocker, String secondaryBlocker, String explanation, List<String> nextActions, long incomingLastMinute, long intakePendingTotal, long pendingInsideEnabledScopes, long pendingOutsideEnabledScopes, long liveQueue, long collectingBatches, long collectingMessages, long pendingRuns, long runningRuns, long completedRuns, Long latestRunId, String latestTerminalReason, OffsetDateTime lastSchedulerTickAt, OffsetDateTime lastMessageReceivedAt, OffsetDateTime lastBatchFlushedAt, OffsetDateTime lastRunStartedAt, OffsetDateTime lastRunCompletedAt) {}
    public record LiveAcceptance(long latestRawId, OffsetDateTime latestRawAt, Long latestProcessedRawId, OffsetDateTime latestProcessedAt, long rawGap, Long runId, String runStatus, String terminalReason, long messageCount, OffsetDateTime runCreatedAt, OffsetDateTime runStartedAt, OffsetDateTime runFinishedAt, long replayRunMessages, long messageEmbeddings, long microclusters, long macroclusters, long singleCandidates, long providerCalls, long materials) {}
    public record WarningItem(String code, String message) {}
    public record StageSummary(int ordinal, String id, String name, String description, String status, long active, long processed, long failed, long skipped, long waiting, Long averageLatencyMs, OffsetDateTime lastUpdatedAt, String warning) {}
    public record StageDetails(StageSummary stage, String reason, String explanation, List<LiveEvent> latestEvents, List<StageMessage> waitingMessages, String workerStatus, String embeddingStatus, String classifierStatus, long clusters, long singleMessageCandidates, long providerCalls, long materialCount) {}
    public record StageMessage(long id, long accountId, long telegramChatId, long telegramMessageId, String chatTitle, String topicTitle, String contentType, String text, String status, String errorCode, String errorMessage, String currentStage, String blockedReason, String actionHint, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record RawMessage(long id, long accountId, long telegramChatId, long telegramMessageId, String chatTitle, String topicTitle, String contentType, String text, JsonNode rawJson) {}
    public record Intake(long rawMessageId, String status, String reason, String intakeSource, Long replayRunId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record TraceRow(String stageId, String stageName, String status, JsonNode inputJson, JsonNode outputJson, String errorCode, String errorMessage, OffsetDateTime startedAt, OffsetDateTime finishedAt, Long durationMs, OffsetDateTime updatedAt) {}
    public record Downstream(Long datasetMessageId, Long runId, Map<String, Object> classification, Map<String, Object> embedding, Map<String, Object> cluster, List<Map<String, Object>> materials) {}
    public record ActionState(String action, boolean enabled, String reason) {}
    public record MessageTrace(RawMessage rawMessage, Intake intake, List<TraceRow> traces, Downstream downstream, String blockedReason, String blockedExplanation, String nextAction, String currentStage, String currentStatus, List<ActionState> actions) {}
    public record WhyEmpty(long rawMessages, long intakeRows, long pending, long queued, long processedMessages, long clusters, long llmJudgedClusters, long knowledgeItems, long singleMessageCandidates, long directMaterialReady, String mainBlocker, String secondaryBlocker, String explanation, List<String> nextActions) {}
    public record AutoPipelineRequest(Long accountId, Long chatId, Long topicId, Boolean enabled, Integer debounceSeconds, Integer batchSize, Integer maxProviderCalls, BigDecimal maxCostUsd, Boolean autoPublish) {
        int debounceSecondsOrDefault() { return debounceSeconds == null ? 30 : Math.max(10, Math.min(debounceSeconds, 300)); }
        int batchSizeOrDefault() { return batchSize == null ? 10 : Math.max(1, Math.min(batchSize, 50)); }
        int maxProviderCallsOrDefault() { return maxProviderCalls == null ? 10 : Math.max(0, Math.min(maxProviderCalls, 100)); }
        BigDecimal maxCostUsdOrDefault() { return maxCostUsd == null ? BigDecimal.ONE : maxCostUsd; }
    }
    public record AutoPipelineSettingDto(Long id, long accountId, Long chatId, Long topicId, boolean enabled, int debounceSeconds, int batchSize, int maxProviderCalls, BigDecimal maxCostUsd, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record AutoPipelineStatusDto(long accountId, long chatId, Long topicId, String scope, boolean configured, boolean enabled, boolean manuallyDisabled, boolean effectiveEnabled, String source, int debounceSeconds, int batchSize, int maxProviderCalls, BigDecimal maxCostUsd) {}
    public record IntakeBackfillRequest(Long accountId, Long chatId, Long topicId, Integer limit, Boolean dryRun) {}
    public record IntakeBackfillResult(int rawFound, int intakeCreated, int queued, int skippedExisting, int skippedOutOfScope, boolean dryRun, boolean autoEnabled) {}
    public record QueuePendingRequest(Integer limit, Boolean dryRun) {}
    public record QueuePendingResult(int candidates, int queued, long remainingInsideEnabledScopes, boolean dryRun) {}
    public record LiveAutoRunStartResult(long runId, boolean started, String status, int messages, String workerStatus, String embeddingStatus) {}
    public record RunHealth(long runId, String verdict, String label, String currentStage, OffsetDateTime lastProgressAt, long queued, long processing, long processed, long failed, String blocker, String nextAction) {}
    public record LiveEvent(String eventId, String type, OffsetDateTime timestamp, long accountId, Long chatId, Long topicId, Long batchId, Long runId, Long rawMessageId, String stage, String fromStage, String toStage, String status, long count, String message) {}
    public record LiveBatch(long batchId, Long runId, long accountId, Long chatId, Long topicId, String status, int messageCount, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime startedAt, OffsetDateTime completedAt, String terminalReason, int materialCount, int candidateCount, String error, List<String> waterfall) {}
    public record PendingBreakdownRow(long accountId, long chatId, Long topicId, String chatTitle, String topicTitle, boolean autoEnabledEffective, long pending, long queued, long collectingBatches, long runningRuns, long processed, OffsetDateTime latestRawMessageAt, OffsetDateTime latestQueuedAt) {}
}
