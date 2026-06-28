CREATE TABLE IF NOT EXISTS telegram_runtime_events (
    id BIGSERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    account_id BIGINT,
    telegram_chat_id BIGINT,
    telegram_message_id BIGINT,
    correlation_id TEXT,
    update_id TEXT,
    duration_ms BIGINT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_telegram_runtime_events_created_at ON telegram_runtime_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telegram_runtime_events_type ON telegram_runtime_events (event_type);
CREATE INDEX IF NOT EXISTS idx_telegram_runtime_events_account_chat ON telegram_runtime_events (account_id, telegram_chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS telegram_runtime_errors (
    id BIGSERIAL PRIMARY KEY,
    level TEXT NOT NULL DEFAULT 'ERROR',
    account_id BIGINT,
    telegram_chat_id BIGINT,
    telegram_message_id BIGINT,
    event_type TEXT NOT NULL,
    message TEXT,
    error_code TEXT,
    error_summary TEXT,
    correlation_id TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_telegram_runtime_errors_created_at ON telegram_runtime_errors (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telegram_runtime_errors_account ON telegram_runtime_errors (account_id, created_at DESC);
