-- NeuroInfoGrinder V39: explicit Telegram ingestion contract.
-- Additive/non-destructive: no data deletion, no purge, no provider/session changes.

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS telegram_account_id BIGINT,
    ADD COLUMN IF NOT EXISTS telegram_chat_id BIGINT;

UPDATE messages m
SET telegram_account_id = g.account_id,
    telegram_chat_id = g.telegram_chat_id
FROM groups g
WHERE m.group_id = g.id
  AND (m.telegram_account_id IS NULL OR m.telegram_chat_id IS NULL);

CREATE TABLE IF NOT EXISTS telegram_monitored_chats (
    id                      BIGSERIAL PRIMARY KEY,
    telegram_account_id     BIGINT,
    owner_user_id           BIGINT,
    chat_id                 BIGINT NOT NULL,
    chat_title              VARCHAR(256) NOT NULL,
    chat_type               VARCHAR(64),
    topic_id                BIGINT,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    live_ingestion_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    backfill_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    last_live_message_id    BIGINT,
    last_live_message_at    TIMESTAMP WITH TIME ZONE,
    last_backfill_message_id BIGINT,
    backfill_status         VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    last_error              TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS telegram_backfill_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_account_id BIGINT,
    owner_user_id       BIGINT,
    chat_id             BIGINT NOT NULL,
    topic_id            BIGINT,
    from_message_id     BIGINT,
    from_date           TIMESTAMP WITH TIME ZONE,
    to_date             TIMESTAMP WITH TIME ZONE,
    max_messages        BIGINT,
    batch_size          INTEGER NOT NULL DEFAULT 100,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    paused_until        TIMESTAMP WITH TIME ZONE,
    flood_wait_seconds  INTEGER,
    messages_fetched    BIGINT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO telegram_monitored_chats (
    telegram_account_id,
    owner_user_id,
    chat_id,
    chat_title,
    chat_type,
    topic_id,
    enabled,
    live_ingestion_enabled,
    backfill_enabled,
    last_live_message_id,
    last_live_message_at,
    last_backfill_message_id,
    backfill_status,
    created_at,
    updated_at
)
SELECT
    g.account_id,
    g.owner_user_id,
    g.telegram_chat_id,
    g.title,
    'GROUP',
    NULL,
    COALESCE(g.enabled, FALSE),
    COALESCE(g.enabled, FALSE),
    FALSE,
    g.last_read_message_id,
    g.last_read_at,
    g.last_read_message_id,
    'IDLE',
    NOW(),
    NOW()
FROM groups g
WHERE g.telegram_chat_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM telegram_monitored_chats existing
      WHERE COALESCE(existing.telegram_account_id, 0) = COALESCE(g.account_id, 0)
        AND COALESCE(existing.owner_user_id, 0) = COALESCE(g.owner_user_id, 0)
        AND existing.chat_id = g.telegram_chat_id
        AND existing.topic_id IS NULL
  );

CREATE INDEX IF NOT EXISTS idx_messages_tg_account_chat_message
    ON messages(telegram_account_id, telegram_chat_id, telegram_message_id);

CREATE INDEX IF NOT EXISTS idx_telegram_monitored_chats_owner
    ON telegram_monitored_chats(owner_user_id, enabled);

CREATE INDEX IF NOT EXISTS idx_telegram_monitored_chats_live
    ON telegram_monitored_chats(telegram_account_id, chat_id, topic_id, enabled, live_ingestion_enabled);

CREATE INDEX IF NOT EXISTS idx_telegram_monitored_chats_backfill
    ON telegram_monitored_chats(telegram_account_id, chat_id, topic_id, enabled, backfill_enabled);

CREATE UNIQUE INDEX IF NOT EXISTS ux_telegram_monitored_chats_account_owner_chat_topic
    ON telegram_monitored_chats(
        COALESCE(telegram_account_id, 0),
        COALESCE(owner_user_id, 0),
        chat_id,
        COALESCE(topic_id, 0)
    );

CREATE INDEX IF NOT EXISTS idx_telegram_backfill_jobs_status
    ON telegram_backfill_jobs(status, paused_until, created_at);

CREATE INDEX IF NOT EXISTS idx_telegram_backfill_jobs_owner
    ON telegram_backfill_jobs(owner_user_id, updated_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ux_messages_tg_account_chat_message'
    ) AND NOT EXISTS (
        SELECT 1
        FROM (
            SELECT telegram_account_id, telegram_chat_id, telegram_message_id, COUNT(*) AS duplicate_count
            FROM messages
            WHERE telegram_account_id IS NOT NULL
              AND telegram_chat_id IS NOT NULL
              AND telegram_message_id IS NOT NULL
            GROUP BY telegram_account_id, telegram_chat_id, telegram_message_id
            HAVING COUNT(*) > 1
        ) duplicates
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX ux_messages_tg_account_chat_message
            ON messages(telegram_account_id, telegram_chat_id, telegram_message_id)
            WHERE telegram_account_id IS NOT NULL
              AND telegram_chat_id IS NOT NULL
              AND telegram_message_id IS NOT NULL';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_messages_telegram_account'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_telegram_account
            FOREIGN KEY (telegram_account_id) REFERENCES telegram_accounts(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_monitored_chats_account'
    ) THEN
        ALTER TABLE telegram_monitored_chats
            ADD CONSTRAINT fk_telegram_monitored_chats_account
            FOREIGN KEY (telegram_account_id) REFERENCES telegram_accounts(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_monitored_chats_owner'
    ) THEN
        ALTER TABLE telegram_monitored_chats
            ADD CONSTRAINT fk_telegram_monitored_chats_owner
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_backfill_jobs_account'
    ) THEN
        ALTER TABLE telegram_backfill_jobs
            ADD CONSTRAINT fk_telegram_backfill_jobs_account
            FOREIGN KEY (telegram_account_id) REFERENCES telegram_accounts(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_backfill_jobs_owner'
    ) THEN
        ALTER TABLE telegram_backfill_jobs
            ADD CONSTRAINT fk_telegram_backfill_jobs_owner
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
END $$;
