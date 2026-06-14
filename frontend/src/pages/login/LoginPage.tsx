import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useLoginMutation } from "@/shared/api/authApi";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SpinnerGap } from "@phosphor-icons/react";

export function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const loginMutation = useLoginMutation();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!username.trim() || !password.trim()) {
      setError("Введите логин и пароль");
      return;
    }

    loginMutation.mutate(
      { username: username.trim(), password },
      {
        onSuccess: () => {
          navigate("/dashboard", { replace: true });
        },
        onError: (err) => {
          setError("Неверный логин или пароль");
          console.error("Login failed:", err);
        },
      }
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-app">
      <div className="w-full max-w-sm rounded-2xl border border-border-subtle bg-bg-card p-8 shadow-[0_8px_30px_rgba(0,0,0,0.25)]">
        {/* Logo */}
        <div className="flex justify-center mb-8">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-blue-soft border border-border-subtle">
            <svg viewBox="0 0 32 32" className="h-9 w-9" fill="none">
              <line x1="16" y1="6" x2="8" y2="22" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" opacity="0.5" />
              <line x1="16" y1="6" x2="24" y2="22" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" opacity="0.5" />
              <line x1="8" y1="22" x2="24" y2="22" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" opacity="0.5" />
              <circle cx="16" cy="6" r="3" fill="currentColor" />
              <circle cx="8" cy="22" r="3" fill="currentColor" />
              <circle cx="24" cy="22" r="3" fill="currentColor" />
              <circle cx="16" cy="16" r="4" stroke="#F5C94A" strokeWidth="1.5" fill="none" />
              <circle cx="16" cy="16" r="1.5" fill="#F5C94A" />
            </svg>
          </div>
        </div>

        <h1 className="text-center text-lg font-semibold text-text-strong mb-1">
          NeuroInfoGrinder
        </h1>
        <p className="text-center text-sm text-text-muted mb-6">
          Войдите в панель управления
        </p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className="block text-xs font-medium text-text-muted mb-1.5">
              Логин
            </label>
            <Input
              type="text"
              placeholder="admin"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-text-muted mb-1.5">
              Пароль
            </label>
            <Input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>

          {error && (
            <p className="text-sm text-danger rounded-lg bg-danger-soft px-3 py-2">
              {error}
            </p>
          )}

          <Button
            type="submit"
            variant="primary"
            className="w-full"
            disabled={loginMutation.isPending}
          >
            {loginMutation.isPending ? (
              <>
                <SpinnerGap size={16} weight="regular" className="animate-spin" />
                Вход...
              </>
            ) : (
              "Войти"
            )}
          </Button>
        </form>
      </div>
    </div>
  );
}
