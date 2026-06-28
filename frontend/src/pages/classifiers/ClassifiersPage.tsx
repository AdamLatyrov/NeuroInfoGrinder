import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Separator } from "@/components/ui/separator";
import { Progress } from "@/components/ui/progress";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { getJsonAuth, postJsonAuth, putJsonAuth, deleteJsonAuth } from "@/shared/api/http";
import {
  MagnifyingGlass, Flask, GitFork, ArrowsDownUp, ChartBar,
  Stack, Gear, Clock, Link, PencilSimple, ArrowClockwise,
  CheckCircle, XCircle, WarningCircle, FileText, CaretRight,
  CaretDown, Eye, EyeSlash, Plus, Sliders, ArrowCounterClockwise, ArrowRight
} from "@phosphor-icons/react";

type ClassifierStatus = {
  workerReachable: boolean; workerStatus: string; classifierStatus: string;
  classifierName: string; embeddingStatus: string; embeddingName: string;
  ruleEngineEnabled: boolean; ruleCount: number;
  singleMessageDetectorEnabled: boolean;
  singleMessageThresholdDirect: number; singleMessageThresholdCandidate: number;
};

type PipelineSetting = {
  key: string; value: string; type: string; category?: string;
  settingKey?: string; settingValue?: string; settingType?: string;
  description: string; safeMin: string | null; safeMax: string | null; defaultValue?: string | null;
};

type RouteMappingEntry = {
  stage: string; providerRoute?: string; provider?: string; modelName?: string; model?: string;
  fallbackModel?: string | null; fallback?: string | null; promptId?: number; promptVersion?: number; isActive?: boolean;
};

type Rule = {
  id: number; code: string; name: string; description: string; decision: string;
  priority: number; enabled: boolean; conditionType: string;
  keywordsJson: string[]; regexJson: string[]; minTextLength: number | null;
  maxTextLength: number | null; requiresLink: boolean; requiresCodeBlock: boolean;
  requiresErrorPattern: boolean; requiresPricePattern: boolean;
  requiresQuestionPattern: boolean; requiresSolutionPattern: boolean;
  examplesJson: string[]; scoringWeightsJson: Record<string, number>;
  version: number; isActive: boolean; createdAt: string; updatedAt: string; updatedBy: string;
};

type TestResult = {
  ruleMatches: {ruleId: number; ruleName: string; decision: string; keywordMatches: string[]; regexMatches: string[]}[];
  classifier: {label: string; confidence: number; model: string};
  singleMessage: {score: number; textLengthScore: number; structureScore: number; guideHowToScore: number;
    troubleshootingScore: number; errorSignalScore: number; solutionSignalScore: number;
    resourceSignalScore: number; priceAccessSignalScore: number; questionAnswerScore: number;
    codeOrCommandScore: number; linkScore: number; classificationConfidence: number;
    noisePenalty: number; decision: string; signals: string[]};
  finalDecision: string; pipelineAction: string; explanation: string;
  llmNextRoute?: {stage: string; promptCode: string; promptName: string; mode: string};
};

type Distribution = Record<string, number>;
type RecentDecision = Record<string, unknown>;
type SingleCandidate = Record<string, unknown>;

const API = "/api/v2/classifiers";
const PIPELINE_API = "/api/v2/pipeline";

function variant(s: string): "success" | "danger" | "warning" | "outline" {
  if (!s) return "outline";
  if (["OK", "UP", "READY"].includes(s)) return "success";
  if (["DOWN", "MODEL_WORKER_DOWN", "MODEL_NOT_CONFIGURED", "FAILED", "ERROR", "NO_CLUSTERS"].includes(s)) return "danger";
  return "warning";
}

const decisionBadgeVariant: Record<string, "success" | "danger" | "warning" | "outline"> = {
  SUPPRESS: "outline", ACCUMULATE: "warning", CANDIDATE: "warning",
  SINGLE_MESSAGE_MATERIAL_CANDIDATE: "warning", DIRECT_MATERIAL_READY: "success",
};

const pipelineActionLabel: Record<string, string> = {
  GENERATE_MATERIAL_DIRECTLY: "Создать материал напрямую",
  LLM_JUDGE_FOR_SINGLE_MESSAGE: "Отправить на LLM Judge",
  WAIT_FOR_CLUSTER: "Ожидать кластер",
  ACCUMULATE_FOR_CLUSTER: "Копить для кластера",
  SKIP: "Пропустить",
};

const decisionDescriptions: Record<string, string> = {
  SUPPRESS: "Шум/флуд — исключено из обработки",
  ACCUMULATE: "Короткий полезный контекст, но самостоятельно ценности нет",
  CANDIDATE: "Полезное сообщение, нужны похожие для кластера",
  SINGLE_MESSAGE_MATERIAL_CANDIDATE: "Одно сообщение содержит материал, нужен LLM Judge",
  DIRECT_MATERIAL_READY: "Одно сообщение уже похоже на готовый гайд/фикс/ресурс",
};

const routeCards = [
  { decision: "SUPPRESS", action: "Не обрабатывать", tone: "outline" as const },
  { decision: "ACCUMULATE", action: "Ждать похожие сообщения", tone: "warning" as const },
  { decision: "CANDIDATE", action: "Embeddings / clustering", tone: "warning" as const },
  { decision: "SINGLE_MESSAGE_MATERIAL_CANDIDATE", action: "LLM single-message judge", tone: "warning" as const },
  { decision: "DIRECT_MATERIAL_READY", action: "LLM / material generation", tone: "success" as const },
];

function settingCategory(setting: PipelineSetting) {
  const key = setting.key ?? setting.settingKey ?? "";
  if (/single|direct/i.test(key)) return "Single-message";
  if (/cluster|judge/i.test(key)) return "Cluster / LLM judge";
  if (/budget|cost|provider/i.test(key)) return "Provider budgets";
  return setting.category || "Общие пороги";
}

export function ClassifiersPage() {
  const [testText, setTestText] = useState("");
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [activeTab, setActiveTab] = useState("overview");
  const [editingRule, setEditingRule] = useState<Rule | null>(null);
  const queryClient = useQueryClient();

  const statusQuery = useQuery({ queryKey: ["classifier-status"], queryFn: () => getJsonAuth<ClassifierStatus>(`${API}/status`), refetchInterval: 10000 });
  const rulesQuery = useQuery({ queryKey: ["classifier-rules"], queryFn: () => getJsonAuth<Rule[]>(`${API}/rules`) });
  const distributionQuery = useQuery({ queryKey: ["classifier-distribution"], queryFn: () => getJsonAuth<Distribution>(`${API}/distribution?runId=0`) });
  const recentDecisionsQuery = useQuery({ queryKey: ["classifier-recent"], queryFn: () => getJsonAuth<RecentDecision[]>(`${API}/recent-decisions?runId=0&limit=20`) });
  const candidatesQuery = useQuery({ queryKey: ["classifier-candidates"], queryFn: () => getJsonAuth<SingleCandidate[]>(`${API}/single-message-candidates?runId=0&limit=10`) });
  const settingsQuery = useQuery({ queryKey: ["pipeline-settings"], queryFn: () => getJsonAuth<PipelineSetting[]>(`${PIPELINE_API}/settings/thresholds`) });
  const routeMappingQuery = useQuery({ queryKey: ["prompt-route-mapping"], queryFn: () => getJsonAuth<{routes: RouteMappingEntry[]}>(`/api/v2/prompts/route-mapping`) });

  const testMutation = useMutation({
    mutationFn: (text: string) => postJsonAuth<TestResult>(`${API}/test`, { text }),
    onSuccess: (r) => setTestResult(r),
  });

  const updateSettingMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      postJsonAuth(`${PIPELINE_API}/settings/${key}`, { value }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["pipeline-settings"] }),
  });

  const resetSettingMutation = useMutation({
    mutationFn: (key: string) => postJsonAuth(`${PIPELINE_API}/settings/${key}/reset`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["pipeline-settings"] }),
  });

  const toggleRuleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      postJsonAuth<Rule>(`${API}/rules/${id}/${enabled ? "activate" : "deactivate"}?updatedBy=ui`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["classifier-rules"] }),
  });

  const status = statusQuery.data;
  const rules = rulesQuery.data ?? [];

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard title="Классификаторы" description="Rule engine, classifier, single-message detector и их настройка">
        <div className="flex gap-2">
          <Select value={activeTab} onValueChange={setActiveTab}>
            <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="overview">Обзор</SelectItem>
              <SelectItem value="rules">Правила</SelectItem>
              <SelectItem value="thresholds">Пороги</SelectItem>
              <SelectItem value="test">Тест</SelectItem>
              <SelectItem value="decisions">Решения</SelectItem>
              <SelectItem value="candidates">Кандидаты</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </PageHeaderCard>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="hidden">
          <TabsTrigger value="overview">Обзор</TabsTrigger>
          <TabsTrigger value="rules">Правила</TabsTrigger>
          <TabsTrigger value="test">Тест</TabsTrigger>
          <TabsTrigger value="thresholds">Пороги</TabsTrigger>
          <TabsTrigger value="decisions">Решения</TabsTrigger>
          <TabsTrigger value="candidates">Кандидаты</TabsTrigger>
        </TabsList>

        {/* A: Status + Flow */}
        <TabsContent value="overview" className="flex flex-col gap-5">
          <div className="grid gap-3 md:grid-cols-5">
            <StatusCard
              label="Model worker"
              value={status?.workerReachable ? status.workerStatus : "Недоступен"}
              variant={status?.workerReachable ? variant(status.workerStatus) : "danger"}
            />
            <StatusCard
              label="Rule engine"
              value={status?.ruleEngineEnabled ? `Включено (${status.ruleCount} правил)` : "Выключено"}
              variant="success"
            />
            <StatusCard
              label="Classifier"
              value={status?.classifierName ?? "BOOTSTRAP_BERT_CLASSIFIER"}
              variant={variant(status?.classifierStatus ?? "")}
              sub={status?.classifierStatus}
            />
            <StatusCard
              label="BGE-M3 embeddings"
              value={status?.embeddingName ?? "нет данных"}
              variant={variant(status?.embeddingStatus ?? "")}
              sub={status?.embeddingStatus}
            />
            <StatusCard
              label="Single-message detector"
              value={status?.singleMessageDetectorEnabled ? `Включён (≥${status.singleMessageThresholdDirect})` : "Выключен"}
              variant="success"
              sub={`candidate ≥${status?.singleMessageThresholdCandidate}`}
            />
          </div>

          <Card>
            <CardContent className="p-5">
              <h2 className="text-lg font-semibold text-text-strong mb-4">Decision flow</h2>
              <div className="flex flex-col items-center gap-0 text-sm">
                {["Raw message", "Rule signals", "Bootstrap/BERT", "Single-message detector", "Final decision", "Pipeline action"].map((label, i) => (
                  <TooltipProvider key={label}>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className="flex flex-col items-center">
                          <span className="rounded-lg bg-bg-elevated px-4 py-2 text-text-strong border border-border-subtle cursor-help whitespace-nowrap">
                            {label}
                          </span>
                          {i < 5 && (
                            <div className="flex items-center justify-center h-6">
                              <svg width="16" height="16" viewBox="0 0 16 16" className="text-text-muted">
                                <line x1="8" y1="0" x2="8" y2="10" stroke="currentColor" strokeWidth="2" />
                                <polygon points="4,10 8,16 12,10" fill="currentColor" />
                              </svg>
                            </div>
                          )}
                        </div>
                      </TooltipTrigger>
                      <TooltipContent side="right">
                        <p className="text-xs max-w-48">
                          {i === 0 && "Исходное сообщение из Telegram"}
                          {i === 1 && "Быстрые эвристики: ключевые слова, regex, длина, ссылки, код"}
                          {i === 2 && "Локальный bootstrap BERT классификатор (20 классов)"}
                          {i === 3 && "Оценка одиночного сообщения: структура, гайд, ошибка, решение"}
                          {i === 4 && "SUPPRESS | ACCUMULATE | CANDIDATE | SINGLE_MESSAGE_MATERIAL_CANDIDATE | DIRECT_MATERIAL_READY"}
                          {i === 5 && "Куда идёт сообщение: кластер, LLM Judge, генерация материала"}
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><GitFork weight="bold" />Маршрутизация решений</CardTitle></CardHeader>
            <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
              {routeCards.map((route) => (
                <div key={route.decision} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
                  <Badge variant={route.tone}>{route.decision}</Badge>
                  <div className="mt-3 text-sm font-semibold text-text-strong">{route.action}</div>
                  <p className="mt-2 text-xs leading-relaxed text-text-muted">{decisionDescriptions[route.decision]}</p>
                </div>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><Link weight="bold" />LLM routes</CardTitle></CardHeader>
            <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {(routeMappingQuery.data?.routes ?? []).map((r) => (
                <div key={r.stage} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
                  <div className="font-semibold text-text-strong">{r.stage}</div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Badge variant="outline">{r.providerRoute ?? r.provider ?? "route не задан"}</Badge>
                    <Badge variant={r.isActive === false ? "warning" : "success"}>{r.isActive === false ? "неактивен" : "активен"}</Badge>
                  </div>
                  <div className="mt-3 font-mono-value text-xs text-text-muted">{r.modelName ?? r.model ?? "model не задан"}</div>
                  {(r.fallbackModel ?? r.fallback) ? <div className="mt-1 text-xs text-text-weak">fallback: {r.fallbackModel ?? r.fallback}</div> : null}
                </div>
              ))}
              {(routeMappingQuery.data?.routes ?? []).length === 0 && <div className="rounded-2xl bg-bg-elevated p-4 text-sm text-text-muted">Нет маршрутов</div>}
            </CardContent>
          </Card>

          {/* E: Class distribution */}
          <Card>
            <CardHeader><CardTitle>Распределение классов</CardTitle></CardHeader>
            <CardContent>
              <div className="grid gap-2">
                {Object.entries(distributionQuery.data ?? {}).slice(0, 12).map(([label, count]) => (
                  <div key={label} className="flex items-center justify-between rounded-xl bg-bg-elevated px-3 py-2 text-sm">
                    <span>{label}</span>
                    <span className="font-mono-value text-text-strong">{count}</span>
                  </div>
                ))}
                {(!distributionQuery.data || Object.keys(distributionQuery.data).length === 0) && (
                  <div className="rounded-xl bg-bg-elevated p-3 text-sm text-text-muted">Нет данных классификации</div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* C: Rule cards */}
        <TabsContent value="rules" className="flex flex-col gap-4">
          <div className="flex justify-end">
            <Button size="sm" onClick={() => setEditingRule({} as Rule)}><Plus weight="bold" />Новое правило</Button>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {rules.map((rule) => (
              <Card key={rule.id} className={rule.isActive ? "" : "opacity-60"}>
                <CardContent className="p-4 flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-text-strong">{rule.name}</span>
                    <div className="flex gap-1">
                      <Badge variant={decisionBadgeVariant[rule.decision] ?? "outline"}>{rule.decision}</Badge>
                      <Badge variant="outline">P{rule.priority}</Badge>
                    </div>
                  </div>
                  <p className="text-sm text-text-muted">{rule.description}</p>
                  <div className="flex flex-wrap gap-1">
                    {rule.keywordsJson?.slice(0, 3).map((kw) => <Badge key={kw} variant="outline" className="text-xs">{kw}</Badge>)}
                    {rule.requiresLink && <Badge variant="outline" className="text-xs">🔗</Badge>}
                    {rule.requiresCodeBlock && <Badge variant="outline" className="text-xs">&lt;/&gt;</Badge>}
                  </div>
                  <div className="flex items-center justify-between mt-1">
                    <div className="flex items-center gap-2">
                      <Switch checked={rule.enabled} onCheckedChange={(v) => toggleRuleMutation.mutate({ id: rule.id, enabled: v })} />
                      <span className="text-xs text-text-muted">v{rule.version}</span>
                    </div>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setEditingRule(rule)}><PencilSimple /></Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Rule editor drawer */}
          <Sheet open={!!editingRule} onOpenChange={(o) => { if (!o) setEditingRule(null); }}>
            <SheetContent className="w-[500px] sm:max-w-lg overflow-y-auto">
              <SheetHeader><SheetTitle>{editingRule?.id ? "Редактировать" : "Создать"} правило</SheetTitle></SheetHeader>
              {editingRule && <RuleEditor rule={editingRule} onClose={() => { setEditingRule(null); queryClient.invalidateQueries({ queryKey: ["classifier-rules"] }); }} />}
            </SheetContent>
          </Sheet>
        </TabsContent>

        {/* H: Thresholds */}
        <TabsContent value="thresholds" className="flex flex-col gap-4">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2"><Sliders weight="bold" />Пороги по группам</CardTitle>
                <Button variant="outline" size="sm" onClick={() => settingsQuery.refetch()}><ArrowClockwise weight="bold" /> Обновить</Button>
              </div>
            </CardHeader>
            <CardContent className="grid gap-4 xl:grid-cols-2">
              {Object.entries((settingsQuery.data ?? []).reduce<Record<string, PipelineSetting[]>>((groups, setting) => {
                const category = settingCategory(setting);
                groups[category] = [...(groups[category] ?? []), setting];
                return groups;
              }, {})).map(([category, settings]) => (
                <div key={category} className="rounded-2xl border border-border-subtle bg-bg-elevated p-4">
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <h3 className="font-semibold text-text-strong">{category}</h3>
                    <Badge variant="outline">{settings.length}</Badge>
                  </div>
                  <div className="space-y-3">
                    {settings.map((s) => (
                      <div key={s.key ?? s.settingKey} className="rounded-xl bg-bg-card p-3">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="font-mono-value text-xs text-text-strong">{s.key ?? s.settingKey}</div>
                            <p className="mt-1 text-xs text-text-muted">{s.description}</p>
                          </div>
                          <Badge variant="outline">{s.type ?? s.settingType}</Badge>
                        </div>
                        <div className="mt-3 flex items-center gap-2">
                          <ThresholdCell
                            setting={s}
                            onSave={(value) => updateSettingMutation.mutate({ key: s.key ?? s.settingKey ?? "", value })}
                            onReset={() => resetSettingMutation.mutate(s.key ?? s.settingKey ?? "")}
                            pending={updateSettingMutation.isPending}
                          />
                          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => resetSettingMutation.mutate(s.key ?? s.settingKey ?? "")} title="Сбросить на значение по умолчанию"><ArrowCounterClockwise weight="bold" /></Button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
              {(settingsQuery.data ?? []).length === 0 && <div className="rounded-2xl bg-bg-elevated p-4 text-sm text-text-muted">Нет порогов</div>}
            </CardContent>
          </Card>

          {/* Route mapping */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2"><Link weight="bold" />Маршрутизация AI-провайдеров</CardTitle>
              </div>
            </CardHeader>
            <CardContent>
              <div className="overflow-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-bg-app text-text-muted">
                    <tr>
                      <th className="px-3 py-2">Промпт</th>
                      <th className="px-3 py-2">Route</th>
                      <th className="px-3 py-2">Model</th>
                      <th className="px-3 py-2">Fallback</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(routeMappingQuery.data?.routes ?? []).map((r) => (
                      <tr key={r.stage} className="border-t border-border-subtle">
                        <td className="px-3 py-2">
                          <span className="font-medium text-text-strong">{r.stage}</span>
                        </td>
                        <td className="px-3 py-2"><Badge variant="outline">{r.providerRoute ?? r.provider ?? "—"}</Badge></td>
                        <td className="px-3 py-2 font-mono-value text-xs">{r.modelName ?? r.model ?? "—"}</td>
                        <td className="px-3 py-2 font-mono-value text-xs text-text-muted">{r.fallbackModel ?? r.fallback ?? "—"}</td>
                      </tr>
                    ))}
                    {(routeMappingQuery.data?.routes ?? []).length === 0 && (
                      <tr><td colSpan={4} className="px-3 py-4 text-center text-text-muted">Нет маршрутов</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          {/* LLM flow explanation */}
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><GitFork weight="bold" />Пути входа в LLM</CardTitle></CardHeader>
            <CardContent className="flex flex-col gap-3 text-sm">
              <div className="rounded-xl bg-bg-elevated p-4 border border-border-subtle">
                <h4 className="font-semibold text-text-strong flex items-center gap-2"><ArrowRight weight="bold" />Path A: Cluster-based</h4>
                <p className="text-text-muted mt-1">Сообщения группируются в кластеры → кластер проходит порог <code className="bg-bg-app px-1 rounded">minClusterScoreForJudge</code> → LLM Judge (LLM_CLUSTER_JUDGE_AND_ROUTING mode=CLUSTER) → confidence ≥ <code className="bg-bg-app px-1 rounded">minJudgeConfidenceForGeneration</code> → LLM Generation (KNOWLEDGE_GENERATION mode=CLUSTER)</p>
              </div>
              <div className="rounded-xl bg-bg-elevated p-4 border border-border-subtle">
                <h4 className="font-semibold text-text-strong flex items-center gap-2"><ArrowRight weight="bold" />Path B: Single-message</h4>
                <p className="text-text-muted mt-1">Сообщение проходит порог <code className="bg-bg-app px-1 rounded">singleMessageCandidateThreshold</code> → если ≥ <code className="bg-bg-app px-1 rounded">directMaterialReadyThreshold</code>, то DIRECT_MATERIAL_READY, иначе SINGLE_MESSAGE_MATERIAL_CANDIDATE → LLM Judge (LLM_CLUSTER_JUDGE_AND_ROUTING mode=SINGLE_MESSAGE) → confidence ≥ <code className="bg-bg-app px-1 rounded">minJudgeConfidenceForGeneration</code> → LLM Generation (KNOWLEDGE_GENERATION mode=SINGLE_MESSAGE)</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* D: Interactive test */}
        <TabsContent value="test" className="flex flex-col gap-4">
          <Card>
            <CardHeader><CardTitle>Проверить сообщение</CardTitle></CardHeader>
            <CardContent className="flex flex-col gap-3">
              <Textarea
                placeholder="Вставьте текст сообщения для проверки..."
                value={testText}
                onChange={(e) => setTestText(e.target.value)}
                rows={5}
              />
              <div className="flex gap-2">
                <Button onClick={() => testMutation.mutate(testText)} disabled={!testText.trim() || testMutation.isPending}>
                  <Flask weight="bold" /> Проверить
                </Button>
                <Button variant="outline" onClick={() => { setTestText(""); setTestResult(null); }}>Очистить</Button>
              </div>
            </CardContent>
          </Card>

          {testResult && (
            <>
              <Card>
                <CardHeader><CardTitle>Результат</CardTitle></CardHeader>
                <CardContent className="flex flex-col gap-3">
                  <div className="flex items-center gap-3">
                    <Badge variant={decisionBadgeVariant[testResult.finalDecision] ?? "outline"} className="text-base px-3 py-1">
                      {testResult.finalDecision}
                    </Badge>
                    <span className="text-sm text-text-muted">{pipelineActionLabel[testResult.pipelineAction] ?? testResult.pipelineAction}</span>
                  </div>
                  <p className="text-sm text-text-muted">{testResult.explanation}</p>

                  {testResult.llmNextRoute && (
                    <>
                      <Separator />
                      <h3 className="font-semibold text-text-strong">LLM next step</h3>
                      <div className="flex items-center gap-2 flex-wrap">
                        <Badge variant="outline">{testResult.llmNextRoute.stage}</Badge>
                        <Badge variant="secondary">{testResult.llmNextRoute.promptCode}</Badge>
                        <Badge variant="warning">{testResult.llmNextRoute.mode}</Badge>
                      </div>
                    </>
                  )}

                  <Separator />
                  <h3 className="font-semibold text-text-strong">Single-message score</h3>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    <ScoreItem label="Score" value={testResult.singleMessage?.score} />
                    <ScoreItem label="Decision" value={testResult.singleMessage?.decision} />
                    <ScoreItem label="Text length" value={testResult.singleMessage?.textLengthScore} />
                    <ScoreItem label="Structure" value={testResult.singleMessage?.structureScore} />
                    <ScoreItem label="Guide/HowTo" value={testResult.singleMessage?.guideHowToScore} />
                    <ScoreItem label="Troubleshooting" value={testResult.singleMessage?.troubleshootingScore} />
                    <ScoreItem label="Error signal" value={testResult.singleMessage?.errorSignalScore} />
                    <ScoreItem label="Solution signal" value={testResult.singleMessage?.solutionSignalScore} />
                    <ScoreItem label="Code/Command" value={testResult.singleMessage?.codeOrCommandScore} />
                    <ScoreItem label="Question/Answer" value={testResult.singleMessage?.questionAnswerScore} />
                    <ScoreItem label="Noise penalty" value={testResult.singleMessage?.noisePenalty} />
                  </div>
                  {testResult.singleMessage?.signals?.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {testResult.singleMessage.signals.map((s) => <Badge key={s} variant="warning">{s}</Badge>)}
                    </div>
                  )}
                </CardContent>
              </Card>

              {testResult.ruleMatches?.length > 0 && (
                <Card>
                  <CardHeader><CardTitle>Rule matches</CardTitle></CardHeader>
                  <CardContent className="flex flex-col gap-2">
                    {testResult.ruleMatches.map((m) => (
                      <div key={m.ruleId} className="rounded-xl bg-bg-elevated p-3">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-text-strong">{m.ruleName}</span>
                          <Badge variant={decisionBadgeVariant[m.decision] ?? "outline"}>{m.decision}</Badge>
                        </div>
                        {m.keywordMatches.length > 0 && <p className="text-xs text-text-muted mt-1">Keywords: {m.keywordMatches.join(", ")}</p>}
                        {m.regexMatches.length > 0 && <p className="text-xs text-text-muted">Regex: {m.regexMatches.join(", ")}</p>}
                      </div>
                    ))}
                  </CardContent>
                </Card>
              )}

              <Card>
                <CardHeader><CardTitle>Classifier</CardTitle></CardHeader>
                <CardContent>
                  <div className="flex items-center gap-2">
                    <Badge variant={variant(testResult.classifier?.label === "NOISE_OR_CHAT" ? "outline" : "warning")}>
                      {testResult.classifier?.label ?? "N/A"}
                    </Badge>
                    <span className="text-sm text-text-muted">
                      confidence: {(testResult.classifier?.confidence * 100).toFixed(0)}%
                    </span>
                    <span className="text-xs text-text-muted">model: {testResult.classifier?.model}</span>
                  </div>
                </CardContent>
              </Card>
            </>
          )}
        </TabsContent>

        {/* F: Recent decisions */}
        <TabsContent value="decisions">
          <Card>
            <CardHeader><CardTitle>Последние решения классификации</CardTitle></CardHeader>
            <CardContent>
              <div className="overflow-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-bg-app text-text-muted">
                    <tr>
                      <th className="px-3 py-2">Сообщение</th>
                      <th className="px-3 py-2">Rule decision</th>
                      <th className="px-3 py-2">Classifier label</th>
                      <th className="px-3 py-2">Confidence</th>
                      <th className="px-3 py-2">Final decision</th>
                      <th className="px-3 py-2">Pipeline action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(recentDecisionsQuery.data ?? []).map((d: any, i: number) => (
                      <tr key={i} className="border-t border-border-subtle">
                        <td className="px-3 py-2 max-w-xs truncate text-text-muted">{d.text_preview}</td>
                        <td className="px-3 py-2"><Badge variant={decisionBadgeVariant[d.rule_decision] ?? "outline"}>{d.rule_decision}</Badge></td>
                        <td className="px-3 py-2">{d.classifier_label}</td>
                        <td className="px-3 py-2">{(d.classifier_confidence * 100).toFixed(0)}%</td>
                        <td className="px-3 py-2"><Badge variant={decisionBadgeVariant[d.final_decision] ?? "outline"}>{d.final_decision}</Badge></td>
                        <td className="px-3 py-2 text-text-muted">{d.pipeline_action}</td>
                      </tr>
                    ))}
                    {(recentDecisionsQuery.data ?? []).length === 0 && (
                      <tr><td colSpan={6} className="px-3 py-4 text-center text-text-muted">Нет данных</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* G: Single-message candidates */}
        <TabsContent value="candidates">
          <Card>
            <CardHeader><CardTitle>Single-message кандидаты</CardTitle></CardHeader>
            <CardContent>
              <div className="overflow-auto">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-bg-app text-text-muted">
                    <tr>
                      <th className="px-3 py-2">Сообщение</th>
                      <th className="px-3 py-2">Rule decision</th>
                      <th className="px-3 py-2">Final decision</th>
                      <th className="px-3 py-2">Classifier</th>
                      <th className="px-3 py-2">Material</th>
                      <th className="px-3 py-2">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(candidatesQuery.data ?? []).map((c: any, i: number) => (
                      <tr key={i} className="border-t border-border-subtle">
                        <td className="px-3 py-2 max-w-xs truncate text-text-muted">{c.text_preview}</td>
                        <td className="px-3 py-2"><Badge variant={decisionBadgeVariant[c.rule_decision] ?? "outline"}>{c.rule_decision}</Badge></td>
                        <td className="px-3 py-2"><Badge variant={decisionBadgeVariant[c.final_decision] ?? "outline"}>{c.final_decision}</Badge></td>
                        <td className="px-3 py-2">{c.classifier_label ?? "—"}</td>
                        <td className="px-3 py-2">
                          {c.knowledge_item_id ? (
                            <a href={`/materials/${c.knowledge_item_id}`} className="text-accent underline">{c.knowledge_item_title}</a>
                          ) : "—"}
                        </td>
                        <td className="px-3 py-2">
                          <Button variant="ghost" size="sm" className="text-xs">Open trace</Button>
                        </td>
                      </tr>
                    ))}
                    {(candidatesQuery.data ?? []).length === 0 && (
                      <tr><td colSpan={6} className="px-3 py-4 text-center text-text-muted">Нет single-message кандидатов</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function StatusCard({ label, value, variant, sub }: { label: string; value: string; variant: "success" | "danger" | "warning" | "outline"; sub?: string }) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Card className="cursor-help"><CardContent className="p-4">
            <div className="text-xs text-text-muted">{label}</div>
            <Badge className="mt-2" variant={variant}>{value}</Badge>
            {sub && <div className="mt-1 text-xs text-text-muted">{sub}</div>}
          </CardContent></Card>
        </TooltipTrigger>
        <TooltipContent side="bottom">
          <p className="text-xs">{label}: {value} ({variant})</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function ThresholdCell({ setting, onSave, onReset, pending }: {
  setting: PipelineSetting; onSave: (value: string) => void; onReset: () => void; pending: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const currentValue = setting.value ?? setting.settingValue ?? "";
  const currentType = setting.type ?? setting.settingType ?? "STRING";
  const [value, setValue] = useState(currentValue);
  const isNum = currentType === "DOUBLE" || currentType === "INTEGER";

  return editing ? (
    <div className="flex items-center gap-1">
      <Input
        type={isNum ? "number" : "text"}
        className="h-8 w-32 text-xs"
        step={currentType === "DOUBLE" ? "0.01" : "1"}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Enter") { onSave(value); setEditing(false); } }}
      />
      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => { onSave(value); setEditing(false); }} disabled={pending}>
        <CheckCircle weight="bold" />
      </Button>
      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => { setValue(currentValue); setEditing(false); }}>
        <XCircle weight="bold" />
      </Button>
    </div>
  ) : (
    <div className="flex items-center gap-1 cursor-pointer" onClick={() => { setValue(currentValue); setEditing(true); }}>
      <span className="font-mono-value text-sm">{currentValue}</span>
      {setting.safeMin && setting.safeMax && (
        <span className="text-[10px] text-text-muted">[{setting.safeMin}–{setting.safeMax}]</span>
      )}
      <PencilSimple size={12} className="text-text-muted opacity-50 hover:opacity-100" />
    </div>
  );
}

function ScoreItem({ label, value }: { label: string; value?: number | string }) {
  return (
    <div className="rounded-xl bg-bg-elevated px-3 py-2 flex items-center justify-between">
      <span className="text-text-muted text-xs">{label}</span>
      <span className="font-mono-value text-text-strong">{value != null ? (typeof value === "number" ? value.toFixed(2) : value) : "—"}</span>
    </div>
  );
}

function RuleEditor({ rule, onClose }: { rule: Rule; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(rule.name ?? "");
  const [description, setDescription] = useState(rule.description ?? "");
  const [decision, setDecision] = useState(rule.decision ?? "ACCUMULATE");
  const [priority, setPriority] = useState(rule.priority ?? 50);
  const [keywords, setKeywords] = useState((rule.keywordsJson ?? []).join(", "));
  const [regex, setRegex] = useState((rule.regexJson ?? []).join("\n"));
  const [minLen, setMinLen] = useState(rule.minTextLength?.toString() ?? "");
  const [maxLen, setMaxLen] = useState(rule.maxTextLength?.toString() ?? "");
  const [reqLink, setReqLink] = useState(rule.requiresLink ?? false);
  const [reqCode, setReqCode] = useState(rule.requiresCodeBlock ?? false);
  const [reqError, setReqError] = useState(rule.requiresErrorPattern ?? false);
  const [reqPrice, setReqPrice] = useState(rule.requiresPricePattern ?? false);
  const [reqQuestion, setReqQuestion] = useState(rule.requiresQuestionPattern ?? false);
  const [reqSolution, setReqSolution] = useState(rule.requiresSolutionPattern ?? false);
  const [changeReason, setChangeReason] = useState("");

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body = {
        name, description, decision, priority,
        keywordsJson: JSON.parse(JSON.stringify(keywords.split(",").map((k: string) => k.trim()).filter(Boolean))),
        regexJson: JSON.parse(JSON.stringify(regex.split("\n").map((r: string) => r.trim()).filter(Boolean))),
        minTextLength: minLen ? parseInt(minLen) : null,
        maxTextLength: maxLen ? parseInt(maxLen) : null,
        requiresLink: reqLink, requiresCodeBlock: reqCode,
        requiresErrorPattern: reqError, requiresPricePattern: reqPrice,
        requiresQuestionPattern: reqQuestion, requiresSolutionPattern: reqSolution,
        changeReason, updatedBy: "ui",
      };
      if (rule.id) {
        return putJsonAuth<Rule>(`${API}/rules/${rule.id}`, body);
      } else {
        return postJsonAuth<Rule>(`${API}/rules`, { ...body, code: name.toLowerCase().replace(/\s+/g, "_") });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["classifier-rules"] });
      onClose();
    },
  });

  return (
    <div className="flex flex-col gap-3 mt-4">
      <Input placeholder="Название" value={name} onChange={(e) => setName(e.target.value)} />
      <Input placeholder="Описание" value={description} onChange={(e) => setDescription(e.target.value)} />
      <div className="flex gap-2">
        <Select value={decision} onValueChange={setDecision}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="SUPPRESS">SUPPRESS</SelectItem>
            <SelectItem value="ACCUMULATE">ACCUMULATE</SelectItem>
            <SelectItem value="CANDIDATE">CANDIDATE</SelectItem>
            <SelectItem value="SINGLE_MESSAGE_MATERIAL_CANDIDATE">SINGLE_MESSAGE_MATERIAL_CANDIDATE</SelectItem>
            <SelectItem value="DIRECT_MATERIAL_READY">DIRECT_MATERIAL_READY</SelectItem>
          </SelectContent>
        </Select>
        <Input type="number" placeholder="Priority" value={priority} onChange={(e) => setPriority(parseInt(e.target.value) || 0)} className="w-20" />
      </div>
      <Separator />
      <p className="text-xs text-text-muted font-medium">Keywords (через запятую)</p>
      <Input placeholder="keyword1, keyword2" value={keywords} onChange={(e) => setKeywords(e.target.value)} />
      <p className="text-xs text-text-muted font-medium">Regex (по одной на строку)</p>
      <Textarea placeholder="[Rr]egex pattern" value={regex} onChange={(e) => setRegex(e.target.value)} rows={3} />
      <div className="flex gap-2">
        <Input type="number" placeholder="Min text len" value={minLen} onChange={(e) => setMinLen(e.target.value)} className="w-28" />
        <Input type="number" placeholder="Max text len" value={maxLen} onChange={(e) => setMaxLen(e.target.value)} className="w-28" />
      </div>
      <Separator />
      <p className="text-xs text-text-muted font-medium">Requirements</p>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <label className="flex items-center gap-2"><Switch checked={reqLink} onCheckedChange={setReqLink} />Link</label>
        <label className="flex items-center gap-2"><Switch checked={reqCode} onCheckedChange={setReqCode} />Code block</label>
        <label className="flex items-center gap-2"><Switch checked={reqError} onCheckedChange={setReqError} />Error pattern</label>
        <label className="flex items-center gap-2"><Switch checked={reqPrice} onCheckedChange={setReqPrice} />Price pattern</label>
        <label className="flex items-center gap-2"><Switch checked={reqQuestion} onCheckedChange={setReqQuestion} />Question pattern</label>
        <label className="flex items-center gap-2"><Switch checked={reqSolution} onCheckedChange={setReqSolution} />Solution pattern</label>
      </div>
      <Separator />
      <Input placeholder="Причина изменения" value={changeReason} onChange={(e) => setChangeReason(e.target.value)} />
      <div className="flex gap-2 mt-2">
        <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending || !name}>
          {rule.id ? "Сохранить версию" : "Создать"}
        </Button>
        <Button variant="outline" onClick={onClose}>Отмена</Button>
      </div>
    </div>
  );
}
