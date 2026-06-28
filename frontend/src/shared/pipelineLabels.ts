export function blockerLabel(code?: string | null) {
  const labels: Record<string, string> = {
    AUTO_PIPELINE_DISABLED: "Автообработка выключена для этого чата или темы",
    MODEL_WORKER_DOWN: "Модельный воркер недоступен",
    MODEL_NOT_CONFIGURED: "Модель embeddings недоступна или не настроена",
    PROVIDER_DISABLED: "AI-провайдер не настроен или выключен",
    PROVIDER_DISABLED_OR_KEY_MISSING: "AI-провайдер не настроен или выключен",
    NO_CLUSTERS: "Недостаточно похожих сообщений для кластера",
    NO_CLUSTERS_OR_SINGLE_MESSAGE_CANDIDATES: "Недостаточно сильных сообщений для материала",
    SINGLE_MESSAGE_CANDIDATES_WAITING_FOR_LLM: "Одиночные кандидаты ждут LLM-оценку",
    THRESHOLD_NOT_MET: "Порог качества материала не пройден",
    NO_MATERIALS: "Материалы пока не сформированы",
    TDLIB_UPDATE_NOT_RECEIVED: "Telegram-обновления не приходят",
    WAITING_FOR_BATCH: "Сообщение ждёт debounce/batch обработку",
    REPLAY_ARTIFACTS_NOT_CREATED: "Replay artifacts для сообщения ещё не созданы",
    NO_TEXT: "Сообщение не содержит текста или подписи",
    NONE: "Blocker не обнаружен",
  };
  if (!code) return "Нет blocker";
  return labels[code] ?? code;
}

export function actionLabel(action?: string | null) {
  const labels: Record<string, string> = {
    ENABLE_AUTO_PIPELINE_FOR_CHAT_OR_TOPIC: "Включить автообработку для выбранного чата или темы",
    ENABLE_AUTO_PIPELINE_FOR_SCOPE: "Включить автообработку для текущего scope",
    START_MODEL_WORKER: "Проверить model-worker",
    CONFIGURE_PROVIDER: "Проверить активный AI-провайдер",
    RUN_SCOPED_REPLAY_OR_WAIT_FOR_MORE_MESSAGES: "Дождаться новых сообщений или запустить scoped replay",
    OPEN_PIPELINE_TRACE: "Открыть trace pipeline",
    NO_ACTION_REQUIRED: "Действие не требуется",
    WAIT_OR_ENABLE_SCOPE: "Подождать batch или включить scope",
  };
  if (!action) return "Нет предложенного действия";
  return labels[action] ?? action;
}

export function statusLabelRu(status?: string | null) {
  const labels: Record<string, string> = {
    PENDING: "Ожидает",
    QUEUED: "В очереди",
    PROCESSING: "В работе",
    RUNNING: "В работе",
    PROCESSED: "Обработано",
    PROCESSED_DEGRADED: "Обработано в degraded mode",
    COMPLETED: "Завершено",
    FAILED: "Ошибка",
    ERROR: "Ошибка",
    SKIPPED: "Пропущено",
    WAITING_FOR_WORKER: "Ждёт model-worker",
    MODEL_WORKER_DOWN: "Модельный воркер недоступен",
    MODEL_NOT_CONFIGURED: "Модель embeddings недоступна или не настроена",
    RUN_CREATED: "Запуск создан",
    NO_CLUSTERS: "Недостаточно похожих сообщений для кластера",
  };
  if (!status) return "Нет данных";
  return labels[status] ?? blockerLabel(status);
}
