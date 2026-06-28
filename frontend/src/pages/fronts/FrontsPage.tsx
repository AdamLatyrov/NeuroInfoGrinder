import { PageHeaderCard } from "@/components/domain/page-header-card";
import { Card, CardContent } from "@/components/ui/card";

export function FrontsPage() {
  return (
    <div className="flex flex-col gap-5">
      <PageHeaderCard
        title="Фронты"
        description="Раздел фронтов готовится. Здесь будут настройки внешних витрин и публикации материалов."
      />
      <Card>
        <CardContent className="p-6 text-sm text-text-muted">
          Сейчас этот раздел не участвует в основном production-сценарии. Рабочий поток: Группы, затем Конвейер, затем Материалы.
        </CardContent>
      </Card>
    </div>
  );
}
