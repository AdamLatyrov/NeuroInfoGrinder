SELECT 'generation_flag', COALESCE(max(setting_value), '0')
FROM pipeline_settings
WHERE setting_key = 'discussionSegmentGenerationEnabled';

SELECT 'discussion_materials_after', count(*)::text
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;

SELECT 'knowledge_items_after', count(*)::text
FROM knowledge_items
WHERE deleted_at IS NULL;

SELECT 'controlled_material_ids', string_agg(id::text, ',' ORDER BY id)
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;

SELECT 'controlled_runs', count(*)::text
FROM replay_runs
WHERE pipeline_version = 'controlled-discussion-materialization-20260626';

SELECT 'controlled_generation_calls', count(*)::text
FROM provider_calls pc
JOIN replay_runs rr ON rr.id = pc.run_id
WHERE rr.pipeline_version = 'controlled-discussion-materialization-20260626'
  AND pc.stage = 'KNOWLEDGE_GENERATION';

SELECT ki.id, ki.run_id, ki.artifact_type, ki.status, ki.title, ki.source_cluster_type, ki.source_cluster_id,
       (SELECT count(*) FROM knowledge_item_sources kis WHERE kis.knowledge_item_id = ki.id) AS source_count,
       ki.provider_call_id
FROM knowledge_items ki
WHERE ki.source_cluster_type = 'DISCUSSION_SEGMENT'
  AND ki.deleted_at IS NULL
ORDER BY ki.id;
