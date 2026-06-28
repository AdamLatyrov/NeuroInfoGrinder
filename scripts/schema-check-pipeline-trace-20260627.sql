SELECT column_name
FROM information_schema.columns
WHERE table_name = 'pipeline_message_trace'
ORDER BY ordinal_position;
