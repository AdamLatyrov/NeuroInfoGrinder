ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS single_message_score NUMERIC;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS single_message_signals_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE replay_run_messages ADD COLUMN IF NOT EXISTS single_message_rejection_reason TEXT;
