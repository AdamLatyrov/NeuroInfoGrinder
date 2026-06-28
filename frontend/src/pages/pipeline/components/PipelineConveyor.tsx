import { PipelineStageCard } from "./PipelineStageCard";
import type { PipelineStageSummary } from "@/shared/api/pipelineApi";

interface PipelineConveyorProps {
  stages: PipelineStageSummary[];
  loading: boolean;
  error: boolean;
  onStageOpen: (stage: PipelineStageSummary) => void;
}

export function PipelineConveyor({ stages, loading, error, onStageOpen }: PipelineConveyorProps) {
  return (
    <section aria-labelledby="pipeline-conveyor-title">
      <h1 id="pipeline-conveyor-title" className="text-3xl font-extrabold tracking-tight text-text-strong">Конвейер</h1>
      <p className="mt-1 text-sm text-text-muted">13 этапов обработки Telegram-сообщений</p>
      {error ? <p className="mt-4 rounded-2xl border border-danger/30 bg-danger/10 p-3 text-sm text-danger">Backend API недоступен</p> : null}
      {stages.length === 0 && !loading ? <p className="mt-4 rounded-2xl border border-border-subtle bg-bg-card p-4 text-sm text-text-muted">Stage данные пока не получены.</p> : null}
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
        {stages.slice(0, 13).map((stage) => <PipelineStageCard key={stage.id} stage={stage} onOpen={onStageOpen} />)}
      </div>
    </section>
  );
}
