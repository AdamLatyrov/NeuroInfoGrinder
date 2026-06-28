import { useState } from "react";
import { CheckCircle, PencilSimple, Plus, SpinnerGap, Trash } from "@phosphor-icons/react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { deleteJsonAuth, getJsonAuth, patchJsonAuth, postJsonAuth } from "@/shared/api/http";
import { queryClient } from "@/shared/api/queryClient";

interface Provider {
  id: number;
  name: string;
  type: string;
  baseUrl: string | null;
  apiKeyRef: string | null;
  enabled: boolean;
  priority: number;
  healthStatus: string;
  lastHealthCheckAt: string | null;
}

interface ProviderFormState {
  id: number;
  name: string;
  type: string;
  baseUrl: string;
  apiKeyRef: string;
}

const emptyProvider: ProviderFormState = {
  id: 0,
  name: "",
  type: "OPENAI_COMPATIBLE",
  baseUrl: "",
  apiKeyRef: "",
};

function healthVariant(status?: string): "success" | "danger" | "warning" | "outline" {
  if (!status) return "outline";
  if (["OK", "SUCCESS", "UP"].includes(status)) return "success";
  if (["MISSING_API_KEY", "DOWN", "MODEL_WORKER_DOWN", "MODEL_NOT_CONFIGURED", "FAILED", "ERROR"].includes(status)) return "danger";
  return "warning";
}

export function AiProvidersPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState<ProviderFormState>(emptyProvider);

  const providers = useQuery({
    queryKey: ["ai-v2-providers"],
    queryFn: () => getJsonAuth<Provider[]>("/api/v2/ai/providers"),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["ai-v2-providers"] });

  const saveProvider = useMutation({
    mutationFn: () => {
      const body = {
        name: form.name,
        type: form.type,
        baseUrl: form.baseUrl || null,
        apiKeyRef: form.apiKeyRef || null,
        enabled: true,
        priority: 100,
      };
      return form.id
        ? patchJsonAuth(`/api/v2/ai/providers/${form.id}`, body)
        : postJsonAuth("/api/v2/ai/providers", body);
    },
    onSuccess: () => {
      setDialogOpen(false);
      setForm(emptyProvider);
      invalidate();
    },
  });

  const deleteProvider = useMutation({
    mutationFn: (id: number) => deleteJsonAuth(`/api/v2/ai/providers/${id}`),
    onSuccess: invalidate,
  });

  const activateProvider = useMutation({
    mutationFn: async (provider: Provider) => {
      const current = providers.data ?? [];
      await Promise.all(
        current.map((item) =>
          patchJsonAuth(`/api/v2/ai/providers/${item.id}`, { enabled: item.id === provider.id })
        )
      );
    },
    onSuccess: invalidate,
  });

  function openCreate() {
    setForm(emptyProvider);
    setDialogOpen(true);
  }

  function openEdit(provider: Provider) {
    setForm({
      id: provider.id,
      name: provider.name,
      type: provider.type,
      baseUrl: provider.baseUrl ?? "",
      apiKeyRef: provider.apiKeyRef ?? "",
    });
    setDialogOpen(true);
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="AI-провайдеры"
        description="Выберите активного провайдера или добавьте новый. Ключи не показываются: сохраняется только ссылка на env/secret."
      />

      <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
        {(providers.data ?? []).map((provider) => (
          <ProviderCard
            key={provider.id}
            provider={provider}
            pending={activateProvider.isPending || deleteProvider.isPending}
            onActivate={() => activateProvider.mutate(provider)}
            onEdit={() => openEdit(provider)}
            onDelete={() => {
              if (window.confirm(`Удалить провайдера ${provider.name}?`)) {
                deleteProvider.mutate(provider.id);
              }
            }}
          />
        ))}

        <button
          type="button"
          onClick={openCreate}
          className="min-h-[190px] rounded-[28px] border border-dashed border-brand-yellow/70 bg-warning-soft/55 p-5 text-left transition hover:border-brand-yellow hover:bg-warning-soft"
        >
          <div className="flex h-full flex-col items-center justify-center gap-3 text-text-strong">
            <span className="grid h-14 w-14 place-items-center rounded-2xl bg-bg-card shadow-sm">
              <Plus size={28} weight="bold" className="text-warning" />
            </span>
            <div className="text-center">
              <div className="font-semibold">Добавить провайдера</div>
              <div className="mt-1 text-sm text-text-muted">Открыть форму настройки</div>
            </div>
          </div>
        </button>
      </div>

      {providers.isLoading && (
        <div className="inline-flex items-center gap-2 rounded-2xl border border-border-subtle bg-bg-card px-4 py-3 text-sm text-text-muted">
          <SpinnerGap className="animate-spin" /> Загружаем провайдеров...
        </div>
      )}

      <ProviderDialog
        open={dialogOpen}
        form={form}
        pending={saveProvider.isPending}
        onOpenChange={setDialogOpen}
        onFormChange={setForm}
        onSubmit={() => saveProvider.mutate()}
      />
    </div>
  );
}

function ProviderCard({ provider, pending, onActivate, onEdit, onDelete }: { provider: Provider; pending: boolean; onActivate: () => void; onEdit: () => void; onDelete: () => void }) {
  return (
    <div
      className={`min-h-[190px] rounded-[28px] border p-5 transition ${
        provider.enabled
          ? "border-brand-yellow bg-warning-soft shadow-[0_18px_45px_rgba(223,180,69,0.18)]"
          : "border-border-subtle bg-bg-card hover:border-brand-blue/40"
      }`}
    >
      <div className="flex h-full flex-col justify-between gap-4">
        <div>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h2 className="truncate text-lg font-semibold text-text-strong">{provider.name}</h2>
                {provider.enabled && <CheckCircle size={18} weight="fill" className="text-warning" />}
              </div>
              <div className="mt-1 text-sm text-text-muted">{provider.type}</div>
            </div>
            <Badge variant={provider.enabled ? "success" : "outline"}>{provider.enabled ? "активный" : "неактивный"}</Badge>
          </div>

          <div className="mt-4 space-y-2 text-sm">
            <div className="truncate text-text-muted">{provider.baseUrl ?? "Base URL не задан"}</div>
            <div className="flex flex-wrap gap-2">
              <Badge variant={provider.apiKeyRef ? "success" : "danger"}>{provider.apiKeyRef ? "Ключ задан" : "Ключ не задан"}</Badge>
              <Badge variant={healthVariant(provider.healthStatus)}>{provider.healthStatus}</Badge>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={provider.enabled ? "secondary" : "primary"} disabled={pending || provider.enabled} onClick={onActivate}>
            Выбрать активным
          </Button>
          <Button size="sm" variant="outline" onClick={onEdit}>
            <PencilSimple size={14} />
            Редактировать
          </Button>
          <Button size="sm" variant="destructive" onClick={onDelete}>
            <Trash size={14} />
            Удалить
          </Button>
        </div>
      </div>
    </div>
  );
}

function ProviderDialog({ open, form, pending, onOpenChange, onFormChange, onSubmit }: { open: boolean; form: ProviderFormState; pending: boolean; onOpenChange: (open: boolean) => void; onFormChange: (form: ProviderFormState) => void; onSubmit: () => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{form.id ? "Редактировать провайдера" : "Добавить провайдера"}</DialogTitle>
          <DialogDescription>
            Введите данные подключения. Секретное значение ключа сюда не вставляем: используйте env/secret ref.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-3">
          <Input placeholder="Название" value={form.name} onChange={(event) => onFormChange({ ...form, name: event.target.value })} />
          <select className="rounded-xl border border-border-subtle bg-bg-elevated px-3 py-2" value={form.type} onChange={(event) => onFormChange({ ...form, type: event.target.value })}>
            <option value="OPENAI_COMPATIBLE">OpenAI-compatible</option>
            <option value="MODELHUB">ModelHub</option>
            <option value="OPENAI">OpenAI</option>
            <option value="ANTHROPIC_COMPATIBLE">Anthropic-compatible</option>
            <option value="CUSTOM">Custom</option>
          </select>
          <Input placeholder="Base URL" value={form.baseUrl} onChange={(event) => onFormChange({ ...form, baseUrl: event.target.value })} />
          <Input placeholder="API key env ref, например MODELHUB_API_KEY" value={form.apiKeyRef} onChange={(event) => onFormChange({ ...form, apiKeyRef: event.target.value })} />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Отменить</Button>
          <Button disabled={!form.name.trim() || pending} onClick={onSubmit}>
            {pending && <SpinnerGap className="animate-spin" />}
            {form.id ? "Сохранить" : "Добавить"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
