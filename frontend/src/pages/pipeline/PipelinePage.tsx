import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ArrowClockwise,
  ArrowRight,
  CaretDown,
  ChartLineUp,
  FolderOpen,
  FunnelSimple,
  Info,
  TelegramLogo,
  WarningCircle,
} from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  type PipelineResultItem,
  type PipelineStageSummary,
  usePipelineLiveStagesQuery,
  usePipelineLiveSummaryQuery,
  usePipelineResultsQuery,
} from "@/shared/api/pipelineApi";
import { queryClient } from "@/shared/api/queryClient";

type Tone = "blue" | "cyan" | "green" | "yellow" | "orange" | "purple" | "teal" | "red" | "gray";
type StatTone = "passed" | "accepted" | "rejected" | "pending" | "failed" | "skipped" | "candidate";

interface StageStat {
  label: string;
  value: number;
  tone: StatTone;
}

interface ConveyorStage {
  id: string;
  n: number;
  title: string;
  input: number;
  value: number;
  pct: number;
  tone: Tone;
  selected: boolean;
  stats: StageStat[];
}

interface TableRow {
  rawId: number;
  chat: string;
  preview: string;
  accepted: boolean;
  reason: string;
  score: number | null;
  materialId: number | null;
}

const FILTERS = [
  ["Период", "Сегодня 00:00 - сейчас"],
  ["Чат", "Все чаты"],
  ["Этап", "Все этапы"],
  ["Статус", "Все статусы"],
  ["Причина отклонения", "Все причины"],
  ["Провайдер / Модель", "Все провайдеры"],
] as const;

export function PipelinePage() {
  const navigate = useNavigate();
  const [selectedStageId, setSelectedStageId] = useState("raw_messages");
  const summaryQuery = usePipelineLiveSummaryQuery();
  const stagesQuery = usePipelineLiveStagesQuery();
  const todayFrom = useMemo(() => startOfTodayIso(), []);
  const resultsQuery = usePipelineResultsQuery(undefined, 0, 200, todayFrom, true);

  const conveyorStages = useMemo(
    () => buildConveyorStages(stagesQuery.data ?? [], summaryQuery.data, selectedStageId),
    [selectedStageId, stagesQuery.data, summaryQuery.data],
  );
  const selectedStage = conveyorStages.find((stage) => stage.id === selectedStageId) ?? conveyorStages[0] ?? null;
  const tableRows = useMemo(
    () => buildTableRows(resultsQuery.data?.content ?? [], selectedStageId),
    [resultsQuery.data?.content, selectedStageId],
  );
  const reasonCounts = useMemo(() => topReasons(tableRows), [tableRows]);
  const summaryCards = useMemo(() => buildSummaryCards(summaryQuery.data), [summaryQuery.data]);
  const losses = useMemo(() => buildLosses(conveyorStages), [conveyorStages]);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-summary"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-stages"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-results"] });
  };

  const openStageDetail = (stageId: string) => {
    navigate(stageHref(stageId));
  };

  return (
    <div className="relative overflow-hidden rounded-[30px] border border-border-subtle/90 bg-[linear-gradient(180deg,#fffefb_0%,#fffaf2_100%)] p-5 shadow-[0_20px_60px_rgba(69,55,29,0.10)] lg:p-7">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_12%_0%,rgba(255,255,255,0.92),transparent_25%),radial-gradient(circle_at_55%_4%,rgba(240,222,181,0.18),transparent_26%),radial-gradient(circle_at_88%_0%,rgba(255,255,255,0.86),transparent_18%),radial-gradient(circle_at_50%_100%,rgba(243,231,205,0.18),transparent_40%)]" />

      <div className="relative">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-[30px] font-extrabold tracking-[-0.04em] text-text-strong lg:text-[34px]">
              Конвейер сообщений
            </h1>
            <p className="mt-1.5 text-[15px] leading-6 text-text-muted">
              Визуальная воронка прохождения сообщений по этапам
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="primary"
              className="h-10 gap-2 rounded-2xl px-4 shadow-[0_14px_30px_rgba(223,180,69,0.28)]"
              onClick={refresh}
            >
              <ArrowClockwise size={18} weight="bold" />
              Обновить
            </Button>
            <Button variant="outline" size="icon" className="h-10 w-10 rounded-2xl" aria-label="Обновить" onClick={refresh}>
              <ArrowClockwise size={17} />
            </Button>
          </div>
        </header>

        <section className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-6" aria-label="Фильтры конвейера">
          {FILTERS.map(([label, value]) => (
            <FilterCard key={label} label={label} value={value} />
          ))}
        </section>

        <section className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4" aria-label="Сводные метрики">
          {summaryCards.map((item) => (
            <SummaryCard key={item.label} {...item} />
          ))}
        </section>

        <section className="mt-4 rounded-[28px] border border-border-subtle/90 bg-[linear-gradient(180deg,rgba(255,253,248,0.98),rgba(255,249,240,0.98))] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.75),0_14px_40px_rgba(76,55,24,0.06)] lg:p-5">
          <div className="mb-4 flex items-center gap-2 text-[15px] font-semibold text-text-muted">
            <Info size={16} className="text-text-weak" />
            Нажмите на строку, чтобы выбрать этап. Стрелка справа откроет подробности.
          </div>
          <FunnelDiagram
            stages={conveyorStages}
            losses={losses}
            onSelect={setSelectedStageId}
            onOpen={openStageDetail}
          />
        </section>

        <StageMessagesTable
          stage={selectedStage}
          rows={tableRows}
          reasonCounts={reasonCounts}
          loading={resultsQuery.isLoading}
          error={resultsQuery.isError}
        />
      </div>
    </div>
  );
}

function FilterCard({ label, value }: { label: string; value: string }) {
  return (
    <button
      type="button"
      className="group flex h-[60px] min-w-0 items-center justify-between gap-3 rounded-[18px] border border-border-subtle bg-white/95 px-4 text-left shadow-[0_8px_20px_rgba(61,45,24,0.04)] transition hover:border-brand-blue/25 hover:bg-[#fffcf6]"
    >
      <div className="min-w-0">
        <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-text-weak">{label}</div>
        <div className="mt-1 truncate text-[15px] font-semibold text-text-strong">{value}</div>
      </div>
      <span className="shrink-0 text-text-muted transition group-hover:text-brand-blue">
        <CaretDown size={16} />
      </span>
    </button>
  );
}

function SummaryCard({ label, value, icon: Icon, tone }: ReturnType<typeof buildSummaryCards>[number]) {
  return (
    <article className="rounded-[20px] border border-border-subtle bg-white/95 px-4 py-3.5 shadow-[0_12px_28px_rgba(61,45,24,0.055)]">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-[14px] font-medium text-text-muted">{label}</p>
          <p className="mt-1 font-mono-value text-[23px] font-extrabold tracking-[-0.03em] text-text-strong">{value}</p>
        </div>
        <span className={cn("grid size-12 shrink-0 place-items-center rounded-2xl", iconTone(tone))}>
          <Icon size={24} weight="duotone" />
        </span>
      </div>
    </article>
  );
}

function FunnelDiagram({
  stages,
  losses,
  onSelect,
  onOpen,
}: {
  stages: ConveyorStage[];
  losses: Array<number | null>;
  onSelect: (stageId: string) => void;
  onOpen: (stageId: string) => void;
}) {
  const maxValue = Math.max(...stages.map((stage) => stage.value), 1);

  return (
    <div className="overflow-hidden rounded-[22px] border border-border-subtle/80 bg-white/80 shadow-[0_16px_40px_rgba(74,55,25,0.06)]">
      <div className="hidden grid-cols-[minmax(210px,1.1fr)_minmax(260px,2.2fr)_120px_110px_145px_24px] items-center gap-4 border-b border-border-subtle bg-[#f7f0e4] px-5 py-3 text-[10px] font-bold uppercase tracking-[0.14em] text-text-weak lg:grid">
        <span>Этап</span>
        <span>Доля от общего потока</span>
        <span className="text-right">Прошло</span>
        <span className="text-right">Конверсия</span>
        <span className="text-right">Потеря дальше</span>
        <span />
      </div>

      <div className="divide-y divide-border-subtle/75">
        {stages.map((stage, index) => {
          const globalPercent = (stage.value / maxValue) * 100;
          const loss = losses[index] ?? null;

          return (
            <div
              key={stage.id}
              className={cn(
                "group grid w-full grid-cols-1 gap-2 px-4 py-3 transition duration-200 hover:bg-[#fff9ed] lg:grid-cols-[minmax(210px,1.1fr)_minmax(260px,2.2fr)_120px_110px_145px_44px] lg:items-stretch lg:gap-4 lg:px-5",
                stage.selected && "bg-warning-soft/65 shadow-[inset_4px_0_0_var(--ui-brand-yellow)]",
              )}
            >
              <button
                type="button"
                onClick={() => onSelect(stage.id)}
                aria-pressed={stage.selected}
                className="grid min-w-0 grid-cols-1 gap-3 rounded-xl text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-yellow lg:col-span-5 lg:grid-cols-subgrid lg:items-center lg:gap-4"
              >
              <div className="flex min-w-0 items-center gap-3 lg:col-start-1">
                <span className={cn("grid size-9 shrink-0 place-items-center rounded-[13px] text-xs font-extrabold", iconTone(stage.tone))}>
                  {stage.n}
                </span>
                <div className="min-w-0">
                  <h3 className="truncate text-sm font-extrabold text-text-strong">{stage.title}</h3>
                  <p className="mt-1 text-[11px] text-text-muted">Вход: {formatCompactNumber(stage.input)}</p>
                </div>
              </div>

              <div className="min-w-0 lg:col-start-2">
                <div className="h-3 overflow-hidden rounded-full bg-[#ede7dc] shadow-[inset_0_1px_2px_rgba(75,56,29,0.10)]">
                  <div
                    className={cn("h-full rounded-full transition-[width] duration-500", flowBarTone(stage.tone))}
                    style={{ width: `${Math.max(globalPercent, stage.value > 0 ? 1.5 : 0)}%` }}
                  />
                </div>
                <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
                  {stage.stats.slice(0, 3).map((stat) => (
                    <span key={`${stage.id}-${stat.label}`} className="inline-flex items-center gap-1.5 text-[11px] text-text-muted">
                      <span className={cn("size-1.5 rounded-[2px]", statDotTone(stat.tone))} />
                      {stat.label} <strong className="font-semibold text-text-default">{formatCompactNumber(stat.value)}</strong>
                    </span>
                  ))}
                </div>
              </div>

              <div className="flex items-baseline justify-between lg:col-start-3 lg:block lg:text-right">
                <span className="text-[11px] font-semibold uppercase tracking-[0.1em] text-text-weak lg:hidden">Прошло</span>
                <span className="font-mono-value text-xl font-extrabold text-text-strong">{formatCompactNumber(stage.value)}</span>
              </div>
              <div className="flex items-center justify-between lg:col-start-4 lg:justify-end">
                <span className="text-[11px] font-semibold uppercase tracking-[0.1em] text-text-weak lg:hidden">Конверсия</span>
                <span className="rounded-lg bg-success-soft px-2.5 py-1 text-xs font-bold text-success">{formatPercent(stage.pct)}</span>
              </div>
              <div className="flex items-center justify-between lg:col-start-5 lg:justify-end">
                <span className="text-[11px] font-semibold uppercase tracking-[0.1em] text-text-weak lg:hidden">Потеря дальше</span>
                {loss != null ? (
                  <span className="rounded-lg bg-danger-soft px-2.5 py-1 text-xs font-bold text-danger">-{formatCompactNumber(loss)}</span>
                ) : (
                  <span className="text-xs font-semibold text-text-weak">—</span>
                )}
              </div>
              </button>
              <button
                type="button"
                onClick={() => onOpen(stage.id)}
                aria-label={`Открыть подробности этапа ${stage.title}`}
                title="Открыть подробности"
                className="flex h-10 items-center justify-center gap-2 self-center rounded-xl border border-border-subtle bg-white/70 px-3 text-xs font-semibold text-text-muted transition hover:border-brand-yellow hover:text-warning focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-yellow lg:col-start-6 lg:w-10 lg:px-0"
              >
                <span className="lg:hidden">Подробнее</span>
                <ArrowRight size={18} className="transition-transform group-hover:translate-x-0.5" />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function StageMessagesTable({
  stage,
  rows,
  reasonCounts,
  loading,
  error,
}: {
  stage: ConveyorStage | null;
  rows: TableRow[];
  reasonCounts: Array<[string, number]>;
  loading: boolean;
  error: boolean;
}) {
  const acceptedCount = rows.filter((row) => row.accepted).length;
  const rejectedCount = rows.length - acceptedCount;
  const conversion = stage?.input ? (acceptedCount / stage.input) * 100 : 0;

  return (
    <section className="mt-4 rounded-[28px] border border-border-subtle bg-white/94 p-4 shadow-[0_14px_36px_rgba(61,45,24,0.075)] lg:p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-[18px] font-extrabold tracking-[-0.03em] text-text-strong">
            Сообщения этапа: {stage?.title ?? "—"}
          </h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <InfoChip label="Вход" value={formatCompactNumber(stage?.input ?? 0)} />
            <InfoChip label="Accepted" value={formatCompactNumber(acceptedCount)} tone="green" />
            <InfoChip label="Rejected" value={formatCompactNumber(rejectedCount)} tone="red" />
            <InfoChip label="Конверсия" value={formatPercent(conversion)} tone="blue" />
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2">
          <span className="text-[11px] font-bold uppercase tracking-[0.12em] text-text-weak">
            Топ причин отклонений:
          </span>
          {reasonCounts.length ? (
            reasonCounts.map(([reason]) => <ReasonChip key={reason}>{reason}</ReasonChip>)
          ) : (
            <ReasonChip>Нет явных причин</ReasonChip>
          )}
          <Button variant="outline" size="sm" className="h-10 rounded-2xl px-4">
            Открыть в таблице
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="mt-4 rounded-[18px] border border-border-subtle bg-[#fffcf7] p-4 text-sm text-text-muted">
          Загружаю реальные сообщения pipeline...
        </div>
      ) : error ? (
        <div className="mt-4 rounded-[18px] border border-border-subtle bg-[#fffcf7] p-4 text-sm text-text-muted">
          Не удалось подгрузить таблицу сообщений для выбранного этапа. Верхняя воронка при этом продолжает показывать доступные live-данные.
        </div>
      ) : (
        <div className="mt-4 overflow-hidden rounded-[18px] border border-border-subtle bg-white">
          <table className="w-full table-fixed text-left text-sm">
            <thead className="bg-[#f7f0e3] text-[11px] uppercase tracking-[0.12em] text-text-weak">
              <tr>
                {["raw_id", "чат", "preview", "status", "reason", "score", "material"].map((header) => (
                  <th key={header} className="px-4 py-3 font-bold">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.slice(0, 10).map((row) => (
                <tr
                  key={row.rawId}
                  className="border-t border-border-subtle bg-white transition hover:bg-[#f9f5ee]"
                >
                  <td className="px-4 py-3 font-mono-value font-bold text-text-strong">{row.rawId}</td>
                  <td className="px-4 py-3">
                    <span className="inline-flex items-center gap-2 font-semibold text-text-strong">
                      <span className="grid size-5 place-items-center rounded-full bg-brand-blue text-white">
                        <TelegramLogo size={11} weight="fill" />
                      </span>
                      {row.chat}
                    </span>
                  </td>
                  <td className="truncate px-4 py-3 text-text-muted">{row.preview}</td>
                  <td className="px-4 py-3">
                    <StatusBadge accepted={row.accepted} />
                  </td>
                  <td className="px-4 py-3">
                    {row.reason === "—" ? <span className="text-text-weak">—</span> : <ReasonChip>{row.reason}</ReasonChip>}
                  </td>
                  <td className="px-4 py-3 font-mono-value font-bold text-text-strong">
                    {row.score == null ? "—" : row.score.toFixed(2)}
                  </td>
                  <td className="truncate px-4 py-3">
                    {row.materialId ? (
                      <a className="font-semibold text-brand-blue hover:text-brand-blue-hover" href={`/materials/${row.materialId}`}>
                        MAT-{row.materialId}
                      </a>
                    ) : (
                      <span className="text-text-weak">—</span>
                    )}
                  </td>
                </tr>
              ))}
              {!rows.length ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-text-muted">
                    Для выбранного этапа пока нет сообщений в сегодняшнем окне.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm text-text-muted">
        <span>
          Показано 1-{Math.min(rows.length, 10)} из {rows.length} сообщений
        </span>
        <div className="flex items-center gap-2">
          <Page active>1</Page>
          <Page>2</Page>
          <Page>3</Page>
          <span className="px-1">...</span>
          <Page>10</Page>
          <span className="ml-2 rounded-xl border border-border-subtle bg-[#fffcf7] px-3 py-2 text-sm">
            Строк на странице: <strong className="text-text-strong">10</strong>
          </span>
        </div>
      </div>
    </section>
  );
}

function InfoChip({ label, value, tone = "gray" }: { label: string; value: string; tone?: "gray" | "green" | "red" | "blue" }) {
  return (
    <span
      className={cn(
        "rounded-full border px-3 py-1.5 text-[12px] font-bold",
        tone === "green"
          ? "border-success/20 bg-success-soft text-success"
          : tone === "red"
            ? "border-danger/20 bg-danger-soft text-danger"
            : tone === "blue"
              ? "border-brand-blue/20 bg-brand-blue-soft text-brand-blue"
              : "border-border-subtle bg-[#f8f4eb] text-text-muted",
      )}
    >
      {label}: <strong>{value}</strong>
    </span>
  );
}

function ReasonChip({ children }: { children: string }) {
  return (
    <span className="rounded-full border border-danger/20 bg-danger-soft px-3 py-1 text-[11px] font-bold text-danger">
      {children}
    </span>
  );
}

function StatusBadge({ accepted }: { accepted: boolean }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full px-3 py-1 text-[12px] font-bold",
        accepted ? "bg-success-soft text-success" : "bg-danger-soft text-danger",
      )}
    >
      <span className={cn("size-1.5 rounded-full", accepted ? "bg-success" : "bg-danger")} />
      {accepted ? "accepted" : "rejected"}
    </span>
  );
}

function Page({ children, active }: { children: string; active?: boolean }) {
  return (
    <button
      type="button"
      className={cn(
        "grid size-9 place-items-center rounded-xl border text-sm font-bold",
        active
          ? "border-brand-blue bg-brand-blue-soft text-brand-blue"
          : "border-border-subtle bg-[#fffcf7] text-text-muted hover:text-text-strong",
      )}
    >
      {children}
    </button>
  );
}

function buildSummaryCards(summary?: ReturnType<typeof usePipelineLiveSummaryQuery>["data"]) {
  const totalMessages = summary?.rawMessages ?? 0;
  const materials = summary?.generatedMaterials ?? 0;
  const rejected = (summary?.skipped ?? 0) + (summary?.failed ?? 0);
  const conversion = totalMessages > 0 ? (materials / totalMessages) * 100 : 0;

  return [
    { label: "Всего сообщений", value: formatCompactNumber(totalMessages), icon: TelegramLogo, tone: "blue" as const },
    { label: "До материалов", value: formatCompactNumber(materials), icon: FolderOpen, tone: "green" as const },
    { label: "Conversion", value: formatPercent(conversion), icon: ChartLineUp, tone: "purple" as const },
    { label: "Rejected", value: formatCompactNumber(rejected), icon: WarningCircle, tone: "red" as const },
  ];
}

function buildConveyorStages(
  stages: PipelineStageSummary[],
  summary: ReturnType<typeof usePipelineLiveSummaryQuery>["data"] | undefined,
  selectedStageId: string,
): ConveyorStage[] {
  const find = (ids: string[], ordinal?: number) =>
    stages.find((stage) => ids.includes(stage.id)) ?? stages.find((stage) => stage.ordinal === ordinal);

  const mapped: Array<{
    id: string;
    n: number;
    title: string;
    tone: Tone;
    source?: PipelineStageSummary;
    fallback?: { input: number; value: number; stats: StageStat[] };
  }> = [
    {
      id: "raw_messages",
      n: 1,
      title: "Raw messages",
      tone: "blue",
      fallback: {
        input: summary?.rawMessages ?? 0,
        value: summary?.rawMessages ?? 0,
        stats: [{ label: "Passed", value: summary?.rawMessages ?? 0, tone: "passed" }],
      },
    },
    { id: "intake", n: 2, title: "Intake", tone: "cyan", source: find(["telegram_ingest", "db_cache", "normalization"], 1) },
    { id: "queue", n: 3, title: "Queue", tone: "green", source: find(["cleanup", "dedupe", "rule_signals"], 6) },
    { id: "run", n: 4, title: "Run", tone: "yellow", source: find(["bootstrap_classification"], 7) },
    { id: "embeddings", n: 5, title: "Embeddings / BGE-M3", tone: "orange", source: find(["embeddings"], 8) },
    { id: "single_message_detection", n: 6, title: "Single-message", tone: "orange", source: find(["single_message_detection"], 10) },
    { id: "clustering", n: 7, title: "Clustering", tone: "purple", source: find(["clustering"], 9) },
    { id: "llm_judge", n: 8, title: "LLM Judge", tone: "blue", source: find(["llm_judge"], 11) },
    { id: "material_generation", n: 9, title: "Material generation", tone: "teal", source: find(["material_generation"], 12) },
    { id: "materials_publish", n: 10, title: "Material created", tone: "green", source: find(["materials_publish"], 13) },
  ];

  return mapped.map((item) => {
    if (item.fallback) {
      return {
        id: item.id,
        n: item.n,
        title: item.title,
        input: item.fallback.input,
        value: item.fallback.value,
        pct: item.fallback.input > 0 ? (item.fallback.value / item.fallback.input) * 100 : 0,
        tone: item.tone,
        selected: item.id === selectedStageId,
        stats: item.fallback.stats,
      };
    }

    const stage = item.source;
    const input = stage ? stage.active + stage.processed + stage.failed + stage.skipped + stage.waiting : 0;
    const value = stage?.processed ?? 0;
    return {
      id: item.id,
      n: item.n,
      title: item.title,
      input,
      value,
      pct: input > 0 ? (value / input) * 100 : 0,
      tone: item.tone,
      selected: item.id === selectedStageId,
      stats: buildStageStats(item.id, stage),
    };
  });
}

function buildStageStats(stageId: string, stage?: PipelineStageSummary): StageStat[] {
  const processed = stage?.processed ?? 0;
  const skipped = stage?.skipped ?? 0;
  const failed = stage?.failed ?? 0;
  const waiting = stage?.waiting ?? 0;

  if (stageId === "intake") {
    return compactStats([
      { label: "Passed", value: processed, tone: "passed" },
      { label: "Pending", value: waiting, tone: "pending" },
    ]);
  }
  if (stageId === "queue") {
    return compactStats([
      { label: "Passed", value: processed, tone: "passed" },
      { label: "Rejected", value: skipped, tone: "rejected" },
      { label: "Failed", value: failed, tone: "failed" },
      { label: "Pending", value: waiting, tone: "pending" },
    ]);
  }
  if (stageId === "run") {
    return compactStats([
      { label: "Passed", value: processed, tone: "passed" },
      { label: "Rejected", value: skipped, tone: "rejected" },
      { label: "Failed", value: failed, tone: "failed" },
    ]);
  }
  if (stageId === "embeddings") {
    return compactStats([
      { label: "Passed", value: processed, tone: "passed" },
      { label: "Skipped", value: skipped, tone: "skipped" },
    ]);
  }
  if (stageId === "single_message_detection") {
    return compactStats([
      { label: "Candidates", value: processed, tone: "candidate" },
      { label: "Rejected", value: skipped + failed, tone: "rejected" },
    ]);
  }
  if (stageId === "clustering") {
    return compactStats([
      { label: "Passed", value: processed, tone: "passed" },
      { label: "Rejected", value: skipped + failed, tone: "rejected" },
      { label: "Candidates", value: processed, tone: "candidate" },
    ]);
  }
  if (stageId === "llm_judge") {
    return compactStats([
      { label: "Accepted", value: processed, tone: "accepted" },
      { label: "Rejected", value: skipped + failed, tone: "rejected" },
    ]);
  }
  if (stageId === "material_generation" || stageId === "materials_publish") {
    return compactStats([
      { label: "Accepted", value: processed, tone: "accepted" },
      { label: "Rejected", value: skipped + failed, tone: "rejected" },
    ]);
  }
  return compactStats([
    { label: "Passed", value: processed, tone: "passed" },
    { label: "Skipped", value: skipped, tone: "skipped" },
    { label: "Failed", value: failed, tone: "failed" },
  ]);
}

function compactStats(stats: StageStat[]) {
  return stats.filter((stat) => stat.value > 0);
}

function buildLosses(stages: ConveyorStage[]) {
  const losses: Array<number | null> = [];
  for (let i = 0; i < stages.length - 1; i += 1) {
    const current = stages[i];
    const next = stages[i + 1];
    const loss = Math.max(current.value - next.value, 0);
    losses.push(loss > 0 ? loss : null);
  }
  return losses.slice(0, 9);
}

function buildTableRows(messages: PipelineResultItem[], selectedStageId: string): TableRow[] {
  return messages
    .filter((message) => matchesStage(message, selectedStageId))
    .slice(0, 50)
    .map((message) => ({
      rawId: message.id,
      chat: message.author ?? "Неизвестный чат",
      preview: truncate(message.text || "Сообщение без текста", 42),
      accepted: isAccepted(message, selectedStageId),
      reason: normalizeReason(message.classifierReason),
      score: message.classifierScore ?? message.signalScore ?? null,
      materialId: message.guideId,
    }));
}

function matchesStage(message: PipelineResultItem, selectedStageId: string) {
  if (selectedStageId === "raw_messages") return true;
  if (selectedStageId === "embeddings") return message.embeddingStatus != null && message.embeddingStatus !== "NONE";
  if (selectedStageId === "single_message_detection") return message.clusterCandidate != null;
  if (selectedStageId === "clustering") return message.clusterCandidate === true;
  if (selectedStageId === "llm_judge") return ["GUIDE_FOUND", "CLASSIFIED", "SKIPPED", "ERROR"].includes(message.status);
  if (selectedStageId === "material_generation" || selectedStageId === "materials_publish") {
    return message.guideId != null || message.status === "GUIDE_FOUND";
  }
  return true;
}

function isAccepted(message: PipelineResultItem, selectedStageId: string) {
  if (selectedStageId === "material_generation" || selectedStageId === "materials_publish") {
    return message.guideId != null || message.status === "GUIDE_FOUND";
  }
  if (selectedStageId === "llm_judge") {
    return message.guideId != null || message.status === "GUIDE_FOUND";
  }
  return !["SKIPPED", "ERROR"].includes(message.status);
}

function topReasons(rows: TableRow[]) {
  const counts = new Map<string, number>();
  rows
    .filter((row) => !row.accepted && row.reason !== "—")
    .forEach((row) => counts.set(row.reason, (counts.get(row.reason) ?? 0) + 1));
  return Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, 2);
}

function normalizeReason(reason: string | null) {
  if (!reason) return "—";
  return reason.replace(/\s+/g, "_").toUpperCase();
}

function startOfTodayIso() {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return date.toISOString();
}

function truncate(value: string, length: number) {
  return value.length > length ? `${value.slice(0, length - 1)}…` : value;
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat("ru-RU").format(value);
}

function formatPercent(value: number) {
  return `${value.toFixed(value >= 10 ? 1 : 1)}%`;
}

function stageHref(stageId: string) {
  return `/pipeline/stages/${stageId}`;
}

function iconTone(tone: Tone) {
  return {
    blue: "bg-[#e7f0ff] text-[#2f74eb]",
    cyan: "bg-[#dff7f8] text-[#2ca9b5]",
    green: "bg-[#e1f3e8] text-[#2ea355]",
    yellow: "bg-[#f7efcf] text-[#b4871c]",
    orange: "bg-[#fbe7d7] text-[#e67d1d]",
    purple: "bg-[#efe4ff] text-[#7e59d1]",
    teal: "bg-[#ddf5f0] text-[#239c88]",
    red: "bg-[#fde5e7] text-[#d65662]",
    gray: "bg-bg-card text-text-muted",
  }[tone];
}

function flowBarTone(tone: Tone) {
  return {
    blue: "bg-[#5594e8]",
    cyan: "bg-[#4bb8c6]",
    green: "bg-[#5eaf71]",
    yellow: "bg-[#d7ad3d]",
    orange: "bg-[#df8b4e]",
    purple: "bg-[#8870cf]",
    teal: "bg-[#43a997]",
    red: "bg-danger",
    gray: "bg-text-muted",
  }[tone];
}

function statDotTone(tone: StatTone) {
  return {
    passed: "bg-success",
    accepted: "bg-success",
    rejected: "bg-danger",
    pending: "bg-warning",
    failed: "bg-danger",
    skipped: "bg-[#989085]",
    candidate: "bg-[#8e6cf7]",
  }[tone];
}
