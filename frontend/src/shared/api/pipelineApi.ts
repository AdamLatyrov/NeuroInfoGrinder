import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";

export type PipelineVisualStatus = "completed" | "active" | "error" | "waiting";

export interface PipelineWarningItem { code: string; message: string; }

export interface PipelineLiveSummary {
  rawMessages: number;
  intakePending: number;
  intakeQueued: number;
  processing: number;
  processed: number;
  skipped: number;
  failed: number;
  waitingForWorker: number;
  generatedMaterials: number;
  latestRunStatus: string | null;
  workerStatus: string | null;
  autoPipelineEnabledCount: number;
  warnings: PipelineWarningItem[];
  mainBlocker?: string | null;
  secondaryBlocker?: string | null;
  explanation?: string | null;
  nextActions?: string[];
  incomingLastMinute?: number;
  intakePendingTotal?: number;
  pendingInsideEnabledScopes?: number;
  pendingOutsideEnabledScopes?: number;
  liveQueue?: number;
  collectingBatches?: number;
  collectingMessages?: number;
  pendingRuns?: number;
  runningRuns?: number;
  completedRuns?: number;
  latestRunId?: number | null;
  latestTerminalReason?: string | null;
  lastSchedulerTickAt?: string | null;
  lastMessageReceivedAt?: string | null;
  lastBatchFlushedAt?: string | null;
  lastRunStartedAt?: string | null;
  lastRunCompletedAt?: string | null;
}

export interface PipelineLiveAcceptance {
  latestRawId: number;
  latestRawAt: string | null;
  latestProcessedRawId: number | null;
  latestProcessedAt: string | null;
  rawGap: number;
  runId: number | null;
  runStatus: string | null;
  terminalReason: string | null;
  messageCount: number;
  runCreatedAt: string | null;
  runStartedAt: string | null;
  runFinishedAt: string | null;
  replayRunMessages: number;
  messageEmbeddings: number;
  microclusters: number;
  macroclusters: number;
  singleCandidates: number;
  providerCalls: number;
  materials: number;
}

export interface PipelineStageSummary {
  ordinal: number;
  id: string;
  name: string;
  description: string;
  status: PipelineVisualStatus;
  active: number;
  processed: number;
  failed: number;
  skipped: number;
  waiting: number;
  averageLatencyMs: number | null;
  lastUpdatedAt: string | null;
  warning: string | null;
}

export interface PipelineLiveEvent {
  eventId: string;
  type: string;
  timestamp: string | null;
  accountId: number;
  chatId: number | null;
  topicId: number | null;
  batchId: number | null;
  runId: number | null;
  rawMessageId: number | null;
  stage: string | null;
  fromStage: string | null;
  toStage: string | null;
  status: string | null;
  count: number;
  message: string;
}

export interface PipelineStageMessage {
  id: number;
  accountId: number;
  telegramChatId: number;
  telegramMessageId: number;
  chatTitle: string | null;
  topicTitle: string | null;
  contentType: string | null;
  text: string | null;
  status: string;
  errorCode: string | null;
  errorMessage: string | null;
  currentStage: string | null;
  blockedReason: string | null;
  actionHint: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface PipelineStageDetails {
  stage: PipelineStageSummary;
  reason: string | null;
  explanation: string;
  latestEvents: PipelineLiveEvent[];
  waitingMessages: PipelineStageMessage[];
  workerStatus: string;
  embeddingStatus: string;
  classifierStatus: string;
  clusters: number;
  singleMessageCandidates: number;
  providerCalls: number;
  materialCount: number;
}

export interface PipelineLiveBatch {
  batchId: number;
  runId: number | null;
  accountId: number;
  chatId: number | null;
  topicId: number | null;
  status: string;
  messageCount: number;
  createdAt: string | null;
  updatedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  terminalReason: string | null;
  materialCount: number;
  candidateCount: number;
  error: string | null;
  waterfall: string[];
}

export interface PipelinePendingBreakdownRow {
  accountId: number;
  chatId: number;
  topicId: number | null;
  chatTitle: string | null;
  topicTitle: string | null;
  autoEnabledEffective: boolean;
  pending: number;
  queued: number;
  collectingBatches: number;
  runningRuns: number;
  processed: number;
  latestRawMessageAt: string | null;
  latestQueuedAt: string | null;
}

export interface ClassifierStatusResponse {
  workerReachable?: boolean;
  status?: string;
  classifierStatus?: string;
  embeddingStatus?: string;
  embeddingName?: string;
  embeddingDimension?: number;
  degraded?: boolean;
}

export interface PipelineQueuePendingRequest { limit: number; dryRun: boolean; }
export interface PipelineQueuePendingResult { candidates: number; queued: number; remainingInsideEnabledScopes: number; dryRun: boolean; }

export interface PipelineDetailResponse {
  kind?: string;
  runId?: number | null;
  latestRunId?: number | null;
  latestTerminalReason?: string | null;
  reason?: string | null;
  explanation?: string | null;
  items?: Array<Record<string, unknown>>;
  providerCalls?: Array<Record<string, unknown>>;
  [key: string]: unknown;
}

export interface PipelineRunDetailsResponse extends Record<string, unknown> {
  runId: number;
  found?: boolean;
  status?: string;
  terminalReason?: string | null;
  stages?: Array<Record<string, unknown>>;
}

export function usePipelineLiveSummaryQuery() {
  return useQuery({
    queryKey: ["pipeline-live-summary"],
    queryFn: () => getJsonAuth<PipelineLiveSummary>("/api/v2/pipeline/live/summary"),
    refetchInterval: 5_000,
  });
}

export function usePipelineLiveAcceptanceQuery() {
  return useQuery({
    queryKey: ["pipeline-live-acceptance"],
    queryFn: () => getJsonAuth<PipelineLiveAcceptance>("/api/v2/pipeline/live/acceptance"),
    refetchInterval: 5_000,
  });
}

export function usePipelineLiveStagesQuery() {
  return useQuery({
    queryKey: ["pipeline-live-stages"],
    queryFn: () => getJsonAuth<PipelineStageSummary[]>("/api/v2/pipeline/live/stages"),
    refetchInterval: 5_000,
  });
}

export function usePipelineLiveEventsQuery(limit = 20) {
  return useQuery({
    queryKey: ["pipeline-live-events", limit],
    queryFn: () => getJsonAuth<{ events: PipelineLiveEvent[] }>(`/api/v2/pipeline/live/events?limit=${limit}`),
    refetchInterval: 3_000,
  });
}

export function usePipelineLiveBatchesQuery(limit = 20) {
  return useQuery({
    queryKey: ["pipeline-live-batches", limit],
    queryFn: () => getJsonAuth<{ batches: PipelineLiveBatch[] }>(`/api/v2/pipeline/live/batches?limit=${limit}`),
    refetchInterval: 5_000,
  });
}

export function usePipelinePendingBreakdownQuery() {
  return useQuery({
    queryKey: ["pipeline-pending-breakdown"],
    queryFn: () => getJsonAuth<PipelinePendingBreakdownRow[]>("/api/v2/pipeline/pending-breakdown"),
    refetchInterval: 12_000,
  });
}

export function usePipelineStageDetailsQuery(stageId: string | null) {
  return useQuery({
    queryKey: ["pipeline-stage-details", stageId],
    queryFn: () => getJsonAuth<PipelineStageDetails>(`/api/v2/pipeline/live/stages/${stageId}/details`),
    enabled: Boolean(stageId),
    refetchInterval: 5_000,
  });
}

export function usePipelineStageMessagesQuery(stageId: string | null, status = "processed", limit = 100) {
  return useQuery({
    queryKey: ["pipeline-stage-messages", stageId, status, limit],
    queryFn: () => getJsonAuth<PipelineStageMessage[]>(`/api/v2/pipeline/live/stages/${stageId}/messages?status=${encodeURIComponent(status)}&limit=${limit}`),
    enabled: Boolean(stageId),
    refetchInterval: 5_000,
    placeholderData: keepPreviousData,
  });
}

export function usePipelineClustersQuery(runId?: number | null, limit = 50) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (runId) query.set("runId", String(runId));
  return useQuery({
    queryKey: ["pipeline-live-clusters", runId ?? "latest", limit],
    queryFn: () => getJsonAuth<PipelineDetailResponse>(`/api/v2/pipeline/live/clusters?${query.toString()}`),
    refetchInterval: 10_000,
  });
}

export function usePipelineEmbeddingsQuery(runId?: number | null, limit = 50) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (runId) query.set("runId", String(runId));
  return useQuery({
    queryKey: ["pipeline-live-embeddings", runId ?? "latest", limit],
    queryFn: () => getJsonAuth<PipelineDetailResponse>(`/api/v2/pipeline/live/embeddings?${query.toString()}`),
    refetchInterval: 10_000,
  });
}

export function usePipelineLlmJudgeQuery(runId?: number | null, limit = 50) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (runId) query.set("runId", String(runId));
  return useQuery({
    queryKey: ["pipeline-live-llm-judge", runId ?? "latest", limit],
    queryFn: () => getJsonAuth<PipelineDetailResponse>(`/api/v2/pipeline/live/llm-judge?${query.toString()}`),
    refetchInterval: 10_000,
  });
}

export function usePipelineMaterialGenerationQuery(runId?: number | null, limit = 50) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (runId) query.set("runId", String(runId));
  return useQuery({
    queryKey: ["pipeline-live-material-generation", runId ?? "latest", limit],
    queryFn: () => getJsonAuth<PipelineDetailResponse>(`/api/v2/pipeline/live/material-generation?${query.toString()}`),
    refetchInterval: 10_000,
  });
}

export function usePipelineRunDetailsQuery(runId: number | null) {
  return useQuery({
    queryKey: ["pipeline-live-run-details", runId],
    queryFn: () => getJsonAuth<PipelineRunDetailsResponse>(`/api/v2/pipeline/live/runs/${runId}/details`),
    enabled: Boolean(runId),
  });
}

export function useClassifierStatusQuery() {
  return useQuery({
    queryKey: ["classifier-status"],
    queryFn: () => getJsonAuth<ClassifierStatusResponse>("/api/v2/classifiers/status"),
    refetchInterval: 10_000,
  });
}

export function usePipelineQueuePendingMutation() {
  return useMutation({
    mutationFn: (request: PipelineQueuePendingRequest) => postJsonAuth<PipelineQueuePendingResult>("/api/v2/pipeline/queue-pending-enabled-scopes", request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-live-summary"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-pending-breakdown"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-live-batches"] });
    },
  });
}

// ── Pipeline status ──

export interface PipelineStatusResponse {
  processorEnabled: boolean;
  unprocessed: number;
  queued: number;
  processing: number;
  classified: number;
  skipped: number;
  guideFound: number;
  errors: number;
  guidesTotal: number;
  messagesWithGuide: number;
  guideGenerationErrors: number;
  classifiedTotal: number;
  skippedTotal: number;
  errorsTotal: number;
}

export function usePipelineStatusQuery(from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);

  return useQuery({
    queryKey: ["pipeline-status", from ?? "all", to ?? "now"],
    queryFn: () =>
      getJsonAuth<PipelineStatusResponse>(
        `/pipeline/status${params.size > 0 ? `?${params.toString()}` : ""}`
      ),
    refetchInterval: 5_000,
  });
}

// ── Process single message ──

export function useProcessMessageMutation() {
  return useMutation({
    mutationFn: (messageId: string) =>
      postJsonAuth<void>(`/pipeline/process/${messageId}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

// ── Process queue manually ──

export function useProcessQueueMutation() {
  return useMutation({
    mutationFn: (limit: number = 10) =>
      postJsonAuth<void>(`/pipeline/process-queue?limit=${limit}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

// ── Pause / Resume ──

export function useRequeuePipelineMutation() {
  return useMutation({
    mutationFn: (statuses: string[] = ["SKIPPED", "CLASSIFIED", "CLEARED"]) => {
      const params = new URLSearchParams();
      statuses.forEach((status) => params.append("statuses", status));
      return postJsonAuth<void>(`/pipeline/requeue?${params.toString()}`, {});
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

export function useRequeueMessageMutation() {
  return useMutation({
    mutationFn: (messageId: number | string) =>
      postJsonAuth<void>(`/pipeline/requeue/${messageId}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

export function useSmokeGuideMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/dev/smoke-guide", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

export interface GenerateMissingGuidesResult {
  scanned: number;
  generated: number;
  skipped: number;
  failed: number;
}

export interface RetryApiErrorsResult {
  scanned: number;
  retried: number;
  succeeded: number;
  failed: number;
  skipped: number;
}

function invalidatePipelineWork() {
  queryClient.invalidateQueries({ queryKey: ["group-messages"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
  queryClient.invalidateQueries({ queryKey: ["guides"] });
  queryClient.invalidateQueries({ queryKey: ["materials"] });
  queryClient.invalidateQueries({ queryKey: ["traces"] });
  queryClient.invalidateQueries({ queryKey: ["message-trace"] });
}

export function useGenerateMissingGuidesMutation() {
  return useMutation({
    mutationFn: (limit: number = 50) =>
      postJsonAuth<GenerateMissingGuidesResult>(
        `/pipeline/generate-missing-guides?limit=${limit}`,
        {}
      ),
    onSuccess: invalidatePipelineWork,
  });
}

export function useRetryApiErrorsMutation() {
  return useMutation({
    mutationFn: (limit: number = 50) =>
      postJsonAuth<RetryApiErrorsResult>(
        `/pipeline/retry-api-errors?limit=${limit}`,
        {}
      ),
    onSuccess: invalidatePipelineWork,
  });
}

export function usePausePipelineMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/pause", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

export function useResumePipelineMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/resume", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

// ── Pipeline results (for tuning) ──

export interface PipelineResultItem {
  id: number;
  groupId: number;
  author: string | null;
  text: string;
  status: string;
  signalScore: number | null;
  classifierScore: number | null;
  classifierReason: string | null;
  classifierResultJson: string | null;
  classificationContextHash: string | null;
  guideId: number | null;
  messageDate: string;
  signalBreakdown: string | null;
  ruleResultJson: string | null;
  guidePotentialScore: number | null;
  problemSignalScore: number | null;
  painScore: number | null;
  urgencyScore: number | null;
  willingnessToPayScore: number | null;
  technicalDepthScore: number | null;
  spamScore: number | null;
  meaningSummary: string | null;
  problemStatement: string | null;
  solutionHint: string | null;
  mentionedToolsJson: string | null;
  mentionedPricesJson: string | null;
  mentionedErrorsJson: string | null;
  intelligenceReason: string | null;
  clusterCandidate: boolean | null;
  embeddingStatus: string | null;
  messageIntelligenceJson: string | null;
}

interface PipelineResultsPage {
  content: PipelineResultItem[];
  totalElements: number;
  totalPages: number;
}

export interface PipelineQueueItem {
  id: number;
  groupId: number;
  groupTitle: string;
  author: string | null;
  text: string | null;
  status: string;
  topicName: string | null;
  messageDate: string;
}

interface PipelineQueuePage {
  content: PipelineQueueItem[];
  totalElements: number;
  totalPages: number;
}

export function usePipelineResultsQuery(
  status?: string | string[],
  page: number = 0,
  size: number = 50,
  from?: string,
  enabled: boolean = true,
  to?: string
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "messageDate,desc",
  });
  if (Array.isArray(status)) {
    status.forEach((item) => params.append("statuses", item));
  } else if (status) {
    params.set("status", status);
  }
  if (from) params.set("from", from);
  if (to) params.set("to", to);

  return useQuery({
    queryKey: ["pipeline-results", Array.isArray(status) ? status.join(",") : status, page, size, from, to],
    queryFn: () =>
      getJsonAuth<PipelineResultsPage>(`/pipeline/results?${params.toString()}`),
    enabled,
    refetchInterval: 5_000,
    placeholderData: keepPreviousData,
  });
}

export interface TopicExplainMessage {
  messageId: number;
  groupId: number;
  groupTitle: string | null;
  topicId: number | null;
  topicName: string | null;
  messageDate: string;
  senderName: string | null;
  sanitizedText: string | null;
  processingStatus: string;
  classifierScore: number | null;
  classifierReason: string | null;
  classifierResultSummary: string | null;
  ruleResultSummary: string | null;
  signalScore: number | null;
  problemSignalScore: number | null;
  painScore: number | null;
  urgencyScore: number | null;
  willingnessToPayScore: number | null;
  technicalDepthScore: number | null;
  guidePotentialScore: number | null;
  problemStatement: string | null;
  solutionHint: string | null;
  mentionedToolsJson: string | null;
  mentionedPricesJson: string | null;
  mentionedErrorsJson: string | null;
  intelligenceReason: string | null;
  clusterCandidate: boolean | null;
  spamScore: number | null;
  guideId: number | null;
  classificationContextHash: string | null;
}

export interface TopicCandidate {
  topicKey: string;
  groupId: number;
  groupTitle: string | null;
  topicId: number | null;
  topicName: string | null;
  startAt: string;
  endAt: string;
  sourceMessageIds: number[];
  participants: string[];
  canonicalSummary: string | null;
  topicLabel: string | null;
  semanticHash: string;
  guidePotentialScore: number | null;
  riskSafetyCategory: string;
  status: string;
  guideId: number | null;
  bestEvidenceMessageIds: number[];
  guideAngles: string[];
  sourceMessages: TopicExplainMessage[];
  groupingReason: string;
}

export interface TopicCandidatesResponse {
  processingUnit: string;
  explanation: string;
  from: string | null;
  to: string | null;
  windowMinutes: number;
  sourceMessageCount: number;
  topicCandidateCount: number;
  topicCandidates: TopicCandidate[];
  caveats: string[];
}

export interface TopicCandidatesQueryOptions {
  from?: string;
  to?: string;
  minProblemSignalScore?: number;
  minPainScore?: number;
  minWillingnessToPayScore?: number;
  minGuidePotentialScore?: number;
  clusterCandidateOnly?: boolean;
  maxSpamScore?: number;
  enabled?: boolean;
  group?: string;
  topic?: string;
  windowMinutes?: number;
  limit?: number;
}

export function useTopicCandidatesQuery(options: TopicCandidatesQueryOptions = {}) {
  const params = new URLSearchParams();
  if (options.group) params.set("group", options.group);
  if (options.topic) params.set("topic", options.topic);
  if (options.from) params.set("from", options.from);
  if (options.to) params.set("to", options.to);
  if (options.windowMinutes != null) params.set("windowMinutes", String(options.windowMinutes));
  if (options.limit != null) params.set("limit", String(options.limit));
  if (options.minProblemSignalScore != null) params.set("minProblemSignalScore", String(options.minProblemSignalScore));
  if (options.minPainScore != null) params.set("minPainScore", String(options.minPainScore));
  if (options.minWillingnessToPayScore != null) params.set("minWillingnessToPayScore", String(options.minWillingnessToPayScore));
  if (options.minGuidePotentialScore != null) params.set("minGuidePotentialScore", String(options.minGuidePotentialScore));
  if (options.clusterCandidateOnly != null) params.set("clusterCandidateOnly", String(options.clusterCandidateOnly));
  if (options.maxSpamScore != null) params.set("maxSpamScore", String(options.maxSpamScore));

  return useQuery({
    queryKey: ["topic-candidates", options],
    queryFn: () =>
      getJsonAuth<TopicCandidatesResponse>(
        `/pipeline/topic-candidates${params.size > 0 ? `?${params.toString()}` : ""}`,
      ),
    enabled: options.enabled ?? true,
    refetchInterval: 10_000,
    placeholderData: keepPreviousData,
  });
}

export function usePipelineQueueQuery(
  statuses: string[] = ["QUEUED", "UNPROCESSED", "PROCESSING"],
  page: number = 0,
  size: number = 50,
  enabled: boolean = true
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  statuses.forEach((status) => params.append("statuses", status));

  return useQuery({
    queryKey: ["pipeline-queue", statuses.join(","), page, size],
    queryFn: () =>
      getJsonAuth<PipelineQueuePage>(`/pipeline/queue?${params.toString()}`),
    enabled,
    refetchInterval: 5_000,
    placeholderData: keepPreviousData,
  });
}

export function useClearPipelineQueueMutation() {
  return useMutation({
    mutationFn: (statuses: string[] = ["QUEUED"]) => {
      const params = new URLSearchParams();
      statuses.forEach((status) => params.append("statuses", status));
      return deleteJsonAuth(`/pipeline/queue?${params.toString()}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
    },
  });
}

export function useClearPipelineResultsMutation() {
  return useMutation({
    mutationFn: () => deleteJsonAuth("/pipeline/results"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
    },
  });
}

export interface AutoPipelineStatus {
  accountId: number;
  chatId: number;
  topicId: number | null;
  scope: "CHAT" | "TOPIC" | string;
  configured: boolean;
  enabled: boolean;
  manuallyDisabled: boolean;
  effectiveEnabled: boolean;
  source: string;
  debounceSeconds: number;
  batchSize: number;
  maxProviderCalls: number;
  maxCostUsd: number;
}

export interface AutoPipelineScope {
  accountId: number;
  chatId: number;
  topicId?: number | null;
}

export interface IntakeBackfillResult {
  rawFound: number;
  intakeCreated: number;
  queued: number;
  skippedExisting: number;
  skippedOutOfScope: number;
  dryRun: boolean;
  autoEnabled: boolean;
}

export interface RunHealth {
  runId: number;
  verdict: string;
  label: string;
  currentStage: string | null;
  lastProgressAt: string | null;
  queued: number;
  processing: number;
  processed: number;
  failed: number;
  blocker: string | null;
  nextAction: string | null;
}

function autoScopeParams(scope: AutoPipelineScope) {
  const params = new URLSearchParams({ accountId: String(scope.accountId), chatId: String(scope.chatId) });
  if (scope.topicId != null) params.set("topicId", String(scope.topicId));
  return params;
}

function invalidateAutoPipeline(scope?: AutoPipelineScope) {
  queryClient.invalidateQueries({ queryKey: ["pipeline-auto-status"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-auto-settings"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-live-summary"] });
  queryClient.invalidateQueries({ queryKey: ["pipeline-live-stages"] });
  if (scope) {
    queryClient.invalidateQueries({ queryKey: ["pipeline-auto-status", scope.accountId, scope.chatId, scope.topicId ?? null] });
  }
}

export function useAutoPipelineStatusQuery(scope: AutoPipelineScope | null | undefined) {
  return useQuery({
    queryKey: ["pipeline-auto-status", scope?.accountId ?? null, scope?.chatId ?? null, scope?.topicId ?? null],
    queryFn: () => getJsonAuth<AutoPipelineStatus>(`/api/v2/pipeline/auto/status?${autoScopeParams(scope!).toString()}`),
    enabled: Boolean(scope?.accountId && scope?.chatId),
    refetchInterval: 15_000,
  });
}

export function useEnableAutoPipelineMutation() {
  return useMutation({
    mutationFn: (scope: AutoPipelineScope) => postJsonAuth<AutoPipelineStatus>("/api/v2/pipeline/auto/enable", scope),
    onSuccess: (_data, scope) => invalidateAutoPipeline(scope),
  });
}

export function useDisableAutoPipelineMutation() {
  return useMutation({
    mutationFn: (scope: AutoPipelineScope) => postJsonAuth<AutoPipelineStatus>("/api/v2/pipeline/auto/disable", scope),
    onSuccess: (_data, scope) => invalidateAutoPipeline(scope),
  });
}

export function useBackfillRawMutation() {
  return useMutation({
    mutationFn: (scope: AutoPipelineScope & { limit?: number; dryRun?: boolean }) =>
      postJsonAuth<IntakeBackfillResult>("/api/v2/pipeline/intake/backfill-from-raw", {
        ...scope,
        limit: scope.limit ?? 30,
        dryRun: scope.dryRun ?? false,
      }),
    onSuccess: (_data, scope) => invalidateAutoPipeline(scope),
  });
}

export function useRunHealthQuery(runId: number | null | undefined) {
  return useQuery({
    queryKey: ["pipeline-run-health", runId ?? null],
    queryFn: () => getJsonAuth<RunHealth>(`/api/v2/pipeline/runs/${runId}/health`),
    enabled: Boolean(runId),
    refetchInterval: 5_000,
  });
}
