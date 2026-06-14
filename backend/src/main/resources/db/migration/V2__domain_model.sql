-- NeuroInfoGrinder V2: Full domain model
-- Telegram accounts, groups, messages, classifiers, rules, guides, providers, prompts, monitoring, settings

-- ─── Telegram Accounts ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telegram_accounts (
    id              BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT,
    username        VARCHAR(64),
    phone           VARCHAR(32) NOT NULL,
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    status          VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    proxy_type      VARCHAR(16),
    proxy_host      VARCHAR(256),
    proxy_port      INTEGER,
    proxy_username  VARCHAR(128),
    proxy_password_encrypted VARCHAR(256),
    last_sync_at    TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(512),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ─── Groups (Telegram chats being monitored) ──────────────────
CREATE TABLE IF NOT EXISTS groups (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_chat_id    BIGINT NOT NULL,
    title               VARCHAR(256) NOT NULL,
    username            VARCHAR(64),
    category            VARCHAR(64),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    account_id          BIGINT REFERENCES telegram_accounts(id),
    last_read_message_id BIGINT DEFAULT 0,
    last_read_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_groups_account_id ON groups(account_id);
CREATE INDEX IF NOT EXISTS idx_groups_enabled ON groups(enabled);

-- ─── Messages ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id                      BIGSERIAL PRIMARY KEY,
    telegram_message_id     BIGINT NOT NULL,
    group_id                BIGINT NOT NULL REFERENCES groups(id),
    sender_name             VARCHAR(128),
    sender_telegram_user_id BIGINT,
    is_bot                  BOOLEAN NOT NULL DEFAULT FALSE,
    text                    TEXT,
    reply_to_message_id     BIGINT,
    reply_count             INTEGER NOT NULL DEFAULT 0,
    processing_status       VARCHAR(32) NOT NULL DEFAULT 'UNPROCESSED',
    guide_id                BIGINT,
    message_date            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_messages_group_id ON messages(group_id);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(processing_status);
CREATE INDEX IF NOT EXISTS idx_messages_date ON messages(message_date);

-- ─── Classifiers ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS classifiers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(16) NOT NULL,
    provider_id     BIGINT,
    prompt_id       BIGINT,
    keywords        TEXT,
    regex_pattern   TEXT,
    version         VARCHAR(16) NOT NULL DEFAULT '1.0',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ─── Classification Rules ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS rules (
    id              BIGSERIAL PRIMARY KEY,
    rule_order      INTEGER NOT NULL,
    conditions_json TEXT NOT NULL,
    actions_json    TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ─── Chain Configuration ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS chain_config (
    id                          BIGSERIAL PRIMARY KEY,
    include_replies             BOOLEAN NOT NULL DEFAULT TRUE,
    time_window_minutes         INTEGER NOT NULL DEFAULT 5,
    min_messages_for_processing INTEGER NOT NULL DEFAULT 2,
    max_messages_per_chain      INTEGER NOT NULL DEFAULT 20,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Single-row config table
INSERT INTO chain_config (include_replies, time_window_minutes, min_messages_for_processing, max_messages_per_chain, created_at, updated_at)
VALUES (TRUE, 5, 2, 20, NOW(), NOW());

-- ─── Guides ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS guides (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(512) NOT NULL,
    content         TEXT,
    content_markdown TEXT,
    group_id        BIGINT NOT NULL REFERENCES groups(id),
    provider_id     BIGINT,
    model           VARCHAR(64),
    classifier_id   BIGINT,
    prompt_id       BIGINT,
    prompt_version  VARCHAR(16),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    duplicate_of_id BIGINT,
    duplicate_score DOUBLE PRECISION,
    confidence      DOUBLE PRECISION,
    input_tokens    INTEGER NOT NULL DEFAULT 0,
    output_tokens   INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    published_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_guides_group_id ON guides(group_id);
CREATE INDEX IF NOT EXISTS idx_guides_status ON guides(status);

-- ─── Guide Source Messages (many-to-many) ─────────────────────
CREATE TABLE IF NOT EXISTS guide_source_messages (
    id          BIGSERIAL PRIMARY KEY,
    guide_id    BIGINT NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    message_id  BIGINT NOT NULL REFERENCES messages(id),
    used_in_prompt BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_gsm_guide_id ON guide_source_messages(guide_id);

-- ─── AI Providers ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_providers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    protocol        VARCHAR(32) NOT NULL,
    endpoint_url    VARCHAR(512) NOT NULL,
    api_key_encrypted VARCHAR(512),
    model           VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    last_tested_at  TIMESTAMP WITH TIME ZONE,
    last_test_result VARCHAR(256),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ─── Prompts ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS prompts (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    version         VARCHAR(16) NOT NULL DEFAULT '1.0',
    content         TEXT NOT NULL,
    variables_json  TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ─── AI Usage Log (token monitoring) ──────────────────────────
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id              BIGSERIAL PRIMARY KEY,
    task_type       VARCHAR(64) NOT NULL,
    provider_id     BIGINT,
    model           VARCHAR(64),
    guide_id        BIGINT,
    input_tokens    INTEGER NOT NULL DEFAULT 0,
    output_tokens   INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_usage_provider ON ai_usage_log(provider_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_created ON ai_usage_log(created_at);

-- ─── Task Queue ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS task_queue (
    id              BIGSERIAL PRIMARY KEY,
    task_type       VARCHAR(64) NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    input_ref_type  VARCHAR(32),
    input_ref_id    BIGINT,
    result_ref_id   BIGINT,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    error_message   VARCHAR(1024),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_task_queue_status ON task_queue(status);

-- ─── Settings ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS settings (
    id                          BIGSERIAL PRIMARY KEY,
    publication_target_group_id BIGINT,
    publication_mode            VARCHAR(32) NOT NULL DEFAULT 'WITH_MODERATION',
    processing_mode             VARCHAR(32) NOT NULL DEFAULT 'NEW_ONLY',
    poll_interval_seconds       INTEGER NOT NULL DEFAULT 30,
    chain_include_replies       BOOLEAN NOT NULL DEFAULT TRUE,
    chain_time_window_minutes   INTEGER NOT NULL DEFAULT 5,
    chain_min_messages          INTEGER NOT NULL DEFAULT 2,
    chain_max_messages          INTEGER NOT NULL DEFAULT 20,
    filter_skip_bots            BOOLEAN NOT NULL DEFAULT TRUE,
    filter_min_message_length   INTEGER NOT NULL DEFAULT 50,
    filter_blacklist_words      TEXT,
    limit_daily_token_limit     BIGINT NOT NULL DEFAULT 50000000,
    limit_monthly_token_limit   BIGINT NOT NULL DEFAULT 500000000,
    limit_alert_threshold_pct   INTEGER NOT NULL DEFAULT 80,
    notification_telegram_chat  VARCHAR(128),
    notification_webhook_url    VARCHAR(512),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Single-row settings
INSERT INTO settings (publication_mode, processing_mode, poll_interval_seconds,
    chain_include_replies, chain_time_window_minutes, chain_min_messages, chain_max_messages,
    filter_skip_bots, filter_min_message_length, filter_blacklist_words,
    limit_daily_token_limit, limit_monthly_token_limit, limit_alert_threshold_pct,
    created_at, updated_at)
VALUES ('WITH_MODERATION', 'NEW_ONLY', 30,
    TRUE, 5, 2, 20,
    TRUE, 50, 'куплю,продам,реклама',
    50000000, 500000000, 80,
    NOW(), NOW());

-- ─── Audit Log ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   BIGINT NOT NULL,
    action      VARCHAR(64) NOT NULL,
    actor       VARCHAR(64) NOT NULL DEFAULT 'system',
    details     VARCHAR(1024),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_log(entity_type, entity_id);
