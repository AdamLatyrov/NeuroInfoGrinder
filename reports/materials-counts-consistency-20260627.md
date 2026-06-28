# Materials Counts Consistency - 2026-06-27

Status: fixed and deployed. No material rows were deleted, archived, regenerated, edited, or mass-updated.

## Root Cause

The UI showed `Все 30`, but visible type tabs summed to `24` because six active DRAFT materials had legacy/unknown raw `artifact_type` values that were not represented by visible tabs.

Raw active type distribution before fix:

- GUIDE: 5
- guide: 6
- generation: 9
- answer: 1
- SUMMARY: 3
- cluster_summary: 3
- newsBrief: 1
- instruction: 1
- REFERENCE: 1

## Missing Six Materials

| material_id | title | type | status | candidateType | created_at | sourceCount |
|---:|---|---|---|---|---|---:|
| 13 | ╨Я╤А╨╕╨▓╨╡╤В╤Б╤В╨▓╨╡╨╜╨╜╨╛╨╡ ╤Б╨╛╨╛╨▒╤Й╨╡╨╜╨╕╨╡ ╨┤╨╗╤П ╨╜╨╛╨▓╤Л╤Е ╤Г╤З╨░╤Б╤В╨╜╨╕╨║╨╛╨▓ ╤Б╨╛╨╛╨▒╤Й╨╡╤Б╤В╨▓╨░ | cluster_summary | DRAFT | MACRO | 2026-06-25T12:09:03.726556+00:00 | 4 |
| 16 | Google DeepMind ╤Б╤В╨░╨╗╨║╨╕╨▓╨░╨╡╤В╤Б╤П ╤Б ╨╜╨╛╨▓╨╛╨╣ ╨▓╨╛╨╗╨╜╨╛╨╣ ╤Г╤Е╨╛╨┤╨░ ╨╕╤Б╤Б╨╗╨╡╨┤╨╛╨▓╨░╤В╨╡╨╗╨╡╨╣ ╨║ ╨║╨╛╨╜╨║╤Г╤А╨╡╨╜╤В╨░╨╝ | newsBrief | DRAFT | MACRO | 2026-06-25T19:02:14.562836+00:00 | 2 |
| 17 | ╨Ю╨▒╤Б╤Г╨╢╨┤╨╡╨╜╨╕╨╡ ╨┐╤А╨╛╨▓╨╡╤А╨║╨╕ ╨╝╨╛╨┤╨╡╨╗╨╕ DeepSeek ╨▓╨╝╨╡╤Б╤В╨╛ Opus 4.8 | cluster_summary | DRAFT | MACRO | 2026-06-25T19:03:13.624458+00:00 | 2 |
| 19 | ╨в╤А╨╡╨▒╨╛╨▓╨░╨╜╨╕╨╡ ╨║ ╤Г╨║╨░╨╖╨░╨╜╨╕╤О 30 ╨╜╨░╨╕╨▒╨╛╨╗╨╡╨╡ ╤З╨░╤Б╤В╤Л╤Е ╨╜╨░╨▓╤Л╨║╨╛╨▓ ╨┐╨╛ ╤Б╨┐╨╡╤Ж╨╕╨░╨╗╤М╨╜╨╛╤Б╤В╨╕ | instruction | DRAFT | SINGLE_MESSAGE | 2026-06-26T12:39:10.767033+00:00 | 1 |
| 21 | ╨Ь╨╛╤Б╨║╨▓╨░: ╨│╨╕╨▒╤А╨╕╨┤╨╜╤Л╨╡ ╨▓╨░╨║╨░╨╜╤Б╨╕╨╕ ╨▓ digital, ╨║╨╛╨╜╤В╨╡╨╜╤В-╨┐╤А╨╛╨┤╨░╨║╤И╨╡╨╜╨╡ ╨╕ B2B-╨┐╤А╨╛╨┤╨░╨╢╨░╤Е | cluster_summary | DRAFT | MACRO | 2026-06-26T12:42:26.826647+00:00 | 2 |
| 32 | Sol: ╤А╨╡╨╢╨╕╨╝╤Л ╤А╨░╤Б╤Б╤Г╨╢╨┤╨╡╨╜╨╕╤П, ╨╕╨╜╤Д╤А╨░╤Б╤В╤А╤Г╨║╤В╤Г╤А╨░ ╨╕ ╨╖╨░╤П╨▓╨╗╨╡╨╜╨╜╤Л╨╡ ╨╝╨╡╤А╤Л ╨▒╨╡╨╖╨╛╨┐╨░╤Б╨╜╨╛╤Б╤В╨╕ | REFERENCE | DRAFT | SINGLE_MESSAGE | 2026-06-27T05:55:42.770953+00:00 | 1 |

## Fix

- Backend now normalizes material types for list/count/filtering: `GUIDE`, `GENERATION`, `ANSWER`, `SUMMARY`, and `OTHER`.
- Unknown/null/legacy values are counted and filtered as `OTHER`.
- `/api/v1/materials` now returns stable `counts.total`, `counts.byType`, and `counts.byStatus` using the same base filter as the list endpoint.
- Frontend labels are Russian: `Гайды`, `Генерации`, `Ответы`, `Сводки`, `Другое`.
- `Другое` tab is visible only when count > 0.
- English labels `Generation` and `Answer` were removed from `/materials` UI labels.

## Production Verification

API after deploy:

`/api/v1/materials?page=0&size=1`

`totalElements=30`

`counts.byType={"GUIDE":11,"GENERATION":9,"ANSWER":1,"SUMMARY":3,"OTHER":6}`

Visible type-tab sum: 30

`/api/v1/materials?contentType=OTHER` returned `totalElements=6` and ids: 32, 21, 19, 17, 16, 13.

`/api/v1/materials?contentType=GENERATION` returned `totalElements=9`.

Status counts use the same base filter: {"DRAFT":30}.

## Tests And Deploy

- Targeted backend: `mvn -Dtest=KnowledgeMaterialServiceTest test` passed, 4 tests.
- Full backend: `mvn test` passed, 70 tests.
- Frontend: `npm run build` passed.
- Deploy: `./scripts/deploy-fast.ps1 -Target all` completed; backend/frontend healthy, public URL HTTP 200.
- Backend logs: no recent ERROR/WARN/Exception lines.
- Browser check: blocked because Kimi WebBridge health returned `running=false`; API/public verification completed.

## Data Repair Recommendation

No DB repair was performed. If later desired, review whether `cluster_summary` should become `SUMMARY` and whether `REFERENCE` should become a first-class tab/type. Do not mass-update without approval.
