import { useMemo, useState } from "react";
import { CellSignalFull, CheckCircle, DotsThree, Pause, Play, Plus, Prohibit, SpinnerGap, Trash } from "@phosphor-icons/react";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { EmptyState } from "@/components/domain/empty-state";
import { StatusBadge } from "@/components/domain/status-badge";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  useAccountsQuery,
  useCreateAccountMutation,
  useDeleteAccountMutation,
  useDisableAccountMutation,
  useEnableAccountMutation,
  usePauseAccountMutation,
  useResumeAccountMutation,
  useSubmitAccountCodeMutation,
  useSubmitAccountPasswordMutation,
} from "@/shared/api/accountsApi";
import { displayAccountStatus } from "@/shared/types";

const FILTERS = ["Все", "CONNECTED", "WAITING_CODE", "WAITING_PASSWORD", "DISCONNECTED", "PAUSED", "ERROR", "DISABLED"] as const;
const FILTER_LABELS: Record<string, string> = {
  Все: "Все",
  CONNECTED: "Подключено",
  WAITING_CODE: "Ожидает код",
  WAITING_PASSWORD: "Ожидает пароль",
  DISCONNECTED: "Отключено",
  PAUSED: "Пауза",
  ERROR: "Ошибка",
  DISABLED: "Выключено",
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
  const [authStage, setAuthStage] = useState<"phone" | "code" | "password" | "done">("phone");

  const accountsQuery = useAccountsQuery();
  const createAccountMutation = useCreateAccountMutation();
  const submitCodeMutation = useSubmitAccountCodeMutation();
  const submitPasswordMutation = useSubmitAccountPasswordMutation();
  const deleteAccountMutation = useDeleteAccountMutation();
  const pauseAccountMutation = usePauseAccountMutation();
  const resumeAccountMutation = useResumeAccountMutation();
  const disableAccountMutation = useDisableAccountMutation();
  const enableAccountMutation = useEnableAccountMutation();

  const accounts = accountsQuery.data ?? [];
  const filtered = accounts.filter((account) => {
    const needle = search.toLowerCase();
    const matchesSearch = (account.username ?? "").toLowerCase().includes(needle) || account.phone.includes(search) || account.id.includes(search);
    const matchesFilter = filter === "Все" || account.status === filter;
    return matchesSearch && matchesFilter;
  });

  const currentStep = useMemo(() => {
    const steps = {
      phone: { title: "Номер телефона", description: "Отправляем запрос в Telegram, чтобы получить код подтверждения.", buttonLabel: "Отправить телефон" },
      code: { title: "Код Telegram", description: "Введите код, который Telegram прислал на указанный номер.", buttonLabel: "Подтвердить код" },
      password: { title: "Пароль 2FA", description: "Если включён пароль, введите его для завершения входа.", buttonLabel: "Подтвердить пароль" },
      done: { title: "Готово", description: "Авторизация завершена, аккаунт добавлен в систему.", buttonLabel: "Закрыть" },
    } as const;
    return steps[authStage];
  }, [authStage]);

  const isSubmitting = createAccountMutation.isPending || submitCodeMutation.isPending || submitPasswordMutation.isPending;

  const resetDialog = () => {
    setAddDialogOpen(false);
    setPhoneInput("");
    setCodeInput("");
    setPasswordInput("");
    setDialogError("");
    setCreatedAccountId(null);
    setAuthStage("phone");
  };

  const handlePrimaryAction = () => {
    setDialogError("");
    if (authStage === "done") {
      resetDialog();
      return;
    }
    if (authStage === "phone") {
      createAccountMutation.mutate({ phone: phoneInput.trim() }, {
        onSuccess: (account) => {
          setCreatedAccountId(account.id);
          setAuthStage(account.status === "CONNECTED" ? "done" : account.status === "WAITING_PASSWORD" ? "password" : "code");
          accountsQuery.refetch();
        },
        onError: (error) => setDialogError(error instanceof Error ? error.message : "Не удалось отправить телефон"),
      });
      return;
    }
    if (!createdAccountId) {
      setDialogError("Сначала нужно отправить телефон.");
      return;
    }
    if (authStage === "code") {
      submitCodeMutation.mutate({ id: createdAccountId, code: codeInput.trim() }, {
        onSuccess: (account) => {
          setAuthStage(account.status === "WAITING_PASSWORD" ? "password" : account.status === "CONNECTED" ? "done" : "code");
          accountsQuery.refetch();
        },
        onError: (error) => setDialogError(error instanceof Error ? error.message : "Не удалось подтвердить код"),
      });
      return;
    }
    submitPasswordMutation.mutate({ id: createdAccountId, password: passwordInput }, {
      onSuccess: (account) => {
        setAuthStage(account.status === "CONNECTED" ? "done" : "password");
        accountsQuery.refetch();
      },
      onError: (error) => setDialogError(error instanceof Error ? error.message : "Не удалось подтвердить пароль"),
    });
  };

  const confirmAction = (question: string, action: () => void) => {
    if (window.confirm(question)) action();
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Аккаунты"
        description="Единое место управления Telegram-сессиями: подключение, пауза и отключение. История и материалы остаются в базе."
        actions={{ primary: { label: "Добавить аккаунт", onClick: () => setAddDialogOpen(true) }, onRefresh: () => accountsQuery.refetch() }}
      />

      <div className="flex flex-wrap items-center gap-3">
        <Input placeholder="Поиск по ID, юзернейму или телефону" value={search} onChange={(event) => setSearch(event.target.value)} className="max-w-sm" />
        <div className="flex flex-wrap gap-1.5">
          {FILTERS.map((item) => <Button key={item} variant={filter === item ? "secondary" : "ghost"} size="sm" onClick={() => setFilter(item)}>{FILTER_LABELS[item]}</Button>)}
        </div>
        {accountsQuery.isLoading ? <SpinnerGap size={16} className="animate-spin text-text-muted" /> : null}
      </div>

      {accountsQuery.isError ? <Alert variant="danger">{(accountsQuery.error as Error | null)?.message ?? "Не удалось загрузить аккаунты"}</Alert> : null}
      {deleteAccountMutation.isError ? <Alert variant="danger">{(deleteAccountMutation.error as Error | null)?.message ?? "Не удалось отключить сессию"}</Alert> : null}

      {accounts.length === 0 && !accountsQuery.isLoading ? (
        <EmptyState icon={CellSignalFull} title="Нет аккаунтов" description="Добавьте Telegram-аккаунт для начала работы." action={<Button variant="primary" size="sm" onClick={() => setAddDialogOpen(true)}><Plus size={16} className="mr-1.5" />Добавить аккаунт</Button>} />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border-subtle bg-bg-card">
          <Table>
            <TableHeader><TableRow><TableHead>ID</TableHead><TableHead>Юзернейм</TableHead><TableHead>Телефон</TableHead><TableHead>Статус</TableHead><TableHead>Чатов</TableHead><TableHead>Proxy</TableHead><TableHead>Активность</TableHead><TableHead className="w-10" /></TableRow></TableHeader>
            <TableBody>
              {filtered.map((account) => (
                <TableRow key={account.id}>
                  <TableCell className="font-mono-value text-text-muted">#{account.id}</TableCell>
                  <TableCell className="font-medium text-text-strong">{account.username ?? "—"}</TableCell>
                  <TableCell className="font-mono-value text-text-muted">{account.phone}</TableCell>
                  <TableCell><StatusBadge status={displayAccountStatus(account.status)} /></TableCell>
                  <TableCell className="font-mono-value text-sm">{account.groupsCount ?? 0}</TableCell>
                  <TableCell><Badge variant="outline">См. Оператор</Badge></TableCell>
                  <TableCell className="text-sm text-text-muted">{account.createdAt ?? "—"}</TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="h-8 w-8"><DotsThree size={16} /></Button></DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {account.status === "PAUSED" ? <DropdownMenuItem onClick={() => confirmAction("Продолжить работу аккаунта?", () => resumeAccountMutation.mutate(account.id))}><Play size={16} className="mr-2" />Продолжить</DropdownMenuItem> : <DropdownMenuItem onClick={() => confirmAction("Поставить аккаунт на паузу?", () => pauseAccountMutation.mutate(account.id))}><Pause size={16} className="mr-2" />Пауза</DropdownMenuItem>}
                        {account.status === "DISABLED" ? <DropdownMenuItem onClick={() => confirmAction("Включить аккаунт?", () => enableAccountMutation.mutate(account.id))}><Play size={16} className="mr-2" />Включить</DropdownMenuItem> : <DropdownMenuItem onClick={() => confirmAction("Отключить аккаунт?", () => disableAccountMutation.mutate(account.id))}><Prohibit size={16} className="mr-2" />Отключить</DropdownMenuItem>}
                        <DropdownMenuItem className="text-danger focus:text-danger" onClick={() => confirmAction("Отключить Telegram-сессию? Это завершит TDLib-сессию для этого пользователя приложения. История, группы, сообщения и материалы для этого Telegram аккаунта останутся в базе.", () => deleteAccountMutation.mutate(account.id))}><Trash size={16} className="mr-2" />Отключить сессию</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={addDialogOpen} onOpenChange={(open) => (open ? setAddDialogOpen(true) : resetDialog())}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Добавить Telegram-аккаунт</DialogTitle><DialogDescription>{currentStep.description}</DialogDescription></DialogHeader>
          <Badge variant="outline" className="w-fit">{currentStep.title}</Badge>
          <div className="py-4">
            {authStage === "phone" ? <Input placeholder="+7xxxxxxxxxx" type="tel" autoFocus value={phoneInput} onChange={(event) => setPhoneInput(event.target.value)} /> : null}
            {authStage === "code" ? <Input placeholder="Код из Telegram" autoFocus value={codeInput} onChange={(event) => setCodeInput(event.target.value)} /> : null}
            {authStage === "password" ? <Input placeholder="Пароль 2FA" type="password" autoFocus value={passwordInput} onChange={(event) => setPasswordInput(event.target.value)} /> : null}
            {authStage === "done" ? <div className="flex items-center gap-2 text-sm text-success"><CheckCircle size={16} />Аккаунт появился в системе</div> : null}
            {dialogError ? <Alert variant="danger" className="mt-4">{dialogError}</Alert> : null}
          </div>
          <DialogFooter><Button onClick={handlePrimaryAction} disabled={isSubmitting || (authStage === "phone" && !phoneInput.trim()) || (authStage === "code" && !codeInput.trim()) || (authStage === "password" && !passwordInput.trim())}>{isSubmitting ? "Отправка..." : currentStep.buttonLabel}</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
