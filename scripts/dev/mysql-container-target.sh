#!/usr/bin/env bash

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
