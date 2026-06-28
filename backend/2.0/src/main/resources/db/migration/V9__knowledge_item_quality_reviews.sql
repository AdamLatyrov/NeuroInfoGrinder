CREATE TABLE IF NOT EXISTS knowledge_item_reviews (
    id BIGSERIAL PRIMARY KEY,
    knowledge_item_id BIGINT NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    reviewer_type TEXT NOT NULL CHECK (reviewer_type IN ('AUTO', 'HUMAN')),
    source_supported_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    hallucination_risk_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    publishability_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    commercial_value_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    actionability_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    specificity_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    duplicate_risk_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    artifact_type_correct BOOLEAN NOT NULL DEFAULT TRUE,
    title_quality_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    body_quality_score NUMERIC(6, 4) NOT NULL DEFAULT 0,
    needs_human_review BOOLEAN NOT NULL DEFAULT TRUE,
    verdict TEXT NOT NULL CHECK (verdict IN (
        'PUBLISHABLE',
        'NEEDS_EDIT',
        'BAD_SOURCE_SUPPORT',
        'DUPLICATE',
        'WRONG_ARTIFACT_TYPE',
        'LOW_VALUE',
        'UNSAFE'
    )),
    review_reasons_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (knowledge_item_id, reviewer_type)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_item_reviews_run_id
    ON knowledge_item_reviews (run_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_item_reviews_verdict
    ON knowledge_item_reviews (verdict);
