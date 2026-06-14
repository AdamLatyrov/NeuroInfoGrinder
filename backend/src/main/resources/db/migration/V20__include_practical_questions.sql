-- V20: Let practical questions reach downstream scoring/classification.

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2009,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    32,
    '[{"type":"LENGTH_GT","value":"24"},{"type":"REGEX_MATCH","value":"(?iu)(\\?|\\bкак\\b|\\bгде\\b|\\bкто\\b|\\bподскажите\\b|\\bесть у кого\\b|\\bделает\\w*\\b|\\bподписк\\w*\\b|\\bнастро\\w*\\b|\\bкупи\\w*\\b|\\bссылк\\w*\\b)"}]',
    '[]',
    'Включить практические вопросы',
    'Пропускает содержательные вопросы и запросы на источник, чтобы ответ рядом мог попасть в цепочку и классификацию.',
    'INCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE id = 2009
);
