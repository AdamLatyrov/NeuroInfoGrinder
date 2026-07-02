# Artifact Type Gate Check - 2026-06-29

## Scope

- Environment: production
- Code path: `backend/2.0` replay/materialization pipeline
- Controlled dataset: `8239` (`NIGART-20260629-artifact-gate`)
- Final validation run: `8258`
- Cleanup: completed after evidence capture

## Changes Deployed

- Added backend-owned required artifact type for single-message candidates.
- Forced generation prompts to include required artifact type and require returned `artifactType` to match it.
- Sanitized generated material JSON before persistence: stored `artifactType` follows backend-required type, while mismatched provider type is preserved as `providerArtifactType`.
- Added explicit classifier branches for `GENERATION`, `ANSWER`, and `SUMMARY`.
- Split semantic clustering by material bucket so `REFERENCE` does not merge into `GUIDE` clusters.
- Rejected unusable generation payloads that look like `REJECTED_NEEDS_MORE_CONTEXT` despite provider `SUCCESS`.
- Filtered duplicate-like source messages from macro material source lists using canonical source text.

## Verification

- Local backend: `mvn -q test` passed.
- Backend deploy: `scripts/deploy-fast.ps1 -Target backend` passed.
- Production health: backend/frontend/model-worker/postgres/redis/ssh-socks/tor healthy.
- Public URL: HTTP 200.

## Final Validation Result

| Scenario | Expected | Actual | Disposition |
|---|---|---|---|
| GUIDE | `GUIDE` material | Material `63`, `GUIDE` | Pass |
| GENERATION | `GENERATION` material | Material `64`, `GENERATION`; provider originally returned `OTHER`, backend corrected and stored `providerArtifactType=OTHER` | Pass |
| ANSWER | `ANSWER` material | Material `65`, `ANSWER` | Pass |
| SUMMARY | `SUMMARY` material | Material `66`, `SUMMARY` | Pass |
| REFERENCE | `REFERENCE` material | Material `67`, `REFERENCE` | Pass |
| DUPLICATE | No separate duplicate material and no source pollution | No separate material; duplicate not present in material `63` sources | Pass |

## Cleanup Evidence

- Removed earlier NIGTEST test materials `37` and `38`.
- Soft-deleted controlled run materials `54-67` before deleting the controlled dataset.
- Deleted controlled dataset `8239` through the dataset API.
- Final cleanup counts:
  - `active_old_nigtest=0`
  - `remaining_dataset=0`
  - `remaining_runs=0`
  - `remaining_messages=0`

## Remaining System Risks

- Artifact typing is now guarded for tested single-message and macro paths, but discussion-segment material types should get the same controlled scenario coverage.
- Provider timeout/error behavior is still a reliability risk; backend fallback prevents total loss for lightweight allowed cases, but provider observability should distinguish fallback-created vs provider-generated material.
- Macro clustering still uses simple topic heuristics after bucket split; longer mixed discussions may need stronger cluster-purity scoring.
