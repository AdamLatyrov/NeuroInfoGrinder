ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS tdlib_chat_type TEXT;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS is_group BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS is_direct BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS is_channel BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS is_supergroup BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS has_topics BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS supergroup_id BIGINT;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS chat_list TEXT;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS position_order BIGINT;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS sync_state TEXT NOT NULL DEFAULT 'DB_CACHE';
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS raw_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS message_thread_id BIGINT;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS icon_color INTEGER;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS order_value BIGINT;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS last_message_id BIGINT;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS raw_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS forum_topic_id BIGINT;
ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS message_thread_id BIGINT;
ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS chat_title TEXT;
ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS topic_title TEXT;
ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS forward_info_json JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_telegram_chats_account_updated ON telegram_chats (account_id, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_telegram_chats_account_type ON telegram_chats (account_id, type);
CREATE INDEX IF NOT EXISTS idx_telegram_chats_account_forum ON telegram_chats (account_id, is_forum);
CREATE INDEX IF NOT EXISTS idx_telegram_topics_account_chat ON telegram_topics (account_id, telegram_chat_id, order_value NULLS LAST, id);
CREATE INDEX IF NOT EXISTS idx_raw_messages_account_chat_thread_date ON raw_messages (account_id, telegram_chat_id, message_thread_id, message_date DESC);
