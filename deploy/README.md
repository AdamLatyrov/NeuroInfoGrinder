# NeuroInfoGrinder deploy

## Что это

Минимальный воспроизводимый deploy для слабого VPS:

- `backend/Dockerfile`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `docker-compose.prod.yml`
- `.env.example`

Схема:

- `frontend` на `nginx` отдаёт статику и проксирует `/api`;
- `backend` — Spring Boot + Flyway + TDLib JNI;
- `postgres` и `redis` живут внутри `docker compose`;
- наружу открыт только `80`, backend опубликован только на `127.0.0.1:8080`.

## Требования

- Ubuntu VPS
- Docker + Docker Compose plugin
- минимум `2 GB RAM`
- желательно `2 GB swap`
- внешний AI provider
- TDLib API credentials (`TDLIB_API_ID`, `TDLIB_API_HASH`)

## First setup

```bash
apt update
apt install -y git curl wget unzip htop nano ufw ca-certificates gnupg lsb-release
```

### Firewall

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 8080/tcp
ufw --force enable
ufw status
```

### Swap

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=20' > /etc/sysctl.d/99-neuroinfogrinder.conf
sysctl --system
free -h
```

### Docker

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable docker
systemctl start docker
docker --version
docker compose version
```

## Clone from GitHub

```bash
mkdir -p /opt/neuroinfogrinder
cd /opt/neuroinfogrinder
git clone https://github.com/AdamLatyrov/NeuroInfoGrinder.git app
cd app
git status
git branch --show-current
git log --oneline -5
```

## Env

```bash
cp .env.example .env
nano .env
```

Заполнить обязательно:

- `DB_PASSWORD`
- `JWT_SECRET`
- `TDLIB_API_ID`
- `TDLIB_API_HASH`
- `TDLIB_DATABASE_ENCRYPTION_KEY`
- ключи/настройки AI provider

Для слабого VPS оставляйте conservative values:

- `JAVA_OPTS=-Xms256m -Xmx768m`
- `TDLIB_SYNC_MAX_PARALLELISM=1`
- `TDLIB_SYNC_BATCH_SIZE=1`
- `TDLIB_SYNC_QUEUE_CAPACITY=2`
- `TDLIB_SYNC_FIXED_DELAY_MS=60000`
- `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`
- `PIPELINE_TRACE_RETENTION_DAYS=45`

## Start

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f --tail=200
```

Если build на VPS падает по памяти — не долбить бесконечно пересборку. Собирать image локально/в CI и выкатывать уже готовый artifact.

## Команды

### Start

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

### Stop

```bash
docker compose -f docker-compose.prod.yml --env-file .env down
```

### Restart

```bash
docker compose -f docker-compose.prod.yml --env-file .env restart backend
docker compose -f docker-compose.prod.yml --env-file .env restart frontend
```

### Logs

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f backend
docker compose -f docker-compose.prod.yml --env-file .env logs -f frontend
docker compose -f docker-compose.prod.yml --env-file .env logs -f postgres
```

### Update from GitHub

```bash
cd /opt/neuroinfogrinder/app
git pull --ff-only
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

### Check health

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -I http://127.0.0.1/
docker compose -f docker-compose.prod.yml --env-file .env ps
```

### Postgres backup

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec -T postgres \
  pg_dump -U "$DB_USERNAME" -d "$DB_NAME" > backup-$(date +%F-%H%M%S).sql
```

## Runtime validation

Проверить после старта:

1. открывается frontend по IP;
2. `curl http://127.0.0.1:8080/actuator/health` возвращает `UP`;
3. логин работает;
4. страница AI providers открывается;
5. TDLib listener стартует;
6. включена только 1 тестовая группа;
7. новое Telegram-сообщение доходит до UI/DB;
8. pipeline/classification работают;
9. нет Hikari saturation;
10. нет restart loop.

## Мониторинг

```bash
cd /opt/neuroinfogrinder/app
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f backend
docker stats
free -h
df -h
```

Логи backend и TDLib пишутся в volume `/app/logs` внутри контейнера backend.

## Что не делать на этом VPS

- не включать много групп;
- не запускать большой history backfill;
- не поднимать Kafka;
- не запускать локальные LLM;
- не открывать наружу Postgres/Redis;
- не хранить `.env` в git.

## TDLib JNI

`backend/Dockerfile` собирает `libtdjni.so` из исходников TDLib на commit,
который совпадает с Java bindings в `backend/src/main/java/org/drinkless/tdlib`.
По умолчанию используется:

- `TDLIB_GIT_COMMIT=e0943d068ce90b5010f1aea946e6901e25b43bf6`
- `TDLIB_BUILD_JOBS=4`

Не подменяйте TDLib binary на готовый release без обновления `TdApi.java`: JVM
завершится с ошибкой несовпадения native/Java версий.
