# Fast deploy на production VPS

Production:

- VPS: `185.130.212.188`
- app dir: `/srv/neuroinfogrinder/app`
- compose: `/srv/neuroinfogrinder/app/docker-compose.prod.yml`
- public URL: `https://neuroinfogrinder.latrdev.ru/`

Секреты лежат на сервере в `.env`; fast deploy их не читает, не копирует и не печатает.

## Что замедляло деплой

Текущий production `backend/Dockerfile` собирает TDLib из исходников внутри backend image build:

- `git fetch` фиксированного TDLib commit
- `cmake --build` TDLib
- сборка Java/JNI binding и `libtdjni.so`
- затем Maven package backend jar

На сервере `/srv/neuroinfogrinder/app` сейчас не git worktree, а каталог с вручную загруженными файлами. Поэтому `git pull` как основной deploy path небезопасен и не работает без отдельной миграции. Fast-файлы (`docker-compose.fast.yml`, `backend/Dockerfile.fast`, `deploy/tdlib/Dockerfile`) должны быть загружены перед первым быстрым backend deploy.

## Выбранный безопасный путь

По умолчанию используется `scp`-based deploy:

- локально создаётся временный `.tar.gz` только с нужными файлами;
- архив загружается в `/tmp` на VPS;
- файлы распаковываются в `/srv/neuroinfogrinder/app`;
- `.env`, volume data, Postgres, Redis, tor и ssh-socks не копируются и не перезапускаются;
- старые файлы на сервере не удаляются автоматически.

Это безопаснее, чем превращать текущий серверный каталог в git repo вслепую. Минус: если файл был переименован или удалён локально, старая копия на сервере может остаться. Для таких случаев нужен отдельный ручной cleanup или аккуратная миграция сервера на git worktree.

## One-time TDLib base image

Один раз собрать cached TDLib image:

```powershell
.\scripts\deploy-fast.ps1 -Target tdlib
```

Будет затронут только Docker image `neuroinfogrinder-tdlib:<commit>`. App containers не перезапускаются.

## Frontend-only deploy

```powershell
.\scripts\deploy-fast.ps1 -Target frontend
```

Что делает:

- загружает frontend files и fast deploy support files;
- собирает только `frontend`;
- выполняет `docker compose up -d --no-deps frontend`;
- ждёт Docker health status `frontend`.

Не трогает backend, Postgres, Redis, tor, ssh-socks.

## Backend-only deploy

```powershell
.\scripts\deploy-fast.ps1 -Target backend
```

Что делает:

- загружает backend files и fast deploy support files;
- при необходимости один раз собирает `neuroinfogrinder-tdlib:<commit>`;
- собирает backend через `backend/Dockerfile.fast`;
- выполняет `docker compose up -d --no-deps backend`;
- ждёт Docker health status `backend`.

Не перезапускает frontend, Postgres, Redis, tor, ssh-socks.

## App-only full deploy

Использовать только когда одновременно менялись backend и frontend:

```powershell
.\scripts\deploy-fast.ps1 -Target all
```

Это full deploy только для app services: `backend` и `frontend`. Infra services и volumes не трогаются.

Не использовать для routine deploy:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Эта команда может пересобирать и затрагивать больше сервисов, чем нужно.

## Healthcheck/status

Read-only проверка статуса:

```powershell
.\scripts\deploy-fast.ps1 -Target status
```

Ручные проверки на VPS:

```bash
cd /srv/neuroinfogrinder/app
docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml --env-file .env ps
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8788/
curl -fsS https://neuroinfogrinder.latrdev.ru/
```

Если `docker-compose.fast.yml` ещё не загружен, для `ps` можно временно использовать только `docker-compose.prod.yml`.

## Restart без rebuild

```powershell
.\scripts\deploy-fast.ps1 -Target restart-backend
.\scripts\deploy-fast.ps1 -Target restart-frontend
```

Перезапускается только выбранный app-service.

## Rollback

Перед каждым fast deploy скрипт тегирует текущий app image:

- `neuroinfogrinder-backend:rollback`
- `neuroinfogrinder-frontend:rollback`

Откатить последний backend или frontend image:

```powershell
.\scripts\deploy-fast.ps1 -Target rollback-backend
.\scripts\deploy-fast.ps1 -Target rollback-frontend
```

Rollback пересоздаёт только выбранный app container через `up -d --no-deps --force-recreate`.

## Альтернатива: git worktree на VPS

Вариант с git worktree можно сделать позже, но только отдельной аккуратной миграцией:

1. сохранить backup текущего `/srv/neuroinfogrinder/app`;
2. проверить, какие серверные файлы отличаются от репозитория;
3. перенести `.env` и серверные secrets вне git;
4. только после этого переключить deploy на `git pull --ff-only`.

До такой миграции safest default: `.\scripts\deploy-fast.ps1 -Target backend|frontend|all` с `-SyncMode scp`.
