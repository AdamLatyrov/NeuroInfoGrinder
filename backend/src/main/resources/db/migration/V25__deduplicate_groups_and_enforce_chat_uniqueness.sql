-- NeuroInfoGrinder V25: clean up duplicate groups and enforce one row per Telegram chat

CREATE TEMP TABLE tmp_group_canonical_map AS
WITH ranked_groups AS (
    SELECT
        g.id,
        g.telegram_chat_id,
        g.title,
        g.username,
        g.category,
        g.forum,
        g.enabled,
        g.account_id,
        g.last_read_message_id,
        g.last_read_at,
        g.created_at,
        g.updated_at,
        FIRST_VALUE(g.id) OVER (
            PARTITION BY g.telegram_chat_id
            ORDER BY
                g.enabled DESC,
                (g.account_id IS NOT NULL) DESC,
                COALESCE(g.last_read_message_id, 0) DESC,
                g.updated_at DESC,
                g.id DESC
        ) AS canonical_id,
        ROW_NUMBER() OVER (
            PARTITION BY g.telegram_chat_id
            ORDER BY
                g.enabled DESC,
                (g.account_id IS NOT NULL) DESC,
                COALESCE(g.last_read_message_id, 0) DESC,
                g.updated_at DESC,
                g.id DESC
        ) AS row_num
    FROM groups g
)
SELECT
    id AS duplicate_id,
    canonical_id,
    telegram_chat_id
FROM ranked_groups
WHERE row_num > 1;

UPDATE groups canonical
SET
    title = COALESCE(NULLIF(canonical.title, ''), merged.title, canonical.title),
    username = COALESCE(NULLIF(canonical.username, ''), merged.username, canonical.username),
    category = COALESCE(NULLIF(canonical.category, ''), merged.category, canonical.category),
    forum = COALESCE(canonical.forum, FALSE) OR COALESCE(merged.forum, FALSE),
    enabled = COALESCE(canonical.enabled, FALSE) OR COALESCE(merged.enabled, FALSE),
    account_id = COALESCE(canonical.account_id, merged.account_id),
    last_read_message_id = GREATEST(COALESCE(canonical.last_read_message_id, 0), COALESCE(merged.max_last_read_message_id, 0)),
    last_read_at = GREATEST(canonical.last_read_at, merged.max_last_read_at),
    updated_at = GREATEST(canonical.updated_at, merged.max_updated_at)
FROM (
    SELECT
        map.canonical_id,
        MAX(NULLIF(duplicate.title, '')) AS title,
        MAX(NULLIF(duplicate.username, '')) AS username,
        MAX(NULLIF(duplicate.category, '')) AS category,
        BOOL_OR(COALESCE(duplicate.forum, FALSE)) AS forum,
        BOOL_OR(COALESCE(duplicate.enabled, FALSE)) AS enabled,
        MAX(duplicate.account_id) AS account_id,
        MAX(COALESCE(duplicate.last_read_message_id, 0)) AS max_last_read_message_id,
        MAX(duplicate.last_read_at) AS max_last_read_at,
        MAX(duplicate.updated_at) AS max_updated_at
    FROM tmp_group_canonical_map map
    JOIN groups duplicate ON duplicate.id = map.duplicate_id
    GROUP BY map.canonical_id
) merged
WHERE canonical.id = merged.canonical_id;

UPDATE messages message
SET group_id = map.canonical_id
FROM tmp_group_canonical_map map
WHERE message.group_id = map.duplicate_id;

CREATE TEMP TABLE tmp_message_canonical_map AS
WITH ranked_messages AS (
    SELECT
        m.id,
        m.group_id,
        m.telegram_message_id,
        FIRST_VALUE(m.id) OVER (
            PARTITION BY m.group_id, m.telegram_message_id
            ORDER BY
                (m.guide_id IS NOT NULL) DESC,
                (m.classifier_result_json IS NOT NULL) DESC,
                (m.rule_result_json IS NOT NULL) DESC,
                COALESCE(m.classifier_score, 0) DESC,
                COALESCE(m.signal_score, 0) DESC,
                LENGTH(COALESCE(m.text, '')) DESC,
                m.updated_at DESC,
                m.id DESC
        ) AS canonical_id,
        ROW_NUMBER() OVER (
            PARTITION BY m.group_id, m.telegram_message_id
            ORDER BY
                (m.guide_id IS NOT NULL) DESC,
                (m.classifier_result_json IS NOT NULL) DESC,
                (m.rule_result_json IS NOT NULL) DESC,
                COALESCE(m.classifier_score, 0) DESC,
                COALESCE(m.signal_score, 0) DESC,
                LENGTH(COALESCE(m.text, '')) DESC,
                m.updated_at DESC,
                m.id DESC
        ) AS row_num
    FROM messages m
)
SELECT
    id AS duplicate_id,
    canonical_id
FROM ranked_messages
WHERE row_num > 1;

UPDATE guide_source_messages source
SET message_id = map.canonical_id
FROM tmp_message_canonical_map map
WHERE source.message_id = map.duplicate_id;

UPDATE pipeline_traces trace
SET message_id = map.canonical_id
FROM tmp_message_canonical_map map
WHERE trace.message_id = map.duplicate_id;

UPDATE guides guide
SET root_message_id = map.canonical_id
FROM tmp_message_canonical_map map
WHERE guide.root_message_id = map.duplicate_id;

DELETE FROM messages duplicate
USING tmp_message_canonical_map map
WHERE duplicate.id = map.duplicate_id;

DROP TABLE tmp_message_canonical_map;

UPDATE guides guide
SET group_id = map.canonical_id
FROM tmp_group_canonical_map map
WHERE guide.group_id = map.duplicate_id;

UPDATE pipeline_traces trace
SET group_id = map.canonical_id
FROM tmp_group_canonical_map map
WHERE trace.group_id = map.duplicate_id;

UPDATE settings app_settings
SET publication_target_group_id = map.canonical_id
FROM tmp_group_canonical_map map
WHERE app_settings.publication_target_group_id = map.duplicate_id;

DELETE FROM groups duplicate
USING tmp_group_canonical_map map
WHERE duplicate.id = map.duplicate_id;

DROP TABLE tmp_group_canonical_map;

CREATE UNIQUE INDEX IF NOT EXISTS uk_groups_telegram_chat_id
    ON groups(telegram_chat_id);
