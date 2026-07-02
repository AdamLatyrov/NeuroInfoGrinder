# Material Lab v2 Production Port Readiness

## Current Verdict

Material Lab v2 is better than the current production material-selection behavior for admission control, but it should not be deployed directly yet. It is ready for manual gold review and then shadow-mode backend port.

## Latest Offline Run

Input: `reports/material-selection-lab-v2/data/raw_messages_20k_rich.jsonl`

Messages: `20,000`

Hidden URL coverage after `raw_json` parsing:

- Messages with hidden URLs: `722`
- Extracted hidden URLs: `1,208`

Routes:

- `CONTEXT_ONLY`: `18,817`
- `REJECT_SAFE`: `515`
- `SIGNAL_ONLY`: `276`
- `NEEDS_ENRICHMENT`: `126`
- `REVIEW_HIGH_RECALL`: `94`
- `AGGREGATE_ONLY`: `94`
- `MANUAL_REVIEW`: `78`

Cluster decisions:

- `REVIEW_SINGLE_SIGNAL`: `197`
- `BLOCKED`: `188`
- `CONTEXT_ONLY`: `49`
- `SIGNAL_PAIR`: `18`
- `CONTEXT_GROUP`: `13`
- `SIGNAL_GROUP`: `9`
- `MANUAL_REVIEW`: `7`
- `LINK_ENRICHMENT_FIRST`: `7`
- `WEAK_PAIR`: `7`
- `EVIDENCE_GROUP_REVIEW`: `1`

## Material-Level Candidate

Only one cluster currently reaches `EVIDENCE_GROUP_REVIEW`:

- `v2c-00173` — `Техническое обсуждение: r-api`
- size: `3`
- independent sources: `2`
- conversations: `1`
- shared strong entity: `r-api`
- samples:
  - `msg-09715`: `Да мы просто переведем r-api на api`
  - `msg-09720`: `а может вы сделаете балансировщик автоматический с r-api на api и наоборот?`
  - `msg-09735`: `r-api?`

## Important Improvements vs Current Production

| Production failure mode | v2 behavior |
|---|---|
| 116 DRAFT materials from 20k, 115 flagged | 1 material-level candidate, 0 generated |
| size-2 weak clusters can become material candidates | size-2 becomes `WEAK_PAIR` or `SIGNAL_PAIR`, not material |
| time/thread chatter can pollute a cluster | non-candidate chatter is context only, not a cluster member |
| generic model names transitively merge unrelated conversations | cross-conversation merge excludes generic model/tool names |
| hidden caption/link entities were lost in old export/lab | rich snapshot + raw_json URL extraction now included |
| promo/free-token links looked like material candidates | promo/risk guard sends them to manual/signal, not material |

## Not Ready To Port Yet

Do not port generation behavior, cluster names, or regex entity extraction as source truth.

Do not enable automatic material generation from v2 decisions until gold labels confirm:

- zero important false negatives in `REJECT_SAFE`
- low false positives in `EVIDENCE_GROUP_REVIEW`
- acceptable retention of useful `SIGNAL_GROUP` items
- link-enrichment and manual-review buckets are reviewed

## Recommended Next Port Shape

Port only in shadow mode first:

1. Add `MaterialEligibilityGate` decision states.
2. Add pre-LLM admission records with `route`, `reason`, `conversation_key`, `strong_entities`, `independent_source_count`.
3. Use v2 rules only to block or shadow-score candidates before LLM.
4. Keep BGE for semantic grouping, but apply v2 evidence sufficiency as the admission gate.
5. Compare shadow decisions against production outcomes and manual gold labels before enabling.
