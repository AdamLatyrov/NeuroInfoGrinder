ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS single_message_score_breakdown_json JSONB NOT NULL DEFAULT '{}'::jsonb;
