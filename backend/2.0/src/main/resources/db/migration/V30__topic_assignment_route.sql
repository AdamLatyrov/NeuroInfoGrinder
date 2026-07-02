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
    routing_policy_json,
    max_calls_per_run,
    max_cost_per_run
)
SELECT
    'TOPIC_ASSIGNMENT',
    primary_model_id,
    fallback_model_id,
    true,
    max_calls_per_day,
    max_calls_per_1000_messages,
    max_cost_per_day,
    timeout_ms,
    retry_count,
    0.0,
    768,
    routing_policy_json,
    max_calls_per_run,
    max_cost_per_run
FROM pipeline_model_routes
WHERE stage = 'KNOWLEDGE_GENERATION'
  AND NOT EXISTS (SELECT 1 FROM pipeline_model_routes WHERE stage = 'TOPIC_ASSIGNMENT')
LIMIT 1;
