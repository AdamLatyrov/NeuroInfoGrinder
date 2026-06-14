-- NeuroInfoGrinder V6: Pipeline trace tracking

CREATE TABLE pipeline_traces (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,
    message_id      BIGINT,
    group_id        BIGINT,
    stage           VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    input_data      TEXT,
    output_data     TEXT,
    error_message   TEXT,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at     TIMESTAMP WITH TIME ZONE,
    duration_ms     BIGINT,
    rule_id         BIGINT,
    classifier_id   BIGINT,
    prompt_id       BIGINT,
    provider_id     BIGINT,
    model           VARCHAR(64),
    input_tokens    INTEGER DEFAULT 0,
    output_tokens   INTEGER DEFAULT 0,
    cost_usd        DOUBLE PRECISION DEFAULT 0.0,
    score           DOUBLE PRECISION,
    confidence      DOUBLE PRECISION,
    reason          TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pipeline_traces_trace_id ON pipeline_traces(trace_id);
CREATE INDEX idx_pipeline_traces_message_id ON pipeline_traces(message_id);
CREATE INDEX idx_pipeline_traces_stage ON pipeline_traces(stage);
CREATE INDEX idx_pipeline_traces_created_at ON pipeline_traces(created_at);
