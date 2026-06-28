import { cn } from "@/lib/utils";
import type { PipelineStageSummary } from "@/shared/api/pipelineApi";

interface PipelineStageCardProps {
  stage: PipelineStageSummary;
  onOpen: (stage: PipelineStageSummary) => void;
}

export function PipelineStageCard({ stage, onOpen }: PipelineStageCardProps) {
  const state = stageState(stage);
  const tone = stageTone(state);
  const name = STAGE_NAMES[stage.ordinal] ?? stage.name;

  return (
    <button
      type="button"
      onClick={() => onOpen(stage)}
      className={cn(
        "min-h-[124px] rounded-2xl border bg-bg-card p-4 text-left shadow-sm",
        "hover:border-brand-blue/60",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue focus-visible:ring-offset-2 focus-visible:ring-offset-bg-app",
        tone.card
      )}
      aria-label={`Открыть details стадии ${stage.ordinal}: ${name}. Состояние: ${state}`}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-border-subtle bg-bg-elevated font-mono-value text-sm font-bold text-text-strong">
          {stage.ordinal}
        </span>
        <span className={cn("mt-1 h-2.5 w-2.5 shrink-0 rounded-full", tone.dot)} aria-hidden="true" />
      </div>
      <h3 className="mt-4 line-clamp-2 text-sm font-bold leading-snug text-text-strong">{name}</h3>
      <div className="mt-3 text-xs font-semibold text-text-muted">
        {state}
      </div>
    </button>
  );
}

const STAGE_NAMES: Record<number, string> = {
  1: "Сбор из Telegram / Форумов",
  2: "DB-cache",
  3: "Нормализация",
  4: "Очистка",
  5: "Дедупликация",
  6: "Rule-сигналы",
  7: "Bootstrap-классификация",
  8: "Embeddings",
  9: "Кластеризация",
  10: "Single-message детекция",
  11: "LLM Judge",
  12: "Генерация материалов",
  13: "Публикация в Материалы",
};

export function stageState(stage: PipelineStageSummary) {
  if (stage.status === "active") return "Running";
  if (stage.status === "completed") return stage.skipped > 0 && stage.processed === 0 ? "Skipped" : "Done";
  if (stage.status === "error") return "Error";
  if (stage.warning) return "Blocked";
  return "Idle";
}

function stageTone(status: string) {
  if (status === "Running") {
    return { card: "border-warning/60", dot: "bg-warning" };
  }
  if (status === "Done") {
    return { card: "border-success/35", dot: "bg-success" };
  }
  if (status === "Error") {
    return { card: "border-danger/55", dot: "bg-danger" };
  }
  if (status === "Blocked") {
    return { card: "border-warning/45", dot: "bg-warning" };
  }
  if (status === "Skipped") {
    return { card: "border-border-subtle", dot: "bg-text-muted" };
  }
  return { card: "border-border-subtle", dot: "bg-text-weak" };
}
