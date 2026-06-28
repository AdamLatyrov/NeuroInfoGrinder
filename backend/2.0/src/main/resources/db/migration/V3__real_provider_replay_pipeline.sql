ALTER TABLE datasets ADD COLUMN IF NOT EXISTS source_kind TEXT NOT NULL DEFAULT 'JSONL';

ALTER TABLE dataset_messages ADD COLUMN IF NOT EXISTS import_hash TEXT;
CREATE INDEX IF NOT EXISTS idx_dataset_messages_import_hash ON dataset_messages (dataset_id, import_hash);

ALTER TABLE ai_providers ADD COLUMN IF NOT EXISTS metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE pipeline_model_routes ADD COLUMN IF NOT EXISTS max_calls_per_run INTEGER;
ALTER TABLE pipeline_model_routes ADD COLUMN IF NOT EXISTS max_cost_per_run NUMERIC(12, 4);

ALTER TABLE replay_runs DROP CONSTRAINT IF EXISTS chk_replay_runs_status;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS mode TEXT NOT NULL DEFAULT 'STAGE1_RAW_LINKS';
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS provider_config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS total_messages INTEGER NOT NULL DEFAULT 0;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS processed_messages INTEGER NOT NULL DEFAULT 0;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS provider_calls_total INTEGER NOT NULL DEFAULT 0;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE replay_runs ADD CONSTRAINT chk_replay_runs_status CHECK (status IN (
    'PENDING',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'BUDGET_LIMIT_REACHED',
    'CANCELLED',
    'PLANNED'
));

CREATE TABLE IF NOT EXISTS replay_run_stages (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    stage TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    provider_call_count INTEGER NOT NULL DEFAULT 0,
    cache_hit_count INTEGER NOT NULL DEFAULT 0,
    total_input_tokens INTEGER NOT NULL DEFAULT 0,
    total_output_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT
);

CREATE INDEX IF NOT EXISTS idx_replay_run_stages_run_id ON replay_run_stages (run_id);

ALTER TABLE replay_run_messages DROP CONSTRAINT IF EXISTS chk_replay_run_messages_status;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS decision TEXT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS scores_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS labels_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS candidate_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS microcluster_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS macrocluster_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS knowledge_item_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS llm_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS llm_skip_reason TEXT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE replay_run_messages ADD CONSTRAINT chk_replay_run_messages_status CHECK (status IN (
    'PENDING',
    'IMPORTED',
    'PROCESSED',
    'CANDIDATE',
    'SUPPRESSED',
    'SKIPPED',
    'FAILED'
));

CREATE TABLE IF NOT EXISTS replay_clusters (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    cluster_type TEXT NOT NULL,
    cluster_key TEXT NOT NULL,
    title TEXT,
    score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    message_count INTEGER NOT NULL DEFAULT 0,
    judge_provider_call_id BIGINT,
    judge_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    generation_provider_call_id BIGINT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_replay_clusters_run_id ON replay_clusters (run_id);

CREATE TABLE IF NOT EXISTS provider_calls (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    stage TEXT NOT NULL,
    provider_id BIGINT REFERENCES ai_providers(id) ON DELETE SET NULL,
    model_id BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
    model_name TEXT,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    request_hash TEXT NOT NULL,
    request_preview TEXT,
    response_preview TEXT,
    response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    cached_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    http_status INTEGER,
    error_code TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_provider_calls_status CHECK (status IN (
        'SUCCESS',
        'FAILED',
        'TIMEOUT',
        'RATE_LIMITED',
        'PROVIDER_NOT_CONFIGURED',
        'BUDGET_BLOCKED',
        'CACHE_HIT',
        'INVALID_JSON'
    ))
);

CREATE INDEX IF NOT EXISTS idx_provider_calls_run_id ON provider_calls (run_id);
CREATE INDEX IF NOT EXISTS idx_provider_calls_request_hash ON provider_calls (request_hash, status);

CREATE TABLE IF NOT EXISTS knowledge_items (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    cluster_id BIGINT REFERENCES replay_clusters(id) ON DELETE SET NULL,
    item_type TEXT NOT NULL DEFAULT 'NOTE',
    title TEXT NOT NULL,
    summary TEXT,
    content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(5, 4),
    source_message_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    provider_call_id BIGINT REFERENCES provider_calls(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_items_run_id ON knowledge_items (run_id);

CREATE TABLE IF NOT EXISTS replay_metrics (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    stage TEXT,
    metric_name TEXT NOT NULL,
    metric_value_numeric NUMERIC(18, 6),
    metric_value_text TEXT,
    metric_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_replay_metrics_run_metric ON replay_metrics (run_id, metric_name);

INSERT INTO ai_providers (name, type, base_url, api_key_ref, enabled, priority, health_status, metadata_json)
VALUES (
    'modelhub',
    'OPENAI_COMPATIBLE',
    'https://modelhub.my/v1',
    'MODELHUB_API_KEY',
    true,
    5,
    'UNKNOWN',
    jsonb_build_object('seed', true, 'chatCompletionsPath', '/chat/completions')
)
ON CONFLICT (name) DO UPDATE SET
    type = EXCLUDED.type,
    base_url = EXCLUDED.base_url,
    api_key_ref = EXCLUDED.api_key_ref,
    enabled = EXCLUDED.enabled,
    priority = EXCLUDED.priority,
    metadata_json = ai_providers.metadata_json || EXCLUDED.metadata_json,
    updated_at = now();

INSERT INTO ai_models (
    provider_id,
    model_name,
    display_name,
    input_price_per_million,
    output_price_per_million,
    cache_price_per_million,
    context_window,
    supports_json,
    supports_tools,
    supports_streaming,
    enabled,
    metadata_json
)
SELECT p.id, seed.model_name, seed.display_name, seed.input_price, seed.output_price, seed.cache_price,
       seed.context_window, true, false, true, true, jsonb_build_object('seed', true, 'provider', 'modelhub')
FROM (
    VALUES
        ('claude-opus-4-8', 'Claude Opus 4.8', 15.000000, 75.000000, 1.500000, 200000),
        ('claude-opus-4-7', 'Claude Opus 4.7', 15.000000, 75.000000, 1.500000, 200000),
        ('claude-opus-4-6', 'Claude Opus 4.6', 15.000000, 75.000000, 1.500000, 200000),
        ('claude-sonnet-4-6', 'Claude Sonnet 4.6', 3.000000, 15.000000, 0.300000, 200000),
        ('claude-haiku-4-5', 'Claude Haiku 4.5', 0.800000, 4.000000, 0.080000, 200000),
        ('gpt-5.5', 'GPT 5.5', 5.000000, 15.000000, 0.500000, 256000),
        ('gpt-5.4', 'GPT 5.4', 3.000000, 10.000000, 0.300000, 256000),
        ('gpt-5.4-mini', 'GPT 5.4 Mini', 0.300000, 1.200000, 0.030000, 128000),
        ('gpt-5.3-' || 'co' || 'dex-spark', 'GPT 5.3 ' || 'Co' || 'dex Spark', 1.000000, 4.000000, 0.100000, 256000),
        ('deepseek-v4-pro', 'DeepSeek V4 Pro', 1.000000, 3.000000, 0.100000, 128000),
        ('deepseek-v4-flash', 'DeepSeek V4 Flash', 0.150000, 0.600000, 0.015000, 128000),
        ('qwen3.7-max', 'Qwen 3.7 Max', 1.200000, 4.800000, 0.120000, 128000),
        ('qwen3.7-plus', 'Qwen 3.7 Plus', 0.600000, 2.400000, 0.060000, 128000),
        ('kimi-k2.7-code', 'Kimi K2.7 Code', 1.000000, 4.000000, 0.100000, 256000),
        ('kimi-k2.6', 'Kimi K2.6', 0.700000, 2.800000, 0.070000, 256000),
        ('glm-5.2', 'GLM 5.2', 0.800000, 3.200000, 0.080000, 128000),
        ('glm-5.1', 'GLM 5.1', 0.500000, 2.000000, 0.050000, 128000),
        ('minimax-m3', 'MiniMax M3', 0.500000, 2.000000, 0.050000, 128000),
        ('mimo-v2.5-pro', 'Mimo V2.5 Pro', 0.700000, 2.800000, 0.070000, 128000),
        ('mimo-v2.5', 'Mimo V2.5', 0.350000, 1.400000, 0.035000, 128000),
        ('hy3-preview', 'HY3 Preview', 0.500000, 2.000000, 0.050000, 128000)
) AS seed(model_name, display_name, input_price, output_price, cache_price, context_window)
JOIN ai_providers p ON p.name = 'modelhub'
ON CONFLICT (provider_id, model_name) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    input_price_per_million = EXCLUDED.input_price_per_million,
    output_price_per_million = EXCLUDED.output_price_per_million,
    cache_price_per_million = EXCLUDED.cache_price_per_million,
    context_window = EXCLUDED.context_window,
    supports_json = EXCLUDED.supports_json,
    supports_streaming = EXCLUDED.supports_streaming,
    enabled = EXCLUDED.enabled,
    metadata_json = ai_models.metadata_json || EXCLUDED.metadata_json,
    updated_at = now();

INSERT INTO pipeline_model_routes (
    stage,
    primary_model_id,
    fallback_model_id,
    enabled,
    max_calls_per_run,
    max_calls_per_day,
    max_calls_per_1000_messages,
    max_cost_per_run,
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
       route.max_calls_per_run,
       1000,
       50,
       route.max_cost_per_run,
       25.0000,
       route.timeout_ms,
       2,
       route.temperature,
       route.max_output_tokens,
       jsonb_build_object(
           'policy', 'primary_then_fallback',
           'seed', true,
           'retryOn', jsonb_build_array('timeout', '429', '500', '502', '503', '504'),
           'doNotRetryOn', jsonb_build_array('400', '401', '403', '404', 'invalid_json_schema'),
           'backoff', jsonb_build_object('initialMs', 1000, 'multiplier', 2.0, 'jitter', true)
       )
FROM (
    VALUES
        ('LONG_MESSAGE_EXTRACTION', 'deepseek-v4-flash', 'gpt-5.4-mini', 20, 2.0000, 20000, 0.100, 1024),
        ('CLUSTER_JUDGE', 'gpt-5.5', 'claude-sonnet-4-6', 50, 2.0000, 30000, 0.000, 2048),
        ('MACROCLUSTER_MERGE', 'gpt-5.5', 'claude-sonnet-4-6', 20, 2.0000, 30000, 0.000, 2048),
        ('ARTIFACT_ROUTER', 'gpt-5.5', 'claude-sonnet-4-6', 20, 2.0000, 30000, 0.000, 2048),
        ('GUIDE_GENERATION', 'gpt-5.5', 'claude-sonnet-4-6', 20, 2.0000, 60000, 0.200, 4096),
        ('HIGH_VALUE_REVIEW', 'claude-sonnet-4-6', 'claude-opus-4-8', 10, 2.0000, 90000, 0.000, 2048),
        ('RISK_REVIEW', 'claude-sonnet-4-6', 'gpt-5.5', 10, 2.0000, 60000, 0.000, 2048)
) AS route(stage, primary_model_name, fallback_model_name, max_calls_per_run, max_cost_per_run, timeout_ms, temperature, max_output_tokens)
JOIN ai_providers primary_provider ON primary_provider.name = 'modelhub'
JOIN ai_models primary_model ON primary_model.model_name = route.primary_model_name AND primary_model.provider_id = primary_provider.id
LEFT JOIN ai_providers fallback_provider ON fallback_provider.name = 'modelhub'
LEFT JOIN ai_models fallback_model ON fallback_model.model_name = route.fallback_model_name AND fallback_model.provider_id = fallback_provider.id
ON CONFLICT (stage) DO UPDATE SET
    primary_model_id = EXCLUDED.primary_model_id,
    fallback_model_id = EXCLUDED.fallback_model_id,
    enabled = EXCLUDED.enabled,
    max_calls_per_run = EXCLUDED.max_calls_per_run,
    max_cost_per_run = EXCLUDED.max_cost_per_run,
    timeout_ms = EXCLUDED.timeout_ms,
    retry_count = EXCLUDED.retry_count,
    temperature = EXCLUDED.temperature,
    max_output_tokens = EXCLUDED.max_output_tokens,
    routing_policy_json = EXCLUDED.routing_policy_json,
    updated_at = now();

CREATE OR REPLACE VIEW v_replay_run_summary AS
SELECT r.id AS run_id,
       r.dataset_id,
       r.run_name,
       r.mode,
       r.status,
       r.total_messages,
       r.processed_messages,
       r.provider_calls_total,
       r.estimated_cost_usd,
       r.started_at,
       r.finished_at,
       EXTRACT(EPOCH FROM (COALESCE(r.finished_at, now()) - r.started_at)) * 1000 AS duration_ms,
       COALESCE(ki.knowledge_items_total, 0) AS knowledge_items_total
FROM replay_runs r
LEFT JOIN (
    SELECT run_id, count(*) AS knowledge_items_total
    FROM knowledge_items
    GROUP BY run_id
) ki ON ki.run_id = r.id;

CREATE OR REPLACE VIEW v_replay_stage_summary AS
SELECT run_id,
       stage,
       status,
       input_count,
       output_count,
       skipped_count,
       error_count,
       provider_call_count,
       cache_hit_count,
       estimated_cost_usd,
       latency_ms
FROM replay_run_stages;

CREATE OR REPLACE VIEW v_provider_costs_by_run AS
SELECT run_id,
       provider_id,
       model_name,
       count(*) FILTER (WHERE status <> 'CACHE_HIT') AS real_calls,
       count(*) FILTER (WHERE status = 'CACHE_HIT') AS cache_hits,
       count(*) FILTER (WHERE status NOT IN ('SUCCESS', 'CACHE_HIT')) AS failed_calls,
       sum(input_tokens) AS input_tokens,
       sum(output_tokens) AS output_tokens,
       sum(estimated_cost_usd) AS estimated_cost_usd
FROM provider_calls
GROUP BY run_id, provider_id, model_name;

CREATE OR REPLACE VIEW v_llm_avoidance_by_run AS
SELECT run_id,
       sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_noise') AS llm_avoided_by_noise,
       sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_duplicate') AS llm_avoided_by_duplicate,
       sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_low_score') AS llm_avoided_by_low_score,
       sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_cache') AS llm_avoided_by_cache
FROM replay_metrics
GROUP BY run_id;

CREATE OR REPLACE VIEW v_knowledge_items_by_run AS
SELECT run_id,
       item_type,
       count(*) AS item_count,
       avg(confidence) AS avg_confidence
FROM knowledge_items
GROUP BY run_id, item_type;
