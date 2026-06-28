ALTER TABLE message_classifications
    ADD COLUMN IF NOT EXISTS classification_source TEXT NOT NULL DEFAULT 'MODEL',
    ADD COLUMN IF NOT EXISTS canonical_dataset_message_id BIGINT REFERENCES dataset_messages(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS skip_reason TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_message_classifications_run_source
    ON message_classifications (run_id, classification_source);

ALTER TABLE replay_run_messages
    ADD COLUMN IF NOT EXISTS bert_skip_reason TEXT;

CREATE TABLE IF NOT EXISTS message_stage_skips (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE,
    dataset_message_id BIGINT NOT NULL REFERENCES dataset_messages(id) ON DELETE CASCADE,
    stage TEXT NOT NULL,
    skip_reason TEXT NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_stage_skips_run_message_stage UNIQUE (run_id, dataset_message_id, stage)
);

CREATE INDEX IF NOT EXISTS idx_message_stage_skips_run_stage
    ON message_stage_skips (run_id, stage, skip_reason);

ALTER TABLE training_examples
    ADD COLUMN IF NOT EXISTS usefulness_label TEXT,
    ADD COLUMN IF NOT EXISTS artifact_type_label TEXT,
    ADD COLUMN IF NOT EXISTS message_role_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS domain_label TEXT,
    ADD COLUMN IF NOT EXISTS evidence_labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS actionability_label TEXT;

DROP VIEW IF EXISTS v_training_examples_summary;

CREATE VIEW v_training_examples_summary AS
SELECT source,
       split,
       COALESCE(usefulness_label, label, 'UNLABELED') AS usefulness_label,
       COALESCE(artifact_type_label, artifact_type, 'NONE') AS artifact_type_label,
       COALESCE(domain_label, 'OTHER') AS domain_label,
       COALESCE(actionability_label, decision, 'UNLABELED') AS actionability_label,
       count(*) AS example_count
FROM training_examples
GROUP BY source,
         split,
         COALESCE(usefulness_label, label, 'UNLABELED'),
         COALESCE(artifact_type_label, artifact_type, 'NONE'),
         COALESCE(domain_label, 'OTHER'),
         COALESCE(actionability_label, decision, 'UNLABELED');

CREATE OR REPLACE VIEW v_active_learning_queue AS
SELECT li.id AS labeling_item_id,
       li.priority,
       COALESCE(
           li.context_snapshot_json->>'activeLearningReason',
           li.context_snapshot_json->>'reason',
           CASE
               WHEN mi.rule_decision = 'SUPPRESS' AND mc.top_label IS NOT NULL AND mc.top_label <> 'NOISE_OR_CHAT'
                   THEN 'RULE_SUPPRESSED_BERT_USEFUL'
               WHEN mc.confidence < 0.60
                   THEN 'BERT_LOW_CONFIDENCE'
               WHEN mi.hard_signal AND COALESCE(rrm.macrocluster_id, li.macrocluster_id) IS NULL
                   THEN 'HIGH_HARD_SIGNAL_NOT_CLUSTERED'
               WHEN li.knowledge_item_id IS NOT NULL
                   THEN 'KNOWLEDGE_ITEM_REVIEW'
               WHEN li.macrocluster_id IS NOT NULL
                   THEN 'CLUSTER_REVIEW'
               ELSE li.suggested_decision
           END
       ) AS reason,
       li.dataset_message_id,
       left(COALESCE(dm.text, dm.caption, li.text_snapshot, ''), 500) AS text_preview,
       mi.rule_decision,
       mc.top_label AS bert_label,
       mc.confidence AS bert_confidence,
       COALESCE(li.macrocluster_id, rrm.macrocluster_id) AS cluster_id,
       li.knowledge_item_id,
       CASE
           WHEN li.suggested_labels_json <> '[]'::jsonb THEN li.suggested_labels_json
           WHEN li.suggested_label IS NOT NULL THEN jsonb_build_array(li.suggested_label)
           ELSE '[]'::jsonb
       END AS suggested_labels_json,
       li.created_at
FROM labeling_items li
LEFT JOIN dataset_messages dm ON dm.id = li.dataset_message_id
LEFT JOIN message_intelligence mi ON mi.run_id = li.run_id AND mi.dataset_message_id = li.dataset_message_id
LEFT JOIN message_classifications mc ON mc.run_id = li.run_id AND mc.dataset_message_id = li.dataset_message_id
LEFT JOIN replay_run_messages rrm ON rrm.run_id = li.run_id AND rrm.dataset_message_id = li.dataset_message_id
WHERE li.status IN ('PENDING', 'DISAGREEMENT', 'NEEDS_REVIEW')
ORDER BY li.priority DESC, li.confidence ASC NULLS FIRST, li.created_at DESC;
