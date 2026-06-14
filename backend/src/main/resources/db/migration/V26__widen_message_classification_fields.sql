ALTER TABLE messages
    ALTER COLUMN classifier_reason TYPE TEXT,
    ALTER COLUMN classifier_result_json TYPE TEXT,
    ALTER COLUMN rule_result_json TYPE TEXT,
    ALTER COLUMN signal_breakdown TYPE TEXT,
    ALTER COLUMN text TYPE TEXT;
