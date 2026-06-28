SELECT 'generation_flag', COALESCE(max(setting_value), '0')
FROM pipeline_settings
WHERE setting_key = 'discussionSegmentGenerationEnabled';

SELECT 'discussion_materials', count(*)::text
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;

SELECT 'knowledge_items', count(*)::text
FROM knowledge_items
WHERE deleted_at IS NULL;

SELECT 'discussion_generation_calls', count(*)::text
FROM provider_calls
WHERE stage = 'KNOWLEDGE_GENERATION'
  AND prompt_mode = 'DISCUSSION_SEGMENT';

SELECT 'discussion_judge_calls', count(*)::text
FROM provider_calls
WHERE stage = 'DISCUSSION_SEGMENT_JUDGE';

SELECT 'new_tightened_judge_calls', count(*)::text
FROM provider_calls
WHERE stage = 'DISCUSSION_SEGMENT_JUDGE'
  AND prompt_mode = 'DISCUSSION_SEGMENT'
  AND response_json ? 'segment_id';

SELECT 'new_material_ids', COALESCE(string_agg(id::text || ':' || coalesce(source_cluster_type, ''), ',' ORDER BY id), '')
FROM knowledge_items
WHERE deleted_at IS NULL
  AND id > 21;
