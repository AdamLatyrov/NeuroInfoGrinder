import { useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { SpinnerGap, MagnifyingGlass, Hash } from "@phosphor-icons/react";
import type { Group } from "@/shared/types";

interface GroupListPanelProps {
  groups: Group[];
  isLoading: boolean;
  selectedGroupId: string | null;
  onSelectGroup: (group: Group) => void;
  showDisabled: boolean;
  onToggleShowDisabled: (value: boolean) => void;
}

export function GroupListPanel({
  groups,
  isLoading,
  selectedGroupId,
  onSelectGroup,
  showDisabled,
  onToggleShowDisabled,
}: GroupListPanelProps) {
  const [search, setSearch] = useState("");

  const filtered = useMemo(
    () =>
      groups.filter(
        (g) =>
          g.title.toLowerCase().includes(search.toLowerCase()) ||
          String(g.telegramChatId).includes(search) ||
          (g.username ?? "").toLowerCase().includes(search.toLowerCase())
      ),
    [groups, search]
  );

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border-subtle px-3 py-2">
        <span className="text-xs font-semibold uppercase text-text-muted">
          Группы
        </span>
        <div className="flex items-center gap-2">
          <label className="flex cursor-pointer items-center gap-1.5 text-[10px] text-text-muted">
            <Switch
              checked={showDisabled}
              onCheckedChange={onToggleShowDisabled}
              className="scale-75"
            />
            Выключенные
          </label>
          {isLoading && (
            <SpinnerGap
              size={12}
              weight="regular"
              className="animate-spin text-text-muted"
            />
          )}
        </div>
      </div>

      {/* Search */}
      <div className="border-b border-border-subtle px-3 py-1.5">
        <div className="relative">
          <MagnifyingGlass
            size={14}
            weight="regular"
            className="absolute left-2 top-1/2 -translate-y-1/2 text-text-weak"
          />
          <Input
            placeholder="Поиск..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-7 pl-7 text-xs"
          />
        </div>
      </div>

      {/* Group list */}
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-0.5 p-1.5">
          {filtered.length === 0 ? (
            <div className="py-6 text-center text-xs text-text-weak">
              {groups.length === 0 ? "Нет групп" : "Ничего не найдено"}
            </div>
          ) : (
            filtered.map((group) => (
              <button
                key={group.id}
                onClick={() => onSelectGroup(group)}
                className={`rounded-lg px-3 py-2 text-left transition-colors ${
                  selectedGroupId === group.id
                    ? "bg-brand-blue-soft text-text-strong"
                    : "text-text-default hover:bg-bg-app"
                }`}
              >
                <div className="flex items-center gap-2">
                  {/* Avatar-like circle */}
                  <div
                    className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${
                      selectedGroupId === group.id
                        ? "bg-brand-blue/20 text-brand-blue"
                        : "bg-bg-elevated text-text-muted"
                    }`}
                  >
                    {group.title.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate text-sm font-medium">
                        {group.title}
                      </span>
                      {group.forum && (
                        <Hash
                          size={12}
                          weight="regular"
                          className="shrink-0 text-brand-blue"
                        />
                      )}
                    </div>
                    <div className="mt-0.5 flex items-center gap-2 text-xs text-text-muted">
                      <span>
                        {Number(group.messagesPerDay).toLocaleString()}/д
                      </span>
                      <Badge
                        variant={group.enabled ? "success" : "outline"}
                        className="px-1.5 py-0 text-[10px]"
                      >
                        {group.enabled ? "on" : "off"}
                      </Badge>
                    </div>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
