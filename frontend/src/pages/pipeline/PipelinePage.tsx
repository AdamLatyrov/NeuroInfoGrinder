import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowSquareOut,
  ArrowsClockwise,
  BookOpen,
  CaretDown,
  CaretUp,
  CheckCircle,
  Clock,
  Pause,
  Play,
  SpinnerGap,
  WarningCircle,
  XCircle,
} from "@phosphor-icons/react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
  type PipelineResultItem,
  usePausePipelineMutation,
  useRequeueMessageMutation,
  usePipelineResultsQuery,
  usePipelineStatusQuery,
  useResumePipelineMutation,
} from "@/shared/api/pipelineApi";
import { usePipelineEvents } from "@/shared/api/pipelineEvents";
import { useMessageTraceQuery } from "@/shared/api/tracesApi";
import { tryParseJson, type PipelineTrace } from "@/shared/types";

const STAGE_LABELS: Record<string, string> = {
  TELEGRAM_READ: "Чтение Telegram",
  RULES: "Правила",
  SIGNAL_SCORING: "Оценка полезности",
  CHAIN_BUILDING: "Сбор контекста",
  CLASSIFICATION: "Классификация",
  CLASSIFIER: "Классификатор",
  LLM_CLASSIFIER: "LLM-классификатор",
  GUIDE_GENERATION: "Создание гайда",
  MODERATION: "Модерация",
};

const STATUS_LABELS: Record<string, string> = {
  UNPROCESSED: "Ожидает",
  QUEUED: "В очереди",
  PROCESSING: "Обрабатывается",
  CLASSIFIED: "Классифицировано",
  SKIPPED: "Отсеяно",
  GUIDE_FOUND: "С гайдом",
  PASSED: "Пройдено",
  COMPLETED: "Завершено",
  SUCCESS: "Успешно",
  REJECTED: "Отклонено",
  FAILED: "Ошибка",
};

const KEY_LABELS: Record<string, string> = {
  score: "Оценка",
  matched: "Совпадение",
  reasoning: "Обоснование",
  reason: "Причина",
  totalScore: "Итоговая оценка",
  threshold: "Порог",
  length: "Длина",
  replies: "Ответы",
  aiKeywords: "AI-ключи",
  guideKeywords: "Guide-ключи",
  title: "Заголовок",
  confidence: "Уверенность",
  classifierType: "Тип классификатора",
  classifierName: "Классификатор",
  chainSize: "Размер цепочки",
  providerId: "Провайдер",
  model: "Модель",
  checks: "Проверки",
  passes: "Прошло",
  ruleName: "Правило",
  actionType: "Действие",
  detail: "Деталь",
  status: "Статус",
};

type PipelineView = "ALL" | "GUIDE_FOUND" | "CLASSIFIED" | "SKIPPED";
type TimePreset = "all" | "live" | "5m" | "15m" | "1h" | "custom";

function toDateTimeLocal(date: Date | null) {
  if (!date) return "";
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

function presetDate(preset: TimePreset) {
  const now = new Date();
  if (preset === "all") return null;
  if (preset === "5m") return new Date(now.getTime() - 5 * 60_000);
  if (preset === "15m") return new Date(now.getTime() - 15 * 60_000);
  if (preset === "1h") return new Date(now.getTime() - 60 * 60_000);
  return now;
}

function localToIso(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatDuration(value: number | null) {
  if (value == null) return "—";
  return value < 1000 ? `${value} мс` : `${(value / 1000).toFixed(1)} с`;
}

function formatNumber(value: number | null | undefined, fractionDigits = 3) {
  if (value == null) return "—";
  return Number.isInteger(value) ? String(value) : value.toFixed(fractionDigits);
}

function statusVariant(status: string): "success" | "danger" | "warning" | "outline" {
  if (["GUIDE_FOUND", "CLASSIFIED", "PASSED", "COMPLETED", "SUCCESS"].includes(status)) return "success";
  if (["SKIPPED", "REJECTED", "FAILED"].includes(status)) return "danger";
  if (["UNPROCESSED", "QUEUED", "PROCESSING"].includes(status)) return "warning";
  return "outline";
}

function keyLabel(key: string) {
  return KEY_LABELS[key] ?? key;
}

function humanValue(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "boolean") return value ? "да" : "нет";
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(3);
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

function parsePipeData(value: string) {
  return Object.fromEntries(
    value
      .split("|")
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const index = part.indexOf("=");
        if (index < 0) return [part, true];
        return [part.slice(0, index).trim(), part.slice(index + 1).trim()];
      }),
  );
}

function normalizeData(value: string | null) {
  if (!value) return null;
  return tryParseJson<unknown>(value) ?? parsePipeData(value);
}

function classifierSummary(value: string | null) {
  const parsed = tryParseJson<{
    score?: number;
    labels?: string[];
    guideCandidate?: boolean;
    guide_candidate?: boolean;
    evidenceMessageIds?: number[];
    evidence_message_ids?: number[];
    reasoning?: string;
  }>(value);
  if (!parsed) return null;
  return {
    score: parsed.score ?? null,
    labels: parsed.labels ?? [],
    guideCandidate: parsed.guideCandidate ?? parsed.guide_candidate ?? false,
    evidenceMessageIds: parsed.evidenceMessageIds ?? parsed.evidence_message_ids ?? [],
    reasoning: parsed.reasoning ?? "",
  };
}

function classifierMessages(
  message: PipelineResultItem,
  classifierInfo: ReturnType<typeof classifierSummary>,
) {
  if (!classifierInfo) return [];

  const labels = new Set(classifierInfo.labels);
  const hints: string[] = [];

  if (message.status === "CLASSIFIED" && classifierInfo.guideCandidate === false) {
    hints.push(
      "Это полезный сигнал, но не гайд: не хватает ответа, инструкции, ссылки, цены, способа или подтверждения.",
    );
  }

  if (message.status === "CLASSIFIED" && classifierInfo.guideCandidate && !message.guideId) {
    hints.push("Кандидат в гайд.");
  }

  if (labels.has("DEMAND_SIGNAL") && !labels.has("SOLUTION_MENTION")) {
    hints.push("Есть спрос/вопрос, но нет решения.");
  }

  if (labels.has("SPAM_OR_AD")) {
    hints.push("Оффер/реклама: сохранено как источник, не как гайд.");
  }

  return hints;
}


type TraceRun = {
  traceId: string;
  traces: PipelineTrace[];
  startedAt: string | null;
};

function groupTraceRuns(traces: PipelineTrace[]): TraceRun[] {
  const grouped = new Map<string, PipelineTrace[]>();
  traces.forEach((trace) => {
    const key = trace.traceId || trace.id;
    const bucket = grouped.get(key) ?? [];
    bucket.push(trace);
    grouped.set(key, bucket);
  });

  return Array.from(grouped.entries())
    .map(([traceId, items]) => ({
      traceId,
      traces: items,
      startedAt: items[0]?.startedAt ?? null,
    }))
    .sort((left, right) => new Date(left.startedAt ?? 0).getTime() - new Date(right.startedAt ?? 0).getTime());
}

function statusCallout(message: PipelineResultItem, classifierInfo: ReturnType<typeof classifierSummary>) {
  if (message.status === "CLASSIFIED") {
    if (classifierInfo?.guideCandidate && !message.guideId) {
      return "Кандидат в гайд";
    }
    return "Полезный сигнал";
  }
  if (message.status === "GUIDE_FOUND") {
    return "Гайд";
  }
  if (message.status === "SKIPPED") {
    return "Отсеяно";
  }
  return STATUS_LABELS[message.status] ?? message.status;
}

function DataValue({ value }: { value: unknown }) {
  if (value == null) {
    return <div className="text-sm text-text-muted">—</div>;
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return <div className="text-sm text-text-muted">Пусто</div>;
    }

    const objectArray = value.every((item) => item && typeof item === "object" && !Array.isArray(item));
    if (objectArray) {
      return (
        <div className="grid gap-2">
          {value.map((item, index) => (
            <div key={index} className="rounded-lg border border-border-subtle bg-bg-card px-3 py-3">
              <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-text-weak">
                Запись {index + 1}
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                {Object.entries(item as Record<string, unknown>).map(([itemKey, itemValue]) => (
                  <div key={itemKey} className="rounded-md bg-bg-app px-3 py-2">
                    <div className="text-[11px] text-text-weak">{keyLabel(itemKey)}</div>
                    <div className="mt-1 whitespace-pre-wrap break-words text-xs font-medium text-text-strong">
                      {humanValue(itemValue)}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      );
    }

    return (
      <div className="flex flex-wrap gap-2">
        {value.map((item, index) => (
          <div key={index} className="rounded-full bg-bg-card px-3 py-1 text-xs font-medium text-text-strong">
            {humanValue(item)}
          </div>
        ))}
      </div>
    );
  }

  if (typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    return (
      <div className="grid gap-2 sm:grid-cols-2">
        {entries.map(([entryKey, entryValue]) => (
          <div key={entryKey} className="rounded-lg bg-bg-card px-3 py-3">
            <div className="text-[11px] text-text-weak">{keyLabel(entryKey)}</div>
            {typeof entryValue === "object" && entryValue !== null ? (
              <div className="mt-2">
                <DataValue value={entryValue} />
              </div>
            ) : (
              <div className="mt-1 whitespace-pre-wrap break-words text-xs font-medium text-text-strong">
                {humanValue(entryValue)}
              </div>
            )}
          </div>
        ))}
      </div>
    );
  }

  return <pre className="whitespace-pre-wrap break-words font-sans text-sm text-text-strong">{humanValue(value)}</pre>;
}

function TraceDataCard({ title, data }: { title: string; data: string | null }) {
  if (!data) return null;

  return (
    <div className="rounded-2xl border border-border-subtle bg-bg-card p-4">
      <div className="mb-3 text-xs font-semibold uppercase tracking-wide text-text-weak">{title}</div>
      <DataValue value={normalizeData(data) ?? data} />
    </div>
  );
}

function TraceSummary({ trace }: { trace: PipelineTrace }) {
  const summaryItems = [
    trace.score != null ? { label: "Оценка", value: formatNumber(trace.score) } : null,
    trace.confidence != null ? { label: "Уверенность", value: formatNumber(trace.confidence) } : null,
    trace.providerId ? { label: "Провайдер", value: trace.providerId } : null,
    trace.model ? { label: "Модель", value: trace.model } : null,
    trace.inputTokens ? { label: "Input tokens", value: String(trace.inputTokens) } : null,
    trace.outputTokens ? { label: "Output tokens", value: String(trace.outputTokens) } : null,
    trace.costUsd ? { label: "Стоимость", value: `$${trace.costUsd.toFixed(4)}` } : null,
  ].filter(Boolean) as Array<{ label: string; value: string }>;

  if (summaryItems.length === 0) {
    return null;
  }

  return (
    <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
      {summaryItems.map((item) => (
        <div key={item.label} className="rounded-lg bg-bg-card px-3 py-2">
          <div className="text-[11px] text-text-weak">{item.label}</div>
          <div className="mt-1 text-sm font-medium text-text-strong">{item.value}</div>
        </div>
      ))}
    </div>
  );
}

function TraceStep({ trace, index, total }: { trace: PipelineTrace; index: number; total: number }) {
  const [showData, setShowData] = useState(index === total - 1);
  const success = ["PASSED", "COMPLETED", "SUCCESS"].includes(trace.status);
  const hasDetails = Boolean(trace.inputData || trace.outputData);
  const Icon = success ? CheckCircle : trace.status === "PENDING" ? WarningCircle : XCircle;

  return (
    <div className="relative grid grid-cols-[28px_minmax(0,1fr)] gap-3 pb-5 last:pb-0">
      <div className="relative flex justify-center">
        {index < total - 1 && <div className="absolute bottom-[-20px] top-6 w-px bg-border-subtle" />}
        <Icon
          size={20}
          weight="fill"
          className={cn("relative z-10 bg-bg-card", {
            "text-success": success,
            "text-warning": trace.status === "PENDING",
            "text-danger": !success && trace.status !== "PENDING",
          })}
        />
      </div>

      <div className="min-w-0 rounded-2xl border border-border-subtle bg-bg-app p-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold text-text-strong">{STAGE_LABELS[trace.stage] ?? trace.stage}</span>
          <Badge variant={statusVariant(trace.status)}>{STATUS_LABELS[trace.status] ?? trace.status}</Badge>
          <span className="text-xs text-text-muted">{formatDuration(trace.durationMs)}</span>
          {trace.startedAt && <span className="text-xs text-text-muted">{formatDate(trace.startedAt)}</span>}
        </div>

        <p
          className={cn("mt-3 whitespace-pre-wrap break-words text-sm", {
            "text-text-muted": success,
            "text-warning": trace.status === "PENDING",
            "font-medium text-danger": !success && trace.status !== "PENDING",
          })}
        >
          {trace.errorMessage || trace.reason || "Этап завершен без пояснения."}
        </p>

        <div className="mt-3">
          <TraceSummary trace={trace} />
        </div>

        {hasDetails && (
          <Button
            variant="ghost"
            size="sm"
            className="mt-3 h-7 px-0 text-xs"
            onClick={() => setShowData((value) => !value)}
          >
            {showData ? <CaretUp size={14} /> : <CaretDown size={14} />}
            {showData ? "Скрыть детали" : "Показать детали"}
          </Button>
        )}

        {showData && (
          <div className="mt-4 grid gap-3">
            <TraceDataCard title="Что проверяли" data={trace.inputData} />
            <TraceDataCard title="Что получилось" data={trace.outputData} />
          </div>
        )}
      </div>
    </div>
  );
}

function ResultRow({
  message,
  expanded,
  onToggle,
}: {
  message: PipelineResultItem;
  expanded: boolean;
  onToggle: () => void;
}) {
  const navigate = useNavigate();
  const requeueMessage = useRequeueMessageMutation();
  const [showHistory, setShowHistory] = useState(false);
  const traceQuery = useMessageTraceQuery(expanded ? String(message.id) : undefined);
  const traces = traceQuery.data ?? [];
  const traceRuns = groupTraceRuns(traces);
  const latestRun = traceRuns[traceRuns.length - 1] ?? null;
  const classifierInfo = classifierSummary(message.classifierResultJson);
  const callout = statusCallout(message, classifierInfo);
  const hints = classifierMessages(message, classifierInfo);
  const displayScore = classifierInfo?.score ?? message.classifierScore ?? message.signalScore;

  return (
    <div className="border-b border-border-subtle last:border-0">
      <div className="flex items-start gap-3 px-4 py-4">
        <button type="button" className="min-w-0 flex-1 text-left" onClick={onToggle}>
          <div className="flex items-start gap-2">
            <span className="line-clamp-2 flex-1 text-sm font-medium text-text-strong">
              {message.text || "Сообщение без текста"}
            </span>
            <Badge variant={statusVariant(message.status)}>{STATUS_LABELS[message.status] ?? message.status}</Badge>
            {callout !== (STATUS_LABELS[message.status] ?? message.status) && <Badge variant="outline">{callout}</Badge>}
          </div>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-text-muted">
            <span>{message.author || "Автор неизвестен"}</span>
            <span>{formatDate(message.messageDate)}</span>
            {message.signalScore != null && <span>Полезность {message.signalScore.toFixed(2)}</span>}
            {message.classifierScore != null && <span>Классификация {message.classifierScore.toFixed(2)}</span>}
            {displayScore != null && <span>Score {formatNumber(displayScore, 2)}</span>}
          </div>
          {message.classifierReason && (
            <p className="mt-2 line-clamp-2 whitespace-pre-wrap break-words text-xs text-text-weak">
              {message.classifierReason}
            </p>
          )}
          {classifierInfo && (
            <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-text-weak">
              {classifierInfo.labels.length > 0 && <span>Labels: {classifierInfo.labels.join(", ")}</span>}
              <span>guideCandidate: {classifierInfo.guideCandidate ? "true" : "false"}</span>
              {classifierInfo.evidenceMessageIds.length > 0 && (
                <span>Evidence: {classifierInfo.evidenceMessageIds.join(", ")}</span>
              )}
            </div>
          )}
          {hints.length > 0 && (
            <div className="mt-3 flex flex-col gap-2">
              {hints.map((hint) => (
                <div key={hint} className="rounded-lg border border-border-subtle bg-bg-app px-3 py-2 text-xs text-text-muted">
                  {hint}
                </div>
              ))}
            </div>
          )}
        </button>

        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 shrink-0"
          title="Открыть исходное сообщение"
          onClick={() => navigate(`/groups?group=${message.groupId}&message=${message.id}`)}
        >
          <ArrowSquareOut size={17} />
        </Button>
        {message.status === "CLASSIFIED" && (
          <Button
            variant="outline"
            size="sm"
            className="shrink-0"
            disabled={requeueMessage.isPending}
            onClick={(event) => {
              event.stopPropagation();
              requeueMessage.mutate(message.id);
            }}
          >
            {requeueMessage.isPending ? "Отправляю..." : "На перепроверку"}
          </Button>
        )}
        <Button variant="ghost" size="icon" className="h-8 w-8 shrink-0" onClick={onToggle}>
          {expanded ? <CaretUp size={17} /> : <CaretDown size={17} />}
        </Button>
      </div>

      {expanded && (
        <div className="border-t border-border-subtle bg-bg-card px-4 py-4">
          {classifierInfo && (
            <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Статус</div>
                <div className="mt-1 text-xs font-medium text-text-strong">{callout}</div>
              </div>
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Score</div>
                <div className="mt-1 text-xs font-medium text-text-strong">
                  {displayScore != null ? formatNumber(displayScore, 2) : "—"}
                </div>
              </div>
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Labels</div>
                <div className="mt-1 text-xs font-medium text-text-strong">
                  {classifierInfo.labels.length > 0 ? classifierInfo.labels.join(", ") : "—"}
                </div>
              </div>
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Guide candidate</div>
                <div className="mt-1 text-xs font-medium text-text-strong">
                  {classifierInfo.guideCandidate ? "true" : "false"}
                </div>
              </div>
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Evidence IDs</div>
                <div className="mt-1 text-xs font-medium text-text-strong">
                  {classifierInfo.evidenceMessageIds.length > 0 ? classifierInfo.evidenceMessageIds.join(", ") : "—"}
                </div>
              </div>
              <div className="rounded-lg bg-bg-app px-3 py-3">
                <div className="text-[11px] text-text-weak">Context hash</div>
                <div className="mt-1 break-all text-xs font-medium text-text-strong">
                  {message.classificationContextHash || "—"}
                </div>
              </div>
              {classifierInfo.reasoning && (
                <div className="rounded-lg bg-bg-app px-3 py-3 sm:col-span-2 xl:col-span-4">
                  <div className="text-[11px] text-text-weak">Reasoning</div>
                  <div className="mt-1 whitespace-pre-wrap break-words text-xs font-medium text-text-strong">
                    {classifierInfo.reasoning}
                  </div>
                </div>
              )}
              {hints.length > 0 && (
                <div className="rounded-lg bg-bg-app px-3 py-3 sm:col-span-2 xl:col-span-4">
                  <div className="text-[11px] text-text-weak">Пояснение</div>
                  <div className="mt-2 flex flex-col gap-2">
                    {hints.map((hint) => (
                      <div key={hint} className="text-xs font-medium text-text-strong">
                        {hint}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-text-strong">Путь сообщения</h4>
            <Badge variant="outline">{traces.length} этапов</Badge>
          </div>

          {traceQuery.isLoading ? (
            <div className="flex items-center gap-2 py-4 text-sm text-text-muted">
              <SpinnerGap size={18} className="animate-spin" />
              Загружаю этапы...
            </div>
          ) : traces.length === 0 ? (
            <p className="py-4 text-sm text-text-muted">Для этого сообщения этапы пока не записаны.</p>
          ) : (
            <div className="grid gap-4">
              {traces.map((trace, index) => (
                <TraceStep key={trace.id} trace={trace} index={index} total={traces.length} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function PipelinePage() {
  const [view, setView] = useState<PipelineView>("ALL");
  const [expandedMessageId, setExpandedMessageId] = useState<number | null>(null);
  const [timePreset, setTimePreset] = useState<TimePreset>("all");
  const [fromLocal, setFromLocal] = useState(() => toDateTimeLocal(presetDate("15m")));
  const connectionState = usePipelineEvents();
  const navigate = useNavigate();

  const fromIso = useMemo(() => (timePreset === "all" ? undefined : localToIso(fromLocal)), [fromLocal, timePreset]);
  const toIso = useMemo(() => (timePreset === "all" ? undefined : new Date().toISOString()), [timePreset]);

  const pipelineStatusQuery = usePipelineStatusQuery(fromIso, toIso);
  const pausePipeline = usePausePipelineMutation();
  const resumePipeline = useResumePipelineMutation();

  const allResultsQuery = usePipelineResultsQuery(undefined, 0, 100, fromIso, view === "ALL");
  const classifiedQuery = usePipelineResultsQuery("CLASSIFIED", 0, 100, fromIso, view === "CLASSIFIED");
  const skippedQuery = usePipelineResultsQuery("SKIPPED", 0, 100, fromIso, view === "SKIPPED");
  const guideQuery = usePipelineResultsQuery("GUIDE_FOUND", 0, 100, fromIso, view === "GUIDE_FOUND");

  const allItems =
    allResultsQuery.data?.content.filter((message) =>
      ["CLASSIFIED", "GUIDE_FOUND", "SKIPPED"].includes(message.status),
    ) ?? [];
  const classifiedItems = classifiedQuery.data?.content ?? [];
  const skippedItems = skippedQuery.data?.content ?? [];
  const guideItems = guideQuery.data?.content ?? [];
  const pipelineStatus = pipelineStatusQuery.data;

  const currentLoading =
    view === "ALL"
      ? allResultsQuery.isLoading
      : view === "CLASSIFIED"
        ? classifiedQuery.isLoading
        : view === "SKIPPED"
          ? skippedQuery.isLoading
          : guideQuery.isLoading;

  const currentListLength =
    view === "ALL"
      ? allItems.length
      : view === "CLASSIFIED"
        ? classifiedItems.length
        : view === "SKIPPED"
          ? skippedItems.length
          : guideItems.length;

  const applyPreset = (preset: TimePreset) => {
    setTimePreset(preset);
    if (preset !== "custom" && preset !== "all") {
      const date = presetDate(preset);
      if (date) {
        setFromLocal(toDateTimeLocal(date));
      }
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Конвейер"
        description="Здесь видно, что сейчас в очереди, что было отсеяно и какие сообщения дошли до гайда. Очередь можно останавливать и запускать прямо отсюда."
      >
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border-subtle pt-4">
          <div className="flex items-center gap-2 text-sm text-text-muted">
            <span
              className={cn("h-2.5 w-2.5 rounded-full", {
                "bg-success": connectionState === "connected",
                "bg-warning": connectionState === "connecting",
                "bg-danger": connectionState === "disconnected",
              })}
            />
            {connectionState === "connected"
              ? "События Telegram и конвейера приходят в реальном времени"
              : connectionState === "connecting"
                ? "Подключаю realtime..."
                : "Realtime отключен, данные обновляются резервно"}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              className="gap-1.5"
              onClick={() => pausePipeline.mutate()}
              disabled={!pipelineStatus?.processorEnabled || pausePipeline.isPending}
            >
              <Pause size={15} />
              Стоп очереди
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="gap-1.5"
              onClick={() => resumePipeline.mutate()}
              disabled={pipelineStatus?.processorEnabled || resumePipeline.isPending}
            >
              <Play size={15} />
              Запустить очередь
            </Button>
            <Button variant="outline" size="sm" onClick={() => navigate("/guides")}>
              <BookOpen size={16} />
              Смотреть гайды
            </Button>
          </div>
        </div>
      </PageHeaderCard>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <div className="rounded-xl border border-border-subtle bg-bg-card px-4 py-3">
          <div className="text-xs text-text-muted">Очередь</div>
          <div className="mt-1 font-mono-value text-2xl font-semibold text-warning">{pipelineStatus?.queued ?? 0}</div>
        </div>
        <div className="rounded-xl border border-border-subtle bg-bg-card px-4 py-3">
          <div className="text-xs text-text-muted">В обработке</div>
          <div className="mt-1 font-mono-value text-2xl font-semibold text-warning">
            {pipelineStatus?.processing ?? 0}
          </div>
        </div>
        <div className="rounded-xl border border-border-subtle bg-bg-card px-4 py-3">
          <div className="text-xs text-text-muted">Классифицировано {timePreset === "all" ? "за все время" : "в окне"}</div>
          <div className="mt-1 font-mono-value text-2xl font-semibold text-success">
            {pipelineStatus?.classified ?? classifiedQuery.data?.totalElements ?? 0}
          </div>
        </div>
        <div className="rounded-xl border border-border-subtle bg-bg-card px-4 py-3">
          <div className="text-xs text-text-muted">Отсеяно {timePreset === "all" ? "за все время" : "в окне"}</div>
          <div className="mt-1 font-mono-value text-2xl font-semibold text-danger">
            {pipelineStatus?.skipped ?? skippedQuery.data?.totalElements ?? 0}
          </div>
        </div>
        <div className="rounded-xl border border-border-subtle bg-bg-card px-4 py-3">
          <div className="text-xs text-text-muted">С гайдом {timePreset === "all" ? "за все время" : "в окне"}</div>
          <div className="mt-1 font-mono-value text-2xl font-semibold text-success">
            {pipelineStatus?.guideFound ?? guideQuery.data?.totalElements ?? 0}
          </div>
        </div>
      </div>

      <Card>
        <CardContent className="p-0">
          <div className="flex flex-wrap items-center gap-2 border-b border-border-subtle px-4 py-4">
            {[
              { value: "ALL", label: "Все" },
              { value: "GUIDE_FOUND", label: "Гайды" },
              { value: "CLASSIFIED", label: "Классифицировано" },
              { value: "SKIPPED", label: "Отсеяно" },
            ].map((item) => (
              <Button
                key={item.value}
                variant={view === item.value ? "secondary" : "ghost"}
                size="sm"
                onClick={() => setView(item.value as PipelineView)}
              >
                {item.label}
              </Button>
            ))}

            <div className="ml-auto flex flex-wrap items-center gap-2">
              <Clock size={15} className="text-text-muted" />
              {[
                { value: "all", label: "За все время" },
                { value: "live", label: "Сейчас" },
                { value: "5m", label: "5 мин" },
                { value: "15m", label: "15 мин" },
                { value: "1h", label: "1 час" },
              ].map((item) => (
                <Button
                  key={item.value}
                  variant={timePreset === item.value ? "secondary" : "ghost"}
                  size="sm"
                  onClick={() => applyPreset(item.value as TimePreset)}
                >
                  {item.label}
                </Button>
              ))}
              <Input
                type="datetime-local"
                value={fromLocal}
                onChange={(event) => {
                  setTimePreset("custom");
                  setFromLocal(event.target.value);
                }}
                className="h-9 w-[220px]"
                title="Показать сообщения начиная с выбранной даты и времени"
                disabled={timePreset === "all"}
              />
            </div>
          </div>

          {currentLoading ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-text-muted">
              <SpinnerGap size={20} className="animate-spin" />
              Загружаю сообщения...
            </div>
          ) : currentListLength === 0 ? (
            <div className="py-12 text-center text-sm text-text-muted">Для текущего режима пока ничего нет.</div>
          ) : view === "ALL" ? (
            allItems.map((message) => (
              <ResultRow
                key={message.id}
                message={message}
                expanded={expandedMessageId === message.id}
                onToggle={() => setExpandedMessageId((current) => (current === message.id ? null : message.id))}
              />
            ))
          ) : view === "CLASSIFIED" ? (
            classifiedItems.map((message) => (
              <ResultRow
                key={message.id}
                message={message}
                expanded={expandedMessageId === message.id}
                onToggle={() => setExpandedMessageId((current) => (current === message.id ? null : message.id))}
              />
            ))
          ) : view === "SKIPPED" ? (
            skippedItems.map((message) => (
              <ResultRow
                key={message.id}
                message={message}
                expanded={expandedMessageId === message.id}
                onToggle={() => setExpandedMessageId((current) => (current === message.id ? null : message.id))}
              />
            ))
          ) : (
            guideItems.map((message) => (
              <ResultRow
                key={message.id}
                message={message}
                expanded={expandedMessageId === message.id}
                onToggle={() => setExpandedMessageId((current) => (current === message.id ? null : message.id))}
              />
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
