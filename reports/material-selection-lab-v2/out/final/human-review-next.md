# Material Lab v2 Human Review Next

Review these files first:

- `reports/material-selection-lab-v2/out/clusters/russian_cluster_summary.md`
- `reports/material-selection-lab-v2/out/evidence/evidence_group_summary.csv`
- `reports/material-selection-lab-v2/out/gold/gold_labels_template.csv`

## Review Order

1. Review the single `EVIDENCE_GROUP_REVIEW` candidate.
2. Review `SIGNAL_GROUP` clusters that may be under-selected.
3. Review `MANUAL_REVIEW` and `LINK_ENRICHMENT_FIRST` for false positives.
4. Review a random sample of `REJECT_SAFE` for false negatives.

## Material Candidate

### `v2c-00173` — `Техническое обсуждение: r-api`

Decision: `EVIDENCE_GROUP_REVIEW`

Manual question: is this enough for a durable material, or should it stay signal/context?

Suggested label fields:

- `eligible_material`: `true/false`
- `should_be_signal`: `true/false`
- `notes`: why

## Signal Groups Worth Manual Review

These are not material candidates right now, but may be useful signals:

- `codex.sale` group — likely promo/risk or link enrichment, not material.
- `deepseek` group — model/news signal, likely aggregate-only.
- `droid` groups — possible tool outage/problem evidence; review whether route should be upgraded.
- `opus` groups — model/provider claims; require official/corroborating source.
- `codex` group — possible technical issue; may need route tuning if truly useful.

## What To Mark

For each reviewed cluster:

- keep as `EVIDENCE_GROUP_REVIEW`
- downgrade to `SIGNAL_ONLY`
- move to `NEEDS_LINK_ENRICHMENT`
- move to `MANUAL_REVIEW`
- reject/context only

## Success Criteria

The v2 process is good enough for shadow production when:

- no important messages in reviewed `REJECT_SAFE` sample
- no promo/risk/referral in `EVIDENCE_GROUP_REVIEW`
- weak pairs do not become material candidates
- useful multi-source technical issues are not stuck as low-value chatter
