const fs = require('fs');

const report = JSON.parse(fs.readFileSync('reports/discussion-segment-tightening-20260626.json', 'utf8'));
const fixtureByLocalId = Object.fromEntries(
  report.summary.fixture_coverage
    .filter((fixture) => fixture.detected === 'yes')
    .map((fixture) => [fixture.local_id, fixture.fixture])
);

const accepted = report.segments.map((segment) => ({
  segment_id: segment.local_id,
  fixture_or_new: fixtureByLocalId[segment.local_id] || 'NEW',
  proposed_type: segment.proposed_material_type,
  raw_ids: String(segment.raw_ids).split(/\s+/).filter(Boolean).map(Number),
  source_count: segment.source_count,
  combined_score: segment.combined_score,
  signals: segment.signals,
  chat: segment.chat,
  topic: segment.topic,
  account_id: segment.account_id,
  telegram_chat_id: segment.telegram_chat_id,
  forum_topic_id: segment.forum_topic_id,
  message_thread_id: segment.message_thread_id,
  sources: segment.rows.map((row, index) => ({
    order: index,
    raw_id: row.raw_id,
    dataset_message_id: row.dataset_message_id,
    message_date: row.message_date,
    text: row.preview || ''
  }))
}));

const selected = [];
for (const fixture of ['E01', 'E02', 'E04', 'E07']) {
  const segment = accepted.find((item) => item.fixture_or_new === fixture);
  if (segment) selected.push({ ...segment, reason_selected: `fixture ${fixture}` });
}

for (const segment of accepted
  .filter((item) => item.fixture_or_new === 'NEW')
  .sort((a, b) => b.combined_score - a.combined_score || b.signals.length - a.signals.length)) {
  if (selected.length >= 10) break;
  selected.push({ ...segment, reason_selected: 'top non-fixture by score/signals' });
}

const excluded = new Set(['E08', 'E09', 'E10']);
if (selected.length > 10) throw new Error('Selected more than 10 segments');
if (selected.some((segment) => excluded.has(segment.fixture_or_new))) throw new Error('Selected excluded fixture');
if (selected.some((segment) => !segment.raw_ids.length)) throw new Error('Selected segment without raw ids');

fs.writeFileSync('reports/discussion-segment-tightened-llm-selected-20260626.json', JSON.stringify(selected, null, 2), 'utf8');
console.log(JSON.stringify({ selected: selected.length, segment_ids: selected.map((item) => item.segment_id) }));
