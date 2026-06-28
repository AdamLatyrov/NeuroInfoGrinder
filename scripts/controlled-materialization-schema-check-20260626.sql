SELECT table_name, string_agg(column_name, ',' ORDER BY ordinal_position) AS columns
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('knowledge_items', 'knowledge_item_sources', 'replay_runs', 'provider_calls', 'discussion_segments', 'discussion_segment_sources', 'replay_run_stages')
GROUP BY table_name
ORDER BY table_name;
