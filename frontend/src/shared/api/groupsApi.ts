import { useMutation, useQuery } from "@tanstack/react-query";
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
  messagesPerDay: number;
  guidesFound: number;
  lastReadAt: string | null;
  lastReadMessageId: number | null;
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
    messagesPerDay: dto.messagesPerDay,
    guidesFound: dto.guidesFound,
    lastReadAt: dto.lastReadAt,
    lastReadMessageId: dto.lastReadMessageId != null ? String(dto.lastReadMessageId) : null,
  };
}

export interface GroupsFilters {
  page?: number;
  size?: number;
  enabled?: boolean;
  search?: string;
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

  return useQuery({
    queryKey: ["groups", { page, size, enabled: filters?.enabled, search }],
    queryFn: async () => {
      const response = await getJsonAuth<PaginatedResponse<GroupDto>>(
        `/groups?${params.toString()}`
      );
      return {
        ...response,
        content: response.content.map(normalizeGroup),
      };
    },
    refetchInterval: 10_000,
    refetchIntervalInBackground: true,
    refetchOnMount: "always",
    refetchOnReconnect: true,
  });
}

export function useUpdateGroupMutation() {
  return useMutation({
    mutationFn: ({ id, ...body }: Partial<Group> & { id: string }) =>
      patchJsonAuth<Group>(`/groups/${id}`, body),
    onSuccess: () => {
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

export function useSyncGroupsMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/groups/sync", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });
}
