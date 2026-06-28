ALTER TABLE pipeline_message_trace DROP CONSTRAINT IF EXISTS chk_pipeline_message_trace_status;
ALTER TABLE pipeline_message_trace ADD CONSTRAINT chk_pipeline_message_trace_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'PROCESSED_DEGRADED', 'SKIPPED', 'FAILED', 'WAITING_FOR_WORKER'));

ALTER TABLE local_model_calls DROP CONSTRAINT IF EXISTS chk_local_model_calls_status;
ALTER TABLE local_model_calls ADD CONSTRAINT chk_local_model_calls_status CHECK (status IN (
    'SUCCESS',
    'PROCESSED_DEGRADED',
    'FAILED',
    'TIMEOUT',
    'MODEL_WORKER_DOWN',
    'MODEL_NOT_CONFIGURED'
));
