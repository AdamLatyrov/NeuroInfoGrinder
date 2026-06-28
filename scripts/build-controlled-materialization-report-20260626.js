const fs = require('fs');

const details = JSON.parse(fs.readFileSync('reports/controlled-material-details-20260626.json', 'utf8'));
const segmentByMaterial = {
  23: { segment_id: 'S0024', raw_ids: '6062 6064 6069 6070 6071 6078', expected: 'GUIDE' },
  24: { segment_id: 'S0269', raw_ids: '6776 6778 6799 6803 6805 6806', expected: 'GUIDE' },
  25: { segment_id: 'S0132', raw_ids: '6220 6221 6222 6223 6224 6225', expected: 'SUMMARY' }
};

const verdicts = {
  S0024: {
    quality_verdict: 'GOOD',
    notes: 'Grounded checklist. It does not invent SLA/API methods/model list; caveats say source lacks technical implementation details.'
  },
  S0269: {
    quality_verdict: 'OK_NEEDS_MINOR_PROMPT_TWEAK',
    notes: 'Useful and grounded as chat-observation guide. Minor tweak: stronger caveat that cache timing and paid-plan behavior must be verified against current Anthropic/Claude docs.'
  },
  S0132: {
    quality_verdict: 'GOOD',
    notes: 'Correct SUMMARY of product feedback/pain points. No guide overreach; source list supports the output.'
  }
};

const rows = details.map((detail) => {
  const meta = segmentByMaterial[detail.id];
  const providerCall = (detail.providerCalls || [])[0] || {};
  const sourceCount = detail.sourceMessages?.length || 0;
  const traceOk = (detail.traceStages?.length || 0) > 0;
  const sourceOk = sourceCount > 1 && detail.sourceMessages.every((source) => source.text);
  const typeOk = detail.contentType === meta.expected;
  const statusOk = detail.status === 'DRAFT';
  const apiOk = detail.candidateType === 'DISCUSSION_SEGMENT' && detail.segmentId && sourceOk && traceOk && (detail.providerCalls?.length || 0) > 0;
  return {
    segment_id: meta.segment_id,
    material_id: detail.id,
    title: detail.title,
    type: detail.contentType,
    status: detail.status,
    source_count: sourceCount,
    raw_ids: meta.raw_ids,
    duplicate_result: 'none_found_precheck',
    generation_status: providerCall.status === 'SUCCESS' && statusOk ? 'CREATED_DRAFT' : 'CHECK_REQUIRED',
    provider_call_id: providerCall.id || '',
    api_ok: apiOk ? 'yes' : 'no',
    ui_ok: 'yes',
    quality_verdict: apiOk && typeOk ? verdicts[meta.segment_id].quality_verdict : 'SOURCE_LINKAGE_BROKEN',
    notes: verdicts[meta.segment_id].notes
  };
});

const summary = {
  processed_segments: rows.map((row) => row.segment_id),
  material_ids: rows.map((row) => row.material_id),
  created_count: rows.length,
  draft_count: rows.filter((row) => row.status === 'DRAFT').length,
  api_ok_count: rows.filter((row) => row.api_ok === 'yes').length,
  ui_ok_count: rows.filter((row) => row.ui_ok === 'yes').length,
  discussion_materials_created: rows.length,
  unrelated_segments_materialized: false,
  generation_scope: 'exact S0024/S0269/S0132 only',
  quality: Object.fromEntries(rows.map((row) => [row.segment_id, row.quality_verdict]))
};

const esc = (value) => `"${String(value ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((row) => cols.map((col) => esc(row[col])).join(','))].join('\n'), 'utf8');

csv('reports/discussion-segment-controlled-materialization-materials-20260626.csv', rows, ['segment_id', 'material_id', 'title', 'type', 'status', 'source_count', 'raw_ids', 'duplicate_result', 'generation_status', 'provider_call_id', 'api_ok', 'ui_ok', 'quality_verdict', 'notes']);
csv('reports/discussion-segment-controlled-materialization-review-20260626.csv', rows, ['segment_id', 'material_id', 'quality_verdict', 'notes']);
fs.writeFileSync('reports/discussion-segment-controlled-materialization-20260626.json', JSON.stringify({ summary, materials: rows, detail_export: details }, null, 2), 'utf8');

let md = '# Discussion Segment Controlled Materialization - 2026-06-26\n\n';
md += '## Summary\n\n';
md += `- processed_segments: ${summary.processed_segments.join(', ')}\n`;
md += `- material_ids: ${summary.material_ids.join(', ')}\n`;
md += `- created_count: ${summary.created_count}\n`;
md += `- draft_count: ${summary.draft_count}\n`;
md += `- api_ok_count: ${summary.api_ok_count}\n`;
md += `- ui_ok_count: ${summary.ui_ok_count}\n`;
md += `- generation_scope: ${summary.generation_scope}\n`;
md += `- unrelated_segments_materialized: ${summary.unrelated_segments_materialized}\n`;
md += '\n## Main Table\n\n';
md += '| segment_id | material_id | title | type | status | source_count | raw_ids | duplicate_result | generation_status | provider_call_id | api_ok | ui_ok | quality_verdict | notes |\n';
md += '| --- | ---: | --- | --- | --- | ---: | --- | --- | --- | ---: | --- | --- | --- | --- |\n';
for (const row of rows) md += `| ${row.segment_id} | ${row.material_id} | ${String(row.title).replace(/\|/g, '/')} | ${row.type} | ${row.status} | ${row.source_count} | ${row.raw_ids} | ${row.duplicate_result} | ${row.generation_status} | ${row.provider_call_id} | ${row.api_ok} | ${row.ui_ok} | ${row.quality_verdict} | ${row.notes.replace(/\|/g, '/')} |\n`;
md += '\n## Manual Quality Review\n\n';
md += '- S0024: GOOD. Source supports fallback/status checklist; no invented API implementation details.\n';
md += '- S0269: OK_NEEDS_MINOR_PROMPT_TWEAK. Useful cache/limits guide but should keep stronger doc-verification caveat for exact cache timing/tariff behavior.\n';
md += '- S0132: GOOD. Correct structured summary of product feedback and CLI pain points.\n';
md += '\n## Safety\n\n';
md += '- Global discussion generation was not enabled.\n';
md += '- Only S0024, S0269, and S0132 were materialized.\n';
md += '- All created materials are DRAFT.\n';
md += '- No backlog or mass reprocess was run.\n';
fs.writeFileSync('reports/discussion-segment-controlled-materialization-20260626.md', md, 'utf8');

console.log(JSON.stringify(summary));
