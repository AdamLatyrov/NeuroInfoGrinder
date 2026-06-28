SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'settings', (
    SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key)
    FROM pipeline_settings
    WHERE setting_key LIKE 'discussionSegment%'
  ),
  'counts', jsonb_build_object(
    'activeKnowledgeItemsTotal', (SELECT count(*) FROM knowledge_items WHERE deleted_at IS NULL),
    'activeDiscussionMaterialsTotal', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL),
    'discussionMaterialsOn20260626Utc', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-26 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-27 00:00Z'),
    'discussionMaterialsOn20260627Utc', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= TIMESTAMPTZ '2026-06-27 00:00Z' AND created_at < TIMESTAMPTZ '2026-06-28 00:00Z')
  )
));
