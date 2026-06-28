import { useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { Topic } from "../types";

interface TopicDto {
  chatId: number;
  forumTopicId: number;
  messageThreadId: number;
  name: string;
  general: boolean;
  titleSource: string | null;
  syncState: string | null;
}

function normalizeTopic(dto: TopicDto): Topic {
  return {
    chatId: String(dto.chatId),
    forumTopicId: String(dto.forumTopicId),
    messageThreadId: String(dto.messageThreadId),
    name: dto.name,
    general: dto.general,
    titleSource: dto.titleSource,
    syncState: dto.syncState,
  };
}

export function useTopicsQuery(
  telegramChatId: string | undefined,
  limit: number = 100
) {
  return useQuery({
    queryKey: ["topics", telegramChatId, limit],
    queryFn: async () => {
      const response = await getJsonAuth<TopicDto[]>(
        `/api/v2/telegram/topics?accountId=1&chatId=${telegramChatId!}`
      );
      return response.map(normalizeTopic);
    },
    enabled: !!telegramChatId,
    refetchInterval: 15_000,
    refetchIntervalInBackground: true,
    refetchOnMount: "always",
    refetchOnReconnect: true,
  });
}

export function useSyncTopicsMutation(telegramChatId: string | undefined) {
  return useMutation({
    mutationFn: async () => postJsonAuth(`/api/v2/telegram/sync/topics/start?accountId=1&chatId=${telegramChatId}&limit=50`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["topics", telegramChatId] }),
  });
}

export function useReconcileTopicsMutation(telegramChatId: string | undefined) {
  return useMutation({
    mutationFn: async () => postJsonAuth(`/api/v2/telegram/topics/reconcile?accountId=1&chatId=${telegramChatId}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["topics", telegramChatId] });
      queryClient.invalidateQueries({ queryKey: ["messages"] });
    },
  });
}
