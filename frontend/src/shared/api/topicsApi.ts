import { useQuery } from "@tanstack/react-query";
import { getJsonAuth } from "./http";
import type { Topic } from "../types";

interface TopicDto {
  chatId: number;
  forumTopicId: number;
  messageThreadId: number;
  name: string;
  general: boolean;
}

function normalizeTopic(dto: TopicDto): Topic {
  return {
    chatId: String(dto.chatId),
    forumTopicId: String(dto.forumTopicId),
    messageThreadId: String(dto.messageThreadId),
    name: dto.name,
    general: dto.general,
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
        `/telegram/chats/${telegramChatId!}/topics?limit=${limit}`
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
