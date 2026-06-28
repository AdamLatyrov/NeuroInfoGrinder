# Usefulness Classification Architecture Fit - 2026-06-27

Status: read-only audit/design. No code, settings, caps, prompts, provider config, thresholds, materials, backlog/reprocess, Telegram/session/proxy, discovery guard, or publish state were changed.

## Recommendation

Use Option B: add a small internal `MessageUsefulnessClassifier` called by the existing `ReplayV2Service.singleMessageDetection`. It must not be a new pipeline and must not create materials directly. It should only produce content/usefulness classes, signals, score hints, proposed material type, and specific rejection reasons for the existing candidate decision path.

Keep the existing flow:

`raw message -> normalized text -> existing classifier/scoring layer -> extended usefulness classes -> existing candidate decision -> LLM judge -> dedupe -> caps/safety gates -> generation -> DRAFT material`

Do not build a parallel “Useful Message Router”.

## Files And Methods Inspected

| Area | File | Methods / Lines |
|---|---|---|
| Main replay orchestration | `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2Service.java` | `processExistingRun` lines 121-160 |
| Deterministic normalization/rules | `ReplayV2Service.java` | `normalizeFeaturesRules` lines 162-178, `features` lines 2011-2032, `labels` line 2033 |
| Worker classifier | `ReplayV2Service.java`, `ModelWorkerClient.java` | `bertClassification` lines 186-274, `ModelWorkerClient.classify` lines 62-72 |
| Embeddings/clustering | `ReplayV2Service.java`, `ModelWorkerClient.java` | `embeddings`, `clustering`, `ranking`, `embedBatch` lines 74-83 |
| Deduplication | `ReplayV2Service.java` | `dedupe` lines 406-428 |
| Single-message scoring | `ReplayV2Service.java` | `singleMessageDetection` lines 2078-2158, `rejectSingleMessage` line 2569 |
| Discussion scoring | `ReplayV2Service.java` | `discussionSegmentDetection` lines 2187-2210, `scoreDiscussionWindow` lines 2220-2260 |
| Controlled gates | `ReplayV2Service.java` | `discussionEligibleForControlledGeneration` lines 2320-2365 |
| LLM/generation routing | `ReplayV2Service.java` | `llm` lines 538-609 |
| Text loading | `ReplayV2Service.java` | `loadMessages` lines 2724-2732 |
| UI message text | `Stage1ReadService.java` | `messageMapper` lines 1175+, `preferredText` lines 1382-1386 |
| Raw to dataset | `AutoPipelineService.java` | `dataset_messages` insert lines 262-266 |
| Material detail/trace | `KnowledgeMaterialService.java` | source messages, `traceStages`, `providerCalls` |
| Schema | `V4__full_intelligence_replay_pipeline.sql`, `V24__discussion_segments.sql` | `message_intelligence`, `message_classifications`, `replay_run_messages`, `pipeline_message_trace`, `discussion_segments` |

## Current Architecture Map

`ReplayV2Service.processExistingRun` already orchestrates the useful-message path:

1. `normalizeFeaturesRules`
2. `bertClassification`
3. `embeddings`
4. `clustering`
5. `ranking`
6. `dedupe`
7. `singleMessageDetection`
8. `discussionSegmentDetection`
9. `llm`

Candidate flows currently supported:

- `SINGLE_MESSAGE`: `singleMessageDetection` -> `LLM_CLUSTER_JUDGE_AND_ROUTING` -> `KNOWLEDGE_GENERATION`.
- `DISCUSSION_SEGMENT`: `discussionSegmentDetection` -> `DISCUSSION_SEGMENT_JUDGE` -> controlled gates -> `KNOWLEDGE_GENERATION`.
- cluster legacy flow: micro/macro cluster scoring -> `LLM_CLUSTER_JUDGE_AND_ROUTING` -> `KNOWLEDGE_GENERATION`.

Candidate data is persisted through `replay_run_messages`, `knowledge_items.source_cluster_type`, `knowledge_item_sources`, `discussion_segments`, `discussion_segment_sources`, `pipeline_message_trace`, and `provider_calls`.

## Input Normalization

Replay text path:

- `loadMessages` loads `dataset_messages.text`, `caption`, `raw_json`, `entities_json`, and `media_json`.
- `normalizeFeaturesRules` uses `coalesce(message.text(), message.caption(), "")`.
- `features` extracts links from normalized text and hidden links from `entities_json`.
- `raw_json` is loaded but is not used as a fallback text source for scoring.

UI text path:

- `/groups` uses `Stage1ReadService.preferredText(text, caption)`, returning `text` if nonblank, else `caption`.

Finding:

- Caption is used by both replay and UI. raw `11142` was not missed because caption was ignored; it was rejected because current scoring favors guide/actionability and produced `LOW_SINGLE_MESSAGE_SCORE` for a news/reference post.
- `raw_json`/link-preview fallback may still be needed for sparse link messages and richer link enrichment.

## Current Classification

The system is hybrid:

- deterministic rules/features in `ReplayV2Service.features`;
- worker `/classify` via `ModelWorkerClient.classify`;
- embedding clustering via `/embed-batch`;
- LLM judge/routing for selected candidates;
- heuristic discussion window scoring before LLM judge.

Current deterministic labels include:

- `ERROR_LOG_WITH_FIX`
- `API_OR_CONFIG_SNIPPET`
- `PRICING_OR_ACCESS_SIGNAL`
- `RESOURCE_LINK_COLLECTION`
- `QUESTION_WITH_VALUABLE_ANSWER`
- `NOISE_OR_CHAT`

Existing training/export vocab already hints at broader usefulness classes:

- artifact labels: `GUIDE`, `NOTE`, `TROUBLESHOOTING_NOTE`, `COMPARISON_INSIGHT`, `PRICE_ACCESS_CARD`, `RESOURCE_CARD`, `RISK_NOTE`, `NEWS_SIGNAL`, `TREND_CLUSTER`, `NONE`.
- message roles: `QUESTION`, `ANSWER`, `ERROR_LOG`, `FIX`, `ANNOUNCEMENT`, `PRICE_OR_ACCESS`, `RESOURCE_LINK`, `PROMO`, `CHAT`, `RISK`, `CODE_OR_CONFIG`, `COMPARISON`.

So the extension fits existing classification/routing concepts.

## Scoring And Reasons

Single-message scoring currently uses length, structure, guide/how-to, troubleshooting/error, code/config, price/access, Q&A, link/resource, hard-signal, and worker-label bonuses.

Single-message rejection reasons include:

- `CHAT_CONTEXT_ONLY`
- `TOO_SHORT`
- `LOW_SINGLE_MESSAGE_SCORE`
- `SUPPRESSED_BY_CLASSIFIER`
- `NO_EMBEDDING`

Discussion reasons/gates include:

- `PROMO_OR_AD`
- `DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT`
- `DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT`
- `DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE`
- `DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT`
- `DISCUSSION_SEGMENT_REJECTED_RISK_SENSITIVE_CONTROLLED_MODE`
- `DISCUSSION_SEGMENT_REJECTED_NOT_FRESH_AFTER_ENABLE_TIME`
- `DISCUSSION_SEGMENT_REJECTED_DAILY_LIMIT`
- `DISCUSSION_SEGMENT_REJECTED_CHAT_TOPIC_DAILY_LIMIT`
- `DISCUSSION_SEGMENT_REJECTED_DUPLICATE_MATERIAL`
- `DISCUSSION_SEGMENT_REJECTED_UNSUPPORTED_TYPE`

Vague reasons to improve:

- `TOO_SHORT` for link-only should become `NEEDS_LINK_ENRICHMENT` or `LINK_ONLY`.
- `LOW_SINGLE_MESSAGE_SCORE` for news/reference posts should become either a specific non-candidate reason like `NEWS_SUMMARY_MODE_NOT_ENABLED` or a routed candidate as `NEWS_UPDATE` -> `SUMMARY`/`REFERENCE`.
- `PROMO_OR_AD` should split into `PROMO_ALONE` and context-only promo signal.

## New Usefulness Classes

These are classifier/usefulness classes, not material types:

| Class | Existing Route | Proposed Material Type / Outcome |
|---|---|---|
| `PRACTICAL_GUIDE` | `SINGLE_MESSAGE` | `GUIDE` |
| `NEWS_UPDATE` | `SINGLE_MESSAGE` route variant | `SUMMARY` or `REFERENCE` |
| `RESOURCE_REFERENCE` | `SINGLE_MESSAGE` route variant | `REFERENCE` |
| `STATUS_OUTAGE` | `SINGLE_MESSAGE` route variant | `GUIDE` or `SUMMARY` |
| `QNA` | `SINGLE_MESSAGE` | `ANSWER` |
| `LINK_WITH_CONTEXT` | `SINGLE_MESSAGE` route variant | `REFERENCE` or `SUMMARY` |
| `LINK_ONLY` | reject | `NEEDS_LINK_ENRICHMENT` |
| `PROMO` | reject/context only | `PROMO_ALONE` |
| `ABUSE_OR_FRAUD` | reject | `ABUSE_OR_FRAUD` |
| `ACCESS_CIRCUMVENTION` | reject/manual | `RISK_SENSITIVE_MANUAL_ONLY` |
| `ENTITY_ONLY` | reject | `ENTITY_ONLY` |
| `NOISE` | reject | `LOW_VALUE` |

Classifier output must not create materials. It only influences candidate decision, route, proposed material type, score breakdown, and rejection reason.

## Option Decision

### Option A - Extend existing scoring

Pros: minimal change, direct fix. Cons: `ReplayV2Service` grows further, harder to reuse in discussion scoring, harder to explain in UI.

### Option B - Add internal `MessageUsefulnessClassifier`

Pros: fits current architecture, keeps replay as orchestrator, isolates class logic, improves traceability, reusable later by discussion scoring. Cons: new internal component and tests.

### Option C - Extend worker `/classify`

Pros: worker already accepts text/features. Cons: makes deterministic product routing depend on model-worker behavior, not ideal for stable reasons like `LINK_ONLY`, `PROMO_ALONE`, `ABUSE_OR_FRAUD`.

Recommendation: Option B.

## Data Model Impact

No schema change is needed initially.

Use existing fields:

- `replay_run_messages.single_message_signals_json`
- `replay_run_messages.single_message_score_breakdown_json`
- `replay_run_messages.single_message_rejection_reason`
- `pipeline_message_trace.output_json`
- `discussion_segments.signals_json`
- `discussion_segments.suppression_reasons_json`

Add scalar indexed columns only later if production filtering/reporting by usefulness class becomes a real UI requirement.

## UI / Trace Impact

The message pipeline drawer should eventually show:

- content class;
- usefulness class;
- source/context class;
- safety class;
- candidate route;
- proposed material type;
- score dimensions;
- positive and negative signals;
- rejection reason;
- human-readable explanation.

Best sources are existing `single_message_*_json` fields and `pipeline_message_trace.output_json`. Extend existing message/pipeline detail responses if they do not expose these fields; do not add a separate diagnostics API unless necessary.

## Next Implementation Plan

1. Add `MessageUsefulnessClassifier` under the replay/pipeline package.
2. Define a result record with `contentClass`, `usefulnessClass`, `sourceContextClass`, `safetyClass`, `proposedMaterialType`, `rejectReason`, `positiveSignals`, `negativeSignals`, and score modifiers.
3. Call it inside `singleMessageDetection` before generic `TOO_SHORT` / `LOW_SINGLE_MESSAGE_SCORE` finalization.
4. Add route-specific scoring for `NEWS_UPDATE`, `RESOURCE_REFERENCE`, `STATUS_OUTAGE`, `QNA`, and `LINK_WITH_CONTEXT`.
5. Add explicit reject reasons for `LINK_ONLY`, `ENTITY_ONLY`, `PROMO_ALONE`, `ABUSE_OR_FRAUD`, and `RISK_SENSITIVE_MANUAL_ONLY`.
6. Keep LLM judge, dedupe, caps/safety gates, and generation unchanged.
7. Store classes/signals in existing JSON fields and trace output.
8. Add regression fixtures based on raw-like examples, without hardcoding production raw ids into production logic.

## Later Fixtures

Positive: GPT-5.6 Sol-like news update, OpenMontage-like resource post, Codex outage/quota-like status post, proxy model-substitution/log-resale risk post.

Negative: lolz.live link-only, PlusVibeAPI promo alone, referral bot abuse, Claude/Fable bypass how-to, bot handle only.

## Risks

- News/reference expansion can generate low-actionability materials if thresholds are too loose.
- Link enrichment can introduce privacy/security risks if arbitrary URLs are fetched without controls.
- Promo context must not become endorsement.
- Access-circumvention must not become exploit instructions.
- First-class `REFERENCE` affects `/materials` type taxonomy and should be decided separately.

## Must Not Change

- No parallel useful-message pipeline.
- No bypass of LLM judge, dedupe, caps, or safety gates.
- No auto-publish.
- No prompt/provider/threshold/settings changes without approval.
- No Telegram/session/proxy changes.
- No backlog/reprocess.
- No hardcoded production raw ids in production logic.
