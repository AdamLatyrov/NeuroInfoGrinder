import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import type { GuideStatus, AccountStatus, ProviderStatus, MessageStatus, PipelineStageStatus as PipelineStageEnum } from "@/shared/types";

type StatusVariant = "default" | "success" | "warning" | "danger" | "outline";

interface StatusBadgeProps {
  status: string;
  className?: string;
}

const statusMap: Record<string, StatusVariant> = {
  // Account statuses
  Online: "success",
  "Waiting code": "warning",
  "Waiting password": "warning",
  Disconnected: "danger",
  Error: "danger",
  Disabled: "outline",

  // Group access
  Healthy: "success",
  Warning: "warning",
  "No account": "outline",

  // Guide statuses
  New: "default",
  Processing: "default",
  "Needs review": "warning",
  Approved: "success",
  Published: "success",
  Failed: "danger",
  Rejected: "danger",

  // Provider statuses
  // (reuses Healthy/Warning/Error/Disabled)

  // Message statuses
  Unprocessed: "outline",
  Classified: "default",
  "Sent to LLM": "warning",
  "Guide generated": "success",

  // Pipeline stage health
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const variant = statusMap[status] ?? "outline";
  return (
    <Badge variant={variant} dot className={cn("whitespace-nowrap", className)}>
      {status}
    </Badge>
  );
}
