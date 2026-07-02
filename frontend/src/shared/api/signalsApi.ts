import { useQuery } from "@tanstack/react-query";
import { getJsonAuth } from "./http";

export interface KnowledgeTopic {
  id: number;
  slug: string;
  name: string;
  description: string;
  materialCount: number;
  signalCount: number;
  reviewCount: number;
  riskCount: number;
}

export interface KnowledgeSignal {
  id: number;
  rawId: number | null;
  datasetMessageId: number | null;
  runId: number | null;
  title: string;
  summary: string | null;
  signalType: string;
  status: string;
  readiness: string;
  reason: string | null;
  riskFlags: unknown;
  evidence: unknown;
  confidence: number | null;
  chatTitle: string | null;
  senderName: string | null;
  senderUsername: string | null;
  messageDate: string | null;
  sourceText: string | null;
  appMessageUrl: string | null;
  entities?: string[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TopicsResponse {
  content: KnowledgeTopic[];
  totalElements: number;
}

export interface TopicResponse {
  topic: Pick<KnowledgeTopic, "id" | "slug" | "name" | "description">;
  signals: KnowledgeSignal[];
  number: number;
  size: number;
  tab: string;
}

export function useKnowledgeTopicsQuery() {
  return useQuery({
    queryKey: ["knowledge-topics"],
    queryFn: () => getJsonAuth<TopicsResponse>("/api/v1/topics"),
    staleTime: 30_000,
  });
}

export function useKnowledgeTopicQuery(slug: string | undefined, tab = "signals") {
  return useQuery({
    queryKey: ["knowledge-topic", slug, tab],
    queryFn: () => getJsonAuth<TopicResponse>(`/api/v1/topics/${slug}?tab=${encodeURIComponent(tab)}&size=100`),
    enabled: Boolean(slug),
    staleTime: 20_000,
  });
}
