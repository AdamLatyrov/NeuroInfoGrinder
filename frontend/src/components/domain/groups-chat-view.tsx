import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { SpinnerGap, ChatCircle } from "@phosphor-icons/react";
import { EmptyState } from "@/components/domain/empty-state";
import { GroupListPanel } from "@/components/domain/group-list-panel";
import { MessageDetailPanel } from "@/components/domain/message-detail-panel";
import { MessagesPanel, type MessageTab } from "@/components/domain/messages-panel";
import { RichMessageText } from "@/components/domain/rich-message-text";
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
} from "@/shared/api/messagesApi";
import { useGroupsQuery } from "@/shared/api/groupsApi";
import { usePipelineEvents } from "@/shared/api/pipelineEvents";
import { useTopicsQuery } from "@/shared/api/topicsApi";
import { getTelegramSyncSeconds, onUiSettingsChange } from "@/shared/ui-settings";
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

function mergeTopics(primary: Topic[], secondary: Topic[]): Topic[] {
  const merged = new Map<string, Topic>();

  for (const topic of [...primary, ...secondary]) {
    const key = topic.messageThreadId || topic.forumTopicId;
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

export function GroupsChatView() {
  const connectionState = usePipelineEvents();

  const [searchParams] = useSearchParams();
  const preselectedGroupId = searchParams.get("group");
  const preselectedMessageId = searchParams.get("message");

  const [showDisabled, setShowDisabled] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [activeTopicId, setActiveTopicId] = useState<string | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<Message | null>(null);
  const [chainSheetOpen, setChainSheetOpen] = useState(false);
  const [telegramSyncMs, setTelegramSyncMs] = useState(() => getTelegramSyncSeconds() * 1000);

  const groupsQuery = useGroupsQuery({ size: 200 });
  const allGroups = groupsQuery.data?.content ?? [];
  const groups = useMemo(
    () => (showDisabled ? allGroups : allGroups.filter((group) => group.enabled)),
    [allGroups, showDisabled]
  );

  const topicsQuery = useTopicsQuery(
    selectedGroup?.forum ? selectedGroup.telegramChatId : undefined
  );
  const topics = topicsQuery.data ?? [];

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
    () => mergeTopics(topics, fallbackTopics),
    [fallbackTopics, topics]
  );

  const tabs = useMemo<MessageTab[]>(() => {
    if (!selectedGroup?.forum || availableTopics.length === 0) {
      return [];
    }

    return [
      { id: null, label: "Все" },
      ...availableTopics.map((topic) => ({
        id: topic.messageThreadId || topic.forumTopicId,
        label: topic.general ? "Основной" : topic.name,
      })),
    ];
  }, [availableTopics, selectedGroup?.forum]);

  const messageChainQuery = useMessageChainQuery(selectedGroup?.id, selectedMessage?.id);
  const enqueueMutation = useEnqueueMessageMutation(selectedGroup?.id);
  const syncMessagesMutation = useSyncMessagesMutation(selectedGroup?.id);

  useEffect(() => {
    if (!selectedGroup && groups.length > 0) {
      const match = preselectedGroupId
        ? groups.find((group) => group.id === preselectedGroupId)
        : null;
      setSelectedGroup(match ?? groups[0]);
    }
  }, [groups, preselectedGroupId, selectedGroup]);

  useEffect(() => {
    setSelectedMessage(null);
    setActiveTopicId(null);
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
    return onUiSettingsChange(() => {
      setTelegramSyncMs(getTelegramSyncSeconds() * 1000);
    });
  }, []);

  const effectiveSyncMs =
    connectionState === "connected" ? Math.max(telegramSyncMs, 30_000) : telegramSyncMs;

  useEffect(() => {
    if (!selectedGroup?.id || !selectedGroup.enabled) {
      return;
    }

    syncMessagesMutation.mutate();
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === "visible" && !syncMessagesMutation.isPending) {
        syncMessagesMutation.mutate();
      }
    }, effectiveSyncMs);

    return () => window.clearInterval(intervalId);
  }, [
    effectiveSyncMs,
    selectedGroup?.enabled,
    selectedGroup?.id,
    syncMessagesMutation,
    syncMessagesMutation.isPending,
  ]);

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
          onSelectGroup={setSelectedGroup}
          showDisabled={showDisabled}
          onToggleShowDisabled={setShowDisabled}
        />
      </div>

      <div className="flex-1 min-h-[360px] min-w-0">
        <MessagesPanel
          messages={groupMessages}
          totalMessages={totalMessages}
          isLoading={messagesQuery.isLoading}
          selectedMessageId={selectedMessage?.id ?? null}
          onSelectMessage={setSelectedMessage}
          headerTitle={centerTitle}
          headerSubtitle={centerSubtitle}
          tabs={tabs.length > 0 ? tabs : undefined}
          activeTabId={activeTopicId}
          onTabChange={setActiveTopicId}
          onSync={() => syncMessagesMutation.mutate()}
          isSyncing={syncMessagesMutation.isPending}
        />
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
