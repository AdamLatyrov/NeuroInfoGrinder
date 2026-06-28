WITH settings AS (
  SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key) AS values
  FROM pipeline_settings
  WHERE setting_key LIKE 'discussionSegment%'
), enable_time AS (
  SELECT COALESCE((values->>'discussionSegmentControlledEnableTime')::timestamptz, TIMESTAMPTZ '2026-06-26T16:23:22Z') AS ts
  FROM settings
), elapsed AS (
  SELECT now() AS captured_at, (SELECT ts FROM enable_time) AS enable_time, now() >= (SELECT ts FROM enable_time) + INTERVAL '24 hours' AS full_24h_elapsed
), segments_after AS (
  SELECT ds.*
  FROM discussion_segments ds, enable_time et
  WHERE ds.end_message_date >= et.ts
), discussion_runs_after AS (
  SELECT DISTINCT rr.*
  FROM replay_runs rr
  JOIN discussion_segments ds ON ds.run_id = rr.id
  JOIN enable_time et ON ds.end_message_date >= et.ts
), provider_after AS (
  SELECT pc.*
  FROM provider_calls pc
  JOIN discussion_runs_after rr ON rr.id = pc.run_id
), discussion_materials_after AS (
  SELECT ki.*
  FROM knowledge_items ki, enable_time et
  WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
    AND ki.deleted_at IS NULL
    AND ki.created_at >= et.ts
), pre_enable_same_day AS (
  SELECT ki.*
  FROM knowledge_items ki, enable_time et
  WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
    AND ki.deleted_at IS NULL
    AND ki.created_at >= date_trunc('day', et.ts)
    AND ki.created_at < et.ts
), health_counts AS (
  SELECT jsonb_build_object(
    'rawMessagesTotal', (SELECT count(*) FROM raw_messages),
    'datasetMessagesTotal', (SELECT count(*) FROM dataset_messages),
    'activeKnowledgeItemsTotal', (SELECT count(*) FROM knowledge_items WHERE deleted_at IS NULL),
    'activeDiscussionMaterialsTotal', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL),
    'pendingIntake', (SELECT count(*) FROM pipeline_message_intake WHERE status = 'PENDING'),
    'queuedIntake', (SELECT count(*) FROM pipeline_message_intake WHERE status = 'QUEUED'),
    'processingIntake', (SELECT count(*) FROM pipeline_message_intake WHERE status = 'PROCESSING'),
    'failedIntake', (SELECT count(*) FROM pipeline_message_intake WHERE status = 'FAILED')
  ) AS data
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', (SELECT captured_at FROM elapsed),
  'enableTime', (SELECT enable_time FROM elapsed),
  'full24hElapsed', (SELECT full_24h_elapsed FROM elapsed),
  'finalEligibleAt', (SELECT enable_time + INTERVAL '24 hours' FROM elapsed),
  'settings', (SELECT values FROM settings),
  'counts', jsonb_build_object(
    'segmentsDetectedAfterEnable', (SELECT count(*) FROM segments_after),
    'scorerAcceptedAfterEnable', (SELECT count(*) FROM segments_after WHERE decision IN ('DISCUSSION_SEGMENT_CANDIDATE','DISCUSSION_SEGMENT_MATERIAL_CANDIDATE') AND rejection_reason IS NULL),
    'llmJudgeCallsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE'),
    'llmJudgeSuccessAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE' AND status = 'SUCCESS'),
    'llmJudgeErrorsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE' AND status <> 'SUCCESS'),
    'generationCallsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION'),
    'generationSuccessAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION' AND status = 'SUCCESS'),
    'generationErrorsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION' AND status <> 'SUCCESS'),
    'draftMaterialsAfterEnable', (SELECT count(*) FROM discussion_materials_after WHERE status = 'DRAFT'),
    'publishedMaterialsAfterEnable', (SELECT count(*) FROM discussion_materials_after WHERE status = 'PUBLISHED'),
    'preEnableSameUtcDayDiscussionMaterials', (SELECT count(*) FROM pre_enable_same_day),
    'discussionMaterialsOn20260626Utc', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-26 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-27 00:00Z'),
    'discussionMaterialsOn20260627Utc', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-27 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-28 00:00Z')
  ),
  'healthCounts', (SELECT data FROM health_counts),
  'skipReasonsAfterEnable', COALESCE((
    SELECT jsonb_object_agg(rejection_reason, reason_count ORDER BY rejection_reason)
    FROM (
      SELECT COALESCE(rejection_reason, 'NONE') AS rejection_reason, count(*) AS reason_count
      FROM segments_after
      GROUP BY COALESCE(rejection_reason, 'NONE')
    ) r
  ), '{}'::jsonb),
  'providerStatusesAfterEnable', COALESCE((
    SELECT jsonb_object_agg(stage || ':' || status, status_count ORDER BY stage || ':' || status)
    FROM (
      SELECT stage, status, count(*) AS status_count
      FROM provider_after
      GROUP BY stage, status
    ) p
  ), '{}'::jsonb),
  'preEnableSameDayDiscussionMaterials', COALESCE((
    SELECT jsonb_agg(jsonb_build_object('id', id, 'title', title, 'status', status, 'artifactType', artifact_type, 'createdAt', created_at, 'runId', run_id, 'segmentId', source_cluster_id) ORDER BY id)
    FROM pre_enable_same_day
  ), '[]'::jsonb),
  'materialsAfterEnable', COALESCE((
    SELECT jsonb_agg(jsonb_build_object('id', id, 'title', title, 'status', status, 'artifactType', artifact_type, 'createdAt', created_at, 'runId', run_id, 'segmentId', source_cluster_id) ORDER BY id)
    FROM discussion_materials_after
  ), '[]'::jsonb)
));
