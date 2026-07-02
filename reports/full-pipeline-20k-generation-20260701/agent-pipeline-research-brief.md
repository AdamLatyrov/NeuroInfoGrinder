# Agent Brief: Research Why NeuroInfoGrinder Generated Bad Materials

## Mission

Research why the current NeuroInfoGrinder 2.0 message pipeline generated poor-quality materials from a 20,000-message production run, and propose a production-quality material-selection architecture that prevents bad material generation before LLM spend.

This is a research task, not an implementation task.

## Hard Constraints

- Do not modify source code.
- Do not deploy.
- Do not delete or clean up generated materials.
- Do not reprocess, backfill, or requeue production messages.
- Do not change prompts, provider config, model-worker config, BGE settings, thresholds, or pipeline settings.
- Do not print secrets, environment variables, API keys, DB passwords, proxy credentials, or Telegram credentials.
- Work from the evidence files in this folder and source-backed external research.
- The output should be a research artifact, not code.

## Evidence Folder

Use this folder as the primary evidence bundle:

`reports/full-pipeline-20k-generation-20260701/`

Required files to inspect:

- `materials.valid.json`
- `run-summary.valid.json`
- `mini-runs.tsv`
- `post-run-audit.json`
- `post-run-audit.md`
- `run-report.md`

Useful related project files:

- `reports/message-lab-20k-20260630-v2.md`
- `reports/message-lab-20k-20260630-v2.json`
- `reports/message-lab-algorithm-comparison-20k-20260630-v3.md`
- `reports/message-lab-algorithm-comparison-20k-20260630-v3.json`
- `scripts/audit-daily-materials-signals.js`
- `scripts/message_lab_core.py`
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/ReplayV2Service.java`
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/replay/MessageUsefulnessClassifier.java`
- `backend/2.0/src/main/java/com/larbcorp/neuroinfogrinder2/signals/KnowledgeSignalService.java`

## Observed Production Run

Adam explicitly requested a full production pipeline run over all 20,000 messages with no practical budget restriction.

Technical execution facts:

- Parent dataset: `15764`.
- Direct V2 run with `maxMessages=20000` was clamped by backend to `5000` and hit model-worker timeout.
- 1,000-message chunk runs also hit BGE/model-worker timeout.
- Final execution used 100 mini datasets of 200 messages each: `15814-15913`.
- All final mini-runs completed: `100/100`.
- Messages processed: `20,000`.
- Provider calls: `465`.
- Estimated cost: `$5.148730`.
- Generated DRAFT materials: `116`.
- Auto-publish: not enabled.

Generated material counts:

- `REFERENCE`: `72`
- `ANSWER`: `23`
- `SUMMARY`: `13`
- `GUIDE`: `5`
- `GENERATION`: `3`

Generated material source types:

- `SINGLE_MESSAGE`: `77`
- `MACRO`: `39`

## Audit Result

Post-run daily audit files:

- `post-run-audit.json`
- `post-run-audit.md`

Audit counts:

- Materials in window: `124`
- Flagged materials: `115`
- Signals in window: `241`
- Flagged signals: `167`

Material flag summary:

- `RULES_ONBOARDING_MATERIAL`: `18`
- `DUPLICATE_TITLE_IN_WINDOW`: `19`
- `RISK_PROMO_REFERRAL_REVIEW`: `22`
- `SINGLE_MESSAGE_MATERIAL`: `84`
- `SINGLE_SOURCE_MATERIAL`: `89`
- `UNVERIFIED_MODEL_PRICING_CLAIM`: `4`
- `EVENT_ANNOUNCEMENT_SINGLE_SOURCE`: `5`
- `JOB_POST_SINGLE_SOURCE`: `10`
- `LLM_SPENT_ON_SINGLE_SOURCE`: `89`
- `OTHER_SINGLE_SOURCE`: `49`

Material issue groups:

- `P2_WEAK_OTHER_SINGLE_SOURCE`: `37`
- `P2_POSSIBLY_USEFUL_BUT_SINGLE_SOURCE`: `27`
- `P1_RISK_MANUAL_REVIEW`: `20`
- `P0_RULES_ONBOARDING_DELETE`: `18`
- `KEEP_OR_NO_FLAGS`: `10`
- `P2_JOB_POST_NOT_MATERIAL`: `5`
- `P1_UNVERIFIED_MODEL_CLAIM`: `4`
- `P2_EVENT_NOT_MATERIAL`: `3`

Example duplicate groups:

- `Правила группы Russian IT in Dubai`: `11` materials.
- `Справка по назначению и правилам чата`: `2` materials.
- `Правила и назначение чата для серьезных обсуждений`: `2` materials.
- `Looper: проверка и оптимизация циклов для ИИ-агентов`: `2` materials.

## Primary Diagnosis To Validate

The pipeline technically processes messages end-to-end, but it fails as a production material generator.

The likely core problem is not BGE alone. BGE/embeddings can support similarity and grouping, but they cannot decide whether a candidate is durable knowledge. The broken boundary appears to be material eligibility before LLM Judge/generation.

The current system appears to confuse these concepts:

- useful signal;
- material candidate;
- durable material;
- topic;
- cluster;
- risk/review item;
- link-enrichment item;
- job/event announcement;
- rules/onboarding/moderation content.

Research must validate or correct this diagnosis with evidence.

## Core Questions

Answer these directly:

1. Did the pipeline fail as a material-quality system?
2. Is BGE-M3 the main reason, or is BGE only one supporting component?
3. Why did single-message and single-source candidates reach material generation?
4. Why did rules/onboarding, jobs, events, risk/referral, and unverified claims become materials?
5. Where should the pipeline stop candidates before LLM spend?
6. What should be deterministic classification vs embedding/clustering vs LLM Judge?
7. What should be persisted as signal/context/review instead of material?
8. What architecture prevents this failure from recurring?

## External Research Topics

Use internet research with authoritative or high-quality engineering sources. Prioritize official documentation and credible engineering/research articles.

Research topics:

- Noisy short-text classification pipelines.
- Short-text clustering limitations.
- Embedding/vector-search limitations: semantic similarity is not semantic eligibility.
- BGE-M3 and sentence-transformer use cases/limits.
- HDBSCAN/BERTopic/UMAP for topic clustering and their limits on short/noisy texts.
- Hybrid retrieval: lexical + vector + metadata filters.
- RAG ingestion quality gates.
- Evidence sufficiency before generation.
- LLM-as-judge limitations and proper placement.
- Duplicate/near-duplicate detection before generation.
- Moderation/risk classification for unsafe or abuse-like content.
- Entity/source authority requirements for pricing/model/provider claims.
- Handling ephemeral content: jobs, events, announcements.

Possible source families:

- BGE-M3 official docs or model card.
- Sentence Transformers docs.
- BERTopic docs.
- HDBSCAN docs.
- Qdrant, Pinecone, Weaviate, or Milvus docs/articles on vector search, filtering, dedupe, hybrid search.
- LangChain or LlamaIndex docs on document ingestion, indexing, deduplication, and RAG data quality.
- OpenAI, Anthropic, or Google guidance on evals/LLM judges if relevant.
- Academic/engineering writing on short-text clustering and social/chat stream classification.

Avoid generic blogspam. Cite every external claim.

## Required Research Output

Create or update:

`.docs/research/pipeline/material-quality/material-selection-pipeline-research.md`

The report must include these sections.

### 1. Executive Diagnosis

- State whether the pipeline failed.
- State whether BGE is the primary bottleneck.
- State the actual broken decision boundary.
- Give the recommended direction in one paragraph.

### 2. Observed Failure Modes

Use local evidence from this folder.

Cover:

- single-message overgeneration;
- single-source overgeneration;
- rules/onboarding leakage;
- job/event leakage;
- risk/referral leakage;
- unverified model/pricing claim leakage;
- duplicate material creation;
- chunk/context fragmentation;
- wasted LLM calls on candidates that should have been stopped earlier.

### 3. Root Cause Analysis

Explain:

- why useful signal is not the same as material;
- why one message rarely equals durable material;
- why embeddings cannot decide material eligibility;
- why LLM generation should be downstream of strict gates;
- why current single-message route is dangerous;
- why chunking can reduce global context and duplicate awareness;
- why the current pipeline needs a material eligibility layer, not only prompt tuning.

### 4. Current Pipeline Description

Describe the current likely pipeline from code/evidence:

- Telegram/raw ingestion;
- dataset snapshot/replay;
- normalization/features/rules;
- classical ML shadow;
- BERT/classifier stage;
- BGE embeddings;
- dedupe;
- semantic search;
- micro/macro clustering;
- topic discovery;
- cluster scoring;
- single-message detection;
- discussion segment detection;
- LLM Judge;
- knowledge generation;
- DRAFT material persistence;
- signal persistence.

Mark where bad candidates currently pass too far.

### 5. Target Pipeline Proposal

Design target stages that prioritize precision:

- raw message normalization;
- deterministic hard rejects;
- message class taxonomy;
- source/risk/authority classification;
- signal/context/link-enrichment routing;
- topic assignment;
- evidence grouping;
- semantic clustering only after hard filters;
- cluster quality scoring;
- evidence sufficiency gate;
- material eligibility gate;
- candidate ranking;
- LLM Judge only for shortlisted candidates;
- generation only after gate pass;
- post-generation audit.

### 6. Hard Routing Matrix

Create a routing matrix for these classes:

- rules/onboarding;
- moderation bot/system event;
- low-value chatter;
- media-only/weak context;
- link-only;
- referral/risk/abuse;
- job post;
- event announcement;
- model/pricing rumor;
- provider/API outage/status;
- tool/resource/repo;
- API troubleshooting;
- workflow/how-to;
- multi-message discussion;
- direct Q&A.

For each class define:

- material allowed: yes/no/only if aggregated;
- signal allowed;
- manual review;
- link enrichment required;
- minimum source/evidence count;
- allowed artifact types;
- hard rejection reason if not material.

### 7. BGE/Embedding Role

Answer:

- Should BGE be kept?
- What should BGE be responsible for?
- What should BGE never be responsible for?
- Should BGE be supplemented with lexical fingerprints, metadata filters, topic classification, HDBSCAN/BERTopic, cross-encoder reranking, or LLM duplicate checks?
- How should short Telegram messages be clustered safely?

### 8. Material Eligibility Gate

Define concrete gate logic.

Include rules such as:

- no rules/onboarding/moderation material;
- no risk/referral/abuse guide material;
- no link-only material before enrichment;
- no single-source job/event material;
- no unverified model/pricing/provider claim without official or corroborating source;
- no single-message material by default except strict whitelist;
- minimum independent evidence requirement for durable materials;
- minimum cluster quality requirement;
- source authority requirement for external provider facts;
- post-generation audit must block or flag before active display if gate violated.

### 9. Evaluation Metrics And Acceptance Criteria

Define measurable pass/fail gates.

Suggested targets:

- `0` rules/onboarding materials.
- `0` risk/referral guide materials.
- `0` single-source job/event materials.
- `0` unverified model/pricing claim materials.
- Single-message material rate below an explicit threshold or off by default.
- Flagged material rate below an explicit threshold.
- Duplicate material rate below an explicit threshold.
- Provider calls wasted on rejected candidates below an explicit threshold.
- Evidence sufficiency recorded for every generated material.
- Every generated material has topic/class/material route trace.

### 10. Recommended Implementation Roadmap

Prioritize stopping bad generation.

Suggested phases:

1. Stop bad generation with hard gates.
2. Add material eligibility scoring and explicit non-material routes.
3. Add topic/class taxonomy and UI-visible topic labels.
4. Improve clustering only after hard filters.
5. Add candidate ranking before LLM Judge.
6. Add duplicate/material-identity handling after material eligibility is fixed.
7. Add evaluation dashboard and regression corpus.

### 11. What Not To Do

Explicitly state what not to waste time on:

- Do not treat BGE as final judge.
- Do not fix this by prompt tuning alone.
- Do not let signals auto-materialize.
- Do not generate more materials until selection precision improves.
- Do not route risk/referral/abuse into guide generation.
- Do not lower thresholds to increase material count.
- Do not rely only on title dedupe.

## Expected Final Answer

End the research report with a direct answer to Adam:

- What is broken?
- Why did the pipeline generate bad materials?
- What should be changed first?
- What should not be changed first?
- What evidence would prove the fix works on the same 20k batch?

## Tone

Be direct and evidence-first. Do not soften the conclusion. If the pipeline failed, say it failed. The goal is to fix material quality, not defend the current architecture.
