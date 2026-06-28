# NeuroInfoGrinder backend 2.0 local stage 1

Локальный backend 2.0 хранит raw Telegram messages, media metadata, links и pipeline events. Production не используется.

## Local Postgres

```powershell
cd C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\backend\2.0
docker compose -f docker-compose.local.yml up -d postgres
```

Default local DB:

- host: `127.0.0.1`
- port: `55432`
- database: `neuroinfogrinder2_dev`
- user: `neuroinfogrinder2`
- password: local-only value from `docker-compose.local.yml`

## Backend

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
mvn spring-boot:run
```

For real TDLib auth also set local-only Telegram app credentials and native library path:

```powershell
$env:TDLIB_ENABLED='true'
$env:TDLIB_API_ID='<local-api-id>'
$env:TDLIB_API_HASH='<local-api-hash>'
$env:TDLIB_LIBRARY_PATH='C:\path\to\tdjni.dll'
$env:TDLIB_DATABASE_ENCRYPTION_KEY='<local-encryption-key>'
```

Do not commit real values.

## Local replay

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=import-local-messages --file fixtures/local-messages.jsonl"
```

## Safe reset

Dry run:

```powershell
.\scripts\dev-reset-db.ps1
```

Real local reset:

```powershell
.\scripts\dev-reset-db.ps1 --yes-i-know-this-is-local
```

The reset script refuses production profile, unknown hosts and DB names without local/dev/test markers.
