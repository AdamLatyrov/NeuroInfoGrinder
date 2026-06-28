ALTER TABLE message_intelligence
    ADD COLUMN IF NOT EXISTS feature_store_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE message_intelligence
    ADD COLUMN IF NOT EXISTS feature_version TEXT NOT NULL DEFAULT 'classical-ml-v1';

ALTER TABLE message_intelligence
    ADD COLUMN IF NOT EXISTS classical_ml_stage_results_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE discussion_segments
    ADD COLUMN IF NOT EXISTS feature_store_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE discussion_segments
    ADD COLUMN IF NOT EXISTS feature_version TEXT NOT NULL DEFAULT 'classical-ml-v1';

ALTER TABLE discussion_segments
    ADD COLUMN IF NOT EXISTS classical_ml_stage_results_json JSONB NOT NULL DEFAULT '{}'::jsonb;
