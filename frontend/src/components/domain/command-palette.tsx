import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  BookOpenText,
  Brain,
  ChartLineUp,
  ChatCircleText,
  DeviceMobile,
  FlowArrow,
  GearSix,
  MagnifyingGlass,
  SquaresFour,
  UsersThree,
} from "@phosphor-icons/react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";

interface SearchItem {
  label: string;
  route: string;
  icon: React.ElementType;
  category: string;
  keywords: string[];
}

const searchItems: SearchItem[] = [
  {
    label: "Сводка",
    route: "/dashboard",
    icon: SquaresFour,
    category: "Обзор",
    keywords: ["dashboard", "overview", "operations", "сводка"],
  },
  {
    label: "Telegram-аккаунты",
    route: "/accounts",
    icon: DeviceMobile,
    category: "Telegram",
    keywords: ["accounts", "reader", "tdlib", "аккаунты"],
  },
  {
    label: "Группы",
    route: "/groups",
    icon: UsersThree,
    category: "Telegram",
    keywords: ["groups", "chats", "группы"],
  },
  {
    label: "Просмотр чатов",
    route: "/groups",
    icon: ChatCircleText,
    category: "Telegram",
    keywords: ["chat", "messages", "viewer", "чаты", "сообщения"],
  },
  {
    label: "Материалы",
    route: "/materials",
    icon: BookOpenText,
    category: "Обработка",
    keywords: ["materials", "guides", "news", "faq", "risks", "материалы", "гайды"],
  },
  {
    label: "Классификаторы",
    route: "/classifiers",
    icon: FlowArrow,
    category: "Обработка",
    keywords: ["rules", "classifiers", "pipeline", "классификаторы", "правила"],
  },
  {
    label: "AI-провайдеры",
    route: "/ai",
    icon: Brain,
    category: "Обработка",
    keywords: ["ai", "providers", "prompts", "models", "llm", "провайдеры"],
  },
  {
    label: "Мониторинг",
    route: "/dashboard",
    icon: ChartLineUp,
    category: "Система",
    keywords: ["monitoring", "tokens", "cost", "errors", "мониторинг"],
  },
  {
    label: "Настройки",
    route: "/settings",
    icon: GearSix,
    category: "Система",
    keywords: ["settings", "configuration", "limits", "настройки"],
  },
];

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key === "k") {
        event.preventDefault();
        setOpen((previous) => !previous);
        setQuery("");
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  const filtered = query.trim()
    ? searchItems.filter((item) => {
        const normalizedQuery = query.toLowerCase();
        return (
          item.label.toLowerCase().includes(normalizedQuery)
          || item.keywords.some((keyword) => keyword.includes(normalizedQuery))
          || item.category.toLowerCase().includes(normalizedQuery)
        );
      })
    : searchItems;

  const grouped = filtered.reduce<Record<string, SearchItem[]>>((accumulator, item) => {
    if (!accumulator[item.category]) {
      accumulator[item.category] = [];
    }
    accumulator[item.category].push(item);
    return accumulator;
  }, {});

  const handleSelect = (route: string) => {
    setOpen(false);
    setQuery("");
    navigate(route);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="gap-0 overflow-hidden p-0 sm:max-w-lg">
        <DialogHeader className="sr-only">
          <DialogTitle>Поиск и навигация</DialogTitle>
        </DialogHeader>
        <div className="flex items-center border-b border-border-subtle px-4">
          <MagnifyingGlass size={16} weight="regular" className="mr-2 shrink-0 text-text-weak" />
          <input
            className="h-12 flex-1 bg-transparent text-sm text-text-strong placeholder:text-text-weak focus:outline-none"
            placeholder="Поиск страниц, групп и материалов..."
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            autoFocus
          />
          <kbd className="ml-2 pointer-events-none inline-flex h-5 select-none items-center gap-1 rounded border border-border-subtle bg-bg-app px-1.5 font-mono text-[10px] font-medium text-text-weak">
            Esc
          </kbd>
        </div>
        <div className="max-h-[300px] overflow-y-auto py-2">
          {Object.entries(grouped).map(([category, items]) => (
            <div key={category}>
              <div className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-text-weak">
                {category}
              </div>
              {items.map((item) => (
                <button
                  key={`${item.route}-${item.label}`}
                  className="flex w-full cursor-pointer items-center gap-3 px-4 py-2 text-sm text-text-default transition-colors hover:bg-bg-app"
                  onClick={() => handleSelect(item.route)}
                >
                  <item.icon size={16} weight="regular" className="shrink-0 text-text-muted" />
                  <span className="flex-1 text-left">{item.label}</span>
                  <Badge variant="outline" className="shrink-0 text-[10px]">
                    {item.route}
                  </Badge>
                </button>
              ))}
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="py-6 text-center text-sm text-text-weak">
              Ничего не найдено
            </div>
          )}
        </div>
        <div className="flex items-center gap-4 border-t border-border-subtle px-4 py-2 text-xs text-text-weak">
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">
              ↑↓
            </kbd>{" "}
            навигация
          </span>
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">
              ↵
            </kbd>{" "}
            открыть
          </span>
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">
              Esc
            </kbd>{" "}
            закрыть
          </span>
        </div>
      </DialogContent>
    </Dialog>
  );
}
