#!/usr/bin/env bash
# P1-INFRA-005: helper to start the three backend service shells locally.
#
# This is intentionally not the production runbook; it proves that the
# auth / admin / app modules can boot independently, register in Nacos,
# and respond to actuator health and placeholder endpoints.
#
# Usage:
#   scripts/dev/start-service-shells.sh [NACOS_PORT]
#
# Defaults:
#   NACOS_PORT=28848
#   Auth HTTP=9001, Dubbo=20881
#   Admin HTTP=9002, Dubbo=20882
#   App HTTP=9003, Dubbo=20883
#
# The script waits for the three HTTP ports to listen and then prints
# health / Nacos status. It does NOT background itself; keep the terminal
# open or wrap with nohup/systemd in real environments.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
NACOS_PORT="${1:-28848}"
NAMESPACE="${DUBBO_NAMESPACE:-dev}"

AUTH_HTTP=9001
ADMIN_HTTP=9002
APP_HTTP=9003

AUTH_DUBBO=20881
ADMIN_DUBBO=20882
APP_DUBBO=20883

log() { echo "[$(date +%H:%M:%S)] $*"; }

log "Starting Nacos standalone on port ${NACOS_PORT}..."
if ! docker ps --format '{{.Names}}' | grep -q '^p1-nacos$'; then
  docker run --rm --name p1-nacos \
    -e MODE=standalone \
    -e NACOS_AUTH_ENABLE=false \
    -p "${NACOS_PORT}:8848" \
    -p "$((NACOS_PORT + 1000)):9848" \
    -d nacos/nacos-server:v2.5.1
fi

log "Waiting for Nacos HTTP..."
for _ in {1..60}; do
  if curl -fs "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?groupName=DEFAULT_GROUP&pageSize=1&pageNo=1" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

log "Starting backend-auth (HTTP ${AUTH_HTTP}, Dubbo ${AUTH_DUBBO})..."
nohup java -jar "${REPO_ROOT}/backend-auth/target/app.jar" \
  --server.port="${AUTH_HTTP}" \
  --dubbo.protocol.port="${AUTH_DUBBO}" \
  --dubbo.registry.address="nacos://127.0.0.1:${NACOS_PORT}?namespace=${NAMESPACE}" \
  > /tmp/backend-auth-shell.log 2>&1 &

log "Starting backend-admin (HTTP ${ADMIN_HTTP}, Dubbo ${ADMIN_DUBBO})..."
nohup java -jar "${REPO_ROOT}/backend-admin/target/app.jar" \
  --server.port="${ADMIN_HTTP}" \
  --dubbo.protocol.port="${ADMIN_DUBBO}" \
  --dubbo.registry.address="nacos://127.0.0.1:${NACOS_PORT}?namespace=${NAMESPACE}" \
  > /tmp/backend-admin-shell.log 2>&1 &

log "Starting backend-app (HTTP ${APP_HTTP}, Dubbo ${APP_DUBBO})..."
nohup java -jar "${REPO_ROOT}/backend-app/target/app.jar" \
  --server.port="${APP_HTTP}" \
  --dubbo.protocol.port="${APP_DUBBO}" \
  --dubbo.registry.address="nacos://127.0.0.1:${NACOS_PORT}?namespace=${NAMESPACE}" \
  > /tmp/backend-app-shell.log 2>&1 &

log "Waiting for HTTP ports..."
for port in "${AUTH_HTTP}" "${ADMIN_HTTP}" "${APP_HTTP}"; do
  for _ in {1..60}; do
    if ss -tln | grep -q ":${port} "; then
      break
    fi
    sleep 1
  done
done

log "Health checks..."
for port in "${AUTH_HTTP}" "${ADMIN_HTTP}" "${APP_HTTP}"; do
  curl -fs "http://127.0.0.1:${port}/actuator/health" | grep -q '"status":"UP"' || {
    echo "Health check failed on port ${port}"
    exit 1
  }
done

log "Nacos service list..."
curl -fs "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?groupName=DEFAULT_GROUP&pageSize=10&pageNo=1&namespaceId=${NAMESPACE}"
echo

log "Done. Auth=${AUTH_HTTP} Admin=${ADMIN_HTTP} App=${APP_HTTP}"
