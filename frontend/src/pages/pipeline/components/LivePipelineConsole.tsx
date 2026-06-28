import { useMemo, useState, type ReactNode } from "react";
import { ArrowClockwise, Play, SpinnerGap, WarningCircle } from "@phosphor-icons/react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { getJsonAuth, postFormAuth, postJsonAuth } from "@/shared/api/http";
import {
  type ClassifierStatusResponse,
  useClassifierStatusQuery,
  usePipelineClustersQuery,
  usePipelineEmbeddingsQuery,
  usePipelineLiveAcceptanceQuery,
  usePipelineLlmJudgeQuery,
  usePipelineLiveBatchesQuery,
  usePipelineLiveEventsQuery,
  usePipelineLiveStagesQuery,
  usePipelineLiveSummaryQuery,
  usePipelineMaterialGenerationQuery,
  usePipelinePendingBreakdownQuery,
  usePipelineQueuePendingMutation,
  type PipelineLiveBatch,
  type PipelineLiveAcceptance,
  type PipelineLiveSummary,
  type PipelinePendingBreakdownRow,
  type PipelineStageSummary,
} from "@/shared/api/pipelineApi";
import { queryClient } from "@/shared/api/queryClient";
import { blockerLabel, statusLabelRu } from "@/shared/pipelineLabels";
import { PipelineConveyor } from "./PipelineConveyor";
import { PipelineInspectorDrawer } from "./PipelineInspectorDrawer";

type Mode = "live" | "test";
type InspectorTab = "queue" | "runs" | "clusters" | "embeddings" | "llm" | "worker" | "events" | "scope";
type DrawerMode = "stage" | "run" | "clusters" | "embeddings" | "llm" | "materials";

interface Dataset { id: number; name: string; messageCount: number; }
interface UploadResult { datasetId: number; importedMessages: number; rawImportedMessages: number; }
interface ReplayRun { id: number; datasetId?: number; runName: string; status: string; startedAt: string | null; finishedAt: string | null; error: string | null; }

export function LivePipelineConsole() {
  const [mode, setMode] = useState<Mode>("live");
  const [inspectorTab, setInspectorTab] = useState<InspectorTab>("queue");
  const [selectedStage, setSelectedStage] = useState<PipelineStageSummary | null>(null);
  const [selectedBatch, setSelectedBatch] = useState<PipelineLiveBatch | null>(null);
  const [drawerMode, setDrawerMode] = useState<DrawerMode | null>(null);

  const summary = usePipelineLiveSummaryQuery();
  const acceptance = usePipelineLiveAcceptanceQuery();
  const stages = usePipelineLiveStagesQuery();
  const events = usePipelineLiveEventsQuery(20);
  const batches = usePipelineLiveBatchesQuery(20);
  const pending = usePipelinePendingBreakdownQuery();
  const worker = useClassifierStatusQuery();
  const clusters = usePipelineClustersQuery(summary.data?.latestRunId, 50);
  const embeddings = usePipelineEmbeddingsQuery(summary.data?.latestRunId, 50);
  const llm = usePipelineLlmJudgeQuery(summary.data?.latestRunId, 50);
  const materials = usePipelineMaterialGenerationQuery(summary.data?.latestRunId, 50);
  const criticalAlert = criticalAlertFor(summary.data, summary.isError || stages.isError, worker.data);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-summary"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-acceptance"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-stages"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-events"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-batches"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-pending-breakdown"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-clusters"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-embeddings"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-llm-judge"] });
    queryClient.invalidateQueries({ queryKey: ["pipeline-live-material-generation"] });
  };

  const openStage = (stage: PipelineStageSummary) => {
    setSelectedStage(stage);
    setSelectedBatch(null);
    setDrawerMode(stageDefaultMode(stage));
  };

  const openBatch = (batch: PipelineLiveBatch) => {
    setSelectedBatch(batch);
    setSelectedStage(null);
    setDrawerMode("run");
  };

  return (
    <div className="space-y-5">
      <section className="rounded-[28px] border border-border-subtle bg-bg-card p-5 shadow-[0_18px_54px_rgba(0,0,0,0.12)] lg:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-text-strong">Конвейер</h1>
            <p className="mt-1 text-sm text-text-muted">Live pipeline обработки Telegram-сообщений</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-xl border border-border-subtle bg-bg-elevated px-3 py-2 text-sm text-text-muted">
              Last run: <span className="font-semibold text-text-strong">{statusLabelRu(summary.data?.latestRunStatus)}</span>
            </span>
            <Button variant="primary" className="gap-2" onClick={() => setMode("test")}>
              <Play size={16} weight="fill" />
              Запустить прогон
            </Button>
            <Button variant="outline" size="icon" onClick={refresh} aria-label="Обновить pipeline">
              <ArrowClockwise size={17} />
            </Button>
          </div>
        </div>
        <div className="mt-5 flex flex-wrap gap-2" role="tablist" aria-label="Pipeline mode">
          <ModeTab active={mode === "live"} onClick={() => setMode("live")}>Рабочий конвейер</ModeTab>
          <ModeTab active={mode === "test"} onClick={() => setMode("test")}>Тестовый прогон</ModeTab>
        </div>
        {criticalAlert ? <CriticalAlert text={criticalAlert} /> : null}
      </section>

      {mode === "test" ? <TestReplay /> : (
        <>
          <PipelineConveyor
            stages={stages.data ?? []}
            loading={stages.isLoading || summary.isLoading}
            error={stages.isError || summary.isError}
            onStageOpen={openStage}
          />
          <LiveAcceptancePanel acceptance={acceptance.data} />
          <CurrentStateSummary summary={summary.data} />
          <InspectorTabs active={inspectorTab} onChange={setInspectorTab} />
          <InspectorPanel
            tab={inspectorTab}
            summary={summary.data}
            batches={batches.data?.batches ?? []}
            events={events.data?.events ?? []}
            pendingRows={pending.data ?? []}
            worker={worker.data}
            clusters={clusters.data}
            embeddings={embeddings.data}
            llm={llm.data}
            materials={materials.data}
            onBatch={openBatch}
            onOpenDrawer={setDrawerMode}
          />
        </>
      )}

      <PipelineInspectorDrawer
        stage={selectedStage}
        batch={selectedBatch}
        mode={drawerMode}
        latestRunId={summary.data?.latestRunId}
        onClose={() => { setSelectedStage(null); setSelectedBatch(null); setDrawerMode(null); }}
        onMode={setDrawerMode}
      />
    </div>
  );
}

function CurrentStateSummary({ summary }: { summary?: PipelineLiveSummary }) {
  const items = [
    ["Live queue", summary?.liveQueue ?? 0, "Только сообщения, уже поставленные в live queue"],
    ["Collecting", summary?.collectingMessages ?? 0, "Сообщения в debounce/batch сборке"],
    ["Active runs", summary?.runningRuns ?? 0, "RUNNING live auto runs"],
    ["Last run", statusLabelRu(summary?.latestRunStatus), blockerLabel(summary?.latestTerminalReason)],
    ["Materials", summary?.generatedMaterials ?? 0, "Созданные knowledge items/materials"],
    ["Main reason", blockerLabel(summary?.mainBlocker ?? summary?.latestTerminalReason), "Почему материалов может не быть"],
  ];
  return <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-6" aria-label="Current state summary">{items.map(([label, value, hint]) => <StateCard key={String(label)} label={String(label)} value={value} hint={String(hint)} />)}</section>;
}

function LiveAcceptancePanel({ acceptance }: { acceptance?: PipelineLiveAcceptance }) {
  const hasFreshRun = Boolean(acceptance?.runId);
  const verdict = !acceptance ? "Загружаю post-fix live state..." : acceptance.rawGap > 0 ? "Fresh raw detected, waiting for pipeline run." : "Backend fix deployed. Latest fresh live run is shown separately from historical backlog.";
  const items = [
    ["Latest raw", acceptance?.latestRawId ?? "—", formatDate(acceptance?.latestRawAt)],
    ["Latest processed", acceptance?.latestProcessedRawId ?? "—", formatDate(acceptance?.latestProcessedAt)],
    ["New raw gap", acceptance?.rawGap ?? "—", "raw after latest processed"],
    ["Post-fix run", hasFreshRun ? `#${acceptance?.runId}` : "—", `${statusLabelRu(acceptance?.runStatus)} / ${blockerLabel(acceptance?.terminalReason)}`],
    ["Run messages", acceptance?.replayRunMessages ?? "—", `batch messages ${acceptance?.messageCount ?? "—"}`],
    ["Embeddings", acceptance?.messageEmbeddings ?? "—", "message_embeddings artifacts"],
    ["Clusters", (acceptance?.microclusters ?? 0) + (acceptance?.macroclusters ?? 0), `micro ${acceptance?.microclusters ?? 0}, macro ${acceptance?.macroclusters ?? 0}`],
    ["Single candidates", acceptance?.singleCandidates ?? "—", "single-message material candidates"],
    ["LLM calls", acceptance?.providerCalls ?? "—", "provider calls"],
    ["Materials", acceptance?.materials ?? "—", "knowledge items"],
  ];
  return <Card><CardContent className="space-y-4 p-5"><div><h2 className="text-lg font-bold text-text-strong">Live acceptance after backend fix</h2><p className="mt-1 text-sm text-text-muted">{verdict} Historical backlog is shown below and is not the active live flow.</p></div><div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">{items.map(([label, value, hint]) => <StateCard key={String(label)} label={String(label)} value={value} hint={String(hint)} />)}</div></CardContent></Card>;
}

function InspectorTabs({ active, onChange }: { active: InspectorTab; onChange: (tab: InspectorTab) => void }) {
  const tabs: Array<[InspectorTab, string]> = [["queue", "Queue & Backlog"], ["runs", "Recent Runs"], ["clusters", "Clusters"], ["embeddings", "Embeddings"], ["llm", "LLM & Materials"], ["worker", "Worker & Models"], ["events", "Events"], ["scope", "Auto Scope"]];
  return <div className="flex gap-2 overflow-x-auto rounded-2xl border border-border-subtle bg-bg-card p-2" role="tablist" aria-label="Pipeline inspectors">{tabs.map(([value, label]) => <button key={value} type="button" onClick={() => onChange(value)} className={cn("shrink-0 rounded-xl px-3 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue", active === value ? "bg-brand-blue-soft text-brand-blue" : "text-text-muted hover:bg-bg-elevated hover:text-text-strong")}>{label}</button>)}</div>;
}

function InspectorPanel(props: { tab: InspectorTab; summary?: PipelineLiveSummary; batches: PipelineLiveBatch[]; events: object[]; pendingRows: PipelinePendingBreakdownRow[]; worker?: ClassifierStatusResponse; clusters?: Record<string, unknown>; embeddings?: Record<string, unknown>; llm?: Record<string, unknown>; materials?: Record<string, unknown>; onBatch: (batch: PipelineLiveBatch) => void; onOpenDrawer: (mode: DrawerMode) => void; }) {
  return <Card><CardContent className="p-5">{props.tab === "queue" ? <QueueBacklog summary={props.summary} rows={props.pendingRows} /> : null}{props.tab === "runs" ? <RecentRuns batches={props.batches} onBatch={props.onBatch} /> : null}{props.tab === "clusters" ? <DetailPreview title="Clusters" data={props.clusters} onOpen={() => props.onOpenDrawer("clusters")} /> : null}{props.tab === "embeddings" ? <DetailPreview title="Embeddings" data={props.embeddings} onOpen={() => props.onOpenDrawer("embeddings")} /> : null}{props.tab === "llm" ? <LlmMaterials llm={props.llm} materials={props.materials} onOpen={props.onOpenDrawer} /> : null}{props.tab === "worker" ? <WorkerModels summary={props.summary} worker={props.worker} /> : null}{props.tab === "events" ? <CompactRows rows={props.events} keys={["timestamp", "type", "runId", "batchId", "stage", "message"]} /> : null}{props.tab === "scope" ? <CompactRows rows={props.pendingRows} keys={["chatTitle", "topicTitle", "autoEnabledEffective", "pending", "queued", "collectingBatches", "runningRuns"]} /> : null}</CardContent></Card>;
}

function QueueBacklog({ summary, rows }: { summary?: PipelineLiveSummary; rows: PipelinePendingBreakdownRow[] }) {
  const [confirm, setConfirm] = useState(false);
  const queuePending = usePipelineQueuePendingMutation();
  return <div className="space-y-5"><div><h2 className="text-lg font-bold text-text-strong">Queue & Backlog</h2><p className="mt-1 text-sm text-text-muted">{formatNumber(summary?.intakePending ?? 0)} - это intake backlog, не live queue. {formatNumber(summary?.pendingInsideEnabledScopes ?? 0)} внутри включённых scope можно поставить в live queue. {formatNumber(summary?.pendingOutsideEnabledScopes ?? 0)} вне auto scope не участвуют.</p></div><div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6"><StateCard label="pending intake" value={summary?.intakePending ?? 0} hint="backlog" /><StateCard label="live queue" value={summary?.liveQueue ?? 0} hint="active queue" /><StateCard label="collecting" value={summary?.collectingMessages ?? 0} hint="debounce" /><StateCard label="active runs" value={summary?.runningRuns ?? 0} hint="RUNNING" /><StateCard label="inside enabled" value={summary?.pendingInsideEnabledScopes ?? 0} hint="can queue" /><StateCard label="outside scope" value={summary?.pendingOutsideEnabledScopes ?? 0} hint="not processed" /></div><div className="flex flex-wrap gap-2"><Button variant="outline" disabled={queuePending.isPending} onClick={() => queuePending.mutate({ limit: 200, dryRun: true })}>Dry-run requeue</Button><Button variant="primary" disabled={queuePending.isPending} onClick={() => setConfirm(true)}>Queue 200 pending</Button><Button variant="outline" onClick={() => document.getElementById("pending-breakdown-table")?.scrollIntoView({ behavior: "smooth" })}>Open pending breakdown</Button></div>{queuePending.data ? <div className="rounded-2xl border border-border-subtle bg-bg-elevated p-3 text-sm text-text-muted">dryRun={String(queuePending.data.dryRun)}; candidates={queuePending.data.candidates}; queued={queuePending.data.queued}; remaining={queuePending.data.remainingInsideEnabledScopes}</div> : null}<CompactRows id="pending-breakdown-table" rows={rows} keys={["chatTitle", "topicTitle", "pending", "queued", "collectingBatches", "runningRuns", "latestRawMessageAt"]} />{confirm ? <ConfirmModal onCancel={() => setConfirm(false)} onConfirm={() => { queuePending.mutate({ limit: 200, dryRun: false }); setConfirm(false); }} /> : null}</div>;
}

function RecentRuns({ batches, onBatch }: { batches: PipelineLiveBatch[]; onBatch: (batch: PipelineLiveBatch) => void }) {
  return <div className="space-y-4"><h2 className="text-lg font-bold text-text-strong">Recent Runs</h2><div className="overflow-auto rounded-2xl border border-border-subtle"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-bg-elevated text-text-muted"><tr>{["runId", "batchId", "status", "terminalReason", "messages", "clusters", "single", "materials", "duration", "completed"].map((item) => <th key={item} className="px-3 py-2 font-semibold">{item}</th>)}</tr></thead><tbody>{batches.map((batch) => <tr key={batch.batchId} className="border-t border-border-subtle hover:bg-bg-elevated"><td className="px-3 py-2"><button className="font-semibold text-brand-blue" onClick={() => onBatch(batch)}>#{batch.runId ?? "—"}</button></td><td className="px-3 py-2">#{batch.batchId}</td><td className="px-3 py-2">{batch.status}</td><td className="px-3 py-2">{blockerLabel(batch.terminalReason)}</td><td className="px-3 py-2">{formatNumber(batch.messageCount)}</td><td className="px-3 py-2">—</td><td className="px-3 py-2">{formatNumber(batch.candidateCount)}</td><td className="px-3 py-2">{formatNumber(batch.materialCount)}</td><td className="px-3 py-2">{duration(batch.startedAt, batch.completedAt)}</td><td className="px-3 py-2">{formatDate(batch.completedAt)}</td></tr>)}</tbody></table></div></div>;
}

function DetailPreview({ title, data, onOpen }: { title: string; data?: Record<string, unknown>; onOpen: () => void }) {
  const items = Array.isArray(data?.items) ? data.items as Array<Record<string, unknown>> : [];
  return <div className="space-y-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-bold text-text-strong">{title}</h2><p className="text-sm text-text-muted">Reason: {String(data?.reason ?? "—")}</p></div><Button variant="outline" onClick={onOpen}>Open details</Button></div><CompactRows rows={items} keys={title === "Clusters" ? ["id", "type", "title", "score", "messageCount", "status", "representativeMessage"] : ["id", "rawMessageId", "kind", "model", "dimension", "hash", "textPreview"]} /></div>;
}

function LlmMaterials({ llm, materials, onOpen }: { llm?: Record<string, unknown>; materials?: Record<string, unknown>; onOpen: (mode: DrawerMode) => void }) {
  return <div className="grid gap-4 lg:grid-cols-2"><DetailPreview title="LLM Judge" data={llm} onOpen={() => onOpen("llm")} /><DetailPreview title="Materials" data={materials} onOpen={() => onOpen("materials")} /></div>;
}

function WorkerModels({ summary, worker }: { summary?: PipelineLiveSummary; worker?: ClassifierStatusResponse }) {
  return <div className="space-y-4"><h2 className="text-lg font-bold text-text-strong">Worker & Models</h2><div className="grid gap-3 md:grid-cols-3"><StateCard label="Worker" value={summary?.workerStatus ?? worker?.status ?? "—"} hint="shown above only if critical" /><StateCard label="BGE-M3" value={worker?.embeddingName ?? "BGE-M3"} hint={`dimension ${String(worker?.embeddingDimension ?? 1024)}`} /><StateCard label="Degraded" value={String(worker?.degraded ?? false)} hint={String(worker?.embeddingStatus ?? "—")} /></div></div>;
}

function CompactRows({ rows, keys, id }: { rows: object[]; keys: string[]; id?: string }) {
  if (!rows.length) return <div id={id} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4 text-sm text-text-muted">Нет данных для отображения.</div>;
  return <div id={id} className="overflow-auto rounded-2xl border border-border-subtle"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-bg-elevated text-text-muted"><tr>{keys.map((key) => <th key={key} className="px-3 py-2 font-semibold">{key}</th>)}</tr></thead><tbody>{rows.slice(0, 50).map((row, index) => <tr key={index} className="border-t border-border-subtle">{keys.map((key) => <td key={key} className="max-w-[280px] px-3 py-2 align-top text-text-default"><span className="line-clamp-3 break-words">{formatValue(readKey(row, key))}</span></td>)}</tr>)}</tbody></table></div>;
}

function TestReplay() {
  const [file, setFile] = useState<File | null>(null);
  const [datasetId, setDatasetId] = useState("");
  const datasets = useQuery({ queryKey: ["v2-datasets"], queryFn: () => getJsonAuth<Dataset[]>("/api/v2/datasets") });
  const upload = useMutation({ mutationFn: async () => { if (!file) throw new Error("Выберите .jsonl файл"); const form = new FormData(); form.append("file", file); form.append("name", file.name.replace(/\.jsonl$/i, "")); return postFormAuth<UploadResult>("/api/v2/datasets/upload", form); }, onSuccess: (result) => setDatasetId(String(result.datasetId)) });
  const run = useMutation({ mutationFn: () => postJsonAuth<ReplayRun>("/api/v2/replay-runs/start", { datasetId: Number(datasetId), runName: `Тестовый прогон ${new Date().toLocaleString("ru-RU")}` }) });
  return <Card><CardContent className="p-5"><h2 className="text-lg font-bold text-text-strong">Тестовый прогон</h2><p className="mt-1 text-sm text-text-muted">Локальный replay для небольшого JSONL dataset. Не запускает массовый Telegram backfill.</p><div className="mt-4 grid gap-3 lg:grid-cols-[1fr_auto_auto]"><Input type="file" accept=".jsonl" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><Button variant="outline" disabled={!file || upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? <SpinnerGap className="animate-spin" /> : null} Загрузить датасет</Button><Button disabled={!datasetId || run.isPending} onClick={() => run.mutate()}>{run.isPending ? <SpinnerGap className="animate-spin" /> : null} Запустить</Button></div><select className="mt-4 w-full rounded-xl border border-border-subtle bg-bg-elevated px-3 py-2 text-text-strong" value={datasetId} onChange={(event) => setDatasetId(event.target.value)}><option value="">Выберите датасет</option>{(datasets.data ?? []).map((dataset) => <option key={dataset.id} value={dataset.id}>#{dataset.id} {dataset.name} ({dataset.messageCount})</option>)}</select>{run.data ? <p className="mt-3 text-sm text-success">Run #{run.data.id}: {run.data.status}</p> : null}</CardContent></Card>;
}

function ConfirmModal({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return <div className="fixed inset-0 z-[60] grid place-items-center bg-black/50 p-4"><div className="max-w-md rounded-2xl border border-border-subtle bg-bg-card p-5 shadow-2xl"><h2 className="text-lg font-bold text-text-strong">Поставить 200 pending в live queue?</h2><p className="mt-2 text-sm text-text-muted">Это write action. Будут затронуты только pending сообщения внутри включённых auto scopes.</p><div className="mt-4 flex justify-end gap-2"><Button variant="outline" onClick={onCancel}>Отмена</Button><Button variant="primary" onClick={onConfirm}>Подтвердить</Button></div></div></div>;
}

function StateCard({ label, value, hint }: { label: string; value: unknown; hint: string }) {
  return <div className="rounded-2xl border border-border-subtle bg-bg-card p-4"><div className="text-xs font-semibold uppercase tracking-[0.14em] text-text-weak">{label}</div><div className="mt-2 break-words font-mono-value text-xl font-bold text-text-strong">{formatValue(value)}</div><div className="mt-1 text-xs text-text-muted">{hint}</div></div>;
}

function CriticalAlert({ text }: { text: string }) {
  return <div className="mt-5 flex gap-3 rounded-2xl border border-danger/35 bg-danger-soft p-4 text-sm text-danger"><WarningCircle className="mt-0.5 shrink-0" size={18} /><span>{text}</span></div>;
}

function ModeTab({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" role="tab" aria-selected={active} onClick={onClick} className={cn("rounded-xl px-3 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue", active ? "bg-brand-blue-soft text-brand-blue" : "bg-bg-elevated text-text-muted hover:text-text-strong")}>{children}</button>;
}

function criticalAlertFor(summary?: PipelineLiveSummary, apiError?: boolean, worker?: ClassifierStatusResponse) {
  if (apiError) return "Backend API недоступен: live pipeline state нельзя проверить.";
  if (summary?.workerStatus && summary.workerStatus !== "OK") return `Worker problem: ${summary.workerStatus}`;
  if (worker?.degraded === true) return "BGE-M3 работает в degraded mode.";
  if (worker?.embeddingDimension && worker.embeddingDimension !== 1024) return `BGE-M3 dimension mismatch: ${String(worker.embeddingDimension)}`;
  if (summary?.latestRunStatus === "FAILED") return "Последний live run завершился ошибкой.";
  const inside = summary?.pendingInsideEnabledScopes ?? 0;
  if (inside > 0 && (summary?.liveQueue ?? 0) === 0 && stale(summary?.lastSchedulerTickAt, 10 * 60_000)) return "Backlog внутри включённых scope не попадает в live queue.";
  return null;
}

function stageDefaultMode(stage: PipelineStageSummary): DrawerMode {
  if (stage.id === "embeddings") return "embeddings";
  if (stage.id === "clustering") return "clusters";
  if (stage.id === "llm_judge") return "llm";
  if (stage.id === "material_generation" || stage.id === "materials_publish") return "materials";
  return "stage";
}

function stale(value: string | null | undefined, thresholdMs: number) {
  if (!value) return true;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? Date.now() - time > thresholdMs : true;
}

function formatNumber(value: number) { return new Intl.NumberFormat("ru-RU").format(value); }
function formatDate(value?: string | null) { return value ? new Date(value).toLocaleString("ru-RU") : "—"; }
function duration(start?: string | null, end?: string | null) { if (!start || !end) return "—"; return `${Math.max(0, Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000))} s`; }
function formatValue(value: unknown) { if (value == null) return "—"; if (typeof value === "number") return formatNumber(value); if (typeof value === "boolean") return value ? "true" : "false"; if (typeof value === "string") return value; return JSON.stringify(value); }
function readKey(row: object, key: string) { return (row as Record<string, unknown>)[key]; }
