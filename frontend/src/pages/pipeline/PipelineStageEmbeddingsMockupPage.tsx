import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowClockwise,
  CaretDown,
  CaretLeft,
  CaretRight,
  FunnelSimple,
  MagnifyingGlass,
  TelegramLogo,
} from "@phosphor-icons/react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  type PipelineDetailResponse,
  type PipelineStageMessage,
  type PipelineStageSummary,
  usePipelineEmbeddingsQuery,
  usePipelineLlmJudgeQuery,
  usePipelineMaterialGenerationQuery,
  usePipelineStageDetailsQuery,
  usePipelineStageMessagesQuery,
} from "@/shared/api/pipelineApi";
import { usePipelineEvents } from "@/shared/api/pipelineEvents";
import { queryClient } from "@/shared/api/queryClient";

type StageStatusFilter = "all" | "processed" | "active" | "waiting" | "skipped" | "failed";
type CardTone = "default" | "success" | "warning" | "danger" | "muted";

interface StageRow {
  rawId: number;
  chat: string;
  preview: string;
  status: string;
  reason: string;
  model: string;
  score: string;
  time: string;
}

const STATUS_OPTIONS: Array<{ value: StageStatusFilter; label: string }> = [
  { value: "all", label: "Все статусы" },
  { value: "processed", label: "Пройдено" },
  { value: "active", label: "В обработке" },
  { value: "waiting", label: "Ожидают" },
  { value: "skipped", label: "Пропущено" },
  { value: "failed", label: "Неудачи" },
];

export function PipelineStageEmbeddingsMockupPage() {
  const params = useParams<{ stageId: string }>();
  const stageId = normalizeStageId(params.stageId);
  const connectionState = usePipelineEvents();
  const [statusFilter, setStatusFilter] = useState<StageStatusFilter>("all");
  const [chatFilter, setChatFilter] = useState("all");
  const [reasonFilter, setReasonFilter] = useState("all");
  const [modelFilter, setModelFilter] = useState("all");
  const [periodFilter, setPeriodFilter] = useState("today");
  const [search, setSearch] = useState("");

  const detailsQuery = usePipelineStageDetailsQuery(stageId);
  const messagesQuery = usePipelineStageMessagesQuery(stageId, statusFilter, 200);
  const embeddingsQuery = usePipelineEmbeddingsQuery(undefined, 100);
  const llmQuery = usePipelineLlmJudgeQuery(undefined, 100);
  const materialQuery = usePipelineMaterialGenerationQuery(undefined, 100);

  const stage = detailsQuery.data?.stage;
  const aux = auxiliaryForStage(stageId, embeddingsQuery.data, llmQuery.data, materialQuery.data);
  const modelByRawId = useMemo(() => buildModelByRawId(stageId, aux), [stageId, aux]);
  const rows = useMemo(
    () => (messagesQuery.data ?? []).map((message) => toStageRow(message, modelByRawId)),
    [messagesQuery.data, modelByRawId],
  );
  const filteredRows = useMemo(
    () => filterRows(rows, { chatFilter, reasonFilter, modelFilter, periodFilter, search }),
    [chatFilter, modelFilter, periodFilter, reasonFilter, rows, search],
  );

  const summaryCards = buildSummaryCards(stage);
  const chats = optionValues(rows.map((row) => row.chat));
  const reasons = optionValues(rows.map((row) => row.reason).filter((reason) => reason !== "—"));
  const models = optionValues(rows.map((row) => row.model).filter((model) => model !== "—"));
  const loading = detailsQuery.isLoading || messagesQuery.isLoading;
  const error = detailsQuery.isError || messagesQuery.isError;

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["pipeline-stage-details", stageId] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-stage-messages", stageId] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-embeddings"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-llm-judge"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-material-generation"] });
  };

  const resetFilters = () => {
    setStatusFilter("all");
    setChatFilter("all");
    setReasonFilter("all");
    setModelFilter("all");
    setPeriodFilter("today");
    setSearch("");
  };

  return (
    <div className="rounded-[30px] border border-border-subtle bg-bg-card p-4 shadow-[0_22px_70px_rgba(31,36,48,0.08)] sm:p-6 xl:p-7">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2 rounded-full text-text-muted hover:text-text-strong">
              <Link to="/pipeline">
                <CaretLeft size={16} />
                Назад к конвейеру
              </Link>
            </Button>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-[-0.03em] text-text-strong sm:text-[32px]">
                Этап: {stage?.name ?? stageTitle(stageId)}
              </h1>
              <Badge className="min-h-8 bg-brand-blue-soft px-4 text-brand-blue">
                {stageBadge(stage)}
              </Badge>
              <span className={cn("inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-bold", connectionTone(connectionState))}>
                <span className="size-2 rounded-full bg-current" />
                {connectionLabel(connectionState)}
              </span>
            </div>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-text-muted sm:text-[15px]">
              {stage?.description ?? "Реальные сообщения и диагностика выбранного этапа pipeline."}
            </p>
            {detailsQuery.data?.reason && (
              <p className="mt-2 max-w-3xl text-sm text-warning">{detailsQuery.data.explanation}</p>
            )}
          </div>
          <Button variant="primary" className="h-11 rounded-[14px] px-5 shadow-[0_12px_26px_rgba(185,133,23,0.20)]" onClick={refresh}>
            <ArrowClockwise size={17} />
            Обновить
          </Button>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {summaryCards.map((card) => (
            <SummaryCard key={card.label} {...card} />
          ))}
        </section>

        <section className="rounded-[24px] border border-border-subtle bg-[#fffaf0] p-4 shadow-[0_12px_36px_rgba(31,36,48,0.045)]">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-text-strong">
            <FunnelSimple size={17} className="text-brand-blue" />
            Реальные фильтры этапа
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            <SelectShell label="Период" value={periodFilter} onChange={setPeriodFilter} options={periodOptions()} />
            <SelectShell label="Чат" value={chatFilter} onChange={setChatFilter} options={[{ value: "all", label: chats.length ? "Все чаты" : "Чаты загрузятся из данных" }, ...chats]} />
            <SelectShell label="Статус" value={statusFilter} onChange={(value) => setStatusFilter(value as StageStatusFilter)} options={STATUS_OPTIONS} />
            <SelectShell label="Причина" value={reasonFilter} onChange={setReasonFilter} options={[{ value: "all", label: reasons.length ? "Все причины" : "Причин пока нет" }, ...reasons]} />
            <SelectShell label="Модель" value={modelFilter} onChange={setModelFilter} options={[{ value: "all", label: models.length ? "Все модели" : "Модель не указана" }, ...models]} />
          </div>
          <div className="mt-3 flex flex-col gap-3 lg:flex-row">
            <label className="relative flex-1">
              <span className="sr-only">Поиск</span>
              <MagnifyingGlass size={18} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-text-weak" />
              <input
                className="h-12 w-full rounded-[16px] border border-border-subtle bg-bg-elevated pl-11 pr-4 text-sm text-text-strong outline-none transition-colors placeholder:text-text-weak focus:border-brand-blue"
                placeholder="Поиск по тексту сообщения или raw_id..."
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </label>
            <Button variant="outline" className="h-12 rounded-[16px] px-5" onClick={resetFilters}>
              Сбросить фильтры
            </Button>
          </div>
        </section>

        <StageTabs stage={stage} statusFilter={statusFilter} onStatusChange={setStatusFilter} />

        <section className="overflow-hidden rounded-[26px] border border-border-subtle bg-bg-elevated shadow-[0_16px_44px_rgba(31,36,48,0.055)]">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1100px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border-subtle bg-[#fbf7ef] text-xs font-semibold uppercase tracking-[0.12em] text-text-weak">
                  <th className="px-5 py-3.5">raw_id</th>
                  <th className="px-5 py-3.5">chat</th>
                  <th className="px-5 py-3.5">preview</th>
                  <th className="px-5 py-3.5">статус на этапе</th>
                  <th className="px-5 py-3.5">причина</th>
                  <th className="px-5 py-3.5">модель</th>
                  <th className="px-5 py-3.5">score</th>
                  <th className="px-5 py-3.5">время</th>
                  <th className="w-12 px-4 py-3.5" aria-label="Открыть" />
                </tr>
              </thead>
              <tbody>
                {loading && <StateRow label="Загружаем реальные данные этапа..." />}
                {error && <StateRow label="Не удалось загрузить реальные данные этапа" danger />}
                {!loading && !error && filteredRows.length === 0 && <StateRow label="Нет сообщений под выбранные фильтры" />}
                {!loading && !error && filteredRows.map((row) => (
                  <tr key={`${row.rawId}-${row.status}-${row.time}`} className="group border-b border-border-subtle/80 transition-colors last:border-b-0 hover:bg-brand-blue-soft/25">
                    <td className="px-5 py-4 font-mono text-[13px] font-semibold text-text-strong">{row.rawId}</td>
                    <td className="px-5 py-4"><ChatCell chat={row.chat} /></td>
                    <td className="max-w-[340px] px-5 py-4 text-text-default"><span className="line-clamp-1">{row.preview}</span></td>
                    <td className="px-5 py-4"><StatusPill status={row.status} /></td>
                    <td className="px-5 py-4 font-mono text-xs font-semibold text-text-muted">{row.reason}</td>
                    <td className="px-5 py-4 font-mono text-xs font-semibold text-text-strong">{row.model}</td>
                    <td className="px-5 py-4"><span className="text-text-weak">{row.score}</span></td>
                    <td className="px-5 py-4 font-mono text-xs text-text-muted">{row.time}</td>
                    <td className="px-4 py-4 text-right">
                      <Link to={`/groups?message=${row.rawId}`} className="grid h-8 w-8 place-items-center rounded-full text-text-weak transition-colors group-hover:bg-bg-card group-hover:text-brand-blue" aria-label={`Открыть сообщение ${row.rawId}`}>
                        <CaretRight size={16} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-border-subtle px-5 py-4 text-sm text-text-muted lg:flex-row lg:items-center lg:justify-between">
            <div>Показано {filteredRows.length} из {rows.length} реальных строк этапа</div>
            <div className="text-xs">Backend status: {messagesQuery.fetchStatus === "fetching" ? "обновляется" : "актуально"}</div>
            <div className="text-xs">Limit: 200</div>
          </div>
        </section>

        <AuxiliaryPanel stageId={stageId} aux={aux} />
      </div>
    </div>
  );
}

function SummaryCard({ label, value, badge, tone }: { label: string; value: string; badge: string | null; tone: CardTone }) {
  return (
    <div className="rounded-[22px] border border-border-subtle bg-bg-elevated p-4 shadow-[0_10px_30px_rgba(31,36,48,0.045)]">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-text-weak">{label}</div>
      <div className="mt-3 flex items-end justify-between gap-3">
        <div className="text-[30px] font-semibold leading-none tracking-[-0.04em] text-text-strong">{value}</div>
        {badge && <span className={cn("rounded-full px-2.5 py-1 text-xs font-semibold", cardTone(tone))}>{badge}</span>}
      </div>
    </div>
  );
}

function SelectShell({ label, value, options, onChange }: { label: string; value: string; options: Array<{ value: string; label: string }>; onChange: (value: string) => void }) {
  return (
    <label className="relative block min-w-0 rounded-[18px] border border-border-subtle bg-bg-elevated px-4 py-3 shadow-[0_8px_20px_rgba(61,45,24,0.04)] transition focus-within:border-brand-blue/30 hover:border-brand-blue/25 hover:bg-[#fffcf6]">
      <span className="block truncate text-[10px] font-bold uppercase tracking-[0.16em] text-text-weak">{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 block h-7 w-full appearance-none truncate border-0 bg-transparent pr-7 text-[15px] font-semibold text-text-strong outline-none">
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
      <CaretDown size={16} className="pointer-events-none absolute bottom-4 right-4 text-text-muted" />
    </label>
  );
}

function ChatCell({ chat }: { chat: string }) {
  return (
    <div className="flex min-w-[136px] items-center gap-2.5">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-border-subtle bg-brand-blue-soft text-brand-blue">
        <TelegramLogo size={13} weight="fill" />
      </span>
      <span className="truncate font-medium text-text-strong">{chat}</span>
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  return <span className={cn("inline-flex rounded-full px-3 py-1 text-xs font-semibold", statusClass(status))}>{statusLabel(status)}</span>;
}

function StateRow({ label, danger }: { label: string; danger?: boolean }) {
  return <tr><td colSpan={9} className={cn("px-5 py-10 text-center text-sm", danger ? "text-danger" : "text-text-muted")}>{label}</td></tr>;
}

function StageTabs({ stage, statusFilter, onStatusChange }: { stage?: PipelineStageSummary; statusFilter: StageStatusFilter; onStatusChange: (status: StageStatusFilter) => void }) {
  const items: Array<{ value: StageStatusFilter; label: string; count: number }> = [
    { value: "all", label: "Все", count: totalStageCount(stage) },
    { value: "processed", label: "Пройдено", count: stage?.processed ?? 0 },
    { value: "active", label: "В обработке", count: stage?.active ?? 0 },
    { value: "waiting", label: "Ожидают", count: stage?.waiting ?? 0 },
    { value: "skipped", label: "Пропущено", count: stage?.skipped ?? 0 },
    { value: "failed", label: "Неудачи", count: stage?.failed ?? 0 },
  ];
  return <div className="flex flex-wrap gap-2">{items.map((item) => <button key={item.value} onClick={() => onStatusChange(item.value)} className={cn("rounded-full px-4 py-2 text-sm font-semibold transition-colors", statusFilter === item.value ? "bg-brand-blue-soft text-brand-blue shadow-[inset_0_0_0_1px_rgba(41,95,190,0.12)]" : "border border-border-subtle bg-bg-elevated text-text-muted hover:text-text-strong")}>{item.label} {formatNumber(item.count)}</button>)}</div>;
}

function AuxiliaryPanel({ stageId, aux }: { stageId: string; aux: PipelineDetailResponse | undefined }) {
  const items = Array.isArray(aux?.items) ? aux.items.slice(0, 5) : [];
  if (!items.length) return null;
  return (
    <section className="rounded-[24px] border border-border-subtle bg-bg-elevated p-4 shadow-[0_12px_36px_rgba(31,36,48,0.045)]">
      <div className="text-sm font-semibold text-text-strong">Дополнительные live-данные: {stageTitle(stageId)}</div>
      <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        {items.map((item, index) => <div key={String(item.id ?? index)} className="rounded-2xl border border-border-subtle bg-bg-card p-3 text-xs text-text-muted"><div className="font-semibold text-text-strong">{String(item.title ?? item.model ?? item.stage ?? item.kind ?? `item ${index + 1}`)}</div><div className="mt-1 line-clamp-2">{String(item.textPreview ?? item.summary ?? item.responsePreview ?? item.errorMessage ?? item.reason ?? "нет preview")}</div></div>)}
      </div>
    </section>
  );
}

function normalizeStageId(value: string | undefined) {
  if (!value || value === "embeddings-bge-m3") return "embeddings";
  return {
    raw_messages: "telegram_ingest",
    intake: "normalization",
    queue: "rule_signals",
    run: "bootstrap_classification",
  }[value] ?? value;
}

function auxiliaryForStage(stageId: string, embeddings?: PipelineDetailResponse, llm?: PipelineDetailResponse, material?: PipelineDetailResponse) {
  if (stageId === "embeddings") return embeddings;
  if (stageId === "llm_judge") return llm;
  if (stageId === "material_generation" || stageId === "materials_publish") return material;
  return undefined;
}

function buildModelByRawId(stageId: string, aux: PipelineDetailResponse | undefined) {
  const result = new Map<number, string>();
  if (stageId !== "embeddings" || !Array.isArray(aux?.items)) return result;
  aux.items.forEach((item) => {
    const rawId = numeric(item.rawMessageId);
    const model = typeof item.model === "string" ? item.model : null;
    if (rawId != null && model) result.set(rawId, model);
  });
  return result;
}

function toStageRow(message: PipelineStageMessage, modelByRawId: Map<number, string>): StageRow {
  const reason = message.blockedReason || message.errorCode || "—";
  return {
    rawId: message.id,
    chat: message.chatTitle || "Неизвестный чат",
    preview: truncate(message.text || "Сообщение без текста", 80),
    status: message.status || "UNKNOWN",
    reason,
    model: modelByRawId.get(message.id) ?? "—",
    score: "—",
    time: formatDateTime(message.updatedAt || message.createdAt),
  };
}

function filterRows(rows: StageRow[], filters: { chatFilter: string; reasonFilter: string; modelFilter: string; periodFilter: string; search: string }) {
  const query = filters.search.trim().toLowerCase();
  const since = periodStart(filters.periodFilter);
  return rows.filter((row) => {
    if (filters.chatFilter !== "all" && row.chat !== filters.chatFilter) return false;
    if (filters.reasonFilter !== "all" && row.reason !== filters.reasonFilter) return false;
    if (filters.modelFilter !== "all" && row.model !== filters.modelFilter) return false;
    if (since && !rowWithinPeriod(row.time, since)) return false;
    if (!query) return true;
    return String(row.rawId).includes(query) || row.preview.toLowerCase().includes(query) || row.chat.toLowerCase().includes(query) || row.reason.toLowerCase().includes(query);
  });
}

function buildSummaryCards(stage?: PipelineStageSummary) {
  const total = totalStageCount(stage);
  return [
    { label: "Всего на этапе", value: formatNumber(total), badge: null, tone: "default" as const },
    { label: "Пройдено", value: formatNumber(stage?.processed ?? 0), badge: percentBadge(stage?.processed ?? 0, total), tone: "success" as const },
    { label: "Пропущено", value: formatNumber(stage?.skipped ?? 0), badge: percentBadge(stage?.skipped ?? 0, total), tone: "warning" as const },
    { label: "Неудачи", value: formatNumber(stage?.failed ?? 0), badge: percentBadge(stage?.failed ?? 0, total), tone: "danger" as const },
    { label: "Ожидает", value: formatNumber((stage?.waiting ?? 0) + (stage?.active ?? 0)), badge: percentBadge((stage?.waiting ?? 0) + (stage?.active ?? 0), total), tone: "muted" as const },
  ];
}

function totalStageCount(stage?: PipelineStageSummary) {
  return (stage?.processed ?? 0) + (stage?.skipped ?? 0) + (stage?.failed ?? 0) + (stage?.waiting ?? 0) + (stage?.active ?? 0);
}

function stageBadge(stage?: PipelineStageSummary) {
  if (!stage) return "загружается";
  return `${formatNumber(stage.processed)} прошло из ${formatNumber(totalStageCount(stage))}`;
}

function optionValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b, "ru")).map((value) => ({ value, label: value }));
}

function periodOptions() {
  return [
    { value: "all", label: "Всё время" },
    { value: "today", label: "Сегодня" },
    { value: "hour", label: "Последний час" },
    { value: "day", label: "24 часа" },
  ];
}

function periodStart(period: string) {
  const now = new Date();
  if (period === "hour") return new Date(now.getTime() - 60 * 60 * 1000);
  if (period === "day") return new Date(now.getTime() - 24 * 60 * 60 * 1000);
  if (period === "today") return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return null;
}

function rowWithinPeriod(value: string, since: Date) {
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) ? parsed >= since : true;
}

function statusLabel(status: string) {
  if (status === "PROCESSED" || status === "PROCESSED_DEGRADED") return "пройдено";
  if (status === "SKIPPED") return "пропущено";
  if (status === "FAILED") return "неудача";
  if (status === "PENDING" || status === "WAITING_FOR_WORKER") return "ожидает";
  if (status === "PROCESSING") return "в обработке";
  return status.toLowerCase();
}

function statusClass(status: string) {
  if (status === "PROCESSED" || status === "PROCESSED_DEGRADED") return "bg-success-soft text-success";
  if (status === "SKIPPED") return "bg-warning-soft text-warning";
  if (status === "FAILED") return "bg-danger-soft text-danger";
  if (status === "PENDING" || status === "WAITING_FOR_WORKER") return "bg-bg-card text-text-muted";
  return "bg-brand-blue-soft text-brand-blue";
}

function cardTone(tone: CardTone) {
  if (tone === "success") return "bg-success-soft text-success";
  if (tone === "warning") return "bg-warning-soft text-warning";
  if (tone === "danger") return "bg-danger-soft text-danger";
  if (tone === "muted") return "bg-brand-blue-soft text-text-muted";
  return "bg-bg-card text-text-muted";
}

function connectionLabel(state: "connecting" | "connected" | "disconnected") {
  if (state === "connected") return "real-time on";
  if (state === "connecting") return "подключаем live";
  return "polling fallback";
}

function connectionTone(state: "connecting" | "connected" | "disconnected") {
  if (state === "connected") return "bg-success-soft text-success";
  if (state === "connecting") return "bg-warning-soft text-warning";
  return "bg-bg-card text-text-muted";
}

function stageTitle(stageId: string) {
  return stageId.replace(/_/g, " ");
}

function percentBadge(value: number, total: number) {
  if (total <= 0) return "0%";
  return `${((value / total) * 100).toFixed(1)}%`;
}

function truncate(value: string, length: number) {
  return value.length > length ? `${value.slice(0, length - 1)}…` : value;
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("ru-RU").format(value);
}

function formatDateTime(value: string | null) {
  if (!value) return "нет данных";
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  return date.toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function numeric(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
