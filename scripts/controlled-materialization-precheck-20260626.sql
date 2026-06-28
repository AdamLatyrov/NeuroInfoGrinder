SELECT 'generation_flag', COALESCE(max(setting_value), '0')
FROM pipeline_settings
WHERE setting_key = 'discussionSegmentGenerationEnabled';

SELECT 'discussion_materials_before', count(*)::text
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;

SELECT 'knowledge_items_before', count(*)::text
FROM knowledge_items
WHERE deleted_at IS NULL;

WITH selected(segment_id, raw_ids) AS (
  VALUES
    ('S0024', ARRAY[6062,6064,6069,6070,6071,6078]::bigint[]),
    ('S0269', ARRAY[6776,6778,6799,6803,6805,6806]::bigint[]),
    ('S0132', ARRAY[6220,6221,6222,6223,6224,6225]::bigint[])
), existing AS (
  SELECT ki.id, ki.title, ki.source_cluster_type, array_agg(rm.id ORDER BY rm.id) FILTER (WHERE rm.id IS NOT NULL) AS raw_ids
  FROM knowledge_items ki
  JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
  JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
  LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
  WHERE ki.deleted_at IS NULL
  GROUP BY ki.id, ki.title, ki.source_cluster_type
), scored AS (
  SELECT s.segment_id,
         e.id AS existing_material_id,
         e.title AS existing_title,
         cardinality(ARRAY(SELECT unnest(s.raw_ids) INTERSECT SELECT unnest(COALESCE(e.raw_ids, ARRAY[]::bigint[]))))::numeric / cardinality(s.raw_ids)::numeric AS source_overlap,
         (s.raw_ids = COALESCE(e.raw_ids, ARRAY[]::bigint[])) AS content_hash_match
  FROM selected s
  LEFT JOIN existing e ON cardinality(ARRAY(SELECT unnest(s.raw_ids) INTERSECT SELECT unnest(COALESCE(e.raw_ids, ARRAY[]::bigint[])))) > 0
)
SELECT jsonb_pretty(jsonb_agg(jsonb_build_object(
  'segment_id', segment_id,
  'duplicate_found', COALESCE(source_overlap >= 0.8 OR content_hash_match, false),
  'existing_material_id', existing_material_id,
  'existing_title', existing_title,
  'source_overlap', COALESCE(source_overlap, 0),
  'content_hash_match', COALESCE(content_hash_match, false)
) ORDER BY segment_id, source_overlap DESC NULLS LAST))
FROM scored;
