# Full Pipeline 20k Generation Run - 2026-07-01

## Summary

- Parent dataset: `15764` (`FULL_PIPELINE_20K_2026_06_30`), latest 20,000 non-empty production `raw_messages`.
- Initial direct V2 attempt with `maxMessages=20000` was clamped by backend to `5000` and failed on model-worker timeout; transaction rolled back, no committed run/materials.
- 20 chunks of 1,000 also hit model-worker HTTP timeout on BGE; stopped the runner.
- Final execution used 100 mini datasets of 200 messages each: `15814-15913`.
- Final full pipeline result: `100/100` mini runs completed successfully.
- Total messages processed: `20,000`.
- Provider calls: `465`.
- Estimated provider cost: `$5.148730`.
- DRAFT materials created: `116`.
- No auto-publish was enabled.

## Material Output

- By artifact type:
  - `REFERENCE`: `72`
  - `ANSWER`: `23`
  - `SUMMARY`: `13`
  - `GUIDE`: `5`
  - `GENERATION`: `3`
- By source cluster type:
  - `SINGLE_MESSAGE`: `77`
  - `MACRO`: `39`

## Post-Run Audit

- Audit command: `node scripts/audit-daily-materials-signals.js --since-msk "2026-06-30 03:00"`
- Audit outputs:
  - `reports/daily-materials-signals-audit-20260701T035836Z.html`
  - `reports/daily-materials-signals-audit-20260701T035836Z.md`
  - `reports/daily-materials-signals-audit-20260701T035836Z.json`
- Audit counts:
  - Materials in window: `124`
  - Flagged materials: `115`
  - Signals in window: `241`
  - Flagged signals: `167`

## Material Issue Groups

- `P2_WEAK_OTHER_SINGLE_SOURCE`: `37`
- `P2_POSSIBLY_USEFUL_BUT_SINGLE_SOURCE`: `27`
- `P1_RISK_MANUAL_REVIEW`: `20`
- `P0_RULES_ONBOARDING_DELETE`: `18`
- `KEEP_OR_NO_FLAGS`: `10`
- `P2_JOB_POST_NOT_MATERIAL`: `5`
- `P1_UNVERIFIED_MODEL_CLAIM`: `4`
- `P2_EVENT_NOT_MATERIAL`: `3`

## Flagged Material IDs By Group

- `P0_RULES_ONBOARDING_DELETE`: `322,318,316,309,307,305,304,302,298,296,286,284,273,269,268,266,263,261`
- `P1_RISK_MANUAL_REVIEW`: `333,319,312,299,297,294,288,287,272,270,257,250,245,243,239,230,229,224,219,212`
- `P1_UNVERIFIED_MODEL_CLAIM`: `334,331,262,255`
- `P2_EVENT_NOT_MATERIAL`: `317,258,223`
- `P2_JOB_POST_NOT_MATERIAL`: `323,303,282,281,252`
- `P2_POSSIBLY_USEFUL_BUT_SINGLE_SOURCE`: `329,326,324,321,315,314,311,310,308,301,295,293,276,275,267,265,259,256,254,246,240,238,236,232,228,221,215`
- `P2_WEAK_OTHER_SINGLE_SOURCE`: `330,327,313,306,300,292,291,290,289,285,283,280,279,278,277,271,264,260,253,244,242,241,237,235,234,233,231,227,226,222,220,218,217,216,214,213,211`
- `KEEP_OR_NO_FLAGS`: `332,328,325,320,274,251,249,248,247,225`

## Evidence Files

- Runner TSV: `reports/full-pipeline-20k-mini-replays-20260630.tsv`
- Summary JSON: `reports/full-pipeline-20k-summary-20260630.json`
- Materials JSONL: `reports/full-pipeline-20k-materials-20260630.jsonl`
- Parent dataset SQL: `reports/create-full-pipeline-20k-dataset-20260630.sql`
- Mini dataset SQL: `reports/create-full-pipeline-20k-mini-datasets-20260630.sql`
- Mini runner script: `reports/run-full-pipeline-20k-mini-replays-20260630.sh`

## Notes

- The run shows the current production pipeline still over-materializes single-message candidates despite latest classifier guards.
- Active bad materials were not deleted in this step; cleanup requires explicit approval/scope.
- Signals were not deleted; there is still no public signal delete endpoint.
