import { Link, useParams } from "react-router-dom";
import {
  ArrowClockwise,
  CaretDown,
  CaretLeft,
  CaretRight,
  FunnelSimple,
  MagnifyingGlass,
  TelegramLogo,
} from "@phosphor-icons/react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type StageStatus = "passed" | "skipped" | "failed" | "processing";

interface StageRow {
  rawId: string;
  chat: string;
  preview: string;
  status: StageStatus;
  reason: string;
  model: string;
  score: string;
  time: string;
}

interface StageDetailConfig {
  title: string;
  badge: string;
  description: string;
  summaryCards: Array<{
    label: string;
    value: string;
    badge: string | null;
    tone: "default" | "success" | "warning" | "danger" | "muted";
  }>;
  tabs: string[];
  rows: StageRow[];
  total: string;
  pageCount: string;
}

const embeddingsRows: StageRow[] = [
  { rawId: "4743", chat: "Vibemode", preview: "попробуй api прямой без прокси, у меня 403 вчера...", status: "passed", reason: "—", model: "bge-m3", score: "0.82", time: "12:41:23" },
  { rawId: "3162", chat: "Vibemode", preview: "OpenAI API 403 после 2 часов нормально работа...", status: "skipped", reason: "NOT_NEED_EMBEDDINGS", model: "—", score: "—", time: "12:40:11" },
  { rawId: "2902", chat: "Паша", preview: "Мини-гайд по проверке API лимитов в OpenAI...", status: "passed", reason: "—", model: "bge-m3", score: "0.91", time: "12:38:55" },
  { rawId: "2898", chat: "Паша", preview: "Кто-нибудь сталкивался с ошибкой rate limit в а...", status: "skipped", reason: "TOO_SHORT", model: "—", score: "—", time: "12:37:42" },
  { rawId: "2871", chat: "AI Helpers", preview: "Подскажите, как лучше структурировать докум...", status: "passed", reason: "—", model: "bge-m3", score: "0.85", time: "12:35:18" },
  { rawId: "2860", chat: "Vibemode", preview: "Вот скрипт для автоматизации создания ассист...", status: "skipped", reason: "CODE_ONLY", model: "—", score: "—", time: "12:34:02" },
  { rawId: "2851", chat: "Паша", preview: "Нашёл решение проблемы с таймаутами в Open...", status: "passed", reason: "—", model: "bge-m3", score: "0.88", time: "12:33:47" },
  { rawId: "2844", chat: "AI Helpers", preview: "Как вы храните контекст между запросами к LLM?", status: "skipped", reason: "NO_TEXT", model: "—", score: "—", time: "12:32:21" },
  { rawId: "2833", chat: "Vibemode", preview: "Рабочий способ обойти 429 ошибку в API...", status: "passed", reason: "—", model: "bge-m3", score: "0.79", time: "12:31:05" },
  { rawId: "2822", chat: "Паша", preview: "Сравнение цен на GPT-4o vs Claude 3.5...", status: "skipped", reason: "NOT_NEED_EMBEDDINGS", model: "—", score: "—", time: "12:30:11" },
];

const llmRows: StageRow[] = [
  { rawId: "4743", chat: "Vibemode", preview: "попробуй api прямой без прокси, у меня 403 вчера...", status: "failed", reason: "LLM_REJECTED_ALL", model: "gpt-4o-mini", score: "0.91", time: "12:41:23" },
  { rawId: "2218", chat: "DevChat", preview: "как отследить chain вызов и ретраи в агенте...", status: "failed", reason: "NO_MATERIAL_CANDIDATES", model: "gpt-4o-mini", score: "0.82", time: "12:40:11" },
  { rawId: "2902", chat: "Паша", preview: "Мини-гайд по проверке API лимитов в OpenAI...", status: "passed", reason: "—", model: "gpt-4o-mini", score: "0.96", time: "12:38:55" },
  { rawId: "1987", chat: "AI Новости", preview: "OpenAI o3 и o4-mini: что изменилось по качеству...", status: "passed", reason: "—", model: "gpt-4o-mini", score: "0.89", time: "12:37:42" },
];

const genericRows: StageRow[] = [
  { rawId: "4743", chat: "Vibemode", preview: "попробуй api прямой без прокси, у меня 403 вчера...", status: "passed", reason: "—", model: "—", score: "—", time: "12:41:23" },
  { rawId: "3162", chat: "DevChat", preview: "как собрать стабильный pipeline для обработки...", status: "processing", reason: "—", model: "—", score: "—", time: "12:40:11" },
  { rawId: "2902", chat: "Паша", preview: "Мини-гайд по проверке API лимитов в OpenAI...", status: "passed", reason: "—", model: "—", score: "—", time: "12:38:55" },
  { rawId: "2844", chat: "AI Helpers", preview: "Как вы храните контекст между запросами к LLM?", status: "skipped", reason: "STAGE_FILTERED", model: "—", score: "—", time: "12:32:21" },
];

const STAGE_CONFIGS: Record<string, StageDetailConfig> = {
  raw_messages: {
    title: "Этап: Raw messages",
    badge: "1,248 сообщений во входящем потоке",
    description: "Первичный вход сообщений из Telegram и сырого потока ingest до последующей нормализации и маршрутизации.",
    summaryCards: [
      { label: "Входящие", value: "1,248", badge: null, tone: "default" },
      { label: "Пройдено", value: "1,248", badge: "100%", tone: "success" },
      { label: "Пропущено", value: "0", badge: "0%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 1,248", "Пройдено 1,248", "Пропущено 0", "Неудачи 0"],
    rows: genericRows,
    total: "1,248",
    pageCount: "125",
  },
  intake: {
    title: "Этап: Intake",
    badge: "1,102 прошло из 1,248 входящих",
    description: "Приём и первичная нормализация сообщений перед постановкой в очередь конвейера.",
    summaryCards: [
      { label: "Входящие", value: "1,248", badge: null, tone: "default" },
      { label: "Пройдено", value: "1,102", badge: "88.3%", tone: "success" },
      { label: "Пропущено", value: "146", badge: "11.7%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 1,248", "Пройдено 1,102", "Пропущено 146", "Неудачи 0"],
    rows: genericRows,
    total: "1,248",
    pageCount: "125",
  },
  queue: {
    title: "Этап: Queue",
    badge: "1,046 прошло из 1,102 входящих",
    description: "Очередь обработки, в которой сообщения ожидают запуска pipeline и первичных проверок.",
    summaryCards: [
      { label: "Входящие", value: "1,102", badge: null, tone: "default" },
      { label: "Пройдено", value: "1,046", badge: "94.9%", tone: "success" },
      { label: "Пропущено", value: "56", badge: "5.1%", tone: "warning" },
      { label: "Неудачи", value: "2", badge: "0.2%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 1,102", "Пройдено 1,046", "Пропущено 56", "Неудачи 2"],
    rows: genericRows,
    total: "1,102",
    pageCount: "111",
  },
  run: {
    title: "Этап: Run",
    badge: "1,020 прошло из 1,046 входящих",
    description: "Активный execution-этап pipeline до ветвления на embedding, clustering и последующие проверки.",
    summaryCards: [
      { label: "Входящие", value: "1,046", badge: null, tone: "default" },
      { label: "Пройдено", value: "1,020", badge: "97.5%", tone: "success" },
      { label: "Пропущено", value: "18", badge: "1.7%", tone: "warning" },
      { label: "Неудачи", value: "8", badge: "0.8%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 1,046", "Пройдено 1,020", "Пропущено 18", "Неудачи 8"],
    rows: genericRows,
    total: "1,046",
    pageCount: "105",
  },
  embeddings: {
    title: "Этап: Embeddings / BGE-M3",
    badge: "420 пройдено из 1,102 входящих",
    description: "Генерация эмбеддингов с помощью BGE-M3. Сообщения на этом этапе могут быть пропущены (skipped), если не требуют векторизации.",
    summaryCards: [
      { label: "Входящие", value: "1,102", badge: null, tone: "default" },
      { label: "Пройдено", value: "420", badge: "38.1%", tone: "success" },
      { label: "Пропущено", value: "682", badge: "61.9%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 1,102", "Пройдено 420", "Пропущено 682", "Неудачи 0"],
    rows: embeddingsRows,
    total: "1,102",
    pageCount: "111",
  },
  single_message_detection: {
    title: "Этап: Single-message",
    badge: "37 кандидатов из 420 входящих",
    description: "Отбор одиночных сообщений-кандидатов, которые могут стать самостоятельным материалом без clustering.",
    summaryCards: [
      { label: "Входящие", value: "420", badge: null, tone: "default" },
      { label: "Пройдено", value: "37", badge: "8.8%", tone: "success" },
      { label: "Пропущено", value: "383", badge: "91.2%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 420", "Кандидаты 37", "Пропущено 383", "Неудачи 0"],
    rows: genericRows,
    total: "420",
    pageCount: "42",
  },
  clustering: {
    title: "Этап: Clustering",
    badge: "31 прошло из 37 входящих",
    description: "Кластеризация похожих сообщений в группы-кандидаты перед LLM Judge.",
    summaryCards: [
      { label: "Входящие", value: "37", badge: null, tone: "default" },
      { label: "Пройдено", value: "31", badge: "83.8%", tone: "success" },
      { label: "Пропущено", value: "6", badge: "16.2%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 37", "Пройдено 31", "Пропущено 6", "Неудачи 0"],
    rows: genericRows,
    total: "37",
    pageCount: "4",
  },
  llm_judge: {
    title: "Этап: LLM Judge",
    badge: "12 принято из 37 входящих",
    description: "LLM-оценка кандидатов перед material generation, включая причины отклонения и итоговые scores.",
    summaryCards: [
      { label: "Входящие", value: "37", badge: null, tone: "default" },
      { label: "Пройдено", value: "12", badge: "32.4%", tone: "success" },
      { label: "Пропущено", value: "25", badge: "67.6%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 37", "Принято 12", "Отклонено 25", "Неудачи 0"],
    rows: llmRows,
    total: "37",
    pageCount: "4",
  },
  material_generation: {
    title: "Этап: Material generation",
    badge: "10 прошло из 12 входящих",
    description: "Генерация итогового материала из принятых LLM Judge кандидатов.",
    summaryCards: [
      { label: "Входящие", value: "12", badge: null, tone: "default" },
      { label: "Пройдено", value: "10", badge: "83.3%", tone: "success" },
      { label: "Пропущено", value: "2", badge: "16.7%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 12", "Пройдено 10", "Пропущено 2", "Неудачи 0"],
    rows: genericRows,
    total: "12",
    pageCount: "2",
  },
  materials_publish: {
    title: "Этап: Material created",
    badge: "9 создано из 10 входящих",
    description: "Финальный этап публикации и сохранения материалов после успешной генерации.",
    summaryCards: [
      { label: "Входящие", value: "10", badge: null, tone: "default" },
      { label: "Пройдено", value: "9", badge: "90.0%", tone: "success" },
      { label: "Пропущено", value: "1", badge: "10.0%", tone: "warning" },
      { label: "Неудачи", value: "0", badge: "0%", tone: "danger" },
      { label: "В обработке", value: "0", badge: "0%", tone: "muted" },
    ],
    tabs: ["Все 10", "Пройдено 9", "Пропущено 1", "Неудачи 0"],
    rows: genericRows,
    total: "10",
    pageCount: "1",
  },
};

const statusLabel: Record<StageStatus, string> = {
  passed: "пройдено",
  skipped: "пропущено",
  failed: "неудача",
  processing: "в обработке",
};

const statusClass: Record<StageStatus, string> = {
  passed: "bg-success-soft text-success",
  skipped: "bg-warning-soft text-warning",
  failed: "bg-danger-soft text-danger",
  processing: "bg-brand-blue-soft text-brand-blue",
};

function SelectShell({ label }: { label: string }) {
  return (
    <button className="flex h-11 min-w-0 items-center justify-between gap-3 rounded-[14px] border border-border-subtle bg-bg-elevated px-3 text-left text-sm text-text-strong shadow-[0_4px_14px_rgba(31,36,48,0.03)] transition-colors hover:border-border-default">
      <span className="truncate">{label}</span>
      <CaretDown size={14} className="shrink-0 text-text-weak" />
    </button>
  );
}

function ChatCell({ chat }: { chat: string }) {
  return (
    <div className="flex min-w-[136px] items-center gap-2.5">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-border-subtle bg-brand-blue-soft text-brand-blue">
        <TelegramLogo size={13} weight="fill" />
      </span>
      <span className="truncate font-medium text-text-strong">{chat}</span>
    </div>
  );
}

function ScoreCell({ row }: { row: StageRow }) {
  if (row.score === "—") {
    return <span className="text-text-weak">—</span>;
  }

  return (
    <span className="inline-flex items-center gap-2 font-medium text-text-strong">
      <span className="h-1.5 w-1.5 rounded-full bg-success" />
      {row.score}
    </span>
  );
}

export function PipelineStageEmbeddingsMockupPage() {
  const params = useParams<{ stageId: string }>();
  const stage = STAGE_CONFIGS[params.stageId ?? "embeddings"] ?? STAGE_CONFIGS.embeddings;

  return (
    <div className="rounded-[30px] border border-border-subtle bg-bg-card p-4 shadow-[0_22px_70px_rgba(31,36,48,0.08)] sm:p-6 xl:p-7">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2 rounded-full text-text-muted hover:text-text-strong">
              <Link to="/pipeline">
                <CaretLeft size={16} />
                Назад к конвейеру
              </Link>
            </Button>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-[-0.03em] text-text-strong sm:text-[32px]">
                {stage.title}
              </h1>
              <Badge className="min-h-8 bg-brand-blue-soft px-4 text-brand-blue">
                {stage.badge}
              </Badge>
            </div>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-text-muted sm:text-[15px]">
              {stage.description}
            </p>
          </div>
          <Button variant="primary" className="h-11 rounded-[14px] px-5 shadow-[0_12px_26px_rgba(185,133,23,0.20)]">
            <ArrowClockwise size={17} />
            Обновить
          </Button>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {stage.summaryCards.map((card) => (
            <div key={card.label} className="rounded-[22px] border border-border-subtle bg-bg-elevated p-4 shadow-[0_10px_30px_rgba(31,36,48,0.045)]">
              <div className="text-xs font-semibold uppercase tracking-[0.16em] text-text-weak">{card.label}</div>
              <div className="mt-3 flex items-end justify-between gap-3">
                <div className="text-[30px] font-semibold leading-none tracking-[-0.04em] text-text-strong">{card.value}</div>
                {card.badge && (
                  <span
                    className={cn(
                      "rounded-full px-2.5 py-1 text-xs font-semibold",
                      card.tone === "success" && "bg-success-soft text-success",
                      card.tone === "warning" && "bg-warning-soft text-warning",
                      card.tone === "danger" && "bg-danger-soft text-danger",
                      card.tone === "muted" && "bg-brand-blue-soft text-text-muted",
                    )}
                  >
                    {card.badge}
                  </span>
                )}
              </div>
            </div>
          ))}
        </section>

        <section className="rounded-[24px] border border-border-subtle bg-[#fffaf0] p-4 shadow-[0_12px_36px_rgba(31,36,48,0.045)]">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-text-strong">
            <FunnelSimple size={17} className="text-brand-blue" />
            Фильтры этапа
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            <SelectShell label="Сегодня 00:00 — сейчас" />
            <SelectShell label="Все чаты" />
            <SelectShell label="Все статусы" />
            <SelectShell label="Все причины" />
            <SelectShell label="Все модели" />
          </div>
          <div className="mt-3 flex flex-col gap-3 lg:flex-row">
            <label className="relative flex-1">
              <span className="sr-only">Поиск</span>
              <MagnifyingGlass size={18} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-text-weak" />
              <input
                className="h-12 w-full rounded-[16px] border border-border-subtle bg-bg-elevated pl-11 pr-4 text-sm text-text-strong outline-none transition-colors placeholder:text-text-weak focus:border-brand-blue"
                placeholder="Поиск по тексту сообщения или raw_id..."
              />
            </label>
            <Button variant="outline" className="h-12 rounded-[16px] px-5">
              Сбросить фильтры
            </Button>
          </div>
        </section>

        <div className="flex flex-wrap gap-2">
          {stage.tabs.map((tab, index) => (
            <button
              key={tab}
              className={cn(
                "rounded-full px-4 py-2 text-sm font-semibold transition-colors",
                index === 0
                  ? "bg-brand-blue-soft text-brand-blue shadow-[inset_0_0_0_1px_rgba(41,95,190,0.12)]"
                  : "border border-border-subtle bg-bg-elevated text-text-muted hover:text-text-strong",
              )}
            >
              {tab}
            </button>
          ))}
        </div>

        <section className="overflow-hidden rounded-[26px] border border-border-subtle bg-bg-elevated shadow-[0_16px_44px_rgba(31,36,48,0.055)]">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1100px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border-subtle bg-[#fbf7ef] text-xs font-semibold uppercase tracking-[0.12em] text-text-weak">
                  <th className="px-5 py-3.5">raw_id</th>
                  <th className="px-5 py-3.5">chat</th>
                  <th className="px-5 py-3.5">preview</th>
                  <th className="px-5 py-3.5">статус на этапе</th>
                  <th className="px-5 py-3.5">причина</th>
                  <th className="px-5 py-3.5">модель</th>
                  <th className="px-5 py-3.5">score</th>
                  <th className="px-5 py-3.5">время</th>
                  <th className="w-12 px-4 py-3.5" aria-label="Открыть" />
                </tr>
              </thead>
              <tbody>
                {stage.rows.map((row) => (
                  <tr key={row.rawId} className="group border-b border-border-subtle/80 transition-colors last:border-b-0 hover:bg-brand-blue-soft/25">
                    <td className="px-5 py-4 font-mono text-[13px] font-semibold text-text-strong">{row.rawId}</td>
                    <td className="px-5 py-4">
                      <ChatCell chat={row.chat} />
                    </td>
                    <td className="max-w-[340px] px-5 py-4 text-text-default">
                      <span className="line-clamp-1">{row.preview}</span>
                    </td>
                    <td className="px-5 py-4">
                      <span className={cn("inline-flex rounded-full px-3 py-1 text-xs font-semibold", statusClass[row.status])}>
                        {statusLabel[row.status]}
                      </span>
                    </td>
                    <td className="px-5 py-4 font-mono text-xs font-semibold text-text-muted">{row.reason}</td>
                    <td className="px-5 py-4 font-mono text-xs font-semibold text-text-strong">{row.model}</td>
                    <td className="px-5 py-4">
                      <ScoreCell row={row} />
                    </td>
                    <td className="px-5 py-4 font-mono text-xs text-text-muted">{row.time}</td>
                    <td className="px-4 py-4 text-right">
                      <Link
                        to={row.rawId === "4743" ? "/pipeline/messages/4743" : "#"}
                        className="grid h-8 w-8 place-items-center rounded-full text-text-weak transition-colors group-hover:bg-bg-card group-hover:text-brand-blue"
                        aria-label={`Открыть сообщение ${row.rawId}`}
                      >
                        <CaretRight size={16} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-border-subtle px-5 py-4 text-sm text-text-muted lg:flex-row lg:items-center lg:justify-between">
            <div>Показано 1–10 из {stage.total}</div>
            <div className="flex items-center justify-center gap-1.5">
              <button className="grid h-8 w-8 place-items-center rounded-full border border-border-subtle bg-bg-card text-text-muted">&lt;</button>
              <button className="grid h-8 min-w-8 place-items-center rounded-full bg-brand-blue-soft px-2 font-semibold text-brand-blue">1</button>
              <button className="grid h-8 min-w-8 place-items-center rounded-full px-2 text-text-muted hover:bg-bg-card">2</button>
              <button className="grid h-8 min-w-8 place-items-center rounded-full px-2 text-text-muted hover:bg-bg-card">3</button>
              <span className="px-1 text-text-weak">…</span>
              <button className="grid h-8 min-w-8 place-items-center rounded-full px-2 text-text-muted hover:bg-bg-card">{stage.pageCount}</button>
              <button className="grid h-8 w-8 place-items-center rounded-full border border-border-subtle bg-bg-card text-text-muted">&gt;</button>
            </div>
            <button className="inline-flex h-9 items-center justify-between gap-3 rounded-full border border-border-subtle bg-bg-card px-3 text-text-strong">
              10 на странице
              <CaretDown size={13} className="text-text-weak" />
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
