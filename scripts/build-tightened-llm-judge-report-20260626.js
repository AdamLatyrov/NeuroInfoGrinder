const fs = require('fs');

const results = JSON.parse(fs.readFileSync('reports/discussion-segment-tightened-llm-results-raw-20260626.json', 'utf8'));
const esc = (v) => `"${String(v ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((o) => cols.map((c) => esc(o[c])).join(','))].join('\n'), 'utf8');
const positive = new Set(['DISCUSSION_SEGMENT_MATERIAL_CANDIDATE', 'DIRECT_MATERIAL_READY']);
const normalizedType = (value) => String(value || '').trim().toUpperCase();
const callRows = results.map((item) => ({
  provider_call_id: item.provider_call_id,
  segment_id: item.segment_id,
  stage: 'DISCUSSION_SEGMENT_JUDGE',
  provider: 'modelhub',
  model: 'gpt-5.5',
  status: item.status,
  duration_ms: item.latency_ms,
  tokens: Number(item.usage?.total_tokens || 0),
  error: item.error || '',
  parsed_decision: item.parsed?.decision || '',
  output_summary: item.parsed?.reason || item.parsed?.title || ''
}));

const rows = results.map((item) => {
  const parsed = item.parsed || {};
  const decision = parsed.decision || '';
  const accepted = parsed.accepted === true || positive.has(decision);
  const materialType = normalizedType(parsed.material_type || parsed.artifactType);
  const proposedType = normalizedType(item.proposed_type);
  const typeMatches = accepted ? materialType === proposedType : true;
  let notes = '';
  if (accepted && !typeMatches) notes = `type mismatch: scorer ${proposedType}, llm ${materialType}`;
  if (!accepted) notes = parsed.reason || parsed.rejection_reason || 'rejected';
  if (item.fixture_or_new === 'NEW' && accepted) notes = notes ? `${notes}; non-fixture accepted` : 'non-fixture accepted';
  return {
    segment_id: item.segment_id,
    fixture_or_new: item.fixture_or_new,
    proposed_type: item.proposed_type,
    raw_ids: item.raw_ids.join(' '),
    source_count: item.source_count,
    combined_score: item.combined_score,
    llm_accepted: accepted ? 'yes' : 'no',
    llm_decision: decision,
    llm_material_type: materialType,
    title: parsed.title || '',
    confidence: parsed.confidence ?? '',
    rejection_reason: parsed.rejection_reason || '',
    matches_scorer: typeMatches ? 'yes' : 'no',
    notes,
    reason: parsed.reason || '',
    suggested_outline: Array.isArray(parsed.suggested_outline) ? parsed.suggested_outline.join(' | ') : ''
  };
});

const accepted = rows.filter((row) => row.llm_accepted === 'yes');
const rejected = rows.filter((row) => row.llm_accepted !== 'yes');
const providerErrors = callRows.filter((row) => row.status !== 'SUCCESS');
const typeCounts = accepted.reduce((map, row) => {
  map[row.llm_material_type || 'UNKNOWN'] = (map[row.llm_material_type || 'UNKNOWN'] || 0) + 1;
  return map;
}, {});
const mismatches = rows.filter((row) => row.matches_scorer === 'no');
const lowValueAccepted = accepted.filter((row) => /low.value|недостат|context without|fragment|duplicate/i.test(row.reason + ' ' + row.notes));
const safetyIssues = rows.filter((row) => /unsupported|unsafe|medical|legal|hallucinat|галлюцин/i.test(row.reason + ' ' + row.notes + ' ' + row.rejection_reason));

const controlledMaterializationCandidates = accepted
  .filter((row) => row.matches_scorer === 'yes')
  .filter((row) => !/risk|unsafe|unsupported|context/i.test(row.reason + ' ' + row.rejection_reason))
  .map((row) => row.segment_id);

const summary = {
  selected_segments: results.length,
  attempted_model_calls: callRows.length,
  persisted_provider_call_rows: callRows.filter((row) => row.provider_call_id).length,
  provider_errors: providerErrors.length,
  llm_accepted: accepted.length,
  llm_rejected: rejected.length,
  material_type_counts: typeCounts,
  type_mismatches: mismatches.map((row) => ({ segment_id: row.segment_id, scorer: row.proposed_type, llm: row.llm_material_type })),
  hallucination_or_safety_issues: safetyIssues.length,
  low_value_segments_accepted: lowValueAccepted.map((row) => row.segment_id),
  controlled_materialization_candidates: controlledMaterializationCandidates,
  notes: [
    'Generation stayed disabled; this report is judge-only.',
    'Non-fixture accepts require manual review before any controlled materialization.'
  ]
};

csv('reports/discussion-segment-tightened-llm-judge-calls-20260626.csv', callRows, ['provider_call_id', 'segment_id', 'stage', 'provider', 'model', 'status', 'duration_ms', 'tokens', 'error', 'parsed_decision', 'output_summary']);
csv('reports/discussion-segment-tightened-llm-judge-vs-scorer-20260626.csv', rows, ['segment_id', 'fixture_or_new', 'proposed_type', 'raw_ids', 'source_count', 'combined_score', 'llm_accepted', 'llm_decision', 'llm_material_type', 'title', 'confidence', 'rejection_reason', 'matches_scorer', 'notes']);
fs.writeFileSync('reports/discussion-segment-tightened-llm-judge-20260626.json', JSON.stringify({ summary, selected_results: rows, provider_calls: callRows, raw_results: results }, null, 2), 'utf8');

let md = '# Tightened Discussion Segment LLM Judge Dry Run - 2026-06-26\n\n';
md += '## Summary\n\n';
md += `- selected_segments: ${summary.selected_segments}\n`;
md += `- attempted_model_calls: ${summary.attempted_model_calls}\n`;
md += `- persisted_provider_call_rows: ${summary.persisted_provider_call_rows}\n`;
md += `- provider_errors: ${summary.provider_errors}\n`;
md += `- llm_accepted: ${summary.llm_accepted}\n`;
md += `- llm_rejected: ${summary.llm_rejected}\n`;
md += `- material_type_counts: ${JSON.stringify(summary.material_type_counts)}\n`;
md += `- type_mismatches: ${JSON.stringify(summary.type_mismatches)}\n`;
md += `- hallucination_or_safety_issues: ${summary.hallucination_or_safety_issues}\n`;
md += `- low_value_segments_accepted: ${summary.low_value_segments_accepted.join(', ') || 'none'}\n`;
md += `- controlled_materialization_candidates: ${summary.controlled_materialization_candidates.join(', ') || 'none'}\n`;
md += '\n## Main Table\n\n';
md += '| segment_id | fixture_or_new | proposed_type | raw_ids | source_count | combined_score | llm_accepted | llm_decision | llm_material_type | title | confidence | rejection_reason | matches_scorer | notes |\n';
md += '| --- | --- | --- | --- | ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- |\n';
for (const row of rows) md += `| ${row.segment_id} | ${row.fixture_or_new} | ${row.proposed_type} | ${row.raw_ids} | ${row.source_count} | ${row.combined_score} | ${row.llm_accepted} | ${row.llm_decision} | ${row.llm_material_type} | ${String(row.title).replace(/\|/g, '/')} | ${row.confidence} | ${row.rejection_reason} | ${row.matches_scorer} | ${String(row.notes).replace(/\|/g, '/')} |\n`;
md += '\n## Provider Calls\n\n';
md += '| provider_call_id | segment_id | stage | provider | model | status | duration_ms | tokens | error | parsed_decision | output_summary |\n';
md += '| ---: | --- | --- | --- | --- | --- | ---: | ---: | --- | --- | --- |\n';
for (const row of callRows) md += `| ${row.provider_call_id} | ${row.segment_id} | ${row.stage} | ${row.provider} | ${row.model} | ${row.status} | ${row.duration_ms} | ${row.tokens} | ${row.error} | ${row.parsed_decision} | ${String(row.output_summary).replace(/\|/g, '/')} |\n`;
md += '\n## Evaluation\n\n';
md += `1. Checked ${summary.selected_segments} selected accepted tightened segments.\n`;
md += `2. LLM accepted ${summary.llm_accepted}.\n`;
md += `3. LLM rejected ${summary.llm_rejected}.\n`;
md += `4. Material types: ${JSON.stringify(summary.material_type_counts)}.\n`;
md += `5. Type mismatches: ${summary.type_mismatches.length ? JSON.stringify(summary.type_mismatches) : 'none'}.\n`;
md += `6. Hallucination/safety issues found in parsed reasons: ${summary.hallucination_or_safety_issues}.\n`;
md += `7. Low-value accepted candidates requiring manual review: ${summary.low_value_segments_accepted.join(', ') || 'none'}.\n`;
md += `8. Candidate IDs for later controlled materialization review: ${summary.controlled_materialization_candidates.join(', ') || 'none'}.\n`;
md += '9. Before materialization: manually review non-fixture accepts, keep E08 needs-context excluded, and do not enable generation until an explicit selected materialization approval.\n';
md += '\n## Safety\n\n- This run called only DISCUSSION_SEGMENT_JUDGE.\n- No KNOWLEDGE_GENERATION call is expected from this script.\n- No knowledge_items insert is performed by this script.\n';
fs.writeFileSync('reports/discussion-segment-tightened-llm-judge-20260626.md', md, 'utf8');

console.log(JSON.stringify(summary));
