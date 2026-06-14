# NeuroInfoGrinder Web

Отдельный frontend-модуль для нового Java backend.

## Стек

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Zod

## Локальный запуск

```bash
npm install
npm run dev
```

По умолчанию приложение работает на:

```text
http://localhost:5173
```

## Интеграция с backend

- frontend вызывает Java backend по `/api/v1`
- для локальной разработки настроен Vite proxy на `http://localhost:8080`
- backend contract описан в `../docs/frontend-backend-spec.md`
