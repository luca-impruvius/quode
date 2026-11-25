#!/bin/bash
# Daily disk check (M0-12): pings healthchecks.io while the root filesystem is below 80% used,
# and /fail above it. A dead man's switch: no ping at all (cron or server down) alerts too.
# Lives in ~/quode/backup/; cron runs it (see crontab next to it).
set -euo pipefail

self=$(readlink -f "$0")
cd "$(dirname "$self")/.."

LIMIT_PERCENT=80

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-; }

url=$(env_value HC_DISK_URL)
[ -n "$url" ] || {
  echo "disk-check: HC_DISK_URL is not set" >&2
  exit 1
}

used=$(df --output=pcent / | tail -n 1 | tr -dc '0-9')
if [ "$used" -lt "$LIMIT_PERCENT" ]; then
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw "disk ${used}% used" "$url"
else
  curl -fsS -m 10 --retry 5 -o /dev/null --data-raw \
    "disk ${used}% used, over ${LIMIT_PERCENT}%: run docker system prune, check logs" "$url/fail"
fi
