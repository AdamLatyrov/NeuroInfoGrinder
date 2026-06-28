WITH settings AS (
  SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key) AS values
  FROM pipeline_settings
  WHERE setting_key LIKE 'discussionSegment%'
), enable_time AS (
  SELECT COALESCE((values->>'discussionSegmentControlledEnableTime')::timestamptz, now()) AS ts
  FROM settings
), segments_after AS (
  SELECT ds.*
  FROM discussion_segments ds, enable_time et
  WHERE ds.end_message_date >= et.ts
), discussion_materials_after AS (
  SELECT ki.*
  FROM knowledge_items ki, enable_time et
  WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
    AND ki.deleted_at IS NULL
    AND ki.created_at >= et.ts
), discussion_materials_today AS (
  SELECT ki.*
  FROM knowledge_items ki
  WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
    AND ki.deleted_at IS NULL
    AND ki.created_at >= date_trunc('day', now())
), discussion_runs_after AS (
  SELECT DISTINCT rr.*
  FROM replay_runs rr
  JOIN discussion_segments ds ON ds.run_id = rr.id
  JOIN enable_time et ON ds.end_message_date >= et.ts
), provider_after AS (
  SELECT pc.*
  FROM provider_calls pc
  JOIN discussion_runs_after rr ON rr.id = pc.run_id
), material_source_counts AS (
  SELECT ki.id, count(kis.id) AS source_count
  FROM discussion_materials_after ki
  LEFT JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
  GROUP BY ki.id
), all_discussion_material_source_counts AS (
  SELECT ki.id, count(kis.id) AS source_count
  FROM knowledge_items ki
  LEFT JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
  WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
    AND ki.deleted_at IS NULL
  GROUP BY ki.id
), segment_source_counts AS (
  SELECT ds.id, count(dss.id) AS source_count
  FROM segments_after ds
  LEFT JOIN discussion_segment_sources dss ON dss.discussion_segment_id = ds.id
  GROUP BY ds.id
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'enableTime', (SELECT ts FROM enable_time),
  'settings', (SELECT values FROM settings),
  'counts', jsonb_build_object(
    'segmentsDetectedAfterEnable', (SELECT count(*) FROM segments_after),
    'scorerAcceptedAfterEnable', (SELECT count(*) FROM segments_after WHERE decision = 'DISCUSSION_SEGMENT_CANDIDATE' AND rejection_reason IS NULL),
    'llmJudgeCallsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE'),
    'llmJudgeSuccessAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE' AND status = 'SUCCESS'),
    'llmJudgeErrorsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'DISCUSSION_SEGMENT_JUDGE' AND status <> 'SUCCESS'),
    'generationCallsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION'),
    'generationSuccessAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION' AND status = 'SUCCESS'),
    'generationErrorsAfterEnable', (SELECT count(*) FROM provider_after WHERE stage = 'KNOWLEDGE_GENERATION' AND status <> 'SUCCESS'),
    'draftMaterialsAfterEnable', (SELECT count(*) FROM discussion_materials_after WHERE status = 'DRAFT'),
    'publishedMaterialsAfterEnable', (SELECT count(*) FROM discussion_materials_after WHERE status = 'PUBLISHED'),
    'discussionMaterialsToday', (SELECT count(*) FROM discussion_materials_today),
    'knowledgeItemsTotalActive', (SELECT count(*) FROM knowledge_items WHERE deleted_at IS NULL),
    'discussionMaterialsTotalActive', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL),
    'materialsMissingSourcesAfterEnable', (SELECT count(*) FROM material_source_counts WHERE source_count = 0),
    'segmentsMissingSourcesAfterEnable', (SELECT count(*) FROM segment_source_counts WHERE source_count = 0)
  ),
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
  'materialsAfterEnable', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'id', id,
      'title', title,
      'status', status,
      'artifactType', artifact_type,
      'createdAt', created_at,
      'runId', run_id,
      'segmentId', source_cluster_id
    ) ORDER BY id)
    FROM discussion_materials_after
  ), '[]'::jsonb),
  'lastDiscussionMaterials', COALESCE((
    SELECT jsonb_agg(row_data ORDER BY (row_data->>'id')::bigint DESC)
    FROM (
      SELECT jsonb_build_object(
        'id', ki.id,
        'title', ki.title,
        'status', ki.status,
        'artifactType', ki.artifact_type,
        'createdAt', ki.created_at,
        'runId', ki.run_id,
        'segmentId', ki.source_cluster_id,
        'sourceCount', COALESCE(msc.source_count, 0)
      ) AS row_data
      FROM knowledge_items ki
      LEFT JOIN all_discussion_material_source_counts msc ON msc.id = ki.id
      WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
        AND ki.deleted_at IS NULL
      ORDER BY ki.id DESC
      LIMIT 10
    ) t
  ), '[]'::jsonb)
));
