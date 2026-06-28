CREATE TABLE IF NOT EXISTS message_embeddings (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL UNIQUE REFERENCES messages(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL DEFAULT 'LOCAL_HASH',
    model VARCHAR(128) NOT NULL DEFAULT 'deterministic-v1',
    vector_dim INTEGER NOT NULL,
    vector_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'EMBEDDED',
    error TEXT,
    embedded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_message_embeddings_status
    ON message_embeddings(status);

CREATE TABLE IF NOT EXISTS signal_macroclusters (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    signature VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
    microcluster_count INTEGER NOT NULL DEFAULT 0,
    message_count INTEGER NOT NULL DEFAULT 0,
    avg_problem_signal_score DOUBLE PRECISION,
    avg_pain_score DOUBLE PRECISION,
    avg_willingness_to_pay_score DOUBLE PRECISION,
    guide_id BIGINT,
    promoted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_macroclusters_group_signature UNIQUE (group_id, signature)
);

CREATE INDEX IF NOT EXISTS idx_signal_macroclusters_status
    ON signal_macroclusters(status);

CREATE TABLE IF NOT EXISTS signal_microclusters (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    macrocluster_id BIGINT REFERENCES signal_macroclusters(id) ON DELETE SET NULL,
    title VARCHAR(512) NOT NULL,
    signature VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
    message_count INTEGER NOT NULL DEFAULT 0,
    avg_problem_signal_score DOUBLE PRECISION,
    avg_pain_score DOUBLE PRECISION,
    avg_willingness_to_pay_score DOUBLE PRECISION,
    centroid_vector_json TEXT,
    last_message_at TIMESTAMPTZ,
    guide_id BIGINT,
    promoted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_microclusters_group_signature UNIQUE (group_id, signature)
);

CREATE INDEX IF NOT EXISTS idx_signal_microclusters_status
    ON signal_microclusters(status);

CREATE INDEX IF NOT EXISTS idx_signal_microclusters_macrocluster
    ON signal_microclusters(macrocluster_id);

CREATE TABLE IF NOT EXISTS signal_cluster_messages (
    id BIGSERIAL PRIMARY KEY,
    microcluster_id BIGINT NOT NULL REFERENCES signal_microclusters(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    distance DOUBLE PRECISION NOT NULL DEFAULT 0,
    role VARCHAR(32) NOT NULL DEFAULT 'EVIDENCE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_cluster_messages_message UNIQUE (message_id),
    CONSTRAINT uq_signal_cluster_messages_cluster_message UNIQUE (microcluster_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_signal_cluster_messages_microcluster
    ON signal_cluster_messages(microcluster_id);
