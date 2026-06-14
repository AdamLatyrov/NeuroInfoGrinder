import { useState, useMemo } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  type CreatePromptRequest,
  type UpdatePromptRequest,
  type TestPromptResult,
  usePromptsQuery,
  useCreatePromptMutation,
  useUpdatePromptMutation,
  useDeletePromptMutation,
  useTestPromptMutation,
} from "@/shared/api/promptsApi";
import { useProvidersQuery } from "@/shared/api/providersApi";
import {
  type Prompt,
  type PromptType,
  type PromptStatus,
  tryParseJson,
} from "@/shared/types";
import {
  FileText,
  Plus,
  Pencil,
  Trash,
  Eye,
  Flask,
  Archive,
} from "@phosphor-icons/react";

// ── Constants ──

const PROMPT_TYPES: { value: PromptType; label: string }[] = [
  { value: "CLASSIFIER", label: "Классификация" },
  { value: "GUIDE_GENERATOR", label: "Генерация гайда" },
];

const PROMPT_STATUSES: PromptStatus[] = ["ACTIVE", "DRAFT", "ARCHIVED"];

function promptTypeLabel(type: PromptType): string {
  const labels: Record<PromptType, string> = {
    CLASSIFIER: "Классификация",
    GUIDE_GENERATOR: "Генерация гайда",
  };
  return labels[type];
}

function promptTypeDescription(type: PromptType): string {
  if (type === "CLASSIFIER") {
    return "Промпт решает, полезно ли сообщение и стоит ли вести его дальше к гайду.";
  }
  return "Промпт превращает подходящие сообщения и контекст в черновик гайда.";
}

function promptStatusBadgeLabel(status: PromptStatus): string {
  const labels: Record<PromptStatus, string> = {
    ACTIVE: "Активный",
    DRAFT: "Черновик",
    ARCHIVED: "Архив",
  };
  return labels[status];
}

function promptStatusVariant(status: PromptStatus) {
  if (status === "ACTIVE") return "success";
  if (status === "ARCHIVED") return "secondary";
  return "warning";
}

const SAMPLE_VARIABLES: Record<string, string> = {
  text: "Как настроить деплой: 1. Откройте настройки...",
  senderName: "devuser",
  senderUsername: "@devuser",
  groupName: "Vibe Dev",
  topicName: "Codex",
  date: "7 июня 2026",
  hasMedia: "false",
  replyThread: "(3 ответа в треде)",
  previousMessages: "[предыдущие сообщения из чата]",
};

// ── Form state ──

interface PromptFormData {
  name: string;
  type: PromptType;
  version: string;
  variables: string; // comma-separated
  content: string;
  status: PromptStatus;
}

const EMPTY_FORM: PromptFormData = {
  name: "",
  type: "CLASSIFIER",
  version: "v1",
  variables:
    "text, senderName, senderUsername, groupName, topicName, date, hasMedia, replyThread, previousMessages",
  content: "",
  status: "DRAFT",
};

// ── Helpers ──

function replaceVariables(content: string, vars: Record<string, string>): string {
  return content.replace(/\{\{(\w+)\}\}/g, (_, key) => vars[key] ?? `{{${key}}}`);
}

function parseVariablesFromString(varsStr: string): string[] {
  return varsStr
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);
}

function variablesFromPrompt(prompt: Prompt): string {
  const parsed = tryParseJson<string[]>(prompt.variablesJson);
  return parsed ? parsed.join(", ") : "";
}

// ── Dialog mode ──

type DialogMode = "create" | "edit" | null;

// ── Component ──

export function PromptsPage() {
  // ── Queries ──
  const promptsQuery = usePromptsQuery();
  const providersQuery = useProvidersQuery();

  // ── Mutations ──
  const createPromptMutation = useCreatePromptMutation();
  const updatePromptMutation = useUpdatePromptMutation();
  const deletePromptMutation = useDeletePromptMutation();
  const testPromptMutation = useTestPromptMutation();

  // ── Derived data ──
  const prompts = promptsQuery.data ?? [];
  const providers = providersQuery.data ?? [];

  // ── Tab state ──
  const [activeTab, setActiveTab] = useState<string>("classifier");

  const filteredPrompts = useMemo(() => {
    const typeFilter: PromptType =
      activeTab === "classifier" ? "CLASSIFIER" : "GUIDE_GENERATOR";
    return prompts.filter((p) => p.type === typeFilter);
  }, [prompts, activeTab]);

  // ── Dialog states ──
  const [dialogMode, setDialogMode] = useState<DialogMode>(null);
  const [editingPromptId, setEditingPromptId] = useState<string | null>(null);
  const [form, setForm] = useState<PromptFormData>(EMPTY_FORM);

  const [deleteTarget, setDeleteTarget] = useState<Prompt | null>(null);

  // ── Preview state ──
  const [selectedPromptId, setSelectedPromptId] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);

  const selectedPrompt = useMemo(
    () => prompts.find((p) => p.id === selectedPromptId) ?? null,
    [prompts, selectedPromptId],
  );

  // ── Test state ──
  const [testDialogOpen, setTestDialogOpen] = useState(false);
  const [testProviderId, setTestProviderId] = useState<string>("");
  const [testPromptId, setTestPromptId] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<TestPromptResult | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  // ── Handlers ──

  function openCreateDialog() {
    const typeForTab: PromptType =
      activeTab === "classifier" ? "CLASSIFIER" : "GUIDE_GENERATOR";
    setForm({ ...EMPTY_FORM, type: typeForTab });
    setEditingPromptId(null);
    setDialogMode("create");
  }

  function openEditDialog(prompt: Prompt) {
    setForm({
      name: prompt.name,
      type: prompt.type,
      version: prompt.version,
      variables: variablesFromPrompt(prompt),
      content: prompt.content,
      status: prompt.status,
    });
    setEditingPromptId(prompt.id);
    setDialogMode("edit");
  }

  function closeDialog() {
    setDialogMode(null);
    setEditingPromptId(null);
  }

  function handleSubmit() {
    const variablesStr = parseVariablesFromString(form.variables).join(",");

    if (dialogMode === "create") {
      const body: CreatePromptRequest = {
        name: form.name,
        type: form.type,
        content: form.content,
        variables: variablesStr,
        version: form.version || "v1",
      };
      createPromptMutation.mutate(body, {
        onSuccess: () => closeDialog(),
      });
    } else if (dialogMode === "edit" && editingPromptId) {
      const body: UpdatePromptRequest = {
        name: form.name,
        type: form.type,
        content: form.content,
        variables: variablesStr,
        version: form.version,
      };
      updatePromptMutation.mutate(
        { id: editingPromptId, ...body },
        {
          onSuccess: () => closeDialog(),
        },
      );
    }
  }

  function handleArchive(prompt: Prompt) {
    updatePromptMutation.mutate(
      { id: prompt.id, status: "ARCHIVED" },
    );
  }

  function handleStatusChange(prompt: Prompt, newStatus: PromptStatus) {
    updatePromptMutation.mutate(
      { id: prompt.id, status: newStatus },
    );
  }

  function handleDelete() {
    if (!deleteTarget) return;
    deletePromptMutation.mutate(deleteTarget.id, {
      onSuccess: () => setDeleteTarget(null),
    });
  }

  function openTestDialog(prompt: Prompt) {
    setTestPromptId(prompt.id);
    setTestProviderId(providers.length > 0 ? providers[0].id : "");
    setTestResult(null);
    setTestError(null);
    setTestDialogOpen(true);
  }

  function handleTest() {
    if (!testPromptId) return;
    const prompt = prompts.find((p) => p.id === testPromptId);
    if (!prompt) return;

    const parsedVars = tryParseJson<string[]>(prompt.variablesJson) ?? [];
    const vars: Record<string, string> = {};
    for (const key of parsedVars) {
      vars[key] = SAMPLE_VARIABLES[key] ?? "";
    }

    testPromptMutation.mutate(
      { id: testPromptId, variables: vars, providerId: testProviderId || undefined },
      {
        onSuccess: (result) => {
          setTestResult(result);
          setTestError(null);
        },
        onError: (err) => {
          setTestError(err instanceof Error ? err.message : "Неизвестная ошибка");
          setTestResult(null);
        },
      },
    );
  }

  function togglePreview(prompt: Prompt) {
    if (selectedPromptId === prompt.id && showPreview) {
      setShowPreview(false);
      setSelectedPromptId(null);
    } else {
      setSelectedPromptId(prompt.id);
      setShowPreview(true);
    }
  }

  // ── Status badge helper ──

  function promptStatusLabel(status: PromptStatus): string {
    const map: Record<PromptStatus, string> = {
      ACTIVE: "Активный",
      DRAFT: "Черновик",
      ARCHIVED: "Архив",
    };
    return map[status] ?? status;
  }

  // ── Render ──

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Промпты"
        description="Классификационный промпт выбирается в LLM-классификаторе. Для генерации гайда используется активный генерационный промпт."
      />

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="classifier" className="gap-1.5">
            <FileText size={14} weight="regular" />
            Классификация
          </TabsTrigger>
          <TabsTrigger value="guide-generator" className="gap-1.5">
            <FileText size={14} weight="regular" />
            Генерация гайда
          </TabsTrigger>
        </TabsList>

        {/* ── Tab: Classifier ── */}
        <TabsContent value="classifier">
          <PromptTable
            prompts={filteredPrompts}
            onEdit={openEditDialog}
            onDelete={setDeleteTarget}
            onArchive={handleArchive}
            onStatusChange={handleStatusChange}
            onPreview={togglePreview}
            onTest={openTestDialog}
            selectedPromptId={showPreview ? selectedPromptId : null}
            onCreate={openCreateDialog}
          />
        </TabsContent>

        {/* ── Tab: Guide Generator ── */}
        <TabsContent value="guide-generator">
          <PromptTable
            prompts={filteredPrompts}
            onEdit={openEditDialog}
            onDelete={setDeleteTarget}
            onArchive={handleArchive}
            onStatusChange={handleStatusChange}
            onPreview={togglePreview}
            onTest={openTestDialog}
            selectedPromptId={showPreview ? selectedPromptId : null}
            onCreate={openCreateDialog}
          />
        </TabsContent>
      </Tabs>

      {/* ── Preview Panel ── */}
      {showPreview && selectedPrompt && (
        <Card>
          <CardContent className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Eye size={18} weight="regular" className="text-text-muted" />
                <h3 className="text-sm font-semibold text-text-strong">
                  Предпросмотр: {selectedPrompt.name}
                </h3>
                <Badge variant="outline" className="text-xs">
                  v{selectedPrompt.version}
                </Badge>
              </div>
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs"
                onClick={() => {
                  setShowPreview(false);
                  setSelectedPromptId(null);
                }}
              >
                Скрыть
              </Button>
            </div>
            <pre className="rounded-xl border border-border-subtle bg-bg-app p-4 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto">
              {replaceVariables(selectedPrompt.content, SAMPLE_VARIABLES)}
            </pre>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {parseVariablesFromString(variablesFromPrompt(selectedPrompt)).map((v) => (
                <Badge key={v} variant="outline" className="text-[10px]">
                  {`{{${v}}}`}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* ── Create / Edit Prompt Dialog ── */}
      <Dialog open={dialogMode !== null} onOpenChange={(open) => !open && closeDialog()}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {dialogMode === "create" ? "Новый промпт" : "Редактировать промпт"}
            </DialogTitle>
            <DialogDescription>
              {dialogMode === "create"
                ? "Заполните содержимое промпта. Переменные указываются как {{variable}}."
                : "Измените параметры и содержимое промпта."}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Название</label>
              <Input
                placeholder="Название промпта"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Тип</label>
                <Select
                  value={form.type}
                  onValueChange={(v) => setForm((f) => ({ ...f, type: v as PromptType }))}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PROMPT_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Версия</label>
                <Input
                  placeholder="v1"
                  value={form.version}
                  onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
                />
              </div>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">
                Переменные (через запятую)
              </label>
              <Input
                placeholder="text, senderName, groupName, topicName, date, hasMedia, replyThread, previousMessages"
                value={form.variables}
                onChange={(e) => setForm((f) => ({ ...f, variables: e.target.value }))}
              />
              <p className="text-[10px] text-text-weak">
                Используются как {"{{variable}}"} в содержимом промпта
              </p>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Содержимое</label>
              <Textarea
                className="min-h-64 font-mono-value text-sm"
                placeholder="Введите содержимое промпта. Используйте {{variable}} для подстановки переменных."
                value={form.content}
                onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
              />
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Статус</label>
              <Select
                value={form.status}
                onValueChange={(v) =>
                  setForm((f) => ({ ...f, status: v as PromptStatus }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PROMPT_STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {promptStatusLabel(s)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={closeDialog}>
                Отмена
              </Button>
              <Button
                variant="primary"
                onClick={handleSubmit}
                disabled={
                  createPromptMutation.isPending ||
                  updatePromptMutation.isPending ||
                  !form.name.trim() ||
                  !form.content.trim()
                }
              >
                {dialogMode === "create" ? "Создать промпт" : "Сохранить"}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

      {/* ── Delete Confirmation Dialog ── */}
      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Удалить промпт</DialogTitle>
            <DialogDescription>
              Вы уверены, что хотите удалить промпт{" "}
              <span className="font-semibold text-text-strong">
                &laquo;{deleteTarget?.name}&raquo;
              </span>
              ? Это действие нельзя отменить.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Отмена
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={deletePromptMutation.isPending}
            >
              <Trash size={14} weight="regular" />
              Удалить
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Test Prompt Dialog ── */}
      <Dialog open={testDialogOpen} onOpenChange={setTestDialogOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Протестировать промпт</DialogTitle>
            <DialogDescription>
              Выберите провайдера и запустите тест. Переменные будут заполнены примерами.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Провайдер</label>
              <Select value={testProviderId} onValueChange={setTestProviderId}>
                <SelectTrigger>
                  <SelectValue placeholder="Выберите провайдера" />
                </SelectTrigger>
                <SelectContent>
                  {providers.map((provider) => (
                    <SelectItem key={provider.id} value={provider.id}>
                      {provider.name} ({provider.model ?? "no model"})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button
              variant="primary"
              onClick={handleTest}
              disabled={testPromptMutation.isPending || !testProviderId}
            >
              <Flask size={14} weight="regular" />
              {testPromptMutation.isPending ? "Тестирование..." : "Протестировать"}
            </Button>

            {testError && (
              <div className="rounded-lg border border-danger/30 bg-danger-soft/10 p-3 text-sm text-danger">
                {testError}
              </div>
            )}

            {testResult && (
              <div className="grid gap-3">
                <div className="grid gap-1.5">
                  <label className="text-xs font-medium text-text-muted">Результат</label>
                  <pre className="rounded-xl border border-border-subtle bg-bg-app p-4 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto max-h-64 overflow-y-auto">
                    {testResult.output}
                  </pre>
                </div>

                <div className="grid grid-cols-4 gap-3">
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">
                      Input tokens
                    </span>
                    <span className="mt-1 block font-mono-value text-sm text-text-strong">
                      {testResult.inputTokens.toLocaleString()}
                    </span>
                  </div>
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">
                      Output tokens
                    </span>
                    <span className="mt-1 block font-mono-value text-sm text-text-strong">
                      {testResult.outputTokens.toLocaleString()}
                    </span>
                  </div>
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">
                      Total tokens
                    </span>
                    <span className="mt-1 block font-mono-value text-sm text-text-strong">
                      {testResult.totalTokens.toLocaleString()}
                    </span>
                  </div>
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">
                      Est. cost
                    </span>
                    <span className="mt-1 block font-mono-value text-sm text-text-strong">
                      ${testResult.estimatedCostUsd.toFixed(4)}
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTestDialogOpen(false)}>
              Закрыть
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ── Prompt Table Sub-component ──

interface PromptTableProps {
  prompts: Prompt[];
  onEdit: (prompt: Prompt) => void;
  onDelete: (prompt: Prompt) => void;
  onArchive: (prompt: Prompt) => void;
  onStatusChange: (prompt: Prompt, status: PromptStatus) => void;
  onPreview: (prompt: Prompt) => void;
  onTest: (prompt: Prompt) => void;
  selectedPromptId: string | null;
  onCreate: () => void;
}

function PromptTable({
  prompts,
  onEdit,
  onDelete,
  onArchive,
  onStatusChange,
  onPreview,
  onTest,
  selectedPromptId,
  onCreate,
}: PromptTableProps) {
  if (prompts.length === 0) {
    return (
      <EmptyState
        icon={FileText}
        title="Нет промптов"
        description="Создайте промпт для настройки шаблона запроса к LLM."
        action={
          <Button variant="primary" size="sm" onClick={onCreate}>
            <Plus size={14} weight="regular" />
            Новый промпт
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {prompts.map((prompt) => {
          const vars = tryParseJson<string[]>(prompt.variablesJson) ?? [];
          const isSelected = selectedPromptId === prompt.id;

          return (
            <Card
              key={prompt.id}
              className={isSelected ? "border-brand-blue/40 bg-brand-blue-soft/10" : ""}
            >
              <CardContent className="flex min-h-[260px] flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="truncate text-sm font-semibold text-text-strong">
                        {prompt.name}
                      </h3>
                      <Badge variant="outline" className="text-[10px]">
                        {prompt.version}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-text-muted">
                      {promptTypeDescription(prompt.type)}
                    </p>
                  </div>
                  <Badge variant={promptStatusVariant(prompt.status)} dot>
                    {promptStatusBadgeLabel(prompt.status)}
                  </Badge>
                </div>

                <div className="mt-3 flex flex-wrap gap-1.5">
                  <Badge variant="secondary" className="text-[10px]">
                    {promptTypeLabel(prompt.type)}
                  </Badge>
                  {vars.slice(0, 5).map((variable) => (
                    <Badge key={variable} variant="outline" className="text-[10px]">
                      {variable}
                    </Badge>
                  ))}
                  {vars.length > 5 && (
                    <Badge variant="outline" className="text-[10px]">
                      +{vars.length - 5}
                    </Badge>
                  )}
                </div>

                <p className="mt-4 line-clamp-5 rounded-xl border border-border-subtle bg-bg-app p-3 text-xs leading-relaxed text-text-muted">
                  {prompt.content || "Содержимое промпта пока пустое."}
                </p>

                <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-border-subtle pt-3">
                  <Select
                    value={prompt.status}
                    onValueChange={(value) =>
                      onStatusChange(prompt, value as PromptStatus)
                    }
                  >
                    <SelectTrigger className="h-8 w-[130px] text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PROMPT_STATUSES.map((status) => (
                        <SelectItem key={status} value={status}>
                          {promptStatusBadgeLabel(status)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  <div className="flex items-center justify-end gap-1">
                    <Button
                      variant="outline"
                      size="icon"
                      className={`h-8 w-8 ${isSelected ? "bg-brand-blue-soft" : ""}`}
                      onClick={() => onPreview(prompt)}
                      title="Предпросмотр"
                    >
                      <Eye size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => onTest(prompt)}
                      title="Протестировать"
                    >
                      <Flask size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => onEdit(prompt)}
                      title="Редактировать"
                    >
                      <Pencil size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => onArchive(prompt)}
                      disabled={prompt.status === "ARCHIVED"}
                      title="В архив"
                    >
                      <Archive size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8 text-danger hover:bg-danger-soft hover:text-danger"
                      onClick={() => onDelete(prompt)}
                      title="Удалить"
                    >
                      <Trash size={14} weight="regular" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}

      <button
        type="button"
        onClick={onCreate}
        className="flex min-h-[260px] flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border-subtle bg-bg-app p-8 text-text-muted transition-colors hover:border-brand-blue/40 hover:bg-bg-card hover:text-brand-blue"
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-full border-2 border-current">
          <Plus size={24} weight="regular" />
        </div>
        <span className="text-sm font-medium">Новый промпт</span>
      </button>
    </div>
  );
}
