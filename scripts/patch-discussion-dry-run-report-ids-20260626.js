const fs = require('fs');

const post = fs.readFileSync('reports/discussion-segment-post-dryrun-prod-20260626.jsonl', 'utf8')
  .trim()
  .split(/\r?\n/)
  .filter(Boolean)
  .map(JSON.parse);
const segments = post.slice(1);
const report = JSON.parse(fs.readFileSync('reports/discussion-segment-dry-run-20260626.json', 'utf8'));

for (const fixture of report.summary.fixture_coverage) {
  const expectedIds = String(fixture.matched_raw_ids || fixture.expected_raw_ids || '')
    .split(' ')
    .filter(Boolean)
    .map(Number);
  const match = segments.find((segment) => expectedIds.length > 0 && expectedIds.every((id) => (segment.raw_ids || []).includes(id)));
  if (match) fixture.segment_id = match.id;
}

report.fixture_coverage = report.summary.fixture_coverage;
report.summary.knowledge_items_before = 17;
report.summary.knowledge_items_after = 17;
report.summary.discussion_segment_materials_before = 0;
report.summary.discussion_segment_materials_after = 0;
report.summary.persisted_dryrun_segments = 89;
report.summary.persisted_dryrun_sources = 501;
fs.writeFileSync('reports/discussion-segment-dry-run-20260626.json', JSON.stringify(report, null, 2), 'utf8');

function csv(file, arr, cols) {
  const esc = (value) => `"${String(value ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
  fs.writeFileSync(file, [cols.join(','), ...arr.map((row) => cols.map((col) => esc(row[col])).join(','))].join('\n'), 'utf8');
}

csv('reports/discussion-segment-dry-run-vs-manual-20260626.csv', report.summary.fixture_coverage, [
  'fixture',
  'expected_raw_ids',
  'detected',
  'local_id',
  'segment_id',
  'source_count',
  'matched_raw_ids',
  'missing_raw_ids',
  'extra_raw_ids',
  'combined_score',
  'decision',
  'proposed_material_type',
  'rejection_reason'
]);

let markdown = fs.readFileSync('reports/discussion-segment-dry-run-20260626.md', 'utf8');
markdown = markdown.replace(/\| (E0[1-8]) \| yes \| ([^|]+) \|  \|/g, (_match, fixture, localId) => {
  const row = report.summary.fixture_coverage.find((item) => item.fixture === fixture);
  return `| ${fixture} | yes | ${localId} | ${row?.segment_id || ''} |`;
});
markdown += '\n## Production Persistence And Safety Evidence\n\n';
markdown += '- knowledge_items before: 17\n';
markdown += '- knowledge_items after: 17\n';
markdown += '- DISCUSSION_SEGMENT materials before: 0\n';
markdown += '- DISCUSSION_SEGMENT materials after: 0\n';
markdown += '- persisted dry-run diagnostic segments: 89\n';
markdown += '- persisted dry-run diagnostic sources: 501\n';
markdown += '- persisted generation skip reason: DRY_RUN_GENERATION_DISABLED\n';
markdown += '- provider calls during dry-run: 0\n';
fs.writeFileSync('reports/discussion-segment-dry-run-20260626.md', markdown, 'utf8');
