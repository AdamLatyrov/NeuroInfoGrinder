import { useMutation, useQuery } from "@tanstack/react-query";
import { getJsonAuth, postJsonAuth, putJsonAuth } from "./http";
import { queryClient } from "./queryClient";
import type { AppSettings, ProcessingMode, PublicationMode } from "../types";

interface SettingsResponseDto {
  activeProviderId: number | null;
  publication: {
    targetGroupId: number | null;
    mode: string;
  };
  processing: {
    mode: string;
    pollIntervalSeconds: number;
    chainWindow: {
      includeReplies: boolean;
      timeWindowMinutes: number;
      minMessagesForProcessing: number;
      maxMessagesPerChain: number;
    };
  };
  filters: {
    blacklistWords: string | null;
    skipBots: boolean;
    minMessageLength: number;
  };
  limits: {
    dailyTokenLimit: number;
    monthlyTokenLimit: number;
    alertThresholdPercent: number;
  };
  notifications: {
    telegramChat: string | null;
    webhookUrl: string | null;
  };
}

interface UpdateSettingsDto {
  publicationTargetGroupId: number | null;
  publicationMode: string;
  processingMode: string;
  pollIntervalSeconds: number;
  chainIncludeReplies: boolean;
  chainTimeWindowMinutes: number;
  chainMinMessages: number;
  chainMaxMessages: number;
  filterSkipBots: boolean;
  filterMinMessageLength: number;
  filterBlacklistWords: string;
  limitDailyTokenLimit: number;
  limitMonthlyTokenLimit: number;
  limitAlertThresholdPct: number;
  notificationTelegramChat: string | null;
  notificationWebhookUrl: string | null;
}

export type StorageCleanupScope = "GUIDES" | "MATERIALS" | "MESSAGES";

export interface StorageCleanupResult {
  scope: StorageCleanupScope;
  guidesDeleted: number;
  materialsDeleted: number;
  messagesDeleted: number;
  sourceLinksDeleted: number;
  tracesDeleted: number;
  topicClustersDeleted: number;
  topicClusterMessagesDeleted: number;
  topicClusterCandidatesDeleted: number;
  embeddingsDeleted: number;
  signalClusterLinksDeleted: number;
  signalMicroclustersDeleted: number;
  signalMacroclustersDeleted: number;
}

const publicationModeFromApi: Record<string, PublicationMode> = {
  AUTOMATIC: "Automatic",
  WITH_MODERATION: "With moderation",
  MIXED: "Mixed",
};

const publicationModeToApi: Record<PublicationMode, string> = {
  Automatic: "AUTOMATIC",
  "With moderation": "WITH_MODERATION",
  Mixed: "MIXED",
};

const processingModeFromApi: Record<string, ProcessingMode> = {
  NEW_ONLY: "New only",
  FULL_BACKFILL: "Full backfill",
};

const processingModeToApi: Record<ProcessingMode, string> = {
  "New only": "NEW_ONLY",
  "Full backfill": "FULL_BACKFILL",
};

function normalizeBlacklistWords(value: string | null): string[] {
  if (!value) return [];
  return value
    .split(",")
    .map((word) => word.trim())
    .filter(Boolean);
}

function normalizeSettings(dto: SettingsResponseDto): AppSettings {
  return {
    activeProviderId:
      dto.activeProviderId != null ? String(dto.activeProviderId) : null,
    publication: {
      targetGroupId:
        dto.publication.targetGroupId != null
          ? String(dto.publication.targetGroupId)
          : null,
      targetGroupTitle:
        dto.publication.targetGroupId != null
          ? `Group #${dto.publication.targetGroupId}`
          : null,
      mode:
        publicationModeFromApi[dto.publication.mode] ?? "With moderation",
    },
    processing: {
      mode: processingModeFromApi[dto.processing.mode] ?? "New only",
      pollIntervalSeconds: dto.processing.pollIntervalSeconds ?? 30,
      chainWindow: {
        includeReplies:
          dto.processing.chainWindow.includeReplies ?? true,
        timeWindowMinutes:
          dto.processing.chainWindow.timeWindowMinutes ?? 5,
        minMessagesForProcessing:
          dto.processing.chainWindow.minMessagesForProcessing ?? 2,
        maxMessagesPerChain:
          dto.processing.chainWindow.maxMessagesPerChain ?? 20,
      },
    },
    filters: {
      blacklistWords: normalizeBlacklistWords(dto.filters.blacklistWords),
      skipBots: dto.filters.skipBots ?? true,
      minMessageLength: dto.filters.minMessageLength ?? 0,
    },
    limits: {
      dailyTokenLimit: dto.limits.dailyTokenLimit ?? 0,
      monthlyTokenLimit: dto.limits.monthlyTokenLimit ?? 0,
      alertThresholdPercent: dto.limits.alertThresholdPercent ?? 80,
    },
    notifications: {
      telegramChat: dto.notifications.telegramChat ?? "",
      webhookUrl: dto.notifications.webhookUrl ?? "",
    },
  };
}

function serializeSettings(settings: AppSettings): UpdateSettingsDto {
  return {
    publicationTargetGroupId: settings.publication.targetGroupId
      ? Number(settings.publication.targetGroupId)
      : null,
    publicationMode: publicationModeToApi[settings.publication.mode],
    processingMode: processingModeToApi[settings.processing.mode],
    pollIntervalSeconds: settings.processing.pollIntervalSeconds,
    chainIncludeReplies: settings.processing.chainWindow.includeReplies,
    chainTimeWindowMinutes:
      settings.processing.chainWindow.timeWindowMinutes,
    chainMinMessages:
      settings.processing.chainWindow.minMessagesForProcessing,
    chainMaxMessages:
      settings.processing.chainWindow.maxMessagesPerChain,
    filterSkipBots: settings.filters.skipBots,
    filterMinMessageLength: settings.filters.minMessageLength,
    filterBlacklistWords: settings.filters.blacklistWords.join(","),
    limitDailyTokenLimit: settings.limits.dailyTokenLimit,
    limitMonthlyTokenLimit: settings.limits.monthlyTokenLimit,
    limitAlertThresholdPct: settings.limits.alertThresholdPercent,
    notificationTelegramChat: settings.notifications.telegramChat || null,
    notificationWebhookUrl: settings.notifications.webhookUrl || null,
  };
}

export function useSettingsQuery() {
  return useQuery({
    queryKey: ["settings"],
    queryFn: async () => {
      const dto = await getJsonAuth<SettingsResponseDto>("/settings");
      return normalizeSettings(dto);
    },
  });
}

export function useUpdateSettingsMutation() {
  return useMutation({
    mutationFn: async (body: AppSettings) => {
      const dto = await putJsonAuth<SettingsResponseDto>(
        "/settings",
        serializeSettings(body)
      );
      return normalizeSettings(dto);
    },
    onSuccess: (settings) => {
      queryClient.setQueryData(["settings"], settings);
      queryClient.invalidateQueries({ queryKey: ["settings"] });
    },
  });
}

export function useStorageCleanupMutation() {
  return useMutation({
    mutationFn: async (scope: StorageCleanupScope) => {
      return postJsonAuth<StorageCleanupResult>("/storage/cleanup", {
        scope,
        confirmation: "DELETE",
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["guides"] });
      queryClient.invalidateQueries({ queryKey: ["materials"] });
      queryClient.invalidateQueries({ queryKey: ["group-messages"] });
      queryClient.invalidateQueries({ queryKey: ["message-chain"] });
      queryClient.invalidateQueries({ queryKey: ["group-message"] });
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-status"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-queue"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
      queryClient.invalidateQueries({ queryKey: ["topic-candidates"] });
      queryClient.invalidateQueries({ queryKey: ["traces"] });
      queryClient.invalidateQueries({ queryKey: ["trace"] });
      queryClient.invalidateQueries({ queryKey: ["message-trace"] });
      queryClient.invalidateQueries({ queryKey: ["flow-metrics"] });
      queryClient.invalidateQueries({ queryKey: ["monitor"] });
    },
  });
}
