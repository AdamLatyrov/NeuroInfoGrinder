import { useMemo, useState } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  type CreateClassifierRequest,
  type CreateRuleRequest,
  type UpdateClassifierRequest,
  type UpdateRuleRequest,
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
  useUpdateRuleStatusMutation,
} from "@/shared/api/classifiersApi";
import { usePromptsQuery } from "@/shared/api/promptsApi";
import { useProvidersQuery } from "@/shared/api/providersApi";
import { tryParseJson, type Classifier, type Rule } from "@/shared/types";
import { Brain, Funnel, Pencil, Plus, Power, Scales, Trash, ArrowUp, ArrowDown } from "@phosphor-icons/react";

const DEFAULT_LINEAR_MODEL = {
  bias: -0.35,
  threshold: 0.58,
  normalization: "sigmoid",
  features: {
    text_length_norm: 0.25,
    reply_count_norm: 0.15,
    has_ai_marker: 1.1,
    has_value_marker: 0.75,
    has_guide_marker: 0.65,
    has_tool_marker: 0.55,
    has_release_marker: 0.45,
    has_url: 0.2,
    has_topic: 0.1,
    is_not_bot: 0.1,
    has_code_marker: 0.2,
    has_deadline_marker: 0.35,
    has_price_marker: 0.3,
    has_question_marker: -0.3,
  },
};

type LinearFeature = { name: string; weight: string };

interface LinearForm {
  name: string;
  version: string;
  order: string;
  bias: string;
  threshold: string;
  normalization: string;
  features: LinearFeature[];
}

function linearConfigToForm(classifier?: Classifier): LinearForm {
  const parsed = tryParseJson<{
    bias?: number;
    threshold?: number;
    normalization?: string;
    features?: Record<string, number>;
  }>(classifier?.modelConfig ?? null);
  const config = {
    ...DEFAULT_LINEAR_MODEL,
    ...parsed,
    features: parsed?.features ?? DEFAULT_LINEAR_MODEL.features,
  };
  return {
    name: classifier?.name ?? "AI useful content linear",
    version: classifier?.version ?? "1.0",
    order: String(classifier?.order ?? 10),
    bias: String(config.bias),
    threshold: String(config.threshold),
    normalization: config.normalization ?? "sigmoid",
    features: Object.entries(config.features).map(([name, weight]) => ({ name, weight: String(weight) })),
  };
}

function linearFormToConfig(form: LinearForm) {
  return JSON.stringify(
    {
      bias: Number(form.bias) || 0,
      threshold: Number(form.threshold) || 0,
      normalization: form.normalization || "sigmoid",
      features: Object.fromEntries(
        form.features
          .filter((feature) => feature.name.trim())
          .map((feature) => [feature.name.trim(), Number(feature.weight) || 0]),
      ),
    },
    null,
    2,
  );
}

function classifierTypeLabel(type: string) {
  const labels: Record<string, string> = {
    KEYWORD: "Ключевые слова",
    REGEX: "Регулярка",
    LLM: "LLM",
    LINEAR_MODEL: "Линейный скоринг",
  };
  return labels[type] ?? type;
}

function entityStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Включено",
    DRAFT: "Черновик",
    DISABLED: "Выключено",
  };
  return labels[status] ?? status;
}

function entityStatusVariant(status: string) {
  if (status === "ACTIVE") return "success";
  if (status === "DISABLED") return "secondary";
  return "warning";
}

function ruleName(name: string) {
  const names: Record<string, string> = {
    "Exclude ultra short noise": "Отсечь совсем короткий шум",
    "Exclude tiny noise": "Отсечь короткий шум",
    "Exclude reaction chatter": "Отсечь реакции и флуд",
    "Exclude short bot noise": "Отсечь короткие сообщения ботов",
    "Include AI value signals": "Пропустить AI-сигналы",
    "Include how-to markers": "Пропустить how-to / гайды",
  };
  return names[name] ?? name;
}

function ruleDescription(rule: Rule) {
  const descriptions: Record<string, string> = {
    "Exclude ultra short noise": "Сообщения короче 5 символов почти всегда мусор.",
    "Exclude tiny noise": "Сообщения короче 8 символов обычно не несут полезной инструкции.",
    "Exclude reaction chatter": "Короткие реакции вроде «ок», «лол», «понял» не должны идти дальше.",
    "Exclude short bot noise": "Короткие сообщения от ботов отсекаются до классификации.",
    "Include AI value signals": "Сообщения про модели, лимиты, API, релизы и доступы помечаются как кандидаты.",
    "Include how-to markers": "Сообщения с маркерами инструкций и гайдов явно пропускаются дальше.",
  };
  if (rule.description && !/[ÐÑ]/.test(rule.description)) return rule.description;
  return descriptions[rule.name] ?? "Пользовательское правило.";
}

function conditionLabel(type: string) {
  const labels: Record<string, string> = {
    LENGTH_LT: "Длина меньше",
    LENGTH_GT: "Длина больше",
    TEXT_CONTAINS: "Текст содержит",
    KEYWORD_MATCH: "Словарь ключевых слов",
    REGEX_MATCH: "Регулярное выражение",
    SENDER_IS_BOT: "Отправитель — бот",
  };
  return labels[type] ?? type;
}

function readableConditions(rule: Rule) {
  const conditions = tryParseJson<Array<{ type?: string; value?: string }>>(rule.conditionsJson) ?? [];
  if (conditions.length === 0) return ["Условия не заданы"];
  return conditions.map((condition) => `${conditionLabel(condition.type ?? "UNKNOWN")}: ${condition.value ?? "—"}`);
}

function shortClassifierConfig(classifier: Classifier) {
  if (classifier.type === "KEYWORD") return classifier.keywords || "Ключевые слова не заданы";
  if (classifier.type === "REGEX") return classifier.regexPattern || "Регулярное выражение не задано";
  if (classifier.type === "LLM") {
    const provider = classifier.providerId === "3001"
      ? "Тестовый mock-провайдер"
      : classifier.providerId
        ? `Провайдер #${classifier.providerId}`
        : "Провайдер не задан";
    const prompt = classifier.promptId ? `Промпт #${classifier.promptId}` : "промпт не задан";
    return `${provider} · ${prompt}`;
  }
  return null;
}

function confirmDelete(label: string) {
  return window.confirm(`Удалить «${label}»? Это действие нельзя отменить.`);
}

function ClassifierCard({
  classifier,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onToggle,
  onEditLinear,
  onEditLlm,
  onDelete,
}: {
  classifier: Classifier;
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onToggle: (classifier: Classifier) => void;
  onEditLinear: (classifier: Classifier) => void;
  onEditLlm: (classifier: Classifier) => void;
  onDelete: (classifier: Classifier) => void;
}) {
  const parsed = tryParseJson<{ bias?: number; threshold?: number; features?: Record<string, number> }>(
    classifier.modelConfig,
  );
  const features = Object.entries(parsed?.features ?? {})
    .sort((left, right) => Math.abs(right[1]) - Math.abs(left[1]))
    .slice(0, 7);

  return (
    <Card className={cn(classifier.status !== "ACTIVE" && "opacity-70")}>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="truncate text-sm font-semibold text-text-strong">{classifier.name}</h3>
              <Badge variant="outline" className="text-[10px]">{classifierTypeLabel(classifier.type)}</Badge>
            </div>
            <p className="mt-1 text-xs text-text-muted">Версия {classifier.version}</p>
          </div>
          <Badge variant={entityStatusVariant(classifier.status)} dot>
            {entityStatusLabel(classifier.status)}
          </Badge>
        </div>

        {classifier.type === "LINEAR_MODEL" ? (
          <div className="mt-4 rounded-xl border border-border-subtle bg-bg-app p-3">
            <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-3">
              <div>
                <div className="text-text-muted">Смещение</div>
                <div className="font-mono-value text-text-strong">{parsed?.bias ?? "—"}</div>
              </div>
              <div>
                <div className="text-text-muted">Порог</div>
                <div className="font-mono-value text-text-strong">{parsed?.threshold ?? "—"}</div>
              </div>
              <div>
                <div className="text-text-muted">Признаков</div>
                <div className="font-mono-value text-text-strong">{Object.keys(parsed?.features ?? {}).length}</div>
              </div>
            </div>
            <div className="mt-3 grid gap-1.5">
              {features.map(([feature, weight]) => (
                <div key={feature} className="flex items-center justify-between gap-3 text-xs">
                  <span className="truncate text-text-strong">{feature}</span>
                  <span className={cn("font-mono-value", weight >= 0 ? "text-success" : "text-danger")}>
                    {weight.toFixed(2)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <p className="mt-4 line-clamp-3 rounded-xl border border-border-subtle bg-bg-app p-3 text-xs text-text-muted">
            {shortClassifierConfig(classifier)}
          </p>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-border-subtle pt-3">
          <Button variant="outline" size="icon" className="h-7 w-7" onClick={onMoveUp} disabled={isFirst} title="Выше">
            <ArrowUp size={14} />
          </Button>
          <Button variant="outline" size="icon" className="h-7 w-7" onClick={onMoveDown} disabled={isLast} title="Ниже">
            <ArrowDown size={14} />
          </Button>
          <Badge variant="outline">Порядок {classifier.order}</Badge>
          <Button variant="outline" size="sm" onClick={() => onToggle(classifier)}>
            <Power size={14} />
            {classifier.status === "ACTIVE" ? "Отключить" : "Включить"}
          </Button>
          {classifier.type === "LINEAR_MODEL" && (
            <Button variant="outline" size="sm" onClick={() => onEditLinear(classifier)}>
              <Pencil size={14} />
              Настроить
            </Button>
          )}
          {classifier.type === "LLM" && (
            <Button variant="outline" size="sm" onClick={() => onEditLlm(classifier)}>
              <Pencil size={14} />
              Провайдер и промпт
            </Button>
          )}
          <Button variant="outline" size="sm" className="text-danger" onClick={() => onDelete(classifier)}>
            <Trash size={14} />
            Удалить
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function RuleCard({
  rule,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onEdit,
  onToggle,
  onDelete,
}: {
  rule: Rule;
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onEdit: (rule: Rule) => void;
  onToggle: (rule: Rule) => void;
  onDelete: (rule: Rule) => void;
}) {
  return (
    <Card className={cn(rule.status !== "ACTIVE" && "opacity-70")}>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-semibold text-text-strong">{ruleName(rule.name)}</h3>
              <Badge variant={rule.actionType === "EXCLUDE" ? "danger" : "success"}>
                {rule.actionType === "EXCLUDE" ? "Отсекает" : "Пропускает"}
              </Badge>
            </div>
            <p className="mt-1 text-xs text-text-muted">{ruleDescription(rule)}</p>
          </div>
          <Badge variant={entityStatusVariant(rule.status)} dot>
            {entityStatusLabel(rule.status)}
          </Badge>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Badge variant="outline">Порядок {rule.ruleOrder}</Badge>
          <Badge variant="outline">{rule.conditionType}</Badge>
        </div>
        <div className="mt-3 grid gap-2">
          {readableConditions(rule).map((condition) => (
            <div key={condition} className="rounded-xl border border-border-subtle bg-bg-app px-3 py-2 text-xs text-text-strong">
              {condition}
            </div>
          ))}
        </div>
        <div className="mt-4 flex items-center justify-between gap-2 border-t border-border-subtle pt-3">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" className="h-7 w-7" onClick={onMoveUp} disabled={isFirst} title="Выше">
              <ArrowUp size={14} />
            </Button>
            <Button variant="outline" size="icon" className="h-7 w-7" onClick={onMoveDown} disabled={isLast} title="Ниже">
              <ArrowDown size={14} />
            </Button>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => onEdit(rule)}>
              <Pencil size={14} />
              Редактировать
            </Button>
            <Button variant="outline" size="sm" onClick={() => onToggle(rule)}>
              <Power size={14} />
              {rule.status === "ACTIVE" ? "Отключить" : "Включить"}
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="text-danger hover:bg-danger-soft"
              title="Удалить правило"
              onClick={() => onDelete(rule)}
            >
              <Trash size={16} />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function ClassifiersPage() {
  const classifiersQuery = useClassifiersQuery();
  const rulesQuery = useRulesQuery();
  const providersQuery = useProvidersQuery();
  const promptsQuery = usePromptsQuery();
  const createClassifier = useCreateClassifierMutation();
  const updateClassifier = useUpdateClassifierMutation();
  const updateClassifierStatus = useUpdateClassifierStatusMutation();
  const updateRuleStatus = useUpdateRuleStatusMutation();
  const updateRule = useUpdateRuleMutation();
  const createRule = useCreateRuleMutation();
  const deleteClassifier = useDeleteClassifierMutation();
  const deleteRule = useDeleteRuleMutation();
  const reorderRules = useReorderRulesMutation();
  const reorderClassifiers = useReorderClassifiersMutation();

  const classifiers = classifiersQuery.data ?? [];
  const rules = rulesQuery.data ?? [];
  const linear = useMemo(() => classifiers.filter((classifier) => classifier.type === "LINEAR_MODEL"), [classifiers]);
  const llm = useMemo(() => classifiers.filter((classifier) => classifier.type === "LLM"), [classifiers]);
  const providers = providersQuery.data ?? [];
  const classificationPrompts = useMemo(
    () => (promptsQuery.data ?? []).filter((prompt) => prompt.type === "CLASSIFIER"),
    [promptsQuery.data],
  );

  const [linearDialogOpen, setLinearDialogOpen] = useState(false);
  const [editingLinearId, setEditingLinearId] = useState<string | null>(null);
  const [linearForm, setLinearForm] = useState<LinearForm>(() => linearConfigToForm());
  const [llmDialogOpen, setLlmDialogOpen] = useState(false);
  const [editingLlm, setEditingLlm] = useState<Classifier | null>(null);
  const [llmProviderId, setLlmProviderId] = useState("");
  const [llmPromptId, setLlmPromptId] = useState("");
  const [llmOrder, setLlmOrder] = useState("20");

  // Rule edit dialog state
  const [ruleDialogOpen, setRuleDialogOpen] = useState(false);
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null);
  const [ruleForm, setRuleForm] = useState({ name: "", description: "", actionType: "INCLUDE", conditions: [{ type: "TEXT_CONTAINS", value: "" }], actions: [{ type: "SEND_TO_LLM", classifierId: "", threshold: "" }] });

  const openCreateLinear = () => {
    setEditingLinearId(null);
    setLinearForm(linearConfigToForm());
    setLinearDialogOpen(true);
  };

  const openEditLinear = (classifier: Classifier) => {
    setEditingLinearId(classifier.id);
    setLinearForm(linearConfigToForm(classifier));
    setLinearDialogOpen(true);
  };

  const saveLinear = () => {
    const body: CreateClassifierRequest | UpdateClassifierRequest = {
      name: linearForm.name,
      type: "LINEAR_MODEL",
      version: linearForm.version,
      order: Number(linearForm.order) || 10,
      modelConfig: linearFormToConfig(linearForm),
    };
    if (editingLinearId) {
      updateClassifier.mutate({ id: editingLinearId, ...(body as UpdateClassifierRequest) }, { onSuccess: () => setLinearDialogOpen(false) });
      return;
    }
    createClassifier.mutate(body as CreateClassifierRequest, { onSuccess: () => setLinearDialogOpen(false) });
  };

  const openEditLlm = (classifier: Classifier) => {
    setEditingLlm(classifier);
    setLlmProviderId(classifier.providerId ?? "");
    setLlmPromptId(classifier.promptId ?? "");
    setLlmOrder(String(classifier.order ?? 20));
    setLlmDialogOpen(true);
  };

  const saveLlm = () => {
    if (!editingLlm || !llmProviderId || !llmPromptId) return;
    updateClassifier.mutate(
      {
        id: editingLlm.id,
        name: editingLlm.name,
        type: "LLM",
        providerId: Number(llmProviderId),
        promptId: Number(llmPromptId),
        version: editingLlm.version,
        order: Number(llmOrder) || 20,
      },
      { onSuccess: () => setLlmDialogOpen(false) },
    );
  };

  const toggleClassifier = (classifier: Classifier) => {
    updateClassifierStatus.mutate({
      id: classifier.id,
      status: classifier.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
    });
  };

  const toggleRule = (rule: Rule) => {
    updateRuleStatus.mutate({
      id: rule.id,
      status: rule.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
    });
  };

  const openEditRule = (rule: Rule) => {
    setEditingRuleId(rule.id);
    const conditions = tryParseJson<{ type: string; value?: string }[]>(rule.conditionsJson) ?? [{ type: "TEXT_CONTAINS", value: "" }];
    const actions = tryParseJson<{ type: string; classifierId?: string; threshold?: number }[]>(rule.actionsJson) ?? [];
    setRuleForm({
      name: rule.name,
      description: rule.description ?? "",
      actionType: rule.actionType,
      conditions: conditions.map((c) => ({ type: c.type, value: c.value ?? "" })),
      actions: actions.length > 0 ? actions.map((a) => ({ type: a.type ?? "SEND_TO_LLM", classifierId: a.classifierId ?? "", threshold: a.threshold !== undefined ? String(a.threshold) : "" })) : [{ type: "SEND_TO_LLM", classifierId: "", threshold: "" }],
    });
    setRuleDialogOpen(true);
  };

  const saveRule = () => {
    const conditionsJson = JSON.stringify(ruleForm.conditions.filter((c) => c.type).map((c) => ({ type: c.type, ...(c.value ? { value: c.value } : {}) })));
    const actionsJson = JSON.stringify(ruleForm.actions.filter((a) => a.type).map((a) => ({ type: a.type, ...(a.classifierId ? { classifierId: a.classifierId } : {}), ...(a.threshold ? { threshold: Number(a.threshold) } : {}) })));
    if (editingRuleId) {
      updateRule.mutate({ id: editingRuleId, name: ruleForm.name, description: ruleForm.description, actionType: ruleForm.actionType, conditions: conditionsJson, actions: actionsJson }, { onSuccess: () => setRuleDialogOpen(false) });
    } else {
      createRule.mutate({ name: ruleForm.name, description: ruleForm.description, actionType: ruleForm.actionType, order: rules.length, conditions: conditionsJson, actions: actionsJson, status: "DRAFT" }, { onSuccess: () => setRuleDialogOpen(false) });
    }
  };

  const removeClassifier = (classifier: Classifier) => {
    if (confirmDelete(classifier.name)) deleteClassifier.mutate(classifier.id);
  };

  const removeRule = (rule: Rule) => {
    if (confirmDelete(ruleName(rule.name))) deleteRule.mutate(rule.id);
  };

  const moveRule = (rule: Rule, direction: "up" | "down") => {
    const sorted = [...rules].sort((a, b) => a.ruleOrder - b.ruleOrder);
    const idx = sorted.findIndex((r) => r.id === rule.id);
    if (direction === "up" && idx <= 0) return;
    if (direction === "down" && idx >= sorted.length - 1) return;
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    const newOrder = [...sorted];
    [newOrder[idx], newOrder[swapIdx]] = [newOrder[swapIdx], newOrder[idx]];
    reorderRules.mutate(newOrder.map((r) => r.id));
  };

  const moveClassifier = (classifier: Classifier, list: Classifier[], direction: "up" | "down") => {
    const sorted = [...list].sort((a, b) => a.order - b.order);
    const idx = sorted.findIndex((c) => c.id === classifier.id);
    if (direction === "up" && idx <= 0) return;
    if (direction === "down" && idx >= sorted.length - 1) return;
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    const newOrder = [...sorted];
    [newOrder[idx], newOrder[swapIdx]] = [newOrder[swapIdx], newOrder[idx]];
    // Reorder among ALL classifiers, not just the tab
    const allSorted = [...classifiers].sort((a, b) => a.order - b.order);
    const reordered = allSorted.map((c) => {
      const newIndex = newOrder.findIndex((n) => n.id === c.id);
      if (newIndex >= 0) return { ...c, _newPos: newIndex };
      return c;
    });
    reorderClassifiers.mutate(newOrder.map((c) => c.id));
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Классификаторы"
        description="Правила отсекают очевидное, скоринг оценивает признаки, LLM принимает смысловое решение."
      />

      <div className="grid gap-3 md:grid-cols-4">
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">Активные правила</div><div className="mt-1 text-2xl font-semibold text-text-strong">{rules.filter((rule) => rule.status === "ACTIVE").length}</div></CardContent></Card>
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">Активные классификаторы</div><div className="mt-1 text-2xl font-semibold text-text-strong">{classifiers.filter((classifier) => classifier.status === "ACTIVE").length}</div></CardContent></Card>
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">Линейные модели</div><div className="mt-1 text-2xl font-semibold text-text-strong">{linear.length}</div></CardContent></Card>
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">LLM</div><div className="mt-1 text-2xl font-semibold text-text-strong">{llm.length}</div></CardContent></Card>
      </div>

      <Tabs defaultValue="rules">
        <TabsList className="flex h-auto flex-wrap justify-start">
          <TabsTrigger value="rules" className="gap-1.5"><Funnel size={14} />Правила</TabsTrigger>
          <TabsTrigger value="linear" className="gap-1.5"><Scales size={14} />Скоринг</TabsTrigger>
          <TabsTrigger value="llm" className="gap-1.5"><Brain size={14} />LLM-классификация</TabsTrigger>
        </TabsList>

        <TabsContent value="linear">
          <div className="mb-4">
            <div>
              <h2 className="text-sm font-semibold text-text-strong">Линейные модели</h2>
              <p className="text-xs text-text-muted">Признаки и веса редактируются полями, JSON собирается автоматически.</p>
            </div>
          </div>
          {linear.length === 0 ? (
            <EmptyState
              icon={Scales}
              title="Моделей пока нет"
              description="Создай LINEAR_MODEL, чтобы настраивать веса признаков."
              action={
                <Button variant="primary" size="sm" onClick={openCreateLinear}>
                  <Plus size={14} />
                  Добавить модель
                </Button>
              }
            />
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              {linear.map((classifier, index) => (
                <ClassifierCard key={classifier.id} classifier={classifier} isFirst={index === 0} isLast={index === linear.length - 1} onMoveUp={() => moveClassifier(classifier, linear, "up")} onMoveDown={() => moveClassifier(classifier, linear, "down")} onToggle={toggleClassifier} onEditLinear={openEditLinear} onEditLlm={openEditLlm} onDelete={removeClassifier} />
              ))}
              <button
                type="button"
                onClick={openCreateLinear}
                className="flex min-h-[260px] flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border-subtle bg-bg-app p-8 text-text-muted transition-colors hover:border-brand-blue/40 hover:bg-bg-card hover:text-brand-blue"
              >
                <div className="flex h-12 w-12 items-center justify-center rounded-full border-2 border-current">
                  <Plus size={24} />
                </div>
                <span className="text-sm font-medium">Добавить модель скоринга</span>
              </button>
            </div>
          )}
        </TabsContent>

        <TabsContent value="rules">
          {rules.length === 0 ? (
            <EmptyState icon={Funnel} title="Правил пока нет" description="Правила — жёсткие условия: текст, словарь, regex, длина, бот и другие быстрые проверки." />
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              {rules.map((rule, index) => <RuleCard key={rule.id} rule={rule} isFirst={index === 0} isLast={index === rules.length - 1} onMoveUp={() => moveRule(rule, "up")} onMoveDown={() => moveRule(rule, "down")} onEdit={openEditRule} onToggle={toggleRule} onDelete={removeRule} />)}
            </div>
          )}
        </TabsContent>

        <TabsContent value="llm">
          {llm.length === 0 ? (
            <EmptyState icon={Brain} title="LLM-классификаторов нет" description="LLM-классификатор связывает AI-провайдера и промпт классификации." />
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              {llm.map((classifier, index) => (
                <ClassifierCard key={classifier.id} classifier={classifier} isFirst={index === 0} isLast={index === llm.length - 1} onMoveUp={() => moveClassifier(classifier, llm, "up")} onMoveDown={() => moveClassifier(classifier, llm, "down")} onToggle={toggleClassifier} onEditLinear={openEditLinear} onEditLlm={openEditLlm} onDelete={removeClassifier} />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={linearDialogOpen} onOpenChange={setLinearDialogOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editingLinearId ? "Настроить линейную модель" : "Создать линейную модель"}</DialogTitle>
            <DialogDescription>Итог: bias + сумма признаков × вес. Если score выше порога — сообщение проходит дальше.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <Input placeholder="Название" value={linearForm.name} onChange={(event) => setLinearForm((form) => ({ ...form, name: event.target.value }))} />
              <Input placeholder="Версия" value={linearForm.version} onChange={(event) => setLinearForm((form) => ({ ...form, version: event.target.value }))} />
              <Input placeholder="Порядок выполнения" type="number" min="1" value={linearForm.order} onChange={(event) => setLinearForm((form) => ({ ...form, order: event.target.value }))} />
              <Input placeholder="Смещение" type="number" step="0.01" value={linearForm.bias} onChange={(event) => setLinearForm((form) => ({ ...form, bias: event.target.value }))} />
              <Input placeholder="Порог" type="number" step="0.01" value={linearForm.threshold} onChange={(event) => setLinearForm((form) => ({ ...form, threshold: event.target.value }))} />
            </div>

            <div className="rounded-xl border border-border-subtle bg-bg-card p-3">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-text-strong">Веса признаков</h3>
                <Button variant="outline" size="sm" onClick={() => setLinearForm((form) => ({ ...form, features: [...form.features, { name: "", weight: "0" }] }))}><Plus size={14} />Признак</Button>
              </div>
              <div className="grid gap-2">
                {linearForm.features.map((feature, index) => (
                  <div key={`${feature.name}-${index}`} className="grid gap-2 sm:grid-cols-[1fr_120px_36px]">
                    <Input value={feature.name} placeholder="feature_name" onChange={(event) => setLinearForm((form) => ({ ...form, features: form.features.map((item, itemIndex) => itemIndex === index ? { ...item, name: event.target.value } : item) }))} />
                    <Input value={feature.weight} type="number" step="0.01" onChange={(event) => setLinearForm((form) => ({ ...form, features: form.features.map((item, itemIndex) => itemIndex === index ? { ...item, weight: event.target.value } : item) }))} />
                    <Button variant="ghost" size="icon" className="text-danger" onClick={() => setLinearForm((form) => ({ ...form, features: form.features.filter((_, itemIndex) => itemIndex !== index) }))}><Trash size={14} /></Button>
                  </div>
                ))}
              </div>
            </div>

            <details className="rounded-xl border border-border-subtle bg-bg-app p-3">
              <summary className="cursor-pointer text-sm font-medium text-text-strong">Показать JSON, который будет сохранён</summary>
              <Textarea readOnly className="mt-3 min-h-64 font-mono-value text-xs" value={linearFormToConfig(linearForm)} />
            </details>
            <Button variant="primary" onClick={saveLinear} disabled={!linearForm.name.trim() || createClassifier.isPending || updateClassifier.isPending}>Сохранить</Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={llmDialogOpen} onOpenChange={setLlmDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Настроить LLM-классификатор</DialogTitle>
            <DialogDescription>
              Провайдер выполняет запрос к модели, а промпт объясняет модели, что считать полезным сообщением.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">AI-провайдер</label>
              <Select value={llmProviderId} onValueChange={setLlmProviderId}>
                <SelectTrigger>
                  <SelectValue placeholder="Выберите провайдера" />
                </SelectTrigger>
                <SelectContent>
                  {editingLlm?.providerId === "3001" && (
                    <SelectItem value="3001">Тестовый mock-провайдер</SelectItem>
                  )}
                  {providers.map((provider) => (
                    <SelectItem key={provider.id} value={provider.id}>
                      {provider.name} · {provider.model ?? "модель не указана"}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Промпт классификации</label>
              <Select value={llmPromptId} onValueChange={setLlmPromptId}>
                <SelectTrigger>
                  <SelectValue placeholder="Выберите промпт" />
                </SelectTrigger>
                <SelectContent>
                  {classificationPrompts.map((prompt) => (
                    <SelectItem key={prompt.id} value={prompt.id}>
                      {prompt.name} · {prompt.version}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Порядок выполнения</label>
              <Input
                type="number"
                min="1"
                value={llmOrder}
                onChange={(event) => setLlmOrder(event.target.value)}
              />
              <p className="text-[10px] text-text-weak">
                Меньшее число выполняется раньше. Все активные классификаторы всё равно проверяются.
              </p>
            </div>

            <Button
              variant="primary"
              onClick={saveLlm}
              disabled={!llmProviderId || !llmPromptId || updateClassifier.isPending}
            >
              Сохранить настройки
            </Button>
          </div>
        </DialogContent>
      </Dialog>
      {/* ── RULE EDIT DIALOG ── */}
      <Dialog open={ruleDialogOpen} onOpenChange={setRuleDialogOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingRuleId ? "Редактировать правило" : "Новое правило"}</DialogTitle>
            <DialogDescription>Условия определяют когда правило срабатывает, действие — что происходит.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Название</label>
              <Input placeholder="Название правила" value={ruleForm.name} onChange={(e) => setRuleForm((f) => ({ ...f, name: e.target.value }))} />
            </div>
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Описание</label>
              <Textarea className="min-h-16" placeholder="Описание правила" value={ruleForm.description} onChange={(e) => setRuleForm((f) => ({ ...f, description: e.target.value }))} />
            </div>
            <div className="grid gap-1.5">
              <label className="text-xs font-medium text-text-muted">Тип действия</label>
              <Select value={ruleForm.actionType} onValueChange={(v) => setRuleForm((f) => ({ ...f, actionType: v }))}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INCLUDE">Пропускает (INCLUDE)</SelectItem>
                  <SelectItem value="EXCLUDE">Отсекает (EXCLUDE)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-text-muted">Условия</label>
                <Button variant="outline" size="sm" className="h-6 gap-1 text-xs" onClick={() => setRuleForm((f) => ({ ...f, conditions: [...f.conditions, { type: "TEXT_CONTAINS", value: "" }] }))}>
                  <Plus size={12} />Добавить
                </Button>
              </div>
              {ruleForm.conditions.map((condition, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Select value={condition.type} onValueChange={(v) => setRuleForm((f) => ({ ...f, conditions: f.conditions.map((c, i) => i === index ? { ...c, type: v } : c) }))}>
                    <SelectTrigger className="w-44 shrink-0"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {["TEXT_CONTAINS", "KEYWORD_MATCH", "REGEX_MATCH", "LENGTH_GT", "LENGTH_LT", "SENDER_IS_BOT", "HAS_TOPIC", "GROUP_MATCH"].map((ct) => (
                        <SelectItem key={ct} value={ct}>{conditionLabel(ct) || ct}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input className="flex-1" placeholder="Значение" value={condition.value} onChange={(e) => setRuleForm((f) => ({ ...f, conditions: f.conditions.map((c, i) => i === index ? { ...c, value: e.target.value } : c) }))} />
                  <Button variant="outline" size="icon" className="h-8 w-8 shrink-0 text-danger" onClick={() => setRuleForm((f) => ({ ...f, conditions: f.conditions.length > 1 ? f.conditions.filter((_, i) => i !== index) : f.conditions }))} disabled={ruleForm.conditions.length <= 1}>
                    <Trash size={14} />
                  </Button>
                </div>
              ))}
            </div>

            <Button variant="primary" onClick={saveRule} disabled={!ruleForm.name.trim() || updateRule.isPending || createRule.isPending}>
              {editingRuleId ? "Сохранить" : "Создать правило"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
