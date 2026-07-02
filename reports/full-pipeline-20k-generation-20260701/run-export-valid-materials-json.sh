#!/usr/bin/env bash
set -euo pipefail

cd /srv/neuroinfogrinder/app
COMPOSE='docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml -f docker-compose.backend2-clean.yml --env-file .env'

tr -d '\r' < /tmp/export-valid-materials-json.sql > /tmp/export-valid-materials-json.lf.sql
docker cp /tmp/export-valid-materials-json.lf.sql neuroinfogrinder-postgres-1:/tmp/export-valid-materials-json.sql
$COMPOSE exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d neuroinfogrinder2_prod_clean -f /tmp/export-valid-materials-json.sql'
docker cp neuroinfogrinder-postgres-1:/tmp/full-pipeline-20k-materials-valid-20260701.json /tmp/full-pipeline-20k-materials-valid-20260701.json
docker cp neuroinfogrinder-postgres-1:/tmp/full-pipeline-20k-run-summary-valid-20260701.json /tmp/full-pipeline-20k-run-summary-valid-20260701.json
