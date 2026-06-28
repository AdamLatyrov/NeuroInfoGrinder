-- V40: Broaden usefulness scope beyond AI-only content.
-- Non-destructive seed tuning: updates prompts/rules/classifier names only.

UPDATE prompts
SET
    name = 'Классификация: общая полезность сообщения',
    version = '1.1',
    content = 'You are classifying Telegram conversations for a knowledge extraction system.

Decide whether the message chain contains generally useful information worth preserving as a guide, note, problem signal, or discussion insight. This is not only AI.

Useful content includes:
- practical problems and pain points;
- CRM, product feedback, onboarding, sales, support, and business-process discussions;
- workflow лайфхаки, concrete approaches, comparisons, and lessons learned;
- actionable questions and useful answers;
- bugs, limits, workarounds, sources, links, tools, providers, releases, and AI/model/payment topics.

Return strict JSON:
{
  "score": 0.0,
  "matched": false,
  "labels": ["PRACTICAL_PROBLEM"],
  "guide_candidate": false,
  "problem_signal_score": 0,
  "pain_score": 0,
  "willingness_to_pay_score": 0,
  "guide_potential_score": 0,
  "urgency_score": 0,
  "technical_depth_score": 0,
  "spam_score": 0,
  "meaning_summary": "Короткая суть сообщения",
  "problem_statement": "Какая проблема или боль выражена",
  "solution_hint": "Упомянутый подход, обходной путь или решение, если есть",
  "mentioned_tools": ["CRM"],
  "mentioned_prices": [],
  "mentioned_errors": [],
  "categories": ["workflow", "crm"],
  "evidence_message_ids": [123],
  "reasoning": "Короткое объяснение на русском"
}

Allowed labels only:
DEMAND_SIGNAL, SOLUTION_MENTION, VENDOR_OR_SOURCE, BUG_OR_LIMITATION,
PAYMENT_WORKAROUND, AI_TOOL_OR_PROVIDER, PRACTICAL_PROBLEM,
WORKFLOW_LIFEHACK, BUSINESS_PROCESS, PRODUCT_FEEDBACK, DISCUSSION_INSIGHT,
PRACTICAL_GUIDE_CANDIDATE, OPPORTUNITY, SPAM_OR_AD, NOT_USEFUL.

Mark matched=true when the chain contains a reusable problem, useful discussion, actionable question/answer, practical lesson, source, workaround, or guide-worthy content.
Use guide_candidate=true only when there is enough concrete information to generate a useful note/guide. A good problem discussion can be matched=true even without being a guide candidate.

Do NOT match:
- pure jokes, greetings, reactions, short acknowledgements;
- vague personal chat with no reusable problem, lesson, or practical detail;
- ads/scams unless they contain useful market/source information, and then label SPAM_OR_AD too.

Use score >= 0.75 only when the content is clearly useful. Preserve CRM/process/lifehack/problem discussions; AI is one useful category, not the only category.',
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'CLASSIFICATION'
  AND (
      name IN ('Starter AI Classification Prompt', 'Классификация: полезность сообщения', 'Классификация: общая полезность сообщения')
      OR content LIKE '%useful AI/LLM/dev-tool information%'
      OR content LIKE '%Preserve useful discussions about AI access%'
  );

UPDATE classifiers
SET
    name = 'LLM: общая полезность сообщений',
    updated_at = CURRENT_TIMESTAMP
WHERE name IN ('AI Useful Content LLM', 'LLM: полезность AI-контента', 'LLM: общая полезность сообщений');

UPDATE classifiers
SET
    name = 'Словарь полезных сигналов',
    keywords = 'gpt,claude,opus,sonnet,codex,cursor,openai,anthropic,api,model,модель,лимит,credits,free,халява,доступ,release,релиз,provider,endpoint,proxy,vpn,repo,github,crm,онбординг,обратная связь,лайфхак,процесс,воронка,клиент,пользователь,продажи,лид,заявка,продукт,проблема,боль,теряются,непонятно,неудобно,решение,разбор,схема,настройка,инструкция,гайд,источник,ссылка',
    updated_at = CURRENT_TIMESTAMP
WHERE name IN ('AI Useful Content Keywords', 'Словарь AI-сигналов', 'Словарь полезных сигналов');

UPDATE rules
SET
    conditions_json = '[{"type":"LENGTH_GT","value":"24"},{"type":"REGEX_MATCH","value":"(?iu)(\\?|\\bкак\\b|\\bгде\\b|\\bкто\\b|\\bподскажите\\b|\\bесть у кого\\b|\\bделает\\w*\\b|\\bподписк\\w*\\b|\\bнастро\\w*\\b|\\bкупи\\w*\\b|\\bссылк\\w*\\b|\\bcrm\\b|\\bонбординг\\w*\\b|\\bобратн\\w*\\s+связ\\w*\\b|\\bлайфхак\\w*\\b|\\bпроцесс\\w*\\b|\\bворонк\\w*\\b|\\bклиент\\w*\\b|\\bпользовател\\w*\\b|\\bпродаж\\w*\\b|\\bпроблем\\w*\\b|\\bтеря\\w*\\b|\\bнепонятн\\w*\\b|\\bрешал\\w*\\b)"}]',
    description = 'Пропускает содержательные вопросы, проблемы, CRM/process обсуждения, лайфхаки и запросы на источники в downstream scoring/classification.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2009;

UPDATE rules
SET
    conditions_json = '[{"type":"KEYWORD_MATCH","value":"gpt,claude,opus,sonnet,codex,cursor,openai,anthropic,api,model,модель,лимит,credits,free,халява,доступ,release,релиз,provider,endpoint,proxy,repo,github,crm,онбординг,обратная связь,лайфхак,процесс,воронка,клиент,пользователь,продажи,лид,заявка,продукт,проблема,боль,теряются,непонятно,неудобно,акк,акки,аккаунт,сайт,ссылка,ключ,провайдер,купить,покупать,где купить,где взять,кто нашел,кто знает,есть у кого"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (2005, 2008);
