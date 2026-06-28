ALTER TABLE replay_run_messages DROP CONSTRAINT IF EXISTS chk_replay_run_messages_status;
ALTER TABLE replay_run_messages ADD CONSTRAINT chk_replay_run_messages_status CHECK (status IN (
    'PENDING',
    'IMPORTED',
    'PROCESSED',
    'CANDIDATE',
    'SUPPRESSED',
    'SKIPPED',
    'FAILED'
));

CREATE UNIQUE INDEX IF NOT EXISTS uq_replay_run_messages_run_dataset_message
    ON replay_run_messages (run_id, dataset_message_id)
    WHERE dataset_message_id IS NOT NULL;

DROP VIEW IF EXISTS v_replay_run_summary;
DROP VIEW IF EXISTS v_replay_stage_summary;
DROP VIEW IF EXISTS v_llm_avoidance_by_run;

CREATE VIEW v_replay_run_summary AS
SELECT r.id AS run_id,
       r.dataset_id,
       r.run_name,
       r.mode,
       r.pipeline_version,
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

CREATE VIEW v_replay_stage_summary AS
SELECT run_id,
       stage,
       status,
       input_count,
       output_count,
       skipped_count,
       error_count,
       provider_call_count,
       local_model_call_count,
       cache_hit_count,
       latency_ms,
       metrics_json,
       error
FROM replay_run_stages;

CREATE VIEW v_llm_avoidance_by_run AS
SELECT run_id,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_rules'), 0) AS llm_avoided_by_rules,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_bert'), 0) AS llm_avoided_by_bert,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_noise'), 0) AS llm_avoided_by_noise,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_duplicate'), 0) AS llm_avoided_by_duplicate,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_low_score'), 0) AS llm_avoided_by_low_score,
       COALESCE(sum(metric_value_numeric) FILTER (WHERE metric_name = 'llm_avoided_by_cache'), 0) AS llm_avoided_by_cache
FROM replay_metrics
GROUP BY run_id;
