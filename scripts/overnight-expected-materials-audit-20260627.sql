WITH bounds AS (
  SELECT
    TIMESTAMPTZ '2026-06-27 03:40:00 Europe/Moscow' AS window_start,
    TIMESTAMPTZ '2026-06-27 04:15:00 Europe/Moscow' AS window_end,
    TIMESTAMPTZ '2026-06-26T16:23:22Z' AS enable_time
), manual_expected AS (
  SELECT * FROM (VALUES
    ('M01', TIMESTAMPTZ '2026-06-27 03:41:00 Europe/Moscow', 'MAYBE_SUMMARY_NOT_GUIDE', ARRAY['gpt-5.6','sol','terra','luna','fable']::text[]),
    ('M02', TIMESTAMPTZ '2026-06-27 03:43:00 Europe/Moscow', 'REJECT_PROMO_ALONE_OR_MERGE_CONTEXT_FOR_PROXY_API_RISK_GUIDE', ARRAY['plusvibe','plus vibe','proxy','opus','gpt-5.5','gemini','deepseek','veo']::text[]),
    ('M03', TIMESTAMPTZ '2026-06-27 03:44:00 Europe/Moscow', 'SHOULD_BECOME_MATERIAL', ARRAY['17 proxy','17 proxies','подмен','gemini-2.5','benchmark','лог','training','medical']::text[]),
    ('M04', TIMESTAMPTZ '2026-06-27 03:44:00 Europe/Moscow', 'MAYBE_SUMMARY_NOT_GUIDE', ARRAY['figma','config 2026','canvas','shader']::text[]),
    ('M05', TIMESTAMPTZ '2026-06-27 03:44:00 Europe/Moscow', 'SHOULD_BECOME_MATERIAL_IF_CONTEXT_ENOUGH', ARRAY['codex','quota','outage','status','github issue','service component']::text[]),
    ('M06', TIMESTAMPTZ '2026-06-27 03:44:00 Europe/Moscow', 'MAYBE_SUMMARY_NOT_GUIDE', ARRAY['government approval','гос','approval','gpt-5.6','limited preview']::text[]),
    ('M07', TIMESTAMPTZ '2026-06-27 03:47:00 Europe/Moscow', 'MAYBE_SAFETY_SUMMARY_ONLY_OR_REJECT_ACCESS_CIRCUMVENTION_HOWTO', ARRAY['claude','fable','ios','claude code','loophole','bug']::text[]),
    ('M08', TIMESTAMPTZ '2026-06-27 03:50:00 Europe/Moscow', 'REJECT_ABUSE', ARRAY['реферал','referral','бот','500 bots','500 ботов','8 руб']::text[]),
    ('M09', TIMESTAMPTZ '2026-06-27 03:55:00 Europe/Moscow', 'SHOULD_BECOME_MATERIAL', ARRAY['openmontage','github','video','agent','kling','runway','flux','elevenlabs','suno']::text[]),
    ('M10', TIMESTAMPTZ '2026-06-27 03:59:00 Europe/Moscow', 'REJECT_ENTITY_ONLY', ARRAY['sensors_discord_bot']::text[]),
    ('M11', TIMESTAMPTZ '2026-06-27 04:06:00 Europe/Moscow', 'REJECT_LINK_ONLY_OR_NEEDS_CONTEXT', ARRAY['lolz.live','почитайте','полезно']::text[]),
    ('M12', TIMESTAMPTZ '2026-06-27 04:13:00 Europe/Moscow', 'REJECT_FEEDBACK_CONTEXT_ONLY', ARRAY['очень полезный гайд','полезный гайд']::text[])
  ) AS x(manual_id, expected_time, expected_verdict, keywords)
), raw_window AS (
  SELECT rm.*,
    coalesce(rm.text, rm.caption, '') AS body
  FROM raw_messages rm
  JOIN bounds b ON rm.message_date >= b.window_start AND rm.message_date < b.window_end
), manual_matches AS (
  SELECT DISTINCT ON (me.manual_id)
    me.manual_id,
    me.expected_time,
    me.expected_verdict,
    rw.id AS raw_id,
    rw.account_id,
    rw.identity_id,
    rw.telegram_chat_id,
    rw.telegram_topic_id,
    rw.message_thread_id,
    rw.telegram_message_id,
    rw.chat_title,
    rw.topic_title,
    rw.sender_name,
    rw.sender_username,
    rw.message_date,
    rw.ingested_at,
    rw.content_type,
    rw.body,
    (
      SELECT count(*)
      FROM unnest(me.keywords) kw
      WHERE lower(rw.body) LIKE '%' || lower(kw) || '%'
    ) AS keyword_hits,
    abs(extract(epoch FROM (rw.message_date - me.expected_time))) AS seconds_from_expected
  FROM manual_expected me
  LEFT JOIN raw_window rw ON rw.message_date >= me.expected_time - INTERVAL '3 minutes'
    AND rw.message_date <= me.expected_time + INTERVAL '5 minutes'
    AND EXISTS (
      SELECT 1 FROM unnest(me.keywords) kw
      WHERE lower(rw.body) LIKE '%' || lower(kw) || '%'
    )
  ORDER BY me.manual_id, keyword_hits DESC NULLS LAST, seconds_from_expected NULLS LAST, rw.id
), target_raw AS (
  SELECT rw.*, mm.manual_id, mm.expected_time, mm.expected_verdict
  FROM raw_window rw
  LEFT JOIN manual_matches mm ON mm.raw_id = rw.id
), target_dataset AS (
  SELECT
    tr.manual_id,
    tr.expected_time,
    tr.expected_verdict,
    tr.id AS raw_id,
    dm.id AS dataset_message_id,
    dm.dataset_id,
    dm.telegram_chat_id,
    dm.telegram_message_id,
    dm.message_date AS dataset_message_date,
    dm.text AS dataset_text,
    dm.caption AS dataset_caption
  FROM target_raw tr
  LEFT JOIN dataset_messages dm
    ON dm.account_id = tr.account_id
   AND dm.telegram_chat_id = tr.telegram_chat_id
   AND dm.telegram_message_id = tr.telegram_message_id
), target_run_messages AS (
  SELECT
    td.manual_id,
    td.raw_id,
    rrm.id AS replay_run_message_id,
    rrm.run_id,
    rrm.dataset_message_id,
    rrm.status,
    rrm.rule_decision,
    rrm.final_decision,
    rrm.single_message_score,
    rrm.single_message_rejection_reason,
    rrm.llm_used,
    rrm.llm_skip_reason,
    rrm.microcluster_id,
    rrm.macrocluster_id,
    rrm.updated_at
  FROM target_dataset td
  LEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = td.dataset_message_id
), target_runs AS (
  SELECT DISTINCT rr.*
  FROM target_run_messages trm
  JOIN replay_runs rr ON rr.id = trm.run_id
), target_segments AS (
  SELECT
    td.manual_id,
    td.raw_id,
    ds.id AS segment_id,
    ds.run_id,
    dss.id AS segment_source_id,
    dss.order_index,
    dss.role,
    ds.source_count,
    ds.combined_score,
    ds.proposed_material_type,
    ds.decision,
    ds.rejection_reason,
    ds.signals_json,
    ds.suppression_reasons_json,
    ds.start_message_date,
    ds.end_message_date,
    left(ds.segment_text, 1600) AS segment_text_preview
  FROM target_dataset td
  JOIN discussion_segment_sources dss ON dss.dataset_message_id = td.dataset_message_id
  JOIN discussion_segments ds ON ds.id = dss.discussion_segment_id
), target_materials AS (
  SELECT DISTINCT
    td.manual_id,
    td.raw_id,
    ki.id AS material_id,
    ki.run_id,
    ki.source_cluster_type,
    ki.source_cluster_id,
    ki.artifact_type,
    ki.title,
    ki.status,
    ki.created_at,
    ki.deleted_at
  FROM target_dataset td
  JOIN knowledge_item_sources kis ON kis.dataset_message_id = td.dataset_message_id
  JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id
), target_provider_calls AS (
  SELECT pc.*
  FROM target_runs rr
  JOIN provider_calls pc ON pc.run_id = rr.id
), target_trace AS (
  SELECT pmt.*
  FROM pipeline_message_trace pmt
  JOIN target_raw tr ON tr.id = pmt.raw_message_id
), chat_states AS (
  SELECT DISTINCT
    tr.id AS raw_id,
    tc.telegram_chat_id,
    tc.title,
    tc.is_enabled,
    tc.chat_list,
    tc.position_order,
    (tc.chat_list IN ('MAIN','ARCHIVE') OR tc.position_order IS NOT NULL) AS active_dialog,
    (tc.is_enabled AND (tc.chat_list IN ('MAIN','ARCHIVE') OR tc.position_order IS NOT NULL)) AS processable,
    aps.enabled AS auto_pipeline_enabled,
    aps.source AS auto_pipeline_source,
    aps.disabled_reason AS auto_pipeline_disabled_reason
  FROM target_raw tr
  LEFT JOIN telegram_chats tc ON tc.account_id = tr.account_id AND tc.telegram_chat_id = tr.telegram_chat_id
  LEFT JOIN auto_pipeline_settings aps ON aps.account_id = tr.account_id AND aps.telegram_chat_id = tr.telegram_chat_id AND COALESCE(aps.topic_id, -1) = COALESCE(tr.telegram_topic_id, tr.message_thread_id, -1)
), cap_counts AS (
  SELECT jsonb_build_object(
    'capturedAt', now(),
    'enableTime', (SELECT enable_time FROM bounds),
    'localWindow', jsonb_build_object(
      'timezone', 'Europe/Moscow',
      'start', ((SELECT window_start FROM bounds) AT TIME ZONE 'Europe/Moscow'),
      'end', ((SELECT window_end FROM bounds) AT TIME ZONE 'Europe/Moscow')
    ),
    'utcWindow', jsonb_build_object(
      'start', (SELECT window_start FROM bounds),
      'end', (SELECT window_end FROM bounds)
    ),
    'discussionMaterialsTodayAtCapture', (
      SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= date_trunc('day', now())
    ),
    'discussionMaterialsOn20260626Utc', (
      SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-26 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-27 00:00Z'
    ),
    'discussionMaterialsOn20260627Utc', (
      SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-27 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-28 00:00Z'
    ),
    'preEnableDiscussionMaterialsSameUtcDay', COALESCE((
      SELECT jsonb_agg(jsonb_build_object('id', id, 'title', title, 'createdAt', created_at, 'segmentId', source_cluster_id) ORDER BY id)
      FROM knowledge_items
      WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
        AND deleted_at IS NULL
        AND created_at >= date_trunc('day', (SELECT enable_time FROM bounds))
        AND created_at < (SELECT enable_time FROM bounds)
    ), '[]'::jsonb),
    'postEnableDiscussionMaterials', COALESCE((
      SELECT jsonb_agg(jsonb_build_object('id', id, 'title', title, 'status', status, 'artifactType', artifact_type, 'createdAt', created_at, 'runId', run_id, 'segmentId', source_cluster_id) ORDER BY id)
      FROM knowledge_items
      WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
        AND deleted_at IS NULL
        AND created_at >= (SELECT enable_time FROM bounds)
    ), '[]'::jsonb),
    'postEnableDiscussionSegments', (
      SELECT count(*) FROM discussion_segments ds WHERE ds.end_message_date >= (SELECT enable_time FROM bounds)
    ),
    'postEnableAcceptedDiscussionSegments', (
      SELECT count(*) FROM discussion_segments ds WHERE ds.end_message_date >= (SELECT enable_time FROM bounds) AND ds.decision IN ('DISCUSSION_SEGMENT_CANDIDATE','DISCUSSION_SEGMENT_MATERIAL_CANDIDATE')
    ),
    'windowDiscussionSegments', (
      SELECT count(DISTINCT segment_id) FROM target_segments
    ),
    'windowDiscussionCandidates', (
      SELECT count(DISTINCT segment_id) FROM target_segments WHERE decision IN ('DISCUSSION_SEGMENT_CANDIDATE','DISCUSSION_SEGMENT_MATERIAL_CANDIDATE')
    )
  ) AS data
), settings_snapshot AS (
  SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key) AS data
  FROM pipeline_settings
  WHERE setting_key LIKE 'discussionSegment%'
), health AS (
  SELECT jsonb_build_object(
    'backendHealthCheckedViaDb', true,
    'now', now(),
    'rawWindowCount', (SELECT count(*) FROM raw_window),
    'manualMatchedCount', (SELECT count(*) FROM manual_matches WHERE raw_id IS NOT NULL),
    'settings', (SELECT data FROM settings_snapshot)
  ) AS data
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'capCounts', (SELECT data FROM cap_counts),
  'health', (SELECT data FROM health),
  'manualMatches', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'manualId', manual_id,
      'expectedTime', expected_time,
      'expectedVerdict', expected_verdict,
      'rawId', raw_id,
      'messageDate', message_date,
      'chatTitle', chat_title,
      'topicTitle', topic_title,
      'keywordHits', keyword_hits,
      'secondsFromExpected', seconds_from_expected,
      'textPreview', left(body, 1200)
    ) ORDER BY manual_id)
    FROM manual_matches
  ), '[]'::jsonb),
  'rawWindow', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'manualId', manual_id,
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
      'text', body
    ) ORDER BY message_date, id)
    FROM target_raw
  ), '[]'::jsonb),
  'datasetMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'manualId', manual_id,
      'rawId', raw_id,
      'datasetMessageId', dataset_message_id,
      'datasetId', dataset_id,
      'telegramChatId', telegram_chat_id,
      'telegramMessageId', telegram_message_id,
      'messageDate', dataset_message_date,
      'textPreview', left(coalesce(dataset_text, dataset_caption, ''), 1200)
    ) ORDER BY raw_id)
    FROM target_dataset
  ), '[]'::jsonb),
  'chatStates', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', raw_id,
      'telegramChatId', telegram_chat_id,
      'title', title,
      'isEnabled', is_enabled,
      'chatList', chat_list,
      'positionOrder', position_order,
      'activeDialog', active_dialog,
      'processable', processable,
      'autoPipelineEnabled', auto_pipeline_enabled,
      'autoPipelineSource', auto_pipeline_source,
      'autoPipelineDisabledReason', auto_pipeline_disabled_reason
    ) ORDER BY raw_id)
    FROM chat_states
  ), '[]'::jsonb),
  'intake', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', i.raw_message_id,
      'status', i.status,
      'reason', i.reason,
      'replayRunId', i.replay_run_id,
      'createdAt', i.created_at,
      'updatedAt', i.updated_at
    ) ORDER BY i.raw_message_id, i.created_at)
    FROM pipeline_message_intake i
    JOIN target_raw tr ON tr.id = i.raw_message_id
  ), '[]'::jsonb),
  'queue', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'rawId', q.raw_message_id,
      'batchId', q.batch_id,
      'status', q.status,
      'reason', q.reason,
      'createdAt', q.created_at,
      'updatedAt', q.updated_at
    ) ORDER BY q.raw_message_id, q.created_at)
    FROM auto_pipeline_queue q
    JOIN target_raw tr ON tr.id = q.raw_message_id
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
    JOIN target_raw tr ON tr.id = q.raw_message_id
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
    ) ORDER BY id)
    FROM target_runs
  ), '[]'::jsonb),
  'runMessages', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'manualId', manual_id,
      'rawId', raw_id,
      'replayRunMessageId', replay_run_message_id,
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
    ) ORDER BY raw_id, run_id)
    FROM target_run_messages
  ), '[]'::jsonb),
  'discussionSegments', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'manualId', manual_id,
      'rawId', raw_id,
      'segmentId', segment_id,
      'runId', run_id,
      'orderIndex', order_index,
      'role', role,
      'sourceCount', source_count,
      'combinedScore', combined_score,
      'proposedMaterialType', proposed_material_type,
      'decision', decision,
      'rejectionReason', rejection_reason,
      'signals', signals_json,
      'suppressionReasons', suppression_reasons_json,
      'startMessageDate', start_message_date,
      'endMessageDate', end_message_date,
      'segmentTextPreview', segment_text_preview
    ) ORDER BY raw_id, segment_id, order_index)
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
      'manualId', manual_id,
      'rawId', raw_id,
      'materialId', material_id,
      'runId', run_id,
      'sourceClusterType', source_cluster_type,
      'sourceClusterId', source_cluster_id,
      'artifactType', artifact_type,
      'title', title,
      'status', status,
      'createdAt', created_at,
      'deletedAt', deleted_at
    ) ORDER BY raw_id, material_id)
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
      'output', output_json,
      'createdAt', created_at,
      'updatedAt', updated_at
    ) ORDER BY raw_message_id, created_at, id)
    FROM target_trace
  ), '[]'::jsonb)
));
