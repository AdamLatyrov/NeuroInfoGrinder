COPY (
WITH mini_runs AS (
  SELECT rr.*
  FROM replay_runs rr
  JOIN datasets d ON d.id = rr.dataset_id
  WHERE d.source = 'RAW_MESSAGES_SNAPSHOT_MINI'
    AND d.metadata_json->>'parentDatasetId' = '15764'
    AND rr.run_name LIKE 'FULL_PIPELINE_20K_UNLIMITED_2026_06_30_MINI_%'
), material_rows AS (
  SELECT
    jsonb_build_object(
      'id', ki.id,
      'runId', ki.run_id,
      'datasetId', rr.dataset_id,
      'runName', rr.run_name,
      'status', ki.status,
      'artifactType', ki.artifact_type,
      'sourceClusterType', ki.source_cluster_type,
      'sourceClusterId', ki.source_cluster_id,
      'vertical', ki.vertical,
      'title', ki.title,
      'summary', ki.summary,
      'body', ki.body_json,
      'knowledgeValueScore', ki.knowledge_value_score,
      'createdAt', ki.created_at,
      'deletedAt', ki.deleted_at,
      'topics', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'id', kt.id,
          'slug', kt.slug,
          'name', kt.name,
          'confidence', kit.confidence
        ) ORDER BY kt.slug)
        FROM knowledge_item_topics kit
        JOIN knowledge_topics kt ON kt.id = kit.topic_id
        WHERE kit.knowledge_item_id = ki.id
      ), '[]'::jsonb),
      'sourceCount', (
        SELECT count(*)
        FROM knowledge_item_sources kis
        WHERE kis.knowledge_item_id = ki.id
      ),
      'sources', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'sourceId', kis.id,
          'datasetMessageId', dm.id,
          'sourceRole', kis.source_role,
          'quote', kis.quote,
          'confidence', kis.confidence,
          'accountId', dm.account_id,
          'telegramChatId', dm.telegram_chat_id,
          'telegramMessageId', dm.telegram_message_id,
          'telegramTopicId', dm.telegram_topic_id,
          'chatTitle', dm.chat_title,
          'senderId', dm.sender_id,
          'senderName', dm.sender_name,
          'senderUsername', dm.sender_username,
          'messageDate', dm.message_date,
          'contentType', dm.content_type,
          'text', dm.text,
          'caption', dm.caption,
          'rawJson', dm.raw_json,
          'mediaJson', dm.media_json
        ) ORDER BY dm.message_date NULLS LAST, dm.id)
        FROM knowledge_item_sources kis
        JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
        WHERE kis.knowledge_item_id = ki.id
      ), '[]'::jsonb),
      'providerCalls', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'id', pc.id,
          'stage', pc.stage,
          'providerId', pc.provider_id,
          'modelId', pc.model_id,
          'modelName', pc.model_name,
          'status', pc.status,
          'inputTokens', pc.input_tokens,
          'outputTokens', pc.output_tokens,
          'cachedTokens', pc.cached_tokens,
          'estimatedCostUsd', pc.estimated_cost_usd,
          'latencyMs', pc.latency_ms,
          'httpStatus', pc.http_status,
          'errorCode', pc.error_code,
          'errorMessage', pc.error_message,
          'createdAt', pc.created_at
        ) ORDER BY pc.id)
        FROM provider_calls pc
        WHERE pc.run_id = ki.run_id
      ), '[]'::jsonb)
    ) AS material_json
  FROM knowledge_items ki
  JOIN mini_runs rr ON rr.id = ki.run_id
  ORDER BY ki.id
)
SELECT jsonb_pretty(jsonb_agg(material_json ORDER BY (material_json->>'id')::bigint))
FROM material_rows
) TO '/tmp/full-pipeline-20k-materials-valid-20260701.json';

COPY (
WITH mini_runs AS (
  SELECT rr.*
  FROM replay_runs rr
  JOIN datasets d ON d.id = rr.dataset_id
  WHERE d.source = 'RAW_MESSAGES_SNAPSHOT_MINI'
    AND d.metadata_json->>'parentDatasetId' = '15764'
    AND rr.run_name LIKE 'FULL_PIPELINE_20K_UNLIMITED_2026_06_30_MINI_%'
), mini_materials AS (
  SELECT ki.*
  FROM knowledge_items ki
  JOIN mini_runs rr ON rr.id = ki.run_id
)
SELECT jsonb_pretty(jsonb_build_object(
  'parentDatasetId', 15764,
  'runCount', (SELECT count(*) FROM mini_runs),
  'completedRunCount', (SELECT count(*) FROM mini_runs WHERE status = 'COMPLETED'),
  'messageCount', (SELECT COALESCE(sum(total_messages), 0) FROM mini_runs),
  'providerCalls', (SELECT COALESCE(sum(provider_calls_total), 0) FROM mini_runs),
  'estimatedCostUsd', (SELECT COALESCE(sum(estimated_cost_usd), 0) FROM mini_runs),
  'materialCount', (SELECT count(*) FROM mini_materials),
  'materialsByType', (SELECT COALESCE(jsonb_object_agg(artifact_type, count), '{}'::jsonb) FROM (SELECT artifact_type, count(*) AS count FROM mini_materials GROUP BY artifact_type ORDER BY artifact_type) t),
  'materialsByClusterType', (SELECT COALESCE(jsonb_object_agg(source_cluster_type, count), '{}'::jsonb) FROM (SELECT source_cluster_type, count(*) AS count FROM mini_materials GROUP BY source_cluster_type ORDER BY source_cluster_type) t),
  'runIds', (SELECT jsonb_agg(id ORDER BY id) FROM mini_runs),
  'datasetIds', (SELECT jsonb_agg(dataset_id ORDER BY dataset_id) FROM mini_runs)
))
) TO '/tmp/full-pipeline-20k-run-summary-valid-20260701.json';
