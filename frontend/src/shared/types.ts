// Domain types for NeuroInfoGrinder
// Matches actual backend API responses exactly

// ── Accounts ──

export type AccountStatus =
  | "CONNECTED"
  | "WAITING_CODE"
  | "WAITING_PASSWORD"
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
}

// ── Groups ──

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
  messagesPerDay: number;
  guidesFound: number;
  lastReadAt: string | null;
  lastReadMessageId: string | null;
}

// ── Topics (from Telegram TDLib) ──

export interface Topic {
  chatId: string;
  forumTopicId: string;
  messageThreadId: string;
  name: string;
  general: boolean;
}

// ── Messages (no backend endpoint yet, kept for ChatViewer) ──

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

export interface Message {
  id: string;
  groupId: string;
  groupTitle: string;
  telegramMessageId: string;
  author: string;
  authorTelegramId: string;
  isBot: boolean;
  text: string;
  replyToMessageId?: string;
  replyCount: number;
  timestamp: string;
  processingStatus: MessageStatus;
  guideId?: string;
  topicName?: string;
  topicId?: string;
  telegramMessageUrl?: string | null;
  hasMedia: boolean;
  signalScore?: number | null;
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

// ── Guides ──

export type GuideStatus =
  | "DRAFT"
  | "PROCESSING"
  | "APPROVED"
  | "PUBLISHED"
  | "FAILED"
  | "REJECTED";

export interface SourceMessage {
  messageId: string;
  groupId: string | null;
  telegramChatId: string | null;
  telegramMessageId: string | null;
  senderDisplayName: string | null;
  senderUsername: string | null;
  senderTelegramUserId: string | null;
  senderNameSource: string | null;
  text: string | null;
  textEntities: {
    type: string | null;
    offset: number | null;
    length: number | null;
    url: string | null;
    text: string | null;
  }[];
  usedInPrompt: boolean;
  relation: string | null;
  replyToTelegramMessageId: string | null;
  topicId: string | null;
  topicName: string | null;
  internalMessageUrl: string | null;
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

export interface Guide {
  id: string;
  title: string;
  sourceGroupId: string | null;
  sourceGroupTitle: string | null;
  rootMessageId: string | null;
  content: string | null;
  contentMarkdown: string | null;
  rawResponse: string | null;
  regeneratedFromGuideId: string | null;
  confidence: number;
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
  tags: string[];
  generationError: string | null;
  duplicateScore: number | null;
  duplicateOfGuideId: string | null;
  sourceMessages: SourceMessage[];
  llmRequest: GuideLlmRequest | null;
  relatedGuideIds: string[];
  possibleDuplicateIds: string[];
}

// ── Classifiers ──

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

// ── Rules ──

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

// ── AI Providers ──

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
  lastTestedAt: string | null;
  lastTestResult: string | null;
  lastError: string | null;
}

// ── Prompts ──

export type PromptType = "CLASSIFIER" | "GUIDE_GENERATOR";
export type PromptStatus = "ACTIVE" | "DRAFT" | "ARCHIVED";

export interface Prompt {
  id: string;
  name: string;
  type: PromptType;
  content: string;
  variablesJson: string | null; // JSON string from API, not an array
  version: string;
  status: PromptStatus;
  avgTokens: number;
  approveRate: number;
  lastEditedAt: string | null;
}

// ── Chain Config ──

export interface ChainConfig {
  includeReplies: boolean;
  timeWindowMinutes: number;
  minMessagesForProcessing: number;
  maxMessagesPerChain: number;
}

// ── Monitor ──

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

// ── Pagination ──

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Pipeline / KPI (no backend yet, kept for Dashboard) ──

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

// ── Settings ──

export type PublicationMode = "Automatic" | "With moderation" | "Mixed";
export type ProcessingMode = "New only" | "Full backfill";

export interface AppSettings {
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

// ── Incidents (no backend yet, kept for monitoring) ──

export interface Incident {
  id: string;
  timestamp: string;
  stage: PipelineStage;
  object: string;
  error: string;
  severity: "Error" | "Warning";
}

// ── Pipeline Traces ──

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

// ── Status display helpers ──

/** Map backend API status values to StatusBadge-friendly display values */
export function displayAccountStatus(status: string): string {
  const map: Record<string, string> = {
    CONNECTED: "Online",
    WAITING_CODE: "Waiting code",
    WAITING_PASSWORD: "Waiting password",
    ERROR: "Error",
    DISABLED: "Disabled",
    DISCONNECTED: "Disconnected",
  };
  return map[status] ?? status;
}

export function displayGuideStatus(status: string): string {
  if (status === "DRAFT") return "Черновик";
  if (status === "PROCESSING") return "В работе";
  if (status === "APPROVED") return "Одобрен";
  if (status === "PUBLISHED") return "Опубликован";
  if (status === "FAILED") return "Ошибка";
  if (status === "REJECTED") return "Отклонён";
  const map: Record<string, string> = {
    NEW: "Новый",
    PROCESSING: "В работе",
    NEEDS_REVIEW: "На проверке",
    APPROVED: "Одобрен",
    PUBLISHED: "Опубликован",
    FAILED: "Ошибка",
    REJECTED: "Отклонён",
  };
  return map[status] ?? status;
}

export function displayProviderStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Healthy",
    HEALTHY: "Healthy",
    WARNING: "Warning",
    ERROR: "Error",
    DISABLED: "Disabled",
  };
  return map[status] ?? status;
}

export function displayClassifierStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Healthy",
    DRAFT: "Needs review",
    DISABLED: "Disabled",
  };
  return map[status] ?? status;
}

export function displayPromptStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Healthy",
    DRAFT: "Needs review",
    ARCHIVED: "Disabled",
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
