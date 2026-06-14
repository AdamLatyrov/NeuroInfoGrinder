import type { PipelineStageStatus } from "@/shared/types";
import { StatusBadge } from "./status-badge";
import { cn } from "@/lib/utils";

interface PipelineStageCardProps {
  stage: PipelineStageStatus;
}

function formatMs(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export function PipelineStageCard({ stage }: PipelineStageCardProps) {
  return (
    <div className="rounded-xl border border-border-subtle bg-bg-card p-4 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold text-text-strong">{stage.stage}</span>
        <StatusBadge status={stage.status} />
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <span className="text-text-muted">Задач</span>
        <span className="text-text-strong font-medium text-right">{stage.tasksCount}</span>
        <span className="text-text-muted">Ср. время</span>
        <span className="text-text-strong font-medium font-mono-value text-right">{formatMs(stage.avgTimeMs)}</span>
        <span className="text-text-muted">Ошибок</span>
        <span className={cn("font-medium text-right", stage.errorsCount > 0 ? "text-danger" : "text-text-strong")}>
          {stage.errorsCount}
        </span>
      </div>
    </div>
  );
}
