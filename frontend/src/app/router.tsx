import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { LoginPage } from "@/pages/login/LoginPage";
import { isAuthenticated, useCurrentUser } from "@/shared/api/authApi";
import { SpinnerGap } from "@phosphor-icons/react";

const AppShell = lazy(() =>
  import("@/components/domain/app-shell").then((module) => ({ default: module.AppShell }))
);
const DashboardPage = lazy(() =>
  import("@/pages/dashboard/DashboardPage").then((module) => ({ default: module.DashboardPage }))
);
const AccountsPage = lazy(() =>
  import("@/pages/accounts/AccountsPage").then((module) => ({ default: module.AccountsPage }))
);
const GroupsPage = lazy(() =>
  import("@/pages/groups/GroupsPage").then((module) => ({ default: module.GroupsPage }))
);
const PipelinePage = lazy(() =>
  import("@/pages/pipeline/PipelinePage").then((module) => ({ default: module.PipelinePage }))
);
const GuidesPage = lazy(() =>
  import("@/pages/guides/GuidesPage").then((module) => ({ default: module.GuidesPage }))
);
const GuideDetailPage = lazy(() =>
  import("@/pages/guides/GuideDetailPage").then((module) => ({ default: module.GuideDetailPage }))
);
const ClassifiersPage = lazy(() =>
  import("@/pages/classifiers/ClassifiersPage").then((module) => ({ default: module.ClassifiersPage }))
);
const AiProvidersPage = lazy(() =>
  import("@/pages/ai-providers/AiProvidersPage").then((module) => ({ default: module.AiProvidersPage }))
);
const PromptsPage = lazy(() =>
  import("@/pages/prompts/PromptsPage").then((module) => ({ default: module.PromptsPage }))
);
const SettingsPage = lazy(() =>
  import("@/pages/settings/SettingsPage").then((module) => ({ default: module.SettingsPage }))
);

function PageLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-app">
      <div className="inline-flex items-center gap-2 rounded-xl border border-border-subtle bg-bg-card px-4 py-3 text-sm text-text-muted">
        <SpinnerGap size={18} className="animate-spin" />
        Загружаем интерфейс...
      </div>
    </div>
  );
}

function RequireAuth() {
  const currentUserQuery = useCurrentUser();

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (currentUserQuery.isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg-app">
        <div className="inline-flex items-center gap-2 rounded-xl border border-border-subtle bg-bg-card px-4 py-3 text-sm text-text-muted">
          <SpinnerGap size={18} className="animate-spin" />
          Проверяем доступ...
        </div>
      </div>
    );
  }

  if (currentUserQuery.isError || !currentUserQuery.data) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

function PublicLoginRoute() {
  return isAuthenticated() ? <Navigate to="/dashboard" replace /> : <LoginPage />;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<PublicLoginRoute />} />
        <Route element={<RequireAuth />}>
          <Route
            element={
              <Suspense fallback={<PageLoader />}>
                <AppShell />
              </Suspense>
            }
          >
            <Route path="/" element={<DashboardPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/chat-viewer" element={<Navigate to="/groups" replace />} />
            <Route path="/pipeline" element={<PipelinePage />} />
            <Route path="/guides" element={<GuidesPage />} />
            <Route path="/guides/:guideId" element={<GuideDetailPage />} />
            <Route path="/rules" element={<Navigate to="/classifiers" replace />} />
            <Route path="/sources" element={<Navigate to="/groups" replace />} />
            <Route path="/classifiers" element={<ClassifiersPage />} />
            <Route path="/ai" element={<AiProvidersPage />} />
            <Route path="/ai-providers" element={<Navigate to="/ai" replace />} />
            <Route path="/prompts" element={<PromptsPage />} />
            <Route path="/test-lab" element={<Navigate to="/ai" replace />} />
            <Route path="/monitoring" element={<Navigate to="/dashboard" replace />} />
            <Route path="/traces" element={<Navigate to="/pipeline" replace />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
