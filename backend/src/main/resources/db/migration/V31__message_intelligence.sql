ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS guide_potential_score integer,
    ADD COLUMN IF NOT EXISTS problem_signal_score integer,
    ADD COLUMN IF NOT EXISTS pain_score integer,
    ADD COLUMN IF NOT EXISTS urgency_score integer,
    ADD COLUMN IF NOT EXISTS willingness_to_pay_score integer,
    ADD COLUMN IF NOT EXISTS technical_depth_score integer,
    ADD COLUMN IF NOT EXISTS spam_score integer,
    ADD COLUMN IF NOT EXISTS meaning_summary text,
    ADD COLUMN IF NOT EXISTS problem_statement text,
    ADD COLUMN IF NOT EXISTS solution_hint text,
    ADD COLUMN IF NOT EXISTS mentioned_tools_json text,
    ADD COLUMN IF NOT EXISTS mentioned_prices_json text,
    ADD COLUMN IF NOT EXISTS mentioned_errors_json text,
    ADD COLUMN IF NOT EXISTS intelligence_reason text,
    ADD COLUMN IF NOT EXISTS cluster_candidate boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS embedding_status varchar(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS message_intelligence_json text;

CREATE INDEX IF NOT EXISTS idx_messages_cluster_candidate
    ON messages (cluster_candidate, message_date DESC);

CREATE INDEX IF NOT EXISTS idx_messages_signal_scores
    ON messages (problem_signal_score, pain_score, willingness_to_pay_score, guide_potential_score);
