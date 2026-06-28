import { Link } from "react-router-dom";
import {
  CalendarBlank,
  CaretDown,
  ChartLineUp,
  ChatCircle,
  Check,
  Clock,
  FileText,
  LinkSimple,
  Network,
  Stack,
  Tag,
  UsersThree,
  X,
} from "@phosphor-icons/react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const messages = [
  {
    rawId: "3162",
    time: "12:14",
    author: "Vibemode",
    preview: "OpenAI API 403 при прямом base_url...",
    signals: ["API_SIGNAL", "TROUBLESHOOTING"],
    status: "processed",
  },
  {
    rawId: "3168",
    time: "12:15",
    author: "Vibemode",
    preview: "проверь /v1 и Authorization Bearer...",
    signals: ["TROUBLESHOOTING"],
    status: "processed",
  },
  {
    rawId: "3172",
    time: "12:17",
    author: "Vibemode",
    preview: "Cursor иногда шлёт другой path...",
    signals: ["TROUBLESHOOTING"],
    status: "processed",
  },
  {
    rawId: "3180",
    time: "12:21",
    author: "Vibemode",
    preview: "если 401 — ключ или заголовок...",
    signals: ["API_SIGNAL"],
    status: "processed",
  },
  {
    rawId: "3183",
    time: "12:23",
    author: "Vibemode",
    preview: "включи полный лог и посмотри ответ...",
    signals: ["TROUBLESHOOTING"],
    status: "linked",
  },
  {
    rawId: "3185",
    time: "12:25",
    author: "Vibemode",
    preview: "убедись, что base_url без /chat/completion...",
    signals: ["API_SIGNAL"],
    status: "processed",
  },
  {
    rawId: "3187",
    time: "12:27",
    author: "Vibemode",
    preview: "итог: base_url + model id + token",
    signals: ["SOLUTION_SIGNAL"],
    status: "candidate",
  },
];

const traceStages = [
  { title: "messages collected", subtitle: "13 сообщений", tone: "success" },
  { title: "embeddings", subtitle: "13 из 13", tone: "success" },
  { title: "cluster formed", subtitle: "score 0.84", tone: "success" },
  { title: "LLM grouping", subtitle: "1 группа", tone: "success" },
  { title: "material routing", subtitle: "кандидат", tone: "success" },
  { title: "rejected / skipped", subtitle: "LLM_REJECTED_ALL", tone: "danger" },
];

const mergeReasons = [
  "один и тот же чат и топик (Vibemode / API / Cursor)",
  "временной разрыв < 5 минут",
  "общие сущности: Cursor, OpenAI API, Bearer, /v1",
  "похожий смысл: поиск и устранение проблем подключения к API",
];

function MetadataItem({ icon: Icon, label, value }: { icon: React.ElementType; label: string; value: string }) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-[18px] bg-bg-elevated px-4 py-3">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand-blue-soft text-brand-blue">
        <Icon size={19} />
      </span>
      <div className="min-w-0">
        <div className="text-xs font-semibold uppercase tracking-[0.14em] text-text-weak">{label}</div>
        <div className="mt-1 truncate text-sm font-semibold text-text-strong">{value}</div>
      </div>
    </div>
  );
}

function SignalChip({ signal }: { signal: string }) {
  const color = signal === "SOLUTION_SIGNAL"
    ? "bg-success-soft text-success"
    : signal === "API_SIGNAL"
      ? "bg-brand-blue-soft text-brand-blue"
      : "bg-[#ece3ff] text-[#6f4bb8]";

  return <span className={cn("rounded-full px-2.5 py-1 text-[11px] font-semibold", color)}>{signal}</span>;
}

function StatusChip({ status }: { status: string }) {
  const labels: Record<string, string> = {
    processed: "processed",
    linked: "linked",
    candidate: "candidate",
  };
  const classes: Record<string, string> = {
    processed: "bg-success-soft text-success",
    linked: "bg-brand-blue-soft text-brand-blue",
    candidate: "bg-warning-soft text-warning",
  };

  return <span className={cn("rounded-full px-3 py-1 text-xs font-semibold", classes[status])}>{labels[status]}</span>;
}

function SideCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-[24px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_12px_34px_rgba(31,36,48,0.045)]">
      <h2 className="text-base font-semibold text-text-strong">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function PipelineClusterDetailMockupPage() {
  return (
    <div className="rounded-[30px] border border-border-subtle bg-bg-card p-4 shadow-[0_22px_70px_rgba(31,36,48,0.08)] sm:p-6 xl:p-7">
      <div className="flex flex-col gap-5">
        <header className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center gap-2 text-sm text-text-muted">
              <Link to="/pipeline" className="hover:text-brand-blue">Конвейер сообщений</Link>
              <span>/</span>
              <span>Карта кластеров</span>
              <span>/</span>
              <span className="font-medium text-text-strong">Кластер</span>
            </div>
            <h1 className="text-2xl font-semibold tracking-[-0.03em] text-text-strong sm:text-[32px]">Кластер #1287</h1>
            <p className="mt-2 text-sm text-text-muted sm:text-[15px]">Почему сообщения были объединены и что получилось на выходе</p>
            <div className="mt-4 flex flex-wrap gap-2">
              <Badge className="bg-brand-blue-soft text-brand-blue">Macro cluster</Badge>
              <Badge variant="secondary">Vibemode</Badge>
              <Badge variant="secondary">13 сообщений</Badge>
              <Badge variant="warning">Candidate</Badge>
              <Badge variant="secondary">Материал не создан</Badge>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="primary" className="h-11 rounded-[14px] px-5 shadow-[0_12px_26px_rgba(185,133,23,0.20)]">
              Открыть в конвейере
            </Button>
            <Button variant="outline" className="h-11 rounded-[14px] px-5">Обновить</Button>
          </div>
        </header>

        <section className="grid gap-3 rounded-[24px] border border-border-subtle bg-[#fffaf0] p-3 shadow-[0_12px_36px_rgba(31,36,48,0.045)] md:grid-cols-2 xl:grid-cols-5">
          <MetadataItem icon={ChatCircle} label="Чат" value="Vibemode" />
          <MetadataItem icon={Tag} label="Тема" value="API / Cursor" />
          <MetadataItem icon={Clock} label="Временной диапазон" value="12:14 — 12:27" />
          <MetadataItem icon={CalendarBlank} label="Создан" value="22.05.2025 12:14" />
          <MetadataItem icon={ChartLineUp} label="Cluster score" value="0.84" />
        </section>

        <main className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_390px]">
          <div className="grid gap-5">
            <section className="overflow-hidden rounded-[26px] border border-border-subtle bg-bg-elevated shadow-[0_16px_44px_rgba(31,36,48,0.055)]">
              <div className="border-b border-border-subtle px-5 py-4">
                <h2 className="text-lg font-semibold text-text-strong">Сообщения в кластере</h2>
                <p className="mt-1 text-sm text-text-muted">Семь видимых источников из тринадцати; строки выглядят как кликабельные элементы.</p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[920px] border-collapse text-left text-sm">
                  <thead>
                    <tr className="border-b border-border-subtle bg-[#fbf7ef] text-xs font-semibold uppercase tracking-[0.12em] text-text-weak">
                      <th className="px-5 py-3.5">raw_id</th>
                      <th className="px-5 py-3.5">Время</th>
                      <th className="px-5 py-3.5">Автор / чат</th>
                      <th className="px-5 py-3.5">Текст (превью)</th>
                      <th className="px-5 py-3.5">Сигналы</th>
                      <th className="px-5 py-3.5">Статус</th>
                    </tr>
                  </thead>
                  <tbody>
                    {messages.map((message) => (
                      <tr key={message.rawId} className="cursor-pointer border-b border-border-subtle/80 transition-colors last:border-b-0 hover:bg-brand-blue-soft/25">
                        <td className="px-5 py-4 font-mono text-[13px] font-semibold text-text-strong">{message.rawId}</td>
                        <td className="px-5 py-4 font-mono text-xs text-text-muted">{message.time}</td>
                        <td className="px-5 py-4">
                          <span className="inline-flex items-center gap-2 font-medium text-text-strong">
                            <span className="grid h-7 w-7 place-items-center rounded-full bg-brand-blue-soft text-xs font-bold text-brand-blue">V</span>
                            {message.author}
                          </span>
                        </td>
                        <td className="max-w-[330px] px-5 py-4 text-text-default"><span className="line-clamp-1">{message.preview}</span></td>
                        <td className="px-5 py-4">
                          <div className="flex flex-wrap gap-1.5">
                            {message.signals.map((signal) => <SignalChip key={signal} signal={signal} />)}
                          </div>
                        </td>
                        <td className="px-5 py-4"><StatusChip status={message.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex justify-center border-t border-border-subtle px-5 py-4">
                <Button variant="outline" className="rounded-full">
                  Показать все (13)
                  <CaretDown size={15} />
                </Button>
              </div>
            </section>

            <section className="rounded-[26px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_16px_44px_rgba(31,36,48,0.055)]">
              <h2 className="text-lg font-semibold text-text-strong">Pipeline trace кластера</h2>
              <div className="mt-5 overflow-x-auto pb-1">
                <div className="flex min-w-[860px] items-start">
                  {traceStages.map((stage, index) => {
                    const isDanger = stage.tone === "danger";
                    return (
                      <div key={stage.title} className="flex flex-1 items-start">
                        <div className="flex min-w-[128px] flex-col items-center text-center">
                          <span className={cn("grid h-12 w-12 place-items-center rounded-full border text-sm shadow-[0_8px_20px_rgba(31,36,48,0.06)]", isDanger ? "border-danger bg-danger-soft text-danger" : "border-success bg-success-soft text-success")}>
                            {isDanger ? <X size={19} weight="bold" /> : <Check size={19} weight="bold" />}
                          </span>
                          <div className="mt-3 text-sm font-semibold text-text-strong">{stage.title}</div>
                          <div className="mt-1 text-xs text-text-muted">{stage.subtitle}</div>
                        </div>
                        {index < traceStages.length - 1 && <div className="mt-6 h-px flex-1 bg-border-subtle" />}
                      </div>
                    );
                  })}
                </div>
              </div>
            </section>
          </div>

          <aside className="grid content-start gap-4">
            <SideCard title="Почему объединено">
              <div className="grid gap-3">
                {mergeReasons.map((reason) => (
                  <div key={reason} className="flex items-start gap-3 text-sm text-text-default">
                    <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-success-soft text-success"><Check size={13} weight="bold" /></span>
                    <span>{reason}</span>
                  </div>
                ))}
              </div>
              <div className="mt-5 flex items-center justify-between rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3">
                <span className="text-sm text-text-muted">Уверенность</span>
                <span className="rounded-full bg-success-soft px-3 py-1 text-sm font-semibold text-success">0.84</span>
              </div>
            </SideCard>

            <SideCard title="LLM Grouping explanation">
              <div className="grid gap-3 text-sm">
                <div className="rounded-[16px] border border-success/20 bg-success-soft px-4 py-3">
                  <div className="flex items-center justify-between gap-3"><span className="font-semibold text-success">output</span><Badge variant="success">valid group</Badge></div>
                  <p className="mt-2 leading-6 text-text-default">Сообщения описывают одну troubleshooting-цепочку: подключение Cursor к OpenAI-compatible API и исправление ошибок base_url/auth/model id.</p>
                </div>
                <div className="grid gap-2 rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3">
                  <div className="flex justify-between"><span className="text-text-muted">confidence</span><span className="font-semibold text-text-strong">0.84</span></div>
                  <div className="flex justify-between"><span className="text-text-muted">shared entities</span><span className="font-semibold text-text-strong">Cursor, OpenAI API, Bearer, /v1</span></div>
                  <div className="flex justify-between"><span className="text-text-muted">time proximity</span><span className="font-semibold text-text-strong">13 минут</span></div>
                  <div className="flex justify-between"><span className="text-text-muted">same chat/topic</span><span className="font-semibold text-text-strong">Vibemode / API</span></div>
                  <div className="flex justify-between"><span className="text-text-muted">semantic relation</span><span className="font-semibold text-text-strong">problem {"->"} checks {"->"} solution</span></div>
                </div>
                <details className="rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3">
                  <summary className="cursor-pointer font-semibold text-text-strong">parsed JSON</summary>
                  <pre className="mt-3 overflow-auto rounded-xl bg-bg-app p-3 text-xs text-text-muted">{`{ "valid": true, "confidence": 0.84, "sharedEntities": ["Cursor", "OpenAI API", "Bearer", "/v1"], "rejectionReason": null }`}</pre>
                </details>
                <details className="rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3">
                  <summary className="cursor-pointer font-semibold text-text-strong">raw response</summary>
                  <p className="mt-3 text-xs leading-5 text-text-muted">valid=true; explanation=messages share same API troubleshooting chain; confidence=0.84</p>
                </details>
              </div>
            </SideCard>

            <SideCard title="Решение кластера">
              <div className="grid gap-3 text-sm">
                <div className="flex items-center justify-between gap-3"><span className="text-text-muted">decision</span><Badge variant="warning">CANDIDATE</Badge></div>
                <div className="flex items-center justify-between gap-3"><span className="text-text-muted">proposed type</span><span className="font-semibold text-text-strong">Guide</span></div>
                <div className="flex items-center justify-between gap-3"><span className="text-text-muted">материал / отклонение</span><Badge variant="danger">LLM_REJECTED_ALL</Badge></div>
                <div className="rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3"><span className="text-text-muted">причина — </span><span className="font-medium text-text-strong">недостаточно связный итог</span></div>
              </div>
            </SideCard>

            <SideCard title="Связи">
              <div className="rounded-[18px] border border-border-subtle bg-bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <span className="grid h-12 w-12 place-items-center rounded-full bg-brand-blue-soft text-brand-blue"><UsersThree size={21} /></span>
                  <span className="h-px flex-1 bg-border-subtle" />
                  <span className="grid h-12 w-12 place-items-center rounded-full bg-warning-soft text-warning"><Network size={21} /></span>
                  <span className="h-px flex-1 bg-border-subtle" />
                  <span className="grid h-12 w-12 place-items-center rounded-full bg-bg-app text-text-weak"><FileText size={21} /></span>
                </div>
                <div className="mt-2 grid grid-cols-3 text-center text-xs text-text-muted">
                  <span>сообщения</span>
                  <span>кластер</span>
                  <span>материал</span>
                </div>
              </div>
              <div className="mt-4 grid gap-2 text-sm">
                <div className="flex justify-between"><span className="text-text-muted">source messages</span><span className="font-semibold text-text-strong">13</span></div>
                <div className="flex justify-between"><span className="text-text-muted">provider calls</span><span className="font-semibold text-text-strong">1</span></div>
                <div className="flex justify-between"><span className="text-text-muted">material</span><span className="font-semibold text-text-strong">не создан</span></div>
                <Link to="/materials/10" className="mt-2 inline-flex items-start gap-2 rounded-[16px] bg-brand-blue-soft px-3 py-2 text-sm font-semibold text-brand-blue hover:text-brand-blue-hover">
                  Мини-гайд по проверке OpenAI-compatible API в Cursor
                  <LinkSimple size={15} className="mt-0.5 shrink-0" />
                </Link>
              </div>
            </SideCard>

            <SideCard title="Следующий шаг">
              <div className="grid gap-3 text-sm">
                <div className="flex items-center justify-between gap-3"><span className="text-text-muted">suggestion</span><Badge className="bg-brand-blue-soft text-brand-blue">DISCUSSION_SEGMENT</Badge></div>
                <div className="rounded-[16px] border border-border-subtle bg-bg-card px-4 py-3 leading-6 text-text-default">
                  <span className="text-text-muted">reason — </span>цепочка полезна вместе, но слабая как одиночные сообщения
                </div>
                <Button variant="primary" className="mt-1 w-full rounded-[14px]">Открыть связанные сообщения</Button>
              </div>
            </SideCard>
          </aside>
        </main>
      </div>
    </div>
  );
}
