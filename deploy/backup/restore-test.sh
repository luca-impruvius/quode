#!/bin/bash
# Restore test (M0-12, docs/architecture.md §8): restores a dump into a throwaway postgres:18
# container and prints what it holds. Never touches the production database.
# Run on the server from ~/quode:
#   backup/restore-test.sh [--keep <path>] [file.dump]   (absolute or ~/ paths)
#   no file: downloads the newest dump from R2 first.
#   --keep <path>: once the dump has restored, keeps a copy there (for restoring production).
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

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }

keep=""
if [ "${1:-}" = "--keep" ]; then
  [ -n "${2:-}" ] || die "--keep needs a path"
  keep=$2
  shift 2
fi

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
  if [ -z "$bucket" ] || [ -z "$RCLONE_CONFIG_R2_ENDPOINT" ] ||
    [ -z "$RCLONE_CONFIG_R2_ACCESS_KEY_ID" ] || [ -z "$RCLONE_CONFIG_R2_SECRET_ACCESS_KEY" ]; then
    die "R2 settings are missing in .env"
  fi
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
# A container left behind by a killed run would block the name.
docker rm -f "$SCRATCH" >/dev/null 2>&1 || true
# Capped: it shares a 4 GB server with production.
docker run -d --name "$SCRATCH" --memory 512m -e POSTGRES_PASSWORD=scratch -e POSTGRES_DB=quode \
  -v "$workdir:/dump:ro" postgres:18 >/dev/null
# Over TCP: the image's temporary init server listens only on the socket, so this waits for the
# real server.
ready=no
for _ in $(seq 1 30); do
  if docker exec "$SCRATCH" pg_isready -h 127.0.0.1 -U postgres -d quode >/dev/null 2>&1; then
    ready=yes
    break
  fi
  sleep 2
done
[ "$ready" = yes ] || die "the scratch database didn't start within 60 seconds"

log "restoring"
# Roles and grants (quode, quode_readonly) belong to the production server, not to the dump's data.
docker exec "$SCRATCH" pg_restore -U postgres -d quode --no-owner --no-privileges \
  --single-transaction --exit-on-error /dump/latest.dump

log "what it holds (compare with production, deploy/README.md):"
docker exec -i "$SCRATCH" psql -U postgres -d quode -v ON_ERROR_STOP=1 -f - <"$(dirname "$self")/counts.sql"

if [ -n "$keep" ]; then
  cp "$workdir/latest.dump" "$keep"
  log "kept a copy at $keep"
fi
log "ok: the dump restores"
