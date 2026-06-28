import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getJsonAuth } from "@/shared/api/http";

interface MessageLink {
  domain: string | null;
  url: string;
}

interface Message {
  id: number;
  telegramMessageId: number;
  groupTitle: string;
  telegramChatId: number | null;
  senderName: string | null;
  senderUsername: string | null;
  date: string | null;
  text: string | null;
  caption: string | null;
  contentType: string;
  topicName: string | null;
  topicId: number | null;
  hasMedia: boolean;
  hasLinks: boolean;
  links: MessageLink[];
  messageIntelligenceJson: string | null;
}

function preview(message: Message) {
  const value = message.text || message.caption || "";
  return value.length > 240 ? `${value.slice(0, 240)}...` : value;
}

export function MessagesPage() {
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Message | null>(null);
  const messages = useQuery({
    queryKey: ["messages-page-recent"],
    queryFn: () => getJsonAuth<Message[]>("/api/v2/telegram/messages/recent?limit=200"),
    refetchInterval: 10000,
  });

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const items = messages.data ?? [];
    if (!needle) return items;
    return items.filter((message) =>
      [message.groupTitle, message.topicName, message.senderName, message.senderUsername, message.text, message.caption]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(needle))
    );
  }, [messages.data, search]);

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Технические сообщения"
        description="Скрытая debug-страница recent raw messages. Основной пользовательский сценарий сообщений находится в разделе Группы."
        actions={{ onRefresh: () => messages.refetch() }}
      />

      <div className="grid gap-3 md:grid-cols-3">
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">Всего загружено</div><div className="mt-1 font-mono-value text-2xl text-text-strong">{messages.data?.length ?? 0}</div></CardContent></Card>
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">С медиа</div><div className="mt-1 font-mono-value text-2xl text-text-strong">{(messages.data ?? []).filter((message) => message.hasMedia).length}</div></CardContent></Card>
        <Card><CardContent className="p-4"><div className="text-xs text-text-muted">Со ссылками</div><div className="mt-1 font-mono-value text-2xl text-text-strong">{(messages.data ?? []).filter((message) => message.hasLinks).length}</div></CardContent></Card>
      </div>

      <Input placeholder="Поиск по чату, теме, отправителю или тексту" value={search} onChange={(event) => setSearch(event.target.value)} />

      <div className="grid gap-4 xl:grid-cols-[1fr_460px]">
        <div className="space-y-3">
          {messages.isLoading ? <div className="rounded-2xl border border-border-subtle bg-bg-card p-4 text-sm text-text-muted">Загрузка...</div> : null}
          {filtered.length === 0 && !messages.isLoading ? <div className="rounded-2xl border border-border-subtle bg-bg-card p-4 text-sm text-text-muted">Нет данных</div> : null}
          {filtered.map((message) => (
            <button key={message.id} type="button" className="w-full rounded-2xl border border-border-subtle bg-bg-card p-4 text-left transition hover:border-brand-blue/40" onClick={() => setSelected(message)}>
              <div className="flex flex-wrap items-center gap-2 text-xs text-text-muted">
                <Badge variant="outline">{message.groupTitle}</Badge>
                {message.topicName ? <Badge variant="secondary">{message.topicName}</Badge> : null}
                <span>{message.senderName ?? message.senderUsername ?? "Отправитель неизвестен"}</span>
                <span>{message.date ?? "Дата неизвестна"}</span>
              </div>
              <p className="mt-2 text-sm text-text-strong">{preview(message) || "Нет текстового превью"}</p>
            </button>
          ))}
        </div>

        <aside className="rounded-3xl border border-border-subtle bg-bg-card p-5">
          <h2 className="text-lg font-semibold text-text-strong">Детали сообщения</h2>
          {selected ? (
            <div className="mt-4 space-y-3 text-sm">
              <Info label="ID сообщения" value={String(selected.telegramMessageId)} />
              <Info label="Чат" value={`${selected.groupTitle} (${selected.telegramChatId ?? "-"})`} />
              <Info label="Тема" value={selected.topicName ?? "Тема без названия"} />
              <Info label="Отправитель" value={selected.senderName ?? selected.senderUsername ?? "Неизвестно"} />
              <Info label="Тип" value={selected.contentType} />
              <div className="rounded-2xl bg-bg-elevated p-3">
                <div className="text-xs uppercase tracking-wide text-text-weak">Текст</div>
                <p className="mt-2 whitespace-pre-wrap text-text-strong">{selected.text || selected.caption || "Нет текста"}</p>
              </div>
              <div className="rounded-2xl bg-bg-elevated p-3">
                <div className="text-xs uppercase tracking-wide text-text-weak">Ссылки</div>
                <div className="mt-2 space-y-1">
                  {selected.links.length ? selected.links.map((link) => <a key={link.url} href={link.url} target="_blank" rel="noreferrer" className="block break-all text-brand-blue">{link.domain ?? link.url}</a>) : <span className="text-text-muted">Нет ссылок</span>}
                </div>
              </div>
              <Button variant="outline" onClick={() => navigator.clipboard.writeText(selected.messageIntelligenceJson ?? "{}")}>Скопировать JSON</Button>
            </div>
          ) : (
            <p className="mt-4 text-sm text-text-muted">Выберите сообщение слева, чтобы посмотреть raw-текст, тему, ссылки и JSON.</p>
          )}
        </aside>
      </div>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl bg-bg-elevated p-3"><div className="text-xs uppercase tracking-wide text-text-weak">{label}</div><div className="mt-1 break-words text-text-strong">{value}</div></div>;
}
