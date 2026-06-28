# Golden Batch Real Posts - 2026-06-28

Initial real-post regression batch assembled from existing audits and dry-runs.

## Night Ingest Fact Check

- Night window: `2026-06-27 03:40:00 - 2026-06-27 04:15:00`
- Raw messages in window: `28`
- Discussion segments in window: `0`
- Conclusion: night ingest existed; expected useful cases mostly failed due to window mismatch or downstream candidate/routing loss, not because raw traffic was zero.

## Cases

| case_id | kind | expected | current | anchor |
|---|---|---|---|---|
| GB01 | single_message_reference | ACCEPT_AS_REFERENCE_NOT_GUIDE | JUDGE_ACCEPTED_REFERENCE | 11142 |
| GB02 | single_message_reference | ACCEPT_AS_REFERENCE_NOT_GUIDE | JUDGE_ACCEPTED_REFERENCE | 11484 |
| GB03 | single_message_negative_control | REJECT_LINK_ONLY_NEEDS_ENRICHMENT | PRE_GATE_REJECTED | 11193 |
| GB04 | discussion_segment_guide | DISCUSSION_SEGMENT_GUIDE | NOT_CAUGHT_TODAY | CL01 |
| GB05 | discussion_segment_guide | DISCUSSION_SEGMENT_GUIDE | NOT_CAUGHT_TODAY | CL02 |
| GB06 | single_or_pair_resume_answer | GUIDE_OR_STRONG_ANSWER | SHOULD_BE_CAUGHT_TODAY_PER_MANUAL_AUDIT | CL05 |
| GB07 | safety_sensitive_context | NEEDS_SAFETY_SUMMARY_OR_REJECT | CORRECT_TO_KEEP_BLOCKED | CL07 |
| GB08 | promo_noise | REJECT_PROMO | CORRECT_TO_REJECT | CL10 |

## Recommended First Regression Slice

- Positive single-message reference: `11142`, `11484`.
- Negative single-message control: `11193`.
- Discussion guide misses: `E01`, `E02`.
- Safety holdout: `E07`.
- Promo reject: `E10`.