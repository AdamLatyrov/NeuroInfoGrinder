# Prompt Audit Report

## Metadata

- Review Date: 2026-06-21
- Reviewer Skill: audit-prompts
- Target Prompt: backend-prompt
- Target Path: `backend/.prompt.md`
- Review Scope: Full

## Storage

- Saved to `.docs/changes/prompt/reviews/backend-prompt/review.md`

## Summary Outcome Grid

| Metric | Value |
|---|---|
| Overall Outcome | Fail |
| MUST Failures | 3 |
| SHOULD Advisories | 2 |

## Standards Evaluation

| Standard ID | Standard | Result | Evidence | Notes |
|---|---|---|---|---|
| PRR-M1 | Singular purpose | Fail | The prompt covers date/time API filtering, frontend date/time picker work, guide search process notes, and run instructions. | Split implementation task prompts from operational guide-search notes. |
| PRR-M2 | Valid frontmatter | Pass | No YAML frontmatter is present. Current VS Code docs allow frontmatter to be optional at platform level. | Add `name` and `description` anyway for discoverability after moving to the standard prompt folder. |
| PRR-M3 | Output format declared | Fail | The prompt declares success criteria but not the expected assistant output structure, verification section, or final response contract. | Add an explicit output contract for changed files, tests, and verification. |
| PRR-M4 | Skill routing present | Fail | No reusable workflow routing such as `Load and follow [SKILL.md]` is present. | Either route to a project task-execution skill/instruction or simplify this into a one-off inline task. |
| PRR-S1 | No conflict with other prompts | Pass | `rg --files -g '*.prompt.md'` found only `backend/.prompt.md`. | No prompt-to-prompt trigger conflict found. |
| PRR-S2 | Brevity | Advisory | The prompt includes current state, tasks, guide-search explanation, run commands, and success criteria in one file. | Reduce to the minimum implementation contract or split supporting notes into docs. |
| PRR-S3 | Growth governance alignment | Advisory | The prompt does not mention reuse-before-create, delta-first edits, tests, or auditability. | Add local constraints that preserve existing API patterns and require focused verification. |

## Source Alignment Check

- Source Catalog Consulted: yes
- Sources Evaluated: 1
- Sources Needing Review: 1 before live check, 0 after live check
- File Location Valid: no
- Frontmatter Semantics Valid: yes, but weak for governance
- Prompt Invocation Notes: VS Code workspace prompt files are expected under `.github/prompts` by default, with optional frontmatter fields such as `name`, `description`, `argument-hint`, `agent`, `model`, and `tools`.
- Recommendation: Move the active prompt to `.github/prompts/`, add frontmatter for discoverability, and split this multi-purpose prompt into focused implementation and operations prompts.

## Recommendations

| Recommendation ID | Description | Priority | Status |
|---|---|---|---|
| REC-001 | Move `backend/.prompt.md` to `.github/prompts/` and add `name`, `description`, and optionally `argument-hint`. | High | Proposed |
| REC-002 | Split the prompt into one implementation prompt for pipeline date filtering and a separate operational note or doc for guide-search execution. | High | Proposed |
| REC-003 | Add an explicit output contract: touched files, tests run, verification notes, and any skipped checks. | Medium | Proposed |
| REC-004 | Add reusable workflow routing or remove the prompt-file framing if this is only a one-off task note. | Medium | Proposed |

## History Guard Check

- History File Loaded: no prior history
- Deny-list Entries Applied: 0
- Suppressed Repeat Recommendations: 0
- Notes: First recorded review for this prompt.

## Reasoning Package

| Area | Notes |
|---|---|
| Assumptions | Treated `backend/.prompt.md` as a workspace prompt artifact because it uses the `.prompt.md` extension. |
| Trade-offs | Keeping one prompt is convenient for a single backlog item, but hurts discoverability and repeatability. |
| Blockers | None. |
| One Recommendation | Promote only reusable prompt content into `.github/prompts/`; keep one-off task notes in docs or issue text. |

## Next Actions

1. Create a focused `.github/prompts/pipeline-date-filter.prompt.md`.
2. Move guide-search run instructions to project documentation if they are still useful.
3. Keep runtime guide-generation seed prompts aligned with the stricter fallback JSON contract before reseeding or editing production prompts.

## Aggregate Results Grid

| Prompt | Outcome | MUST Failures | SHOULD Advisories | Report |
|---|---|---:|---:|---|
| backend-prompt | Fail | 3 | 2 | `.docs/changes/prompt/reviews/backend-prompt/review.md` |
