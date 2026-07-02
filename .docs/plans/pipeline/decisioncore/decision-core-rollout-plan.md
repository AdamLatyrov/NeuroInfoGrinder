# Decision Core Implementation Plan

> **For agentic workers:** REQUIRED WORKFLOW: Use the `plan-researcher` agent for planning handoff, then use a backend/.NET-equivalent specialist or direct backend engineer to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely migrate NeuroInfoGrinder 2.0 from scattered stage-local decisions to a unified traceable decision core for Telegram message processing.

**Architecture:** Keep the existing pipeline running while a new shadow-only decision layer records independent semantic, risk, dedupe, context, ranking, and candidate-competition decisions. Roll out from schema-only to shadow observations, shadow ranking, graph discussion assembly, link enrichment, controlled routing, ranked LLM selection, and finally production decision switch with old-path fallback.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, PostgreSQL/Flyway, JSONB, existing replay pipeline tables, existing ModelHub/provider call infrastructure, existing DRAFT-only knowledge item storage.

---

## 0. Executive Summary

The architectural change is not cosmetic. The current pipeline lets cleanup, dedupe, bootstrap classification, clustering, single-message detection, discussion segmentation, route ML shadow, LLM Judge, and generation make partially overlapping local decisions. The new decision core introduces one normalized decision object per message/segment/cluster/link/material candidate and one candidate ledger per source set so decisions are comparable, reversible, explainable, and rankable.

The new core is:

- `semantic_decision_objects`: normalized decision state with independent axes.
- `semantic_decision_observations`: append-only observations from old and new stages.
- `candidate_decision_ledger`: competition history, winners, losers, duplicates, LLM eligibility, fallback policy.
- Supporting tables for retained context, relation graph, link enrichment, dedupe identities, claim/event identities, candidate ranking, safety gate, signal promotion, material update candidates, and quality metrics.

Safety stays conservative:

- Existing runtime behavior stays unchanged until controlled rollout phases.
- SignalStore still never writes materials.
- LLM Judge remains candidate-only.
- Material generation remains gated and DRAFT-only.
- No auto-publish.
- Risk/referral/abuse can become warning/signal/manual-review, not guide.
- Link-only cannot become material before enrichment.

Already implemented in Phase 1:

- Added Flyway migration `V29__decision_core_shadow_schema.sql` with shadow-only tables and indexes.
- Added DTO skeletons under `com.larbcorp.neuroinfogrinder2.decisioncore`.
- Added unit tests for independent eligibility axes and candidate ledger loser records.

## 1. Target Architecture

Target flow:

```text
raw_messages
  -> observations from normalization / cleanup / rules / classifiers / embeddings / links / safety
  -> SemanticDecisionObject
  -> context retention + link enrichment + relation graph + dedupe + candidate generation
  -> CandidateDecisionLedger records all candidate alternatives
  -> CandidateRanker selects top candidates under budget/caps
  -> IndependentSafetyGate confirms allowed route
  -> LLM Judge for selected candidates only
  -> material generation only after gates
  -> DRAFT save / signal / manual review / reject
```

Key rule: old stages no longer make final decisions directly once the new path is active. They write observations, flags, and candidate proposals. Final material route is produced by the decision core and ledger.

## 2. New Data Model

### `semantic_decision_objects`

Purpose: canonical decision state for one `message`, `discussion_segment`, `cluster`, `link`, or `material_candidate`.

Key fields:

- Identity: `trace_version`, `decision_version`, `mode`, `object_type`, `object_id`, `run_id`, `raw_message_id`, `dataset_message_id`, `discussion_segment_id`, `cluster_level`, `cluster_id`, `source_message_ids`.
- Independent labels: `meaning_labels`, `content_class_labels`, `topic_candidates`, `entity_candidates`, `link_entities`.
- Scores: `value_score`, `readiness_score`, `context_need_score`, `evidence_score`, `risk_score`, `duplicate_score`, `novelty_score`, `source_quality_score`, `actionability_score`.
- Routes: `material_route_candidates`, `final_route`, `artifact_type_candidates`, `required_artifact_type`.
- Identities: `dedupe_identity`, `cluster_identity`, `discussion_identity`, `time_window_id`.
- Eligibility axes: `context_eligibility`, `material_eligibility`, `signal_eligibility`, `enrichment_eligibility`, `llm_eligibility`, `manual_review_eligibility`.
- Explainability: `reason_codes`, `hard_blocks`, `soft_warnings`, `model_outputs`, `rule_outputs`.

Indexes:

- Unique identity by decision version/object/run/cluster identity.
- Run/object/final route.
- Raw/dataset message lookup.
- GIN on `source_message_ids`.
- Dedupe, cluster, discussion identity indexes.

Retention:

- Keep indefinitely for replay-backed runs and material/signals sources.
- Optional TTL later for non-material raw live trace older than operational retention window.

### `semantic_decision_observations`

Purpose: append-only stage observations feeding the decision object.

Fields:

- `decision_object_id`, `run_id`, `stage_name`, `observer_name`, `observation_type`, `confidence`, `payload_json`, `reason_codes`, `created_at`.

Indexes:

- By decision object/stage/type.
- By run/stage/time.

Retention:

- Same as parent decision object.

### `candidate_decision_ledger`

Purpose: records candidate competition, not only the winner.

Fields:

- `decision_object_id`, `run_id`, `candidate_group_id`, `candidate_id`, `candidate_type`, `event_type`, `event_status`, `winner`.
- `competing_candidate_ids`, `duplicate_anchor_id`, `material_candidate_id`, `knowledge_item_id`, `provider_call_id`.
- `rank_score`, route/artifact before/after, `reason_codes`, retained context nodes, excluded messages, details.

Indexes:

- Candidate group timeline.
- Run/event/status.
- Decision object.

Example rows:

- `CANDIDATE_CREATED`: `single:42`, reason `SINGLE_MESSAGE_HEURISTIC_MATCH`.
- `LOSER_RECORDED`: `single:42` loses to `discussion:77`, reason `DISCUSSION_HAS_STRONGER_EVIDENCE`.
- `DUPLICATE_SUPPRESSED`: `cluster:12`, duplicate anchor `material:132`, reason `SAME_CLAIM_LOW_CONFIDENCE_ANCHOR`.
- `LLM_SKIPPED`: candidate risk block, reason `RISK_SENSITIVE_MANUAL_ONLY`.
- `FALLBACK_DRAFT_FORBIDDEN`: reason `PROVIDER_PRICING_CLAIM_NEEDS_GENERATION_AND_CAVEAT`.

### `context_retained_nodes`

Purpose: stores messages not material-ready but useful as context graph evidence.

Fields:

- `decision_object_id`, `raw_message_id`, `dataset_message_id`, `run_id`, `retention_reason`, `node_role`, eligibility JSON, `expires_at`.

Roles:

- `context`, `link_only`, `answer_fragment`, `correction`, `confirmation`, `disagreement`, `risk_context`, `promo_context`, `pricing_signal`, `api_signal`.

Retention:

- Default 7-30 days by role for live non-material nodes.
- Longer for nodes linked to knowledge signals/materials.

### `message_relation_edges`

Purpose: graph edges between message/decision nodes.

Fields:

- Source/target decision object ids, source/target dataset message ids, `edge_type`, `weight`, `negative_constraint`, `evidence_json`.

Edge types:

- `reply_to`, `quote`, `same_thread`, `same_canonical_url`, `same_entity`, `same_provider`, `same_error_signature`, `temporal_proximity`, `author_alternation`, `question_answer`, `problem_solution`, `confirmation`, `correction`, `contradiction`, `same_source`, `spam_burst_penalty`, `risk_relation`.

### `link_enrichment_jobs`

Purpose: queue for link enrichment.

Fields:

- `decision_object_id`, raw/dataset ids, URL/canonical URL, status, priority, attempts, next attempt, last error.

Retries:

- Exponential backoff, max attempt policy per domain category.

### `link_enrichment_results`

Purpose: normalized metadata and risk classification for links.

Fields:

- Original/canonical/final URL, domain, category, reputation, referral flag, shortlink flag, risk flags, title/description, repo metadata, provider entities, result JSON, `fetched_metadata_is_instruction=false`.

Invariant:

- Fetched metadata is data, never prompt instruction.

### `dedupe_identities`

Purpose: stable duplicate identities independent of clusters.

Types:

- `exact_text`, `canonical_url`, `entity_intent`, `claim`, `event`, `embedding_near_duplicate`, `source_overlap`, `material_semantic`.

Important field:

- `anchor_review_status`: `UNREVIEWED`, `REVIEWED_GOOD`, `REVIEWED_BAD`, `DISABLED_ANCHOR`.

### `claim_units`

Purpose: fact/claim hash for late dedupe, verification, and contradiction handling.

Fields:

- `claim_hash`, `claim_text`, `claim_type`, source messages, confidence, verification status.

### `event_identities`

Purpose: groups repeated reports of the same event without treating same topic as duplicate.

Fields:

- `event_identity`, `event_type`, canonical entities, source messages, first/last seen, confidence.

### `candidate_rankings`

Purpose: rank candidates before LLM.

Fields:

- Candidate group/type, rank position/score, component scores, selected-for-LLM flag, budget bucket, reason codes.

### `safety_gate_results`

Purpose: independent final safety gate result.

Fields:

- Safety class, hard block, manual review, risk signal, warning allowed, risk score, flags, observations.

### `signal_promotion_runs`

Purpose: batch process for promoting/staling/false-marking signals.

Fields:

- Trigger type, status, counts, metrics, error.

### `material_update_candidates`

Purpose: proposed updates from signal promotion or corrections, never automatic material rewrite.

Fields:

- Knowledge item, decision object, promotion run, update type, status, proposed changes, source messages, reason codes.

### `quality_metrics_snapshots`

Purpose: durable quality metrics by run/dataset/time window.

Fields:

- Snapshot name/scope, run/dataset/window, metrics JSON.

## 3. Pipeline Changes By Existing Stage

### normalization

Old: normalizes text and contributes to local rule decisions.

New:

- Writes `semantic_decision_observations` with normalized text length, language, media type, source scope, text/caption availability.
- Creates/updates message-level `semantic_decision_objects` in shadow mode.
- Does not final reject.

### cleanup

Old: may suppress low text/noise-like content.

New:

- Writes observations: noise-like, media-only, short text, template markers.
- Sets eligibility proposals: material ineligible, context eligible if it can support a graph.
- Cannot discard from context by itself except hard corrupt/unparseable input.

### dedupe

Old: exact normalized hash can suppress LLM/material path.

New:

- Early dedupe writes `dedupe_identities` and observations.
- Suppression only applies to material creation, not trace/context retention.
- Corrections and contradictions are never suppressed solely by same entity/topic.

### rule_signals

Old: local labels and hard signal.

New:

- Writes `rule_outputs`, `reason_codes`, link/code/error/price/question observations.
- Proposes route candidates but does not final-route material.

### bootstrap_classification

Old: `NOISE_OR_CHAT` can suppress unless hard signal.

New:

- Writes model outputs and confidence.
- Low-confidence/noise-with-hard-signal goes to active learning and context retention, not terminal discard.

### embeddings

Old: filters out non-material signals before embeddings.

New:

- Embeds material-eligible and context-retained nodes as separate embedding purposes where cost allows.
- Embedding result is technical similarity only; it cannot create final material route.

### clustering

Old: connected components and macrocluster grouping may over-merge.

New:

- Produces cluster candidates and cluster identity observations.
- Cluster creation does not imply material candidate.
- Cluster coherence report required before candidate generation.

### single_message_detection

Old: creates candidate/signal/reject from heuristics/usefulness.

New:

- Writes single-message candidate proposal.
- Writes ledger competition row.
- Final decision waits for ranking and safety gate.

### discussion_segment

Old: sliding window builder can create persisted segments.

New:

- Sliding window becomes fallback observation source.
- GraphDiscussionSegmentBuilder becomes primary segment extractor.
- Both write candidates into ledger; ranking decides if either competes for LLM.

### route ML shadow

Old: shadow can sometimes block through route policy.

New:

- Writes model observation, confidence, uncertainty, abstain reason.
- In shadow phases it cannot block production decisions.
- In active phases it can contribute hard block only through IndependentSafetyGate or explicit route policy.

### LLM Judge

Old: receives candidates from local stage iteration order.

New:

- Receives top-N candidates from CandidateRanker after safety and budget filters.
- Writes provider call id and judge output to ledger and observations.

### material generation

Old: generation or fallback can create DRAFT.

New:

- Generation requires `material_eligibility=ELIGIBLE`, safety pass, rank selection, Judge accepted, and route-specific evidence minimum.
- Fallback uses `DRAFT_FALLBACK_NEEDS_REVIEW`, separate review queue, no duplicate anchor until reviewed.

### DRAFT save

Old: inserts `knowledge_items` DRAFT.

New:

- Still DRAFT-only.
- Links back to decision object and ledger event in later migrations.
- Does not rewrite existing materials automatically.

## 4. New Stages

### DecisionObjectBuilder

Input: `raw_messages`, `dataset_messages`, `replay_run_messages`, existing trace rows.

Processing:

- Upserts message-level `semantic_decision_objects`.
- Initializes independent scores to zero/unknown.

Output/writes:

- `semantic_decision_objects`.
- observation `DECISION_OBJECT_CREATED`.

Failure behavior:

- Fail-open in shadow: log and continue old pipeline.

Idempotency:

- Unique object identity by version/object/run/message.

### ContextRetentionGate

Input: decision object + observations.

Processing:

- Determines `retain_for_context` independent of material eligibility.

Writes:

- `context_retained_nodes`.
- ledger event `CONTEXT_NODE_RETAINED`.

Failure:

- Does not block old pipeline.

### LinkEnrichmentStage

Input: decision objects with links or `NEEDS_LINK_ENRICHMENT`.

Processing:

- Canonicalize URL.
- Detect referral/affiliate params.
- Resolve shortlinks safely.
- Fetch metadata under timeout/domain policy.
- Extract title, description, repo/provider/tool info.
- Classify domain category/reputation/risk.

Writes:

- `link_enrichment_jobs`.
- `link_enrichment_results`.
- observations and ledger re-score events.

Failure:

- Retry with backoff.
- If unsafe domain or fetch blocked, route signal/manual-review; never material.

### MessageRelationGraphBuilder

Input: decision objects, retained nodes, links, entities, authors, threads.

Processing:

- Adds positive and negative weighted edges.
- Negative constraints include spam burst, promo/risk-to-legit separation, contradictory claims, unrelated domains.

Writes:

- `message_relation_edges`.

### GraphDiscussionSegmentBuilder

Input: relation graph.

Processing:

- Extracts connected subgraphs with positive weight threshold and no hard negative constraints.
- Applies adaptive time windows by relation type.
- Scores evidence, roles, source span, coherence.

Writes:

- `discussion_segments`, `discussion_segment_sources` when persisted.
- decision objects and ledger candidates.

### MultiLevelDedupeStage

Input: decision objects, links, claims, events, embeddings, source overlap.

Processing:

- Early exact duplicate detection.
- Late semantic/fact/material duplicate checks.

Writes:

- `dedupe_identities`, `claim_units`, `event_identities`, ledger duplicate events.

### ClusterIdentityBuilder

Input: microclusters, entities, links, claims, risk, artifact proposals.

Processing:

- Builds `cluster_identity` and coherence report.
- Splits/blocks over-merged clusters.

Writes:

- decision object cluster fields, observations, ledger exclusions.

### CandidateGenerator

Input: decision objects, graph segments, cluster identities, link enrichment, safety observations.

Processing:

- Emits single/discussion/cluster/link-enriched/signal/manual candidates.

Writes:

- decision objects for material candidates.
- ledger `CANDIDATE_CREATED`.

### CandidateRanker

Input: all candidates in a source/time group.

Processing:

- Applies hard rules, then route-specific scoring.

Writes:

- `candidate_rankings`.
- ledger winners/losers.

### IndependentSafetyGate

Input: decision object + candidate + link enrichment + risk observations.

Processing:

- Determines hard block/manual review/risk signal/warning allowed.

Writes:

- `safety_gate_results`.

### SignalPromotionPipeline

Input: `knowledge_signals`, enrichment results, repeated weak signals, new answers/corrections.

Processing:

- Re-scores signal groups.
- Promotes to candidate/manual review or marks stale/false.

Writes:

- `signal_promotion_runs`, `material_update_candidates`, ledger rows.

### CandidateDecisionLedgerWriter

Input: every stage event.

Processing:

- Writes normalized event rows.

Failure:

- Shadow mode fail-open; active mode fail-closed for material generation only if ledger is required.

### QualityMetricsCollector

Input: replay runs, ledgers, decisions, reviews.

Processing:

- Computes recall/precision/proxy quality metrics.

Writes:

- `quality_metrics_snapshots`.

## 5. Decision Rules

### hard_reject_all

Use only when:

- corrupt/unparseable source cannot be traced;
- explicit abuse/fraud/access-circumvention unsafe how-to with no value as warning signal;
- malware/credential leakage or direct harm instruction requiring exclusion;
- policy says do not retain.

### reject_for_material_now but retain_for_context

Use when:

- `LOW_VALUE` but relation graph useful: short answer, confirmation, correction, disagreement;
- `INSUFFICIENT_CONTEXT`;
- media/text fragment that may answer previous question;
- duplicate that confirms same event.

### persist_as_knowledge_signal

Use when:

- weak but potentially useful signal exists;
- link-only has topic/provider/pricing/free-token relevance;
- risk/referral should be tracked;
- outage/status repeated but not material-ready.

### route_to_link_enrichment

Use when:

- link-only;
- hidden textUrl;
- shortlink;
- docs/repo/pricing/status/blog/paper URL;
- suspicious domain needing classification.

### route_to_discussion_assembly

Use when:

- question/answer split across messages;
- problem/solution fragments;
- repeated confirmations/corrections;
- short replies with same entity/error/link.

### route_to_single_candidate

Use only when:

- evidence is standalone;
- no required context/link enrichment;
- safety does not require manual review;
- artifact type can be grounded from text.

### route_to_cluster_candidate

Use when:

- multiple messages have coherent entity/claim/event identity;
- no negative merge constraints;
- cluster coherence score passes threshold.

### route_to_manual_review

Use when:

- risk-sensitive warning may be useful;
- classifier disagreement;
- high value but low evidence;
- link reputation unknown and material value high.

### route_to_llm_judge

Use when:

- candidate selected by CandidateRanker;
- IndependentSafetyGate allows Judge;
- budget and caps available;
- duplicate/material coverage does not suppress.

### material generation allowed

Requires:

- `material_eligibility=ELIGIBLE`;
- `llm_eligibility=ELIGIBLE`;
- safety hard block false;
- Judge accepted;
- evidence minimum satisfied;
- DRAFT-only status.

### only DRAFT_FALLBACK_NEEDS_REVIEW

Allowed when:

- Judge accepted;
- generation failed transiently;
- evidence is source-grounded and non-risky;
- no provider/pricing/API exact claims without caveat;
- no duplicate anchor until human review.

## 6. Migration Plan

### Phase 1: Schema Only, No Behavior Change

Goal: introduce storage and DTOs.

Implementation:

- Add `V29__decision_core_shadow_schema.sql`.
- Add DTOs and tests.

Tables affected:

- New tables only.

Feature flags:

- None required; no runtime code writes yet.

Validation metrics:

- Flyway migration success.
- Backend tests pass.

Rollback criteria:

- Migration syntax/performance issue. Roll back before production deploy or add corrective migration.

### Phase 2: Shadow Observations

Goal: old pipeline works as-is while new tables fill.

Implementation:

- Add `DecisionObjectBuilder` and `SemanticDecisionObservationWriter`.
- Write observations at normalization, cleanup, rule, classifier, embedding, single-message, discussion, cluster, LLM stages.

Feature flags:

- `decisionCoreShadowEnabled=false` default locally until ready, then true in controlled env.
- `decisionCoreFailOpen=true`.

Validation:

- Coverage: decision objects per replay message.
- No change in knowledge item counts vs old path.

Rollback:

- Disable `decisionCoreShadowEnabled`.

### Phase 3: Shadow Candidate Ranking

Goal: compute candidates/ranks but do not affect LLM.

Implementation:

- Add CandidateGenerator and CandidateRanker in read-only/shadow path.
- Write rankings/ledger.

Validation:

- Compare old LLM-sent candidates vs new top-N.
- Measure ranker hit rate on controlled datasets.

Rollback:

- Disable `decisionCoreRankingShadowEnabled`.

### Phase 4: Shadow Discussion Graph

Goal: graph segments are computed but not materialized.

Implementation:

- Add MessageRelationGraphBuilder and GraphDiscussionSegmentBuilder.
- Keep sliding window as fallback observation.

Validation:

- Overlap with manual overnight expected chains.
- Undermerge/overmerge metrics.

Rollback:

- Disable `decisionCoreGraphShadowEnabled`.

### Phase 5: Link Enrichment Shadow

Goal: enrich links without material route changes.

Implementation:

- Add enrichment job worker and result table writes.
- Use result to update signal evidence only.

Validation:

- Conversion rate from `NEEDS_LINK_ENRICHMENT` to signal/manual/material-candidate proposal.
- No material created from enrichment alone.

Rollback:

- Disable worker flag and leave pending jobs.

### Phase 6: Controlled Routing

Goal: new core controls routing on bounded chats/topics/datasets.

Implementation:

- Enable decision core final route for allowlisted datasets/chats.
- Old path remains fallback.

Validation:

- Material precision on controlled datasets.
- Zero auto-publish.
- No unsafe guide from risk/referral.

Rollback:

- Disable allowlist flag.

### Phase 7: LLM Top-N After Ranking

Goal: LLM receives candidates selected by CandidateRanker.

Implementation:

- Replace old iteration order with rank queue for allowlisted scopes.
- Apply budget/caps after ranking.

Validation:

- Lower LLM calls per material-ready candidate.
- Better material candidate precision.

Rollback:

- Revert flag to old LLM source.

### Phase 8: Production Switch

Goal: decision core is default production routing.

Implementation:

- Enable active mode globally after metrics pass.
- Keep old path as fallback and comparison for at least one release.

Validation:

- Quality dashboards green.
- No increase in risk bypass/fallback materials/overmerge.

Rollback:

- Set `decisionCoreMode=SHADOW` or `decisionCoreRoutingEnabled=false`.

## 7. Backward Compatibility

- Existing `knowledge_items` remain DRAFT and are not rewritten.
- Existing bad materials are not edited/deleted automatically.
- Old `semantic_decision_json` from `replay_run_messages` and `discussion_segments` maps into new decision objects only in scoped dry-run/backfill jobs.
- Duplicate anchors from old materials start `UNREVIEWED`; they cannot permanently suppress new candidates until reviewed or confidence threshold passes.
- No uncontrolled mass reprocess.
- Use scoped replay/dry-run replay per dataset/chat/time window.

## 8. Testing Plan

### Unit Tests

- DTO serialization and required axes.
- Decision rules: link-only, low-value context, risk/referral, fallback policy.
- Rank formula component calculations.

### Integration Tests

- Replay pipeline writes decision objects in shadow without changing old outputs.
- Ledger records winners/losers.
- Link enrichment job lifecycle.

### Replay Tests

- Existing NIGTOP scenarios.
- ZAUR controlled batch.
- Overnight manual expected-material window.

### Golden Datasets

- Single standalone guide.
- Q/A split across messages.
- Link-only before/after enrichment.
- Hidden referral.
- Promo burst.
- Risk warning.
- Same provider outage vs pricing not merged.

### Adversarial Safety Tests

- BIN/CVV/card fraud.
- Trial/access bypass.
- Obfuscated referral params.
- URL shorteners.
- Non-Russian/non-English spam.
- Unsafe how-to framed as education.

### Overmerge Tests

- Duet/Literouter/Langdock/PrismaticAPI must not merge without list/comparison intent.
- Promo/risk and legit reference must not merge.

### Undermarge Tests

- Slow question-answer chain across hours with reply edge.
- Correction attaches to previous event.

### Link Enrichment Tests

- Hidden textUrl extraction.
- Shortlink resolution.
- Referral detection.
- GitHub repo metadata.
- Fetched metadata treated as data only.

### Dedupe Tests

- Same URL different caption.
- Same claim different wording.
- Same event multiple confirmations.
- Correction not suppressed.
- Bad unreviewed material anchor does not hard-block.

### LLM Prompt Contract Tests

- Judge receives coherence report.
- Generation requires artifact type match.
- Risk candidate cannot become guide.

### Fallback Material Tests

- Allowed for safe source-grounded case.
- Forbidden for pricing/API/provider exact claims without caveat.
- Status is `DRAFT_FALLBACK_NEEDS_REVIEW`.

### Migration Tests

- Flyway applies V29 on clean schema.
- Indexes and checks exist.
- Existing data unaffected.

## 9. Observability And Metrics

Dashboards are backend/admin monitoring, not user-facing UI requirement.

### Decision Core Dashboard

- Decision objects per run/stage/object type.
- Eligibility distribution.
- Final route distribution.

### Candidate Competition Dashboard

- Candidate groups.
- Winners/losers.
- Single vs discussion vs cluster outcomes.
- LLM sent/skipped reasons.

### Signal Promotion Dashboard

- Signals promoted/staled/false.
- Signal-to-material conversion.
- Repeated weak signals.

### Link Enrichment Dashboard

- Jobs by status.
- Domain categories.
- Referral/suspicious rate.
- Conversion after enrichment.

### Cluster Quality Dashboard

- Cluster purity.
- Overmerge rate.
- Undermerge rate.
- Split/merge decisions.

### Safety Bypass Dashboard

- Hard blocks.
- Manual review route.
- Risk signal route.
- Bypass regressions from golden set.

### LLM Budget Dashboard

- Calls per 1000 messages.
- Top-N selected vs skipped.
- Cost per material-ready candidate.

### Fallback Materials Dashboard

- Fallback count/rate.
- Review status.
- Forbidden fallback reasons.

### Quality Regression Dashboard

- Useful signal recall.
- False reject rate.
- False materialization rate.
- Duplicate suppression error.
- Candidate ranker hit rate.

## 10. Risks And Mitigations

### Schema Bloat

Risk: many tables and JSONB columns.

Mitigation: shadow tables are append-only/normalized; add retention policies before global active mode.

### Performance Overhead

Risk: graph and enrichment cost.

Mitigation: feature flags, scoped rollout, batch writes, indexes, async workers.

### Double-Writing Bugs

Risk: old and new trace diverge.

Mitigation: fail-open in shadow, comparison metrics, idempotent upserts.

### Inconsistent Old/New Decisions

Risk: confusion during rollout.

Mitigation: ledger records old path as observation until active switch.

### Too Many Context-Retained Nodes

Risk: graph noise/explosion.

Mitigation: role-specific TTL, max nodes per scope, spam burst penalty.

### Graph Explosion

Risk: O(N^2) relation building.

Mitigation: scope buckets, candidate blocking by time/thread/entity/url, capped neighbors.

### Link Enrichment Cost

Risk: external fetch volume.

Mitigation: domain cache, canonical URL dedupe, priority queue, retry caps.

### LLM Cost

Risk: more candidates.

Mitigation: ranking before LLM, budget-aware top-N, route-specific caps.

### False Safety Blocks

Risk: useful warning never materializes.

Mitigation: warning route/manual review, golden set, reviewed risk labels.

### Ranking Starves Rare Classes

Risk: high-frequency low-value classes dominate.

Mitigation: route-specific quotas and rare-class exploration slots.

### Migration Inconsistency

Risk: old anchors/materials suppress incorrectly.

Mitigation: all old anchors unreviewed; no permanent suppression until reviewed.

## 11. Concrete Implementation Checklist

### Database Migrations

- [x] Add V29 shadow decision core schema.
- [ ] Add optional FK columns from `knowledge_items` to decision object after shadow validation.
- [ ] Add retention/cleanup job tables if needed.

### DTOs

- [x] Add `SemanticDecisionCoreObject`.
- [x] Add `CandidateDecisionLedgerEntry`.
- [x] Add shared enums.
- [ ] Add request/response DTOs for internal services.

### Services

- [ ] `DecisionObjectBuilder`.
- [ ] `SemanticDecisionObservationWriter`.
- [ ] `CandidateDecisionLedgerWriter`.
- [ ] `ContextRetentionGate`.
- [ ] `MessageRelationGraphBuilder`.
- [ ] `GraphDiscussionSegmentBuilder`.
- [ ] `LinkEnrichmentStage`.
- [ ] `MultiLevelDedupeStage`.
- [ ] `ClusterIdentityBuilder`.
- [ ] `CandidateGenerator`.
- [ ] `CandidateRanker`.
- [ ] `IndependentSafetyGate`.
- [ ] `SignalPromotionPipeline`.
- [ ] `QualityMetricsCollector`.

### Repositories

- [ ] Jdbc repository for decision objects.
- [ ] Jdbc repository for observations.
- [ ] Jdbc repository for ledger.
- [ ] Jdbc repository for graph edges.
- [ ] Jdbc repository for enrichment jobs/results.
- [ ] Jdbc repository for dedupe/claims/events.

### Feature Flags

- [ ] `decisionCoreShadowEnabled`.
- [ ] `decisionCoreFailOpen`.
- [ ] `decisionCoreRankingShadowEnabled`.
- [ ] `decisionCoreGraphShadowEnabled`.
- [ ] `decisionCoreLinkEnrichmentEnabled`.
- [ ] `decisionCoreControlledRoutingEnabled`.
- [ ] `decisionCoreLlmRankedSelectionEnabled`.
- [ ] `decisionCoreMode=SHADOW|CONTROLLED|ACTIVE`.

### Workers And Queues

- [ ] Link enrichment worker.
- [ ] Signal promotion worker.
- [ ] Quality metrics snapshot worker.

### Metrics

- [ ] Decision object coverage.
- [ ] Candidate competition outcomes.
- [ ] Link enrichment conversion.
- [ ] Cluster purity/overmerge/undermerge.
- [ ] Safety bypass.
- [ ] Fallback material rate.

### Tests

- [x] DTO/ledger basic unit tests.
- [ ] Flyway migration integration test.
- [ ] Shadow write integration test.
- [ ] Graph discussion tests.
- [ ] Link enrichment tests.
- [ ] Dedupe identity tests.
- [ ] Ranking tests.
- [ ] Independent safety gate tests.
- [ ] Fallback policy tests.
- [ ] Controlled replay regression suite.

### Rollout Steps

- [x] Phase 1 local schema/DTO/test implementation.
- [ ] Deploy schema-only after approval.
- [ ] Enable shadow observations in dev/control runs.
- [ ] Compare old vs new decisions on controlled datasets.
- [ ] Enable ranked LLM only for allowlisted datasets.
- [ ] Switch production routing after quality gates pass.
