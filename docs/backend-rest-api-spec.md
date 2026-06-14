# NeuroInfoGrinder — Backend REST API Specification

## Общие правила

### Базовый префикс
Все endpoint'ы — `/api/v1`

### Формат ответа
JSON, `Content-Type: application/json`

### Формат ошибок
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Phone number is required",
  "details": null,
  "timestamp": "2026-06-05T12:00:00Z"
}
```

Коды ошибок:
- `VALIDATION_ERROR` — 400, ошибка валидации входных данных
- `NOT_FOUND` — 404, ресурс не найден
- `CONFLICT` — 409, конфликт (дубликат и тп)
- `UNAUTHORIZED` — 401, не авторизован
- `FORBIDDEN` — 403, нет прав
- `TOO_MANY_REQUESTS` — 429, лимит запросов
- `PROVIDER_ERROR` — 502, ошибка внешнего провайдера (AI, Telegram)
- `INTERNAL_ERROR` — 500, внутренняя ошибка

### Пагинация
Параметры запроса: `page` (0-based, default 0), `size` (default 50, max 100), `sort` (field,direction)

Формат ответа:
```json
{
  "content": [...],
  "page": 0,
  "size": 50,
  "totalElements": 234,
  "totalPages": 5
}
```

### Фильтрация
Через query-параметры: `status`, `groupId`, `providerId`, `from`, `to` (ISO date)

### Идентификаторы
Все сущности используют `Long` ID. Внешние Telegram ID — `Long` с префиксом `telegram` в названии поля.

---

## 1. Telegram Аккаунты

### GET /api/v1/accounts
Список подключённых Telegram-аккаунтов.

Response:
```json
[
  {
    "id": 1,
    "telegramUserId": 123456789,
    "username": "main_reader",
    "phone": "+79991234567",
    "firstName": "Ivan",
    "lastName": "P.",
    "status": "CONNECTED",
    "proxy": {
      "type": "SOCKS5",
      "host": "1.2.3.4",
      "port": 1080,
      "username": "proxyuser",
      "hasPassword": true
    },
    "groupsCount": 12,
    "tokensUsed": 15200,
    "lastActivityAt": "2026-06-05T10:42:00Z",
    "createdAt": "2026-05-01T08:00:00Z"
  }
]
```

Status enum: `CONNECTED`, `WAITING_CODE`, `WAITING_PASSWORD`, `WAITING_REGISTRATION`, `DISCONNECTED`, `ERROR`

### POST /api/v1/accounts
Начать подключение нового аккаунта (шаг 1 — отправка телефона).

Request:
```json
{
  "phone": "+79991234567",
  "proxy": {
    "type": "SOCKS5",
    "host": "1.2.3.4",
    "port": 1080,
    "username": "proxyuser",
    "password": "proxypass"
  }
}
```

Response:
```json
{
  "id": 2,
  "status": "WAITING_CODE",
  "phone": "+79991234567"
}
```

### POST /api/v1/accounts/{id}/code
Шаг 2 — ввод кода подтверждения.

Request:
```json
{
  "code": "12345"
}
```

Response:
```json
{
  "id": 2,
  "status": "CONNECTED"
}
```
или
```json
{
  "id": 2,
  "status": "WAITING_PASSWORD"
}
```

### POST /api/v1/accounts/{id}/password
Шаг 3 — ввод 2FA пароля (если нужен).

Request:
```json
{
  "password": "my2fapass"
}
```

Response:
```json
{
  "id": 2,
  "status": "CONNECTED"
}
```

### PATCH /api/v1/accounts/{id}/proxy
Обновить прокси для аккаунта.

Request:
```json
{
  "type": "SOCKS5",
  "host": "5.6.7.8",
  "port": 1080,
  "username": "newuser",
  "password": "newpass"
}
```

Response: обновлённый аккаунт (как в GET).

Для удаления прокси:
```json
{
  "type": null,
  "host": null,
  "port": null,
  "username": null,
  "password": null
}
```

### POST /api/v1/accounts/{id}/reconnect
Переподключить аккаунт (заново пройти авторизацию).

Response:
```json
{
  "id": 2,
  "status": "WAITING_CODE"
}
```

### DELETE /api/v1/accounts/{id}
Отключить и удалить аккаунт. Возвращает все привязанные группы в «без аккаунта».

Response: `204 No Content`

---

## 2. Группы

### GET /api/v1/groups
Список групп с пагинацией и фильтрами.

Query params:
- `page`, `size`, `sort`
- `enabled` (boolean) — фильтр по статусу
- `accountId` (Long) — фильтр по аккаунту-читателю
- `search` (String) — поиск по названию

Response:
```json
{
  "content": [
    {
      "id": 1,
      "telegramChatId": -1001234567890,
      "title": "VPN Обсуждение",
      "username": "vpn_discuss",
      "category": "IT",
      "enabled": true,
      "accountId": 1,
      "accountName": "main_reader",
      "messagesPerDay": 2140,
      "guidesFound": 12,
      "sparkline": [180, 210, 195, 220, 240, 190, 2140],
      "lastReadAt": "2026-06-05T10:42:00Z",
      "lastReadMessageId": 98765,
      "createdAt": "2026-05-01T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 234,
  "totalPages": 5
}
```

### POST /api/v1/groups/sync
Синхронизировать список групп из Telegram (вызвать getChats через TDLib и обновить БД).

Request:
```json
{
  "accountId": 1,
  "limit": 100
}
```

Response:
```json
{
  "added": 5,
  "updated": 12,
  "removed": 0
}
```

### PATCH /api/v1/groups/{id}
Обновить группу (включить/выключить, сменить аккаунт, категорию).

Request:
```json
{
  "enabled": true,
  "accountId": 2,
  "category": "VPN"
}
```

Response: обновлённая группа (как в GET).

### POST /api/v1/groups/bulk-toggle
Массовое включение/выключение групп.

Request:
```json
{
  "groupIds": [1, 2, 3],
  "enabled": true
}
```

Response:
```json
{
  "updated": 3
}
```

### POST /api/v1/groups/bulk-assign
Массовая смена аккаунта-читателя.

Request:
```json
{
  "groupIds": [1, 2, 3],
  "accountId": 2
}
```

Response:
```json
{
  "updated": 3
}
```

---

## 3. Чаты (просмотр сообщений)

### GET /api/v1/groups/{groupId}/messages
Сообщения группы с пагинацией.

Query params:
- `page`, `size`, `sort`
- `fromMessageId` (Long) — начиная с этого сообщения (для бесконечного скролла вверх)
- `processingStatus` (enum) — фильтр: `ALL`, `UNPROCESSED`, `IN_QUEUE`, `GUIDE_FOUND`, `SKIPPED`
- `dateFrom`, `dateTo` (ISO date)

Response:
```json
{
  "content": [
    {
      "id": 1,
      "telegramMessageId": 98765,
      "groupId": 1,
      "senderName": "Alex",
      "senderTelegramUserId": 111222333,
      "isBot": false,
      "text": "Вот способ зайти через новый сервер...",
      "replyToMessageId": null,
      "replyCount": 3,
      "processingStatus": "GUIDE_FOUND",
      "guideId": 42,
      "dateAt": "2026-06-05T10:42:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 2140,
  "totalPages": 43
}
```

ProcessingStatus enum: `UNPROCESSED`, `IN_QUEUE`, `PROCESSING`, `GUIDE_FOUND`, `SKIPPED`, `ERROR`

### GET /api/v1/groups/{groupId}/messages/{messageId}/chain
Получить цепочку сообщений для данного сообщения.

Query params:
- `chainType` (enum) — `REPLIES`, `TIME_WINDOW`, `COMBINED` (default: `COMBINED`)
- `timeWindowMinutes` (int, default: 5) — окно для TIME_WINDOW

Response:
```json
{
  "rootMessage": {
    "id": 1,
    "telegramMessageId": 98765,
    "senderName": "Alex",
    "text": "Вот способ зайти через новый сервер...",
    "dateAt": "2026-06-05T10:42:00Z"
  },
  "messages": [
    {
      "id": 2,
      "telegramMessageId": 98766,
      "senderName": "Maria",
      "text": "Да, проверил — работает. Шаги: 1. Скачать клиент...",
      "replyToMessageId": 98765,
      "isSignificant": true,
      "dateAt": "2026-06-05T10:44:00Z"
    }
  ],
  "processingResult": {
    "classifierId": 2,
    "classifierName": "Инструкция-детектор v2",
    "status": "GUIDE_FOUND",
    "guideId": 42,
    "guideTitle": "VPN подключение через новый сервер"
  }
}
```

### POST /api/v1/groups/{groupId}/messages/{messageId}/enqueue
Отправить сообщение/цепочку в обработку вручную.

Response:
```json
{
  "queued": true,
  "messageId": 1
}
```

---

## 4. Классификаторы

### GET /api/v1/classifiers
Список классификаторов.

Query params: `status` (ACTIVE, DRAFT, DISABLED)

Response:
```json
[
  {
    "id": 1,
    "name": "Спам-фильтр",
    "type": "KEYWORD",
    "providerId": null,
    "providerName": null,
    "promptId": null,
    "keywords": ["спам", "реклама", "куплю", "продам"],
    "regex": null,
    "version": 1,
    "status": "ACTIVE",
    "rulesCount": 2,
    "createdAt": "2026-05-01T08:00:00Z",
    "updatedAt": "2026-05-15T12:00:00Z"
  },
  {
    "id": 2,
    "name": "Инструкция-детектор",
    "type": "LLM",
    "providerId": 1,
    "providerName": "Claude",
    "promptId": 3,
    "keywords": null,
    "regex": null,
    "version": 2,
    "status": "ACTIVE",
    "rulesCount": 3,
    "createdAt": "2026-05-01T08:00:00Z",
    "updatedAt": "2026-06-01T15:00:00Z"
  }
]
```

ClassifierType enum: `LLM`, `KEYWORD`, `REGEX`

### POST /api/v1/classifiers
Создать классификатор.

Request:
```json
{
  "name": "Инструкция-детектор",
  "type": "LLM",
  "providerId": 1,
  "promptId": 3,
  "keywords": null,
  "regex": null,
  "status": "DRAFT"
}
```

Response: созданный классификатор (как в GET) с `id` и `version: 1`.

### PUT /api/v1/classifiers/{id}
Обновить классификатор (создаёт новую версию).

Request: как POST, но все поля опциональны.

Response: обновлённый классификатор с incremented `version`.

### PATCH /api/v1/classifiers/{id}/status
Изменить статус классификатора.

Request:
```json
{
  "status": "ACTIVE"
}
```

### DELETE /api/v1/classifiers/{id}
Удалить классификатор (только если не привязан к правилам).

Response: `204 No Content`

Ошибка если привязан: `409 CONFLICT` с message «Classifier is used by N rules».

---

## 5. Правила классификации

### GET /api/v1/rules
Список правил в порядке выполнения.

Response:
```json
[
  {
    "id": 1,
    "order": 1,
    "conditionType": "ALL_GROUPS",
    "conditionValue": null,
    "classifierId": 1,
    "classifierName": "Спам-фильтр v1",
    "action": "SKIP",
    "status": "ACTIVE",
    "createdAt": "2026-05-01T08:00:00Z"
  },
  {
    "id": 2,
    "order": 2,
    "conditionType": "GROUP_CATEGORY",
    "conditionValue": "IT,VPN",
    "classifierId": 2,
    "classifierName": "Инструкция-детектор v2",
    "action": "SEND_TO_LLM",
    "status": "ACTIVE",
    "createdAt": "2026-05-01T08:00:00Z"
  }
]
```

ConditionType enum:
- `ALL_GROUPS` — все группы
- `GROUP_CATEGORY` — категории групп (conditionValue = comma-separated)
- `SPECIFIC_GROUP` — конкретная группа (conditionValue = groupId)
- `MESSAGE_LENGTH_GT` — длина > N (conditionValue = число)
- `MESSAGE_LENGTH_LT` — длина < N
- `CONTAINS_WORD` — содержит слово (conditionValue = comma-separated words)
- `NOT_CONTAINS_WORD` — не содержит слово
- `FROM_BOT` — от бота (conditionValue = true/false)
- `MESSAGE_AGE_LT` — возраст < N часов (conditionValue = число)

Action enum: `SEND_TO_LLM`, `SKIP`, `MANUAL_REVIEW`

### POST /api/v1/rules
Создать правило.

Request:
```json
{
  "order": 3,
  "conditionType": "MESSAGE_LENGTH_GT",
  "conditionValue": "500",
  "classifierId": 2,
  "action": "SEND_TO_LLM",
  "status": "ACTIVE"
}
```

Response: созданное правило с `id`.

### PUT /api/v1/rules/{id}
Обновить правило.

### PATCH /api/v1/rules/reorder
Переставить порядок правил.

Request:
```json
{
  "ruleOrders": [
    { "ruleId": 1, "order": 1 },
    { "ruleId": 2, "order": 3 },
    { "ruleId": 3, "order": 2 }
  ]
}
```

### DELETE /api/v1/rules/{id}
Удалить правило.

Response: `204 No Content`

---

## 6. Настройка цепочек

### GET /api/v1/chain-config
Текущая конфигурация цепочек.

Response:
```json
{
  "includeReplies": true,
  "includeTimeWindow": true,
  "timeWindowMinutes": 5,
  "minMessagesForProcessing": 2,
  "maxMessagesPerChain": 50
}
```

### PUT /api/v1/chain-config
Обновить конфигурацию.

Request: как GET response, все поля обязательны.

Response: обновлённая конфигурация.

---

## 7. Гайды

### GET /api/v1/guides
Список гайдов с пагинацией и фильтрами.

Query params:
- `page`, `size`, `sort`
- `status` (DRAFT, PUBLISHED, REJECTED)
- `groupId` (Long)
- `providerId` (Long)
- `classifierId` (Long)
- `dateFrom`, `dateTo`
- `search` (String) — поиск по тексту

Response:
```json
{
  "content": [
    {
      "id": 42,
      "title": "VPN подключение через новый сервер",
      "groupId": 1,
      "groupTitle": "VPN Обсуждение",
      "providerId": 1,
      "providerName": "Claude",
      "model": "claude-sonnet-4-6",
      "classifierId": 2,
      "classifierName": "Инструкция-детектор v2",
      "promptTokens": 890,
      "completionTokens": 340,
      "totalTokens": 1230,
      "status": "DRAFT",
      "duplicateOf": null,
      "duplicateScore": null,
      "createdAt": "2026-06-05T10:42:00Z",
      "publishedAt": null
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 156,
  "totalPages": 4
}
```

GuideStatus enum: `DRAFT`, `PUBLISHED`, `REJECTED`

### GET /api/v1/guides/{id}
Полный гайд с исходными данными.

Response:
```json
{
  "id": 42,
  "title": "VPN подключение через новый сервер",
  "content": "## VPN подключение через новый сервер\n\n1. Скачать клиент с сайта...\n2. Установить, выбрав сервер NL-03...",
  "contentMarkdown": "## VPN подключение через новый сервер\n\n1. Скачать клиент с сайта...\n2. Установить, выбрав сервер NL-03...",
  "groupId": 1,
  "groupTitle": "VPN Обсуждение",
  "providerId": 1,
  "providerName": "Claude",
  "model": "claude-sonnet-4-6",
  "classifierId": 2,
  "classifierName": "Инструкция-детектор v2",
  "promptId": 3,
  "promptVersion": 2,
  "status": "DRAFT",
  "duplicateOf": null,
  "duplicateScore": null,
  "sourceMessages": [
    {
      "id": 1,
      "telegramMessageId": 98765,
      "senderName": "Alex",
      "text": "Вот способ зайти через новый сервер...",
      "replyToTelegramMessageId": null,
      "isRootMessage": true,
      "isSignificant": true,
      "telegramLink": "https://t.me/c/1234567890/98765",
      "dateAt": "2026-06-05T10:42:00Z"
    },
    {
      "id": 2,
      "telegramMessageId": 98766,
      "senderName": "Maria",
      "text": "Да, проверил — работает. Шаги: 1. Скачать клиент...",
      "replyToTelegramMessageId": 98765,
      "isRootMessage": false,
      "isSignificant": true,
      "telegramLink": "https://t.me/c/1234567890/98766",
      "dateAt": "2026-06-05T10:44:00Z"
    }
  ],
  "llmRequest": {
    "fullPrompt": "Извлеки из следующих сообщений пошаговую инструкцию...\n\n---\nСообщения:\n[Alex]: Вот способ зайти...\n[Maria]: Да, проверил...",
    "rawResponse": "{ \"title\": \"VPN подключение через новый сервер\", \"steps\": [...] }",
    "promptTokens": 890,
    "completionTokens": 340,
    "totalTokens": 1230,
    "durationMs": 3200,
    "model": "claude-sonnet-4-6",
    "providerName": "Claude"
  },
  "relatedGuides": [
    {
      "id": 38,
      "title": "Обход блокировки через DNS",
      "groupId": 1,
      "groupTitle": "VPN Обсуждение",
      "status": "PUBLISHED",
      "duplicateScore": null
    }
  ],
  "possibleDuplicates": [
    {
      "id": 55,
      "title": "VPN новый способ",
      "groupId": 3,
      "groupTitle": "IT Инструкции",
      "status": "PUBLISHED",
      "duplicateScore": 0.78
    }
  ],
  "createdAt": "2026-06-05T10:42:00Z",
  "publishedAt": null
}
```

### PATCH /api/v1/guides/{id}/status
Изменить статус гайда.

Request:
```json
{
  "status": "PUBLISHED"
}
```

Если `PUBLISHED` — бот постит гайд в целевую группу (если режим публикации — автоматический, иначе просто меняет статус).

Response: обновлённый гайд.

### PUT /api/v1/guides/{id}/content
Отредактировать текст гайда.

Request:
```json
{
  "contentMarkdown": "## Обновлённый гайд\n\n1. Новый шаг..."
}
```

Response: обновлённый гайд.

### POST /api/v1/guides/{id}/mark-not-duplicate
Отметить, что гайд НЕ является дубликатом указанного.

Request:
```json
{
  "otherGuideId": 55
}
```

Response: `200 OK`

### DELETE /api/v1/guides/{id}
Удалить гайд.

Response: `204 No Content`

---

## 8. AI Провайдеры

### GET /api/v1/providers
Список провайдеров.

Response:
```json
[
  {
    "id": 1,
    "name": "Claude",
    "protocol": "ANTHROPIC",
    "endpointUrl": "https://api.anthropic.com",
    "hasApiKey": true,
    "model": "claude-sonnet-4-6",
    "status": "CONNECTED",
    "totalTokensUsed": 47200,
    "estimatedCostUsd": 12.40,
    "lastTestedAt": "2026-06-05T08:00:00Z",
    "lastTestResult": "SUCCESS",
    "createdAt": "2026-05-01T08:00:00Z"
  }
]
```

ProviderProtocol enum: `ANTHROPIC`, `OPENAI_COMPATIBLE`, `CUSTOM_HTTP`

ProviderStatus enum: `CONNECTED`, `ERROR`, `DISABLED`

### POST /api/v1/providers
Создать провайдер.

Request:
```json
{
  "name": "Claude",
  "protocol": "ANTHROPIC",
  "endpointUrl": "https://api.anthropic.com",
  "apiKey": "sk-ant-xxxxx",
  "model": "claude-sonnet-4-6"
}
```

Response: созданный провайдер (`hasApiKey: true`, apiKey не возвращается).

### PUT /api/v1/providers/{id}
Обновить провайдер.

Request: как POST. Для обновления apiKey — передать новое значение. Для сохранения текущего — не передавать поле.

### DELETE /api/v1/providers/{id}
Удалить провайдер (только если не используется классификаторами/промптами).

Response: `204 No Content`

### POST /api/v1/providers/{id}/test
Тест соединения с провайдером.

Response:
```json
{
  "success": true,
  "responsePreview": "Hello! I'm Claude...",
  "tokensUsed": 45,
  "durationMs": 800,
  "error": null
}
```

или
```json
{
  "success": false,
  "responsePreview": null,
  "tokensUsed": 0,
  "durationMs": 10000,
  "error": "Connection timeout after 10s"
}
```

---

## 9. Промпты

### GET /api/v1/prompts
Список промптов.

Query params: `type` (CLASSIFIER, GUIDE_GENERATOR)

Response:
```json
[
  {
    "id": 3,
    "name": "Гайд-экстрактор",
    "type": "GUIDE_GENERATOR",
    "version": 3,
    "content": "Извлеки из следующих сообщений пошаговую инструкцию...",
    "variables": ["messages", "group_name"],
    "status": "ACTIVE",
    "linkedClassifiers": ["Инструкция-детектор v2"],
    "linkedRules": 3,
    "createdAt": "2026-05-01T08:00:00Z",
    "updatedAt": "2026-06-01T15:00:00Z"
  }
]
```

PromptType enum: `CLASSIFIER`, `GUIDE_GENERATOR`

### POST /api/v1/prompts
Создать промпт.

Request:
```json
{
  "name": "Гайд-экстрактор",
  "type": "GUIDE_GENERATOR",
  "content": "Извлеки из следующих сообщений пошаговую инструкцию...",
  "status": "DRAFT"
}
```

Response: созданный промпт с `version: 1`.

### PUT /api/v1/prompts/{id}
Обновить промпт (создаёт новую версию).

Request: как POST. Если `content` изменён — `version` инкрементируется.

Response: обновлённый промпт с новой версией.

### POST /api/v1/prompts/{id}/test
Тест-режим: прогнать промпт с выбранными данными через провайдер.

Request:
```json
{
  "providerId": 1,
  "chainSource": "EXISTING",
  "messageId": 1,
  "customMessages": null
}
```

или

```json
{
  "providerId": 1,
  "chainSource": "CUSTOM",
  "messageId": null,
  "customMessages": "Alex: Вот способ зайти...\nMaria: Да, работает..."
}
```

Response:
```json
{
  "fullPrompt": "Извлеки из следующих сообщений...\n\n---\n[Alex]: Вот способ зайти...",
  "rawResponse": "{ \"title\": \"VPN подключение\", \"steps\": [...] }",
  "tokensUsed": {
    "prompt": 890,
    "completion": 340,
    "total": 1230
  },
  "durationMs": 3200,
  "providerName": "Claude",
  "model": "claude-sonnet-4-6"
}
```

ChainSource enum: `EXISTING` (использовать существующую цепочку по messageId), `CUSTOM` (ввести текст вручную)

### DELETE /api/v1/prompts/{id}
Удалить промпт.

Response: `204 No Content`

---

## 10. Мониторинг — Токены

### GET /api/v1/monitor/tokens/summary
Сводка по токенам за сегодня.

Response:
```json
{
  "todayTotal": 47200,
  "todayCostUsd": 12.40,
  "dailyLimitPercent": 63,
  "dailyLimit": 75000,
  "monthlyLimitPercent": 28,
  "monthlyLimit": 2000000,
  "monthlyTotal": 560000
}
```

### GET /api/v1/monitor/tokens/daily
Токены по дням.

Query params: `from`, `to` (ISO date, default: 30 дней назад — сегодня)

Response:
```json
[
  {
    "date": "2026-06-05",
    "classifierTokens": 12100,
    "generatorTokens": 35100,
    "totalTokens": 47200,
    "costUsd": 12.40
  }
]
```

### GET /api/v1/monitor/tokens/by-provider
Токены по провайдерам.

Query params: `from`, `to`

Response:
```json
[
  {
    "providerId": 1,
    "providerName": "Claude",
    "classifierTokens": 12100,
    "generatorTokens": 35100,
    "totalTokens": 47200,
    "costUsd": 12.40
  },
  {
    "providerId": 2,
    "providerName": "GPT-4",
    "classifierTokens": 8300,
    "generatorTokens": 14800,
    "totalTokens": 23100,
    "costUsd": 8.20
  }
]
```

### GET /api/v1/monitor/tokens/export
Экспорт статистики токенов в CSV.

Query params: `from`, `to`

Response: `Content-Type: text/csv`, файл с колонками:
`date,provider,classifier_tokens,generator_tokens,total_tokens,cost_usd`

---

## 11. Мониторинг — Очередь

### GET /api/v1/monitor/queue/status
Текущее состояние очереди.

Response:
```json
{
  "queueSize": 142,
  "processingRate": 8.3,
  "stuckCount": 3,
  "isPaused": false
}
```

### GET /api/v1/monitor/queue/history
История размера очереди за 24 часа.

Query params: `hours` (int, default 24)

Response:
```json
[
  {
    "timestamp": "2026-06-05T10:00:00Z",
    "queueSize": 120,
    "processingRate": 7.5
  }
]
```

### GET /api/v1/monitor/queue/stuck
Список зависших задач.

Response:
```json
[
  {
    "id": 1,
    "groupId": 1,
    "groupTitle": "VPN Обсуждение",
    "messageId": 1,
    "processingSince": "2026-06-05T09:30:00Z",
    "classifierId": 2,
    "classifierName": "Инструкция-детектор v2"
  }
]
```

### POST /api/v1/monitor/queue/pause
Поставить очередь на паузу.

Response: `{"paused": true}`

### POST /api/v1/monitor/queue/resume
Возобновить очередь.

Response: `{"paused": false}`

### DELETE /api/v1/monitor/queue/clear
Очистить очередь (не трогая текущие в обработке).

Response: `{"cleared": 142}`

---

## 12. Мониторинг — Статистика групп

### GET /api/v1/monitor/groups/stats
Статистика по группам.

Query params: `sort` (messages, llmSent, guidesFound, conversionRate), `direction` (asc, desc), `from`, `to`

Response:
```json
[
  {
    "groupId": 1,
    "groupTitle": "VPN Обсуждение",
    "messagesRead": 2140,
    "llmSent": 89,
    "guidesFound": 12,
    "conversionRate": 13.5
  }
]
```

### GET /api/v1/monitor/groups/top-messages
Топ групп по сообщениям (для BarChart).

Query params: `limit` (default 10)

Response:
```json
[
  {
    "groupId": 1,
    "groupTitle": "VPN Обсуждение",
    "messagesRead": 2140
  }
]
```

### GET /api/v1/monitor/groups/guides-by-group
Гайды за 7 дней по группам (для Stacked BarChart).

Query params: `days` (default 7)

Response:
```json
[
  {
    "date": "2026-06-05",
    "groups": [
      { "groupTitle": "VPN Обсуждение", "count": 3 },
      { "groupTitle": "IT Инструкции", "count": 2 }
    ]
  }
]
```

---

## 13. Настройки

### GET /api/v1/settings
Текущие настройки.

Response:
```json
{
  "publication": {
    "targetGroupId": 5,
    "targetGroupTitle": "NIG Гайды",
    "mode": "WITH_MODERATION"
  },
  "processing": {
    "mode": "NEW_ONLY",
    "pollIntervalSeconds": 30
  },
  "filters": {
    "blacklistWords": ["спам", "реклама", "куплю", "продам"],
    "skipBots": true,
    "minMessageLength": 20
  },
  "limits": {
    "dailyTokenLimit": 75000,
    "monthlyTokenLimit": 2000000,
    "alertThresholdPercent": 80
  },
  "notifications": {
    "telegramChat": "@neuroinfogrinder_alerts",
    "webhookUrl": "https://hooks.example.com/nig",
    "events": {
      "tokenLimitExceeded": true,
      "queueStuck": true,
      "accountDisconnected": true,
      "newGuide": false,
      "duplicateGuide": false
    }
  }
}
```

PublicationMode enum: `AUTOMATIC`, `WITH_MODERATION`
ProcessingMode enum: `NEW_ONLY`, `FULL_BACKFILL`

### PUT /api/v1/settings
Обновить настройки (полная замена).

Request: как GET response.

Response: обновлённые настройки.

### PATCH /api/v1/settings/{section}
Обновить секцию настроек (publication, processing, filters, limits, notifications).

Request: только нужная секция.

Response: полные обновлённые настройки.

---

## 14. Существующие endpoint'ы (оставить как есть)

```
GET  /api/v1/system/info
GET  /api/v1/telegram/auth/state
POST /api/v1/telegram/auth/phone
POST /api/v1/telegram/auth/code
POST /api/v1/telegram/auth/password
GET  /api/v1/telegram/chats
GET  /api/v1/telegram/chats/{chatId}/topics
GET  /api/v1/telegram/chats/{chatId}/messages
POST /api/v1/telegram/messages/batch
```

Эти endpoint'ы используются для прямой работы с TDLib. Новые `/api/v1/accounts` оборачивают TDLib auth и добавляют персистентность, прокси, мульти-аккаунт. Старые `/api/v1/telegram/*` остаются для низкоуровневого доступа.

---

## Сводная таблица endpoint'ов

| Метод | Путь | Описание |
|-------|------|----------|
| GET | /api/v1/accounts | Список аккаунтов |
| POST | /api/v1/accounts | Подключить аккаунт (шаг 1) |
| POST | /api/v1/accounts/{id}/code | Ввести код (шаг 2) |
| POST | /api/v1/accounts/{id}/password | Ввести 2FA (шаг 3) |
| PATCH | /api/v1/accounts/{id}/proxy | Настроить прокси |
| POST | /api/v1/accounts/{id}/reconnect | Переподключить |
| DELETE | /api/v1/accounts/{id} | Удалить аккаунт |
| GET | /api/v1/groups | Список групп |
| POST | /api/v1/groups/sync | Синхронизировать из Telegram |
| PATCH | /api/v1/groups/{id} | Обновить группу |
| POST | /api/v1/groups/bulk-toggle | Массово вкл/выкл |
| POST | /api/v1/groups/bulk-assign | Массово сменить аккаунт |
| GET | /api/v1/groups/{groupId}/messages | Сообщения группы |
| GET | /api/v1/groups/{groupId}/messages/{messageId}/chain | Цепочка сообщений |
| POST | /api/v1/groups/{groupId}/messages/{messageId}/enqueue | Отправить в обработку |
| GET | /api/v1/classifiers | Список классификаторов |
| POST | /api/v1/classifiers | Создать классификатор |
| PUT | /api/v1/classifiers/{id} | Обновить классификатор |
| PATCH | /api/v1/classifiers/{id}/status | Изменить статус |
| DELETE | /api/v1/classifiers/{id} | Удалить |
| GET | /api/v1/rules | Список правил |
| POST | /api/v1/rules | Создать правило |
| PUT | /api/v1/rules/{id} | Обновить правило |
| PATCH | /api/v1/rules/reorder | Переставить порядок |
| DELETE | /api/v1/rules/{id} | Удалить |
| GET | /api/v1/chain-config | Конфигурация цепочек |
| PUT | /api/v1/chain-config | Обновить конфигурацию |
| GET | /api/v1/guides | Список гайдов |
| GET | /api/v1/guides/{id} | Полный гайд |
| PATCH | /api/v1/guides/{id}/status | Изменить статус |
| PUT | /api/v1/guides/{id}/content | Редактировать текст |
| POST | /api/v1/guides/{id}/mark-not-duplicate | Не дубликат |
| DELETE | /api/v1/guides/{id} | Удалить |
| GET | /api/v1/providers | Список провайдеров |
| POST | /api/v1/providers | Создать провайдер |
| PUT | /api/v1/providers/{id} | Обновить провайдер |
| DELETE | /api/v1/providers/{id} | Удалить |
| POST | /api/v1/providers/{id}/test | Тест соединения |
| GET | /api/v1/prompts | Список промптов |
| POST | /api/v1/prompts | Создать промпт |
| PUT | /api/v1/prompts/{id} | Обновить промпт |
| POST | /api/v1/prompts/{id}/test | Тест-режим |
| DELETE | /api/v1/prompts/{id} | Удалить |
| GET | /api/v1/monitor/tokens/summary | Сводка токенов |
| GET | /api/v1/monitor/tokens/daily | Токены по дням |
| GET | /api/v1/monitor/tokens/by-provider | Токены по провайдерам |
| GET | /api/v1/monitor/tokens/export | Экспорт CSV |
| GET | /api/v1/monitor/queue/status | Состояние очереди |
| GET | /api/v1/monitor/queue/history | История очереди |
| GET | /api/v1/monitor/queue/stuck | Зависшие задачи |
| POST | /api/v1/monitor/queue/pause | Пауза |
| POST | /api/v1/monitor/queue/resume | Возобновить |
| DELETE | /api/v1/monitor/queue/clear | Очистить |
| GET | /api/v1/monitor/groups/stats | Статистика групп |
| GET | /api/v1/monitor/groups/top-messages | Топ по сообщениям |
| GET | /api/v1/monitor/groups/guides-by-group | Гайды по группам |
| GET | /api/v1/settings | Настройки |
| PUT | /api/v1/settings | Обновить все настройки |
| PATCH | /api/v1/settings/{section} | Обновить секцию |
