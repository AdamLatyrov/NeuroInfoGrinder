# Classifier Fine-Tuning Plan

## Goal

Prepare the replay classifier for heterogeneous Telegram messages without baking concrete topics, vendors, products, or model names into labels. The classifier should learn stable value axes that keep working across AI, crypto, construction, business, dev tools, errors, pricing, news, promotions, and chat.

## Why Not One Flat Topic Classifier

A flat label such as `HOW_TO_GUIDE` or a topic-specific label such as `Claude`, `Runic`, `Nexus`, or `Kimi` mixes several different questions:

- is the message useful;
- what artifact can be produced from it;
- what role the message plays in a conversation;
- which broad domain it belongs to;
- whether it has evidence;
- whether a user can act on it.

That shape overfits to the current replay sample and makes the classifier brittle when the next Telegram export has different products, communities, slang, or language mix. Fine-tuning should teach reusable value structure, not memorize concrete names.

## Multi-Axis Label Schema

Use a multi-head or multi-label classifier with these heads.

### Axis 1: Usefulness

- `USEFUL`
- `POTENTIALLY_USEFUL`
- `NOT_USEFUL`
- `NOISE`

### Axis 2: Artifact Type

- `GUIDE`
- `NOTE`
- `TROUBLESHOOTING_NOTE`
- `COMPARISON_INSIGHT`
- `PRICE_ACCESS_CARD`
- `RESOURCE_CARD`
- `RISK_NOTE`
- `NEWS_SIGNAL`
- `TREND_CLUSTER`
- `NONE`

### Axis 3: Message Role

Multi-label:

- `QUESTION`
- `ANSWER`
- `ERROR_LOG`
- `FIX`
- `ANNOUNCEMENT`
- `PRICE_OR_ACCESS`
- `RESOURCE_LINK`
- `PROMO`
- `CHAT`
- `RISK`
- `CODE_OR_CONFIG`
- `COMPARISON`

### Axis 4: Domain

- `AI`
- `CRYPTO`
- `CONSTRUCTION`
- `BUSINESS`
- `DEV_TOOLS`
- `OTHER`

### Axis 5: Evidence Quality

Multi-label:

- `HAS_SOURCE`
- `HAS_DETAILS`
- `HAS_NUMBERS`
- `HAS_STEPS`
- `HAS_ERROR_SIGNATURE`
- `UNSUPPORTED_CLAIM`

### Axis 6: Actionability

- `ACTIONABLE`
- `REFERENCE_ONLY`
- `FYI_ONLY`
- `NOT_ACTIONABLE`

## Training Example Format

`training_examples` should keep the original text, context, split, source, confidence, and all axis labels:

- `text`
- `context_json`
- `usefulness_label`
- `artifact_type_label`
- `message_role_labels_json`
- `domain_label`
- `evidence_labels_json`
- `actionability_label`
- `source`
- `confidence`
- `split`
- `run_id`
- `created_at`

The classifier dataset export should emit JSONL with:

- `id`
- `text`
- `context`
- `labels.usefulness`
- `labels.artifactType`
- `labels.messageRoles`
- `labels.domain`
- `labels.evidence`
- `labels.actionability`
- `source`
- `split`

## Example Sources

Use both positive and negative examples:

- human labels from the labeling UI;
- corrected pipeline mistakes;
- accepted knowledge items with strong source support;
- rejected or low-value clusters as hard negatives;
- reviewed rule/BERT disagreements;
- LLM judge accepted/rejected clusters as weak labels;
- duplicate groups as duplicate or near-duplicate examples;
- noise suppression examples as negative examples;
- ads, weak news, questions without answers, errors without fixes, errors with fixes, price/access messages, resource links, and chat.

Weak labels must stay marked as weak through `source` and `confidence`; they should not silently become human-reviewed truth.

## Active Learning

Prioritize review with `v_active_learning_queue`. Highest priority cases:

- rule says suppress, classifier says useful;
- classifier confidence is low;
- cluster score is high but LLM rejected it;
- LLM accepted the cluster but no generation happened;
- generated item has low source support;
- hard signal is high but the message is not clustered;
- utility is high but the cluster was not sent to LLM;
- duplicate suspicion;
- rare classes with too few reviewed examples.

Review actions should capture:

- useful;
- not useful;
- wrong class;
- wrong artifact type;
- duplicate;
- source unsupported.

## Sampling Strategy

Do not sample only good material. A useful v1 set needs:

- short chat/noise;
- media-only or no-text cases;
- promotional messages;
- price and access messages;
- weak news and announcements;
- questions without answers;
- errors without fixes;
- errors with fixes;
- resource collections;
- duplicates and near duplicates;
- high hard-signal messages that failed clustering;
- accepted and rejected clusters.

Use stratified sampling by class, source chat, time, cluster id, confidence bucket, and rule/BERT disagreement reason.

## Dataset Size Targets

For `classifier-v1`:

- minimum: 500 reviewed examples;
- preferred: 1500-3000 reviewed examples;
- rare classes: at least 50-100 reviewed examples per class;
- include weak labels separately for pretraining or auxiliary experiments, but evaluate on reviewed labels only.

## Split Strategy

Use `time_and_chat_grouped`:

- no leakage by exact duplicate;
- no leakage by same cluster;
- no leakage by same chat/time burst;
- validation and test should include newer messages and different chats where possible.

Keep splits stable between experiments so before/after classifier comparisons are meaningful.

## Validation

Track at least:

- precision and recall for `USEFUL`;
- false suppression rate;
- artifact type accuracy;
- hard signal recall;
- noise precision;
- calibration by confidence;
- per-domain performance;
- rare-class recall;
- active-learning yield, meaning how many reviewed queue items become corrections.

The replay comparison should include:

- classification coverage;
- skipped reasons;
- class distribution;
- semantic neighbors;
- microclusters and macroclusters;
- clusters sent to LLM;
- judge accepted/rejected clusters;
- knowledge items;
- labeling items;
- provider calls and cost.

## Versioning

Use explicit classifier versions:

- `bootstrap`: current heuristic/model-worker baseline;
- `classifier-v1`: first reviewed multi-axis model;
- `classifier-v2`: next model after active-learning corrections and broader domains.

Store version metadata with:

- training dataset path;
- source counts;
- label distribution;
- validation metrics;
- replay comparison run ids;
- model artifact checksum;
- rollout date.

## Release And Rollback

Release a classifier only after replaying the same fixed dataset against the previous version and the candidate version. Roll back when:

- false suppression increases materially;
- useful recall drops;
- rare classes disappear from predictions;
- confidence calibration gets worse;
- knowledge item source support degrades;
- provider calls or generated noise rise unexpectedly.

Rollback should be a config/model-version switch in the model worker. Keep the previous model artifact and dataset manifest available until the next version is proven stable.

## Current Local State

The current replay pipeline now records classification audit data, skipped BERT reasons, duplicate-inherited classification source, multi-axis training fields, active-learning queue rows, and classifier dataset export.

Current observed training summary after run 11:

- total training examples: 980;
- `WEAK_NOISE_SUPPRESSION`: 794;
- `WEAK_BERT_REPLAY`: 166;
- `ACCEPTED_KNOWLEDGE_ITEM`: 10;
- `WEAK_ACCEPTED_KNOWLEDGE_ITEM`: 10 from the previous run remains in the local DB.

This is enough to inspect and bootstrap review, but not enough to claim classifier quality. `classifier-v1` still needs at least 500 human-reviewed examples, preferably 1500-3000.
