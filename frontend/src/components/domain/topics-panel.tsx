import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import { SpinnerGap, Hash, Star } from "@phosphor-icons/react";
import type { Topic } from "@/shared/types";

interface TopicsPanelProps {
  topics: Topic[];
  isLoading: boolean;
  selectedTopicId: string | null;
  onSelectTopic: (topic: Topic) => void;
  groupTitle: string;
}

export function TopicsPanel({
  topics,
  isLoading,
  selectedTopicId,
  onSelectTopic,
  groupTitle,
}: TopicsPanelProps) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-border-subtle px-3 py-2">
        <Hash size={14} weight="regular" className="text-brand-blue" />
        <span className="text-xs font-semibold uppercase text-text-muted">
          Темы — {groupTitle}
        </span>
        {isLoading && (
          <SpinnerGap
            size={12}
            weight="regular"
            className="ml-auto animate-spin text-text-muted"
          />
        )}
      </div>

      {/* Topic list */}
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-0.5 p-1.5">
          {topics.length === 0 && !isLoading ? (
            <div className="py-6 text-center text-xs text-text-weak">
              Нет тем в этой группе
            </div>
          ) : (
            topics.map((topic) => (
              <button
                key={topic.forumTopicId}
                onClick={() => onSelectTopic(topic)}
                className={`rounded-lg px-3 py-2.5 text-left transition-colors ${
                  selectedTopicId === topic.messageThreadId
                    ? "bg-brand-blue-soft text-text-strong"
                    : "text-text-default hover:bg-bg-app"
                }`}
              >
                <div className="flex items-center gap-2">
                  <div
                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                      selectedTopicId === topic.messageThreadId
                        ? "bg-brand-blue/20 text-brand-blue"
                        : topic.general
                        ? "bg-warning/15 text-warning"
                        : "bg-bg-elevated text-text-muted"
                    }`}
                  >
                    {topic.general ? (
                      <Star size={14} weight="fill" />
                    ) : (
                      topic.name.charAt(0).toUpperCase()
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate text-sm font-medium">
                        {topic.name}
                      </span>
                      {topic.general && (
                        <Badge
                          variant="outline"
                          className="px-1.5 py-0 text-[10px]"
                        >
                          general
                        </Badge>
                      )}
                    </div>
                    <div className="mt-0.5 text-xs text-text-muted">
                      thread {topic.messageThreadId}
                    </div>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
