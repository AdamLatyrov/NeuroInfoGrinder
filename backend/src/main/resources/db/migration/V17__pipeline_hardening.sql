-- V17: Pipeline hardening — raise thresholds, add bot exclude rule, disable mock provider

-- Fix 4: Raise LINEAR_MODEL threshold from 0.58 to 0.75
-- This aligns the linear model's internal threshold with CLASSIFIER_THRESHOLD (0.75)
UPDATE classifiers
SET model_config_json = REPLACE(model_config_json, '"threshold": 0.58', '"threshold": 0.75')
WHERE type = 'LINEAR_MODEL' AND name = 'Линейная модель полезности';

-- Fix 7: Add EXCLUDE rule for all bot messages (order -1 — runs FIRST before any other rules)
-- This catches ALL bot messages regardless of length, not just short ones
-- With default-deny logic, this rule ensures bots never reach signal scoring
INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2007,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    -1,
    '[{"type": "SENDER_IS_BOT", "value": "true"}]',
    '[{"type": "EXCLUDE", "target": "message"}]',
    'Отсечь все сообщения ботов',
    'Исключает любые сообщения от ботов — системные уведомления, верификационные коды, спам-боты',
    'EXCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Отсечь все сообщения ботов'
);

-- Fix 8: Disable mock provider — it should not be active in production
-- Mock always returns score=0.95, matched=true, confidence=0.91 — trivially approves everything
UPDATE ai_providers
SET status = 'DISABLED'
WHERE model = 'mock-guide-model' AND protocol = 'MOCK';