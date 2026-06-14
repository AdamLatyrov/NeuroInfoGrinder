-- Add signal breakdown and rule result JSON columns for pipeline trace detail
ALTER TABLE messages ADD COLUMN signal_breakdown TEXT;
ALTER TABLE messages ADD COLUMN rule_result_json TEXT;

CREATE INDEX idx_messages_signal_breakdown ON messages (signal_breakdown);
CREATE INDEX idx_messages_rule_result ON messages (rule_result_json);
