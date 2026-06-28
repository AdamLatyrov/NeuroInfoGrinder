# NeuroInfoGrinder agent instructions

Работай в проекте:

`C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder`

Пользователь Adam говорит по-русски; отвечай по-русски.

## Актуальный контекст для следующего агента

Всегда сначала читай этот файл и затем `_agent/PROJECT_MEMORY.md`. `_agent/PROJECT_MEMORY.md` является живым handoff-файлом: обновляй его после крупных изменений, деплоя, production-проверок и перед завершением сессии.

Текущее направление работы:

- Делаем production-ready UI/UX для инспекции message pipeline: mockup-экраны `Конвейер сообщений`, `Карта кластеров`, `Кластер #1287`, `Embeddings / BGE-M3`, `raw_id 4743`, плюс реальный drawer на `/groups`.
- Последняя активная правка: `frontend/src/pages/pipeline/PipelineMessagesMockupPage.tsx` — funnel должен быть единой встроенной схемой, где тонкая цветная ribbon-лента идёт над compact stage cards, loss badges стоят между этапами, dotted guide lines идут к границам между cards, `LLM Judge` выделен pale blue/blue border, нижняя таблица видна в одном viewport.
- Frontend mockup route: `/pipeline/messages-mockup`.
- Production URL для просмотра: `https://neuroinfogrinder.ru/pipeline/messages-mockup`.
- Локальный backend без Docker Desktop не поднимается, потому local Postgres compose требует Docker. Для просмотра UI чаще используется production backend + frontend deploy.
- Kimi WebBridge может быть нерабочим из-за stale PID; если браузер automation не отвечает, можно открыть URL через `Start-Process`.

Важное незавершённое/частично завершённое:

- Message pipeline inspector drawer на `/groups` частично реализован и задеплоен ранее: read-only backend endpoint `/api/v1/messages/{rawId}/pipeline-detail`, frontend `message-detail-panel`, tests и production API spot-checks. Требуются финальные reports `reports/message-pipeline-drawer-20260626.md/.json` и, если нужно, дополнительная browser-проверка.
- Не продолжай backlog/reprocess/material generation/prompt changes/threshold/provider config без явного запроса Adam.

Быстрые команды локальной проверки:

```powershell
cd C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\frontend
npm run build
```

```powershell
cd C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\backend\2.0
mvn test
```

Открыть текущий production mockup:

```powershell
Start-Process "https://neuroinfogrinder.ru/pipeline/messages-mockup"
```

## Обязательные правила безопасности

- Всегда сначала читай `AGENTS.md`. Если в корне его нет, всё равно найди и прочитай найденные `AGENTS.md`.
- Не печатай секреты, env, API keys, JWT secrets, Telegram secrets, provider keys, пароли.
- Не выводи содержимое `/srv/neuroinfogrinder/app/.env`.
- Не трогай VPN/proxy сервер `95.164.93.173`, кроме read-only проверки, если она реально нужна.
- Не делай прямые write-запросы в production DB без отдельного явного разрешения Adam.
- Не перезапускай Postgres, Redis, `ssh-socks`, tor без необходимости.
- Не делай `docker compose up -d --build` на все сервисы для обычных изменений.
- Если действие потенциально разрушительное, сначала остановись и спроси.

## Production

- VPS: `185.130.212.188`
- App path: `/srv/neuroinfogrinder/app`
- Compose: `/srv/neuroinfogrinder/app/docker-compose.prod.yml`
- Env: `/srv/neuroinfogrinder/app/.env` - не печатать.
- Public URL: `https://neuroinfogrinder.latrdev.ru/`
- SSH key: `C:\Users\Adam\.ssh\neuroinfogrinder_ru_vps`

## Быстрый деплой

Причина долгого backend deploy: обычный `backend/Dockerfile` собирает TDLib из исходников при backend image build. Быстрый путь использует cached TDLib base image `neuroinfogrinder-tdlib:<tdlib_commit>` и `backend/Dockerfile.fast`.

Основная документация: `docs/fast-deploy.md`.

Перед production deploy явно напиши, какие сервисы будут затронуты. Не продолжай, если есть риск затронуть DB, Redis, VPN/proxy, tor или `ssh-socks`.

### Выбранный безопасный путь

По умолчанию используй `scp`-based deploy через:

```powershell
.\scripts\deploy-fast.ps1
```

Не превращай `/srv/neuroinfogrinder/app` в git repo вслепую: серверный каталог сейчас может содержать вручную скопированные файлы. Git worktree на VPS допустим только отдельной аккуратной миграцией с backup и сравнением отличий.

### Команды

Read-only статус и healthcheck:

```powershell
.\scripts\deploy-fast.ps1 -Target status
```

One-time сборка TDLib base image:

```powershell
.\scripts\deploy-fast.ps1 -Target tdlib
```

Frontend-only deploy:

```powershell
.\scripts\deploy-fast.ps1 -Target frontend
```

Backend-only deploy:

```powershell
.\scripts\deploy-fast.ps1 -Target backend
```

App-only full deploy, только когда менялись backend и frontend:

```powershell
.\scripts\deploy-fast.ps1 -Target all
```

Restart без rebuild:

```powershell
.\scripts\deploy-fast.ps1 -Target restart-backend
.\scripts\deploy-fast.ps1 -Target restart-frontend
```

Rollback последнего app image:

```powershell
.\scripts\deploy-fast.ps1 -Target rollback-backend
.\scripts\deploy-fast.ps1 -Target rollback-frontend
```

## Что fast deploy не должен трогать

Routine `frontend`, `backend` и `all` deploy должны использовать `docker compose up -d --no-deps <service>` и не должны перезапускать:

- `postgres`
- `redis`
- `tor`
- `ssh-socks`

`frontend-only` затрагивает только `frontend`.

`backend-only` затрагивает только `backend`; при первом запуске может собрать Docker image `neuroinfogrinder-tdlib:<commit>`, но не должен перезапускать infra services.

## Проверки после деплоя

Используй:

```powershell
.\scripts\deploy-fast.ps1 -Target status
```

Или на VPS read-only:

```bash
cd /srv/neuroinfogrinder/app
docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml --env-file .env ps
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8788/
curl -fsS https://neuroinfogrinder.latrdev.ru/
```

Не печатай `.env` или секретные значения при диагностике.
