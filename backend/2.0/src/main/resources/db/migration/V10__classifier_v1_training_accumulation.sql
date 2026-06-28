ALTER TABLE training_examples
    ADD COLUMN IF NOT EXISTS decision_label TEXT,
    ADD COLUMN IF NOT EXISTS context_need_label TEXT NOT NULL DEFAULT 'STANDALONE';

UPDATE training_examples
SET decision_label = COALESCE(decision_label, decision),
    context_need_label = COALESCE(context_need_label, 'STANDALONE')
WHERE decision_label IS NULL OR context_need_label IS NULL;

ALTER TABLE labeling_items
    ADD COLUMN IF NOT EXISTS batch_id BIGINT,
    ADD COLUMN IF NOT EXISTS batch_strategy TEXT,
    ADD COLUMN IF NOT EXISTS batch_bucket TEXT,
    ADD COLUMN IF NOT EXISTS usefulness_label TEXT,
    ADD COLUMN IF NOT EXISTS decision_label TEXT,
    ADD COLUMN IF NOT EXISTS artifact_type_label TEXT,
    ADD COLUMN IF NOT EXISTS message_role_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS evidence_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS actionability_label TEXT,
    ADD COLUMN IF NOT EXISTS context_need_label TEXT;

CREATE TABLE IF NOT EXISTS labeling_batches (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    strategy TEXT NOT NULL,
    target_size INTEGER NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0,
    bucket_counts_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_labeling_items_batch_id
    ON labeling_items (batch_id);

CREATE INDEX IF NOT EXISTS idx_training_examples_source
    ON training_examples (source);

CREATE INDEX IF NOT EXISTS idx_training_examples_usefulness_label
    ON training_examples (usefulness_label);
