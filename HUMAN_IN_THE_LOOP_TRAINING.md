# Human-in-the-loop training

## Purpose

The production pipeline should accumulate data automatically and ask humans to review only the highest-value or most ambiguous cases. Classifier-v1 must train on reviewed examples, not on raw generated drafts.

## What the system labels automatically

Every production replay run stores weak training signals from rules, BERT replay, duplicate handling, LLM cluster decisions, generated item sources, and high-signal message patterns.

Current weak sources include:

- `WEAK_RULE_SUPPRESSION`
- `WEAK_BERT_REPLAY`
- `WEAK_LLM_ACCEPTED_CLUSTER`
- `WEAK_LLM_REJECTED_CLUSTER`
- `WEAK_DUPLICATE_GROUP`
- `WEAK_GENERATED_ITEM_SOURCE`
- `WEAK_NOISE_SUPPRESSION`
- `WEAK_PRICE_ACCESS_SIGNAL`
- `WEAK_ERROR_FIX_SIGNAL`
- `WEAK_RESOURCE_SIGNAL`
- `WEAK_QUESTION_ANSWER_SIGNAL`

Each weak example must preserve source, confidence, reason/context, run id, dataset message id, model version, and created timestamp. Weak labels are useful for discovery, prioritization, QA, and offline analysis. They are not strong labels.

## What humans review

Humans review a daily active queue, not every message. The queue is capped and targeted so editors spend time on cases that improve the classifier most.

Default daily target: `50-200` items.

Daily queue composition:

- `20%` suspected false noise
- `15%` low confidence classifier
- `15%` useful unclustered messages
- `15%` generated knowledge item review
- `10%` rare classes
- `10%` price/access
- `10%` error/fix
- `5%` duplicate/source support issues

If the system has fewer eligible items, the batch is smaller. If there are many eligible items, the batch is capped.

## Daily review batch

Create a daily batch with:

```http
POST /api/v2/labeling/batches/create-daily-review-batch
```

Example request:

```json
{
  "date": "2026-06-23",
  "targetSize": 150,
  "strategy": "DAILY_ACTIVE_LEARNING",
  "includeRunsFromLastHours": 24
}
```

The response includes `batchId`, `itemCount`, `bucketCounts`, `estimatedReviewMinutes`, and `priorityReasons`.

## Fast review UI

The UI shows the message, queue reason, classifier/rule suggestion, source context, cluster context, and generated material if present.

Quick actions:

- `Useful`
- `Not useful`
- `Noise`
- `Wrong class`
- `Good material`
- `Needs edit`
- `Bad source support`
- `Duplicate`
- `Unsafe`
- `Needs context`
- `Skip`

Hotkeys:

- `1` Useful
- `2` Not useful
- `3` Noise
- `4` Good material
- `5` Needs edit
- `6` Bad source support
- `7` Wrong class
- `8` Duplicate
- `9` Needs context
- `S` Skip
- `Enter` Save and next

## What becomes strong training data

Strong classifier-v1 examples can only come from explicit human or reviewed sources:

- `HUMAN_LABEL`
- `CORRECTED_PIPELINE`
- `ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW`
- `REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW`

Weak labels must not be mixed into classifier-v1 as strong labels without an explicit weak-label experiment flag.

Quick action examples:

- `Noise` creates `usefulness_label=NOISE`, `decision_label=SUPPRESS`, `artifact_type_label=NONE`, `actionability_label=NOT_ACTIONABLE`, `source=HUMAN_LABEL`.
- `Useful` creates `usefulness_label=USEFUL`, `decision_label=CANDIDATE` or `ACCUMULATE`, `source=HUMAN_LABEL`.
- `Good material` creates an accepted knowledge item review and source-message training examples with `source=ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW`.
- `Bad source support` creates a `BAD_SOURCE_SUPPORT` review verdict, `evidence_label=UNSUPPORTED_CLAIM`, and `source=REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW`.

Every non-skip human action should create a `labeling_event`, a `training_example`, updated label distribution counters, and an audit trail linking the event to the training example.

## Readiness for classifier-v1

`GET /api/v2/training/data-readiness` reports real human progress:

- total human-reviewed examples
- strong examples
- weak examples
- reviewed today
- reviewed this week
- seven-day review average
- remaining examples to the minimum
- rare class gaps
- estimated days to classifier-v1 at current pace

Minimum gate for classifier-v1 is intentionally conservative: at least `500` human-reviewed examples plus rare-class coverage. A better early iteration target is `1k-3k` reviewed examples.

## Why weak labels are not enough

Weak labels are produced by heuristics and models. They can contain systematic classifier errors, rule bias, duplicate leakage, unsupported generated claims, and noisy class boundaries. Training classifier-v1 directly on weak labels would reinforce those mistakes.

Good accumulation targets:

- `10k+` raw/candidate examples can be accumulated automatically.
- `1k-3k` reviewed examples are enough for early classifier iterations.
- `300-1000` accepted/rejected knowledge item reviews are useful.

Bad target:

- `10k` generated unreviewed guides are not a good training dataset because they mostly teach the model to imitate unverified generation artifacts, not human-confirmed value judgments.
