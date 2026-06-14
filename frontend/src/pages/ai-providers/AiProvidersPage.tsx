import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  CheckCircle,
  ClockCounterClockwise,
  Cpu,
  FloppyDisk,
  SpinnerGap,
  TestTube,
  Trash,
  WarningCircle,
  XCircle,
} from "@phosphor-icons/react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  useCreateProviderMutation,
  useDeleteProviderMutation,
  useProvidersQuery,
  useTestProviderMutation,
  useUpdateProviderMutation,
  type TestProviderResult,
} from "@/shared/api/providersApi";
import { displayProviderStatus, type AIProvider } from "@/shared/types";

type ProviderTestState = {
  providerId: string;
  providerName: string;
  result: TestProviderResult;
  testedAt: string;
};

function formatLatency(latencyMs: number | null) {
  if (latencyMs == null) return "—";
  return latencyMs < 1000 ? `${latencyMs} мс` : `${(latencyMs / 1000).toFixed(1)} с`;
}

function ProviderCard({
  provider,
  isTesting,
  testState,
  onTest,
  onDelete,
}: {
  provider: AIProvider;
  isTesting: boolean;
  testState: ProviderTestState | null;
  onTest: (provider: AIProvider) => void;
  onDelete: (provider: AIProvider) => void;
}) {
  const statusVariant = provider.status === "ERROR" ? "danger" : "outline";
  const resultVariant =
    provider.lastTestResult === "OK" || provider.lastTestResult === "PIPELINE_OK"
      ? "success"
      : "warning";

  return (
    <Card className={provider.lastError ? "border-danger/30 bg-danger-soft/30" : undefined}>
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <div className="text-sm font-semibold text-text-strong">{provider.name}</div>
              <Badge variant="outline">{provider.protocol}</Badge>
            </div>
            <div className="mt-1 text-xs text-text-muted">{provider.endpointUrl}</div>
            <div className="mt-2 flex flex-wrap gap-2">
              <Badge variant="outline">{provider.model ?? "model не указан"}</Badge>
              <Badge variant={provider.hasApiKey ? "success" : "warning"}>
                {provider.hasApiKey ? "API key задан" : "API key отсутствует"}
              </Badge>
              {provider.lastError && <Badge variant="danger">API problem</Badge>}
            </div>
          </div>
          <Badge variant={statusVariant}>{displayProviderStatus(provider.status)}</Badge>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            className="gap-1.5"
            onClick={() => onTest(provider)}
            disabled={isTesting}
          >
            {isTesting ? <SpinnerGap size={14} className="animate-spin" /> : <TestTube size={14} />}
            {isTesting ? "Проверяю..." : "Проверить"}
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="gap-1.5 text-danger hover:text-danger"
            onClick={() => onDelete(provider)}
            disabled={isTesting}
          >
            <Trash size={14} />
            Удалить
          </Button>
        </div>

        {testState && (
          <Alert variant={testState.result.success ? "success" : "danger"} className="mt-4">
            {testState.result.success ? (
              <CheckCircle size={18} weight="fill" />
            ) : (
              <XCircle size={18} weight="fill" />
            )}
            <AlertTitle>
              {testState.result.success ? "Проверка прошла успешно" : "Проверка не прошла"}
            </AlertTitle>
            <AlertDescription>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                <span>Задержка: {formatLatency(testState.result.latencyMs)}</span>
                <span>Время: {new Date(testState.testedAt).toLocaleTimeString("ru-RU")}</span>
              </div>
              {testState.result.error && (
                <div className="mt-2 whitespace-pre-wrap break-words">{testState.result.error}</div>
              )}
            </AlertDescription>
          </Alert>
        )}

        {!testState && provider.lastTestedAt && provider.lastTestResult && (
          <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-text-muted">
            <ClockCounterClockwise size={14} />
            <span>
              Последняя проверка: {new Date(provider.lastTestedAt).toLocaleString("ru-RU")}
            </span>
            <Badge variant={resultVariant}>{provider.lastTestResult}</Badge>
          </div>
        )}

        {provider.lastError && (
          <Alert variant="danger" className="mt-4">
            <WarningCircle size={18} weight="fill" />
            <AlertTitle>Последняя ошибка API</AlertTitle>
            <AlertDescription>
              <div className="whitespace-pre-wrap break-words">{provider.lastError}</div>
            </AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

export function AiProvidersPage() {
  const providersQuery = useProvidersQuery();
  const createProvider = useCreateProviderMutation();
  const updateProvider = useUpdateProviderMutation();
  const testProvider = useTestProviderMutation();
  const deleteProvider = useDeleteProviderMutation();

  const providers = providersQuery.data ?? [];
  const [name, setName] = useState("");
  const [protocol, setProtocol] = useState("OPENAI_COMPATIBLE");
  const [endpointUrl, setEndpointUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [testState, setTestState] = useState<ProviderTestState | null>(null);

  const activeProvider = useMemo(
    () => providers.find((provider) => provider.status !== "DISABLED" && provider.status !== "ERROR"),
    [providers],
  );

  const saveProvider = () => {
    const existing = providers.find((provider) => provider.name === name);
    if (existing) {
      updateProvider.mutate({
        id: existing.id,
        protocol,
        endpointUrl,
        apiKey: apiKey || undefined,
        model: model || undefined,
      });
      return;
    }

    createProvider.mutate({
      name,
      protocol,
      endpointUrl,
      apiKey: apiKey || undefined,
      model: model || undefined,
    });
  };

  const runProviderTest = (provider: AIProvider) => {
    setTestState(null);
    testProvider.mutate(provider.id, {
      onSuccess: (result) => {
        setTestState({
          providerId: provider.id,
          providerName: provider.name,
          result,
          testedAt: new Date().toISOString(),
        });
      },
      onError: (error) => {
        setTestState({
          providerId: provider.id,
          providerName: provider.name,
          result: {
            success: false,
            latencyMs: null,
            error: error instanceof Error ? error.message : "Unknown error",
          },
          testedAt: new Date().toISOString(),
        });
      },
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="AI провайдеры"
        description="Здесь находятся реальные подключенные провайдеры и управление ими. Промпты вынесены на отдельную страницу."
        actions={{ onRefresh: () => providersQuery.refetch() }}
      >
        <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-border-subtle pt-4 text-sm text-text-muted">
          <span>Промпты и тестирование шаблонов снова доступны отдельно.</span>
          <Link to="/prompts" className="font-medium text-brand-blue hover:underline">
            Открыть промпты
          </Link>
        </div>
      </PageHeaderCard>

      {testState && (
        <Alert variant={testState.result.success ? "success" : "danger"}>
          {testState.result.success ? (
            <CheckCircle size={18} weight="fill" />
          ) : (
            <WarningCircle size={18} weight="fill" />
          )}
          <AlertTitle>
            {testState.providerName}:{" "}
            {testState.result.success ? "подключение работает" : "проверка не прошла"}
          </AlertTitle>
          <AlertDescription>
            <div>Задержка: {formatLatency(testState.result.latencyMs)}</div>
            {testState.result.error && (
              <div className="mt-1 whitespace-pre-wrap">{testState.result.error}</div>
            )}
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-text-strong">
            <FloppyDisk size={16} />
            Добавить или обновить провайдера
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <Input placeholder="Имя провайдера" value={name} onChange={(event) => setName(event.target.value)} />
            <Input placeholder="Протокол" value={protocol} onChange={(event) => setProtocol(event.target.value)} />
            <Input placeholder="Endpoint URL" value={endpointUrl} onChange={(event) => setEndpointUrl(event.target.value)} />
            <Input placeholder="Model" value={model} onChange={(event) => setModel(event.target.value)} />
            <div className="md:col-span-2">
              <Input placeholder="API key" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
            </div>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <Button
              variant="primary"
              className="gap-1.5"
              onClick={saveProvider}
              disabled={!name || !endpointUrl || createProvider.isPending || updateProvider.isPending}
            >
              <Cpu size={16} />
              Сохранить
            </Button>
            {activeProvider && (
              <div className="text-xs text-text-muted">
                Активный провайдер:{" "}
                <span className="font-medium text-text-strong">{activeProvider.name}</span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-2">
        {providers.map((provider) => (
          <ProviderCard
            key={provider.id}
            provider={provider}
            isTesting={testProvider.isPending && testProvider.variables === provider.id}
            testState={testState?.providerId === provider.id ? testState : null}
            onTest={runProviderTest}
            onDelete={(currentProvider) => deleteProvider.mutate(currentProvider.id)}
          />
        ))}
      </div>
    </div>
  );
}
