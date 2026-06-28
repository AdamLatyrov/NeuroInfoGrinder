CREATE TABLE IF NOT EXISTS topic_discussion_clusters (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    telegram_topic_id BIGINT,
    topic_title VARCHAR(256),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    topic_label VARCHAR(512),
    topic_summary TEXT,
    semantic_hash VARCHAR(128) NOT NULL,
    guide_potential_score INTEGER,
    problem_signal_score INTEGER,
    safety_category VARCHAR(64) NOT NULL DEFAULT 'normal',
    classification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    guide_generation_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    classifier_id BIGINT,
    classifier_score DOUBLE PRECISION,
    classifier_result_json TEXT,
    guide_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_topic_discussion_clusters_group_topic_time
    ON topic_discussion_clusters(group_id, telegram_topic_id, end_at DESC);

CREATE INDEX IF NOT EXISTS idx_topic_discussion_clusters_status
    ON topic_discussion_clusters(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_topic_discussion_clusters_semantic
    ON topic_discussion_clusters(group_id, semantic_hash);

CREATE TABLE IF NOT EXISTS topic_cluster_messages (
    id BIGSERIAL PRIMARY KEY,
    cluster_id BIGINT NOT NULL REFERENCES topic_discussion_clusters(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'evidence',
    contribution_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_topic_cluster_messages_message UNIQUE (message_id),
    CONSTRAINT uq_topic_cluster_messages_cluster_message UNIQUE (cluster_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_topic_cluster_messages_cluster
    ON topic_cluster_messages(cluster_id);

CREATE TABLE IF NOT EXISTS topic_cluster_guide_candidates (
    id BIGSERIAL PRIMARY KEY,
    cluster_id BIGINT NOT NULL REFERENCES topic_discussion_clusters(id) ON DELETE CASCADE,
    guide_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    guide_angle VARCHAR(512) NOT NULL,
    safety_category VARCHAR(64) NOT NULL DEFAULT 'normal',
    why_this_cluster TEXT,
    source_message_ids_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_topic_cluster_guide_candidates_cluster
    ON topic_cluster_guide_candidates(cluster_id);

ALTER TABLE guides
    ADD COLUMN IF NOT EXISTS topic_cluster_id BIGINT,
    ADD COLUMN IF NOT EXISTS topic_cluster_guide_candidate_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_guides_topic_cluster
    ON guides(topic_cluster_id);
