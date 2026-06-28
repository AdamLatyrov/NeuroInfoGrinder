ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS title_source TEXT NOT NULL DEFAULT 'PLACEHOLDER_UNKNOWN';
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS title_confidence NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS title_updated_at TIMESTAMPTZ;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS sync_state TEXT NOT NULL DEFAULT 'NEEDS_TOPIC_SYNC';

CREATE UNIQUE INDEX IF NOT EXISTS uq_telegram_topics_account_chat_thread
    ON telegram_topics (account_id, telegram_chat_id, message_thread_id)
    WHERE message_thread_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS telegram_topic_sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    sync_id TEXT NOT NULL UNIQUE,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    requested_limit INTEGER NOT NULL DEFAULT 50,
    loaded_count INTEGER NOT NULL DEFAULT 0,
    stored_count INTEGER NOT NULL DEFAULT 0,
    pages_loaded INTEGER NOT NULL DEFAULT 0,
    next_offset_date INTEGER,
    next_offset_message_id BIGINT,
    next_offset_forum_topic_id INTEGER,
    last_error TEXT,
    usable_topics BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_telegram_topic_sync_jobs_account_chat
    ON telegram_topic_sync_jobs (account_id, telegram_chat_id, started_at DESC);

CREATE TABLE IF NOT EXISTS auto_pipeline_settings (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT,
    topic_id BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    debounce_seconds INTEGER NOT NULL DEFAULT 30,
    batch_size INTEGER NOT NULL DEFAULT 10,
    max_provider_calls INTEGER NOT NULL DEFAULT 10,
    max_cost_usd NUMERIC(10,4) NOT NULL DEFAULT 1.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_auto_pipeline_settings_account_global
    ON auto_pipeline_settings (account_id)
    WHERE telegram_chat_id IS NULL AND topic_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_auto_pipeline_settings_account_chat
    ON auto_pipeline_settings (account_id, telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL AND topic_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_auto_pipeline_settings_account_chat_topic
    ON auto_pipeline_settings (account_id, telegram_chat_id, topic_id)
    WHERE telegram_chat_id IS NOT NULL AND topic_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS auto_pipeline_batches (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT,
    topic_id BIGINT,
    status TEXT NOT NULL DEFAULT 'COLLECTING',
    message_count INTEGER NOT NULL DEFAULT 0,
    replay_run_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    error TEXT
);

CREATE INDEX IF NOT EXISTS idx_auto_pipeline_batches_collecting
    ON auto_pipeline_batches (account_id, telegram_chat_id, topic_id, status, created_at);

CREATE TABLE IF NOT EXISTS auto_pipeline_queue (
    raw_message_id BIGINT PRIMARY KEY REFERENCES raw_messages(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    topic_id BIGINT,
    batch_id BIGINT REFERENCES auto_pipeline_batches(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auto_pipeline_queue_status
    ON auto_pipeline_queue (status, created_at);
