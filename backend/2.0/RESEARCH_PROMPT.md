# Prompt for Backend 2.0 Research Agent

Ты исследовательско-архитектурный агент в проекте NeuroInfoGrinder.

Твоя задача: спроектировать полностью новую backend-систему 2.0 для классификации, кластеризации, дедупликации, обобщения и превращения Telegram-сообщений в полезные знания. Frontend пока остается как есть, поэтому backend 2.0 должен либо сохранить совместимость с текущими REST/SSE контрактами, либо явно описать compatibility facade/adapters.

Рабочая папка для новой backend-архитектуры:

`C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\backend\2.0`

## Обязательные правила

1. Сначала прочитай `AGENTS.md`.
2. Не печатай секреты, env, API keys, JWT secrets, Telegram secrets, provider keys, пароли.
3. Не выводи содержимое `/srv/neuroinfogrinder/app/.env`.
4. Не делай write-запросы в production DB.
5. Не трогай VPN/proxy сервер `95.164.93.173`, кроме read-only проверки, если она реально нужна.
6. Не деплой и не перезапускай production-сервисы.
7. Текущий backend можно читать как reference, но проектировать 2.0 как чистую систему.
8. Ответ и итоговые документы пиши по-русски.

## Контекст проекта

NeuroInfoGrinder читает Telegram-группы и должен находить в потоке сообщений полезные фрагменты:

- практические гайды;
- инструкции;
- решения проблем;
- настройки инструментов;
- сравнения моделей/сервисов;
- новости, если они помогают быстро принять решение;
- цены, лимиты, доступы, кредиты, тарифы;
- ссылки на ресурсы;
- предупреждения о рисках;
- цепочки вопрос-ответ, где ответ полезнее исходного вопроса.

Старая система уже имеет понятия:

- messages;
- groups/topics;
- rules;
- classifiers;
- prompts;
- guides;
- pipeline traces;
- tuning cases;
- guide generation.

Но система переписывается. Нужно предложить новый pipeline, который лучше работает с реальной природой Telegram-чата: шум, реакции, оффтоп, дубли, рекламные посты, неполные фрагменты, полезные ответы внутри мусорной цепочки, спорные новости, токсичные или рискованные намерения.

## Что обязательно изучить

Прочитай минимум:

- `README.md`
- `docs/NIGHTLY_REALTIME_PIPELINE_PLAN.md`
- `docs/pipeline-tuning-observability.md`
- `docs/backend-rest-api-spec.md`
- `docs/admin-site-design.md`
- `backend/src/main/resources/db/migration/V10__starter_pipeline_kit.sql`
- `reports/latest-guides-full-20260620.md`
- текущий код вокруг `messages`, `findings`, `classifiers`, `guides`, `pipeline traces`
- `legacy-mvp`, если там есть старая логика кластеризации или извлечения

## Главный результат

Нужен не обзор и не “идея”, а implementable research/spec для backend 2.0:

1. новая архитектура pipeline;
2. алгоритм real-time обработки;
3. алгоритм nightly/batch кластеризации;
4. taxonomy полезности;
5. признаки и scoring;
6. JSON-схемы;
7. LLM prompts для классификации и генерации;
8. стратегия дедупликации;
9. стратегия human review/feedback loop;
10. совместимость с текущим frontend;
11. схема данных backend 2.0;
12. план внедрения по фазам.

## Что считать полезностью

Сообщение, цепочка или кластер полезны, если содержат хотя бы часть этих свойств:

- actionable: после чтения можно что-то сделать;
- reusable: знание пригодится позже, не только сейчас;
- specific: есть детали, названия, команды, ссылки, версии, цены, лимиты, даты;
- evidence-backed: есть ссылка, опыт, лог, проверка, source или контекст;
- knowledge-dense: полезного больше, чем болтовни;
- time-sensitive: важная новость, скидка, лимит, релиз, deadline;
- problem-solving: есть проблема, причина, решение, workaround или диагностический шаг;
- comparative: помогает выбрать между моделями, провайдерами, инструментами, тарифами;
- risk-aware: предупреждает о блокировках, скаме, нестабильности, нарушении ToS, потере денег или данных.

## Taxonomy классов

Предложи и уточни taxonomy. Стартовый набор:

- `HOW_TO_GUIDE`
- `TROUBLESHOOTING_FIX`
- `TOOL_OR_MODEL_RELEASE`
- `PRICING_OR_ACCESS_SIGNAL`
- `COMPARISON_OR_BENCHMARK`
- `PROMPT_OR_AGENT_PATTERN`
- `API_OR_CONFIG_SNIPPET`
- `WORKFLOW_AUTOMATION`
- `SECURITY_OR_RISK_WARNING`
- `MARKET_OR_ECOSYSTEM_SIGNAL`
- `RESOURCE_LINK_COLLECTION`
- `QUESTION_WITH_VALUABLE_ANSWER`
- `ARCHITECTURE_DECISION`
- `ERROR_LOG_WITH_FIX`
- `RAW_NEWS_LOW_ACTIONABILITY`
- `PROMO_WITH_USEFUL_DETAILS`
- `NOISE_OR_CHAT`
- `DUPLICATE_OR_NEAR_DUPLICATE`
- `UNSUPPORTED_HYPE`
- `UNSAFE_OR_POLICY_RISK`

Для каждого класса опиши:

- признаки;
- positive examples;
- negative examples;
- recommended artifact type;
- когда auto-generate;
- когда manual review;
- когда skip.

## Реальные примеры из текущих данных

Ниже не финальный датасет, а seed examples. Используй `reports/latest-guides-full-20260620.md`, чтобы расширить и проверить их.

### Example A: одиночное архитектурное сообщение

Input:

```text
Я изначально начинал делать на CLI, потом выделял ядро, делал в вебе. Взял за основу Lovable, скачивал проект, приводил в нормальный вид. Плюсы веба в том, что библиотек миллиард и что угодно можно найти готовое, но минусы в интерпретаторе/мосту. WPF взял как быстрый UI, Avalonia рассматривал, но отложил до готового API.
```

Expected:

- useful: yes;
- class: `ARCHITECTURE_DECISION`;
- artifact: `GUIDE` или `NOTE`;
- reason: содержит опыт выбора UI-стека, trade-offs, последовательность CLI -> core -> web/WPF/Avalonia;
- cluster behavior: может быть singleton guide, если сообщение плотное.

### Example B: пошаговый how-to с командами

Input:

```text
Гайд на Opencode в кармане Android. Качаем Termux, открываем и вводим:
apt update && apt upgrade -y
apt install glibc-repo -y
apt install upstream -y
```

Expected:

- useful: yes;
- class: `HOW_TO_GUIDE` + `API_OR_CONFIG_SNIPPET`;
- artifact: `GUIDE`;
- extract commands exactly;
- add risk note;
- remove jokes/опасные фразы;
- preserve platform: Android/Termux.

### Example C: вопрос-ответ, где полезен ответ

Input chain:

```text
User A: Из Gemini AI Pro можно API тянуть?
User B: Лимиты сильно кастрированные, аккаунты могут улетать в бан или сниматься Pro. Не сильно рациональное занятие.
```

Expected:

- useful: yes;
- class: `QUESTION_WITH_VALUABLE_ANSWER` + `PRICING_OR_ACCESS_SIGNAL` + `SECURITY_OR_RISK_WARNING`;
- artifact: `NOTE`;
- score вопроса отдельно невысокий, но цепочки высокий;
- evidence message ids должны включать вопрос и ответ;
- вывод: “не опираться как на стабильный production path”.

### Example D: рекламно-полезный пост с тарифами и ссылками

Input shape:

```text
Пост про API-провайдера/прокси. Есть регистрация, пополнение, создание API-ключа, тарифы GPT/Claude, коэффициенты расхода, Copilot Kit/upstream configs, много реферальных ссылок и повторов.
```

Expected:

- useful: yes, but with caution;
- class: `PROMO_WITH_USEFUL_DETAILS` + `PRICING_OR_ACCESS_SIGNAL` + `RESOURCE_LINK_COLLECTION`;
- artifact: `NOTE` или `RESOURCE_CARD`, не безусловный guide;
- remove repeated referral spam;
- preserve domains, тарифы, coefficients, payment method, dates;
- add risk note: third-party provider/proxy, ToS, privacy, balance risk;
- dedupe by domain/provider and repeated link bodies.

### Example E: тонкий model signal

Input:

```text
GLM 5.2 хорошо пишет код.
```

Expected:

- useful: borderline;
- class: `TOOL_OR_MODEL_RELEASE` или `COMPARISON_OR_BENCHMARK`, но confidence низкий;
- artifact: `ACCUMULATE`, не сразу guide;
- нужно дождаться кластера с источником, ссылкой, тестами, config или сравнением;
- singleton guide создавать только если есть политика “короткие сигналы сохранять как заметку”.

### Example F: рискованный intent и безопасный ответ

Input chain:

```text
User A: какую модель лучше юзать для чернухи?
User B: никакую, фильтры стоят, с этим API никакую.
```

Expected:

- useful: limited;
- class: `UNSAFE_OR_POLICY_RISK` + `SECURITY_OR_RISK_WARNING`;
- artifact: usually `SKIP` или `RISK_NOTE`, не how-to;
- нельзя генерировать инструкцию обхода фильтров;
- можно сохранить безопасный вывод: “не использовать API для запрещенных сценариев, учитывать фильтры и ToS”.

### Example G: raw news with low actionability

Input:

```text
upstream нанимает известного инженера из Google и бывшего советника по ИИ перед IPO.
```

Expected:

- useful: maybe;
- class: `MARKET_OR_ECOSYSTEM_SIGNAL` или `RAW_NEWS_LOW_ACTIONABILITY`;
- artifact: `NEWS_CARD`;
- no guide unless есть явное действие: кого это затрагивает, что проверить, почему важно;
- score ниже, если нет источника/ссылки/практического вывода.

### Example H: troubleshooting fix with command

Input:

```text
Файл ~/.upstream/logs_2.sqlite быстро растет из-за TRACE-логов. Временное решение:
sqlite3 ~/.upstream/logs_2.sqlite "CREATE TRIGGER IF NOT EXISTS block_log_inserts BEFORE INSERT ON logs BEGIN SELECT RAISE(IGNORE); END;"
Контекст: github issue.
```

Expected:

- useful: yes;
- class: `TROUBLESHOOTING_FIX` + `API_OR_CONFIG_SNIPPET`;
- artifact: `GUIDE`;
- preserve command exactly;
- mark as temporary workaround;
- include rollback/caution;
- source link важен.

### Example I: noisy cluster with one useful answer

Input chain shape:

```text
Много шуток, конфликт, реакции, картинки, затем вопрос:
"Где сейчас можно протестировать GLM-5.2?"
Ответ:
"Официальный API open.bigmodel.cn или агрегаторы вроде SiliconFlow; проверь playground/key; учитывай региональные ограничения."
```

Expected:

- useful part: yes;
- cluster should not summarize the conflict;
- class: `QUESTION_WITH_VALUABLE_ANSWER` + `TOOL_OR_MODEL_RELEASE`;
- artifact: `NOTE`;
- evidence ids should point to useful question/answer only;
- noisy messages become context/noise, not sources.

### Example J: error log without explicit fix

Input:

```text
API Error: 410 Model "kimi-k2.5" is no longer available. Use "kimi-k2.6" instead.
API Error: 402 provider for model kimi-k2.6 has exhausted credits.
```

Expected:

- useful: yes as diagnostic note;
- class: `ERROR_LOG_WITH_FIX`;
- artifact: `NOTE`;
- extract old model, replacement model, credit exhaustion cause;
- no over-generation: if no fix beyond "switch model/check credits", keep concise.

### Example K: pure reactions/noise

Input:

```text
ахахах
кайфуем
о и ты тут привет
Класс
emoji-only/image-only
```

Expected:

- useful: no;
- class: `NOISE_OR_CHAT`;
- artifact: `SKIP`;
- but if reaction confirms a fix worked, it may become support evidence, not standalone useful content.

### Example L: duplicate/near-duplicate promo

Input shape:

```text
Один и тот же referral/provider текст повторяется несколько раз, иногда с немного измененными ссылками, тарифами или emphatic formatting.
```

Expected:

- classify as duplicate cluster;
- preserve only freshest/best/most complete version;
- compare extracted facts, not raw text only;
- if tariffs differ, mark conflict and require review.

## Pipeline 2.0: что нужно спроектировать

Предложи два режима:

1. Real-time pipeline: быстро обрабатывает новое сообщение и дает UI состояние.
2. Nightly/batch pipeline: переосмысливает накопленные сообщения, строит кластеры, объединяет дубли, находит пропущенные полезности.

### Real-time pipeline

Опиши:

1. ingest;
2. normalization;
3. entity/link/code extraction;
4. message intelligence;
5. cheap gates;
6. chain reconstruction;
7. local scoring;
8. candidate neighborhood search;
9. LLM judge only for promising/borderline cases;
10. artifact routing;
11. guide/note generation;
12. trace/events for frontend.

Требования:

- новое сообщение должно появляться в UI быстро;
- дорогой LLM не должен вызываться на очевидный мусор;
- короткое сообщение со ссылкой/командой/ценой/версией нельзя выкидывать только по длине;
- одиночные плотные сообщения можно превращать в заметку/гайд;
- неполные сообщения надо копить до кластера.

### Nightly/batch pipeline

Опиши:

1. загрузку окна сообщений;
2. recompute message intelligence;
3. embeddings;
4. entity-aware clustering;
5. reply graph clustering;
6. temporal clustering;
7. URL/domain clustering;
8. dedupe;
9. cluster LLM classification;
10. cluster artifact generation;
11. conflict detection;
12. review queue.

Кластеризация не должна полагаться только на embeddings. Комбинируй:

- reply graph;
- topic/thread;
- time window;
- shared URLs/domains;
- shared tools/models/entities;
- semantic embeddings;
- near-duplicate hashing;
- author/source credibility;
- message role: question/answer/correction/experience/log/promo.

## Scoring

Предложи формулу:

```text
usefulnessScore =
  0.18 * actionability
+ 0.14 * specificity
+ 0.12 * evidence
+ 0.12 * problemSolutionStructure
+ 0.10 * toolModelRelevance
+ 0.08 * sourceLinkQuality
+ 0.08 * novelty
+ 0.07 * discussionSupport
+ 0.06 * timeSensitivity
+ 0.05 * authorSignal
- 0.18 * spamScore
- 0.14 * pureReactionScore
- 0.12 * unsupportedHypeScore
- 0.10 * duplicatePenalty
- 0.10 * unsafeInstructionPenalty
```

Это стартовая формула. Проверь, раскритикуй, предложи лучше.

Стартовые thresholds:

- `< 0.25`: auto skip;
- `0.25-0.45`: accumulate only;
- `0.45-0.65`: cheap cluster/review later;
- `0.65-0.80`: LLM judge;
- `> 0.80`: generate if low-risk, иначе manual review.

Обязательно предложи отдельные thresholds для:

- singleton dense messages;
- Q/A chains;
- promo posts;
- news;
- unsafe/risky content;
- command/code snippets;
- duplicate clusters.

## JSON-схемы

Опиши строгие JSON-схемы:

- `messageIntelligenceJson`
- `messageCandidateJson`
- `chainContextJson`
- `clusterCandidateJson`
- `classifierResultJson`
- `dedupeResultJson`
- `guideGenerationInputJson`
- `guideGenerationOutputJson`
- `feedbackEventJson`
- `pipelineTraceJson`

Схемы должны быть пригодны для Java/Kotlin/TypeScript DTO.

## LLM classifier prompt

Составь production-ready prompt для LLM-классификатора.

Требования:

- strict JSON only;
- не выдумывать;
- evidence message ids mandatory;
- отличать полезность от хайпа;
- не превращать unsafe intent в инструкцию;
- уметь вернуть `ACCUMULATE`, если контекста мало;
- уметь вернуть `REVIEW`, если есть риск/конфликт;
- уметь вернуть `SKIP`, если шум;
- учитывать примеры A-L выше.

Ожидаемые поля:

```json
{
  "decision": "GENERATE|ACCUMULATE|REVIEW|SKIP",
  "matched": true,
  "score": 0.0,
  "confidence": 0.0,
  "usefulnessClass": "HOW_TO_GUIDE",
  "artifactType": "GUIDE|NOTE|NEWS_CARD|RESOURCE_CARD|RISK_NOTE|NONE",
  "titleCandidate": "",
  "summary": "",
  "reasoning": "",
  "evidenceMessageIds": [],
  "contextMessageIds": [],
  "noiseMessageIds": [],
  "missingContext": [],
  "riskFlags": [],
  "dedupeKey": "",
  "shouldWaitForMoreContext": false
}
```

## LLM generation prompt

Составь production-ready prompt для генератора.

Требования:

- strict JSON only;
- разные форматы для guide/note/news/resource/risk;
- сохранять команды, ссылки, версии, цены, даты;
- убирать шум, мат, реакции, повторы;
- не добавлять несуществующие шаги;
- если source слабый, писать короткую заметку, не “железный гайд”;
- unsafe content переводить в безопасную риск-заметку;
- отмечать confidence и missing facts.

## Feedback loop

Спроектируй feedback events:

- useful/not useful;
- wrong class;
- duplicate;
- too noisy;
- missed useful content;
- bad source selection;
- hallucinated detail;
- unsafe output;
- generated as wrong artifact type;
- merge clusters;
- split cluster.

Опиши, как feedback превращается в:

- tuning dataset;
- prompt revision candidates;
- thresholds tuning;
- classifier regression tests;
- nightly audit reports.

## Compatibility with current frontend

Frontend остается как есть. Поэтому:

1. Определи текущие endpoints из `docs/backend-rest-api-spec.md`.
2. Предложи слой совместимости для:
   - groups/messages;
   - message chain;
   - enqueue;
   - guides list/detail;
   - pipeline queue/events;
   - traces/tuning cases;
   - providers/settings.
3. Если новый backend 2.0 меняет domain model, опиши adapter DTO.
4. Не требуй переписывать frontend на первом этапе.

## Data model 2.0

Предложи новую схему данных:

- raw_messages;
- normalized_messages;
- message_intelligence;
- message_links;
- message_entities;
- message_chains;
- clusters;
- cluster_members;
- cluster_facts;
- knowledge_candidates;
- generated_artifacts;
- artifact_sources;
- classifier_runs;
- generation_runs;
- feedback_events;
- pipeline_traces;
- provider_calls;
- prompt_versions.

Для каждой сущности опиши:

- назначение;
- ключевые поля;
- индексы;
- retention;
- как она мапится на старые frontend DTO.

## Evaluation

Предложи метрики:

- precision@k по найденным полезностям;
- recall на размеченном наборе;
- false positive rate;
- false negative audit;
- duplicate rate;
- guide conversion rate;
- cost per useful guide;
- LLM calls per 1000 messages;
- real-time latency;
- nightly cluster yield;
- percentage of useful artifacts found only by batch;
- unsafe/risk handling accuracy;
- source attribution accuracy;
- hallucination rate in generated artifacts.

Составь annotation guide:

- как размечать одиночные сообщения;
- как размечать цепочки;
- как размечать кластеры;
- как отличать новость от полезной новости;
- как отличать хайп от actionable insight;
- как размечать promo-with-details;
- как размечать unsafe/risky content;
- как выбирать evidence messages;
- как помечать дубли и конфликтующие факты.

## Итоговый формат ответа

Верни результат как архитектурный документ для backend 2.0:

1. Executive summary.
2. Что не так с текущим подходом.
3. Цели backend 2.0.
4. Архитектура pipeline.
5. Real-time алгоритм.
6. Nightly алгоритм.
7. Taxonomy полезности.
8. Примеры A-L и как система должна их обработать.
9. Scoring и thresholds.
10. JSON-схемы.
11. LLM classifier prompt.
12. LLM generation prompt.
13. Data model.
14. API/compatibility layer для текущего frontend.
15. Observability/traces.
16. Feedback loop.
17. Evaluation plan.
18. Implementation roadmap:
    - 1 день;
    - 3 дня;
    - 1 неделя;
    - 2-3 недели.
19. Риски и mitigations.
20. Список конкретных задач для backend 2.0.

Пиши конкретно. Не используй общие фразы вроде “улучшить качество классификации” без механизма, метрики и места в pipeline.
