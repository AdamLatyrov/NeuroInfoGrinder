CREATE TABLE IF NOT EXISTS telegram_identities (
    id BIGSERIAL PRIMARY KEY,
    phone_hash TEXT UNIQUE,
    phone_encrypted TEXT,
    username TEXT,
    telegram_user_id BIGINT,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS owner_user_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS identity_id BIGINT REFERENCES telegram_identities(id);
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS phone_hash TEXT;
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS phone_encrypted TEXT;
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
ALTER TABLE telegram_accounts ADD COLUMN IF NOT EXISTS disconnect_reason TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_telegram_accounts_owner_identity_active
ON telegram_accounts(owner_user_id, identity_id)
WHERE deleted_at IS NULL AND identity_id IS NOT NULL;

ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS identity_id BIGINT REFERENCES telegram_identities(id);
ALTER TABLE raw_messages ADD COLUMN IF NOT EXISTS identity_id BIGINT REFERENCES telegram_identities(id);
ALTER TABLE telegram_topics ADD COLUMN IF NOT EXISTS identity_id BIGINT REFERENCES telegram_identities(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_raw_messages_identity_chat_message
ON raw_messages(identity_id, telegram_chat_id, telegram_message_id)
WHERE identity_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_telegram_chats_identity_chat
ON telegram_chats(identity_id, telegram_chat_id)
WHERE identity_id IS NOT NULL;

ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
ALTER TABLE knowledge_items ADD COLUMN IF NOT EXISTS deleted_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_knowledge_items_not_deleted
ON knowledge_items(id)
WHERE deleted_at IS NULL;
