#!/usr/bin/env bash
set -euo pipefail

cd /srv/neuroinfogrinder/app
COMPOSE='docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml -f docker-compose.backend2-clean.yml --env-file .env'

tr -d '\r' < /tmp/select-materials-valid-json.sql > /tmp/select-materials-valid-json.lf.sql
tr -d '\r' < /tmp/select-run-summary-valid-json.sql > /tmp/select-run-summary-valid-json.lf.sql
docker cp /tmp/select-materials-valid-json.lf.sql neuroinfogrinder-postgres-1:/tmp/select-materials-valid-json.sql
docker cp /tmp/select-run-summary-valid-json.lf.sql neuroinfogrinder-postgres-1:/tmp/select-run-summary-valid-json.sql
$COMPOSE exec -T postgres sh -lc 'psql -AtX -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d neuroinfogrinder2_prod_clean -f /tmp/select-materials-valid-json.sql' > /tmp/full-pipeline-20k-materials-valid-20260701.json
$COMPOSE exec -T postgres sh -lc 'psql -AtX -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d neuroinfogrinder2_prod_clean -f /tmp/select-run-summary-valid-json.sql' > /tmp/full-pipeline-20k-run-summary-valid-20260701.json
