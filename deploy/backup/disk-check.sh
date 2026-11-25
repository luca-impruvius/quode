#!/bin/bash
# Daily disk check (M0-12): pings healthchecks.io while the root filesystem is below 80% used,
# and /fail above it. A dead man's switch: no ping at all (cron or server down) alerts too.
# Lives in ~/quode/backup/; cron runs it (see crontab next to it).
set -euo pipefail

self=$(readlink -f "$0")
cd "$(dirname "$self")/.."

LIMIT_PERCENT=80

log() { echo "disk-check: $(date -u +%Y-%m-%dT%H:%M:%SZ) $*"; }

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- || true; }

url=$(env_value HC_DISK_URL)
if [ -z "$url" ]; then
  log "error: HC_DISK_URL is not set"
  exit 1
fi

used=$(df --output=pcent / | tail -n 1 | tr -dc '0-9')
if [ -z "$used" ]; then
  log "error: can't read the disk use from df"
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw "disk-check can't read df's output" "$url/fail"
  exit 1
fi
if [ "$used" -lt "$LIMIT_PERCENT" ]; then
  log "disk ${used}% used"
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw "disk ${used}% used" "$url"
else
  log "disk ${used}% used, over ${LIMIT_PERCENT}%"
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw \
    "disk ${used}% used, over ${LIMIT_PERCENT}%: see deploy/README.md, Backups and alerts" "$url/fail"
fi
