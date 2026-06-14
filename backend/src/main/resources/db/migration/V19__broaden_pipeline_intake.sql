-- V19: Broaden early intake so short but valuable request/guide messages
-- reach chain building and downstream classification.

-- Keep settings singleton deterministic in existing databases.
DELETE FROM settings
WHERE id NOT IN (
    SELECT MIN(id)
    FROM settings
);

ALTER TABLE settings
    ALTER COLUMN filter_min_message_length SET DEFAULT 12;

UPDATE settings
SET filter_min_message_length = 12,
    updated_at = CURRENT_TIMESTAMP
WHERE filter_min_message_length > 12;

-- Questions and source-seeking messages should not be penalized by default.
UPDATE classifiers
SET model_config_json = REPLACE(model_config_json, '"has_question_marker": -0.30', '"has_question_marker": 0.18'),
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'LINEAR_MODEL'
  AND model_config_json LIKE '%"has_question_marker": -0.30%';

-- Broaden the AI/useful-content include rule with account/provider/source markers.
UPDATE rules
SET conditions_json = '[{"type":"KEYWORD_MATCH","value":"gpt,claude,opus,sonnet,codex,cursor,openai,anthropic,api,model,модель,лимит,credits,free,халява,доступ,release,релиз,provider,endpoint,proxy,repo,github,акк,акки,аккаунт,аккаунты,сайт,ссылка,ключ,провайдер,эндпоинт,прокси,купить,покупать,где купить,где взять,кто нашел,кто знает,есть у кого"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2005;

-- Broaden explicit guide/how-to markers beyond the literal word "guide".
UPDATE rules
SET conditions_json = '[{"type":"KEYWORD_MATCH","value":"guide,гайд,tutorial,туториал,how to,как сделать,инструкция,шаг,prompt,промпт,setup,install,настроить,настройка,подключить,схема,разбор"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2006;

-- Catch source-seeking and workflow questions even when they are short.
INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2008,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    30,
    '[{"type":"KEYWORD_MATCH","value":"кто знает,кто нашел,подскажите,есть у кого,где купить,где взять,сайт,ссылка,провайдер,ключ,эндпоинт,прокси,акк,акки,аккаунт,аккаунты,промпт,настроить,настройка,подключить,инструкция"}]',
    '[]',
    'Включить полезные запросы и источники',
    'Пропускает короткие, но ценные сообщения про источники, доступы, настройки и ответы на практические вопросы.',
    'INCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE id = 2008
);
