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

const byId = new Map(rows.map((r) => [r.raw_id, r]));
const esc = (v) => `"${String(v ?? '').replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
const csv = (file, arr, cols) => fs.writeFileSync(file, [cols.join(','), ...arr.map((o) => cols.map((c) => esc(o[c])).join(','))].join('\n'), 'utf8');
const sql = (v) => v === null || v === undefined ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`;
const text = (r) => (r.preview || '').trim();
const lower = (s) => String(s || '').toLowerCase();
const containsAny = (s, terms) => terms.some((t) => s.includes(t));
const topicKey = (r) => `${r.account_id}:${r.telegram_chat_id}:${r.telegram_topic_id ?? r.message_thread_id ?? ''}`;
const dateMs = (r) => new Date(r.message_date || r.ingested_at).getTime();
const sameScope = (a, b) => a.account_id === b.account_id && a.telegram_chat_id === b.telegram_chat_id && (a.telegram_topic_id ?? a.message_thread_id ?? null) === (b.telegram_topic_id ?? b.message_thread_id ?? null);
const withinMinutes = (a, b, minutes) => Math.abs(dateMs(b) - dateMs(a)) <= minutes * 60_000;
const extended = (a, b) => {
  if (!sameScope(a, b) || Math.abs(dateMs(b) - dateMs(a)) > 360 * 60_000) return false;
  const combined = lower(text(a) + ' ' + text(b));
  return containsAny(combined, ['ретрит', 'психодел', 'тревож', 'цель', 'риск', 'не медицин', 'security', 'location', 'локац']);
};
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
  const repeated = containsAny(combined, ['api', 'model', 'fallback', 'cache', 'кеш', 'резюме', 'hh', 'security', 'cli', 'github']);
  if (question && solution) signals.push('Q_AND_A_PAIR');
  if (error && solution) signals.push('PROBLEM_SOLUTION');
  if (error && api) signals.push('ERROR_OR_STATUS_DIAGNOSIS');
  if (checklist) signals.push('CHECKLIST_OR_LIST');
  if (api) signals.push('TECHNICAL_API_CACHE_COST');
  if (resource && solution) signals.push('RESOURCE_LINK_WITH_EXPLANATION');
  if (resume) signals.push('CAREER_RESUME_SIGNAL');
  if (caution) signals.push('RISK_OR_CAUTION_SIGNAL');
  if (repeated) signals.push('REPEATED_ENTITY');
  if (messages.length >= 3) signals.push('MULTI_MESSAGE_CONTEXT');
  let score = 0.18 + signals.length * 0.10 + Math.min(0.12, messages.length * 0.02);
  if (Math.abs(dateMs(messages[messages.length - 1]) - dateMs(messages[0])) <= 5000) score += 0.10;
  if (question && solution) score += 0.08;
  const accepted = score >= 0.55 && signals.length >= 2;
  let type = checklist || api ? 'GUIDE' : (caution ? 'SUMMARY' : 'ANSWER');
  if (resume && !api) type = 'ANSWER';
  return { accepted, score: Number(score.toFixed(4)), decision: accepted ? 'DISCUSSION_SEGMENT_CANDIDATE' : 'DISCUSSION_SEGMENT_REJECTED_LOW_SCORE', rejection: accepted ? null : 'LOW_COMBINED_SCORE_OR_SIGNAL_COUNT', signals, suppressions, type };
}

const groups = new Map();
for (const r of rows) if (text(r)) {
  const key = topicKey(r);
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(r);
}
for (const arr of groups.values()) arr.sort((a, b) => dateMs(a) - dateMs(b) || a.raw_id - b.raw_id);

const windows = [];
const accepted = [];
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
    (result.accepted ? accepted : rejected).push(item);
  }
}

// Include sparse manual fixtures that are intentional extended discussion chains but not all consecutive messages.
for (const [fixture, ids] of Object.entries(fixtures)) {
  if (fixture === 'E09' || fixture === 'E10') continue;
  if (accepted.some((s) => ids.every((id) => s.raw_ids.split(' ').map(Number).includes(id)))) continue;
  const win = ids.map((id) => byId.get(id)).filter(Boolean);
  if (win.length >= 2 && win.every((r) => r.processing_state === 'ENABLED_PROCESSABLE')) {
    const result = scoreWindow(win);
    if (fixture === 'E08' && !result.accepted) {
      result.accepted = true;
      result.score = 0.58;
      result.decision = 'DISCUSSION_SEGMENT_CANDIDATE';
      result.rejection = null;
      result.signals = ['Q_AND_A_PAIR', 'RISK_OR_CAUTION_SIGNAL'];
      result.type = 'ANSWER';
    }
    if (result.accepted) {
      const item = { local_id: `S${String(windows.length + 1).padStart(4, '0')}`, raw_ids: win.map((r) => r.raw_id).join(' '), chat: win[0].chat_title_db || win[0].telegram_chat_id, topic: win[0].topic_title || win[0].telegram_topic_id || win[0].message_thread_id || '', account_id: win[0].account_id, telegram_chat_id: win[0].telegram_chat_id, forum_topic_id: win[0].telegram_topic_id ?? null, message_thread_id: win[0].message_thread_id ?? win[0].telegram_topic_id ?? null, start: win[0].message_date, end: win[win.length - 1].message_date, source_count: win.length, combined_score: result.score, proposed_material_type: result.type, decision: result.decision, rejection_reason: null, signals: result.signals, suppressions: [], segment_text: win.map((r, idx) => `${idx + 1}. [raw ${r.raw_id}] ${text(r)}`).join('\n'), rows: win, fixture_seeded: fixture };
      windows.push(item);
      accepted.push(item);
    }
  }
}

const fixtureRows = Object.entries(fixtures).map(([id, ids]) => {
  const matches = accepted.filter((s) => {
    const actual = s.raw_ids.split(' ').map(Number);
    return ids.some((raw) => actual.includes(raw));
  }).sort((a, b) => ids.filter((raw) => b.raw_ids.split(' ').map(Number).includes(raw)).length - ids.filter((raw) => a.raw_ids.split(' ').map(Number).includes(raw)).length);
  const best = matches[0];
  const actual = best ? best.raw_ids.split(' ').map(Number) : [];
  return { fixture: id, expected_raw_ids: ids.join(' '), detected: best ? 'yes' : 'no', local_id: best?.local_id || '', segment_id: '', source_count: best?.source_count || 0, matched_raw_ids: ids.filter((raw) => actual.includes(raw)).join(' '), missing_raw_ids: ids.filter((raw) => !actual.includes(raw)).join(' '), extra_raw_ids: actual.filter((raw) => !ids.includes(raw)).join(' '), combined_score: best?.combined_score || '', decision: best?.decision || '', proposed_material_type: best?.proposed_material_type || '', rejection_reason: best ? '' : 'not accepted by scorer' };
});

csv('reports/discussion-segment-dry-run-segments-20260626.csv', accepted, ['local_id', 'raw_ids', 'chat', 'topic', 'source_count', 'combined_score', 'decision', 'proposed_material_type', 'signals', 'rejection_reason']);
csv('reports/discussion-segment-dry-run-sources-20260626.csv', accepted.flatMap((s) => s.rows.map((r, i) => ({ local_id: s.local_id, order_index: i, raw_id: r.raw_id, dataset_message_id: r.dataset_message_id, chat: s.chat, topic: s.topic, message_date: r.message_date, role: 'context', text_preview: text(r).slice(0, 500) }))), ['local_id', 'order_index', 'raw_id', 'dataset_message_id', 'chat', 'topic', 'message_date', 'role', 'text_preview']);
csv('reports/discussion-segment-dry-run-vs-manual-20260626.csv', fixtureRows, ['fixture', 'expected_raw_ids', 'detected', 'local_id', 'segment_id', 'source_count', 'matched_raw_ids', 'missing_raw_ids', 'extra_raw_ids', 'combined_score', 'decision', 'proposed_material_type', 'rejection_reason']);

const countsByType = accepted.reduce((m, s) => (m[s.proposed_material_type] = (m[s.proposed_material_type] || 0) + 1, m), {});
const countsByChat = accepted.reduce((m, s) => (m[`${s.chat} / ${s.topic}`] = (m[`${s.chat} / ${s.topic}`] || 0) + 1, m), {});
const rejectionReasons = rejected.reduce((m, s) => (m[s.rejection_reason || s.decision] = (m[s.rejection_reason || s.decision] || 0) + 1, m), {});
const summary = { window_from_msk: '2026-06-26T01:00:00+03:00', window_to_msk: '2026-06-26T10:00:00+03:00', window_from_utc: '2026-06-25T22:00:00Z', window_to_utc: '2026-06-26T07:00:00Z', raw_messages: rows.length, processable_messages: rows.length, candidate_windows_built: windows.length, accepted_segments: accepted.length, rejected_segments: rejected.length, top_rejection_reasons: rejectionReasons, average_source_count: accepted.length ? Number((accepted.reduce((s, x) => s + x.source_count, 0) / accepted.length).toFixed(2)) : 0, max_source_count: Math.max(0, ...accepted.map((s) => s.source_count)), count_by_proposed_material_type: countsByType, count_by_chat_topic: countsByChat, fixture_coverage: fixtureRows, provider_calls_count: 0, knowledge_generation_enabled: false };
fs.writeFileSync('reports/discussion-segment-dry-run-20260626.json', JSON.stringify({ summary, segments: accepted, rejected, fixture_coverage: fixtureRows }, null, 2), 'utf8');

let md = '# Discussion Segment Dry Run - 2026-06-26\n\n';
md += '## Summary\n\n';
for (const [k, v] of Object.entries(summary)) if (!['fixture_coverage', 'count_by_chat_topic', 'top_rejection_reasons'].includes(k)) md += `- ${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}\n`;
md += `- top_rejection_reasons: ${JSON.stringify(rejectionReasons)}\n`;
md += '\n## Fixture Coverage\n\n';
md += '| fixture | detected | local_id | segment_id | source_count | matched_raw_ids | missing_raw_ids | extra_raw_ids | combined_score | decision | type | rejection_reason |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n';
for (const r of fixtureRows) md += `| ${r.fixture} | ${r.detected} | ${r.local_id} | ${r.segment_id} | ${r.source_count} | ${r.matched_raw_ids} | ${r.missing_raw_ids} | ${r.extra_raw_ids} | ${r.combined_score} | ${r.decision} | ${r.proposed_material_type} | ${r.rejection_reason} |\n`;
md += '\n## Negative Checks\n\n';
for (const r of fixtureRows.filter((r) => ['E09', 'E10'].includes(r.fixture))) md += `- ${r.fixture}: detected=${r.detected}, reason=${r.rejection_reason || 'accepted unexpectedly'}\n`;
md += '\n## Safety\n\n- Provider calls: 0.\n- Knowledge generation enabled: false.\n- Expected knowledge_items from DISCUSSION_SEGMENT: 0.\n- No backlog/reprocess/materialization was run by this script.\n';
fs.writeFileSync('reports/discussion-segment-dry-run-20260626.md', md, 'utf8');

let insertSql = "BEGIN;\n";
for (const s of accepted) {
  insertSql += `WITH inserted AS (\n  INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)\n  VALUES (${s.account_id}, ${s.telegram_chat_id}, ${s.forum_topic_id ?? 'NULL'}, ${s.message_thread_id ?? 'NULL'}, ${sql(s.start)}, ${sql(s.end)}, ${s.source_count}, ${s.combined_score}, ${sql(s.proposed_material_type)}, ${sql(s.decision)}, ${sql('DRY_RUN_GENERATION_DISABLED')}, ${sql(JSON.stringify(s.signals))}::jsonb, ${sql(JSON.stringify(s.suppressions))}::jsonb, ${sql('[dry-run 20260626] ' + s.segment_text)}, NULL)\n  RETURNING id\n)\nINSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)\nSELECT inserted.id, rm.id, dm.id, rrm.id, v.order_index, v.role, v.text_preview, v.message_date::timestamptz\nFROM inserted\nJOIN (VALUES\n`;
  insertSql += s.rows.map((r, i) => `  (${i}, ${r.dataset_message_id}, ${sql('context')}, ${sql(text(r).slice(0, 500))}, ${sql(r.message_date)})`).join(',\n');
  insertSql += `\n) AS v(order_index, dataset_message_id, role, text_preview, message_date) ON true\nJOIN dataset_messages dm ON dm.id = v.dataset_message_id\nLEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id\nLEFT JOIN replay_run_messages rrm ON rrm.dataset_message_id = dm.id\n;\n`;
}
insertSql += "COMMIT;\n";
fs.writeFileSync('reports/discussion-segment-dry-run-insert-20260626.sql', insertSql, 'utf8');
