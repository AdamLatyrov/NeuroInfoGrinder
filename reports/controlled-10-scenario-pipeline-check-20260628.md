# Controlled 10-Scenario Pipeline Check - 2026-06-28

## Scope

- Production URL: `https://neuroinfogrinder.latrdev.ru`
- Dataset: `NIGCHECK-20260628-10-scenarios` (`dataset_id=8136`)
- Final run: `8153`
- Cleanup: completed after evidence capture

## Code Changes Deployed Before Final Run

- Expanded and tightened signal topic keyword routing.
- Stopped `PROMO_ALONE` from being stored as a knowledge signal.
- Preserved `LINK_ONLY` as `NEEDS_LINK_ENRICHMENT` for future link enrichment.
- Added signal source resolution via `dataset_messages -> raw_messages` so signal cards can expose `rawId`, `sourceText`, and `/groups?chatId=...&message=<rawId>`.
- Prevented obvious non-material cases (`LINK_ONLY`, `PROMO_ALONE`, risk/manual-review, `LOW_VALUE`) from entering BGE/semantic material clustering.
- Added signal persistence on hard route rejection for risk/manual-review cases.

## Verification

- Backend tests: `mvn test` passed `113/113`.
- Frontend build: `npm run build` passed.
- Deploy status: backend and frontend healthy; public URL returned HTTP 200.

## Final Run Result

| Scenario | Expected | Actual | Disposition |
|---|---|---|---|
| S01 GUIDE | Material GUIDE | Included in macro GUIDE with S05/S10 | Partial |
| S02 GENERATION | Generation material | Single material created, artifact `ANSWER` | Misclassified type |
| S03 ANSWER | Answer material | Single material created, artifact `GUIDE` | Misclassified type |
| S04 SUMMARY | Summary material | Single material created, artifact `ANSWER` | Misclassified type |
| S05 REFERENCE/OTHER | Reference/other material or catalog item | Included in macro GUIDE with S01/S10 | Misclustered |
| S06 LINK_ONLY | Signal only, no material | `LINK_ONLY`, `NEEDS_LINK_ENRICHMENT`, topic `tools-repos` | Pass |
| S07 PROMO | No material, no normal signal | `REJECTED_SINGLE_MESSAGE`, `PROMO_ALONE`, no signal | Pass |
| S08 RISK | Risk/manual-review signal only | `RISK_OR_REFERRAL_SIGNAL`, `RISK_SENSITIVE_MANUAL_REVIEW`, topics `abuse-risk,security-privacy` | Pass |
| S09 LOW_VALUE | No material, no signal | `REJECTED_SINGLE_MESSAGE`, `CHAT_CONTEXT_ONLY`, no signal | Pass |
| S10 DUPLICATE | No separate duplicate material | Included in macro GUIDE with S01/S05 | Partial |

## Created During Final Run

- Materials: `49`, `50`, `51`, `52`
- Signals: `22`, `23`

## Cleanup Evidence

- Deleted test signal topics: `4`
- Deleted test signals: `3` across runs `8151`, `8152`, `8153`
- Deleted test replay runs: `8151`, `8152`, `8153`
- Deleted test dataset: `8136`
- Verification after cleanup:
  - `remaining_dataset_messages=0`
  - `remaining_runs=0`
  - `remaining_materials=0`
  - `remaining_signals=0`

## Current Public Signal Counts After Cleanup

- `models-releases`: 0
- `free-tokens-quotas`: 0
- `providers-routers`: 1
- `api-integrations`: 1
- `outages-limits`: 1
- `tools-repos`: 1
- `agents-prompts`: 1
- `abuse-risk`: 2
- `security-privacy`: 0
- `pricing-costs`: 0

## Findings

- Signal routing safety is improved: link-only, promo, risk, and low-value no longer leak into materials.
- Topic assignment is less noisy: free-token/pricing topics no longer contain the old unrelated promo/risk/outage mix.
- Material type classification remains weak: generation/answer/summary scenarios were created, but artifact types were incorrect.
- Macro clustering still over-merges guide/reference/duplicate content into one GUIDE.

## Recommended Next Fixes

- Add artifact-type gate tests for `GENERATION`, `ANSWER`, `SUMMARY`, `REFERENCE/OTHER`.
- Split reference/link catalog candidates from guide macro clusters.
- Add duplicate-specific assertion so duplicate content links to existing cluster but does not inflate source mix.
- Add `primaryTopic` storage to avoid duplicate rendering when secondary topics are introduced later.
