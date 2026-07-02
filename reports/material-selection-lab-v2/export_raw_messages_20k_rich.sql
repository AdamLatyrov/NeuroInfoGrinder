-- Read-only SELECT only. No writes.
-- Rich 20k export from raw_messages for the offline Material Selection Lab v2.
-- Preserves reply chain, sender, raw_json, account_id needed for conversation-first
-- clustering and independent-source/evidence checks.
SELECT jsonb_build_object(
  'id', rm.id,
  'account_id', rm.account_id,
  'telegram_message_id', rm.telegram_message_id,
  'telegram_chat_id', rm.telegram_chat_id,
  'telegram_topic_id', rm.telegram_topic_id,
  'message_thread_id', rm.message_thread_id,
  'reply_to_message_id', rm.reply_to_message_id,
  'sender_id', rm.sender_id,
  'sender_name', rm.sender_name,
  'chat_title', COALESCE(rm.chat_title, ''),
  'topic_title', COALESCE(rm.topic_title, ''),
  'content_type', COALESCE(rm.content_type, ''),
  'message_date', rm.message_date,
  'ingested_at', rm.ingested_at,
  'text', COALESCE(rm.text, rm.caption, ''),
  'caption', COALESCE(rm.caption, ''),
  'raw_json', rm.raw_json
)::text
FROM raw_messages rm
WHERE COALESCE(rm.text, rm.caption, '') <> ''
ORDER BY COALESCE(rm.message_date, rm.ingested_at) DESC, rm.id DESC
LIMIT 20000;
