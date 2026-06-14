import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, patchJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { ChainConfig, Classifier, Rule } from "../types";

interface ClassifierDto {
  id: number;
  name: string;
  type: string;
  providerId: number | null;
  promptId: number | null;
  keywords: string | null;
  regex: string | null;
  modelConfig: string | null;
  version: string;
  status: string;
  order: number;
  rulesCount: number;
}

interface RuleDto {
  id: number;
  order: number;
  conditions: unknown[];
  actions: unknown[];
  status: string;
}

export interface CreateClassifierRequest {
  name: string;
  type: string;
  providerId?: number;
  promptId?: number;
  keywords?: string;
  regex?: string;
  modelConfig?: string;
  version?: string;
  order?: number;
}

export interface CreateRuleRequest {
  name?: string;
  description?: string;
  actionType?: string;
  status?: string;
  order: number;
  conditions: string;
  actions: string;
}

function normalizeClassifier(dto: ClassifierDto): Classifier {
  return {
    id: String(dto.id),
    name: dto.name,
    type: dto.type as Classifier["type"],
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    promptId: dto.promptId != null ? String(dto.promptId) : null,
    keywords: dto.keywords ?? null,
    regexPattern: dto.regex ?? null,
    modelConfig: dto.modelConfig ?? null,
    version: dto.version,
    status: dto.status as Classifier["status"],
    order: dto.order ?? 100,
    avgTokens: 0,
    successRate: 0,
  };
}

function normalizeRule(dto: RuleDto): Rule {
  return {
    id: String(dto.id),
    name: (dto as any).name ?? "Unnamed rule",
    description: (dto as any).description ?? null,
    actionType: (dto as any).actionType ?? "INCLUDE",
    ruleOrder: dto.order,
    conditionType: Array.isArray(dto.conditions) && dto.conditions.length > 1 ? "AND" : "SINGLE",
    conditionsJson: JSON.stringify(dto.conditions ?? []),
    actionsJson: JSON.stringify(dto.actions ?? []),
    status: dto.status,
  };
}

export function useClassifiersQuery() {
  return useQuery({
    queryKey: ["classifiers"],
    queryFn: async () => {
      const response = await getJsonAuth<ClassifierDto[]>("/classifiers");
      return response.map(normalizeClassifier);
    },
  });
}

export function useRulesQuery() {
  return useQuery({
    queryKey: ["rules"],
    queryFn: async () => {
      const response = await getJsonAuth<RuleDto[]>("/rules");
      return response.map(normalizeRule);
    },
  });
}

export function useChainConfigQuery() {
  return useQuery({
    queryKey: ["chain-config"],
    queryFn: () => getJsonAuth<ChainConfig>("/chain-config"),
  });
}

export function useCreateClassifierMutation() {
  return useMutation({
    mutationFn: async (body: CreateClassifierRequest) => {
      const response = await postJsonAuth<ClassifierDto>("/classifiers", body);
      return normalizeClassifier(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifiers"] });
    },
  });
}

export function useCreateRuleMutation() {
  return useMutation({
    mutationFn: async (body: CreateRuleRequest) => {
      const response = await postJsonAuth<RuleDto>("/rules", body);
      return normalizeRule(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
}

// ── Classifier CRUD mutations ──

export interface UpdateClassifierRequest {
  name?: string;
  type?: string;
  providerId?: number;
  promptId?: number;
  keywords?: string;
  regex?: string;
  modelConfig?: string;
  version?: string;
  order?: number;
}

export function useUpdateClassifierMutation() {
  return useMutation({
    mutationFn: async ({ id, ...body }: { id: string } & UpdateClassifierRequest) => {
      const response = await putJsonAuth<ClassifierDto>(`/classifiers/${id}`, body);
      return normalizeClassifier(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifiers"] });
    },
  });
}

export function useDeleteClassifierMutation() {
  return useMutation({
    mutationFn: (id: string) => deleteJsonAuth(`/classifiers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifiers"] });
    },
  });
}

export function useUpdateClassifierStatusMutation() {
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      patchJsonAuth(`/classifiers/${id}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifiers"] });
    },
  });
}

// ── Rule CRUD mutations ──

export interface UpdateRuleRequest {
  name?: string;
  description?: string;
  actionType?: string;
  order?: number;
  conditions?: string;
  actions?: string;
  status?: string;
}

export function useUpdateRuleMutation() {
  return useMutation({
    mutationFn: async ({ id, ...body }: { id: string } & UpdateRuleRequest) => {
      const response = await putJsonAuth<RuleDto>(`/rules/${id}`, body);
      return normalizeRule(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
}

export function useDeleteRuleMutation() {
  return useMutation({
    mutationFn: (id: string) => deleteJsonAuth(`/rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
}

export function useUpdateRuleStatusMutation() {
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      patchJsonAuth(`/rules/${id}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
}

export function useReorderRulesMutation() {
  return useMutation({
    mutationFn: (ruleIds: string[]) =>
      patchJsonAuth("/rules/reorder", { ruleIds: ruleIds.map(Number) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
}

export function useReorderClassifiersMutation() {
  return useMutation({
    mutationFn: (classifierIds: string[]) =>
      patchJsonAuth("/classifiers/reorder", { classifierIds: classifierIds.map(Number) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifiers"] });
    },
  });
}
