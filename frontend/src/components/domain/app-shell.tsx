import { Outlet, useLocation } from "react-router-dom";
import { CommandPalette } from "@/components/domain/command-palette";
import {
  FloatingSidebar,
  getActiveNavItem,
  MobileSidebar,
  ThemeToggleButton,
} from "@/components/domain/floating-sidebar";

export function AppShell() {
  const location = useLocation();
  const currentItem = getActiveNavItem(location.pathname);

  return (
    <div className="min-h-screen bg-bg-app">
      <FloatingSidebar />
      <CommandPalette />

      <header className="sticky top-0 z-30 border-b border-border-subtle bg-bg-app/88 px-4 py-3 backdrop-blur lg:hidden">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <MobileSidebar />
            <div className="min-w-0">
              <div className="text-[11px] font-semibold uppercase tracking-[0.22em] text-text-weak">Neuro InfoGrinder</div>
              <div className="truncate text-sm font-semibold text-text-strong">{currentItem?.label ?? "Рабочая область"}</div>
            </div>
          </div>
          <ThemeToggleButton className="shrink-0" />
        </div>
      </header>

      <main className="mx-auto min-h-screen max-w-[1600px] px-4 py-4 sm:px-6 sm:py-6 lg:ml-[120px] lg:px-8">
        <Outlet />
      </main>
    </div>
  );
}
