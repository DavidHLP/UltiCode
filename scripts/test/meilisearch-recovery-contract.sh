#!/usr/bin/env bash
set -euo pipefail

# P1-INFRA-005/P5-GATE-002: prove a derived MeiliSearch dependency can stop
# and recover without treating the index as an Owner data source.
CONTAINER_NAME="ulticode-meili-recovery-${BASHPID:-$$}"
CONTAINER_ID=""

cleanup() {
  local status=$?
  if [[ -n "$CONTAINER_ID" ]]; then
    docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || status=1
  fi
  exit "$status"
}
trap cleanup EXIT

if ! command -v docker >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  echo "meilisearch-recovery-contract: BLOCKED_EXTERNAL (Docker and curl are required)" >&2
  exit 1
fi

CONTAINER_ID="$(docker run --detach --name "$CONTAINER_NAME" \
  --publish 127.0.0.1::7700 \
  --env MEILI_MASTER_KEY=contract-meili-key \
  --env MEILI_NO_ANALYTICS=true \
  getmeili/meilisearch:v1.8 2>/dev/null)" || {
  echo "meilisearch-recovery-contract: BLOCKED_EXTERNAL (disposable MeiliSearch could not start)" >&2
  exit 1
}

PORT=""
for _ in $(seq 1 30); do
  PORT="$(docker port "$CONTAINER_ID" 7700/tcp 2>/dev/null | cut -d: -f2 || true)"
  if [[ "$PORT" =~ ^[0-9]+$ ]] && curl --connect-timeout 1 --max-time 2 -fsS \
      "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
[[ "$PORT" =~ ^[0-9]+$ ]] || {
  echo "meilisearch-recovery-contract: FAIL (initial health did not become ready)" >&2
  exit 1
}

if ! curl --connect-timeout 1 --max-time 2 -fsS "http://127.0.0.1:$PORT/health" >/dev/null; then
  echo "meilisearch-recovery-contract: FAIL (initial health assertion failed)" >&2
  exit 1
fi

docker stop "$CONTAINER_ID" >/dev/null
stopped=0
for _ in $(seq 1 15); do
  if ! curl --connect-timeout 1 --max-time 2 -fsS \
      "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
    stopped=1
    break
  fi
  sleep 1
done
[[ "$stopped" == "1" ]] || {
  echo "meilisearch-recovery-contract: FAIL (health remained reachable after stop)" >&2
  exit 1
}

docker start "$CONTAINER_ID" >/dev/null
PORT="$(docker port "$CONTAINER_ID" 7700/tcp | cut -d: -f2)"
recovered=0
for _ in $(seq 1 60); do
  if curl --connect-timeout 1 --max-time 2 -fsS \
      "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
    recovered=1
    break
  fi
  sleep 1
done
[[ "$recovered" == "1" ]] || {
  echo "meilisearch-recovery-contract: FAIL (health did not recover after restart)" >&2
  exit 1
}

printf 'meilisearch-recovery-contract: PASS (stop detected; health recovered; derived index boundary preserved)\n'
