import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowSquareOut, Funnel, ShieldWarning, Sparkle, Tag, WarningCircle } from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { useKnowledgeTopicQuery, type KnowledgeSignal } from "@/shared/api/signalsApi";
import { useMemo } from "react";

type TopicTab = "signals" | "review" | "risk";

const TABS: Array<{ value: TopicTab; label: string }> = [
  { value: "signals", label: "Сигналы" },
  { value: "review", label: "Нужна проверка" },
  { value: "risk", label: "Риск" },
];

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    NEEDS_LINK_ENRICHMENT: "Нужна проверка ссылки",
    NEEDS_REVIEW: "Нужна проверка",
    PROMO_ALONE: "Промо без проверки",
    ABUSE_OR_FRAUD: "Риск/абьюз",
    RISK_SENSITIVE_MANUAL_ONLY: "Только ручная проверка",
    LOW_SINGLE_MESSAGE_SCORE: "Слабый одиночный сигнал",
  };
  return labels[status] ?? status;
}

function riskFlags(signal: KnowledgeSignal) {
  return Array.isArray(signal.riskFlags) ? signal.riskFlags.map(String) : [];
}

function entitiesOf(signal: KnowledgeSignal): string[] {
  return Array.isArray(signal.entities) ? signal.entities : [];
}

function SignalCard({ signal, activeEntity }: { signal: KnowledgeSignal; activeEntity: string | null }) {
  const navigate = useNavigate();
  const risks = riskFlags(signal);
  const ents = entitiesOf(signal);

  return (
    <Card className="border-border-subtle bg-bg-card transition hover:-translate-y-0.5 hover:border-amber-400/50 hover:shadow-[0_18px_50px_rgba(245,158,11,0.12)]">
      <CardContent className="p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge className="border-amber-400/40 bg-amber-500/10 text-amber-700" variant="outline">SIGNAL</Badge>
              <Badge variant="outline">{statusLabel(signal.status)}</Badge>
              <Badge variant="outline">{signal.signalType}</Badge>
              {risks.length > 0 ? (
                <Badge className="border-red-400/40 bg-red-500/10 text-red-700" variant="outline">
                  {risks.length} risk
                </Badge>
              ) : null}
            </div>
            <h3 className="mt-3 text-lg font-semibold leading-tight text-text-strong">{signal.title}</h3>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-text-muted">{signal.summary ?? signal.reason ?? "Полезный сигнал без готового материала."}</p>
            {ents.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-1.5">
                {ents.map((entity) => {
                  const isActive = activeEntity === entity;
                  return (
                    <span
                      key={entity}
                      className={cn(
                        "rounded-full border px-2 py-1 text-[11px] font-medium",
                        isActive
                          ? "border-brand-blue bg-brand-blue-soft text-brand-blue"
                          : "border-border-subtle bg-bg-app/60 text-text-muted",
                      )}
                    >
                      {entity}
                    </span>
                  );
                })}
              </div>
            ) : null}
            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-text-weak">
              <span>raw_id: {signal.rawId ?? "—"}</span>
              <span>dataset: {signal.datasetMessageId ?? "—"}</span>
              <span>{signal.chatTitle ?? "Источник не указан"}</span>
              <span>{formatDate(signal.messageDate)}</span>
            </div>
            {risks.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-1.5">
                {risks.map((risk) => (
                  <span key={risk} className="rounded-full border border-red-200 bg-red-50 px-2 py-1 text-[11px] font-medium text-red-700">
                    {risk}
                  </span>
                ))}
              </div>
            ) : null}
            <details className="mt-4 rounded-xl border border-border-subtle bg-bg-app/60 p-3 text-sm">
              <summary className="cursor-pointer font-semibold text-text-strong">Полный текст сигнала</summary>
              <div className="mt-3 whitespace-pre-wrap break-words leading-6 text-text-default">
                {signal.sourceText?.trim() || signal.title || "Текст источника не найден"}
              </div>
            </details>
          </div>

          <div className="flex shrink-0 flex-wrap gap-2 lg:flex-col">
            {signal.appMessageUrl ? (
              <Button variant="outline" size="sm" onClick={() => navigate(signal.appMessageUrl!)}>
                <ArrowSquareOut size={15} />
                Открыть в чате
              </Button>
            ) : null}
            <Button variant="ghost" size="sm" disabled>
              Материала нет
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function TopicSignalsPage() {
  const { slug } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (TABS.some((tab) => tab.value === searchParams.get("tab")) ? searchParams.get("tab") : "signals") as TopicTab;
  const activeEntity = searchParams.get("entity") ?? "__all__";
  const topicQuery = useKnowledgeTopicQuery(slug, activeTab);
  const topic = topicQuery.data?.topic;
  const allSignals = topicQuery.data?.signals ?? [];

  // Build entity options from all signals in the current tab (before filtering), so the dropdown
  // reflects what is actually available. Sorted by frequency.
  const entityOptions = useMemo(() => {
    const counts = new Map<string, number>();
    for (const s of allSignals) for (const e of entitiesOf(s)) counts.set(e, (counts.get(e) ?? 0) + 1);
    return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([entity]) => entity);
  }, [allSignals]);

  const signals = useMemo(() => {
    if (activeEntity === "__all__") return allSignals;
    return allSignals.filter((s) => entitiesOf(s).includes(activeEntity));
  }, [allSignals, activeEntity]);

  const updateTab = (value: TopicTab) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", value);
    setSearchParams(next, { replace: true });
  };

  const updateEntity = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value === "__all__") next.delete("entity");
    else next.set("entity", value);
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link to="/materials" className="inline-flex items-center gap-2 text-sm text-text-muted transition hover:text-brand-blue">
          <ArrowLeft size={16} />
          База знаний
        </Link>
      </div>

      <PageHeaderCard
        title={topic?.name ?? "Тема"}
        description={topic?.description ?? "Материалы и сигналы этой темы."}
        pipelineNote="Сигналы не являются готовыми материалами: их нужно проверить перед генерацией."
        actions={{ onRefresh: () => topicQuery.refetch(), refreshPending: topicQuery.isFetching }}
      />

      <Card className="border-border-subtle bg-bg-card">
        <CardContent className="flex flex-wrap items-center justify-between gap-3 p-4">
          <Tabs value={activeTab} onValueChange={(value) => updateTab(value as TopicTab)}>
            <TabsList className="flex h-auto flex-wrap justify-start gap-1 bg-transparent p-0">
              {TABS.map((tab) => (
                <TabsTrigger key={tab.value} value={tab.value} className="rounded-lg border border-transparent px-3 py-2 text-text-muted data-[state=active]:border-brand-blue/10 data-[state=active]:bg-brand-blue-soft data-[state=active]:text-brand-blue data-[state=active]:shadow-none">
                  {tab.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          <div className="flex items-center gap-2">
            <Funnel size={16} className="text-text-muted" />
            <Select value={activeEntity} onValueChange={updateEntity}>
              <SelectTrigger className="h-9 w-[220px]">
                <SelectValue placeholder="Все сущности" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">Все сущности</SelectItem>
                {entityOptions.map((entity) => (
                  <SelectItem key={entity} value={entity}>{entity}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-3 sm:grid-cols-3">
        <Card className="border-border-subtle bg-bg-card"><CardContent className="flex items-center gap-3 p-4"><Sparkle className="text-amber-600" size={22} /><div><div className="text-xl font-semibold text-text-strong">{signals.length}</div><div className="text-xs text-text-muted">{activeEntity === "__all__" ? "сигналов в выборке" : "отфильтровано по сущности"}</div></div></CardContent></Card>
        <Card className="border-border-subtle bg-bg-card"><CardContent className="flex items-center gap-3 p-4"><WarningCircle className="text-amber-600" size={22} /><div><div className="text-xl font-semibold text-text-strong">{signals.filter((s) => s.status.includes("REVIEW") || s.status.includes("ENRICHMENT")).length}</div><div className="text-xs text-text-muted">нуждаются в проверке</div></div></CardContent></Card>
        <Card className="border-border-subtle bg-bg-card"><CardContent className="flex items-center gap-3 p-4"><ShieldWarning className="text-red-600" size={22} /><div><div className="text-xl font-semibold text-text-strong">{signals.filter((s) => riskFlags(s).length > 0).length}</div><div className="text-xs text-text-muted">с risk flags</div></div></CardContent></Card>
      </div>

      {topicQuery.isLoading ? (
        <div className="grid gap-3">
          {Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-40 animate-pulse rounded-lg border border-border-subtle bg-bg-card" />)}
        </div>
      ) : signals.length === 0 ? (
        <EmptyState icon={Tag} title={activeEntity === "__all__" ? "Сигналов пока нет" : "Нет сигналов по этой сущности"} description={activeEntity === "__all__" ? "Для этой темы ещё нет сохранённых полезных rejected-сообщений." : `Сущность «${activeEntity}» не встречается в сигналах этой темы.`} />
      ) : (
        <div className={cn("grid gap-3")}>
          {signals.map((signal) => <SignalCard key={signal.id} signal={signal} activeEntity={activeEntity === "__all__" ? null : activeEntity} />)}
        </div>
      )}
    </div>
  );
}
