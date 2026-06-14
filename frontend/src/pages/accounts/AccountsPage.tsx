import { useMemo, useState } from "react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert } from "@/components/ui/alert";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  useAccountsQuery,
  useCreateAccountMutation,
  useSubmitAccountCodeMutation,
  useSubmitAccountPasswordMutation,
} from "@/shared/api/accountsApi";
import { displayAccountStatus } from "@/shared/types";
import {
  Plus,
  DotsThree,
  MagnifyingGlass,
  CellSignalFull,
  CellSignalSlash,
  GlobeHemisphereWest,
  Pause,
  Trash,
  WarningCircle,
  CheckCircle,
  SpinnerGap,
} from "@phosphor-icons/react";

const FILTERS = [
  "Все",
  "CONNECTED",
  "WAITING_CODE",
  "WAITING_PASSWORD",
  "ERROR",
  "DISABLED",
] as const;

const FILTER_LABELS: Record<string, string> = {
  Все: "Все",
  CONNECTED: "Online",
  WAITING_CODE: "Waiting code",
  WAITING_PASSWORD: "Waiting password",
  ERROR: "Error",
  DISABLED: "Disabled",
};

export function AccountsPage() {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<string>("Все");
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [phoneInput, setPhoneInput] = useState("");
  const [codeInput, setCodeInput] = useState("");
  const [passwordInput, setPasswordInput] = useState("");
  const [dialogError, setDialogError] = useState("");
  const [createdAccountId, setCreatedAccountId] = useState<string | null>(null);
  const [authStage, setAuthStage] = useState<
    "phone" | "code" | "password" | "done"
  >("phone");

  const accountsQuery = useAccountsQuery();
  const createAccountMutation = useCreateAccountMutation();
  const submitCodeMutation = useSubmitAccountCodeMutation();
  const submitPasswordMutation = useSubmitAccountPasswordMutation();

  const accounts = accountsQuery.data ?? [];

  const filtered = accounts.filter((account) => {
    const matchesSearch =
      (account.username ?? "").toLowerCase().includes(search.toLowerCase()) ||
      account.phone.includes(search);
    const matchesFilter =
      filter === "Все" || account.status === filter;
    return matchesSearch && matchesFilter;
  });

  const isSubmitting =
    createAccountMutation.isPending ||
    submitCodeMutation.isPending ||
    submitPasswordMutation.isPending;

  const stepIndex = useMemo(() => {
    switch (authStage) {
      case "phone":
        return 0;
      case "code":
        return 1;
      case "password":
        return 2;
      default:
        return 3;
    }
  }, [authStage]);

  const stepMeta = {
    phone: {
      title: "Номер телефона",
      description:
        "Отправляем запрос в Telegram, чтобы получить код подтверждения.",
      buttonLabel: "Отправить код",
    },
    code: {
      title: "Код Telegram",
      description:
        "Введите код, который Telegram прислал на указанный номер.",
      buttonLabel: "Подтвердить код",
    },
    password: {
      title: "Пароль 2FA",
      description:
        "Если на аккаунте включён пароль, введите его для завершения входа.",
      buttonLabel: "Подтвердить пароль",
    },
    done: {
      title: "Готово",
      description:
        "Авторизация завершена, аккаунт добавлен в систему.",
      buttonLabel: "Закрыть",
    },
  } as const;

  const resetDialog = () => {
    setAddDialogOpen(false);
    setPhoneInput("");
    setCodeInput("");
    setPasswordInput("");
    setDialogError("");
    setCreatedAccountId(null);
    setAuthStage("phone");
  };

  const handleCreateAccount = () => {
    setDialogError("");
    createAccountMutation.mutate(
      { phone: phoneInput.trim() },
      {
        onSuccess: (account) => {
          setCreatedAccountId(account.id);
          if (account.status === "WAITING_PASSWORD") {
            setAuthStage("password");
            return;
          }
          if (account.status === "CONNECTED") {
            setAuthStage("done");
            accountsQuery.refetch();
            return;
          }
          setAuthStage("code");
          accountsQuery.refetch();
        },
        onError: (error) => {
          setDialogError(
            error instanceof Error
              ? error.message
              : "Не удалось отправить номер телефона."
          );
        },
      }
    );
  };

  const handleSubmitCode = () => {
    if (!createdAccountId) {
      setDialogError("Сначала нужно отправить номер телефона.");
      return;
    }

    setDialogError("");
    submitCodeMutation.mutate(
      { id: createdAccountId, code: codeInput.trim() },
      {
        onSuccess: (account) => {
          if (account.status === "WAITING_PASSWORD") {
            setAuthStage("password");
            return;
          }
          if (account.status === "CONNECTED") {
            setAuthStage("done");
            accountsQuery.refetch();
            return;
          }
          setDialogError(
            "Telegram не перевёл сессию в ожидаемое состояние после кода."
          );
        },
        onError: (error) => {
          setDialogError(
            error instanceof Error
              ? error.message
              : "Не удалось подтвердить код."
          );
        },
      }
    );
  };

  const handleSubmitPassword = () => {
    if (!createdAccountId) {
      setDialogError("Сначала нужно отправить номер телефона.");
      return;
    }

    setDialogError("");
    submitPasswordMutation.mutate(
      { id: createdAccountId, password: passwordInput },
      {
        onSuccess: (account) => {
          if (account.status === "CONNECTED") {
            setAuthStage("done");
            accountsQuery.refetch();
            return;
          }
          setDialogError(
            "Telegram не подтвердил вход после пароля."
          );
        },
        onError: (error) => {
          setDialogError(
            error instanceof Error
              ? error.message
              : "Не удалось подтвердить пароль."
          );
        },
      }
    );
  };

  const handlePrimaryAction = () => {
    if (authStage === "phone") {
      handleCreateAccount();
      return;
    }
    if (authStage === "code") {
      handleSubmitCode();
      return;
    }
    if (authStage === "password") {
      handleSubmitPassword();
      return;
    }
    resetDialog();
  };

  const currentStep = stepMeta[authStage];

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Telegram-аккаунты"
        description="Управление reader-аккаунтами и авторизацией"
        actions={{
          primary: {
            label: "Добавить аккаунт",
            onClick: () => {
              setAddDialogOpen(true);
              setDialogError("");
              setAuthStage("phone");
            },
          },
          onFilter: () => {},
          onRefresh: () => {
            accountsQuery.refetch();
          },
        }}
      />

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative max-w-xs flex-1">
          <MagnifyingGlass
            size={16}
            weight="regular"
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-weak"
          />
          <Input
            placeholder="Поиск по username или телефону"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex gap-1.5">
          {FILTERS.map((item) => (
            <Button
              key={item}
              variant={filter === item ? "secondary" : "ghost"}
              size="sm"
              onClick={() => setFilter(item)}
            >
              {FILTER_LABELS[item] ?? item}
            </Button>
          ))}
        </div>
        {accountsQuery.isLoading && !accountsQuery.data && (
          <SpinnerGap
            size={16}
            weight="regular"
            className="animate-spin text-text-muted"
          />
        )}
      </div>

      {accountsQuery.isError ? (
        <Alert variant="danger">
          {(accountsQuery.error as Error | null)?.message ?? "Failed to load accounts"}
        </Alert>
      ) : accounts.length === 0 && !accountsQuery.isLoading ? (
        <EmptyState
          icon={CellSignalFull}
          title="Нет аккаунтов"
          description="Добавьте Telegram-аккаунт для начала работы"
          action={
            <Button
              variant="primary"
              size="sm"
              onClick={() => {
                setAddDialogOpen(true);
                setDialogError("");
                setAuthStage("phone");
              }}
            >
              <Plus size={16} weight="regular" className="mr-1.5" /> Добавить аккаунт

            </Button>
          }
        />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Username</TableHead>
                <TableHead>Телефон</TableHead>
                <TableHead>Статус</TableHead>
                <TableHead>Язык</TableHead>
                <TableHead>Часовой пояс</TableHead>
                <TableHead>Создан</TableHead>
                <TableHead className="w-10" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((account) => (
                <TableRow key={account.id}>
                  <TableCell className="font-medium text-text-strong">
                    {account.username ?? "\u2014"}
                  </TableCell>
                  <TableCell className="font-mono-value text-text-muted">
                    {account.phone}
                  </TableCell>
                  <TableCell>
                    <StatusBadge
                      status={displayAccountStatus(account.status)}
                    />
                  </TableCell>
                  <TableCell className="text-sm">
                    {account.languageCode ?? "\u2014"}
                  </TableCell>
                  <TableCell className="text-sm">
                    {account.timezone ?? "\u2014"}
                  </TableCell>
                  <TableCell className="text-text-muted text-sm">
                    {account.createdAt ?? "\u2014"}
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                        >
                          <DotsThree size={16} weight="regular" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem>
                          <CellSignalFull
                            size={16}
                            weight="regular"
                            className="mr-2"
                          />
                          Проверить соединение
                        </DropdownMenuItem>
                        <DropdownMenuItem>
                          <GlobeHemisphereWest
                            size={16}
                            weight="regular"
                            className="mr-2"
                          />
                          Сменить прокси
                        </DropdownMenuItem>
                        <DropdownMenuItem>
                          <Pause
                            size={16}
                            weight="regular"
                            className="mr-2"
                          />
                          Поставить на паузу
                        </DropdownMenuItem>
                        <DropdownMenuItem className="text-danger focus:text-danger">
                          <Trash
                            size={16}
                            weight="regular"
                            className="mr-2"
                          />
                          Удалить
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog
        open={addDialogOpen}
        onOpenChange={(open) => (open ? setAddDialogOpen(true) : resetDialog())}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Добавить Telegram-аккаунт</DialogTitle>
            <DialogDescription>{currentStep.description}</DialogDescription>
          </DialogHeader>

          <div className="mb-2 flex items-center gap-1">
            {[0, 1, 2, 3].map((index) => (
              <div
                key={index}
                className={`h-1.5 flex-1 rounded-full transition-colors ${
                  index <= stepIndex
                    ? "bg-brand-blue"
                    : "bg-border-subtle"
                }`}
              />
            ))}
          </div>

          <Badge variant="outline" className="w-fit">
            {currentStep.title}
          </Badge>

          <div className="py-4">
            {authStage === "phone" && (
              <Input
                placeholder="+7xxxxxxxxxx"
                type="tel"
                autoFocus
                value={phoneInput}
                onChange={(e) => setPhoneInput(e.target.value)}
              />
            )}

            {authStage === "code" && (
              <Input
                placeholder="Код из Telegram"
                type="text"
                autoFocus
                value={codeInput}
                onChange={(e) => setCodeInput(e.target.value)}
              />
            )}

            {authStage === "password" && (
              <Input
                placeholder="Пароль 2FA"
                type="password"
                autoFocus
                value={passwordInput}
                onChange={(e) => setPasswordInput(e.target.value)}
              />
            )}

            {authStage === "done" && (
              <div className="flex flex-col gap-2 text-sm">
                <div className="flex items-center gap-2 text-success">
                  <CheckCircle size={16} weight="regular" /> Авторизация
                  завершена
                </div>
                <div className="flex items-center gap-2 text-success">
                  <CheckCircle size={16} weight="regular" /> Аккаунт появился в
                  системе
                </div>
                <div className="flex items-center gap-2 text-text-muted">
                  <CellSignalSlash size={16} weight="regular" /> Дальше можно
                  настраивать прокси и группы
                </div>
              </div>
            )}

            {dialogError && (
              <Alert variant="danger" className="mt-4">
                {dialogError}
              </Alert>
            )}
          </div>

          <DialogFooter>
            {authStage !== "phone" && authStage !== "done" && (
              <Button
                variant="outline"
                onClick={() => {
                  setDialogError("");
                  setAuthStage(
                    authStage === "password" ? "code" : "phone"
                  );
                }}
              >
                Назад
              </Button>
            )}
            <Button
              variant={authStage === "done" ? "primary" : "default"}
              onClick={handlePrimaryAction}
              disabled={
                isSubmitting ||
                (authStage === "phone" && !phoneInput.trim()) ||
                (authStage === "code" && !codeInput.trim()) ||
                (authStage === "password" && !passwordInput.trim())
              }
            >
              {isSubmitting ? (
                <>
                  <SpinnerGap
                    size={16}
                    weight="regular"
                    className="mr-2 animate-spin"
                  />{" "}
                  Отправка...
                </>
              ) : (
                currentStep.buttonLabel
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

