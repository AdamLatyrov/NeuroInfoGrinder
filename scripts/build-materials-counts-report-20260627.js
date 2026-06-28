const fs = require('fs');
const path = require('path');

const reports = path.join(__dirname, '..', 'reports');
const readJson = (name) => JSON.parse(fs.readFileSync(path.join(reports, name), 'utf8').replace(/^\uFEFF/, ''));

const before = readJson('materials-counts-audit-raw-20260627.json');
const afterAll = readJson('materials-api-counts-after-20260627.json');
const afterOther = readJson('materials-api-other-after-20260627.json');
const afterGeneration = readJson('materials-api-generation-after-20260627.json');

function csv(value) {
  if (value === null || value === undefined) return '';
  return `"${String(value).replace(/"/g, '""').replace(/\r?\n/g, ' ')}"`;
}

const rows = before.materials.map((m) => ({
  material_id: m.materialId,
  title: m.title,
  type: m.type,
  normalized_type: m.normalizedType,
  status: m.status,
  candidateType: m.candidateType,
  created_at: m.createdAt,
  included_in_total: m.includedInTotal,
  included_in_type_tab: m.includedInTypeTab,
  tab_name: m.tabName,
  notes: m.notes,
}));

const headers = ['material_id','title','type','normalized_type','status','candidateType','created_at','included_in_total','included_in_type_tab','tab_name','notes'];
fs.writeFileSync(
  path.join(reports, 'materials-counts-audit-20260627.csv'),
  [headers.join(','), ...rows.map((r) => headers.map((h) => csv(r[h])).join(','))].join('\n') + '\n',
  'utf8'
);

const typeCounts = afterAll.counts.byType;
const sumVisible = ['GUIDE', 'GENERATION', 'ANSWER', 'SUMMARY', 'OTHER'].reduce((sum, key) => sum + (typeCounts[key] || 0), 0);
const out = {
  capturedAt: new Date().toISOString(),
  rootCause: 'Six active materials used legacy/unknown artifact_type values not represented by visible UI tabs: cluster_summary x3, newsBrief, instruction, REFERENCE.',
  before: before.counts,
  missingMaterialsBeforeFix: before.missingCurrentTypeTabs,
  after: {
    totalElements: afterAll.totalElements,
    counts: afterAll.counts,
    typeTabSum: sumVisible,
    otherFilter: {
      totalElements: afterOther.totalElements,
      ids: afterOther.content.map((x) => x.id),
    },
    generationFilter: {
      totalElements: afterGeneration.totalElements,
    },
  },
  verification: {
    backendTests: 'mvn test: 70 tests, 0 failures',
    targetedBackendTests: 'mvn -Dtest=KnowledgeMaterialServiceTest test: 4 tests, 0 failures',
    frontendBuild: 'npm run build passed',
    deploy: './scripts/deploy-fast.ps1 -Target all completed; backend/frontend healthy; infra remained up',
    publicUrl: 'deploy status returned public URL HTTP 200',
    backendLogs: 'No recent ERROR/WARN/Exception lines in reports/materials-counts-backend-logs-20260627.txt',
    browserCheck: 'Blocked: Kimi WebBridge health returned running=false. API/public verification completed instead.',
  },
  dataPolicy: 'No DB type values were updated. Unknown/null/legacy types are normalized to OTHER at API/UI layer only.',
};
fs.writeFileSync(path.join(reports, 'materials-counts-consistency-20260627.json'), JSON.stringify(out, null, 2), 'utf8');

const missingTable = before.missingCurrentTypeTabs.map((m) => `| ${m.materialId} | ${m.title} | ${m.type ?? ''} | ${m.status} | ${m.candidateType} | ${m.createdAt} | ${m.sourceCount} |`).join('\n');
const md = `# Materials Counts Consistency - 2026-06-27

Status: fixed and deployed. No material rows were deleted, archived, regenerated, edited, or mass-updated.

## Root Cause

The UI showed \`Все 30\`, but visible type tabs summed to \`24\` because six active DRAFT materials had legacy/unknown raw \`artifact_type\` values that were not represented by visible tabs.

Raw active type distribution before fix:

- GUIDE: ${before.counts.byRawType.GUIDE ?? 0}
- guide: ${before.counts.byRawType.guide ?? 0}
- generation: ${before.counts.byRawType.generation ?? 0}
- answer: ${before.counts.byRawType.answer ?? 0}
- SUMMARY: ${before.counts.byRawType.SUMMARY ?? 0}
- cluster_summary: ${before.counts.byRawType.cluster_summary ?? 0}
- newsBrief: ${before.counts.byRawType.newsBrief ?? 0}
- instruction: ${before.counts.byRawType.instruction ?? 0}
- REFERENCE: ${before.counts.byRawType.REFERENCE ?? 0}

## Missing Six Materials

| material_id | title | type | status | candidateType | created_at | sourceCount |
|---:|---|---|---|---|---|---:|
${missingTable}

## Fix

- Backend now normalizes material types for list/count/filtering: \`GUIDE\`, \`GENERATION\`, \`ANSWER\`, \`SUMMARY\`, and \`OTHER\`.
- Unknown/null/legacy values are counted and filtered as \`OTHER\`.
- \`/api/v1/materials\` now returns stable \`counts.total\`, \`counts.byType\`, and \`counts.byStatus\` using the same base filter as the list endpoint.
- Frontend labels are Russian: \`Гайды\`, \`Генерации\`, \`Ответы\`, \`Сводки\`, \`Другое\`.
- \`Другое\` tab is visible only when count > 0.
- English labels \`Generation\` and \`Answer\` were removed from \`/materials\` UI labels.

## Production Verification

API after deploy:

\`/api/v1/materials?page=0&size=1\`

\`totalElements=${afterAll.totalElements}\`

\`counts.byType=${JSON.stringify(afterAll.counts.byType)}\`

Visible type-tab sum: ${sumVisible}

\`/api/v1/materials?contentType=OTHER\` returned \`totalElements=${afterOther.totalElements}\` and ids: ${afterOther.content.map((x) => x.id).join(', ')}.

\`/api/v1/materials?contentType=GENERATION\` returned \`totalElements=${afterGeneration.totalElements}\`.

Status counts use the same base filter: ${JSON.stringify(afterAll.counts.byStatus)}.

## Tests And Deploy

- Targeted backend: \`mvn -Dtest=KnowledgeMaterialServiceTest test\` passed, 4 tests.
- Full backend: \`mvn test\` passed, 70 tests.
- Frontend: \`npm run build\` passed.
- Deploy: \`./scripts/deploy-fast.ps1 -Target all\` completed; backend/frontend healthy, public URL HTTP 200.
- Backend logs: no recent ERROR/WARN/Exception lines.
- Browser check: blocked because Kimi WebBridge health returned \`running=false\`; API/public verification completed.

## Data Repair Recommendation

No DB repair was performed. If later desired, review whether \`cluster_summary\` should become \`SUMMARY\` and whether \`REFERENCE\` should become a first-class tab/type. Do not mass-update without approval.
`;
fs.writeFileSync(path.join(reports, 'materials-counts-consistency-20260627.md'), md, 'utf8');

console.log('materials counts reports generated');
