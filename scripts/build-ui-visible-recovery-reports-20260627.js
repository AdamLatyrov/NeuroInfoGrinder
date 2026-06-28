const fs = require('fs');
const path = require('path');

const reports = path.join(__dirname, '..', 'reports');
const readJson = (name) => JSON.parse(fs.readFileSync(path.join(reports, name), 'utf8').replace(/^\uFEFF/, ''));
const raw = readJson('ui-visible-message-source-recovery-raw-20260627.json');
const api = readJson('ui-visible-message-source-recovery-api-groups-38-20260627.json');

const byRaw = (arr, key = 'rawId') => {
  const map = new Map();
  for (const x of arr || []) {
    if (!map.has(x[key])) map.set(x[key], []);
    map.get(x[key]).push(x);
  }
  return map;
};

const datasetByRaw = byRaw(raw.datasetMessages);
const runMsgByRaw = byRaw(raw.runMessages);
const embByRaw = new Map((raw.embeddings || []).map((x) => [x.rawId, x.embeddingCount]));
const segByRaw = byRaw(raw.discussionSegments);
const matByRaw = byRaw(raw.materials);
const provByRaw = byRaw(raw.providerCalls);
const traceByRaw = byRaw(raw.traceStages);
const apiById = new Map((api.content || []).map((x) => [x.id, x]));
const chat = raw.targetChat?.[0] || {};

function msk(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(d).replace(' ', 'T');
}

const bestByUi = new Map();
for (const hit of raw.selectedHits || []) {
  if (!bestByUi.has(hit.uiId) || hit.rank < bestByUi.get(hit.uiId).rank) bestByUi.set(hit.uiId, hit);
}

function reasonFor(hit) {
  const run = runMsgByRaw.get(hit.rawId)?.[0];
  const segs = segByRaw.get(hit.rawId) || [];
  const mats = matByRaw.get(hit.rawId) || [];
  const providers = provByRaw.get(hit.rawId) || [];
  if (mats.length) return `materialized:${mats.map((m) => m.materialId).join('|')}`;
  if (segs.length) return segs.map((s) => s.rejectionReason || s.decision).filter(Boolean).join('|') || 'discussion_segment_no_material';
  if (run?.singleMessageRejectionReason) return run.singleMessageRejectionReason;
  if (providers.some((p) => p.status !== 'SUCCESS')) return providers.map((p) => `${p.stage}:${p.status}`).join('|');
  return run?.finalDecision || 'NO_PIPELINE_DECISION_FOUND';
}

function statusFor(hit) {
  const run = runMsgByRaw.get(hit.rawId)?.[0];
  const mats = matByRaw.get(hit.rawId) || [];
  if (mats.length) return 'MATERIAL_CREATED';
  if (run) return run.finalDecision || run.status || 'PIPELINE_RUN_MESSAGE_FOUND';
  return 'UI_MESSAGE_NOT_PIPELINE_RESOLVABLE';
}

const messageRows = [...bestByUi.values()].sort((a, b) => a.uiId.localeCompare(b.uiId)).map((hit) => {
  const ds = datasetByRaw.get(hit.rawId)?.[0] || {};
  const run = runMsgByRaw.get(hit.rawId)?.[0] || {};
  const mats = matByRaw.get(hit.rawId) || [];
  const apiMsg = apiById.get(hit.rawId);
  return {
    ui_id: hit.uiId,
    expected_label: hit.expectedLabel,
    raw_id: hit.rawId,
    dataset_message_id: ds.datasetMessageId || '',
    replay_run_message_id: run.replayRunMessageId || '',
    telegram_message_id: hit.telegramMessageId,
    message_date_utc: hit.messageDate,
    message_date_msk: hit.messageDateMsk || msk(hit.messageDate),
    author: hit.senderName || hit.senderUsername || '',
    source_field: hit.sourceField,
    text_preview: hit.textPreview || '',
    processable: chat.processingState === 'ENABLED_PROCESSABLE',
    processingState: chat.processingState || '',
    pipelineStatus: statusFor(hit),
    material_id: mats.map((m) => m.materialId).join('|'),
    rejection_or_skip_reason: reasonFor(hit),
    notes: apiMsg ? `/groups/38/messages returned id=${apiMsg.id}; id maps to raw_messages.id` : 'Not present in fetched API page; found by DB search',
    api_present: Boolean(apiMsg),
    links: (hit.links || []).map((l) => l.url).join('|'),
    embedding_count: embByRaw.get(hit.rawId) || 0,
  };
});

const pipelineRows = [];
for (const row of messageRows) {
  const rawId = row.raw_id;
  const run = runMsgByRaw.get(rawId)?.[0] || {};
  const providers = provByRaw.get(rawId) || [];
  const traces = traceByRaw.get(rawId) || [];
  const segs = segByRaw.get(rawId) || [];
  const mats = matByRaw.get(rawId) || [];
  pipelineRows.push({
    raw_id: rawId,
    dataset_message_id: row.dataset_message_id,
    replay_run_message_id: row.replay_run_message_id,
    run_id: run.runId || '',
    embedded: (row.embedding_count || 0) > 0,
    embedding_count: row.embedding_count,
    replay_status: run.status || '',
    rule_decision: run.ruleDecision || '',
    final_decision: run.finalDecision || '',
    single_message_score: run.singleMessageScore ?? '',
    single_message_rejection_reason: run.singleMessageRejectionReason || '',
    llm_used: run.llmUsed ?? '',
    llm_skip_reason: run.llmSkipReason || '',
    microcluster_id: run.microclusterId || '',
    macrocluster_id: run.macroclusterId || '',
    discussion_segment_ids: segs.map((s) => s.segmentId).join('|'),
    discussion_rejection_reasons: segs.map((s) => s.rejectionReason || s.decision).filter(Boolean).join('|'),
    provider_calls: providers.map((p) => `${p.stage}:${p.status}`).join('|'),
    material_ids: mats.map((m) => m.materialId).join('|'),
    trace_stage_count: traces.length,
    daily_cap_impact: segs.length ? 'check segment rejection' : 'NOT_APPLICABLE_NO_DISCUSSION_SEGMENT',
  });
}

function csv(v) { return v === null || v === undefined ? '' : `"${String(v).replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`; }
function writeCsv(name, rows, headers) {
  fs.writeFileSync(path.join(reports, name), [headers.join(','), ...rows.map((r) => headers.map((h) => csv(r[h])).join(','))].join('\n') + '\n', 'utf8');
}

writeCsv('ui-visible-message-source-recovery-messages-20260627.csv', messageRows, ['ui_id','expected_label','raw_id','dataset_message_id','replay_run_message_id','telegram_message_id','message_date_utc','message_date_msk','author','source_field','text_preview','processable','processingState','pipelineStatus','material_id','rejection_or_skip_reason','notes']);
writeCsv('ui-visible-message-source-recovery-pipeline-20260627.csv', pipelineRows, ['raw_id','dataset_message_id','replay_run_message_id','run_id','embedded','embedding_count','replay_status','rule_decision','final_decision','single_message_score','single_message_rejection_reason','llm_used','llm_skip_reason','microcluster_id','macrocluster_id','discussion_segment_ids','discussion_rejection_reasons','provider_calls','material_ids','trace_stage_count','daily_cap_impact']);

const mStatuses = [
  { id: 'M01', status: 'FOUND_UI_EQUIVALENT', rawIds: '11142', note: 'Maps to U01/U04 long GPT-5.6 Sol caption in API SUPPORT | ModelHub; outside previous exact window.' },
  { id: 'M02', status: 'NOT_FOUND', rawIds: '', note: 'PlusVibeAPI-specific content not recovered in target chat search.' },
  { id: 'M03', status: 'NOT_FOUND', rawIds: '', note: 'Specific proxy-risk/model-substitution/log-resale content not recovered in target chat search.' },
  { id: 'M04', status: 'NOT_FOUND', rawIds: '', note: 'Figma Config content not recovered in target chat search.' },
  { id: 'M05', status: 'FOUND_SIMILAR', rawIds: '', note: 'Codex mentions exist in broader-day search, but this UI reconciliation did not recover the requested outage/quota message.' },
  { id: 'M06', status: 'FOUND_UI_EQUIVALENT', rawIds: '11142', note: 'Government/access-policy wording may be part of the same long GPT-5.6 caption or related UI item; recovered hit did not match every named source snippet.' },
  { id: 'M07', status: 'FOUND_SIMILAR', rawIds: '11142', note: 'Fable/Mythos/GPT-5.6 context appears in raw 11142; exact Claude iOS loophole text not recovered here.' },
  { id: 'M08', status: 'NOT_FOUND', rawIds: '', note: 'Referral/bot abuse content not recovered in this target-chat UI source search.' },
  { id: 'M09', status: 'FOUND_SIMILAR', rawIds: '', note: 'OpenMontage-like candidate was previously found as broader-day raw 11484, outside U01-U04 target set.' },
  { id: 'M10', status: 'NOT_FOUND', rawIds: '', note: 'sensors_discord_bot not recovered in target chat search.' },
  { id: 'M11', status: 'FOUND_EXACT', rawIds: '11193', note: 'lolz.live link-only message recovered from /groups API and raw_messages.text.' },
  { id: 'M12', status: 'NOT_FOUND', rawIds: '', note: 'feedback-only useful-guide text not recovered in target chat search.' },
];

const out = {
  capturedAt: new Date().toISOString(),
  targetChat: raw.targetChat,
  apiInspection: {
    endpoint: '/api/v1/groups/38/messages?page=0&size=300&sort=messageDate,desc',
    totalElements: api.totalElements,
    fetchedElements: api.content?.length || 0,
    finding: 'MessageDto.id returned by /groups API is raw_messages.id; recovered raw ids 11142 and 11193 are present in API response.',
  },
  recoveredMessages: messageRows,
  pipeline: pipelineRows,
  expectedMessageRecovery: mStatuses,
  conclusions: {
    previousAuditMismatch: [
      'Wrong/narrow time window: old audit used 03:40-04:15 MSK (00:40-01:15Z). Recovered GPT-5.6 raw 11142 is 2026-06-26T23:28:47Z / 02:28:47 MSK. Recovered lolz raw 11193 is 2026-06-27T00:14:07Z / 03:14:07 MSK.',
      'Text source mismatch: GPT-5.6 message is stored in raw_messages.caption but UI normalizes caption into text via preferredText(). Old audit did search caption, but only inside the wrong window.',
      'Exact-match strategy was too strict for UI-visible material because UI pagination/chat selection showed messages outside the manually typed window.',
      'UI uses raw_messages directly, so UI visibility is not proof of dataset/replay failure; these two recovered messages are resolvable to dataset and replay rows.',
    ],
    recovered: 'U01/U04 and U02-equivalent map to raw 11142; U03 maps to raw 11193.',
    pipelineOutcome: 'Both raw ids were processed, embedded, rejected at single-message stage, had no discussion segment, no LLM/provider calls, and no materials.',
    genuineMissesOrExpectedRejects: 'raw 11142 is a likely pipeline/product-mode miss for news/reference summary because score 0.51 ended as LOW_SINGLE_MESSAGE_SCORE. raw 11193 is an expected reject for link-only/insufficient context, but current reason is TOO_SHORT rather than explicit LINK_ONLY/NEEDS_LINK_ENRICHMENT.',
    recommendations: [
      'Add news/reference mode for high-value release/resource announcements.',
      'Add link enrichment or explicit LINK_ONLY/NEEDS_LINK_ENRICHMENT rejection reason.',
      'Add UI-to-pipeline resolver/report utility based on /groups MessageDto.id = raw_messages.id.',
      'Improve audit search to start from chat id and UI API before time-window keyword search.',
      'Consider manual materialization for raw 11142 only if a news/reference summary is desired; do not create guide by default.',
    ],
  },
};
fs.writeFileSync(path.join(reports, 'ui-visible-message-source-recovery-20260627.json'), JSON.stringify(out, null, 2), 'utf8');
fs.writeFileSync(path.join(reports, 'expected-message-source-recovery-20260627.json'), JSON.stringify({ capturedAt: out.capturedAt, statuses: mStatuses, source: 'ui-visible-message-source-recovery-20260627.json' }, null, 2), 'utf8');

const row11142 = messageRows.find((r) => r.raw_id === 11142) || {};
const row11193 = messageRows.find((r) => r.raw_id === 11193) || {};

const md = `# UI-visible Message Source Recovery - 2026-06-27

Status: read-only reconciliation complete. No settings, caps, prompts, materials, reprocess, Telegram session/proxy, discovery guard, or publish state were changed.

## Target Chat

- Title: ${chat.title}
- Internal group id: ${chat.groupId}
- Telegram chat id: ${chat.telegramChatId}
- Enabled: ${chat.isEnabled}
- Active dialog: ${chat.activeDialog}
- Processing state: ${chat.processingState}
- Raw messages in 2026-06-26..2026-06-27 search range: ${chat.rawMessagesInRange}

## UI/API Reconciliation

- Inspected endpoint: \`/api/v1/groups/38/messages?page=0&size=300&sort=messageDate,desc\`
- API total elements: ${api.totalElements}
- Fetched elements: ${api.content?.length || 0}
- Backend query reads \`raw_messages\` directly.
- \`MessageDto.id\` from the UI API is \`raw_messages.id\`.
- Recovered raw ids \`11142\` and \`11193\` are present in the fetched API response.

## Why Previous Audit Did Not Match

- The previous exact window was too narrow/wrong for these UI-visible examples: \`03:40-04:15 MSK\` maps to \`00:40-01:15Z\`.
- GPT-5.6 raw \`11142\` is \`2026-06-26T23:28:47Z\` / \`02:28:47 MSK\`, outside that window.
- lolz.live raw \`11193\` is \`2026-06-27T00:14:07Z\` / \`03:14:07 MSK\`, also outside that window.
- GPT-5.6 was stored in \`raw_messages.caption\`; UI normalizes caption into \`text\` through \`preferredText()\`.
- Exact timestamp/content matching was too strict; UI-first reconciliation should start from chat id and \`/groups\` response ids.

## Recovered UI-visible Messages

| ui_id | expected_label | raw_id | dataset_message_id | replay_run_message_id | telegram_message_id | message_date_utc | message_date_msk | author | source_field | processable | pipelineStatus | material_id | rejection_or_skip_reason |
|---|---|---:|---:|---:|---:|---|---|---|---|---|---|---|---|
${messageRows.map((r) => `| ${r.ui_id} | ${r.expected_label} | ${r.raw_id} | ${r.dataset_message_id} | ${r.replay_run_message_id} | ${r.telegram_message_id} | ${r.message_date_utc} | ${r.message_date_msk} | ${r.author} | ${r.source_field} | ${r.processable} | ${r.pipelineStatus} | ${r.material_id} | ${r.rejection_or_skip_reason} |`).join('\n')}

## Pipeline Outcome

| raw_id | embedded | run_id | final_decision | single_message_score | single_message_rejection_reason | discussion_segment_ids | provider_calls | material_ids | daily_cap_impact |
|---:|---|---:|---|---:|---|---|---|---|---|
${pipelineRows.map((r) => `| ${r.raw_id} | ${r.embedded} | ${r.run_id} | ${r.final_decision} | ${r.single_message_score} | ${r.single_message_rejection_reason} | ${r.discussion_segment_ids} | ${r.provider_calls} | ${r.material_ids} | ${r.daily_cap_impact} |`).join('\n')}

## M01-M12 Updated Mapping

| manual_id | status | raw_ids | note |
|---|---|---|---|
${mStatuses.map((m) => `| ${m.id} | ${m.status} | ${m.rawIds} | ${m.note} |`).join('\n')}

## Conclusions

- U01/U04 and the U02-equivalent GPT-5.6 message were recovered as raw 11142, dataset ${row11142.dataset_message_id || ''}, replay message ${row11142.replay_run_message_id || ''}.
- U03 lolz.live was recovered as raw 11193, dataset ${row11193.dataset_message_id || ''}, replay message ${row11193.replay_run_message_id || ''}.
- Both were pipeline-resolvable and embedded.
- Neither became a material.
- raw \`11142\`: rejected as \`LOW_SINGLE_MESSAGE_SCORE\` with score \`0.51\`; this is a likely news/reference-mode gap, not a daily-cap issue.
- raw \`11193\`: rejected as \`TOO_SHORT\` with score \`0\`; outcome is expected for link-only content, but the reason should become explicit \`LINK_ONLY\` or \`NEEDS_LINK_ENRICHMENT\`.
- Daily cap did not apply because neither recovered message reached DISCUSSION_SEGMENT generation.

## Recommendations

- Add news/reference mode for high-value model-release and source-link announcements.
- Add link enrichment and explicit link-only rejection reasons.
- Add a UI-to-pipeline resolver that accepts \`/groups\` message id and returns raw/dataset/replay/material trace.
- Make future audits start from chat id + \`/groups\` API ids before timestamp keyword matching.
- Consider manual materialization for raw \`11142\` only as a SUMMARY/REFERENCE if approved; do not make it a practical GUIDE by default.
`;
fs.writeFileSync(path.join(reports, 'ui-visible-message-source-recovery-20260627.md'), md, 'utf8');

const expectedMd = `# Expected Message Source Recovery - 2026-06-27

This file appends the UI reconciliation result after the earlier exact-window audit.

| manual_id | status | raw_ids | note |
|---|---|---|---|
${mStatuses.map((m) => `| ${m.id} | ${m.status} | ${m.rawIds} | ${m.note} |`).join('\n')}

Key correction: previous \`0/12 exact match\` remains true only for the narrow \`03:40-04:15 MSK\` window. It is not true as a UI-visible source recovery result. M01 has a UI-equivalent raw \`11142\`; M11 has exact raw \`11193\`.
`;
fs.writeFileSync(path.join(reports, 'expected-message-source-recovery-20260627.md'), expectedMd, 'utf8');

console.log('ui visible recovery reports generated');
