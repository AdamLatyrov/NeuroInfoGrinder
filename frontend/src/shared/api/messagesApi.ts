import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import { getMessageRefreshSeconds } from "../ui-settings";
import type { Message, MessageChain, MessageLink, PaginatedResponse } from "../types";

export interface MessagePipelineDetail {
  rawId: number;
  datasetMessageId: number | null;
  replayRunMessageId: number | null;
  chatId: number | null;
  chatTitle: string | null;
  topicId: number | null;
  threadId: number | null;
  messageDate: string | null;
  text: string | null;
  textUnavailableReason: string | null;
  appMessageUrl?: string | null;
  telegramMessageUrl?: string | null;
  telegramLinkAvailable?: boolean;
  telegramLinkReason?: string | null;
  processable?: boolean;
  processingState?: string | null;
  activeDialog?: boolean;
  autoPipelineEnabled?: boolean;
  pipelineStatus: string;
  candidateType?: string | null;
  material?: {
    materialId: number;
    title: string;
    type: string;
    status: string;
    url: string;
  } | null;
  singleMessage?: Record<string, unknown>;
  discussionSegment?: {
    segmentId: number;
    sourceCount: number;
    score: number | null;
    decision: string | null;
    materialType: string | null;
    signals: unknown[];
    suppressionReasons: unknown[];
    rejectionReason?: string | null;
    sources: Array<{
      orderIndex: number;
      rawId: number | null;
      datasetMessageId: number | null;
      replayRunMessageId: number | null;
      messageDate: string | null;
      role: string | null;
      text: string | null;
      selected?: boolean;
    }>;
  } | null;
  cluster?: Record<string, unknown> | null;
  llmJudge?: Record<string, unknown>;
  generation?: Record<string, unknown>;
  traceStages: Array<Record<string, unknown>>;
  providerCalls: Array<Record<string, unknown>>;
  rejectionReasons: string[];
  timeline: Array<{
    stage: string;
    status: string;
    timestamp: string | null;
    durationMs: number | null;
    durationLabel: string;
    reason: string | null;
    score: number | string | null;
  }>;
}

interface MessageLinkDto {
  id: number;
  messageId: number;
  url: string;
  normalizedUrl: string | null;
  domain: string | null;
  anchorText: string | null;
  source: string;
  entityType: string;
  offsetStart: number | null;
  offsetEnd: number | null;
  hidden: boolean;
  visibleUrl: boolean;
  telegramLink: boolean;
  referralLike: boolean;
}

interface MessageDto {
  id: number;
  telegramMessageId: number;
  groupId: number;
  telegramChatId?: number | null;
  senderName: string | null;
  senderTelegramUserId: number | null;
  isBot: boolean;
  text: string | null;
  caption?: string | null;
  contentType?: string | null;
  replyToMessageId: number | null;
  replyCount: number;
  processingStatus: string;
  guideId: number | null;
  topicName: string | null;
  topicId: number | null;
  telegramMessageUrl: string | null;
  date: string | null;
  hasMedia?: boolean;
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
  links?: MessageLinkDto[];
  signalScore: number | null;
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
  classifierScore: number | null;
  classifierReason: string | null;
  classifierResultJson: string | null;
  classificationContextHash: string | null;
  signalBreakdown: string | null;
  ruleResultJson: string | null;
}

interface MessageChainDto {
  chainId: number;
  groupId: number;
  rootMessageId: number;
  messages: MessageDto[];
  chainType: string;
}

export interface MessageSyncResponse {
  scheduled: boolean;
  reason:
    | "scheduled"
    | "already_running"
    | "cooldown"
    | "executor_saturated"
    | "telegram_not_ready"
    | "group_not_found"
    | "disabled_group"
    | "error"
    | string;
  groupId: number | null;
}

function normalizeMessage(dto: MessageDto): Message {
  const links: MessageLink[] = (dto.links ?? []).map((link) => ({
    id: String(link.id),
    messageId: String(link.messageId),
    url: link.url,
    normalizedUrl: link.normalizedUrl,
    domain: link.domain,
    anchorText: link.anchorText,
    source: link.source,
    entityType: link.entityType,
    offsetStart: link.offsetStart,
    offsetEnd: link.offsetEnd,
    hidden: link.hidden,
    visibleUrl: link.visibleUrl,
    telegramLink: link.telegramLink,
    referralLike: link.referralLike,
  }));

  return {
    id: String(dto.id),
    groupId: String(dto.groupId),
    groupTitle: "",
    telegramChatId:
      dto.telegramChatId != null ? String(dto.telegramChatId) : null,
    telegramMessageId: String(dto.telegramMessageId),
    author: dto.senderName ?? "Unknown",
    authorTelegramId:
      dto.senderTelegramUserId != null ? String(dto.senderTelegramUserId) : "",
    isBot: dto.isBot,
    text: dto.text ?? "",
    caption: dto.caption ?? null,
    contentType: dto.contentType ?? null,
    replyToMessageId:
      dto.replyToMessageId != null ? String(dto.replyToMessageId) : undefined,
    replyCount: dto.replyCount,
    timestamp: dto.date ?? "",
    processingStatus: dto.processingStatus as Message["processingStatus"],
    guideId: dto.guideId != null ? String(dto.guideId) : undefined,
    topicName: dto.topicName ?? undefined,
    topicId: dto.topicId != null ? String(dto.topicId) : undefined,
    telegramMessageUrl: dto.telegramMessageUrl,
    hasMedia: dto.hasMedia ?? false,
    hasLinks: dto.hasLinks ?? links.length > 0,
    mediaType: dto.mediaType ?? null,
    hasVoice: dto.hasVoice ?? false,
    hasGif: dto.hasGif ?? false,
    hasDocument: dto.hasDocument ?? false,
    hasPhoto: dto.hasPhoto ?? false,
    hasVideo: dto.hasVideo ?? false,
    fileName: dto.fileName ?? null,
    mimeType: dto.mimeType ?? null,
    durationSeconds: dto.durationSeconds ?? null,
    mediaJson: dto.mediaJson ?? null,
    links,
    signalScore: dto.signalScore,
    guidePotentialScore: dto.guidePotentialScore,
    problemSignalScore: dto.problemSignalScore,
    painScore: dto.painScore,
    urgencyScore: dto.urgencyScore,
    willingnessToPayScore: dto.willingnessToPayScore,
    technicalDepthScore: dto.technicalDepthScore,
    spamScore: dto.spamScore,
    meaningSummary: dto.meaningSummary,
    problemStatement: dto.problemStatement,
    solutionHint: dto.solutionHint,
    mentionedToolsJson: dto.mentionedToolsJson,
    mentionedPricesJson: dto.mentionedPricesJson,
    mentionedErrorsJson: dto.mentionedErrorsJson,
    intelligenceReason: dto.intelligenceReason,
    clusterCandidate: dto.clusterCandidate,
    embeddingStatus: dto.embeddingStatus,
    messageIntelligenceJson: dto.messageIntelligenceJson,
    classifierScore: dto.classifierScore,
    classifierReason: dto.classifierReason ?? undefined,
    classifierResultJson: dto.classifierResultJson ?? undefined,
    classificationContextHash: dto.classificationContextHash ?? undefined,
    signalBreakdown: dto.signalBreakdown ?? undefined,
    ruleResultJson: dto.ruleResultJson ?? undefined,
  };
}

/** Simple query that fetches messages page from DB, auto-refreshes based on UI settings */
interface GroupMessagesQueryOptions {
  disablePolling?: boolean;
}

export function useGroupMessagesQuery(
  groupId: string | undefined,
  topicId?: string | null,
  options?: GroupMessagesQueryOptions
) {
  return useQuery({
    queryKey: ["group-messages", groupId, topicId],
    queryFn: async () => {
      let url = `/groups/${groupId!}/messages?page=0&size=200&sort=messageDate,desc`;
      if (topicId) {
        url += `&topicId=${topicId}`;
      }
      const response = await getJsonAuth<PaginatedResponse<MessageDto>>(url);
      return {
        content: response.content.map(normalizeMessage),
        totalElements: response.totalElements,
        totalPages: response.totalPages,
      };
    },
    enabled: !!groupId,
    // Keep the current messages visible during background refetch so the list
    // does not flash/jump while new live messages arrive.
    placeholderData: keepPreviousData,
    staleTime: 5_000,
    refetchInterval: () =>
      options?.disablePolling ? false : getMessageRefreshSeconds() * 1000,
    refetchIntervalInBackground: false,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: true,
  });
}

export function useMessageChainQuery(
  groupId: string | undefined,
  messageId: string | undefined
) {
  return useQuery({
    queryKey: ["message-chain", groupId, messageId],
    queryFn: async (): Promise<MessageChain> => {
      const response = await getJsonAuth<MessageChainDto>(
        `/groups/${groupId!}/messages/${messageId!}/chain`
      );
      return {
        id: String(response.chainId),
        groupId: String(response.groupId),
        rootMessageId: String(response.rootMessageId),
        messages: response.messages.map(normalizeMessage),
        totalMessages: response.messages.length,
        usedInLlm: response.chainType !== "thread_root",
      };
    },
    enabled: !!groupId && !!messageId,
  });
}

export function useMessagePipelineDetailQuery(rawId: string | undefined) {
  return useQuery({
    queryKey: ["message-pipeline-detail", rawId],
    queryFn: () => getJsonAuth<MessagePipelineDetail>(`/messages/${rawId!}/pipeline-detail`),
    enabled: !!rawId,
    staleTime: 10_000,
  });
}

export function useMessageByIdQuery(
  groupId: string | undefined,
  messageId: string | undefined
) {
  return useQuery({
    queryKey: ["group-message", groupId, messageId],
    queryFn: async (): Promise<Message> => {
      const response = await getJsonAuth<MessageDto>(
        `/groups/${groupId!}/messages/${messageId!}`
      );
      return normalizeMessage(response);
    },
    enabled: !!groupId && !!messageId,
  });
}

export function useEnqueueMessageMutation(groupId: string | undefined) {
  return useMutation({
    mutationFn: (messageId: string) =>
      postJsonAuth<void>(`/groups/${groupId!}/messages/${messageId}/enqueue`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages", groupId] });
    },
  });
}

export function useSyncMessagesMutation(groupId: string | undefined) {
  return useMutation({
    mutationFn: () =>
      postJsonAuth<MessageSyncResponse>(`/groups/${groupId!}/messages/sync`, {}),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ["group-messages", groupId] });
      if (response.scheduled) {
        window.setTimeout(() => {
          queryClient.invalidateQueries({ queryKey: ["group-messages", groupId] });
          queryClient.invalidateQueries({ queryKey: ["groups"] });
        }, 4_000);
      }
    },
  });
}
