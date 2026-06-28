import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowClockwise, CheckCircle, Flask, WarningCircle } from "@phosphor-icons/react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getJsonAuth, postJsonAuth } from "@/shared/api/http";
import { queryClient } from "@/shared/api/queryClient";

type Threshold = { key: string; value: string; type: string; description: string; safeMin: string | null; safeMax: string | null; defaultValue: string | null };
type WorkerStatus = { reachable: boolean; status: string; classifierStatus: string; classifierName: string; embeddingStatus: string; embeddingName: string; embeddingDimension: number; degraded: boolean; lastError?: string | null };
type SettingsOverview = {
  pipeline?: { latestRunStatus?: string | null; mainBlocker?: string | null; explanation?: string | null; autoPipelineEnabledCount?: number; workerStatus?: string | null; generatedMaterials?: number };
  thresholds?: Threshold[];
  worker?: WorkerStatus;
  providers?: { providers?: Array<Record<string, unknown>>; routes?: Array<Record<string, unknown>>; promptRoutes?: { routes?: Array<Record<string, unknown>> } };
  promptRoutes?: { routes?: Array<Record<string, unknown>> };
  autoPipeline?: Array<Record<string, unknown>>;
};

export function SettingsPage() {
  const settings = useQuery({ queryKey: ["settings-v2"], queryFn: () => getJsonAuth<SettingsOverview>("/api/v2/settings"), refetchInterval: 10000 });
  const updateThreshold = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => postJsonAuth(`/api/v2/pipeline/settings/${key}`, { value, updatedBy: "settings-ui" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings-v2"] }),
  });
  const providerTest = useMutation({ mutationFn: (id: number) => postJsonAuth(`/api/v2/ai/providers/${id}/test`, {}) });
  const data = settings.data;
  const providers = data?.providers?.providers ?? [];
  const routes = data?.promptRoutes?.routes ?? data?.providers?.promptRoutes?.routes ?? [];

  return <div className="flex flex-col gap-5">
    <PageHeaderCard title="Настройки" description="Понятные параметры конвейера: что сейчас включено, на что влияет и нужно ли перезапускать сервис.">
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => settings.refetch()}><ArrowClockwise size={16} />Обновить</Button>
        <Badge variant="outline">Режим: редактируемые безопасные thresholds</Badge>
      </div>
    </PageHeaderCard>

    {settings.isError ? <Card><CardContent className="p-5 text-danger">/api/v2/settings недоступен. Проверь backend deploy.</CardContent></Card> : null}

    <div className="grid gap-4 xl:grid-cols-3">
      <StatusCard title="Конвейер" rows={[
        ["Статус обработки", (data?.pipeline?.autoPipelineEnabledCount ?? 0) > 0 ? "включена для выбранных чатов" : "выключена / scope не выбран"],
        ["Активных scope", data?.pipeline?.autoPipelineEnabledCount ?? 0],
        ["Последний run", data?.pipeline?.latestRunStatus ?? "нет данных"],
        ["Главная причина остановки", data?.pipeline?.mainBlocker ?? "нет"],
        ["Материалов", data?.pipeline?.generatedMaterials ?? 0],
      ]} />
      <StatusCard title="Worker health" rows={[
        ["Worker", data?.worker?.reachable ? data.worker.status : "DOWN"],
        ["BGE-M3", data?.worker?.embeddingStatus ?? "UNKNOWN"],
        ["dimension", data?.worker?.embeddingDimension ?? 1024],
        ["degraded", data?.worker?.degraded ? "true" : "false"],
        ["Зачем важно", "без worker нет embeddings/classification"],
      ]} />
      <StatusCard title="LLM provider" rows={[
        ["Провайдеров", providers.length],
        ["Маршрутов", routes.length],
        ["Статус", String(providers[0]?.healthStatus ?? providers[0]?.health_status ?? "UNKNOWN")],
        ["Ключ", String(providers[0]?.configured ?? providers[0]?.keyConfigured ?? "см. provider card")],
        ["Зачем важно", "решает judge/generation качество и стоимость"],
      ]} />
    </div>

    <Card>
      <CardHeader><CardTitle>Настройки конвейера и пороги</CardTitle></CardHeader>
      <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {(data?.thresholds ?? []).map((item) => <ThresholdRow key={item.key} item={item} pending={updateThreshold.isPending} onSave={(value) => updateThreshold.mutate({ key: item.key, value })} />)}
      </CardContent>
    </Card>

    <Card>
      <CardHeader><CardTitle>Маршруты prompt stages</CardTitle></CardHeader>
      <CardContent className="grid gap-3 lg:grid-cols-2">
        {routes.map((route, index) => <div key={`${route.stage}-${index}`} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
          <div className="flex flex-wrap items-center gap-2"><Badge variant="outline">{String(route.stage)}</Badge><Badge variant={route.routeEnabled === false ? "danger" : "success"}>{route.routeEnabled === false ? "disabled" : "enabled"}</Badge></div>
          <div className="mt-3 grid gap-2 text-sm text-text-muted">
            <div>Провайдер: <span className="text-text-strong">{String(route.provider ?? "—")}</span></div>
            <div>Модель: <span className="text-text-strong">{String(route.model ?? "—")}</span></div>
            <div>Fallback: <span className="text-text-strong">{String(route.fallback ?? "—")}</span></div>
            <div>Последняя ошибка: <span className="text-text-strong">{String(route.lastError ?? "—")}</span></div>
            <div>Зачем важно: <span className="text-text-strong">выбирает модель для judge/generation</span></div>
          </div>
        </div>)}
        {!routes.length ? <div className="rounded-2xl bg-bg-elevated p-4 text-sm text-text-muted">Маршруты не найдены.</div> : null}
      </CardContent>
    </Card>

    <Card>
      <CardHeader><CardTitle>AI providers</CardTitle></CardHeader>
      <CardContent className="grid gap-3 lg:grid-cols-2">
        {providers.map((provider) => <div key={String(provider.id)} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
          <div className="flex flex-wrap items-center justify-between gap-2"><div className="font-semibold text-text-strong">{String(provider.name)}</div><Badge variant={provider.enabled === false ? "danger" : "success"}>{provider.enabled === false ? "disabled" : "enabled"}</Badge></div>
          <div className="mt-2 text-sm text-text-muted">Base URL: {String(provider.baseUrl ?? "—")}</div>
          <div className="mt-1 text-sm text-text-muted">Key status: {String(provider.apiKeyRef ? "configured by env ref" : "missing ref")}</div>
          <Button className="mt-3" variant="outline" size="sm" disabled={providerTest.isPending} onClick={() => providerTest.mutate(Number(provider.id))}><Flask size={16} />Test provider</Button>
        </div>)}
      </CardContent>
    </Card>
  </div>;
}

function StatusCard({ title, rows }: { title: string; rows: Array<[string, unknown]> }) {
  return <Card><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent className="space-y-2">{rows.map(([label, value]) => <div key={label} className="flex items-center justify-between gap-3 rounded-xl bg-bg-elevated px-3 py-2 text-sm"><span className="text-text-muted">{label}</span><span className="text-right font-medium text-text-strong">{String(value)}</span></div>)}</CardContent></Card>;
}

function ThresholdRow({ item, pending, onSave }: { item: Threshold; pending: boolean; onSave: (value: string) => void }) {
  const id = `threshold-${item.key}`;
  const description = settingDescription(item.key, item.description);
  return <div className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
    <div className="flex items-start justify-between gap-2"><div><label htmlFor={id} className="font-semibold text-text-strong">{item.key}</label><p className="mt-1 text-xs text-text-muted">{description}</p></div><div className="flex flex-col items-end gap-1"><Badge variant="outline">{item.type}</Badge><Badge variant="success">safe</Badge></div></div>
    <div className="mt-3 grid gap-2 text-xs text-text-muted"><div><span className="font-semibold text-text-strong">Текущее значение:</span> {item.value}</div><div><span className="font-semibold text-text-strong">Allowed:</span> {item.safeMin ?? "—"}..{item.safeMax ?? "—"}</div><div><span className="font-semibold text-text-strong">Default:</span> {item.defaultValue ?? "—"}</div><div><span className="font-semibold text-text-strong">Влияние:</span> {settingImpact(item.key)}</div><div><span className="font-semibold text-text-strong">Restart required:</span> no</div></div>
    <div className="mt-3 flex gap-2"><Input id={id} defaultValue={item.value} aria-label={`Новое значение ${item.key}`} onKeyDown={(event) => { if (event.key === "Enter") onSave((event.target as HTMLInputElement).value); }} /><Button variant="primary" size="sm" disabled={pending} onClick={() => { const input = document.getElementById(id) as HTMLInputElement | null; if (input) onSave(input.value); }}><CheckCircle size={16} />Сохранить</Button></div>
  </div>;
}

function settingDescription(key: string, fallback: string) {
  if (/threshold|score/i.test(key)) return "Порог принятия кандидата. Чем выше значение, тем меньше сообщений дойдёт до LLM/material.";
  if (/max|limit/i.test(key)) return "Ограничение объёма обработки, чтобы защитить стоимость и стабильность.";
  if (/discussion/i.test(key)) return "Параметр discussion segment: влияет на группировку цепочек сообщений.";
  return fallback || "Параметр backend pipeline.";
}

function settingImpact(key: string) {
  if (/judge/i.test(key)) return "влияет на переход от LLM Judge к generation";
  if (/cluster/i.test(key)) return "влияет на попадание кластеров в LLM Judge";
  if (/discussion/i.test(key)) return "влияет на multi-message grouping";
  if (/single/i.test(key)) return "влияет на single-message candidates";
  return "изменяет поведение pipeline без перезапуска";
}
