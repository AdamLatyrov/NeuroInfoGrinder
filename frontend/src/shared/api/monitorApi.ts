import { useQuery } from "@tanstack/react-query";
import { getJsonAuth } from "./http";
import type { TokenSummary, QueueStatus } from "../types";

export function useTokenSummaryQuery() {
  return useQuery({
    queryKey: ["monitor", "tokens", "summary"],
    queryFn: () => getJsonAuth<TokenSummary>("/monitor/tokens/summary"),
    refetchInterval: 30_000,
  });
}

export function useQueueStatusQuery() {
  return useQuery({
    queryKey: ["monitor", "queue", "status"],
    queryFn: () => getJsonAuth<QueueStatus>("/monitor/queue/status"),
    refetchInterval: 15_000,
  });
}

export interface TokenDailyEntry {
  date: string;
  tokens: number;
  cost: number;
}

export function useTokenDailyQuery(days: number = 14) {
  return useQuery({
    queryKey: ["monitor", "tokens", "daily", { days }],
    queryFn: () =>
      getJsonAuth<TokenDailyEntry[]>(`/monitor/tokens/daily?days=${days}`),
  });
}

export interface GroupStatsEntry {
  groupId: string;
  groupTitle: string;
  messagesRead: number;
  chainsBuilt: number;
  guidesGenerated: number;
  guidesPublished: number;
  errorCount: number;
}

export function useGroupStatsQuery() {
  return useQuery({
    queryKey: ["monitor", "groups", "stats"],
    queryFn: () => getJsonAuth<GroupStatsEntry[]>("/monitor/groups/stats"),
  });
}
