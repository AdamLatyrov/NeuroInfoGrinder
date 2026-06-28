-- V29: Minimal rule tuning from classification audit.
-- Keep this seed deterministic and narrow: improve technical/how-to recall without
-- mass-rewriting rule execution or deleting existing rule behavior.

UPDATE rules
SET conditions_json = '[{"type":"KEYWORD_MATCH","value":"gpt,claude,opus,sonnet,codex,cursor,openai,anthropic,api,model,модель,лимит,credits,free,халява,доступ,release,релиз,provider,endpoint,proxy,vpn,cloudflare worker,api endpoint,repo,github,agent sdk,second brain,claude code,model limit,pricing,payment workaround,акк,акки,аккаунт,аккаунты,сайт,ссылка,ключ,провайдер,эндпоинт,прокси,купить,покупать,где купить,где взять,кто нашел,кто знает,есть у кого"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2005;

UPDATE rules
SET conditions_json = '[{"type":"KEYWORD_MATCH","value":"guide,гайд,tutorial,туториал,how to,how-to,как сделать,инструкция,шаг,prompt,промпт,setup,install,настроить,настройка,подключить,запусти,решение,схема,разбор,.clinerules,readme,cockpit,stack ai,mythos"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2006;

INSERT INTO rules (
    id, created_at, updated_at, rule_order, conditions_json, actions_json,
    name, description, action_type, status
)
SELECT
    2010,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    31,
    '[{"type":"LENGTH_GT","value":"24"},{"type":"REGEX_MATCH","value":"(?iu)(cloudflare\\s+worker|proxy|vpn|api\\s+endpoint|github|repo|codex|claude\\s+code|agent\\s+sdk|second\\s+brain|model\\s+limit|setup|pricing|payment\\s+workaround|\\.clinerules|readme|cockpit|stack\\s+ai|mythos|инструкц|настро|запусти|решени|гайд|прокси|оплат|лимит)"}]',
    '[]',
    'Включить технические how-to сигналы',
    'Пропускает сообщения с конкретными техническими маркерами из аудита: proxy, VPN, API endpoint, GitHub repo, Codex, Claude Code, Agent SDK, Second Brain, setup/pricing/payment workaround.',
    'INCLUDE',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE id = 2010
);
