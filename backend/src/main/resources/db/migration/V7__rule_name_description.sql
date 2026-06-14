-- NeuroInfoGrinder V7: Rule name, description, and action type

ALTER TABLE rules ADD COLUMN name VARCHAR(256) DEFAULT 'Unnamed rule';
ALTER TABLE rules ADD COLUMN description TEXT;
ALTER TABLE rules ADD COLUMN action_type VARCHAR(32) DEFAULT 'INCLUDE';
