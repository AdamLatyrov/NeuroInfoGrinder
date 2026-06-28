BEGIN;
INSERT INTO knowledge_items (run_id, item_type, title, summary, content_json, confidence, source_message_ids, provider_call_id, source_cluster_type, source_cluster_id, artifact_type, vertical, body_json, usefulness_score, publishability_score, knowledge_value_score, status)
VALUES (2914, 'GUIDE', 'debug rollback', 'debug rollback', '{}'::jsonb, 0.82, '5103,5113', 107, 'DISCUSSION_SEGMENT', 118, 'GUIDE', 'telegram-intelligence', '{}'::jsonb, 0.90, 0.82, 0.90, 'DRAFT')
RETURNING id;
ROLLBACK;
