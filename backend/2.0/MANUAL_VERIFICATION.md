# Manual Telegram ingest verification

Эта проверка нужна, потому что реальный Telegram auth нельзя надежно автоматизировать без локальных TDLib credentials, native library и одноразового кода Telegram.

Не выводи и не коммить реальные значения TDLib/API/env.

## 1. Local Postgres

```powershell
cd C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\backend\2.0
docker compose -f docker-compose.local.yml up -d postgres
```

## 2. Backend 2.0

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
$env:TDLIB_ENABLED='true'
$env:TDLIB_API_ID='<local-api-id>'
$env:TDLIB_API_HASH='<local-api-hash>'
$env:TDLIB_LIBRARY_PATH='<path-to-tdjni>'
$env:TDLIB_DATABASE_ENCRYPTION_KEY='<local-encryption-key>'
mvn spring-boot:run
```

Acceptance:

- `GET http://127.0.0.1:8080/api/v1/system/info` отвечает.
- `GET http://127.0.0.1:8080/api/v2/telegram/health` отвечает.
- Flyway migration проходит без ошибок.

## 3. Frontend

```powershell
cd C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\frontend
npm run dev
```

Acceptance:

- Login работает через backend 2.0.
- Accounts/Groups/Messages открываются без ошибок API.

## 4. Telegram auth

Через UI или curl:

- Создать account с phone.
- Отправить code.
- Отправить password, если Telegram запросит 2FA.
- Проверить `GET /api/v1/telegram/auth/state?accountId=<id>`.
- Проверить `GET /api/v2/telegram/accounts/<id>/health`.

Acceptance:

- Account status становится `CONNECTED`.
- В health есть auth state и TDLib database path без секретов.

## 5. Real chats

- Вызвать `GET /api/v1/telegram/chats?accountId=<id>`.
- Вызвать `POST /api/v1/groups/sync?accountId=<id>`.
- Открыть Groups page.

Acceptance:

- Реальные Telegram chats сохранены в `telegram_chats`.
- В UI видны title, telegramChatId, type, enabled status.
- `pipeline_events` содержит `CHAT_SYNCED`.

## 6. Live message

- Включить monitoring для выбранного чата.
- Открыть frontend messages view для этого чата.
- Отправить новое сообщение в реальный Telegram-чат.

Acceptance:

- В `tdlib_update_inbox` появляется `updateNewMessage`.
- В `raw_messages` появляется одна запись по `(account_id, telegram_chat_id, telegram_message_id)`.
- В `pipeline_events` есть `TDLIB_UPDATE_RECEIVED`, `RAW_MESSAGE_PERSISTED`, `LINKS_EXTRACTED`, `MESSAGE_INGESTED`.
- SSE `/api/v1/pipeline/events/stream` отправляет `MESSAGE_INGESTED`.
- Frontend показывает сообщение после SSE invalidation/refetch.

## 7. Hidden hyperlinks

- Отправить Telegram-сообщение со скрытой ссылкой, например anchor `тут` с real URL.
- Открыть message detail.
- Проверить `GET /api/v2/messages/<rawMessageId>/links`.

Acceptance:

- `message_links.url` содержит real URL.
- `anchor_text` содержит видимый anchor.
- `is_hidden = true`.
- `source = TEXT` или `CAPTION`.
- `domain` заполнен.

## 8. Backfill

- Вызвать `POST /api/v1/groups/<groupId>/messages/sync`.

Acceptance:

- Последние сообщения сохраняются в `raw_messages`.
- Повторный backfill не создает дубликаты.
- Live listener после backfill продолжает принимать новые сообщения.
- `telegram_chat_health.backfill_status` становится `COMPLETED` или `FAILED`.
