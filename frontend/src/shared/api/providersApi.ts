import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { AIProvider } from "../types";

export function useProvidersQuery() {
  return useQuery({
    queryKey: ["providers"],
    queryFn: () => getJsonAuth<AIProvider[]>("/providers"),
  });
}

export interface CreateProviderRequest {
  name: string;
  protocol: string;
  endpointUrl: string;
  apiKey?: string;
  model?: string;
}

export function useCreateProviderMutation() {
  return useMutation({
    mutationFn: (body: CreateProviderRequest) =>
      postJsonAuth<AIProvider>("/providers", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}

export interface TestProviderResult {
  success: boolean;
  latencyMs: number | null;
  error: string | null;
}

export function useTestProviderMutation() {
  return useMutation({
    mutationFn: (id: string) =>
      postJsonAuth<TestProviderResult>(`/providers/${id}/test`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}

// ── Provider edit/delete mutations ──

export interface UpdateProviderRequest {
  name?: string;
  protocol?: string;
  endpointUrl?: string;
  apiKey?: string;
  model?: string;
}

export function useUpdateProviderMutation() {
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string } & UpdateProviderRequest) =>
      putJsonAuth<AIProvider>(`/providers/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}

export function useDeleteProviderMutation() {
  return useMutation({
    mutationFn: (id: string) => deleteJsonAuth(`/providers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}
