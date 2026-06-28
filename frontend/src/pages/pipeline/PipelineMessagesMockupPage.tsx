import {
  ArrowClockwise,
  CaretDown,
  ChartLineUp,
  CheckCircle,
  Clock,
  FolderOpen,
  FunnelSimple,
  MagnifyingGlass,
  Package,
  Pulse,
  TelegramLogo,
  XCircle,
} from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type Tone = "blue" | "cyan" | "green" | "yellow" | "orange" | "purple" | "teal" | "red" | "gray";
type StatTone = "passed" | "accepted" | "rejected" | "pending" | "failed" | "skipped" | "candidate";

interface Stage {
  n: number;
  title: string;
  input: string;
  value: string;
  pct: string;
  tone: Tone;
  selected?: boolean;
  stats: Array<[string, string, StatTone]>;
}

const filters = [
  ["Период", "Сегодня 00:00 - сейчас"],
  ["Чат", "Все чаты"],
  ["Этап", "Все этапы"],
  ["Статус", "Все статусы"],
  ["Причина отклонения", "Все причины"],
  ["Провайдер / Модель", "Все провайдеры"],
] as const;

const summary = [
  { label: "Всего сообщений", value: "1,248", hint: "Telegram/raw за период", icon: TelegramLogo, tone: "blue" as const },
  { label: "До материалов", value: "9", hint: "создано материалов", icon: FolderOpen, tone: "green" as const },
  { label: "Conversion", value: "0.72%", hint: "raw -> material", icon: ChartLineUp, tone: "purple" as const },
  { label: "Rejected", value: "416", hint: "явные отклонения", icon: XCircle, tone: "red" as const },
];

const losses = ["-146", "-56", "-26", "-682", "-383", "-6", "-25", "-2", "-1"];

const stages: Stage[] = [
  { n: 1, title: "Raw messages", input: "1,248", value: "1,248", pct: "100%", tone: "blue", stats: [["Passed", "1,248", "passed"]] },
  { n: 2, title: "Intake", input: "1,248", value: "1,102", pct: "88.3%", tone: "cyan", stats: [["Passed", "1,102", "passed"], ["Pending", "146", "pending"]] },
  { n: 3, title: "Queue", input: "1,102", value: "1,046", pct: "94.9%", tone: "green", stats: [["Passed", "1,046", "passed"], ["Rejected", "22", "rejected"], ["Failed", "2", "failed"], ["Pending", "56", "pending"]] },
  { n: 4, title: "Run", input: "1,046", value: "1,020", pct: "97.5%", tone: "yellow", stats: [["Passed", "1,020", "passed"], ["Rejected", "18", "rejected"], ["Failed", "8", "failed"]] },
  { n: 5, title: "Embeddings / BGE-M3", input: "1,102", value: "420", pct: "38.1%", tone: "orange", stats: [["Passed", "420", "passed"], ["Skipped", "682", "skipped"]] },
  { n: 6, title: "Single-message", input: "420", value: "37", pct: "8.8%", tone: "purple", stats: [["Candidates", "37", "candidate"], ["Rejected", "383", "rejected"]] },
  { n: 7, title: "Clustering", input: "37", value: "31", pct: "83.8%", tone: "teal", stats: [["Passed", "31", "passed"], ["Rejected", "6", "rejected"], ["Candidates", "31", "candidate"]] },
  { n: 8, title: "LLM Judge", input: "37", value: "12", pct: "32.4%", tone: "blue", selected: true, stats: [["Accepted", "12", "accepted"], ["Rejected", "25", "rejected"]] },
  { n: 9, title: "Material generation", input: "12", value: "10", pct: "83.3%", tone: "orange", stats: [["Accepted", "10", "accepted"], ["Rejected", "2", "rejected"]] },
  { n: 10, title: "Material created", input: "10", value: "9", pct: "90.0%", tone: "teal", stats: [["Accepted", "9", "accepted"]] },
];

const rows = [
  ["4743", "Vibemode", "попробуй api прямой...", "rejected", "LLM_REJECTED_ALL", "0.91", "-"],
  ["2218", "DevChat", "как отследить chain...", "rejected", "NO_MATERIAL_CANDIDATES", "0.82", "-"],
  ["2902", "Паша", "Мини-гайд по проверке API...", "accepted", "-", "0.96", "MAT-2025-05-29-0123"],
  ["1987", "AI Новости", "OpenAI o3 и o4-mini...", "accepted", "-", "0.89", "MAT-2025-05-29-0117"],
] as const;

export function PipelineMessagesMockupPage() {
  return (
    <div className="mx-auto max-w-[calc(100vw-7.5rem)] rounded-[28px] border border-border-subtle bg-bg-card p-4 shadow-[0_18px_58px_rgba(54,44,29,0.10)] lg:p-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="mb-2 inline-flex items-center gap-1.5 rounded-full border border-border-subtle bg-bg-elevated px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-text-muted">
            <Pulse size={14} className="text-brand-blue" /> Live analytics
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-text-strong lg:text-[30px]">Конвейер сообщений</h1>
          <p className="mt-1 text-sm leading-5 text-text-muted">Визуальная воронка прохождения сообщений по этапам</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="primary" className="gap-2 shadow-[0_12px_24px_rgba(223,180,69,0.28)]"><ArrowClockwise size={17} weight="bold" />Обновить</Button>
          <Button variant="outline" size="icon" aria-label="Обновить"><ArrowClockwise size={17} /></Button>
        </div>
      </header>

      <section className="mt-4 grid grid-cols-6 gap-2" aria-label="Фильтры конвейера">
        {filters.map(([label, value]) => <FilterCard key={label} label={label} value={value} />)}
      </section>

      <section className="mt-3 grid grid-cols-4 gap-2.5" aria-label="Сводные метрики">
        {summary.map((item) => <SummaryCard key={item.label} {...item} />)}
      </section>

      <section className="mt-3 rounded-[24px] border border-border-subtle bg-[#fffaf1] p-3 shadow-inner shadow-white/60">
        <div className="mb-2.5 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-extrabold text-text-strong">Pipeline funnel</h2>
            <p className="mt-0.5 text-xs text-text-muted">Потери между этапами и текущая точка анализа</p>
          </div>
          <div className="rounded-full border border-border-subtle bg-bg-elevated px-2.5 py-1 text-[11px] font-semibold text-text-muted">selected: <span className="text-brand-blue">LLM Judge</span></div>
        </div>
        <div className="mb-2 flex w-fit items-center gap-2 rounded-xl border border-border-subtle bg-bg-elevated px-3 py-2 text-xs font-semibold text-text-muted">
          <MagnifyingGlass size={15} className="text-brand-blue" />Нажмите на этап, чтобы посмотреть сообщения
        </div>
        <FunnelDiagram />
      </section>

      <StageMessagesTable />
    </div>
  );
}

function FilterCard({ label, value }: { label: string; value: string }) {
  return (
    <button type="button" className="group flex h-[46px] min-w-0 items-center justify-between gap-2 rounded-xl border border-border-subtle bg-bg-elevated px-3 text-left shadow-[0_5px_14px_rgba(54,44,29,0.035)] transition hover:border-brand-blue/35 hover:bg-[#fffbf3] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue">
      <div className="min-w-0">
        <div className="text-[9px] font-bold uppercase tracking-[0.12em] text-text-weak">{label}</div>
        <div className="mt-0.5 truncate text-xs font-semibold text-text-strong">{value}</div>
      </div>
      <span className="flex shrink-0 items-center gap-1 text-text-weak transition group-hover:text-brand-blue">
        <FunnelSimple size={12} />
        <CaretDown size={12} />
      </span>
    </button>
  );
}

function SummaryCard({ label, value, hint, icon: Icon, tone }: typeof summary[number]) {
  return (
    <article className="rounded-[18px] border border-border-subtle bg-bg-elevated p-3 shadow-[0_10px_26px_rgba(54,44,29,0.055)]">
      <div className="flex items-start justify-between gap-2">
        <div><p className="text-[10px] font-bold uppercase tracking-[0.12em] text-text-weak">{label}</p><p className="mt-1 font-mono-value text-2xl font-extrabold text-text-strong">{value}</p><p className="mt-0.5 text-[11px] text-text-muted">{hint}</p></div>
        <span className={cn("grid size-9 place-items-center rounded-xl opacity-90", iconTone(tone))}><Icon size={18} weight="duotone" /></span>
      </div>
    </article>
  );
}

function FunnelDiagram() {
  return (
    <div className="relative px-1 pb-1 pt-0">
      <div className="pointer-events-none absolute inset-x-[calc(5%+4px)] top-[43px] z-30 grid grid-cols-9 gap-0">
        {losses.map((loss, index) => (
          <div key={`${loss}-${index}`} className="relative flex justify-center">
            <span className="z-10 rounded-full border border-danger/20 bg-danger-soft px-2 py-0.5 text-[10px] font-extrabold text-danger shadow-[0_4px_10px_rgba(198,83,91,0.10)]">{loss} ↓</span>
            <span className="absolute top-5 h-[112px] border-l border-dotted border-danger/30" />
          </div>
        ))}
      </div>
      <svg className="relative z-0 h-[44px] w-full overflow-visible" viewBox="0 0 1600 44" preserveAspectRatio="none" role="img" aria-label="Сужающаяся лента pipeline">
        <defs>
          <linearGradient id="pipelineGradient" x1="0" x2="1" y1="0" y2="0"><stop offset="0%" stopColor="#7db6ff" /><stop offset="14%" stopColor="#6ed6e4" /><stop offset="31%" stopColor="#87d69b" /><stop offset="48%" stopColor="#f1c75b" /><stop offset="64%" stopColor="#f0a06a" /><stop offset="81%" stopColor="#a58cf0" /><stop offset="100%" stopColor="#62c7b3" /></linearGradient>
          <filter id="softShadow" x="-5%" y="-40%" width="110%" height="180%"><feDropShadow dx="0" dy="7" stdDeviation="7" floodColor="#75624a" floodOpacity="0.15" /></filter>
        </defs>
        <path d="M12 12 C150 4 260 17 390 12 C520 7 628 17 760 15 C900 12 990 18 1110 18 C1265 18 1400 21 1588 25 L1588 34 C1410 34 1270 31 1115 33 C985 37 880 29 760 32 C625 37 505 29 392 33 C260 39 150 34 12 39 Z" fill="url(#pipelineGradient)" filter="url(#softShadow)" opacity="0.95" />
      </svg>
      <div className="relative z-20 -mt-1 grid grid-cols-10 gap-2">
        <div className="pointer-events-none absolute left-0 right-0 top-1/2 -z-10 h-px bg-border-subtle/80" />
        {stages.map((stage) => <StageCard key={stage.n} stage={stage} />)}
      </div>
    </div>
  );
}

function StageCard({ stage }: { stage: Stage }) {
  return (
    <button type="button" className={cn("group flex min-h-[132px] min-w-0 flex-col rounded-[15px] border bg-bg-elevated/95 p-2 text-left shadow-[0_6px_18px_rgba(54,44,29,0.045)] transition hover:-translate-y-0.5 hover:border-brand-blue/30 hover:shadow-[0_10px_24px_rgba(54,44,29,0.09)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue", stage.selected ? "border-brand-blue bg-brand-blue-soft/60 shadow-[0_0_0_3px_rgba(41,95,190,0.10),0_10px_26px_rgba(41,95,190,0.16)]" : "border-border-subtle") }>
      <div className="flex items-center justify-between gap-1"><span className={cn("grid size-6 place-items-center rounded-full text-[10px] font-extrabold", iconTone(stage.tone))}>{stage.n}</span>{stage.selected ? <span className="rounded-full bg-brand-blue-soft px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide text-brand-blue">sel</span> : null}</div>
      <h3 className="mt-1.5 min-h-[28px] text-[11px] font-extrabold leading-tight text-text-strong">{stage.title}</h3>
      <p className="mt-0.5 text-[10px] text-text-muted">Вход: <span className="font-semibold text-text-default">{stage.input}</span></p>
      <div className="mt-1.5 rounded-xl border border-border-subtle bg-[#fbf6ec] p-1.5"><div className="font-mono-value text-base font-extrabold leading-none text-text-strong">{stage.value}</div><div className="mt-0.5 text-[10px] font-semibold text-text-muted">{stage.pct}</div></div>
      <div className="mt-1.5 space-y-1">{stage.stats.map(([label, value, tone]) => <div key={`${label}-${value}`} className="flex items-center justify-between gap-1 text-[10px]"><span className="truncate text-text-muted">{label}</span><span className={cn("rounded-full px-1.5 py-0.5 font-bold leading-none", statTone(tone))}>{value}</span></div>)}</div>
    </button>
  );
}

function StageMessagesTable() {
  return (
    <section className="mt-3 rounded-[24px] border border-border-subtle bg-bg-elevated p-3 shadow-[0_12px_34px_rgba(54,44,29,0.075)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-lg font-extrabold text-text-strong">Сообщения этапа: LLM Judge</h2><div className="mt-2 flex flex-wrap gap-1.5"><InfoChip label="Вход" value="37" /><InfoChip label="Accepted" value="12" tone="green" /><InfoChip label="Rejected" value="25" tone="red" /><InfoChip label="Конверсия" value="32.4%" tone="blue" /></div></div>
        <div className="flex flex-wrap items-center justify-end gap-1.5"><span className="text-[10px] font-bold uppercase tracking-[0.10em] text-text-weak">Топ причин отклонений:</span><ReasonChip>LLM_REJECTED_ALL</ReasonChip><ReasonChip>NO_MATERIAL_CANDIDATES</ReasonChip><Button variant="outline" size="sm" className="h-8 rounded-xl">Открыть в таблице</Button></div>
      </div>
      <div className="mt-3 overflow-hidden rounded-xl border border-border-subtle"><table className="w-full table-fixed text-left text-xs"><thead className="bg-[#f6efe3] text-[10px] uppercase tracking-[0.10em] text-text-weak"><tr>{["raw_id", "чат", "preview", "status", "reason", "score", "material"].map((h) => <th key={h} className="px-3 py-2 font-bold first:w-[82px] nth-[2]:w-[120px]">{h}</th>)}</tr></thead><tbody>{rows.map(([rawId, chat, preview, status, reason, score, material]) => <tr key={rawId} className="border-t border-border-subtle bg-bg-elevated transition hover:bg-brand-blue-soft/35"><td className="px-3 py-2 font-mono-value font-bold text-text-strong">{rawId}</td><td className="px-3 py-2 font-semibold text-text-strong">{chat}</td><td className="truncate px-3 py-2 text-text-muted">{preview}</td><td className="px-3 py-2"><StatusBadge status={status} /></td><td className="px-3 py-2">{reason === "-" ? <span className="text-text-weak">-</span> : <ReasonChip>{reason}</ReasonChip>}</td><td className="px-3 py-2 font-mono-value font-bold text-text-strong">{score}</td><td className="truncate px-3 py-2">{material === "-" ? <span className="text-text-weak">-</span> : <a className="font-semibold text-brand-blue" href="#">{material}</a>}</td></tr>)}</tbody></table></div>
      <div className="mt-2.5 flex flex-wrap items-center justify-between gap-2 text-xs text-text-muted"><span>Показано 1–4 из 37 сообщений</span><div className="flex items-center gap-1.5"><Page active>1</Page><Page>2</Page><Page>3</Page><span className="px-1">...</span><Page>10</Page><span className="ml-1 rounded-lg border border-border-subtle bg-bg-card px-2.5 py-1.5">rows per page: <strong className="text-text-strong">10</strong></span></div></div>
    </section>
  );
}

function InfoChip({ label, value, tone = "gray" }: { label: string; value: string; tone?: "gray" | "green" | "red" | "blue" }) { return <span className={cn("rounded-full border px-2.5 py-1 text-[11px] font-bold", tone === "green" ? "border-success/20 bg-success-soft text-success" : tone === "red" ? "border-danger/20 bg-danger-soft text-danger" : tone === "blue" ? "border-brand-blue/20 bg-brand-blue-soft text-brand-blue" : "border-border-subtle bg-bg-card text-text-muted")}>{label}: <strong>{value}</strong></span>; }
function ReasonChip({ children }: { children: string }) { return <span className="rounded-full border border-danger/20 bg-danger-soft px-2 py-0.5 text-[10px] font-bold text-danger">{children}</span>; }
function StatusBadge({ status }: { status: string }) { const accepted = status === "accepted"; return <span className={cn("inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-bold", accepted ? "bg-success-soft text-success" : "bg-danger-soft text-danger")}><span className={cn("size-1.5 rounded-full", accepted ? "bg-success" : "bg-danger")} />{accepted ? "accepted" : "rejected"}</span>; }
function Page({ children, active }: { children: string; active?: boolean }) { return <button type="button" className={cn("grid size-7 place-items-center rounded-lg border text-xs font-bold", active ? "border-brand-blue bg-brand-blue-soft text-brand-blue" : "border-border-subtle bg-bg-card text-text-muted hover:text-text-strong")}>{children}</button>; }
function iconTone(tone: Tone) { return { blue: "bg-brand-blue-soft text-brand-blue", cyan: "bg-cyan-100 text-cyan-700", green: "bg-success-soft text-success", yellow: "bg-warning-soft text-warning", orange: "bg-orange-100 text-orange-700", purple: "bg-purple-100 text-purple-700", teal: "bg-teal-100 text-teal-700", red: "bg-danger-soft text-danger", gray: "bg-bg-card text-text-muted" }[tone]; }
function statTone(tone: StatTone) { return { passed: "bg-success-soft text-success", accepted: "bg-success-soft text-success", rejected: "bg-danger-soft text-danger", pending: "bg-warning-soft text-warning", failed: "bg-danger-soft text-danger", skipped: "bg-bg-card text-text-muted", candidate: "bg-brand-blue-soft text-brand-blue" }[tone]; }
