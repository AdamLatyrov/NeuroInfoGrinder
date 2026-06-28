SELECT jsonb_pretty(jsonb_build_object(
  'settings', (
    SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key)
    FROM pipeline_settings
    WHERE setting_key LIKE 'discussionSegment%'
  ),
  'discussionMaterials', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type='DISCUSSION_SEGMENT' AND deleted_at IS NULL),
  'knowledgeItems', (SELECT count(*) FROM knowledge_items WHERE deleted_at IS NULL),
  'newDiscussionMaterialsAfterEnable', (
    SELECT count(*)
    FROM knowledge_items ki
    WHERE ki.source_cluster_type='DISCUSSION_SEGMENT'
      AND ki.deleted_at IS NULL
      AND ki.created_at >= COALESCE((SELECT setting_value::timestamptz FROM pipeline_settings WHERE setting_key='discussionSegmentControlledEnableTime'), now())
  )
));
