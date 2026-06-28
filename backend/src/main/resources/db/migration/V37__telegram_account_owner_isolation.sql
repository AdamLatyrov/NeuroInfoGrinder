-- NeuroInfoGrinder V37: Telegram account ownership and owner-scoped generated data.
-- This migration is intentionally non-destructive. If the existing production owner
-- cannot be resolved unambiguously, rows remain unowned and owner-scoped APIs hide
-- them until an explicit production mapping/backfill is approved.

ALTER TABLE telegram_accounts
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE guides
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE guide_publication_settings
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE guide_publication_log
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE topic_discussion_clusters
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE pipeline_traces
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE message_embeddings
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE signal_microclusters
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE signal_macroclusters
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

WITH adam_owner AS (
    SELECT MIN(id) AS id, COUNT(*) AS cnt
    FROM users
    WHERE LOWER(username) = 'adam'
),
sole_owner AS (
    SELECT MIN(id) AS id, COUNT(*) AS cnt
    FROM users
    WHERE COALESCE(hidden, FALSE) = FALSE
),
selected_owner AS (
    SELECT id FROM adam_owner WHERE cnt = 1
    UNION ALL
    SELECT id FROM sole_owner
    WHERE cnt = 1
      AND NOT EXISTS (SELECT 1 FROM adam_owner WHERE cnt = 1)
)
UPDATE telegram_accounts
SET owner_user_id = (SELECT id FROM selected_owner LIMIT 1)
WHERE owner_user_id IS NULL
  AND (SELECT COUNT(*) FROM selected_owner) = 1;

WITH sole_owned_account AS (
    SELECT MIN(id) AS id, COUNT(*) AS cnt
    FROM telegram_accounts
    WHERE owner_user_id IS NOT NULL
)
UPDATE groups
SET account_id = (SELECT id FROM sole_owned_account)
WHERE account_id IS NULL
  AND (SELECT cnt FROM sole_owned_account) = 1;

UPDATE groups g
SET owner_user_id = a.owner_user_id
FROM telegram_accounts a
WHERE g.account_id = a.id
  AND g.owner_user_id IS DISTINCT FROM a.owner_user_id;

UPDATE messages m
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE m.group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND m.owner_user_id IS DISTINCT FROM g.owner_user_id;

UPDATE guides guide
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE guide.group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND guide.owner_user_id IS DISTINCT FROM g.owner_user_id;

UPDATE guide_publication_settings settings
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE settings.target_group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND settings.owner_user_id IS DISTINCT FROM g.owner_user_id;

WITH selected_owner AS (
    SELECT owner_user_id AS id
    FROM telegram_accounts
    WHERE owner_user_id IS NOT NULL
    GROUP BY owner_user_id
    HAVING COUNT(*) = (SELECT COUNT(*) FROM telegram_accounts WHERE owner_user_id IS NOT NULL)
)
UPDATE guide_publication_settings
SET owner_user_id = (SELECT id FROM selected_owner LIMIT 1)
WHERE owner_user_id IS NULL
  AND (SELECT COUNT(*) FROM selected_owner) = 1;

UPDATE guide_publication_log log
SET owner_user_id = guide.owner_user_id
FROM guides guide
WHERE log.guide_id = guide.id
  AND guide.owner_user_id IS NOT NULL
  AND log.owner_user_id IS DISTINCT FROM guide.owner_user_id;

UPDATE topic_discussion_clusters cluster
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE cluster.group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND cluster.owner_user_id IS DISTINCT FROM g.owner_user_id;

UPDATE pipeline_traces trace
SET owner_user_id = m.owner_user_id
FROM messages m
WHERE trace.message_id = m.id
  AND m.owner_user_id IS NOT NULL
  AND trace.owner_user_id IS DISTINCT FROM m.owner_user_id;

UPDATE pipeline_traces trace
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE trace.group_id = g.id
  AND trace.owner_user_id IS NULL
  AND g.owner_user_id IS NOT NULL;

UPDATE message_embeddings embedding
SET owner_user_id = m.owner_user_id
FROM messages m
WHERE embedding.message_id = m.id
  AND m.owner_user_id IS NOT NULL
  AND embedding.owner_user_id IS DISTINCT FROM m.owner_user_id;

UPDATE signal_microclusters microcluster
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE microcluster.group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND microcluster.owner_user_id IS DISTINCT FROM g.owner_user_id;

UPDATE signal_macroclusters macrocluster
SET owner_user_id = g.owner_user_id
FROM groups g
WHERE macrocluster.group_id = g.id
  AND g.owner_user_id IS NOT NULL
  AND macrocluster.owner_user_id IS DISTINCT FROM g.owner_user_id;

DROP INDEX IF EXISTS uk_groups_telegram_chat_id;

CREATE INDEX IF NOT EXISTS idx_telegram_accounts_owner
    ON telegram_accounts(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_groups_owner_enabled
    ON groups(owner_user_id, enabled);

CREATE UNIQUE INDEX IF NOT EXISTS uk_groups_owner_telegram_chat_id
    ON groups(owner_user_id, telegram_chat_id)
    WHERE owner_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_messages_owner_group_status
    ON messages(owner_user_id, group_id, processing_status);

CREATE INDEX IF NOT EXISTS idx_guides_owner_group_status
    ON guides(owner_user_id, group_id, status);

CREATE INDEX IF NOT EXISTS idx_guide_publication_settings_owner
    ON guide_publication_settings(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_guide_publication_log_owner_created
    ON guide_publication_log(owner_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_topic_clusters_owner_status
    ON topic_discussion_clusters(owner_user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_pipeline_traces_owner_created
    ON pipeline_traces(owner_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_message_embeddings_owner_status
    ON message_embeddings(owner_user_id, status);

CREATE INDEX IF NOT EXISTS idx_signal_microclusters_owner_status
    ON signal_microclusters(owner_user_id, status);

CREATE INDEX IF NOT EXISTS idx_signal_macroclusters_owner_status
    ON signal_macroclusters(owner_user_id, status);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_telegram_accounts_owner_user'
    ) THEN
        ALTER TABLE telegram_accounts
            ADD CONSTRAINT fk_telegram_accounts_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_groups_owner_user'
    ) THEN
        ALTER TABLE groups
            ADD CONSTRAINT fk_groups_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_messages_owner_user'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_guides_owner_user'
    ) THEN
        ALTER TABLE guides
            ADD CONSTRAINT fk_guides_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_guide_publication_settings_owner_user'
    ) THEN
        ALTER TABLE guide_publication_settings
            ADD CONSTRAINT fk_guide_publication_settings_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_guide_publication_log_owner_user'
    ) THEN
        ALTER TABLE guide_publication_log
            ADD CONSTRAINT fk_guide_publication_log_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_topic_clusters_owner_user'
    ) THEN
        ALTER TABLE topic_discussion_clusters
            ADD CONSTRAINT fk_topic_clusters_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES users(id) NOT VALID;
    END IF;
END $$;
