# Overnight Expected Materials Audit - 2026-06-27

Status: read-only audit complete. No settings, materials, prompts, caps, reprocess, Telegram session, proxy, or publishing state were changed.

## Scope

- Local window: 2026-06-27T03:40:00 - 2026-06-27T04:15:00 Europe/Moscow
- UTC window: 2026-06-27T00:40:00+00:00 - 2026-06-27T01:15:00+00:00
- Raw messages in window: 28
- Dataset messages in window: 28
- Replay run messages in window: 28
- DISCUSSION_SEGMENT rows in window: 0
- Provider calls from window runs: 0
- Materials from window sources: 0

## Plain Answers

1. Did the system ingest all night messages? Yes for the specified DB window: 28 raw rows and 28 dataset rows were present.
2. Were they processable? The nearest timestamp rows used for trace were processable where chat state existed; the expected content itself was not found in that exact window.
3. Did DISCUSSION_SEGMENT detect G01/G02/G03/G04? No. There were zero DISCUSSION_SEGMENT rows in the specified window.
4. Did any expected useful material become a DRAFT? No. Zero materials were linked to the specified window.
5. Was non-materialization due to daily cap? No for the specified window: no DISCUSSION_SEGMENT candidate reached the cap gate. UTC 2026-06-27 had zero DISCUSSION_SEGMENT materials at capture. Pre-enable materials 23/24/25 used the 2026-06-26 UTC day, not this night window's UTC day.
6. Which expected materials should be created manually now? G03 from broader-day candidate raw 11484 is the clearest candidate if approved. G01/G02 need exact source raw ids first because the expected content was not found in the specified window. G04 should only be safety summary or reject.
7. Which rules need tuning? Add news/reference mode, improve link-only enrichment, and improve multi-message temporal grouping across non-singleton bursts.
8. Which messages were correctly rejected? M08/M10/M11/M12 should not become guides as abuse/entity-only/link-only/feedback-only; G04 must not become bypass how-to.

## Critical Finding

The expected M01-M12 content does not match the actual raw text in the requested 2026-06-27T03:40:00-2026-06-27T04:15:00 MSK window. Exact keyword/content matching found 0 of 12 expected items in that window. A broader-day search found candidate content for some items outside the requested window, for example M01 around 07:48 MSK, M09 around 09:01 MSK, and M11 around 03:14 MSK.

## Message Comparison

| manual_id | raw_id | expected_verdict | actual_status | candidate_type | segment_id | score | llm_decision | material_id | skip_or_reject_reason | correct? | notes |
|---|---:|---|---|---|---|---:|---|---:|---|---|---|
| M01 |  | MAYBE_SUMMARY_NOT_GUIDE | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=26 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11411; broader_day_message_date=2026-06-27T04:48:25+00:00; hit_count=6 |
| M02 |  | REJECT_PROMO_ALONE_OR_MERGE_CONTEXT_FOR_PROXY_API_RISK_GUIDE | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=23 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11313; broader_day_message_date=2026-06-27T01:18:53+00:00; hit_count=1 |
| M03 |  | SHOULD_BECOME_MATERIAL | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=37 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11434; broader_day_message_date=2026-06-27T05:15:17+00:00; hit_count=2 |
| M04 |  | MAYBE_SUMMARY_NOT_GUIDE | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=37 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11392; broader_day_message_date=2026-06-27T03:36:27+00:00; hit_count=1 |
| M05 |  | SHOULD_BECOME_MATERIAL_IF_CONTEXT_ENOUGH | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=37 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11427; broader_day_message_date=2026-06-27T05:04:57+00:00; hit_count=1 |
| M06 |  | MAYBE_SUMMARY_NOT_GUIDE | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=37 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11427; broader_day_message_date=2026-06-27T05:04:57+00:00; hit_count=2 |
| M07 |  | MAYBE_SAFETY_SUMMARY_ONLY_OR_REJECT_ACCESS_CIRCUMVENTION_HOWTO | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=217 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11427; broader_day_message_date=2026-06-27T05:04:57+00:00; hit_count=5 |
| M08 |  | REJECT_ABUSE | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | NONE_FOR_EXPECTED_CONTENT |  |  |  |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=98 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11963; broader_day_message_date=2026-06-27T08:09:58+00:00; hit_count=2 |
| M09 |  | SHOULD_BECOME_MATERIAL | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=2 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11484; broader_day_message_date=2026-06-27T06:01:19+00:00; hit_count=5 |
| M10 |  | REJECT_ENTITY_ONLY | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=170 | LIKELY_CORRECT_REJECT_OR_NOT_PRESENT | no broader-day content candidate found by keyword search |
| M11 |  | REJECT_LINK_ONLY_OR_NEEDS_CONTEXT | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=5 | NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW | broader_day_candidate_raw=11193; broader_day_message_date=2026-06-27T00:14:07+00:00; hit_count=4 |
| M12 |  | REJECT_FEEDBACK_CONTEXT_ONLY | EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW | SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP |  |  | REJECTED_SINGLE_MESSAGE |  | nearest timestamp raw does not match expected content; nearest_delta_seconds=1 | LIKELY_CORRECT_REJECT_OR_NOT_PRESENT | no broader-day content candidate found by keyword search |

## Group Comparison

| group_id | expected_material | expected_type | source_manual_ids | actual_segment_id | actual_material_id | actual_status | reason | correct? | fix_needed |
|---|---|---|---|---|---|---|---|---|---|
| G01 | Как проверять AI API proxy: подмена моделей, приватность логов и риски | GUIDE | M02,M03 |  |  | NOT_DETECTED_IN_SPECIFIED_WINDOW | Expected M02/M03 content was not found in the specified 03:40-04:15 MSK window. No DISCUSSION_SEGMENT existed in that window. Broader-day weak proxy candidates exist but do not confirm the exact PlusVibe/proxy-risk pair. | NO | If source content is confirmed outside window, manually materialize or run bounded approved audit/reprocess for exact raw ids only; tune discussion routing for proxy-risk/news/reference chains. |
| G02 | Как диагностировать сбои Codex и AI coding tools: статусы, квоты, fallback | GUIDE | M05,M01,M06,M07 |  |  | NOT_DETECTED_IN_SPECIFIED_WINDOW | Expected Codex/status content was not found in specified window. Broader-day Codex mentions exist, but the requested night window had singleton batches ending NO_MATERIAL_CANDIDATES and zero DISCUSSION_SEGMENT rows. | NO | Add news/status/reference mode and multi-message temporal grouping; consider manual material only after exact source ids are selected. |
| G03 | OpenMontage: что это за AI video-agent pipeline и как оценить пользу | REFERENCE_OR_SUMMARY | M09 |  |  | NOT_DETECTED_IN_SPECIFIED_WINDOW | OpenMontage-like broader-day candidate raw 11484 exists at 06:01:19Z / 09:01:19 MSK, outside specified 03:40-04:15 MSK. No material was created. | NO_FOR_EXPECTED_CONTENT | Add reference/resource mode; manually materialize G03 from exact raw 11484 if approved. |
| G04 | Почему не стоит строить workflow на временных model access loopholes | SUMMARY_OR_ANSWER | M07 |  |  | NOT_DETECTED_IN_SPECIFIED_WINDOW | Expected Claude/Fable loophole content was not found in specified window. Broader-day digest candidate exists. Risk-sensitive guard should keep this safety-summary-only or reject. | YES_TO_NOT_GENERATE_HOWTO | Keep as safety summary only; do not generate bypass instructions. |

## Daily Cap Impact

- Pre-enable same UTC day DISCUSSION materials: 3 (materials 23/24/25).
- DISCUSSION materials on 2026-06-26 UTC: 3.
- DISCUSSION materials on 2026-06-27 UTC at capture: 0.
- Window DISCUSSION candidates: 0.
- Conclusion: daily cap did not block the specified night window because no window DISCUSSION candidate existed. The earlier cap issue remains real for 2026-06-26 UTC and should be fixed separately by counting only post-enable auto materials.

## Recommendations

- Keep controlled mode as-is until the real 24h final snapshot can be taken.
- Do not increase caps during this audit task.
- Later, change daily cap accounting so pre-enable manual materialization does not consume controlled auto cap.
- Add news/reference mode for high-value model-release, product-update, and repository-resource posts.
- Improve link-only enrichment before generating from links.
- Manually materialize G03 from raw 11484 if approved; identify exact raw ids before materializing G01/G02.
- Keep G04 safety-summary-only or reject.
- Continue rejecting abuse/referral, entity-only, link-only, and feedback-only content.
