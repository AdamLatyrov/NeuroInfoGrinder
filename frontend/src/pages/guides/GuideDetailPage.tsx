import { useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ArrowsClockwise,
  CheckCircle,
  FileDashed,
  Link as LinkIcon,
  PaperPlaneTilt,
  SpinnerGap,
  WarningCircle,
  XCircle,
} from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { MarkdownArticle } from "@/components/domain/markdown-article";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { StatusBadge } from "@/components/domain/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useGuideDetailQuery,
  useRegenerateGuideMutation,
  useUpdateGuideStatusMutation,
} from "@/shared/api/guidesApi";
import { useMessageTraceQuery } from "@/shared/api/tracesApi";
import { displayGuideStatus, type PipelineTrace } from "@/shared/types";

const STAGE_LABELS: Partial<Record<PipelineTrace["stage"], string>> = {
  RULES: "Правила",
  SIGNAL_SCORING: "Сигнальный скоринг",
  CLASSIFICATION: "Классификация",
  CHAIN_BUILDING: "Сбор цепочки",
  GUIDE_GENERATION: "Генерация гайда",
  MODERATION: "Модерация",
};

function stageDisplayName(stage: PipelineTrace["stage"]): string {
  return STAGE_LABELS[stage] ?? stage;
}

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function formatDuration(ms: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

function relationLabel(relation: string | null): string {
  switch (relation) {
    case "ROOT":
      return "Основное сообщение";
    case "PARENT_REPLY":
      return "Сообщение, на которое ответили";
    case "DIRECT_REPLY":
      return "Прямой ответ";
    case "THREAD_REPLY":
      return "Ответ в треде";
    case "SAME_TOPIC_NEARBY":
      return "Рядом в той же теме";
    case "SAME_AUTHOR_NEARBY":
      return "Рядом от того же автора";
    case "TIMELINE_NEARBY":
      return "Соседнее сообщение";
    default:
      return relation ?? "Контекст";
  }
}

function displayGuideTitle(title: string | null | undefined, id: string): string {
  if (!title) return `Guide #${id}`;
  const trimmed = title.trim();
  if (!trimmed) return `Guide #${id}`;
  if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
    return `Guide #${id}`;
  }
  return trimmed;
}

function TraceTimeline({ steps }: { steps: PipelineTrace[] }) {
  return (
    <div className="flex flex-col gap-3">
      {steps.map((step) => (
        <div key={step.id} className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-sm font-semibold text-text-strong">
                {stageDisplayName(step.stage)}
              </div>
              <div className="mt-1 text-xs text-text-muted">
                {step.status} · {formatDuration(step.durationMs)}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              {step.score != null && <Badge variant="outline">score {step.score.toFixed(2)}</Badge>}
              {step.confidence != null && (
                <Badge variant="outline">conf {step.confidence.toFixed(2)}</Badge>
              )}
              {step.model && <Badge variant="outline">{step.model}</Badge>}
            </div>
          </div>
          {step.reason && <div className="mt-2 text-sm text-text-default">{step.reason}</div>}
          {step.errorMessage && (
            <div className="mt-2 text-sm text-danger">{step.errorMessage}</div>
          )}
        </div>
      ))}
    </div>
  );
}

export function GuideDetailPage() {
  const { guideId } = useParams<{ guideId: string }>();
  const navigate = useNavigate();
  const guideQuery = useGuideDetailQuery(guideId);
  const updateGuideStatusMutation = useUpdateGuideStatusMutation();
  const regenerateGuideMutation = useRegenerateGuideMutation();
  const guide = guideQuery.data;
  const traceQuery = useMessageTraceQuery(guide?.rootMessageId ?? undefined);

  const sourceSummary = useMemo(() => {
    if (!guide) return { total: 0, usedInPrompt: 0 };
    return {
      total: guide.sourceMessages.length,
      usedInPrompt: guide.sourceMessages.filter((message) => message.usedInPrompt).length,
    };
  }, [guide]);

  const renderedTitle = guide ? displayGuideTitle(guide.title, guide.id) : "";
  const displayMarkdown = guide?.contentMarkdown || guide?.content || "";
  const showContentParseError = !displayMarkdown && (!!guide?.rawResponse || !!guide?.generationError);

  if (guideQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <SpinnerGap size={24} className="animate-spin text-text-muted" />
      </div>
    );
  }

  if (!guide) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeaderCard
          title="Гайд не найден"
          description="Запрошенный гайд отсутствует или был удалён."
        />
        <EmptyState
          icon={FileDashed}
          title="Guide not found"
          description="The requested guide does not exist or has been removed."
        />
      </div>
    );
  }

  const updateStatus = (status: string) =>
    updateGuideStatusMutation.mutate({ id: guide.id, status });

  const regenerateGuide = () =>
    regenerateGuideMutation.mutate(guide.id, {
      onSuccess: (result) => {
        navigate(`/guides/${result.newGuideId}`);
      },
    });

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title={renderedTitle}
        description={`Гайд #${guide.id} · ${
          guide.sourceGroupTitle
            ? `${guide.sourceGroupTitle}${guide.sourceGroupId ? ` (#${guide.sourceGroupId})` : ""}`
            : guide.sourceGroupId
              ? `группа #${guide.sourceGroupId}`
              : "без группы"
        }`}
        pipelineNote={`${guide.model ?? "model —"} · tokens ${guide.totalTokens.toLocaleString()}`}
        actions={{
          onRefresh: () => {
            guideQuery.refetch();
            traceQuery.refetch();
          },
        }}
      >
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <StatusBadge status={displayGuideStatus(guide.status)} />
          <Badge variant="outline">confidence {guide.confidence.toFixed(2)}</Badge>
          <Badge variant="outline">${guide.estimatedCost.toFixed(4)}</Badge>
          <Badge variant="outline">{sourceSummary.total} source messages</Badge>
          <Badge variant="outline">{sourceSummary.usedInPrompt} used in prompt</Badge>
          {guide.rootMessageId && <Badge variant="outline">root msg #{guide.rootMessageId}</Badge>}
          {guide.regeneratedFromGuideId && (
            <Badge variant="outline">regenerated from #{guide.regeneratedFromGuideId}</Badge>
          )}
        </div>
        {guide.tags.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {guide.tags.map((tag) => (
              <Badge key={tag} variant="secondary">
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </PageHeaderCard>

      {guide.generationError && (
        <Alert variant="danger">
          <WarningCircle size={18} weight="fill" />
          <AlertTitle>Гайд не собрался автоматически</AlertTitle>
          <AlertDescription>
            <div className="whitespace-pre-wrap break-words">{guide.generationError}</div>
          </AlertDescription>
        </Alert>
      )}

      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          onClick={() => updateStatus("DRAFT")}
          disabled={updateGuideStatusMutation.isPending}
        >
          <ArrowsClockwise size={14} />
          В черновик
        </Button>
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          onClick={() => updateStatus("APPROVED")}
          disabled={updateGuideStatusMutation.isPending}
        >
          <CheckCircle size={14} />
          Одобрить
        </Button>
        <Button
          variant="primary"
          size="sm"
          className="gap-1.5"
          onClick={() => updateStatus("PUBLISHED")}
          disabled={updateGuideStatusMutation.isPending}
        >
          <PaperPlaneTilt size={14} />
          Опубликовать
        </Button>
        <Button
          variant="secondary"
          size="sm"
          className="gap-1.5"
          onClick={regenerateGuide}
          disabled={regenerateGuideMutation.isPending}
        >
          <ArrowsClockwise size={14} />
          Сгенерировать заново
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="gap-1.5 text-danger hover:text-danger"
          onClick={() => updateStatus("REJECTED")}
          disabled={updateGuideStatusMutation.isPending}
        >
          <XCircle size={14} />
          Отклонить
        </Button>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className={guide.generationError ? "border-danger/30" : undefined}>
          <CardContent className="p-0">
            <div className="border-b border-border-subtle px-4 py-3 text-sm font-semibold text-text-strong">
              Содержимое гайда
            </div>
            <ScrollArea className="h-[460px]">
              <div className="p-4">
                {showContentParseError ? (
                  <Alert>
                    <WarningCircle size={18} weight="fill" />
                    <AlertTitle>Контент не распарсился как гайд</AlertTitle>
                    <AlertDescription>
                      Сырая модельная выдача сохранена во вкладке Raw. Основной контент не показывается, чтобы не
                      путать JSON с готовым гайдом.
                    </AlertDescription>
                  </Alert>
                ) : (
                  <MarkdownArticle markdown={displayMarkdown} />
                )}
              </div>
            </ScrollArea>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-4">
            <div className="text-sm font-semibold text-text-strong">Метаданные</div>
            <div className="mt-4 grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
              <span className="text-text-muted">Создан</span>
              <span>{formatDate(guide.createdAt)}</span>
              <span className="text-text-muted">Опубликован</span>
              <span>{formatDate(guide.publishedAt)}</span>
              <span className="text-text-muted">Provider</span>
              <span>{guide.llmRequest?.providerId ?? "—"}</span>
              <span className="text-text-muted">Model</span>
              <span className="font-mono-value">{guide.llmRequest?.model ?? guide.model ?? "—"}</span>
              <span className="text-text-muted">Prompt</span>
              <span>{guide.llmRequest?.promptVersion ?? guide.promptVersion ?? "—"}</span>
              <span className="text-text-muted">Input tokens</span>
              <span className="font-mono-value">{guide.llmRequest?.inputTokens.toLocaleString() ?? "0"}</span>
              <span className="text-text-muted">Output tokens</span>
              <span className="font-mono-value">{guide.llmRequest?.outputTokens.toLocaleString() ?? "0"}</span>
              <span className="text-text-muted">Duplicate of</span>
              <span>{guide.duplicateOfGuideId ?? "—"}</span>
              <span className="text-text-muted">Duplicate score</span>
              <span className="font-mono-value">
                {guide.duplicateScore != null ? guide.duplicateScore.toFixed(2) : "—"}
              </span>
            </div>
            <Separator className="my-4" />
            <div className="text-sm font-semibold text-text-strong">Связанные guide ID</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {guide.relatedGuideIds.length === 0 ? (
                <span className="text-sm text-text-muted">Нет связанных гайдов</span>
              ) : (
                guide.relatedGuideIds.map((id) => (
                  <Badge key={id} variant="outline">
                    #{id}
                  </Badge>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="sources">
        <TabsList>
          <TabsTrigger value="sources">Источники</TabsTrigger>
          <TabsTrigger value="raw">Raw</TabsTrigger>
          <TabsTrigger value="trace">Trace</TabsTrigger>
        </TabsList>

        <TabsContent value="sources">
          <Card>
            <CardContent className="p-4">
              <div className="mb-4 text-sm font-semibold text-text-strong">
                Исходные сообщения, на которых построен гайд
              </div>
              <div className="flex flex-col gap-3">
                {guide.sourceMessages.length === 0 ? (
                  <div className="text-sm text-text-muted">Источник не сохранён.</div>
                ) : (
                  guide.sourceMessages.map((message) => (
                    <div
                      key={message.messageId}
                      className="rounded-2xl border border-border-subtle bg-bg-app/60 p-3"
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="font-medium text-text-strong">
                          {message.senderDisplayName ?? "Unknown"}
                        </div>
                        {message.senderUsername && (
                          <Badge variant="outline">@{message.senderUsername}</Badge>
                        )}
                        <Badge variant={message.usedInPrompt ? "success" : "outline"}>
                          {message.usedInPrompt ? "used in prompt" : "stored only"}
                        </Badge>
                        <Badge variant="secondary">{relationLabel(message.relation)}</Badge>
                        <Badge variant="outline">db #{message.messageId}</Badge>
                        {message.telegramMessageId && (
                          <Badge variant="outline">tg #{message.telegramMessageId}</Badge>
                        )}
                        {message.replyToTelegramMessageId && (
                          <Badge variant="outline">reply to #{message.replyToTelegramMessageId}</Badge>
                        )}
                        {message.topicName && <Badge variant="outline">{message.topicName}</Badge>}
                        {message.senderNameSource && (
                          <Badge variant="outline">name: {message.senderNameSource}</Badge>
                        )}
                      </div>
                      <div className="mt-2 whitespace-pre-wrap text-sm text-text-default">
                        <RichMessageText text={message.text ?? ""} />
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {message.internalMessageUrl && (
                          <a
                            href={message.internalMessageUrl}
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-default hover:bg-bg-card"
                          >
                            <LinkIcon size={12} />
                            Открыть в приложении
                          </a>
                        )}
                        {message.telegramLinkAvailable && message.telegramMessageUrl ? (
                          <a
                            href={message.telegramMessageUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-default hover:bg-bg-card"
                          >
                            <LinkIcon size={12} />
                            Открыть в Telegram
                          </a>
                        ) : (
                          <span
                            className="inline-flex items-center gap-1 rounded-md border border-border-subtle px-2 py-1 text-xs text-text-muted"
                            title={message.telegramLinkReason ?? "Ссылка недоступна"}
                          >
                            <LinkIcon size={12} />
                            Telegram link unavailable
                          </span>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="raw">
          <Card>
            <CardContent className="p-4">
              <pre className="whitespace-pre-wrap font-mono-value text-xs leading-relaxed text-text-default">
                {guide.rawResponse || guide.content || "—"}
              </pre>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="trace">
          <Card>
            <CardContent className="p-4">
              <div className="mb-4 text-sm font-semibold text-text-strong">
                Трассировка обработки
              </div>
              {traceQuery.isLoading ? (
                <div className="flex items-center gap-2 text-text-muted">
                  <SpinnerGap size={16} className="animate-spin" />
                  <span className="text-sm">Загрузка трассировки...</span>
                </div>
              ) : !traceQuery.data || traceQuery.data.length === 0 ? (
                <div className="text-sm text-text-muted">Трассировка не найдена.</div>
              ) : (
                <TraceTimeline steps={traceQuery.data} />
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
