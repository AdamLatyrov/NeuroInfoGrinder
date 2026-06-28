# UI-visible Message Source Recovery - 2026-06-27

Status: read-only reconciliation complete. No settings, caps, prompts, materials, reprocess, Telegram session/proxy, discovery guard, or publish state were changed.

## Target Chat

- Title: API SUPPORT | ModelHub
- Internal group id: 38
- Telegram chat id: -1003898985313
- Enabled: true
- Active dialog: true
- Processing state: ENABLED_PROCESSABLE
- Raw messages in 2026-06-26..2026-06-27 search range: 244

## UI/API Reconciliation

- Inspected endpoint: `/api/v1/groups/38/messages?page=0&size=300&sort=messageDate,desc`
- API total elements: 488
- Fetched elements: 200
- Backend query reads `raw_messages` directly.
- `MessageDto.id` from the UI API is `raw_messages.id`.
- Recovered raw ids `11142` and `11193` are present in the fetched API response.

## Why Previous Audit Did Not Match

- The previous exact window was too narrow/wrong for these UI-visible examples: `03:40-04:15 MSK` maps to `00:40-01:15Z`.
- GPT-5.6 raw `11142` is `2026-06-26T23:28:47Z` / `02:28:47 MSK`, outside that window.
- lolz.live raw `11193` is `2026-06-27T00:14:07Z` / `03:14:07 MSK`, also outside that window.
- GPT-5.6 was stored in `raw_messages.caption`; UI normalizes caption into `text` through `preferredText()`.
- Exact timestamp/content matching was too strict; UI-first reconciliation should start from chat id and `/groups` response ids.

## Recovered UI-visible Messages

| ui_id | expected_label | raw_id | dataset_message_id | replay_run_message_id | telegram_message_id | message_date_utc | message_date_msk | author | source_field | processable | pipelineStatus | material_id | rejection_or_skip_reason |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|
| U01 | GPT-5.6 Sol message | 11142 | 10121 | 7397 | 5697961984 | 2026-06-26T23:28:47+00:00 | 2026-06-27T02:28:47 | ╨Р╨╗╨╡╨║╤Б╨░╨╜╨┤╤А | raw_messages.caption | true | REJECTED_SINGLE_MESSAGE |  | LOW_SINGLE_MESSAGE_SCORE |
| U02 | GPT-5.6 government approval message | 11142 | 10121 | 7397 | 5697961984 | 2026-06-26T23:28:47+00:00 | 2026-06-27T02:28:47 | ╨Р╨╗╨╡╨║╤Б╨░╨╜╨┤╤А | raw_messages.caption | true | REJECTED_SINGLE_MESSAGE |  | LOW_SINGLE_MESSAGE_SCORE |
| U03 | Link-only lolz.live message | 11193 | 10168 | 7444 | 5705302016 | 2026-06-27T00:14:07+00:00 | 2026-06-27T03:14:07 | Store | raw_messages.text | true | REJECTED_SINGLE_MESSAGE |  | TOO_SHORT |
| U04 | Long GPT-5.6 announcement from Aleksandr | 11142 | 10121 | 7397 | 5697961984 | 2026-06-26T23:28:47+00:00 | 2026-06-27T02:28:47 | ╨Р╨╗╨╡╨║╤Б╨░╨╜╨┤╤А | raw_messages.caption | true | REJECTED_SINGLE_MESSAGE |  | LOW_SINGLE_MESSAGE_SCORE |

## Pipeline Outcome

| raw_id | embedded | run_id | final_decision | single_message_score | single_message_rejection_reason | discussion_segment_ids | provider_calls | material_ids | daily_cap_impact |
|---:|---|---:|---|---:|---|---|---|---|---|
| 11142 | true | 5055 | REJECTED_SINGLE_MESSAGE | 0.51 | LOW_SINGLE_MESSAGE_SCORE |  |  |  | NOT_APPLICABLE_NO_DISCUSSION_SEGMENT |
| 11142 | true | 5055 | REJECTED_SINGLE_MESSAGE | 0.51 | LOW_SINGLE_MESSAGE_SCORE |  |  |  | NOT_APPLICABLE_NO_DISCUSSION_SEGMENT |
| 11193 | true | 5092 | REJECTED_SINGLE_MESSAGE | 0 | TOO_SHORT |  |  |  | NOT_APPLICABLE_NO_DISCUSSION_SEGMENT |
| 11142 | true | 5055 | REJECTED_SINGLE_MESSAGE | 0.51 | LOW_SINGLE_MESSAGE_SCORE |  |  |  | NOT_APPLICABLE_NO_DISCUSSION_SEGMENT |

## M01-M12 Updated Mapping

| manual_id | status | raw_ids | note |
|---|---|---|---|
| M01 | FOUND_UI_EQUIVALENT | 11142 | Maps to U01/U04 long GPT-5.6 Sol caption in API SUPPORT | ModelHub; outside previous exact window. |
| M02 | NOT_FOUND |  | PlusVibeAPI-specific content not recovered in target chat search. |
| M03 | NOT_FOUND |  | Specific proxy-risk/model-substitution/log-resale content not recovered in target chat search. |
| M04 | NOT_FOUND |  | Figma Config content not recovered in target chat search. |
| M05 | FOUND_SIMILAR |  | Codex mentions exist in broader-day search, but this UI reconciliation did not recover the requested outage/quota message. |
| M06 | FOUND_UI_EQUIVALENT | 11142 | Government/access-policy wording may be part of the same long GPT-5.6 caption or related UI item; recovered hit did not match every named source snippet. |
| M07 | FOUND_SIMILAR | 11142 | Fable/Mythos/GPT-5.6 context appears in raw 11142; exact Claude iOS loophole text not recovered here. |
| M08 | NOT_FOUND |  | Referral/bot abuse content not recovered in this target-chat UI source search. |
| M09 | FOUND_SIMILAR |  | OpenMontage-like candidate was previously found as broader-day raw 11484, outside U01-U04 target set. |
| M10 | NOT_FOUND |  | sensors_discord_bot not recovered in target chat search. |
| M11 | FOUND_EXACT | 11193 | lolz.live link-only message recovered from /groups API and raw_messages.text. |
| M12 | NOT_FOUND |  | feedback-only useful-guide text not recovered in target chat search. |

## Conclusions

- U01/U04 and the U02-equivalent GPT-5.6 message were recovered as raw 11142, dataset 10121, replay message 7397.
- U03 lolz.live was recovered as raw 11193, dataset 10168, replay message 7444.
- Both were pipeline-resolvable and embedded.
- Neither became a material.
- raw `11142`: rejected as `LOW_SINGLE_MESSAGE_SCORE` with score `0.51`; this is a likely news/reference-mode gap, not a daily-cap issue.
- raw `11193`: rejected as `TOO_SHORT` with score `0`; outcome is expected for link-only content, but the reason should become explicit `LINK_ONLY` or `NEEDS_LINK_ENRICHMENT`.
- Daily cap did not apply because neither recovered message reached DISCUSSION_SEGMENT generation.

## Recommendations

- Add news/reference mode for high-value model-release and source-link announcements.
- Add link enrichment and explicit link-only rejection reasons.
- Add a UI-to-pipeline resolver that accepts `/groups` message id and returns raw/dataset/replay/material trace.
- Make future audits start from chat id + `/groups` API ids before timestamp keyword matching.
- Consider manual materialization for raw `11142` only as a SUMMARY/REFERENCE if approved; do not make it a practical GUIDE by default.
