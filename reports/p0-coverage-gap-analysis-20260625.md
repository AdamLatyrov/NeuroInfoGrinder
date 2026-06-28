# P0 Pipeline Coverage Gap Analysis

Generated: 20260625

Scope: production clean DB, enabled `telegram_chats.is_enabled=true` raw messages only. Read-only diagnostics. No requeue, no reprocess, no threshold changes, no material generation.

## Audit Baseline

| Metric | Value |
|---|---:|
| Enabled raw total | 3558 |
| Potentially useful | 424 |
| Guide-ready | 107 |
| Context/discussion-needed | 173 |
| Already materialized | 11 |
| Useful not materialized | 416 |
| Estimated recall | 1.9% |
| Estimated precision | 81.8% |

## Overall Coverage Reasons

| reason | count |
|---|---|
| suppressed_by_scope_no_explicit_auto_setting | 995 |
| intake_pending_not_queued | 993 |
| replay_message_exists_but_no_embedding | 642 |
| run_created_but_no_replay_messages | 399 |
| text_extraction_empty_or_no_text | 295 |
| single_message_rejected_TOO_SHORT | 101 |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 83 |
| no_material_candidates | 21 |
| materialized | 11 |
| candidate_without_material | 10 |
| queue_status_processed | 6 |
| single_message_rejected_LOW_SIGNAL | 2 |

## INTAKE_PENDING Breakdown

| reason | count |
|---|---|
| intake_pending_not_queued | 993 |
| suppressed_by_scope_no_explicit_auto_setting | 953 |

## Useful Candidate Breakdown

| reason | count |
|---|---|
| suppressed_by_scope_no_explicit_auto_setting | 212 |
| intake_pending_not_queued | 124 |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 31 |
| replay_message_exists_but_no_embedding | 15 |
| run_created_but_no_replay_messages | 14 |
| materialized | 8 |
| candidate_without_material | 8 |
| no_material_candidates | 6 |
| single_message_rejected_TOO_SHORT | 4 |
| queue_status_processed | 2 |

## Reason Examples

| reason | raw_id | chat | class | score | preview |
|---|---|---|---|---|---|
| suppressed_by_scope_no_explicit_auto_setting | 395 | ОМ: Полезное | TROUBLESHOOTING | 100 | Рост из айтишника в предпринимателя / Вот тебе пальто, носи и мечтай о великом Как эго мешает программисту двигаться вперед, почему все таки придется дрочить со |
| suppressed_by_scope_no_explicit_auto_setting | 422 | ОМ: Полезное | GUIDE_READY | 100 | Дайджест сообщества за апрель 📱 Ролики - Выпуск №3 для олдов: Сообщество, бабки, пострадавшие от Ульянова - МОК-интервью по System Design / Frontend-разработчик |
| suppressed_by_scope_no_explicit_auto_setting | 424 | ОМ: Полезное | PROMPT_OR_TEMPLATE | 100 | Как наладить жизнь до 30 (отчет миллионера) / Презираю бесцельных людей (скидка 15%) В настолке попался вопрос: "каких людей ты презираешь?". Я эмпат, поэтому н |
| intake_pending_not_queued | 305 | founderStack / Общение, знакомства | GUIDE_READY | 100 | 📝 ⚖️ Юрисдикции, договоры и спорные сделки Обсуждали, как международные нормы по интеллектуальной собственности и банкротству работают на практике: где можно су |
| intake_pending_not_queued | 2222 | ОМ: Резюме | GUIDE_READY | 100 | 1) "Готов к редким командировкам" заменить на "готов к командировкам", режет конверсию. Лучше отказаться от оффера если условия подходить не будут, чем HR не по |
| intake_pending_not_queued | 2194 | ОМ: Резюме | GUIDE_READY | 100 | 9) Достижения в опыте работы оформить согласно правилам: 9.1) Вообще убрать то что относится к штатным обязанностям по твоей профессии (и так понятно что точно  |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 4205 | Vibe GIG Мастерская | GUIDE_READY | 100 | Черный рынок токенов развернули в Китае — местные покупают доступы к запрещенной в стране Сlaude на 93% дешевле, чем юзеры по всему миру! Темщики Поднебесной ис |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 2735 | Паша | GUIDE_READY | 100 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом пр |
| single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | 2793 | Паша | GUIDE_READY | 100 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом пр |
| replay_message_exists_but_no_embedding | 4025 | Vibe Dev | TROUBLESHOOTING | 85 | у них на сайте есть примеры промптов по каждому скиллу, типа Rewrite this feature description to be more concrete and unexpected using made-to-stick skill идея  |
| replay_message_exists_but_no_embedding | 2233 | ОМ: Резюме | GUIDE_READY | 82 | 2) Если смотреть на вышку в целом то ее не обязательно прямо подтверждать. Ты просто указываешь что она есть чтобы проходить фильтры а дальше при оформлении дип |
| replay_message_exists_but_no_embedding | 3084 | Vibemode | USEFUL_SIGNAL | 64 | у нас сменились эндпоинты пожалуйста сначала проверьте свои конфиги на актуальность, старый лк и эндпоинт больше не работают |
| run_created_but_no_replay_messages | 2254 | founderStack / Общение, знакомства | GUIDE_READY | 100 | 📝 🤔 Вайбкодинг, деньги и смысл работы Обсуждали, что мотивирует людей заниматься продуктами и кодом: деньги, интерес к созданию нового, польза, известность или  |
| run_created_but_no_replay_messages | 2178 | Vibemode | TOOL_OR_RELEASE | 58 | а подскажите по такому моменту, у меня тогда как-то забился контекст, мне тут сказали, что надо уменьшить, и мне нейронка уменьшила, но чет вообще жопа стала. с |
| run_created_but_no_replay_messages | 1974 | Vibe Dev | USEFUL_SIGNAL | 56 | ~/.claude/settings.json { "env": { "ANTHROPIC_BASE_URL": "https://byesu.com", "ANTHROPIC_AUTH_TOKEN": "sk-ключ", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "1" |
| materialized | 2947 | Нейродвиж | GUIDE_READY | 100 | Бустим свою ЗП за один промт — нашли подсказку, которая поможет аргументированно получить прибавку к зарплате. Логика простая: ChatGPT расспросит вас о ваших до |
| materialized | 2902 | Паша | GUIDE_READY | 100 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом пр |
| materialized | 2926 | Нейродвиж | TROUBLESHOOTING | 69 | Нашли 7 рабочих промтов для ChatGPT, которые помогут разобраться в себе и найти причину беспокойства. Не замена врачам, но полезно: Ищем проблему: Спроси меня н |
| candidate_without_material | 3254 | Miloslavski | GUIDE_READY | 100 | С небольшим опозданием, но все же, открываем поток на Июльский набор обучения. Цены стандартные : Групповое : 150 000. Личное : 350 000. Но! Первые 10 "групповы |
| candidate_without_material | 4422 | ㅤЗаур | GUIDE_READY | 100 | StageWise \| Opus 4.8, GPT 5.5, GLM 5.2 \| IDE - Скачать софт - https://github.com/Asati-Privatka/stagewise-account-manager у кого не открывается ссылка на гитхаб |
| candidate_without_material | 2843 | Vibemode | GUIDE_READY | 100 | Ребят, если в Factory/Droid ловите BYOK 503 “No provider account is currently available”, скорее всего проблема не в ключах. Поменялись endpoint’ы Vibemode, клю |
| no_material_candidates | 2619 | Vibe Dev | GUIDE_READY | 86 | Deimos, коллеги тут в меру упоротом холиваре про доступность Claude Fable 5. Суть спора: - 👌 утверждает, что модель недоступна вообще нигде, включая США — прави |
| no_material_candidates | 2625 | Vibe Dev | TROUBLESHOOTING | 67 | Да классика же. Когда нет живого пруфа, а есть только «а вот заявлялось» против «а вот щас не работает» — начинается цирк с конями. Оба правы по-своему, но моде |
| no_material_candidates | 2655 | Vibe Dev | USEFUL_SIGNAL | 57 | Ну они заагрились на технику безопасности антропиков, а антропики вроде базируются в США, правительство США сказало закрыть, они закрыли без спора, сейчас споря |
| single_message_rejected_TOO_SHORT | 2789 | Vibemode | DISCUSSION_SEGMENT | 39 | новая старая одно и тоже не работает да ) конфиг новый с новым адресом |
| single_message_rejected_TOO_SHORT | 2976 | Vibemode | DISCUSSION_SEGMENT | 39 | Есть небольшие проблемы с большими сессиями на r-api, правим |
| single_message_rejected_TOO_SHORT | 2988 | Vibemode | DISCUSSION_SEGMENT | 39 | проблема с r-api была исправлена |
| queue_status_processed | 2609 | Паша | GUIDE_READY | 100 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом пр |
| queue_status_processed | 48 | Vibecoder Chat [Public] | TROUBLESHOOTING | 77 | Нейросеть Codex оказалась способна «убить» SSD меньше чем за год Разработчик под ником 1996fanrui рассказал о неожиданной проблеме в работе консольного инструме |

## Top Chat Coverage

| chat | chat_id | activeDialog | explicitAuto | processingState | enabledRaw | intakePending | withEmbeddings | withMaterials | latestRaw | missedUseful |
|---|---|---|---|---|---|---|---|---|---|---|
| ОМ: Полезное | -1001750589044 | True | False | DISPLAY_ONLY_AUTO_DISABLED | 62 | 43 | 0 | 0 | 2026-06-25T14:28:43+00:00 | 41 |
| Vibecoder Chat [Public] | -1002922797592 | True | True | ENABLED_PROCESSABLE | 239 | 163 | 19 | 1 | 2026-06-25T14:22:36+00:00 | 38 |
| Vibemode | -1003919536687 | True | True | ENABLED_PROCESSABLE | 446 | 159 | 71 | 0 | 2026-06-25T14:32:19+00:00 | 38 |
| ОМ: Резюме | -1001594483330 | True | True | ENABLED_PROCESSABLE | 134 | 64 | 34 | 5 | 2026-06-25T14:30:45+00:00 | 36 |
| Vibe GIG Мастерская | -1003922856266 | True | True | ENABLED_PROCESSABLE | 303 | 207 | 14 | 0 | 2026-06-25T12:39:28+00:00 | 30 |
| Нейродвиж | -1001603435168 | True | False | DISPLAY_ONLY_AUTO_DISABLED | 50 | 0 | 18 | 2 | 2026-06-24T16:52:47+00:00 | 26 |
| Vibe Dev | -1003854867646 | True | True | ENABLED_PROCESSABLE | 680 | 200 | 44 | 2 | 2026-06-25T14:27:06+00:00 | 22 |
| founderStack / Общение, знакомства | -1003695549430 | True | True | ENABLED_PROCESSABLE | 293 | 187 | 6 | 0 | 2026-06-25T06:08:28+00:00 | 19 |
| ОМ: Флудилка | -1001750348316 | True | False | DISPLAY_ONLY_AUTO_DISABLED | 125 | 119 | 0 | 0 | 2026-06-25T13:05:50+00:00 | 17 |
| API SUPPORT \| ModelHub | -1003898985313 | True | False | DISPLAY_ONLY_AUTO_DISABLED | 176 | 150 | 0 | 0 | 2026-06-25T14:30:39+00:00 | 12 |

## Required Raw-ID Drilldown

| raw_id | chat | class | score | coverage_reason | status | run_id | embeddings | final_decision | single_score | rejection | provider_calls | materials | preview |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2609 | Паша | GUIDE_READY | 100 | queue_status_processed | already_materialized_duplicate | 164 | 1 | CANDIDATE |  |  | 0 | 0 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом проверь, что ключ передаётся как Bearer token, модель указана ровно тем именем, ко |
| 2735 | Паша | GUIDE_READY | 100 | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | already_materialized_duplicate | 202 | 1 | REJECTED_SINGLE_MESSAGE | 0.3107 | LOW_SINGLE_MESSAGE_SCORE | 0 | 0 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом проверь, что ключ передаётся как Bearer token, модель указана ровно тем именем, ко |
| 2793 | Паша | GUIDE_READY | 100 | single_message_rejected_LOW_SINGLE_MESSAGE_SCORE | already_materialized_duplicate | 215 | 1 | REJECTED_SINGLE_MESSAGE | 0.3107 | LOW_SINGLE_MESSAGE_SCORE | 0 | 0 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом проверь, что ключ передаётся как Bearer token, модель указана ровно тем именем, ко |
| 2866 | Паша | GUIDE_READY | 100 | candidate_without_material | already_materialized_duplicate | 239 | 1 | SINGLE_MESSAGE_MATERIAL_CANDIDATE | 1.0 |  | 2 | 0 | Мини-гайд по проверке API в Cursor: если внешний OpenAI-compatible endpoint не подключается, сначала проверь base URL — он должен заканчиваться на /v1. Потом проверь, что ключ передаётся как Bearer token, модель указана ровно тем именем, ко |

## Discussion Segment Data Notes

| metric | value |
|---|---|
| context_candidate_messages | 173 |
| context_chain_count | 75 |
| median_chain_length | 1 |
| max_chain_length | 15 |

## Top Context Chains

| chat | topic | thread | candidate_count | example_raw | preview |
|---|---|---|---|---|---|
| Vibemode | 1302 | 1302 | 15 | 968 | Новый чат запустил, на старом тоже 413 Короче похоже 4 чата параллельно - потолок Пользуюсь https://r-portal.vibemod.pro/client |
| Vibe Dev | 4 | 4 | 15 | 1092 | А давно плюс акками невозможно пользоваться блять? 5 часов за запрос кушает, причем там просто фикс ебанный |
| Local chat 999999000001 |  |  | 11 | 590 | При попытке вызвать model.encode() появляется CUDA out of memory. Уменьшите batch_size до 8 или используйте CPU fallback. |
| ОМ: Резюме | 11429 | 11429 | 10 | 996 | Всем привет! У меня есть резюме на голанг, но вижу с него слабую конверсию. Ищу ментора, с которым могу разобрать резюме и сделать его сильнее. Подскажите, в то |
| Vibe GIG Мастерская | 142 | 142 | 8 | 667 | есть у кого промпт на аквариум с рыбками? |
| Vibemode | 1292 | 1292 | 7 | 1636 | с инструкциями еще ковыряемся |
| Vibe Dev | 33608 | 33608 | 6 | 123 | Нейрогейт рефка Такой ни у кого нет, 5 баксов за пополнение сверху дает🥰 Нейрогейт если глм релиз сделает то будет пиздец имба |
| ОМ: Бэкенд | 1 | 1 | 5 | 266 | Добро пожаловать в стаю, @strekok! Помощник ОМ — это твой путеводитель по сообществу. Внутри ты найдешь: - базу знаний - список всех доступных чатов и ресурсов  |
| ОМ: Резюме | 11582 | 11582 | 5 | 2216 | базовое правило: если есть пути вката без накрутки то не накручивай (гарантированные стажировки, нетворкинг, рефералки и т.д.) Если нет таких вариантов, то без  |
| Vibecoder Chat [Public] | 1 | 1 | 4 | 946 | Дмитрий Волков, сначала ознакомьтесь с правилами и нажмите "Я соглашаюсь". |

## Root-Cause Interpretation

- `intake_pending_not_queued` means raw and intake exist, but no `auto_pipeline_queue` row exists. This is a coverage gap before batch/run creation, not a scoring problem.
- `suppressed_by_scope_no_explicit_auto_setting` means the chat is display-enabled but not processable under the current P0 guard because explicit auto scope is absent.
- `single_message_rejected_LOW_SINGLE_MESSAGE_SCORE` means the message reached replay, embeddings, and single-message detection, but failed candidate threshold.
- `embedding_exists_but_no_single_message_decision` points to old/pre-fix or incomplete replay evidence where embeddings exist but no single-message decision was recorded.
- `candidate_without_material` means local candidate exists but provider/material stage did not complete.

## Final P0 Findings

| Finding | Disposition |
|---|---|
| Current live path after SOCKS5 restore | Working for processable chats. Fresh rows after `2026-06-25T12:38Z` are moving through intake, queue, batch, run, replay, and single-message decision. |
| Main `INTAKE_PENDING` cause | Mixed backlog/scope issue, not a current live-ingest bug. Pending rows split between `intake_pending_not_queued` and `suppressed_by_scope_no_explicit_auto_setting`. |
| P0 public discovery guard | Should stay as-is. `DISPLAY_ONLY_AUTO_DISABLED` chats explain many useful-but-unprocessed rows and are not a bug under the current guard. |
| Processable enabled chats with pending rows | Need controlled bounded reprocess/backfill if Adam wants historical coverage. Do not mass requeue. |
| `EMBEDDED_NO_MATERIAL` examples | Several are old/pre-fix or duplicates of already materialized content. Raw `2735` and `2793` are old low-score rejects; raw `2866` became a candidate and called provider but did not create a material; raw `2902` already materialized the same mini-guide. |
| Discussion/context gap | Real and large enough for a next gate: 173 context candidates across 75 chain groups, max chain length 15. Do not implement before controlled backlog decision. |

## Fresh Live Path Evidence

Fresh processable sample after `2026-06-25T12:38Z`: 80 rows exported to `p0-fresh-live-path-20260625.jsonl`.

| Stage | Evidence |
|---|---:|
| `intake_status=PROCESSED` | 80/80 |
| `queue_status=PROCESSED` | 80/80 |
| `batch_status=PROCESSED` | 80/80 |
| `run_status=COMPLETED` | 80/80 |
| single-message decision present | 80/80 |
| embeddings created | 13/80 |
| provider calls present | 3/80 |
| material created | 0/80 |
| candidate seen | raw `4743` as `SINGLE_MESSAGE_MATERIAL_CANDIDATE`, provider call made, LLM rejected/no material |

Interpretation: fresh live path is not stuck at `INTAKE_PENDING`. Rejections have explicit reasons such as `CHAT_CONTEXT_ONLY`, `SUPPRESSED_BY_CLASSIFIER`, `TOO_SHORT`, and `LOW_SINGLE_MESSAGE_SCORE`.

## Required Raw-ID Status

| raw_id | Final status |
|---:|---|
| 2609 | Old/pre-materialization replay artifact. Has embedding and legacy `final_decision=CANDIDATE`, no provider/material. Same content later materialized by raw `2902`, so treat as `already_materialized_duplicate`, not a required reprocess target. |
| 2735 | Replayed after single-message detector existed, has embedding, rejected with `LOW_SINGLE_MESSAGE_SCORE` at `0.3107`. Same content already materialized by raw `2902`; status `already_materialized_duplicate` plus historical scoring false negative. |
| 2793 | Same as raw `2735`: embedding exists, `REJECTED_SINGLE_MESSAGE`, score `0.3107`, duplicate of materialized raw `2902`. |
| 2866 | Candidate path worked: embedding exists, `SINGLE_MESSAGE_MATERIAL_CANDIDATE`, score `1.0`, two provider calls, no material. Same content already materialized by raw `2902`; status `already_materialized_duplicate`, not a coverage blocker. |

## Safe Reprocess Plan, Not Executed

Use only if Adam explicitly approves.

1. Scope: only `processingState=ENABLED_PROCESSABLE`, i.e. active dialog plus explicit enabled auto setting.
2. Exclude `DISPLAY_ONLY_AUTO_DISABLED` chats unless Adam explicitly enables their scope first.
3. Exclude raw messages whose normalized text/content hash is already represented by an existing `knowledge_item_sources` material.
4. Start with dry-run only: count candidates by chat, topic, age bucket, and reason.
5. Bound the first execution to a small window, e.g. latest 24h or max 100 messages, ordered newest-first.
6. Max provider budget: use existing auto settings budget plus a hard per-run cap.
7. Material status: `DRAFT` only, no auto-publish.
8. After run: report before/after counts for intake pending, queued, runs, embeddings, candidates, provider calls, materials, failures.
9. Stop if provider/model errors rise, if public guard rejects appear, or if duplicate material rate is high.

## Recommended Next Gate

| Priority | Gate | Why |
|---:|---|---|
| 1 | Controlled reprocess dry-run for processable chats | This directly addresses `intake_pending_not_queued` without weakening guards. |
| 2 | Dedup/source hash before reprocess | Required because raw `2609/2735/2793/2866` show repeated same-content messages. |
| 3 | Discussion segment design/implementation | Needed for 173 context candidates, but should follow backlog scope control. |
| 4 | Single-message calibration | Useful but not first; many apparent false negatives are duplicates or old rows. |

## Minimal Fix Done

No product code fix was applied in this task. The current live path is functioning; the remaining gap is historical/backlog and scope-controlled. The new artifacts are diagnostics/reporting only.

## Output Files

- Row-level reason CSV: `p0-coverage-gap-row-reasons-20260625.csv`
- Metrics JSON: `p0-coverage-gap-analysis-20260625.json`
- Fresh live evidence JSONL: `p0-fresh-live-path-20260625.jsonl`
