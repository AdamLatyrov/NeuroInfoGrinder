# NIGTOP Controlled Topic Check - 2026-06-29

## Summary

- Environment: production
- Final dataset: `8400` (`NIGTOP-20260629-topic-check-final-r4`)
- Final run: `8420`
- Status: `PASSED`
- Replay result: `COMPLETED`, `10/10` messages processed, `providerCallsTotal=10`, `error=null`
- Active output left for UI review: 5 DRAFT materials and 2 signals

## Validation Grid

| Scenario | Expected | Actual | Topics | Result |
|---|---|---|---|---|
| `NIGTOP-S01-GUIDE` | One `GUIDE` material | Material `87`, `GUIDE` | `agents-prompts`, `api-integrations`, `outages-limits`, `providers-routers` | Pass |
| `NIGTOP-S02-GENERATION` | One `GENERATION` material | Material `88`, `GENERATION` | `agents-prompts`, `models-releases` | Pass |
| `NIGTOP-S03-ANSWER` | One `ANSWER` material | Material `89`, `ANSWER` | `api-integrations`, `providers-routers` | Pass |
| `NIGTOP-S04-SUMMARY` | One `SUMMARY` material | Material `90`, `SUMMARY` | `api-integrations`, `free-tokens-quotas`, `outages-limits`, `providers-routers` | Pass |
| `NIGTOP-S05-REFERENCE` | One `REFERENCE` material | Material `91`, `REFERENCE` | `security-privacy`, `tools-repos` | Pass |
| `NIGTOP-S06-LINK-ONLY` | Signal only, no material | Signal `44`, `LINK_ONLY`, `NEEDS_LINK_ENRICHMENT` | `free-tokens-quotas` | Pass |
| `NIGTOP-S07-PROMO` | No material, no signal | `REJECTED_SINGLE_MESSAGE`, `PROMO_ALONE` | none | Pass |
| `NIGTOP-S08-RISK` | Risk signal only, no material | Signal `45`, `RISK_OR_REFERRAL_SIGNAL`, `ABUSE_OR_FRAUD` | `abuse-risk`, `outages-limits` | Pass |
| `NIGTOP-S09-LOW-VALUE` | No material, no signal | `REJECTED_SINGLE_MESSAGE`, `CHAT_CONTEXT_ONLY` | none | Pass |
| `NIGTOP-S10-DUPLICATE` | No separate material | `llm_skip_reason=DUPLICATE`, dedupe group `85`, no material | none | Pass |

## Materials

| ID | Type | Source |
|---:|---|---|
| `87` | `GUIDE` | `NIGTOP-S01-GUIDE` |
| `88` | `GENERATION` | `NIGTOP-S02-GENERATION` |
| `89` | `ANSWER` | `NIGTOP-S03-ANSWER` |
| `90` | `SUMMARY` | `NIGTOP-S04-SUMMARY` |
| `91` | `REFERENCE` | `NIGTOP-S05-REFERENCE` |

## Signals

| ID | Type | Status | Source | Topics |
|---:|---|---|---|---|
| `44` | `LINK_ONLY` | `NEEDS_LINK_ENRICHMENT` | `NIGTOP-S06-LINK-ONLY` | `free-tokens-quotas` |
| `45` | `RISK_OR_REFERRAL_SIGNAL` | `ABUSE_OR_FRAUD` | `NIGTOP-S08-RISK` | `abuse-risk`, `outages-limits` |

## Fixes Applied During The Check

- Added `knowledge_item_topics` assignment for generated materials.
- Kept explicit status summaries as `SUMMARY` instead of outage `GUIDE` when the source says `сводка/summary/итог`.
- Prevented safe reference wording like `не инструкция по обходу` from becoming a false abuse-risk signal.
- Added risk detection for `bypass rate limit`, account farming, `invite=`, referral chains, and anti-abuse gateway bypass.
- Restored the missing `MessageUsefulnessBenchmarkService` used by the existing benchmark API/test.
- Improved duplicate suppression by canonicalizing test/service prefixes and skipping `llm_skip_reason=DUPLICATE` rows in single-message materialization.

## Verification

- Targeted backend tests passed: `MessageUsefulnessClassifierTest`, `KnowledgeSignalServiceTest`, `MessageUsefulnessBenchmarkServiceTest`.
- Full backend `mvn -q test` passed.
- Backend-only deploys completed; production health after deploy was OK.
- Final counts: active materials `5`, signals `2`, material-topic links `14`, signal-topic links `3`.

## Residual Notes

- Final validation outputs are intentionally left active for UI review.
- Topic assignment is rule-based and may assign multiple relevant topics per material. This is expected for cross-cutting API/provider/outage examples.
