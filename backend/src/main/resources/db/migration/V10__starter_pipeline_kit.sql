-- NeuroInfoGrinder V10: Starter prompts, rules and classifiers for AI/useful-content discovery

INSERT INTO prompts (name, type, version, content, variables_json, status, created_at, updated_at)
SELECT
    'Starter AI Classification Prompt',
    'CLASSIFICATION',
    '1.0',
    'You are classifying Telegram conversations for a knowledge extraction system.

Decide whether the message chain contains useful AI/LLM/dev-tool information worth turning into a guide or knowledge note.

Return strict JSON:
{
  "score": 0.0-1.0,
  "matched": true/false,
  "reasoning": "short explanation"
}

Mark as matched when at least one is true:
- actionable guidance, workaround, configuration, setup, debugging tip
- useful release/update information about AI tools or models
- pricing/access/credits/news that can help users act quickly
- concrete comparison of models, agents, prompts, APIs, automation tools

Do NOT match:
- pure jokes, greetings, reactions, short acknowledgements
- off-topic chat without useful information
- vague hype with no actionable detail

Use score >= 0.75 only when the content is clearly useful.',
    '[]',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM prompts WHERE name = 'Starter AI Classification Prompt'
);

INSERT INTO prompts (name, type, version, content, variables_json, status, created_at, updated_at)
SELECT
    'Starter Guide Generation Prompt',
    'GENERATION',
    '1.0',
    'You convert useful Telegram message chains into concise practical guides.

Return strict JSON:
{
  "title": "short title",
  "content": "plain text guide",
  "contentMarkdown": "# Markdown guide",
  "confidence": 0.0-1.0
}

Rules:
- keep only useful information
- preserve links, commands, tools, versions, prices, deadlines when present
- if this is a short but useful update/news item, write it as a short actionable note
- if this is a real how-to discussion, write it as a step-by-step guide
- remove fluff, banter, emojis and duplicate phrasing',
    '[]',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM prompts WHERE name = 'Starter Guide Generation Prompt'
);

INSERT INTO classifiers (name, type, provider_id, prompt_id, keywords, regex_pattern, version, status, created_at, updated_at)
SELECT
    'AI Useful Content Keywords',
    'KEYWORD',
    NULL,
    NULL,
    'gpt,claude,opus,sonnet,llm,ai,нейросеть,нейросети,нейронка,prompt,промпт,rag,agent,cursor,codex,openai,anthropic,api,model,модель,credits,free,бесплатно,халява,релиз,release,гайд,туториал',
    NULL,
    '1.0',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM classifiers WHERE name = 'AI Useful Content Keywords'
);

INSERT INTO classifiers (name, type, provider_id, prompt_id, keywords, regex_pattern, version, status, created_at, updated_at)
SELECT
    'AI Useful Content LLM',
    'LLM',
    (SELECT id FROM ai_providers WHERE status = 'ACTIVE' ORDER BY id LIMIT 1),
    (SELECT id FROM prompts WHERE name = 'Starter AI Classification Prompt' ORDER BY id LIMIT 1),
    NULL,
    NULL,
    '1.0',
    CASE
        WHEN EXISTS (SELECT 1 FROM ai_providers WHERE status = 'ACTIVE') THEN 'ACTIVE'
        ELSE 'DRAFT'
    END,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM classifiers WHERE name = 'AI Useful Content LLM'
);

INSERT INTO rules (rule_order, conditions_json, actions_json, name, description, action_type, status, created_at, updated_at)
SELECT
    10,
    '[{"type":"LENGTH_LT","value":"8"}]',
    '[]',
    'Exclude tiny noise',
    'Reject very short reactions that almost never contain useful guide content.',
    'EXCLUDE',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Exclude tiny noise'
);

INSERT INTO rules (rule_order, conditions_json, actions_json, name, description, action_type, status, created_at, updated_at)
SELECT
    20,
    '[{"type":"SENDER_IS_BOT","value":"true"},{"type":"LENGTH_LT","value":"20"}]',
    '[]',
    'Exclude short bot noise',
    'Reject short bot messages that do not carry useful content.',
    'EXCLUDE',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = 'Exclude short bot noise'
);
