-- NeuroInfoGrinder V8: Add pipeline score fields to messages for filtering/tuning

ALTER TABLE messages ADD COLUMN signal_score DOUBLE PRECISION;
ALTER TABLE messages ADD COLUMN classifier_score DOUBLE PRECISION;
ALTER TABLE messages ADD COLUMN classifier_reason VARCHAR(1024);

CREATE INDEX idx_messages_signal_score ON messages(signal_score);
CREATE INDEX idx_messages_classifier_score ON messages(classifier_score);
CREATE INDEX idx_messages_processing_status ON messages(processing_status);
