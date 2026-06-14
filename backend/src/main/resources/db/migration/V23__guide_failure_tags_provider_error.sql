ALTER TABLE guides
    ADD COLUMN IF NOT EXISTS root_message_id BIGINT,
    ADD COLUMN IF NOT EXISTS tags_json TEXT,
    ADD COLUMN IF NOT EXISTS generation_error TEXT;

ALTER TABLE ai_providers
    ADD COLUMN IF NOT EXISTS last_error TEXT;
