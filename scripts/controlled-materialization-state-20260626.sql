SELECT ki.id, ki.run_id, rr.run_name, ki.source_cluster_id, ki.artifact_type, ki.title, ki.status, ki.provider_call_id,
       (SELECT count(*) FROM knowledge_item_sources kis WHERE kis.knowledge_item_id = ki.id) AS source_count
FROM knowledge_items ki
JOIN replay_runs rr ON rr.id = ki.run_id
WHERE rr.pipeline_version = 'controlled-discussion-materialization-20260626'
ORDER BY ki.id;

SELECT pc.id, pc.run_id, rr.run_name, pc.stage, pc.status, pc.http_status, pc.error_message, pc.response_json->'parsed'->>'artifactType' AS artifact_type, pc.response_json->'parsed'->>'title' AS title
FROM provider_calls pc
JOIN replay_runs rr ON rr.id = pc.run_id
WHERE rr.pipeline_version = 'controlled-discussion-materialization-20260626'
ORDER BY pc.id;
