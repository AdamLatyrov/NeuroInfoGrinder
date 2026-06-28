# Message Usefulness LLM Judge Dry-Run - 2026-06-27

## Scope

Controlled LLM Judge dry-run for selected `MessageUsefulnessClassifier` single-message candidates.

No material generation was enabled or executed. No backlog/reprocess/settings/caps/threshold/provider-config/Telegram/public-discovery changes were made.

## Pre-Check

| Metric | Value |
|---|---:|
| Active materials baseline for this task | 29 |
| Knowledge items total | 33 |
| Active DISCUSSION_SEGMENT materials | 3 |
| Provider calls total | 197 |
| Existing KNOWLEDGE_GENERATION calls total | 38 |
| Existing LLM judge calls total | 140 |

Note: earlier known active material count was `30`, but this task's pre-check observed `29` before any judge dry-run. This task used `29` as the current baseline and verified it stayed unchanged.

## Selected Candidates

| raw_id | class | route | proposed_type | expected verdict | reason |
|---:|---|---|---|---|---|
| 11142 | NEWS_UPDATE | SINGLE_MESSAGE | REFERENCE | Accept if source context enough | Mandatory GPT-5.6 candidate |
| 11484 | RESOURCE_REFERENCE | SINGLE_MESSAGE | REFERENCE | Likely accept if source text enough | Mandatory GitHub/resource candidate |
| 10172 | NEWS_UPDATE | SINGLE_MESSAGE | REFERENCE | Accept if source context enough | Recent GPT-5.6 news candidate |
| 12038 | RESOURCE_REFERENCE | SINGLE_MESSAGE | REFERENCE | Accept if context enough | Recent GitHub tool candidate |
| 11434 | RESOURCE_REFERENCE | SINGLE_MESSAGE | REFERENCE | Likely accept | Recent OpenCode Proxy repo candidate |
| 8447 | LINK_WITH_CONTEXT | SINGLE_MESSAGE | REFERENCE | Accept cautiously or reject promo/referral | API/provider reference with referral/provider-claim caveat |
| 11193 | LINK_ONLY | REJECT | n/a | Pre-gate reject | Negative control |

## Execution

The existing prompt-test API could not be used because `ModelhubProviderGateway.recordCall` requires primitive `long runId`; prompt-test calls with `run_id=NULL` failed before provider response with `NullPointerException` and created no judge calls.

To keep the run judge-only and auditable, a synthetic replay run was created only as a provider-call container:

- `run_id=6601`
- `mode=JUDGE_DRY_RUN_ONLY`
- `run_name=message-usefulness-llm-judge-dry-run-20260627`
- `total_messages=0`
- no `replay_run_messages`
- no `knowledge_items`

Six direct provider calls were made to stage `LLM_CLUSTER_JUDGE_AND_ROUTING` and persisted as `provider_calls` rows under run `6601`. No `KNOWLEDGE_GENERATION` call was made.

Negative control raw `11193` was not sent to LLM because classifier route is `REJECT` with `NEEDS_LINK_ENRICHMENT`.

## Judge Results

| raw_id | contentClass | proposedType | provider_call_id | decision | judgeType | confidence | accepted | grounded | hallucinationRisk | shouldMaterializeLater |
|---:|---|---|---:|---|---|---:|---|---|---|---|
| 11142 | NEWS_UPDATE | REFERENCE | 209 | NEWS_REFERENCE_MATERIAL_CANDIDATE | REFERENCE | 0.78 | yes | true | medium | YES_CONTROLLED |
| 11484 | RESOURCE_REFERENCE | REFERENCE | 210 | RESOURCE_REFERENCE_MATERIAL_CANDIDATE | REFERENCE | 0.74 | yes | true | MEDIUM | YES_CONTROLLED_WITH_SOURCE_CAVEAT |
| 10172 | NEWS_UPDATE | REFERENCE | 211 | HTTP_502 | n/a | n/a | no | n/a | n/a | NO_RESULT |
| 12038 | RESOURCE_REFERENCE | REFERENCE | 212 | HTTP_502 | n/a | n/a | no | n/a | n/a | NO_RESULT |
| 11434 | RESOURCE_REFERENCE | REFERENCE | 213 | HTTP_502 | n/a | n/a | no | n/a | n/a | NO_RESULT |
| 8447 | LINK_WITH_CONTEXT | REFERENCE | 214 | HTTP_502 | n/a | n/a | no | n/a | n/a | NO_RESULT |
| 11193 | LINK_ONLY | n/a | n/a | PRE_GATE_REJECTED | n/a | 0 | no | false | low | NO |

Detailed call rows: `reports/message-usefulness-llm-judge-calls-20260627.csv`.

Classifier-vs-judge table: `reports/message-usefulness-llm-judge-vs-classifier-20260627.csv`.

## Accepted Candidate Analysis

raw `11142` was accepted as `NEWS_REFERENCE_MATERIAL_CANDIDATE` with `REFERENCE`, confidence `0.78`. The judge correctly treated it as a news/reference item, not a guide. It noted medium hallucination risk because benchmark/provider claims must be treated as source claims, not verified facts.

raw `11484` was accepted as `RESOURCE_REFERENCE_MATERIAL_CANDIDATE` with `REFERENCE`, confidence `0.74`. The judge correctly identified source-context limitations because the normalized text had no visible URL. Later materialization should include repository/license/activity/issues/security caveat and avoid expanding beyond source claims.

## Failed/Inconclusive Candidates

raw `10172`, `12038`, `11434`, and `8447` received provider HTTP `502` before parsed judge decisions. These are inconclusive, not classifier false positives and not safe materialization candidates from this dry-run.

## Negative Control

raw `11193` stayed pre-gate rejected as `LINK_ONLY` / `NEEDS_LINK_ENRICHMENT`. No LLM judge call was made, as expected.

## Post-Check

| Metric | Value |
|---|---:|
| Active materials after dry-run | 29 |
| Knowledge items total after dry-run | 33 |
| Active DISCUSSION_SEGMENT materials after dry-run | 3 |
| Provider calls total after dry-run | 203 |
| Dry-run judge calls under run 6601 | 6 |
| Dry-run KNOWLEDGE_GENERATION calls under run 6601 | 0 |
| Dry-run knowledge items under run 6601 | 0 |
| Public URL | 200 |
| Backend logs | clean |

## No-Generation Verification

- No material rows were created for run `6601`.
- No `KNOWLEDGE_GENERATION` calls were created for run `6601`.
- Active material count stayed unchanged at task baseline `29`.
- The negative control did not reach LLM.
- No settings, caps, thresholds, provider config, Telegram session, public discovery guard, or auto-publish behavior changed.

## Recommendation

If Adam approves controlled materialization later, start only with:

1. raw `11142` as DRAFT `REFERENCE` or short `SUMMARY`, with provider/source caveat and no unsupported benchmark claims.
2. raw `11484` as DRAFT `REFERENCE`, with repository/license/activity/issues/security caveat and explicit note that the visible normalized text lacked the repository URL.

Do not materialize raw `11193`.

Do not materialize HTTP `502` candidates (`10172`, `12038`, `11434`, `8447`) from this dry-run; optionally retry judge-only later if provider stability is needed.
