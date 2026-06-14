import { useMutation, useQuery } from "@tanstack/react-query";
import { deleteJsonAuth, getJsonAuth, patchJsonAuth, postJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type {
  Guide,
  GuideLlmRequest,
  PaginatedResponse,
  SourceMessage,
} from "../types";

export interface GuidesFilters {
  page?: number;
  size?: number;
  status?: string;
}

interface GuideSummaryDto {
  id: number;
  title: string;
  groupId: number | null;
  groupTitle: string | null;
  rootMessageId: number | null;
  providerId: number | null;
  model: string | null;
  classifierId: number | null;
  promptId: number | null;
  promptVersion: string | null;
  status: string;
  duplicateOfId: number | null;
  duplicateScore: number | null;
  confidence: number | null;
  totalTokens: number | null;
  estimatedCostUsd: number | null;
  tags: string[] | null;
  generationError: string | null;
  publishedAt: string | null;
  createdAt: string | null;
}

interface SourceMessageDto {
  messageId: number;
  telegramMessageId: number | null;
  senderName: string | null;
  text: string | null;
  usedInPrompt: boolean;
  relation: string | null;
  replyToTelegramMessageId: number | null;
  topicId: number | null;
  topicName: string | null;
}

interface GuideLlmRequestDto {
  providerId: number | null;
  model: string | null;
  promptId: number | null;
  promptVersion: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  estimatedCostUsd: number | null;
}

interface GuideDetailDto extends GuideSummaryDto {
  content: string | null;
  contentMarkdown: string | null;
  sourceMessages: SourceMessageDto[] | null;
  llmRequest: GuideLlmRequestDto | null;
  relatedGuideIds: number[] | null;
  possibleDuplicateIds: number[] | null;
}

function normalizeSourceMessage(dto: SourceMessageDto): SourceMessage {
  return {
    messageId: String(dto.messageId),
    telegramMessageId:
      dto.telegramMessageId != null ? String(dto.telegramMessageId) : null,
    senderName: dto.senderName,
    text: dto.text,
    usedInPrompt: dto.usedInPrompt,
    relation: dto.relation,
    replyToTelegramMessageId:
      dto.replyToTelegramMessageId != null ? String(dto.replyToTelegramMessageId) : null,
    topicId: dto.topicId != null ? String(dto.topicId) : null,
    topicName: dto.topicName,
  };
}

function normalizeLlmRequest(dto: GuideLlmRequestDto | null): GuideLlmRequest | null {
  if (!dto) return null;
  return {
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model,
    promptId: dto.promptId != null ? String(dto.promptId) : null,
    promptVersion: dto.promptVersion,
    inputTokens: dto.inputTokens ?? 0,
    outputTokens: dto.outputTokens ?? 0,
    totalTokens: dto.totalTokens ?? 0,
    estimatedCostUsd: dto.estimatedCostUsd ?? 0,
  };
}

function normalizeGuide(dto: GuideSummaryDto | GuideDetailDto): Guide {
  const detail = dto as GuideDetailDto;
  return {
    id: String(dto.id),
    title: dto.title,
    sourceGroupId: dto.groupId != null ? String(dto.groupId) : null,
    sourceGroupTitle: dto.groupTitle ?? null,
    rootMessageId: dto.rootMessageId != null ? String(dto.rootMessageId) : null,
    content: detail.content ?? null,
    contentMarkdown: detail.contentMarkdown ?? null,
    confidence: dto.confidence ?? 0,
    status: dto.status as Guide["status"],
    providerId: dto.providerId != null ? String(dto.providerId) : null,
    model: dto.model ?? null,
    promptVersion: dto.promptVersion ?? null,
    inputTokens: detail.llmRequest?.inputTokens ?? 0,
    outputTokens: detail.llmRequest?.outputTokens ?? 0,
    totalTokens: dto.totalTokens ?? 0,
    estimatedCost: dto.estimatedCostUsd ?? 0,
    createdAt: dto.createdAt,
    publishedAt: dto.publishedAt,
    tags: Array.isArray(dto.tags) ? dto.tags : [],
    generationError: dto.generationError ?? null,
    duplicateScore: dto.duplicateScore,
    duplicateOfGuideId:
      dto.duplicateOfId != null ? String(dto.duplicateOfId) : null,
    sourceMessages: Array.isArray(detail.sourceMessages)
      ? detail.sourceMessages.map(normalizeSourceMessage)
      : [],
    llmRequest: normalizeLlmRequest(detail.llmRequest ?? null),
    relatedGuideIds: Array.isArray(detail.relatedGuideIds)
      ? detail.relatedGuideIds.map(String)
      : [],
    possibleDuplicateIds: Array.isArray(detail.possibleDuplicateIds)
      ? detail.possibleDuplicateIds.map(String)
      : [],
  };
}

export function useGuidesQuery(filters?: GuidesFilters) {
  const page = filters?.page ?? 0;
  const size = filters?.size ?? 50;

  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (filters?.status) {
    params.set("status", filters.status);
  }

  return useQuery({
    queryKey: ["guides", { page, size, status: filters?.status }],
    queryFn: async () => {
      const response = await getJsonAuth<PaginatedResponse<GuideSummaryDto>>(
        `/guides?${params.toString()}`
      );
      return {
        ...response,
        content: response.content.map(normalizeGuide),
      };
    },
  });
}

export function useGuideDetailQuery(id: string | undefined) {
  return useQuery({
    queryKey: ["guides", id],
    queryFn: async () => {
      const dto = await getJsonAuth<GuideDetailDto>(`/guides/${id!}`);
      return normalizeGuide(dto);
    },
    enabled: !!id,
  });
}

export function useUpdateGuideStatusMutation() {
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const dto = await patchJsonAuth<GuideSummaryDto>(`/guides/${id}/status`, {
        status,
      });
      return normalizeGuide(dto);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["guides", variables.id] });
    },
  });
}

export function useDeleteGuideMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      await deleteJsonAuth(`/guides/${id}`);
      return id;
    },
    onSuccess: (id) => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["guides", id] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}

export function useBulkDeleteGuidesMutation() {
  return useMutation({
    mutationFn: async (ids: string[]) => {
      await postJsonAuth<{ deleted: number }>("/guides/bulk-delete", {
        ids: ids.map((id) => Number(id)),
      });
      return ids;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
    },
  });
}
