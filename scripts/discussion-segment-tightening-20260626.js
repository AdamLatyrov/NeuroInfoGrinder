const fs = require('fs');

const rows = fs.readFileSync('reports/overnight-raw-evidence-20260626.jsonl', 'utf8')
  .trim().split(/\r?\n/).filter(Boolean).map(JSON.parse)
  .filter((r) => r.processing_state === 'ENABLED_PROCESSABLE');

const fixtures = {
  E01: [6034, 6042, 6052, 6062, 6069, 6070, 6078, 6079, 6105],
  E02: [6765, 6776, 6778, 6799, 6803, 6805, 6817, 6826, 6836, 6850, 6876],
  E04: [6221, 6223, 6224, 6225],
  E07: [6341, 6540, 6543, 6592, 6688, 6820],
  E08: [6144, 6367],
  E09: [6419, 6544, 6545, 6831],
  E10: [6306, 6307]
};

const esc = (v) => `"${String(v ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((o) => cols.map((c) => esc(o[c])).join(','))].join('\n'), 'utf8');
const text = (r) => (r.preview || '').trim();
const lower = (s) => String(s || '').toLowerCase();
const containsAny = (s, terms) => terms.some((t) => s.includes(t));
const topicKey = (r) => `${r.account_id}:${r.telegram_chat_id}:${r.telegram_topic_id ?? r.message_thread_id ?? ''}`;
const dateMs = (r) => new Date(r.message_date || r.ingested_at).getTime();
const sameScope = (a, b) => a.account_id === b.account_id && a.telegram_chat_id === b.telegram_chat_id && (a.telegram_topic_id ?? a.message_thread_id ?? null) === (b.telegram_topic_id ?? b.message_thread_id ?? null);
const withinMinutes = (a, b, minutes) => Math.abs(dateMs(b) - dateMs(a)) <= minutes * 60_000;
const sameTimestampBurst = (messages) => messages.length >= 2 && Math.abs(dateMs(messages[messages.length - 1]) - dateMs(messages[0])) <= 5000;
const extended = (a, b) => {
  if (!sameScope(a, b) || Math.abs(dateMs(b) - dateMs(a)) > 360 * 60_000) return false;
  const combined = lower(text(a) + ' ' + text(b));
  return containsAny(combined, ['ретрит', 'психодел', 'тревож', 'цель', 'риск', 'не медицин', 'security', 'location', 'локац']);
};
const rawIds = (s) => s.rows.map((r) => r.raw_id);
const overlapRatio = (a, b) => {
  const left = new Set(rawIds(a));
  const right = new Set(rawIds(b));
  const intersection = [...left].filter((id) => right.has(id)).length;
  return intersection ? intersection / Math.min(left.size, right.size) : 0;
};
const timeOverlap = (a, b) => dateMs(a.rows[a.rows.length - 1]) >= dateMs(b.rows[0]) && dateMs(b.rows[b.rows.length - 1]) >= dateMs(a.rows[0]);
const usefulSignalCount = (s) => s.signals.filter((x) => x !== 'REPEATED_ENTITY' && x !== 'MULTI_MESSAGE_CONTEXT').length;
const hasSolution = (s) => s.signals.some((x) => ['Q_AND_A_PAIR', 'PROBLEM_SOLUTION', 'FINAL_SUMMARY_SIGNAL', 'CHECKLIST_OR_LIST'].includes(x));
const sourcePenalty = (s) => s.source_count >= 2 && s.source_count <= 6 ? 0 : Math.abs(s.source_count - 6);
const bestFirst = (a, b) => (b.combined_score - a.combined_score) || (usefulSignalCount(b) - usefulSignalCount(a)) || (sourcePenalty(a) - sourcePenalty(b)) || (Number(hasSolution(b)) - Number(hasSolution(a))) || (dateMs(a.rows[0]) - dateMs(b.rows[0]));
const bucketKey = (s) => `${s.account_id}:${s.telegram_chat_id}:${s.forum_topic_id ?? ''}:${s.message_thread_id ?? ''}:${Math.floor((dateMs(s.rows[0]) / 60000) / 20)}`;

function scoreWindow(messages) {
  const combined = lower(messages.map(text).join('\n'));
  const suppressions = [];
  if (containsAny(combined, ['промокод', 'скидк', 'купи', 'hosting', 'vps', 'реф ссыл', 'акция'])) suppressions.push('PROMO_OR_AD');
  if (containsAny(combined, ['лонг', 'шорт', 'ликвид', 'депо', 'бирж', 'prop firm', 'трейд'])) suppressions.push('CRYPTO_TRADING_OFFTOPIC');
  if (containsAny(combined, ['добро пожаловать', 'welcome', 'правила сообщества', 'ознакомься'])) suppressions.push('WELCOME_TEMPLATE');
  if (containsAny(combined, ['gpt-5.6', 'новости ai', 'rutube', 'новая модель']) && !containsAny(combined, ['как', 'решение', 'чеклист', 'проверь'])) suppressions.push('LOW_ACTIONABILITY_NEWS');
  if (messages.every((r) => !text(r))) suppressions.push('MEDIA_NO_TEXT');
  if (suppressions.length) return { accepted: false, score: 0, decision: 'DISCUSSION_SEGMENT_REJECTED_NOISE', rejection: suppressions.join(';'), signals: [], suppressions, type: 'IGNORE' };
  const signals = [];
  const question = containsAny(combined, ['?', 'как ', 'можно ли', 'что делать', 'почему', 'какая цель', 'хочу задать']);
  const solution = containsAny(combined, ['проверь', 'нужно', 'надо', 'лучше', 'решение', 'работает', 'если', 'значит', 'отключ', 'сделай']);
  const error = containsAny(combined, ['error', 'ошиб', '401', '404', 'timeout', 'тупит', 'не работает', 'лимит']);
  const checklist = containsAny(combined, ['чеклист', '1.', '2.', '3.', 'список', 'пункт', 'фидбек']);
  const api = containsAny(combined, ['api', 'endpoint', 'model', 'fallback', 'openai', 'hermes', 'anthropic', 'cache', 'token', 'лимит', 'context', 'контекст', 'кеш']);
  const resource = containsAny(combined, ['github', 'http://', 'https://', 'repo', 'репо', 'ссылка']);
  const resume = containsAny(combined, ['резюме', 'hh', 'достижен', 'формулиров', 'hr', 'ai-фильтр', 'фильтр']);
  const caution = containsAny(combined, ['риск', 'security', 'тревож', 'негатив', 'не медицин', 'осторож', 'спалить', 'цель']);
  const finalSummary = containsAny(combined, ['итог', 'вывод', 'резюме:', 'summary']);
  const productFeedback = sameTimestampBurst(messages) && containsAny(combined, ['боли', 'фидбек', 'пользовател', 'нет контроля', 'дорого', 'слабые модели', 'собираю ос', 'буду рад ос', 'горение', 'горит', 'существующий список', 'отсутствие', 'траты токенов']);
  const repeated = containsAny(combined, ['api', 'model', 'fallback', 'cache', 'кеш', 'резюме', 'hh', 'security', 'cli', 'github']);
  if (question && solution) signals.push('Q_AND_A_PAIR');
  if (error && solution) signals.push('PROBLEM_SOLUTION');
  if (error && api) signals.push('ERROR_OR_STATUS_DIAGNOSIS');
  if (checklist) signals.push('CHECKLIST_OR_LIST');
  if (api) signals.push('TECHNICAL_API_CACHE_COST');
  if (resource && solution) signals.push('RESOURCE_LINK_WITH_EXPLANATION');
  if (resume) signals.push('CAREER_RESUME_SIGNAL');
  if (caution) signals.push('RISK_OR_CAUTION_SIGNAL');
  if (finalSummary) signals.push('FINAL_SUMMARY_SIGNAL');
  if (productFeedback) signals.push('PRODUCT_FEEDBACK_BURST');
  if (repeated) signals.push('REPEATED_ENTITY');
  if (messages.length >= 3) signals.push('MULTI_MESSAGE_CONTEXT');
  const useful = signals.filter((x) => x !== 'REPEATED_ENTITY' && x !== 'MULTI_MESSAGE_CONTEXT').length;
  const highRiskNeedsContext = caution && question && messages.length <= 2 && containsAny(combined, ['security', 'location', 'локац', 'компани', 'работать из другой страны', 'спалить', 'юрид', 'медицин', 'здоров', 'финанс']);
  if (highRiskNeedsContext) return { accepted: false, score: 0, decision: 'DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT', rejection: 'DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT', signals, suppressions, type: 'IGNORE' };
  if (useful <= 1 && repeated) return { accepted: false, score: 0, decision: 'DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY', rejection: 'DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY', signals, suppressions, type: 'IGNORE' };
  if (useful <= 1 && messages.length >= 3) return { accepted: false, score: 0, decision: 'DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT', rejection: 'DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT', signals, suppressions, type: 'IGNORE' };
  let score = 0.18 + signals.length * 0.10 + Math.min(0.12, messages.length * 0.02);
  if (sameTimestampBurst(messages)) score += 0.10;
  if (question && solution) score += 0.08;
  if (score < 0.55 || useful < 2) return { accepted: false, score: Number(score.toFixed(4)), decision: 'DISCUSSION_SEGMENT_REJECTED_LOW_SCORE', rejection: 'LOW_COMBINED_SCORE_OR_SIGNAL_COUNT', signals, suppressions, type: 'IGNORE' };
  let guideSignals = 0;
  if (question && solution) guideSignals++;
  if (error && solution) guideSignals++;
  if (error && api) guideSignals++;
  if (checklist && !productFeedback) guideSignals++;
  if (api) guideSignals++;
  if (finalSummary) guideSignals++;
  let type = guideSignals >= 2 && !productFeedback ? 'GUIDE' : (caution || checklist || productFeedback ? 'SUMMARY' : 'ANSWER');
  if (resume && !api) type = 'ANSWER';
  if (caution && !api) type = 'SUMMARY';
  if (resource && !api && !solution) type = 'REFERENCE';
  return { accepted: true, score: Number(score.toFixed(4)), decision: 'DISCUSSION_SEGMENT_CANDIDATE', rejection: null, signals, suppressions, type };
}

const groups = new Map();
for (const r of rows) if (text(r)) {
  const key = topicKey(r);
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(r);
}
for (const arr of groups.values()) arr.sort((a, b) => dateMs(a) - dateMs(b) || a.raw_id - b.raw_id);

const windows = [];
const acceptedPreDedupe = [];
const rejected = [];
const seen = new Set();
for (const arr of groups.values()) {
  for (let i = 0; i < arr.length; i++) {
    const win = [];
    const start = arr[i];
    for (let j = i; j < arr.length && win.length < 6; j++) {
      const next = arr[j];
      if (!sameScope(start, next)) break;
      if (!withinMinutes(start, next, 20) && !extended(start, next)) break;
      win.push(next);
    }
    if (win.length < 2) continue;
    const key = win.map((r) => r.raw_id).join(',');
    if (!seen.add(key)) continue;
    const result = scoreWindow(win);
    const item = { local_id: `S${String(windows.length + 1).padStart(4, '0')}`, raw_ids: win.map((r) => r.raw_id).join(' '), chat: win[0].chat_title_db || win[0].telegram_chat_id, topic: win[0].topic_title || win[0].telegram_topic_id || win[0].message_thread_id || '', account_id: win[0].account_id, telegram_chat_id: win[0].telegram_chat_id, forum_topic_id: win[0].telegram_topic_id ?? null, message_thread_id: win[0].message_thread_id ?? win[0].telegram_topic_id ?? null, start: win[0].message_date, end: win[win.length - 1].message_date, source_count: win.length, combined_score: result.score, proposed_material_type: result.type, decision: result.decision, rejection_reason: result.rejection, signals: result.signals, suppressions: result.suppressions, segment_text: win.map((r, idx) => `${idx + 1}. [raw ${r.raw_id}] ${text(r)}`).join('\n'), rows: win };
    windows.push(item);
    (result.accepted ? acceptedPreDedupe : rejected).push(item);
  }
}

const acceptedAfterOverlap = [];
for (const item of [...acceptedPreDedupe].sort(bestFirst)) {
  const duplicate = acceptedAfterOverlap.find((s) => sameScope(s.rows[0], item.rows[0]) && overlapRatio(s, item) >= 0.60 && timeOverlap(s, item));
  if (!duplicate) acceptedAfterOverlap.push(item);
  else rejected.push({ ...item, decision: 'DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE', rejection_reason: 'DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE', overlap_ratio: Number(overlapRatio(duplicate, item).toFixed(4)), duplicate_of: duplicate.local_id });
}

const accepted = [];
const buckets = new Map();
for (const s of acceptedAfterOverlap) {
  const key = bucketKey(s);
  if (!buckets.has(key)) buckets.set(key, []);
  buckets.get(key).push(s);
}
for (const bucket of buckets.values()) {
  const kept = [];
  for (const item of bucket.sort(bestFirst)) {
    const distinctThird = kept.length === 2 && kept.every((s) => s.proposed_material_type !== item.proposed_material_type && overlapRatio(s, item) < 0.30);
    if (kept.length < 2 || (kept.length < 3 && distinctThird)) kept.push(item);
    else rejected.push({ ...item, decision: 'DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT', rejection_reason: 'DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT' });
  }
  accepted.push(...kept);
}
accepted.sort((a, b) => dateMs(a.rows[0]) - dateMs(b.rows[0]) || a.rows[0].raw_id - b.rows[0].raw_id);

const fixtureRows = Object.entries(fixtures).map(([id, ids]) => {
  const matches = accepted.filter((s) => {
    const actual = rawIds(s);
    return ids.some((raw) => actual.includes(raw));
  }).sort((a, b) => ids.filter((raw) => rawIds(b).includes(raw)).length - ids.filter((raw) => rawIds(a).includes(raw)).length);
  const best = matches[0];
  const actual = best ? rawIds(best) : [];
  const rejectedMatches = rejected.filter((s) => ids.some((raw) => rawIds(s).includes(raw)));
  return { fixture: id, expected_raw_ids: ids.join(' '), detected: best ? 'yes' : 'no', local_id: best?.local_id || '', source_count: best?.source_count || 0, matched_raw_ids: ids.filter((raw) => actual.includes(raw)).join(' '), missing_raw_ids: ids.filter((raw) => !actual.includes(raw)).join(' '), extra_raw_ids: actual.filter((raw) => !ids.includes(raw)).join(' '), combined_score: best?.combined_score || '', decision: best?.decision || rejectedMatches[0]?.decision || '', proposed_material_type: best?.proposed_material_type || '', rejection_reason: best ? '' : (rejectedMatches[0]?.rejection_reason || 'not accepted by scorer') };
});

const countsByType = accepted.reduce((m, s) => (m[s.proposed_material_type] = (m[s.proposed_material_type] || 0) + 1, m), {});
const rejectionReasons = rejected.reduce((m, s) => (m[s.rejection_reason || s.decision] = (m[s.rejection_reason || s.decision] || 0) + 1, m), {});
const summary = { window_from_msk: '2026-06-26T01:00:00+03:00', window_to_msk: '2026-06-26T10:00:00+03:00', window_from_utc: '2026-06-25T22:00:00Z', window_to_utc: '2026-06-26T07:00:00Z', raw_messages: rows.length, processable_messages: rows.length, previous_candidate_windows_built: 367, previous_accepted_segments: 89, previous_persisted_sources: 501, candidate_windows_built: windows.length, accepted_before_dedupe: acceptedPreDedupe.length, accepted_segments: accepted.length, rejected_segments: rejected.length, overlap_duplicates_rejected: rejectionReasons.DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE || 0, window_limit_rejected: rejectionReasons.DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT || 0, low_value_adjacent_rejected: rejectionReasons.DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT || 0, entity_only_rejected: rejectionReasons.DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY || 0, needs_more_context: rejectionReasons.DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT || 0, persisted_sources_if_inserted: accepted.reduce((s, x) => s + x.source_count, 0), count_by_proposed_material_type: countsByType, top_rejection_reasons: rejectionReasons, fixture_coverage: fixtureRows, provider_calls_count: 0, knowledge_generation_enabled: false, discussion_materials_created: 0 };

csv('reports/discussion-segment-tightening-segments-20260626.csv', accepted, ['local_id', 'raw_ids', 'chat', 'topic', 'source_count', 'combined_score', 'decision', 'proposed_material_type', 'signals', 'rejection_reason']);
csv('reports/discussion-segment-tightening-comparison-20260626.csv', [summary], ['previous_candidate_windows_built', 'candidate_windows_built', 'previous_accepted_segments', 'accepted_before_dedupe', 'accepted_segments', 'previous_persisted_sources', 'persisted_sources_if_inserted', 'overlap_duplicates_rejected', 'window_limit_rejected', 'low_value_adjacent_rejected', 'entity_only_rejected', 'needs_more_context']);
fs.writeFileSync('reports/discussion-segment-tightening-20260626.json', JSON.stringify({ summary, segments: accepted, rejected, fixture_coverage: fixtureRows }, null, 2), 'utf8');

let md = '# Discussion Segment Tightening - 2026-06-26\n\n';
md += '## Summary\n\n';
for (const [k, v] of Object.entries(summary)) if (!['fixture_coverage', 'top_rejection_reasons'].includes(k)) md += `- ${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}\n`;
md += `- top_rejection_reasons: ${JSON.stringify(rejectionReasons)}\n`;
md += '\n## Fixture Coverage\n\n';
md += '| fixture | detected | local_id | source_count | matched_raw_ids | missing_raw_ids | extra_raw_ids | score | decision | type | rejection_reason |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n';
for (const r of fixtureRows) md += `| ${r.fixture} | ${r.detected} | ${r.local_id} | ${r.source_count} | ${r.matched_raw_ids} | ${r.missing_raw_ids} | ${r.extra_raw_ids} | ${r.combined_score} | ${r.decision} | ${r.proposed_material_type} | ${r.rejection_reason} |\n`;
md += '\n## Safety\n\n- Provider calls: 0.\n- Knowledge generation enabled: false.\n- DISCUSSION_SEGMENT materials created: 0.\n- This script only reads prior exported evidence and writes local reports.\n';
fs.writeFileSync('reports/discussion-segment-tightening-20260626.md', md, 'utf8');
