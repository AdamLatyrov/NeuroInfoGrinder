# Full Pipeline 20k Generation Evidence - 2026-07-01

This folder contains the evidence bundle for Adam's production full-pipeline generation run over the latest 20,000 non-empty Telegram raw messages.

## Files

- `materials.valid.json` - valid JSON array of all `116` generated DRAFT materials, including body JSON, topics, sources, source raw payloads, and provider calls for the material's run.
- `run-summary.valid.json` - valid JSON summary of the 100 mini replay runs.
- `mini-runs.tsv` - per-mini-run execution result table, copied from the server runner output.
- `post-run-audit.json` - structured daily audit after generation.
- `post-run-audit.md` - Markdown daily audit after generation.
- `run-report.md` - human-readable run report created immediately after the 20k run.
- `export-valid-materials-json.sql` - read-only SQL used to export `materials.valid.json` and `run-summary.valid.json`.
- `run-export-valid-materials-json.sh` - server-side runner used for the export.
- `agent-pipeline-research-brief.md` - detailed agent handoff/research brief focused on why the pipeline generated bad materials and what to investigate.

## Key Facts

- Parent dataset: `15764`.
- Final mini datasets: `15814-15913`.
- Full V2 runs completed: `100/100`.
- Messages processed: `20,000`.
- Provider calls: `465`.
- Estimated cost: `$5.148730`.
- Generated materials: `116` DRAFT materials.
- Post-run audit: `124` materials in window, `115` flagged.
- Main failure mode: material selection/eligibility failed before generation; the system over-materialized single-message and single-source candidates.

## Important Caveat

This folder is evidence only. It does not represent accepted/published knowledge. Most generated materials are flagged by the audit and should not be treated as good production output without review or cleanup.
