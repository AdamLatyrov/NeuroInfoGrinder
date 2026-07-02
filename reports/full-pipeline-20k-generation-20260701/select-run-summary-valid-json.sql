WITH mini_runs AS (
  SELECT rr.*
  FROM replay_runs rr
  JOIN datasets d ON d.id = rr.dataset_id
  WHERE d.source = 'RAW_MESSAGES_SNAPSHOT_MINI'
    AND d.metadata_json->>'parentDatasetId' = '15764'
    AND rr.run_name LIKE 'FULL_PIPELINE_20K_UNLIMITED_2026_06_30_MINI_%'
), mini_materials AS (
  SELECT ki.*
  FROM knowledge_items ki
  JOIN mini_runs rr ON rr.id = ki.run_id
)
SELECT jsonb_build_object(
  'parentDatasetId', 15764,
  'runCount', (SELECT count(*) FROM mini_runs),
  'completedRunCount', (SELECT count(*) FROM mini_runs WHERE status = 'COMPLETED'),
  'messageCount', (SELECT COALESCE(sum(total_messages), 0) FROM mini_runs),
  'providerCalls', (SELECT COALESCE(sum(provider_calls_total), 0) FROM mini_runs),
  'estimatedCostUsd', (SELECT COALESCE(sum(estimated_cost_usd), 0) FROM mini_runs),
  'materialCount', (SELECT count(*) FROM mini_materials),
  'materialsByType', (SELECT COALESCE(jsonb_object_agg(artifact_type, count), '{}'::jsonb) FROM (SELECT artifact_type, count(*) AS count FROM mini_materials GROUP BY artifact_type ORDER BY artifact_type) t),
  'materialsByClusterType', (SELECT COALESCE(jsonb_object_agg(source_cluster_type, count), '{}'::jsonb) FROM (SELECT source_cluster_type, count(*) AS count FROM mini_materials GROUP BY source_cluster_type ORDER BY source_cluster_type) t),
  'runIds', (SELECT jsonb_agg(id ORDER BY id) FROM mini_runs),
  'datasetIds', (SELECT jsonb_agg(dataset_id ORDER BY dataset_id) FROM mini_runs)
)::text;
