import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  ArrowsClockwise,
  FileDashed,
  FloppyDisk,
  Link as LinkIcon,
  PencilSimple,
  SpinnerGap,
  Trash,
  WarningCircle,
} from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { MarkdownArticle } from "@/components/domain/markdown-article";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { StatusBadge } from "@/components/domain/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useDeleteGuideMutation,
  useDeleteMaterialMutation,
  useGuideDetailQuery,
  useMaterialDetailQuery,
  useRegenerateGuideMutation,
  useUpdateGuideContentMutation,
} from "@/shared/api/guidesApi";
import { useMessageTraceQuery } from "@/shared/api/tracesApi";
import { displayGuideStatus, type Guide, type MaterialProviderCall, type MaterialTraceStage, type PipelineTrace, type SourceMessage } from "@/shared/types";

const STAGE_LABELS: Partial<Record<PipelineTrace["stage"], string>> = {
  RULES: "Правила",
  SIGNAL_SCORING: "Сигнальный скоринг",
  CLASSIFICATION: "Классификация",
  CHAIN_BUILDING: "Сбор цепочки",
  GUIDE_GENERATION: "Генерация гайда",
  MODERATION: "Модерация",
};

function stageDisplayName(stage: PipelineTrace["stage"]): string {
  return STAGE_LABELS[stage] ?? stage;
}

const TRACE_STATUS_LABELS: Partial<Record<PipelineTrace["status"], string>> = {
  SUCCESS: "Успешно",
  PASSED: "Пройдено",
  SKIPPED: "Пропущено",
  FAILED: "Ошибка",
  REJECTED: "Отклонено",
  PENDING: "Ожидает",
  COMPLETED: "Завершено",
};

function traceStatusDisplayName(status: PipelineTrace["status"]): string {
  return TRACE_STATUS_LABELS[status] ?? status;
}

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatDuration(ms: number | null): string {
  if (ms == null || ms === 0) return "нет данных";
  if (ms < 1000) return `${ms} мс`;
  return `${(ms / 1000).toFixed(1)} с`;
}

function relationLabel(relation: string | null): string {
  switch (relation) {
    case "ROOT":
      return "Основное сообщение";
    case "PARENT_REPLY":
      return "Сообщение, на которое ответили";
    case "DIRECT_REPLY":
      return "Прямой ответ";
    case "THREAD_REPLY":
      return "Ответ в треде";
    case "SAME_TOPIC_NEARBY":
      return "Рядом в той же теме";
    case "SAME_AUTHOR_NEARBY":
      return "Рядом от того же автора";
    case "TIMELINE_NEARBY":
      return "Соседнее сообщение";
    default:
      return relation ?? "Контекст";
  }
}

function displayGuideTitle(title: string | null | undefined, id: string): string {
  if (!title) return `Гайд #${id}`;
  const trimmed = title.trim();
  if (!trimmed) return `Гайд #${id}`;
  if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
    return `Гайд #${id}`;
  }
  return trimmed;
}

function displayMaterialTitle(title: string | null | undefined, id: string): string {
  if (!title) return `Материал #${id}`;
  const trimmed = title.trim();
  if (!trimmed) return `Материал #${id}`;
  if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
    return `Материал #${id}`;
  }
  return trimmed;
}

function usefulnessMeta(score: number): { label: string; variant: "danger" | "warning" | "default" | "success" } {
  if (score <= 20) return { label: "слабый", variant: "danger" };
  if (score <= 50) return { label: "сомнительный", variant: "warning" };
  if (score <= 75) return { label: "нормальный", variant: "default" };
  return { label: "сильный", variant: "success" };
}

function TraceTimeline({ steps }: { steps: PipelineTrace[] }) {
  return (
    <div className="flex flex-col gap-3">
      {steps.map((step) => (
        <div key={step.id} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-sm font-semibold text-text-strong">
                {stageDisplayName(step.stage)}
              </div>
              <div className="mt-1 text-xs text-text-muted">
                {traceStatusDisplayName(step.status)} · {formatDuration(step.durationMs)}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              {step.score != null && <Badge variant="outline">Оценка {step.score.toFixed(2)}</Badge>}
              {step.confidence != null && (
                <Badge variant="outline">Уверенность {step.confidence.toFixed(2)}</Badge>
              )}
              {step.model && <Badge variant="outline">{step.model}</Badge>}
            </div>
          </div>
          {step.reason && <div className="mt-2 text-sm text-text-default">{step.reason}</div>}
          {step.errorMessage && (
            <div className="mt-2 text-sm text-danger">{step.errorMessage}</div>
          )}
        </div>
      ))}
    </div>
  );
}

function materialTypeLabel(value: string | null | undefined) {
  const normalized = value?.toUpperCase();
  if (normalized === "GUIDE") return "Guide";
  if (normalized === "GENERATION") return "Generation";
  if (normalized === "ANSWER") return "Answer";
  if (normalized === "CLUSTER_SUMMARY") return "Сводка";
  return value ?? "Material";
}

function candidateLabel(value: string | null | undefined) {
  const normalized = value?.toUpperCase();
  if (normalized === "SINGLE_MESSAGE") return "Single message";
  if (normalized === "DISCUSSION_SEGMENT") return "Discussion segment";
  if (normalized === "MACRO") return "Macro cluster";
  if (normalized === "MICRO") return "Micro cluster";
  return value ?? "—";
}

function qualityText(score: number | null) {
  if (score == null) return "Качество не оценено";
  if (score >= 90) return "сильный материал";
  if (score >= 75) return "требует проверки";
  return "слабый материал";
}

function firstSource(guide: Guide): SourceMessage | null {
  return guide.sourceMessages[0] ?? null;
}

function rawId(guide: Guide): string | null {
  return firstSource(guide)?.rawId ?? guide.traceStages.find((stage) => stage.rawId != null)?.rawId ?? null;
}

function sourceTextFallback(message: SourceMessage) {
  if (message.text) return null;
  if (message.textUnavailableReason === "media_without_caption") return "Медиа без подписи";
  if (message.textUnavailableReason === "source_raw_message_missing") return "Текст источника не найден";
  return message.textUnavailableReason ?? "Источник не найден";
}

function CompactRow({ label, value }: { label: string; value: React.ReactNode }) {
  return <div className="flex items-start justify-between gap-4 border-b border-border-subtle/70 py-2 text-sm last:border-b-0"><span className="text-text-muted">{label}</span><span className="text-right text-text-strong">{value}</span></div>;
}

function QualityCard({ score }: { score: number | null }) {
  return <Card><CardContent className="p-4"><div className="text-sm font-semibold text-text-strong">Качество</div>{score == null ? <div className="mt-4 text-sm text-text-muted">Качество не оценено</div> : <><div className="mt-4 font-mono-value text-4xl font-semibold text-text-strong">{score} <span className="text-lg text-text-muted">/ 100</span></div><Badge className="mt-3" variant={score >= 90 ? "success" : score >= 75 ? "warning" : "outline"}>{qualityText(score)}</Badge></>}</CardContent></Card>;
}

function ProviderInfoCard({ calls, guide }: { calls: MaterialProviderCall[]; guide: Guide }) {
  const first = calls[0];
  const totalCost = calls.reduce((sum, call) => sum + (call.estimatedCostUsd ?? 0), 0);
  const totalTokens = calls.reduce((sum, call) => sum + (call.inputTokens ?? 0) + (call.outputTokens ?? 0), 0);
  return <Card><CardContent className="p-4"><div className="text-sm font-semibold text-text-strong">LLM / Провайдер</div><div className="mt-3"><CompactRow label="Provider calls" value={calls.length} /><CompactRow label="Model" value={<span className="font-mono-value">{first?.model ?? guide.model ?? "—"}</span>} /><CompactRow label="Provider" value={first?.providerId ?? guide.providerId ?? "—"} /><CompactRow label="Tokens" value={totalTokens || guide.totalTokens || "—"} /><CompactRow label="Cost" value={totalCost > 0 ? `$${totalCost.toFixed(4)}` : guide.estimatedCost ? `$${guide.estimatedCost.toFixed(4)}` : "—"} /></div></CardContent></Card>;
}

function WhyCreatedCard({ guide }: { guide: Guide }) {
  const signals = [guide.contentSubtype, guide.publicationKind, guide.safetyCategory].filter(Boolean) as string[];
  return <Card><CardContent className="p-4"><div className="text-sm font-semibold text-text-strong">Почему создано</div><div className="mt-3"><CompactRow label="Тип кандидата" value={candidateLabel(guide.candidate?.type ?? guide.publicationKind)} /><CompactRow label="Оценка кандидата" value={guide.confidence != null ? guide.confidence.toFixed(2) : "—"} /></div><div className="mt-3 flex flex-wrap gap-2">{signals.length === 0 ? <span className="text-sm text-text-muted">Сигналы не сохранены.</span> : signals.map((signal) => <Badge key={signal} variant="secondary">{signal}</Badge>)}</div></CardContent></Card>;
}

function PipelineTraceCard({ stages }: { stages: MaterialTraceStage[] }) {
  return <Card><CardContent className="p-4"><div className="text-sm font-semibold text-text-strong">Путь сообщения</div><div className="mt-4 flex flex-col gap-3">{stages.length === 0 ? <div className="text-sm text-text-muted">Трассировка не найдена.</div> : stages.map((stage) => <div key={stage.id} className="flex gap-3"><div className="mt-1 h-2.5 w-2.5 rounded-full bg-success" /><div className="min-w-0 flex-1"><div className="flex items-center justify-between gap-2"><span className="truncate text-sm font-medium text-text-strong">{stage.stageName ?? stage.stage}</span><Badge variant={stage.status === "PROCESSED" ? "success" : stage.status === "FAILED" ? "danger" : "outline"}>{stage.status}</Badge></div><div className="mt-1 text-xs text-text-muted">raw #{stage.rawId ?? "—"} · {stage.reason ?? "—"} · {formatDuration(stage.durationMs)}</div></div></div>)}</div></CardContent></Card>;
}

function SourceMessageCard({ guide }: { guide: Guide }) {
  const source = firstSource(guide);
  return <Card><CardContent className="p-4 sm:p-5"><div className="text-sm font-semibold text-text-strong">Исходное сообщение</div>{!source ? <div className="mt-3 text-sm text-text-muted">Источник не найден</div> : <><div className="mt-3 flex flex-wrap gap-2"><Badge variant="outline">{source.chatTitle ?? guide.sourceGroupTitle ?? "Группа не указана"}</Badge>{source.topicName ? <Badge variant="outline">{source.topicName}</Badge> : null}{source.rawId ? <Badge variant="outline">raw #{source.rawId}</Badge> : null}{source.telegramMessageId ? <Badge variant="outline">Telegram #{source.telegramMessageId}</Badge> : null}{source.messageDate ? <Badge variant="outline">{formatDate(source.messageDate)}</Badge> : null}</div><div className="mt-4 rounded-2xl border border-border-subtle bg-bg-app/60 p-4 text-sm leading-6 text-text-default">{source.text ? <RichMessageText text={source.text} entities={source.textEntities} /> : <span className="text-text-muted">{sourceTextFallback(source)}</span>}</div><div className="mt-4 flex flex-wrap gap-2">{(source.appMessageUrl ?? source.internalMessageUrl) ? <Button asChild variant="primary" size="sm"><a href={source.appMessageUrl ?? source.internalMessageUrl ?? undefined}><LinkIcon size={14} />Открыть в чате</a></Button> : null}{source.telegramLinkAvailable && source.telegramMessageUrl ? <Button asChild variant="outline" size="sm"><a href={source.telegramMessageUrl} target="_blank" rel="noreferrer"><LinkIcon size={14} />Открыть в Telegram</a></Button> : <Button variant="outline" size="sm" disabled>{source.telegramLinkReason ?? "Telegram-ссылка недоступна для приватного чата"}</Button>}</div></>}</CardContent></Card>;
}

function JsonDetails({ value }: { value: unknown }) {
  if (value == null || value === "") return <span className="text-text-muted">нет данных</span>;
  return <pre className="mt-2 max-h-72 overflow-auto rounded-xl bg-bg-app/70 p-3 text-xs text-text-default">{typeof value === "string" ? value : JSON.stringify(value, null, 2)}</pre>;
}

function SourceMessagesPanel({ guide }: { guide: Guide }) {
  const [showAll, setShowAll] = useState(false);
  const sourceCount = guide.sourceCount || guide.sourceMessages.length;
  const isMultiSource = sourceCount > 1;
  const visibleSources = showAll ? guide.sourceMessages : guide.sourceMessages.slice(0, 3);
  return <Card><CardContent className="p-4 sm:p-5"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-sm font-semibold text-text-strong">{isMultiSource ? "Источники материала" : "Источник"}</div>{isMultiSource ? <div className="mt-1 text-sm text-text-muted">Материал составлен из нескольких сообщений, объединённых в discussion segment / cluster.</div> : null}</div><div className="flex flex-wrap gap-2"><Badge variant="outline">{candidateLabel(guide.candidate?.type ?? guide.candidateType ?? guide.publicationKind)}</Badge><Badge variant="outline">sources: {sourceCount}</Badge>{guide.segmentId ? <Badge variant="outline">segment #{guide.segmentId}</Badge> : null}{guide.clusterId ? <Badge variant="outline">cluster #{guide.clusterId}</Badge> : null}</div></div><div className="mt-4 grid gap-2 text-sm sm:grid-cols-2"><CompactRow label="Тип кандидата" value={candidateLabel(guide.candidate?.type ?? guide.candidateType ?? guide.publicationKind)} /><CompactRow label="Source count" value={sourceCount} /><CompactRow label="Raw ids" value={guide.sourceMessages.map((source) => source.rawId).filter(Boolean).join(", ") || "—"} /><CompactRow label="Chat/topic" value={guide.sourceMessages[0]?.chatTitle ?? guide.sourceMessages[0]?.topicName ?? "—"} /><CompactRow label="LLM judge" value={guide.segmentDecision ?? "—"} /><CompactRow label="Generation" value={guide.providerCalls.find((call) => call.stage === "KNOWLEDGE_GENERATION")?.status ?? "—"} /></div><div className="mt-5 flex flex-col gap-3">{visibleSources.length === 0 ? <div className="text-sm text-text-muted">Источники не найдены.</div> : visibleSources.map((source, index) => <details key={`${source.datasetMessageId ?? index}-${source.rawId ?? "raw"}`} open={index < 3} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-4"><summary className="cursor-pointer text-sm font-semibold text-text-strong">#{source.orderIndex ?? index} · raw #{source.rawId ?? "—"} · dataset #{source.datasetMessageId ?? "—"}</summary><div className="mt-3 flex flex-wrap gap-2"><Badge variant="outline">{source.chatTitle ?? "chat —"}</Badge>{source.topicName ? <Badge variant="outline">{source.topicName}</Badge> : null}{source.threadId ?? source.topicId ? <Badge variant="outline">thread {source.threadId ?? source.topicId}</Badge> : null}<Badge variant="outline">role: {source.role ?? source.relation ?? "unknown"}</Badge>{source.replayRunMessageId ? <Badge variant="outline">rrm #{source.replayRunMessageId}</Badge> : null}{source.messageDate ? <Badge variant="outline">{formatDate(source.messageDate)}</Badge> : null}</div><div className="mt-3 text-sm text-text-muted">Автор: {source.author ?? source.senderDisplayName ?? source.senderUsername ?? "—"}</div><div className="mt-3 rounded-xl border border-border-subtle bg-bg-surface p-3 text-sm leading-6 text-text-default">{source.text ? <RichMessageText text={source.text} entities={source.textEntities} /> : <span className="text-text-muted">{sourceTextFallback(source)}</span>}</div><div className="mt-3 flex flex-wrap gap-2">{(source.appMessageUrl ?? source.internalMessageUrl) ? <Button asChild variant="primary" size="sm"><a href={source.appMessageUrl ?? source.internalMessageUrl ?? undefined}><LinkIcon size={14} />Открыть в чате</a></Button> : null}{source.telegramLinkAvailable && source.telegramMessageUrl ? <Button asChild variant="outline" size="sm"><a href={source.telegramMessageUrl} target="_blank" rel="noreferrer"><LinkIcon size={14} />Открыть в Telegram</a></Button> : <Button variant="outline" size="sm" disabled>{source.telegramLinkReason ?? "Telegram-ссылка недоступна"}</Button>}</div></details>)}</div>{!showAll && guide.sourceMessages.length > 3 ? <Button className="mt-4" variant="outline" onClick={() => setShowAll(true)}>Показать все источники</Button> : null}</CardContent></Card>;
}

function HowBuiltCard({ guide }: { guide: Guide }) {
  const steps = guide.howBuiltSteps ?? [];
  return <Card><CardContent className="p-4 sm:p-5"><div className="text-sm font-semibold text-text-strong">Как был составлен материал</div><div className="mt-4 flex flex-col gap-3">{steps.length === 0 ? <div className="text-sm text-text-muted">Детали сборки не найдены.</div> : steps.map((step, index) => <div key={`${step.title}-${index}`} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div className="text-sm font-semibold text-text-strong">{index + 1}. {step.title}</div><Badge variant="outline">{step.status ?? "—"}</Badge></div><JsonDetails value={step.details} /></div>)}</div></CardContent></Card>;
}

function TraceProviderDetailsCard({ guide }: { guide: Guide }) {
  return <Card><CardContent className="p-4 sm:p-5"><div className="text-sm font-semibold text-text-strong">Trace / provider calls</div>{guide.generationSkipReason ? <Alert className="mt-4" variant="warning"><WarningCircle size={18} weight="fill" /><AlertTitle>Generation skip reason</AlertTitle><AlertDescription>{guide.generationSkipReason}</AlertDescription></Alert> : null}<div className="mt-4"><div className="text-xs font-semibold uppercase tracking-wide text-text-muted">Provider calls</div><div className="mt-2 flex flex-col gap-3">{guide.providerCalls.length === 0 ? <div className="text-sm text-text-muted">Provider calls не найдены.</div> : guide.providerCalls.map((call) => <details key={call.id} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3"><summary className="cursor-pointer text-sm font-semibold text-text-strong">{call.stage ?? "stage"} · {call.status ?? "status"} · {call.model ?? "model —"}</summary><div className="mt-3 grid gap-2 text-sm sm:grid-cols-2"><CompactRow label="duration" value={formatDuration(call.latencyMs)} /><CompactRow label="http" value={call.httpStatus ?? "—"} /><CompactRow label="tokens" value={(call.inputTokens ?? 0) + (call.outputTokens ?? 0)} /><CompactRow label="cost" value={call.estimatedCostUsd ? `$${call.estimatedCostUsd}` : "—"} /></div>{call.errorMessage ? <div className="mt-2 text-sm text-danger">{call.errorMessage}</div> : null}<JsonDetails value={call.responseJson ?? call.responsePreview ?? call.requestPreview} /></details>)}</div></div><div className="mt-5"><div className="text-xs font-semibold uppercase tracking-wide text-text-muted">Trace stages</div><div className="mt-2 flex flex-col gap-3">{guide.traceStages.length === 0 ? <div className="text-sm text-text-muted">Trace stages не найдены.</div> : guide.traceStages.map((stage) => <details key={stage.id} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3"><summary className="cursor-pointer text-sm font-semibold text-text-strong">{stage.stageName ?? stage.stage} · {stage.status}</summary><div className="mt-3 grid gap-2 text-sm sm:grid-cols-2"><CompactRow label="raw" value={stage.rawId ?? "—"} /><CompactRow label="duration" value={formatDuration(stage.durationMs)} /><CompactRow label="reason" value={stage.reason ?? "—"} /><CompactRow label="error" value={stage.errorCode ?? "—"} /></div>{stage.errorMessage ? <div className="mt-2 text-sm text-danger">{stage.errorMessage}</div> : null}<JsonDetails value={stage.outputJson} /></details>)}</div></div></CardContent></Card>;
}

function MaterialDetailViewV2({ guide, title, qualityScore, displayMarkdown, showContentParseError, onRefresh }: { guide: Guide; title: string; qualityScore: number | null; displayMarkdown: string; showContentParseError: boolean; onRefresh: () => void }) {
  return <div className="flex flex-col gap-5"><PageHeaderCard title={title} description={`Материалы / ${materialTypeLabel(guide.contentType)}`} pipelineNote={`run_id: ${guide.run?.id ?? "—"} · source_count: ${guide.sourceCount ?? guide.sourceMessages.length} · создано: ${formatDate(guide.createdAt)}`} actions={{ onRefresh }}><div className="mt-3 flex flex-wrap items-center gap-2"><Badge variant="secondary">{materialTypeLabel(guide.contentType)}</Badge><StatusBadge status={displayGuideStatus(guide.status)} /><Badge variant="outline">{candidateLabel(guide.candidate?.type ?? guide.candidateType ?? guide.publicationKind)}</Badge></div></PageHeaderCard>{guide.generationError ? <Alert variant="danger"><WarningCircle size={18} weight="fill" /><AlertTitle>Материал не собрался автоматически</AlertTitle><AlertDescription><div className="whitespace-pre-wrap break-words">{guide.generationError}</div></AlertDescription></Alert> : null}<div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]"><div className="flex flex-col gap-5"><Card><CardContent className="p-0"><div className="border-b border-border-subtle px-5 py-4 text-sm font-semibold text-text-strong">Материал</div><div className="p-5 sm:p-6">{showContentParseError ? <Alert><WarningCircle size={18} weight="fill" /><AlertTitle>Контент не распарсился</AlertTitle><AlertDescription>Основной контент не показывается, чтобы не путать JSON с готовым материалом.</AlertDescription></Alert> : <MarkdownArticle markdown={displayMarkdown} />}</div></CardContent></Card><SourceMessagesPanel guide={guide} /><HowBuiltCard guide={guide} /><TraceProviderDetailsCard guide={guide} /></div><div className="flex flex-col gap-5"><QualityCard score={qualityScore} /><ProviderInfoCard calls={guide.providerCalls} guide={guide} /><WhyCreatedCard guide={guide} /></div></div></div>;
}

function MaterialDetailView({ guide, title, qualityScore, displayMarkdown, showContentParseError, onRefresh }: { guide: Guide; title: string; qualityScore: number | null; displayMarkdown: string; showContentParseError: boolean; onRefresh: () => void }) {
  const source = firstSource(guide);
  return <div className="flex flex-col gap-5"><PageHeaderCard title={title} description={`Материалы / ${materialTypeLabel(guide.contentType)}`} pipelineNote={`run_id: ${guide.run?.id ?? "—"} · raw_id: ${rawId(guide) ?? "—"} · создано: ${formatDate(guide.createdAt)}`} actions={{ onRefresh }}><div className="mt-3 flex flex-wrap items-center gap-2"><Badge variant="secondary">{materialTypeLabel(guide.contentType)}</Badge><StatusBadge status={displayGuideStatus(guide.status)} /><Badge variant="outline">{candidateLabel(guide.candidate?.type ?? guide.publicationKind)}</Badge></div></PageHeaderCard>{guide.generationError ? <Alert variant="danger"><WarningCircle size={18} weight="fill" /><AlertTitle>Материал не собрался автоматически</AlertTitle><AlertDescription><div className="whitespace-pre-wrap break-words">{guide.generationError}</div></AlertDescription></Alert> : null}<div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]"><div className="flex flex-col gap-5"><Card><CardContent className="p-0"><div className="border-b border-border-subtle px-5 py-4 text-sm font-semibold text-text-strong">Материал</div><div className="p-5 sm:p-6">{showContentParseError ? <Alert><WarningCircle size={18} weight="fill" /><AlertTitle>Контент не распарсился</AlertTitle><AlertDescription>Основной контент не показывается, чтобы не путать JSON с готовым материалом.</AlertDescription></Alert> : <MarkdownArticle markdown={displayMarkdown} />}</div></CardContent></Card><SourceMessageCard guide={guide} /><Card><CardContent className="p-4 text-sm text-text-muted"><div className="font-semibold text-text-strong">Диагностика источника и генерации</div><div className="mt-3 flex flex-wrap gap-3"><span>Provider calls: {guide.providerCalls.length}</span><span>trace visible: {guide.traceStages.length > 0 ? "yes" : "no"}</span><span>run_id: {guide.run?.id ?? "—"}</span><span>raw_id: {source?.rawId ?? rawId(guide) ?? "—"}</span><span>Создано: {formatDate(guide.createdAt)}</span></div></CardContent></Card></div><div className="flex flex-col gap-4"><QualityCard score={qualityScore} /><ProviderInfoCard calls={guide.providerCalls} guide={guide} /><WhyCreatedCard guide={guide} /><PipelineTraceCard stages={guide.traceStages} /></div></div></div>;
}

export function GuideDetailPage() {
  const { guideId } = useParams<{ guideId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isMaterialRoute = location.pathname.startsWith("/materials/");
  const guideQuery = useGuideDetailQuery(guideId, !isMaterialRoute);
  const materialQuery = useMaterialDetailQuery(guideId, isMaterialRoute);
  const detailQuery = isMaterialRoute ? materialQuery : guideQuery;
  const updateGuideContentMutation = useUpdateGuideContentMutation();
  const regenerateGuideMutation = useRegenerateGuideMutation();
  const deleteGuideMutation = useDeleteGuideMutation();
  const deleteMaterialMutation = useDeleteMaterialMutation();
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState("");
  const [operationNotice, setOperationNotice] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const guide = detailQuery.data;
  const traceQuery = useMessageTraceQuery(guide?.rootMessageId ?? undefined);

  const sourceSummary = useMemo(() => {
    if (!guide) return { total: 0, usedInPrompt: 0 };
    return {
      total: guide.sourceMessages.length,
      usedInPrompt: guide.sourceMessages.filter((message) => message.usedInPrompt).length,
    };
  }, [guide]);

  const renderedTitle = guide
    ? isMaterialRoute
      ? displayMaterialTitle(guide.contentTitle || guide.topicLabel || guide.title, guide.id)
      : displayGuideTitle(guide.title, guide.id)
    : "";
  const entityNominative = isMaterialRoute ? "материал" : "гайд";
  const entityGenitive = isMaterialRoute ? "материала" : "гайда";
  const qualityScore = guide?.contentQualityScore ?? guide?.usefulnessScore ?? null;
  const usefulness = qualityScore != null ? usefulnessMeta(qualityScore) : null;
  const displayMarkdown = guide?.contentMarkdown || guide?.content || "";
  const showContentParseError = !displayMarkdown && (!!guide?.rawResponse || !!guide?.generationError);
  const titleContentForSave = guide?.content?.trim()
    ? guide.content
    : guide?.contentMarkdown?.trim()
      ? guide.contentMarkdown
      : "";
  const canSaveTitle =
    !!guide &&
    titleDraft.trim().length > 0 &&
    titleContentForSave.trim().length > 0 &&
    titleDraft.trim() !== (guide.title ?? "").trim();

  useEffect(() => {
    if (guide && !isEditingTitle) {
      setTitleDraft(guide.title ?? "");
    }
  }, [guide, isEditingTitle]);

  if (detailQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <SpinnerGap size={24} className="animate-spin text-text-muted" />
      </div>
    );
  }

  if (!guide) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeaderCard
          title={isMaterialRoute ? "Материал не найден" : "Гайд не найден"}
          description={isMaterialRoute ? "Запрошенный материал отсутствует или был удалён." : "Запрошенный гайд отсутствует или был удалён."}
        />
        <EmptyState
          icon={FileDashed}
          title={isMaterialRoute ? "Материал не найден" : "Гайд не найден"}
          description={isMaterialRoute ? "Запрошенный материал отсутствует или был удалён." : "Запрошенный гайд отсутствует или был удалён."}
        />
      </div>
    );
  }

  if (isMaterialRoute) {
    return (
      <div className="flex flex-col gap-5">
        <MaterialDetailViewV2
          guide={guide}
          title={renderedTitle}
          qualityScore={qualityScore}
          displayMarkdown={displayMarkdown}
          showContentParseError={showContentParseError}
          onRefresh={() => detailQuery.refetch()}
        />
        <Card>
          <CardContent className="flex flex-wrap items-center justify-between gap-3 p-4">
            <div>
              <div className="text-sm font-semibold text-text-strong">Удаление материала</div>
              <div className="mt-1 text-sm text-text-muted">Материал исчезнет из списка. Исходные сообщения и трассировка останутся в базе.</div>
            </div>
            <Button
              variant="outline"
              className="text-danger hover:text-danger"
              disabled={deleteMaterialMutation.isPending}
              onClick={() => {
                if (window.confirm("Удалить материал? Он исчезнет из списка. Исходные сообщения и трассировка останутся в базе.")) {
                  deleteMaterialMutation.mutate(guide.id, { onSuccess: () => navigate("/materials") });
                }
              }}
            >
              <Trash size={16} />
              Удалить материал
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const regenerateGuide = () => {
    if (sourceSummary.total === 0) {
      setOperationNotice("Перегенерация недоступна: у гайда нет исходных сообщений.");
      return;
    }
    regenerateGuideMutation.mutate(guide.id, {
      onSuccess: (result) => {
        setOperationNotice(`${isMaterialRoute ? "Материал" : "Гайд"} #${result.newGuideId} обновлён.`);
      },
      onError: (error) => {
        setOperationNotice(error instanceof Error ? error.message : "Не удалось перегенерировать гайд.");
      },
    });
  };

  const deleteGuide = () => {
    deleteGuideMutation.mutate(guide.id, {
      onSuccess: () => {
        setDeleteDialogOpen(false);
        navigate(isMaterialRoute ? "/materials" : "/materials?contentType=GUIDE");
      },
      onError: (error) => {
        setOperationNotice(error instanceof Error ? error.message : `Не удалось удалить ${isMaterialRoute ? "материал" : "гайд"}.`);
      },
    });
  };

  const saveTitle = () => {
    if (!guide || !canSaveTitle) return;

    updateGuideContentMutation.mutate(
      {
        id: guide.id,
        title: titleDraft.trim(),
        content: titleContentForSave,
        contentMarkdown: guide.contentMarkdown ?? guide.content ?? null,
      },
      {
        onSuccess: () => {
          setIsEditingTitle(false);
        },
      }
    );
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title={renderedTitle}
        description={`${isMaterialRoute ? "Материал" : "Гайд"} #${guide.id} · ${
          guide.sourceGroupTitle
            ? `${guide.sourceGroupTitle}${guide.sourceGroupId ? ` (#${guide.sourceGroupId})` : ""}`
            : guide.sourceGroupId
              ? `группа #${guide.sourceGroupId}`
              : "без группы"
        }`}
        pipelineNote={`${guide.model ?? "модель —"} · токены ${guide.totalTokens.toLocaleString()}`}
        actions={{
          onRefresh: () => {
            detailQuery.refetch();
            traceQuery.refetch();
          },
        }}
      >
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <StatusBadge status={displayGuideStatus(guide.status)} />
          {usefulness && qualityScore != null ? (
            <Badge variant={usefulness.variant}>
              Качество {qualityScore}/100 · {usefulness.label}
            </Badge>
          ) : (
            <Badge variant="outline">Качество не оценено</Badge>
          )}
          {guide.confidence != null ? (
            <Badge variant="outline">Уверенность {guide.confidence.toFixed(2)}</Badge>
          ) : null}
          <Badge variant="outline">Стоимость ${guide.estimatedCost.toFixed(4)}</Badge>
          <Badge variant="outline">Источников: {sourceSummary.total}</Badge>
          <Badge variant="outline">В промпте: {sourceSummary.usedInPrompt}</Badge>
          {guide.rootMessageId && <Badge variant="outline">Корневое сообщение #{guide.rootMessageId}</Badge>}
          {guide.regeneratedFromGuideId && (
            <Badge variant="outline">Перегенерирован из #{guide.regeneratedFromGuideId}</Badge>
          )}
        </div>
        {guide.tags.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {guide.tags.map((tag) => (
              <Badge key={tag} variant="secondary">
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </PageHeaderCard>

      {guide.generationError && (
        <Alert variant="danger">
          <WarningCircle size={18} weight="fill" />
          <AlertTitle>{isMaterialRoute ? "Материал" : "Гайд"} не собрался автоматически</AlertTitle>
          <AlertDescription>
            <div className="whitespace-pre-wrap break-words">{guide.generationError}</div>
          </AlertDescription>
        </Alert>
      )}

      {!isMaterialRoute && <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          onClick={() => {
            setTitleDraft(guide.title ?? renderedTitle);
            setIsEditingTitle((value) => !value);
          }}
        >
          <PencilSimple size={14} />
          {isEditingTitle ? "Скрыть заголовок" : "Редактировать заголовок"}
        </Button>
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          onClick={regenerateGuide}
          disabled={regenerateGuideMutation.isPending}
        >
          {regenerateGuideMutation.isPending ? (
            <SpinnerGap size={14} className="animate-spin" />
          ) : (
            <ArrowsClockwise size={14} />
          )}
          {regenerateGuideMutation.isPending ? "Перегенерирую..." : "Перегенерировать"}
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="gap-1.5 text-danger hover:text-danger"
          onClick={() => setDeleteDialogOpen(true)}
          disabled={deleteGuideMutation.isPending}
        >
          {deleteGuideMutation.isPending ? (
            <SpinnerGap size={14} className="animate-spin" />
          ) : (
            <Trash size={14} />
          )}
          Удалить
        </Button>
      </div>}

      {operationNotice && (
        <div className="rounded-lg border border-brand-blue/20 bg-brand-blue/5 px-4 py-2 text-sm text-text-strong">
          {operationNotice}
        </div>
      )}

      {!isMaterialRoute && isEditingTitle && (
        <Card>
          <CardContent className="p-4">
            <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]">
              <Input
                value={titleDraft}
                onChange={(event) => setTitleDraft(event.target.value)}
                placeholder={isMaterialRoute ? "Заголовок материала" : "Заголовок гайда"}
                aria-label={isMaterialRoute ? "Заголовок материала" : "Заголовок гайда"}
              />
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="primary"
                  className="gap-1.5"
                  onClick={saveTitle}
                  disabled={!canSaveTitle || updateGuideContentMutation.isPending}
                >
                  {updateGuideContentMutation.isPending ? (
                    <SpinnerGap size={14} className="animate-spin" />
                  ) : (
                    <FloppyDisk size={14} />
                  )}
                  Сохранить
                </Button>
                <Button
                  variant="outline"
                  onClick={() => {
                    setTitleDraft(guide.title ?? "");
                    setIsEditingTitle(false);
                  }}
                  disabled={updateGuideContentMutation.isPending}
                >
                  Отмена
                </Button>
              </div>
            </div>
            {titleDraft.trim() && !titleContentForSave.trim() && (
              <div className="mt-2 text-xs text-warning">
                Сохранение заголовка недоступно: у {entityGenitive} пустое содержимое.
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className={guide.generationError ? "border-danger/30" : undefined}>
          <CardContent className="p-0">
            <div className="border-b border-border-subtle px-4 py-3 text-sm font-semibold text-text-strong">
              Содержимое {entityGenitive}
            </div>
            <ScrollArea className="h-[460px]">
              <div className="p-4">
                {showContentParseError ? (
                  <Alert>
                    <WarningCircle size={18} weight="fill" />
                    <AlertTitle>Контент не распарсился как {entityNominative}</AlertTitle>
                    <AlertDescription>
                      Сырая модельная выдача сохранена во вкладке «Сырой ответ». Основной контент не показывается, чтобы не
                      путать JSON с готовым {entityNominative}.
                    </AlertDescription>
                  </Alert>
                ) : (
                  <MarkdownArticle markdown={displayMarkdown} />
                )}
              </div>
            </ScrollArea>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-4">
            <div className="text-sm font-semibold text-text-strong">Метаданные</div>
            <div className="mt-4 grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
              <span className="text-text-muted">Создан</span>
              <span>{formatDate(guide.createdAt)}</span>
              <span className="text-text-muted">Опубликован</span>
              <span>{formatDate(guide.publishedAt)}</span>
              <span className="text-text-muted">Провайдер</span>
              <span>{guide.llmRequest?.providerId ?? "—"}</span>
              <span className="text-text-muted">Модель</span>
              <span className="font-mono-value">{guide.llmRequest?.model ?? guide.model ?? "—"}</span>
              <span className="text-text-muted">Run</span>
              <span className="font-mono-value">{guide.run?.id ?? "—"}</span>
              <span className="text-text-muted">Candidate</span>
              <span>{guide.candidate?.type ?? guide.publicationKind ?? "—"}</span>
              <span className="text-text-muted">Terminal reason</span>
              <span>{guide.run?.terminalReason ?? "—"}</span>
              <span className="text-text-muted">Промпт</span>
              <span>{guide.llmRequest?.promptVersion ?? guide.promptVersion ?? "—"}</span>
              <span className="text-text-muted">Входные токены</span>
              <span className="font-mono-value">{guide.llmRequest?.inputTokens.toLocaleString() ?? "0"}</span>
              <span className="text-text-muted">Выходные токены</span>
              <span className="font-mono-value">{guide.llmRequest?.outputTokens.toLocaleString() ?? "0"}</span>
              <span className="text-text-muted">Дубликат</span>
              <span>{guide.duplicateOfGuideId ?? "—"}</span>
              <span className="text-text-muted">Сходство дубликата</span>
              <span className="font-mono-value">
                {guide.duplicateScore != null ? guide.duplicateScore.toFixed(2) : "—"}
              </span>
            </div>
            {guide.providerCalls.length > 0 && (
              <>
                <Separator className="my-4" />
                <div className="text-sm font-semibold text-text-strong">LLM/provider calls</div>
                <div className="mt-2 flex flex-col gap-2 text-xs text-text-default">
                  {guide.providerCalls.map((call) => (
                    <div key={call.id} className="rounded-lg border border-border-subtle bg-bg-app/60 p-2">
                      <div className="flex flex-wrap gap-2">
                        <Badge variant="outline">#{call.id}</Badge>
                        <Badge variant={call.status === "SUCCESS" ? "success" : "outline"}>{call.status ?? "UNKNOWN"}</Badge>
                        <Badge variant="outline">{call.model ?? "model —"}</Badge>
                      </div>
                      <div className="mt-1 text-text-muted">
                        tokens {call.inputTokens + call.outputTokens} · ${call.estimatedCostUsd.toFixed(4)} · {call.latencyMs ?? "—"} ms
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
            {isMaterialRoute && guide.traceStages.length > 0 && (
              <>
                <Separator className="my-4" />
                <div className="text-sm font-semibold text-text-strong">Путь сообщения</div>
                <div className="mt-2 flex flex-col gap-2 text-xs text-text-default">
                  {guide.traceStages.map((stage) => (
                    <div key={stage.id} className="rounded-lg border border-border-subtle bg-bg-app/60 p-2">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-medium text-text-strong">{stage.stageName ?? stage.stage}</span>
                        <Badge variant={stage.status === "PROCESSED" ? "success" : stage.status === "FAILED" ? "danger" : "outline"}>
                          {stage.status}
                        </Badge>
                      </div>
                      <div className="mt-1 text-text-muted">
                        raw #{stage.rawId ?? "—"} · {stage.reason ?? "no reason"} · {formatDuration(stage.durationMs)}
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
            <Separator className="my-4" />
            <div className="text-sm font-semibold text-text-strong">
              {isMaterialRoute ? "Связанные материалы" : "Связанные гайды"}
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              {guide.relatedGuideIds.length === 0 ? (
                <span className="text-sm text-text-muted">
                  {isMaterialRoute ? "Нет связанных материалов" : "Нет связанных гайдов"}
                </span>
              ) : (
                guide.relatedGuideIds.map((id) => (
                  <Badge key={id} variant="outline">
                    #{id}
                  </Badge>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="sources">
        <TabsList>
          <TabsTrigger value="sources">Источники</TabsTrigger>
          <TabsTrigger value="raw">Сырой ответ</TabsTrigger>
          <TabsTrigger value="trace">Трассировка</TabsTrigger>
        </TabsList>

        <TabsContent value="sources">
          <Card>
            <CardContent className="p-4">
              <div className="mb-4 text-sm font-semibold text-text-strong">
                Исходные сообщения, на которых построен {entityNominative}
              </div>
              <div className="flex flex-col gap-3">
                {guide.sourceMessages.length === 0 ? (
                  <div className="text-sm text-text-muted">Источник не сохранён.</div>
                ) : (
                  guide.sourceMessages.map((message) => (
                    <div
                      key={message.messageId}
                      className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3"
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="font-medium text-text-strong">
                          {message.senderDisplayName ?? "Неизвестный автор"}
                        </div>
                        {message.senderUsername && (
                          <Badge variant="outline">@{message.senderUsername}</Badge>
                        )}
                        <Badge variant={message.usedInPrompt ? "success" : "outline"}>
                          {message.usedInPrompt ? "В промпте" : "Только сохранено"}
                        </Badge>
                        <Badge variant="secondary">{relationLabel(message.relation)}</Badge>
                        <Badge variant="outline">БД #{message.messageId}</Badge>
                        {message.rawId && <Badge variant="outline">raw #{message.rawId}</Badge>}
                        {message.telegramMessageId && (
                          <Badge variant="outline">Telegram #{message.telegramMessageId}</Badge>
                        )}
                        {message.messageDate && <Badge variant="outline">{formatDate(message.messageDate)}</Badge>}
                        {message.replyToTelegramMessageId && (
                          <Badge variant="outline">Ответ на #{message.replyToTelegramMessageId}</Badge>
                        )}
                        {message.topicName && <Badge variant="outline">{message.topicName}</Badge>}
                        {message.senderNameSource && (
                          <Badge variant="outline">Источник имени: {message.senderNameSource}</Badge>
                        )}
                      </div>
                      <div className="mt-2 whitespace-pre-wrap text-sm text-text-default">
                        {message.text ? (
                          <RichMessageText
                            text={message.text}
                            entities={message.textEntities}
                          />
                        ) : (
                          <span className="text-text-muted">
                            {message.textUnavailableReason === "media_without_caption"
                              ? "Медиа без подписи"
                              : message.textUnavailableReason === "source_raw_message_missing"
                                ? "Текст источника не найден: raw message missing"
                                : "Текст источника не найден"}
                          </span>
                        )}
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {(message.appMessageUrl ?? message.internalMessageUrl) && (
                          <a
                            href={message.appMessageUrl ?? message.internalMessageUrl ?? undefined}
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-default hover:bg-bg-card"
                          >
                            <LinkIcon size={12} />
                            Открыть в чате
                          </a>
                        )}
                        {message.telegramLinkAvailable && message.telegramMessageUrl ? (
                          <a
                            href={message.telegramMessageUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-default hover:bg-bg-card"
                          >
                            <LinkIcon size={12} />
                            Открыть в Telegram
                          </a>
                        ) : (
                          <span
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-muted"
                          >
                            <LinkIcon size={12} />
                            {message.telegramLinkReason ?? "Ссылка Telegram недоступна"}
                          </span>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="raw">
          <Card>
            <CardContent className="p-4">
              <pre className="whitespace-pre-wrap font-mono-value text-xs leading-relaxed text-text-default">
                {guide.rawResponse || guide.content || "—"}
              </pre>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="trace">
          <Card>
            <CardContent className="p-4">
              <div className="mb-4 text-sm font-semibold text-text-strong">
                Трассировка обработки
              </div>
              {isMaterialRoute ? (
                guide.traceStages.length === 0 ? (
                  <div className="text-sm text-text-muted">Трассировка не найдена.</div>
                ) : (
                  <div className="flex flex-col gap-3">
                    {guide.traceStages.map((stage) => (
                      <div key={stage.id} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div>
                            <div className="text-sm font-semibold text-text-strong">{stage.stageName ?? stage.stage}</div>
                            <div className="mt-1 text-xs text-text-muted">
                              raw #{stage.rawId ?? "—"} · {stage.status} · {formatDuration(stage.durationMs)}
                            </div>
                          </div>
                          {stage.reason && <Badge variant="outline">{stage.reason}</Badge>}
                        </div>
                        {stage.errorMessage && <div className="mt-2 text-sm text-danger">{stage.errorMessage}</div>}
                      </div>
                    ))}
                  </div>
                )
              ) : traceQuery.isLoading ? (
                <div className="flex items-center gap-2 text-text-muted">
                  <SpinnerGap size={16} className="animate-spin" />
                  <span className="text-sm">Загрузка трассировки...</span>
                </div>
              ) : !traceQuery.data || traceQuery.data.length === 0 ? (
                <div className="text-sm text-text-muted">Трассировка не найдена.</div>
              ) : (
                <TraceTimeline steps={traceQuery.data} />
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {!isMaterialRoute && <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Удалить {isMaterialRoute ? "материал" : "гайд"}</DialogTitle>
            <DialogDescription>
              «{renderedTitle}» будет удалён. Это действие нельзя отменить.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={deleteGuideMutation.isPending}
            >
              Отмена
            </Button>
            <Button
              variant="destructive"
              onClick={deleteGuide}
              disabled={deleteGuideMutation.isPending}
            >
              {deleteGuideMutation.isPending ? (
                <SpinnerGap size={16} className="animate-spin" />
              ) : (
                <Trash size={16} />
              )}
              {deleteGuideMutation.isPending ? "Удаляю..." : "Удалить"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  );
}
