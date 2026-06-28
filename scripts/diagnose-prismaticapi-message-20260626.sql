WITH target_raw AS (
  SELECT rm.*
  FROM raw_messages rm
  WHERE coalesce(rm.text, rm.caption, '') ILIKE '%Prismaticapi%'
     OR coalesce(rm.text, rm.caption, '') ILIKE '%prismaticapi.com%'
  ORDER BY rm.id DESC
  LIMIT 20
), target_dataset AS (
  SELECT dm.*, tr.id AS raw_id
  FROM target_raw tr
  LEFT JOIN dataset_messages dm
    ON dm.account_id = tr.account_id
   AND dm.telegram_chat_id = tr.telegram_chat_id
   AND dm.telegram_message_id = tr.telegram_message_id
), target_runs AS (
  SELECT DISTINCT rr.*
  FROM target_dataset td
  JOIN replay_run_messages rrm ON rrm.dataset_message_id = td.id
  JOIN replay_runs rr ON rr.id = rrm.run_id
), target_materials AS (
  SELECT ki.*
  FROM target_dataset td
  JOIN knowledge_item_sources kis ON kis.dataset_message_id = td.id
  JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id
  WHERE ki.deleted_at IS NULL
), target_trace AS (
  SELECT tr.*
  FROM pipeline_message_trace tr
  JOIN target_raw rm ON rm.id = tr.raw_message_id
), target_segments AS (
  SELECT ds.*
  FROM target_dataset td
  JOIN discussion_segment_sources dss ON dss.dataset_message_id = td.id
  JOIN discussion_segments ds ON ds.id = dss.discussion_segment_id
), target_provider_calls AS (
  SELECT pc.*
  FROM target_runs rr
  JOIN provider_calls pc ON pc.run_id = rr.id
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'rawMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', id,
      'accountId', account_id,
      'identityId', identity_id,
      'telegramChatId', telegram_chat_id,
      'telegramTopicId', telegram_topic_id,
      'messageThreadId', message_thread_id,
      'telegramMessageId', telegram_message_id,
      'chatTitle', chat_title,
      'topicTitle', topic_title,
      'senderName', sender_name,
      'senderUsername', sender_username,
      'messageDate', message_date,
      'ingestedAt', ingested_at,
      'contentType', content_type,
      'textPreview', left(coalesce(text, caption, ''), 1000)
    ) ORDER BY id DESC)
    FROM target_raw
  ), '[]'::jsonb),
  'chatStates', COALESCE((
    SELECT jsonb_agg(DISTINCT jsonb_build_object(
      'telegramChatId', tc.telegram_chat_id,
      'title', tc.title,
      'isEnabled', tc.is_enabled,
      'chatList', tc.chat_list,
      'positionOrder', tc.position_order,
      'activeDialog', (tc.chat_list IN ('MAIN','ARCHIVE') OR tc.position_order IS NOT NULL)
    ))
    FROM target_raw tr
    LEFT JOIN telegram_chats tc ON tc.account_id = tr.account_id AND tc.telegram_chat_id = tr.telegram_chat_id
  ), '[]'::jsonb),
  'datasetMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'datasetMessageId', id,
      'rawId', raw_id,
      'datasetId', dataset_id,
      'telegramChatId', telegram_chat_id,
      'telegramMessageId', telegram_message_id,
      'messageDate', message_date,
      'textPreview', left(coalesce(text, caption, ''), 1000)
    ) ORDER BY id DESC)
    FROM target_dataset
  ), '[]'::jsonb),
  'intake', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', i.raw_message_id,
      'status', i.status,
      'reason', i.reason,
      'replayRunId', i.replay_run_id,
      'createdAt', i.created_at,
      'updatedAt', i.updated_at
    ) ORDER BY i.raw_message_id DESC, i.created_at DESC)
    FROM pipeline_message_intake i
    JOIN target_raw rm ON rm.id = i.raw_message_id
  ), '[]'::jsonb),
  'queue', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', q.raw_message_id,
      'batchId', q.batch_id,
      'status', q.status,
      'reason', q.reason,
      'createdAt', q.created_at,
      'updatedAt', q.updated_at
    ) ORDER BY q.raw_message_id DESC, q.created_at DESC)
    FROM auto_pipeline_queue q
    JOIN target_raw rm ON rm.id = q.raw_message_id
  ), '[]'::jsonb),
  'batches', COALESCE((
    SELECT jsonb_agg(DISTINCT jsonb_build_object(
      'batchId', b.id,
      'status', b.status,
      'createdAt', b.created_at,
      'updatedAt', b.updated_at,
      'runId', b.replay_run_id
    ))
    FROM auto_pipeline_queue q
    JOIN auto_pipeline_batches b ON b.id = q.batch_id
    JOIN target_raw rm ON rm.id = q.raw_message_id
  ), '[]'::jsonb),
  'runs', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'runId', id,
      'datasetId', dataset_id,
      'runName', run_name,
      'status', status,
      'startedAt', started_at,
      'finishedAt', finished_at,
      'totalMessages', total_messages,
      'processedMessages', processed_messages,
      'providerCallsTotal', provider_calls_total,
      'error', error
    ) ORDER BY id DESC)
    FROM target_runs
  ), '[]'::jsonb),
  'runMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'runId', rrm.run_id,
      'datasetMessageId', rrm.dataset_message_id,
      'rawId', td.raw_id,
      'status', rrm.status,
      'ruleDecision', rrm.rule_decision,
      'finalDecision', rrm.final_decision,
      'singleMessageScore', rrm.single_message_score,
      'singleMessageRejectionReason', rrm.single_message_rejection_reason,
      'llmUsed', rrm.llm_used,
      'llmSkipReason', rrm.llm_skip_reason,
      'microclusterId', rrm.microcluster_id,
      'macroclusterId', rrm.macrocluster_id,
      'updatedAt', rrm.updated_at
    ) ORDER BY rrm.run_id DESC)
    FROM target_dataset td
    JOIN replay_run_messages rrm ON rrm.dataset_message_id = td.id
  ), '[]'::jsonb),
  'discussionSegments', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'segmentId', id,
      'runId', run_id,
      'sourceCount', source_count,
      'combinedScore', combined_score,
      'proposedMaterialType', proposed_material_type,
      'decision', decision,
      'rejectionReason', rejection_reason,
      'startMessageDate', start_message_date,
      'endMessageDate', end_message_date
    ) ORDER BY id DESC)
    FROM target_segments
  ), '[]'::jsonb),
  'providerCalls', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'id', id,
      'runId', run_id,
      'stage', stage,
      'modelName', model_name,
      'status', status,
      'httpStatus', http_status,
      'errorCode', error_code,
      'errorMessage', error_message,
      'createdAt', created_at
    ) ORDER BY id)
    FROM target_provider_calls
  ), '[]'::jsonb),
  'materials', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'id', id,
      'title', title,
      'artifactType', artifact_type,
      'sourceClusterType', source_cluster_type,
      'sourceClusterId', source_cluster_id,
      'status', status,
      'createdAt', created_at
    ) ORDER BY id DESC)
    FROM target_materials
  ), '[]'::jsonb),
  'traceStages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_message_id,
      'runId', replay_run_id,
      'stageId', stage_id,
      'stageName', stage_name,
      'status', status,
      'errorCode', error_code,
      'errorMessage', error_message,
      'updatedAt', updated_at
    ) ORDER BY raw_message_id DESC, created_at, id)
    FROM target_trace
  ), '[]'::jsonb)
));
