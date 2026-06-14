import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";

// ── Pipeline status ──

export interface PipelineStatusResponse {
  processorEnabled: boolean;
  unprocessed: number;
  queued: number;
  processing: number;
  classified: number;
  skipped: number;
  guideFound: number;
}

export function usePipelineStatusQuery(from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);

  return useQuery({
    queryKey: ["pipeline-status", from ?? "all", to ?? "now"],
    queryFn: () =>
      getJsonAuth<PipelineStatusResponse>(
        `/pipeline/status${params.size > 0 ? `?${params.toString()}` : ""}`
      ),
    refetchInterval: 5_000,
  });
}

// ── Process single message ──

export function useProcessMessageMutation() {
  return useMutation({
    mutationFn: (messageId: string) =>
      postJsonAuth<void>(`/pipeline/process/${messageId}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

// ── Process queue manually ──

export function useProcessQueueMutation() {
  return useMutation({
    mutationFn: (limit: number = 10) =>
      postJsonAuth<void>(`/pipeline/process-queue?limit=${limit}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

// ── Pause / Resume ──

export function useRequeuePipelineMutation() {
  return useMutation({
    mutationFn: (statuses: string[] = ["SKIPPED", "CLASSIFIED", "CLEARED"]) => {
      const params = new URLSearchParams();
      statuses.forEach((status) => params.append("statuses", status));
      return postJsonAuth<void>(`/pipeline/requeue?${params.toString()}`, {});
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

export function useSmokeGuideMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/dev/smoke-guide", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
    },
  });
}

export function usePausePipelineMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/pause", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

export function useResumePipelineMutation() {
  return useMutation({
    mutationFn: () => postJsonAuth<void>("/pipeline/resume", {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

// ── Pipeline results (for tuning) ──

export interface PipelineResultItem {
  id: number;
  groupId: number;
  author: string | null;
  text: string;
  status: string;
  signalScore: number | null;
  classifierScore: number | null;
  classifierReason: string | null;
  guideId: number | null;
  messageDate: string;
  signalBreakdown: string | null;
  ruleResultJson: string | null;
}

interface PipelineResultsPage {
  content: PipelineResultItem[];
  totalElements: number;
  totalPages: number;
}

export interface PipelineQueueItem {
  id: number;
  groupId: number;
  groupTitle: string;
  author: string | null;
  text: string | null;
  status: string;
  topicName: string | null;
  messageDate: string;
}

interface PipelineQueuePage {
  content: PipelineQueueItem[];
  totalElements: number;
  totalPages: number;
}

export function usePipelineResultsQuery(
  status?: string,
  page: number = 0,
  size: number = 50,
  from?: string,
  enabled: boolean = true
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "messageDate,desc",
  });
  if (status) params.set("status", status);
  if (from) params.set("from", from);

  return useQuery({
    queryKey: ["pipeline-results", status, page, size, from],
    queryFn: () =>
      getJsonAuth<PipelineResultsPage>(`/pipeline/results?${params.toString()}`),
    enabled,
    refetchInterval: 5_000,
    placeholderData: keepPreviousData,
  });
}

export function usePipelineQueueQuery(
  statuses: string[] = ["QUEUED", "UNPROCESSED", "PROCESSING"],
  page: number = 0,
  size: number = 50,
  enabled: boolean = true
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  statuses.forEach((status) => params.append("statuses", status));

  return useQuery({
    queryKey: ["pipeline-queue", statuses.join(","), page, size],
    queryFn: () =>
      getJsonAuth<PipelineQueuePage>(`/pipeline/queue?${params.toString()}`),
    enabled,
    refetchInterval: 5_000,
    placeholderData: keepPreviousData,
  });
}

export function useClearPipelineQueueMutation() {
  return useMutation({
    mutationFn: (statuses: string[] = ["QUEUED"]) => {
      const params = new URLSearchParams();
      statuses.forEach((status) => params.append("statuses", status));
      return deleteJsonAuth(`/pipeline/queue?${params.toString()}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
    },
  });
}

export function useClearPipelineResultsMutation() {
  return useMutation({
    mutationFn: () => deleteJsonAuth("/pipeline/results"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
    },
  });
}
