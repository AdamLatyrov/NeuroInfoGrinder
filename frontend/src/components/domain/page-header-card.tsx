import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Plus, Funnel, ArrowClockwise, DownloadSimple } from "@phosphor-icons/react";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, DropdownMenuSeparator, DropdownMenuLabel } from "@/components/ui/dropdown-menu";

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
    onExport?: () => void;
  };
  className?: string;
  children?: React.ReactNode;
}

export function PageHeaderCard({ title, description, pipelineNote, actions, className, children }: PageHeaderCardProps) {
  return (
    <div className={cn("rounded-[24px] border border-border-subtle bg-bg-card px-4 py-4 shadow-[0_12px_30px_rgba(15,18,26,0.08)] sm:px-6 sm:py-5", className)}>
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="min-w-0">
          <h1 className="text-xl font-bold leading-tight text-text-strong sm:text-2xl">{title}</h1>
          {description && <p className="text-sm text-text-muted mt-1">{description}</p>}
          {pipelineNote && (
            <p className="text-xs text-text-weak mt-1 font-mono-value">{pipelineNote}</p>
          )}
        </div>
        {actions && (
          <div className="flex items-center gap-2 shrink-0">
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
                    {actions.primary.items.map((item, i) => (
                      <DropdownMenuItem key={i} onClick={item.onClick}>
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
              <Button variant="outline" size="icon" onClick={actions.onRefresh} title="Обновить">
                <ArrowClockwise size={16} weight="regular" />
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
