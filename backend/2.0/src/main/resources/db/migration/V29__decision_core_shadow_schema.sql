CREATE TABLE IF NOT EXISTS semantic_decision_objects (
    id BIGSERIAL PRIMARY KEY,
    trace_version TEXT NOT NULL DEFAULT 'decision-core-shadow-v1',
    decision_version TEXT NOT NULL DEFAULT 'decision-core-shadow-v1',
    mode TEXT NOT NULL DEFAULT 'SHADOW',
    object_type TEXT NOT NULL,
    object_id BIGINT,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    raw_message_id BIGINT REFERENCES raw_messages(id) ON DELETE SET NULL,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    discussion_segment_id BIGINT REFERENCES discussion_segments(id) ON DELETE SET NULL,
    cluster_level TEXT,
    cluster_id BIGINT,
    source_message_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    meaning_labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_class_labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    topic_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    entity_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    link_entities JSONB NOT NULL DEFAULT '[]'::jsonb,
    value_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    readiness_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    context_need_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    evidence_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    risk_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    duplicate_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    novelty_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    source_quality_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    actionability_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    material_route_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    final_route TEXT NOT NULL DEFAULT 'NO_DECISION',
    artifact_type_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_artifact_type TEXT,
    dedupe_identity TEXT,
    cluster_identity TEXT,
    discussion_identity TEXT,
    time_window_id TEXT,
    context_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    material_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    signal_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    enrichment_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    llm_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    manual_review_eligibility TEXT NOT NULL DEFAULT 'UNKNOWN',
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    hard_blocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    soft_warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_outputs JSONB NOT NULL DEFAULT '{}'::jsonb,
    rule_outputs JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT semantic_decision_objects_object_type_check CHECK (object_type IN ('message', 'discussion_segment', 'cluster', 'link', 'material_candidate')),
    CONSTRAINT semantic_decision_objects_mode_check CHECK (mode IN ('SHADOW', 'CONTROLLED', 'ACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_semantic_decision_objects_identity
    ON semantic_decision_objects (decision_version, object_type, (COALESCE(object_id, -1)), (COALESCE(run_id, -1)), (COALESCE(cluster_level, '')), (COALESCE(cluster_id, -1)));

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_run
    ON semantic_decision_objects (run_id, object_type, final_route);

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_dataset_message
    ON semantic_decision_objects (dataset_message_id) WHERE dataset_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_raw_message
    ON semantic_decision_objects (raw_message_id) WHERE raw_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_source_messages_gin
    ON semantic_decision_objects USING GIN (source_message_ids);

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_dedupe_identity
    ON semantic_decision_objects (dedupe_identity) WHERE dedupe_identity IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_cluster_identity
    ON semantic_decision_objects (cluster_identity) WHERE cluster_identity IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_semantic_decision_objects_discussion_identity
    ON semantic_decision_objects (discussion_identity) WHERE discussion_identity IS NOT NULL;

CREATE TABLE IF NOT EXISTS semantic_decision_observations (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT NOT NULL REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    stage_name TEXT NOT NULL,
    observer_name TEXT NOT NULL,
    observation_type TEXT NOT NULL,
    confidence NUMERIC(6,5),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_semantic_decision_observations_object
    ON semantic_decision_observations (decision_object_id, stage_name, observation_type);

CREATE INDEX IF NOT EXISTS ix_semantic_decision_observations_run_stage
    ON semantic_decision_observations (run_id, stage_name, created_at DESC);

CREATE TABLE IF NOT EXISTS candidate_decision_ledger (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    candidate_group_id TEXT NOT NULL,
    candidate_id TEXT NOT NULL,
    candidate_type TEXT NOT NULL,
    event_type TEXT NOT NULL,
    event_status TEXT NOT NULL DEFAULT 'RECORDED',
    winner BOOLEAN NOT NULL DEFAULT false,
    competing_candidate_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    duplicate_anchor_id TEXT,
    material_candidate_id BIGINT,
    knowledge_item_id BIGINT REFERENCES knowledge_items(id) ON DELETE SET NULL,
    provider_call_id BIGINT REFERENCES provider_calls(id) ON DELETE SET NULL,
    rank_score NUMERIC(8,5),
    route_before TEXT,
    route_after TEXT,
    artifact_type_before TEXT,
    artifact_type_after TEXT,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    context_nodes_retained BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    excluded_message_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT candidate_decision_ledger_candidate_type_check CHECK (candidate_type IN ('single_message', 'discussion_segment', 'cluster', 'link_enriched', 'signal_only', 'manual_review', 'material_candidate'))
);

CREATE INDEX IF NOT EXISTS ix_candidate_decision_ledger_group
    ON candidate_decision_ledger (candidate_group_id, created_at, id);

CREATE INDEX IF NOT EXISTS ix_candidate_decision_ledger_run_event
    ON candidate_decision_ledger (run_id, event_type, event_status);

CREATE INDEX IF NOT EXISTS ix_candidate_decision_ledger_decision_object
    ON candidate_decision_ledger (decision_object_id);

CREATE TABLE IF NOT EXISTS context_retained_nodes (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    raw_message_id BIGINT REFERENCES raw_messages(id) ON DELETE SET NULL,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    retention_reason TEXT NOT NULL,
    node_role TEXT NOT NULL,
    eligibility_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT context_retained_nodes_node_role_check CHECK (node_role IN ('context', 'link_only', 'answer_fragment', 'correction', 'confirmation', 'disagreement', 'risk_context', 'promo_context', 'pricing_signal', 'api_signal'))
);

CREATE INDEX IF NOT EXISTS ix_context_retained_nodes_run
    ON context_retained_nodes (run_id, node_role, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_context_retained_nodes_dataset_message
    ON context_retained_nodes (dataset_message_id) WHERE dataset_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_context_retained_nodes_expires
    ON context_retained_nodes (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS message_relation_edges (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    source_decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    target_decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    source_dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    target_dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    edge_type TEXT NOT NULL,
    weight NUMERIC(7,5) NOT NULL DEFAULT 0,
    negative_constraint BOOLEAN NOT NULL DEFAULT false,
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_message_relation_edges_run_type
    ON message_relation_edges (run_id, edge_type, negative_constraint);

CREATE INDEX IF NOT EXISTS ix_message_relation_edges_source_target
    ON message_relation_edges (source_dataset_message_id, target_dataset_message_id);

CREATE TABLE IF NOT EXISTS link_enrichment_jobs (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE SET NULL,
    raw_message_id BIGINT REFERENCES raw_messages(id) ON DELETE SET NULL,
    dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    url TEXT NOT NULL,
    canonical_url TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 50,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT link_enrichment_jobs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'BLOCKED_MANUAL_REVIEW'))
);

CREATE INDEX IF NOT EXISTS ix_link_enrichment_jobs_status
    ON link_enrichment_jobs (status, priority DESC, next_attempt_at);

CREATE INDEX IF NOT EXISTS ix_link_enrichment_jobs_canonical_url
    ON link_enrichment_jobs (canonical_url) WHERE canonical_url IS NOT NULL;

CREATE TABLE IF NOT EXISTS link_enrichment_results (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES link_enrichment_jobs(id) ON DELETE CASCADE,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE SET NULL,
    original_url TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    final_url TEXT,
    domain TEXT,
    domain_category TEXT,
    reputation_score NUMERIC(6,5),
    referral_detected BOOLEAN NOT NULL DEFAULT false,
    shortlink_resolved BOOLEAN NOT NULL DEFAULT false,
    risk_flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    extracted_title TEXT,
    extracted_description TEXT,
    repo_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_entities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    enrichment_result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    fetched_metadata_is_instruction BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_link_enrichment_results_canonical_url
    ON link_enrichment_results (canonical_url);

CREATE INDEX IF NOT EXISTS ix_link_enrichment_results_domain
    ON link_enrichment_results (domain, referral_detected);

CREATE TABLE IF NOT EXISTS dedupe_identities (
    id BIGSERIAL PRIMARY KEY,
    identity_type TEXT NOT NULL,
    identity_value TEXT NOT NULL,
    confidence NUMERIC(6,5) NOT NULL DEFAULT 1,
    anchor_decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE SET NULL,
    anchor_knowledge_item_id BIGINT REFERENCES knowledge_items(id) ON DELETE SET NULL,
    anchor_review_status TEXT NOT NULL DEFAULT 'UNREVIEWED',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT dedupe_identities_unique UNIQUE (identity_type, identity_value)
);

CREATE INDEX IF NOT EXISTS ix_dedupe_identities_anchor_review
    ON dedupe_identities (anchor_review_status, identity_type);

CREATE TABLE IF NOT EXISTS claim_units (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    claim_hash TEXT NOT NULL,
    claim_text TEXT NOT NULL,
    claim_type TEXT NOT NULL,
    source_message_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    confidence NUMERIC(6,5) NOT NULL DEFAULT 0,
    verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED',
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_claim_units_hash
    ON claim_units (claim_hash, claim_type);

CREATE INDEX IF NOT EXISTS ix_claim_units_decision_object
    ON claim_units (decision_object_id);

CREATE TABLE IF NOT EXISTS event_identities (
    id BIGSERIAL PRIMARY KEY,
    event_identity TEXT NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    canonical_entities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_message_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    first_seen_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    confidence NUMERIC(6,5) NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_event_identities_type_seen
    ON event_identities (event_type, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS candidate_rankings (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    decision_object_id BIGINT NOT NULL REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    candidate_group_id TEXT NOT NULL,
    candidate_type TEXT NOT NULL,
    rank_position INTEGER NOT NULL,
    rank_score NUMERIC(8,5) NOT NULL,
    value_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    evidence_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    coherence_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    source_quality_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    risk_penalty NUMERIC(6,5) NOT NULL DEFAULT 0,
    promo_penalty NUMERIC(6,5) NOT NULL DEFAULT 0,
    duplicate_penalty NUMERIC(6,5) NOT NULL DEFAULT 0,
    freshness_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    novelty_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    actionability_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    material_readiness_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    already_covered_penalty NUMERIC(6,5) NOT NULL DEFAULT 0,
    contradiction_penalty NUMERIC(6,5) NOT NULL DEFAULT 0,
    selected_for_llm BOOLEAN NOT NULL DEFAULT false,
    budget_bucket TEXT,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_candidate_rankings_group
    ON candidate_rankings (candidate_group_id, rank_position);

CREATE INDEX IF NOT EXISTS ix_candidate_rankings_run_selected
    ON candidate_rankings (run_id, selected_for_llm, rank_score DESC);

CREATE TABLE IF NOT EXISTS safety_gate_results (
    id BIGSERIAL PRIMARY KEY,
    decision_object_id BIGINT NOT NULL REFERENCES semantic_decision_objects(id) ON DELETE CASCADE,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    gate_version TEXT NOT NULL DEFAULT 'safety-gate-shadow-v1',
    safety_class TEXT NOT NULL,
    hard_block BOOLEAN NOT NULL DEFAULT false,
    manual_review BOOLEAN NOT NULL DEFAULT false,
    risk_signal BOOLEAN NOT NULL DEFAULT false,
    warning_material_allowed BOOLEAN NOT NULL DEFAULT false,
    risk_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    risk_flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    observations_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_safety_gate_results_run_class
    ON safety_gate_results (run_id, safety_class, hard_block);

CREATE INDEX IF NOT EXISTS ix_safety_gate_results_decision_object
    ON safety_gate_results (decision_object_id);

CREATE TABLE IF NOT EXISTS signal_promotion_runs (
    id BIGSERIAL PRIMARY KEY,
    run_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'CREATED',
    trigger_type TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    input_signal_count INTEGER NOT NULL DEFAULT 0,
    promoted_candidate_count INTEGER NOT NULL DEFAULT 0,
    stale_signal_count INTEGER NOT NULL DEFAULT 0,
    false_signal_count INTEGER NOT NULL DEFAULT 0,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT,
    CONSTRAINT signal_promotion_runs_status_check CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS ix_signal_promotion_runs_status
    ON signal_promotion_runs (status, started_at DESC);

CREATE TABLE IF NOT EXISTS material_update_candidates (
    id BIGSERIAL PRIMARY KEY,
    knowledge_item_id BIGINT REFERENCES knowledge_items(id) ON DELETE SET NULL,
    decision_object_id BIGINT REFERENCES semantic_decision_objects(id) ON DELETE SET NULL,
    signal_promotion_run_id BIGINT REFERENCES signal_promotion_runs(id) ON DELETE SET NULL,
    update_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING_REVIEW',
    proposed_changes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_message_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_material_update_candidates_status
    ON material_update_candidates (status, update_type, created_at DESC);

CREATE TABLE IF NOT EXISTS quality_metrics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_name TEXT NOT NULL,
    snapshot_scope TEXT NOT NULL,
    run_id BIGINT REFERENCES replay_runs(id) ON DELETE SET NULL,
    dataset_id BIGINT REFERENCES datasets(id) ON DELETE SET NULL,
    window_start TIMESTAMPTZ,
    window_end TIMESTAMPTZ,
    metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_quality_metrics_snapshots_scope
    ON quality_metrics_snapshots (snapshot_scope, window_end DESC, created_at DESC);
