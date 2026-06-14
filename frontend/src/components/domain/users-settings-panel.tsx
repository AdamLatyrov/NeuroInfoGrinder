import { useMemo, useState } from "react";
import { Eye, EyeSlash, Key, Plus, SpinnerGap } from "@phosphor-icons/react";
import { useCurrentUser } from "@/shared/api/authApi";
import {
  type AppUser,
  useChangeUserPasswordMutation,
  useCreateUserMutation,
  useSetUserHiddenMutation,
  useUsersQuery,
} from "@/shared/api/usersApi";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

function formatDate(value: string): string {
  return new Date(value).toLocaleString("ru-RU");
}

export function UsersSettingsPanel() {
  const currentUserQuery = useCurrentUser();
  const usersQuery = useUsersQuery();
  const createUserMutation = useCreateUserMutation();
  const setUserHiddenMutation = useSetUserHiddenMutation();
  const changeUserPasswordMutation = useChangeUserPasswordMutation();

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [passwordDialogUser, setPasswordDialogUser] = useState<AppUser | null>(null);
  const [username, setUsername] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [nextPassword, setNextPassword] = useState("");
  const [createError, setCreateError] = useState("");
  const [passwordError, setPasswordError] = useState("");

  const users = usersQuery.data ?? [];
  const currentUserId = currentUserQuery.data?.userId ?? null;
  const activeUsersCount = useMemo(
    () => users.filter((user) => !user.hidden).length,
    [users]
  );

  function resetCreateDialog() {
    setCreateDialogOpen(false);
    setUsername("");
    setCreatePassword("");
    setCreateError("");
  }

  function resetPasswordDialog() {
    setPasswordDialogUser(null);
    setNextPassword("");
    setPasswordError("");
  }

  function handleCreateUser() {
    const normalizedUsername = username.trim();
    if (normalizedUsername.length < 3) {
      setCreateError("Логин должен быть не короче 3 символов.");
      return;
    }
    if (createPassword.trim().length < 4) {
      setCreateError("Пароль должен быть не короче 4 символов.");
      return;
    }

    setCreateError("");
    createUserMutation.mutate(
      { username: normalizedUsername, password: createPassword },
      {
        onSuccess: () => resetCreateDialog(),
        onError: () => {
          setCreateError("Не удалось создать пользователя. Проверьте логин и попробуйте снова.");
        },
      }
    );
  }

  function handleChangePassword() {
    if (!passwordDialogUser) {
      return;
    }
    if (nextPassword.trim().length < 4) {
      setPasswordError("Пароль должен быть не короче 4 символов.");
      return;
    }

    setPasswordError("");
    changeUserPasswordMutation.mutate(
      { userId: passwordDialogUser.id, password: nextPassword },
      {
        onSuccess: () => resetPasswordDialog(),
        onError: () => {
          setPasswordError("Не удалось сохранить новый пароль.");
        },
      }
    );
  }

  function handleToggleHidden(user: AppUser) {
    setUserHiddenMutation.mutate({
      userId: user.id,
      hidden: !user.hidden,
    });
  }

  return (
    <>
      <Card>
        <CardContent className="p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-text-strong">Пользователи</h3>
              <p className="mt-1 text-sm text-text-muted">
                Все аккаунты имеют права администратора. Удаление отключено:
                пользователя можно только скрыть, вернуть и сменить ему пароль.
              </p>
            </div>
            <Button variant="primary" size="sm" onClick={() => setCreateDialogOpen(true)}>
              <Plus size={16} weight="regular" />
              Добавить пользователя
            </Button>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Badge variant="success">{activeUsersCount} активных</Badge>
            <Badge variant="secondary">{users.length - activeUsersCount} скрытых</Badge>
          </div>

          <div className="mt-4 overflow-hidden rounded-xl border border-border-subtle">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Логин</TableHead>
                  <TableHead>Статус</TableHead>
                  <TableHead>Роль</TableHead>
                  <TableHead>Создан</TableHead>
                  <TableHead className="text-right">Действия</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {usersQuery.isLoading ? (
                  <TableRow>
                    <TableCell colSpan={5} className="py-10 text-center text-text-muted">
                      <span className="inline-flex items-center gap-2">
                        <SpinnerGap size={16} className="animate-spin" />
                        Загружаем пользователей...
                      </span>
                    </TableCell>
                  </TableRow>
                ) : users.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="py-10 text-center text-text-muted">
                      Пользователей пока нет.
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((user) => {
                    const isCurrentUser = currentUserId === user.id;
                    const disableHide = isCurrentUser || (!user.hidden && activeUsersCount <= 1);

                    return (
                      <TableRow key={user.id}>
                        <TableCell className="font-medium text-text-strong">
                          <div className="flex items-center gap-2">
                            <span>{user.username}</span>
                            {isCurrentUser ? <Badge variant="outline">вы</Badge> : null}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={user.hidden ? "secondary" : "success"}>
                            {user.hidden ? "Скрыт" : "Активен"}
                          </Badge>
                        </TableCell>
                        <TableCell>{user.role}</TableCell>
                        <TableCell className="text-text-muted">{formatDate(user.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setPasswordDialogUser(user)}
                            >
                              <Key size={16} weight="regular" />
                              Пароль
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleToggleHidden(user)}
                              disabled={disableHide || setUserHiddenMutation.isPending}
                            >
                              {user.hidden ? (
                                <>
                                  <Eye size={16} weight="regular" />
                                  Показать
                                </>
                              ) : (
                                <>
                                  <EyeSlash size={16} weight="regular" />
                                  Скрыть
                                </>
                              )}
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>

          {(usersQuery.isError || setUserHiddenMutation.isError) && (
            <p className="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
              Не удалось обновить список пользователей или сохранить изменения.
            </p>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={createDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            resetCreateDialog();
            return;
          }
          setCreateDialogOpen(true);
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Новый пользователь</DialogTitle>
            <DialogDescription>
              Создайте ещё один админский аккаунт для входа в систему.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-text-muted">Логин</label>
              <Input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="operator_2"
                autoFocus
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-text-muted">Пароль</label>
              <Input
                type="password"
                value={createPassword}
                onChange={(event) => setCreatePassword(event.target.value)}
                placeholder="Минимум 4 символа"
              />
            </div>
            {createError ? (
              <p className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{createError}</p>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={resetCreateDialog}>
              Отмена
            </Button>
            <Button variant="primary" onClick={handleCreateUser} disabled={createUserMutation.isPending}>
              {createUserMutation.isPending ? (
                <>
                  <SpinnerGap size={16} className="animate-spin" />
                  Создаём...
                </>
              ) : (
                "Создать"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={passwordDialogUser !== null}
        onOpenChange={(open) => {
          if (!open) {
            resetPasswordDialog();
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Смена пароля</DialogTitle>
            <DialogDescription>
              Новый пароль будет установлен для пользователя {passwordDialogUser?.username ?? "—"}.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-text-muted">Новый пароль</label>
              <Input
                type="password"
                value={nextPassword}
                onChange={(event) => setNextPassword(event.target.value)}
                placeholder="Минимум 4 символа"
                autoFocus
              />
            </div>
            {passwordError ? (
              <p className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{passwordError}</p>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={resetPasswordDialog}>
              Отмена
            </Button>
            <Button
              variant="primary"
              onClick={handleChangePassword}
              disabled={changeUserPasswordMutation.isPending}
            >
              {changeUserPasswordMutation.isPending ? (
                <>
                  <SpinnerGap size={16} className="animate-spin" />
                  Сохраняем...
                </>
              ) : (
                "Сменить пароль"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
