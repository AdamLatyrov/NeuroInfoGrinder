# DISCUSSION_SEGMENT Controlled Production 24h Report - 2026-06-27

Status: FINAL

This report is the final read-only 24h snapshot after controlled DISCUSSION_SEGMENT generation was enabled. No settings, materials, provider config, Telegram session, proxy, queue, backlog, or database state were changed while producing this report.

## Timing

- Enabled at: 2026-06-26T16:23:22+00:00
- Captured at: 2026-06-27T18:19:28.88972+00:00
- Final eligible at: 2026-06-27T16:23:22+00:00
- Full 24h elapsed: true

## Effective Settings Observed

- Mode: CONTROLLED
- Generation enabled: 1
- Full policy details remain governed by the existing production settings and backend controlled-mode guards.

## Activity After Enable

- Segments detected after enable: 1175
- Scorer accepted after enable: 0
- DISCUSSION_SEGMENT_JUDGE calls after enable: 8
- LLM judge success/errors after enable: 25/66
- Generation calls after enable: 13
- Generation success/errors after enable: 9/4
- DRAFT DISCUSSION materials after enable: 0
- PUBLISHED DISCUSSION materials after enable: 0
- DISCUSSION materials on 2026-06-26 UTC: 3
- DISCUSSION materials on 2026-06-27 UTC: 0
- Active knowledge items: 29
- Active DISCUSSION_SEGMENT materials: 3

## Active DISCUSSION_SEGMENT Materials

| ID | Status | Type | Created At | Source Segment | Title |
| --- | --- | --- | --- | --- | --- |
| 23 | DRAFT | GUIDE | 2026-06-26T15:19:04.363769+00:00 | 118 | Fallback на другие модели и статус доступности при задержках API-провайдера |
| 24 | DRAFT | GUIDE | 2026-06-26T15:20:06.899126+00:00 | 119 | Как кеширование может влиять на расход лимитов в Claude Code |
| 25 | DRAFT | SUMMARY | 2026-06-26T15:24:01.502605+00:00 | 120 | Сбор обратной связи о главных болях вайбкодеров |

## Provider Statuses After Enable

| Stage / Status | Count |
| --- | ---: |
| KNOWLEDGE_GENERATION:SUCCESS | 9 |
| KNOWLEDGE_GENERATION:TIMEOUT | 4 |
| LLM_CLUSTER_JUDGE_AND_ROUTING:FAILED | 4 |
| LLM_CLUSTER_JUDGE_AND_ROUTING:SUCCESS | 25 |
| LLM_CLUSTER_JUDGE_AND_ROUTING:TIMEOUT | 54 |
| DISCUSSION_SEGMENT_JUDGE:PROVIDER_NOT_CONFIGURED | 8 |

## Rejection Reasons After Enable

| Reason | Count |
| --- | ---: |
| LOW_COMBINED_SCORE_OR_SIGNAL_COUNT | 664 |
| DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT | 487 |
| DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY | 10 |
| GENERATION_PROVIDER_ERROR | 8 |
| DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE | 3 |
| DISCUSSION_SEGMENT_REJECTED_DAILY_LIMIT | 2 |
| DISCUSSION_SEGMENT_REJECTED_RISK_SENSITIVE_CONTROLLED_MODE | 1 |

## Stop Conditions And Health

- Production services: healthy via `deploy-fast.ps1 -Target status`.
- Public URL: HTTP 200.
- New DISCUSSION materials after enable: 0.
- New published materials after enable: 0.
- Active DISCUSSION materials remain the pre-enable controlled materials 23/24/25.
- Provider instability remains visible through timeouts/failures/provider-not-configured statuses.
- No backlog/requeue/reprocess/material body rewrite/provider config/scoring/threshold changes were made.

## Recommendation

Keep controlled mode gated. Do not broaden DISCUSSION_SEGMENT generation until provider configuration/timeouts and daily-cap semantics are reviewed. Existing materials 23/24/25 should remain read-only unless Adam explicitly approves regeneration or manual editing.
