const fs = require('fs');

const raw = JSON.parse(fs.readFileSync('reports/discussion-segment-tightened-llm-results-raw-20260626.json', 'utf8'));
const accepted = raw.filter((item) => item.parsed?.decision === 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE');
const byId = Object.fromEntries(accepted.map((item) => [item.segment_id, item]));

const reviews = [
  {
    segment_id: 'S0024',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'MERGE_WITH_S0021',
    safety_ok: true,
    verdict: 'APPROVE_AFTER_MERGE',
    recommendation: 'Use S0024 as the fixture anchor but merge S0021 sources, especially raw 6052 monitoring-page request and r-api context. Do not materialize S0021 separately.',
    explanation: 'Title/reason are grounded in fallback, slow API provider, external outage caveat, and model availability status. The source supports a guide/checklist, but the best material needs the earlier monitoring-page source from S0021.'
  },
  {
    segment_id: 'S0269',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'MERGE_WITH_S0266',
    safety_ok: true,
    verdict: 'APPROVE_AFTER_MERGE',
    recommendation: 'Use S0269 as the fixture anchor but merge S0266 setup sources, especially raw 6765 cache-theory explanation and 6769 clarification prompt. Do not materialize S0266 separately.',
    explanation: 'Title/reason are grounded in cache write/read explanation, uneven limit burn, avoiding long pauses, and /new or /clear caution. Some exact timing/tariff claims must be framed as chat observations, not official Anthropic facts.'
  },
  {
    segment_id: 'S0132',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'NONE',
    safety_ok: true,
    verdict: 'APPROVE_FOR_CONTROLLED_MATERIALIZATION',
    recommendation: 'Materialize as SUMMARY only. Keep it as a structured feedback/pain-point summary, not a how-to guide.',
    explanation: 'Title, reason, and outline match the source: a request for OS/feedback and a numbered list of user pain points. The material is useful as product-feedback synthesis; language should be normalized/sanitized for publication.'
  },
  {
    segment_id: 'S0217',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'NONE',
    safety_ok: true,
    verdict: 'APPROVE_FOR_CONTROLLED_MATERIALIZATION',
    recommendation: 'Materialize only as cautious source-grounded SUMMARY. No medical advice, no safety/health benefit claims, no generalized conclusions about psychedelics.',
    explanation: 'LLM output correctly frames this as a request for experiences plus one participant’s anecdotal caution. Source supports a careful summary. Safety is acceptable only if generated text preserves subjective framing and avoids medical claims.'
  },
  {
    segment_id: 'S0021',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'DUPLICATE_OF_S0024_WITH_USEFUL_CONTEXT',
    safety_ok: true,
    verdict: 'REJECT_DUPLICATE',
    recommendation: 'Do not materialize separately. Merge raw 6047, 6048, and especially 6052 into S0024 material.',
    explanation: 'The LLM output is grounded and useful, but it overlaps S0024 on raw 6062, 6064, and 6069. Its unique value is earlier context about r-api and monitoring page/model status, so it should enrich S0024 rather than become a separate material.'
  },
  {
    segment_id: 'S0266',
    source_grounded: true,
    useful: true,
    type_correct: true,
    duplicate_or_merge: 'DUPLICATE_OF_S0269_WITH_USEFUL_CONTEXT',
    safety_ok: true,
    verdict: 'REJECT_DUPLICATE',
    recommendation: 'Do not materialize separately. Merge raw 6765 and 6769 into S0269 material; ignore raw 6691 as low-signal/noise.',
    explanation: 'The LLM output is grounded in cache theory and Q&A setup, but it overlaps S0269 on raw 6776, 6778, and 6799. It contains one low-signal noisy source and should be merged into the fuller E02 fixture material.'
  }
];

for (const review of reviews) {
  const item = byId[review.segment_id];
  if (!item) throw new Error(`Missing accepted segment ${review.segment_id}`);
  review.fixture_or_new = item.fixture_or_new;
  review.llm_type = item.parsed.material_type;
  review.title = item.parsed.title;
  review.confidence = item.parsed.confidence;
  review.raw_ids = item.raw_ids.join(' ');
  review.source_count = item.source_count;
  review.llm_reason = item.parsed.reason;
  review.suggested_outline = item.parsed.suggested_outline || [];
  review.sources = item.sources.map((source) => ({
    raw_id: source.raw_id,
    order: source.order,
    text: source.text
  }));
}

const approvedNext = ['S0024', 'S0269', 'S0132', 'S0217'];
const summary = {
  reviewed_segments: reviews.length,
  source_messages_inspected: reviews.reduce((sum, item) => sum + item.sources.length, 0),
  approved_for_controlled_materialization_now: ['S0132', 'S0217'],
  approved_after_merge: ['S0024', 'S0269'],
  reject_duplicate: ['S0021', 'S0266'],
  recommended_next_controlled_candidates_max_4: approvedNext,
  retry_http_502_segments: false,
  generation_enabled: false,
  materials_created: false,
  notes: [
    'S0021 should merge into S0024, not materialize separately.',
    'S0266 should merge into S0269, not materialize separately.',
    'S0217 is allowed only as cautious anecdotal/source-grounded summary with no medical claims.'
  ]
};

const esc = (v) => `"${String(v ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((o) => cols.map((c) => esc(o[c])).join(','))].join('\n'), 'utf8');

const tableRows = reviews.map((item) => ({
  segment_id: item.segment_id,
  fixture_or_new: item.fixture_or_new,
  llm_type: item.llm_type,
  title: item.title,
  confidence: item.confidence,
  source_grounded: item.source_grounded ? 'yes' : 'no',
  useful: item.useful ? 'yes' : 'no',
  type_correct: item.type_correct ? 'yes' : 'no',
  duplicate_or_merge: item.duplicate_or_merge,
  safety_ok: item.safety_ok ? 'yes' : 'no',
  verdict: item.verdict,
  recommendation: item.recommendation
}));

csv('reports/discussion-segment-accepted-review-table-20260626.csv', tableRows, ['segment_id', 'fixture_or_new', 'llm_type', 'title', 'confidence', 'source_grounded', 'useful', 'type_correct', 'duplicate_or_merge', 'safety_ok', 'verdict', 'recommendation']);
fs.writeFileSync('reports/discussion-segment-accepted-review-20260626.json', JSON.stringify({ summary, reviews }, null, 2), 'utf8');

let md = '# Discussion Segment Accepted Judge Review - 2026-06-26\n\n';
md += '## Summary\n\n';
md += `- reviewed_segments: ${summary.reviewed_segments}\n`;
md += `- source_messages_inspected: ${summary.source_messages_inspected}\n`;
md += `- approved_for_controlled_materialization_now: ${summary.approved_for_controlled_materialization_now.join(', ')}\n`;
md += `- approved_after_merge: ${summary.approved_after_merge.join(', ')}\n`;
md += `- reject_duplicate: ${summary.reject_duplicate.join(', ')}\n`;
md += `- recommended_next_controlled_candidates_max_4: ${summary.recommended_next_controlled_candidates_max_4.join(', ')}\n`;
md += '- no materialization was started; generation remains disabled.\n';
md += '\n## Main Table\n\n';
md += '| segment_id | fixture_or_new | llm_type | title | confidence | source_grounded | useful | type_correct | duplicate_or_merge | safety_ok | verdict | recommendation |\n';
md += '| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |\n';
for (const row of tableRows) md += `| ${row.segment_id} | ${row.fixture_or_new} | ${row.llm_type} | ${row.title.replace(/\|/g, '/')} | ${row.confidence} | ${row.source_grounded} | ${row.useful} | ${row.type_correct} | ${row.duplicate_or_merge} | ${row.safety_ok} | ${row.verdict} | ${row.recommendation.replace(/\|/g, '/')} |\n`;
md += '\n## Segment Notes\n\n';
for (const item of reviews) {
  md += `### ${item.segment_id} / ${item.fixture_or_new}\n\n`;
  md += `- verdict: ${item.verdict}\n`;
  md += `- explanation: ${item.explanation}\n`;
  md += `- source grounding: title/reason/outline checked against ${item.sources.length} source messages.\n`;
  md += `- source raw ids: ${item.raw_ids}\n`;
  md += `- recommendation: ${item.recommendation}\n\n`;
}
md += '## Final Recommendation\n\n';
md += 'Next controlled materialization candidates, max 4: S0024 after merge with S0021, S0269 after merge with S0266, S0132, and S0217.\n\n';
md += 'Do not materialize S0021 or S0266 separately because they are overlapping duplicates with useful context to merge. Do not retry HTTP-502 segments automatically; use a separate judge-only retry if needed.\n';
fs.writeFileSync('reports/discussion-segment-accepted-review-20260626.md', md, 'utf8');

console.log(JSON.stringify(summary));
