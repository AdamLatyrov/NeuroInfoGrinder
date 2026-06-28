# Message Usefulness Classifier - 2026-06-27

## Summary

Implemented a minimal internal `MessageUsefulnessClassifier` for the existing `ReplayV2Service.singleMessageDetection` flow. It is not a parallel pipeline and does not create materials directly.

Positive classifier routes still enter the existing single-message candidate path and must pass the existing LLM judge before generation. Reject classes short-circuit before LLM calls with clearer rejection reasons.

## Files Changed

- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/MessageUsefulnessClassifier.java`
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/MessageUsefulnessResult.java`
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2Service.java`
- `backend/2.0/src/test/java/com/larbcorp/neuroinfogrinder2/replay/MessageUsefulnessClassifierTest.java`
- `reports/message-usefulness-classifier-20260627.md`
- `reports/message-usefulness-classifier-20260627.json`
- `reports/message-usefulness-classifier-tests-20260627.csv`
- `reports/message-usefulness-classifier-dry-run-20260627.csv`

## Classes Added

- `MessageUsefulnessClassifier`
- `MessageUsefulnessResult`

## Integration Point

- `ReplayV2Service.singleMessageDetection`

The classifier runs after normalized text is available and before generic `CHAT_CONTEXT_ONLY` / `TOO_SHORT` / low-score rejection handling. It only influences:

- candidate route
- proposed material type
- score boost dimension
- signals
- rejection reason
- trace/debug JSON

It does not change DISCUSSION_SEGMENT, clustering, settings, provider config, caps, or global thresholds.

## Supported Classes

- `PRACTICAL_GUIDE`
- `NEWS_UPDATE`
- `RESOURCE_REFERENCE`
- `STATUS_OUTAGE`
- `QNA`
- `LINK_WITH_CONTEXT`
- `LINK_ONLY`
- `PROMO_ALONE`
- `ABUSE_OR_FRAUD`
- `ACCESS_CIRCUMVENTION`
- `ENTITY_ONLY`
- `LOW_VALUE`

## Trace Fields

Stored in existing `single_message_score_breakdown_json.usefulnessClassification` without a DB migration:

- `contentClass`
- `usefulnessClass`
- `sourceContextClass`
- `safetyClass`
- `candidateRoute`
- `proposedMaterialType`
- `overallScore`
- `scoreDimensions`
- `positiveSignals`
- `negativeSignals`
- `rejectReason`
- `humanReason`

The same usefulness JSON is included in the single-message LLM prompt for judge/generation context.

## Tests Run

- Red phase confirmed: `mvn -Dtest=MessageUsefulnessClassifierTest test` failed before implementation because `MessageUsefulnessClassifier` and `MessageUsefulnessResult` did not exist.
- Targeted: `mvn -Dtest=MessageUsefulnessClassifierTest test` -> 10 tests passed.
- Full backend: `mvn test` -> 80 tests passed.

## Dry-Run Results

Read-only production sample:

- raw `11142`: `NEWS_UPDATE`, `SINGLE_MESSAGE`, `REFERENCE`, LLM judge eligible, not `LOW_SINGLE_MESSAGE_SCORE`.
- raw `11193`: `LINK_ONLY`, `REJECT`, `NEEDS_LINK_ENRICHMENT`, no LLM call, not generic `TOO_SHORT`.
- raw `11484`: `RESOURCE_REFERENCE`, `SINGLE_MESSAGE`, `REFERENCE`, LLM judge eligible.
- 20 random recent processable messages: 20 rejected as `LOW_VALUE`; no false positive material candidates observed.

Detailed rows: `reports/message-usefulness-classifier-dry-run-20260627.csv`.

## No-Change Verification

- No separate pipeline created.
- No backlog/reprocess run.
- No materials created by this task.
- No caps/settings changed.
- No provider config changed.
- No global thresholds changed.
- No Telegram logout/session/auth/proxy touched.
- No public discovery guard weakened.
- No auto-publish change.
- No raw ids hardcoded in production logic.

## Recommendation

Allow controlled LLM judge for new `NEWS_UPDATE`, `RESOURCE_REFERENCE`, and `STATUS_OUTAGE` single-message candidates. Keep existing LLM judge, generation, dedupe, caps, and safety gates authoritative.
