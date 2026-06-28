import { useEffect, useMemo, useState, type ReactNode } from "react";
import { X } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  usePipelineClustersQuery,
  usePipelineEmbeddingsQuery,
  usePipelineLlmJudgeQuery,
  usePipelineMaterialGenerationQuery,
  usePipelineRunDetailsQuery,
  usePipelineStageDetailsQuery,
  type PipelineLiveBatch,
  type PipelineStageDetails,
  type PipelineStageSummary,
} from "@/shared/api/pipelineApi";

type DrawerMode = "stage" | "run" | "clusters" | "embeddings" | "llm" | "materials";

interface PipelineInspectorDrawerProps {
  stage: PipelineStageSummary | null;
  batch: PipelineLiveBatch | null;
  mode: DrawerMode | null;
  latestRunId?: number | null;
  onClose: () => void;
  onMode: (mode: DrawerMode) => void;
}

const STAGE_TABS = ["Overview", "Recent events", "Messages", "Artifacts", "Errors", "Related runs"] as const;

export function PipelineInspectorDrawer({ stage, batch, mode, latestRunId, onClose, onMode }: PipelineInspectorDrawerProps) {
  const [tab, setTab] = useState<(typeof STAGE_TABS)[number]>("Overview");
  const runId = batch?.runId ?? latestRunId ?? null;
  const stageDetails = usePipelineStageDetailsQuery(stage?.id ?? null);
  const clusters = usePipelineClustersQuery(runId, 50);
  const embeddings = usePipelineEmbeddingsQuery(runId, 50);
  const llm = usePipelineLlmJudgeQuery(runId, 50);
  const materials = usePipelineMaterialGenerationQuery(runId, 50);
  const run = usePipelineRunDetailsQuery(batch?.runId ?? null);
  const open = Boolean(mode || stage || batch);

  useEffect(() => {
    if (!open) return;
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, onClose]);

  const effectiveMode: DrawerMode = mode ?? (batch ? "run" : "stage");
  const title = drawerTitle(effectiveMode, stage, batch);
  const details = stageDetails.data;

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/45" onMouseDown={onClose} role="presentation">
      <aside
        className="h-full w-full max-w-[760px] overflow-hidden border-l border-border-subtle bg-bg-card shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex h-full flex-col">
          <header className="border-b border-border-subtle p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-weak">Pipeline inspector</p>
                <h2 className="mt-2 text-xl font-bold text-text-strong">{title}</h2>
                <p className="mt-1 text-sm text-text-muted">{subtitle(effectiveMode, stage, batch, runId)}</p>
              </div>
              <Button variant="ghost" size="icon" onClick={onClose} aria-label="Закрыть details">
                <X size={18} />
              </Button>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <DrawerTab active={effectiveMode === "stage"} onClick={() => onMode("stage")} disabled={!stage}>Stage details</DrawerTab>
              <DrawerTab active={effectiveMode === "embeddings"} onClick={() => onMode("embeddings")}>Embeddings</DrawerTab>
              <DrawerTab active={effectiveMode === "clusters"} onClick={() => onMode("clusters")}>Clusters</DrawerTab>
              <DrawerTab active={effectiveMode === "llm"} onClick={() => onMode("llm")}>LLM Judge</DrawerTab>
              <DrawerTab active={effectiveMode === "materials"} onClick={() => onMode("materials")}>Materials</DrawerTab>
              <DrawerTab active={effectiveMode === "run"} onClick={() => onMode("run")} disabled={!batch}>Run</DrawerTab>
            </div>
          </header>
          <main className="flex-1 overflow-auto p-5">
            {effectiveMode === "stage" ? (
              <section>
                {stage ? <StageOverview stage={stage} details={details} loading={stageDetails.isLoading} tab={tab} onTab={setTab} /> : <Empty text="Выберите stage на конвейере." />}
              </section>
            ) : null}
            {effectiveMode === "run" ? <RunDetails batch={batch} data={run.data} loading={run.isLoading} /> : null}
            {effectiveMode === "clusters" ? <DetailList title="Clusters" data={clusters.data} loading={clusters.isLoading} emptyText="Кластеры не найдены для последнего live run." /> : null}
            {effectiveMode === "embeddings" ? <DetailList title="Embeddings" data={embeddings.data} loading={embeddings.isLoading} emptyText="Embedding artifacts не найдены для последнего live run." /> : null}
            {effectiveMode === "llm" ? <DetailList title="LLM Judge" data={llm.data} loading={llm.isLoading} emptyText="LLM judge не запускался для последнего live run." /> : null}
            {effectiveMode === "materials" ? <DetailList title="Material generation" data={materials.data} loading={materials.isLoading} emptyText="Материалы не созданы для последнего live run." /> : null}
          </main>
        </div>
      </aside>
    </div>
  );
}

function StageOverview({ stage, details, loading, tab, onTab }: { stage: PipelineStageSummary; details?: PipelineStageDetails; loading: boolean; tab: (typeof STAGE_TABS)[number]; onTab: (tab: (typeof STAGE_TABS)[number]) => void }) {
  const metrics = [
    ["active", stage.active],
    ["waiting", stage.waiting],
    ["processed", stage.processed],
    ["failed", stage.failed],
    ["skipped", stage.skipped],
    ["latency", formatMs(stage.averageLatencyMs)],
  ];

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full border border-border-subtle px-2.5 py-1 text-xs font-semibold text-text-muted">Stage {stage.ordinal}</span>
          <span className="rounded-full bg-brand-blue-soft px-2.5 py-1 text-xs font-semibold text-brand-blue">{stage.status}</span>
        </div>
        <h3 className="mt-3 text-lg font-bold text-text-strong">{stage.name}</h3>
        <p className="mt-1 text-sm text-text-muted">{stage.description}</p>
        <p className="mt-3 text-sm text-warning">Reason: {details?.reason ?? stage.warning ?? "blocker не обнаружен"}</p>
        <p className="mt-1 text-sm text-text-muted">Latest update: {formatDate(stage.lastUpdatedAt)}</p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {metrics.map(([label, value]) => <Metric key={label} label={String(label)} value={value} />)}
      </div>
      <div className="flex flex-wrap gap-2">
        {STAGE_TABS.map((item) => <DrawerTab key={item} active={tab === item} onClick={() => onTab(item)}>{item}</DrawerTab>)}
      </div>
      {loading ? <Empty text="Загружаю stage details..." /> : null}
      {tab === "Overview" ? <OverviewContent details={details} /> : null}
      {tab === "Recent events" ? <Rows rows={details?.latestEvents ?? []} keys={["timestamp", "type", "runId", "batchId", "message"]} /> : null}
      {tab === "Messages" ? <Rows rows={details?.waitingMessages ?? []} keys={["id", "status", "blockedReason", "errorMessage", "text"]} /> : null}
      {tab === "Artifacts" ? <OverviewContent details={details} artifacts /> : null}
      {tab === "Errors" ? <Rows rows={(details?.waitingMessages ?? []).filter((item) => item.errorCode)} keys={["id", "errorCode", "errorMessage", "actionHint"]} /> : null}
      {tab === "Related runs" ? <Rows rows={details?.latestEvents?.filter((event) => event.runId) ?? []} keys={["runId", "batchId", "type", "timestamp", "message"]} /> : null}
    </div>
  );
}

function OverviewContent({ details, artifacts = false }: { details?: PipelineStageDetails; artifacts?: boolean }) {
  if (!details) return <Empty text="Stage details пока не загружены." />;
  const rows = artifacts ? [
    ["workerStatus", details.workerStatus],
    ["embeddingStatus", details.embeddingStatus],
    ["classifierStatus", details.classifierStatus],
    ["clusters", details.clusters],
    ["singleMessageCandidates", details.singleMessageCandidates],
    ["providerCalls", details.providerCalls],
    ["materials", details.materialCount],
  ] : [
    ["explanation", details.explanation],
    ["workerStatus", details.workerStatus],
    ["embeddingStatus", details.embeddingStatus],
    ["clusters", details.clusters],
    ["singleMessageCandidates", details.singleMessageCandidates],
    ["materials", details.materialCount],
  ];
  return <div className="grid gap-3 sm:grid-cols-2">{rows.map(([label, value]) => <Metric key={String(label)} label={String(label)} value={value} />)}</div>;
}

function RunDetails({ batch, data, loading }: { batch: PipelineLiveBatch | null; data?: Record<string, unknown>; loading: boolean }) {
  if (!batch) return <Empty text="Выберите run из Recent Runs." />;
  if (loading) return <Empty text="Загружаю run details..." />;
  return <div className="space-y-4"><MetricGrid data={{ batchId: batch.batchId, runId: batch.runId, status: batch.status, terminalReason: batch.terminalReason, messages: batch.messageCount, materials: batch.materialCount, candidates: batch.candidateCount, duration: duration(batch.startedAt, batch.completedAt) }} /><Rows rows={(data?.stages as Array<Record<string, unknown>> | undefined) ?? []} keys={["stage", "status", "input_count", "output_count", "skipped_count", "error"]} /></div>;
}

function DetailList({ title, data, loading, emptyText }: { title: string; data?: Record<string, unknown>; loading: boolean; emptyText: string }) {
  const items = useMemo(() => (Array.isArray(data?.items) ? data.items as Array<Record<string, unknown>> : []), [data]);
  const providerCalls = useMemo(() => (Array.isArray(data?.providerCalls) ? data.providerCalls as Array<Record<string, unknown>> : []), [data]);
  if (loading) return <Empty text={`Загружаю ${title}...`} />;
  return (
    <div className="space-y-5">
      <MetricGrid data={{ runId: data?.runId ?? "latest", reason: data?.reason ?? "—", total: data?.total ?? items.length, latestTerminalReason: data?.latestTerminalReason ?? "—" }} />
      {items.length ? <Rows rows={items} keys={preferredKeys(title)} /> : <Empty text={emptyText} />}
      {providerCalls.length ? <><h3 className="text-sm font-bold text-text-strong">Provider calls</h3><Rows rows={providerCalls} keys={["id", "stage", "model", "status", "cost", "errorCode", "errorMessage"]} /></> : null}
    </div>
  );
}

function Rows({ rows, keys }: { rows: object[]; keys: string[] }) {
  if (!rows.length) return <Empty text="Нет строк для выбранного раздела." />;
  return <div className="space-y-2">{rows.slice(0, 50).map((row, index) => <div key={index} className="rounded-2xl border border-border-subtle bg-bg-elevated p-3 text-sm"><div className="grid gap-2 md:grid-cols-2">{keys.map((key) => <div key={key}><div className="text-xs uppercase tracking-wide text-text-weak">{key}</div><div className="mt-1 break-words text-text-strong">{formatValue(readKey(row, key))}</div></div>)}</div></div>)}</div>;
}

function MetricGrid({ data }: { data: Record<string, unknown> }) {
  return <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{Object.entries(data).map(([label, value]) => <Metric key={label} label={label} value={value} />)}</div>;
}

function Metric({ label, value }: { label: string; value: unknown }) {
  return <div className="rounded-2xl border border-border-subtle bg-bg-elevated p-3"><div className="text-xs uppercase tracking-wide text-text-weak">{label}</div><div className="mt-1 break-words font-mono-value text-text-strong">{formatValue(value)}</div></div>;
}

function DrawerTab({ active, disabled, onClick, children }: { active?: boolean; disabled?: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" disabled={disabled} onClick={onClick} className={cn("rounded-xl border px-3 py-2 text-xs font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue disabled:opacity-40", active ? "border-brand-blue bg-brand-blue-soft text-brand-blue" : "border-border-subtle bg-bg-elevated text-text-muted hover:text-text-strong")}>{children}</button>;
}

function Empty({ text }: { text: string }) {
  return <div className="rounded-2xl border border-border-subtle bg-bg-elevated p-4 text-sm text-text-muted">{text}</div>;
}

function drawerTitle(mode: DrawerMode, stage: PipelineStageSummary | null, batch: PipelineLiveBatch | null) {
  if (mode === "stage" && stage) return `${stage.ordinal}. ${stage.name}`;
  if (mode === "run" && batch) return `Run #${batch.runId ?? "—"} / Batch #${batch.batchId}`;
  if (mode === "clusters") return "Clusters details";
  if (mode === "embeddings") return "Embedding artifacts";
  if (mode === "llm") return "LLM Judge details";
  return "Material generation details";
}

function subtitle(mode: DrawerMode, stage: PipelineStageSummary | null, batch: PipelineLiveBatch | null, runId: number | null) {
  if (mode === "stage" && stage) return stage.description;
  if (mode === "run" && batch) return `status ${batch.status}; terminal ${batch.terminalReason ?? "—"}`;
  return `latest relevant run: ${runId ?? "not selected"}`;
}

function preferredKeys(title: string) {
  if (title === "Clusters") return ["id", "type", "title", "score", "messageCount", "status", "judgeStatus", "representativeMessage", "errorMessage"];
  if (title === "Embeddings") return ["id", "rawMessageId", "kind", "model", "dimension", "hash", "textPreview", "createdAt"];
  if (title === "LLM Judge") return ["id", "stage", "model", "status", "inputTokens", "outputTokens", "cost", "errorCode", "errorMessage"];
  return ["id", "title", "itemType", "artifactType", "confidence", "providerStatus", "errorCode", "summary"];
}

function formatValue(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number") return new Intl.NumberFormat("ru-RU").format(value);
  if (typeof value === "boolean") return value ? "true" : "false";
  return JSON.stringify(value);
}

function readKey(row: object, key: string) {
  return (row as Record<string, unknown>)[key];
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatMs(value?: number | null) {
  return value == null ? "—" : value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`;
}

function duration(start?: string | null, end?: string | null) {
  if (!start || !end) return "—";
  return `${Math.max(0, Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000))} s`;
}
