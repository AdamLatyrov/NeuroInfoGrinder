SELECT 'discussion_segment_materials=' || count(*)
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;

SELECT 'active_knowledge_items=' || count(*)
FROM knowledge_items
WHERE deleted_at IS NULL;
