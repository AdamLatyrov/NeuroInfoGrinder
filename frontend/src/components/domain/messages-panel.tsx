import { useEffect, useRef } from "react";
import { SpinnerGap, ArrowClockwise } from "@phosphor-icons/react";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { Message } from "@/shared/types";

function formatTime(value: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDateSep(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString("ru-RU", {
    day: "numeric",
    month: "long",
  });
}

export interface MessageTab {
  id: string | null;
  label: string;
}

interface MessagesPanelProps {
  messages: Message[];
  totalMessages: number;
  isLoading: boolean;
  selectedMessageId: string | null;
  onSelectMessage: (message: Message) => void;
  headerTitle: string;
  headerSubtitle?: string;
  tabs?: MessageTab[];
  activeTabId?: string | null;
  onTabChange?: (tabId: string | null) => void;
  onSync?: () => void;
  isSyncing?: boolean;
}

function sameDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  return (
    da.getFullYear() === db.getFullYear() &&
    da.getMonth() === db.getMonth() &&
    da.getDate() === db.getDate()
  );
}

export function MessagesPanel({
  messages,
  totalMessages,
  isLoading,
  selectedMessageId,
  onSelectMessage,
  headerTitle,
  headerSubtitle,
  tabs,
  activeTabId,
  onTabChange,
  onSync,
  isSyncing,
}: MessagesPanelProps) {
  const messageRefs = useRef<Record<string, HTMLDivElement | null>>({});

  useEffect(() => {
    if (!selectedMessageId) {
      return;
    }

    const target = messageRefs.current[selectedMessageId];
    if (!target) {
      return;
    }

    window.requestAnimationFrame(() => {
      target.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
    });
  }, [selectedMessageId, messages]);

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-app">
      <div className="flex items-center justify-between border-b border-border-subtle bg-bg-card px-4 py-2.5">
        <div className="min-w-0">
          <span className="text-sm font-semibold text-text-strong">
            {headerTitle}
          </span>
          {headerSubtitle && (
            <span className="ml-2 font-mono-value text-xs text-text-muted">
              {headerSubtitle}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {onSync && (
            <Button
              variant="outline"
              size="sm"
              className="h-7 gap-1.5 text-xs"
              onClick={onSync}
              disabled={isSyncing}
            >
              {isSyncing ? (
                <SpinnerGap size={12} weight="regular" className="animate-spin" />
              ) : (
                <ArrowClockwise size={12} weight="regular" />
              )}
              Загрузить
            </Button>
          )}
          {isLoading && (
            <SpinnerGap
              size={14}
              weight="regular"
              className="animate-spin text-text-muted"
            />
          )}
          <Badge variant="outline" className="text-xs">
            {totalMessages}
          </Badge>
        </div>
      </div>

      {tabs && tabs.length > 1 && (
        <div className="flex items-center gap-1 overflow-x-auto border-b border-border-subtle bg-bg-card px-3 py-1.5">
          {tabs.map((tab) => (
            <button
              key={tab.id ?? "__all__"}
              onClick={() => onTabChange?.(tab.id)}
              className={`shrink-0 rounded-lg px-3 py-1 text-xs font-medium transition-colors ${
                activeTabId === tab.id
                  ? "bg-brand-blue-soft text-brand-blue"
                  : "text-text-muted hover:bg-bg-elevated hover:text-text-default"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {messages.length === 0 && !isLoading ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-10 text-sm text-text-weak">
          Сообщений нет
        </div>
      ) : (
        <ScrollArea className="flex-1">
          <div className="flex flex-col gap-0 px-3 py-3">
            {messages.map((message, index) => {
              const previous = index > 0 ? messages[index - 1] : null;
              const showDateSep = !previous || !sameDay(previous.timestamp, message.timestamp);
              const sameAuthor = previous && previous.author === message.author && !showDateSep;
              const isSelected = selectedMessageId === message.id;
              const isBot = message.isBot;

              return (
                <div
                  key={message.id}
                  ref={(element) => {
                    messageRefs.current[message.id] = element;
                  }}
                >
                  {showDateSep && (
                    <div className="my-3 flex justify-center">
                      <span className="rounded-full bg-bg-elevated px-3 py-0.5 text-[11px] font-medium text-text-muted">
                        {formatDateSep(message.timestamp)}
                      </span>
                    </div>
                  )}

                  <button
                    onClick={() => onSelectMessage(message)}
                    className={`group flex w-full flex-col rounded-[16px] px-3 pb-2 pt-1.5 text-left transition-colors ${
                      isSelected
                        ? "bg-brand-blue-soft/70 ring-1 ring-brand-blue/30"
                        : "bg-bg-card hover:bg-bg-card/80"
                    } ${sameAuthor ? "mt-0.5" : "mt-2"}`}
                  >
                    {!sameAuthor && (
                      <div className="mb-0.5 flex items-center gap-1.5">
                        <span
                          className={`text-[13px] font-semibold ${
                            isBot ? "text-warning" : "text-brand-blue"
                          }`}
                        >
                          {message.author || "Unknown"}
                        </span>
                        {isBot && (
                          <Badge variant="outline" className="h-4 px-1 text-[8px]">
                            bot
                          </Badge>
                        )}
                        {message.topicName && (
                          <span className="text-[10px] text-text-weak">
                            в {message.topicName}
                          </span>
                        )}
                      </div>
                    )}

                    <div className="whitespace-pre-wrap break-words text-[13.5px] leading-relaxed text-text-default">
                      <RichMessageText text={message.text} />
                    </div>

                    <div className="mt-0.5 flex items-center justify-end gap-1.5">
                      {message.processingStatus !== "UNPROCESSED" && (
                        <span
                          className={`text-[10px] ${
                            message.processingStatus === "ERROR"
                              ? "text-danger"
                              : message.processingStatus === "GUIDE_GENERATED"
                                ? "text-success"
                                : "text-text-weak"
                          }`}
                        >
                          {message.processingStatus === "CLASSIFIED" && "Классифицировано"}
                          {message.processingStatus === "SENT_TO_LLM" && "В LLM"}
                          {message.processingStatus === "GUIDE_GENERATED" && "Гайд"}
                          {message.processingStatus === "ERROR" && "Ошибка"}
                          {message.processingStatus === "QUEUED" && "В очереди"}
                        </span>
                      )}
                      {message.replyCount > 0 && (
                        <span className="text-[10px] text-text-weak">
                          {message.replyCount} отв.
                        </span>
                      )}
                      <span className="text-[10px] text-text-weak">
                        {formatTime(message.timestamp)}
                      </span>
                    </div>
                  </button>
                </div>
              );
            })}
          </div>
        </ScrollArea>
      )}
    </div>
  );
}
