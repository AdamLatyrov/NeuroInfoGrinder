# Ночная задача: real-time Telegram → правила → классификация → гайд

## Цель

Сделать полностью наблюдаемый pipeline, в котором новое Telegram-сообщение появляется в UI почти сразу, проходит каждый этап обработки перед глазами пользователя и либо превращается в гайд, либо получает понятную причину остановки.

Целевой путь:

`TDLib updateNewMessage → сохранение → правило → LINEAR_MODEL → LLM-классификатор → генерация гайда → UI`

## Что сейчас не устраивает

1. Telegram-сообщения подтягиваются периодическим ручным sync, а не push-событиями TDLib.
2. Frontend использует частый polling и создаёт лишнюю нагрузку, но всё равно выглядит медленным.
3. QueueProcessor запускается раз в 5 секунд и берёт только небольшие пачки.
4. Пользователь не видит сообщение как живой объект, перемещающийся между стадиями.
5. Слишком много правил и классификаторов мешают понять базовую механику.
6. Реальный AI provider не настроен, поэтому невозможно честно проверить LLM-этап.
7. Нет гарантированного end-to-end теста от нового сообщения до сохранённого гайда.

## Итоговая минимальная конфигурация

### Одно правило

`Reject obvious noise`

- отклоняет пустые сообщения;
- отклоняет сообщения короче 5 символов;
- все остальные сообщения пропускает дальше;
- сохраняет понятный trace с входом, результатом и причиной.

### Один локальный классификатор

`Guide Candidate Linear`

- тип `LINEAR_MODEL`;
- использует длину, наличие ссылки, AI/tool/how-to маркеры, вопрос, код и тему;
- возвращает score, threshold и вклад каждого признака;
- конфигурация редактируется через UI.

### Один LLM-классификатор

`Gemini Guide Judge`

- provider: Gemini Developer API;
- начальная модель: `gemini-2.5-flash` либо актуальная бесплатная Flash-модель;
- возвращает строгий JSON: `matched`, `score`, `reasoning`, `guideType`;
- вызывается только после успешного LINEAR_MODEL;
- при отсутствии API key остаётся доступен локальный mock provider.

### Один prompt генерации гайда

- принимает цепочку сообщений и результат классификации;
- создаёт короткую заметку или пошаговый гайд;
- возвращает строгий JSON;
- сохраняет исходные сообщения и полный trace.

## Архитектура real-time

### Backend

1. Подписать `TdlibClientManager` на `TdApi.UpdateNewMessage`.
2. Добавить `TelegramMessageIngestService`:
   - определить локальную группу по `chatId`;
   - игнорировать выключенные группы;
   - выполнить idempotent upsert сообщения;
   - установить `QUEUED`;
   - опубликовать `MessageReceivedEvent`.
3. Добавить внутренний event bus через Spring events.
4. QueueProcessor заменить на event-driven обработчик:
   - новое сообщение запускается сразу;
   - scheduled processor остаётся как recovery fallback;
   - защита от двойной обработки через atomic status transition.
5. Добавить серверный поток событий:
   - предпочтительно SSE для простого однонаправленного UI;
   - endpoint `/api/v1/events/stream`;
   - события `message.received`, `stage.started`, `stage.completed`, `message.completed`, `guide.created`, `pipeline.error`.
6. Каждое событие содержит:
   - `messageId`;
   - `groupId`;
   - `traceId`;
   - `stage`;
   - `status`;
   - `timestamp`;
   - `durationMs`;
   - краткую причину.

### Frontend

1. Создать один `EventSource` на уровне приложения.
2. При событии обновлять React Query cache точечно, а не перезапрашивать всё каждые 500 мс.
3. Polling оставить только как fallback:
   - очередь: 5–10 секунд;
   - сообщения: 5–10 секунд;
   - трейсы: только при потере SSE.
4. На странице `Очередь` показать живые колонки:
   - `Получено`;
   - `Правило`;
   - `LINEAR_MODEL`;
   - `LLM`;
   - `Генерация`;
   - `Гайд`;
   - `Отклонено`.
5. Карточка сообщения визуально перемещается между стадиями.
6. При раскрытии карточки показывать:
   - каждый этап;
   - время начала;
   - длительность;
   - input/output;
   - score и threshold;
   - причину прохождения или отсева.
7. Убрать прыжки layout:
   - фиксированные области загрузки;
   - skeleton вместо полной замены блока;
   - очередь и обработанные сообщения обновляются без сброса scroll.

## Gemini provider

1. Добавить отдельный протокол `GEMINI`.
2. Использовать официальный REST endpoint Gemini Developer API.
3. API key читать только из `GEMINI_API_KEY`.
4. Не сохранять открытый ключ в frontend или логах.
5. Добавить provider:
   - name: `Google Gemini Free`;
   - protocol: `GEMINI`;
   - model: конфигурируемая Flash-модель;
   - status: `DISABLED`, пока ключ не задан и test не прошёл.
6. Добавить кнопку `Проверить и включить`.
7. После успешного теста provider становится `ACTIVE`.
8. При ошибке UI показывает HTTP status, response body и понятную рекомендацию.
9. Mock provider оставить для гарантированного smoke-теста.

## Наблюдаемость

1. Ввести единый `traceId` на полный путь сообщения.
2. Записывать trace до начала и после завершения каждого этапа.
3. Ошибки не должны оставлять `PROCESSING`:
   - финальный статус `FAILED`;
   - error trace;
   - возможность `Повторить`.
4. Добавить latency metrics:
   - Telegram receive → DB saved;
   - DB saved → pipeline started;
   - время каждого этапа;
   - total processing time;
   - guide creation latency.
5. В UI показать p50/p95 и последние ошибки.

## Контроль производительности

Целевые показатели локальной разработки:

- новое TDLib сообщение появляется в UI: до 1 секунды;
- pipeline начинается: до 500 мс после сохранения;
- rule stage: до 20 мс;
- LINEAR_MODEL: до 30 мс;
- UI получает stage event: до 300 мс после записи trace;
- без LLM полный локальный smoke pipeline: до 2 секунд;
- с Gemini зависит от API, но UI сразу показывает `LLM выполняется`.

## Порядок выполнения

### Фаза 1. Диагностика и baseline

1. Зафиксировать текущие интервалы sync/polling/scheduler.
2. Измерить текущую задержку на тестовом сообщении.
3. Добавить integration test текущего pipeline.

### Фаза 2. Минимальная конфигурация

1. Отключить лишние стартовые rules/classifiers без удаления данных.
2. Создать одно noise rule.
3. Создать один LINEAR_MODEL.
4. Создать один LLM classifier.
5. Создать один generation prompt.
6. Добавить экран `Активная конфигурация`.

### Фаза 3. TDLib push ingest

1. Обработать `UpdateNewMessage`.
2. Реализовать idempotent persistence.
3. Проверить выключенные группы.
4. Автоматически enqueue новое сообщение.
5. Добавить unit/integration tests.

### Фаза 4. Event-driven pipeline

1. Добавить domain events.
2. Запускать pipeline сразу после ingest.
3. Сделать scheduled recovery processor.
4. Исключить зависание `PROCESSING`.
5. Реализовать retry.

### Фаза 5. SSE и live UI

1. Реализовать backend SSE endpoint.
2. Реализовать frontend event client.
3. Обновлять React Query cache событиями.
4. Сделать живую доску стадий.
5. Уменьшить polling.
6. Проверить reconnect SSE.

### Фаза 6. Gemini

1. Реализовать Gemini protocol.
2. Добавить env/config.
3. Добавить test connection.
4. Добавить provider activation.
5. Проверить classification JSON.
6. Проверить generation JSON.
7. Добавить обработку rate limit и timeout.

### Фаза 7. End-to-end доказательство

1. Создать тестовое сообщение.
2. Дождаться `message.received`.
3. Проверить rule trace.
4. Проверить LINEAR_MODEL trace.
5. Проверить LLM trace.
6. Проверить GUIDE_GENERATION trace.
7. Проверить сохранённый guide.
8. Открыть guide из UI.
9. Открыть исходное сообщение.
10. Зафиксировать latency каждого этапа.

## Автоматические тесты

1. Unit: rule matcher.
2. Unit: LINEAR_MODEL feature extraction.
3. Unit: Gemini response parser.
4. Unit: pipeline status transitions.
5. Integration: TDLib update mapping без native вызова.
6. Integration: message → mock provider → guide.
7. Integration: message → Gemini stub → guide.
8. Integration: failed provider → `FAILED`, не `PROCESSING`.
9. Integration: disabled group message не попадает в pipeline.
10. Frontend: SSE event обновляет нужную карточку.
11. Frontend: reconnect не дублирует сообщения.

## Критерии готовности

- [ ] Одно новое сообщение видно в UI менее чем за 1 секунду.
- [ ] Пользователь видит начало и конец каждого этапа без обновления страницы.
- [ ] Активны ровно: одно правило, один LINEAR_MODEL, один LLM classifier.
- [ ] Mock smoke test всегда создаёт гайд.
- [ ] Gemini test создаёт гайд при наличии `GEMINI_API_KEY`.
- [ ] Ошибки provider видны в trace.
- [ ] Ни одно сообщение не зависает в `PROCESSING`.
- [ ] Выключенные группы не обрабатываются.
- [ ] Очередь и UI не прыгают при обновлении.
- [ ] Backend tests и frontend build проходят.

## Ограничения ночного запуска

1. Не удалять Telegram-аккаунты и пользовательские сообщения.
2. Не менять TDLib credentials.
3. Не отключать существующий mock smoke path.
4. Миграции делать только добавочными и идемпотентными.
5. Перед изменением native TDLib кода сохранить рабочий путь запуска.
6. При отсутствии `GEMINI_API_KEY` завершить Gemini-интеграцию на mock/stub и явно отметить внешний blocker.
7. Не считать задачу готовой без реального сохранённого guide и полного trace.

## Финальный отчёт

Ночной запуск должен вернуть:

1. Что изменено по backend/frontend.
2. Какой provider активен.
3. ID тестового сообщения и guide.
4. Полную последовательность стадий.
5. Latency каждого этапа.
6. Результаты тестов и сборок.
7. Оставшиеся blockers.
