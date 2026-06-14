import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { Prompt } from "../types";

interface PromptDto {
  id: number;
  name: string;
  type: string;
  version: string;
  content: string;
  variables: string[];
  status: string;
}

export interface CreatePromptRequest {
  name: string;
  type: string;
  content: string;
  variables?: string;
  version?: string;
}

function normalizePromptType(type: string): Prompt["type"] {
  if (type === "CLASSIFICATION" || type === "CLASSIFIER") return "CLASSIFIER";
  if (type === "GENERATION" || type === "GUIDE_GENERATOR") return "GUIDE_GENERATOR";
  return type as Prompt["type"];
}

function toApiPromptType(type?: string): string | undefined {
  if (type === "CLASSIFIER") return "CLASSIFICATION";
  if (type === "GUIDE_GENERATOR") return "GENERATION";
  return type;
}

function normalizePrompt(dto: PromptDto): Prompt {
  return {
    id: String(dto.id),
    name: dto.name,
    type: normalizePromptType(dto.type),
    content: dto.content,
    variablesJson: JSON.stringify(dto.variables ?? []),
    version: dto.version,
    status: dto.status as Prompt["status"],
    avgTokens: 0,
    approveRate: 0,
    lastEditedAt: null,
  };
}

export function usePromptsQuery() {
  return useQuery({
    queryKey: ["prompts"],
    queryFn: async () => {
      const response = await getJsonAuth<PromptDto[]>("/prompts");
      return response.map(normalizePrompt);
    },
  });
}

export function useCreatePromptMutation() {
  return useMutation({
    mutationFn: async (body: CreatePromptRequest) => {
      const response = await postJsonAuth<PromptDto>("/prompts", {
        ...body,
        type: toApiPromptType(body.type),
      });
      return normalizePrompt(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

// ── Prompt edit/delete/test mutations ──

export interface UpdatePromptRequest {
  name?: string;
  type?: string;
  content?: string;
  variables?: string;
  version?: string;
  status?: string;
}

export interface TestPromptResult {
  output: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
}

export function useUpdatePromptMutation() {
  return useMutation({
    mutationFn: async ({ id, ...body }: { id: string } & UpdatePromptRequest) => {
      const response = await putJsonAuth<PromptDto>(`/prompts/${id}`, {
        ...body,
        type: toApiPromptType(body.type),
      });
      return normalizePrompt(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export function useDeletePromptMutation() {
  return useMutation({
    mutationFn: (id: string) => deleteJsonAuth(`/prompts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export function useTestPromptMutation() {
  return useMutation({
    mutationFn: ({ id, variables, providerId }: { id: string; variables: Record<string, string>; providerId?: string }) =>
      postJsonAuth<TestPromptResult>(`/prompts/${id}/test`, {
        variables,
        providerId: providerId ? Number(providerId) : undefined,
      }),
  });
}
