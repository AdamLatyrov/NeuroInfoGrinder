# Problems Page Implementation Plan

> **For agentic workers:** REQUIRED WORKFLOW: Use the `plan-researcher` agent for planning handoff, then use `csharp-engineer` or `defect-debugger` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current `/signals` screen from an internal "clusters and signals" view into a product-facing "Проблемы людей" page that shows repeated human pains, evidence messages, demand strength, and next actions.

**Architecture:** Keep `/signals` as the route for minimal router churn, but rename the UI/navigation to "Проблемы". Build a frontend view-model layer that converts existing `TopicCandidate` and source-message data into `ProblemCandidate` cards; then optionally enrich the backend read model so evidence rows expose pain/payment/technical fields already stored on messages. Use existing AI/classifier output first; add new live AI synthesis only after the MVP proves useful.

**Tech Stack:** React 18, TypeScript, Vite, TanStack Query, Tailwind utility classes, Spring Boot 3.4, Java 17, JUnit/Mockito/AssertJ, existing NeuroInfoGrinder pipeline APIs.

---

## Current Context

- The current page is `frontend/src/pages/signals/SignalsPage.tsx`.
- The sidebar still labels the route as "Сигналы" in `frontend/src/components/domain/floating-sidebar.tsx`.
- Existing frontend data comes from:
  - `useTopicCandidatesQuery` in `frontend/src/shared/api/pipelineApi.ts`
  - `useSignalCandidatesQuery` in `frontend/src/shared/api/pipelineApi.ts`
  - legacy signal cluster hooks in `frontend/src/shared/api/signalsApi.ts`
- `TopicCandidate` already contains:
  - `topicLabel`, `canonicalSummary`, `guidePotentialScore`, `riskSafetyCategory`, `guideAngles`
  - `sourceMessageIds`, `participants`, `bestEvidenceMessageIds`
  - `sourceMessages` with sanitized message text and basic scores
- `PipelineResultItem` already contains richer problem fields:
  - `problemStatement`, `solutionHint`, `painScore`, `willingnessToPayScore`, `technicalDepthScore`, `mentionedToolsJson`, `mentionedPricesJson`, `mentionedErrorsJson`
- `TopicExplainMessage` currently does not expose the richer pain/payment/problem fields, so the frontend can filter topic candidates by pain/payment but cannot fully display those dimensions inside a topic card yet.
- Frontend has no test runner configured. Verification is `npm run build` plus manual browser validation.
- Backend has no Maven wrapper; verification uses local Maven from `backend`: `mvn test`.

## Product Decisions

Default decisions for implementation unless Adam changes them:

- Keep URL `/signals`; rename the visible page and sidebar item to **Проблемы** or **Проблемы людей**.
- Main tab becomes **Проблемы**.
- Move raw/legacy implementation views behind a less prominent **Техническое** tab.
- Use current classifier/AI fields first. Do not call AI directly from the frontend.
- Do not write to production DB during implementation or validation.
- Do not introduce migrations for the first frontend-only MVP.
- If backend enrichment is implemented, make it a read-only API contract extension using existing `messages` columns.

## UX Target

The page should answer these questions immediately:

- What problem are people repeatedly having?
- How strong is the pain?
- Is there willingness to pay?
- How many people/messages support this?
- What are people trying now?
- What evidence messages prove it?
- What can Adam do next: open messages, create/check a guide, hide/debug?

## Data Shape

Create a frontend-only view model:

```ts
export type ProblemKind =
  | "access"
  | "payment"
  | "limits"
  | "technical"
  | "provider"
  | "safety"
  | "general";

export interface ProblemMetric {
  label: string;
  value: number | null;
  hint: string;
}

export interface ProblemEvidence {
  messageId: number;
  groupId: number;
  date: string;
  senderName: string | null;
  text: string | null;
  problemStatement: string | null;
  solutionHint: string | null;
  painScore: number | null;
  willingnessToPayScore: number | null;
  guidePotentialScore: number | null;
  spamScore: number | null;
}

export interface ProblemCandidate {
  id: string;
  kind: ProblemKind;
  title: string;
  summary: string | null;
  groupLabel: string;
  topicLabel: string | null;
  startAt: string;
  endAt: string;
  messageCount: number;
  participantCount: number;
  guidePotentialScore: number | null;
  painScore: number | null;
  willingnessToPayScore: number | null;
  problemSignalScore: number | null;
  spamScore: number | null;
  riskSafetyCategory: string;
  status: string;
  guideId: number | null;
  guideAngles: string[];
  evidence: ProblemEvidence[];
  currentWorkarounds: string[];
}
```

Score derivation:

- `problemSignalScore`: max `sourceMessages[].problemSignalScore`
- `painScore`: max `sourceMessages[].painScore`; fallback to `problemSignalScore` when backend enrichment is not present
- `willingnessToPayScore`: max `sourceMessages[].willingnessToPayScore`; fallback to `null`
- `guidePotentialScore`: `candidate.guidePotentialScore` or max `sourceMessages[].guidePotentialScore`
- `spamScore`: max `sourceMessages[].spamScore`
- `messageCount`: `candidate.sourceMessageIds.length`
- `participantCount`: `candidate.participants.length`

Kind derivation:

- `payment`: payment score is at least 60, or text contains "оплат", "карта", "сбп", "fanpay", "gift", "пополн".
- `limits`: pain score is at least 60, or text contains "лимит", "безлимит", "quota", "rate limit".
- `access`: text contains "доступ", "аккаунт", "ключ", "api", "provider", "endpoint", "прокси".
- `technical`: text contains "ошибка", "429", "настроить", "код", "repo", "install", "debug".
- `safety`: `riskSafetyCategory` is not `normal`.
- `provider`: text mentions concrete providers/tools but no stronger category wins.
- `general`: fallback.

## Task 1: Create Problem View Model

**Files:**
- Create: `frontend/src/pages/signals/problemViewModel.ts`
- Modify: `frontend/src/shared/api/pipelineApi.ts`

- [ ] **Step 1: Extend frontend source message type for planned backend fields**

In `frontend/src/shared/api/pipelineApi.ts`, add optional nullable fields to `TopicExplainMessage` without changing current behavior:

```ts
  painScore?: number | null;
  urgencyScore?: number | null;
  willingnessToPayScore?: number | null;
  technicalDepthScore?: number | null;
  problemStatement?: string | null;
  solutionHint?: string | null;
  mentionedToolsJson?: string | null;
  mentionedPricesJson?: string | null;
  mentionedErrorsJson?: string | null;
  intelligenceReason?: string | null;
```

- [ ] **Step 2: Create the view-model module**

Create `frontend/src/pages/signals/problemViewModel.ts` with exported types:

- `ProblemKind`
- `ProblemMetric`
- `ProblemEvidence`
- `ProblemCandidate`

Add exported functions:

- `toProblemCandidate(candidate: TopicCandidate): ProblemCandidate`
- `toProblemCandidates(candidates: TopicCandidate[]): ProblemCandidate[]`
- `problemKindLabel(kind: ProblemKind): string`
- `problemKindTone(kind: ProblemKind): "default" | "success" | "warning" | "danger" | "outline" | "secondary"`
- `problemMetrics(problem: ProblemCandidate): ProblemMetric[]`
- `isHotProblem(problem: ProblemCandidate): boolean`
- `isMoneyProblem(problem: ProblemCandidate): boolean`
- `isGuideProblem(problem: ProblemCandidate): boolean`

- [ ] **Step 3: Implement title and summary selection**

In `toProblemCandidate`, pick `title` in this order:

1. `candidate.topicLabel`
2. `candidate.canonicalSummary`
3. `candidate.topicName`
4. `Проблема ${candidate.semanticHash.slice(0, 8)}`

Normalize titles to human-facing language:

- If a title starts with `topic-`, use `Обсуждают повторяющуюся проблему`.
- If title length is over 120 characters, trim to 117 characters plus `...`.

- [ ] **Step 4: Implement evidence selection**

Build `evidence` from `candidate.sourceMessages`.

Sort messages so IDs in `candidate.bestEvidenceMessageIds` come first, then newer messages. Keep all messages in the view model, but the UI will show the first three collapsed.

- [ ] **Step 5: Implement workaround extraction**

Build `currentWorkarounds` from:

- `candidate.guideAngles`
- parsed `mentionedToolsJson`
- parsed `mentionedPricesJson`
- parsed `mentionedErrorsJson`

Keep only unique non-empty strings and show at most six in the UI.

- [ ] **Step 6: Verify TypeScript**

Run:

```powershell
cd frontend
npm run build
```

Expected: build passes with no TypeScript errors.

## Task 2: Redesign Main Signals Page Into Problems Page

**Files:**
- Modify: `frontend/src/pages/signals/SignalsPage.tsx`
- Use: `frontend/src/pages/signals/problemViewModel.ts`

- [ ] **Step 1: Import problem view model**

In `SignalsPage.tsx`, import:

```ts
import {
  isGuideProblem,
  isHotProblem,
  isMoneyProblem,
  problemKindLabel,
  problemKindTone,
  problemMetrics,
  toProblemCandidates,
  type ProblemCandidate,
} from "./problemViewModel";
```

- [ ] **Step 2: Rename header**

Change:

- title from `Кластеры и сигналы` to `Проблемы людей`
- description to `Повторяющиеся боли из обсуждений: что люди не могут сделать, чем обходятся сейчас и где виден спрос.`

Do not show internal words like `cluster-first`, `raw signal metrics`, `legacy` in the main header.

- [ ] **Step 3: Replace primary stat labels**

Replace internal labels with:

- `Найдено проблем`
- `Сообщений в проблемах`
- `Горячих`
- `С деньгами`
- `Кандидатов на гайд`
- `Ждут обработки`

Compute frontend-only counts from `ProblemCandidate[]`:

- hot: `isHotProblem(problem)`
- money: `isMoneyProblem(problem)`
- guide: `isGuideProblem(problem)`

- [ ] **Step 4: Replace tab labels**

Use tabs:

- `Проблемы`
- `Сообщения`
- `Техническое`

Inside `Техническое`, keep legacy micro/macro sections in the same page. They should be secondary and visibly diagnostic.

- [ ] **Step 5: Replace numeric filters with user-facing filters**

Keep existing state variables, but change labels:

- `Сила проблемы от`
- `Боль от`
- `Готовность платить от`
- `Потенциал гайда от`
- `Спам ниже`

Add compact preset buttons:

- `Все`
- `Горячие`
- `Деньги`
- `Гайды`

Preset behavior:

- `Все`: current thresholds, `minProblemSignalScore = 50`, `maxSpamScore = 70`
- `Горячие`: `minProblemSignalScore = 65`, `minPainScore = 50`, `maxSpamScore = 60`
- `Деньги`: `minProblemSignalScore = 50`, `minWillingnessToPayScore = 50`, `maxSpamScore = 60`
- `Гайды`: `minProblemSignalScore = 50`, `minGuidePotentialScore = 60`, `maxSpamScore = 60`

- [ ] **Step 6: Build `ProblemCard`**

Create a local component in `SignalsPage.tsx`:

```ts
function ProblemCard({ problem }: { problem: ProblemCandidate }) {
  // render card
}
```

The card must show:

- kind badge, risk badge, date range, group/topic
- title and summary
- metrics from `problemMetrics(problem)`
- message count and participant count
- current workarounds chips
- buttons:
  - `Открыть` -> navigate to first evidence message in `/groups?group=<groupId>&message=<messageId>`
  - `Доказательства` -> expand/collapse evidence
  - `Гайд` -> navigate to `/guides/<guideId>` when `guideId` is present

- [ ] **Step 7: Build `ProblemEvidenceRow`**

Create a local component:

```ts
function ProblemEvidenceRow({ evidence }: { evidence: ProblemEvidence }) {
  // render row
}
```

Show:

- sender/date
- sanitized message text
- `problemStatement` when present
- `solutionHint` when present
- compact scores for pain/payment/guide/spam

Do not show raw JSON blobs in this row.

- [ ] **Step 8: Keep raw messages as a secondary tab**

Rename `Messages debug` to `Сообщения`.

Keep `SignalRow`, but change visible English labels:

- `Problem` -> `Проблема`
- `Guide` -> `Гайд`
- `Classifier` -> `Классификатор`
- `Spam` -> `Спам`
- `Evidence` -> `Доказательства`
- `Open` -> `Открыть`

- [ ] **Step 9: Move legacy micro/macro into diagnostics**

Render legacy micro/macro under the `Техническое` tab with headings:

- `Legacy microclusters`
- `Legacy macroclusters`

This keeps operational access without making it the page's main product concept.

- [ ] **Step 10: Verify frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: build passes.

## Task 3: Navigation And Empty States

**Files:**
- Modify: `frontend/src/components/domain/floating-sidebar.tsx`
- Modify: `frontend/src/pages/signals/SignalsPage.tsx`

- [ ] **Step 1: Rename sidebar item**

In `floating-sidebar.tsx`, change the `/signals` nav item label from `Сигналы` to `Проблемы`.

Keep the icon as `IconSparklesFilled` unless a better existing Tabler icon is already imported nearby.

- [ ] **Step 2: Rewrite empty state**

Replace `Кластеров пока нет` with:

`Проблем пока нет`

Replace explanatory copy with:

`Здесь появятся повторяющиеся боли после обработки новых сообщений. Попробуй снизить фильтры или дождаться следующего прохода конвейера.`

Keep the stats block, but rename labels:

- `Сообщений с оценками`
- `Кандидатов в проблемы`
- `Видимых сейчас`
- `Ждут эмбеддинги`

- [ ] **Step 3: Check responsive layout**

Run local Vite:

```powershell
cd frontend
npm run dev
```

Open `/signals` and check:

- desktop width around 1440px
- tablet width around 900px
- mobile width around 390px

Expected:

- no overlapping text
- evidence rows wrap cleanly
- score cards do not resize awkwardly
- buttons stay readable

Stop the dev server after validation.

## Task 4: Backend Read Model Enrichment

This task is optional for the first UI MVP, but recommended before judging whether the page is useful.

**Files:**
- Modify: `backend/src/main/java/com/larbcorp/neuroinfogrinder/domain/findings/TopicClusterService.java`
- Modify: `frontend/src/shared/api/pipelineApi.ts`
- Test: `backend/src/test/java/com/larbcorp/neuroinfogrinder/domain/findings/TopicClusterServiceTest.java`
- Test: `backend/src/test/java/com/larbcorp/neuroinfogrinder/api/rest/PipelineControllerTest.java`

- [ ] **Step 1: Extend `TopicExplainMessage` backend record**

In `TopicClusterService.TopicExplainMessage`, add fields after `Integer problemSignalScore`:

```java
Integer painScore,
Integer urgencyScore,
Integer willingnessToPayScore,
Integer technicalDepthScore,
String problemStatement,
String solutionHint,
String mentionedToolsJson,
String mentionedPricesJson,
String mentionedErrorsJson,
String intelligenceReason,
```

Keep `String sanitizedText` unchanged and do not expose raw env/secrets.

- [ ] **Step 2: Populate fields in `toTopicExplainMessage`**

Find `toTopicExplainMessage(MessageEntity message, GroupEntity group)` in `TopicClusterService.java`.

Map fields from `MessageEntity`:

```java
message.getPainScore()
message.getUrgencyScore()
message.getWillingnessToPayScore()
message.getTechnicalDepthScore()
message.getProblemStatement()
message.getSolutionHint()
message.getMentionedToolsJson()
message.getMentionedPricesJson()
message.getMentionedErrorsJson()
message.getIntelligenceReason()
```

Keep existing sanitizer behavior for message text.

- [ ] **Step 3: Update frontend type**

In `frontend/src/shared/api/pipelineApi.ts`, make the same fields non-optional after backend support is implemented:

```ts
  painScore: number | null;
  urgencyScore: number | null;
  willingnessToPayScore: number | null;
  technicalDepthScore: number | null;
  problemStatement: string | null;
  solutionHint: string | null;
  mentionedToolsJson: string | null;
  mentionedPricesJson: string | null;
  mentionedErrorsJson: string | null;
  intelligenceReason: string | null;
```

- [ ] **Step 4: Add backend service test assertions**

In `TopicClusterServiceTest.topicCandidatesGroupEightRelatedMessagesIntoOneCluster`, update one test message:

```java
messages.get(0).setWillingnessToPayScore(65);
messages.get(0).setProblemStatement("Пользователь не может оплатить доступ к модели");
messages.get(0).setSolutionHint("Показать безопасные варианты доступа и оплаты");
```

Then assert:

```java
assertThat(response.topicCandidates().get(0).sourceMessages().get(0).willingnessToPayScore()).isEqualTo(65);
assertThat(response.topicCandidates().get(0).sourceMessages().get(0).problemStatement())
    .isEqualTo("Пользователь не может оплатить доступ к модели");
```

- [ ] **Step 5: Add controller-level safety assertion**

In `PipelineControllerTest.topicCandidatesGroupRelatedMessagesAndExposeSafeEvidence`, set:

```java
second.setProblemStatement("Пользователь просит способ диагностировать риск");
```

Assert the field is present, while `sanitizedText` still does not contain `secret-value`.

- [ ] **Step 6: Run backend tests**

Run:

```powershell
cd backend
mvn test -Dtest=TopicClusterServiceTest,PipelineControllerTest
```

Expected: both test classes pass.

## Task 5: Optional AI Problem Synthesis

Do this only after Adam approves token/cost usage for better problem wording.

**Files:**
- Modify: `backend/src/main/java/com/larbcorp/neuroinfogrinder/domain/findings/TopicClusterService.java`
- Modify: `backend/src/main/java/com/larbcorp/neuroinfogrinder/domain/findings/ContentRoutingService.java` only if reusing routing helpers is cleaner
- Modify: `frontend/src/pages/signals/SignalsPage.tsx`

- [ ] **Step 1: Do not add frontend AI calls**

Keep AI calls server-side only. The frontend must not receive provider keys or call model endpoints.

- [ ] **Step 2: Prefer existing stored classifier output**

Before adding a new model call, use these existing fields:

- `problemStatement`
- `solutionHint`
- `meaningSummary`
- `guideAngles`
- `canonicalSummary`

If these fields produce useful titles, stop here.

- [ ] **Step 3: If needed, add a backend cluster synthesis method**

Add a method that accepts a `TopicCandidateItem` and returns:

```java
record ProblemSynthesis(
    String title,
    String affectedUser,
    String pain,
    String currentWorkaround,
    String opportunity
) {}
```

Use only sanitized text and existing classifier fields as prompt input.

- [ ] **Step 4: Keep synthesis read-through and cache-free for MVP**

Do not add database writes in this task. If synthesis is too slow or costly, disable it and rely on local title selection.

- [ ] **Step 5: Add a visible AI caveat**

If AI synthesis is shown in the UI, add a small secondary label:

`AI-сводка, проверь по доказательствам`

Do not hide evidence messages behind the AI summary.

## Task 6: Final Verification

**Files:**
- No new files expected beyond tasks above.

- [ ] **Step 1: Run frontend build**

```powershell
cd frontend
npm run build
```

Expected: TypeScript and Vite build pass.

- [ ] **Step 2: Run targeted backend tests if Task 4 was implemented**

```powershell
cd backend
mvn test -Dtest=TopicClusterServiceTest,PipelineControllerTest
```

Expected: targeted tests pass.

- [ ] **Step 3: Manual UI validation**

Run:

```powershell
cd frontend
npm run dev
```

Validate `/signals`:

- page title says `Проблемы людей`
- sidebar says `Проблемы`
- main tab shows problem cards, not internal cluster wording
- each problem card has evidence
- filters change the query and the visible result set
- debug/legacy views are still reachable
- no secrets or raw `.env` values are shown

- [ ] **Step 4: Deployment impact note**

If only Tasks 1-3 are implemented, production deploy impact is frontend-only:

```powershell
.\scripts\deploy-fast.ps1 -Target frontend
```

If Task 4 is implemented, production deploy impact is backend + frontend:

```powershell
.\scripts\deploy-fast.ps1 -Target all
```

Before any production deploy, explicitly state affected services. Routine deploy must not restart `postgres`, `redis`, `tor`, or `ssh-socks`.

## Acceptance Criteria

- The primary page is understandable without knowing the words "cluster", "embedding", or "legacy".
- The first screen answers what people are struggling with.
- Every problem card includes real evidence messages.
- Money/pain/guide potential are visible as human-facing scores.
- Raw message and legacy diagnostics remain available but secondary.
- Frontend build passes.
- If backend read model enrichment is implemented, targeted backend tests pass.

## Questions For Adam

These decisions are useful but do not block starting with the defaults above:

1. Page name: **Проблемы** in the sidebar and **Проблемы людей** as page title, or both should be **Проблемы людей**?
2. Scope: only AI access/payment/provider pains, or also technical/security/how-to pains?
3. AI: use only existing classifier fields for now, or approve a separate AI synthesis pass for nicer problem titles?

## Self-Review

- Spec coverage: the plan covers the requested transformation from "Кластеры и сигналы" to human problems, includes AI usage boundaries, frontend UX, backend enrichment, and validation.
- Placeholder scan: no `TBD`, fake paths, fake commands, or unspecified test commands are used.
- Type consistency: frontend `TopicExplainMessage` additions match backend `TopicExplainMessage` additions by name and nullability.
- Safety: no production DB writes, no env output, no full-service compose rebuild, and no infra restarts are required.

## Execution Handoff

Plan complete and saved to `docs/plans/product/problems/problems-page-plan.md`. Two execution options:

1. **Frontend MVP first** - Implement Tasks 1-3 and Task 6 Step 1/3. Fastest way to see whether the concept feels right.
2. **Full read-model version** - Implement Tasks 1-4 and Task 6. Better evidence cards because pain/payment/problem fields are visible inside each problem.

Recommended first execution: **Frontend MVP first**, then add Task 4 if the screen feels directionally right.
