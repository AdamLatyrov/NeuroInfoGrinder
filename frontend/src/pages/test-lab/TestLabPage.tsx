import { useState, useCallback } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useGroupsQuery } from "@/shared/api/groupsApi";
import { useGroupMessagesQuery } from "@/shared/api/messagesApi";
import { useTopicsQuery } from "@/shared/api/topicsApi";
import { useMessageTraceQuery, useTracesQuery } from "@/shared/api/tracesApi";
import { useRulesQuery } from "@/shared/api/classifiersApi";
import { useClassifiersQuery } from "@/shared/api/classifiersApi";
import { useProvidersQuery } from "@/shared/api/providersApi";
import { usePromptsQuery } from "@/shared/api/promptsApi";
import { useProcessMessageMutation } from "@/shared/api/pipelineApi";
import type {
  PipelineTrace,
  TraceStage,
  TraceStatus,
  Message,
  Group,
  Topic,
} from "@/shared/types";
import {
  Flask,
  Play,
  CheckCircle,
  XCircle,
  Clock,
  ArrowRight,
  SpinnerGap,
  ChatCircleDots,
  FolderOpen,
  Hash,
} from "@phosphor-icons/react";
import { cn } from "@/lib/utils";

// ── Input mode ──

type InputMode = "real" | "custom";

// ── Pipeline stage toggles ──

interface StageToggles {
  rules: boolean;
  scoring: boolean;
  classifier: boolean;
  guide: boolean;
}

const DEFAULT_TOGGLES: StageToggles = {
  rules: true,
  scoring: true,
  classifier: true,
  guide: true,
};

// ── Simulated trace result ──

interface SimulatedStageResult {
  stage: TraceStage;
  label: string;
  status: "PASSED" | "SKIPPED" | "FAILED";
  statusLabel: string;
  details: { label: string; value: string | boolean; ok?: boolean }[];
}

interface SimulatedTraceResult {
  messageText: string;
  author: string;
  groupTitle: string;
  topic: string;
  stages: SimulatedStageResult[];
  finalStatus: string;
  finalLabel: string;
}

// ── Simulation helper ──

function buildSimulatedTrace(
  text: string,
  author: string,
  groupTitle: string,
  topic: string,
  toggles: StageToggles
): SimulatedTraceResult {
  const charCount = text.length;
  const hasNumbering = /^\s*\d+[.)]/m.test(text);

  const stages: SimulatedStageResult[] = [];

  // Stage 1: Rules
  if (toggles.rules) {
    const keywordMatch = /деплой|docker|git|настройк|установк|запуст/i.test(text);
    stages.push({
      stage: "RULES",
      label: "Правила",
      status: "PASSED",
      statusLabel: "ПРОШЁЛ",
      details: [
        { label: 'Правило "Ключевые слова"', value: keywordMatch ? 'совпадение найдено' : 'нет совпадений', ok: keywordMatch },
        { label: 'Правило "Длина > 200"', value: `${charCount} симв.`, ok: charCount > 200 },
        { label: 'Правило "Исключить ботов"', value: author !== "bot", ok: author !== "bot" },
      ],
    });
  } else {
    stages.push({
      stage: "RULES",
      label: "Правила",
      status: "SKIPPED",
      statusLabel: "ПРОПУЩЕН",
      details: [],
    });
  }

  // Stage 2: Heuristic scoring
  const scoreBase = 0.3;
  const lengthContrib = Math.min(charCount / 5000, 0.25);
  const numContrib = hasNumbering ? 0.30 : 0;
  const kwContrib = /деплой|docker|git|настройк/i.test(text) ? 0.15 : 0;
  const totalScore = scoreBase + lengthContrib + numContrib + kwContrib;
  const threshold = 0.6;

  if (toggles.scoring) {
    stages.push({
      stage: "SIGNAL_SCORING",
      label: "Эвристический скоринг",
      status: totalScore >= threshold ? "PASSED" : "FAILED",
      statusLabel: totalScore >= threshold ? "ПОТЕНЦИАЛЬНЫЙ ГАЙД" : "НЕ ГАЙД",
      details: [
        { label: "Score", value: `${totalScore.toFixed(2)} (порог: ${threshold})`, ok: totalScore >= threshold },
        { label: "Нумерация", value: `+${numContrib.toFixed(2)}`, ok: hasNumbering },
        { label: "Длина", value: `+${lengthContrib.toFixed(2)}`, ok: charCount > 200 },
        { label: "Ключевые слова", value: `+${kwContrib.toFixed(2)}`, ok: kwContrib > 0 },
      ],
    });
  } else {
    stages.push({
      stage: "SIGNAL_SCORING",
      label: "Эвристический скоринг",
      status: "SKIPPED",
      statusLabel: "ПРОПУЩЕН",
      details: [],
    });
  }

  // Stage 3: LLM classifier
  const confidence = 0.85 + Math.random() * 0.12;
  const llmPass = totalScore >= threshold;

  if (toggles.classifier) {
    stages.push({
      stage: "LLM_CLASSIFIER",
      label: "LLM-классификатор",
      status: llmPass ? "PASSED" : "FAILED",
      statusLabel: llmPass ? "ПОТЕНЦИАЛЬНЫЙ ГАЙД" : "НЕ ГАЙД",
      details: [
        { label: "Уверенность", value: confidence.toFixed(2), ok: confidence > 0.7 },
        { label: "Причина", value: hasNumbering ? "Пошаговая инструкция с нумерованными шагами" : "Описание процесса" },
        { label: "Промпт", value: "Классификатор v3" },
        { label: "API", value: "OpenAI GPT-4o-mini" },
        { label: "Токены", value: "450 / 120" },
        { label: "Время", value: `${(0.8 + Math.random() * 0.6).toFixed(1)} сек` },
      ],
    });
  } else {
    stages.push({
      stage: "LLM_CLASSIFIER",
      label: "LLM-классификатор",
      status: "SKIPPED",
      statusLabel: "ПРОПУЩЕН",
      details: [],
    });
  }

  // Stage 4: Guide generation
  const guideCreated = llmPass && toggles.classifier;

  if (toggles.guide && guideCreated) {
    stages.push({
      stage: "GUIDE_GENERATION",
      label: "Генерация гайда",
      status: "PASSED",
      statusLabel: "ГАЙД СОЗДАН",
      details: [
        { label: "Промпт", value: "Генератор v2" },
        { label: "API", value: "OpenAI GPT-4o" },
        { label: "Токены", value: "1\u00A0200 / 800" },
        { label: "Стоимость", value: "$0.018" },
        { label: "Время", value: `${(2.5 + Math.random() * 1.5).toFixed(1)} сек` },
        { label: "Результат", value: "[гайд текст]" },
      ],
    });
  } else if (toggles.guide && !guideCreated) {
    stages.push({
      stage: "GUIDE_GENERATION",
      label: "Генерация гайда",
      status: "SKIPPED",
      statusLabel: "ПРОПУЩЕН",
      details: [{ label: "Причина", value: "Сообщение не прошло классификацию" }],
    });
  } else {
    stages.push({
      stage: "GUIDE_GENERATION",
      label: "Генерация гайда",
      status: "SKIPPED",
      statusLabel: "ПРОПУЩЕН",
      details: [],
    });
  }

  const anyGuide = stages.some((s) => s.stage === "GUIDE_GENERATION" && s.status === "PASSED");
  const finalLabel = anyGuide ? "Гайд создан" : "Гайд не создан";
  const finalStatus = anyGuide ? 'Ожидает модерации' : 'Отклонён';

  return {
    messageText: text,
    author,
    groupTitle,
    topic,
    stages,
    finalStatus,
    finalLabel,
  };
}

// ── Stage status badge ──

function StageStatusBadge({ status, label }: { status: SimulatedStageResult["status"]; label: string }) {
  const variant =
    status === "PASSED" ? "success" :
    status === "FAILED" ? "danger" :
    "secondary";
  return <Badge variant={variant}>{label}</Badge>;
}

// ── Trace timeline ──

function TraceTimeline({ result }: { result: SimulatedTraceResult }) {
  return (
    <div className="flex flex-col">
      {/* Input message node */}
      <div className="flex items-start gap-3">
        <div className="flex flex-col items-center">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-bg-app border border-border-subtle text-lg">
            📥
          </div>
          <div className="w-px flex-1 bg-border-subtle min-h-[12px]" />
        </div>
        <div className="flex-1 pb-4">
          <div className="text-sm font-semibold text-text-strong mb-1">Входное сообщение</div>
          <div className="text-xs text-text-muted space-y-0.5">
            <div>
              <span className="text-text-weak">Текст: </span>
              <span className="text-text-strong font-mono-value">
                &laquo;{result.messageText.length > 120 ? result.messageText.slice(0, 120) + "..." : result.messageText}&raquo;
              </span>
            </div>
            <div>
              <span className="text-text-weak">Автор: </span>
              <span className="text-text-strong">{result.author}</span>
            </div>
            <div>
              <span className="text-text-weak">Группа: </span>
              <span className="text-text-strong">{result.groupTitle}</span>
              {result.topic && (
                <>
                  <span className="text-text-weak">, тема: </span>
                  <span className="text-text-strong">{result.topic}</span>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Pipeline stages */}
      {result.stages.map((stage, i) => (
        <div key={stage.stage} className="flex items-start gap-3">
          <div className="flex flex-col items-center">
            <div
              className={cn(
                "flex h-8 w-8 items-center justify-center rounded-full border text-sm",
                stage.status === "PASSED" && "bg-success-soft border-success/30 text-success",
                stage.status === "FAILED" && "bg-danger-soft border-danger/30 text-danger",
                stage.status === "SKIPPED" && "bg-bg-app border-border-subtle text-text-weak"
              )}
            >
              {stage.status === "PASSED" ? (
                <CheckCircle size={18} weight="fill" />
              ) : stage.status === "FAILED" ? (
                <XCircle size={18} weight="fill" />
              ) : (
                <span className="text-xs">—</span>
              )}
            </div>
            {i < result.stages.length - 1 && (
              <div className="w-px flex-1 bg-border-subtle min-h-[12px]" />
            )}
          </div>
          <div className={cn("flex-1 pb-4", i === result.stages.length - 1 && "pb-0")}>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-sm font-semibold text-text-strong">
                Этап {i + 1}: {stage.label}
              </span>
              <span className="text-text-muted">—</span>
              <StageStatusBadge status={stage.status} label={stage.statusLabel} />
            </div>
            {stage.details.length > 0 && (
              <div className="text-xs text-text-muted space-y-0.5 ml-0">
                {stage.details.map((d, j) => (
                  <div key={j} className="flex items-start gap-1.5">
                    <span className="text-text-weak shrink-0">├─</span>
                    <span>
                      <span className="text-text-weak">{d.label}: </span>
                      <span
                        className={cn(
                          "text-text-strong",
                          d.ok === true && "text-success",
                          d.ok === false && "text-danger"
                        )}
                      >
                        {d.value}
                      </span>
                      {d.ok === true && <span className="text-success ml-1">✅</span>}
                      {d.ok === false && <span className="text-danger ml-1">❌</span>}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      ))}

      {/* Final result node */}
      <div className="flex items-start gap-3 mt-2">
        <div className="flex flex-col items-center">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-bg-app border border-border-subtle text-lg">
            📋
          </div>
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-text-strong">Итог:</span>
            <span className="text-sm text-text-strong">{result.finalLabel}</span>
            <span className="text-text-muted">→</span>
            <Badge variant={result.finalStatus === "Отклонён" ? "danger" : "warning"}>
              статус: &laquo;{result.finalStatus}&raquo;
            </Badge>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Recent traces list ──

function realStageStatusVariant(status: TraceStatus): "success" | "danger" | "secondary" {
  if (status === "PASSED" || status === "COMPLETED" || status === "SUCCESS") return "success";
  if (status === "FAILED" || status === "REJECTED") return "danger";
  return "secondary";
}

function realStageLabel(stage: TraceStage): string {
  const map: Record<TraceStage, string> = {
    TELEGRAM_READ: "Чтение Telegram",
    RULES: "Правила",
    SIGNAL_SCORING: "Скоринг",
    CLASSIFICATION: "Классификация",
    CLASSIFIER: "Классификатор",
    LLM_CLASSIFIER: "LLM-классификатор",
    GUIDE_GENERATION: "Генерация гайда",
    MODERATION: "Модерация",
    CHAIN_BUILDING: "Сборка цепочки",
  };
  return map[stage] ?? stage;
}

function realStatusLabel(status: TraceStatus): string {
  const map: Record<TraceStatus, string> = {
    SUCCESS: "УСПЕХ",
    PASSED: "ПРОШЁЛ",
    SKIPPED: "ПРОПУЩЕН",
    FAILED: "ОШИБКА",
    REJECTED: "ОТКЛОНЁН",
    PENDING: "В ОЧЕРЕДИ",
    COMPLETED: "ЗАВЕРШЁН",
  };
  return map[status] ?? status;
}

function RealTraceTimeline({ traces }: { traces: PipelineTrace[] }) {
  if (traces.length === 0) {
    return (
      <div className="py-6 text-center text-sm text-text-weak">
        Трейс для сообщения пока не появился.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {traces.map((trace) => {
        const variant = realStageStatusVariant(trace.status);

        return (
          <div
            key={trace.id}
            className="rounded-xl border border-border-subtle bg-bg-app p-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold text-text-strong">
                {realStageLabel(trace.stage)}
              </span>
              <Badge variant={variant}>{realStatusLabel(trace.status)}</Badge>
              {trace.durationMs != null && (
                <span className="font-mono-value text-xs text-text-muted">
                  {trace.durationMs < 1000
                    ? `${trace.durationMs}ms`
                    : `${(trace.durationMs / 1000).toFixed(1)}s`}
                </span>
              )}
              {trace.score != null && (
                <span className="font-mono-value text-xs text-text-muted">
                  score: {trace.score.toFixed(2)}
                </span>
              )}
            </div>

            {trace.reason && (
              <p className="mt-2 text-xs text-text-muted">{trace.reason}</p>
            )}

            <div className="mt-2 flex flex-wrap gap-3 text-xs text-text-muted">
              {trace.model && (
                <span>
                  Модель: <span className="text-text-strong">{trace.model}</span>
                </span>
              )}
              {(trace.inputTokens > 0 || trace.outputTokens > 0) && (
                <span>
                  Токены:{" "}
                  <span className="text-text-strong">
                    {trace.inputTokens} / {trace.outputTokens}
                  </span>
                </span>
              )}
              {trace.costUsd > 0 && (
                <span>
                  Стоимость:{" "}
                  <span className="text-text-strong">
                    ${trace.costUsd.toFixed(4)}
                  </span>
                </span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function RecentTraces() {
  const tracesQuery = useTracesQuery(undefined, undefined, 0, 5);
  const traces = tracesQuery.data?.content ?? [];

  if (tracesQuery.isLoading) {
    return (
      <div className="text-sm text-text-weak py-4 text-center">
        Загрузка трейсов...
      </div>
    );
  }

  if (traces.length === 0) {
    return (
      <div className="text-sm text-text-weak py-4 text-center">
        Нет недавних трейсов
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {traces.map((trace) => (
        <div
          key={trace.id}
          className="flex items-center gap-3 rounded-xl border border-border-subtle bg-bg-card px-3 py-2 text-xs"
        >
          <Badge
            variant={
              trace.status === "SUCCESS"
                ? "success"
                : trace.status === "FAILED"
                ? "danger"
                : trace.status === "SKIPPED"
                ? "secondary"
                : "default"
            }
          >
            {trace.status}
          </Badge>
          <span className="text-text-weak">{trace.stage}</span>
          {trace.durationMs != null && (
            <span className="font-mono-value text-text-muted">
              {trace.durationMs < 1000
                ? `${trace.durationMs}ms`
                : `${(trace.durationMs / 1000).toFixed(1)}s`}
            </span>
          )}
          <span className="text-text-weak ml-auto font-mono-value">
            {trace.startedAt ? new Date(trace.startedAt).toLocaleString("ru") : ""}
          </span>
        </div>
      ))}
    </div>
  );
}

// ── Telegram-style Message Picker Modal ──

function formatMsgTime(value: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString("ru-RU", { hour: "2-digit", minute: "2-digit" });
}

function formatMsgDate(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString("ru-RU", { day: "numeric", month: "long" });
}

function sameDay(a: string, b: string): boolean {
  const da = new Date(a), db = new Date(b);
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

interface MessagePickerModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  groups: Group[];
  selectedGroupId: string;
  selectedMessageId: string;
  onSelectGroup: (groupId: string) => void;
  onSelectMessage: (message: Message) => void;
}

function MessagePickerModal({
  open,
  onOpenChange,
  groups,
  selectedGroupId,
  selectedMessageId,
  onSelectGroup,
  onSelectMessage,
}: MessagePickerModalProps) {
  // Internal state: which group + topic are we browsing in the modal
  const [pickerGroupId, setPickerGroupId] = useState<string>("");
  const [pickerTopicId, setPickerTopicId] = useState<string | null>(null);

  // Reset picker state when modal opens
  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) {
      setPickerGroupId(selectedGroupId || "");
      setPickerTopicId(null);
    }
    onOpenChange(nextOpen);
  };

  const pickerGroup = groups.find((g) => g.id === pickerGroupId);
  const isForum = pickerGroup?.forum ?? false;

  // Topics for forum groups
  const topicsQuery = useTopicsQuery(isForum ? pickerGroup?.telegramChatId : undefined);
  const topics: Topic[] = topicsQuery.data ?? [];

  // Messages for selected group+topic
  const messagesQuery = useGroupMessagesQuery(pickerGroupId || undefined, pickerTopicId);
  const messages: Message[] = messagesQuery.data?.content ?? [];

  // Group messages by topicName for non-forum groups (if they have topics)
  const messagesByTopic = new Map<string, Message[]>();
  if (!isForum) {
    for (const m of messages) {
      const key = m.topicName ?? "_general";
      const list = messagesByTopic.get(key) ?? [];
      list.push(m);
      messagesByTopic.set(key, list);
    }
  }

  const handleSelectGroup = (groupId: string) => {
    setPickerGroupId(groupId);
    setPickerTopicId(null);
  };

  const handleSelectMessage = (message: Message) => {
    onSelectGroup(pickerGroupId);
    onSelectMessage(message);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-3xl h-[80vh] flex flex-col p-0 gap-0">
        <DialogHeader className="px-5 pt-5 pb-3 border-b border-border-subtle shrink-0">
          <DialogTitle className="flex items-center gap-2">
            <ChatCircleDots size={18} weight="regular" className="text-text-muted" />
            Выбрать сообщение
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-1 min-h-0 overflow-hidden">
          {/* Left panel: groups */}
          <div className="w-56 shrink-0 border-r border-border-subtle flex flex-col">
            <div className="px-3 py-2 border-b border-border-subtle">
              <span className="text-xs font-semibold uppercase text-text-muted tracking-wider">Группы</span>
            </div>
            <ScrollArea className="flex-1">
              <div className="p-2 flex flex-col gap-0.5">
                {groups.map((g) => (
                  <button
                    key={g.id}
                    type="button"
                    onClick={() => handleSelectGroup(g.id)}
                    className={cn(
                      "flex items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition-colors w-full",
                      g.id === pickerGroupId
                        ? "bg-primary/10 text-primary"
                        : "hover:bg-bg-app text-text-strong"
                    )}
                  >
                    <div className={cn(
                      "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold",
                      g.id === pickerGroupId ? "bg-primary/20 text-primary" : "bg-bg-app text-text-muted"
                    )}>
                      {g.title.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1 min-w-0">
                      <span className="block text-sm font-medium truncate">{g.title}</span>
                      {g.forum && (
                        <span className="block text-[10px] text-text-weak">Forum</span>
                      )}
                    </div>
                  </button>
                ))}
              </div>
            </ScrollArea>
          </div>

          {/* Right panel: topics + messages */}
          <div className="flex-1 flex flex-col min-w-0">
            {!pickerGroupId ? (
              <div className="flex-1 flex items-center justify-center text-text-weak">
                <div className="text-center">
                  <FolderOpen size={32} weight="regular" className="mx-auto mb-2 opacity-40" />
                  <p className="text-sm">Выберите группу слева</p>
                </div>
              </div>
            ) : (
              <>
                {/* Topic tabs for forum groups */}
                {isForum && topics.length > 0 && (
                  <div className="px-4 py-2 border-b border-border-subtle flex items-center gap-1 overflow-x-auto shrink-0">
                    <button
                      type="button"
                      onClick={() => setPickerTopicId(null)}
                      className={cn(
                        "flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors shrink-0",
                        pickerTopicId === null
                          ? "bg-primary/10 text-primary"
                          : "hover:bg-bg-app text-text-muted"
                      )}
                    >
                      <Hash size={12} weight="regular" />
                      Общее
                    </button>
                    {topics.map((t) => (
                      <button
                        key={t.forumTopicId}
                        type="button"
                        onClick={() => setPickerTopicId(t.forumTopicId)}
                        className={cn(
                          "flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors shrink-0 max-w-[150px]",
                          pickerTopicId === t.forumTopicId
                            ? "bg-primary/10 text-primary"
                            : "hover:bg-bg-app text-text-muted"
                        )}
                      >
                        <Hash size={12} weight="regular" />
                        <span className="truncate">{t.name}</span>
                      </button>
                    ))}
                  </div>
                )}

                {/* Messages list — Telegram-style bubbles */}
                <ScrollArea className="flex-1">
                  <div className="p-4 flex flex-col gap-1">
                    {messagesQuery.isLoading && (
                      <div className="flex items-center justify-center py-12 text-text-weak">
                        <SpinnerGap size={24} weight="regular" className="animate-spin mr-2" />
                        <span className="text-sm">Загрузка...</span>
                      </div>
                    )}

                    {!messagesQuery.isLoading && messages.length === 0 && (
                      <div className="flex items-center justify-center py-12 text-text-weak">
                        <span className="text-sm">Нет сообщений</span>
                      </div>
                    )}

                    {!messagesQuery.isLoading && messages.map((m, i) => {
                      const prevMsg = messages[i - 1];
                      const showDateSep = i === 0 || (prevMsg && !sameDay(m.timestamp, prevMsg.timestamp));
                      const dateSep = formatMsgDate(m.timestamp);

                      return (
                        <div key={m.id}>
                          {showDateSep && dateSep && (
                            <div className="flex items-center justify-center py-3">
                              <span className="rounded-full bg-bg-app px-3 py-1 text-[11px] font-medium text-text-weak">
                                {dateSep}
                              </span>
                            </div>
                          )}
                          <button
                            type="button"
                            onClick={() => handleSelectMessage(m)}
                            className={cn(
                              "w-full text-left rounded-xl px-3 py-2.5 transition-colors group",
                              m.id === selectedMessageId
                                ? "bg-primary/10 ring-1 ring-primary/30"
                                : "hover:bg-bg-app"
                            )}
                          >
                            <div className="flex items-start gap-2">
                              {/* Avatar */}
                              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-bg-app text-[10px] font-bold text-text-muted mt-0.5">
                                {m.author.charAt(0).toUpperCase()}
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-baseline gap-2">
                                  <span className="text-xs font-semibold text-text-strong">{m.author}</span>
                                  <span className="text-[10px] text-text-weak">{formatMsgTime(m.timestamp)}</span>
                                  {m.topicName && !isForum && (
                                    <span className="text-[10px] text-primary/70">#{m.topicName}</span>
                                  )}
                                </div>
                                <p className="text-xs text-text-muted mt-0.5 whitespace-pre-wrap break-words line-clamp-3">
                                  {m.text}
                                </p>
                              </div>
                              {m.id === selectedMessageId && (
                                <CheckCircle size={16} weight="fill" className="text-primary shrink-0 mt-1" />
                              )}
                            </div>
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </ScrollArea>
              </>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ── Main component ──

export function TestLabPage() {
  // ── Queries ──
  const groupsQuery = useGroupsQuery({ size: 200, enabled: true });
  const rulesQuery = useRulesQuery();
  const classifiersQuery = useClassifiersQuery();
  const providersQuery = useProvidersQuery();
  const promptsQuery = usePromptsQuery();

  // ── Mutations ──
  const processMessageMutation = useProcessMessageMutation();

  const groups = groupsQuery.data?.content ?? [];
  const rules = rulesQuery.data ?? [];
  const activeRules = rules.filter((r) => r.status === "ACTIVE" || r.status === "INCLUDE");
  const classifiers = classifiersQuery.data ?? [];
  const providers = providersQuery.data ?? [];
  const prompts = promptsQuery.data ?? [];

  // ── Input state ──
  const [inputMode, setInputMode] = useState<InputMode>("real");
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");
  const [selectedMessageId, setSelectedMessageId] = useState<string>("");
  const [customText, setCustomText] = useState("");
  const [pickerOpen, setPickerOpen] = useState(false);

  // ── Messages query (depends on selected group) ──
  const messagesQuery = useGroupMessagesQuery(selectedGroupId || undefined);
  const messages: Message[] = messagesQuery.data?.content ?? [];

  // ── Selected message ──
  const selectedMessage = messages.find((m) => m.id === selectedMessageId) ?? null;

  // ── Pipeline toggles ──
  const [stageToggles, setStageToggles] = useState<StageToggles>(DEFAULT_TOGGLES);

  const toggleStage = (key: keyof StageToggles) => {
    setStageToggles((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  // ── Run state ──
  const [isRunning, setIsRunning] = useState(false);
  const [traceResult, setTraceResult] = useState<SimulatedTraceResult | null>(null);
  const [lastProcessedMessageId, setLastProcessedMessageId] = useState<string | null>(null);

  // ── Effective message text for preview ──
  const effectiveText =
    inputMode === "real" && selectedMessage
      ? selectedMessage.text
      : inputMode === "custom"
        ? customText
        : "";

  const effectiveAuthor =
    inputMode === "real" && selectedMessage
      ? selectedMessage.author
      : "custom-input";

  const effectiveGroup =
    inputMode === "real" && selectedMessage
      ? selectedMessage.groupTitle || groups.find((g) => g.id === selectedGroupId)?.title || ""
      : "";

  const effectiveTopic =
    inputMode === "real" && selectedMessage
      ? selectedMessage.topicName || ""
      : "";

  const canRun = effectiveText.trim().length > 0 && (stageToggles.rules || stageToggles.scoring || stageToggles.classifier || stageToggles.guide);
  const traceMessageId =
    inputMode === "real"
      ? (lastProcessedMessageId ?? selectedMessageId ?? undefined)
      : undefined;
  const shouldPollMessageTrace =
    inputMode === "real" &&
    !!traceMessageId &&
    (processMessageMutation.isPending || processMessageMutation.isSuccess);
  const messageTraceQuery = useMessageTraceQuery(traceMessageId, {
    enabled: inputMode === "real" && !!traceMessageId,
    refetchInterval: shouldPollMessageTrace ? 2000 : false,
  });
  const realTraces = messageTraceQuery.data ?? [];

  // ── Picker handlers ──
  const handlePickerSelectGroup = (groupId: string) => {
    setSelectedGroupId(groupId);
    setSelectedMessageId("");
    setLastProcessedMessageId(null);
    processMessageMutation.reset();
  };

  const handlePickerSelectMessage = (message: Message) => {
    setSelectedMessageId(message.id);
    setLastProcessedMessageId(null);
    processMessageMutation.reset();
  };

  // ── Run handler (simulated) ──
  const handleRun = useCallback(() => {
    if (!canRun) return;
    setIsRunning(true);
    setTraceResult(null);

    // Simulate async processing with progressive delays
    setTimeout(() => {
      const result = buildSimulatedTrace(
        effectiveText,
        effectiveAuthor,
        effectiveGroup,
        effectiveTopic,
        stageToggles
      );
      setTraceResult(result);
      setIsRunning(false);
    }, 1500);
  }, [canRun, effectiveText, effectiveAuthor, effectiveGroup, effectiveTopic, stageToggles]);

  // ── Provider/prompt info for context display ──
  const classifierProvider = providers.find((p) => {
    const llmC = classifiers.find((c) => c.type === "LLM" && c.status === "ACTIVE");
    return llmC && p.id === llmC.providerId;
  });
  const classifierPrompt = prompts.find((p) => {
    const llmC = classifiers.find((c) => c.type === "LLM" && c.status === "ACTIVE");
    return llmC && p.id === llmC.promptId;
  });
  const guidePrompt = prompts.find((p) => p.type === "GUIDE_GENERATOR" && p.status === "ACTIVE");

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Тест-лаб"
        description="Тестирование конвейера обработки на выбранных или пользовательских сообщениях"
      />

      {/* ── Input section ── */}
      <Card>
        <CardContent className="p-5">
          <h3 className="text-sm font-semibold text-text-strong mb-3">
            Входное сообщение
          </h3>

          {/* Input mode toggle */}
          <div className="flex items-center gap-2 mb-4">
            <Button
              variant={inputMode === "real" ? "primary" : "outline"}
              size="sm"
              onClick={() => setInputMode("real")}
            >
              Реальное сообщение
            </Button>
            <Button
              variant={inputMode === "custom" ? "primary" : "outline"}
              size="sm"
              onClick={() => setInputMode("custom")}
            >
              Свой текст
            </Button>
          </div>

          {inputMode === "real" ? (
            <div className="flex flex-col gap-3">
              {/* Message picker trigger button */}
              <button
                type="button"
                onClick={() => setPickerOpen(true)}
                className="flex items-center gap-3 rounded-xl border border-border-subtle bg-bg-card p-4 text-left transition-colors hover:border-primary/30 hover:bg-primary/5"
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-bg-app">
                  <ChatCircleDots size={20} weight="regular" className="text-text-muted" />
                </div>
                <div className="flex-1 min-w-0">
                  {selectedMessage ? (
                    <>
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="text-sm font-semibold text-text-strong">{selectedMessage.author}</span>
                        <span className="text-[10px] text-text-weak font-mono-value">id:{selectedMessage.id}</span>
                      </div>
                      <p className="text-xs text-text-muted truncate">{selectedMessage.text}</p>
                      <div className="flex items-center gap-1.5 mt-1">
                        {groups.find((g) => g.id === selectedGroupId) && (
                          <Badge variant="outline" className="text-[10px]">{groups.find((g) => g.id === selectedGroupId)!.title}</Badge>
                        )}
                        {selectedMessage.topicName && (
                          <Badge variant="secondary" className="text-[10px]">#{selectedMessage.topicName}</Badge>
                        )}
                      </div>
                    </>
                  ) : (
                    <>
                      <span className="text-sm font-medium text-text-strong">Выбрать сообщение</span>
                      <p className="text-xs text-text-muted">Откройте список групп и выберите сообщение для тестирования</p>
                    </>
                  )}
                </div>
                <span className="text-xs text-primary font-medium shrink-0">Открыть</span>
              </button>
            </div>
          ) : (
            <div>
              <label className="mb-1 block text-xs font-medium text-text-muted">
                Текст сообщения
              </label>
              <Textarea
                placeholder="Введите текст сообщения для тестирования конвейера..."
                value={customText}
                onChange={(e) => setCustomText(e.target.value)}
                rows={5}
                className="font-mono-value text-sm"
              />
            </div>
          )}

          {/* Message preview card */}
          {effectiveText && (
            <div className="mt-4 rounded-xl border border-border-subtle bg-bg-app p-3">
              <div className="flex items-center gap-2 mb-2">
                <Badge variant="outline">Превью</Badge>
                {inputMode === "real" && selectedMessage && (
                  <Badge variant="secondary">
                    {selectedMessage.author}
                  </Badge>
                )}
              </div>
              <p className="text-sm text-text-strong whitespace-pre-wrap break-words max-h-[160px] overflow-auto">
                {effectiveText}
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── Pipeline status card ── */}
      {false && <Card />}

      {/* ── Pipeline stages selection (only for simulated mode) ── */}
      {inputMode === "custom" && (
      <Card>
        <CardContent className="p-5">
          <h3 className="text-sm font-semibold text-text-strong mb-1">
            Этапы конвейера
          </h3>
          <p className="text-xs text-text-muted mb-4">
            Выберите, какие этапы запустить при тестировании
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Rules */}
            <label className="flex items-center gap-3 rounded-xl border border-border-subtle p-3 cursor-pointer hover:bg-bg-app transition-colors">
              <Checkbox
                checked={stageToggles.rules}
                onCheckedChange={() => toggleStage("rules")}
              />
              <div className="flex-1">
                <span className="text-sm font-medium text-text-strong">
                  Правила
                </span>
                <p className="text-xs text-text-muted mt-0.5">
                  Проверка активными правилами ({activeRules.length} активных)
                </p>
              </div>
              <Badge variant="outline" className="font-mono-value text-[10px]">
                RULES
              </Badge>
            </label>

            {/* Scoring */}
            <label className="flex items-center gap-3 rounded-xl border border-border-subtle p-3 cursor-pointer hover:bg-bg-app transition-colors">
              <Checkbox
                checked={stageToggles.scoring}
                onCheckedChange={() => toggleStage("scoring")}
              />
              <div className="flex-1">
                <span className="text-sm font-medium text-text-strong">
                  Эвристический скоринг
                </span>
                <p className="text-xs text-text-muted mt-0.5">
                  SignalScorer — оценка по формальным признакам
                </p>
              </div>
              <Badge variant="outline" className="font-mono-value text-[10px]">
                SCORING
              </Badge>
            </label>

            {/* LLM classifier */}
            <label className="flex items-center gap-3 rounded-xl border border-border-subtle p-3 cursor-pointer hover:bg-bg-app transition-colors">
              <Checkbox
                checked={stageToggles.classifier}
                onCheckedChange={() => toggleStage("classifier")}
              />
              <div className="flex-1">
                <span className="text-sm font-medium text-text-strong">
                  LLM-классификатор
                </span>
                <p className="text-xs text-text-muted mt-0.5">
                  {classifierProvider
                    ? `${classifierProvider.name} / ${classifierProvider.model ?? "no model"}`
                    : "Провайдер не настроен"}
                </p>
              </div>
              <Badge variant="outline" className="font-mono-value text-[10px]">
                LLM
              </Badge>
            </label>

            {/* Guide generation */}
            <label className="flex items-center gap-3 rounded-xl border border-border-subtle p-3 cursor-pointer hover:bg-bg-app transition-colors">
              <Checkbox
                checked={stageToggles.guide}
                onCheckedChange={() => toggleStage("guide")}
              />
              <div className="flex-1">
                <span className="text-sm font-medium text-text-strong">
                  Генерация гайда
                </span>
                <p className="text-xs text-text-muted mt-0.5">
                  {guidePrompt
                    ? `Промпт: ${guidePrompt.name} (v${guidePrompt.version})`
                    : "Промпт не настроен"}
                </p>
              </div>
              <Badge variant="outline" className="font-mono-value text-[10px]">
                GUIDE
              </Badge>
            </label>
          </div>
        </CardContent>
      </Card>
      )}

      {/* ── Run button ── */}
      <div className="flex items-center gap-3">
        {inputMode === "real" && selectedMessage ? (
          <Button
            variant="primary"
            size="lg"
            className="gap-2"
            disabled={processMessageMutation.isPending}
            onClick={() => {
              setLastProcessedMessageId(selectedMessage.id);
              processMessageMutation.reset();
              processMessageMutation.mutate(selectedMessage.id);
            }}
          >
            {processMessageMutation.isPending ? (
              <SpinnerGap size={18} weight="regular" className="animate-spin" />
            ) : (
              <Play size={18} weight="fill" />
            )}
            {processMessageMutation.isPending ? "Обработка..." : "Запустить конвейер"}
          </Button>
        ) : (
          <Button
            variant="primary"
            size="lg"
            className="gap-2"
            disabled={!canRun || isRunning}
            onClick={handleRun}
          >
            {isRunning ? (
              <SpinnerGap size={18} weight="regular" className="animate-spin" />
            ) : (
              <Play size={18} weight="fill" />
            )}
            {isRunning ? "Симуляция..." : "Симуляция конвейера"}
          </Button>
        )}
        {!canRun && effectiveText.trim().length === 0 && (
          <span className="text-xs text-text-weak">
            Укажите входное сообщение
          </span>
        )}
        {!canRun && effectiveText.trim().length > 0 && (
          <span className="text-xs text-text-weak">
            Включите хотя бы один этап
          </span>
        )}
      </div>

      {/* ── Real pipeline result ── */}
      {false && (
        processMessageMutation.isPending ? (
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center justify-center py-8 text-text-weak">
                <SpinnerGap size={24} weight="regular" className="animate-spin mr-3" />
                <span className="text-sm">Запуск конвейера на бэкенде...</span>
              </div>
            </CardContent>
          </Card>
        ) : processMessageMutation.isSuccess ? (
          <Card>
            <CardContent className="p-5">
              <div className="flex items-center gap-2 mb-3">
                <Flask size={18} weight="regular" className="text-text-muted" />
                <h3 className="text-sm font-semibold text-text-strong">
                  Результат конвейера
                </h3>
                <Badge variant="success" dot>Отправлено</Badge>
              </div>
              <p className="text-xs text-text-muted mb-3">
                Сообщение отправлено в конвейер. Проверьте статус сообщения через несколько секунд — оно обновится автоматически.
              </p>

              {/* Show current message state with scores */}
              {selectedMessage?.signalScore != null && (
                <div className="rounded-xl border border-border-subtle p-3 space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-text-muted">Signal score:</span>
                    <span className={cn(
                      "font-mono-value text-sm font-semibold",
                      selectedMessage!.signalScore! >= 0.45 ? "text-success" : "text-danger"
                    )}>
                      {selectedMessage!.signalScore!.toFixed(2)}
                    </span>
                    <Badge variant={selectedMessage!.signalScore! >= 0.45 ? "success" : "danger"} className="text-[10px]">
                      {selectedMessage!.signalScore! >= 0.45 ? "ПРОШЁЛ" : "НЕ ПРОШЁЛ"}
                    </Badge>
                    <span className="text-[10px] text-text-weak">(порог: 0.45)</span>
                  </div>
                  {selectedMessage?.classifierScore != null && (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-text-muted">Classifier score:</span>
                      <span className={cn(
                        "font-mono-value text-sm font-semibold",
                        selectedMessage!.classifierScore! >= 0.75 ? "text-success" : "text-warning"
                      )}>
                        {selectedMessage!.classifierScore!.toFixed(2)}
                      </span>
                      <Badge variant={selectedMessage!.classifierScore! >= 0.75 ? "success" : "warning"} className="text-[10px]">
                        {selectedMessage!.classifierScore! >= 0.75 ? "ГАЙД" : "СОМНИТЕЛЬНО"}
                      </Badge>
                      <span className="text-[10px] text-text-weak">(порог: 0.75)</span>
                    </div>
                  )}
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-text-muted">Статус:</span>
                    <Badge variant={selectedMessage!.processingStatus === "GUIDE_FOUND" ? "success" : selectedMessage!.processingStatus === "SKIPPED" ? "danger" : "default"}>
                      {selectedMessage!.processingStatus}
                    </Badge>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ) : null
      )}

      {inputMode === "real" && selectedMessage && lastProcessedMessageId && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-3">
              <Flask size={18} weight="regular" className="text-text-muted" />
              <h3 className="text-sm font-semibold text-text-strong">Реальный trace</h3>
              {processMessageMutation.isPending && <Badge variant="default" dot>Запуск</Badge>}
              {!processMessageMutation.isPending && realTraces.length === 0 && (
                <Badge variant="warning" dot>Ждём trace</Badge>
              )}
              {!processMessageMutation.isPending && realTraces.length > 0 && (
                <Badge variant="success" dot>Получен</Badge>
              )}
            </div>

            {processMessageMutation.isError && (
              <div className="rounded-xl border border-danger/30 bg-danger-soft p-3 text-sm text-danger">
                Не удалось запустить обработку: {processMessageMutation.error instanceof Error ? processMessageMutation.error.message : "неизвестная ошибка"}
              </div>
            )}

            {!processMessageMutation.isError && processMessageMutation.isPending && (
              <div className="flex items-center justify-center py-8 text-text-weak">
                <SpinnerGap size={24} weight="regular" className="animate-spin mr-3" />
                <span className="text-sm">Запускаем обработку сообщения на backend...</span>
              </div>
            )}

            {!processMessageMutation.isError && !processMessageMutation.isPending && realTraces.length === 0 && (
              <div className="flex items-center justify-center py-8 text-text-weak">
                <SpinnerGap size={24} weight="regular" className="animate-spin mr-3" />
                <span className="text-sm">Сообщение отправлено. Ждём, пока backend запишет trace.</span>
              </div>
            )}

            {!processMessageMutation.isError && realTraces.length > 0 && (
              <div className="space-y-4">
                <div className="rounded-xl border border-border-subtle p-3 space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs text-text-muted">Статус:</span>
                    <Badge variant={selectedMessage!.processingStatus === "GUIDE_FOUND" ? "success" : selectedMessage!.processingStatus === "SKIPPED" ? "danger" : "default"}>
                      {selectedMessage!.processingStatus}
                    </Badge>
                    {selectedMessage!.signalScore != null && (
                      <span className="font-mono-value text-xs text-text-muted">
                        signal: <span className="text-text-strong">{selectedMessage!.signalScore!.toFixed(2)}</span>
                      </span>
                    )}
                    {selectedMessage!.classifierScore != null && (
                      <span className="font-mono-value text-xs text-text-muted">
                        classifier: <span className="text-text-strong">{selectedMessage!.classifierScore!.toFixed(2)}</span>
                      </span>
                    )}
                  </div>

                  {selectedMessage!.classifierReason && (
                    <p className="text-xs text-text-muted">
                      Причина классификатора: <span className="text-text-strong">{selectedMessage!.classifierReason}</span>
                    </p>
                  )}

                  {selectedMessage!.signalBreakdown && (
                    <pre className="rounded-lg bg-bg-app px-3 py-2 text-xs text-text-muted whitespace-pre-wrap break-words">
                      {selectedMessage!.signalBreakdown}
                    </pre>
                  )}
                </div>

                <RealTraceTimeline traces={realTraces} />
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ── Trace result visualization ── */}
      {(isRunning || traceResult) && (
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center gap-2 mb-4">
              <Flask size={18} weight="regular" className="text-text-muted" />
              <h3 className="text-sm font-semibold text-text-strong">
                Результат трейса
              </h3>
              {isRunning && (
                <Badge variant="default" dot>
                  <SpinnerGap size={12} weight="regular" className="animate-spin mr-1" />
                  Выполнение
                </Badge>
              )}
              {traceResult && !isRunning && (
                <Badge variant="success" dot>Завершено</Badge>
              )}
            </div>

            {isRunning && (
              <div className="flex items-center justify-center py-12 text-text-weak">
                <SpinnerGap size={32} weight="regular" className="animate-spin mr-3" />
                <span className="text-sm">Симуляция конвейера...</span>
              </div>
            )}

            {traceResult && !isRunning && (
              <ScrollArea className="max-h-[600px]">
                <TraceTimeline result={traceResult} />
              </ScrollArea>
            )}
          </CardContent>
        </Card>
      )}

      {/* ── Recent traces section ── */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center gap-2 mb-3">
            <Clock size={16} weight="regular" className="text-text-muted" />
            <h3 className="text-sm font-semibold text-text-strong">
              Недавние трейсы
            </h3>
          </div>
          <RecentTraces />
        </CardContent>
      </Card>

      {/* ── Message Picker Modal ── */}
      <MessagePickerModal
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        groups={groups}
        selectedGroupId={selectedGroupId}
        selectedMessageId={selectedMessageId}
        onSelectGroup={handlePickerSelectGroup}
        onSelectMessage={handlePickerSelectMessage}
      />
    </div>
  );
}
