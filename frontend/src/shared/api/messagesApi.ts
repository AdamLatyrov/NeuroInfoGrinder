import { useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import { getMessageRefreshSeconds } from "../ui-settings";
import type { Message, MessageChain, PaginatedResponse } from "../types";

interface MessageDto {
  id: number;
  telegramMessageId: number;
  groupId: number;
  senderName: string | null;
  senderTelegramUserId: number | null;
  isBot: boolean;
  text: string | null;
  replyToMessageId: number | null;
  replyCount: number;
  processingStatus: string;
  guideId: number | null;
  topicName: string | null;
  topicId: number | null;
  telegramMessageUrl: string | null;
  date: string | null;
  signalScore: number | null;
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

function normalizeMessage(dto: MessageDto): Message {
  return {
    id: String(dto.id),
    groupId: String(dto.groupId),
    groupTitle: "",
    telegramMessageId: String(dto.telegramMessageId),
    author: dto.senderName ?? "Unknown",
    authorTelegramId:
      dto.senderTelegramUserId != null ? String(dto.senderTelegramUserId) : "",
    isBot: dto.isBot,
    text: dto.text ?? "",
    replyToMessageId:
      dto.replyToMessageId != null ? String(dto.replyToMessageId) : undefined,
    replyCount: dto.replyCount,
    timestamp: dto.date ?? "",
    processingStatus: dto.processingStatus as Message["processingStatus"],
    guideId: dto.guideId != null ? String(dto.guideId) : undefined,
    topicName: dto.topicName ?? undefined,
    topicId: dto.topicId != null ? String(dto.topicId) : undefined,
    telegramMessageUrl: dto.telegramMessageUrl,
    hasMedia: false,
    signalScore: dto.signalScore,
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
    refetchInterval: () =>
      options?.disablePolling ? false : getMessageRefreshSeconds() * 1000,
    refetchIntervalInBackground: true,
    refetchOnMount: "always",
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
      postJsonAuth<void>(`/groups/${groupId!}/messages/sync`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages", groupId] });
    },
  });
}
