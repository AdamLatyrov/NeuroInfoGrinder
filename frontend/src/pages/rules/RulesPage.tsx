import { useState } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
import { Switch } from "@/components/ui/switch";
import {
  type CreateClassifierRequest,
  type CreateRuleRequest,
  type UpdateClassifierRequest,
  type UpdateRuleRequest,
  useChainConfigQuery,
  useClassifiersQuery,
  useCreateClassifierMutation,
  useCreateRuleMutation,
  useDeleteClassifierMutation,
  useDeleteRuleMutation,
  useReorderClassifiersMutation,
  useReorderRulesMutation,
  useRulesQuery,
  useUpdateClassifierMutation,
  useUpdateClassifierStatusMutation,
  useUpdateRuleMutation,
} from "@/shared/api/classifiersApi";
import {
  displayClassifierStatus,
  tryParseJson,
  type Classifier,
  type Rule,
} from "@/shared/types";
import { Funnel, Plus, Pencil, Trash, Play, Power, ArrowUp, ArrowDown } from "@phosphor-icons/react";

// ── Constants ──

const CONDITION_TYPES = [
  "TEXT_CONTAINS",
  "KEYWORD_MATCH",
  "REGEX_MATCH",
  "LENGTH_GT",
  "LENGTH_LT",
  "SENDER_IS_BOT",
  "HAS_TOPIC",
  "GROUP_MATCH",
] as const;

const ACTION_TYPES = ["SEND_TO_LLM", "CLASSIFY", "SKIP", "MARK_REVIEW"] as const;

const RULE_STATUSES = ["ACTIVE", "DRAFT", "DISABLED"] as const;

// ── Form types ──

interface ConditionEntry {
  type: string;
  value: string;
}

interface ActionEntry {
  type: string;
  classifierId: string;
  threshold: string;
}

interface RuleFormData {
  name: string;
  description: string;
  actionType: string;
  order: number;
  status: string;
  conditions: ConditionEntry[];
  actions: ActionEntry[];
}

interface ClassifierFormData {
  name: string;
  type: string;
  version: string;
  keywords: string;
  regex: string;
}

// ── Helpers ──

function parseConditionsFromRule(rule: Rule): ConditionEntry[] {
  const parsed = tryParseJson<{ type: string; value?: string }[]>(rule.conditionsJson);
  if (!parsed || parsed.length === 0) return [{ type: "TEXT_CONTAINS", value: "" }];
  return parsed.map((c) => ({ type: c.type, value: c.value ?? "" }));
}

function parseActionsFromRule(rule: Rule): ActionEntry[] {
  const parsed = tryParseJson<{ type: string; classifierId?: string; threshold?: number }[]>(
    rule.actionsJson,
  );
  if (!parsed || parsed.length === 0) return [{ type: "SEND_TO_LLM", classifierId: "", threshold: "" }];
  return parsed.map((a) => ({
    type: a.type,
    classifierId: a.classifierId ?? "",
    threshold: a.threshold !== undefined ? String(a.threshold) : "",
  }));
}

function ruleToFormData(rule: Rule): RuleFormData {
  return {
    name: rule.name,
    description: rule.description ?? "",
    actionType: rule.actionType,
    order: rule.ruleOrder,
    status: rule.status,
    conditions: parseConditionsFromRule(rule),
    actions: parseActionsFromRule(rule),
  };
}

const emptyRuleForm: RuleFormData = {
  name: "",
  description: "",
  actionType: "INCLUDE",
  order: 10,
  status: "DRAFT",
  conditions: [{ type: "TEXT_CONTAINS", value: "" }],
  actions: [{ type: "SEND_TO_LLM", classifierId: "", threshold: "" }],
};

const emptyClassifierForm: ClassifierFormData = {
  name: "",
  type: "KEYWORD",
  version: "v1",
  keywords: "",
  regex: "",
};

function displayRuleStatus(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "Healthy",
    DRAFT: "Needs review",
    DISABLED: "Disabled",
  };
  return map[status] ?? status;
}

// ── Dialog mode types ──

type RuleDialogMode = "create" | "edit" | null;
type ClassifierDialogMode = "create" | "edit" | null;
type DeleteTarget =
  | { type: "rule"; id: string; name: string }
  | { type: "classifier"; id: string; name: string }
  | null;

// ── Component ──

export function RulesPage() {
  // ── Dialog state ──
  const [ruleDialogMode, setRuleDialogMode] = useState<RuleDialogMode>(null);
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null);
  const [ruleForm, setRuleForm] = useState<RuleFormData>(emptyRuleForm);

  const [classifierDialogMode, setClassifierDialogMode] = useState<ClassifierDialogMode>(null);
  const [editingClassifierId, setEditingClassifierId] = useState<string | null>(null);
  const [classifierForm, setClassifierForm] = useState<ClassifierFormData>(emptyClassifierForm);

  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null);

  // ── Queries ──
  const classifiersQuery = useClassifiersQuery();
  const rulesQuery = useRulesQuery();
  const chainConfigQuery = useChainConfigQuery();

  // ── Mutations ──
  const createClassifierMutation = useCreateClassifierMutation();
  const updateClassifierMutation = useUpdateClassifierMutation();
  const deleteClassifierMutation = useDeleteClassifierMutation();
  const updateClassifierStatusMutation = useUpdateClassifierStatusMutation();

  const createRuleMutation = useCreateRuleMutation();
  const updateRuleMutation = useUpdateRuleMutation();
  const deleteRuleMutation = useDeleteRuleMutation();
  const reorderRulesMutation = useReorderRulesMutation();
  const reorderClassifiersMutation = useReorderClassifiersMutation();

  // ── Derived data ──
  const classifiers = classifiersQuery.data ?? [];
  const rules = rulesQuery.data ?? [];
  const chainConfig = chainConfigQuery.data;

  // ── Rule dialog handlers ──

  function openCreateRule() {
    setRuleForm({ ...emptyRuleForm });
    setEditingRuleId(null);
    setRuleDialogMode("create");
  }

  function openEditRule(rule: Rule) {
    setRuleForm(ruleToFormData(rule));
    setEditingRuleId(rule.id);
    setRuleDialogMode("edit");
  }

  function closeRuleDialog() {
    setRuleDialogMode(null);
    setEditingRuleId(null);
  }

  function submitRuleForm() {
    const conditionsJson = JSON.stringify(
      ruleForm.conditions
        .filter((c) => c.type)
        .map((c) => ({ type: c.type, ...(c.value ? { value: c.value } : {}) })),
    );
    const actionsJson = JSON.stringify(
      ruleForm.actions
        .filter((a) => a.type)
        .map((a) => ({
          type: a.type,
          ...(a.classifierId ? { classifierId: a.classifierId } : {}),
          ...(a.threshold ? { threshold: Number(a.threshold) } : {}),
        })),
    );

    if (ruleDialogMode === "create") {
      createRuleMutation.mutate(
        {
          order: ruleForm.order,
          conditions: conditionsJson,
          actions: actionsJson,
          name: ruleForm.name,
          description: ruleForm.description,
          actionType: ruleForm.actionType,
          status: ruleForm.status,
        } as CreateRuleRequest,
        { onSuccess: () => closeRuleDialog() },
      );
    } else if (ruleDialogMode === "edit" && editingRuleId) {
      const body: UpdateRuleRequest = {
        name: ruleForm.name,
        description: ruleForm.description,
        actionType: ruleForm.actionType,
        order: ruleForm.order,
        conditions: conditionsJson,
        actions: actionsJson,
      };
      updateRuleMutation.mutate(
        { id: editingRuleId, ...body },
        { onSuccess: () => closeRuleDialog() },
      );
    }
  }

  // ── Classifier dialog handlers ──

  function openCreateClassifier() {
    setClassifierForm({ ...emptyClassifierForm });
    setEditingClassifierId(null);
    setClassifierDialogMode("create");
  }

  function openEditClassifier(classifier: Classifier) {
    setClassifierForm({
      name: classifier.name,
      type: classifier.type,
      version: classifier.version,
      keywords: classifier.type === "KEYWORD" ? (classifier.regexPattern ?? "") : "",
      regex: classifier.type === "REGEX" ? (classifier.regexPattern ?? "") : "",
    });
    setEditingClassifierId(classifier.id);
    setClassifierDialogMode("edit");
  }

  function closeClassifierDialog() {
    setClassifierDialogMode(null);
    setEditingClassifierId(null);
  }

  function submitClassifierForm() {
    if (classifierDialogMode === "create") {
      const body: CreateClassifierRequest = {
        name: classifierForm.name,
        type: classifierForm.type,
        version: classifierForm.version || "v1",
        keywords: classifierForm.type === "KEYWORD" ? classifierForm.keywords : undefined,
        regex: classifierForm.type === "REGEX" ? classifierForm.regex : undefined,
      };
      createClassifierMutation.mutate(body, { onSuccess: () => closeClassifierDialog() });
    } else if (classifierDialogMode === "edit" && editingClassifierId) {
      const body: UpdateClassifierRequest = {
        name: classifierForm.name,
        type: classifierForm.type,
        version: classifierForm.version,
        keywords: classifierForm.type === "KEYWORD" ? classifierForm.keywords : undefined,
        regex: classifierForm.type === "REGEX" ? classifierForm.regex : undefined,
      };
      updateClassifierMutation.mutate(
        { id: editingClassifierId, ...body },
        { onSuccess: () => closeClassifierDialog() },
      );
    }
  }

  // ── Delete handler ──

  function confirmDelete() {
    if (!deleteTarget) return;
    if (deleteTarget.type === "rule") {
      deleteRuleMutation.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
    } else {
      deleteClassifierMutation.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
    }
  }

  // ── Toggle handlers ──

  function toggleRuleStatus(rule: Rule) {
    const next = rule.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    updateRuleMutation.mutate({ id: rule.id, status: next });
  }

  function moveRule(rule: Rule, direction: "up" | "down") {
    const sortedRules = [...rules].sort((a, b) => a.ruleOrder - b.ruleOrder);
    const idx = sortedRules.findIndex((r) => r.id === rule.id);
    if (direction === "up" && idx <= 0) return;
    if (direction === "down" && idx >= sortedRules.length - 1) return;
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    const newOrder = [...sortedRules];
    [newOrder[idx], newOrder[swapIdx]] = [newOrder[swapIdx], newOrder[idx]];
    reorderRulesMutation.mutate(newOrder.map((r) => r.id));
  }

  function toggleClassifierStatus(classifier: Classifier) {
    const next = classifier.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    updateClassifierStatusMutation.mutate({ id: classifier.id, status: next });
  }

  function moveClassifier(classifier: Classifier, direction: "up" | "down") {
    const sorted = [...classifiers].sort((a, b) => a.order - b.order);
    const idx = sorted.findIndex((c) => c.id === classifier.id);
    if (direction === "up" && idx <= 0) return;
    if (direction === "down" && idx >= sorted.length - 1) return;
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    const newOrder = [...sorted];
    [newOrder[idx], newOrder[swapIdx]] = [newOrder[swapIdx], newOrder[idx]];
    reorderClassifiersMutation.mutate(newOrder.map((c) => c.id));
  }

  // ── Rule form helpers ──

  function updateCondition(index: number, patch: Partial<ConditionEntry>) {
    setRuleForm((f) => ({
      ...f,
      conditions: f.conditions.map((c, i) => (i === index ? { ...c, ...patch } : c)),
    }));
  }

  function removeCondition(index: number) {
    setRuleForm((f) => ({
      ...f,
      conditions: f.conditions.length > 1 ? f.conditions.filter((_, i) => i !== index) : f.conditions,
    }));
  }

  function addCondition() {
    setRuleForm((f) => ({
      ...f,
      conditions: [...f.conditions, { type: "TEXT_CONTAINS", value: "" }],
    }));
  }

  function updateAction(index: number, patch: Partial<ActionEntry>) {
    setRuleForm((f) => ({
      ...f,
      actions: f.actions.map((a, i) => (i === index ? { ...a, ...patch } : a)),
    }));
  }

  function removeAction(index: number) {
    setRuleForm((f) => ({
      ...f,
      actions: f.actions.length > 1 ? f.actions.filter((_, i) => i !== index) : f.actions,
    }));
  }

  function addAction() {
    setRuleForm((f) => ({
      ...f,
      actions: [...f.actions, { type: "SEND_TO_LLM", classifierId: "", threshold: "" }],
    }));
  }

  // ── Render ──

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Правила"
        description="Классификаторы, правила pipeline и конфигурация цепочек"
        actions={{
          primary: {
            label: "Добавить",
            items: [
              { label: "Правило", onClick: openCreateRule },
              { label: "Классификатор", onClick: openCreateClassifier },
            ],
          },
          onRefresh: () => {
            classifiersQuery.refetch();
            rulesQuery.refetch();
            chainConfigQuery.refetch();
          },
        }}
      />
      <Card>
        <CardContent className="p-4 text-sm text-text-muted">
          This page shows DB-managed rules and classifier config.
          Built-in runtime context-aware signals run on the backend during pipeline execution.
          See Pipeline Classified/Skipped for labels, reasoning, and context hash.
        </CardContent>
      </Card>


      <Tabs defaultValue="rules">
        <TabsList>
          <TabsTrigger value="rules">Правила</TabsTrigger>
          <TabsTrigger value="classifiers">Классификаторы</TabsTrigger>
          <TabsTrigger value="chains">Настройки цепочек</TabsTrigger>
        </TabsList>

        {/* ── RULES TAB ── */}
        <TabsContent value="rules">
          <div className="flex flex-col gap-4">
            {rules.length === 0 ? (
              <EmptyState
                icon={Funnel}
                title="Нет правил"
                description="Создайте правило для маршрутизации и обработки сообщений."
                action={
                  <Button variant="primary" size="sm" onClick={openCreateRule}>
                    Добавить правило
                  </Button>
                }
              />
            ) : (
              rules.map((rule) => {
                const conditions =
                  tryParseJson<{ type: string; value?: string }[]>(rule.conditionsJson) ?? [];
                const actions =
                  tryParseJson<{
                    type: string;
                    classifierName?: string;
                    threshold?: number;
                    classifierId?: string;
                  }[]>(rule.actionsJson) ?? [];

                return (
                  <Card key={rule.id}>
                    <CardContent className="p-5">
                      {/* Header */}
                      <div className="mb-4 flex items-start justify-between gap-4">
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex flex-wrap items-center gap-2">
                            <span className="text-sm font-semibold text-text-strong">
                              {rule.name}
                            </span>
                            <Badge
                              variant={rule.actionType === "INCLUDE" ? "success" : "danger"}
                              className="text-xs"
                            >
                              {rule.actionType}
                            </Badge>
                            <Badge variant="outline" className="text-xs">
                              #{rule.ruleOrder}
                            </Badge>
                          </div>
                          {rule.description && (
                            <p className="text-xs text-text-muted">{rule.description}</p>
                          )}
                        </div>
                        <div className="shrink-0">
                          <StatusBadge status={displayRuleStatus(rule.status)} />
                        </div>
                      </div>

                      {/* Conditions */}
                      <div className="mb-3">
                        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
                          Если
                        </div>
                        <div className="flex flex-col gap-1.5">
                          {conditions.map((condition, index) => (
                            <div
                              key={`${rule.id}-cond-${index}`}
                              className="flex items-center gap-2 rounded-lg border border-border-subtle px-3 py-2 text-sm"
                            >
                              <Badge variant="outline" className="shrink-0 text-xs">
                                {condition.type}
                              </Badge>
                              <span className="text-text-strong">
                                {condition.value ?? "—"}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Actions */}
                      <div className="mb-4">
                        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
                          То
                        </div>
                        <div className="flex flex-col gap-1.5">
                          {actions.map((action, index) => (
                            <div
                              key={`${rule.id}-act-${index}`}
                              className="flex items-center gap-2 rounded-lg border border-border-subtle px-3 py-2 text-sm"
                            >
                              <Badge variant="default" className="shrink-0 text-xs">
                                {action.type}
                              </Badge>
                              <span className="text-text-strong">
                                {action.classifierName ?? ""}
                              </span>
                              {action.threshold !== undefined && (
                                <span className="font-mono-value text-text-muted">
                                  score &gt; {action.threshold}
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Action buttons */}
                      <div className="flex flex-wrap items-center gap-2 border-t border-border-subtle pt-3">
                        <Button
                          variant="outline"
                          size="icon"
                          className="h-7 w-7"
                          onClick={() => moveRule(rule, "up")}
                          disabled={rule.ruleOrder === Math.min(...rules.map((r) => r.ruleOrder))}
                          title="Выше"
                        >
                          <ArrowUp size={14} weight="regular" />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          className="h-7 w-7"
                          onClick={() => moveRule(rule, "down")}
                          disabled={rule.ruleOrder === Math.max(...rules.map((r) => r.ruleOrder))}
                          title="Ниже"
                        >
                          <ArrowDown size={14} weight="regular" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1.5"
                          onClick={() => openEditRule(rule)}
                        >
                          <Pencil size={14} weight="regular" />
                          Редактировать
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1.5"
                          onClick={() => toggleRuleStatus(rule)}
                        >
                          <Power size={14} weight="regular" />
                          {rule.status === "ACTIVE" ? "Отключить" : "Включить"}
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1.5"
                          onClick={() => alert("Тест правила — заглушка")}
                        >
                          <Play size={14} weight="regular" />
                          Тест
                        </Button>
                        <div className="flex-1" />
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1.5 text-danger hover:text-danger"
                          onClick={() =>
                            setDeleteTarget({
                              type: "rule",
                              id: rule.id,
                              name: rule.name,
                            })
                          }
                        >
                          <Trash size={14} weight="regular" />
                          Удалить
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                );
              })
            )}

            <Button variant="outline" className="self-start gap-1.5" onClick={openCreateRule}>
              <Plus size={16} weight="regular" />
              Добавить правило
            </Button>
          </div>
        </TabsContent>

        {/* ── CLASSIFIERS TAB ── */}
        <TabsContent value="classifiers">
          {classifiers.length === 0 ? (
            <EmptyState
              icon={Funnel}
              title="Нет классификаторов"
              description="Создайте классификатор для маршрутизации сообщений."
              action={
                <Button variant="primary" size="sm" onClick={openCreateClassifier}>
                  Добавить классификатор
                </Button>
              }
            />
          ) : (
            <div className="rounded-2xl border border-border-subtle bg-bg-card">
              <div className="grid grid-cols-9 gap-4 border-b border-border-subtle px-4 py-3 text-xs font-semibold uppercase text-text-muted">
                <span>Название</span>
                <span>Тип</span>
                <span>Provider</span>
                <span>Prompt</span>
                <span>Pattern</span>
                <span>Версия</span>
                <span>Статус</span>
                <span className="text-right">Rules</span>
                <span className="text-right">Действия</span>
              </div>
              {classifiers.map((classifier) => (
                <div
                  key={classifier.id}
                  className="grid grid-cols-9 gap-4 border-b border-border-subtle px-4 py-3 text-sm last:border-b-0 items-center"
                >
                  <span className="font-medium text-text-strong truncate">
                    {classifier.name}
                  </span>
                  <span>{classifier.type}</span>
                  <span>{classifier.providerId ?? "—"}</span>
                  <span>{classifier.promptId ?? "—"}</span>
                  <span className="truncate text-text-muted">
                    {classifier.regexPattern ?? "—"}
                  </span>
                  <span>{classifier.version}</span>
                  <span>
                    <StatusBadge status={displayClassifierStatus(classifier.status)} />
                  </span>
                  <span className="text-right font-mono-value">
                    {classifier.successRate ? `${classifier.successRate}` : "0"}
                  </span>
                  <span className="flex items-center justify-end gap-1">
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => moveClassifier(classifier, "up")}
                      disabled={classifier.order === Math.min(...classifiers.map((c) => c.order))}
                      title="Выше"
                    >
                      <ArrowUp size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => moveClassifier(classifier, "down")}
                      disabled={classifier.order === Math.max(...classifiers.map((c) => c.order))}
                      title="Ниже"
                    >
                      <ArrowDown size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => openEditClassifier(classifier)}
                      title="Редактировать"
                    >
                      <Pencil size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => toggleClassifierStatus(classifier)}
                      title={classifier.status === "ACTIVE" ? "Отключить" : "Включить"}
                    >
                      <Power size={14} weight="regular" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-7 w-7 text-danger hover:text-danger"
                      onClick={() =>
                        setDeleteTarget({
                          type: "classifier",
                          id: classifier.id,
                          name: classifier.name,
                        })
                      }
                      title="Удалить"
                    >
                      <Trash size={14} weight="regular" />
                    </Button>
                  </span>
                </div>
              ))}
            </div>
          )}
        </TabsContent>

        {/* ── CHAIN CONFIG TAB ── */}
        <TabsContent value="chains">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Настройки цепочек сообщений
              </h3>
              {chainConfig ? (
                <div className="grid max-w-lg grid-cols-2 gap-4">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-text-muted">Включать replies</span>
                    <Badge variant={chainConfig.includeReplies ? "success" : "outline"}>
                      {chainConfig.includeReplies ? "Да" : "Нет"}
                    </Badge>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-text-muted">Time window (мин)</span>
                    <span className="font-mono-value">{chainConfig.timeWindowMinutes}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-text-muted">Мин. сообщений</span>
                    <span className="font-mono-value">
                      {chainConfig.minMessagesForProcessing}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-text-muted">Макс. сообщений</span>
                    <span className="font-mono-value">{chainConfig.maxMessagesPerChain}</span>
                  </div>
                </div>
              ) : (
                <div className="py-4 text-center text-sm text-text-weak">
                  Chain configuration not available
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* ── RULE CREATE / EDIT DIALOG ── */}
      <Dialog
        open={ruleDialogMode !== null}
        onOpenChange={(open) => !open && closeRuleDialog()}
      >
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {ruleDialogMode === "create" ? "Новое правило" : "Редактировать правило"}
            </DialogTitle>
            <DialogDescription>
              Задайте условия и действия для правила pipeline.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            {/* Name */}
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Название</label>
              <Input
                placeholder="Название правила"
                value={ruleForm.name}
                onChange={(e) => setRuleForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>

            {/* Description */}
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Описание</label>
              <Textarea
                className="min-h-20"
                placeholder="Описание правила"
                value={ruleForm.description}
                onChange={(e) => setRuleForm((f) => ({ ...f, description: e.target.value }))}
              />
            </div>

            {/* Action type + Order */}
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Тип действия</label>
                <Select
                  value={ruleForm.actionType}
                  onValueChange={(v) => setRuleForm((f) => ({ ...f, actionType: v }))}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INCLUDE">INCLUDE</SelectItem>
                    <SelectItem value="EXCLUDE">EXCLUDE</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <label className="text-xs font-medium text-text-muted">Порядок</label>
                <Input
                  type="number"
                  value={ruleForm.order}
                  onChange={(e) =>
                    setRuleForm((f) => ({ ...f, order: Number(e.target.value) }))
                  }
                />
              </div>
            </div>

            {/* Status */}
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Статус</label>
              <Select
                value={ruleForm.status}
                onValueChange={(v) => setRuleForm((f) => ({ ...f, status: v }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RULE_STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* ── Conditions ── */}
            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-text-muted">Условия</label>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 gap-1 text-xs"
                  onClick={addCondition}
                >
                  <Plus size={12} weight="regular" />
                  Добавить
                </Button>
              </div>
              {ruleForm.conditions.map((condition, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Select
                    value={condition.type}
                    onValueChange={(v) => updateCondition(index, { type: v })}
                  >
                    <SelectTrigger className="w-44 shrink-0">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CONDITION_TYPES.map((ct) => (
                        <SelectItem key={ct} value={ct}>
                          {ct}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input
                    className="flex-1"
                    placeholder="Значение"
                    value={condition.value}
                    onChange={(e) => updateCondition(index, { value: e.target.value })}
                  />
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 shrink-0 text-danger hover:text-danger"
                    onClick={() => removeCondition(index)}
                    disabled={ruleForm.conditions.length <= 1}
                  >
                    <Trash size={14} weight="regular" />
                  </Button>
                </div>
              ))}
            </div>

            {/* ── Actions ── */}
            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-text-muted">Действия</label>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 gap-1 text-xs"
                  onClick={addAction}
                >
                  <Plus size={12} weight="regular" />
                  Добавить
                </Button>
              </div>
              {ruleForm.actions.map((action, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Select
                    value={action.type}
                    onValueChange={(v) => updateAction(index, { type: v })}
                  >
                    <SelectTrigger className="w-36 shrink-0">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {ACTION_TYPES.map((at) => (
                        <SelectItem key={at} value={at}>
                          {at}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input
                    className="flex-1"
                    placeholder="Classifier ID"
                    value={action.classifierId}
                    onChange={(e) => updateAction(index, { classifierId: e.target.value })}
                  />
                  <Input
                    className="w-24 shrink-0"
                    type="number"
                    placeholder="Threshold"
                    value={action.threshold}
                    onChange={(e) => updateAction(index, { threshold: e.target.value })}
                  />
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 shrink-0 text-danger hover:text-danger"
                    onClick={() => removeAction(index)}
                    disabled={ruleForm.actions.length <= 1}
                  >
                    <Trash size={14} weight="regular" />
                  </Button>
                </div>
              ))}
            </div>

            {/* Submit */}
            <Button
              variant="primary"
              onClick={submitRuleForm}
              disabled={
                createRuleMutation.isPending ||
                updateRuleMutation.isPending ||
                !ruleForm.name.trim()
              }
            >
              {ruleDialogMode === "create" ? "Создать правило" : "Сохранить"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* ── CLASSIFIER CREATE / EDIT DIALOG ── */}
      <Dialog
        open={classifierDialogMode !== null}
        onOpenChange={(open) => !open && closeClassifierDialog()}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {classifierDialogMode === "create"
                ? "Новый классификатор"
                : "Редактировать классификатор"}
            </DialogTitle>
            <DialogDescription>
              Для keyword достаточно заполнить Keywords, для regex — поле Regex.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <Input
              placeholder="Name"
              value={classifierForm.name}
              onChange={(e) =>
                setClassifierForm((f) => ({ ...f, name: e.target.value }))
              }
            />
            <Select
              value={classifierForm.type}
              onValueChange={(v) =>
                setClassifierForm((f) => ({ ...f, type: v }))
              }
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="KEYWORD">KEYWORD</SelectItem>
                <SelectItem value="REGEX">REGEX</SelectItem>
                <SelectItem value="LLM">LLM</SelectItem>
              </SelectContent>
            </Select>
            <Input
              placeholder="Version"
              value={classifierForm.version}
              onChange={(e) =>
                setClassifierForm((f) => ({ ...f, version: e.target.value }))
              }
            />
            <Input
              placeholder="Keywords"
              value={classifierForm.keywords}
              onChange={(e) =>
                setClassifierForm((f) => ({ ...f, keywords: e.target.value }))
              }
            />
            <Input
              placeholder="Regex"
              value={classifierForm.regex}
              onChange={(e) =>
                setClassifierForm((f) => ({ ...f, regex: e.target.value }))
              }
            />
            <Button
              variant="primary"
              onClick={submitClassifierForm}
              disabled={
                createClassifierMutation.isPending ||
                updateClassifierMutation.isPending ||
                !classifierForm.name.trim()
              }
            >
              {classifierDialogMode === "create"
                ? "Добавить классификатор"
                : "Сохранить"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* ── DELETE CONFIRMATION DIALOG ── */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Подтвердите удаление</DialogTitle>
            <DialogDescription>
              {deleteTarget?.type === "rule"
                ? `Удалить правило «${deleteTarget.name}»? Это действие нельзя отменить.`
                : `Удалить классификатор «${deleteTarget?.name}»? Это действие нельзя отменить.`}
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Отмена
            </Button>
            <Button
              variant="primary"
              className="bg-danger hover:bg-danger/90"
              onClick={confirmDelete}
              disabled={deleteRuleMutation.isPending || deleteClassifierMutation.isPending}
            >
              Удалить
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
