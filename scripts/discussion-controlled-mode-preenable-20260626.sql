SELECT jsonb_pretty(jsonb_build_object(
  'settings', (
    SELECT jsonb_object_agg(setting_key, setting_value ORDER BY setting_key)
    FROM pipeline_settings
    WHERE setting_key IN (
      'discussionSegmentGenerationEnabled',
      'discussionSegmentGenerationMode',
      'discussionSegmentMaxMaterialsPerDay',
      'discussionSegmentMaxMaterialsPerChatTopicPerDay',
      'discussionSegmentRequireLlmAccepted',
      'discussionSegmentDraftOnly',
      'discussionSegmentSkipRiskSensitive',
      'discussionSegmentFreshOnly',
      'discussionSegmentStopOnProviderError',
      'discussionSegmentStopOnGenerationError',
      'discussionSegmentControlledEnableTime'
    )
  ),
  'discussionMaterials', (SELECT count(*) FROM knowledge_items WHERE source_cluster_type='DISCUSSION_SEGMENT' AND deleted_at IS NULL),
  'knowledgeItems', (SELECT count(*) FROM knowledge_items WHERE deleted_at IS NULL),
  'last10Materials', (
    SELECT jsonb_agg(jsonb_build_object('id', id, 'sourceType', source_cluster_type, 'status', status, 'title', title, 'createdAt', created_at) ORDER BY created_at DESC, id DESC)
    FROM (
      SELECT id, source_cluster_type, status, title, created_at
      FROM knowledge_items
      WHERE deleted_at IS NULL
      ORDER BY created_at DESC, id DESC
      LIMIT 10
    ) last_materials
  )
));
