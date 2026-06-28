# backend-prompt History

## 2026-06-21 Review

| Field | Value |
|---|---|
| Prompt | backend-prompt |
| Path | `backend/.prompt.md` |
| Outcome | Fail |
| Reviewer Skill | audit-prompts |
| Report | `.docs/changes/prompt/reviews/backend-prompt/review.md` |

## Findings

| Code | Status | Summary |
|---|---|---|
| PRR-M1 | Failed | Prompt mixes implementation work, operational guide-search notes, and run instructions. |
| PRR-M3 | Failed | Prompt has success criteria but no assistant output contract. |
| PRR-M4 | Failed | Reusable workflow routing is absent. |
| PRR-S2 | Advisory | Prompt is longer than necessary for a repeatable implementation prompt. |
| PRR-S3 | Advisory | Growth-governance expectations are not explicit. |

## Recommendations

| Recommendation ID | Status | Description |
|---|---|---|
| REC-001 | Proposed | Move `backend/.prompt.md` to `.github/prompts/` and add prompt metadata. |
| REC-002 | Proposed | Split implementation and operational guide-search content. |
| REC-003 | Proposed | Add explicit output and verification contract. |
| REC-004 | Proposed | Add workflow routing or keep this as non-prompt task documentation. |
