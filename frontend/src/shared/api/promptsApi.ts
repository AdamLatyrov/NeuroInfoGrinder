import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { Prompt } from "../types";

interface PromptTemplateDto {
  id: number;
  code: string;
  name: string;
  description: string;
  stage: string;
  supportedModesJson: string[];
  systemPrompt: string;
  userPromptTemplate: string;
  outputSchemaJson: Record<string, unknown> | null;
  providerRoute: string;
  modelName: string;
  fallbackModel: string | null;
  version: number;
  isActive: boolean;
  status: string;
  variablesJson: string[];
  metadataJson: Record<string, unknown> | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePromptRequest {
  code: string;
  name: string;
  description?: string;
  stage: string;
  promptMode?: string;
  systemPrompt?: string;
  userPromptTemplate: string;
  outputSchema?: string;
  providerRoute?: string;
  modelName?: string;
  fallbackModel?: string;
}

export interface UpdatePromptRequest {
  name?: string;
  description?: string;
  userPromptTemplate?: string;
  systemPrompt?: string;
  outputSchema?: string;
  providerRoute?: string;
  modelName?: string;
  fallbackModel?: string;
  changeReason?: string;
}

export interface TestPromptResult {
  success: boolean;
  status: string;
  output: string;
  decision: string;
  confidence: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCostUsd: number;
}

const API = "/api/v2/prompts";

function normalizeMode(modes: string[]): string {
  if (!modes || modes.length === 0) return "BOTH";
  if (modes.length === 1) return modes[0];
  return "BOTH";
}

function normalizePrompt(dto: PromptTemplateDto): Prompt {
  return {
    id: String(dto.id),
    name: dto.name,
    code: dto.code,
    description: dto.description,
    stage: dto.stage,
    promptMode: normalizeMode(dto.supportedModesJson),
    type: dto.stage === "KNOWLEDGE_GENERATION" ? "GUIDE_GENERATOR" : "CLASSIFIER",
    content: dto.userPromptTemplate,
    systemPrompt: dto.systemPrompt,
    outputSchema: dto.outputSchemaJson ? JSON.stringify(dto.outputSchemaJson, null, 2) : "",
    providerRoute: dto.providerRoute,
    modelName: dto.modelName,
    fallbackModel: dto.fallbackModel,
    version: String(dto.version),
    versionNum: dto.version,
    status: (dto.isActive ? "ACTIVE" : dto.status === "ARCHIVED" ? "ARCHIVED" : "DRAFT") as Prompt["status"],
    variablesJson: JSON.stringify(dto.variablesJson ?? []),
    avgTokens: 0,
    approveRate: 0,
    lastEditedAt: dto.updatedAt,
  };
}

export function usePromptsQuery() {
  return useQuery({
    queryKey: ["prompts"],
    queryFn: async () => {
      const response = await getJsonAuth<PromptTemplateDto[]>(API);
      return response.map(normalizePrompt);
    },
  });
}

export function useCreatePromptMutation() {
  return useMutation({
    mutationFn: async (body: CreatePromptRequest) => {
      const response = await postJsonAuth<PromptTemplateDto>(API, body);
      return normalizePrompt(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export function useUpdatePromptMutation() {
  return useMutation({
    mutationFn: async ({ id, ...body }: { id: string } & UpdatePromptRequest) => {
      const response = await putJsonAuth<PromptTemplateDto>(`${API}/${id}`, body);
      return normalizePrompt(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export function useDeletePromptMutation() {
  return useMutation({
    mutationFn: (id: string) => deleteJsonAuth(`${API}/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export function useTestPromptMutation() {
  return useMutation({
    mutationFn: ({ id, variables, mode }: { id: string; variables?: Record<string, string>; mode?: string }) => {
      const body: Record<string, unknown> = {};
      if (variables) body.variables = variables;
      if (mode) body.mode = mode;
      return postJsonAuth<TestPromptResult>(`${API}/${id}/test`, body);
    },
  });
}

export function useActivatePromptMutation() {
  return useMutation({
    mutationFn: (id: string) => postJsonAuth(`${API}/${id}/activate`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}

export interface PromptVersionDto {
  id: number;
  promptTemplateId: number;
  version: number;
  snapshotJson: string;
  changeReason: string | null;
  createdAt: string;
}

export function usePromptVersionsQuery(id: string) {
  return useQuery({
    queryKey: ["prompt-versions", id],
    queryFn: () => getJsonAuth<PromptVersionDto[]>(`${API}/${id}/versions`),
  });
}

export function useRollbackPromptMutation() {
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      postJsonAuth(`${API}/${id}/rollback/${version}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prompts"] });
    },
  });
}
