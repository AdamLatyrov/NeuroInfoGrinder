ALTER TABLE macroclusters
    ADD COLUMN IF NOT EXISTS feature_store_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE macroclusters
    ADD COLUMN IF NOT EXISTS feature_version TEXT NOT NULL DEFAULT 'classical-ml-v1';

ALTER TABLE macroclusters
    ADD COLUMN IF NOT EXISTS classical_ml_stage_results_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE discovered_topics
    ADD COLUMN IF NOT EXISTS analytics_json JSONB NOT NULL DEFAULT '{}'::jsonb;
