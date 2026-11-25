#!/bin/bash
# Tests backup.sh and disk-check.sh with stub docker, curl and df: no Docker, network or R2 needed.
# Run from anywhere: deploy/backup/test.sh (CI runs it).
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
failures=0

mkdir -p "$root/quode/backup" "$root/bin"
cp "$here/backup.sh" "$here/disk-check.sh" "$root/quode/backup/"
cat >"$root/quode/.env" <<'ENV'
DB_USER=quode
R2_ENDPOINT=https://example.r2.cloudflarestorage.com
R2_BUCKET=quode-backups
R2_ACCESS_KEY_ID=stub-key
R2_SECRET_ACCESS_KEY=stub-secret
HC_BACKUP_URL=https://hc.example/ping/backup
HC_DISK_URL=https://hc.example/ping/disk
ENV

cat >"$root/bin/docker" <<'STUB'
#!/bin/bash
echo "docker $*" >>"$STUB_LOG"
case "$*" in
  *pg_dump*)
    [ "${STUB_DUMP_FAIL:-0}" = 1 ] && exit 1
    head -c "${STUB_DUMP_BYTES:-100}" /dev/zero ;;
  *pg_restore*) cat >/dev/null; exit "${STUB_RESTORE_EXIT:-0}" ;;
  run\ *) exit "${STUB_UPLOAD_EXIT:-0}" ;;
esac
STUB
cat >"$root/bin/curl" <<'STUB'
#!/bin/bash
echo "curl $*" >>"$STUB_LOG"
STUB
cat >"$root/bin/df" <<'STUB'
#!/bin/bash
printf 'Use%%\n %s%%\n' "$STUB_DISK"
STUB
chmod +x "$root/bin/"* "$root/quode/backup/"*.sh

# $1: name; $2: expected exit code; then env assignments; runs the script named by SCRIPT.
run() {
  local name=$1 want=$2
  shift 2
  export STUB_LOG="$root/log"
  : >"$STUB_LOG"
  local got=0
  env PATH="$root/bin:$PATH" "$@" "$root/quode/backup/$SCRIPT" >"$root/out" 2>&1 || got=$?
  if [ "$got" != "$want" ]; then
    echo "FAIL $name: exit $got, expected $want"
    cat "$root/out"
    failures=$((failures + 1))
  fi
}

# $1: name; $2: grep pattern that must (yes) or must not (no) be in the stub log; $3: yes/no.
expect() {
  if grep -qE -- "$2" "$root/log"; then found=yes; else found=no; fi
  if [ "$found" != "$3" ]; then
    echo "FAIL $1: '$2' in the log: $found, expected $3"
    cat "$root/log"
    failures=$((failures + 1))
  fi
}

SCRIPT=backup.sh
run "a good backup" 0
expect "it starts the check" 'hc.example/ping/backup/start' yes
expect "it uploads the dump" 'docker run .*rclone.* copyto /dump/quode-.*\.dump r2:quode-backups/quode-' yes
expect "it pings success with the size" 'curl .*uploaded quode-.*\(100 bytes\) https://hc.example/ping/backup$' yes
expect "it never puts a secret in arguments" 'stub-secret|stub-key' no

run "an oversized dump" 1 QUODE_BACKUP_MAX_BYTES=50
expect "an oversized dump is not uploaded" 'docker run' no
expect "an oversized dump alerts" 'over the 50 limit.*ping/backup/fail' yes

run "a failed pg_dump" 1 STUB_DUMP_FAIL=1
expect "a failed pg_dump alerts" 'pg_dump failed.*ping/backup/fail' yes
expect "a failed pg_dump uploads nothing" 'docker run' no

run "an unreadable dump" 1 STUB_RESTORE_EXIT=1
expect "an unreadable dump alerts" "can't read the dump.*ping/backup/fail" yes

run "a failed upload" 1 STUB_UPLOAD_EXIT=1
expect "a failed upload alerts" 'upload to R2 failed.*ping/backup/fail' yes
expect "a failed upload never pings success" 'uploaded quode-' no

SCRIPT=disk-check.sh
run "a disk below 80%" 0 STUB_DISK=42
expect "below 80% pings success" 'disk 42% used https://hc.example/ping/disk$' yes
run "a disk at 80%" 0 STUB_DISK=80
expect "at 80% pings fail" 'disk 80% used, over 80%.*ping/disk/fail' yes

if [ "$failures" -gt 0 ]; then
  echo "$failures failed"
  exit 1
fi
echo "all backup script tests passed"
