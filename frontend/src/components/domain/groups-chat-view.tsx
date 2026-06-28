import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { ArrowClockwise, ChatCircle, DotsThreeVertical, SpinnerGap } from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { GroupListPanel } from "@/components/domain/group-list-panel";
import { MessageDetailPanel } from "@/components/domain/message-detail-panel";
import { MessagesPanel, type MessageTab } from "@/components/domain/messages-panel";
import { RichMessageText } from "@/components/domain/rich-message-text";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  useEnqueueMessageMutation,
  useGroupMessagesQuery,
  useMessageByIdQuery,
  useMessageChainQuery,
  useSyncMessagesMutation,
  type MessageSyncResponse,
} from "@/shared/api/messagesApi";
import { useGroupsQuery } from "@/shared/api/groupsApi";
import { usePipelineEvents } from "@/shared/api/pipelineEvents";
import { useReconcileTopicsMutation, useSyncTopicsMutation, useTopicsQuery } from "@/shared/api/topicsApi";
import type { Group, Message, Topic } from "@/shared/types";

function formatChainTime(value: string): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString("ru-RU", { hour: "2-digit", minute: "2-digit" });
}

function isGeneralTopic(topicName: string): boolean {
  const normalized = topicName.trim().toLowerCase();
  return normalized === "general" || normalized === "основной";
}

function topicDedupKey(topic: Topic, accountId: string | null | undefined) {
  const chatId = topic.chatId || "unknown-chat";
  const forumTopicId = topic.forumTopicId || "";
  const messageThreadId = topic.messageThreadId || "";
  return forumTopicId
    ? `${accountId ?? "unknown-account"}:${chatId}:forum:${forumTopicId}`
    : `${accountId ?? "unknown-account"}:${chatId}:thread:${messageThreadId || "unknown-thread"}`;
}

function mergeTopics(primary: Topic[], secondary: Topic[], accountId: string | null | undefined): Topic[] {
  const merged = new Map<string, Topic>();

  for (const topic of [...primary, ...secondary]) {
    const key = topicDedupKey(topic, accountId);
    if (!merged.has(key)) {
      merged.set(key, topic);
    }
  }

  return Array.from(merged.values()).sort((left, right) => {
    if (left.general !== right.general) {
      return left.general ? -1 : 1;
    }
    return left.name.localeCompare(right.name, "ru-RU", { sensitivity: "base" });
  });
}

function isPlaceholderTopic(topic: Topic) {
  return !topic.name || topic.name === "Тема без названия" || topic.name.startsWith("Topic ") || topic.titleSource === "PLACEHOLDER_UNKNOWN";
}

function topicLabel(topic: Topic) {
  if (topic.general) return "Основной";
  if (!isPlaceholderTopic(topic)) return topic.name;
  const id = topic.forumTopicId || topic.messageThreadId;
  return id ? `Тема #${id}` : "Тема";
}

function syncStatusText(response: MessageSyncResponse | undefined, error: unknown): string | null {
  if (error) {
    return "Ошибка синхронизации";
  }
  if (!response) {
    return null;
  }
  const labels: Record<string, string> = {
    scheduled: "Очередь синхронизации",
    already_running: "Синхронизация...",
    cooldown: "Синхронизировано недавно",
    executor_saturated: "Очередь синхронизации",
    telegram_not_ready: "Telegram сейчас недоступен",
    group_not_found: "Чат не найден",
    disabled_group: "Чат отключён",
    error: "Ошибка синхронизации",
  };
  return labels[response.reason] ?? `Синхронизация: ${response.reason}`;
}

function groupTypeLabel(group: Group) {
  if (group.forum) return "форум";
  if (group.sourceType === "CHANNEL") return "канал";
  if (group.sourceType === "DIRECT_CHAT") return "личный";
  return "группа";
}

function filterMessages(messages: Message[], filter: string) {
  if (filter === "media") return messages.filter((message) => message.hasMedia);
  if (filter === "links") return messages.filter((message) => message.hasLinks);
  if (filter === "voice") return messages.filter((message) => message.hasVoice);
  if (filter === "documents") return messages.filter((message) => message.hasDocument);
  if (filter === "text") return messages.filter((message) => !message.hasMedia && !message.hasLinks);
  return messages;
}

export function GroupsChatView() {
  const connectionState = usePipelineEvents();

  const [searchParams, setSearchParams] = useSearchParams();
  const preselectedGroupId = searchParams.get("group");
  const preselectedChatId = searchParams.get("chatId");
  const preselectedMessageId = searchParams.get("message");

  const [showDisabled, setShowDisabled] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [activeTopicId, setActiveTopicId] = useState<string | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<Message | null>(null);
  const [chainSheetOpen, setChainSheetOpen] = useState(false);
  const [contentFilter, setContentFilter] = useState("all");
  const [lastAutoSyncKey, setLastAutoSyncKey] = useState<string | null>(null);
  const [lastAutoTopicSyncKey, setLastAutoTopicSyncKey] = useState<string | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = useState<Date | null>(null);

  const groupsQuery = useGroupsQuery({ accountId: "1", size: 200 });
  const allGroups = groupsQuery.data?.content ?? [];
  const groups = useMemo(
    () => (showDisabled ? allGroups : allGroups.filter((group) => group.enabled)),
    [allGroups, showDisabled]
  );

  const topicsQuery = useTopicsQuery(
    selectedGroup?.forum ? selectedGroup.telegramChatId : undefined
  );
  const topics = topicsQuery.data ?? [];
  const syncTopicsMutation = useSyncTopicsMutation(selectedGroup?.telegramChatId);
  const reconcileTopicsMutation = useReconcileTopicsMutation(selectedGroup?.telegramChatId);

  const messagesQuery = useGroupMessagesQuery(selectedGroup?.id, activeTopicId, {
    disablePolling: connectionState === "connected",
  });
  const pinnedMessageQuery = useMessageByIdQuery(
    selectedGroup?.id,
    preselectedMessageId ?? undefined
  );

  const fetchedMessages = messagesQuery.data?.content ?? [];
  const pinnedMessage =
    pinnedMessageQuery.data &&
    !fetchedMessages.some((message) => message.id === pinnedMessageQuery.data?.id)
      ? pinnedMessageQuery.data
      : null;
  const groupMessages = pinnedMessage ? [pinnedMessage, ...fetchedMessages] : fetchedMessages;
  const visibleMessages = useMemo(
    () => filterMessages(groupMessages, contentFilter),
    [contentFilter, groupMessages]
  );
  const totalMessages = messagesQuery.data?.totalElements ?? 0;

  const fallbackTopics = useMemo<Topic[]>(() => {
    if (!selectedGroup?.forum) {
      return [];
    }

    const uniqueTopics = new Map<string, Topic>();
    for (const message of groupMessages) {
      if (!message.topicId || !message.topicName) {
        continue;
      }

      if (!uniqueTopics.has(message.topicId)) {
        uniqueTopics.set(message.topicId, {
          chatId: selectedGroup.telegramChatId,
          forumTopicId: message.topicId,
          messageThreadId: message.topicId,
          name: message.topicName,
          general: isGeneralTopic(message.topicName),
        });
      }
    }

    return Array.from(uniqueTopics.values());
  }, [groupMessages, selectedGroup?.forum, selectedGroup?.telegramChatId]);

  const availableTopics = useMemo(
    () => mergeTopics(topics, fallbackTopics, selectedGroup?.accountId),
    [fallbackTopics, selectedGroup?.accountId, topics]
  );

  const tabs = useMemo<MessageTab[]>(() => {
    if (!selectedGroup?.forum || availableTopics.length === 0) {
      return [];
    }

    return [
      { id: null, label: "Все" },
      ...availableTopics.map((topic) => ({
        id: topic.messageThreadId || topic.forumTopicId,
        label: topicLabel(topic),
      })),
    ];
  }, [availableTopics, selectedGroup?.forum]);

  const messageChainQuery = useMessageChainQuery(selectedGroup?.id, selectedMessage?.id);
  const enqueueMutation = useEnqueueMessageMutation(selectedGroup?.id);
  const syncMessagesMutation = useSyncMessagesMutation(selectedGroup?.id);
  const topicActionStatus = syncTopicsMutation.isPending
    ? "Синхронизация тем..."
    : reconcileTopicsMutation.isPending
      ? "Сверяю названия тем..."
      : null;
  const syncStatus = topicActionStatus
    ?? (syncMessagesMutation.isPending ? "Синхронизация..." : null)
    ?? syncStatusText(syncMessagesMutation.data, syncMessagesMutation.error)
    ?? (lastSyncedAt ? `Синхронизировано ${lastSyncedAt.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}` : null);
  const handleManualSync = () => {
    if (!selectedGroup || syncMessagesMutation.isPending || syncTopicsMutation.isPending) {
      return;
    }
    syncMessagesMutation.mutate(undefined, { onSuccess: () => setLastSyncedAt(new Date()) });
    if (selectedGroup.forum) {
      syncTopicsMutation.mutate();
    }
  };

  useEffect(() => {
    if (groups.length === 0) {
      return;
    }

    // URL is the source of truth on (deep-)link open. Resolve it to a group,
    // but stay idempotent: if the current selection already matches the URL,
    // do nothing so a manual click is never reverted.
    if (preselectedGroupId) {
      if (selectedGroup?.id === preselectedGroupId) {
        return;
      }
      const match = groups.find((group) => group.id === preselectedGroupId);
      if (match) {
        setSelectedGroup(match);
      }
      return;
    }

    if (preselectedChatId) {
      if (selectedGroup?.telegramChatId === preselectedChatId) {
        return;
      }
      const match = groups.find((group) => group.telegramChatId === preselectedChatId);
      if (match) {
        setSelectedGroup(match);
      }
      return;
    }

    if (!selectedGroup) {
      setSelectedGroup(groups[0]);
    }
  }, [groups, preselectedChatId, preselectedGroupId, selectedGroup]);

  const handleSelectGroup = useCallback((group: Group) => {
    // Manual click becomes the new source of truth: rewrite the URL to the
    // canonical ?group={internalId}, dropping a stale chatId/message deep link
    // so the URL effect selects this group once and never bounces via stale state.
    setSearchParams(
      (params) => {
        const next = new URLSearchParams(params);
        next.set("group", group.id);
        next.delete("chatId");
        next.delete("message");
        return next;
      },
      { replace: true }
    );
  }, [setSearchParams]);

  useEffect(() => {
    setSelectedMessage(null);
    setActiveTopicId(null);
    setContentFilter("all");
  }, [selectedGroup?.id]);

  useEffect(() => {
    if (!preselectedMessageId || groupMessages.length === 0) {
      return;
    }

    const match = groupMessages.find((message) => message.id === preselectedMessageId);
    if (match && selectedMessage?.id !== match.id) {
      setSelectedMessage(match);
    }
  }, [groupMessages, preselectedMessageId, selectedMessage?.id]);

  useEffect(() => {
    if (selectedGroup && !selectedGroup.enabled && !showDisabled) {
      const firstEnabled = groups.find((group) => group.enabled);
      setSelectedGroup(firstEnabled ?? null);
    }
  }, [groups, selectedGroup, showDisabled]);

  useEffect(() => {
    setSelectedMessage(null);
  }, [activeTopicId]);

  useEffect(() => {
    if (!selectedGroup || syncMessagesMutation.isPending) {
      return;
    }

    const key = `${selectedGroup.id}:${activeTopicId ?? "all"}`;
    if (lastAutoSyncKey === key) {
      return;
    }

    const timer = window.setTimeout(() => {
      syncMessagesMutation.mutate(undefined, { onSuccess: () => setLastSyncedAt(new Date()) });
      setLastAutoSyncKey(key);
    }, 700);

    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTopicId, lastAutoSyncKey, selectedGroup?.id]);

  useEffect(() => {
    if (!selectedGroup?.forum || syncTopicsMutation.isPending) {
      return;
    }

    const key = `${selectedGroup.telegramChatId}:${activeTopicId ?? "all"}`;
    if (lastAutoTopicSyncKey === key) {
      return;
    }

    const timer = window.setTimeout(() => {
      syncTopicsMutation.mutate();
      setLastAutoTopicSyncKey(key);
    }, 900);
    return () => window.clearTimeout(timer);
  }, [activeTopicId, lastAutoTopicSyncKey, selectedGroup?.forum, selectedGroup?.telegramChatId, syncTopicsMutation]);

  const selectedChain = messageChainQuery.data;
  const centerTitle = selectedGroup?.title ?? "Сообщения";
  const centerSubtitle = selectedGroup?.telegramChatId ?? "";

  if (groups.length === 0 && !groupsQuery.isLoading) {
    return (
      <EmptyState
        icon={ChatCircle}
        title="Нет групп"
        description="Группы подтягиваются из подключённых Telegram-аккаунтов."
      />
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-140px)] gap-3 xl:flex-row flex-col">
      <div className="xl:w-72 xl:shrink-0 min-h-[280px]">
        <GroupListPanel
          groups={groups}
          isLoading={groupsQuery.isLoading}
          selectedGroupId={selectedGroup?.id ?? null}
          onSelectGroup={handleSelectGroup}
          showDisabled={showDisabled}
          onToggleShowDisabled={setShowDisabled}
        />
      </div>

      <div className="flex-1 min-h-[360px] min-w-0">
        {selectedGroup && (
          <div className="mb-3 rounded-2xl border border-border-subtle bg-bg-card p-3">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="truncate text-lg font-semibold text-text-strong">{selectedGroup.title}</h2>
                  <Badge variant="outline">{groupTypeLabel(selectedGroup)}</Badge>
                  <Badge variant={selectedGroup.enabled ? "success" : "warning"}>{selectedGroup.enabled ? "ingest включён" : "ingest отключён"}</Badge>
                </div>
                <div className="mt-2 flex flex-wrap gap-3 text-xs text-text-muted">
                  <span>Сообщений: {totalMessages}</span>
                  <span>Тем: {selectedGroup.forum ? availableTopics.length : 0}</span>
                  <span>{syncStatus ?? `Синхронизировано: ${selectedGroup.lastReadAt ? new Date(selectedGroup.lastReadAt).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }) : "нет данных"}`}</span>
                  <span>ID: {selectedGroup.telegramChatId}</span>
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-end gap-2">
                <Button variant="outline" size="icon" onClick={handleManualSync} disabled={syncMessagesMutation.isPending || syncTopicsMutation.isPending} title="Синхронизировать сейчас" aria-label="Синхронизировать сейчас">
                  {syncMessagesMutation.isPending || syncTopicsMutation.isPending ? <SpinnerGap size={17} className="animate-spin" /> : <ArrowClockwise size={17} />}
                </Button>
              </div>
            </div>
          </div>
        )}
        <MessagesPanel
          messages={visibleMessages}
          totalMessages={totalMessages}
          isLoading={messagesQuery.isLoading}
          selectedMessageId={selectedMessage?.id ?? null}
          onSelectMessage={setSelectedMessage}
          headerTitle={centerTitle}
          headerSubtitle={centerSubtitle}
          tabs={tabs.length > 0 ? tabs : undefined}
          activeTabId={activeTopicId}
          onTabChange={setActiveTopicId}
          onSync={undefined}
          isSyncing={syncMessagesMutation.isPending}
          syncStatus={syncStatus}
          contentFilter={contentFilter}
          onContentFilterChange={setContentFilter}
        />
        {selectedGroup?.forum && (
          <div className="mt-2 flex flex-wrap gap-2 rounded-2xl border border-border-subtle bg-bg-card p-2">
            <button className="rounded-xl bg-bg-elevated px-2 py-1.5 text-text-muted" onClick={() => reconcileTopicsMutation.mutate()} disabled={reconcileTopicsMutation.isPending} title="Дополнительные действия по темам" aria-label="Дополнительные действия по темам"><DotsThreeVertical size={18} /></button>
          </div>
        )}
      </div>

      <div className="xl:w-80 xl:shrink-0 min-h-[260px]">
        <MessageDetailPanel
          message={selectedMessage}
          group={selectedGroup}
          onShowChain={() => setChainSheetOpen(true)}
          onEnqueue={(messageId) => enqueueMutation.mutate(messageId)}
          isEnqueueing={enqueueMutation.isPending}
        />
      </div>

      <Sheet open={chainSheetOpen} onOpenChange={setChainSheetOpen}>
        <SheetContent side="right" className="w-[480px] sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Цепочка сообщений</SheetTitle>
          </SheetHeader>
          <ScrollArea className="mt-4">
            {messageChainQuery.isLoading ? (
              <div className="flex items-center justify-center py-10">
                <SpinnerGap
                  size={20}
                  weight="regular"
                  className="animate-spin text-text-muted"
                />
              </div>
            ) : selectedChain && selectedChain.messages.length > 0 ? (
              <div className="flex flex-col gap-0">
                {selectedChain.messages.map((message, index) => {
                  const previous = index > 0 ? selectedChain.messages[index - 1] : null;
                  const sameAuthor = previous && previous.author === message.author;
                  const isActive = selectedMessage?.id === message.id;

                  return (
                    <button
                      key={message.id}
                      onClick={() => setSelectedMessage(message)}
                      className={`flex w-full flex-col rounded-[16px] px-3 pb-2 pt-1.5 text-left transition-colors ${
                        isActive
                          ? "bg-brand-blue-soft/70 ring-1 ring-brand-blue/30"
                          : "bg-bg-card hover:bg-bg-card/80"
                      } ${sameAuthor ? "mt-0.5" : "mt-2"}`}
                    >
                      {!sameAuthor && (
                        <span className="mb-0.5 text-[13px] font-semibold text-brand-blue">
                          {message.author || "Unknown"}
                        </span>
                      )}
                      <div className="whitespace-pre-wrap break-words text-[13.5px] leading-relaxed text-text-default">
                        <RichMessageText text={message.text} />
                      </div>
                      <div className="mt-0.5 flex items-center justify-end gap-1.5">
                        <span className="text-[10px] text-text-weak">
                          {formatChainTime(message.timestamp)}
                        </span>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="py-8 text-center text-sm text-text-weak">
                {selectedMessage
                  ? "Для этого сообщения цепочка не найдена"
                  : "Выберите сообщение"}
              </div>
            )}
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </div>
  );
}
