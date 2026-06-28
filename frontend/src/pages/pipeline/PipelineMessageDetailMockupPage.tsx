import type { ElementType, ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ArrowSquareOut,
  CaretDown,
  CaretLeft,
  Check,
  Diamond,
  FileCode,
  Prohibit,
  X,
} from "@phosphor-icons/react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type TimelineTone = "processed" | "completed" | "candidate" | "rejected" | "skipped";

interface TimelineStage {
  name: string;
  status: string;
  duration: string;
  input: string;
  output: string;
  tone: TimelineTone;
  expanded?: boolean;
}

const statusPills = [
  "Vibemode",
  "MessageText",
  "ENABLED_PROCESSABLE",
  "SINGLE_MESSAGE_MATERIAL_CANDIDATE",
];

const metadataRows = [
  { label: "Тема", value: "API / Cursor" },
  { label: "telegram_chat_id", value: "-10018273645", mono: true },
  { label: "telegram_message_id", value: "947192", mono: true },
  { label: "message_date", value: "22.05.2025 12:41:22" },
  { label: "ingested_at", value: "22.05.2025 12:41:24" },
];

const stages: TimelineStage[] = [
  { name: "Telegram ingest", status: "PROCESSED", duration: "120ms", input: "Telegram Update", output: "Raw Message", tone: "processed" },
  { name: "Text extraction", status: "PROCESSED", duration: "85ms", input: "Raw Message", output: "Message Text", tone: "processed" },
  { name: "Rules / signals", status: "PROCESSED", duration: "35ms", input: "Message Text", output: "Signals", tone: "processed" },
  { name: "Intake", status: "PROCESSED", duration: "210ms", input: "Signals", output: "Intake Record", tone: "processed" },
  { name: "Queue", status: "PROCESSED", duration: "15ms", input: "Intake Record", output: "Queue Job", tone: "processed" },
  { name: "Run", status: "COMPLETED", duration: "1.2s", input: "Queue Job", output: "Run Result", tone: "completed" },
  { name: "Embeddings / BGE-M3", status: "PROCESSED", duration: "820ms", input: "Message Text", output: "Embeddings", tone: "processed" },
  { name: "Single-message detection", status: "CANDIDATE", duration: "210ms", input: "Embeddings", output: "Candidate", tone: "candidate" },
  { name: "LLM Judge", status: "REJECTED", duration: "3.2s", input: "Candidate", output: "Judge Result", tone: "rejected", expanded: true },
  { name: "Material generation", status: "SKIPPED", duration: "—", input: "Judge Result", output: "—", tone: "skipped" },
];

const toneClasses: Record<TimelineTone, { chip: string; dot: string; icon: ElementType }> = {
  processed: { chip: "bg-success-soft text-success", dot: "border-success bg-success-soft text-success", icon: Check },
  completed: { chip: "bg-brand-blue-soft text-brand-blue", dot: "border-brand-blue bg-brand-blue-soft text-brand-blue", icon: Check },
  candidate: { chip: "bg-warning-soft text-warning", dot: "border-warning bg-warning-soft text-warning", icon: Diamond },
  rejected: { chip: "bg-danger-soft text-danger", dot: "border-danger bg-danger-soft text-danger", icon: X },
  skipped: { chip: "bg-bg-app text-text-muted", dot: "border-border-default bg-bg-card text-text-weak", icon: Prohibit },
};

function InfoRow({ label, value, mono }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div className="grid grid-cols-[150px_1fr] gap-4 border-b border-border-subtle/70 py-2.5 last:border-b-0">
      <div className="text-sm text-text-muted">{label}</div>
      <div className={cn("min-w-0 text-sm font-medium text-text-strong", mono && "font-mono text-xs")}>{value}</div>
    </div>
  );
}

function SmallPanel({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-[18px] border border-border-subtle bg-bg-elevated shadow-[0_8px_22px_rgba(31,36,48,0.035)]">
      <button className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-semibold text-text-strong">
        {title}
        <CaretDown size={15} className="text-text-weak" />
      </button>
      {children && <div className="border-t border-border-subtle px-4 py-3">{children}</div>}
    </div>
  );
}

function TimelineRow({ stage, isLast }: { stage: TimelineStage; isLast: boolean }) {
  const tone = toneClasses[stage.tone];
  const Icon = tone.icon;

  return (
    <div className="relative grid gap-4 pl-12">
      {!isLast && <div className="absolute left-[18px] top-10 h-[calc(100%-8px)] w-px bg-border-subtle" />}
      <div className={cn("absolute left-0 top-4 grid h-9 w-9 place-items-center rounded-full border text-sm", tone.dot)}>
        <Icon size={16} weight="bold" />
      </div>

      <div
        className={cn(
          "rounded-[22px] border border-border-subtle bg-bg-elevated p-4 shadow-[0_10px_30px_rgba(31,36,48,0.04)]",
          stage.expanded && "border-danger/35 bg-danger-soft/35 shadow-[0_16px_42px_rgba(198,83,91,0.10)]"
        )}
      >
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <h3 className="text-base font-semibold text-text-strong">{stage.name}</h3>
              <span className={cn("rounded-full px-3 py-1 text-xs font-semibold", tone.chip)}>{stage.status}</span>
              <span className="rounded-full border border-border-subtle bg-bg-card px-2.5 py-1 text-xs font-medium text-text-muted">{stage.duration}</span>
            </div>
            <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-text-muted">
              <span>Вход: <span className="font-medium text-text-strong">{stage.input}</span></span>
              <span>Выход: <span className="font-medium text-text-strong">{stage.output}</span></span>
            </div>
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            <Button variant="outline" size="sm" className="rounded-full">
              <FileCode size={15} />
              Показать JSON
            </Button>
            {stage.expanded && <Button variant="secondary" size="sm" className="rounded-full">Скрыть детали</Button>}
          </div>
        </div>

        {stage.expanded && (
          <div className="mt-4 grid gap-4 xl:grid-cols-[360px_1fr]">
            <div className="rounded-[18px] border border-border-subtle bg-bg-card p-4">
              <InfoRow label="Провайдер" value="ModelHub" />
              <InfoRow label="Модель" value="gpt-5.5" mono />
              <InfoRow label="Длительность" value="3.2s" />
              <InfoRow label="Версия промпта" value="LLM_CLUSTER_JUDGE_AND_ROUTING" mono />
            </div>
            <div className="grid gap-3">
              <SmallPanel title="Запрос (prompt)" />
              <SmallPanel title="Ответ (response)" />
              <SmallPanel title="Распарсенный результат">
                <div className="grid gap-2 text-sm">
                  <InfoRow label="accepted" value="false" mono />
                  <InfoRow label="reason" value={<span className="rounded-full bg-danger-soft px-3 py-1 text-xs font-semibold text-danger">not enough standalone value</span>} />
                  <InfoRow label="recommendation" value={<span className="rounded-full bg-warning-soft px-3 py-1 text-xs font-semibold text-warning">needs discussion context</span>} />
                </div>
              </SmallPanel>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function PipelineMessageDetailMockupPage() {
  return (
    <div className="rounded-[30px] border border-border-subtle bg-bg-card p-4 shadow-[0_22px_70px_rgba(31,36,48,0.08)] sm:p-6 xl:p-7">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div className="min-w-0">
            <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2 rounded-full text-text-muted hover:text-text-strong">
              <Link to="/pipeline/stages/embeddings-bge-m3">
                <CaretLeft size={16} />
                Назад к этапу
              </Link>
            </Button>
            <h1 className="text-2xl font-semibold tracking-[-0.03em] text-text-strong sm:text-[32px]">Сообщение raw_id: 4743</h1>
            <p className="mt-2 text-sm text-text-muted sm:text-[15px]">Полный путь сообщения через pipeline</p>
            <div className="mt-4 flex flex-wrap gap-2">
              {statusPills.map((pill) => <Badge key={pill} className="bg-brand-blue-soft text-brand-blue">{pill}</Badge>)}
              <Badge variant="danger">LLM rejected</Badge>
            </div>
          </div>
          <Button variant="primary" className="h-11 rounded-[14px] px-5 shadow-[0_12px_26px_rgba(185,133,23,0.20)]">
            Открыть в конвейере
            <ArrowSquareOut size={17} />
          </Button>
        </div>

        <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_390px]">
          <div className="rounded-[26px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_14px_38px_rgba(31,36,48,0.05)]">
            <h2 className="text-lg font-semibold text-text-strong">Исходное сообщение</h2>
            <div className="mt-4 grid gap-x-8 lg:grid-cols-2">
              <InfoRow label="Чат" value={<span className="inline-flex items-center gap-2"><span className="grid h-7 w-7 place-items-center rounded-full bg-brand-blue-soft text-xs font-bold text-brand-blue">V</span>Vibemode</span>} />
              {metadataRows.map((row) => <InfoRow key={row.label} label={row.label} value={row.value} mono={row.mono} />)}
            </div>
            <div className="mt-5">
              <div className="mb-2 text-sm font-semibold text-text-strong">Текст сообщения</div>
              <div className="rounded-[20px] border border-border-subtle bg-[#fffaf0] p-4 text-[15px] leading-7 text-text-strong shadow-inner">
                попробуй api прямой, проверь base_url, bearer token и модель...
              </div>
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              <Button variant="primary" className="rounded-[14px]">Открыть в чате</Button>
              <Button variant="outline" className="rounded-[14px]" disabled>Telegram-ссылка недоступна</Button>
            </div>
          </div>

          <div className="grid gap-4">
            <div className="rounded-[24px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_12px_34px_rgba(31,36,48,0.045)]">
              <h2 className="text-base font-semibold text-text-strong">Текущее состояние</h2>
              <div className="mt-3 grid gap-2">
                <InfoRow label="Текущий этап" value={<span className="rounded-full bg-brand-blue-soft px-3 py-1 text-xs font-semibold text-brand-blue">LLM Judge</span>} />
                <InfoRow label="Статус" value={<span className="rounded-full bg-danger-soft px-3 py-1 text-xs font-semibold text-danger">Rejected</span>} />
                <InfoRow label="Причина" value={<span className="rounded-full bg-danger-soft px-3 py-1 text-xs font-semibold text-danger">LLM_REJECTED_ALL</span>} />
              </div>
            </div>
            <div className="rounded-[24px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_12px_34px_rgba(31,36,48,0.045)]">
              <h2 className="text-base font-semibold text-text-strong">Оценка</h2>
              <div className="mt-3 flex items-center gap-3">
                <span className="text-[42px] font-semibold leading-none tracking-[-0.04em] text-text-strong">0.91</span>
                <span className="h-2.5 w-2.5 rounded-full bg-warning" />
                <span className="text-sm text-text-muted">Score</span>
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <Badge variant="warning">API_OR_STATUS_SIGNAL</Badge>
                <Badge variant="warning">SOLUTION_SIGNAL</Badge>
                <Badge variant="warning">HARD_SIGNAL</Badge>
              </div>
            </div>
            <div className="rounded-[24px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_12px_34px_rgba(31,36,48,0.045)]">
              <h2 className="text-base font-semibold text-text-strong">Связи</h2>
              <div className="mt-3 grid gap-2">
                <InfoRow label="Cluster" value="—" />
                <InfoRow label="Material" value="не создан" />
                <InfoRow label="Provider calls" value="1" mono />
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-4 xl:grid-cols-3">
          <DecisionCard title="LLM Provider" why="Почему важно: provider/model определяет качество judge, стоимость и вероятность 502/timeout.">
            <InfoRow label="Provider / model" value="ModelHub / gpt-5.5" />
            <InfoRow label="Provider calls" value="1" mono />
            <InfoRow label="Success / failure" value="success" />
            <InfoRow label="Duration" value="2.4s" mono />
            <a href="#" className="mt-3 inline-flex text-sm font-semibold text-brand-blue">Открыть provider call detail</a>
          </DecisionCard>
          <DecisionCard title="Качество" why="Почему важно: score влияет на переход в LLM Judge, но не равен качеству будущего материала.">
            <InfoRow label="Candidate score" value="0.91" mono />
            <InfoRow label="Threshold" value="0.55" mono />
            <InfoRow label="Pipeline effect" value="дошло до LLM Judge" />
            <InfoRow label="Material quality" value="не подтверждено, LLM rejected" />
          </DecisionCard>
          <DecisionCard title="Диагностика источника" why="Почему важно: показывает, хватает ли source context для материала без домыслов.">
            <InfoRow label="Источник" value="single message" />
            <InfoRow label="Source count" value="1" mono />
            <InfoRow label="Reliability" value="medium: chat source only" />
            <InfoRow label="Text present" value="yes" />
            <InfoRow label="Telegram/app link" value="app link ok, telegram link missing" />
            <InfoRow label="Duplicate/source hash" value="not duplicated" />
          </DecisionCard>
        </section>

        <section className="rounded-[28px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_18px_50px_rgba(31,36,48,0.055)]">
          <div className="mb-5 flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-xl font-semibold tracking-[-0.02em] text-text-strong">Путь сообщения</h2>
              <p className="mt-1 text-sm text-text-muted">От Telegram ingest до LLM Judge и пропуска Material generation.</p>
            </div>
            <Badge variant="outline" className="w-fit bg-bg-card">raw_id 4743</Badge>
          </div>
          <div className="grid gap-4">
            {stages.map((stage, index) => <TimelineRow key={stage.name} stage={stage} isLast={index === stages.length - 1} />)}
          </div>
        </section>
      </div>
    </div>
  );
}

function DecisionCard({ title, why, children }: { title: string; why: string; children: ReactNode }) {
  return <div className="rounded-[24px] border border-border-subtle bg-bg-elevated p-5 shadow-[0_12px_34px_rgba(31,36,48,0.045)]"><h2 className="text-base font-semibold text-text-strong">{title}</h2><p className="mt-2 rounded-2xl bg-brand-blue-soft px-3 py-2 text-xs font-semibold leading-5 text-brand-blue">{why}</p><div className="mt-3 grid gap-2">{children}</div></div>;
}
