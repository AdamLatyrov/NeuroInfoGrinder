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
  Онлайн: "success",
  "Waiting code": "warning",
  "Ожидает код": "warning",
  "Waiting password": "warning",
  "Ожидает пароль": "warning",
  Disconnected: "danger",
  Error: "danger",
  Ошибка: "danger",
  Disabled: "outline",
  Отключён: "outline",

  // Group access / provider health
  Healthy: "success",
  Исправен: "success",
  Warning: "warning",
  Предупреждение: "warning",
  "No account": "outline",
  Активен: "success",

  // Guide statuses
  New: "default",
  Новый: "default",
  Draft: "outline",
  Черновик: "outline",
  Processing: "default",
  "В работе": "default",
  "Needs review": "warning",
  "Нужна проверка": "warning",
  Approved: "success",
  Одобрен: "success",
  Published: "success",
  Опубликован: "success",
  Failed: "danger",
  Rejected: "danger",
  Отклонён: "danger",

  // Message statuses
  Unprocessed: "outline",
  Classified: "default",
  "Sent to LLM": "warning",
  "Guide generated": "success",
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const variant = statusMap[status] ?? "outline";
  return (
    <Badge variant={variant} dot className={cn("min-w-[92px] whitespace-nowrap", className)}>
      {status}
    </Badge>
  );
}
