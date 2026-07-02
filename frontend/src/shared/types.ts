// Domain types for NeuroInfoGrinder
// Matches actual backend API responses exactly

// в”Ђв”Ђ Accounts в”Ђв”Ђ

export type AccountStatus =
  | "CONNECTED"
  | "WAITING_CODE"
  | "WAITING_PASSWORD"
  | "PAUSED"
  | "ERROR"
  | "DISABLED"
  | "DISCONNECTED";

export interface TelegramAccount {
  id: string;
  phone: string;
  status: AccountStatus;
  externalTelegramUserId: string | null;
  username: string | null;
  languageCode: string | null;
  timezone: string | null;
  createdAt: string | null;
  groupsCount?: number;
}

// в”Ђв”Ђ Groups в”Ђв”Ђ

export interface Group {
  id: string;
  telegramChatId: string;
  title: string;
  username: string | null;
  sourceType: "GROUP" | "DIRECT_CHAT" | "CHANNEL";
  category: string | null;
  forum: boolean;
  enabled: boolean;
  accountId: string | null;
  messagesPerDay: number | null;
  guidesFound: number;
  lastReadAt: string | null;
  lastReadMessageId: string | null;
  rawMessagesTotal: number | null;
  rawMessagesLast24h: number | null;
  latestRawMessageAt: string | null;
  topicsCount: number | null;
  autoPipelineEnabled: boolean;
  activeDialog: boolean;
  displayState: string | null;
  processingState: string | null;
  source: string | null;
}

// в”Ђв”Ђ Topics (from Telegram TDLib) в”Ђв”Ђ

export interface Topic {
  chatId: string;
  forumTopicId: string;
  messageThreadId: string;
  name: string;
  general: boolean;
  titleSource?: string | null;
  syncState?: string | null;
}

// в”Ђв”Ђ Messages (no backend endpoint yet, kept for ChatViewer) в”Ђв”Ђ

export type MessageStatus =
  | "UNPROCESSED"
  | "QUEUED"
  | "PROCESSING"
  | "CLASSIFIED"
  | "SKIPPED"
  | "SENT_TO_LLM"
  | "GUIDE_FOUND"
  | "GUIDE_GENERATED"
  | "ERROR"
  | "REJECTED";

export interface MessageLink {
  id: string;
  messageId: string;
  url: string;
  normalizedUrl: string | null;
  domain: string | null;
  anchorText: string | null;
  source: "TEXT" | "CAPTION" | "WEB_PAGE" | "BUTTON" | "UNKNOWN" | string;
  entityType: "TEXT_URL" | "URL" | "MENTION" | "TEXT_MENTION" | "EMAIL" | "PHONE" | "BOT_COMMAND" | "UNKNOWN" | string;
  offsetStart: number | null;
  offsetEnd: number | null;
  hidden: boolean;
  visibleUrl: boolean;
  telegramLink: boolean;
  referralLike: boolean;
}

export interface Message {
  id: string;
  groupId: string;
  groupTitle: string;
  telegramChatId?: string | null;
  telegramMessageId: string;
  author: string;
  authorTelegramId: string;
  isBot: boolean;
  text: string;
  caption?: string | null;
  contentType?: string | null;
  replyToMessageId?: string;
  replyCount: number;
  timestamp: string;
  processingStatus: MessageStatus;
  guideId?: string;
  topicName?: string;
  topicId?: string;
  telegramMessageUrl?: string | null;
  hasMedia: boolean;
  hasLinks?: boolean;
  mediaType?: string | null;
  hasVoice?: boolean;
  hasGif?: boolean;
  hasDocument?: boolean;
  hasPhoto?: boolean;
  hasVideo?: boolean;
  fileName?: string | null;
  mimeType?: string | null;
  durationSeconds?: number | null;
  mediaJson?: string | null;
  links?: MessageLink[];
  signalScore?: number | null;
  guidePotentialScore?: number | null;
  problemSignalScore?: number | null;
  painScore?: number | null;
  urgencyScore?: number | null;
  willingnessToPayScore?: number | null;
  technicalDepthScore?: number | null;
  spamScore?: number | null;
  meaningSummary?: string | null;
  problemStatement?: string | null;
  solutionHint?: string | null;
  mentionedToolsJson?: string | null;
  mentionedPricesJson?: string | null;
  mentionedErrorsJson?: string | null;
  intelligenceReason?: string | null;
  clusterCandidate?: boolean | null;
  embeddingStatus?: "NONE" | "REQUIRED" | "EMBEDDED" | "FAILED" | string | null;
  messageIntelligenceJson?: string | null;
  classifierScore?: number | null;
  classifierReason?: string | null;
  classifierResultJson?: string | null;
  classificationContextHash?: string | null;
  signalBreakdown?: string | null;
  ruleResultJson?: string | null;
}

export interface MessageChain {
  id: string;
  groupId: string;
  rootMessageId: string;
  messages: Message[];
  totalMessages: number;
  usedInLlm: boolean;
}

// в”Ђв”Ђ Guides в”Ђв”Ђ

export type GuideStatus =
  | "DRAFT"
  | "PROCESSING"
  | "APPROVED"
  | "PUBLISHED"
  | "FAILED"
  | "REJECTED";

export type ContentType =
  | "GUIDE"
  | "GENERATION"
  | "ANSWER"
  | "OTHER"
  | "CLUSTER_SUMMARY"
  | "TROUBLESHOOTING"
  | "CHECKLIST"
  | "RESOURCE_LIST"
  | "SUMMARY"
  | "PROMPT"
  | "CODE_SNIPPET"
  | "CASE_NOTE"
  | "COMPARISON"
  | "UNKNOWN"
  | "NEWS"
  | "USEFUL_INFO"
  | "FAQ"
  | "WARNING"
  | "RISK_INSIGHT"
  | "PRODUCT_UPDATE"
  | "REFERENCE"
  | "DISCUSSION_ONLY"
  | "DEFERRED";

export interface SourceMessage {
  orderIndex?: number | null;
  rawId: string | null;
  datasetMessageId: string | null;
  replayRunMessageId?: string | null;
  messageId: string;
  groupId: string | null;
  telegramChatId: string | null;
  telegramMessageId: string | null;
  chatTitle: string | null;
  messageDate: string | null;
  senderDisplayName: string | null;
  senderUsername: string | null;
  author?: string | null;
  senderTelegramUserId: string | null;
  senderNameSource: string | null;
  text: string | null;
  textUnavailableReason: string | null;
  preview: string | null;
  textEntities: {
    type: string | null;
    offset: number | null;
    length: number | null;
    url: string | null;
    text: string | null;
  }[];
  usedInPrompt: boolean;
  role?: string | null;
  relation: string | null;
  replyToTelegramMessageId: string | null;
  topicId: string | null;
  threadId?: string | null;
  topicName: string | null;
  internalMessageUrl: string | null;
  appMessageUrl: string | null;
  telegramMessageUrl: string | null;
  telegramLinkAvailable: boolean;
  telegramLinkReason: string | null;
}

export interface GuideLlmRequest {
  providerId: string | null;
  model: string | null;
  promptId: string | null;
  promptVersion: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export interface MaterialProviderCall {
  id: string;
  stage: string | null;
  providerId: string | null;
  model: string | null;
  status: string | null;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  estimatedCostUsd: number;
  latencyMs: number | null;
  httpStatus: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  requestPreview?: string | null;
  responsePreview?: string | null;
  responseJson?: string | null;
  createdAt: string | null;
}

export interface MaterialHowBuiltStep {
  title: string;
  status: string | null;
  details: Record<string, unknown> | null;
}

export interface MaterialTraceStage {
  id: string;
  rawId: string | null;
  runId: string | null;
  stage: string;
  stageName: string | null;
  status: string;
  reason: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  durationMs: number | null;
  outputJson: string | null;
}

export interface MaterialRunInfo {
  id: string;
  status: string | null;
  terminalReason: string | null;
  totalMessages: number;
  processedMessages: number;
  providerCallsTotal: number;
  createdAt: string | null;
  finishedAt: string | null;
}

export interface Guide {
  id: string;
  title: string;
  sourceGroupId: string | null;
  sourceGroupTitle: string | null;
  rootMessageId: string | null;
  contentType: ContentType;
  type?: string | null;
  candidateType?: string | null;
  materialId?: string | null;
  contentSubtype: string | null;
  topicLabel: string | null;
  topicSummary: string | null;
  contentTitle: string | null;
  contentSummary: string | null;
  normalizedTopicKey: string | null;
  entities?: string[];
  contentQualityScore: number | null;
  importanceScore: number | null;
  actionabilityScore: number | null;
  noveltyScore: number | null;
  evidenceScore: number | null;
  riskScore: number | null;
  confidenceScore: number | null;
  noiseScore: number | null;
  routingReason: string | null;
  safetyCategory: string | null;
  publicationKind: string | null;
  content: string | null;
  contentMarkdown: string | null;
  rawResponse: string | null;
  regeneratedFromGuideId: string | null;
  confidence: number | null;
  quality?: number | null;
  usefulnessScore: number | null;
  status: GuideStatus;
  providerId: string | null;
  model: string | null;
  promptVersion: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  estimatedCost: number;
  createdAt: string | null;
  publishedAt: string | null;
  publicationStatus: "NOT_SENT" | "QUEUED" | "SENT" | "FAILED" | "SKIPPED" | string;
  tags: string[];
  generationError: string | null;
  sourceCount: number;
  segmentId?: string | null;
  clusterId?: string | null;
  segmentScore?: number | null;
  segmentDecision?: string | null;
  segmentSignals?: unknown[] | null;
  segmentSuppressionReasons?: unknown[] | null;
  generationSkipReason?: string | null;
  duplicateScore: number | null;
  duplicateOfGuideId: string | null;
  sourceMessages: SourceMessage[];
  llmRequest: GuideLlmRequest | null;
  providerCalls: MaterialProviderCall[];
  traceStages: MaterialTraceStage[];
  run: MaterialRunInfo | null;
  candidate: { type: string | null } | null;
  howBuiltSteps?: MaterialHowBuiltStep[];
  relatedGuideIds: string[];
  possibleDuplicateIds: string[];
}

// в”Ђв”Ђ Classifiers в”Ђв”Ђ

export type ClassifierType = "LLM" | "KEYWORD" | "REGEX" | "LINEAR_MODEL";
export type ClassifierStatus = "ACTIVE" | "DRAFT" | "DISABLED";

export interface Classifier {
  id: string;
  name: string;
  type: ClassifierType;
  providerId: string | null;
  promptId: string | null;
  keywords: string | null;
  regexPattern: string | null;
  modelConfig: string | null;
  version: string;
  status: ClassifierStatus;
  order: number;
  avgTokens: number;
  successRate: number;
}

// в”Ђв”Ђ Rules в”Ђв”Ђ

export interface Rule {
  id: string;
  name: string;
  description: string | null;
  actionType: string; // "INCLUDE" | "EXCLUDE"
  ruleOrder: number;
  conditionType: string;
  conditionsJson: string; // JSON string from API
  actionsJson: string; // JSON string from API
  status: string;
}

// в”Ђв”Ђ AI Providers в”Ђв”Ђ

export type ProviderProtocol =
  | "OPENAI_COMPATIBLE"
  | "ANTHROPIC"
  | "YANDEXGPT"
  | "CUSTOM_HTTP";
export type ProviderStatus = "ACTIVE" | "HEALTHY" | "WARNING" | "ERROR" | "DISABLED";

export interface AIProvider {
  id: string;
  name: string;
  protocol: ProviderProtocol;
  endpointUrl: string;
  hasApiKey: boolean;
  model: string | null;
  status: ProviderStatus;
  active: boolean;
  lastTestedAt: string | null;
  lastTestResult: string | null;
  lastError: string | null;
}

// в”Ђв”Ђ Prompts в”Ђв”Ђ

export type PromptType = "CLASSIFIER" | "GUIDE_GENERATOR";
export type PromptStatus = "ACTIVE" | "DRAFT" | "ARCHIVED";

export interface Prompt {
  id: string;
  name: string;
  code?: string;
  description?: string;
  stage?: string;
  promptMode?: string;
  type: PromptType;
  content: string;
  systemPrompt?: string;
  outputSchema?: string;
  providerRoute?: string;
  modelName?: string;
  fallbackModel?: string | null;
  variablesJson: string | null; // JSON string from API, not an array
  version: string;
  versionNum?: number;
  status: PromptStatus;
  avgTokens: number;
  approveRate: number;
  lastEditedAt: string | null;
}

// в”Ђв”Ђ Chain Config в”Ђв”Ђ

export interface ChainConfig {
  includeReplies: boolean;
  timeWindowMinutes: number;
  minMessagesForProcessing: number;
  maxMessagesPerChain: number;
}

// в”Ђв”Ђ Monitor в”Ђв”Ђ

export interface TokenSummary {
  totalTokensToday: number;
  totalCostToday: number;
  tokensByProvider: { provider: string; tokens: number; cost: number }[];
}

export interface QueueStatus {
  queued: number;
  running: number;
  stuck: number;
  paused: boolean; // boolean, not number
}

// в”Ђв”Ђ Pagination в”Ђв”Ђ

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  number?: number;
  size: number;
  totalElements: number;
  totalPages: number;
  counts?: {
    total: number;
    byType: Record<string, number>;
    byStatus: Record<string, number>;
  };
}

// в”Ђв”Ђ Pipeline / KPI (no backend yet, kept for Dashboard) в”Ђв”Ђ

export type PipelineStage =
  | "Ingest"
  | "Chain Build"
  | "Classify"
  | "Generate Guide"
  | "Review"
  | "Publish";

export interface PipelineStageStatus {
  stage: PipelineStage;
  tasksCount: number;
  avgTimeMs: number;
  errorsCount: number;
  status: "Healthy" | "Warning" | "Error";
}

export interface KpiMetric {
  label: string;
  value: string | number;
  change?: string;
  trend?: "up" | "down" | "flat";
}

// в”Ђв”Ђ Settings в”Ђв”Ђ

export type PublicationMode = "Automatic" | "With moderation" | "Mixed";
export type ProcessingMode = "New only" | "Full backfill";

export interface AppSettings {
  activeProviderId: string | null;
  publication: {
    targetGroupId: string | null;
    targetGroupTitle: string | null;
    mode: PublicationMode;
  };
  processing: {
    mode: ProcessingMode;
    pollIntervalSeconds: number;
    chainWindow: {
      includeReplies: boolean;
      timeWindowMinutes: number;
      minMessagesForProcessing: number;
      maxMessagesPerChain: number;
    };
  };
  filters: {
    blacklistWords: string[];
    skipBots: boolean;
    minMessageLength: number;
  };
  limits: {
    dailyTokenLimit: number;
    monthlyTokenLimit: number;
    alertThresholdPercent: number;
  };
  notifications: {
    telegramChat: string | null;
    webhookUrl: string | null;
  };
}

// в”Ђв”Ђ Incidents (no backend yet, kept for monitoring) в”Ђв”Ђ

export interface Incident {
  id: string;
  timestamp: string;
  stage: PipelineStage;
  object: string;
  error: string;
  severity: "Error" | "Warning";
}

// в”Ђв”Ђ Pipeline Traces в”Ђв”Ђ

export type TraceStage =
  | "TELEGRAM_READ"
  | "RULES"
  | "SIGNAL_SCORING"
  | "CLASSIFICATION"
  | "CLASSIFIER"
  | "LLM_CLASSIFIER"
  | "GUIDE_GENERATION"
  | "MODERATION"
  | "CHAIN_BUILDING";

export type TraceStatus = "SUCCESS" | "PASSED" | "SKIPPED" | "FAILED" | "REJECTED" | "PENDING" | "COMPLETED";

export interface PipelineTrace {
  id: string;
  traceId: string;
  messageId: string | null;
  groupId: string | null;
  stage: TraceStage;
  status: TraceStatus;
  inputData: string | null;
  outputData: string | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  ruleId: string | null;
  classifierId: string | null;
  promptId: string | null;
  providerId: string | null;
  model: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  score: number | null;
  confidence: number | null;
  reason: string | null;
  entityType: string | null;
  entityName: string | null;
  entityVersion: string | null;
  configSnapshotJson: string | null;
  tuningHint: string | null;
}

export interface PipelineTuningCase {
  traceRowId: number;
  traceId: string;
  traceCreatedAt: string;
  stage: TraceStage;
  status: TraceStatus;
  entityType: string | null;
  entityName: string | null;
  entityVersion: string | null;
  messageId: number | null;
  groupId: number | null;
  messageDate: string | null;
  processingStatus: string | null;
  guideId: number | null;
  messagePreview: string | null;
  messageText: string | null;
  ruleId: number | null;
  classifierId: number | null;
  promptId: number | null;
  providerId: number | null;
  model: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  score: number | null;
  confidence: number | null;
  guidePotentialScore: number | null;
  problemSignalScore: number | null;
  painScore: number | null;
  urgencyScore: number | null;
  willingnessToPayScore: number | null;
  technicalDepthScore: number | null;
  spamScore: number | null;
  reason: string | null;
  errorMessage: string | null;
  tuningHint: string | null;
  agentFocus: string[];
  inputData: string | null;
  outputData: string | null;
  configSnapshotJson: string | null;
  ruleResultJson: string | null;
  signalBreakdown: string | null;
  classifierResultJson: string | null;
  messageIntelligenceJson: string | null;
}

export interface FlowMetrics {
  messagesRead: number;
  rulesPassed: number;
  classified: number;
  sentToLlm: number;
  guidesCreated: number;
  sentToModeration: number;
  approved: number;
  rejected: number;
  errors: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCostUsd: number;
}

// в”Ђв”Ђ Status display helpers в”Ђв”Ђ

/** Map backend API status values to StatusBadge-friendly display values */
export function displayAccountStatus(status: string): string {
  const map: Record<string, string> = {
    CONNECTED: "Онлайн",
    WAITING_CODE: "Ожидает код",
    WAITING_PASSWORD: "Ожидает пароль",
    ERROR: "Ошибка",
    DISABLED: "Отключён",
    DISCONNECTED: "Отключён",
  };
  return map[status] ?? status;
}
export function displayGuideStatus(status: string): string {
  const map: Record<string, string> = {
    DRAFT: "Черновик",
    NEW: "Новый",
    PROCESSING: "В работе",
    NEEDS_REVIEW: "Нужна проверка",
    APPROVED: "Одобрен",
    PUBLISHED: "Опубликован",
    FAILED: "Ошибка",
    REJECTED: "Отклонён",
  };
  return map[status] ?? status;
}

export function displayProviderStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Активен",
    HEALTHY: "Исправен",
    WARNING: "Предупреждение",
    ERROR: "Ошибка",
    DISABLED: "Отключён",
  };
  return map[status] ?? status;
}

export function displayClassifierStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Исправен",
    DRAFT: "Нужна проверка",
    DISABLED: "Отключён",
  };
  return map[status] ?? status;
}

export function displayPromptStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Исправен",
    DRAFT: "Нужна проверка",
    ARCHIVED: "Отключён",
  };
  return map[status] ?? status;
}

/** Try to parse a JSON string from the API; returns null on failure */
export function tryParseJson<T>(json: string | null): T | null {
  if (!json) return null;
  try {
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
}
