#!/bin/bash
# Nightly database backup (M0-12, ADR-0036). Lives in ~/quode/backup/ on the server; cron runs it
# (see crontab next to it). Settings come from ~/quode/.env (see .env.example).
#
# 1. pg_dump in the custom format (compressed) from the postgres container.
# 2. Refuses a dump over 500 MB: it alerts instead of uploading (R2 free-tier guard).
# 3. Checks that pg_restore can read the archive.
# 4. Uploads it to the R2 bucket, where a lifecycle rule deletes objects after 14 days.
# 5. Pings healthchecks.io: /start, then success or /fail with the reason. No ping = an alert.
#
# Never prints secrets: the R2 keys reach rclone as environment variables, not arguments.
set -euo pipefail

self=$(readlink -f "$0")
cd "$(dirname "$self")/.."

MAX_BYTES=${QUODE_BACKUP_MAX_BYTES:-524288000}
RCLONE_IMAGE=rclone/rclone:1.71.0

log() { echo "backup: $(date -u +%Y-%m-%dT%H:%M:%SZ) $*"; }

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-; }

# $1: "" (success), /start or /fail; $2: a message for the check's log (never a secret).
ping_check() {
  local url
  url=$(env_value HC_BACKUP_URL)
  if [ -z "$url" ]; then
    log "HC_BACKUP_URL is not set: no ping"
    return 0
  fi
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw "${2:-}" "$url$1" || log "ping $1 failed"
}

fail() {
  log "error: $*"
  ping_check /fail "$*"
  exit 1
}

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

ping_check /start
name="quode-$(date -u +%Y-%m-%dT%H%M%SZ).dump"
dump="$workdir/$name"

log "dumping to $name"
docker compose exec -T postgres pg_dump -U "$(env_value DB_USER)" -d quode --format=custom \
  --compress=9 >"$dump" || fail "pg_dump failed"

size=$(wc -c <"$dump" | tr -d ' ')
[ "$size" -gt 0 ] || fail "the dump is empty"
[ "$size" -le "$MAX_BYTES" ] ||
  fail "the dump is $size bytes, over the $MAX_BYTES limit: not uploaded"

# pg_restore needs a seekable file, so the check reads a copy inside the container.
docker compose exec -T postgres sh -c \
  'f=$(mktemp) && cat >"$f" && pg_restore --list "$f" >/dev/null; s=$?; rm -f "$f"; exit $s' \
  <"$dump" || fail "pg_restore can't read the dump"

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY
RCLONE_CONFIG_R2_ENDPOINT=$(env_value R2_ENDPOINT)
RCLONE_CONFIG_R2_ACCESS_KEY_ID=$(env_value R2_ACCESS_KEY_ID)
RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=$(env_value R2_SECRET_ACCESS_KEY)
bucket=$(env_value R2_BUCKET)
[ -n "$bucket" ] && [ -n "$RCLONE_CONFIG_R2_ENDPOINT" ] || fail "R2 settings are missing in .env"

log "uploading $size bytes"
# The token can only reach its bucket, so rclone must not try to create or list buckets.
docker run --rm -v "$workdir:/dump:ro" \
  -e RCLONE_CONFIG_R2_TYPE -e RCLONE_CONFIG_R2_PROVIDER -e RCLONE_CONFIG_R2_ENDPOINT \
  -e RCLONE_CONFIG_R2_ACCESS_KEY_ID -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY \
  "$RCLONE_IMAGE" copyto "/dump/$name" "r2:$bucket/$name" --s3-no-check-bucket ||
  fail "the upload to R2 failed"

log "done: $name"
ping_check "" "uploaded $name ($size bytes)"
