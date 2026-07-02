# Eligible for LLM Judge — v4 (post hand-audit cleanup)

Lab: `material_lab_v2` on rich 20k snapshot.
Verify: `34/34` messages, `15/15` clusters green (`scripts/verify_material_lab_v2.py`).

## Routes (20k)
- `CONTEXT_ONLY=18748`, `REVIEW_HIGH_RECALL=290`, `AGGREGATE_ONLY=156`, `NEEDS_ENRICHMENT=140`, `MANUAL_REVIEW=107`, `REJECT_SAFE=469`, `SIGNAL_ONLY=90`

## Cluster decisions (535 clusters)
- `REVIEW_SINGLE_SIGNAL=187`, `CONTEXT_ONLY=93`, `LINK_ENRICHMENT_FIRST=116`, `MANUAL_REVIEW=78`, `SIGNAL_PAIR=22`, `CONTEXT_GROUP=14`, `WEAK_PAIR=8`, `SIGNAL_GROUP=5`, `BLOCKED=4`, `EVIDENCE_GROUP_REVIEW=1`, `SINGLE_MESSAGE_GUIDE_CANDIDATE=6`, `SINGLE_MESSAGE_REFERENCE_CANDIDATE=1`

## Eligible for LLM Judge: 8 (all manually verified technical)
1 EVIDENCE_GROUP_REVIEW + 6 SINGLE_MESSAGE_GUIDE_CANDIDATE + 1 SINGLE_MESSAGE_REFERENCE_CANDIDATE

| cluster | decision | raw | subject | hand verdict |
|---|---|---|---|---|
| `v2c-00162` | EVIDENCE_GROUP_REVIEW | 25353/25367/25371 | r-api → api migration (2 senders) | KEEP — real multi-source technical discussion; thin, LLM may reject |
| `v2c-00025` | GUIDE_CANDIDATE | 27612 | Claude Code desktop setup (step-by-step) | KEEP — real how-to, tool=claudecode |
| `v2c-00041` | GUIDE_CANDIDATE | 29577 | codebase-memory-mcp + Codex setup task | KEEP — real technical task, tool=codebase-memory-mcp |
| `v2c-00323` | GUIDE_CANDIDATE | 15253 | Codex limit drain investigation + config fix | KEEP — real technical, tool=codex, actionable fix |
| `v2c-00528` | GUIDE_CANDIDATE | 25811 | RTXmacOC NVIDIA RTX 4070 GSP bring-up | KEEP — real open-source GPU RE project, domain=github.com |
| `v2c-00394` | GUIDE_CANDIDATE | 19514 | AI storage: private repos + MCP/plugins/skills | KEEP — technical announcement, tool=mcp (borderline; LLM decides) |
| `v2c-00434` | GUIDE_CANDIDATE | 31918 | Notion CLI alternatives on Linux (npm/SDK/API) | KEEP — technical resource list; profanity present, LLM should clean |
| `v2c-00275` | REFERENCE_CANDIDATE | 24787 | Yandex postamat MVP (Java coding task) | KEEP — real coding task, has_code (class/interface) |

## Removed by hand-audit cleanup (6 junk/borderline → downgraded)
| raw | was | now | bug fixed |
|---|---|---|---|
| 26202 | GUIDE_CANDIDATE | REVIEW_SINGLE_SIGNAL | #1 e-commerce domains counted as technical entity |
| 17737 | REFERENCE_CANDIDATE | MANUAL_REVIEW | #2 promo guard missed "халявные токены / бесплатными нейронками / до конца жизни" |
| 19578 | GUIDE_CANDIDATE | CONTEXT_ONLY | #3 NON_MATERIAL_LONG_RE missed "обобщающий пост / мысли из статьи / не дословно" |
| 20836 | GUIDE_CANDIDATE | MANUAL_REVIEW | #4 ABUSE_OR_FRAUD missed temp-email "temporam" + free-credit farming |
| 27968 | GUIDE_CANDIDATE | REVIEW_SINGLE_SIGNAL | #5 CODE_RE matched "public" in "Build in public" marketing prose |
| 32305/32318 | GUIDE_CANDIDATE | CONTEXT_ONLY | #6 CODE_RE matched "make" in fiction + NON_MATERIAL_LONG_RE missed English roleplay fiction |

## Bugs fixed + locked with gold regression
- #1 `has_technical_entity()` requires model/tool/api/code/api-term/technical-domain; bare e-commerce/content domains no longer qualify a shopping list as a guide.
- #2 `RISK_PROMO_REFERRAL` expanded: `халявные токены`, `бесплатными нейронками`, `бесконечные нейронки`, `до конца жизни`, `полтора доллара / доллар за труды/регистрацию`.
- #3 `NON_MATERIAL_LONG_RE` expanded: `обобщающий пост`, `мысли из статьи`, `не дословно`, `обзор статьи`, `мотивационный пост`.
- #4 `ABUSE_OR_FRAUD` expanded: temp-email services (`temporam`, `10minutemail`, `guerrillamail`, `tempmail`, `временная/одноразовая почта`).
- #5 `CODE_RE` tightened: ambiguous words (`public/private/select/update/insert/function/class/def/interface`) now require code context; `make` removed (too common in English).
- #6 `NON_MATERIAL_LONG_RE` expanded: English fiction/roleplay (`plane crashed`, `passengers survived`, `struggling to survive`, `survival scenario`, `roleplay scenario`, `how to make guns/weapons`).

## Gold regression cases added
- Cluster: `ct-shopping-guide`, `ct-omniroute-promo`, `ct-article-digest`, `ct-cometapi-abuse`, `ct-buildinpublic-marketing`, `ct-fiction-roleplay` (6 new → 15 total)
- Message: `tc-digest-01`, `tc-promo-freetokens-02`, `tc-abuse-tempemail-01`, `tc-fiction-01` (4 new → 34 total)
