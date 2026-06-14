ALTER TABLE classifiers ADD COLUMN IF NOT EXISTS model_config_json TEXT;

INSERT INTO classifiers (
    id, created_at, updated_at, name, type, provider_id, prompt_id,
    keywords, regex_pattern, model_config_json, version, status
)
SELECT
    1002,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'AI Useful Content Linear',
    'LINEAR_MODEL',
    NULL,
    NULL,
    NULL,
    NULL,
    '{
      "bias": -0.35,
      "threshold": 0.58,
      "normalization": "sigmoid",
      "features": {
        "text_length_norm": 0.25,
        "reply_count_norm": 0.15,
        "has_ai_marker": 1.10,
        "has_value_marker": 0.75,
        "has_guide_marker": 0.65,
        "has_tool_marker": 0.55,
        "has_release_marker": 0.45,
        "has_url": 0.20,
        "has_topic": 0.10,
        "is_not_bot": 0.10,
        "has_code_marker": 0.20,
        "has_deadline_marker": 0.35,
        "has_price_marker": 0.30,
        "has_question_marker": -0.30
      }
    }',
    '1.0',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM classifiers WHERE name = 'AI Useful Content Linear'
);

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2003,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    5,
    '[{"type":"LENGTH_LT","value":"5"}]',
    '[]',
    'Exclude ultra short noise',
    'Режет пустые, односложные и случайные короткие сообщения до основного пайплайна.',
    'EXCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Exclude ultra short noise'
);

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2004,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    15,
    '[{"type":"LENGTH_LT","value":"32"},{"type":"KEYWORD_MATCH","value":"ок,окей,ага,понял,ясно,спс,лол,ахаха,хвхв,жесть,пздц,совпадение"}]',
    '[]',
    'Exclude reaction chatter',
    'Фильтрует короткие реакционные реплики и смех, которые редко несут полезную инструктивную информацию.',
    'EXCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Exclude reaction chatter'
);

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2005,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    25,
    '[{"type":"KEYWORD_MATCH","value":"gpt,claude,opus,sonnet,codex,cursor,openai,anthropic,api,model,модель,лимит,credits,free,халява,доступ,release,релиз"}]',
    '[]',
    'Include AI value signals',
    'Помечает сообщения про AI-модели, доступы, лимиты и релизы как полезные кандидаты для дальнейшей классификации.',
    'INCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Include AI value signals'
);

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2006,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    35,
    '[{"type":"KEYWORD_MATCH","value":"guide,гайд,tutorial,туториал,how to,как сделать,инструкция,шаг"}]',
    '[]',
    'Include how-to markers',
    'Помечает сообщения с явными how-to маркерами, чтобы они были видны в трассировке и легче отлаживались.',
    'INCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Include how-to markers'
);
