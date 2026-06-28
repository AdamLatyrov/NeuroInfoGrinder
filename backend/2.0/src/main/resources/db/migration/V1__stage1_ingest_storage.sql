CREATE TABLE IF NOT EXISTS telegram_accounts (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    phone_masked TEXT,
    status TEXT NOT NULL DEFAULT 'AUTH_REQUIRED',
    tdlib_database_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_telegram_accounts_status CHECK (status IN (
        'AUTH_REQUIRED',
        'WAIT_PHONE',
        'WAIT_CODE',
        'WAIT_PASSWORD',
        'CONNECTING',
        'CONNECTED',
        'DEGRADED',
        'DISCONNECTED',
        'FLOOD_WAIT'
    ))
);

CREATE TABLE IF NOT EXISTS telegram_chats (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'GROUP',
    username TEXT,
    is_forum BOOLEAN NOT NULL DEFAULT FALSE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_message_id BIGINT,
    last_message_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_telegram_chats_account_chat UNIQUE (account_id, telegram_chat_id)
);

CREATE TABLE IF NOT EXISTS telegram_topics (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    telegram_topic_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_telegram_topics_account_chat_topic UNIQUE (account_id, telegram_chat_id, telegram_topic_id)
);

CREATE TABLE IF NOT EXISTS tdlib_update_inbox (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    update_type TEXT NOT NULL,
    telegram_chat_id BIGINT,
    telegram_message_id BIGINT,
    raw_update_json JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    persisted_at TIMESTAMPTZ,
    processing_status TEXT NOT NULL DEFAULT 'PENDING',
    error TEXT,
    CONSTRAINT uq_tdlib_update_inbox_message_update UNIQUE (account_id, update_type, telegram_chat_id, telegram_message_id)
);

CREATE TABLE IF NOT EXISTS raw_messages (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    telegram_message_id BIGINT NOT NULL,
    telegram_topic_id BIGINT,
    sender_id BIGINT,
    sender_name TEXT,
    sender_username TEXT,
    sender_is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    message_date TIMESTAMPTZ,
    edit_date TIMESTAMPTZ,
    reply_to_message_id BIGINT,
    text TEXT,
    caption TEXT,
    content_type TEXT NOT NULL DEFAULT 'messageText',
    has_text BOOLEAN NOT NULL DEFAULT FALSE,
    has_media BOOLEAN NOT NULL DEFAULT FALSE,
    has_links BOOLEAN NOT NULL DEFAULT FALSE,
    raw_json JSONB NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_raw_messages_account_chat_message UNIQUE (account_id, telegram_chat_id, telegram_message_id)
);

CREATE INDEX IF NOT EXISTS idx_raw_messages_chat_date ON raw_messages (telegram_chat_id, message_date DESC);
CREATE INDEX IF NOT EXISTS idx_raw_messages_account_chat_date ON raw_messages (account_id, telegram_chat_id, message_date DESC);
CREATE INDEX IF NOT EXISTS idx_raw_messages_topic_date ON raw_messages (telegram_topic_id, message_date DESC);
CREATE INDEX IF NOT EXISTS idx_raw_messages_reply_to ON raw_messages (reply_to_message_id);
CREATE INDEX IF NOT EXISTS idx_raw_messages_has_links ON raw_messages (has_links);

CREATE TABLE IF NOT EXISTS message_links (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES raw_messages(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    telegram_message_id BIGINT NOT NULL,
    url TEXT NOT NULL,
    normalized_url TEXT,
    domain TEXT,
    anchor_text TEXT,
    source TEXT NOT NULL DEFAULT 'UNKNOWN',
    entity_type TEXT NOT NULL DEFAULT 'UNKNOWN',
    offset_start INTEGER,
    offset_end INTEGER,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible_url BOOLEAN NOT NULL DEFAULT FALSE,
    is_telegram_link BOOLEAN NOT NULL DEFAULT FALSE,
    is_referral_like BOOLEAN NOT NULL DEFAULT FALSE,
    raw_entity_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_message_links_source CHECK (source IN ('TEXT', 'CAPTION', 'WEB_PAGE', 'BUTTON', 'UNKNOWN')),
    CONSTRAINT chk_message_links_entity_type CHECK (entity_type IN (
        'TEXT_URL',
        'URL',
        'MENTION',
        'TEXT_MENTION',
        'EMAIL',
        'PHONE',
        'BOT_COMMAND',
        'UNKNOWN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_message_links_message_id ON message_links (message_id);
CREATE INDEX IF NOT EXISTS idx_message_links_domain ON message_links (domain);
CREATE INDEX IF NOT EXISTS idx_message_links_account_chat_message ON message_links (account_id, telegram_chat_id, telegram_message_id);

CREATE TABLE IF NOT EXISTS raw_message_media (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES raw_messages(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL,
    file_id TEXT,
    file_unique_id TEXT,
    mime_type TEXT,
    file_name TEXT,
    file_size BIGINT,
    caption TEXT,
    raw_media_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pipeline_events (
    id BIGSERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    entity_type TEXT,
    entity_id BIGINT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_pipeline_events_event_type CHECK (event_type IN (
        'MESSAGE_INGESTED',
        'CHAT_SYNCED',
        'ACCOUNT_STATE_CHANGED',
        'TDLIB_UPDATE_RECEIVED',
        'RAW_MESSAGE_PERSISTED',
        'LINKS_EXTRACTED',
        'BACKFILL_STARTED',
        'BACKFILL_COMPLETED',
        'BACKFILL_FAILED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_pipeline_events_created_at ON pipeline_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pipeline_events_type ON pipeline_events (event_type);

CREATE TABLE IF NOT EXISTS telegram_account_health (
    account_id BIGINT PRIMARY KEY REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'AUTH_REQUIRED',
    last_update_received_at TIMESTAMPTZ,
    last_message_persisted_at TIMESTAMPTZ,
    inbox_pending_count BIGINT NOT NULL DEFAULT 0,
    reconnect_count BIGINT NOT NULL DEFAULT 0,
    dropped_update_count BIGINT NOT NULL DEFAULT 0,
    duplicate_update_count BIGINT NOT NULL DEFAULT 0,
    flood_wait_until TIMESTAMPTZ,
    error_summary TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS telegram_chat_health (
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    last_update_received_at TIMESTAMPTZ,
    last_message_persisted_at TIMESTAMPTZ,
    last_backfill_at TIMESTAMPTZ,
    backfill_status TEXT,
    lag_seconds BIGINT,
    message_count_24h BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, telegram_chat_id)
);
