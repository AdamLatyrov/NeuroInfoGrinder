-- Multi-source signals: a signal can be assembled from several messages (sender-series grouping,
-- e.g. modelhub Store bot posts "Claude пул пополнен" + "gpt-5.4-mini бесплатны" within an hour).
-- Also backs the signal detail page ("посмотреть все сообщения из которых он собрался") and the
-- manual "promote signal to material" action.
CREATE TABLE IF NOT EXISTS knowledge_signal_sources (
    signal_id        BIGINT       NOT NULL REFERENCES knowledge_signals(id) ON DELETE CASCADE,
    raw_message_id   BIGINT,
    dataset_message_id BIGINT,
    text             TEXT,
    sender_id        BIGINT,
    sender_name      TEXT,
    message_date     TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_knowledge_signal_sources_signal ON knowledge_signal_sources(signal_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_signal_sources_raw ON knowledge_signal_sources(raw_message_id) WHERE raw_message_id IS NOT NULL;
-- Unique per source message per signal (plain columns — PostgreSQL does not allow expressions in
-- a UNIQUE/PK column list; use a partial unique index for dedupe).
CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_signal_sources_dedupe
    ON knowledge_signal_sources(signal_id, raw_message_id, dataset_message_id);

-- Backfill one source row per existing signal (so existing single-message signals keep a source row).
INSERT INTO knowledge_signal_sources (signal_id, raw_message_id, dataset_message_id)
SELECT ks.id, ks.raw_message_id, ks.dataset_message_id
FROM knowledge_signals ks
WHERE NOT EXISTS (SELECT 1 FROM knowledge_signal_sources kss WHERE kss.signal_id = ks.id);
