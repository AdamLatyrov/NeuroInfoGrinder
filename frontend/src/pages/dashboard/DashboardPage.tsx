import { PageHeaderCard } from "@/components/domain/page-header-card";
import { useNavigate } from "react-router-dom";
import { KpiRibbon } from "@/components/domain/kpi-ribbon";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useGuidesQuery } from "@/shared/api/guidesApi";
import {
  useQueueStatusQuery,
  useTokenSummaryQuery,
  useTokenDailyQuery,
} from "@/shared/api/monitorApi";
import { useAccountsQuery } from "@/shared/api/accountsApi";
import { displayGuideStatus } from "@/shared/types";
import { SpinnerGap } from "@phosphor-icons/react";
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
} from "recharts";

function fmtTokM(v: number): string {
  return (v / 1000000).toFixed(0) + "M";
}

function fmtTokM1(v: number): string {
  return (v / 1000000).toFixed(1) + "M";
}

function tooltipValueToNumber(value: unknown): number {
  return typeof value === "number" ? value : Number(value ?? 0);
}

function ChartPlaceholder({ title }: { title: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[200px] flex items-center justify-center text-xs text-text-weak">
          Данные появятся после запуска обработки
        </div>
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const accountsQuery = useAccountsQuery();
  const guidesQuery = useGuidesQuery({ page: 0, size: 10 });
  const tokenSummaryQuery = useTokenSummaryQuery();
  const queueStatusQuery = useQueueStatusQuery();
  const tokenDailyQuery = useTokenDailyQuery(14);

  const accounts = accountsQuery.data ?? [];
  const guides = guidesQuery.data?.content ?? [];
  const tokenSummary = tokenSummaryQuery.data;
  const queueStatus = queueStatusQuery.data;
  const tokenDaily = tokenDailyQuery.data ?? [];

  const onlineAccounts = accounts.filter(
    (a) => a.status === "CONNECTED"
  ).length;

  const kpiMetrics = [
    {
      label: "Аккаунтов online",
      value: onlineAccounts,
      change: "+0",
      trend: "flat" as const,
    },
    {
      label: "Токены сегодня",
      value: tokenSummary ? fmtTokM(tokenSummary.totalTokensToday) : "0",
      change: "+0",
      trend: "flat" as const,
    },
    {
      label: "Стоимость сегодня",
      value: tokenSummary
        ? `$${tokenSummary.totalCostToday.toFixed(2)}`
        : "$0.00",
      change: "+0",
      trend: "flat" as const,
    },
    {
      label: "Размер очереди",
      value: queueStatus ? queueStatus.queued : 0,
      change: "+0",
      trend: "flat" as const,
    },
    {
      label: "Задачи в работе",
      value: queueStatus ? queueStatus.running : 0,
      change: "+0",
      trend: "flat" as const,
    },
    {
      label: "Зависшие задачи",
      value: queueStatus ? queueStatus.stuck : 0,
      change: "+0",
      trend: "flat" as const,
    },
  ];

  const costByProvider =
    tokenSummary?.tokensByProvider.map((p) => ({
      provider: p.provider,
      cost: p.cost,
    })) ?? [];

  const isLoading = accountsQuery.isLoading && guidesQuery.isLoading;
  const isRefreshing =
    accountsQuery.isFetching ||
    guidesQuery.isFetching ||
    tokenSummaryQuery.isFetching ||
    queueStatusQuery.isFetching ||
    tokenDailyQuery.isFetching;

  const refreshDashboard = () => {
    accountsQuery.refetch();
    guidesQuery.refetch();
    tokenSummaryQuery.refetch();
    queueStatusQuery.refetch();
    tokenDailyQuery.refetch();
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Операционная сводка"
        description="Состояние конвейера NeuroInfoGrinder"
        pipelineNote="Поток: Telegram → Цепочки → Классификация → LLM → Ревью → Публикация"
        actions={{
          primary: {
            label: "Добавить",
            items: [
              { label: "Аккаунт", onClick: () => navigate("/accounts") },
              { label: "Группу", onClick: () => navigate("/groups") },
              { label: "Классификатор", onClick: () => navigate("/classifiers") },
              { label: "Промпт", onClick: () => navigate("/prompts") },
            ],
          },
          onRefresh: refreshDashboard,
          refreshPending: isRefreshing,
        }}
      >
        <div className="mt-4">
          {isLoading ? (
            <div className="flex items-center justify-center py-4">
              <SpinnerGap
                size={20}
                weight="regular"
                className="animate-spin text-text-muted"
              />
            </div>
          ) : (
            <KpiRibbon metrics={kpiMetrics} />
          )}
        </div>
      </PageHeaderCard>

      {/* Pipeline stages */}
      <section>
        <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider mb-3">
          Pipeline
        </h2>
        <div className="rounded-2xl border border-border-subtle bg-bg-card p-6 text-center text-xs text-text-weak">
          Состояние конвейера появится после запуска обработки
        </div>
      </section>

      {/* Charts 2x2 */}
      <section>
        <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider mb-3">
          Аналитика
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Messages by hour - no backend endpoint yet */}
          <ChartPlaceholder title="Сообщения по часам" />

          {/* Tokens by day */}
          {tokenDaily.length > 0 ? (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">Токены по дням</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="h-[200px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={tokenDaily}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" />
                      <XAxis
                        dataKey="date"
                        tick={{ fontSize: 11, fill: "#6B7280" }}
                      />
                      <YAxis
                        tick={{ fontSize: 11, fill: "#6B7280" }}
                        tickFormatter={fmtTokM}
                      />
                      <RechartsTooltip
                        contentStyle={{
                          fontSize: 12,
                          borderRadius: 8,
                          border: "1px solid #E7E5E4",
                        }}
                        formatter={(value) => [
                          fmtTokM1(tooltipValueToNumber(value)),
                          "Токены",
                        ]}
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

          {/* Published guides by day - no backend endpoint yet */}
          <ChartPlaceholder title="Гайды по дням" />

          {/* Cost by provider */}
          {costByProvider.length > 0 ? (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">
                  Стоимость по провайдерам
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="h-[200px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={costByProvider} layout="vertical">
                      <CartesianGrid strokeDasharray="3 3" stroke="#E7E5E4" />
                      <XAxis
                        type="number"
                        tick={{ fontSize: 11, fill: "#6B7280" }}
                        tickFormatter={(v: number) => "$" + v}
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
                        formatter={(value) => [
                          `$${tooltipValueToNumber(value).toFixed(2)}`,
                          "Стоимость",
                        ]}
                      />
                      <Bar
                        dataKey="cost"
                        fill="#F5C94A"
                        radius={[0, 4, 4, 0]}
                        name="Стоимость"
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>
          ) : (
            <ChartPlaceholder title="Стоимость по провайдерам" />
          )}
        </div>
      </section>

      {/* Recent guides */}
      <section>
        <h2 className="text-sm font-semibold text-text-muted uppercase tracking-wider mb-3">
          Последние гайды
        </h2>
        {guides.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {guides.slice(0, 4).map((guide) => {
              return (
                <Card
                  key={guide.id}
                  className="hover:shadow-md transition-shadow"
                >
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="text-sm font-semibold text-text-strong line-clamp-2 leading-snug">
                        {guide.title}
                      </span>
                      <StatusBadge status={displayGuideStatus(guide.status)} />
                    </div>
                    <div className="flex flex-wrap gap-1.5 mb-2">
                      <Badge variant="outline" className="text-[10px]">
                        {guide.sourceGroupId
                          ? `Группа #${guide.sourceGroupId}`
                          : "\u2014"}
                      </Badge>
                      <Badge variant="outline" className="text-[10px]">
                        {guide.providerId
                          ? `Провайдер #${guide.providerId}`
                          : "\u2014"}
                        {guide.model ? ` / ${guide.model}` : ""}
                      </Badge>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-text-muted">
                      <span className="font-mono-value">
                        {guide.totalTokens.toLocaleString()} ток.
                      </span>
                      <span>${guide.estimatedCost.toFixed(3)}</span>
                      {guide.confidence != null ? (
                        <span
                          className={
                            guide.confidence >= 0.85
                              ? "text-success"
                              : guide.confidence >= 0.75
                              ? "text-warning"
                              : "text-danger"
                          }
                        >
                          уверенность {guide.confidence}
                        </span>
                      ) : (
                        <span>уверенность —</span>
                      )}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        ) : (
          <EmptyState
            title="Нет гайдов"
            description="Гайды появятся после обработки сообщений из групп"
          />
        )}
      </section>
    </div>
  );
}
