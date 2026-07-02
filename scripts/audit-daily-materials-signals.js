#!/usr/bin/env node

/*
 * Read-only daily quality audit for NeuroInfoGrinder materials and signals.
 *
 * Usage:
 *   node scripts/audit-daily-materials-signals.js
 *   node scripts/audit-daily-materials-signals.js --since-msk "2026-06-30 03:00"
 *
 * This script does not delete, reprocess, backfill, generate, publish, or call provider/model endpoints.
 */

const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const options = {
  baseUrl: 'https://neuroinfogrinder.latrdev.ru',
  sinceMsk: null,
  outDir: 'reports',
  pageSize: 100,
  skipMaterialDetails: false,
  skipSignalDetails: false,
  rawLimit: 1000,
};

for (let i = 0; i < args.length; i += 1) {
  const arg = args[i];
  if (arg === '--base-url') options.baseUrl = args[++i];
  else if (arg === '--since-msk') options.sinceMsk = args[++i];
  else if (arg === '--out-dir') options.outDir = args[++i];
  else if (arg === '--page-size') options.pageSize = Number(args[++i]);
  else if (arg === '--skip-material-details') options.skipMaterialDetails = true;
  else if (arg === '--skip-signal-details') options.skipSignalDetails = true;
  else if (arg === '--raw-limit') options.rawLimit = Number(args[++i]);
  else if (arg === '--help' || arg === '-h') {
    console.log('Usage: node scripts/audit-daily-materials-signals.js [--since-msk "YYYY-MM-DD HH:mm"] [--base-url URL] [--out-dir reports]');
    process.exit(0);
  }
}

const RULES_MARKERS = [
  'приветствуем вас в группе',
  'правила группы',
  'ознакомились с правилами',
  'подтвердите',
  '#whois',
  '!report',
  'закрепленными сообщениями',
  'закреплёнными сообщениями',
  'chatkeeperbot',
  'russian it in dubai',
  'памятка для',
];

const RISK_MARKERS = [
  'ref=',
  'ref_',
  'start=ref',
  'affiliate',
  'рефера',
  'рефок',
  'обход',
  'bypass',
  'заабузить доступ',
  'абуз',
  'bin',
  'cvv',
  'sms activate',
  'uncensored',
  'без цензуры',
  'слив метода',
  'накрут',
  'промокод',
  'накрутка',
  'бесконечную подп',
];

const MODERATION_MARKERS = [
  'lols ban',
  'тебя заблокировали',
  'заблокировал',
  'бан',
  'разбан',
  'mute',
  'предупреждение',
];

const MODEL_CLAIM_MARKERS = [
  'sonnet 5',
  'claude fable',
  'claude mythos',
  'opus 4.8',
  'gpt-5.5',
  'gpt 5.5',
  'whatllm',
  'api-цен',
  'цены sonnet',
  'заявленные параметры',
  'доступности opus',
];

const EVENT_MARKERS = [
  'доклад',
  'митап',
  'вебинар',
  'конференц',
  'мероприят',
  'маршрут',
  'экскурс',
  'по средам',
];

const JOB_MARKERS = [
  'ваканси',
  'резюме',
  'hr',
  'офер',
  'зарплат',
  'вилка',
  'директор по маркетингу',
];

const REJECT_SHAPED_TITLES = [
  'недостаточно материала',
  'недостаточно данных',
  'вежливый отказ',
  'помощь с техническими задачами в конструктивном формате',
];

function parseSinceMsk(value) {
  if (value) {
    const normalized = value.trim().replace(' ', 'T');
    return new Date(`${normalized.length === 16 ? `${normalized}:00` : normalized}+03:00`);
  }
  const now = new Date();
  const msk = new Date(now.getTime() + 3 * 60 * 60 * 1000);
  const y = msk.getUTCFullYear();
  const m = String(msk.getUTCMonth() + 1).padStart(2, '0');
  const d = String(msk.getUTCDate()).padStart(2, '0');
  return new Date(`${y}-${m}-${d}T03:00:00+03:00`);
}

function toMskString(date) {
  if (!date || Number.isNaN(date.getTime())) return null;
  const msk = new Date(date.getTime() + 3 * 60 * 60 * 1000);
  return `${msk.getUTCFullYear()}-${String(msk.getUTCMonth() + 1).padStart(2, '0')}-${String(msk.getUTCDate()).padStart(2, '0')} ${String(msk.getUTCHours()).padStart(2, '0')}:${String(msk.getUTCMinutes()).padStart(2, '0')}:${String(msk.getUTCSeconds()).padStart(2, '0')} +03:00`;
}

function asArray(value) {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

function safeText(...parts) {
  return parts.flatMap((part) => asArray(part)).filter((part) => part !== null && part !== undefined && String(part).trim() !== '').map(String).join('\n');
}

function containsAny(text, markers) {
  const lower = String(text || '').toLowerCase();
  return markers.some((marker) => lower.includes(marker.toLowerCase()));
}

function firstUrlDomain(text) {
  const match = String(text || '').match(/https?:\/\/(?:www\.)?([^\s/?#]+)/i);
  return match ? match[1].toLowerCase() : '';
}

function isSocialDomain(domain) {
  return /(^|\.)(youtube\.com|youtu\.be|tiktok\.com|instagram\.com|x\.com|twitter\.com|vk\.com|reddit\.com)$/i.test(domain);
}

function isTelegramDomain(domain) {
  return /(^|\.)(t\.me|telegram\.me|telegram\.dog)$/i.test(domain);
}

function isResourceDomain(domain) {
  return /(^|\.)(github\.com|gitlab\.com|huggingface\.co)$/i.test(domain) || domain.startsWith('docs.');
}

function addFlag(flags, code, severity, message) {
  flags.push({ code, severity, message });
}

async function getJson(url) {
  const response = await fetch(url, { headers: { accept: 'application/json' } });
  if (!response.ok) throw new Error(`GET ${url} failed: ${response.status}`);
  return response.json();
}

async function getAllMaterials() {
  const all = [];
  for (let page = 0; page < 200; page += 1) {
    const response = await getJson(`${options.baseUrl}/api/v1/materials?page=${page}&size=${options.pageSize}`);
    const content = asArray(response.content);
    all.push(...content);
    if (response.last === true || content.length === 0) break;
  }
  return all;
}

async function getAllSignals() {
  const topicsResponse = await getJson(`${options.baseUrl}/api/v1/topics`);
  const topics = asArray(topicsResponse.content || topicsResponse);
  const byId = new Map();

  for (const topic of topics) {
    if (!topic.slug) continue;
    const topicSignals = await getJson(`${options.baseUrl}/api/v1/topics/${topic.slug}?tab=signals&size=${options.pageSize}`);
    for (const signal of asArray(topicSignals.signals)) {
      if (!signal.id) continue;
      const key = String(signal.id);
      if (!byId.has(key)) {
        let detail = signal;
        if (!options.skipSignalDetails) {
          try { detail = await getJson(`${options.baseUrl}/api/v1/signals/${key}`); } catch { detail = signal; }
        }
        byId.set(key, { signal, detail, topics: [] });
      }
      byId.get(key).topics.push(topic.slug);
    }
  }

  return [...byId.values()];
}

function countByTitle(rows, titleSelector) {
  const counts = new Map();
  for (const row of rows) {
    const title = String(titleSelector(row) || '');
    counts.set(title, (counts.get(title) || 0) + 1);
  }
  return counts;
}

function materialFlags(material, detail, duplicateTitleCounts) {
  const flags = [];
  const sourceMessages = asArray(detail.sourceMessages);
  const sourceText = safeText(...sourceMessages.flatMap((source) => [source.text, source.preview, source.senderUsername, source.senderDisplayName]));
  const text = safeText(material.title, material.contentTitle, material.topicLabel, material.contentSummary, detail.content, detail.contentMarkdown, sourceText);
  const sourceCount = Number(material.sourceCount || 0);

  if (sourceCount <= 1) addFlag(flags, 'SINGLE_SOURCE_MATERIAL', 'P1', 'Material is based on one source message.');
  if (material.publicationKind === 'SINGLE_MESSAGE' || detail.candidateType === 'SINGLE_MESSAGE') addFlag(flags, 'SINGLE_MESSAGE_MATERIAL', 'P1', 'Material used the single-message path.');
  if (containsAny(text, RULES_MARKERS)) addFlag(flags, 'RULES_ONBOARDING_MATERIAL', 'P0', 'Rules/welcome/onboarding text became a material.');
  if (containsAny(text, REJECT_SHAPED_TITLES)) addFlag(flags, 'REJECT_SHAPED_MATERIAL', 'P0', 'Provider/generated title says there was not enough material or no constructive answer.');
  if (material.contentType === 'OTHER' && sourceCount <= 1) addFlag(flags, 'OTHER_SINGLE_SOURCE', 'P2', 'OTHER material has only one source; likely weak knowledge value.');
  if (containsAny(text, RISK_MARKERS)) addFlag(flags, 'RISK_PROMO_REFERRAL_REVIEW', 'P1', 'Risk/promo/referral markers found in material or source.');
  if (containsAny(text, MODEL_CLAIM_MARKERS) && sourceCount <= 1) addFlag(flags, 'UNVERIFIED_MODEL_PRICING_CLAIM', 'P1', 'Model/pricing/availability claim has only one source and needs official-source verification.');
  if (containsAny(text, EVENT_MARKERS) && sourceCount <= 1) addFlag(flags, 'EVENT_ANNOUNCEMENT_SINGLE_SOURCE', 'P2', 'Event announcement is not durable knowledge without aggregation or enrichment.');
  if (containsAny(text, JOB_MARKERS) && sourceCount <= 1) addFlag(flags, 'JOB_POST_SINGLE_SOURCE', 'P2', 'Job/career post is not durable knowledge without aggregation.');
  if ((duplicateTitleCounts.get(String(material.title || '')) || 0) > 1) addFlag(flags, 'DUPLICATE_TITLE_IN_WINDOW', 'P1', 'Same material title appears multiple times in the audit window.');
  if (asArray(detail.possibleDuplicateIds).length > 0) addFlag(flags, 'POSSIBLE_DUPLICATE_IDS', 'P2', 'Material API reports possible duplicates.');
  if (asArray(detail.providerCalls).some((call) => call.stage === 'KNOWLEDGE_GENERATION') && sourceCount <= 1) addFlag(flags, 'LLM_SPENT_ON_SINGLE_SOURCE', 'P2', 'Knowledge generation call was spent on one-message evidence.');

  return flags;
}

function materialIssueGroup(row) {
  const codes = new Set(row.flags.map((flag) => flag.code));
  if (codes.has('RULES_ONBOARDING_MATERIAL')) return 'P0_RULES_ONBOARDING_DELETE';
  if (codes.has('REJECT_SHAPED_MATERIAL')) return 'P0_REJECT_SHAPED_DELETE';
  if (codes.has('RISK_PROMO_REFERRAL_REVIEW')) return 'P1_RISK_MANUAL_REVIEW';
  if (codes.has('UNVERIFIED_MODEL_PRICING_CLAIM')) return 'P1_UNVERIFIED_MODEL_CLAIM';
  if (codes.has('JOB_POST_SINGLE_SOURCE')) return 'P2_JOB_POST_NOT_MATERIAL';
  if (codes.has('EVENT_ANNOUNCEMENT_SINGLE_SOURCE')) return 'P2_EVENT_NOT_MATERIAL';
  if (codes.has('SINGLE_MESSAGE_MATERIAL') && codes.has('OTHER_SINGLE_SOURCE')) return 'P2_WEAK_OTHER_SINGLE_SOURCE';
  if (codes.has('SINGLE_MESSAGE_MATERIAL')) return 'P2_POSSIBLY_USEFUL_BUT_SINGLE_SOURCE';
  return 'KEEP_OR_NO_FLAGS';
}

function signalFlags(entry, duplicateTitleCounts) {
  const flags = [];
  const signal = entry.signal;
  const detail = entry.detail;
  const text = safeText(signal.title, signal.summary, signal.text, signal.preview, detail.title, detail.summary, detail.text, detail.preview, detail.reason);

  if (containsAny(text, RULES_MARKERS)) addFlag(flags, 'RULES_ONBOARDING_SIGNAL', 'P0', 'Rules/welcome/onboarding text became a signal.');
  if (containsAny(text, RISK_MARKERS)) addFlag(flags, 'RISK_PROMO_REFERRAL_SIGNAL', 'P1', 'Risk/promo/referral markers found in signal.');
  if ((duplicateTitleCounts.get(String(signal.title || '')) || 0) > 1) addFlag(flags, 'DUPLICATE_SIGNAL_TITLE_IN_WINDOW', 'P1', 'Same signal title appears multiple times in the audit window.');
  if (signal.rawId == null && detail.rawId == null) addFlag(flags, 'NO_RAW_ID_VISIBLE', 'P2', 'Signal does not expose rawId in API response.');

  return flags;
}

async function getRawMessages() {
  try {
    const response = await getJson(`${options.baseUrl}/api/v2/pipeline/live/stages/telegram_ingest/messages?status=all&limit=${options.rawLimit}`);
    return asArray(response);
  } catch (error) {
    return [];
  }
}

function classifyRawMessage(message) {
  const text = safeText(message.text, message.caption, message.chatTitle, message.topicTitle).toLowerCase();
  const contentType = String(message.contentType || '');
  const domain = firstUrlDomain(text);
  const labels = [];
  let primaryClass = 'LOW_VALUE_CHATTER';
  let materialReadiness = 'NO_MATERIAL';
  let risk = 'LOW';

  if (containsAny(text, RULES_MARKERS)) {
    primaryClass = 'RULES_ONBOARDING';
    materialReadiness = 'HARD_NO_MATERIAL';
    labels.push('rules_onboarding');
  } else if (containsAny(text, MODERATION_MARKERS)) {
    primaryClass = 'MODERATION_BOT_EVENT';
    materialReadiness = 'HARD_NO_MATERIAL';
    labels.push('moderation_bot_event');
  } else if (containsAny(text, RISK_MARKERS)) {
    primaryClass = domain ? 'REFERRAL_OR_INVITE_LINK' : 'RISK_PROMO_REFERRAL';
    materialReadiness = 'SIGNAL_ONLY_RISK_REVIEW';
    risk = 'HIGH';
    labels.push('risk_promo_referral');
  } else if (containsAny(text, MODEL_CLAIM_MARKERS)) {
    primaryClass = 'MODEL_RUMOR_OR_PRICING_CLAIM';
    materialReadiness = 'RETAIN_CONTEXT_UNTIL_OFFICIAL_SOURCE';
    risk = 'MEDIUM';
    labels.push('model_claim');
  } else if (containsAny(text, JOB_MARKERS) && !containsAny(text, ['собеседован', 'mvp', 'спроектировать'])) {
    primaryClass = 'CAREER_JOB_POST';
    materialReadiness = 'RETAIN_CONTEXT_ONLY';
    labels.push('job_post');
  } else if (containsAny(text, EVENT_MARKERS)) {
    primaryClass = 'EVENT_ANNOUNCEMENT';
    materialReadiness = 'RETAIN_CONTEXT_ONLY';
    labels.push('event_announcement');
  } else if (/https?:\/\//i.test(text) || text.includes('github.com')) {
    if (isTelegramDomain(domain)) primaryClass = 'INTERNAL_TELEGRAM_LINK';
    else if (isSocialDomain(domain)) primaryClass = 'SOCIAL_MEDIA_LINK';
    else if (isResourceDomain(domain)) primaryClass = 'RESOURCE_LINK';
    else primaryClass = 'LINK_SHARE';
    materialReadiness = 'NEEDS_LINK_ENRICHMENT';
    labels.push('link');
  } else if (containsAny(text, ['api', 'модель', 'model', 'gpt', 'claude', 'router', 'quota', 'лимит', 'pricing', 'токен'])) {
    primaryClass = 'TECH_SIGNAL';
    materialReadiness = 'WEAK_SIGNAL';
    labels.push('tech_signal');
  } else if (containsAny(text, ['ошибка', '429', '500', 'упал', 'не работает', 'outage', 'limit', 'rate limit'])) {
    primaryClass = 'OUTAGE_STATUS';
    materialReadiness = 'DISCUSSION_OR_SIGNAL';
    labels.push('status');
  } else if (contentType && contentType !== 'MessageText') {
    primaryClass = 'MEDIA_ONLY_OR_WEAK_CONTEXT';
    materialReadiness = 'RETAIN_CONTEXT_ONLY';
    labels.push('media');
  }

  if (String(message.text || message.caption || '').trim().length < 12) labels.push('short');

  return { primaryClass, materialReadiness, risk, labels };
}

function rawClusterKey(row) {
  const chat = String(row.chatTitle || 'unknown-chat').toLowerCase();
  const topic = String(row.topicTitle || row.messageThreadId || 'main').toLowerCase();
  const cls = row.classification.primaryClass;
  const text = safeText(row.text, row.caption).toLowerCase();
  const domain = firstUrlDomain(text);
  const signature = topic === 'main' || topic === '' ? text.split(/\s+/).filter((token) => token.length >= 4).slice(0, 3).join('-') : '';
  return `${cls}|${chat}|${topic}|${domain}|${signature}`;
}

function buildDailyClusters(rawRows) {
  const groups = new Map();
  for (const row of rawRows) {
    const key = rawClusterKey(row);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(row);
  }
  return [...groups.entries()].map(([key, rows], index) => {
    const classes = [...new Set(rows.map((row) => row.classification.primaryClass))];
    const risks = [...new Set(rows.map((row) => row.classification.risk))];
    const sourceChats = [...new Set(rows.map((row) => row.chatTitle).filter(Boolean))];
    const sourceTopics = [...new Set(rows.map((row) => row.topicTitle).filter(Boolean))];
    const materialPotential = rows.some((row) => ['TECH_SIGNAL', 'OUTAGE_STATUS'].includes(row.classification.primaryClass))
      ? 'REVIEW_SIGNAL_OR_DISCUSSION'
      : rows.some((row) => ['LINK_SHARE', 'RESOURCE_LINK', 'SOCIAL_MEDIA_LINK', 'INTERNAL_TELEGRAM_LINK'].includes(row.classification.primaryClass))
        ? 'NEEDS_LINK_ENRICHMENT'
      : 'NO_MATERIAL';
    return {
      id: `daily-cluster-${index + 1}`,
      key,
      size: rows.length,
      classes,
      risks,
      sourceChats,
      sourceTopics,
      materialPotential,
      sampleMessageIds: rows.slice(0, 10).map((row) => row.id),
      sampleText: rows.slice(0, 3).map((row) => safeText(row.text, row.caption).slice(0, 180)),
      messages: rows.slice(0, 40).map((row) => ({
        id: row.id,
        createdAtMsk: row.createdAtMsk,
        chatTitle: row.chatTitle,
        topicTitle: row.topicTitle,
        contentType: row.contentType,
        primaryClass: row.classification.primaryClass,
        materialReadiness: row.classification.materialReadiness,
        risk: row.classification.risk,
        preview: safeText(row.text, row.caption).slice(0, 260),
      })),
    };
  }).sort((a, b) => b.size - a.size || a.key.localeCompare(b.key));
}

function recommendMaterialAction(row) {
  const codes = new Set(row.flags.map((flag) => flag.code));
  if (codes.has('RULES_ONBOARDING_MATERIAL')) return { action: 'SOFT_DELETE_CANDIDATE', severity: 'P0', reason: 'Rules/onboarding content should not be a knowledge material.' };
  if (codes.has('REJECT_SHAPED_MATERIAL')) return { action: 'SOFT_DELETE_CANDIDATE', severity: 'P0', reason: 'Generated title indicates the material itself is a rejection/insufficient-data artifact.' };
  if (codes.has('RISK_PROMO_REFERRAL_REVIEW')) return { action: 'MANUAL_REVIEW_REQUIRED', severity: 'P1', reason: 'Risk/promo/referral markers require review before keeping material.' };
  if (codes.has('UNVERIFIED_MODEL_PRICING_CLAIM')) return { action: 'VERIFY_OFFICIAL_SOURCE_OR_DELETE', severity: 'P1', reason: 'Model/pricing claim needs official-source verification before materialization.' };
  if (codes.has('JOB_POST_SINGLE_SOURCE') || codes.has('EVENT_ANNOUNCEMENT_SINGLE_SOURCE')) return { action: 'SOFT_DELETE_OR_KEEP_AS_SIGNAL_ONLY', severity: 'P2', reason: 'Announcement/post is weak as durable material without enrichment.' };
  if (codes.has('SINGLE_MESSAGE_MATERIAL') && codes.has('OTHER_SINGLE_SOURCE')) return { action: 'SOFT_DELETE_OR_REPROCESS_CANDIDATE', severity: 'P1', reason: 'Weak OTHER single-message draft.' };
  if (codes.has('SINGLE_MESSAGE_MATERIAL')) return { action: 'REVIEW_FOR_MULTI_SOURCE_REPROCESS', severity: 'P2', reason: 'One-message draft should be checked against nearby context.' };
  return { action: 'KEEP', severity: 'INFO', reason: 'No blocking quality flag.' };
}

function countByIssueGroup(rows) {
  const counts = new Map();
  for (const row of rows) counts.set(row.issueGroup, (counts.get(row.issueGroup) || 0) + 1);
  return [...counts.entries()].map(([group, count]) => ({ group, count })).sort((a, b) => b.count - a.count || a.group.localeCompare(b.group));
}

function flagSummary(rows) {
  const severityRank = { P0: 0, P1: 1, P2: 2, P3: 3 };
  const byCode = new Map();
  for (const row of rows) {
    for (const flag of asArray(row.flags)) {
      if (!byCode.has(flag.code)) byCode.set(flag.code, { code: flag.code, count: 0, maxSeverity: flag.severity });
      const item = byCode.get(flag.code);
      item.count += 1;
      if (severityRank[flag.severity] < severityRank[item.maxSeverity]) item.maxSeverity = flag.severity;
    }
  }
  return [...byCode.values()].sort((a, b) => severityRank[a.maxSeverity] - severityRank[b.maxSeverity] || a.code.localeCompare(b.code));
}

function tableEscape(value) {
  return String(value ?? '').replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}

function renderMarkdown(snapshot) {
  const lines = [];
  lines.push('# Daily Materials/Signals Audit');
  lines.push('');
  lines.push(`- Generated UTC: \`${snapshot.generatedAtUtc}\``);
  lines.push(`- Window start MSK: \`${snapshot.sinceMsk}\``);
  lines.push(`- Window start UTC: \`${snapshot.sinceUtc}\``);
  lines.push(`- Base URL: \`${snapshot.baseUrl}\``);
  lines.push('- Mode: read-only public API collection; no deletes, reprocess, generation, publish, or provider calls.');
  lines.push('');
  lines.push('## Summary');
  lines.push('');
  lines.push('| Area | Count | Flagged |');
  lines.push('|---|---:|---:|');
  lines.push(`| Materials | ${snapshot.counts.materialsInWindow} | ${snapshot.counts.flaggedMaterials} |`);
  lines.push(`| Signals | ${snapshot.counts.signalsInWindow} | ${snapshot.counts.flaggedSignals} |`);
  lines.push(`| Raw messages | ${snapshot.counts.rawMessagesInWindow} | ${snapshot.counts.rawClusters} clusters |`);
  lines.push('');
  lines.push('## Material Flag Summary');
  lines.push('');
  lines.push('| Flag | Severity | Count |');
  lines.push('|---|---|---:|');
  if (snapshot.materialFlagSummary.length === 0) lines.push('| none | - | 0 |');
  for (const flag of snapshot.materialFlagSummary) lines.push(`| ${flag.code} | ${flag.maxSeverity} | ${flag.count} |`);
  lines.push('');
  lines.push('## Material Issue Groups');
  lines.push('');
  lines.push('| Issue Group | Count |');
  lines.push('|---|---:|');
  if (snapshot.materialIssueSummary.length === 0) lines.push('| none | 0 |');
  for (const item of snapshot.materialIssueSummary) lines.push(`| ${item.group} | ${item.count} |`);
  lines.push('');
  lines.push('## Flagged Materials');
  lines.push('');
  lines.push('| ID | Created MSK | Issue Group | Type | Sources | Title | Flags |');
  lines.push('|---:|---|---|---|---:|---|---|');
  const flaggedMaterials = snapshot.materials.filter((row) => row.flags.length > 0).sort((a, b) => String(b.createdAtUtc).localeCompare(String(a.createdAtUtc)));
  if (flaggedMaterials.length === 0) lines.push('| - | - | - | - | no flagged materials | - |');
  for (const row of flaggedMaterials) {
    const flags = row.flags.map((flag) => `${flag.severity}:${flag.code}`).join(', ');
    lines.push(`| ${row.id} | ${row.createdAtMsk} | ${row.issueGroup} | ${row.contentType}/${row.publicationKind} | ${row.sourceCount} | ${tableEscape(row.title)} | ${flags}; action=${row.recommendedAction.action} |`);
  }
  lines.push('');
  lines.push('## Signal Flag Summary');
  lines.push('');
  lines.push('| Flag | Severity | Count |');
  lines.push('|---|---|---:|');
  if (snapshot.signalFlagSummary.length === 0) lines.push('| none | - | 0 |');
  for (const flag of snapshot.signalFlagSummary) lines.push(`| ${flag.code} | ${flag.maxSeverity} | ${flag.count} |`);
  lines.push('');
  lines.push('## Flagged Signals');
  lines.push('');
  lines.push('| ID | Created MSK | Topics | Title | Flags |');
  lines.push('|---:|---|---|---|---|');
  const flaggedSignals = snapshot.signals.filter((row) => row.flags.length > 0).sort((a, b) => String(b.createdAtUtc).localeCompare(String(a.createdAtUtc)));
  if (flaggedSignals.length === 0) lines.push('| - | - | - | no flagged signals | - |');
  for (const row of flaggedSignals) {
    const flags = row.flags.map((flag) => `${flag.severity}:${flag.code}`).join(', ');
    lines.push(`| ${row.id} | ${row.createdAtMsk} | ${tableEscape(row.topics.join(', '))} | ${tableEscape(row.title)} | ${flags} |`);
  }
  lines.push('');
  lines.push('## All Materials In Window');
  lines.push('');
  lines.push('| ID | Created MSK | Type | Sources | Title |');
  lines.push('|---:|---|---|---:|---|');
  for (const row of snapshot.materials.slice().sort((a, b) => String(b.createdAtUtc).localeCompare(String(a.createdAtUtc)))) {
    lines.push(`| ${row.id} | ${row.createdAtMsk} | ${row.contentType}/${row.publicationKind} | ${row.sourceCount} | ${tableEscape(row.title)} |`);
  }
  lines.push('');
  lines.push('## Flagged Material Source Samples');
  lines.push('');
  for (const row of flaggedMaterials.slice(0, 40)) {
    lines.push(`### ${row.id} · ${row.issueGroup} · ${row.title}`);
    lines.push('');
    lines.push(`- Action: \`${row.recommendedAction.action}\``);
    lines.push(`- Content preview: ${tableEscape(row.contentPreview || '').slice(0, 500) || 'n/a'}`);
    lines.push('');
    lines.push('| Raw ID | Chat | Sender | Source Preview |');
    lines.push('|---:|---|---|---|');
    if (!row.sourcePreviews || row.sourcePreviews.length === 0) lines.push('| - | - | - | no source preview visible |');
    for (const source of row.sourcePreviews || []) {
      lines.push(`| ${source.rawId ?? '-'} | ${tableEscape(source.chatTitle || '')} | ${tableEscape(source.sender || '')} | ${tableEscape(source.preview || '')} |`);
    }
    lines.push('');
  }
  lines.push('## Daily Raw Classification Summary');
  lines.push('');
  lines.push('| Class | Count |');
  lines.push('|---|---:|');
  for (const item of snapshot.rawClassificationSummary) lines.push(`| ${item.className} | ${item.count} |`);
  lines.push('');
  lines.push('## Daily Deterministic Clusters');
  lines.push('');
  lines.push('| Cluster | Size | Classes | Potential | Chats | Sample IDs |');
  lines.push('|---|---:|---|---|---|---|');
  for (const cluster of snapshot.rawClusters.slice(0, 30)) {
    lines.push(`| ${cluster.id} | ${cluster.size} | ${tableEscape(cluster.classes.join(', '))} | ${cluster.materialPotential} | ${tableEscape(cluster.sourceChats.join(', '))} | ${cluster.sampleMessageIds.join(', ')} |`);
  }
  lines.push('');
  lines.push('## Cluster Message Samples');
  lines.push('');
  for (const cluster of snapshot.rawClusters.slice(0, 20)) {
    lines.push(`### ${cluster.id} · ${cluster.size} messages · ${cluster.materialPotential}`);
    lines.push('');
    lines.push('| Message ID | Time MSK | Chat | Class | Readiness | Preview |');
    lines.push('|---:|---|---|---|---|---|');
    for (const message of cluster.messages.slice(0, 20)) {
      lines.push(`| ${message.id} | ${message.createdAtMsk} | ${tableEscape(message.chatTitle)} | ${message.primaryClass} | ${message.materialReadiness} | ${tableEscape(message.preview)} |`);
    }
    lines.push('');
  }
  lines.push('');
  return `${lines.join('\n')}\n`;
}

function renderHtml(snapshot) {
  const esc = (value) => String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const flaggedMaterials = snapshot.materials.filter((row) => row.flags.length > 0);
  const flaggedSignals = snapshot.signals.filter((row) => row.flags.length > 0);
  return `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <title>NeuroInfoGrinder Daily Audit</title>
  <style>
    :root { color-scheme: light dark; --bg:#0b1020; --card:#141b2f; --text:#e9eefc; --muted:#9fb0d0; --p0:#ff5c7a; --p1:#ffb454; --p2:#7cc7ff; --ok:#7ee787; }
    body { margin:0; font-family: Inter, Segoe UI, Arial, sans-serif; background:linear-gradient(135deg,#0b1020,#18213a); color:var(--text); }
    header { padding:32px 40px 18px; }
    h1 { margin:0; font-size:32px; letter-spacing:-0.03em; }
    .subtitle { color:var(--muted); margin-top:8px; }
    .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:16px; padding:0 40px 24px; }
    .card { background:rgba(20,27,47,.88); border:1px solid rgba(255,255,255,.08); border-radius:18px; padding:18px; box-shadow:0 18px 60px rgba(0,0,0,.25); }
    .metric { font-size:34px; font-weight:750; }
    .label { color:var(--muted); font-size:13px; text-transform:uppercase; letter-spacing:.08em; }
    main { padding:0 40px 44px; }
    table { width:100%; border-collapse:collapse; margin-top:12px; overflow:hidden; border-radius:14px; }
    th,td { padding:10px 12px; border-bottom:1px solid rgba(255,255,255,.08); vertical-align:top; }
    th { text-align:left; color:#c9d7f5; font-size:12px; text-transform:uppercase; letter-spacing:.06em; }
    tr:hover { background:rgba(255,255,255,.035); }
    .pill { display:inline-block; border-radius:999px; padding:3px 8px; margin:2px; font-size:12px; background:#263250; color:#d6e2ff; }
    .p0 { background:rgba(255,92,122,.18); color:#ff99ad; }
    .p1 { background:rgba(255,180,84,.16); color:#ffd29b; }
    .p2 { background:rgba(124,199,255,.16); color:#a8dcff; }
    .action { color:#ffd29b; font-weight:650; }
    section { margin-top:24px; }
    details { margin:10px 0; border:1px solid rgba(255,255,255,.08); border-radius:14px; padding:10px 12px; background:rgba(255,255,255,.025); }
    summary { cursor:pointer; font-weight:700; color:#dbe7ff; }
    .preview { color:#dbe7ff; max-width:680px; }
    .muted { color:var(--muted); }
  </style>
</head>
<body>
  <header>
    <h1>NeuroInfoGrinder Daily Audit</h1>
    <div class="subtitle">Window: ${esc(snapshot.sinceMsk)} · Generated: ${esc(snapshot.generatedAtUtc)} · read-only</div>
  </header>
  <div class="grid">
    <div class="card"><div class="label">Materials</div><div class="metric">${snapshot.counts.materialsInWindow}</div><div>${snapshot.counts.flaggedMaterials} flagged</div></div>
    <div class="card"><div class="label">Signals</div><div class="metric">${snapshot.counts.signalsInWindow}</div><div>${snapshot.counts.flaggedSignals} flagged</div></div>
    <div class="card"><div class="label">Raw Messages</div><div class="metric">${snapshot.counts.rawMessagesInWindow}</div><div>${snapshot.counts.rawClusters} deterministic clusters</div></div>
    <div class="card"><div class="label">Top Issue</div><div class="metric">${esc(snapshot.materialIssueSummary[0]?.group || snapshot.materialFlagSummary[0]?.code || 'none')}</div><div>${snapshot.materialIssueSummary[0]?.count || snapshot.materialFlagSummary[0]?.count || 0} hits</div></div>
  </div>
  <main>
    <section class="card"><h2>Material Issue Groups</h2><table><thead><tr><th>Group</th><th>Count</th></tr></thead><tbody>
      ${snapshot.materialIssueSummary.map((item) => `<tr><td>${esc(item.group)}</td><td>${item.count}</td></tr>`).join('\n')}
    </tbody></table></section>
    <section class="card"><h2>Flagged Materials</h2><table><thead><tr><th>ID</th><th>Created</th><th>Issue</th><th>Title</th><th>Flags</th><th>Action</th></tr></thead><tbody>
      ${flaggedMaterials.map((row) => `<tr><td>${row.id}</td><td>${esc(row.createdAtMsk)}</td><td>${esc(row.issueGroup)}</td><td>${esc(row.title)}</td><td>${row.flags.map((f) => `<span class="pill ${String(f.severity).toLowerCase()}">${esc(f.severity)}:${esc(f.code)}</span>`).join('')}</td><td><span class="action">${esc(row.recommendedAction.action)}</span><br><small>${esc(row.recommendedAction.reason)}</small></td></tr>`).join('\n')}
    </tbody></table></section>
    <section class="card"><h2>Flagged Signals</h2><table><thead><tr><th>ID</th><th>Created</th><th>Title</th><th>Topics</th><th>Flags</th></tr></thead><tbody>
      ${flaggedSignals.map((row) => `<tr><td>${row.id}</td><td>${esc(row.createdAtMsk)}</td><td>${esc(row.title)}</td><td>${esc(row.topics.join(', '))}</td><td>${row.flags.map((f) => `<span class="pill ${String(f.severity).toLowerCase()}">${esc(f.severity)}:${esc(f.code)}</span>`).join('')}</td></tr>`).join('\n')}
    </tbody></table></section>
    <section class="card"><h2>Daily Raw Clusters</h2><table><thead><tr><th>Cluster</th><th>Size</th><th>Classes</th><th>Potential</th><th>Chats</th><th>Sample IDs</th></tr></thead><tbody>
      ${snapshot.rawClusters.slice(0, 30).map((cluster) => `<tr><td>${esc(cluster.id)}</td><td>${cluster.size}</td><td>${esc(cluster.classes.join(', '))}</td><td>${esc(cluster.materialPotential)}</td><td>${esc(cluster.sourceChats.join(', '))}</td><td>${esc(cluster.sampleMessageIds.join(', '))}</td></tr>`).join('\n')}
    </tbody></table></section>
    <section class="card"><h2>Cluster Message Samples</h2>
      ${snapshot.rawClusters.slice(0, 20).map((cluster) => `<details ${cluster.materialPotential !== 'NO_MATERIAL' ? 'open' : ''}><summary>${esc(cluster.id)} · ${cluster.size} messages · ${esc(cluster.materialPotential)} · ${esc(cluster.sourceChats.join(', '))}</summary><table><thead><tr><th>ID</th><th>Time</th><th>Chat</th><th>Class</th><th>Readiness</th><th>Preview</th></tr></thead><tbody>${cluster.messages.slice(0, 20).map((message) => `<tr><td>${message.id}</td><td>${esc(message.createdAtMsk)}</td><td>${esc(message.chatTitle || '')}<br><small class="muted">${esc(message.topicTitle || '')}</small></td><td>${esc(message.primaryClass)}</td><td>${esc(message.materialReadiness)}</td><td class="preview">${esc(message.preview)}</td></tr>`).join('')}</tbody></table></details>`).join('\n')}
    </section>
  </main>
</body>
</html>`;
}

async function main() {
  const since = parseSinceMsk(options.sinceMsk);
  const sinceUtcMs = since.getTime();
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');

  if (!fs.existsSync(options.outDir)) fs.mkdirSync(options.outDir, { recursive: true });

  const allMaterials = await getAllMaterials();
  const windowMaterials = allMaterials.filter((item) => item.createdAt && new Date(item.createdAt).getTime() >= sinceUtcMs);
  const materialTitleCounts = countByTitle(windowMaterials, (item) => item.title);
  const materialRows = [];

  for (const material of windowMaterials) {
    let detail = material;
    if (!options.skipMaterialDetails) {
      try { detail = await getJson(`${options.baseUrl}/api/v1/materials/${material.id}`); } catch { detail = material; }
    }
    const created = material.createdAt ? new Date(material.createdAt) : null;
    materialRows.push({
      id: material.id,
      createdAtUtc: created ? created.toISOString() : null,
      createdAtMsk: toMskString(created),
      title: material.title,
      contentType: material.contentType,
      contentSubtype: material.contentSubtype,
      status: material.status,
      publicationKind: material.publicationKind,
      candidateType: detail.candidateType,
      sourceCount: Number(material.sourceCount || 0),
      runId: detail.run?.id ?? null,
      sourceRawIds: asArray(detail.sourceMessages).map((source) => source.rawId).filter((value) => value != null),
      sourceChats: [...new Set(asArray(detail.sourceMessages).map((source) => source.chatTitle).filter(Boolean))],
      sourceAuthors: [...new Set(asArray(detail.sourceMessages).flatMap((source) => [source.senderUsername, source.senderDisplayName]).filter(Boolean))],
      contentPreview: safeText(detail.content, detail.contentMarkdown, material.contentSummary).slice(0, 700),
      sourcePreviews: asArray(detail.sourceMessages).slice(0, 5).map((source) => ({
        rawId: source.rawId,
        chatTitle: source.chatTitle,
        sender: source.senderUsername || source.senderDisplayName || null,
        preview: safeText(source.text, source.preview).slice(0, 500),
      })),
      flags: materialFlags(material, detail, materialTitleCounts),
    });
  }

  const allSignalEntries = await getAllSignals();
  const windowSignalEntries = allSignalEntries.filter((entry) => {
    const createdAt = entry.signal.createdAt || entry.detail.createdAt;
    return createdAt && new Date(createdAt).getTime() >= sinceUtcMs;
  });
  const signalTitleCounts = countByTitle(windowSignalEntries, (entry) => entry.signal.title);
  const signalRows = windowSignalEntries.map((entry) => {
    const created = new Date(entry.signal.createdAt || entry.detail.createdAt);
    return {
      id: entry.signal.id,
      createdAtUtc: created.toISOString(),
      createdAtMsk: toMskString(created),
      title: entry.signal.title,
      summary: entry.signal.summary,
      rawId: entry.signal.rawId ?? entry.detail.rawId ?? null,
      datasetMessageId: entry.signal.datasetMessageId ?? entry.detail.datasetMessageId ?? null,
      runId: entry.signal.runId ?? entry.detail.runId ?? null,
      topics: entry.topics,
      flags: signalFlags(entry, signalTitleCounts),
    };
  });

  const rawMessages = await getRawMessages();
  const rawRows = rawMessages
    .filter((message) => {
      const createdAt = message.createdAt || message.ingestedAt || message.currentStatusAt;
      return createdAt && new Date(createdAt).getTime() >= sinceUtcMs;
    })
    .map((message) => {
      const created = new Date(message.createdAt || message.ingestedAt || message.currentStatusAt);
      return {
        id: message.id,
        createdAtUtc: created.toISOString(),
        createdAtMsk: toMskString(created),
        accountId: message.accountId,
        telegramChatId: message.telegramChatId,
        telegramMessageId: message.telegramMessageId,
        chatTitle: message.chatTitle,
        topicTitle: message.topicTitle,
        contentType: message.contentType,
        text: message.text,
        caption: message.caption,
        status: message.status,
        classification: classifyRawMessage(message),
      };
    });
  const rawClusters = buildDailyClusters(rawRows);
  const rawClassCounts = countByTitle(rawRows, (row) => row.classification.primaryClass);
  const rawClassificationSummary = [...rawClassCounts.entries()].map(([className, count]) => ({ className, count })).sort((a, b) => b.count - a.count || a.className.localeCompare(b.className));

  for (const row of materialRows) row.recommendedAction = recommendMaterialAction(row);
  for (const row of materialRows) row.issueGroup = materialIssueGroup(row);

  const snapshot = {
    generatedAtUtc: new Date().toISOString(),
    baseUrl: options.baseUrl,
    sinceMsk: toMskString(since),
    sinceUtc: since.toISOString(),
    counts: {
      materialsInWindow: materialRows.length,
      flaggedMaterials: materialRows.filter((row) => row.flags.length > 0).length,
      signalsInWindow: signalRows.length,
      flaggedSignals: signalRows.filter((row) => row.flags.length > 0).length,
      rawMessagesInWindow: rawRows.length,
      rawClusters: rawClusters.length,
    },
    materialFlagSummary: flagSummary(materialRows),
    materialIssueSummary: countByIssueGroup(materialRows),
    signalFlagSummary: flagSummary(signalRows),
    materials: materialRows,
    signals: signalRows,
    rawMessages: rawRows,
    rawClassificationSummary,
    rawClusters,
  };

  const jsonPath = path.join(options.outDir, `daily-materials-signals-audit-${stamp}.json`);
  const mdPath = path.join(options.outDir, `daily-materials-signals-audit-${stamp}.md`);
  const htmlPath = path.join(options.outDir, `daily-materials-signals-audit-${stamp}.html`);
  fs.writeFileSync(jsonPath, `${JSON.stringify(snapshot, null, 2)}\n`, 'utf8');
  fs.writeFileSync(mdPath, renderMarkdown(snapshot), 'utf8');
  fs.writeFileSync(htmlPath, renderHtml(snapshot), 'utf8');

  console.log(`HTML: ${htmlPath}`);
  console.log(`Markdown: ${mdPath}`);
  console.log(`JSON: ${jsonPath}`);
  console.log(`Materials in window: ${snapshot.counts.materialsInWindow}, flagged: ${snapshot.counts.flaggedMaterials}`);
  console.log(`Signals in window: ${snapshot.counts.signalsInWindow}, flagged: ${snapshot.counts.flaggedSignals}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
