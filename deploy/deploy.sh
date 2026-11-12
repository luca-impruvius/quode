#!/bin/bash
# Deploys one image tag on the server (M0-11, ADR-0042). Lives next to compose.yml in ~/quode/.
#
# GitHub Actions reaches it through a key that can run nothing else: in ~/.ssh/authorized_keys,
#   command="/home/deploy/quode/deploy.sh",restrict ssh-ed25519 AAAA... github-actions-deploy
# so the request arrives in SSH_ORIGINAL_COMMAND. By hand: ~/quode/deploy.sh deploy <sha>.
#
#   deploy <40-hex commit>  set QUODE_TAG, pull, restart, check health; roll back if unhealthy
#   status                  the current and previous tags, and the containers
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

HEALTH_TIMEOUT_SECONDS=150
IMAGE_PATTERN='/quode-(backend|frontend):'

log() { echo "deploy: $*"; }
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
    grep -oE '"commit":\{"id":"[0-9a-f]+"' | grep -oE '[0-9a-f]{7,}' || true)
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
  [[ "$tag" =~ ^[0-9a-f]{40}$ ]] || die "not a 40-character commit SHA: '$tag'"
  previous=$(env_value QUODE_TAG)
  [ -n "$previous" ] || die "QUODE_TAG is missing from .env"
  log "deploying $tag (running: $previous)"

  set_tag "$tag"
  if ! docker compose pull --quiet backend caddy; then
    set_tag "$previous"
    die "could not pull images for $tag; nothing changed, still on $previous"
  fi

  if docker compose up -d --remove-orphans && healthy "$tag"; then
    if [ "$previous" != "$tag" ]; then
      echo "$previous" > .previous-tag
    fi
    prune_images "$tag" "$(cat .previous-tag 2>/dev/null || echo none)"
    log "done: $tag is live"
    echo "compose-sha256 $(sha256sum compose.yml | cut -d' ' -f1)"
    echo "caddyfile-sha256 $(sha256sum Caddyfile | cut -d' ' -f1)"
    return 0
  fi

  log "rolling back to $previous"
  docker compose logs backend --since 5m --tail 40 || true
  set_tag "$previous"
  if docker compose up -d --remove-orphans && healthy "$previous"; then
    die "$tag was unhealthy; rolled back to $previous, which is healthy"
  fi
  die "$tag was unhealthy and the rollback to $previous is unhealthy too: the site needs attention"
}

status() {
  echo "current:  $(env_value QUODE_TAG)"
  echo "previous: $(cat .previous-tag 2>/dev/null || echo none)"
  docker compose ps --format '{{.Service}}  {{.Status}}'
}

# Arguments when run by hand; otherwise the command the SSH client asked for.
if [ "$#" -gt 0 ]; then
  request=("$@")
else
  read -r -a request <<< "${SSH_ORIGINAL_COMMAND:-}"
fi

case "${request[0]:-}" in
  deploy)
    [ "${#request[@]}" -eq 2 ] || die "usage: deploy <commit-sha>"
    exec 9> .deploy.lock
    flock -w 600 9 || die "another deploy is still running"
    deploy "${request[1]}"
    ;;
  status)
    [ "${#request[@]}" -eq 1 ] || die "usage: status"
    status
    ;;
  *) die "usage: deploy <commit-sha> | status" ;;
esac
