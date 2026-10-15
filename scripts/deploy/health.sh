#!/usr/bin/env bash
set -euo pipefail

stage="${1:-}"
case "$stage" in
  install-ssh-key)
set -euo pipefail
[[ -n "$DEPLOY_KEY" ]] || { echo "::error::health SSH key is empty" >&2; exit 1; }
[[ -n "$DEPLOY_KNOWN_HOSTS" ]] || { echo "::error::health known_hosts is empty" >&2; exit 1; }
[[ "$HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "::error::invalid deploy host" >&2; exit 1; }
[[ "$USER" =~ ^[A-Za-z_][A-Za-z0-9_.-]*$ ]] || { echo "::error::invalid deploy user" >&2; exit 1; }
if [[ ! "$DEPLOY_PORT" =~ ^[0-9]{1,5}$ ]] || (( 10#$DEPLOY_PORT < 1 || 10#$DEPLOY_PORT > 65535 )); then
  echo "::error::invalid deploy port" >&2
  exit 1
fi
printf 'HOST=%s\nUSER=%s\nDEPLOY_PORT=%s\n' "$HOST" "$USER" "$DEPLOY_PORT" >> "$GITHUB_ENV"
install -d -m 700 ~/.ssh
printf '%s\n' "$DEPLOY_KEY" > ~/.ssh/deploy_key
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > ~/.ssh/known_hosts
chmod 600 ~/.ssh/deploy_key ~/.ssh/known_hosts
    ;;
  check-service-health)
set -euo pipefail
[[ -s ~/.ssh/known_hosts ]] || { echo "::error::known_hosts is empty" >&2; exit 1; }
host_lookup="${HOST}"
[[ "${DEPLOY_PORT}" == "22" ]] || host_lookup="[${HOST}]:${DEPLOY_PORT}"
ssh-keygen -F "$host_lookup" -f ~/.ssh/known_hosts >/dev/null \
  || { echo "::error::known_hosts has no pinned key for $host_lookup" >&2; exit 1; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "${DEPLOY_PORT}"
  "${USER}@${HOST}")
shell_quote() { printf '%q' "$1"; }

mark_deployment() {
  local status="$1" remote_command
  if [[ -z "$DEPLOYMENT_PATH" && -z "$DEPLOYMENT_COMMIT" && -z "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ]]; then
    return 0
  fi
  for required in DEPLOYMENT_PATH DEPLOYMENT_COMMIT EXPECTED_SCHEMA_MANIFEST_CHECKSUM; do
    [[ -n "${!required}" ]] || {
      echo "::error::$required is required to persist deployment health" >&2
      return 1
    }
  done
  remote_command="cd $(shell_quote "$DEPLOYMENT_PATH") &&"
  remote_command+=" DEPLOYMENT_COMMIT=$(shell_quote "$DEPLOYMENT_COMMIT")"
  remote_command+=" EXPECTED_SCHEMA_MANIFEST_CHECKSUM=$(shell_quote "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM")"
  remote_command+=" DEPLOYMENT_STATUS=$(shell_quote "$status")"
  remote_command+=" DEPLOYMENT_DESCRIPTOR_FILE=$(shell_quote "$DEPLOYMENT_DESCRIPTOR_FILE")"
  remote_command+=" ./scripts/runbooks/deployment-integrity.sh mark-health"
  "${SSH_CMD[@]}" "$remote_command"
}

check_one() {
  local name="$1" mode="$2" port="$3" path_="$4"
  for i in $(seq 1 "${ATTEMPTS}"); do
    case "$mode" in
      curl-container)
        if "${SSH_CMD[@]}" "docker exec ulticode-$name curl --max-time 5 -sf 'http://localhost:$port$path_' > /dev/null 2>&1"; then return 0; fi ;;
      curl-host)
        if "${SSH_CMD[@]}" "curl --max-time 5 -sf http://localhost:$port > /dev/null 2>&1"; then return 0; fi ;;
      curl-host-https)
        if "${SSH_CMD[@]}" "curl --max-time 5 -kfsS https://localhost:$port > /dev/null 2>&1"; then return 0; fi ;;
      inspect)
        if "${SSH_CMD[@]}" "test \"\$(docker inspect -f '{{.State.Health.Status}}' ulticode-$name 2>/dev/null)\" = healthy"; then return 0; fi ;;
      *)
        echo "::error::unknown health mode '$mode' for $name"; return 1 ;;
    esac
    if [ "$i" -eq "${ATTEMPTS}" ]; then
      echo "::error::$name failed health check after $((ATTEMPTS * INTERVAL)) seconds"
      return 1
    fi
    echo "  Waiting for $name... ($i/${ATTEMPTS})"
    sleep "$INTERVAL"
  done
  return 1
}

checked=0
passed=0
failed=0
while read -r name mode port path_; do
  [ -z "$name" ] && continue
  case "$name" in \#*) continue ;; esac
  case "$name" in
    backend-auth|backend-admin|backend-app|backend-notification|backend-submission|backend-search|backend-judge|console|management) ;;
    *) echo "::error::unknown health service '$name'" >&2; failed=$((failed + 1)); continue ;;
  esac
  checked=$((checked + 1))
  echo "Checking $name..."
  if ! check_one "$name" "$mode" "$port" "$path_"; then
    failed=1
  else
    passed=$((passed + 1))
    echo "  $name is healthy"
  fi
done <<< "$SERVICES"

if [ "$checked" -eq 0 ]; then
  echo "::error::SYSTEM HEALTH FAILED: no services were checked" >&2
  mark_deployment FAILED || true
  exit 1
fi
if [ "$failed" -ne 0 ]; then
  echo "::error::SYSTEM HEALTH FAILED: $passed/$checked services healthy; partial success is not a deployment success" >&2
  mark_deployment FAILED || true
  exit 1
fi
if ! mark_deployment HEALTHY; then
  echo "::error::SYSTEM HEALTH FAILED: deployment descriptor could not be marked healthy" >&2
  exit 1
fi
echo "::notice::SYSTEM HEALTH PASS: $passed/$checked services healthy"
    ;;
  *) echo "usage: health.sh {install-ssh-key|check-service-health}" >&2; exit 2 ;;
esac
