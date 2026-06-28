ALTER TABLE replay_runs DROP CONSTRAINT IF EXISTS chk_replay_runs_status;
ALTER TABLE replay_runs ADD COLUMN IF NOT EXISTS model_config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE replay_runs ADD CONSTRAINT chk_replay_runs_status CHECK (status IN (
    'CREATED',
    'PENDING',
    'PLANNED',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'MODEL_NOT_CONFIGURED',
    'MODEL_WORKER_DOWN',
    'PROVIDER_NOT_CONFIGURED',
    'BUDGET_LIMIT_REACHED'
));

ALTER TABLE replay_run_stages ADD COLUMN IF NOT EXISTS local_model_call_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS rule_decision TEXT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS final_decision TEXT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS rule_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS bert_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS embedding_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS dedupe_group_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS topic_id BIGINT;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS labeling_item_id BIGINT;

CREATE TABLE IF NOT EXISTS local_model_workers (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    base_url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    health_status TEXT NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMPTZ,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS local_model_calls (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    stage TEXT NOT NULL,
    worker_id BIGINT REFERENCES local_model_workers(id) ON DELETE SET NULL,
    operation TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    latency_ms BIGINT,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_local_model_calls_status CHECK (status IN (
        'SUCCESS',
        'FAILED',
        'TIMEOUT',
        'MODEL_WORKER_DOWN',
        'MODEL_NOT_CONFIGURED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_local_model_calls_run_id ON local_model_calls (run_id);

CREATE TABLE IF NOT EXISTS message_intelligence (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    raw_message_id BIGINT,
    raw_text TEXT,
    normalized_text TEXT,
    language TEXT,
    text_len INTEGER NOT NULL DEFAULT 0,
    token_estimate INTEGER NOT NULL DEFAULT 0,
    structural_features_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    links_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    entities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    code_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    errors_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    prices_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    question_answer_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    weak_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    rule_scores_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    hard_signal BOOLEAN NOT NULL DEFAULT FALSE,
    rule_decision TEXT NOT NULL DEFAULT 'SUPPRESS',
    decision_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_intelligence_run_message UNIQUE (run_id, dataset_message_id)
);

CREATE INDEX IF NOT EXISTS idx_message_intelligence_run_id ON message_intelligence (run_id);

CREATE TABLE IF NOT EXISTS classifier_models (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    model_path TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS classifier_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    model_id BIGINT REFERENCES classifier_models(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'CREATED',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT
);

CREATE TABLE IF NOT EXISTS message_classifications (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    model_id BIGINT REFERENCES classifier_models(id) ON DELETE SET NULL,
    model_name TEXT NOT NULL,
    top_label TEXT NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_classifications_run_message UNIQUE (run_id, dataset_message_id)
);

CREATE TABLE IF NOT EXISTS embedding_models (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    model_path TEXT,
    dimension INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS message_embeddings (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    raw_message_id BIGINT,
    model_id BIGINT REFERENCES embedding_models(id) ON DELETE SET NULL,
    embedding_kind TEXT NOT NULL,
    embedding_vector JSONB NOT NULL DEFAULT '[]'::jsonb,
    embedding_text TEXT,
    embedding_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_embeddings_run_message_kind UNIQUE (run_id, dataset_message_id, embedding_kind)
);

CREATE TABLE IF NOT EXISTS dedupe_groups (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dedupe_type TEXT NOT NULL,
    canonical_dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dedupe_group_members (
    group_id BIGINT NOT NULL REFERENCES dedupe_groups(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    similarity NUMERIC(6, 5) NOT NULL DEFAULT 1,
    reason_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (group_id, dataset_message_id)
);

CREATE TABLE IF NOT EXISTS semantic_neighbors (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    source_dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    target_dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    similarity NUMERIC(6, 5) NOT NULL,
    reason_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_semantic_neighbors_run_id ON semantic_neighbors (run_id);

CREATE TABLE IF NOT EXISTS microclusters (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    cluster_key TEXT NOT NULL,
    title TEXT NOT NULL,
    cluster_type TEXT NOT NULL,
    score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    centroid_embedding_id BIGINT REFERENCES message_embeddings(id) ON DELETE SET NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS microcluster_members (
    microcluster_id BIGINT NOT NULL REFERENCES microclusters(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MEMBER',
    similarity NUMERIC(6, 5),
    edge_weight NUMERIC(6, 5),
    reason_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (microcluster_id, dataset_message_id)
);

CREATE TABLE IF NOT EXISTS macroclusters (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    macrocluster_type TEXT NOT NULL,
    score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    message_count INTEGER NOT NULL DEFAULT 0,
    microcluster_count INTEGER NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS macrocluster_members (
    macrocluster_id BIGINT NOT NULL REFERENCES macroclusters(id) ON DELETE CASCADE,
    microcluster_id BIGINT NOT NULL REFERENCES microclusters(id) ON DELETE CASCADE,
    similarity NUMERIC(6, 5),
    reason_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (macrocluster_id, microcluster_id)
);

CREATE TABLE IF NOT EXISTS discovered_topics (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    topic_key TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    message_count INTEGER NOT NULL DEFAULT 0,
    cluster_count INTEGER NOT NULL DEFAULT 0,
    top_entities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    top_domains_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    top_terms_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    method TEXT NOT NULL,
    score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cluster_scores (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    cluster_type TEXT NOT NULL,
    cluster_id BIGINT NOT NULL,
    usefulness_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    pain_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    wtp_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    publishability_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    novelty_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    trend_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    duplicate_penalty NUMERIC(5, 4) NOT NULL DEFAULT 0,
    spam_penalty NUMERIC(5, 4) NOT NULL DEFAULT 0,
    unsafe_penalty NUMERIC(5, 4) NOT NULL DEFAULT 0,
    final_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    score_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS source_cluster_type TEXT;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS source_cluster_id BIGINT;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS artifact_type TEXT NOT NULL DEFAULT 'NOTE';
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS vertical TEXT;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS body_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS usefulness_score NUMERIC(5, 4);
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS pain_score NUMERIC(5, 4);
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS wtp_score NUMERIC(5, 4);
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS publishability_score NUMERIC(5, 4);
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS knowledge_value_score NUMERIC(5, 4);
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'DRAFT';

CREATE TABLE IF NOT EXISTS knowledge_item_sources (
    id BIGSERIAL PRIMARY KEY,
    knowledge_item_id BIGINT NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    source_role TEXT NOT NULL DEFAULT 'EVIDENCE',
    quote TEXT,
    confidence NUMERIC(5, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS labeling_items (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE CASCADE,
    microcluster_id BIGINT REFERENCES microclusters(id) ON DELETE SET NULL,
    macrocluster_id BIGINT REFERENCES macroclusters(id) ON DELETE SET NULL,
    knowledge_item_id BIGINT REFERENCES knowledge_items(id) ON DELETE SET NULL,
    item_type TEXT NOT NULL,
    text_snapshot TEXT,
    context_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    suggested_label TEXT,
    suggested_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    suggested_artifact_type TEXT,
    suggested_decision TEXT,
    confidence NUMERIC(5, 4),
    priority INTEGER NOT NULL DEFAULT 50,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS labeling_events (
    id BIGSERIAL PRIMARY KEY,
    labeling_item_id BIGINT NOT NULL REFERENCES labeling_items(id) ON DELETE CASCADE,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    user_id TEXT,
    event_type TEXT NOT NULL,
    old_value_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    new_value_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS training_examples (
    id BIGSERIAL PRIMARY KEY,
    source TEXT NOT NULL,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    text TEXT NOT NULL,
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    label TEXT,
    artifact_type TEXT,
    decision TEXT,
    confidence NUMERIC(5, 4),
    split TEXT NOT NULL DEFAULT 'UNASSIGNED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO local_model_workers (name, base_url, enabled, metadata_json)
VALUES ('local-fastapi-worker', 'http://127.0.0.1:8095', true, jsonb_build_object('classifier', 'BOOTSTRAP_BERT_CLASSIFIER', 'embeddings', 'BAAI/bge-m3'))
ON CONFLICT (name) DO UPDATE SET
    base_url = EXCLUDED.base_url,
    enabled = EXCLUDED.enabled,
    metadata_json = local_model_workers.metadata_json || EXCLUDED.metadata_json,
    updated_at = now();

INSERT INTO classifier_models (name, type, model_path, enabled, labels_json, metadata_json)
VALUES (
    'BOOTSTRAP_BERT_CLASSIFIER',
    'BERT_COMPATIBLE',
    'model-worker/bootstrap',
    true,
    '[
      "NOISE_OR_CHAT",
      "HOW_TO_GUIDE",
      "TROUBLESHOOTING_FIX",
      "TOOL_OR_MODEL_RELEASE",
      "PRICING_OR_ACCESS_SIGNAL",
      "COMPARISON_OR_BENCHMARK",
      "PROMPT_OR_AGENT_PATTERN",
      "API_OR_CONFIG_SNIPPET",
      "WORKFLOW_AUTOMATION",
      "SECURITY_OR_RISK_WARNING",
      "MARKET_OR_ECOSYSTEM_SIGNAL",
      "RESOURCE_LINK_COLLECTION",
      "QUESTION_WITH_VALUABLE_ANSWER",
      "ARCHITECTURE_DECISION",
      "ERROR_LOG_WITH_FIX",
      "RAW_NEWS_LOW_ACTIONABILITY",
      "PROMO_WITH_USEFUL_DETAILS",
      "DUPLICATE_OR_NEAR_DUPLICATE",
      "UNSUPPORTED_HYPE",
      "UNSAFE_OR_POLICY_RISK"
    ]'::jsonb,
    jsonb_build_object('seed', true, 'note', 'Bootstrap classifier until a fine-tuned BERT model is available')
)
ON CONFLICT (name) DO UPDATE SET labels_json = EXCLUDED.labels_json, metadata_json = EXCLUDED.metadata_json, updated_at = now();

INSERT INTO embedding_models (name, type, model_path, dimension, enabled, metadata_json)
VALUES ('BAAI/bge-m3', 'BGE_M3', 'BAAI/bge-m3', 1024, true, jsonb_build_object('seed', true))
ON CONFLICT (name) DO UPDATE SET dimension = EXCLUDED.dimension, enabled = EXCLUDED.enabled, metadata_json = EXCLUDED.metadata_json, updated_at = now();

INSERT INTO pipeline_model_routes (
    stage,
    primary_model_id,
    fallback_model_id,
    enabled,
    max_calls_per_run,
    max_calls_per_day,
    max_cost_per_run,
    timeout_ms,
    retry_count,
    temperature,
    max_output_tokens,
    routing_policy_json
)
SELECT route.stage,
       primary_model.id,
       fallback_model.id,
       route.enabled,
       route.max_calls_per_run,
       1000,
       route.max_cost_per_run,
       route.timeout_ms,
       2,
       route.temperature,
       route.max_output_tokens,
       jsonb_build_object(
           'policy', 'primary_then_fallback',
           'manualOnly', route.manual_only,
           'retryOn', jsonb_build_array('timeout', '429', '500', '502', '503', '504'),
           'doNotRetryOn', jsonb_build_array('400', '401', '403', 'invalid_json_schema')
       )
FROM (
    VALUES
        ('LLM_CLUSTER_JUDGE_AND_ROUTING', 'gpt-5.5', 'claude-sonnet-4-6', true, false, 50, 2.0000, 30000, 0.000, 2048),
        ('KNOWLEDGE_GENERATION', 'gpt-5.5', 'claude-sonnet-4-6', true, false, 50, 2.0000, 60000, 0.200, 4096),
        ('HIGH_VALUE_MANUAL_REVIEW', 'claude-sonnet-4-6', 'claude-opus-4-8', false, true, 10, 2.0000, 90000, 0.000, 2048),
        ('RISK_MANUAL_REVIEW', 'claude-sonnet-4-6', 'gpt-5.5', false, true, 10, 2.0000, 60000, 0.000, 2048)
) AS route(stage, primary_model_name, fallback_model_name, enabled, manual_only, max_calls_per_run, max_cost_per_run, timeout_ms, temperature, max_output_tokens)
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

CREATE OR REPLACE VIEW v_classifier_summary_by_run AS
SELECT run_id,
       model_name,
       count(*) AS classified_count,
       count(*) FILTER (WHERE top_label = 'NOISE_OR_CHAT') AS noise_count,
       avg(confidence) AS avg_confidence
FROM message_classifications
GROUP BY run_id, model_name;

CREATE OR REPLACE VIEW v_embedding_summary_by_run AS
SELECT run_id,
       embedding_kind,
       count(*) AS embedding_count
FROM message_embeddings
GROUP BY run_id, embedding_kind;

CREATE OR REPLACE VIEW v_cluster_summary_by_run AS
SELECT run_id, 'MICRO' AS cluster_level, count(*) AS cluster_count, sum(message_count) AS message_count, avg(score) AS avg_score
FROM microclusters
GROUP BY run_id
UNION ALL
SELECT run_id, 'MACRO' AS cluster_level, count(*) AS cluster_count, sum(message_count) AS message_count, avg(score) AS avg_score
FROM macroclusters
GROUP BY run_id;

CREATE OR REPLACE VIEW v_topic_summary_by_run AS
SELECT run_id, count(*) AS topic_count, avg(score) AS avg_score
FROM discovered_topics
GROUP BY run_id;

CREATE OR REPLACE VIEW v_labeling_summary_by_run AS
SELECT run_id, status, count(*) AS item_count
FROM labeling_items
GROUP BY run_id, status;

CREATE OR REPLACE VIEW v_training_examples_summary AS
SELECT source, split, label, count(*) AS example_count
FROM training_examples
GROUP BY source, split, label;
