ALTER TABLE training_examples
    ADD COLUMN IF NOT EXISTS run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_training_examples_run_source
    ON training_examples (run_id, source);
