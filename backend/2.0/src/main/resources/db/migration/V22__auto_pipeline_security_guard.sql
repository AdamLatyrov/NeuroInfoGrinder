ALTER TABLE auto_pipeline_settings ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE auto_pipeline_settings ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE auto_pipeline_settings ADD COLUMN IF NOT EXISTS disabled_reason TEXT;
ALTER TABLE auto_pipeline_settings ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ;

UPDATE auto_pipeline_settings
SET source = CASE
    WHEN telegram_chat_id IS NULL THEN 'LEGACY_ACCOUNT_GLOBAL'
    ELSE 'LEGACY_EXPLICIT_SETTING'
END
WHERE source = 'UNKNOWN';
