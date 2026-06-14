# Frontend / Backend Runtime Spec

## Цель

Этот документ фиксирует целевую схему взаимодействия между:

- `frontend` — отдельный frontend на React + TypeScript + Vite
- `backend` — отдельный Java backend на Spring Boot

Оба модуля запускаются независимо и общаются по HTTP API.

## Модули

### `frontend`

- отвечает только за UI, навигацию, формы и отображение данных
- не хранит бизнес-логику обработки Telegram/AI
- использует `VITE_API_BASE_URL=/api/v1`
- в локальной разработке работает на `http://localhost:5173`

### `backend`

- отвечает за REST API, безопасность, доменную логику и доступ к данным
- публикует OpenAPI / Swagger
- в локальной разработке работает на `http://localhost:8080`

## Взаимодействие

```text
Browser
  -> frontend (Vite dev server, port 5173)
  -> HTTP /api/v1/*
  -> backend (Spring Boot, port 8080)
  -> DB / Redis / external integrations
```

## Контракт интеграции

Frontend работает только через Java API и не зависит от Python-роутов.

Базовые правила:

1. Все прикладные endpoint’ы публикуются под `/api/v1`
2. Ответы возвращаются в JSON
3. Ошибки возвращаются в едином формате:
   - `code`
   - `message`
   - `details`
   - `timestamp`
4. Контракт backend считается первичным источником для frontend
5. Swagger / OpenAPI используется как живая спецификация интеграции

## Первый набор endpoint’ов

### Уже есть

- `GET /api/v1/system/info`

### Следующие endpoint’ы под MVP

- `GET /api/v1/users`
- `POST /api/v1/users`
- `GET /api/v1/chats`
- `PATCH /api/v1/chats/{chatId}`
- `POST /api/v1/chats/refresh`
- `POST /api/v1/chats/backfill`
- `GET /api/v1/findings`
- `PATCH /api/v1/findings/{findingId}/status`
- `GET /api/v1/questions`
- `GET /api/v1/messages`

## Раздельный запуск локально

### Backend

```bash
mvn -f backend/pom.xml spring-boot:run
```

Backend URLs:

- `http://localhost:8080/api/v1/system/info`
- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:8080/actuator/health`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

## Локальная прокси-схема

В `frontend/vite.config.ts` настроен proxy:

- `/api` -> `http://localhost:8080`
- `/actuator` -> `http://localhost:8080`

Это позволяет frontend вызывать backend без отдельной CORS-настройки на первом этапе локальной разработки.

## Границы ответственности

### Frontend

- layout
- состояние интерфейса
- data fetching
- optimistic updates
- валидация форм на клиенте
- UX вокруг loading / empty / error states

### Backend

- аутентификация и авторизация
- доменные операции
- обработка Telegram workflow
- AI orchestration
- persistence
- аудит, логи, миграции

## Текущее решение по репозиторию

- существующий Python UI считается временным
- новый production-контур строится вокруг `frontend` + `backend`
- Python-часть может оставаться как legacy/reference implementation на время миграции
