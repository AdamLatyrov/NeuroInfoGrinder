# Discussion Segment Tightening - 2026-06-26

## Summary

- window_from_msk: 2026-06-26T01:00:00+03:00
- window_to_msk: 2026-06-26T10:00:00+03:00
- window_from_utc: 2026-06-25T22:00:00Z
- window_to_utc: 2026-06-26T07:00:00Z
- raw_messages: 919
- processable_messages: 919
- previous_candidate_windows_built: 367
- previous_accepted_segments: 89
- previous_persisted_sources: 501
- candidate_windows_built: 363
- accepted_before_dedupe: 60
- accepted_segments: 17
- rejected_segments: 346
- overlap_duplicates_rejected: 38
- window_limit_rejected: 5
- low_value_adjacent_rejected: 132
- entity_only_rejected: 10
- needs_more_context: 1
- persisted_sources_if_inserted: 90
- count_by_proposed_material_type: {"ANSWER":5,"GUIDE":6,"SUMMARY":6}
- provider_calls_count: 0
- knowledge_generation_enabled: false
- discussion_materials_created: 0
- top_rejection_reasons: {"DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT":132,"PROMO_OR_AD":12,"DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY":10,"LOW_COMBINED_SCORE_OR_SIGNAL_COUNT":106,"LOW_ACTIONABILITY_NEWS":3,"CRYPTO_TRADING_OFFTOPIC":38,"DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT":1,"PROMO_OR_AD;CRYPTO_TRADING_OFFTOPIC":1,"DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE":38,"DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT":5}

## Fixture Coverage

| fixture | detected | local_id | source_count | matched_raw_ids | missing_raw_ids | extra_raw_ids | score | decision | type | rejection_reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E01 | yes | S0024 | 6 | 6062 6069 6070 6078 | 6034 6042 6052 6079 6105 | 6064 6071 | 0.98 | DISCUSSION_SEGMENT_CANDIDATE | GUIDE |  |
| E02 | yes | S0269 | 6 | 6776 6778 6799 6803 6805 | 6765 6817 6826 6836 6850 6876 | 6806 | 0.98 | DISCUSSION_SEGMENT_CANDIDATE | GUIDE |  |
| E04 | yes | S0132 | 6 | 6221 6223 6224 6225 |  | 6220 6222 | 1 | DISCUSSION_SEGMENT_CANDIDATE | SUMMARY |  |
| E07 | yes | S0217 | 6 | 6341 6540 6543 | 6592 6688 6820 | 6360 6538 6539 | 0.78 | DISCUSSION_SEGMENT_CANDIDATE | SUMMARY |  |
| E08 | no |  | 0 |  | 6144 6367 |  |  | DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT |  | DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT |
| E09 | no |  | 0 |  | 6419 6544 6545 6831 |  |  | DISCUSSION_SEGMENT_REJECTED_NOISE |  | LOW_ACTIONABILITY_NEWS |
| E10 | no |  | 0 |  | 6306 6307 |  |  | DISCUSSION_SEGMENT_REJECTED_NOISE |  | PROMO_OR_AD |

## Safety

- Provider calls: 0.
- Knowledge generation enabled: false.
- DISCUSSION_SEGMENT materials created: 0.
- This script only reads prior exported evidence and writes local reports.
