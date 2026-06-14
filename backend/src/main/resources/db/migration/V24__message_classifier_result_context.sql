ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS classifier_result_json TEXT,
    ADD COLUMN IF NOT EXISTS classification_context_hash VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_messages_classification_context_hash
    ON messages (classification_context_hash);
