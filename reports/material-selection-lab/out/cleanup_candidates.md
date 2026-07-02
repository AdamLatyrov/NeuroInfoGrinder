# Cleanup Candidates

Cleanup was partially executed after Adam repeated the request to remove old/unneeded files.

Deleted: high-confidence temp/smoke artifacts and superseded daily-audit/message-lab report iterations listed below. Kept: current Material Selection Lab outputs, fixed 20k snapshot, canonical evidence bundle, latest post-run audit, and post-cleanup baseline audit.

## Keep

| Path/Pattern | Reason |
|---|---|
| `reports/material-selection-lab/` | Current offline Material Selection Lab outputs. |
| `reports/raw-messages-20k-20260630.jsonl` | Fixed 20k snapshot used by the lab. |
| `reports/full-pipeline-20k-generation-20260701/` | Canonical evidence bundle for the failed 20k production generation run. |
| `reports/daily-materials-signals-audit-20260701T035836Z.*` | Latest post-20k-generation audit. |
| `reports/daily-materials-signals-audit-20260630T201505Z.*` | Important post-cleanup baseline before 20k generation. |
| `reports/message-lab-20k-20260630-v2.*` | Latest calibrated legacy 20k message lab result. |
| `reports/message-lab-algorithm-comparison-20k-20260630-v3.*` | Latest practical legacy algorithm comparison referenced in memory. |

## Removed

| Path/Pattern | Confidence | Reason |
|---|---|---|
| `tmp_replay_zaur_batch_20260630.jsonl` | High | Temporary controlled replay input; not needed for current lab. |
| `reports/message-lab-algorithm-comparison-smoke-20260630.*` | High | Smoke artifact superseded by full 20k comparisons. |
| `reports/message-lab-20k-20260630.*` | Medium | Superseded by `message-lab-20k-20260630-v2.*`. |
| `reports/message-lab-algorithm-comparison-20k-20260630.*` | Medium | Superseded by later comparisons. |
| `reports/message-lab-algorithm-comparison-20k-20260630-v2.*` | Medium | Superseded by `-v3`. |
| `reports/message-lab-algorithm-comparison-20k-20260630-final.*` | Medium | Superseded by `-v3` for current handoff. |
| `reports/daily-materials-signals-audit-20260630T074436Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T080436Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T080811Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T085839Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T090844Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T193742Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T194533Z.*` | Medium | Old daily-audit iteration superseded by later reports. |
| `reports/daily-materials-signals-audit-20260630T195053Z.*` | Medium | Superseded by cleanup baseline `20260630T201505Z.*`. |

## Archive Candidates After Approval

| Path/Pattern | Confidence | Reason |
|---|---|---|
| `reports/full-pipeline-20k-generation-run-20260701.md` | Medium | Duplicated into canonical evidence bundle as `run-report.md`; archive rather than delete if external links may exist. |
| `reports/full-pipeline-20k-materials-20260630.jsonl` | Medium | Duplicated/normalized into canonical evidence bundle; archive rather than delete. |
| `reports/full-pipeline-20k-summary-20260630.json` | Medium | Duplicated/normalized into canonical evidence bundle; archive rather than delete. |
| `reports/full-pipeline-20k-mini-replays-20260630.tsv` | Medium | Duplicated as `mini-runs.tsv` in canonical evidence bundle; archive rather than delete. |

## Still Requires Explicit Approval

Say one of:

- `заархивируй archive candidates`
- `удали archive candidates`
- `ничего больше не удаляй`
