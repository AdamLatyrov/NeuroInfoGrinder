import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { TelegramAccount } from "../types";

// Backend returns numeric IDs; frontend uses strings for consistency
interface BackendAccountResponse {
  id: number;
  phone: string;
  telegramUserId: number | null;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  status: string;
  proxy: unknown;
  groupsCount: number;
  tokensUsed: number;
  lastActivityAt: string | null;
}

function mapAccount(account: BackendAccountResponse): TelegramAccount {
  return {
    id: String(account.id),
    phone: account.phone,
    status: account.status as TelegramAccount["status"],
    externalTelegramUserId:
      account.telegramUserId != null
        ? String(account.telegramUserId)
        : null,
    username: account.username,
    languageCode: null,
    timezone: null,
    createdAt: account.lastActivityAt,
    groupsCount: account.groupsCount,
  };
}

export interface CreateAccountRequest {
  phone: string;
}

export interface SubmitCodeRequest {
  code: string;
}

export interface SubmitPasswordRequest {
  password: string;
}

export function useAccountsQuery() {
  return useQuery({
    queryKey: ["accounts"],
    queryFn: async () => {
      const response = await getJsonAuth<BackendAccountResponse[]>("/accounts");
      return response.map(mapAccount);
    },
  });
}

export function useCreateAccountMutation() {
  return useMutation({
    mutationFn: async (body: CreateAccountRequest) => {
      const response = await postJsonAuth<BackendAccountResponse>("/accounts", body);
      return mapAccount(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
  });
}

export function useSubmitAccountCodeMutation() {
  return useMutation({
    mutationFn: async ({ id, code }: { id: string; code: string }) => {
      const response = await postJsonAuth<BackendAccountResponse>(
        `/accounts/${id}/code`,
        { code }
      );
      return mapAccount(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
  });
}

export function useSubmitAccountPasswordMutation() {
  return useMutation({
    mutationFn: async ({ id, password }: { id: string; password: string }) => {
      const response = await postJsonAuth<BackendAccountResponse>(
        `/accounts/${id}/password`,
        { password }
      );
      return mapAccount(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
  });
}

function invalidateAccounts() {
  queryClient.invalidateQueries({ queryKey: ["accounts"] });
  queryClient.invalidateQueries({ queryKey: ["telegram-accounts"] });
  queryClient.invalidateQueries({ queryKey: ["operator-status"] });
}

export function useDeleteAccountMutation() {
  return useMutation({
    mutationFn: async (id: string) => deleteJsonAuth(`/api/v2/telegram/accounts/${id}`),
    onSuccess: invalidateAccounts,
  });
}

export function usePauseAccountMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await postJsonAuth<BackendAccountResponse>(`/api/v2/telegram/accounts/${id}/pause`, {});
      return mapAccount(response);
    },
    onSuccess: invalidateAccounts,
  });
}

export function useResumeAccountMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await postJsonAuth<BackendAccountResponse>(`/api/v2/telegram/accounts/${id}/resume`, {});
      return mapAccount(response);
    },
    onSuccess: invalidateAccounts,
  });
}

export function useDisableAccountMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await postJsonAuth<BackendAccountResponse>(`/api/v2/telegram/accounts/${id}/disable`, {});
      return mapAccount(response);
    },
    onSuccess: invalidateAccounts,
  });
}

export function useEnableAccountMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await postJsonAuth<BackendAccountResponse>(`/api/v2/telegram/accounts/${id}/enable`, {});
      return mapAccount(response);
    },
    onSuccess: invalidateAccounts,
  });
}
