#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
docker compose up -d --wait postgres
printf '%s\n' 'PostgreSQL is healthy. Starting CampusNav Phase 5 application...'
CAMPUSNAV_STORAGE=postgres exec /bin/sh scripts/api.sh
