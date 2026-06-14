import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Separator } from "@/components/ui/separator";
import { useGroupsQuery } from "@/shared/api/groupsApi";
import {
  useEnqueueMessageMutation,
  useGroupMessagesQuery,
  useMessageChainQuery,
  useSyncMessagesMutation,
} from "@/shared/api/messagesApi";
import type { Group, Message } from "@/shared/types";
import { ArrowsClockwise, ChatCircle, MagnifyingGlass, SpinnerGap, Stack, UploadSimple } from "@phosphor-icons/react";

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

export function ChatViewerPage() {
  const [searchParams] = useSearchParams();
  const preselectedGroupId = searchParams.get("group");
  const groupsQuery = useGroupsQuery({ size: 200 });
  const groups = groupsQuery.data?.content ?? [];

  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<Message | null>(null);
  const [chainSheetOpen, setChainSheetOpen] = useState(false);
  const [groupSearch, setGroupSearch] = useState("");

  const messagesQuery = useGroupMessagesQuery(selectedGroup?.id);
  const groupMessages = messagesQuery.data?.content ?? [];
  const totalMessages = messagesQuery.data?.totalElements ?? 0;
  const hasMoreMessages = false;
  const messageChainQuery = useMessageChainQuery(
    selectedGroup?.id,
    selectedMessage?.id
  );
  const enqueueMutation = useEnqueueMessageMutation(selectedGroup?.id);
  const syncMessagesMutation = useSyncMessagesMutation(selectedGroup?.id);

  useEffect(() => {
    if (!selectedGroup && groups.length > 0) {
      const match = preselectedGroupId
        ? groups.find((g) => g.id === preselectedGroupId)
        : null;
      setSelectedGroup(match ?? groups[0]);
    }
  }, [groups, selectedGroup, preselectedGroupId]);

  useEffect(() => {
    setSelectedMessage(null);
  }, [selectedGroup?.id]);

  const filteredGroups = useMemo(
    () =>
      groups.filter((group) =>
        group.title.toLowerCase().includes(groupSearch.toLowerCase()) ||
        String(group.telegramChatId).includes(groupSearch)
      ),
    [groups, groupSearch]
  );

  const selectedChain = messageChainQuery.data;
  const selectedGroupTitle = selectedGroup?.title ?? "Сообщения";
  const enrichedMessages = useMemo(
    () =>
      groupMessages.map((message: Message) => ({
        ...message,
        groupTitle: selectedGroupTitle,
      })),
    [groupMessages, selectedGroupTitle]
  );

  return (
    <div className="flex min-h-[calc(100vh-140px)] flex-col gap-5">
      <PageHeaderCard
        title="Просмотр чатов"
        description="Исследование входящих сообщений и контекста обработки"
      />

      {groups.length === 0 && !groupsQuery.isLoading ? (
        <EmptyState
          icon={ChatCircle}
          title="Нет групп"
          description="Сначала подтяните Telegram-группы на странице групп, чтобы смотреть сообщения."
        />
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-3 xl:flex-row">
          <div className="flex min-h-[220px] w-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card xl:w-64 xl:shrink-0">
            <div className="flex items-center justify-between border-b border-border-subtle px-3 py-2">
              <span className="text-xs font-semibold uppercase text-text-muted">
                Группы
              </span>
              {groupsQuery.isLoading && (
                <SpinnerGap size={12} weight="regular" className="animate-spin text-text-muted" />
              )}
            </div>
            <div className="border-b border-border-subtle px-3 py-1.5">
              <div className="relative">
                <MagnifyingGlass
                  size={14}
                  weight="regular"
                  className="absolute left-2 top-1/2 -translate-y-1/2 text-text-weak"
                />
                <Input
                  placeholder="Поиск..."
                  value={groupSearch}
                  onChange={(e) => setGroupSearch(e.target.value)}
                  className="h-7 pl-7 text-xs"
                />
              </div>
            </div>
            <ScrollArea className="flex-1">
              <div className="flex flex-col gap-0.5 p-1.5">
                {filteredGroups.map((group) => (
                  <button
                    key={group.id}
                    onClick={() => setSelectedGroup(group)}
                    className={`rounded-lg px-3 py-2 text-left transition-colors ${
                      selectedGroup?.id === group.id
                        ? "bg-brand-blue-soft text-text-strong"
                        : "text-text-default hover:bg-bg-app"
                    }`}
                  >
                    <div className="truncate text-sm font-medium">{group.title}</div>
                    <div className="mt-0.5 flex items-center gap-2 text-xs text-text-muted">
                      <span>{Number(group.messagesPerDay).toLocaleString()}/д</span>
                      <Badge variant={group.enabled ? "success" : "outline"} className="px-1.5 py-0 text-[10px]">
                        {group.enabled ? "on" : "off"}
                      </Badge>
                    </div>
                  </button>
                ))}
              </div>
            </ScrollArea>
          </div>

          <div className="flex min-h-[320px] min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
            <div className="flex items-center justify-between border-b border-border-subtle px-4 py-2.5">
              <div>
                <span className="text-sm font-semibold text-text-strong">
                  {selectedGroup?.title ?? "Сообщения"}
                </span>
                <span className="ml-2 font-mono-value text-xs text-text-muted">
                  {selectedGroup?.telegramChatId ?? ""}
                </span>
              </div>
              <div className="flex items-center gap-2">
                {messagesQuery.isLoading && (
                  <SpinnerGap size={14} weight="regular" className="animate-spin text-text-muted" />
                )}
                <Badge variant="outline" className="text-xs">
                  {enrichedMessages.length} сообщений
                </Badge>
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                  onClick={() => syncMessagesMutation.mutate()}
                  disabled={syncMessagesMutation.isPending}
                >
                  {syncMessagesMutation.isPending ? (
                    <SpinnerGap size={14} weight="regular" className="animate-spin" />
                  ) : (
                    <ArrowsClockwise size={14} weight="regular" />
                  )}
                  Загрузить
                </Button>
              </div>
            </div>

            {!selectedGroup ? (
              <div className="flex flex-1 items-center justify-center text-sm text-text-weak">
                Выберите группу для просмотра сообщений
              </div>
            ) : enrichedMessages.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-3 py-10 text-sm text-text-weak">
                Сообщений за последнюю неделю нет
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                  onClick={() => syncMessagesMutation.mutate()}
                  disabled={syncMessagesMutation.isPending}
                >
                  {syncMessagesMutation.isPending ? (
                    <SpinnerGap size={14} weight="regular" className="animate-spin" />
                  ) : (
                    <ArrowsClockwise size={14} weight="regular" />
                  )}
                  Загрузить из Telegram
                </Button>
              </div>
            ) : (
              <ScrollArea className="flex-1">
                <div className="flex flex-col divide-y divide-border-subtle">
                  {enrichedMessages.map((message: Message) => (
                    <button
                      key={message.id}
                      onClick={() => setSelectedMessage(message)}
                      className={`px-4 py-3 text-left transition-colors hover:bg-bg-app/60 ${
                        selectedMessage?.id === message.id ? "bg-brand-blue-soft/50" : ""
                      }`}
                    >
                      <div className="mb-1 flex items-center justify-between gap-4">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-sm font-medium text-text-strong">
                            {message.author || "Unknown"}
                          </span>
                          {message.isBot && (
                            <Badge variant="outline" className="px-1 py-0 text-[9px]">bot</Badge>
                          )}
                        </div>
                        <div className="shrink-0 text-xs text-text-muted">
                          {formatTime(message.timestamp)}
                        </div>
                      </div>
                      <div className="line-clamp-2 text-sm text-text-default">
                        {message.text || "Пустое сообщение"}
                      </div>
                      <div className="mt-2 flex items-center gap-2 text-xs text-text-muted">
                        {message.topicName && (
                          <Badge variant="outline" className="text-[10px]">
                            {message.topicName}
                          </Badge>
                        )}
                        <Badge variant="outline" className="text-[10px]">
                          {message.processingStatus}
                        </Badge>
                        {message.replyCount > 0 && (
                          <span>{message.replyCount} replies</span>
                        )}
                        {message.guideId && <span>Guide #{message.guideId}</span>}
                      </div>
                    </button>
                  ))}
                </div>
                {hasMoreMessages && (
                  <div className="flex justify-center border-t border-border-subtle py-3">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="gap-1.5"
                      onClick={() => undefined}
                      disabled
                    >
                      {null}
                      Загрузить ещё
                    </Button>
                  </div>
                )}
              </ScrollArea>
            )}
          </div>

          <div className="flex min-h-[260px] w-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card xl:w-80 xl:shrink-0">
            <div className="border-b border-border-subtle px-4 py-2.5">
              <span className="text-xs font-semibold uppercase text-text-muted">
                Детали сообщения
              </span>
            </div>
            {selectedMessage ? (
              <ScrollArea className="flex-1">
                <div className="flex flex-col gap-4 p-4">
                  <div>
                    <div className="mb-2 text-sm font-semibold text-text-strong">
                      Сообщение
                    </div>
                    <p className="whitespace-pre-wrap text-sm text-text-default">
                      {selectedMessage.text || "Пустое сообщение"}
                    </p>
                  </div>
                  <Separator />
                  <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
                    <span className="text-text-muted">Автор</span>
                    <span className="text-text-strong">{selectedMessage.author}</span>
                    <span className="text-text-muted">Дата</span>
                    <span>{formatTime(selectedMessage.timestamp)}</span>
                    <span className="text-text-muted">Статус</span>
                    <span>{selectedMessage.processingStatus}</span>
                    <span className="text-text-muted">Guide</span>
                    <span>{selectedMessage.guideId ?? "—"}</span>
                  </div>
                  <Separator />
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="gap-1.5"
                      onClick={() => setChainSheetOpen(true)}
                    >
                      <Stack size={14} weight="regular" />
                      Показать цепочку
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      className="gap-1.5"
                      onClick={() => enqueueMutation.mutate(selectedMessage.id)}
                      disabled={enqueueMutation.isPending}
                    >
                      <UploadSimple size={14} weight="regular" />
                      Отправить в очередь
                    </Button>
                  </div>
                </div>
              </ScrollArea>
            ) : (
              <div className="flex flex-1 items-center justify-center text-sm text-text-weak">
                Выберите сообщение для просмотра деталей
              </div>
            )}
          </div>
        </div>
      )}

      <Sheet open={chainSheetOpen} onOpenChange={setChainSheetOpen}>
        <SheetContent side="right" className="w-[480px] sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Цепочка сообщений</SheetTitle>
          </SheetHeader>
          <ScrollArea className="mt-4">
            {messageChainQuery.isLoading ? (
              <div className="flex items-center justify-center py-10">
                <SpinnerGap size={20} weight="regular" className="animate-spin text-text-muted" />
              </div>
            ) : selectedChain && selectedChain.messages.length > 0 ? (
              <div className="flex flex-col gap-3">
                {selectedChain.messages.map((message) => (
                  <div
                    key={message.id}
                    className="rounded-xl border border-border-subtle px-3 py-3"
                  >
                    <div className="mb-1 flex items-center justify-between gap-3">
                      <span className="text-sm font-medium text-text-strong">
                        {message.author}
                      </span>
                      <span className="text-xs text-text-muted">
                        {formatTime(message.timestamp)}
                      </span>
                    </div>
                    <div className="text-sm text-text-default">{message.text}</div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="py-8 text-center text-sm text-text-weak">
                Для этого сообщения цепочка не найдена
              </div>
            )}
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </div>
  );
}
