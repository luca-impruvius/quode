#!/bin/bash
# Restore test (M0-12, docs/architecture.md §8): restores a dump into a throwaway postgres:18
# container and prints what it holds. Never touches the production database.
# Run on the server from ~/quode: backup/restore-test.sh [file.dump]
#   no argument: downloads the newest dump from R2 first.
set -euo pipefail

self=$(readlink -f "$0")
cd "$(dirname "$self")/.."

RCLONE_IMAGE=rclone/rclone:1.71.0
SCRATCH=quode-restore-test

log() { echo "restore-test: $*"; }
die() {
  echo "restore-test: error: $*" >&2
  exit 1
}

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-; }

workdir=$(mktemp -d)
cleanup() {
  docker rm -f "$SCRATCH" >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

if [ $# -ge 1 ]; then
  [ -f "$1" ] || die "no such file: $1"
  cp "$1" "$workdir/latest.dump"
else
  export RCLONE_CONFIG_R2_TYPE=s3
  export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
  export RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY
  RCLONE_CONFIG_R2_ENDPOINT=$(env_value R2_ENDPOINT)
  RCLONE_CONFIG_R2_ACCESS_KEY_ID=$(env_value R2_ACCESS_KEY_ID)
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=$(env_value R2_SECRET_ACCESS_KEY)
  bucket=$(env_value R2_BUCKET)
  rclone() {
    docker run --rm -v "$workdir:/dump" \
      -e RCLONE_CONFIG_R2_TYPE -e RCLONE_CONFIG_R2_PROVIDER -e RCLONE_CONFIG_R2_ENDPOINT \
      -e RCLONE_CONFIG_R2_ACCESS_KEY_ID -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY \
      "$RCLONE_IMAGE" "$@"
  }
  # Names sort by time (quode-<UTC timestamp>.dump), so the last one is the newest.
  newest=$(rclone lsf "r2:$bucket" --files-only --include 'quode-*.dump' | sort | tail -n 1)
  [ -n "$newest" ] || die "no dumps in the bucket"
  log "downloading $newest"
  rclone copyto "r2:$bucket/$newest" /dump/latest.dump
fi

log "starting a scratch postgres:18"
docker run -d --name "$SCRATCH" -e POSTGRES_PASSWORD=scratch -e POSTGRES_DB=quode \
  -v "$workdir:/dump:ro" postgres:18 >/dev/null
for _ in $(seq 1 30); do
  docker exec "$SCRATCH" pg_isready -U postgres -d quode >/dev/null 2>&1 && break
  sleep 2
done

log "restoring"
# Roles and grants (quode, quode_readonly) belong to the production server, not to the dump's data.
docker exec "$SCRATCH" pg_restore -U postgres -d quode --no-owner --no-privileges \
  --exit-on-error /dump/latest.dump

log "what it holds:"
docker exec "$SCRATCH" psql -U postgres -d quode -v ON_ERROR_STOP=1 -c "
  select (select max(version::int) from flyway_schema_history where success) as migration,
         (select count(*) from app_user) as users,
         (select count(*) from topic) as topics,
         (select count(*) from question) as questions,
         (select count(*) from attempt) as attempts,
         (select count(*) from review_state) as review_states"
log "ok: the dump restores"
