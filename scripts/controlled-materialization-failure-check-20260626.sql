SELECT 'controlled_runs', count(*)::text
FROM replay_runs
WHERE pipeline_version = 'controlled-discussion-materialization-20260626';

SELECT id, run_name, status, error
FROM replay_runs
WHERE pipeline_version = 'controlled-discussion-materialization-20260626'
ORDER BY id;

SELECT 'controlled_discussion_segments', count(*)::text
FROM discussion_segments
WHERE run_id IN (
  SELECT id
  FROM replay_runs
  WHERE pipeline_version = 'controlled-discussion-materialization-20260626'
);

SELECT ds.id, ds.run_id, rr.run_name, ds.proposed_material_type, ds.decision, ds.source_count
FROM discussion_segments ds
JOIN replay_runs rr ON rr.id = ds.run_id
WHERE rr.pipeline_version = 'controlled-discussion-materialization-20260626'
ORDER BY ds.id;

SELECT 'controlled_materials', count(*)::text
FROM knowledge_items
WHERE source_cluster_type = 'DISCUSSION_SEGMENT'
  AND deleted_at IS NULL;
