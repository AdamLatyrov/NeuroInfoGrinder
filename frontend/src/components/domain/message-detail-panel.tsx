import { ArrowSquareOut, CaretDown, CheckCircle, Circle, Info, X } from "@phosphor-icons/react";
import { Link } from "react-router-dom";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { useMessagePipelineDetailQuery } from "@/shared/api/messagesApi";
import type { Group, Message } from "@/shared/types";
import { cn } from "@/lib/utils";

interface MessageDetailPanelProps {
  message: Message | null;
  group: Pick<Group, "telegramChatId" | "username"> | null;
  onShowChain: () => void;
  onEnqueue: (messageId: string) => void;
  isEnqueueing: boolean;
}

const reasonText: Record<string, string> = {
  LOW_SINGLE_MESSAGE_SCORE: "Сообщение само по себе недостаточно полезно для отдельного материала.",
  CHAT_CONTEXT_ONLY: "Сообщение имеет смысл только как часть переписки.",
  TOO_SHORT: "Слишком мало содержательного текста.",
  DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT: "Цепочка похожа на полезную, но контекста недостаточно.",
  DUPLICATE_SKIPPED: "Похожий материал уже существует.",
  GENERATION_NOT_CALLED: "Генерация материала не запускалась после предыдущего решения pipeline.",
};

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function resultLabel(status: string) {
  if (status === "MATERIAL_CREATED") return { label: "Материал создан", variant: "success" as const };
  if (status.includes("REJECTED")) return { label: "Отклонено", variant: "danger" as const };
  if (status.includes("SKIPPED")) return { label: "Пропущено", variant: "warning" as const };
  if (status === "NOT_INGESTED" || status === "INGESTED_ONLY") return { label: "Не обработано", variant: "secondary" as const };
  return { label: "В обработке", variant: "default" as const };
}

function pipelineSubtitle(detail: ReturnType<typeof useMessagePipelineDetailQuery>["data"] | undefined) {
  if (!detail) return "Выберите сообщение";
  if (detail.material) return `Сообщение использовано в материале #${detail.material.materialId}`;
  if (detail.discussionSegment) return `Сообщение вошло в цепочку из ${detail.discussionSegment.sourceCount} сообщений`;
  if (detail.pipelineStatus === "NOT_INGESTED" || detail.pipelineStatus === "INGESTED_ONLY") return "Сообщение ещё не обработано pipeline";
  return "Сообщение не стало материалом";
}

function TimelineIcon({ status }: { status: string }) {
  if (status === "success") return <CheckCircle size={15} weight="fill" className="text-success" />;
  if (status === "rejected" || status === "error") return <X size={15} weight="bold" className="text-danger" />;
  return <Circle size={15} weight="fill" className="text-text-weak" />;
}

export function MessageDetailPanel({ message, onShowChain }: MessageDetailPanelProps) {
  const detailQuery = useMessagePipelineDetailQuery(message?.id);
  const detail = detailQuery.data;
  const result = resultLabel(detail?.pipelineStatus ?? "INGESTED_ONLY");
  const sourceMessages = detail?.discussionSegment?.sources ?? [];

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card shadow-[0_12px_34px_rgba(31,36,48,0.045)]">
      <div className="border-b border-border-subtle px-4 py-3">
        <div className="text-sm font-semibold text-text-strong">Что произошло с сообщением</div>
        <div className="mt-1 text-xs text-text-muted">{pipelineSubtitle(detail)}</div>
      </div>

      {!message ? (
        <div className="flex flex-1 items-center justify-center px-5 text-center text-sm text-text-weak">
          Выберите сообщение, чтобы увидеть путь через pipeline
        </div>
      ) : detailQuery.isLoading ? (
        <div className="flex flex-1 items-center justify-center px-5 text-sm text-text-muted">Загружаем pipeline detail...</div>
      ) : detailQuery.isError ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 px-5 text-center text-sm">
          <Info size={24} className="text-warning" />
          <div className="font-semibold text-text-strong">Pipeline detail недоступен</div>
          <div className="text-text-muted">Raw message можно просмотреть, но связанный pipeline trace не загрузился.</div>
        </div>
      ) : (
        <ScrollArea className="flex-1">
          <div className="flex flex-col gap-4 p-4">
            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">Сообщение</div>
              <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">
                <span className="text-text-muted">raw_id</span><span className="font-mono-value text-text-strong">{detail?.rawId ?? message.id}</span>
                <span className="text-text-muted">dataset_message_id</span><span className="font-mono-value text-text-strong">{detail?.datasetMessageId ?? "—"}</span>
                <span className="text-text-muted">chat</span><span className="text-text-strong">{detail?.chatTitle ?? message.groupTitle ?? "—"}</span>
                <span className="text-text-muted">topic/thread</span><span>{detail?.topicId ?? "—"} / {detail?.threadId ?? "—"}</span>
                <span className="text-text-muted">date</span><span>{formatDate(detail?.messageDate ?? message.timestamp)}</span>
              </div>
              <div className="mt-3 rounded-xl bg-bg-card p-3 text-sm text-text-default">
                <RichMessageText text={detail?.text ?? message.text ?? detail?.textUnavailableReason ?? "Текст недоступен"} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {detail?.appMessageUrl && <Button variant="outline" size="sm" asChild><Link to={detail.appMessageUrl}>Открыть в чате</Link></Button>}
                {detail?.telegramMessageUrl ? (
                  <Button variant="outline" size="sm" asChild><a href={detail.telegramMessageUrl} target="_blank" rel="noreferrer">Открыть Telegram <ArrowSquareOut size={13} /></a></Button>
                ) : (
                  <Button variant="outline" size="sm" disabled title={detail?.telegramLinkReason ?? "Telegram-ссылка недоступна"}>Telegram-ссылка недоступна</Button>
                )}
              </div>
            </section>

            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">Результат pipeline</div>
              <Badge variant={result.variant} className="mb-3">{result.label}</Badge>
              <InfoGrid rows={[
                ["candidateType", detail?.candidateType ?? "—"],
                ["material type", detail?.material?.type ?? detail?.discussionSegment?.materialType ?? "—"],
                ["material_id", detail?.material?.materialId ?? "—"],
                ["material title", detail?.material?.title ?? "—"],
              ]} />
              {detail?.material && <Button variant="primary" size="sm" className="mt-3 w-full" asChild><Link to={detail.material.url}>Открыть материал</Link></Button>}
            </section>

            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">Почему так произошло</div>
              {detail?.material ? (
                <ul className="grid gap-2 text-sm text-text-default">
                  <li>Сообщение вошло в цепочку {detail.candidateType ?? "pipeline"}.</li>
                  <li>LLM Judge подтвердил ценность.</li>
                  <li>Материал создан как {detail.material.status}.</li>
                </ul>
              ) : (
                <div className="grid gap-2">
                  {(detail?.rejectionReasons?.length ? detail.rejectionReasons : [detail?.pipelineStatus ?? "NOT_PROCESSED"]).map((reason) => (
                    <div key={reason} className="rounded-lg bg-bg-card p-2 text-sm">
                      <div className="font-mono-value text-xs text-danger">{reason}</div>
                      <div className="mt-1 text-text-muted">{reasonText[reason] ?? "Pipeline остановил обработку на этом этапе или не нашёл достаточной ценности для материала."}</div>
                    </div>
                  ))}
                  {detail?.singleMessage?.score != null && <div className="text-xs text-text-muted">score: <span className="font-semibold text-text-strong">{String(detail.singleMessage.score)}</span></div>}
                </div>
              )}
            </section>

            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">{sourceMessages.length > 1 ? "Источники цепочки" : "Источник"}</div>
              {sourceMessages.length > 0 ? (
                <div className="grid gap-2">
                  {sourceMessages.map((source) => (
                    <div key={`${source.orderIndex}:${source.rawId}`} className={cn("rounded-lg border border-border-subtle bg-bg-card p-2 text-xs", source.selected && "border-brand-blue bg-brand-blue-soft/45")}>
                      <div className="mb-1 flex items-center justify-between gap-2"><span className="font-semibold text-text-strong">#{source.orderIndex} raw {source.rawId}</span><Badge variant="outline" className="text-[10px]">{source.role ?? "source"}</Badge></div>
                      <div className="text-text-muted">{formatDate(source.messageDate)}</div>
                      <div className="mt-1 line-clamp-2 text-text-default">{source.text ?? "Текст недоступен"}</div>
                    </div>
                  ))}
                  <Button variant="outline" size="sm" onClick={onShowChain}>Показать полностью</Button>
                </div>
              ) : <div className="text-sm text-text-muted">Сообщение не связано с цепочкой или кластером.</div>}
            </section>

            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">Этапы обработки</div>
              <div className="grid gap-2">
                {(detail?.timeline ?? []).map((stage, index) => (
                  <div key={`${stage.stage}:${index}`} className="flex gap-2 rounded-lg bg-bg-card p-2 text-xs">
                    <TimelineIcon status={stage.status} />
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-text-strong">{stage.stage}</div>
                      <div className="text-text-muted">{formatDate(stage.timestamp)} · {stage.durationLabel}</div>
                      {stage.reason && <div className="mt-1 text-warning">{stage.reason}</div>}
                      {stage.score != null && <div className="mt-1 text-text-muted">score: {String(stage.score)}</div>}
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-border-subtle bg-bg-elevated p-3">
              <div className="mb-2 text-sm font-semibold text-text-strong">AI-вызовы</div>
              {detail?.providerCalls?.length ? detail.providerCalls.map((call, index) => (
                <details key={`${String(call.providerCallId)}:${index}`} className="mb-2 rounded-lg border border-border-subtle bg-bg-card p-2 text-xs last:mb-0">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-2 font-semibold text-text-strong">
                    <span>{String(call.stage)} · {String(call.status)}</span><CaretDown size={13} />
                  </summary>
                  <InfoGrid rows={[
                    ["provider", String(call.provider ?? "—")],
                    ["model", String(call.model ?? "—")],
                    ["duration", call.durationMs ? `${String(call.durationMs)} мс` : "нет данных"],
                    ["decision", JSON.stringify(call.parsedDecision ?? null)],
                    ["error", String(call.error ?? "—")],
                  ]} />
                  <div className="mt-2 rounded bg-bg-app p-2 text-text-muted">prompt: {String(call.promptPreview ?? "—")}</div>
                  <div className="mt-2 rounded bg-bg-app p-2 text-text-muted">response: {String(call.responsePreview ?? "—")}</div>
                </details>
              )) : <div className="text-sm text-text-muted">AI-вызовы для сообщения не найдены.</div>}
            </section>
          </div>
        </ScrollArea>
      )}
    </div>
  );
}

function InfoGrid({ rows }: { rows: Array<[string, unknown]> }) {
  return <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">{rows.map(([label, value]) => <><span key={`${label}-l`} className="text-text-muted">{label}</span><span key={`${label}-v`} className="break-words text-text-strong">{String(value)}</span></>)}</div>;
}
