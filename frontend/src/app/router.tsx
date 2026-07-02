import { lazy, Suspense } from "react";
import { BrowserRouter, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { SpinnerGap } from "@phosphor-icons/react";
import { LoginPage } from "@/pages/login/LoginPage";
import { isAuthenticated, useCurrentUser } from "@/shared/api/authApi";

const AppShell = lazy(() =>
  import("@/components/domain/app-shell").then((module) => ({ default: module.AppShell }))
);
const DashboardPage = lazy(() =>
  import("@/pages/dashboard/DashboardPage").then((module) => ({ default: module.DashboardPage }))
);
const AccountsPage = lazy(() =>
  import("@/pages/accounts/AccountsPage").then((module) => ({ default: module.AccountsPage }))
);
const OperatorPage = lazy(() =>
  import("@/pages/operator/OperatorPage").then((module) => ({ default: module.OperatorPage }))
);
const GroupsPage = lazy(() =>
  import("@/pages/groups/GroupsPage").then((module) => ({ default: module.GroupsPage }))
);
const MaterialsPage = lazy(() =>
  import("@/pages/materials/MaterialsPage").then((module) => ({ default: module.MaterialsPage }))
);
const TopicSignalsPage = lazy(() =>
  import("@/pages/materials/TopicSignalsPage").then((module) => ({ default: module.TopicSignalsPage }))
)

const SignalDetailPage = lazy(() =>
  import("@/pages/materials/SignalDetailPage").then((module) => ({ default: module.SignalDetailPage }))
);
const GuideDetailPage = lazy(() =>
  import("@/pages/guides/GuideDetailPage").then((module) => ({ default: module.GuideDetailPage }))
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
const MessagesPage = lazy(() =>
  import("@/pages/messages/MessagesPage").then((module) => ({ default: module.MessagesPage }))
);
const FrontsPage = lazy(() =>
  import("@/pages/fronts/FrontsPage").then((module) => ({ default: module.FrontsPage }))
);
const PipelinePage = lazy(() =>
  import("@/pages/pipeline/PipelinePage").then((module) => ({ default: module.PipelinePage }))
);
const PipelineStageEmbeddingsMockupPage = lazy(() =>
  import("@/pages/pipeline/PipelineStageEmbeddingsMockupPage").then((module) => ({ default: module.PipelineStageEmbeddingsMockupPage }))
);
const PipelineMessagesMockupPage = lazy(() =>
  import("@/pages/pipeline/PipelineMessagesMockupPage").then((module) => ({ default: module.PipelineMessagesMockupPage }))
);
const PipelineMessageDetailMockupPage = lazy(() =>
  import("@/pages/pipeline/PipelineMessageDetailMockupPage").then((module) => ({ default: module.PipelineMessageDetailMockupPage }))
);
const PipelineClusterDetailMockupPage = lazy(() =>
  import("@/pages/pipeline/PipelineClusterDetailMockupPage").then((module) => ({ default: module.PipelineClusterDetailMockupPage }))
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
            <Route path="/operator" element={<OperatorPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/messages" element={<MessagesPage />} />
            <Route path="/chat-viewer" element={<Navigate to="/groups" replace />} />
            <Route path="/pipeline/stages/embeddings-bge-m3" element={<Navigate to="/pipeline/stages/embeddings" replace />} />
            <Route path="/pipeline/stages/:stageId" element={<PipelineStageEmbeddingsMockupPage />} />
            <Route path="/pipeline/messages/4743" element={<PipelineMessageDetailMockupPage />} />
            <Route path="/pipeline/clusters/1287" element={<PipelineClusterDetailMockupPage />} />
            <Route path="/pipeline/messages-mockup" element={<Navigate to="/pipeline" replace />} />
            <Route path="/pipeline/clusters-map-mockup" element={<Navigate to="/pipeline" replace />} />
            <Route path="/pipeline" element={<PipelinePage />} />
            <Route path="/materials" element={<MaterialsPage />} />
            <Route path="/materials/topics/:slug" element={<TopicSignalsPage />} />
            <Route path="/materials/signals/:id" element={<SignalDetailPage />} />
            <Route path="/materials/:guideId" element={<GuideDetailPage />} />
            <Route path="/guides" element={<Navigate to="/materials?contentType=GUIDE" replace />} />
            <Route path="/guides/:guideId" element={<GuideDetailPage />} />
            <Route path="/rules" element={<Navigate to="/dashboard" replace />} />
            <Route path="/sources" element={<Navigate to="/groups" replace />} />
            <Route path="/classifiers" element={<Navigate to="/dashboard" replace />} />
            <Route path="/ai" element={<AiProvidersPage />} />
            <Route path="/providers" element={<Navigate to="/ai" replace />} />
            <Route path="/fronts" element={<FrontsPage />} />
            <Route path="/ai-providers" element={<Navigate to="/ai" replace />} />
            <Route path="/ai-providers/providers" element={<Navigate to="/ai" replace />} />
            <Route path="/prompts" element={<PromptsPage />} />
            <Route path="/test-lab" element={<Navigate to="/ai" replace />} />
            <Route path="/monitoring" element={<Navigate to="/dashboard" replace />} />
            <Route path="/traces" element={<Navigate to="/dashboard" replace />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
