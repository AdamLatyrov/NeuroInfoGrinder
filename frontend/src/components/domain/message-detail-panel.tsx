import { ArrowSquareOut, Stack, UploadSimple } from "@phosphor-icons/react";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import type { Group, Message } from "@/shared/types";

function formatTime(value: string): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface MessageDetailPanelProps {
  message: Message | null;
  group: Pick<Group, "telegramChatId" | "username"> | null;
  onShowChain: () => void;
  onEnqueue: (messageId: string) => void;
  isEnqueueing: boolean;
}

export function MessageDetailPanel({
  message,
  onShowChain,
  onEnqueue,
  isEnqueueing,
}: MessageDetailPanelProps) {
  const telegramMessageUrl = message?.telegramMessageUrl ?? null;

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
      <div className="border-b border-border-subtle px-4 py-2.5">
        <span className="text-xs font-semibold uppercase text-text-muted">Детали сообщения</span>
      </div>
      {message ? (
        <ScrollArea className="flex-1">
          <div className="flex flex-col gap-4 p-4">
            <div>
              <div className="mb-2 text-sm font-semibold text-text-strong">Сообщение</div>
              <p className="whitespace-pre-wrap text-sm text-text-default">
                <RichMessageText text={message.text} />
              </p>
            </div>
            <Separator />
            <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
              <span className="text-text-muted">Автор</span>
              <span className="text-text-strong">{message.author}</span>
              <span className="text-text-muted">Дата</span>
              <span>{formatTime(message.timestamp)}</span>
              <span className="text-text-muted">Статус</span>
              <span>
                <Badge variant="outline" className="text-xs">
                  {message.processingStatus}
                </Badge>
              </span>
              <span className="text-text-muted">Guide</span>
              <span>{message.guideId ?? "—"}</span>
              <span className="text-text-muted">Telegram</span>
              <span>
                {telegramMessageUrl ? (
                  <a
                    href={telegramMessageUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-brand-blue hover:underline"
                  >
                    открыть
                    <ArrowSquareOut size={13} />
                  </a>
                ) : (
                  <span title="Ссылка недоступна для этого типа чата">—</span>
                )}
              </span>
              {message.topicName && (
                <>
                  <span className="text-text-muted">Тема</span>
                  <span>
                    <Badge variant="outline" className="text-xs">
                      {message.topicName}
                    </Badge>
                  </span>
                </>
              )}
            </div>
            <Separator />
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" size="sm" className="gap-1.5" onClick={onShowChain}>
                <Stack size={14} weight="regular" />
                Показать цепочку
              </Button>
              <Button
                variant="primary"
                size="sm"
                className="gap-1.5"
                onClick={() => onEnqueue(message.id)}
                disabled={isEnqueueing}
              >
                <UploadSimple size={14} weight="regular" />
                Отправить в очередь
              </Button>
              {telegramMessageUrl ? (
                <Button variant="outline" size="sm" className="gap-1.5" asChild>
                  <a href={telegramMessageUrl} target="_blank" rel="noreferrer">
                    <ArrowSquareOut size={14} weight="regular" />
                    Открыть в Telegram
                  </a>
                </Button>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                  disabled
                  title="Ссылка недоступна для этого типа чата"
                >
                  <ArrowSquareOut size={14} weight="regular" />
                  Открыть в Telegram
                </Button>
              )}
            </div>
          </div>
        </ScrollArea>
      ) : (
        <div className="flex flex-1 items-center justify-center text-sm text-text-weak">
          Выберите сообщение для просмотра деталей
        </div>
      )}
    </div>
  );
}
