import { useQuery } from "@tanstack/react-query";
import { getJsonAuth } from "./http";
import type {
  FlowMetrics,
  PaginatedResponse,
  PipelineTrace,
  PipelineTuningCase,
} from "../types";

interface TraceDto {
  id: number;
  traceId: string;
  messageId: number | null;
  groupId: number | null;
  stage: string;
  status: string;
  inputData: string | null;
  outputData: string | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  ruleId: number | null;
  classifierId: number | null;
  promptId: number | null;
  providerId: number | null;
  model: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  score: number | null;
  confidence: number | null;
  reason: string | null;
  entityType: string | null;
  entityName: string | null;
  entityVersion: string | null;
  configSnapshotJson: string | null;
  tuningHint: string | null;
}

function normalizeTrace(dto: TraceDto): PipelineTrace {
  return {
    id: String(dto.id),
    traceId: dto.traceId,
    messageId: dto.messageId != null ? String(dto.messageId) : null,
    groupId: dto.groupId != null ? String(dto.groupId) : null,
    stage: dto.stage as PipelineTrace["stage"],
    status: dto.status as PipelineTrace["status"],
    inputData: dto.inputData,
    outputData: dto.outputData,
    errorMessage: dto.errorMessage,
    startedAt: dto.startedAt,
    finishedAt: dto.finishedAt,
    durationMs: dto.durationMs,
    ruleId: dto.ruleId != null ? String(dto.ruleId) : null,
    classifierId: dto.classifierId != null ? String(dto.classifierId) : null,
    promptId: dto.promptId != null ? String(dto.promptId) : null,
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model,
    inputTokens: dto.inputTokens,
    outputTokens: dto.outputTokens,
    costUsd: dto.costUsd,
    score: dto.score,
    confidence: dto.confidence,
    reason: dto.reason,
    entityType: dto.entityType,
    entityName: dto.entityName,
    entityVersion: dto.entityVersion,
    configSnapshotJson: dto.configSnapshotJson,
    tuningHint: dto.tuningHint,
  };
}

export function useTraceQuery(traceId: string | undefined) {
  return useQuery({
    queryKey: ["trace", traceId],
    queryFn: async () => {
      const response = await getJsonAuth<TraceDto[]>(`/traces/${traceId}`);
      return response.map(normalizeTrace);
    },
    enabled: !!traceId,
  });
}

interface MessageTraceQueryOptions {
  enabled?: boolean;
  refetchInterval?: number | false;
}

export function useMessageTraceQuery(
  messageId: string | undefined,
  options?: MessageTraceQueryOptions
) {
  return useQuery({
    queryKey: ["message-trace", messageId],
    queryFn: async () => {
      const response = await getJsonAuth<TraceDto[]>(`/traces/message/${messageId}`);
      return response.map(normalizeTrace);
    },
    enabled: options?.enabled ?? !!messageId,
    refetchInterval: options?.refetchInterval,
  });
}

export function useTracesQuery(from?: string, to?: string, page = 0, size = 50) {
  return useQuery({
    queryKey: ["traces", from, to, page, size],
    queryFn: async () => {
      const resolvedTo = to ?? new Date().toISOString();
      const resolvedFrom =
        from ??
        new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

      let url =
        `/traces?page=${page}&size=${size}` +
        `&from=${encodeURIComponent(resolvedFrom)}` +
        `&to=${encodeURIComponent(resolvedTo)}`;
      const response = await getJsonAuth<PaginatedResponse<TraceDto>>(url);
      return {
        ...response,
        content: response.content.map(normalizeTrace),
      };
    },
    refetchInterval: 5_000,
  });
}

export function useFlowMetricsQuery() {
  return useQuery({
    queryKey: ["flow-metrics"],
    queryFn: () => getJsonAuth<FlowMetrics>("/traces/metrics"),
    refetchInterval: 30_000,
  });
}

interface TuningCasesOptions {
  from?: string;
  to?: string;
  stage?: string;
  status?: string;
  classifierId?: string;
  promptId?: string;
  ruleId?: string;
  groupId?: string;
  problemOnly?: boolean;
  page?: number;
  size?: number;
}

export function useTuningCasesQuery(options?: TuningCasesOptions) {
  const page = options?.page ?? 0;
  const size = options?.size ?? 50;
  return useQuery({
    queryKey: ["tuning-cases", options],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(size));
      params.set("problemOnly", String(options?.problemOnly ?? true));
      if (options?.from) params.set("from", options.from);
      if (options?.to) params.set("to", options.to);
      if (options?.stage) params.set("stage", options.stage);
      if (options?.status) params.set("status", options.status);
      if (options?.classifierId) params.set("classifierId", options.classifierId);
      if (options?.promptId) params.set("promptId", options.promptId);
      if (options?.ruleId) params.set("ruleId", options.ruleId);
      if (options?.groupId) params.set("groupId", options.groupId);
      return getJsonAuth<PaginatedResponse<PipelineTuningCase>>(
        `/traces/tuning-cases?${params.toString()}`
      );
    },
    refetchInterval: 10_000,
  });
}
