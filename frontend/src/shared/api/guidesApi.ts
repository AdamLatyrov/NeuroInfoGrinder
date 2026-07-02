import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, patchJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type {
  Guide,
  GuideLlmRequest,
  ContentType,
  PaginatedResponse,
  SourceMessage,
} from "../types";

export interface GuidesFilters {
  page?: number;
  size?: number;
  status?: string;
  minUsefulness?: number;
  maxUsefulness?: number;
  sort?: GuidesSort;
}

export interface MaterialsFilters extends GuidesFilters {
  contentType?: ContentType | string;
}

export type GuidesSort = "createdAt_desc" | "createdAt_asc";

interface GuideSummaryDto {
  id: number;
  title: string;
  groupId: number | null;
  groupTitle: string | null;
  rootMessageId: number | null;
  contentType: ContentType | string | null;
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
  providerId: number | null;
  model: string | null;
  classifierId: number | null;
  promptId: number | null;
  promptVersion: string | null;
  status: string;
  duplicateOfId: number | null;
  duplicateScore: number | null;
  confidence: number | null;
  usefulnessScore: number | null;
  totalTokens: number | null;
  estimatedCostUsd: number | null;
  tags: string[] | null;
  generationError: string | null;
  sourceCount: number | null;
  publicationStatus: string | null;
  publishedAt: string | null;
  createdAt: string | null;
}

interface SourceMessageDto {
  rawId: number | null;
  datasetMessageId: number | null;
  groupId: number | null;
  telegramChatId: number | null;
  messageId: number;
  telegramMessageId: number | null;
  chatTitle: string | null;
  messageDate: string | null;
  senderDisplayName: string | null;
  senderUsername: string | null;
  senderTelegramUserId: number | null;
  senderNameSource: string | null;
  text: string | null;
  textUnavailableReason: string | null;
  preview: string | null;
  textEntities:
    | {
        type: string | null;
        offset: number | null;
        length: number | null;
        url: string | null;
        text: string | null;
      }[]
    | null;
  usedInPrompt: boolean;
  relation: string | null;
  replyToTelegramMessageId: number | null;
  topicId: number | null;
  topicName: string | null;
  internalMessageUrl: string | null;
  appMessageUrl: string | null;
  telegramMessageUrl: string | null;
  telegramLinkAvailable: boolean;
  telegramLinkReason: string | null;
}

interface MaterialProviderCallDto {
  id: number;
  stage: string | null;
  providerId: number | null;
  model: string | null;
  status: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cachedTokens: number | null;
  estimatedCostUsd: number | null;
  latencyMs: number | null;
  httpStatus: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string | null;
}

interface MaterialTraceStageDto {
  id: string;
  rawId: number | null;
  runId: number | null;
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

interface MaterialRunInfoDto {
  id: number;
  status: string | null;
  terminalReason: string | null;
  totalMessages: number | null;
  processedMessages: number | null;
  providerCallsTotal: number | null;
  createdAt: string | null;
  finishedAt: string | null;
}

interface GuideLlmRequestDto {
  providerId: number | null;
  model: string | null;
  promptId: number | null;
  promptVersion: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  estimatedCostUsd: number | null;
}

interface GuideDetailDto extends GuideSummaryDto {
  content: string | null;
  contentMarkdown: string | null;
  rawResponse: string | null;
  regeneratedFromGuideId: number | null;
  sourceMessages: SourceMessageDto[] | null;
  llmRequest: GuideLlmRequestDto | null;
  providerCalls: MaterialProviderCallDto[] | null;
  traceStages: MaterialTraceStageDto[] | null;
  run: MaterialRunInfoDto | null;
  candidate: { type: string | null } | null;
  relatedGuideIds: number[] | null;
  possibleDuplicateIds: number[] | null;
}

function normalizeSourceMessage(dto: SourceMessageDto): SourceMessage {
  return {
    rawId: dto.rawId != null ? String(dto.rawId) : null,
    datasetMessageId: dto.datasetMessageId != null ? String(dto.datasetMessageId) : null,
    messageId: String(dto.messageId),
    groupId: dto.groupId != null ? String(dto.groupId) : null,
    telegramChatId: dto.telegramChatId != null ? String(dto.telegramChatId) : null,
    telegramMessageId:
      dto.telegramMessageId != null ? String(dto.telegramMessageId) : null,
    chatTitle: dto.chatTitle ?? null,
    messageDate: dto.messageDate ?? null,
    senderDisplayName: dto.senderDisplayName,
    senderUsername: dto.senderUsername,
    senderTelegramUserId:
      dto.senderTelegramUserId != null ? String(dto.senderTelegramUserId) : null,
    senderNameSource: dto.senderNameSource,
    text: dto.text,
    textUnavailableReason: dto.textUnavailableReason ?? null,
    preview: dto.preview ?? null,
    textEntities: Array.isArray(dto.textEntities) ? dto.textEntities : [],
    usedInPrompt: dto.usedInPrompt,
    relation: dto.relation,
    replyToTelegramMessageId:
      dto.replyToTelegramMessageId != null ? String(dto.replyToTelegramMessageId) : null,
    topicId: dto.topicId != null ? String(dto.topicId) : null,
    topicName: dto.topicName,
    internalMessageUrl: dto.internalMessageUrl,
    appMessageUrl: dto.appMessageUrl ?? dto.internalMessageUrl,
    telegramMessageUrl: dto.telegramMessageUrl,
    telegramLinkAvailable: dto.telegramLinkAvailable,
    telegramLinkReason: dto.telegramLinkReason,
  };
}

function normalizeProviderCall(dto: MaterialProviderCallDto) {
  return {
    id: String(dto.id),
    stage: dto.stage ?? null,
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model ?? null,
    status: dto.status ?? null,
    inputTokens: dto.inputTokens ?? 0,
    outputTokens: dto.outputTokens ?? 0,
    cachedTokens: dto.cachedTokens ?? 0,
    estimatedCostUsd: dto.estimatedCostUsd ?? 0,
    latencyMs: dto.latencyMs ?? null,
    httpStatus: dto.httpStatus ?? null,
    errorCode: dto.errorCode ?? null,
    errorMessage: dto.errorMessage ?? null,
    createdAt: dto.createdAt ?? null,
  };
}

function normalizeTraceStage(dto: MaterialTraceStageDto) {
  return {
    id: dto.id,
    rawId: dto.rawId != null ? String(dto.rawId) : null,
    runId: dto.runId != null ? String(dto.runId) : null,
    stage: dto.stage,
    stageName: dto.stageName ?? null,
    status: dto.status,
    reason: dto.reason ?? null,
    errorCode: dto.errorCode ?? null,
    errorMessage: dto.errorMessage ?? null,
    startedAt: dto.startedAt ?? null,
    finishedAt: dto.finishedAt ?? null,
    createdAt: dto.createdAt ?? null,
    updatedAt: dto.updatedAt ?? null,
    durationMs: dto.durationMs ?? null,
    outputJson: dto.outputJson ?? null,
  };
}

function normalizeRunInfo(dto: MaterialRunInfoDto | null) {
  if (!dto) return null;
  return {
    id: String(dto.id),
    status: dto.status ?? null,
    terminalReason: dto.terminalReason ?? null,
    totalMessages: dto.totalMessages ?? 0,
    processedMessages: dto.processedMessages ?? 0,
    providerCallsTotal: dto.providerCallsTotal ?? 0,
    createdAt: dto.createdAt ?? null,
    finishedAt: dto.finishedAt ?? null,
  };
}

function normalizeLlmRequest(dto: GuideLlmRequestDto | null): GuideLlmRequest | null {
  if (!dto) return null;
  return {
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model,
    promptId: dto.promptId != null ? String(dto.promptId) : null,
    promptVersion: dto.promptVersion,
    inputTokens: dto.inputTokens ?? 0,
    outputTokens: dto.outputTokens ?? 0,
    totalTokens: dto.totalTokens ?? 0,
    estimatedCostUsd: dto.estimatedCostUsd ?? 0,
  };
}

function cleanDisplayText(value: string | null | undefined): string | null {
  if (!value?.trim()) return null;
  const cleaned = value
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^(FAQ|Риск|Полезно знать|Полезное|Новость|Обновление|Справка|Предупреждение)\s*[:：]\s*(?=Практическ)/iu, "")
    .replace(/^Практический гайд по теме кластера\s*[:：-]?\s*/iu, "")
    .replace(/^Гайд по теме кластера\s*[:：-]?\s*/iu, "")
    .replace(/^Practical guide by cluster topic\s*[:：-]?\s*/iu, "")
    .replace(/^Guide angle\s*[:：-]?\s*/iu, "");
  if (!cleaned || /^[{[].*[\]}]$/.test(cleaned)) return null;
  return cleaned;
}

function isGenericDisplayTitle(value: string | null | undefined): boolean {
  const normalized = cleanDisplayText(value)?.toLocaleLowerCase("ru-RU") ?? "";
  return !normalized
    || normalized === "faq"
    || normalized === "полезное"
    || normalized === "практический гайд"
    || normalized === "гайд по теме кластера"
    || normalized.includes("faq практический гайд")
    || normalized.includes("практический гайд по теме кластера")
    || normalized.includes("practical guide by cluster topic");
}

function inferDisplayTitleFromContent(value: string | null | undefined): string | null {
  const cleaned = cleanDisplayText(value);
  if (!cleaned) return null;
  const normalized = cleaned.toLocaleLowerCase("ru-RU");
  const product = cleaned
    .match(/(?:название|name)\s*[:：]\s*([A-Za-zА-Яа-я0-9_.@/-][A-Za-zА-Яа-я0-9_.@/ -]{1,48})/i)?.[1]
    ?.replace(/\s+(что делает|для кого|github|как запустить).*$/iu, "")
    .trim();
  if (product) {
    if (normalized.includes("desktop") || normalized.includes("настольн") || normalized.includes("агент")) {
      return `${product}: desktop-клиент для AI-агентов`;
    }
    return product;
  }
  if (normalized.includes("verdent.ai") && normalized.includes("100 кредит")) return "verdent.ai: расход кредитов на Claude Opus";
  if (normalized.includes("runic") && (normalized.includes("генерац") || normalized.includes("изображ") || normalized.includes("фото"))) {
    return "Runic: пополнение в рублях и генерация изображений";
  }
  if (normalized.includes("runic") && (normalized.includes("новые модели") || normalized.includes("цены") || normalized.includes("openrouter"))) {
    return "Runic: новые модели и цены API";
  }
  if (normalized.includes("подписк") && normalized.includes("api") && normalized.includes("код")) return "Подписка vs API для кодинга";
  if (normalized.includes("итератив") && normalized.includes("планирован") && normalized.includes("gemini")) {
    return "Выбор AI-модели для стратегического планирования";
  }
  if (normalized.includes("smmplanner")) return "SMMplanner для автопостинга Instagram";
  return null;
}

function firstSpecificTitle(...values: Array<string | null | undefined>): string | null {
  for (const value of values) {
    const cleaned = cleanDisplayText(value);
    if (cleaned && !isGenericDisplayTitle(cleaned)) {
      return cleaned.split(/\s+/).slice(0, 14).join(" ");
    }
  }
  return null;
}

function normalizeGuide(dto: GuideSummaryDto | GuideDetailDto): Guide {
  const detail = dto as GuideDetailDto;
  const topicSummary = cleanDisplayText(dto.topicSummary) ?? dto.topicSummary ?? null;
  const contentSummary = cleanDisplayText(dto.contentSummary) ?? dto.contentSummary ?? null;
  const inferredTitle = inferDisplayTitleFromContent(
    contentSummary ?? topicSummary ?? detail.contentMarkdown ?? detail.content ?? null
  );
  const title = firstSpecificTitle(inferredTitle, dto.contentTitle, dto.topicLabel, dto.title) ?? dto.title;
  const topicLabel = inferredTitle ? title : firstSpecificTitle(dto.topicLabel) ?? title;
  const contentTitle = inferredTitle ? title : firstSpecificTitle(dto.contentTitle) ?? title;
  return {
    id: String(dto.id),
    title,
    sourceGroupId: dto.groupId != null ? String(dto.groupId) : null,
    sourceGroupTitle: dto.groupTitle ?? null,
    rootMessageId: dto.rootMessageId != null ? String(dto.rootMessageId) : null,
    contentType: normalizeContentType(dto.contentType),
    contentSubtype: dto.contentSubtype ?? null,
    topicLabel,
    topicSummary,
    contentTitle,
    contentSummary,
    normalizedTopicKey: dto.normalizedTopicKey ?? null,
    entities: Array.isArray(dto.entities) ? dto.entities : [],
    contentQualityScore: dto.contentQualityScore ?? null,
    importanceScore: dto.importanceScore ?? null,
    actionabilityScore: dto.actionabilityScore ?? null,
    noveltyScore: dto.noveltyScore ?? null,
    evidenceScore: dto.evidenceScore ?? null,
    riskScore: dto.riskScore ?? null,
    confidenceScore: dto.confidenceScore ?? null,
    noiseScore: dto.noiseScore ?? null,
    routingReason: dto.routingReason ?? null,
    safetyCategory: dto.safetyCategory ?? null,
    publicationKind: dto.publicationKind ?? null,
    content: detail.content ?? null,
    contentMarkdown: detail.contentMarkdown ?? null,
    rawResponse: detail.rawResponse ?? null,
    regeneratedFromGuideId:
      detail.regeneratedFromGuideId != null ? String(detail.regeneratedFromGuideId) : null,
    confidence: dto.confidence ?? null,
    usefulnessScore: dto.usefulnessScore ?? null,
    status: dto.status as Guide["status"],
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model ?? null,
    promptVersion: dto.promptVersion ?? null,
    inputTokens: detail.llmRequest?.inputTokens ?? 0,
    outputTokens: detail.llmRequest?.outputTokens ?? 0,
    totalTokens: dto.totalTokens ?? 0,
    estimatedCost: dto.estimatedCostUsd ?? 0,
    createdAt: dto.createdAt,
    publishedAt: dto.publishedAt,
    publicationStatus: dto.publicationStatus ?? "NOT_SENT",
    tags: Array.isArray(dto.tags) ? dto.tags : [],
    generationError: dto.generationError ?? null,
    sourceCount:
      dto.sourceCount
      ?? (Array.isArray(detail.sourceMessages) ? detail.sourceMessages.length : 0),
    duplicateScore: dto.duplicateScore,
    duplicateOfGuideId:
      dto.duplicateOfId != null ? String(dto.duplicateOfId) : null,
    sourceMessages: Array.isArray(detail.sourceMessages)
      ? detail.sourceMessages.map(normalizeSourceMessage)
      : [],
    llmRequest: normalizeLlmRequest(detail.llmRequest ?? null),
    providerCalls: Array.isArray(detail.providerCalls)
      ? detail.providerCalls.map(normalizeProviderCall)
      : [],
    traceStages: Array.isArray(detail.traceStages)
      ? detail.traceStages.map(normalizeTraceStage)
      : [],
    run: normalizeRunInfo(detail.run ?? null),
    candidate: detail.candidate ?? null,
    relatedGuideIds: Array.isArray(detail.relatedGuideIds)
      ? detail.relatedGuideIds.map(String)
      : [],
    possibleDuplicateIds: Array.isArray(detail.possibleDuplicateIds)
      ? detail.possibleDuplicateIds.map(String)
      : [],
  };
}

export function useGuidesQuery(filters?: GuidesFilters) {
  const page = filters?.page ?? 0;
  const size = filters?.size ?? 50;

  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: filters?.sort === "createdAt_asc" ? "createdAt,asc" : "createdAt,desc",
  });

  if (filters?.status) {
    params.set("status", filters.status);
  }
  if (filters?.minUsefulness != null) {
    params.set("minUsefulness", String(filters.minUsefulness));
  }
  if (filters?.maxUsefulness != null) {
    params.set("maxUsefulness", String(filters.maxUsefulness));
  }

  return useQuery({
    queryKey: [
      "guides",
      {
        page,
        size,
        status: filters?.status,
        minUsefulness: filters?.minUsefulness,
        maxUsefulness: filters?.maxUsefulness,
        sort: filters?.sort,
      },
    ],
    queryFn: async () => {
      const response = await getJsonAuth<PaginatedResponse<GuideSummaryDto>>(
        `/guides?${params.toString()}`
      );
      return {
        ...response,
        content: response.content.map(normalizeGuide),
      };
    },
  });
}

export function useMaterialsQuery(filters?: MaterialsFilters) {
  const page = filters?.page ?? 0;
  const size = filters?.size ?? 50;

  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: filters?.sort === "createdAt_asc" ? "createdAt,asc" : "createdAt,desc",
  });

  if (filters?.contentType) {
    params.set("contentType", filters.contentType);
  }
  if (filters?.status) {
    params.set("status", filters.status);
  }
  if (filters?.minUsefulness != null) {
    params.set("minUsefulness", String(filters.minUsefulness));
  }
  if (filters?.maxUsefulness != null) {
    params.set("maxUsefulness", String(filters.maxUsefulness));
  }

  return useQuery({
    queryKey: [
      "materials",
      {
        page,
        size,
        contentType: filters?.contentType,
        status: filters?.status,
        minUsefulness: filters?.minUsefulness,
        maxUsefulness: filters?.maxUsefulness,
        sort: filters?.sort,
      },
    ],
    queryFn: async () => {
      const response = await getJsonAuth<PaginatedResponse<GuideSummaryDto>>(
        `/materials?${params.toString()}`
      );
      return {
        ...response,
        content: response.content.map(normalizeGuide),
      };
    },
  });
}

function normalizeContentType(value: string | null | undefined): ContentType {
  const candidate = (value ?? "GUIDE").toUpperCase();
  const known: ContentType[] = [
    "GUIDE",
    "GENERATION",
    "ANSWER",
    "OTHER",
    "CLUSTER_SUMMARY",
    "TROUBLESHOOTING",
    "CHECKLIST",
    "RESOURCE_LIST",
    "SUMMARY",
    "PROMPT",
    "CODE_SNIPPET",
    "CASE_NOTE",
    "COMPARISON",
    "UNKNOWN",
    "NEWS",
    "USEFUL_INFO",
    "FAQ",
    "WARNING",
    "RISK_INSIGHT",
    "PRODUCT_UPDATE",
    "REFERENCE",
    "DISCUSSION_ONLY",
    "DEFERRED",
  ];
  return known.includes(candidate as ContentType) ? (candidate as ContentType) : "OTHER";
}

export function useGuideDetailQuery(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ["guides", id],
    queryFn: async () => {
      const dto = await getJsonAuth<GuideDetailDto>(`/guides/${id!}`);
      return normalizeGuide(dto);
    },
    enabled: enabled && !!id,
  });
}

export function useMaterialDetailQuery(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ["materials", id],
    queryFn: async () => {
      const dto = await getJsonAuth<GuideDetailDto>(`/materials/${id!}`);
      return normalizeGuide(dto);
    },
    enabled: enabled && !!id,
  });
}

export function useUpdateGuideStatusMutation() {
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const dto = await patchJsonAuth<GuideSummaryDto>(`/guides/${id}/status`, {
        status,
      });
      return normalizeGuide(dto);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["guides", variables.id] });
    },
  });
}

export function useUpdateGuideContentMutation() {
  return useMutation({
    mutationFn: async ({
      id,
      title,
      content,
      contentMarkdown,
    }: {
      id: string;
      title: string;
      content: string;
      contentMarkdown: string | null;
    }) => {
      const dto = await putJsonAuth<GuideSummaryDto>(`/guides/${id}/content`, {
        title,
        content,
        contentMarkdown,
      });
      return normalizeGuide(dto);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["guides", variables.id] });
    },
  });
}

export function useRegenerateGuideMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      return postJsonAuth<{ oldGuideId: number; newGuideId: number; status: string }>(
        `/guides/${id}/regenerate`,
        {}
      );
    },
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["guides", id] });
      queryClient.invalidateQueries({ queryKey: ["materials", id] });
    },
  });
}

export interface RegenerateGuidesResult {
  requested: number;
  succeeded: number;
  skipped: number;
  failed: number;
}

export function useRegenerateGuidesMutation() {
  return useMutation({
    mutationFn: async (ids: string[]): Promise<RegenerateGuidesResult> => {
      const results = await Promise.allSettled(
        ids.map((id) =>
          postJsonAuth<{ oldGuideId: number; newGuideId: number; status: string }>(
            `/guides/${id}/regenerate`,
            {}
          )
        )
      );

      return {
        requested: ids.length,
        succeeded: results.filter((result) => result.status === "fulfilled").length,
        skipped: 0,
        failed: results.filter((result) => result.status === "rejected").length,
      };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

export function useDeleteGuideMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      await deleteJsonAuth(`/guides/${id}`);
      return id;
    },
    onSuccess: (id) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["guides", id] });
      queryClient.invalidateQueries({ queryKey: ["materials", id] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

export function useDeleteMaterialMutation() {
  return useDeleteGuideMutation();
}

export function useBulkDeleteGuidesMutation() {
  return useMutation({
    mutationFn: async (ids: string[]) => {
      await postJsonAuth<{ deleted: number }>("/guides/bulk-delete", {
        ids: ids.map((id) => Number(id)),
      });
      return ids;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}
