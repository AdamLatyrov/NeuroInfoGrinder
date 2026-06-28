ALTER TABLE guides
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(32) NOT NULL DEFAULT 'GUIDE',
    ADD COLUMN IF NOT EXISTS content_subtype VARCHAR(64),
    ADD COLUMN IF NOT EXISTS topic_label VARCHAR(512),
    ADD COLUMN IF NOT EXISTS topic_summary TEXT,
    ADD COLUMN IF NOT EXISTS content_title VARCHAR(512),
    ADD COLUMN IF NOT EXISTS content_summary TEXT,
    ADD COLUMN IF NOT EXISTS normalized_topic_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS content_quality_score INTEGER,
    ADD COLUMN IF NOT EXISTS importance_score INTEGER,
    ADD COLUMN IF NOT EXISTS actionability_score INTEGER,
    ADD COLUMN IF NOT EXISTS novelty_score INTEGER,
    ADD COLUMN IF NOT EXISTS evidence_score INTEGER,
    ADD COLUMN IF NOT EXISTS risk_score INTEGER,
    ADD COLUMN IF NOT EXISTS confidence_score INTEGER,
    ADD COLUMN IF NOT EXISTS noise_score INTEGER,
    ADD COLUMN IF NOT EXISTS routing_reason TEXT,
    ADD COLUMN IF NOT EXISTS safety_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS publication_kind VARCHAR(32);

ALTER TABLE topic_cluster_guide_candidates
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(32) NOT NULL DEFAULT 'GUIDE',
    ADD COLUMN IF NOT EXISTS content_subtype VARCHAR(64),
    ADD COLUMN IF NOT EXISTS topic_label VARCHAR(512),
    ADD COLUMN IF NOT EXISTS topic_summary TEXT,
    ADD COLUMN IF NOT EXISTS content_title VARCHAR(512),
    ADD COLUMN IF NOT EXISTS content_summary TEXT,
    ADD COLUMN IF NOT EXISTS normalized_topic_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS content_quality_score INTEGER,
    ADD COLUMN IF NOT EXISTS importance_score INTEGER,
    ADD COLUMN IF NOT EXISTS actionability_score INTEGER,
    ADD COLUMN IF NOT EXISTS novelty_score INTEGER,
    ADD COLUMN IF NOT EXISTS evidence_score INTEGER,
    ADD COLUMN IF NOT EXISTS risk_score INTEGER,
    ADD COLUMN IF NOT EXISTS confidence_score INTEGER,
    ADD COLUMN IF NOT EXISTS noise_score INTEGER,
    ADD COLUMN IF NOT EXISTS routing_reason TEXT,
    ADD COLUMN IF NOT EXISTS publication_kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS should_create_material BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS should_generate_full_guide BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_guides_content_type_created
    ON guides(content_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_guides_group_content_type_created
    ON guides(group_id, content_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_guides_topic_cluster_content_type
    ON guides(topic_cluster_id, content_type);

CREATE INDEX IF NOT EXISTS idx_topic_cluster_candidates_content_type
    ON topic_cluster_guide_candidates(cluster_id, content_type, status);
