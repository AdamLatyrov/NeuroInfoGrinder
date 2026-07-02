# MaterialEligibilityGate — Shadow Port Implementation

## Status
- **Implemented + tested locally, NOT deployed.** Backend `mvn test` 165/165 pass; gate unit test 19/19 pass; Python lab gold 34/34 + 15/15.
- **Shadow only.** The gate computes a v2-style eligibility verdict and logs it to `semantic_decision_objects`; it does NOT change materialization, routing, or generation. The legacy `MessageUsefulnessClassifier` + score thresholds remain authoritative.

## What was ported
A pure, side-effect-free Java class `com.larbcorp.neuroinfogrinder2.decisioncore.MaterialEligibilityGate` that mirrors the verified offline v2 lab (`scripts/material_lab_v2.py`):
- Hard routing matrix: rules/onboarding -> REJECT_SAFE; risk/referral/promo/free-token/temp-email -> MANUAL_REVIEW; job/event -> AGGREGATE_ONLY; link-thin -> NEEDS_ENRICHMENT; unverified model/pricing claim -> SIGNAL_ONLY; non-material long form (roleplay/fiction/system-prompt/disclaimer/article-digest) -> CONTEXT_ONLY.
- Technical-entity requirement: a single-message guide/reference requires a model/tool/api/code/api-term/technical-domain anchor. A bare e-commerce/content domain (shopping list, marketing blog) does NOT qualify.
- Single-message eligibility tiers: `SINGLE_MESSAGE_GUIDE_CANDIDATE` (>=60 tokens + guide structure + technical entity) and `SINGLE_MESSAGE_REFERENCE_CANDIDATE` (>=60 tokens + technical entity + code/api). Both are `eligible` for LLM Judge.
- Cluster evidence sufficiency: `EVIDENCE_GROUP_REVIEW` requires size>=3, independent senders>=2, shared strong entity, all REVIEW_HIGH_RECALL, no risk, short-acks < size.
- Expanded promo/abuse regexes: `халявные токены`, `бесплатными нейронками`, `бесконечные нейронки`, `до конца жизни`, `полтора доллара`, temp-email services (`temporam`, `10minutemail`, `guerrillamail`, `tempmail`, `временная/одноразовая почта`).
- Tightened CODE_RE: ambiguous words (`public/private/select/update/insert/function/class/def/interface`) require code context; `make` removed (matched "Build in public", "How to make guns").
- Latent fix: `\bты\s+—\s+\w+` (was `ты\s+—\s+\w+` which matched the suffix in "работы — можно").

## Wiring (shadow)
- `DecisionCoreShadowService.upsertMessageDecision`: when `materialEligibilityGateShadowEnabled=1` (default 0), calls `MaterialEligibilityGate.evaluateMessage(.)` and writes the v2 verdict (`final_route`, `material_eligibility` = ELIGIBLE/INELIGIBLE, `reason_codes`, plus `rule_outputs.materialEligibilityGate` JSON with route/reason/tier/eligible/technicalEntity/strongEntities) to `semantic_decision_objects`.
- Called at normalization time (`ReplayV2Service` L318) via `decisionCoreMessage`. At that point the `MessageUsefulnessClassifier` has not run yet, so the classifier verdict is passed as null and the gate uses its pure-regex routing fallback; the eligibility flags (technicalEntity, nonMaterial, tier) are still accurate because they are computed from text alone.
- New setting `materialEligibilityGateShadowEnabled` (INTEGER, default 0, group `decision_core`). Requires `decisionCoreShadowEnabled=1` too.

## NOT ported (deliberate)
- Generation behavior, prompts, cluster names, regex entity extraction as source truth.
- The gate does NOT replace `MessageUsefulnessClassifier`; it augments it with the v2-specific filters (technical entity, non-material long form, evidence sufficiency).
- BGE-M3/HDBSCAN semantic grouping stays in production; the gate is a deterministic admission layer.

## Verification
- `MaterialEligibilityGateTest` (19 cases) ports the v2 gold regressions: rules/promo/temp-email/digest/fiction -> reject/manual/context; shopping-guide + build-in-public -> not eligible (no technical entity); codebase-mcp/claude-code/codex/postamat -> eligible; cluster evidence sufficiency (multi-source eligible, single-source/not-shared/risky not eligible).
- Full `mvn test` 165/165.
- Python lab `verify_material_lab_v2.py` 34/34 messages + 15/15 clusters (unchanged after `\bты` fix).

## Next steps (require Adam approval)
1. Deploy backend-only (`scripts/deploy-fast.ps1 -Target backend`).
2. Enable shadow: set `decisionCoreShadowEnabled=1` + `materialEligibilityGateShadowEnabled=1`.
3. Run a controlled replay over a known dataset; compare `semantic_decision_objects.material_eligibility` / `final_route` / `rule_outputs.materialEligibilityGate` against actual `knowledge_items` outcomes.
4. Measure: zero important false negatives in REJECT_SAFE/CONTEXT_ONLY, low false positives in eligible, acceptable retention vs the 20k production over-materialization baseline (115/124 flagged).
5. Only after shadow validates: wire the gate as an admission gate before LLM Judge (block candidates the gate marks INELIGIBLE), still behind a setting.
