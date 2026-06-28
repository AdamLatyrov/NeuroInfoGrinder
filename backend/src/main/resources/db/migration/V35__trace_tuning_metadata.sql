-- NeuroInfoGrinder V35: agent-friendly tuning metadata for pipeline traces

ALTER TABLE pipeline_traces
    ADD COLUMN entity_type VARCHAR(32),
    ADD COLUMN entity_name VARCHAR(256),
    ADD COLUMN entity_version VARCHAR(32),
    ADD COLUMN config_snapshot_json TEXT,
    ADD COLUMN tuning_hint TEXT;

CREATE INDEX idx_pipeline_traces_status ON pipeline_traces(status);
CREATE INDEX idx_pipeline_traces_classifier_id ON pipeline_traces(classifier_id);
CREATE INDEX idx_pipeline_traces_prompt_id ON pipeline_traces(prompt_id);
CREATE INDEX idx_pipeline_traces_rule_id ON pipeline_traces(rule_id);
CREATE INDEX idx_pipeline_traces_entity_type ON pipeline_traces(entity_type);
