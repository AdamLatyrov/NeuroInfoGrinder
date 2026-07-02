import { useMemo, useState, type KeyboardEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowClockwise,
  BookOpenText,
  CaretRight,
  FileText,
  Info,
  MagnifyingGlass,
  Sparkle,
  Tag,
  Trash,
} from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { StatusBadge } from "@/components/domain/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { useDeleteMaterialMutation, useMaterialsQuery, type GuidesSort } from "@/shared/api/guidesApi";
import { useKnowledgeTopicsQuery, type KnowledgeTopic } from "@/shared/api/signalsApi";
import { displayGuideStatus, type ContentType, type Guide } from "@/shared/types";

type MaterialTab = "ALL" | "GUIDE" | "GENERATION" | "ANSWER" | "SUMMARY" | "OTHER" | "SIGNALS";
type StatusTab = "all" | "DRAFT" | "PUBLISHED";

const MATERIAL_TABS: Array<{
  value: MaterialTab;
  label: string;
  types?: ContentType[];
  queryValue?: string;
}> = [
  { value: "ALL", label: "Все" },
  { value: "GUIDE", label: "Гайды", types: ["GUIDE"], queryValue: "GUIDE" },
  { value: "GENERATION", label: "Генерации", types: ["GENERATION"], queryValue: "GENERATION" },
  { value: "ANSWER", label: "Ответы", types: ["ANSWER"], queryValue: "ANSWER" },
  { value: "SUMMARY", label: "Сводки", types: ["SUMMARY"], queryValue: "SUMMARY" },
  { value: "OTHER", label: "Другое", types: ["OTHER"], queryValue: "OTHER" },
  { value: "SIGNALS", label: "Сигналы" },
];

const SORTS: Array<{ value: GuidesSort; label: string }> = [
  { value: "createdAt_desc", label: "Новые сверху" },
  { value: "createdAt_asc", label: "Старые сверху" },
];

const STATUS_TABS: Array<{ value: StatusTab; label: string; apiStatus?: string }> = [
  { value: "all", label: "Все" },
  { value: "DRAFT", label: "Черновики", apiStatus: "DRAFT" },
  { value: "PUBLISHED", label: "Опубликованные", apiStatus: "PUBLISHED" },
];

const TYPE_META: Partial<Record<ContentType, { label: string; tone: string; icon: React.ElementType }>> = {
  GUIDE: { label: "Гайд", tone: "border-emerald-400/40 bg-emerald-500/10 text-emerald-700", icon: BookOpenText },
  GENERATION: { label: "Генерация", tone: "border-violet-400/40 bg-violet-500/10 text-violet-700", icon: Info },
  ANSWER: { label: "Ответ", tone: "border-sky-400/40 bg-sky-500/10 text-sky-700", icon: Info },
  SUMMARY: { label: "Сводка", tone: "border-amber-400/40 bg-amber-500/10 text-amber-700", icon: FileText },
  OTHER: { label: "Другое", tone: "border-slate-400/40 bg-slate-500/10 text-slate-700", icon: FileText },
};

const SCORE_LABELS = {
  quality: "Качество",
  action: "Практичность",
  novelty: "Новизна",
  evidence: "Доказательность",
  risk: "Риск",
  confidence: "Уверенность",
} as const;

const TECHNICAL_LABELS: Record<string, string> = {
  ACCOUNT_RESALE: "Перепродажа аккаунтов",
  ANNOUNCEMENT: "Анонс",
  BROAD_OR_TEMPORAL: "Широкая или временная тема",
  BYPASS: "Обход ограничений",
  DEFERRED: "Отложено",
  DISCUSSION_ONLY: "Только обсуждение",
  DRM_COPYRIGHT: "Авторские права",
  GRAY_MARKET_OR_SECURITY_RISK: "Серый рынок или безопасность",
  HARMFUL: "Опасное содержимое",
  LOW_VALUE: "Низкая ценность",
  NORMAL: "Обычная тема",
  PAYMENT_RISK: "Риск оплаты",
  PRACTICAL_WORKFLOW: "Практический сценарий",
  PRODUCT_CHANGE: "Изменение продукта",
  PROMO_OR_PROVIDER_OFFER: "Промо или предложение провайдера",
  QUESTION_ANSWER: "Вопрос-ответ",
  REFERENCE_CARD: "Справочная карточка",
  RISK_ABUSE_CYBER_SAFETY: "Риск злоупотребления",
  RUMOR_MONITORING: "Проверка слуха",
  SHORT_INSIGHT: "Короткое наблюдение",
  SPAM_OR_AD: "Спам или реклама",
  WEAK_EVIDENCE: "Слабые подтверждения",
};

function materialTabFromSearch(value: string | null): MaterialTab {
  if (!value) {
    return "ALL";
  }
  const normalized = value.trim().toUpperCase();
  if (normalized === "ALL") return "ALL";
  if (normalized === "GENERATION") return "GENERATION";
  if (normalized === "ANSWER") return "ANSWER";
  if (normalized === "CLUSTER_SUMMARY") return "OTHER";
  if (normalized === "SUMMARY") return "SUMMARY";
  if (normalized === "OTHER") return "OTHER";
  if (normalized === "SIGNALS") return "SIGNALS";
  return (
    MATERIAL_TABS.find(
      (tab) => tab.value === normalized || (tab.queryValue != null && tab.queryValue.toUpperCase() === normalized)
    )?.value ?? "GUIDE"
  );
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatSourceCount(count: number) {
  const mod10 = count % 10;
  const mod100 = count % 100;
  let suffix = "источников";
  if (mod10 === 1 && mod100 !== 11) {
    suffix = "источник";
  } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    suffix = "источника";
  }
  return `${count} ${suffix}`;
}

function formatMaterialCount(count: number) {
  const mod10 = count % 10;
  const mod100 = count % 100;
  let suffix = "материалов";
  if (mod10 === 1 && mod100 !== 11) {
    suffix = "материал";
  } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    suffix = "материала";
  }
  return `${count} ${suffix}`;
}

function normalizeTechnicalKey(value: string | null | undefined) {
  return value?.trim().replace(/[\s-]+/g, "_").toUpperCase() ?? "";
}

function displayTechnicalLabel(value: string | null | undefined) {
  const normalized = normalizeTechnicalKey(value);
  if (!normalized) {
    return null;
  }
  return TECHNICAL_LABELS[normalized] ?? "Другая категория";
}

function displaySafetyCategory(value: string | null | undefined) {
  const normalized = normalizeTechnicalKey(value);
  if (!normalized || normalized === "NORMAL") {
    return null;
  }
  return displayTechnicalLabel(value);
}

function displayMaterialType(material: Guide) {
  const type = material.contentType;
  const normalized = normalizeTechnicalKey(type);
  const labels: Record<string, string> = {
    GUIDE: "Гайд",
    GENERATION: "Генерация",
    ANSWER: "Ответ",
    TROUBLESHOOTING: "Troubleshooting",
    CHECKLIST: "Checklist",
    FAQ: "FAQ",
    RESOURCE_LIST: "Resource list",
    CLUSTER_SUMMARY: "Другое",
    SUMMARY: "Сводка",
    OTHER: "Другое",
    REFERENCE: "Другое",
    PROMPT: "Промпт",
    CODE_SNIPPET: "Фрагмент кода",
    CASE_NOTE: "Заметка",
    COMPARISON: "Сравнение",
    UNKNOWN: "Другое",
  };
  return labels[normalized] ?? (material.contentType === "GUIDE" ? "Гайд" : "Другое");
}

function displayCandidateType(value: string | null | undefined) {
  const normalized = normalizeTechnicalKey(value);
  if (normalized === "SINGLE_MESSAGE") return "Single message";
  if (normalized === "MICRO") return "Micro cluster";
  if (normalized === "MACRO") return "Macro cluster";
  return value ?? "knowledge_items";
}

function materialTitle(material: Guide) {
  return material.contentTitle || material.topicLabel || material.title || `Материал #${material.id}`;
}

function materialSummary(material: Guide) {
  if (material.contentSummary) {
    return material.contentSummary;
  }
  if (material.topicSummary) {
    return material.topicSummary;
  }
  return material.contentType === "GUIDE"
    ? "Краткое описание гайда пока не заполнено."
    : "Краткое описание пока не заполнено.";
}

function qualityLabel(value: number | null) {
  if (value == null) return "без оценки";
  if (value >= 90) return "сильный материал";
  if (value >= 80) return "хороший материал";
  if (value >= 70) return "требует проверки";
  return "слабый материал";
}

function qualityTone(value: number | null) {
  if (value == null) return "border-border-subtle bg-bg-elevated text-text-muted";
  if (value >= 90) return "border-emerald-400/40 bg-emerald-500/10 text-emerald-700";
  if (value >= 80) return "border-sky-400/40 bg-sky-500/10 text-sky-700";
  if (value >= 70) return "border-amber-400/40 bg-amber-500/10 text-amber-700";
  return "border-red-400/40 bg-red-500/10 text-red-700";
}

function materialAudienceLabel(material: Guide) {
  if (material.contentType === "GUIDE") return "Пошагово";
  if (material.contentType === "ANSWER") return "Короткий ответ";
  if (material.contentType === "SUMMARY") return "Обзор обсуждения";
  return "Заметка";
}

function typeMatches(tab: MaterialTab, material: Guide) {
  if (tab === "ALL") return true;
  if (tab === "OTHER") return material.contentType === "OTHER";
  return material.contentType === tab;
}

function MaterialBadge({ type }: { type: ContentType }) {
  const fallback = {
    label: displayMaterialType({ contentType: type } as Guide),
    tone: "border-border-subtle bg-bg-elevated text-text-muted",
    icon: Info,
  };
  const meta = TYPE_META[type] ?? fallback;
  const Icon = meta.icon;
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium", meta.tone)}>
      <Icon size={13} />
      {meta.label}
    </span>
  );
}

function ScorePill({ label, value }: { label: string; value: number | null }) {
  if (value == null) {
    return null;
  }

  return (
    <div className="min-w-[92px] rounded-md border border-border-subtle bg-bg-elevated px-2.5 py-2">
      <div className="text-[11px] uppercase tracking-wide text-text-weak">{label}</div>
      <div className="mt-1 font-mono-value text-sm text-text-strong">{value}/100</div>
    </div>
  );
}

function MaterialTabTrigger({ tab, status, countOverride }: { tab: (typeof MATERIAL_TABS)[number]; status?: string; countOverride?: number }) {
  const countQuery = useMaterialsQuery({
    page: 0,
    size: 1,
    contentType: tab.queryValue,
    status,
  });
  const count = countOverride ?? countQuery.data?.totalElements;
  if (tab.value === "OTHER" && !countQuery.isLoading && (count ?? 0) <= 0) {
    return null;
  }

  return (
    <TabsTrigger
      value={tab.value}
      className="rounded-lg border border-transparent px-3 py-2 text-text-muted data-[state=active]:border-brand-blue/10 data-[state=active]:bg-brand-blue-soft data-[state=active]:text-brand-blue data-[state=active]:shadow-none"
    >
      <span>{tab.label}</span>
      <span className="ml-1.5 inline-flex min-w-5 items-center justify-center rounded-full bg-brand-blue-soft px-1.5 py-0.5 text-[11px] font-semibold leading-none text-brand-blue">
        {countOverride == null && countQuery.isLoading ? "..." : count ?? 0}
      </span>
    </TabsTrigger>
  );
}

function TopicCard({ topic, signalsOnly = false }: { topic: KnowledgeTopic; signalsOnly?: boolean }) {
  const navigate = useNavigate();
  const totalFindings = signalsOnly ? topic.signalCount : topic.materialCount + topic.signalCount;
  const badge = totalFindings > 0 ? String(totalFindings) : "тема";
  return (
    <button
      type="button"
      onClick={() => navigate(`/materials/topics/${topic.slug}`)}
      className="group rounded-xl border border-border-subtle bg-bg-card p-4 text-left transition hover:-translate-y-0.5 hover:border-brand-blue/40 hover:shadow-[0_18px_50px_rgba(28,92,255,0.10)]"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-blue-soft text-brand-blue">
          <Tag size={19} />
        </div>
        <span className="rounded-full bg-bg-elevated px-2 py-1 text-xs font-medium text-text-muted">{badge}</span>
      </div>
      <h3 className="mt-3 line-clamp-1 font-semibold text-text-strong transition group-hover:text-brand-blue">{topic.name}</h3>
      <p className="mt-1 line-clamp-2 text-xs leading-5 text-text-muted">{topic.description}</p>
      <div className="mt-3 flex flex-wrap gap-1.5 text-[11px]">
        {!signalsOnly ? <span className="rounded-full border border-border-subtle bg-bg-elevated px-2 py-1 text-text-muted">{topic.materialCount} материалов</span> : null}
        <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-1 text-amber-700">{topic.signalCount} сигналов</span>
        {topic.reviewCount > 0 ? <span className="rounded-full border border-orange-200 bg-orange-50 px-2 py-1 text-orange-700">{topic.reviewCount} проверка</span> : null}
        {topic.riskCount > 0 ? <span className="rounded-full border border-red-200 bg-red-50 px-2 py-1 text-red-700">{topic.riskCount} risk</span> : null}
      </div>
    </button>
  );
}

function statusTabFromSearch(value: string | null): StatusTab {
  const normalized = value?.trim().toUpperCase();
  if (normalized === "DRAFT" || normalized === "PUBLISHED") return normalized;
  return "all";
}

function MaterialCard({ material }: { material: Guide }) {
  const navigate = useNavigate();
  const deleteMutation = useDeleteMaterialMutation();
  const quality = material.contentQualityScore;
  const safetyLabel = displaySafetyCategory(material.safetyCategory);
  const subtypeLabel = displayTechnicalLabel(material.contentSubtype);
  const footerSubtypeLabel = subtypeLabel !== safetyLabel ? subtypeLabel : null;
  const sourceCount = material.sourceCount ?? material.sourceMessages.length;
  const title = materialTitle(material);

  const openMaterial = () => {
    navigate(`/materials/${material.id}`);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openMaterial();
    }
  };

  const deleteMaterial = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    if (window.confirm("Удалить материал? Он исчезнет из списка. Исходные сообщения и трассировка останутся в базе.")) {
      deleteMutation.mutate(material.id);
    }
  };

  return (
    <Card
      role="link"
      tabIndex={0}
      aria-label={`Открыть материал: ${title}`}
      onClick={openMaterial}
      onKeyDown={handleKeyDown}
      className="group cursor-pointer overflow-hidden border-border-subtle bg-bg-card transition hover:-translate-y-0.5 hover:border-brand-blue/40 hover:shadow-[0_18px_50px_rgba(28,92,255,0.12)] focus:outline-none focus:ring-2 focus:ring-brand-blue/40"
    >
      <CardContent className="p-0">
        <div className="grid gap-0 md:grid-cols-[132px_minmax(0,1fr)_86px]">
          <div className="border-b border-border-subtle bg-bg-app/50 p-4 md:border-b-0 md:border-r">
            <div className="text-[11px] font-semibold uppercase tracking-wide text-text-weak">Качество</div>
            <div className="mt-2 flex items-center gap-2">
              <span className={cn("h-2 w-2 rounded-full", quality == null ? "bg-text-weak/40" : "bg-success")} />
              <div className="font-mono-value text-2xl font-semibold text-text-strong">{quality == null ? "—" : quality}</div>
            </div>
            <div className="mt-1 text-xs text-text-muted">{quality == null ? "не оценено" : "/ 100"}</div>
          </div>

          <div className="min-w-0 p-4 sm:p-5">
            <div className="flex flex-wrap items-center gap-2">
              <MaterialBadge type={material.contentType} />
              <StatusBadge status={displayGuideStatus(material.status)} />
              <Badge variant="outline">{materialAudienceLabel(material)}</Badge>
              {safetyLabel ? <Badge variant="outline">{safetyLabel}</Badge> : null}
            </div>
            <h3 className="mt-3 line-clamp-2 text-lg font-semibold leading-tight text-text-strong transition group-hover:text-brand-blue sm:text-xl">
              {title}
            </h3>
            <p className="mt-2 line-clamp-2 max-w-4xl text-sm leading-6 text-text-muted">
              {materialSummary(material)}
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-text-weak">
              <span>Группа: {material.sourceGroupTitle ?? "—"}</span>
              <span>Источник: Telegram</span>
              <span>Обновлено: {formatDate(material.createdAt)}</span>
              <span>{formatSourceCount(sourceCount)}</span>
              <span>Категория: {footerSubtypeLabel ?? displayMaterialType(material)}</span>
              <span>{displayCandidateType(material.publicationKind)}</span>
            </div>
          </div>

          <div className="hidden items-center justify-center gap-1 border-l border-border-subtle px-2 text-text-muted transition group-hover:text-brand-blue md:flex">
            <Button variant="ghost" size="icon" className="h-8 w-8 text-text-muted hover:text-danger" disabled={deleteMutation.isPending} onClick={deleteMaterial} aria-label="Удалить материал">
              <Trash size={15} />
            </Button>
            <CaretRight size={18} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function MaterialsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState("");
  const sortOrder = SORTS.some((sort) => sort.value === searchParams.get("sort"))
    ? (searchParams.get("sort") as GuidesSort)
    : "createdAt_desc";
  const activeTab = materialTabFromSearch(searchParams.get("contentType") ?? searchParams.get("type"));
  const activeStatus = statusTabFromSearch(searchParams.get("status"));
  const activeTabConfig = MATERIAL_TABS.find((tab) => tab.value === activeTab) ?? MATERIAL_TABS[0];
  const activeStatusConfig = STATUS_TABS.find((tab) => tab.value === activeStatus) ?? STATUS_TABS[0];
  const topicsQuery = useKnowledgeTopicsQuery();
  const topics = topicsQuery.data?.content ?? [];
  const signalCount = topics.reduce((sum, topic) => sum + topic.signalCount, 0);
  const filteredTopics = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return topics;
    return topics.filter((topic) =>
      [topic.name, topic.description, topic.slug].some((value) => value.toLowerCase().includes(needle)),
    );
  }, [search, topics]);

  const materialsQuery = useMaterialsQuery({
    page: 0,
    size: 100,
    sort: sortOrder,
    contentType: activeTabConfig.queryValue,
    status: activeStatusConfig.apiStatus,
  });
  const materials = materialsQuery.data?.content ?? [];
  const totalMaterials = materialsQuery.data?.totalElements ?? materials.length;
  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (activeTab === "SIGNALS") return [];
    return materials
      .filter((material) => typeMatches(activeTab, material))
      .filter((material) => {
        if (!needle) {
          return true;
        }
        return [
          materialTitle(material),
          materialSummary(material),
          material.sourceGroupTitle,
          displaySafetyCategory(material.safetyCategory),
          displayTechnicalLabel(material.contentSubtype),
        ]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(needle));
      });
  }, [activeTab, materials, search]);

  const updateTab = (value: MaterialTab) => {
    const next = new URLSearchParams(searchParams);
    const tab = MATERIAL_TABS.find((item) => item.value === value);
    if (tab?.queryValue) {
      next.set("contentType", tab.queryValue);
      next.delete("type");
    } else if (value === "SIGNALS") {
      next.delete("contentType");
      next.set("type", "SIGNALS");
    } else {
      next.delete("contentType");
      next.delete("type");
    }
    setSearchParams(next, { replace: true });
  };

  const updateSort = (value: GuidesSort) => {
    const next = new URLSearchParams(searchParams);
    next.set("sort", value);
    setSearchParams(next, { replace: true });
  };

  const updateStatus = (value: StatusTab) => {
    const next = new URLSearchParams(searchParams);
    if (value === "all") next.delete("status");
    else next.set("status", value);
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeaderCard
        title="Материалы"
        description="Гайды, полезные заметки и тематические сигналы из Telegram-потока."
        pipelineNote={`Всего: ${formatMaterialCount(totalMaterials)}`}
        actions={{ onRefresh: () => materialsQuery.refetch(), refreshPending: materialsQuery.isFetching }}
      />

      <Card className="border-border-subtle bg-bg-card">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="relative w-full lg:max-w-md">
              <MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-weak" size={17} />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Поиск по названию, описанию, источнику"
                className="h-10 pl-9"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <div className="inline-flex rounded-md border border-border-subtle bg-bg-elevated p-1">
                {SORTS.map((sort) => (
                  <button
                    key={sort.value}
                    type="button"
                    onClick={() => updateSort(sort.value)}
                    className={cn(
                      "rounded px-3 py-1.5 text-sm transition",
                      sortOrder === sort.value
                        ? "bg-bg-card text-text-strong shadow-sm"
                        : "text-text-muted hover:text-text-strong"
                    )}
                  >
                    {sort.label}
                  </button>
                ))}
              </div>
              <Button variant="outline" size="sm" onClick={() => materialsQuery.refetch()} disabled={materialsQuery.isFetching}>
                <ArrowClockwise size={16} />
                Обновить
              </Button>
            </div>
          </div>

          <div className="space-y-3 border-t border-border-subtle pt-4">
            <div>
              <div className="mb-2 text-xs uppercase tracking-wide text-text-weak">Показать</div>
              <Tabs value={activeTab} onValueChange={(value) => updateTab(value as MaterialTab)}>
                <TabsList className="flex h-auto flex-wrap justify-start gap-1 bg-transparent p-0">
                  {MATERIAL_TABS.map((tab) => (
                    <MaterialTabTrigger key={tab.value} tab={tab} status={activeStatusConfig.apiStatus} countOverride={tab.value === "SIGNALS" ? signalCount : undefined} />
                  ))}
                </TabsList>
              </Tabs>
            </div>

            <div>
              <div className="mb-2 text-xs uppercase tracking-wide text-text-weak">Готовность</div>
              <Tabs value={activeStatus} onValueChange={(value) => updateStatus(value as StatusTab)}>
                <TabsList className="flex h-auto flex-wrap justify-start gap-1 bg-transparent p-0">
                  {STATUS_TABS.map((tab) => (
                    <TabsTrigger key={tab.value} value={tab.value} className="rounded-lg border border-transparent px-3 py-2 text-text-muted data-[state=active]:border-brand-blue/10 data-[state=active]:bg-brand-blue-soft data-[state=active]:text-brand-blue data-[state=active]:shadow-none">
                      {tab.label}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>
            </div>
          </div>
        </CardContent>
      </Card>

      {activeTab === "SIGNALS" ? (
        <Card className="overflow-hidden border-border-subtle bg-bg-card">
          <CardContent className="p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700">
                  <Sparkle size={14} />
                  Темы сигналов
                </div>
                <h2 className="mt-3 text-xl font-semibold text-text-strong">Карта сигналов</h2>
                <p className="mt-1 max-w-3xl text-sm leading-6 text-text-muted">
                  В теме показаны только сырые полезные сигналы и сообщения на проверке. Сейчас сигналов по темам: {formatMaterialCount(signalCount)}.
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={() => topicsQuery.refetch()} disabled={topicsQuery.isFetching}>
                <ArrowClockwise size={15} />
                Обновить темы
              </Button>
            </div>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-text-muted">
              <span>Показано {formatMaterialCount(filteredTopics.length)} из {formatMaterialCount(topics.length)} тем</span>
              <span>Вкладка показывает карту сигналов.</span>
            </div>
            <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
              {filteredTopics.map((topic) => <TopicCard key={topic.slug} topic={topic} signalsOnly />)}
              {topicsQuery.isLoading ? Array.from({ length: 5 }).map((_, index) => <div key={index} className="h-40 animate-pulse rounded-xl border border-border-subtle bg-bg-app" />) : null}
            </div>
          </CardContent>
        </Card>
      ) : materialsQuery.isLoading ? (
        <div className="grid gap-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-44 animate-pulse rounded-lg border border-border-subtle bg-bg-card" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={Info}
          title="Материалов нет"
          description="Для текущего фильтра пока нет записей."
        />
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-text-muted">
            <span>Показано {formatMaterialCount(filtered.length)} из {formatMaterialCount(totalMaterials)}</span>
            <span>Открой карточку, чтобы увидеть полный текст и источники.</span>
          </div>
          <div className="grid gap-3">
            {filtered.map((material) => (
              <MaterialCard key={material.id} material={material} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
