ALTER TABLE classifiers ADD COLUMN IF NOT EXISTS classifier_order INTEGER NOT NULL DEFAULT 100;

UPDATE classifiers SET classifier_order = 10 WHERE type = 'LINEAR_MODEL';
UPDATE classifiers SET classifier_order = 20 WHERE type = 'LLM';
UPDATE classifiers SET classifier_order = 30 WHERE type IN ('KEYWORD', 'REGEX');
