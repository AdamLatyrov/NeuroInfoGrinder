ALTER TABLE labeling_batches
    ADD COLUMN IF NOT EXISTS review_date DATE,
    ADD COLUMN IF NOT EXISTS include_runs_from_last_hours INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_review_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS priority_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE labeling_events
    ADD COLUMN IF NOT EXISTS review_action TEXT,
    ADD COLUMN IF NOT EXISTS training_example_id BIGINT REFERENCES training_examples(id) ON DELETE SET NULL;

ALTER TABLE training_examples
    ADD COLUMN IF NOT EXISTS labeling_event_id BIGINT REFERENCES labeling_events(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS review_action TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

ALTER TABLE training_examples
    ADD COLUMN IF NOT EXISTS model_version TEXT;

CREATE INDEX IF NOT EXISTS idx_labeling_batches_review_date
    ON labeling_batches (review_date);

CREATE INDEX IF NOT EXISTS idx_training_examples_reviewed_at
    ON training_examples (reviewed_at);
