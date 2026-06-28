CREATE TABLE IF NOT EXISTS pipeline_message_intake (
    id BIGSERIAL PRIMARY KEY,
    raw_message_id BIGINT NOT NULL REFERENCES raw_messages(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    topic_id BIGINT,
    message_thread_id BIGINT,
    intake_source TEXT NOT NULL DEFAULT 'TELEGRAM_LIVE',
    status TEXT NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    replay_run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_message_intake_raw_message UNIQUE (raw_message_id),
    CONSTRAINT chk_pipeline_message_intake_source CHECK (intake_source IN ('TELEGRAM_LIVE', 'TELEGRAM_BACKFILL', 'DATASET_REPLAY', 'MANUAL_RETRY')),
    CONSTRAINT chk_pipeline_message_intake_status CHECK (status IN ('PENDING', 'QUEUED', 'PROCESSING', 'PROCESSED', 'SKIPPED', 'FAILED', 'WAITING_FOR_WORKER'))
);

CREATE INDEX IF NOT EXISTS idx_pipeline_message_intake_status
    ON pipeline_message_intake (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_pipeline_message_intake_scope
    ON pipeline_message_intake (account_id, telegram_chat_id, topic_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS pipeline_message_trace (
    id BIGSERIAL PRIMARY KEY,
    raw_message_id BIGINT NOT NULL REFERENCES raw_messages(id) ON DELETE CASCADE,
    replay_run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    stage_id TEXT NOT NULL,
    stage_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code TEXT,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_message_trace_raw_stage UNIQUE (raw_message_id, stage_id),
    CONSTRAINT chk_pipeline_message_trace_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'SKIPPED', 'FAILED', 'WAITING_FOR_WORKER'))
);

CREATE INDEX IF NOT EXISTS idx_pipeline_message_trace_stage_status
    ON pipeline_message_trace (stage_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_pipeline_message_trace_raw_message
    ON pipeline_message_trace (raw_message_id, stage_id);

INSERT INTO pipeline_message_intake (
    raw_message_id,
    account_id,
    telegram_chat_id,
    topic_id,
    message_thread_id,
    intake_source,
    status,
    reason,
    created_at,
    updated_at
)
SELECT m.id,
       m.account_id,
       m.telegram_chat_id,
       m.telegram_topic_id,
       m.message_thread_id,
       'TELEGRAM_LIVE',
       CASE WHEN COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') = '' THEN 'SKIPPED' ELSE 'PENDING' END,
       CASE WHEN COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') = '' THEN 'NO_TEXT' ELSE 'AUTO_PIPELINE_DISABLED' END,
       COALESCE(m.ingested_at, now()),
       now()
FROM raw_messages m
ON CONFLICT (raw_message_id) DO NOTHING;

INSERT INTO pipeline_message_trace (raw_message_id, stage_id, stage_name, status, input_json, output_json, error_code, error_message, started_at, finished_at, duration_ms, created_at, updated_at)
SELECT m.id,
       s.stage_id,
       s.stage_name,
       CASE
           WHEN s.ordinal <= 2 THEN 'PROCESSED'
           WHEN COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') = '' THEN 'SKIPPED'
           WHEN s.ordinal BETWEEN 3 AND 7 THEN 'PENDING'
           ELSE 'WAITING_FOR_WORKER'
       END,
       jsonb_build_object('rawMessageId', m.id, 'accountId', m.account_id, 'telegramChatId', m.telegram_chat_id),
       CASE WHEN s.ordinal <= 2 THEN jsonb_build_object('stored', true) ELSE '{}'::jsonb END,
       CASE
           WHEN COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') = '' THEN 'NO_TEXT'
           WHEN s.ordinal >= 8 THEN 'MODEL_WORKER_NOT_CONFIRMED'
           ELSE NULL
       END,
       CASE
           WHEN COALESCE(NULLIF(btrim(COALESCE(m.text, m.caption, '')), ''), '') = '' THEN 'Message has no text or caption'
           WHEN s.ordinal >= 8 THEN 'Worker-dependent stages wait for a replay/model worker run'
           ELSE NULL
       END,
       CASE WHEN s.ordinal <= 2 THEN COALESCE(m.ingested_at, now()) ELSE NULL END,
       CASE WHEN s.ordinal <= 2 THEN COALESCE(m.ingested_at, now()) ELSE NULL END,
       CASE WHEN s.ordinal <= 2 THEN 0 ELSE NULL END,
       COALESCE(m.ingested_at, now()),
       now()
FROM raw_messages m
CROSS JOIN (VALUES
    (1, 'telegram_ingest', 'Сбор из Telegram / Форумов'),
    (2, 'db_cache', 'DB-cache'),
    (3, 'normalization', 'Нормализация'),
    (4, 'cleanup', 'Очистка'),
    (5, 'dedupe', 'Дедупликация'),
    (6, 'rule_signals', 'Rule-сигналы'),
    (7, 'bootstrap_classification', 'Bootstrap-классификация'),
    (8, 'embeddings', 'Embeddings'),
    (9, 'clustering', 'Кластеризация'),
    (10, 'llm_judge', 'LLM Judge'),
    (11, 'material_generation', 'Генерация материалов'),
    (12, 'materials_publish', 'Публикация в Материалы')
) AS s(ordinal, stage_id, stage_name)
ON CONFLICT (raw_message_id, stage_id) DO NOTHING;
