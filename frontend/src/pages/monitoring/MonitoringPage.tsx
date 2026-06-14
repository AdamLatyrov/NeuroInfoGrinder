import { useState, useMemo } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useTokenSummaryQuery,
  useQueueStatusQuery,
  useTokenDailyQuery,
  useGroupStatsQuery,
} from "@/shared/api/monitorApi";
import { useProvidersQuery } from "@/shared/api/providersApi";
import {
  useFlowMetricsQuery,
  useTracesQuery,
  useTraceQuery,
} from "@/shared/api/tracesApi";
import { displayProviderStatus } from "@/shared/types";
import type {
  FlowMetrics,
  PipelineTrace,
  TraceStage,
  TraceStatus,
} from "@/shared/types";
import {
  Warning,
  CellSignalSlash,
  ChartLineUp,
  SpinnerGap,
  ArrowRight,
  Lightning,
} from "@phosphor-icons/react";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
} from "recharts";

// ── Stage / Status display helpers ──

const STAGE_LABELS: Partial<Record<TraceStage, string>> = {
  TELEGRAM_READ: "Telegram",
  RULES: "Правила",
  SIGNAL_SCORING: "Скоринг",
  CLASSIFIER: "Классификатор",
  LLM_CLASSIFIER: "LLM",
  GUIDE_GENERATION: "Гайды",
  MODERATION: "Модерация",
};

const STATUS_LABELS: Partial<Record<TraceStatus, string>> = {
  SUCCESS: "Успех",
  SKIPPED: "Пропущен",
  FAILED: "Ошибка",
  PENDING: "Ожидание",
};

const STATUS_VARIANT: Partial<Record<TraceStatus, "success" | "warning" | "error" | "neutral">> = {
  SUCCESS: "success",
  SKIPPED: "neutral",
  FAILED: "error",
  PENDING: "warning",
};

const ALL_STAGES: TraceStage[] = [
  "TELEGRAM_READ",
  "RULES",
  "SIGNAL_SCORING",
  "CLASSIFIER",
  "LLM_CLASSIFIER",
  "GUIDE_GENERATION",
  "MODERATION",
];

const ALL_STATUSES: TraceStatus[] = ["SUCCESS", "SKIPPED", "FAILED", "PENDING"];

// ── Shared helpers ──

function ChartPlaceholder({ title }: { title: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[240px] flex items-center justify-center text-xs text-text-weak">
          Data will appear when processing starts
        </div>
      </CardContent>
    </Card>
  );
}

function fmtNum(n: number) {
  return n.toLocaleString("ru-RU");
}

function fmtCost(n: number) {
  return `$${n.toFixed(4)}`;
}

function fmtDuration(ms: number | null) {
  if (ms == null) return "\u2014";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function fmtTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

// ── Flow KPI card ──

function KpiCard({ label, value, accent }: { label: string; value: string | number; accent?: string }) {
  return (
    <Card>
      <CardContent className="p-3">
        <div className="text-xs text-text-muted mb-0.5">{label}</div>
        <div className={`text-xl font-bold ${accent ?? "text-text-strong"}`}>
          {value}
        </div>
      </CardContent>
    </Card>
  );
}

// ── Conversion funnel ──

function ConversionFunnel({ metrics }: { metrics: FlowMetrics }) {
  const steps = [
    { label: "Telegram", value: metrics.messagesRead },
    { label: "Правила", value: metrics.rulesPassed },
    { label: "Классификатор", value: metrics.classified },
    { label: "LLM", value: metrics.sentToLlm },
    { label: "Гайды", value: metrics.guidesCreated },
    { label: "Модерация", value: metrics.sentToModeration },
  ];

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Конверсия воронки</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap items-center gap-1.5 text-sm">
          {steps.map((step, i) => {
            const prev = i === 0 ? step.value : steps[i - 1].value;
            const pct = prev > 0 ? ((step.value / prev) * 100).toFixed(1) : "\u2014";
            return (
              <span key={step.label} className="flex items-center gap-1.5">
                {i > 0 && <ArrowRight size={14} className="text-text-weak" />}
                <span className="inline-flex flex-col items-center rounded-lg border border-border-subtle px-2.5 py-1.5">
                  <span className="text-xs text-text-muted">{step.label}</span>
                  <span className="font-bold text-text-strong">{fmtNum(step.value)}</span>
                  <span className="text-xs text-brand-blue">
                    {i === 0 ? "100%" : `${pct}%`}
                  </span>
                </span>
              </span>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

// ── Trace detail dialog ──

function TraceDetailDialog({
  traceId,
  open,
  onOpenChange,
}: {
  traceId: string | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const traceQuery = useTraceQuery(traceId);

  const steps = traceQuery.data ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Трассировка {traceId}</DialogTitle>
          <DialogDescription>
            {steps.length} этап(ов) в цепочке обработки
          </DialogDescription>
        </DialogHeader>

        {traceQuery.isLoading ? (
          <div className="flex items-center justify-center py-8 text-sm text-text-muted">
            <SpinnerGap size={20} className="animate-spin mr-2" />
            Загрузка...
          </div>
        ) : steps.length === 0 ? (
          <div className="py-8 text-center text-sm text-text-weak">
            Нет данных по этой трассировке
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {steps.map((step) => (
              <div
                key={step.id}
                className="rounded-lg border border-border-subtle p-3"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-semibold text-text-strong">
                    {STAGE_LABELS[step.stage] ?? step.stage}
                  </span>
                  <StatusBadge status={STATUS_VARIANT[step.status] ?? "neutral"} />
                </div>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-text-muted">
                  {step.durationMs != null && (
                    <div>Длительность: <span className="text-text-strong">{fmtDuration(step.durationMs)}</span></div>
                  )}
                  {step.inputTokens > 0 && (
                    <div>In токены: <span className="text-text-strong">{fmtNum(step.inputTokens)}</span></div>
                  )}
                  {step.outputTokens > 0 && (
                    <div>Out токены: <span className="text-text-strong">{fmtNum(step.outputTokens)}</span></div>
                  )}
                  {step.costUsd > 0 && (
                    <div>Стоимость: <span className="text-text-strong">{fmtCost(step.costUsd)}</span></div>
                  )}
                  {step.score != null && (
                    <div>Score: <span className="text-text-strong">{step.score}</span></div>
                  )}
                  {step.model && (
                    <div>Модель: <span className="text-text-strong">{step.model}</span></div>
                  )}
                  {step.errorMessage && (
                    <div className="col-span-2 text-danger">
                      Ошибка: {step.errorMessage}
                    </div>
                  )}
                  {step.reason && (
                    <div className="col-span-2">
                      Причина: <span className="text-text-strong">{step.reason}</span>
                    </div>
                  )}
                </div>
                {step.inputData && (
                  <details className="mt-2">
                    <summary className="text-xs text-text-muted cursor-pointer">Входные данные</summary>
                    <pre className="mt-1 text-xs bg-bg-app rounded-lg p-2 overflow-x-auto max-h-40 text-text-muted">
                      {step.inputData}
                    </pre>
                  </details>
                )}
                {step.outputData && (
                  <details className="mt-1">
                    <summary className="text-xs text-text-muted cursor-pointer">Выходные данные</summary>
                    <pre className="mt-1 text-xs bg-bg-app rounded-lg p-2 overflow-x-auto max-h-40 text-text-muted">
                      {step.outputData}
                    </pre>
                  </details>
                )}
              </div>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

// ── Main page ──

export function MonitoringPage() {
  // ── existing queries ──
  const tokenSummaryQuery = useTokenSummaryQuery();
  const queueStatusQuery = useQueueStatusQuery();
  const tokenDailyQuery = useTokenDailyQuery(14);
  const providersQuery = useProvidersQuery();
  const groupStatsQuery = useGroupStatsQuery();

  // ── new queries ──
  const flowMetricsQuery = useFlowMetricsQuery();
  const tracesQuery = useTracesQuery();

  const tokenSummary = tokenSummaryQuery.data;
  const queueStatus = queueStatusQuery.data;
  const tokenDaily = tokenDailyQuery.data ?? [];
  const providers = providersQuery.data ?? [];
  const groupStats = groupStatsQuery.data ?? [];
  const flowMetrics = flowMetricsQuery.data;
  const tracesData = tracesQuery.data;
  const traces: PipelineTrace[] = tracesData?.content ?? [];

  // ── traces filter state ──
  const [stageFilter, setStageFilter] = useState<string>("ALL");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [selectedTraceId, setSelectedTraceId] = useState<string | undefined>(undefined);
  const [traceDialogOpen, setTraceDialogOpen] = useState(false);

  const filteredTraces = useMemo(() => {
    return traces.filter((t) => {
      if (stageFilter !== "ALL" && t.stage !== stageFilter) return false;
      if (statusFilter !== "ALL" && t.status !== statusFilter) return false;
      return true;
    });
  }, [traces, stageFilter, statusFilter]);

  const formatTokens = (n: number) =>
    n >= 1000000
      ? `${(n / 1000000).toFixed(1)}M`
      : `${(n / 1000).toFixed(0)}K`;

  const costByProvider =
    tokenSummary?.tokensByProvider.map((p) => ({
      provider: p.provider,
      cost: p.cost,
    })) ?? [];

  const hasData =
    tokenSummary || queueStatus || providers.length > 0 || flowMetrics;

  // ── refetch all ──
  const refetchAll = () => {
    tokenSummaryQuery.refetch();
    queueStatusQuery.refetch();
    tokenDailyQuery.refetch();
    providersQuery.refetch();
    groupStatsQuery.refetch();
    flowMetricsQuery.refetch();
    tracesQuery.refetch();
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Мониторинг"
        description="Токены, стоимость, очереди и статус конвейера"
        actions={{
          onRefresh: refetchAll,
          onExport: () => {},
        }}
      />

      {/* Alert cards from real provider data */}
      {providers.filter((p) => p.status !== "HEALTHY").length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {providers
            .filter((p) => p.status !== "HEALTHY")
            .map((p) => (
              <Card key={p.id}>
                <CardContent className="p-3">
                  <div className="flex items-center gap-2 mb-1">
                    <CellSignalSlash size={14} weight="regular" className="text-warning" />
                    <span className="text-sm font-medium text-text-strong">{p.name}</span>
                  </div>
                  <div className="text-xs text-text-muted">
                    {p.lastTestResult ?? `Status: ${p.status}`}
                  </div>
                  {p.lastTestedAt && (
                    <div className="text-xs text-text-weak mt-0.5">
                      Last tested: {p.lastTestedAt}
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
        </div>
      )}

      {!hasData && !tokenSummaryQuery.isLoading && !providersQuery.isLoading && !flowMetricsQuery.isLoading ? (
        <EmptyState
          icon={ChartLineUp}
          title="No monitoring data yet"
          description="Monitoring data will appear once the pipeline starts processing"
        />
      ) : (
        <Tabs defaultValue="flow">
          <TabsList>
            <TabsTrigger value="flow">Поток</TabsTrigger>
            <TabsTrigger value="traces">Трассировки</TabsTrigger>
            <TabsTrigger value="tokens">Токены</TabsTrigger>
            <TabsTrigger value="cost">Стоимость</TabsTrigger>
            <TabsTrigger value="queue">Очередь</TabsTrigger>
            <TabsTrigger value="groups">Группы</TabsTrigger>
            <TabsTrigger value="providers">Провайдеры</TabsTrigger>
          </TabsList>

          {/* ═══ Flow tab ═══ */}
          <TabsContent value="flow">
            {flowMetrics ? (
              <div className="flex flex-col gap-4">
                {/* KPI ribbon */}
                <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
                  <KpiCard label="Прочитано" value={fmtNum(flowMetrics.messagesRead)} />
                  <KpiCard label="Правила пройдено" value={fmtNum(flowMetrics.rulesPassed)} />
                  <KpiCard label="Классифицировано" value={fmtNum(flowMetrics.classified)} />
                  <KpiCard label="В LLM" value={fmtNum(flowMetrics.sentToLlm)} />
                  <KpiCard label="Гайдов создано" value={fmtNum(flowMetrics.guidesCreated)} />
                  <KpiCard label="Модерации" value={fmtNum(flowMetrics.sentToModeration)} />
                  <KpiCard label="Принято" value={fmtNum(flowMetrics.approved)} accent="text-success" />
                  <KpiCard label="Отклонено" value={fmtNum(flowMetrics.rejected)} accent={flowMetrics.rejected > 0 ? "text-danger" : undefined} />
                </div>

                {/* Conversion funnel */}
                <ConversionFunnel metrics={flowMetrics} />

                {/* Error count + tokens + cost summary */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <Card>
                    <CardContent className="p-3">
                      <div className="flex items-center gap-2 mb-0.5">
                        <Lightning size={14} className={flowMetrics.errors > 0 ? "text-danger" : "text-text-muted"} />
                        <span className="text-xs text-text-muted">Ошибки</span>
                      </div>
                      <div className={`text-xl font-bold ${flowMetrics.errors > 0 ? "text-danger" : "text-text-strong"}`}>
                        {fmtNum(flowMetrics.errors)}
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardContent className="p-3">
                      <div className="text-xs text-text-muted mb-0.5">Всего токенов</div>
                      <div className="text-xl font-bold text-text-strong">
                        {fmtNum(flowMetrics.totalInputTokens + flowMetrics.totalOutputTokens)}
                      </div>
                      <div className="text-xs text-text-muted mt-0.5">
                        In: {fmtNum(flowMetrics.totalInputTokens)} / Out: {fmtNum(flowMetrics.totalOutputTokens)}
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardContent className="p-3">
                      <div className="text-xs text-text-muted mb-0.5">Общая стоимость</div>
                      <div className="text-xl font-bold text-text-strong">
                        {fmtCost(flowMetrics.totalCostUsd)}
                      </div>
                    </CardContent>
                  </Card>
                </div>
              </div>
            ) : (
              <Card>
                <CardContent className="p-5">
                  <div className="text-sm text-text-weak text-center py-6">
                    {flowMetricsQuery.isLoading ? "Загрузка..." : "Нет данных о потоке"}
                  </div>
                </CardContent>
              </Card>
            )}
          </TabsContent>

          {/* ═══ Traces tab ═══ */}
          <TabsContent value="traces">
            <div className="flex flex-col gap-4">
              {/* Filters */}
              <div className="flex items-center gap-3">
                <Select value={stageFilter} onValueChange={setStageFilter}>
                  <SelectTrigger className="w-[200px]">
                    <SelectValue placeholder="Этап" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Все этапы</SelectItem>
                    {ALL_STAGES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {STAGE_LABELS[s]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Select value={statusFilter} onValueChange={setStatusFilter}>
                  <SelectTrigger className="w-[200px]">
                    <SelectValue placeholder="Статус" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Все статусы</SelectItem>
                    {ALL_STATUSES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {STATUS_LABELS[s] ?? s}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                {tracesData && (
                  <span className="text-xs text-text-muted">
                    {filteredTraces.length} из {tracesData.totalElements} записей
                  </span>
                )}
              </div>

              {/* Table */}
              <div className="rounded-2xl border border-border-subtle bg-bg-card overflow-hidden">
                {filteredTraces.length > 0 ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Время</TableHead>
                        <TableHead>Группа</TableHead>
                        <TableHead>Сообщение</TableHead>
                        <TableHead>Этап</TableHead>
                        <TableHead>Статус</TableHead>
                        <TableHead className="text-right">Score</TableHead>
                        <TableHead className="text-right">Токены</TableHead>
                        <TableHead className="text-right">Стоимость</TableHead>
                        <TableHead className="text-right">Длительность</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredTraces.map((t) => (
                        <TableRow
                          key={t.id}
                          className="cursor-pointer hover:bg-bg-app/50"
                          onClick={() => {
                            setSelectedTraceId(t.traceId);
                            setTraceDialogOpen(true);
                          }}
                        >
                          <TableCell className="text-sm text-text-muted whitespace-nowrap">
                            {fmtTime(t.startedAt)}
                          </TableCell>
                          <TableCell className="font-mono-value text-sm">
                            {t.groupId ?? "\u2014"}
                          </TableCell>
                          <TableCell className="font-mono-value text-sm">
                            {t.messageId ?? "\u2014"}
                          </TableCell>
                          <TableCell className="text-sm">
                            {STAGE_LABELS[t.stage] ?? t.stage}
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={STATUS_VARIANT[t.status] ?? "neutral"} />
                          </TableCell>
                          <TableCell className="text-right font-mono-value text-sm">
                            {t.score != null ? t.score.toFixed(2) : "\u2014"}
                          </TableCell>
                          <TableCell className="text-right font-mono-value text-sm">
                            <span className="text-text-muted">
                              {fmtNum(t.inputTokens)}/{fmtNum(t.outputTokens)}
                            </span>
                          </TableCell>
                          <TableCell className="text-right font-mono-value text-sm">
                            {t.costUsd > 0 ? fmtCost(t.costUsd) : "\u2014"}
                          </TableCell>
                          <TableCell className="text-right font-mono-value text-sm">
                            {fmtDuration(t.durationMs)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <div className="text-sm text-text-weak text-center py-8">
                    {tracesQuery.isLoading
                      ? "Загрузка..."
                      : "Нет трассировок по выбранным фильтрам"}
                  </div>
                )}
              </div>
            </div>
          </TabsContent>

          {/* ═══ Tokens tab ═══ */}
          <TabsContent value="tokens">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {tokenDaily.length > 0 ? (
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm">Токены по дням</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="h-[240px]">
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={tokenDaily}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" />
                          <XAxis
                            dataKey="date"
                            tick={{ fontSize: 11, fill: "#6B7280" }}
                          />
                          <YAxis
                            tick={{ fontSize: 11, fill: "#6B7280" }}
                            tickFormatter={(v: number) => formatTokens(v)}
                          />
                          <RechartsTooltip
                            contentStyle={{
                              fontSize: 12,
                              borderRadius: 8,
                              border: "1px solid #E7E5E4",
                            }}
                          />
                          <Line
                            type="monotone"
                            dataKey="tokens"
                            stroke="#3B82F6"
                            strokeWidth={2}
                            dot={false}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  </CardContent>
                </Card>
              ) : (
                <ChartPlaceholder title="Токены по дням" />
              )}

              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm">
                    Токены по провайдерам
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {tokenSummary && tokenSummary.tokensByProvider.length > 0 ? (
                    <div className="flex flex-col gap-3">
                      {tokenSummary.tokensByProvider.map((p) => (
                        <div key={p.provider} className="flex items-center gap-3">
                          <span className="text-sm text-text-strong w-36 shrink-0">
                            {p.provider}
                          </span>
                          <div className="flex-1 h-6 rounded-full bg-bg-app overflow-hidden">
                            {p.tokens > 0 && (
                              <div
                                className="h-full rounded-full bg-brand-blue"
                                style={{
                                  width: `${Math.min(
                                    (p.tokens /
                                      (tokenSummary.totalTokensToday || 1)) *
                                      100,
                                    100
                                  )}%`,
                                }}
                              />
                            )}
                          </div>
                          <span className="font-mono-value text-sm text-text-strong w-16 text-right">
                            {formatTokens(p.tokens)}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="h-[240px] flex items-center justify-center text-xs text-text-weak">
                      Data will appear when processing starts
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          {/* ═══ Cost tab ═══ */}
          <TabsContent value="cost">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {costByProvider.length > 0 ? (
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm">
                      Стоимость по провайдерам
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="h-[240px]">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={costByProvider} layout="vertical">
                          <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" />
                          <XAxis
                            type="number"
                            tick={{ fontSize: 11, fill: "#6B7280" }}
                            tickFormatter={(v: number) => `$${v}`}
                          />
                          <YAxis
                            type="category"
                            dataKey="provider"
                            tick={{ fontSize: 11, fill: "#6B7280" }}
                            width={110}
                          />
                          <RechartsTooltip
                            contentStyle={{
                              fontSize: 12,
                              borderRadius: 8,
                              border: "1px solid #E7E5E4",
                            }}
                          />
                          <Bar
                            dataKey="cost"
                            fill="#F5C94A"
                            radius={[0, 4, 4, 0]}
                          />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </CardContent>
                </Card>
              ) : (
                <ChartPlaceholder title="Стоимость по провайдерам" />
              )}

              <ChartPlaceholder title="Cost per published guide" />
            </div>
          </TabsContent>

          {/* ═══ Queue tab ═══ */}
          <TabsContent value="queue">
            <Card>
              <CardContent className="p-5">
                {queueStatus ? (
                  <div className="flex items-center gap-4 text-sm">
                    <div>
                      <span className="text-text-muted block">Queue size</span>
                      <span className="text-2xl font-bold text-text-strong">
                        {queueStatus.queued}
                      </span>
                    </div>
                    <div>
                      <span className="text-text-muted block">Stuck jobs</span>
                      <span
                        className={`text-2xl font-bold ${
                          queueStatus.stuck > 0
                            ? "text-warning"
                            : "text-text-strong"
                        }`}
                      >
                        {queueStatus.stuck}
                      </span>
                    </div>
                    <div>
                      <span className="text-text-muted block">Processing</span>
                      <span className="text-2xl font-bold text-brand-blue">
                        {queueStatus.running}
                      </span>
                    </div>
                    <div>
                      <span className="text-text-muted block">Paused</span>
                      <span
                        className={`text-2xl font-bold ${
                          queueStatus.paused
                            ? "text-warning"
                            : "text-text-strong"
                        }`}
                      >
                        {queueStatus.paused ? "Yes" : "No"}
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="text-sm text-text-weak text-center py-6">
                    {queueStatusQuery.isLoading
                      ? "Загрузка..."
                      : "Queue data not available"}
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          {/* ═══ Groups tab ═══ */}
          <TabsContent value="groups">
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold text-text-strong mb-3">
                  Статистика групп
                </h3>
                {groupStats.length > 0 ? (
                  <div className="rounded-xl border border-border-subtle overflow-hidden">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Группа</TableHead>
                          <TableHead className="text-right">
                            Сообщений
                          </TableHead>
                          <TableHead className="text-right">
                            Цепочек
                          </TableHead>
                          <TableHead className="text-right">
                            Гайдов
                          </TableHead>
                          <TableHead className="text-right">
                            Опубликовано
                          </TableHead>
                          <TableHead className="text-right">
                            Ошибок
                          </TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {groupStats.map((gs) => (
                          <TableRow key={gs.groupId}>
                            <TableCell className="font-medium">
                              {gs.groupTitle}
                            </TableCell>
                            <TableCell className="text-right font-mono-value">
                              {gs.messagesRead.toLocaleString()}
                            </TableCell>
                            <TableCell className="text-right font-mono-value">
                              {gs.chainsBuilt}
                            </TableCell>
                            <TableCell className="text-right font-mono-value">
                              {gs.guidesGenerated}
                            </TableCell>
                            <TableCell className="text-right font-mono-value">
                              {gs.guidesPublished}
                            </TableCell>
                            <TableCell className="text-right font-mono-value">
                              {gs.errorCount > 0 ? (
                                <span className="text-danger">{gs.errorCount}</span>
                              ) : (
                                "0"
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                ) : (
                  <div className="text-sm text-text-weak text-center py-6">
                    {groupStatsQuery.isLoading
                      ? "Загрузка..."
                      : "No group statistics available"}
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          {/* ═══ Providers tab ═══ */}
          <TabsContent value="providers">
            {providers.length > 0 ? (
              <div className="rounded-2xl border border-border-subtle bg-bg-card overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Провайдер</TableHead>
                      <TableHead>Модель</TableHead>
                      <TableHead>Статус</TableHead>
                      <TableHead>API key</TableHead>
                      <TableHead>Последний тест</TableHead>
                      <TableHead>Результат</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {providers.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell className="font-medium text-text-strong">
                          {p.name}
                        </TableCell>
                        <TableCell className="font-mono-value">
                          {p.model ?? "\u2014"}
                        </TableCell>
                        <TableCell>
                          <StatusBadge
                            status={displayProviderStatus(p.status)}
                          />
                        </TableCell>
                        <TableCell>
                          <span
                            className={
                              p.hasApiKey ? "text-success" : "text-warning"
                            }
                          >
                            {p.hasApiKey ? "Set" : "Missing"}
                          </span>
                        </TableCell>
                        <TableCell className="text-sm text-text-muted">
                          {p.lastTestedAt ?? "\u2014"}
                        </TableCell>
                        <TableCell className="text-sm text-text-muted">
                          {p.lastTestResult ?? "\u2014"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <EmptyState
                icon={Warning}
                title="Нет данных о провайдерах"
                description="Провайдеры появятся после их добавления в систему"
              />
            )}
          </TabsContent>
        </Tabs>
      )}

      {/* Trace detail dialog (lives outside Tabs) */}
      <TraceDetailDialog
        traceId={selectedTraceId}
        open={traceDialogOpen}
        onOpenChange={setTraceDialogOpen}
      />
    </div>
  );
}
