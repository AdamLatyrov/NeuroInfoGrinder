# Discussion Segment Dry Run - 2026-06-26

## Summary

- window_from_msk: 2026-06-26T01:00:00+03:00
- window_to_msk: 2026-06-26T10:00:00+03:00
- window_from_utc: 2026-06-25T22:00:00Z
- window_to_utc: 2026-06-26T07:00:00Z
- raw_messages: 919
- processable_messages: 919
- candidate_windows_built: 367
- accepted_segments: 89
- rejected_segments: 278
- average_source_count: 5.63
- max_source_count: 11
- count_by_proposed_material_type: {"GUIDE":49,"ANSWER":40}
- provider_calls_count: 0
- knowledge_generation_enabled: false
- top_rejection_reasons: {"LOW_COMBINED_SCORE_OR_SIGNAL_COUNT":224,"PROMO_OR_AD":12,"LOW_ACTIONABILITY_NEWS":3,"CRYPTO_TRADING_OFFTOPIC":38,"PROMO_OR_AD;CRYPTO_TRADING_OFFTOPIC":1}

## Fixture Coverage

| fixture | detected | local_id | segment_id | source_count | matched_raw_ids | missing_raw_ids | extra_raw_ids | combined_score | decision | type | rejection_reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E01 | yes | S0364 | 86 | 9 | 6034 6042 6052 6062 6069 6070 6078 6079 6105 |  |  | 0.98 | DISCUSSION_SEGMENT_CANDIDATE | GUIDE |  |
| E02 | yes | S0365 | 87 | 11 | 6765 6776 6778 6799 6803 6805 6817 6826 6836 6850 6876 |  |  | 0.98 | DISCUSSION_SEGMENT_CANDIDATE | GUIDE |  |
| E04 | yes | S0132 | 50 | 6 | 6221 6223 6224 6225 |  | 6220 6222 | 0.9 | DISCUSSION_SEGMENT_CANDIDATE | GUIDE |  |
| E07 | yes | S0366 | 88 | 6 | 6341 6540 6543 6592 6688 6820 |  |  | 0.78 | DISCUSSION_SEGMENT_CANDIDATE | ANSWER |  |
| E08 | yes | S0367 | 89 | 2 | 6144 6367 |  |  | 0.58 | DISCUSSION_SEGMENT_CANDIDATE | ANSWER |  |
| E09 | no |  |  | 0 |  | 6419 6544 6545 6831 |  |  |  |  | not accepted by scorer |
| E10 | no |  |  | 0 |  | 6306 6307 |  |  |  |  | not accepted by scorer |

## Negative Checks

- E09: detected=no, reason=not accepted by scorer
- E10: detected=no, reason=not accepted by scorer

## Safety

- Provider calls: 0.
- Knowledge generation enabled: false.
- Expected knowledge_items from DISCUSSION_SEGMENT: 0.
- No backlog/reprocess/materialization was run by this script.

## Production Persistence And Safety Evidence

- knowledge_items before: 17
- knowledge_items after: 17
- DISCUSSION_SEGMENT materials before: 0
- DISCUSSION_SEGMENT materials after: 0
- persisted dry-run diagnostic segments: 89
- persisted dry-run diagnostic sources: 501
- persisted generation skip reason: DRY_RUN_GENERATION_DISABLED
- provider calls during dry-run: 0
