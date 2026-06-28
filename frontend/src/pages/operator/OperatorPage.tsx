import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getJsonAuth } from "@/shared/api/http";

interface OperatorStatus {
  backendHealth: string;
  dbHealth: string;
  tdlibStatus: string;
  primaryAccountId: number | null;
  lastUpdateReceivedAt: string | null;
  lastMessagePersistedAt: string | null;
  rawMessageCount: number;
  pipelineEventCount: number;
  replayRunCount: number;
  latestRuns: ReplayRun[];
  recentErrors: RuntimeLog[];
}

interface Diagnostics {
  proxyEnabled: boolean;
  proxyConfiguredInTdlib: boolean;
  proxyPingStatus: string | null;
  lastUpdateReceivedAt: string | null;
  lastUpdateNewMessageAt: string | null;
  rawMessagesCount: number;
  lastError: string | null;
  liveUpdatesStatus: string | null;
}

interface SyncDiagnostics {
  tdlibClientAlive: boolean;
  updateLoopRunning: boolean;
  dbChatsCount: number;
  rawMessagesCount: number;
  proxyEnabled: boolean;
  proxyConfiguredInTdlib: boolean;
  proxyPingStatus: string | null;
  sourceOfTruth: string;
}

interface RuntimeLog {
  timestamp: string;
  level: string;
  accountId: number | null;
  eventType: string;
  message: string | null;
  errorCode: string | null;
  errorSummary: string | null;
}

interface RuntimeEvent {
  timestamp: string;
  eventType: string;
  accountId: number | null;
  telegramChatId: number | null;
  telegramMessageId: number | null;
  durationMs: number | null;
}

interface PipelineEvent {
  id: number;
  eventType: string;
  createdAt: string;
  payload: string;
}

interface Message {
  id: number;
  groupTitle: string;
  topicName: string | null;
  text: string | null;
  caption: string | null;
  date: string | null;
}

interface ReplayRun {
  id: number;
  runName: string;
  status: string;
  startedAt: string | null;
  error: string | null;
}

interface WorkerHealth {
  reachable: boolean;
  status: string;
}

function yesNo(value?: boolean) {
  return value ? "Да" : "Нет";
}

function statusText(value?: string | null) {
  const labels: Record<string, string> = {
    UP: "Работает",
    DOWN: "Недоступно",
    OK: "Работает",
    CONNECTED: "Подключено",
    DISCONNECTED: "Отключено",
    AUTH_REQUIRED: "Нужна авторизация",
    NOT_TESTED: "Не проверялось",
    ERROR: "Ошибка",
    UNKNOWN: "Неизвестно",
  };
  return labels[value ?? ""] ?? value ?? "Неизвестно";
}

function sseText(value: string) {
  if (value === "CONNECTED") return "Подключено";
  if (value === "CONNECTING") return "Подключение...";
  if (value === "ERROR") return "Ошибка";
  return "Отключено";
}

export function OperatorPage() {
  const queryClient = useQueryClient();
  const [sseStatus, setSseStatus] = useState("DISCONNECTED");
  const [lastSseEvent, setLastSseEvent] = useState<string | null>(null);

  const status = useQuery({ queryKey: ["operator-status"], queryFn: () => getJsonAuth<OperatorStatus>("/api/v2/operator/status"), refetchInterval: 10000 });
  const diagnostics = useQuery({ queryKey: ["telegram-diagnostics"], queryFn: () => getJsonAuth<Diagnostics>("/api/v2/telegram/diagnostics"), refetchInterval: 10000 });
  const syncDiagnostics = useQuery({ queryKey: ["telegram-sync-diagnostics", status.data?.primaryAccountId], queryFn: () => getJsonAuth<SyncDiagnostics>(`/api/v2/telegram/sync/diagnostics?accountId=${status.data?.primaryAccountId ?? 1}`), refetchInterval: 10000 });
  const worker = useQuery({ queryKey: ["ai-v2-worker"], queryFn: () => getJsonAuth<WorkerHealth>("/api/v2/ai/model-worker/health"), refetchInterval: 10000 });
  const messages = useQuery({ queryKey: ["telegram-recent-messages"], queryFn: () => getJsonAuth<Message[]>("/api/v2/telegram/messages/recent?limit=5"), refetchInterval: 5000 });
  const pipelineEvents = useQuery({ queryKey: ["pipeline-recent-events"], queryFn: () => getJsonAuth<PipelineEvent[]>("/api/v2/pipeline/events/recent?limit=20"), refetchInterval: 5000 });
  const logs = useQuery({ queryKey: ["telegram-recent-logs"], queryFn: () => getJsonAuth<RuntimeLog[]>("/api/v2/telegram/logs/recent?limit=10"), refetchInterval: 10000 });
  const runtimeEvents = useQuery({ queryKey: ["telegram-runtime-events"], queryFn: () => getJsonAuth<RuntimeEvent[]>("/api/v2/telegram/events/recent?limit=15"), refetchInterval: 10000 });

  useEffect(() => {
    const source = new EventSource("/api/v1/pipeline/events/stream", { withCredentials: false });
    setSseStatus("CONNECTING");
    source.onopen = () => setSseStatus("CONNECTED");
    source.onerror = () => setSseStatus("ERROR");
    source.addEventListener("pipeline", (event) => {
      setLastSseEvent(event instanceof MessageEvent ? event.data : new Date().toISOString());
      queryClient.invalidateQueries({ queryKey: ["telegram-recent-messages"] });
      queryClient.invalidateQueries({ queryKey: ["pipeline-recent-events"] });
    });
    return () => source.close();
  }, [queryClient]);

  const lastMessage = messages.data?.[0];
  const ingestedCount = (pipelineEvents.data ?? []).filter((event) => event.eventType === "MESSAGE_INGESTED").length;

  return (
    <div className="space-y-6">
      <PageHeaderCard title="Оператор" description="Мониторинг backend, TDLib, live ingest, ошибок, событий и быстрых переходов. Управление аккаунтами и чатами находится в разделах Аккаунты и Группы." pipelineNote={`SSE: ${sseText(sseStatus)}`} />

      <section className="grid gap-3 md:grid-cols-4" aria-label="Обзор системы">
        <Metric title="Состояние backend" value={statusText(status.data?.backendHealth)} />
        <Metric title="Состояние базы" value={statusText(status.data?.dbHealth)} />
        <Metric title="Состояние TDLib" value={syncDiagnostics.data?.tdlibClientAlive ? "Подключено" : statusText(status.data?.tdlibStatus)} tone={syncDiagnostics.data?.tdlibClientAlive ? "success" : "warning"} />
        <Metric title="Состояние proxy" value={`${yesNo(syncDiagnostics.data?.proxyConfiguredInTdlib)} / ${statusText(syncDiagnostics.data?.proxyPingStatus)}`} />
        <Metric title="Воркер моделей" value={worker.data?.reachable ? statusText(worker.data.status) : "Воркер моделей недоступен"} tone={worker.data?.reachable ? "success" : "danger"} />
        <Metric title="Активный аккаунт" value={status.data?.primaryAccountId ? `#${status.data.primaryAccountId}` : "Не выбран"} />
        <Metric title="Raw messages" value={String(status.data?.rawMessageCount ?? syncDiagnostics.data?.rawMessagesCount ?? 0)} />
        <Metric title="События конвейера" value={String(status.data?.pipelineEventCount ?? 0)} />
        <Metric title="Запуски конвейера" value={String(status.data?.replayRunCount ?? 0)} />
        <Metric title="Чатов в DB-cache" value={String(syncDiagnostics.data?.dbChatsCount ?? 0)} />
        <Metric title="Update loop" value={syncDiagnostics.data?.updateLoopRunning ? "Работает" : "Остановлен"} />
        <Metric title="Источник данных" value={syncDiagnostics.data?.sourceOfTruth ?? "DB_CACHE"} />
      </section>

      <section className="grid gap-5 xl:grid-cols-[1fr_420px]">
        <Card><CardContent className="p-5">
          <h2 className="text-lg font-semibold text-text-strong">Живой поток</h2>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <Info label="SSE статус" value={sseText(sseStatus)} />
            <Info label="MESSAGE_INGESTED за выборку" value={String(ingestedCount)} />
            <Info label="Последнее событие" value={lastSseEvent ? lastSseEvent.slice(0, 160) : "Нет событий"} />
            <Info label="Последнее сообщение" value={lastMessage ? `${lastMessage.groupTitle}: ${(lastMessage.text || lastMessage.caption || "").slice(0, 120)}` : "Нет данных"} />
          </div>
          <div className="mt-4"><Button asChild variant="outline"><Link to="/messages">Открыть сообщения</Link></Button></div>
        </CardContent></Card>

        <Card><CardContent className="p-5">
          <h2 className="text-lg font-semibold text-text-strong">Быстрые действия</h2>
          <div className="mt-4 grid gap-2">
            <Button asChild variant="outline"><Link to="/accounts">Перейти к аккаунтам</Link></Button>
            <Button asChild variant="outline"><Link to="/groups">Перейти к группам</Link></Button>
            <Button asChild variant="outline"><Link to="/messages">Перейти к сообщениям</Link></Button>
            <Button asChild variant="outline"><Link to="/pipeline">Перейти к конвейеру</Link></Button>
            <Button asChild variant="outline"><Link to="/materials">Перейти к материалам</Link></Button>
          </div>
        </CardContent></Card>
      </section>

      <section className="grid gap-5 xl:grid-cols-3">
        <LogPanel title="Последние ошибки" items={(logs.data ?? []).map((item) => `${item.timestamp} ${item.level} ${item.eventType}: ${item.errorSummary ?? item.message ?? "Без описания"}`)} />
        <LogPanel title="Последние Telegram runtime events" items={(runtimeEvents.data ?? []).map((item) => `${item.timestamp} ${item.eventType} account=${item.accountId ?? "-"} chat=${item.telegramChatId ?? "-"}`)} />
        <Card><CardContent className="p-5">
          <h2 className="text-lg font-semibold text-text-strong">TDLib logs</h2>
          <div className="mt-3 space-y-2 text-sm">
            <Info label="Последнее обновление" value={diagnostics.data?.lastUpdateReceivedAt ?? "Нет данных"} />
            <Info label="Последнее сообщение" value={diagnostics.data?.lastUpdateNewMessageAt ?? "Нет данных"} />
            <Info label="Живой ingest" value={diagnostics.data?.liveUpdatesStatus ?? "Нет данных"} />
            <Info label="Последняя ошибка" value={diagnostics.data?.lastError ?? "Нет ошибок"} />
          </div>
        </CardContent></Card>
      </section>
    </div>
  );
}

function Metric({ title, value, tone = "default" }: { title: string; value: string; tone?: "default" | "success" | "warning" | "danger" }) {
  return <div className="rounded-3xl border border-border-subtle bg-bg-card p-5"><p className="text-sm text-text-muted">{title}</p><div className="mt-2"><Badge variant={tone === "success" ? "success" : tone === "danger" ? "danger" : tone === "warning" ? "warning" : "outline"}>{value}</Badge></div></div>;
}

function Info({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl bg-bg-elevated p-3"><p className="text-xs uppercase tracking-wide text-text-weak">{label}</p><p className="mt-1 break-words text-sm text-text-strong">{value}</p></div>;
}

function LogPanel({ title, items }: { title: string; items: string[] }) {
  return <Card><CardContent className="p-5"><h2 className="text-lg font-semibold text-text-strong">{title}</h2><div className="mt-3 max-h-[320px] space-y-2 overflow-auto font-mono-value text-xs text-text-muted">{items.length ? items.map((item, index) => <div key={`${item}-${index}`} className="rounded-xl bg-bg-elevated p-2">{item}</div>) : <div className="rounded-xl bg-bg-elevated p-2">Нет данных</div>}</div></CardContent></Card>;
}
