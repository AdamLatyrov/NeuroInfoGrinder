import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, patchJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { Group, PaginatedResponse } from "../types";

interface GroupDto {
  id: number;
  telegramChatId: number;
  title: string;
  username: string | null;
  sourceType: "GROUP" | "DIRECT_CHAT" | "CHANNEL";
  category: string | null;
  forum: boolean;
  enabled: boolean;
  accountId: number | null;
  messagesPerDay: number | null;
  guidesFound: number;
  lastReadAt: string | null;
  lastReadMessageId: number | null;
  rawMessagesTotal: number | null;
  rawMessagesLast24h: number | null;
  latestRawMessageAt: string | null;
  topicsCount: number | null;
  autoPipelineEnabled: boolean | null;
  activeDialog: boolean | null;
  displayState: string | null;
  processingState: string | null;
  source: string | null;
}

function normalizeGroup(dto: GroupDto): Group {
  return {
    id: String(dto.id),
    telegramChatId: String(dto.telegramChatId),
    title: dto.title,
    username: dto.username,
    sourceType: dto.sourceType,
    category: dto.category,
    forum: dto.forum,
    enabled: dto.enabled,
    accountId: dto.accountId != null ? String(dto.accountId) : null,
    messagesPerDay: dto.messagesPerDay ?? null,
    guidesFound: dto.guidesFound,
    lastReadAt: dto.lastReadAt,
    lastReadMessageId: dto.lastReadMessageId != null ? String(dto.lastReadMessageId) : null,
    rawMessagesTotal: dto.rawMessagesTotal ?? null,
    rawMessagesLast24h: dto.rawMessagesLast24h ?? null,
    latestRawMessageAt: dto.latestRawMessageAt ?? null,
    topicsCount: dto.topicsCount ?? null,
    autoPipelineEnabled: Boolean(dto.autoPipelineEnabled),
    activeDialog: Boolean(dto.activeDialog),
    displayState: dto.displayState ?? null,
    processingState: dto.processingState ?? null,
    source: dto.source ?? null,
  };
}

export interface GroupsFilters {
  page?: number;
  size?: number;
  enabled?: boolean;
  search?: string;
  accountId?: string;
}

export function useGroupsQuery(filters?: GroupsFilters) {
  const page = filters?.page ?? 0;
  const size = filters?.size ?? 50;
  const search = filters?.search;

  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (filters?.enabled !== undefined) {
    params.set("enabled", String(filters.enabled));
  }
  if (search) {
    params.set("search", search);
  }
  if (filters?.accountId) {
    params.set("accountId", filters.accountId);
  }

  return useQuery({
    queryKey: ["groups", { page, size, enabled: filters?.enabled, search, accountId: filters?.accountId }],
    queryFn: async () => {
      const response = await getJsonAuth<PaginatedResponse<GroupDto>>(
        `/groups?${params.toString()}`
      );
      return {
        ...response,
        content: response.content.map(normalizeGroup),
      };
    },
    // Keep the previous list visible while refetching so chats do not flicker
    // or jump when background polling returns updated counters.
    placeholderData: keepPreviousData,
    staleTime: 15_000,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: true,
  });
}

function updateGroupsCache(
  updater: (group: Group) => Group,
  targetId?: string
) {
  const cacheEntries = queryClient.getQueriesData<PaginatedResponse<Group>>({
    queryKey: ["groups"],
  });

  const previousEntries = cacheEntries.map(([queryKey, data]) => [queryKey, data] as const);

  cacheEntries.forEach(([queryKey, data]) => {
    if (!data) return;
    queryClient.setQueryData<PaginatedResponse<Group>>(queryKey, {
      ...data,
      content: data.content.map((group) =>
        !targetId || group.id === targetId ? updater(group) : group
      ),
    });
  });

  return previousEntries;
}

export function useUpdateGroupMutation() {
  return useMutation({
    mutationFn: async ({ id, ...body }: Partial<Group> & { id: string }) => {
      const response = await patchJsonAuth<GroupDto>(`/groups/${id}`, body);
      return normalizeGroup(response);
    },
    onMutate: async ({ id, ...body }) => {
      await queryClient.cancelQueries({ queryKey: ["groups"] });
      const previousEntries = updateGroupsCache(
        (group) => ({
          ...group,
          ...body,
        }),
        id
      );
      return { previousEntries };
    },
    onError: (_error, _variables, context) => {
      context?.previousEntries?.forEach(([queryKey, data]) => {
        queryClient.setQueryData(queryKey, data);
      });
    },
    onSuccess: (updatedGroup) => {
      updateGroupsCache((group) => ({
        ...group,
        ...updatedGroup,
      }), updatedGroup.id);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });
}

export interface BulkToggleRequest {
  groupIds: string[];
  enabled: boolean;
}

export function useBulkToggleMutation() {
  return useMutation({
    mutationFn: (body: BulkToggleRequest) =>
      postJsonAuth<void>("/groups/bulk-toggle", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });
}

export interface BulkAssignRequest {
  groupIds: string[];
  readerAccountId: string;
}

export function useBulkAssignMutation() {
  return useMutation({
    mutationFn: (body: BulkAssignRequest) =>
      postJsonAuth<void>("/groups/bulk-assign", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });
}

export function useSyncGroupsMutation(accountId?: string) {
  return useMutation({
    mutationFn: () => postJsonAuth<void>(accountId ? `/groups/sync?accountId=${accountId}` : "/groups/sync", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });
}
