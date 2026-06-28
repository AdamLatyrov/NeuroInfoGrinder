import { NavLink, useLocation } from "react-router-dom";
import {
  IconBook2,
  IconBraces,
  IconDeviceMobileFilled,
  IconHeartbeat,
  IconMenu2,
  IconMoonFilled,
  IconRoute,
  IconSettingsFilled,
  IconSparklesFilled,
  IconSunFilled,
  IconUsersGroup,
} from "@tabler/icons-react";
import { useTheme } from "@/app/theme";
import { clearToken, useCurrentUser } from "@/shared/api/authApi";
import { useProvidersQuery } from "@/shared/api/providersApi";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

export interface NavItem {
  to: string;
  icon: React.ElementType;
  label: string;
  activePaths: string[];
}

export const navGroups: { label: string; items: NavItem[] }[] = [
  {
    label: "Telegram",
    items: [
      { to: "/operator", icon: IconHeartbeat, label: "Оператор", activePaths: ["/", "/dashboard", "/operator"] },
      { to: "/accounts", icon: IconDeviceMobileFilled, label: "Аккаунты", activePaths: ["/accounts"] },
      { to: "/groups", icon: IconUsersGroup, label: "Группы", activePaths: ["/groups", "/messages", "/chat-viewer"] },
    ],
  },
  {
    label: "Обработка",
    items: [
      { to: "/pipeline", icon: IconRoute, label: "Конвейер сообщений", activePaths: ["/pipeline"] },
      { to: "/materials", icon: IconBook2, label: "Материалы", activePaths: ["/materials", "/guides"] },
    ],
  },
  {
    label: "AI",
    items: [
      { to: "/ai", icon: IconSparklesFilled, label: "AI-провайдеры", activePaths: ["/ai", "/providers", "/ai-providers", "/fronts"] },
      { to: "/prompts", icon: IconBraces, label: "Промпты", activePaths: ["/prompts"] },
    ],
  },
  {
    label: "Система",
    items: [{ to: "/settings", icon: IconSettingsFilled, label: "Настройки", activePaths: ["/settings"] }],
  },
];

export function getActiveNavItem(pathname: string) {
  return navGroups
    .flatMap((group) => group.items)
    .find((item) => item.activePaths.some((path) => matchesNavPath(pathname, path)));
}

function matchesNavPath(pathname: string, activePath: string) {
  if (activePath === "/") return pathname === "/";
  return pathname === activePath || pathname.startsWith(`${activePath}/`);
}

function navClassName(isActive: boolean) {
  return cn(
    "sidebar-nav-link grid h-12 w-12 place-items-center rounded-[18px] transition-all duration-150",
    isActive
      ? "bg-[#f1c75b] text-[#1f2430] shadow-[inset_0_0_0_1px_rgba(139,99,12,0.20),0_10px_24px_rgba(185,133,23,0.28)]"
      : "bg-bg-elevated text-text-muted hover:bg-brand-blue-soft hover:text-text-strong"
  );
}

export function ThemeToggleButton({ className }: { className?: string }) {
  const { theme, toggleTheme } = useTheme();

  return (
    <Button
      type="button"
      variant="outline"
      size="icon"
      className={cn("rounded-2xl border-sidebar-border bg-bg-card text-text-strong", className)}
      onClick={toggleTheme}
      title={theme === "dark" ? "Переключить на светлую тему" : "Переключить на тёмную тему"}
    >
      {theme === "dark" ? <IconSunFilled size={16} /> : <IconMoonFilled size={16} />}
    </Button>
  );
}

function SidebarNavContent({ onNavigate }: { onNavigate?: () => void }) {
  const location = useLocation();
  const currentUserQuery = useCurrentUser();
  const providersQuery = useProvidersQuery();
  const activeItem = getActiveNavItem(location.pathname);
  const username = currentUserQuery.data?.username ?? "Admin";
  const hasProviderError = (providersQuery.data ?? []).some(
    (provider) => provider.status === "ERROR" || !!provider.lastError
  );

  function handleLogout() {
    clearToken();
    window.location.href = "/login";
  }

  return (
    <TooltipProvider delayDuration={200}>
      <div className="flex h-full w-full flex-col items-center">
        <nav className="flex flex-1 flex-col items-center gap-4 overflow-y-auto pt-1">
          {navGroups.map((group) => (
            <div key={group.label} className="flex flex-col items-center gap-4">
              {group.items.map((item) => {
                const isActive = activeItem?.to === item.to;

                return <Tooltip key={item.to}>
                  <TooltipTrigger asChild>
                    <NavLink
                      to={item.to}
                      className={navClassName(isActive)}
                      data-active={isActive ? "true" : "false"}
                      aria-current={isActive ? "page" : undefined}
                      onClick={onNavigate}
                    >
                      <span className="relative grid h-full w-full place-items-center">
                        <item.icon
                          size={26}
                          stroke={item.icon === IconUsersGroup ? 1.9 : 1.45}
                          className="block text-current"
                        />
                        {item.to === "/ai" && hasProviderError && (
                          <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-danger ring-2 ring-sidebar-bg" />
                        )}
                      </span>
                    </NavLink>
                  </TooltipTrigger>
                  <TooltipContent side="right" sideOffset={10}>
                    {item.label}
                  </TooltipContent>
                </Tooltip>;
              })}
            </div>
          ))}
        </nav>

        <div className="mt-auto flex flex-col items-center gap-3 pt-4">
          <ThemeToggleButton />

          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={handleLogout}
                className="relative flex h-10 w-10 items-center justify-center rounded-full border border-sidebar-border bg-brand-blue-soft text-text-default transition-transform hover:scale-105"
              >
                <span className="text-xs font-semibold uppercase tracking-wide">
                  {username.slice(0, 1) || "A"}
                </span>
                <span className="absolute right-0 top-0 h-[7px] w-[7px] rounded-full border border-sidebar-bg bg-success" />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              {username} - выйти
            </TooltipContent>
          </Tooltip>
        </div>
      </div>
    </TooltipProvider>
  );
}

export function FloatingSidebar() {
  return (
    <aside className="fixed bottom-4 left-4 top-4 z-40 hidden w-[64px] rounded-[22px] border border-sidebar-border bg-sidebar-bg px-2 py-2 shadow-[0_20px_50px_rgba(15,18,26,0.18)] backdrop-blur lg:flex">
      <SidebarNavContent />
    </aside>
  );
}

export function MobileSidebar() {
  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          className="rounded-2xl border-sidebar-border bg-bg-card text-text-strong lg:hidden"
        >
          <IconMenu2 size={18} stroke={2} />
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-[292px] border-sidebar-border bg-sidebar-bg p-4">
        <SheetHeader className="sr-only">
          <SheetTitle>Навигация</SheetTitle>
        </SheetHeader>
        <SidebarNavContent />
      </SheetContent>
    </Sheet>
  );
}
