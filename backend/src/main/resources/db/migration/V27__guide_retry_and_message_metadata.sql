ALTER TABLE messages
    ADD COLUMN sender_username VARCHAR(128),
    ADD COLUMN text_entities_json TEXT;

ALTER TABLE guides
    ADD COLUMN raw_response TEXT,
    ADD COLUMN regenerated_from_guide_id BIGINT;
