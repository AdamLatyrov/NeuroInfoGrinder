import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, ArrowSquareOut, ArrowUp, ChatCircle } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getJsonAuth } from "@/shared/api/http";
import { usePromoteSignal, useSignalSourcesQuery, type KnowledgeSignal } from "@/shared/api/signalsApi";
import { useState } from "react";

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("ru-RU") : "—";
}

function useSignalDetail(signalId: number | undefined) {
  const q = useQuery({
    queryKey: ["signal-detail", signalId],
    queryFn: () => getJsonAuth<KnowledgeSignal & { topics?: Array<{ slug: string; name: string }> }>(`/api/v1/signals/${signalId}`),
    enabled: signalId != null,
    staleTime: 15_000,
  });
  return q.data;
}

export function SignalDetailPage() {
  const { id } = useParams();
  const signalId = id ? Number(id) : undefined;
  const navigate = useNavigate();
  const promote = usePromoteSignal();
  const [promoting, setPromoting] = useState(false);
  const sig = useSignalDetail(signalId);
  const sourcesQuery = useSignalSourcesQuery(signalId);

  const handlePromote = async () => {
    if (signalId == null) return;
    setPromoting(true);
    try {
      const res = await promote.promote(signalId);
      navigate(`/materials/${res.materialId}`);
    } catch (err) {
      console.error(err);
      setPromoting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link to="/materials" className="inline-flex items-center gap-2 text-sm text-text-muted transition hover:text-brand-blue">
          <ArrowLeft size={16} />
          База знаний
        </Link>
      </div>

      {sig ? (
        <PageHeaderCard
          title={sig.title ?? "Сигнал"}
          description={sig.summary ?? sig.reason ?? "Полезный сигнал без готового материала."}
          pipelineNote="Сигнал — это отклонённое сообщение (или серия сообщений одного отправителя), которое требует проверки перед генерацией материала."
        />
      ) : (
        <Card className="border-border-subtle bg-bg-card"><CardContent className="p-6 text-sm text-text-muted">Загрузка сигнала…</CardContent></Card>
      )}

      <PageHeaderCard
        title="Источники сигнала"
        description="Все сообщения, из которых собран этот сигнал."
        actions={{ onRefresh: () => sourcesQuery.refetch(), refreshPending: sourcesQuery.isFetching }}
      />

      <div className="grid gap-3">
        <Button variant="default" size="default" className="w-fit" onClick={handlePromote} disabled={promoting || signalId == null}>
          <ArrowUp size={16} />
          {promoting ? "Создаю материал…" : "Сделать материалом"}
        </Button>

        {sourcesQuery.isLoading ? (
          <div className="grid gap-3">
            {Array.from({ length: 2 }).map((_, i) => <div key={i} className="h-32 animate-pulse rounded-lg border border-border-subtle bg-bg-card" />)}
          </div>
        ) : (sourcesQuery.data ?? []).length === 0 ? (
          <Card className="border-border-subtle bg-bg-card"><CardContent className="p-6 text-sm text-text-muted">У этого сигнала пока нет сохранённых source-сообщений.</CardContent></Card>
        ) : (
          (sourcesQuery.data ?? []).map((src, idx) => (
            <Card key={idx} className="border-border-subtle bg-bg-card">
              <CardContent className="p-5">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="outline">raw_id {src.rawId ?? "—"}</Badge>
                  <Badge variant="outline">{src.chatTitle ?? "Чат не указан"}</Badge>
                  <span className="text-xs text-text-weak">{formatDate(src.messageDate)}</span>
                </div>
                <div className="mt-3 flex items-center gap-2 text-sm text-text-muted">
                  <ChatCircle size={15} />
                  <span>{src.senderName ?? "Аноним"}</span>
                </div>
                <div className="mt-3 whitespace-pre-wrap break-words leading-6 text-text-default">
                  {src.text?.trim() || "Текст не сохранён"}
                </div>
                {src.appMessageUrl ? (
                  <Button variant="outline" size="sm" className="mt-4" onClick={() => navigate(src.appMessageUrl!)}>
                    <ArrowSquareOut size={15} />
                    Открыть в чате
                  </Button>
                ) : null}
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
