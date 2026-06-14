import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { MagnifyingGlass, SpinnerGap, Trash, WarningCircle } from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  useBulkDeleteGuidesMutation,
  useDeleteGuideMutation,
  useGuidesQuery,
} from "@/shared/api/guidesApi";
import { displayGuideStatus, type Guide, type GuideStatus } from "@/shared/types";

const GUIDE_FILTERS: Array<{ value: GuideStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "Все" },
  { value: "DRAFT", label: "Черновики" },
  { value: "PUBLISHED", label: "Опубликовано" },
  { value: "REJECTED", label: "Отклонено" },
  { value: "FAILED", label: "Ошибка" },
];

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatTokens(value: number) {
  return value > 0 ? value.toLocaleString() : "—";
}

export function GuidesPage() {
  const navigate = useNavigate();
  const guidesQuery = useGuidesQuery({ page: 0, size: 200 });
  const deleteGuide = useDeleteGuideMutation();
  const bulkDeleteGuides = useBulkDeleteGuidesMutation();

  const guides = guidesQuery.data?.content ?? [];
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<GuideStatus | "ALL">("ALL");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const filteredGuides = useMemo(() => {
    const query = search.trim().toLowerCase();
    return guides.filter((guide) => {
      const tagText = guide.tags.join(" ").toLowerCase();
      const groupText = `${guide.sourceGroupTitle ?? ""} ${guide.sourceGroupId ?? ""}`.toLowerCase();
      const matchesSearch =
        !query ||
        guide.title.toLowerCase().includes(query) ||
        (guide.model ?? "").toLowerCase().includes(query) ||
        groupText.includes(query) ||
        tagText.includes(query) ||
        (guide.generationError ?? "").toLowerCase().includes(query);
      const matchesStatus = statusFilter === "ALL" || guide.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [guides, search, statusFilter]);

  const publishedCount = guides.filter((guide) => guide.status === "PUBLISHED").length;
  const failedCount = guides.filter((guide) => guide.status === "FAILED" || !!guide.generationError).length;
  const selectedGuideIds = useMemo(() => Array.from(selectedIds), [selectedIds]);

  const allVisibleSelected =
    filteredGuides.length > 0 && filteredGuides.every((guide) => selectedIds.has(guide.id));

  const toggleSelected = (guideId: string) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(guideId)) {
        next.delete(guideId);
      } else {
        next.add(guideId);
      }
      return next;
    });
  };

  const toggleAllVisible = () => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (allVisibleSelected) {
        filteredGuides.forEach((guide) => next.delete(guide.id));
      } else {
        filteredGuides.forEach((guide) => next.add(guide.id));
      }
      return next;
    });
  };

  const handleSingleDelete = (guide: Guide) => {
    const confirmed = window.confirm(`Удалить гайд "${guide.title}"?`);
    if (!confirmed) {
      return;
    }

    deleteGuide.mutate(guide.id, {
      onSuccess: () => {
        setSelectedIds((current) => {
          const next = new Set(current);
          next.delete(guide.id);
          return next;
        });
      },
    });
  };

  const handleBulkDelete = () => {
    if (selectedGuideIds.length === 0) {
      return;
    }

    const confirmed = window.confirm(
      `Удалить выбранные гайды: ${selectedGuideIds.length} шт.?`
    );
    if (!confirmed) {
      return;
    }

    bulkDeleteGuides.mutate(selectedGuideIds, {
      onSuccess: () => {
        setSelectedIds(new Set());
      },
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Гайды"
        description="Список готовых, опубликованных и проблемных гайдов с быстрым поиском и модерацией."
        actions={{ onRefresh: () => guidesQuery.refetch() }}
      />

      <div className="grid gap-3 md:grid-cols-4">
        <Card>
          <CardContent className="p-4">
            <div className="text-xs uppercase tracking-wider text-text-muted">Всего</div>
            <div className="mt-2 text-2xl font-semibold text-text-strong">{guides.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-xs uppercase tracking-wider text-text-muted">Опубликовано</div>
            <div className="mt-2 text-2xl font-semibold text-text-strong">{publishedCount}</div>
          </CardContent>
        </Card>
        <Card className={failedCount > 0 ? "border-danger/30" : undefined}>
          <CardContent className="p-4">
            <div className="text-xs uppercase tracking-wider text-text-muted">Ошибки API</div>
            <div className={`mt-2 text-2xl font-semibold ${failedCount > 0 ? "text-danger" : "text-text-strong"}`}>
              {failedCount}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-xs uppercase tracking-wider text-text-muted">Выбрано</div>
            <div className="mt-2 text-2xl font-semibold text-text-strong">{selectedIds.size}</div>
          </CardContent>
        </Card>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[260px] flex-1">
          <MagnifyingGlass size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-weak" />
          <Input
            placeholder="Поиск по названию, группе, тегам, модели или ошибке"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {GUIDE_FILTERS.map((filter) => (
            <Button
              key={filter.value}
              size="sm"
              variant={statusFilter === filter.value ? "secondary" : "ghost"}
              onClick={() => setStatusFilter(filter.value)}
            >
              {filter.label}
            </Button>
          ))}
        </div>
      </div>

      {selectedIds.size > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-danger/20 bg-danger/5 px-4 py-3">
          <div className="text-sm font-medium text-text-strong">
            Выбрано гайдов: {selectedIds.size}
          </div>
          <Button
            size="sm"
            variant="outline"
            className="gap-1.5 text-danger hover:text-danger"
            onClick={handleBulkDelete}
            disabled={bulkDeleteGuides.isPending}
          >
            <Trash size={14} />
            Удалить выбранные
          </Button>
          {(bulkDeleteGuides.isPending || deleteGuide.isPending) && (
            <SpinnerGap size={16} className="animate-spin text-text-muted" />
          )}
        </div>
      )}

      {filteredGuides.length === 0 && !guidesQuery.isLoading ? (
        <EmptyState
          title="Гайды не найдены"
          description="Либо список уже почищен, либо текущий фильтр ничего не показывает."
        />
      ) : (
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
                <TableHead>Гайд</TableHead>
                <TableHead>Статус</TableHead>
                <TableHead>Группа</TableHead>
                <TableHead>Теги</TableHead>
                <TableHead>Модель</TableHead>
                <TableHead className="text-right">Confidence</TableHead>
                <TableHead className="text-right">Токены</TableHead>
                <TableHead>Создан</TableHead>
                <TableHead className="w-28 text-right">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredGuides.map((guide) => {
                const failed = guide.status === "FAILED" || !!guide.generationError;
                return (
                  <TableRow
                    key={guide.id}
                    className={failed ? "bg-danger-soft/20 hover:bg-danger-soft/30" : "hover:bg-bg-app/40"}
                    title={guide.generationError ?? undefined}
                  >
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(guide.id)}
                        onChange={() => toggleSelected(guide.id)}
                        className="h-4 w-4 rounded border-border-subtle accent-brand-blue"
                      />
                    </TableCell>
                    <TableCell
                      className="max-w-[420px] cursor-pointer"
                      onClick={() => navigate(`/guides/${guide.id}`)}
                    >
                      <div className="flex items-center gap-2">
                        <div className="truncate font-medium text-text-strong">{guide.title}</div>
                        {failed && <WarningCircle size={14} className="text-danger" />}
                      </div>
                      <div className="mt-1 text-xs text-text-muted">#{guide.id}</div>
                      {guide.generationError && (
                        <div className="mt-1 text-xs text-danger">
                          {guide.generationError}
                        </div>
                      )}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={displayGuideStatus(guide.status)} />
                    </TableCell>
                    <TableCell>
                      {guide.sourceGroupTitle ?? (guide.sourceGroupId ? `#${guide.sourceGroupId}` : "—")}
                    </TableCell>
                    <TableCell className="max-w-[240px]">
                      <div className="flex flex-wrap gap-1.5">
                        {guide.tags.length === 0 ? (
                          <span className="text-sm text-text-muted">—</span>
                        ) : (
                          guide.tags.slice(0, 3).map((tag) => (
                            <Badge key={tag} variant="secondary">
                              {tag}
                            </Badge>
                          ))
                        )}
                      </div>
                    </TableCell>
                    <TableCell>{guide.model ?? "—"}</TableCell>
                    <TableCell className="text-right font-mono-value">
                      {guide.confidence.toFixed(2)}
                    </TableCell>
                    <TableCell className="text-right font-mono-value">
                      {formatTokens(guide.totalTokens)}
                    </TableCell>
                    <TableCell className="text-sm text-text-muted">
                      {formatDate(guide.createdAt)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        className="gap-1.5 text-danger hover:text-danger"
                        onClick={() => handleSingleDelete(guide)}
                        disabled={deleteGuide.isPending}
                      >
                        <Trash size={14} />
                        Удалить
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
