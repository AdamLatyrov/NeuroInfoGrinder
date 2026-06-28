WITH target_chat AS (
  SELECT tc.*
  FROM telegram_chats tc
  WHERE tc.telegram_chat_id = -1003898985313
     OR tc.title ILIKE '%API SUPPORT%ModelHub%'
), raw_chat AS (
  SELECT rm.*, coalesce(rm.text, rm.caption, '') AS body
  FROM raw_messages rm
  JOIN target_chat tc ON tc.account_id = rm.account_id AND tc.telegram_chat_id = rm.telegram_chat_id
  WHERE rm.message_date >= TIMESTAMPTZ '2026-06-26 00:00Z'
    AND rm.message_date < TIMESTAMPTZ '2026-06-28 00:00Z'
), link_rows AS (
  SELECT ml.*
  FROM message_links ml
  JOIN raw_chat rm ON rm.id = ml.message_id
), search_hits AS (
  SELECT * FROM (
    SELECT 'U01' AS ui_id, 'GPT-5.6 Sol message' AS expected_label, rm.*, ARRAY_REMOVE(ARRAY[
      CASE WHEN lower(rm.body) LIKE '%openai%' THEN 'openai' END,
      CASE WHEN lower(rm.body) LIKE '%gpt-5.6%' THEN 'gpt-5.6' END,
      CASE WHEN lower(rm.body) LIKE '%sol%' THEN 'sol' END,
      CASE WHEN lower(rm.body) LIKE '%terra%' THEN 'terra' END,
      CASE WHEN lower(rm.body) LIKE '%luna%' THEN 'luna' END,
      CASE WHEN lower(rm.body) LIKE '%fable%' THEN 'fable' END,
      CASE WHEN rm.raw_json::text ILIKE '%GPT-5.6%' THEN 'raw_json:gpt-5.6' END
    ], NULL) AS matched_terms
    FROM raw_chat rm
    WHERE lower(rm.body) LIKE '%gpt-5.6%' AND lower(rm.body) LIKE '%sol%'

    UNION ALL
    SELECT 'U02', 'GPT-5.6 government approval message', rm.*, ARRAY_REMOVE(ARRAY[
      CASE WHEN lower(rm.body) LIKE '%правитель%' THEN 'government_ru' END,
      CASE WHEN lower(rm.body) LIKE '%government%' THEN 'government_en' END,
      CASE WHEN lower(rm.body) LIKE '%gpt-5.6%' THEN 'gpt-5.6' END,
      CASE WHEN lower(rm.body) LIKE '%mythos%' THEN 'mythos' END,
      CASE WHEN lower(rm.body) LIKE '%fable%' THEN 'fable' END,
      CASE WHEN lower(rm.body) LIKE '%reuters%' THEN 'reuters' END,
      CASE WHEN lower(rm.body) LIKE '%bloomberg%' THEN 'bloomberg' END,
      CASE WHEN lower(rm.body) LIKE '%axios%' THEN 'axios' END,
      CASE WHEN lower(rm.body) LIKE '%the information%' THEN 'the information' END
    ], NULL)
    FROM raw_chat rm
    WHERE lower(rm.body) LIKE '%gpt-5.6%'
      AND (lower(rm.body) LIKE '%правитель%' OR lower(rm.body) LIKE '%government%' OR lower(rm.body) LIKE '%reuters%' OR lower(rm.body) LIKE '%bloomberg%' OR lower(rm.body) LIKE '%axios%' OR lower(rm.body) LIKE '%mythos%')

    UNION ALL
    SELECT 'U03', 'Link-only lolz.live message', rm.*, ARRAY_REMOVE(ARRAY[
      CASE WHEN lower(rm.body) LIKE '%lolz.live%' THEN 'text:lolz.live' END,
      CASE WHEN EXISTS (SELECT 1 FROM link_rows lr WHERE lr.message_id = rm.id AND lower(lr.url) LIKE '%lolz.live%') THEN 'message_links:lolz.live' END,
      CASE WHEN lower(rm.body) LIKE '%почитайте%' THEN 'read_later' END,
      CASE WHEN lower(rm.body) LIKE '%полезно%' THEN 'useful' END
    ], NULL)
    FROM raw_chat rm
    WHERE lower(rm.body) LIKE '%lolz.live%'
       OR EXISTS (SELECT 1 FROM link_rows lr WHERE lr.message_id = rm.id AND lower(lr.url) LIKE '%lolz.live%')

    UNION ALL
    SELECT 'U04', 'Long GPT-5.6 announcement from Aleksandr', rm.*, ARRAY_REMOVE(ARRAY[
      CASE WHEN lower(rm.body) LIKE '%итак, встречайте%' THEN 'intro' END,
      CASE WHEN lower(rm.body) LIKE '%gpt-5.6%' THEN 'gpt-5.6' END,
      CASE WHEN lower(rm.body) LIKE '%openai.com/index/previewing-gpt-5-6-sol%' THEN 'openai_url_text' END,
      CASE WHEN EXISTS (SELECT 1 FROM link_rows lr WHERE lr.message_id = rm.id AND lower(lr.url) LIKE '%openai.com/index/previewing-gpt-5-6-sol%') THEN 'openai_url_link' END,
      CASE WHEN lower(rm.body) LIKE '%terra%' THEN 'terra' END,
      CASE WHEN lower(rm.body) LIKE '%luna%' THEN 'luna' END,
      CASE WHEN lower(rm.body) LIKE '%mythos%' THEN 'mythos' END,
      CASE WHEN lower(rm.body) LIKE '%fable%' THEN 'fable' END
    ], NULL)
    FROM raw_chat rm
    WHERE lower(rm.body) LIKE '%gpt-5.6%'
      AND (lower(rm.body) LIKE '%итак, встречайте%' OR lower(rm.body) LIKE '%openai.com/index/previewing-gpt-5-6-sol%' OR EXISTS (SELECT 1 FROM link_rows lr WHERE lr.message_id = rm.id AND lower(lr.url) LIKE '%openai.com/index/previewing-gpt-5-6-sol%'))
  ) x
  WHERE array_length(matched_terms, 1) IS NOT NULL
), ranked_hits AS (
  SELECT *, row_number() OVER (
    PARTITION BY ui_id
    ORDER BY array_length(matched_terms, 1) DESC NULLS LAST, message_date, id
  ) AS rn
  FROM search_hits
), selected_hits AS (
  SELECT * FROM ranked_hits WHERE rn <= 8
), selected_raw AS (
  SELECT DISTINCT id AS raw_id FROM selected_hits
), selected_dataset AS (
  SELECT sr.raw_id, dm.*
  FROM selected_raw sr
  JOIN raw_messages rm ON rm.id = sr.raw_id
  LEFT JOIN dataset_messages dm
    ON dm.account_id = rm.account_id
   AND dm.telegram_chat_id = rm.telegram_chat_id
   AND dm.telegram_message_id = rm.telegram_message_id
), selected_run_messages AS (
  SELECT sd.raw_id, rrm.*
  FROM selected_dataset sd
  LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = sd.id
), selected_runs AS (
  SELECT DISTINCT rr.*
  FROM selected_run_messages srm
  JOIN replay_runs rr ON rr.id = srm.run_id
), selected_segments AS (
  SELECT sd.raw_id, ds.*, dss.order_index, dss.role, dss.replay_run_message_id
  FROM selected_dataset sd
  JOIN discussion_segment_sources dss ON dss.dataset_message_id = sd.id
  JOIN discussion_segments ds ON ds.id = dss.discussion_segment_id
), selected_materials AS (
  SELECT DISTINCT sd.raw_id, ki.*
  FROM selected_dataset sd
  JOIN knowledge_item_sources kis ON kis.dataset_message_id = sd.id
  JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id
), selected_embeddings AS (
  SELECT sd.raw_id, count(me.*) AS embedding_count
  FROM selected_dataset sd
  LEFT JOIN message_embeddings me ON me.dataset_message_id = sd.id
  GROUP BY sd.raw_id
), selected_provider_calls AS (
  SELECT DISTINCT srm.raw_id, pc.*
  FROM selected_run_messages srm
  JOIN provider_calls pc ON pc.run_id = srm.run_id
), selected_trace AS (
  SELECT pmt.*
  FROM selected_raw sr
  JOIN pipeline_message_trace pmt ON pmt.raw_message_id = sr.raw_id
), chat_state AS (
  SELECT jsonb_agg(jsonb_build_object(
    'groupId', id,
    'accountId', account_id,
    'telegramChatId', telegram_chat_id,
    'title', title,
    'isEnabled', is_enabled,
    'chatList', chat_list,
    'positionOrder', position_order,
    'activeDialog', (chat_list IN ('MAIN','ARCHIVE') OR position_order IS NOT NULL),
    'processingState', CASE WHEN is_enabled AND (chat_list IN ('MAIN','ARCHIVE') OR position_order IS NOT NULL) THEN 'ENABLED_PROCESSABLE' WHEN is_enabled THEN 'ENABLED_NOT_ACTIVE_DIALOG' ELSE 'DISABLED' END,
    'rawMessagesInRange', (SELECT count(*) FROM raw_chat)
  ) ORDER BY id) AS data
  FROM target_chat
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'targetChat', COALESCE((SELECT data FROM chat_state), '[]'::jsonb),
  'selectedHits', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'uiId', ui_id,
      'expectedLabel', expected_label,
      'rank', rn,
      'rawId', id,
      'telegramMessageId', telegram_message_id,
      'telegramChatId', telegram_chat_id,
      'messageDate', message_date,
      'messageDateMsk', message_date AT TIME ZONE 'Europe/Moscow',
      'ingestedAt', ingested_at,
      'senderName', sender_name,
      'senderUsername', sender_username,
      'topicId', telegram_topic_id,
      'messageThreadId', message_thread_id,
      'contentType', content_type,
      'hasLinks', has_links,
      'matchedTerms', to_jsonb(matched_terms),
      'sourceField', CASE
        WHEN coalesce(text, '') <> '' THEN 'raw_messages.text'
        WHEN coalesce(caption, '') <> '' THEN 'raw_messages.caption'
        ELSE 'raw_json_or_message_links'
      END,
      'textPreview', left(body, 2500),
      'links', COALESCE((
        SELECT jsonb_agg(jsonb_build_object('url', lr.url, 'domain', lr.domain, 'source', lr.source, 'visibleUrl', lr.is_visible_url) ORDER BY lr.id)
        FROM link_rows lr WHERE lr.message_id = selected_hits.id
      ), '[]'::jsonb)
    ) ORDER BY ui_id, rn)
    FROM selected_hits
  ), '[]'::jsonb),
  'datasetMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'datasetMessageId', id,
      'datasetId', dataset_id,
      'telegramMessageId', telegram_message_id,
      'messageDate', message_date,
      'textPreview', left(coalesce(text, caption, ''), 1200)
    ) ORDER BY raw_id, id)
    FROM selected_dataset
  ), '[]'::jsonb),
  'runMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'replayRunMessageId', id,
      'runId', run_id,
      'datasetMessageId', dataset_message_id,
      'status', status,
      'ruleDecision', rule_decision,
      'finalDecision', final_decision,
      'singleMessageScore', single_message_score,
      'singleMessageRejectionReason', single_message_rejection_reason,
      'llmUsed', llm_used,
      'llmSkipReason', llm_skip_reason,
      'microclusterId', microcluster_id,
      'macroclusterId', macrocluster_id,
      'updatedAt', updated_at
    ) ORDER BY raw_id, run_id, id)
    FROM selected_run_messages
  ), '[]'::jsonb),
  'runs', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'runId', id,
      'runName', run_name,
      'status', status,
      'error', error,
      'startedAt', started_at,
      'finishedAt', finished_at,
      'totalMessages', total_messages,
      'processedMessages', processed_messages,
      'providerCallsTotal', provider_calls_total
    ) ORDER BY id)
    FROM selected_runs
  ), '[]'::jsonb),
  'embeddings', COALESCE((
    SELECT jsonb_agg(jsonb_build_object('rawId', raw_id, 'embeddingCount', embedding_count) ORDER BY raw_id)
    FROM selected_embeddings
  ), '[]'::jsonb),
  'discussionSegments', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'segmentId', id,
      'runId', run_id,
      'orderIndex', order_index,
      'role', role,
      'sourceCount', source_count,
      'combinedScore', combined_score,
      'proposedMaterialType', proposed_material_type,
      'decision', decision,
      'rejectionReason', rejection_reason,
      'startMessageDate', start_message_date,
      'endMessageDate', end_message_date,
      'signals', signals_json,
      'suppressionReasons', suppression_reasons_json,
      'segmentTextPreview', left(segment_text, 1200)
    ) ORDER BY raw_id, id, order_index)
    FROM selected_segments
  ), '[]'::jsonb),
  'providerCalls', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'id', id,
      'runId', run_id,
      'stage', stage,
      'modelName', model_name,
      'status', status,
      'httpStatus', http_status,
      'errorCode', error_code,
      'errorMessage', error_message,
      'createdAt', created_at
    ) ORDER BY raw_id, id)
    FROM selected_provider_calls
  ), '[]'::jsonb),
  'materials', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'materialId', id,
      'runId', run_id,
      'sourceClusterType', source_cluster_type,
      'sourceClusterId', source_cluster_id,
      'artifactType', artifact_type,
      'title', title,
      'status', status,
      'createdAt', created_at,
      'deletedAt', deleted_at
    ) ORDER BY raw_id, id)
    FROM selected_materials
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
      'output', output_json,
      'createdAt', created_at,
      'updatedAt', updated_at
    ) ORDER BY raw_message_id, created_at, id)
    FROM selected_trace
  ), '[]'::jsonb),
  'capCounts', jsonb_build_object(
    'discussionMaterialsOn20260627Utc', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-27 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-28 00:00Z'),
    'activeDiscussionMaterialsTotal', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL),
    'settings', (SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key) FROM pipeline_settings WHERE setting_key LIKE 'discussionSegment%')
  )
));
