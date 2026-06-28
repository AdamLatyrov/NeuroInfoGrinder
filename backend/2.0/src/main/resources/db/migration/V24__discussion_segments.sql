CREATE TABLE IF NOT EXISTS discussion_segments (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    telegram_chat_id BIGINT NOT NULL,
    forum_topic_id BIGINT NULL,
    message_thread_id BIGINT NULL,
    start_message_date TIMESTAMPTZ NULL,
    end_message_date TIMESTAMPTZ NULL,
    source_count INTEGER NOT NULL,
    combined_score NUMERIC(8,4) NOT NULL,
    proposed_material_type TEXT NULL,
    decision TEXT NOT NULL,
    rejection_reason TEXT NULL,
    signals_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    suppression_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    segment_text TEXT NOT NULL DEFAULT '',
    run_id BIGINT NULL REFERENCES replay_runs(id) ON DELETE SET NULL,
    candidate_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_discussion_segments_run ON discussion_segments(run_id);
CREATE INDEX IF NOT EXISTS idx_discussion_segments_scope ON discussion_segments(account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date);

CREATE TABLE IF NOT EXISTS discussion_segment_sources (
    id BIGSERIAL PRIMARY KEY,
    discussion_segment_id BIGINT NOT NULL REFERENCES discussion_segments(id) ON DELETE CASCADE,
    raw_message_id BIGINT NULL REFERENCES raw_messages(id) ON DELETE SET NULL,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    replay_run_message_id BIGINT NULL REFERENCES replay_run_messages(id) ON DELETE SET NULL,
    order_index INTEGER NOT NULL,
    role TEXT NOT NULL DEFAULT 'unknown',
    text_preview TEXT NULL,
    message_date TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_discussion_segment_source_order UNIQUE (discussion_segment_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_discussion_segment_sources_segment ON discussion_segment_sources(discussion_segment_id, order_index);
CREATE INDEX IF NOT EXISTS idx_discussion_segment_sources_raw ON discussion_segment_sources(raw_message_id);
CREATE INDEX IF NOT EXISTS idx_discussion_segment_sources_dataset ON discussion_segment_sources(dataset_message_id);
