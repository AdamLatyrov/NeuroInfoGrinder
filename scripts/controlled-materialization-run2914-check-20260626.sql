SELECT id, stage, status, http_status, error_message, response_json->'parsed'->>'artifactType' AS artifact_type, response_json->'parsed'->>'title' AS title
FROM provider_calls
WHERE run_id = 2914
ORDER BY id;

SELECT id, run_id, source_cluster_type, source_cluster_id, title, status
FROM knowledge_items
WHERE run_id = 2914;
