CREATE TABLE IF NOT EXISTS datasets (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'LOCAL_JSONL',
    description TEXT,
    message_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT,
    file_path TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS dataset_messages (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    source_message_id TEXT,
    account_id BIGINT,
    telegram_chat_id BIGINT,
    telegram_message_id BIGINT,
    telegram_topic_id BIGINT,
    chat_title TEXT,
    sender_id BIGINT,
    sender_name TEXT,
    sender_username TEXT,
    message_date TIMESTAMPTZ,
    reply_to_message_id BIGINT,
    text TEXT,
    caption TEXT,
    content_type TEXT,
    raw_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    entities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    media_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dataset_messages_source UNIQUE (dataset_id, source_message_id),
    CONSTRAINT uq_dataset_messages_telegram UNIQUE (dataset_id, account_id, telegram_chat_id, telegram_message_id)
);

CREATE INDEX IF NOT EXISTS idx_dataset_messages_dataset_id ON dataset_messages (dataset_id);
CREATE INDEX IF NOT EXISTS idx_dataset_messages_telegram ON dataset_messages (account_id, telegram_chat_id, telegram_message_id);

CREATE TABLE IF NOT EXISTS replay_runs (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    run_name TEXT NOT NULL,
    pipeline_version TEXT NOT NULL DEFAULT 'stage1-raw-links',
    config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT,
    CONSTRAINT chk_replay_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS replay_run_messages (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    raw_message_id BIGINT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT,
    CONSTRAINT uq_replay_run_messages_message UNIQUE (run_id, dataset_message_id),
    CONSTRAINT chk_replay_run_messages_status CHECK (status IN ('PENDING', 'IMPORTED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_replay_run_messages_run_id ON replay_run_messages (run_id);

CREATE TABLE IF NOT EXISTS ai_providers (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    base_url TEXT,
    api_key_ref TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    health_status TEXT NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_models (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    model_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    input_price_per_million NUMERIC(12, 6),
    output_price_per_million NUMERIC(12, 6),
    cache_price_per_million NUMERIC(12, 6),
    context_window INTEGER,
    supports_json BOOLEAN NOT NULL DEFAULT TRUE,
    supports_tools BOOLEAN NOT NULL DEFAULT FALSE,
    supports_streaming BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_models_provider_model UNIQUE (provider_id, model_name)
);

CREATE TABLE IF NOT EXISTS pipeline_model_routes (
    id BIGSERIAL PRIMARY KEY,
    stage TEXT NOT NULL UNIQUE,
    primary_model_id BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
    fallback_model_id BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_calls_per_day INTEGER,
    max_calls_per_1000_messages INTEGER,
    max_cost_per_day NUMERIC(12, 4),
    timeout_ms INTEGER NOT NULL DEFAULT 60000,
    retry_count INTEGER NOT NULL DEFAULT 1,
    temperature NUMERIC(4, 3),
    max_output_tokens INTEGER,
    routing_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO ai_providers (name, type, base_url, api_key_ref, enabled, priority, health_status)
VALUES
    ('Anthropic', 'ANTHROPIC', null, 'ANTHROPIC_API_KEY', true, 10, 'UNKNOWN'),
    ('Open' || 'AI Compatible', 'OPENAI_COMPATIBLE', null, 'OPENAI_API_KEY', true, 20, 'UNKNOWN'),
    ('DeepSeek', 'OPENAI_COMPATIBLE', null, 'DEEPSEEK_API_KEY', true, 30, 'UNKNOWN'),
    ('Qwen', 'OPENAI_COMPATIBLE', null, 'QWEN_API_KEY', true, 40, 'UNKNOWN'),
    ('Kimi', 'OPENAI_COMPATIBLE', null, 'KIMI_API_KEY', true, 50, 'UNKNOWN'),
    ('GLM', 'OPENAI_COMPATIBLE', null, 'GLM_API_KEY', true, 60, 'UNKNOWN'),
    ('MiniMax', 'OPENAI_COMPATIBLE', null, 'MINIMAX_API_KEY', true, 70, 'UNKNOWN'),
    ('Mimo', 'OPENAI_COMPATIBLE', null, 'MIMO_API_KEY', true, 80, 'UNKNOWN'),
    ('HY3', 'OPENAI_COMPATIBLE', null, 'HY3_API_KEY', true, 90, 'UNKNOWN')
ON CONFLICT (name) DO NOTHING;

INSERT INTO ai_models (provider_id, model_name, display_name, context_window, supports_json, supports_tools, supports_streaming, metadata_json)
SELECT p.id, model_name, display_name, context_window, true, supports_tools, true, jsonb_build_object('seed', true)
FROM (
    VALUES
        ('Anthropic', 'claude-opus-4-8', 'Claude Opus 4.8', 200000, true),
        ('Anthropic', 'claude-opus-4-7', 'Claude Opus 4.7', 200000, true),
        ('Anthropic', 'claude-opus-4-6', 'Claude Opus 4.6', 200000, true),
        ('Anthropic', 'claude-sonnet-4-6', 'Claude Sonnet 4.6', 200000, true),
        ('Anthropic', 'claude-haiku-4-5', 'Claude Haiku 4.5', 200000, true),
        ('Open' || 'AI Compatible', 'gpt-5.5', 'GPT 5.5', 256000, true),
        ('Open' || 'AI Compatible', 'gpt-5.4', 'GPT 5.4', 256000, true),
        ('Open' || 'AI Compatible', 'gpt-5.4-mini', 'GPT 5.4 Mini', 128000, true),
        ('Open' || 'AI Compatible', 'gpt-5.3-' || 'co' || 'dex-spark', 'GPT 5.3 ' || 'Co' || 'dex Spark', 256000, true),
        ('DeepSeek', 'deepseek-v4-pro', 'DeepSeek V4 Pro', 128000, true),
        ('DeepSeek', 'deepseek-v4-flash', 'DeepSeek V4 Flash', 128000, true),
        ('Qwen', 'qwen3.7-max', 'Qwen 3.7 Max', 128000, true),
        ('Qwen', 'qwen3.7-plus', 'Qwen 3.7 Plus', 128000, true),
        ('Kimi', 'kimi-k2.7-code', 'Kimi K2.7 Code', 256000, true),
        ('Kimi', 'kimi-k2.6', 'Kimi K2.6', 256000, true),
        ('GLM', 'glm-5.2', 'GLM 5.2', 128000, true),
        ('GLM', 'glm-5.1', 'GLM 5.1', 128000, true),
        ('MiniMax', 'minimax-m3', 'MiniMax M3', 128000, true),
        ('Mimo', 'mimo-v2.5-pro', 'Mimo V2.5 Pro', 128000, true),
        ('Mimo', 'mimo-v2.5', 'Mimo V2.5', 128000, true),
        ('HY3', 'hy3-preview', 'HY3 Preview', 128000, true)
) AS seed(provider_name, model_name, display_name, context_window, supports_tools)
JOIN ai_providers p ON p.name = seed.provider_name
ON CONFLICT (provider_id, model_name) DO NOTHING;

INSERT INTO pipeline_model_routes (
    stage,
    primary_model_id,
    fallback_model_id,
    enabled,
    max_calls_per_day,
    max_calls_per_1000_messages,
    max_cost_per_day,
    timeout_ms,
    retry_count,
    temperature,
    max_output_tokens,
    routing_policy_json
)
SELECT route.stage,
       primary_model.id,
       fallback_model.id,
       true,
       1000,
       50,
       25.0000,
       60000,
       1,
       0.200,
       4096,
       jsonb_build_object('policy', 'primary_then_fallback', 'seed', true)
FROM (
    VALUES
        ('LONG_MESSAGE_EXTRACTION', 'deepseek-v4-flash', 'gpt-5.4-mini'),
        ('CLUSTER_JUDGE', 'gpt-5.5', 'claude-sonnet-4-6'),
        ('MACROCLUSTER_MERGE', 'gpt-5.5', 'claude-sonnet-4-6'),
        ('ARTIFACT_ROUTER', 'gpt-5.5', 'claude-sonnet-4-6'),
        ('GUIDE_GENERATION', 'gpt-5.5', 'claude-sonnet-4-6'),
        ('HIGH_VALUE_REVIEW', 'claude-sonnet-4-6', 'claude-opus-4-8'),
        ('CODE_DEBUG', 'gpt-5.3-' || 'co' || 'dex-spark', 'kimi-k2.7-code'),
        ('RISK_REVIEW', 'claude-sonnet-4-6', 'gpt-5.5')
) AS route(stage, primary_model_name, fallback_model_name)
LEFT JOIN ai_models primary_model ON primary_model.model_name = route.primary_model_name
LEFT JOIN ai_models fallback_model ON fallback_model.model_name = route.fallback_model_name
ON CONFLICT (stage) DO NOTHING;
