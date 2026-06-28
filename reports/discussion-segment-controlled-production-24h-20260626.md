# DISCUSSION_SEGMENT Controlled Production 24h - Initial Snapshot - 2026-06-26

## Verdict

This is an initial snapshot, not a completed 24h report. The effective enable time is `2026-06-26T16:23:22Z`, and the snapshot was captured at `2026-06-26T16:38:00Z`.

## Snapshot Counts

| metric | value |
|---|---:|
| Segments detected after enable time | 0 |
| Scorer accepted after enable time | 0 |
| LLM judge calls after enable time | 0 |
| LLM judge successes after enable time | 0 |
| LLM judge errors after enable time | 0 |
| Generation calls after enable time | 0 |
| Generation successes after enable time | 0 |
| Generation errors after enable time | 0 |
| DISCUSSION_SEGMENT DRAFT materials after enable time | 0 |
| DISCUSSION_SEGMENT PUBLISHED materials after enable time | 0 |
| Active DISCUSSION_SEGMENT materials total | 3 |
| DISCUSSION_SEGMENT materials today | 3 |
| Active knowledge items total | 24 |
| Materials missing sources after enable time | 0 |
| Segments missing sources after enable time | 0 |

## Guard Checks

| guard | status | evidence |
|---|---|---|
| Fresh-only gate | PASS | no materials after effective enable time yet; old controlled materials predate enable time |
| DRAFT-only | PASS | 0 PUBLISHED materials after enable time |
| Daily cap 3 | PASS_WITH_NOTE | daily count is already 3 because controlled manual materials 23, 24, 25 were created earlier today; additional controlled generation should be blocked until next UTC day |
| Per chat/topic/day cap 1 | NOT_OBSERVED | no fresh post-enable segments yet |
| Require LLM accepted | NOT_OBSERVED | no post-enable judge calls yet |
| Risk-sensitive skip | NOT_OBSERVED | no post-enable risk-sensitive candidate yet |
| Provider/generation error stop | NOT_OBSERVED | no post-enable provider/generation calls yet |
| No backlog/reprocess | PASS | no old historical windows were requeued or reprocessed during this step |
| Source/trace visibility | PASS_FOR_EXISTING_CONTROLLED_MATERIALS | last controlled materials each have 6 sources; post-enable material count is 0 |

## Existing Controlled Materials

| id | segmentId | type | status | sourceCount | createdAt |
|---:|---:|---|---|---:|---|
| 25 | 120 | SUMMARY | DRAFT | 6 | 2026-06-26T15:24:01Z |
| 24 | 119 | GUIDE | DRAFT | 6 | 2026-06-26T15:20:06Z |
| 23 | 118 | GUIDE | DRAFT | 6 | 2026-06-26T15:19:04Z |

## Service Health

| service/check | result |
|---|---|
| backend | healthy |
| frontend | healthy |
| model-worker | healthy |
| postgres | healthy |
| redis | healthy |
| ssh-socks | healthy |
| tor | healthy |
| public URL | HTTP 200 |
| backend recent logs | startup/health only; no runtime errors observed in sampled logs |

## Next Monitoring Window

Run the final 24h report after `2026-06-27T16:23:22Z`. Stop earlier if any stop condition occurs: more than 3 DISCUSSION_SEGMENT materials in the UTC day, any auto-created PUBLISHED material, rejected/needs-context segment materialized, risk-sensitive material auto-generated, missing sources/trace, duplicate spam, repeated provider errors, hallucinated material, backend runtime errors, or public discovery regression.

## Artifacts

| artifact | purpose |
|---|---|
| `reports/discussion-controlled-production-monitor-raw-20260626.json` | raw monitor snapshot |
| `reports/discussion-segment-controlled-mode-enable-20260626.md` | enable report |
| `reports/discussion-segment-controlled-mode-enable-20260626.json` | machine-readable enable report |
