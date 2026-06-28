import type { PipelineLiveEvent, PipelineLiveSummary } from "@/shared/api/pipelineApi";

const PULSE_EVENTS = new Set([
  "BATCH_FLUSHED",
  "RUN_CREATED",
  "RUN_STARTED",
  "STAGE_STARTED",
  "STAGE_COMPLETED",
  "RUN_COMPLETED",
  "MATERIAL_CREATED",
]);

interface PipelineRouteProps {
  summary?: PipelineLiveSummary;
  events: PipelineLiveEvent[];
}

export function PipelineRoute({ summary, events }: PipelineRouteProps) {
  const activeEvent = events.find((event) => PULSE_EVENTS.has(event.type));
  const hasActiveWork = (summary?.liveQueue ?? 0) > 0 || (summary?.runningRuns ?? 0) > 0 || (summary?.pendingRuns ?? 0) > 0;
  const showPulse = Boolean(activeEvent && hasActiveWork);
  const path = "M 8 62 H 49 C 53 62 56 66 56 70 V 147 C 56 151 53 155 49 155 H 8";

  return (
    <svg className="pointer-events-none absolute inset-x-6 top-16 z-0 hidden h-[260px] xl:block" viewBox="0 0 64 220" preserveAspectRatio="none" aria-hidden="true">
      <path d="M 0 16 H 64" stroke="var(--ui-border-default)" strokeWidth="1.6" strokeLinecap="round" strokeDasharray="5 8" opacity="0.35" />
      <path d={path} fill="none" stroke="var(--ui-warning)" strokeWidth="1.9" strokeLinecap="round" strokeDasharray="5 8" opacity="0.35" />
      <path d="M 0 186 H 56" stroke="var(--ui-border-default)" strokeWidth="1.6" strokeLinecap="round" strokeDasharray="5 8" opacity="0.35" />
      {showPulse ? (
        <circle r="1.5" fill="var(--ui-warning)" opacity="0.92">
          <animateMotion dur="2.6s" repeatCount="1" fill="freeze" path={path} />
        </circle>
      ) : null}
    </svg>
  );
}
