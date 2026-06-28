# Material Style And Source Transparency - 2026-06-26

## Verdict

Future generation prompt/style rules are fixed, the material detail API now exposes multi-source transparency, and the material detail UI now renders ordered sources, how-built steps, and trace/provider call diagnostics. Existing materials `23`, `24`, and `25` were verified read-only and were not regenerated or rewritten.

Important: existing bodies for `23`, `24`, and `25` still contain old meta-chat phrasing because they were generated before this fix. Regeneration or manual editing is recommended, but was not performed without approval.

## Files Changed

| file | change |
|---|---|
| `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2Service.java` | Added shared generation style instructions for cluster, single-message, and discussion-segment generation prompts. |
| `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/materials/KnowledgeMaterialService.java` | Expanded `/api/v1/materials/{id}` contract with ordered source fields, `howBuiltSteps`, discussion segment metadata, provider preview fields, redaction, and null-safe duration diagnostics. |
| `backend/2.0/src/test/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2ServiceDiscussionSegmentTest.java` | Added prompt regression tests for forbidden meta-chat framing, output structure, source grounding, external provider caveat, and hallucination guardrails. |
| `backend/2.0/src/test/java/com/larbcorp/neuroinfogrinder2/materials/KnowledgeMaterialServiceTest.java` | Added H2-backed material detail contract regression for ordered DISCUSSION_SEGMENT sources and `howBuiltSteps`. |
| `frontend/src/pages/guides/GuideDetailPage.tsx` | Added multi-source material detail UI: `Источники материала`, ordered source cards, how-built block, trace/provider collapsed diagnostics, no fake `0 мс`. |
| `frontend/src/shared/types.ts` | Extended frontend material/source/provider/trace/how-built TypeScript contract fields. |
| `scripts/material-style-count-check-20260626.sql` | Added read-only count check to verify no new materials were generated. |

## Prompt Changes

The generation prompt now instructs future generation to:

- Write as standalone guide/answer/summary/reference, not as chat recap.
- Start with useful information directly.
- Avoid meta-chat framing and forbidden phrases.
- Use neutral caveat `Данные основаны на предоставленном контексте.` when source limits must be stated.
- Include output structure requirements for GUIDE, ANSWER, SUMMARY, and REFERENCE.
- Preserve useful facts from source context.
- Avoid unsupported exact API docs, pricing, time limits, model availability, SLA, provider behavior, legal, medical, or security claims.
- Always include provider caveat for external provider/API behavior: `Проверьте актуальную документацию провайдера: лимиты, тарифы, поведение cache и доступность моделей могут меняться.`

## Tests Added

| test | coverage |
|---|---|
| `discussionSegmentGuideGenerationPromptForbidsMetaChatFraming` | DISCUSSION_SEGMENT GUIDE prompt forbids meta-chat framing and requires guide structure. |
| `discussionSegmentSummaryGenerationPromptForbidsMetaChatFraming` | DISCUSSION_SEGMENT SUMMARY prompt forbids recap style and requires summary structure. |
| `singleMessageGuideGenerationPromptForbidsMetaChatFraming` | SINGLE_MESSAGE GUIDE prompt gets same no-meta-chat rules. |
| `clusterGenerationPromptPreservesFactsAndBlocksUnsupportedExactValues` | Cluster generation preserves source facts, blocks unsupported exact values, and includes provider caveat rule. |
| `discussionSegmentDetailExposesOrderedSourcesAndHowBuiltSteps` | Material detail API exposes ordered multi-source fields and how-built path for DISCUSSION_SEGMENT. |

Red phase evidence: targeted tests failed before fix on missing prompt guardrails. The API test initially failed until ordered source/how-built contract was implemented.

Green phase evidence:

- Targeted backend tests: `mvn "-Dtest=ReplayV2ServiceDiscussionSegmentTest,KnowledgeMaterialServiceTest" test` -> `18` tests passed.
- Full backend tests: `mvn test` -> `67` tests passed.
- Frontend build: `npm run build` passed.

## Backend API Fields Added Or Verified

`/api/v1/materials/{id}` now includes or verifies:

- `materialId`
- `type`
- `candidateType`
- `sourceCount`
- `segmentId`
- `clusterId`
- `segmentScore`
- `segmentDecision`
- `segmentSignals`
- `segmentSuppressionReasons`
- `segmentTimeWindow`
- `generationSkipReason`
- `quality`
- `confidence`
- `sourceMessages[].orderIndex`
- `sourceMessages[].rawId`
- `sourceMessages[].datasetMessageId`
- `sourceMessages[].replayRunMessageId`
- `sourceMessages[].chatTitle`
- `sourceMessages[].topicId`
- `sourceMessages[].threadId`
- `sourceMessages[].messageDate`
- `sourceMessages[].role`
- `sourceMessages[].author`
- `sourceMessages[].text`
- `sourceMessages[].textUnavailableReason`
- `sourceMessages[].appMessageUrl`
- `sourceMessages[].telegramMessageUrl`
- `sourceMessages[].telegramLinkReason`
- `traceStages[]`
- `providerCalls[]`
- `providerCalls[].requestPreview`
- `providerCalls[].responsePreview`
- `providerCalls[].responseJson`
- `howBuiltSteps[]`

Provider preview fields are redacted for key/token/password/secret-like values.

## Frontend UI Changes

- Multi-source materials show `Источники материала`, not `Исходное сообщение`.
- Subtitle explains that the material was assembled from several messages joined into a discussion segment/cluster.
- Summary shows candidate type, source count, raw ids, chat/topic, LLM judge decision, and generation status.
- Ordered source cards show raw id, dataset id, replay-run-message id, chat/topic/thread, date, author, role, text/caption, app link, and honest Telegram link reason.
- First three source cards are expanded; remaining cards are collapsed behind `Показать все источники` when there are more than three.
- `Как был составлен материал` block renders how-built steps from API.
- `Trace / provider calls` block renders provider calls and trace stages with JSON collapsed by default.
- Null or zero duration now renders `нет данных`, not fake `0 мс`.

## Verification For Materials 23/24/25

| material | candidateType | sourceCount | sourceMessages | howBuiltSteps | traceStages | providerCalls | forbidden phrases in existing body | recommendation |
|---:|---|---:|---:|---:|---:|---:|---|---|
| 23 | DISCUSSION_SEGMENT | 6 | 6 | 5 | 114 | 1 | `участники обсуждения`, `в обсуждении` | Regenerate or manually edit after approval. |
| 24 | DISCUSSION_SEGMENT | 6 | 6 | 5 | 114 | 1 | `участники обсуждения`, `в обсуждении` | Regenerate or manually edit after approval. |
| 25 | DISCUSSION_SEGMENT | 6 | 6 | 5 | 114 | 1 | `в обсуждении` | Regenerate or manually edit after approval. |

Browser check notes:

- `/materials/23`, `/materials/24`, `/materials/25` opened in the authenticated browser session.
- DOM checks confirmed `sources: 6`, ordered `raw #`/`dataset #` source cards, provider calls, `Trace / provider calls`, no old `Исходное сообщение` label, and no fake `0 мс` after the frontend duration fix.
- Russian accessibility-tree text is mojibake through WebBridge, so browser verification used structural/ASCII checks plus API snapshots.

API snapshot artifacts:

- `reports/material-23-style-source-20260626.json`
- `reports/material-24-style-source-20260626.json`
- `reports/material-25-style-source-20260626.json`

## Production Deployment

- Deployed backend and frontend with `./scripts/deploy-fast.ps1 -Target all` after local tests/build passed.
- Deployed a frontend-only follow-up for duration display with `./scripts/deploy-fast.ps1 -Target frontend`.
- Final production status: backend healthy, frontend healthy, model-worker healthy, postgres healthy, redis healthy, ssh-socks healthy, tor healthy, public URL `200`.

## What Was Not Touched

- No backlog/reprocess.
- No new materials generated.
- No existing material body rewritten.
- No provider config changes.
- No threshold changes.
- No Telegram logout/session/auth/proxy changes.
- No public discovery guard weakening.
- No auto-publish.
- No material deletion/archive.

## Count Check

Read-only production count after the task:

- `discussion_segment_materials=3`
- `active_knowledge_items=24`

This matches the expected state from before the task and confirms no new material generation occurred.
