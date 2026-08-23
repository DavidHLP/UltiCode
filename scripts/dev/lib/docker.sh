#!/usr/bin/env bash
# scripts/dev/lib/docker.sh — Docker container probes and health waits.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. Helpers are frozen readonly -f so a hostile .env cannot replace them.

if ! [[ -v __ULTICODE_DOCKER_SOURCED ]]; then
  declare -gr __ULTICODE_DOCKER_SOURCED=1

  mysql_container_targets_configured_host() {
    local container="$1" container_port="$2" host="$3" port="$4"
    local endpoint published_host published_port
    while IFS= read -r endpoint; do
      published_port="${endpoint##*:}"
      [[ "$published_port" == "$port" ]] || continue
      published_host="${endpoint%:*}"
      published_host="${published_host#[}"
      published_host="${published_host%]}"
      case "$host" in
        localhost)
          [[ "$published_host" == "127.0.0.1" || "$published_host" == "0.0.0.0" \
            || "$published_host" == "::1" || "$published_host" == "::" ]] && return 0
          ;;
        127.0.0.1)
          [[ "$published_host" == "127.0.0.1" || "$published_host" == "0.0.0.0" ]] && return 0
          ;;
        ::1)
          [[ "$published_host" == "::1" || "$published_host" == "::" ]] && return 0
          ;;
      esac
    done < <(docker port "$container" "$container_port/tcp" 2>/dev/null)
    return 1
  }

  container_running() {
    [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
  }

  await_container_health() {
    local container="$1"
    local attempts="${2:-60}"
    local interval_seconds="${3:-2}"
    local i status
    for ((i = 1; i <= attempts; i++)); do
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
      if [[ "$status" == "healthy" || "$status" == "running" ]]; then
        return 0
      fi
      sleep "$interval_seconds"
    done
    echo "Container did not become healthy: $container" >&2
    docker logs --tail 100 "$container" >&2 || true
    return 1
  }

  readonly -f mysql_container_targets_configured_host container_running await_container_health
fi
