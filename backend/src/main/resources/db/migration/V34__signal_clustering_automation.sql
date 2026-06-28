ALTER TABLE signal_microclusters
    ADD COLUMN IF NOT EXISTS avg_guide_potential_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS avg_technical_depth_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS error TEXT,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE signal_macroclusters
    ADD COLUMN IF NOT EXISTS avg_guide_potential_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS avg_technical_depth_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS error TEXT,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

UPDATE signal_microclusters
SET status = 'OPEN'
WHERE status = 'CANDIDATE';

UPDATE signal_macroclusters
SET status = 'NEW'
WHERE status = 'CANDIDATE';
