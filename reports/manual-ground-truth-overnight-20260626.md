# Manual Ground Truth Overnight Audit - 2026-06-26

## 1. Scope And Reading

- window_from_msk: `2026-06-26T01:00:00+03:00`
- window_to_msk: `2026-06-26T10:00:00+03:00`
- window_from_utc: `2026-06-25T22:00:00Z`
- window_to_utc: `2026-06-26T07:00:00Z`
- manually reviewed raw messages in grouped context: 919
- processable: 919
- display-only/out-of-scope: 0
- context file used for reading: `reports/manual-ground-truth-context-20260626.md`

## 2. Manual Classes / Themes

| class_id | title | description | raw_ids | message_count | useful_count | potential_materials | recommendation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| C01 | API / provider status and fallback | ModelHub/r-api/OpenAI status, endpoint selection, model availability, fallback behavior for Hermes/Codex-like clients. | 5951 5953 5954 5955 6012 6014 6016 6018 6024 6026 6030 6034 6042 6047 6048 6052 6062 6064 6069 6070 6078 6079 6105 6111 6113 6117 6120 6125 6280 | 29 | 9 | 1 | Needs discussion-chain grouping; standalone scoring correctly rejects most fragments but misses combined status/fallback answer. |
| C02 | AI coding tools: cache, context and limits | Claude/Anthropic cache mechanics, subscription/API token cost behavior, long context cost, /new and /clear advice. | 6765 6769 6776 6778 6799 6803 6805 6817 6819 6826 6836 6850 6876 6304 6317 6318 | 16 | 9 | 1 | High-value multi-message answer; should be captured by discussion segment with technical explanation signals. |
| C03 | Tools and resource links | Lampa/StreamBert GitHub links, YouTube downloader promo/resource posts, repo/test requests. | 6107 6109 6110 6119 6121 6208 6210 6683 6730 | 9 | 4 | 1-2 | Resource-link answer/card detection should combine duplicate cross-posts and avoid promo-only posts. |
| C04 | Career / resume / hh | hh resume visibility, AI filters, resume wording polish and achievement phrasing. | 6536 6716 6861 6031 6311 6348 6379 | 7 | 3 | 2 | Single candidate handling plus discussion-pair grouping; raw 6861 is strong standalone guide. |
| C05 | Product feedback / CLI pain points | Singular CLI/vibecoding product principles and pain-point list for agents/CLIs. | 6219 6220 6221 6222 6223 6224 6225 6226 6258 6259 6262 | 11 | 3 | 1 | Needs same-timestamp message-burst grouping; one long list raw 6224 scored 0.48 but should join setup context. |
| C06 | Biohacking / retreats / coaching | Psychedelic retreat research, risks, goal-setting alternative, anxiety/goal observations. | 6341 6360 6538 6539 6540 6543 6571 6574 6575 6591 6592 6684 6688 6820 | 14 | 4 | 1 | Needs discussion segment; useful only across question-answer chain and should be framed cautiously. |
| C07 | Remote work / location risk | Discussion of working from another location, hiding location, company/security risk. | 6144 6367 | 2 | 1 | 1 weak | Potential short answer but lower priority; avoid overconfident advice. |
| C08 | AI/news/low-actionability updates | GPT-5.6 rumors, Rutube/GigaChat agents, model/news digests. | 6419 6542 6544 6545 6831 | 5 | 2 | 0-1 | Mostly news digest; low actionability unless newsBrief mode is explicitly desired. |
| C09 | Promo / ads | Hosting promo, YouTube downloader promo, channel-style posts. | 6306 6307 6683 | 3 | 0 | 0 | Correct reject or separate promo card only if product wants ads. |
| C10 | Onboarding templates | Repeated community welcome/onboarding messages. | 6028 6205 6346 6677 6031 6311 6348 6379 6745 | 9 | 0 | 0 | Duplicate/welcome suppression should stay active. |
| C11 | Crypto/trading/offtopic chatter | Oprichnina trading, liquidation, prop-firm, jokes, personal/offtopic, media. | 6136 6230 6233 6239 6285 6301 6302 6340 6353 6635 6845 6851 | 614 | 0 | 0 | Correct reject; avoid turning risky financial chatter into materials. |
| C12 | General low-value chatter/media | Short replies, jokes, join events, stickers, voice/video without text, one-word confirmations. | 5964 5966 5969 5970 6163 6209 6417 6418 6537 6744 | 200 | 0 | 0 | Correct reject; keep media/no-text and short-chat filters. |

## 3. Manual Clusters

| cluster_id | class_id | title | raw_ids | messages_count | time_window | proposed_type | should_materialize | expected_title | reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CL01 | C01 | API status page and fallback diagnostics | 6034 6042 6047 6048 6052 6062 6069 6070 6078 6079 6105 | 11 | 2026-06-26 01:10-01:17 MSK | DISCUSSION_SEGMENT | yes | Checklist for diagnosing ModelHub/API slowdowns and model fallback | One coherent support/product feedback thread about status monitoring and fallback diagnostics. |
| CL02 | C02 | Anthropic cache and Claude limits explanation | 6765 6769 6776 6778 6799 6803 6805 6817 6819 6826 6836 6850 6876 | 13 | 2026-06-26 09:49-09:59 MSK | GUIDE | yes | How Claude/Anthropic prompt caching affects subscription limits and API cost | A single explanatory discussion about prompt cache, long context and limits. |
| CL03 | C03 | Lampa StreamBert resource and repo | 6107 6109 6110 6119 6121 6208 6730 | 7 | 2026-06-26 01:17-09:46 MSK | ANSWER | yes | Lampa StreamBert repo and what it does | Duplicate/cross-posted resource context around one GitHub project. |
| CL04 | C05 | Singular CLI pain points for vibe-coding tools | 6219 6220 6221 6222 6223 6224 6225 6226 | 8 | 2026-06-26 02:30-02:31 MSK | SUMMARY | yes | Pain points for AI coding CLI users | Same-timestamp burst forms one product research artifact. |
| CL05 | C04 | hh resume visibility and AI filters | 6716 6861 | 2 | 2026-06-26 09:44-09:58 MSK | GUIDE | yes | How to improve hh.ru resume visibility: activity checklist and AI-filter limitations | Question plus substantive answer; answer is standalone enough but better with question context. |
| CL06 | C04 | Resume achievement phrasing polish | 6536 | 1 | 2026-06-26 07:24 MSK | ANSWER | yes | How to phrase technical resume achievements actively | Single useful coaching note on resume wording. |
| CL07 | C06 | Psychedelic retreat risks and goal-setting alternative | 6341 6538 6540 6543 6592 6688 6820 | 7 | 2026-06-26 05:35-09:55 MSK | DISCUSSION_SEGMENT | yes | Psychedelic retreats: cautionary observations and goal-setting alternative | One Q&A chain about retreat research and psychological/coaching risks. |
| CL08 | C07 | Remote work from another location risk | 6144 6367 | 2 | 2026-06-26 01:40-06:03 MSK | ANSWER | maybe | Working remotely from another location: risk checklist | Short practical answer to remote-location risk. |
| CL09 | C08 | AI news digest and GPT-5.6 rumors | 6419 6544 6545 6831 | 4 | 2026-06-26 06:37-09:55 MSK | SUMMARY | no | AI news digest | News content with low direct actionability. |
| CL10 | C09 | Hosting promo post | 6306 6307 6676 | 3 | 2026-06-26 03:36-09:23 MSK | IGNORE | no | N/A | Ad/promo cluster. |
| CL11 | C10 | Repeated OM onboarding messages | 6028 6205 6346 6677 6031 6311 6348 6379 6745 | 9 | whole window | IGNORE | no | N/A | Repeated boilerplate template. |
| CL12 | C11 | Crypto/trading chatter | 6136 6230 6233 6239 6285 6301 6302 6340 6353 6635 6845 6851 | 12 | whole window | IGNORE | no | N/A | Large off-topic/risky financial chatter. |

## 4. Expected Materials

| expected_id | title | type | raw_ids | cluster_id | single_or_multi | should_system_catch_today | needed_feature | expected_outline |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E01 | Checklist for diagnosing ModelHub/API slowdowns and model fallback | NEEDS_DISCUSSION_SEGMENT | 6034 6042 6052 6062 6069 6070 6078 6079 6105 | CL01 | multi | no | DISCUSSION_SEGMENT plus API/status signal aggregation | Problem symptoms; endpoint/region check; status/model availability; client fallback strategy; product diagnostics request. |
| E02 | How Claude/Anthropic prompt caching affects limits and API cost | SHOULD_BE_GUIDE | 6765 6776 6778 6799 6803 6805 6817 6826 6836 6850 6876 | CL02 | multi | no | DISCUSSION_SEGMENT and combined technical explanation scoring | Cache write/read; why first request is costly; /new/clear caveat; long context cost; practical rules. |
| E03 | Lampa StreamBert repo and what it does | SHOULD_BE_ANSWER | 6107 6119 6208 6730 | CL03 | multi | no | Resource-link clustering/dedupe across cross-posts | Repo link; source project; added Russian dubbing/player; platform caveat; testing request. |
| E04 | Pain points for AI coding CLI users | SHOULD_BE_SUMMARY | 6221 6223 6224 6225 | CL04 | multi | borderline | Same-timestamp burst grouping; list-structure boost | Product principle; categorized pain points; follow-up questions. |
| E05 | How to improve hh.ru resume visibility: activity checklist and AI-filter limitations | SHOULD_BE_GUIDE | 6716 6861 | CL05 | single_or_pair | yes | Fixed by approvedJudgeDecision bugfix; optional controlled reprocess for raw 6861 | Disclaimer; checklist of activity signals; contact-data caveat; timing; AI-filter limitation; resume review recommendation. |
| E06 | How to phrase technical resume achievements actively | SHOULD_BE_ANSWER | 6536 | CL06 | single | maybe | Career/resume answer scoring boost for concrete before/after examples | Before/after phrasing; emphasize owner action; note marginal conversion impact. |
| E07 | Psychedelic retreats: cautionary observations and goal-setting alternative | NEEDS_DISCUSSION_SEGMENT | 6341 6540 6543 6592 6688 6820 | CL07 | multi | no | DISCUSSION_SEGMENT with safety framing and health-topic caution | Question context; observed harms; goal-setting alternative; caveats and non-medical framing. |
| E08 | Working remotely from another location: risk checklist | SHOULD_BE_ANSWER | 6144 6367 | CL08 | pair | no | Q&A pair linking; risk/policy cautious prompt | Company-specific risk; security detection; ask manager/colleagues; hide location caveat. |
| E09 | AI news digest from overnight posts | LOW_VALUE | 6419 6544 6545 6831 | CL09 | single_or_multi | no | Keep newsBrief optional; do not boost by default | N/A unless news mode enabled. |
| E10 | Hosting promo post | NOISE | 6306 6307 | CL10 | single | no | Promo suppression | N/A |

## 5. Manual Vs Pipeline Gaps

| expected_id | manual_title | raw_ids | expected_type | actual_stage | actual_decision | actual_reason | actual_material_id | gap_type | fix_recommendation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E01 | Checklist for diagnosing ModelHub/API slowdowns and model fallback | 6034 6042 6052 6062 6069 6070 6078 6079 6105 | NEEDS_DISCUSSION_SEGMENT | embedding | REJECTED_SINGLE_MESSAGE | CHAT_CONTEXT_ONLY; LOW_SINGLE_MESSAGE_SCORE; TOO_SHORT |  | DISCUSSION_SEGMENT_MISSING | Add bounded same chat/topic/thread chain grouping with combined score. |
| E02 | How Claude/Anthropic prompt caching affects limits and API cost | 6765 6776 6778 6799 6803 6805 6817 6826 6836 6850 6876 | SHOULD_BE_GUIDE | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE; TOO_SHORT; CHAT_CONTEXT_ONLY |  | DISCUSSION_SEGMENT_MISSING | Add bounded same chat/topic/thread chain grouping with combined score. |
| E03 | Lampa StreamBert repo and what it does | 6107 6119 6208 6730 | SHOULD_BE_ANSWER | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE |  | CLUSTERING_MISSED | Resource-link dedupe/clustering across cross-posts. |
| E04 | Pain points for AI coding CLI users | 6221 6223 6224 6225 | SHOULD_BE_SUMMARY | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE; TOO_SHORT |  | CLUSTERING_MISSED | Same-timestamp burst/list grouping; list-structure boost. |
| E05 | How to improve hh.ru resume visibility: activity checklist and AI-filter limitations | 6716 6861 | SHOULD_BE_GUIDE | llm_judge | SINGLE_MESSAGE_MATERIAL_CANDIDATE | LLM_REJECTED_ALL |  | MATERIAL_GENERATION_BUG | Fixed approvedJudgeDecision whitelist; controlled raw 6861 reprocess only if approved. |
| E06 | How to phrase technical resume achievements actively | 6536 | SHOULD_BE_ANSWER | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE |  | SINGLE_MESSAGE_FALSE_NEGATIVE | Boost concrete before/after career wording examples; maybe route resume advice to answer. |
| E07 | Psychedelic retreats: cautionary observations and goal-setting alternative | 6341 6540 6543 6592 6688 6820 | NEEDS_DISCUSSION_SEGMENT | embedding | REJECTED_SINGLE_MESSAGE | TOO_SHORT; LOW_SINGLE_MESSAGE_SCORE; CHAT_CONTEXT_ONLY |  | DISCUSSION_SEGMENT_MISSING | Add bounded same chat/topic/thread chain grouping with combined score. |
| E08 | Working remotely from another location: risk checklist | 6144 6367 | SHOULD_BE_ANSWER | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE |  | DISCUSSION_SEGMENT_MISSING | Add bounded same chat/topic/thread chain grouping with combined score. |
| E09 | AI news digest from overnight posts | 6419 6544 6545 6831 | LOW_VALUE | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE |  | CORRECT_REJECT | Keep rejected. |
| E10 | Hosting promo post | 6306 6307 | NOISE | embedding | REJECTED_SINGLE_MESSAGE | LOW_SINGLE_MESSAGE_SCORE; CHAT_CONTEXT_ONLY |  | CORRECT_REJECT | Keep rejected. |

Summary: expected useful materials = 8; created = 0 in window; missed useful = 8. Main miss reasons: DISCUSSION_SEGMENT_MISSING, CLUSTERING_MISSED, SINGLE_MESSAGE_FALSE_NEGATIVE, and MATERIAL_GENERATION_BUG. Some expected rows overlap by cause.

## 6. Top 10 Missed Guides / Materials

| id | title | type | raw_ids | why useful | needed feature |
| --- | --- | --- | --- | --- | --- |
| E01 | Checklist for diagnosing ModelHub/API slowdowns and model fallback | NEEDS_DISCUSSION_SEGMENT | 6034 6042 6052 6062 6069 6070 6078 6079 6105 | Actionable support/product feedback for API users and model fallback. | DISCUSSION_SEGMENT plus API/status signal aggregation |
| E02 | How Claude/Anthropic prompt caching affects limits and API cost | SHOULD_BE_GUIDE | 6765 6776 6778 6799 6803 6805 6817 6826 6836 6850 6876 | Strong practical explanation with examples and user-facing advice. | DISCUSSION_SEGMENT and combined technical explanation scoring |
| E03 | Lampa StreamBert repo and what it does | SHOULD_BE_ANSWER | 6107 6119 6208 6730 | Resource link with contextual explanation. | Resource-link clustering/dedupe across cross-posts |
| E04 | Pain points for AI coding CLI users | SHOULD_BE_SUMMARY | 6221 6223 6224 6225 | Good product research artifact with numbered list. | Same-timestamp burst grouping; list-structure boost |
| E05 | How to improve hh.ru resume visibility: activity checklist and AI-filter limitations | SHOULD_BE_GUIDE | 6716 6861 | Strong standalone guide-like answer. | Fixed by approvedJudgeDecision bugfix; optional controlled reprocess for raw 6861 |
| E06 | How to phrase technical resume achievements actively | SHOULD_BE_ANSWER | 6536 | Specific resume-writing advice. | Career/resume answer scoring boost for concrete before/after examples |
| E07 | Psychedelic retreats: cautionary observations and goal-setting alternative | NEEDS_DISCUSSION_SEGMENT | 6341 6540 6543 6592 6688 6820 | Coherent Q&A chain but sensitive and context-dependent. | DISCUSSION_SEGMENT with safety framing and health-topic caution |
| E08 | Working remotely from another location: risk checklist | SHOULD_BE_ANSWER | 6144 6367 | Short practical risk answer. | Q&A pair linking; risk/policy cautious prompt |

## 7. Top Discussion Chains

| chain_id | chat/topic | raw_ids | gap/window | combined_signals | expected_title | why_needs_discussion_segment |
| --- | --- | --- | --- | --- | --- | --- |
| CH01 | Vibemode / Баги и вопросы по API | 6034 6042 6052 6062 6069 6070 6078 6079 6105 | 2026-06-26 01:10-01:17 MSK | API_OR_STATUS_SIGNAL; troubleshooting; product requirement; fallback | Checklist for diagnosing ModelHub/API slowdowns and model fallback | DISCUSSION_SEGMENT plus API/status signal aggregation |
| CH02 | Vibecoder Chat [Public] / Claude Code | 6765 6776 6778 6799 6803 6805 6817 6826 6836 6850 6876 | 2026-06-26 09:49-09:59 MSK | API_OR_STATUS_SIGNAL; cost explanation; actionable usage advice; examples | How Claude/Anthropic prompt caching affects limits and API cost | DISCUSSION_SEGMENT and combined technical explanation scoring |
| CH03 | Vibe GIG Мастерская / Флудилка + Полезные ссылки | 6107 6119 6208 6730 | 2026-06-26 01:17-09:46 MSK | LINK_CONTEXT; resource; setup caveat | Lampa StreamBert repo and what it does | Resource-link clustering/dedupe across cross-posts |
| CH04 | Vibe GIG Мастерская / Флудилка | 6221 6223 6224 6225 | 2026-06-26 02:30-02:31 MSK | numbered list; product feedback; UX pain points; agent tooling | Pain points for AI coding CLI users | Same-timestamp burst grouping; list-structure boost |
| CH05 | ОМ: Полезные обсуждения | 6716 6861 | 2026-06-26 09:44-09:58 MSK | GUIDE_OR_HOWTO; checklist; limitations; AI filters; hh.ru | How to improve hh.ru resume visibility: activity checklist and AI-filter limitations | Fixed by approvedJudgeDecision bugfix; optional controlled reprocess for raw 6861 |
| CH06 | ОМ: Биохакинг | 6341 6540 6543 6592 6688 6820 | 2026-06-26 05:35-09:55 MSK | caution; observed cases; alternative intervention; question-answer chain | Psychedelic retreats: cautionary observations and goal-setting alternative | DISCUSSION_SEGMENT with safety framing and health-topic caution |
| CH07 | ОМ: Полезные обсуждения | 6144 6367 | 2026-06-26 01:40-06:03 MSK | risk; conditional advice; operational policy | Working remotely from another location: risk checklist | Q&A pair linking; risk/policy cautious prompt |

## 8. Single-message False Negatives

| raw_id | preview | manual_verdict | score | rejection_reason | why_false_negative | fix |
| --- | --- | --- | --- | --- | --- | --- |
| 6536 | "GUI и USB-транспорт стали отдельными модулями" - не стали сами, а ты сделал их отдельными<br><br>"интерфейс не зависает" - не сам не зависает, а ты сделал чтобы интерфейс не зависал<br><br>"Установка на новой машине Astra заняла од | SHOULD_BE_ANSWER | 0.3368 | LOW_SINGLE_MESSAGE_SCORE | Concrete before/after resume wording guidance is useful but lacks API/code signals and is below generic single-message threshold. | Add career/resume concrete-example signal or route resume channels with lower answer threshold plus LLM judge. |
| 6224 | актуальный уже существующий список:<br><br>1. кривой ии-слопный фронт<br>2. объяснение агенту простых очевидных вещей которых он не знает, отчего ты вынужден тратить время и токены на инференс<br>3. отсутствие фри моделей в агенте<br>4 | SHOULD_BE_SUMMARY when combined with 6221-6225 | 0.48 | LOW_SINGLE_MESSAGE_SCORE | Long numbered product pain list is useful, but standalone score 0.48 misses because surrounding setup is split into same-timestamp messages. | Same-timestamp burst grouping and numbered-list/product-feedback boost. |
| 6765 | почитайте теорию как работает кеширование в антропике. В подписке оно тоже используется, как и в api. Именно на первых запросах в сессии основная часть падает в кеш на запись, а лимиты в подписке считай что деньги в апи, | SHOULD_BE_GUIDE when combined with 6776/6799/6817/6876 | 0.4878 | LOW_SINGLE_MESSAGE_SCORE | Technical explanation has enough signal but needs companion simplification and examples. | Discussion segment for technical explanations within 15 minutes; cache/cost/token keywords boost. |
| 6105 | Статус openai, тг чат, проверять свой vpn, проверять настройки самого инструмента, проверять автосжатие, в лк смотреть и ловить запросы, переключаться на другие модели, создавать новые чаты | SHOULD_BE_ANSWER as part of API diagnostics chain | 0.3578 | LOW_SINGLE_MESSAGE_SCORE | Checklist fragment is actionable but terse and context-dependent. | Combine with surrounding API status thread; checklist/action-verb signal boost. |
| 6543 | У меня есть знакомые (4 человек), которые ретритили.<br><br>После ретритов их проблемы не решались, они чаще уходили в "нужно жить в гармонии и по кайфу ваще". <br><br>У всех после этого появились проблемы с целеполаганием типа "у с | NEEDS_DISCUSSION_SEGMENT | 0.44 | LOW_SINGLE_MESSAGE_SCORE | Useful cautionary answer in health/coaching thread but sensitive and requires Q context. | Discussion segment plus safety/health caution routing, not simple threshold lowering. |

## 9. LLM Judge False Negatives

| raw_ids | provider_call_id | model | LLM decision | human verdict | why LLM wrong | fix |
| --- | --- | --- | --- | --- | --- | --- |
| 6716 | 69 | gpt-5.5 | reject | CORRECT_REJECT as standalone; useful only after answer 6861 arrives. | Not wrong for raw 6716 alone. | For Q+A thread, judge combined pair after answer arrives rather than single question. |

Finding: no true LLM false negative was found for a single candidate. Raw 6716 was correctly rejected alone; the system needed to judge the Q+A pair after raw 6861 arrived. Raw 6861 was positive but hit material-generation parser bug already fixed.

## 10. Existing Materials Quality Review

| material_id | title | type | source_raw_ids | source_count | manual_verdict | explanation | fix |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 18 | Скепсис к фишкам ради фишек | generation | 5241 5243 | 2 | LOW_VALUE | Meta-chat skepticism; likely not a durable material. | Raise generation quality gate for opinion-only clusters. |
| 17 | DeepSeek vs Opus check discussion | cluster_summary | 5049 5053 | 2 | LOW_VALUE | Thin cluster summary, not actionable. | Require concrete takeaway or user action for cluster summaries. |
| 16 | Google DeepMind researchers to competitors | newsBrief | 5023 5028 | 2 | OK_BUT_WRONG_TYPE | Valid news brief if news mode is desired; otherwise low product value. | Separate newsBrief tab/setting from guides. |
| 15 | Personal acquaintance clarification | generation | 5060 5064 | 2 | SHOULD_NOT_EXIST | Personal/social clarification has no durable knowledge value. | Add personal-chat/social-chatter suppression. |
| 14 | Multiple ChatGPT Plus accounts switch point | guide | 4561 | 1 | REVIEW | Potentially useful but may be policy-sensitive and needs source verification. | Risk/policy warning and human review. |
| 13 | Repeated community welcome message | cluster_summary | 2440 2813 3240 4174 | 4 | SHOULD_NOT_EXIST | Onboarding boilerplate duplicate; not a material. | Suppress repeated welcome templates. |
| 12 | Optimize resume for AI filters and HR on hh | guide | 2662 | 1 | GOOD | Existing related material means overnight hh guide may be partial duplicate but raw 6861 adds visibility checklist. | Dedupe should compare topic and update existing guide rather than blindly skip. |
| 11 | Useful ChatGPT prompts collection | generation | 2926 2947 | 2 | GOOD | Useful if prompts are actually preserved in body. | Ensure material body includes concrete prompts. |
| 10 | Cursor OpenAI-compatible API check mini-guide | guide | 2902 | 1 | GOOD | High-quality concrete troubleshooting guide. | Use as positive scoring exemplar. |
| 9 | Anthropic Careers Overview | generation | 2672 2678 | 2 | LOW_VALUE | Generic URL summary; not from overnight and likely low value. | Require user problem or actionable extraction. |
| 8 | BGE-M3 RAM and embedding dimension | answer | synthetic | 2 | GOOD | Concrete technical answer. | Keep. |
| 7 | Hugging Face and ML/RAG infrastructure note | generation | synthetic | 5 | OK_BUT_WRONG_TYPE | Broad reference material, probably should be guide/reference. | Improve type labeling. |
| 6 | Docker build failures for PyTorch CPU-only images | guide | synthetic | 5 | GOOD | Concrete troubleshooting guide. | Keep. |
| 5 | FastAPI launch and production deployment | guide | synthetic | 2 | GOOD | Concrete technical guide. | Keep. |
| 4 | SentenceTransformer import error | generation | synthetic | 2 | GOOD | Concrete troubleshooting note, but type should be answer/guide. | Improve artifact type mapping. |
| 3 | Scoped acceptance run budget guidance | generation | synthetic | 3 | GOOD | Operational guidance useful for this product. | Keep internal/operator classification. |
| 2 | Spring pipeline operations debugging | generation | synthetic | 5 | GOOD | Operational guide useful for system debugging. | Keep internal/operator classification. |

## 11. Calibration Map

### Scoring

- Undervalued: numbered product-feedback lists (`6224`), concrete before/after resume examples (`6536`), technical cost/cache explanations (`6765/6776/6817/6876`), resource links with context (`6107/6208/6730`), checklist fragments inside support threads (`6105`).
- Overvalued risk to avoid: generic long text, welcome templates, promo posts, crypto/trading chatter, media captions, news digests.
- Do not globally lower `0.55`; add targeted signals and discussion-level scoring first.

### Clustering

- Live clustering created no useful clusters overnight. The misses were mostly temporal/thread discussion chains rather than semantic macroclusters.
- Need same chat/topic/thread burst grouping and cross-post resource dedupe.

### Discussion Segment

- Expected useful materials requiring discussion context: 6 of 8 useful expected materials.
- Proposed rule: same account/chat/topic/thread; max 6 messages; max 20 minutes; allow same-timestamp bursts; min combined score 0.55; at least two useful signal families; stop on topic switch, long silence, media-only/noise streak, or author-only joke chain.

### LLM Judge

- Judge was correct for raw `6716` alone. Need pair/chain-level judge after answer arrives.
- Approved decisions should include `SINGLE_MESSAGE_MATERIAL_CANDIDATE` and `DIRECT_MATERIAL_READY`; backend has been fixed.
- Add strict schema validation and persist parsed decision plus skip reason per candidate.

### Material Generation

- Accepted candidate raw `6861` did not become material because approval parser skipped generation; fixed.
- Need explicit per-candidate generation skip logging, not only aggregate `LLM_REJECTED_ALL`.

### Dedupe

- No overnight duplicate false positive found. Existing material `12` overlaps hh resume optimization; richer new answers should update/merge rather than be suppressed.

## 12. Next Tasks In Priority Order

1. Design DISCUSSION_SEGMENT grouping and scoring using chains E01/E02/E04/E07 as fixtures; do not implement without approval.
2. Add trace persistence fix so live `replay_run_messages.raw_message_id` is populated or diagnostics always join through `dataset_messages`.
3. Add per-candidate LLM/generation skip reason fields: approved/rejected/low-confidence/generation-called/material-written.
4. Add targeted scoring fixtures for `6224`, `6536`, `6765`, `6105`, not a global threshold change.
5. Add resource-link dedupe/merge logic for Lampa/StreamBert cross-posts.
6. Review low-value existing materials `13`, `15`, `17`, `18`, `9` for archive/delete using existing soft-delete flow only after approval.
