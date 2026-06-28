const fs = require('fs');
const path = require('path');

const reports = path.join(__dirname, '..', 'reports');
const readJson = (name) => JSON.parse(fs.readFileSync(path.join(reports, name), 'utf8').replace(/^\uFEFF/, ''));

const audit = readJson('overnight-expected-materials-audit-raw-20260627.json');
const broad = readJson('overnight-broader-keyword-search-raw-20260627.json');
const controlled = readJson('discussion-controlled-production-24h-raw-20260627.json');

const expected = [
  ['M01', 'GPT-5.6 / Sol Terra Luna announcement', 'MAYBE_SUMMARY_NOT_GUIDE'],
  ['M02', 'PlusVibeAPI ad / proxy API platform', 'REJECT_PROMO_ALONE_OR_MERGE_CONTEXT_FOR_PROXY_API_RISK_GUIDE'],
  ['M03', 'Proxy API risks: model substitution and log resale', 'SHOULD_BECOME_MATERIAL'],
  ['M04', 'Figma Config 2026 canvas/code/agents', 'MAYBE_SUMMARY_NOT_GUIDE'],
  ['M05', 'Codex outage / quotas chronic problem', 'SHOULD_BECOME_MATERIAL_IF_CONTEXT_ENOUGH'],
  ['M06', 'GPT-5.6 government approval / restricted access', 'MAYBE_SUMMARY_NOT_GUIDE'],
  ['M07', 'Claude Fable 5 through iOS + Claude Code loophole', 'MAYBE_SAFETY_SUMMARY_ONLY_OR_REJECT_ACCESS_CIRCUMVENTION_HOWTO'],
  ['M08', 'Referral abuse with bots', 'REJECT_ABUSE'],
  ['M09', 'OpenMontage GitHub video-agent system', 'SHOULD_BECOME_MATERIAL'],
  ['M10', 'sensors_discord_bot', 'REJECT_ENTITY_ONLY'],
  ['M11', 'lolz.live link useful', 'REJECT_LINK_ONLY_OR_NEEDS_CONTEXT'],
  ['M12', 'useful guide feedback', 'REJECT_FEEDBACK_CONTEXT_ONLY'],
];

const rawById = new Map(audit.rawWindow.map((x) => [x.rawId, x]));
const datasetByRaw = new Map(audit.datasetMessages.map((x) => [x.rawId, x]));
const chatByRaw = new Map(audit.chatStates.map((x) => [x.rawId, x]));
const intakeByRaw = new Map(audit.intake.map((x) => [x.rawId, x]));
const queueByRaw = new Map(audit.queue.map((x) => [x.rawId, x]));
const runMsgByRaw = new Map(audit.runMessages.map((x) => [x.rawId, x]));
const topByManual = new Map((broad.topMatches || []).map((x) => [x.manual_id, x]));

const nearestByExpected = new Map();
for (const m of audit.manualMatches) {
  const expectedMs = new Date(m.expectedTime).getTime();
  let best = null;
  for (const raw of audit.rawWindow) {
    const delta = Math.abs(new Date(raw.messageDate).getTime() - expectedMs) / 1000;
    if (!best || delta < best.delta) best = { raw, delta };
  }
  nearestByExpected.set(m.manualId, best);
}

function csvValue(value) {
  if (value === null || value === undefined) return '';
  return `"${String(value).replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
}

function rowFor(id, name, verdict) {
  const nearest = nearestByExpected.get(id);
  const raw = nearest?.raw || null;
  const dataset = raw ? datasetByRaw.get(raw.rawId) : null;
  const chat = raw ? chatByRaw.get(raw.rawId) : null;
  const intake = raw ? intakeByRaw.get(raw.rawId) : null;
  const queue = raw ? queueByRaw.get(raw.rawId) : null;
  const runMsg = raw ? runMsgByRaw.get(raw.rawId) : null;
  const broadTop = topByManual.get(id) || null;
  const exactMatched = (audit.manualMatches || []).find((x) => x.manualId === id && x.rawId);
  const inWindowContentFound = Boolean(exactMatched);
  const status = inWindowContentFound ? 'FOUND_IN_SPECIFIED_WINDOW' : 'EXPECTED_CONTENT_NOT_FOUND_IN_SPECIFIED_WINDOW';
  const skip = inWindowContentFound
    ? 'See pipeline evidence'
    : raw
      ? `nearest timestamp raw does not match expected content; nearest_delta_seconds=${Math.round(nearest.delta)}`
      : 'no raw message near expected timestamp';
  return {
    manual_id: id,
    expected_name: name,
    expected_verdict: verdict,
    specified_window_status: status,
    raw_id: exactMatched?.rawId || null,
    nearest_raw_id: raw?.rawId || null,
    nearest_dataset_message_id: dataset?.datasetMessageId || null,
    nearest_replay_run_message_id: runMsg?.replayRunMessageId || null,
    nearest_run_id: runMsg?.runId || null,
    nearest_message_date: raw?.messageDate || null,
    nearest_chat_id: raw?.telegramChatId || null,
    nearest_chat_title: raw?.chatTitle || null,
    processable: chat?.processable ?? null,
    active_dialog: chat?.activeDialog ?? null,
    auto_pipeline_enabled: chat?.autoPipelineEnabled ?? null,
    pipeline_status: intake?.status || null,
    intake_reason: intake?.reason || null,
    queue_status: queue?.status || null,
    queue_reason: queue?.reason || null,
    single_message_score: runMsg?.singleMessageScore || null,
    final_decision: runMsg?.finalDecision || null,
    single_message_rejection_reason: runMsg?.singleMessageRejectionReason || null,
    discussion_segment_ids: [],
    cluster_membership: runMsg ? [runMsg.microclusterId, runMsg.macroclusterId].filter(Boolean).join('/') : '',
    llm_judge_calls: 0,
    generation_calls: 0,
    material_id: null,
    skip_or_reject_reason: skip,
    correct: id === 'M10' || id === 'M12' ? 'LIKELY_CORRECT_REJECT_OR_NOT_PRESENT' : 'NO_DECISION_ON_EXPECTED_CONTENT_IN_WINDOW',
    notes: broadTop
      ? `broader_day_candidate_raw=${broadTop.raw_id}; broader_day_message_date=${broadTop.message_date}; hit_count=${broadTop.hit_count}`
      : 'no broader-day content candidate found by keyword search',
    nearest_text_preview: raw?.text || '',
    broader_day_candidate_raw_id: broadTop?.raw_id || null,
    broader_day_candidate_message_date: broadTop?.message_date || null,
    broader_day_candidate_hit_count: broadTop?.hit_count || null,
  };
}

const messageRows = expected.map(([id, name, verdict]) => rowFor(id, name, verdict));

const groups = [
  {
    group_id: 'G01',
    expected_material: 'Как проверять AI API proxy: подмена моделей, приватность логов и риски',
    expected_type: 'GUIDE',
    source_manual_ids: 'M02,M03',
    actual_segment_id: '',
    actual_material_id: '',
    actual_status: 'NOT_DETECTED_IN_SPECIFIED_WINDOW',
    reason: 'Expected M02/M03 content was not found in the specified 03:40-04:15 MSK window. No DISCUSSION_SEGMENT existed in that window. Broader-day weak proxy candidates exist but do not confirm the exact PlusVibe/proxy-risk pair.',
    correct: 'NO',
    fix_needed: 'If source content is confirmed outside window, manually materialize or run bounded approved audit/reprocess for exact raw ids only; tune discussion routing for proxy-risk/news/reference chains.',
  },
  {
    group_id: 'G02',
    expected_material: 'Как диагностировать сбои Codex и AI coding tools: статусы, квоты, fallback',
    expected_type: 'GUIDE',
    source_manual_ids: 'M05,M01,M06,M07',
    actual_segment_id: '',
    actual_material_id: '',
    actual_status: 'NOT_DETECTED_IN_SPECIFIED_WINDOW',
    reason: 'Expected Codex/status content was not found in specified window. Broader-day Codex mentions exist, but the requested night window had singleton batches ending NO_MATERIAL_CANDIDATES and zero DISCUSSION_SEGMENT rows.',
    correct: 'NO',
    fix_needed: 'Add news/status/reference mode and multi-message temporal grouping; consider manual material only after exact source ids are selected.',
  },
  {
    group_id: 'G03',
    expected_material: 'OpenMontage: что это за AI video-agent pipeline и как оценить пользу',
    expected_type: 'REFERENCE_OR_SUMMARY',
    source_manual_ids: 'M09',
    actual_segment_id: '',
    actual_material_id: '',
    actual_status: 'NOT_DETECTED_IN_SPECIFIED_WINDOW',
    reason: 'OpenMontage-like broader-day candidate raw 11484 exists at 06:01:19Z / 09:01:19 MSK, outside specified 03:40-04:15 MSK. No material was created.',
    correct: 'NO_FOR_EXPECTED_CONTENT',
    fix_needed: 'Add reference/resource mode; manually materialize G03 from exact raw 11484 if approved.',
  },
  {
    group_id: 'G04',
    expected_material: 'Почему не стоит строить workflow на временных model access loopholes',
    expected_type: 'SUMMARY_OR_ANSWER',
    source_manual_ids: 'M07',
    actual_segment_id: '',
    actual_material_id: '',
    actual_status: 'NOT_DETECTED_IN_SPECIFIED_WINDOW',
    reason: 'Expected Claude/Fable loophole content was not found in specified window. Broader-day digest candidate exists. Risk-sensitive guard should keep this safety-summary-only or reject.',
    correct: 'YES_TO_NOT_GENERATE_HOWTO',
    fix_needed: 'Keep as safety summary only; do not generate bypass instructions.',
  },
];

const outJson = {
  capturedAt: new Date().toISOString(),
  scope: {
    localWindow: audit.capCounts.localWindow,
    utcWindow: audit.capCounts.utcWindow,
    note: 'Expected M01-M12 content did not match raw message content in the specified window; rows include nearest timestamp raw message plus broader-day keyword candidate where found.',
  },
  counts: {
    rawWindow: audit.rawWindow.length,
    datasetMessages: audit.datasetMessages.length,
    runMessages: audit.runMessages.length,
    discussionSegments: audit.discussionSegments.length,
    providerCalls: audit.providerCalls.length,
    materials: audit.materials.length,
    manualContentMatchesInWindow: audit.manualMatches.filter((x) => x.rawId).length,
  },
  capImpact: {
    dailyCapWasCauseForSpecifiedWindow: false,
    reason: 'No DISCUSSION_SEGMENT candidates existed in the specified window, so daily cap was not reached for those expected messages. Also UTC day 2026-06-27 had zero DISCUSSION_SEGMENT materials at capture.',
    rawCapCounts: audit.capCounts,
  },
  messages: messageRows,
  groups,
  correctRejects: ['M08 abuse/referral should be rejected if found', 'M10 entity-only not found/should reject', 'M11 link-only should reject or needs-context', 'M12 feedback-only not found/should reject', 'G04 should not become bypass how-to'],
  manualMaterialCandidates: ['G03 OpenMontage raw 11484 if approved and source verified', 'G01 only if exact PlusVibe/proxy-risk source raw ids are identified', 'G02 only if exact Codex outage/status source raw ids are selected'],
  recommendations: [
    'Keep controlled mode as-is until final 24h window elapses.',
    'Do not increase cap in this task.',
    'Reset daily cap calculation later to count only post-enable auto DISCUSSION_SEGMENT materials, not pre-enable manual materialization.',
    'Add news/reference mode for high-value links and product/resource announcements.',
    'Improve link-only enrichment before treating links as material candidates.',
    'Keep G04 safety-summary-only or reject; never generate bypass how-to.',
    'Reject abuse/referral content.',
  ],
};

fs.writeFileSync(path.join(reports, 'overnight-expected-materials-audit-20260627.json'), JSON.stringify(outJson, null, 2), 'utf8');

const msgHeader = ['manual_id','raw_id','expected_verdict','actual_status','candidate_type','segment_id','score','llm_decision','material_id','skip_or_reject_reason','correct','notes','nearest_raw_id','dataset_message_id','replay_run_message_id','message_date','processable','active_dialog','auto_pipeline_enabled','pipeline_status','single_message_rejection_reason','broader_day_candidate_raw_id','broader_day_candidate_message_date'];
const msgLines = [msgHeader.join(',')];
for (const r of messageRows) {
  msgLines.push([
    r.manual_id,
    r.raw_id,
    r.expected_verdict,
    r.specified_window_status,
    r.discussion_segment_ids.length ? 'DISCUSSION_SEGMENT' : (r.final_decision ? 'SINGLE_MESSAGE_EVALUATED' : 'NONE_FOR_EXPECTED_CONTENT'),
    r.discussion_segment_ids.join('|'),
    r.single_message_score,
    r.final_decision,
    r.material_id,
    r.skip_or_reject_reason,
    r.correct,
    r.notes,
    r.nearest_raw_id,
    r.nearest_dataset_message_id,
    r.nearest_replay_run_message_id,
    r.nearest_message_date,
    r.processable,
    r.active_dialog,
    r.auto_pipeline_enabled,
    r.pipeline_status,
    r.single_message_rejection_reason,
    r.broader_day_candidate_raw_id,
    r.broader_day_candidate_message_date,
  ].map(csvValue).join(','));
}
fs.writeFileSync(path.join(reports, 'overnight-expected-materials-audit-messages-20260627.csv'), msgLines.join('\n') + '\n', 'utf8');

const groupHeader = ['group_id','expected_material','expected_type','source_manual_ids','actual_segment_id','actual_material_id','actual_status','reason','correct','fix_needed'];
const groupLines = [groupHeader.join(',')];
for (const g of groups) groupLines.push(groupHeader.map((k) => csvValue(g[k])).join(','));
fs.writeFileSync(path.join(reports, 'overnight-expected-materials-audit-groups-20260627.csv'), groupLines.join('\n') + '\n', 'utf8');

const md = `# Overnight Expected Materials Audit - 2026-06-27

Status: read-only audit complete. No settings, materials, prompts, caps, reprocess, Telegram session, proxy, or publishing state were changed.

## Scope

- Local window: ${audit.capCounts.localWindow.start} - ${audit.capCounts.localWindow.end} ${audit.capCounts.localWindow.timezone}
- UTC window: ${audit.capCounts.utcWindow.start} - ${audit.capCounts.utcWindow.end}
- Raw messages in window: ${audit.rawWindow.length}
- Dataset messages in window: ${audit.datasetMessages.length}
- Replay run messages in window: ${audit.runMessages.length}
- DISCUSSION_SEGMENT rows in window: ${audit.discussionSegments.length}
- Provider calls from window runs: ${audit.providerCalls.length}
- Materials from window sources: ${audit.materials.length}

## Plain Answers

1. Did the system ingest all night messages? Yes for the specified DB window: ${audit.rawWindow.length} raw rows and ${audit.datasetMessages.length} dataset rows were present.
2. Were they processable? The nearest timestamp rows used for trace were processable where chat state existed; the expected content itself was not found in that exact window.
3. Did DISCUSSION_SEGMENT detect G01/G02/G03/G04? No. There were zero DISCUSSION_SEGMENT rows in the specified window.
4. Did any expected useful material become a DRAFT? No. Zero materials were linked to the specified window.
5. Was non-materialization due to daily cap? No for the specified window: no DISCUSSION_SEGMENT candidate reached the cap gate. UTC 2026-06-27 had zero DISCUSSION_SEGMENT materials at capture. Pre-enable materials 23/24/25 used the 2026-06-26 UTC day, not this night window's UTC day.
6. Which expected materials should be created manually now? G03 from broader-day candidate raw 11484 is the clearest candidate if approved. G01/G02 need exact source raw ids first because the expected content was not found in the specified window. G04 should only be safety summary or reject.
7. Which rules need tuning? Add news/reference mode, improve link-only enrichment, and improve multi-message temporal grouping across non-singleton bursts.
8. Which messages were correctly rejected? M08/M10/M11/M12 should not become guides as abuse/entity-only/link-only/feedback-only; G04 must not become bypass how-to.

## Critical Finding

The expected M01-M12 content does not match the actual raw text in the requested ${audit.capCounts.localWindow.start}-${audit.capCounts.localWindow.end} MSK window. Exact keyword/content matching found 0 of 12 expected items in that window. A broader-day search found candidate content for some items outside the requested window, for example M01 around 07:48 MSK, M09 around 09:01 MSK, and M11 around 03:14 MSK.

## Message Comparison

| manual_id | raw_id | expected_verdict | actual_status | candidate_type | segment_id | score | llm_decision | material_id | skip_or_reject_reason | correct? | notes |
|---|---:|---|---|---|---|---:|---|---:|---|---|---|
${messageRows.map((r) => `| ${r.manual_id} | ${r.raw_id || ''} | ${r.expected_verdict} | ${r.specified_window_status} | ${r.discussion_segment_ids.length ? 'DISCUSSION_SEGMENT' : (r.final_decision ? 'SINGLE_MESSAGE_EVALUATED_NEAREST_TIMESTAMP' : 'NONE_FOR_EXPECTED_CONTENT')} | ${r.discussion_segment_ids.join('|')} | ${r.single_message_score || ''} | ${r.final_decision || ''} | ${r.material_id || ''} | ${r.skip_or_reject_reason} | ${r.correct} | ${r.notes} |`).join('\n')}

## Group Comparison

| group_id | expected_material | expected_type | source_manual_ids | actual_segment_id | actual_material_id | actual_status | reason | correct? | fix_needed |
|---|---|---|---|---|---|---|---|---|---|
${groups.map((g) => `| ${g.group_id} | ${g.expected_material} | ${g.expected_type} | ${g.source_manual_ids} | ${g.actual_segment_id} | ${g.actual_material_id} | ${g.actual_status} | ${g.reason} | ${g.correct} | ${g.fix_needed} |`).join('\n')}

## Daily Cap Impact

- Pre-enable same UTC day DISCUSSION materials: ${audit.capCounts.preEnableDiscussionMaterialsSameUtcDay.length} (materials 23/24/25).
- DISCUSSION materials on 2026-06-26 UTC: ${audit.capCounts.discussionMaterialsOn20260626Utc}.
- DISCUSSION materials on 2026-06-27 UTC at capture: ${audit.capCounts.discussionMaterialsOn20260627Utc}.
- Window DISCUSSION candidates: ${audit.capCounts.windowDiscussionCandidates}.
- Conclusion: daily cap did not block the specified night window because no window DISCUSSION candidate existed. The earlier cap issue remains real for 2026-06-26 UTC and should be fixed separately by counting only post-enable auto materials.

## Recommendations

- Keep controlled mode as-is until the real 24h final snapshot can be taken.
- Do not increase caps during this audit task.
- Later, change daily cap accounting so pre-enable manual materialization does not consume controlled auto cap.
- Add news/reference mode for high-value model-release, product-update, and repository-resource posts.
- Improve link-only enrichment before generating from links.
- Manually materialize G03 from raw 11484 if approved; identify exact raw ids before materializing G01/G02.
- Keep G04 safety-summary-only or reject.
- Continue rejecting abuse/referral, entity-only, link-only, and feedback-only content.
`;
fs.writeFileSync(path.join(reports, 'overnight-expected-materials-audit-20260627.md'), md, 'utf8');

const controlledOut = {
  capturedAt: controlled.capturedAt,
  enableTime: controlled.enableTime,
  finalEligibleAt: controlled.finalEligibleAt,
  status: controlled.full24hElapsed ? 'FINAL_24H' : 'INTERIM_NOT_FINAL',
  full24hElapsed: controlled.full24hElapsed,
  settings: controlled.settings,
  counts: controlled.counts,
  healthCounts: controlled.healthCounts,
  skipReasonsAfterEnable: controlled.skipReasonsAfterEnable,
  providerStatusesAfterEnable: controlled.providerStatusesAfterEnable,
  preEnableSameDayDiscussionMaterials: controlled.preEnableSameDayDiscussionMaterials,
  materialsAfterEnable: controlled.materialsAfterEnable,
  recommendation: controlled.full24hElapsed
    ? 'Review final evidence before changing caps or routing.'
    : 'This is interim only. Take final report after finalEligibleAt before calling 24h complete.',
};
fs.writeFileSync(path.join(reports, 'discussion-segment-controlled-production-24h-20260627.json'), JSON.stringify(controlledOut, null, 2), 'utf8');

const controlledMd = `# DISCUSSION_SEGMENT Controlled Production 24h Report - 2026-06-27

Status: ${controlledOut.status}

This report is ${controlled.full24hElapsed ? 'a final 24h snapshot.' : 'an interim snapshot only. The full 24h window has not elapsed.'}

## Timing

- Enabled at: ${controlled.enableTime}
- Captured at: ${controlled.capturedAt}
- Final eligible at: ${controlled.finalEligibleAt}
- Full 24h elapsed: ${controlled.full24hElapsed}

## Effective Settings

- Mode: ${controlled.settings.discussionSegmentGenerationMode}
- Generation enabled: ${controlled.settings.discussionSegmentGenerationEnabled}
- Fresh only: ${controlled.settings.discussionSegmentFreshOnly}
- Draft only: ${controlled.settings.discussionSegmentDraftOnly}
- Daily cap: ${controlled.settings.discussionSegmentMaxMaterialsPerDay}
- Per chat/topic/day cap: ${controlled.settings.discussionSegmentMaxMaterialsPerChatTopicPerDay}
- LLM accepted required: ${controlled.settings.discussionSegmentRequireLlmAccepted}
- Risk-sensitive skip: ${controlled.settings.discussionSegmentSkipRiskSensitive}
- Stop on provider error: ${controlled.settings.discussionSegmentStopOnProviderError}
- Stop on generation error: ${controlled.settings.discussionSegmentStopOnGenerationError}

## Activity

- Segments detected after enable: ${controlled.counts.segmentsDetectedAfterEnable}
- Scorer accepted after enable: ${controlled.counts.scorerAcceptedAfterEnable}
- LLM judge calls after enable: ${controlled.counts.llmJudgeCallsAfterEnable}
- LLM judge success/errors: ${controlled.counts.llmJudgeSuccessAfterEnable}/${controlled.counts.llmJudgeErrorsAfterEnable}
- Generation calls after enable: ${controlled.counts.generationCallsAfterEnable}
- Generation success/errors: ${controlled.counts.generationSuccessAfterEnable}/${controlled.counts.generationErrorsAfterEnable}
- DRAFT materials after enable: ${controlled.counts.draftMaterialsAfterEnable}
- PUBLISHED materials after enable: ${controlled.counts.publishedMaterialsAfterEnable}
- Pre-enable same UTC day DISCUSSION materials: ${controlled.counts.preEnableSameUtcDayDiscussionMaterials}
- DISCUSSION materials on 2026-06-26 UTC: ${controlled.counts.discussionMaterialsOn20260626Utc}
- DISCUSSION materials on 2026-06-27 UTC: ${controlled.counts.discussionMaterialsOn20260627Utc}

## Stop Conditions And Health

- Provider statuses: ${JSON.stringify(controlled.providerStatusesAfterEnable)}
- Skip reasons: ${JSON.stringify(controlled.skipReasonsAfterEnable)}
- Failed intake: ${controlled.healthCounts.failedIntake}
- Queued intake: ${controlled.healthCounts.queuedIntake}
- Pending intake: ${controlled.healthCounts.pendingIntake}
- Active knowledge items: ${controlled.healthCounts.activeKnowledgeItemsTotal}
- Active DISCUSSION_SEGMENT materials: ${controlled.healthCounts.activeDiscussionMaterialsTotal}

## Recommendation

${controlledOut.recommendation}

Keep controlled mode as-is for now. Do not call this final until after ${controlled.finalEligibleAt}. Separately investigate provider configuration/timeouts and daily cap semantics before increasing automation.
`;
fs.writeFileSync(path.join(reports, 'discussion-segment-controlled-production-24h-20260627.md'), controlledMd, 'utf8');

console.log('reports generated');
