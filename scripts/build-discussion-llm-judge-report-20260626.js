const fs = require('fs');

const calls = fs.readFileSync('reports/discussion-segment-llm-judge-calls-prod-20260626.jsonl', 'utf8')
  .trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const fixtureRows = fs.readFileSync('reports/discussion-segment-dry-run-vs-manual-20260626.csv', 'utf8')
  .trim().split(/\r?\n/).slice(1).map((line) => {
    const cells = line.match(/("(?:""|[^"])*"|[^,]*)/g).filter((_, i) => i % 2 === 0).map((v) => v.replace(/^"|"$/g, '').replace(/""/g, '"'));
    return { fixture: cells[0], expected_raw_ids: cells[1], segment_id: Number(cells[4]), source_count: Number(cells[5]), matched_raw_ids: cells[6], proposed_material_type: cells[11] };
  }).filter((r) => [86, 87, 50, 88, 89].includes(r.segment_id));
const fixturesBySegment = new Map(fixtureRows.map((r) => [r.segment_id, r]));
const manual = {
  86: { expected: 'accept', type: 'GUIDE', title: 'E01 API fallback/status' },
  87: { expected: 'accept', type: 'GUIDE_OR_REFERENCE', title: 'E02 Claude/Anthropic cache/cost' },
  50: { expected: 'accept', type: 'SUMMARY', title: 'E04 CLI pain burst' },
  88: { expected: 'careful_accept', type: 'SUMMARY_OR_ANSWER', title: 'E07 retreat caution' },
  89: { expected: 'accept', type: 'ANSWER', title: 'E08 remote work risk' }
};
const esc = (v) => `"${String(v ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((o) => cols.map((c) => esc(o[c])).join(','))].join('\n'), 'utf8');
function parsed(call) { return call.response_json || {}; }
function matchManual(segmentId, response) {
  const expected = manual[segmentId];
  if (!expected) return 'unknown';
  if (segmentId === 89) return response.decision === 'REJECTED_NEEDS_MORE_CONTEXT' ? 'mismatch_too_conservative' : 'match';
  if (segmentId === 88) return response.accepted && ['ANSWER', 'SUMMARY'].includes(response.material_type) ? 'match_careful_accept' : 'mismatch';
  if (segmentId === 50) return response.accepted ? (response.material_type === 'SUMMARY' ? 'match' : 'partial_wrong_type') : 'mismatch';
  return response.accepted ? 'match' : 'mismatch';
}
const table = calls.map((call) => {
  const segmentId = Number(call.segment_id);
  const response = parsed(call);
  const fixture = fixturesBySegment.get(segmentId);
  return {
    segment_id: segmentId,
    fixture: fixture?.fixture || '',
    raw_ids: fixture?.matched_raw_ids || '',
    source_count: fixture?.source_count || '',
    proposed_type: fixture?.proposed_material_type || '',
    llm_accepted: response.accepted,
    llm_decision: response.decision,
    material_type: response.material_type,
    title: response.title,
    confidence: response.confidence,
    rejection_reason: response.rejection_reason,
    matches_manual: matchManual(segmentId, response),
    notes: response.reason
  };
});
const callRows = calls.map((call) => {
  const response = parsed(call);
  return {
    provider_call_id: call.provider_call_id,
    segment_id: call.segment_id,
    stage: call.stage,
    provider: call.provider_id,
    model: call.model_name,
    status: call.status,
    duration_ms: call.latency_ms,
    tokens: Number(call.input_tokens || 0) + Number(call.output_tokens || 0),
    error: [call.error_code, call.error_message].filter(Boolean).join(': '),
    parsed_decision: response.decision,
    output_summary: JSON.stringify(response).slice(0, 1000)
  };
});
csv('reports/discussion-segment-llm-judge-calls-20260626.csv', callRows, ['provider_call_id', 'segment_id', 'stage', 'provider', 'model', 'status', 'duration_ms', 'tokens', 'error', 'parsed_decision', 'output_summary']);
csv('reports/discussion-segment-llm-judge-vs-manual-20260626.csv', table, ['segment_id', 'fixture', 'raw_ids', 'source_count', 'proposed_type', 'llm_accepted', 'llm_decision', 'material_type', 'title', 'confidence', 'rejection_reason', 'matches_manual', 'notes']);

const data = {
  scope: { segment_ids: [86, 87, 50, 88, 89], provider_calls: calls.length, knowledge_generation_calls: 0, discussion_materials_after: 0, generation_enabled: false },
  safety: { knowledge_items_before: 17, knowledge_items_after_observed: 20, discussion_materials_before: 0, discussion_materials_after: 0, note: '3 unrelated live-auto knowledge_items were created during the interval; none were source_cluster_type DISCUSSION_SEGMENT and none came from this judge dry-run.' },
  results: table,
  provider_calls: callRows
};
fs.writeFileSync('reports/discussion-segment-llm-judge-dry-run-20260626.json', JSON.stringify(data, null, 2), 'utf8');

function mdTable(rows, cols) {
  return ['| ' + cols.map((c) => c.h).join(' | ') + ' |', '| ' + cols.map(() => '---').join(' | ') + ' |', ...rows.map((r) => '| ' + cols.map((c) => String(c.v(r) ?? '').replace(/\|/g, '/').replace(/\r?\n/g, '<br>')).join(' | ') + ' |')].join('\n');
}
let md = '# Discussion Segment LLM Judge Dry Run - 2026-06-26\n\n';
md += '## Scope\n\n- Segment IDs judged: `86, 87, 50, 88, 89`\n- Provider calls: `5`\n- `KNOWLEDGE_GENERATION` calls: `0`\n- `DISCUSSION_SEGMENT` materials: `0`\n- `discussionSegmentGenerationEnabled`: code fallback `0`, no DB override row\n\n';
md += '## Results\n\n' + mdTable(table, [
  { h: 'segment_id', v: (r) => r.segment_id },
  { h: 'fixture', v: (r) => r.fixture },
  { h: 'raw_ids', v: (r) => r.raw_ids },
  { h: 'source_count', v: (r) => r.source_count },
  { h: 'proposed_type', v: (r) => r.proposed_type },
  { h: 'llm_accepted', v: (r) => r.llm_accepted },
  { h: 'llm_decision', v: (r) => r.llm_decision },
  { h: 'material_type', v: (r) => r.material_type },
  { h: 'title', v: (r) => r.title },
  { h: 'confidence', v: (r) => r.confidence },
  { h: 'rejection_reason', v: (r) => r.rejection_reason },
  { h: 'matches_manual', v: (r) => r.matches_manual },
  { h: 'notes', v: (r) => r.notes }
]) + '\n\n';
md += '## Provider Calls\n\n' + mdTable(callRows, [
  { h: 'provider_call_id', v: (r) => r.provider_call_id },
  { h: 'segment_id', v: (r) => r.segment_id },
  { h: 'stage', v: (r) => r.stage },
  { h: 'provider', v: (r) => r.provider },
  { h: 'model', v: (r) => r.model },
  { h: 'status', v: (r) => r.status },
  { h: 'duration_ms', v: (r) => r.duration_ms },
  { h: 'tokens', v: (r) => r.tokens },
  { h: 'error', v: (r) => r.error },
  { h: 'parsed_decision', v: (r) => r.parsed_decision },
  { h: 'output_summary', v: (r) => r.output_summary }
]) + '\n\n';
md += '## Manual Comparison\n\n- E01 accepted as GUIDE: match.\n- E02 accepted as GUIDE with source-aware caveats: match.\n- E04 accepted, but LLM chose GUIDE instead of expected SUMMARY: partial mismatch / type calibration needed.\n- E07 accepted as ANSWER with careful no-medical-claims framing: match.\n- E08 rejected as NEEDS_MORE_CONTEXT: mismatch against manual expectation, but safety-conservative and arguably acceptable if source context is incomplete.\n\n';
md += '## Safety\n\n- `knowledge_items` before: 17.\n- `knowledge_items` after observed: 20.\n- Important: the +3 rows were unrelated live-auto `SINGLE_MESSAGE`/`MACRO` materials from runs `2549`, `2615`, `2618`, not DISCUSSION_SEGMENT and not this judge dry-run.\n- `DISCUSSION_SEGMENT` materials before: 0.\n- `DISCUSSION_SEGMENT` materials after: 0.\n- Generation provider calls for discussion: 0.\n\n';
md += '## Recommendation\n\n1. Tighten segment scorer/overlap dedupe before larger LLM runs: current dry-run accepted 89 segments and sliding windows over-produce.\n2. Add max accepted segments per chat/topic/window and merge overlapping windows before LLM.\n3. Add type-specific rules: product-feedback bursts should prefer SUMMARY over GUIDE; risk Q&A pairs need the original question or should be marked NEEDS_MORE_CONTEXT.\n4. Next safe step: run LLM judge on top 20 only after dedupe/merge, not all 89.\n5. Do not approve material generation yet; first fix over-generation and type calibration.\n';
fs.writeFileSync('reports/discussion-segment-llm-judge-dry-run-20260626.md', md, 'utf8');
