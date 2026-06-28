const fs = require('fs');

const judge = JSON.parse(fs.readFileSync('reports/discussion-segment-tightened-llm-results-raw-20260626.json', 'utf8'));
const review = JSON.parse(fs.readFileSync('reports/discussion-segment-accepted-review-20260626.json', 'utf8'));
const selectedIds = ['S0024', 'S0269', 'S0132'];
const selected = selectedIds.map((id) => {
  const judgeItem = judge.find((item) => item.segment_id === id);
  const reviewItem = review.reviews.find((item) => item.segment_id === id);
  if (!judgeItem || !reviewItem) throw new Error(`Missing selected segment ${id}`);
  if (judgeItem.parsed?.decision !== 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE') throw new Error(`Segment ${id} not accepted by judge`);
  return {
    segment_id: id,
    fixture_or_new: judgeItem.fixture_or_new,
    raw_ids: judgeItem.raw_ids,
    dataset_message_ids: judgeItem.sources.map((source) => source.dataset_message_id),
    source_count: judgeItem.source_count,
    scorer_type: judgeItem.proposed_type,
    llm_decision: judgeItem.parsed.decision,
    llm_type: judgeItem.parsed.material_type,
    title: judgeItem.parsed.title,
    confidence: judgeItem.parsed.confidence,
    reason: judgeItem.parsed.reason,
    suggested_outline: judgeItem.parsed.suggested_outline || [],
    sources: judgeItem.sources,
    review_verdict: reviewItem.verdict,
    merge_sources_from: reviewItem.duplicate_or_merge?.startsWith('MERGE_WITH_') ? reviewItem.duplicate_or_merge.replace('MERGE_WITH_', '') : null
  };
});

fs.writeFileSync('reports/discussion-segment-controlled-selected-20260626.json', JSON.stringify(selected, null, 2), 'utf8');
console.log(JSON.stringify({ selected: selected.map((item) => item.segment_id), count: selected.length }));
