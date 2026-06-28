import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { getJsonAuth } from "@/shared/api/http";
import {
  type CreatePromptRequest, type TestPromptResult,
  usePromptsQuery, useCreatePromptMutation, useUpdatePromptMutation,
  useDeletePromptMutation, useTestPromptMutation,
  useActivatePromptMutation, usePromptVersionsQuery, useRollbackPromptMutation,
} from "@/shared/api/promptsApi";
import type { Prompt, PromptType, PromptStatus } from "@/shared/types";
import {
  FileText, Plus, Pencil, Trash, Eye, Flask, ArrowClockwise,
  CaretDown, CaretRight, ClockCounterClockwise, Link, CheckCircle,
} from "@phosphor-icons/react";

const STAGES = [
  { value: "LLM_CLUSTER_JUDGE_AND_ROUTING", label: "LLM Cluster Judge & Routing" },
  { value: "KNOWLEDGE_GENERATION", label: "Knowledge Generation" },
];

const PROMPT_MODES = [
  { value: "CLUSTER", label: "Cluster" },
  { value: "SINGLE_MESSAGE", label: "Single Message" },
  { value: "BOTH", label: "Both" },
];

const PROMPT_STATUSES: PromptStatus[] = ["ACTIVE", "DRAFT", "ARCHIVED"];

function stageLabel(stage: string): string {
  const labels: Record<string, string> = {
    LLM_CLUSTER_JUDGE_AND_ROUTING: "Judge & Routing",
    KNOWLEDGE_GENERATION: "Generation",
  };
  return labels[stage] ?? stage;
}

function promptTypeLabel(type: PromptType): string {
  const labels: Record<string, string> = {
    CLASSIFIER: "Классификация",
    GUIDE_GENERATOR: "Генерация гайда",
  };
  return labels[type];
}

function promptTypeDescription(type: PromptType): string {
  if (type === "CLASSIFIER") return "LLM решает, стоит ли сообщение генерации материала";
  return "LLM превращает сообщения и контекст в материал (гайд/фикс/ресурс)";
}

function promptStatusBadgeLabel(status: PromptStatus): string {
  const labels: Record<string, string> = {
    ACTIVE: "Активный",
    DRAFT: "Черновик",
    ARCHIVED: "Архив",
  };
  return labels[status] ?? status;
}

function promptStatusVariant(status: PromptStatus) {
  if (status === "ACTIVE") return "success";
  if (status === "ARCHIVED") return "secondary";
  return "warning";
}

interface PromptFormData {
  code: string;
  name: string;
  description: string;
  stage: string;
  promptMode: string;
  systemPrompt: string;
  userPromptTemplate: string;
  outputSchema: string;
  providerRoute: string;
  modelName: string;
  fallbackModel: string;
  changeReason: string;
}

const EMPTY_FORM: PromptFormData = {
  code: "",
  name: "",
  description: "",
  stage: "LLM_CLUSTER_JUDGE_AND_ROUTING",
  promptMode: "BOTH",
  systemPrompt: "",
  userPromptTemplate: "",
  outputSchema: JSON.stringify({ decision: "string", confidence: "number", reason: "string" }, null, 2),
  providerRoute: "default",
  modelName: "gpt-4o",
  fallbackModel: "",
  changeReason: "",
};

type DialogMode = "create" | "edit" | null;

export function PromptsPage() {
  const promptsQuery = usePromptsQuery();
  const createMutation = useCreatePromptMutation();
  const updateMutation = useUpdatePromptMutation();
  const deleteMutation = useDeletePromptMutation();
  const testMutation = useTestPromptMutation();
  const activateMutation = useActivatePromptMutation();

  const prompts = promptsQuery.data ?? [];

  const [activeTab, setActiveTab] = useState<string>("classifier");

  const filteredPrompts = useMemo(() => {
    const stage = activeTab === "classifier" ? "LLM_CLUSTER_JUDGE_AND_ROUTING" : "KNOWLEDGE_GENERATION";
    return prompts.filter((p) => p.stage === stage);
  }, [prompts, activeTab]);

  const [dialogMode, setDialogMode] = useState<DialogMode>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<PromptFormData>(EMPTY_FORM);

  const [deleteTarget, setDeleteTarget] = useState<Prompt | null>(null);

  const [selectedPromptId, setSelectedPromptId] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);
  const [showVersions, setShowVersions] = useState<string | null>(null);

  const selectedPrompt = useMemo(() => prompts.find((p) => p.id === selectedPromptId) ?? null, [prompts, selectedPromptId]);

  const versionsQuery = usePromptVersionsQuery(showVersions ?? "");
  const rollbackMutation = useRollbackPromptMutation();

  const [testDialogOpen, setTestDialogOpen] = useState(false);
  const [testPromptId, setTestPromptId] = useState<string | null>(null);
  const [testMode, setTestMode] = useState<string>("CLUSTER");
  const [testResult, setTestResult] = useState<TestPromptResult | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  function openCreate() {
    const stage = activeTab === "classifier" ? "LLM_CLUSTER_JUDGE_AND_ROUTING" : "KNOWLEDGE_GENERATION";
    setForm({ ...EMPTY_FORM, stage });
    setEditingId(null);
    setDialogMode("create");
  }

  function openEdit(p: Prompt) {
    setForm({
      code: p.code ?? "",
      name: p.name,
      description: p.description ?? "",
      stage: p.stage ?? (p.type === "GUIDE_GENERATOR" ? "KNOWLEDGE_GENERATION" : "LLM_CLUSTER_JUDGE_AND_ROUTING"),
      promptMode: p.promptMode ?? "BOTH",
      systemPrompt: p.systemPrompt ?? "",
      userPromptTemplate: p.content,
      outputSchema: p.outputSchema ?? "",
      providerRoute: p.providerRoute ?? "default",
      modelName: p.modelName ?? "",
      fallbackModel: p.fallbackModel ?? "",
      changeReason: "",
    });
    setEditingId(p.id);
    setDialogMode("edit");
  }

  function closeDialog() { setDialogMode(null); setEditingId(null); }

  function handleSubmit() {
    if (dialogMode === "create") {
      const body: CreatePromptRequest = {
        code: form.code || form.name.toLowerCase().replace(/[^a-z0-9_]/g, "_"),
        name: form.name,
        description: form.description || undefined,
        stage: form.stage,
        promptMode: form.promptMode || undefined,
        systemPrompt: form.systemPrompt || undefined,
        userPromptTemplate: form.userPromptTemplate,
        outputSchema: form.outputSchema || undefined,
        providerRoute: form.providerRoute || undefined,
        modelName: form.modelName || undefined,
        fallbackModel: form.fallbackModel || undefined,
      };
      createMutation.mutate(body, { onSuccess: () => closeDialog() });
    } else if (dialogMode === "edit" && editingId) {
      updateMutation.mutate({
        id: editingId,
        name: form.name,
        description: form.description || undefined,
        userPromptTemplate: form.userPromptTemplate,
        systemPrompt: form.systemPrompt || undefined,
        outputSchema: form.outputSchema || undefined,
        providerRoute: form.providerRoute || undefined,
        modelName: form.modelName || undefined,
        fallbackModel: form.fallbackModel || undefined,
        changeReason: form.changeReason || undefined,
      }, { onSuccess: () => closeDialog() });
    }
  }

  function handleDelete() {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
  }

  function handleActivate(id: string) {
    activateMutation.mutate(id);
  }

  function handleRollback(version: number) {
    if (!showVersions) return;
    rollbackMutation.mutate({ id: showVersions, version }, { onSuccess: () => setShowVersions(null) });
  }

  function openTest(p: Prompt) {
    setTestPromptId(p.id);
    setTestMode(p.promptMode === "SINGLE_MESSAGE" ? "SINGLE_MESSAGE" : "CLUSTER");
    setTestResult(null);
    setTestError(null);
    setTestDialogOpen(true);
  }

  function handleTest() {
    if (!testPromptId) return;
    testMutation.mutate(
      { id: testPromptId, mode: testMode },
      {
        onSuccess: (r) => { setTestResult(r); setTestError(null); },
        onError: (err) => { setTestError(err instanceof Error ? err.message : "Unknown error"); setTestResult(null); },
      },
    );
  }

  function togglePreview(id: string) {
    if (selectedPromptId === id && showPreview) { setShowPreview(false); setSelectedPromptId(null); }
    else { setSelectedPromptId(id); setShowPreview(true); }
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Промпты"
        description="Prompt templates для LLM Judge и Knowledge Generation. Поддерживают CLUSTER и SINGLE_MESSAGE режимы, versioning, rollback."
      >
        <div className="flex gap-2">
          <Select value={activeTab} onValueChange={setActiveTab}>
            <SelectTrigger className="w-44"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="classifier">Judge & Routing</SelectItem>
              <SelectItem value="guide-generator">Generation</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </PageHeaderCard>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="hidden">
          <TabsTrigger value="classifier">Judge & Routing</TabsTrigger>
          <TabsTrigger value="guide-generator">Generation</TabsTrigger>
        </TabsList>

        <TabsContent value="classifier">
          <PromptTable
            prompts={filteredPrompts}
            onEdit={openEdit}
            onDelete={setDeleteTarget}
            onActivate={handleActivate}
            onPreview={togglePreview}
            onTest={openTest}
            onVersions={setShowVersions}
            selectedPromptId={showPreview ? selectedPromptId : null}
            onCreate={openCreate}
          />
        </TabsContent>

        <TabsContent value="guide-generator">
          <PromptTable
            prompts={filteredPrompts}
            onEdit={openEdit}
            onDelete={setDeleteTarget}
            onActivate={handleActivate}
            onPreview={togglePreview}
            onTest={openTest}
            onVersions={setShowVersions}
            selectedPromptId={showPreview ? selectedPromptId : null}
            onCreate={openCreate}
          />
        </TabsContent>
      </Tabs>

      {/* Preview */}
      {showPreview && selectedPrompt && (
        <Card>
          <CardContent className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Eye size={18} className="text-text-muted" />
                <h3 className="text-sm font-semibold text-text-strong">{selectedPrompt.name}</h3>
                <Badge variant="outline" className="text-xs">v{selectedPrompt.version}</Badge>
                <Badge variant="secondary" className="text-xs">{selectedPrompt.promptMode}</Badge>
              </div>
              <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => { setShowPreview(false); setSelectedPromptId(null); }}>
                Скрыть
              </Button>
            </div>
            {selectedPrompt.systemPrompt && (
              <div className="mb-2">
                <p className="text-xs text-text-muted font-medium mb-1">System prompt:</p>
                <pre className="rounded-xl border border-border-subtle bg-bg-app p-3 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto">{selectedPrompt.systemPrompt}</pre>
              </div>
            )}
            <p className="text-xs text-text-muted font-medium mb-1">User prompt template:</p>
            <pre className="rounded-xl border border-border-subtle bg-bg-app p-4 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto">{selectedPrompt.content}</pre>
            {selectedPrompt.outputSchema && (
              <div className="mt-2">
                <p className="text-xs text-text-muted font-medium mb-1">Output schema:</p>
                <pre className="rounded-xl border border-border-subtle bg-bg-app p-3 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto">{selectedPrompt.outputSchema}</pre>
              </div>
            )}
            <div className="mt-3 flex flex-wrap gap-2">
              <Badge variant="outline" className="text-[10px]">route: {selectedPrompt.providerRoute ?? "default"}</Badge>
              <Badge variant="outline" className="text-[10px]">model: {selectedPrompt.modelName ?? "—"}</Badge>
              {selectedPrompt.fallbackModel && <Badge variant="outline" className="text-[10px]">fallback: {selectedPrompt.fallbackModel}</Badge>}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Version history */}
      <Dialog open={showVersions !== null} onOpenChange={(o) => { if (!o) setShowVersions(null); }}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader><DialogTitle>История версий</DialogTitle></DialogHeader>
          <div className="flex flex-col gap-2">
            {(versionsQuery.data ?? []).map((v) => (
              <div key={v.id} className="rounded-xl bg-bg-elevated p-3 border border-border-subtle">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">v{v.version}</Badge>
                    <span className="text-xs text-text-muted">{v.createdAt}</span>
                    {v.changeReason && <span className="text-xs text-text-muted italic">— {v.changeReason}</span>}
                  </div>
                  <Button variant="outline" size="sm" className="text-xs h-7" onClick={() => handleRollback(v.version)}>
                    <ClockCounterClockwise weight="bold" /> Rollback
                  </Button>
                </div>
              </div>
            ))}
            {(versionsQuery.data ?? []).length === 0 && (
              <div className="text-center text-text-muted py-4">Нет истории версий</div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* Create/Edit Dialog */}
      <Dialog open={dialogMode !== null} onOpenChange={(o) => !o && closeDialog()}>
        <DialogContent className="sm:max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{dialogMode === "create" ? "Новый prompt template" : "Редактировать prompt template"}</DialogTitle>
            <DialogDescription>
              Prompt templates привязаны к stage конвейера. Поддерживают CLUSTER и SINGLE_MESSAGE режимы. Версия автоинкрементится при каждом сохранении.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Code</label>
                <Input placeholder="LLM_CLUSTER_JUDGE_AND_ROUTING" value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} />
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Название</label>
                <Input placeholder="LLM Cluster Judge & Routing" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
              </div>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Описание</label>
              <Input placeholder="Optional description" value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Stage</label>
                <Select value={form.stage} onValueChange={(v) => setForm((f) => ({ ...f, stage: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {STAGES.map((s) => <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Mode</label>
                <Select value={form.promptMode} onValueChange={(v) => setForm((f) => ({ ...f, promptMode: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {PROMPT_MODES.map((m) => <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Provider route</label>
                <Input placeholder="default" value={form.providerRoute} onChange={(e) => setForm((f) => ({ ...f, providerRoute: e.target.value }))} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Model name</label>
                <Input placeholder="gpt-4o" value={form.modelName} onChange={(e) => setForm((f) => ({ ...f, modelName: e.target.value }))} />
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Fallback model</label>
                <Input placeholder="Optional" value={form.fallbackModel} onChange={(e) => setForm((f) => ({ ...f, fallbackModel: e.target.value }))} />
              </div>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">System prompt</label>
              <Textarea className="min-h-20 font-mono-value text-sm" placeholder="You are a helpful assistant..." value={form.systemPrompt} onChange={(e) => setForm((f) => ({ ...f, systemPrompt: e.target.value }))} />
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">User prompt template</label>
              <Textarea className="min-h-32 font-mono-value text-sm" placeholder="Analyze the following message..." value={form.userPromptTemplate} onChange={(e) => setForm((f) => ({ ...f, userPromptTemplate: e.target.value }))} />
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Output schema (JSON)</label>
              <Textarea className="min-h-20 font-mono-value text-sm" placeholder='{"decision": "string", "confidence": "number"}' value={form.outputSchema} onChange={(e) => setForm((f) => ({ ...f, outputSchema: e.target.value }))} />
            </div>

            <Separator />
            <Input placeholder="Причина изменения" value={form.changeReason} onChange={(e) => setForm((f) => ({ ...f, changeReason: e.target.value }))} />

            <DialogFooter>
              <Button variant="outline" onClick={closeDialog}>Отмена</Button>
              <Button variant="primary" onClick={handleSubmit} disabled={createMutation.isPending || updateMutation.isPending || !form.name.trim() || !form.userPromptTemplate.trim()}>
                {dialogMode === "create" ? "Создать" : "Сохранить версию"}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete */}
      <Dialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Удалить prompt template</DialogTitle>
            <DialogDescription>
              Вы уверены, что хотите удалить <span className="font-semibold text-text-strong">&laquo;{deleteTarget?.name}&raquo;</span>? Это действие нельзя отменить.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>Отмена</Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}><Trash size={14} />Удалить</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Test */}
      <Dialog open={testDialogOpen} onOpenChange={setTestDialogOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Тестировать prompt template</DialogTitle>
            <DialogDescription>Выберите режим и запустите тест. Результат вернётся от AI провайдера.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Режим теста</label>
              <Select value={testMode} onValueChange={setTestMode}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="CLUSTER">Cluster (с контекстом кластера)</SelectItem>
                  <SelectItem value="SINGLE_MESSAGE">Single Message (одно сообщение)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Button variant="primary" onClick={handleTest} disabled={testMutation.isPending}>
              <Flask size={14} />{testMutation.isPending ? "Тестирование..." : "Протестировать"}
            </Button>

            {testError && <div className="rounded-lg border border-danger/30 bg-danger-soft/10 p-3 text-sm text-danger">{testError}</div>}

            {testResult && (
              <div className="grid gap-3">
                <div className="flex items-center gap-2">
                  <Badge variant={testResult.success ? "success" : "danger"}>{testResult.success ? "SUCCESS" : testResult.status}</Badge>
                  {testResult.decision && <Badge variant="outline">{testResult.decision}</Badge>}
                  {testResult.confidence > 0 && <span className="text-xs text-text-muted">confidence: {(testResult.confidence * 100).toFixed(0)}%</span>}
                </div>
                <div className="grid gap-1.5">
                  <label className="text-xs font-medium text-text-muted">Output</label>
                  <pre className="rounded-xl border border-border-subtle bg-bg-app p-4 text-xs font-mono-value text-text-muted whitespace-pre-wrap overflow-x-auto max-h-64 overflow-y-auto">{testResult.output}</pre>
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">Input tokens</span>
                    <span className="mt-1 block font-mono-value text-sm">{testResult.inputTokens.toLocaleString()}</span>
                  </div>
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">Output tokens</span>
                    <span className="mt-1 block font-mono-value text-sm">{testResult.outputTokens.toLocaleString()}</span>
                  </div>
                  <div className="rounded-lg border border-border-subtle p-3">
                    <span className="block text-[10px] uppercase text-text-muted">Est. cost</span>
                    <span className="mt-1 block font-mono-value text-sm">${testResult.estimatedCostUsd.toFixed(4)}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTestDialogOpen(false)}>Закрыть</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

interface PromptTableProps {
  prompts: Prompt[];
  onEdit: (p: Prompt) => void;
  onDelete: (p: Prompt) => void;
  onActivate: (id: string) => void;
  onPreview: (id: string) => void;
  onTest: (p: Prompt) => void;
  onVersions: (id: string) => void;
  selectedPromptId: string | null;
  onCreate: () => void;
}

function PromptTable({ prompts, onEdit, onDelete, onActivate, onPreview, onTest, onVersions, selectedPromptId, onCreate }: PromptTableProps) {
  if (prompts.length === 0) {
    return (
      <EmptyState
        icon={FileText}
        title="Нет prompt templates"
        description="Создайте prompt template для настройки запроса к LLM на этом этапе конвейера."
        action={<Button variant="primary" size="sm" onClick={onCreate}><Plus size={14} />Новый prompt</Button>}
      />
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
      {prompts.map((p) => {
        const isSelected = selectedPromptId === p.id;
        return (
          <Card key={p.id} className={isSelected ? "border-brand-blue/40 bg-brand-blue-soft/10" : ""}>
            <CardContent className="flex min-h-[280px] flex-col p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="truncate text-sm font-semibold text-text-strong">{p.name}</h3>
                    <Badge variant="outline" className="text-[10px]">v{p.version}</Badge>
                    <Badge variant="secondary" className="text-[10px]">{p.promptMode ?? "BOTH"}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-text-muted">{p.description || promptTypeDescription(p.type)}</p>
                </div>
                <Badge variant={promptStatusVariant(p.status)} dot>{promptStatusBadgeLabel(p.status)}</Badge>
              </div>

              <div className="mt-3 flex flex-wrap gap-1.5">
                <Badge variant="secondary" className="text-[10px]">{stageLabel(p.stage ?? "")}</Badge>
                <Badge variant="outline" className="text-[10px]">route: {p.providerRoute ?? "default"}</Badge>
                {p.modelName && <Badge variant="outline" className="text-[10px]">{p.modelName}</Badge>}
              </div>

              <p className="mt-4 line-clamp-5 rounded-xl border border-border-subtle bg-bg-app p-3 text-xs leading-relaxed text-text-muted">
                {p.content || "Содержимое пока пустое."}
              </p>

              <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-border-subtle pt-3">
                <div className="flex items-center gap-1">
                  {p.status !== "ACTIVE" && (
                    <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => onActivate(p.id)}>
                      <CheckCircle size={12} />Активировать
                    </Button>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onPreview(p.id)} title="Предпросмотр">
                    <Eye size={14} />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onVersions(p.id)} title="Версии">
                    <ClockCounterClockwise size={14} />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onTest(p)} title="Тест">
                    <Flask size={14} />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onEdit(p)} title="Редактировать">
                    <Pencil size={14} />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-7 w-7 text-danger hover:bg-danger-soft hover:text-danger" onClick={() => onDelete(p)} title="Удалить">
                    <Trash size={14} />
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        );
      })}

      <button type="button" onClick={onCreate}
        className="flex min-h-[260px] flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border-subtle bg-bg-app p-8 text-text-muted transition-colors hover:border-brand-blue/40 hover:bg-bg-card hover:text-brand-blue"
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-full border-2 border-current">
          <Plus size={24} />
        </div>
        <span className="text-sm font-medium">Новый prompt template</span>
      </button>
    </div>
  );
}
