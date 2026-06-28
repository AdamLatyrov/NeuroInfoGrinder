ALTER TABLE replay_run_messages
    ADD COLUMN IF NOT EXISTS semantic_decision_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE discussion_segments
    ADD COLUMN IF NOT EXISTS semantic_decision_json JSONB NOT NULL DEFAULT '{}'::jsonb;
