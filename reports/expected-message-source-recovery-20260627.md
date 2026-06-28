# Expected Message Source Recovery - 2026-06-27

This file appends the UI reconciliation result after the earlier exact-window audit.

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

Key correction: previous `0/12 exact match` remains true only for the narrow `03:40-04:15 MSK` window. It is not true as a UI-visible source recovery result. M01 has a UI-equivalent raw `11142`; M11 has exact raw `11193`.
