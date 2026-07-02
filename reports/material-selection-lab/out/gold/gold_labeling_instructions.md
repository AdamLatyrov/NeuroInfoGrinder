# Gold Labeling Instructions

Fill `gold_labels_template.csv` manually. Use `true`/`false` or `1`/`0`.

Rules:
- Important messages must not land in `REJECT_SAFE`.
- Junk must not land in `MATERIAL_CANDIDATE_PRELIMINARY`.
- If uncertain, prefer `REVIEW_HIGH_RECALL` over losing a useful signal.
- Mark `eligible_material=true` only when evidence is sufficient and the item should become durable knowledge after enrichment/dedupe checks.
- Single-message, single-source, job/event single-source, rules/onboarding, risk/referral, and link-only-before-enrichment should not be eligible materials.
