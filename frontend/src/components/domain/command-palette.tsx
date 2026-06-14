import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  SquaresFour,
  DeviceMobile,
  UsersThree,
  ChatCircleText,
  BookOpenText,
  FlowArrow,
  Brain,
  ChartLineUp,
  GearSix,
  MagnifyingGlass,
} from "@phosphor-icons/react";

interface SearchItem {
  label: string;
  route: string;
  icon: React.ElementType;
  category: string;
  keywords: string[];
}

const searchItems: SearchItem[] = [
  { label: "Сводка", route: "/dashboard", icon: SquaresFour, category: "Overview", keywords: ["dashboard", "сводка", "операционная"] },
  { label: "Telegram-аккаунты", route: "/accounts", icon: DeviceMobile, category: "Telegram", keywords: ["accounts", "аккаунты", "reader", "tdlib"] },
  { label: "Группы", route: "/groups", icon: UsersThree, category: "Telegram", keywords: ["groups", "группы", "chats", "чаты"] },
  { label: "Просмотр чатов", route: "/chat-viewer", icon: ChatCircleText, category: "Telegram", keywords: ["chat", "чаты", "messages", "сообщения"] },
  { label: "Гайды", route: "/guides", icon: BookOpenText, category: "Processing", keywords: ["guides", "гайды", "инструкции", "kanban"] },
  { label: "Правила", route: "/rules", icon: FlowArrow, category: "Processing", keywords: ["rules", "правила", "классификаторы", "pipeline"] },
  { label: "AI-провайдеры", route: "/ai-providers", icon: Brain, category: "Processing", keywords: ["ai", "providers", "промпты", "models", "llm"] },
  { label: "Мониторинг", route: "/monitoring", icon: ChartLineUp, category: "System", keywords: ["monitoring", "мониторинг", "tokens", "токены", "cost", "errors"] },
  { label: "Настройки", route: "/settings", icon: GearSix, category: "System", keywords: ["settings", "настройки", "конфигурация", "limits"] },
];

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setOpen((prev) => !prev);
        setQuery("");
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  const filtered = query.trim()
    ? searchItems.filter((item) => {
        const q = query.toLowerCase();
        return (
          item.label.toLowerCase().includes(q) ||
          item.keywords.some((k) => k.includes(q)) ||
          item.category.toLowerCase().includes(q)
        );
      })
    : searchItems;

  const grouped = filtered.reduce<Record<string, SearchItem[]>>((acc, item) => {
    if (!acc[item.category]) acc[item.category] = [];
    acc[item.category].push(item);
    return acc;
  }, {});

  const handleSelect = (route: string) => {
    setOpen(false);
    setQuery("");
    navigate(route);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-lg p-0 gap-0 overflow-hidden">
        <DialogHeader className="sr-only">
          <DialogTitle>Поиск и навигация</DialogTitle>
        </DialogHeader>
        {/* Search input */}
        <div className="flex items-center border-b border-border-subtle px-4">
          <MagnifyingGlass size={16} weight="regular" className="mr-2 shrink-0 text-text-weak" />
          <input
            className="flex-1 h-12 bg-transparent text-sm text-text-strong placeholder:text-text-weak focus:outline-none"
            placeholder="Поиск страниц, групп, гайдов..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
          <kbd className="ml-2 pointer-events-none inline-flex h-5 select-none items-center gap-1 rounded border border-border-subtle bg-bg-app px-1.5 font-mono text-[10px] font-medium text-text-weak">
            Esc
          </kbd>
        </div>
        {/* Results */}
        <div className="max-h-[300px] overflow-y-auto py-2">
          {Object.entries(grouped).map(([category, items]) => (
            <div key={category}>
              <div className="px-4 py-1.5 text-xs font-semibold text-text-weak uppercase tracking-wider">
                {category}
              </div>
              {items.map((item) => (
                <button
                  key={item.route}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-text-default hover:bg-bg-app transition-colors cursor-pointer"
                  onClick={() => handleSelect(item.route)}
                >
                  <item.icon size={16} weight="regular" className="text-text-muted shrink-0" />
                  <span className="flex-1 text-left">{item.label}</span>
                  <Badge variant="outline" className="text-[10px] shrink-0">
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
        {/* Footer hint */}
        <div className="border-t border-border-subtle px-4 py-2 flex items-center gap-4 text-xs text-text-weak">
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">↑↓</kbd> навигация
          </span>
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">↵</kbd> открыть
          </span>
          <span>
            <kbd className="inline-flex h-4 items-center rounded border border-border-subtle bg-bg-app px-1 font-mono text-[10px]">Esc</kbd> закрыть
          </span>
        </div>
      </DialogContent>
    </Dialog>
  );
}
