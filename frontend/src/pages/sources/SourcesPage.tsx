import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Checkbox } from "@/components/ui/checkbox";
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
import { useAccountsQuery } from "@/shared/api/accountsApi";
import {
  useGroupsQuery,
  useSyncGroupsMutation,
  useUpdateGroupMutation,
} from "@/shared/api/groupsApi";
import { displayAccountStatus } from "@/shared/types";
import type { TelegramAccount } from "@/shared/types";
import {
  DeviceMobile,
  Users,
  ArrowsClockwise,
  Check,
  X,
  MagnifyingGlass,
  SpinnerGap,
  LinkBreak,
  ChatCircle,
} from "@phosphor-icons/react";

// ── localStorage keys ──

const LS_READING_MODE = "nig-reading-mode";
const LS_SELECTED_GROUPS = "nig-selected-groups";

// ── Reading mode type ──

type ReadingMode = "all" | "selected" | "except";

const READING_MODES: { key: ReadingMode; label: string }[] = [
  { key: "all", label: "Все группы" },
  { key: "selected", label: "Только выбранные" },
  { key: "except", label: "Все, кроме" },
];

function loadReadingMode(): ReadingMode {
  try {
    const raw = localStorage.getItem(LS_READING_MODE);
    if (raw === "all" || raw === "selected" || raw === "except") return raw;
  } catch {
    /* ignore */
  }
  return "all";
}

function saveReadingMode(mode: ReadingMode) {
  try {
    localStorage.setItem(LS_READING_MODE, mode);
  } catch {
    /* ignore */
  }
}

function loadSelectedGroups(): string[] {
  try {
    const raw = localStorage.getItem(LS_SELECTED_GROUPS);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed.map(String);
    }
  } catch {
    /* ignore */
  }
  return [];
}

function saveSelectedGroups(ids: string[]) {
  try {
    localStorage.setItem(LS_SELECTED_GROUPS, JSON.stringify(ids));
  } catch {
    /* ignore */
  }
}

// ── Helper: format date ──

function formatDate(iso: string | null): string {
  if (!iso) return "\u2014";
  try {
    const d = new Date(iso);
    return d.toLocaleDateString("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

// ── Account Card ──

function AccountCard({ account }: { account: TelegramAccount }) {
  const isConnected = account.status === "CONNECTED";

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3 min-w-0">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-blue-soft">
              <DeviceMobile
                size={20}
                weight="regular"
                className="text-brand-blue"
              />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-medium text-text-strong truncate">
                  {account.username ?? account.phone}
                </span>
                <StatusBadge
                  status={displayAccountStatus(account.status)}
                />
              </div>
              <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-text-muted">
                <span className="font-mono-value">{account.phone}</span>
                {account.username && (
                  <span>@{account.username}</span>
                )}
                {account.createdAt && (
                  <span>Создан: {formatDate(account.createdAt)}</span>
                )}
              </div>
            </div>
          </div>

          {!isConnected && (
            <div className="flex items-center gap-2 shrink-0">
              <Link to="/accounts">
                <Button variant="outline" size="sm" className="gap-1.5">
                  <LinkBreak size={14} weight="regular" />
                  Переподключить
                </Button>
              </Link>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// ── Stats ribbon ──

interface StatsRibbonProps {
  total: number;
  enabled: number;
  forums: number;
  guidesFound: number;
}

function StatsRibbon({ total, enabled, forums, guidesFound }: StatsRibbonProps) {
  const items = [
    { label: "Всего групп", value: total },
    { label: "Включены", value: enabled },
    { label: "Форумы", value: forums },
    { label: "Гайдов найдено", value: guidesFound },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {items.map((item) => (
        <Card key={item.label}>
          <CardContent className="flex flex-col items-center justify-center py-3 px-2">
            <span className="text-2xl font-bold text-text-strong tabular-nums">
              {item.value.toLocaleString()}
            </span>
            <span className="text-xs text-text-muted mt-0.5">{item.label}</span>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

// ── Main page ──

export function SourcesPage() {
  const [readingMode, setReadingMode] = useState<ReadingMode>(loadReadingMode);
  const [selectedGroupIds, setSelectedGroupIds] = useState<string[]>(
    loadSelectedGroups
  );
  const [search, setSearch] = useState("");
  const [syncDialogOpen, setSyncDialogOpen] = useState(false);

  const accountsQuery = useAccountsQuery();
  const groupsQuery = useGroupsQuery({ size: 200 });
  const syncGroupsMutation = useSyncGroupsMutation();
  const updateGroupMutation = useUpdateGroupMutation();

  const accounts = accountsQuery.data ?? [];
  const connectedAccount = accounts.length > 0 ? accounts[0] : null;

  const groups = groupsQuery.data?.content ?? [];

  // ── Persist reading mode ──

  useEffect(() => {
    saveReadingMode(readingMode);
  }, [readingMode]);

  // ── Persist selected groups ──

  useEffect(() => {
    saveSelectedGroups(selectedGroupIds);
  }, [selectedGroupIds]);

  // ── Filtered groups (client-side search) ──

  const filteredGroups = useMemo(
    () =>
      groups.filter((g) =>
        g.title.toLowerCase().includes(search.toLowerCase())
      ),
    [groups, search]
  );

  // ── Stats ──

  const stats = useMemo(() => {
    const total = groups.length;
    const enabled = groups.filter((g) => g.enabled).length;
    const forums = groups.filter((g) => g.forum).length;
    const guidesFound = groups.reduce((sum, g) => sum + g.guidesFound, 0);
    return { total, enabled, forums, guidesFound };
  }, [groups]);

  // ── Checkbox helpers ──

  const isSelected = useCallback(
    (id: string) => selectedGroupIds.includes(id),
    [selectedGroupIds]
  );

  const toggleSelected = useCallback(
    (id: string) => {
      setSelectedGroupIds((prev) =>
        prev.includes(id)
          ? prev.filter((x) => x !== id)
          : [...prev, id]
      );
    },
    []
  );

  const toggleAll = useCallback(() => {
    if (selectedGroupIds.length === filteredGroups.length) {
      setSelectedGroupIds([]);
    } else {
      setSelectedGroupIds(filteredGroups.map((g) => g.id));
    }
  }, [filteredGroups, selectedGroupIds.length]);

  // ── Is a group "active" for reading based on mode ──

  const isReadingActive = useCallback(
    (id: string) => {
      if (readingMode === "all") return true;
      if (readingMode === "selected") return selectedGroupIds.includes(id);
      // "except" — active if NOT selected
      return !selectedGroupIds.includes(id);
    },
    [readingMode, selectedGroupIds]
  );

  // ── Sync handler ──

  const handleSync = () => {
    syncGroupsMutation.mutate(undefined, {
      onSuccess: () => {
        setSyncDialogOpen(true);
      },
    });
  };

  // ── Are checkboxes interactive? ──

  const checkboxesActive = readingMode !== "all";

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Источники"
        description="Telegram-аккаунт и группы для чтения"
        actions={{
          onRefresh: () => {
            accountsQuery.refetch();
            groupsQuery.refetch();
          },
        }}
      />

      {/* ── Telegram Account section ── */}
      {accountsQuery.isLoading && !accountsQuery.data ? (
        <div className="flex items-center gap-2 text-text-muted text-sm">
          <SpinnerGap
            size={16}
            weight="regular"
            className="animate-spin"
          />
          Загрузка аккаунта...
        </div>
      ) : accountsQuery.isError ? (
        <Alert variant="danger">
          {(accountsQuery.error as Error | null)?.message ?? "Failed to load accounts"}
        </Alert>
      ) : connectedAccount ? (
        <AccountCard account={connectedAccount} />
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-8 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-bg-app border border-border-subtle mb-3">
              <DeviceMobile
                size={24}
                weight="regular"
                className="text-text-weak"
              />
            </div>
            <p className="text-sm font-medium text-text-strong mb-1">
              Подключите Telegram-аккаунт
            </p>
            <p className="text-xs text-text-muted max-w-sm mb-3">
              Без подключённого аккаунта невозможно читать группы и получать
              сообщения.
            </p>
            <Link to="/accounts">
              <Button variant="primary" size="sm" className="gap-1.5">
                <DeviceMobile size={14} weight="regular" />
                Подключить аккаунт
              </Button>
            </Link>
          </CardContent>
        </Card>
      )}

      {/* ── Reading mode selector ── */}
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium text-text-muted shrink-0">
          Режим чтения:
        </span>
        <div className="flex items-center gap-1 rounded-xl border border-border-subtle bg-bg-elevated p-1">
          {READING_MODES.map((mode) => (
            <Button
              key={mode.key}
              variant={readingMode === mode.key ? "secondary" : "ghost"}
              size="sm"
              onClick={() => setReadingMode(mode.key)}
            >
              {mode.label}
            </Button>
          ))}
        </div>
        {readingMode === "selected" && (
          <span className="text-xs text-text-muted">
            — читаются только отмеченные группы
          </span>
        )}
        {readingMode === "except" && (
          <span className="text-xs text-text-muted">
            — читаются все, кроме отмеченных
          </span>
        )}
      </div>

      {/* ── Stats ── */}
      {groups.length > 0 && (
        <StatsRibbon
          total={stats.total}
          enabled={stats.enabled}
          forums={stats.forums}
          guidesFound={stats.guidesFound}
        />
      )}

      {/* ── Toolbar: search + sync ── */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative max-w-xs flex-1">
          <MagnifyingGlass
            size={16}
            weight="regular"
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-weak"
          />
          <Input
            placeholder="Поиск по названию группы"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Button
          variant="primary"
          size="sm"
          className="gap-1.5"
          onClick={handleSync}
          disabled={syncGroupsMutation.isPending}
        >
          <ArrowsClockwise size={14} weight="regular" />
          Синхронизировать
        </Button>
        {(groupsQuery.isLoading || syncGroupsMutation.isPending) && (
          <SpinnerGap
            size={16}
            weight="regular"
            className="animate-spin text-text-muted"
          />
        )}
      </div>

      {/* ── Group list ── */}
      {groups.length === 0 && !groupsQuery.isLoading ? (
        <EmptyState
          icon={Users}
          title="Нет групп"
          description="Подключите Telegram-аккаунт и синхронизируйте группы."
          action={
            <Button
              variant="primary"
              size="sm"
              className="gap-1.5"
              onClick={handleSync}
              disabled={syncGroupsMutation.isPending}
            >
              <ArrowsClockwise size={14} weight="regular" />
              Подтянуть группы
            </Button>
          }
        />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  {checkboxesActive && (
                    <Checkbox
                      checked={
                        selectedGroupIds.length === filteredGroups.length &&
                        filteredGroups.length > 0
                      }
                      onCheckedChange={toggleAll}
                    />
                  )}
                </TableHead>
                <TableHead>Название</TableHead>
                <TableHead className="w-20">Тип</TableHead>
                <TableHead className="w-16">Вкл</TableHead>
                <TableHead className="w-24">Доступ</TableHead>
                <TableHead>Последнее чтение</TableHead>
                <TableHead className="text-right">Гайдов</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredGroups.map((group) => {
                const readingActive = isReadingActive(group.id);

                return (
                  <TableRow
                    key={group.id}
                    className={
                      !readingActive ? "opacity-50" : undefined
                    }
                  >
                    {/* Checkbox */}
                    <TableCell>
                      {checkboxesActive ? (
                        <Checkbox
                          checked={isSelected(group.id)}
                          onCheckedChange={() => toggleSelected(group.id)}
                        />
                      ) : (
                        <Check
                          size={14}
                          weight="regular"
                          className="text-brand-blue"
                        />
                      )}
                    </TableCell>

                    {/* Title */}
                    <TableCell>
                      <div>
                        <div className="font-medium text-text-strong">
                          {group.title}
                        </div>
                        {group.username && (
                          <div className="mt-0.5 text-xs text-text-muted">
                            @{group.username}
                          </div>
                        )}
                      </div>
                    </TableCell>

                    {/* Type (forum badge) */}
                    <TableCell>
                      {group.forum ? (
                        <Badge variant="success" className="text-xs">
                          <ChatCircle size={10} weight="regular" />
                          форум
                        </Badge>
                      ) : (
                        <span className="text-text-weak">
                          {"\u2014"}
                        </span>
                      )}
                    </TableCell>

                    {/* Enabled switch */}
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <Switch
                        checked={group.enabled}
                        onCheckedChange={(enabled) =>
                          updateGroupMutation.mutate({
                            id: group.id,
                            enabled,
                          })
                        }
                      />
                    </TableCell>

                    {/* Status access */}
                    <TableCell>
                      {group.enabled ? (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-success">
                          <Check
                            size={12}
                            weight="bold"
                          />
                          OK
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-danger">
                          <X
                            size={12}
                            weight="bold"
                          />
                          Off
                        </span>
                      )}
                    </TableCell>

                    {/* Last reading */}
                    <TableCell className="text-sm text-text-muted">
                      {formatDate(group.lastReadAt)}
                    </TableCell>

                    {/* Guides found */}
                    <TableCell className="text-right font-mono-value">
                      {group.guidesFound.toLocaleString()}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      {/* ── Sync dialog ── */}
      <Dialog open={syncDialogOpen} onOpenChange={setSyncDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Синхронизация запущена</DialogTitle>
            <DialogDescription>
              Запрос на подтяжку групп отправлен. Если аккаунт уже подключён и
              видит чаты, список обновится после завершения синка.
            </DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    </div>
  );
}
