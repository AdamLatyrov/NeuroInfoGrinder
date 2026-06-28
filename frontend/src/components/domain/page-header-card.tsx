import { ArrowClockwise, DownloadSimple, Funnel, Plus, SpinnerGap } from "@phosphor-icons/react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface PageHeaderCardProps {
  title: string;
  description?: string;
  pipelineNote?: string;
  actions?: {
    primary?: {
      label: string;
      icon?: React.ReactNode;
      items?: { label: string; onClick?: () => void }[];
      onClick?: () => void;
    };
    onFilter?: () => void;
    onRefresh?: () => void;
    refreshPending?: boolean;
    onExport?: () => void;
  };
  className?: string;
  children?: React.ReactNode;
}

export function PageHeaderCard({
  title,
  description,
  pipelineNote,
  actions,
  className,
  children,
}: PageHeaderCardProps) {
  return (
    <div
      className={cn(
        "rounded-[24px] border border-border-subtle bg-bg-card px-4 py-4 shadow-[0_12px_30px_rgba(15,18,26,0.08)] sm:px-6 sm:py-5",
        className
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-xl font-bold leading-tight text-text-strong sm:text-2xl">{title}</h1>
          {description && <p className="mt-1 text-sm text-text-muted">{description}</p>}
          {pipelineNote && <p className="mt-1 font-mono-value text-xs text-text-weak">{pipelineNote}</p>}
        </div>
        {actions && (
          <div className="flex shrink-0 items-center gap-2">
            {actions.primary && (
              actions.primary.items ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="primary" size="sm" className="gap-1.5">
                      {actions.primary.icon ?? <Plus size={16} weight="regular" />}
                      {actions.primary.label}
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuLabel>Создать</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    {actions.primary.items.map((item, index) => (
                      <DropdownMenuItem key={index} onClick={item.onClick}>
                        {item.label}
                      </DropdownMenuItem>
                    ))}
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Button variant="primary" size="sm" className="gap-1.5" onClick={actions.primary.onClick}>
                  {actions.primary.icon ?? <Plus size={16} weight="regular" />}
                  {actions.primary.label}
                </Button>
              )
            )}
            {actions.onFilter && (
              <Button variant="outline" size="icon" onClick={actions.onFilter} title="Фильтр">
                <Funnel size={16} weight="regular" />
              </Button>
            )}
            {actions.onRefresh && (
              <Button
                variant="outline"
                size="icon"
                onClick={actions.onRefresh}
                title={actions.refreshPending ? "Обновляю..." : "Обновить"}
                aria-label={actions.refreshPending ? "Обновляю" : "Обновить"}
                disabled={actions.refreshPending}
              >
                {actions.refreshPending ? (
                  <SpinnerGap size={16} weight="regular" className="animate-spin" />
                ) : (
                  <ArrowClockwise size={16} weight="regular" />
                )}
              </Button>
            )}
            {actions.onExport && (
              <Button variant="outline" size="icon" onClick={actions.onExport} title="Экспорт">
                <DownloadSimple size={16} weight="regular" />
              </Button>
            )}
          </div>
        )}
      </div>
      {children}
    </div>
  );
}
