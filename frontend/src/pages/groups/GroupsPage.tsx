import { Fragment, useMemo, useState } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GroupsChatView } from "@/components/domain/groups-chat-view";
import {
  useBulkToggleMutation,
  useGroupsQuery,
  useSyncGroupsMutation,
  useUpdateGroupMutation,
} from "@/shared/api/groupsApi";
import type { Group } from "@/shared/types";
import {
  ArrowsClockwise,
  ChatCircle,
  List,
  MagnifyingGlass,
  SpinnerGap,
  Users,
} from "@phosphor-icons/react";

type ViewMode = "chat" | "table";

const FILTERS = [
  { key: "all", label: "Все" },
  { key: "enabled", label: "Включены" },
  { key: "disabled", label: "Выключены" },
  { key: "high", label: "Высокая активность" },
] as const;

const SOURCE_TYPE_LABELS: Record<Group["sourceType"], string> = {
  GROUP: "Группа",
  CHANNEL: "Канал",
  DIRECT_CHAT: "Чат",
};

const SOURCE_TYPE_ORDER: Record<Group["sourceType"], number> = {
  GROUP: 0,
  CHANNEL: 1,
  DIRECT_CHAT: 2,
};

function sortGroups(items: Group[]) {
  return [...items].sort((left, right) => {
    const typeDelta = SOURCE_TYPE_ORDER[left.sourceType] - SOURCE_TYPE_ORDER[right.sourceType];
    if (typeDelta !== 0) return typeDelta;

    const titleDelta = left.title.localeCompare(right.title, "ru", { sensitivity: "base" });
    if (titleDelta !== 0) return titleDelta;

    return left.telegramChatId.localeCompare(right.telegramChatId, "en");
  });
}

function formatGroupName(group: Group) {
  if (group.title?.trim()) return group.title;
  if (group.username?.trim()) return `@${group.username}`;
  return group.sourceType === "DIRECT_CHAT"
    ? `Чат ${group.telegramChatId}`
    : `Группа ${group.telegramChatId}`;
}

export function GroupsPage() {
  const [viewMode, setViewMode] = useState<ViewMode>("chat");
  const [infoOpen, setInfoOpen] = useState(false);
  const syncGroupsMutation = useSyncGroupsMutation();

  const syncGroups = () => {
    syncGroupsMutation.mutate(undefined, {
      onSuccess: () => {
        setInfoOpen(true);
      },
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Группы"
        description="Источники данных и метрики обработки"
        actions={{
          primary: {
            label: syncGroupsMutation.isPending ? "Подтягиваю..." : "Подтянуть группы",
            icon: <ArrowsClockwise size={16} weight="regular" />,
            onClick: syncGroups,
          },
        }}
      >
        <div className="mt-3 flex items-center gap-1 rounded-xl border border-border-subtle bg-bg-elevated p-1">
          <Button
            variant={viewMode === "chat" ? "secondary" : "ghost"}
            size="sm"
            className="gap-1.5"
            onClick={() => setViewMode("chat")}
          >
            <ChatCircle size={14} weight="regular" />
            Чаты
          </Button>
          <Button
            variant={viewMode === "table" ? "secondary" : "ghost"}
            size="sm"
            className="gap-1.5"
            onClick={() => setViewMode("table")}
          >
            <List size={14} weight="regular" />
            Управление
          </Button>
        </div>
      </PageHeaderCard>

      {viewMode === "chat" ? (
        <GroupsChatView />
      ) : (
        <GroupsTable onSyncGroups={syncGroups} syncPending={syncGroupsMutation.isPending} />
      )}

      <Dialog open={infoOpen} onOpenChange={setInfoOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Синхронизация запущена</DialogTitle>
            <DialogDescription>
              Запрос на подтяжку групп отправлен. Новые чаты и группы подтянутся вместе с
              последними сообщениями.
            </DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function GroupsTable({
  onSyncGroups,
  syncPending,
}: {
  onSyncGroups: () => void;
  syncPending: boolean;
}) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["key"]>("all");
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  const groupsQuery = useGroupsQuery({ size: 200 });
  const updateGroupMutation = useUpdateGroupMutation();
  const bulkToggleMutation = useBulkToggleMutation();

  const groups = groupsQuery.data?.content ?? [];

  const filteredGroups = useMemo(() => {
    const normalizedSearch = search.toLowerCase();

    return sortGroups(
      groups.filter((group) => {
        const matchesSearch =
          formatGroupName(group).toLowerCase().includes(normalizedSearch) ||
          String(group.telegramChatId).includes(search) ||
          (group.username ?? "").toLowerCase().includes(normalizedSearch);

        const matchesFilter =
          filter === "enabled"
            ? group.enabled
            : filter === "disabled"
              ? !group.enabled
              : filter === "high"
                ? group.messagesPerDay > 5000
                : true;

        return matchesSearch && matchesFilter;
      })
    );
  }, [filter, groups, search]);

  const groupedSections = useMemo(
    () => [
      {
        key: "messaging",
        title: "Группы и каналы",
        description: "Источники, где обычно появляется общий поток сообщений.",
        items: filteredGroups.filter((group) => group.sourceType !== "DIRECT_CHAT"),
      },
      {
        key: "direct",
        title: "Telegram чаты",
        description: "Личные диалоги и direct chats, подтянутые из Telegram.",
        items: filteredGroups.filter((group) => group.sourceType === "DIRECT_CHAT"),
      },
    ],
    [filteredGroups]
  );

  const visibleGroupIds = filteredGroups.map((group) => group.id);
  const selectedGroupIds = Array.from(selectedRows);

  const toggleRow = (id: string) => {
    setSelectedRows((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAllVisible = () => {
    const allVisibleSelected =
      visibleGroupIds.length > 0 && visibleGroupIds.every((id) => selectedRows.has(id));

    if (allVisibleSelected) {
      setSelectedRows((prev) => {
        const next = new Set(prev);
        visibleGroupIds.forEach((id) => next.delete(id));
        return next;
      });
      return;
    }

    setSelectedRows((prev) => {
      const next = new Set(prev);
      visibleGroupIds.forEach((id) => next.add(id));
      return next;
    });
  };

  const allVisibleSelected =
    visibleGroupIds.length > 0 && visibleGroupIds.every((id) => selectedRows.has(id));

  return (
    <>
      {(groupsQuery.isLoading || syncPending) && (
        <div className="flex items-center gap-2 text-sm text-text-muted">
          <SpinnerGap size={16} weight="regular" className="animate-spin text-text-muted" />
          Обновляю список групп...
        </div>
      )}

      {selectedRows.size > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-brand-blue/20 bg-brand-blue-soft px-4 py-2.5">
          <span className="text-sm font-medium text-brand-blue">Выбрано: {selectedRows.size}</span>
          <Separator orientation="vertical" className="h-5" />
          <Button
            variant="secondary"
            size="sm"
            onClick={() =>
              bulkToggleMutation.mutate({
                groupIds: selectedGroupIds,
                enabled: true,
              })
            }
          >
            Включить
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              bulkToggleMutation.mutate({
                groupIds: selectedGroupIds,
                enabled: false,
              })
            }
          >
            Выключить
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative max-w-xs flex-1">
          <MagnifyingGlass
            size={16}
            weight="regular"
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-weak"
          />
          <Input
            placeholder="Поиск по названию / Chat ID / username"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex flex-wrap gap-1.5">
          {FILTERS.map((item) => (
            <Button
              key={item.key}
              variant={filter === item.key ? "secondary" : "ghost"}
              size="sm"
              onClick={() => setFilter(item.key)}
            >
              {item.label}
            </Button>
          ))}
        </div>
      </div>

      {groups.length === 0 && !groupsQuery.isLoading ? (
        <EmptyState
          icon={Users}
          title="Нет групп"
          description="Группы и чаты подтягиваются из подключённых Telegram-аккаунтов."
          action={
            <Button variant="primary" size="sm" className="gap-1.5" onClick={onSyncGroups}>
              <ArrowsClockwise size={14} weight="regular" />
              Подтянуть группы
            </Button>
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          <div className="overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">
                    <input
                      type="checkbox"
                      checked={allVisibleSelected}
                      onChange={toggleAllVisible}
                      className="h-4 w-4 rounded border-border-subtle accent-brand-blue"
                    />
                  </TableHead>
                  <TableHead className="w-16">Вкл</TableHead>
                  <TableHead>Источник</TableHead>
                  <TableHead>Тип</TableHead>
                  <TableHead>Chat ID</TableHead>
                  <TableHead>Форум</TableHead>
                  <TableHead>Категория</TableHead>
                  <TableHead>Account</TableHead>
                  <TableHead className="text-right">Msg/д</TableHead>
                  <TableHead className="text-right">Гайдов</TableHead>
                  <TableHead>Последнее чтение</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {groupedSections.every((section) => section.items.length === 0) ? (
                  <TableRow>
                    <TableCell colSpan={11} className="py-10 text-center text-sm text-text-muted">
                      По текущему фильтру ничего не найдено.
                    </TableCell>
                  </TableRow>
                ) : (
                  groupedSections.map((section) => (
                    <Fragment key={section.key}>
                      {section.items.length > 0 && (
                        <TableRow className="bg-bg-app/60 hover:bg-bg-app/60">
                          <TableCell colSpan={11} className="py-3">
                            <div className="flex flex-col gap-1">
                              <span className="text-sm font-semibold text-text-strong">{section.title}</span>
                              <span className="text-xs text-text-muted">{section.description}</span>
                            </div>
                          </TableCell>
                        </TableRow>
                      )}
                      {section.items.map((group) => (
                        <TableRow key={group.id} className="hover:bg-bg-app/40">
                          <TableCell>
                            <input
                              type="checkbox"
                              checked={selectedRows.has(group.id)}
                              onChange={() => toggleRow(group.id)}
                              className="h-4 w-4 rounded border-border-subtle accent-brand-blue"
                            />
                          </TableCell>
                          <TableCell>
                            <Switch
                              checked={group.enabled}
                              onCheckedChange={(enabled) =>
                                updateGroupMutation.mutate({ id: group.id, enabled })
                              }
                            />
                          </TableCell>
                          <TableCell>
                            <div>
                              <div className="font-medium text-text-strong">{formatGroupName(group)}</div>
                              {group.username && (
                                <div className="mt-0.5 text-xs text-text-muted">@{group.username}</div>
                              )}
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className="text-xs">
                              {SOURCE_TYPE_LABELS[group.sourceType]}
                            </Badge>
                          </TableCell>
                          <TableCell className="font-mono-value text-xs text-text-muted">
                            {group.telegramChatId}
                          </TableCell>
                          <TableCell>
                            {group.forum ? (
                              <Badge variant="success" className="text-xs">
                                форум
                              </Badge>
                            ) : (
                              <span className="text-text-weak">—</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {group.category ? (
                              <Badge variant="outline" className="text-xs">
                                {group.category}
                              </Badge>
                            ) : (
                              <span className="text-text-weak">—</span>
                            )}
                          </TableCell>
                          <TableCell>{group.accountId ?? "—"}</TableCell>
                          <TableCell className="text-right font-mono-value">
                            {Number(group.messagesPerDay).toLocaleString()}
                          </TableCell>
                          <TableCell className="text-right font-mono-value">
                            {group.guidesFound}
                          </TableCell>
                          <TableCell className="text-sm text-text-muted">
                            {group.lastReadAt ?? "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </Fragment>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </>
  );
}
