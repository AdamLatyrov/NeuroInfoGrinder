WITH base AS (
  SELECT
    ki.id,
    ki.title,
    ki.artifact_type AS type,
    CASE
      WHEN upper(coalesce(ki.artifact_type, '')) IN ('GUIDE', 'GENERATION', 'ANSWER', 'SUMMARY') THEN upper(ki.artifact_type)
      ELSE 'OTHER'
    END AS normalized_type,
    ki.status,
    ki.source_cluster_type AS candidate_type,
    ki.created_at,
    ki.deleted_at,
    (SELECT count(*) FROM knowledge_item_sources kis WHERE kis.knowledge_item_id = ki.id) AS source_count
  FROM knowledge_items ki
  WHERE ki.deleted_at IS NULL
), counts AS (
  SELECT jsonb_build_object(
    'totalActive', (SELECT count(*) FROM base),
    'byStatus', COALESCE((SELECT jsonb_object_agg(status, count ORDER BY status) FROM (SELECT coalesce(status, 'NULL') AS status, count(*) AS count FROM base GROUP BY coalesce(status, 'NULL')) s), '{}'::jsonb),
    'byRawType', COALESCE((SELECT jsonb_object_agg(type, count ORDER BY type) FROM (SELECT coalesce(type, 'NULL') AS type, count(*) AS count FROM base GROUP BY coalesce(type, 'NULL')) t), '{}'::jsonb),
    'byNormalizedType', COALESCE((SELECT jsonb_object_agg(normalized_type, count ORDER BY normalized_type) FROM (SELECT normalized_type, count(*) AS count FROM base GROUP BY normalized_type) n), '{}'::jsonb),
    'sumKnownUiTabsCurrent', (SELECT count(*) FROM base WHERE normalized_type <> 'OTHER'),
    'otherCount', (SELECT count(*) FROM base WHERE normalized_type = 'OTHER')
  ) AS data
), rows AS (
  SELECT jsonb_agg(jsonb_build_object(
    'materialId', id,
    'title', title,
    'type', type,
    'normalizedType', normalized_type,
    'status', status,
    'candidateType', candidate_type,
    'createdAt', created_at,
    'sourceCount', source_count,
    'includedInTotal', true,
    'includedInTypeTab', true,
    'tabName', CASE normalized_type
      WHEN 'GUIDE' THEN 'Гайды'
      WHEN 'GENERATION' THEN 'Генерации'
      WHEN 'ANSWER' THEN 'Ответы'
      WHEN 'SUMMARY' THEN 'Сводки'
      ELSE 'Другое'
    END,
    'notes', CASE WHEN normalized_type = 'OTHER' THEN 'Unknown/null/legacy artifact_type bucketed as OTHER' ELSE 'Known visible type' END
  ) ORDER BY normalized_type, id) AS data
  FROM base
), missing_current_tabs AS (
  SELECT jsonb_agg(jsonb_build_object(
    'materialId', id,
    'title', title,
    'type', type,
    'normalizedType', normalized_type,
    'status', status,
    'candidateType', candidate_type,
    'createdAt', created_at,
    'sourceCount', source_count
  ) ORDER BY id) AS data
  FROM base
  WHERE normalized_type = 'OTHER'
)
SELECT jsonb_pretty(jsonb_build_object(
  'capturedAt', now(),
  'counts', (SELECT data FROM counts),
  'missingCurrentTypeTabs', COALESCE((SELECT data FROM missing_current_tabs), '[]'::jsonb),
  'materials', COALESCE((SELECT data FROM rows), '[]'::jsonb)
));
