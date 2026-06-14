# NeuroInfoGrinder

Чистая стартовая структура для новой ручной разработки.

## Структура

```text
backend/      Java backend на Spring Boot
frontend/     отдельный frontend
legacy-mvp/   старый Python MVP / reference source
docs/         общие заметки и спецификации
```

## Как использовать

### `backend/`

- основной новый backend-проект
- открывайте эту папку в IntelliJ IDEA
- внутри уже есть Maven/Spring Boot skeleton

### `frontend/`

- отдельный frontend-проект
- живёт независимо от backend

### `legacy-mvp/`

- старый код, из которого можно срисовывать логику
- не считается новой целевой архитектурой
- нужен как reference при ручной переписке

## Целевой режим работы

- `backend/` и `frontend/` развиваются отдельно
- взаимодействие идёт через HTTP API
- `legacy-mvp/` используется только как источник поведения и бизнес-логики
