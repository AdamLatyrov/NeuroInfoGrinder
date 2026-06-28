ALTER TABLE guides
    ADD COLUMN IF NOT EXISTS usefulness_score INTEGER;

ALTER TABLE guides
    ADD CONSTRAINT chk_guides_usefulness_score_range
        CHECK (usefulness_score IS NULL OR (usefulness_score >= 0 AND usefulness_score <= 100));

CREATE INDEX IF NOT EXISTS idx_guides_usefulness_score ON guides(usefulness_score);
