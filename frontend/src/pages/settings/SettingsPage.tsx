import { useState } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { UsersSettingsPanel } from "@/components/domain/users-settings-panel";
import { EmptyState } from "@/components/domain/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useSettingsQuery,
  useUpdateSettingsMutation,
} from "@/shared/api/settingsApi";
import { useTokenSummaryQuery } from "@/shared/api/monitorApi";
import type {
  AppSettings,
  ProcessingMode,
  PublicationMode,
} from "@/shared/types";
import { GearSix, SpinnerGap } from "@phosphor-icons/react";
import {
  getMessageRefreshSeconds,
  setMessageRefreshSeconds,
  getTelegramSyncSeconds,
  setTelegramSyncSeconds,
} from "@/shared/ui-settings";

function SaveButton({ pending, onClick }: { pending: boolean; onClick: () => void }) {
  return (
    <Button variant="primary" size="sm" onClick={onClick} disabled={pending}>
      {pending ? (
        <>
          <SpinnerGap size={16} weight="regular" className="mr-2 animate-spin" />
          Сохранение...
        </>
      ) : (
        "Сохранить"
      )}
    </Button>
  );
}

export function SettingsPage() {
  const settingsQuery = useSettingsQuery();
  const updateSettingsMutation = useUpdateSettingsMutation();
  const tokenSummaryQuery = useTokenSummaryQuery();

  const [localSettings, setLocalSettings] = useState<AppSettings | null>(null);
  const settings = localSettings ?? settingsQuery.data ?? null;

  // UI settings (localStorage-based)
  const [msgRefresh, setMsgRefresh] = useState(getMessageRefreshSeconds);
  const [tgSync, setTgSync] = useState(getTelegramSyncSeconds);

  const saveMsgRefresh = (value: number) => {
    const clamped = Math.max(0.1, value);
    setMsgRefresh(clamped);
    setMessageRefreshSeconds(clamped);
  };

  const saveTgSync = (value: number) => {
    const clamped = Math.max(1, value);
    setTgSync(clamped);
    setTelegramSyncSeconds(clamped);
  };

  const dailyUsage = tokenSummaryQuery.data?.totalTokensToday ?? 0;
  const dailyUsagePct =
    settings && settings.limits.dailyTokenLimit > 0
      ? Math.min(100, (dailyUsage / settings.limits.dailyTokenLimit) * 100)
      : 0;

  const handleSave = () => {
    if (settings) {
      updateSettingsMutation.mutate(settings, {
        onSuccess: (saved) => setLocalSettings(saved),
      });
    }
  };

  const updatePublication = <K extends keyof AppSettings["publication"]>(
    key: K,
    value: AppSettings["publication"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      publication: { ...(prev ?? settings).publication, [key]: value },
    }));
  };

  const updateProcessing = <K extends keyof AppSettings["processing"]>(
    key: K,
    value: AppSettings["processing"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      processing: { ...(prev ?? settings).processing, [key]: value },
    }));
  };

  const updateChainWindow = <
    K extends keyof AppSettings["processing"]["chainWindow"]
  >(
    key: K,
    value: AppSettings["processing"]["chainWindow"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      processing: {
        ...(prev ?? settings).processing,
        chainWindow: {
          ...(prev ?? settings).processing.chainWindow,
          [key]: value,
        },
      },
    }));
  };

  const updateFilters = <K extends keyof AppSettings["filters"]>(
    key: K,
    value: AppSettings["filters"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      filters: { ...(prev ?? settings).filters, [key]: value },
    }));
  };

  const updateLimits = <K extends keyof AppSettings["limits"]>(
    key: K,
    value: AppSettings["limits"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      limits: { ...(prev ?? settings).limits, [key]: value },
    }));
  };

  const updateNotifications = <K extends keyof AppSettings["notifications"]>(
    key: K,
    value: AppSettings["notifications"][K]
  ) => {
    if (!settings) return;
    setLocalSettings((prev) => ({
      ...(prev ?? settings),
      notifications: {
        ...(prev ?? settings).notifications,
        [key]: value,
      },
    }));
  };

  if (settingsQuery.isLoading) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeaderCard
          title="Настройки"
          description="Глобальная конфигурация системы NeuroInfoGrinder"
        />
        <div className="flex items-center justify-center py-20">
          <SpinnerGap size={24} weight="regular" className="animate-spin text-text-muted" />
        </div>
      </div>
    );
  }

  if (!settings) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeaderCard
          title="Настройки"
          description="Глобальная конфигурация системы NeuroInfoGrinder"
        />
        <EmptyState
          icon={GearSix}
          title="Настройки недоступны"
          description="Не удалось загрузить настройки. Проверьте подключение к серверу."
          action={
            <Button variant="outline" size="sm" onClick={() => settingsQuery.refetch()}>
              Попробовать снова
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Настройки"
        description="Глобальная конфигурация системы NeuroInfoGrinder"
        pipelineNote={`Режим публикации: ${settings.publication.mode}`}
      >
        <div className="mt-3 flex items-center gap-2">
          <span className="text-sm text-text-muted">Текущий режим:</span>
          <Badge
            variant={
              settings.publication.mode === "Automatic"
                ? "success"
                : settings.publication.mode === "With moderation"
                ? "warning"
                : "default"
            }
          >
            {settings.publication.mode === "Automatic"
              ? "Автопубликация"
              : settings.publication.mode === "With moderation"
              ? "Ручное ревью"
              : "Смешанный"}
          </Badge>
        </div>
      </PageHeaderCard>

      <Tabs defaultValue="publication">
        <TabsList>
          <TabsTrigger value="publication">Публикация</TabsTrigger>
          <TabsTrigger value="processing">Обработка</TabsTrigger>
          <TabsTrigger value="interface">Интерфейс</TabsTrigger>
          <TabsTrigger value="limits">Лимиты</TabsTrigger>
          <TabsTrigger value="notifications">Уведомления</TabsTrigger>
          <TabsTrigger value="roles">Пользователи</TabsTrigger>
          <TabsTrigger value="storage">Хранение и аудит</TabsTrigger>
        </TabsList>

        <TabsContent value="publication">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Настройки публикации
              </h3>
              <div className="grid max-w-xl grid-cols-[200px_1fr] items-center gap-x-4 gap-y-4">
                <span className="text-sm text-text-muted">Target группа</span>
                <Input
                  value={settings.publication.targetGroupTitle ?? "Не настроено"}
                  readOnly
                />

                <span className="text-sm text-text-muted">Режим публикации</span>
                <Select
                  value={settings.publication.mode}
                  onValueChange={(value) =>
                    updatePublication("mode", value as PublicationMode)
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Automatic">Автопубликация</SelectItem>
                    <SelectItem value="With moderation">Ручное ревью</SelectItem>
                    <SelectItem value="Mixed">Смешанный</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Separator className="my-4" />
              <SaveButton
                pending={updateSettingsMutation.isPending}
                onClick={handleSave}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="processing">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Настройки обработки
              </h3>
              <div className="grid max-w-xl grid-cols-[200px_1fr] items-center gap-x-4 gap-y-4">
                <span className="text-sm text-text-muted">Режим обработки</span>
                <Select
                  value={settings.processing.mode}
                  onValueChange={(value) =>
                    updateProcessing("mode", value as ProcessingMode)
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="New only">Только новые</SelectItem>
                    <SelectItem value="Full backfill">Полный backfill</SelectItem>
                  </SelectContent>
                </Select>

                <span className="text-sm text-text-muted">Интервал опроса (сек)</span>
                <Input
                  type="number"
                  value={settings.processing.pollIntervalSeconds}
                  onChange={(event) =>
                    updateProcessing("pollIntervalSeconds", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Time window (мин)</span>
                <Input
                  type="number"
                  value={settings.processing.chainWindow.timeWindowMinutes}
                  onChange={(event) =>
                    updateChainWindow("timeWindowMinutes", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Мин. сообщений</span>
                <Input
                  type="number"
                  value={settings.processing.chainWindow.minMessagesForProcessing}
                  onChange={(event) =>
                    updateChainWindow(
                      "minMessagesForProcessing",
                      Number(event.target.value)
                    )
                  }
                />

                <span className="text-sm text-text-muted">Макс. сообщений в цепочке</span>
                <Input
                  type="number"
                  value={settings.processing.chainWindow.maxMessagesPerChain}
                  onChange={(event) =>
                    updateChainWindow("maxMessagesPerChain", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Включать replies</span>
                <Switch
                  checked={settings.processing.chainWindow.includeReplies}
                  onCheckedChange={(value) =>
                    updateChainWindow("includeReplies", value)
                  }
                />

                <span className="text-sm text-text-muted">Пропускать ботов</span>
                <Switch
                  checked={settings.filters.skipBots}
                  onCheckedChange={(value) => updateFilters("skipBots", value)}
                />

                <span className="text-sm text-text-muted">Мин. длина сообщения</span>
                <Input
                  type="number"
                  value={settings.filters.minMessageLength}
                  onChange={(event) =>
                    updateFilters("minMessageLength", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Blacklist слова</span>
                <Input
                  value={settings.filters.blacklistWords.join(", ")}
                  onChange={(event) =>
                    updateFilters(
                      "blacklistWords",
                      event.target.value
                        .split(",")
                        .map((word) => word.trim())
                        .filter(Boolean)
                    )
                  }
                />
              </div>
              <Separator className="my-4" />
              <SaveButton
                pending={updateSettingsMutation.isPending}
                onClick={handleSave}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="interface">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Настройки интерфейса
              </h3>
              <p className="mb-4 text-sm text-text-muted">
                Управление частотой обновления данных в реальном времени. Изменения применяются мгновенно.
              </p>
              <div className="grid max-w-xl grid-cols-[220px_1fr] items-center gap-x-4 gap-y-4">
                <div>
                  <span className="text-sm text-text-strong">Обновление сообщений</span>
                  <p className="text-xs text-text-muted mt-0.5">
                    Как часто список сообщений обновляется из базы данных
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    step={0.1}
                    min={0.1}
                    value={msgRefresh}
                    onChange={(event) => saveMsgRefresh(Number(event.target.value))}
                    className="h-8 w-24 text-right font-mono-value text-sm"
                  />
                  <span className="text-sm text-text-muted">сек</span>
                  <Badge
                    variant={msgRefresh <= 1 ? "warning" : msgRefresh <= 3 ? "default" : "success"}
                    className="text-xs"
                  >
                    {msgRefresh <= 1 ? "Очень часто" : msgRefresh <= 3 ? "Часто" : msgRefresh <= 10 ? "Умеренно" : "Редко"}
                  </Badge>
                </div>

                <div>
                  <span className="text-sm text-text-strong">Синхронизация с Telegram</span>
                  <p className="text-xs text-text-muted mt-0.5">
                    Как часто новые сообщения подтягиваются из Telegram
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    step={1}
                    min={1}
                    value={tgSync}
                    onChange={(event) => saveTgSync(Number(event.target.value))}
                    className="h-8 w-24 text-right font-mono-value text-sm"
                  />
                  <span className="text-sm text-text-muted">сек</span>
                  <Badge
                    variant={tgSync <= 5 ? "warning" : tgSync <= 30 ? "default" : "success"}
                    className="text-xs"
                  >
                    {tgSync <= 5 ? "Очень часто" : tgSync <= 30 ? "Стандарт" : "Редко"}
                  </Badge>
                </div>
              </div>
              <Separator className="my-4" />
              <p className="text-xs text-text-weak italic">
                Эти настройки хранятся в браузере и не требуют сохранения на сервере. Применяются ко всем открытым вкладкам.
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="limits">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Лимиты токенов
              </h3>
              <div className="grid max-w-xl grid-cols-[200px_1fr] items-center gap-x-4 gap-y-4">
                <span className="text-sm text-text-muted">Daily token limit</span>
                <Input
                  type="number"
                  value={settings.limits.dailyTokenLimit}
                  onChange={(event) =>
                    updateLimits("dailyTokenLimit", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Monthly token limit</span>
                <Input
                  type="number"
                  value={settings.limits.monthlyTokenLimit}
                  onChange={(event) =>
                    updateLimits("monthlyTokenLimit", Number(event.target.value))
                  }
                />

                <span className="text-sm text-text-muted">Alert threshold %</span>
                <Input
                  type="number"
                  value={settings.limits.alertThresholdPercent}
                  onChange={(event) =>
                    updateLimits("alertThresholdPercent", Number(event.target.value))
                  }
                />
              </div>

              <Separator className="my-4" />

              <div className="mb-4">
                <div className="mb-1 flex justify-between text-sm">
                  <span className="text-text-muted">Daily usage</span>
                  <span className="font-mono-value">
                    {(dailyUsage / 1_000_000).toFixed(2)}M /{" "}
                    {(settings.limits.dailyTokenLimit / 1_000_000).toFixed(0)}M
                  </span>
                </div>
                <Progress value={dailyUsagePct} className="h-3" />
                <div className="mt-1 flex justify-between text-xs text-text-weak">
                  <span>{dailyUsagePct.toFixed(0)}% использовано</span>
                  <span>Alert at {settings.limits.alertThresholdPercent}%</span>
                </div>
              </div>

              <SaveButton
                pending={updateSettingsMutation.isPending}
                onClick={handleSave}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="notifications">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Уведомления
              </h3>
              <div className="grid max-w-xl grid-cols-[200px_1fr] items-center gap-x-4 gap-y-4">
                <span className="text-sm text-text-muted">Telegram chat</span>
                <Input
                  value={settings.notifications.telegramChat ?? ""}
                  onChange={(event) =>
                    updateNotifications("telegramChat", event.target.value)
                  }
                />

                <span className="text-sm text-text-muted">Webhook URL</span>
                <Input
                  value={settings.notifications.webhookUrl ?? ""}
                  onChange={(event) =>
                    updateNotifications("webhookUrl", event.target.value)
                  }
                />
              </div>
              <Separator className="my-4" />
              <SaveButton
                pending={updateSettingsMutation.isPending}
                onClick={handleSave}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="roles">
          <UsersSettingsPanel />
        </TabsContent>

        <TabsContent value="storage">
          <Card>
            <CardContent className="p-5">
              <h3 className="mb-4 text-sm font-semibold text-text-strong">
                Хранение и аудит
              </h3>
              <div className="grid max-w-xl grid-cols-[200px_1fr] items-center gap-x-4 gap-y-4">
                <span className="text-sm text-text-muted">Retention messages</span>
                <Input value="30 дней" readOnly />

                <span className="text-sm text-text-muted">Retention guides</span>
                <Input value="Бессрочно" readOnly />

                <span className="text-sm text-text-muted">Export logs</span>
                <Button variant="outline" size="sm">
                  Скачать CSV
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
