import type { KpiMetric } from "@/shared/types";
import { TrendUp, TrendDown, Minus } from "@phosphor-icons/react";
import { cn } from "@/lib/utils";

interface KpiRibbonProps {
  metrics: KpiMetric[];
  className?: string;
}

export function KpiRibbon({ metrics, className }: KpiRibbonProps) {
  return (
    <div className={cn("grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3", className)}>
      {metrics.map((m) => (
        <div
          key={m.label}
          className="rounded-xl border border-border-subtle bg-bg-card px-3 py-2.5 flex flex-col"
        >
          <span className="text-xs text-text-muted font-medium truncate">{m.label}</span>
          <div className="flex items-baseline gap-1.5 mt-0.5">
            <span className="text-xl font-bold text-text-strong leading-tight">{m.value}</span>
            {m.change && (
              <span
                className={cn(
                  "text-xs font-medium flex items-center gap-0.5",
                  m.trend === "up" && "text-success",
                  m.trend === "down" && "text-danger",
                  m.trend === "flat" && "text-text-weak"
                )}
              >
                {m.trend === "up" && <TrendUp size={12} weight="regular" />}
                {m.trend === "down" && <TrendDown size={12} weight="regular" />}
                {m.trend === "flat" && <Minus size={12} weight="regular" />}
                {m.change}
              </span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
