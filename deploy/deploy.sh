#!/bin/bash
# Deploys one image tag on the server (M0-11, ADR-0042). Lives next to compose.yml in ~/quode/.
#
# GitHub Actions reaches it through a key that can run nothing else: in ~/.ssh/authorized_keys,
#   command="/home/deploy/quode/deploy.sh",restrict ssh-ed25519 AAAA... github-actions-deploy
# so the request arrives in SSH_ORIGINAL_COMMAND. By hand: ~/quode/deploy.sh deploy <sha>.
#
#   deploy <40-hex commit>  set QUODE_TAG, pull, restart, check health; roll back if unhealthy
#   status                  the current and previous tags, and the containers
#
# The deploy itself runs detached from the SSH session and logs to deploy.log, so a dropped
# connection or a cancelled job can't stop it halfway; the session only follows the log.
# Container logs never go to the session (Actions logs are public): see failed-deploy.log.
set -euo pipefail

self=$(readlink -f "$0")
cd "$(dirname "$self")"

HEALTH_TIMEOUT_SECONDS=150
LOCK_WAIT_SECONDS=300
IMAGE_PATTERN='/quode-(backend|frontend):'

log() { echo "deploy: $(date -u +%H:%M:%S) $*"; }
die() { echo "deploy: error: $*" >&2; exit 1; }

env_value() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-; }

set_tag() { sed -i "s/^QUODE_TAG=.*/QUODE_TAG=$1/" .env; }

# Waits for the backend container's healthcheck, then checks that the running build is the
# requested commit (Actuator's git info) and that Caddy serves the API over HTTPS.
healthy() {
  local tag=$1 domain container state deadline running
  domain=$(env_value QUODE_DOMAIN)
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while :; do
    container=$(docker compose ps -q backend)
    state=$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || echo missing)
    [ "$state" = healthy ] && break
    if [ "$SECONDS" -ge "$deadline" ]; then
      log "backend is '$state' after ${HEALTH_TIMEOUT_SECONDS}s"
      return 1
    fi
    sleep 3
  done
  running=$(curl -fsS --max-time 10 http://127.0.0.1:8081/actuator/info |
    grep -oE '"commit":\{[^}]*"id":"[0-9a-f]+"' | grep -oE '"id":"[0-9a-f]+"' | grep -oE '[0-9a-f]{7,}' || true)
  if [ -z "$running" ] || [ "${tag#"$running"}" = "$tag" ]; then
    log "backend reports commit '${running:-none}', expected $tag"
    return 1
  fi
  if ! curl -fsS --max-time 10 --resolve "$domain:443:127.0.0.1" -o /dev/null "https://$domain/api/health"; then
    log "https://$domain/api/health is not answering through Caddy"
    return 1
  fi
  log "healthy: backend runs $running, https://$domain/api/health answers"
}

# Removes quode images except the two tags worth keeping (current, and previous for rollback).
prune_images() {
  local keep_current=$1 keep_previous=$2 image
  { docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E "$IMAGE_PATTERN" || true; } |
    while read -r image; do
      case "$image" in
        *":$keep_current" | *":$keep_previous") ;;
        *) docker image rm "$image" >/dev/null 2>&1 && log "removed $image" || true ;;
      esac
    done
}

deploy() {
  local tag=$1 previous
  previous=$(env_value QUODE_TAG)
  [ -n "$previous" ] || die "QUODE_TAG is missing from .env"
  log "deploying $tag (running: $previous)"

  set_tag "$tag"
  if ! docker compose pull --quiet backend caddy; then
    set_tag "$previous"
    die "could not pull images for $tag; nothing changed, still on $previous"
  fi

  if docker compose up -d && healthy "$tag"; then
    if [ "$previous" != "$tag" ]; then
      echo "$previous" > .previous-tag
    fi
    prune_images "$tag" "$(cat .previous-tag 2>/dev/null || echo none)"
    log "done: $tag is live"
    echo "compose-sha256 $(sha256sum compose.yml | cut -d' ' -f1)"
    echo "caddyfile-sha256 $(sha256sum Caddyfile | cut -d' ' -f1)"
    echo "deploy-sh-sha256 $(sha256sum deploy.sh | cut -d' ' -f1)"
    return 0
  fi

  docker compose logs backend --since 10m --tail 200 > failed-deploy.log 2>&1 || true
  log "rolling back to $previous (backend log: ~/quode/failed-deploy.log on the server)"
  set_tag "$previous"
  if docker compose up -d && healthy "$previous"; then
    die "$tag was unhealthy; rolled back to $previous, which is healthy"
  fi
  die "$tag was unhealthy and the rollback to $previous is unhealthy too: the site needs attention"
}

# Runs detached (setsid, own log, no terminal): holds the lock and writes its exit code to the file
# start_deploy named, then exits.
worker() {
  local tag=$1 exit_file=$2 code
  exec 9> .deploy.lock
  if ! flock -w "$LOCK_WAIT_SECONDS" 9; then
    echo "deploy: error: another deploy is still running" >&2
    echo 1 > "$exit_file"
    exit 1
  fi
  set +e
  (
    set -e
    deploy "$tag"
  )
  code=$?
  set -e
  echo "$code" > "$exit_file"
  exit "$code"
}

# Starts the worker and follows its part of deploy.log until it exits, then exits with its code.
# If this session dies (dropped connection, cancelled job), the worker carries on, rollback included.
start_deploy() {
  local tag=$1 exit_file=".deploy-exit.$$" start pid code
  [[ "$tag" =~ ^[0-9a-f]{40}$ ]] || die "not a 40-character commit SHA: '$tag'"
  find . -maxdepth 1 -name '.deploy-exit.*' -mmin +60 -delete
  touch deploy.log
  start=$(wc -l < deploy.log)
  setsid -w "$self" __worker "$tag" "$$" >> deploy.log 2>&1 < /dev/null &
  pid=$!
  tail -n "+$((start + 1))" -f --pid="$pid" deploy.log || true
  wait "$pid" 2>/dev/null || true
  code=$(cat "$exit_file" 2>/dev/null || echo 1)
  rm -f "$exit_file"
  exit "$code"
}

status() {
  echo "current:  $(env_value QUODE_TAG)"
  echo "previous: $(cat .previous-tag 2>/dev/null || echo none)"
  docker compose ps --format '{{.Service}}  {{.Status}}'
}

# Arguments when run by hand; otherwise the command the SSH client asked for, on one line.
if [ "$#" -gt 0 ]; then
  request=("$@")
else
  [[ "${SSH_ORIGINAL_COMMAND:-}" != *$'\n'* ]] || die "one command per connection"
  read -r -a request <<< "${SSH_ORIGINAL_COMMAND:-}"
fi

case "${request[0]:-}" in
  deploy)
    [ "${#request[@]}" -eq 2 ] || die "usage: deploy <commit-sha>"
    start_deploy "${request[1]}"
    ;;
  __worker)
    # Internal, started by start_deploy with arguments. Over SSH the forced command gets none,
    # so a client asking for "__worker" ends here with "$#" = 0 and is refused.
    [ "$#" -eq 3 ] || die "usage: deploy <commit-sha> | status"
    [[ "$2" =~ ^[0-9a-f]{40}$ && "$3" =~ ^[0-9]+$ ]] || die "usage: deploy <commit-sha> | status"
    worker "$2" ".deploy-exit.$3"
    ;;
  status)
    [ "${#request[@]}" -eq 1 ] || die "usage: status"
    status
    ;;
  *) die "usage: deploy <commit-sha> | status" ;;
esac
