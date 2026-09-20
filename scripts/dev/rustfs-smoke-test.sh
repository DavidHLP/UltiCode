#!/usr/bin/env bash
# RustFS instance-level smoke test.
#
# Starts a throwaway RustFS container from the pinned image with the same
# credential/volume wiring as the Compose service and runs RustFsSmokeTest
# against it through the real S3Storage adapter:
#   put / get / openStream / putFile / delete, unsigned read refused,
#   restart persistence, and a clear error when the endpoint is unavailable.
#
# Requirements: docker, a JDK 17 toolchain and the repository Maven wrapper.
# Usage: scripts/dev/rustfs-smoke-test.sh [--keep]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${RUSTFS_IMAGE_REF:-rustfs/rustfs:1.0.0@sha256:8cc9801755448b71a786705ce76692c77e14936cccd87cf2fc31842e58f4d1ff}"
NAME="${RUSTFS_SMOKE_NAME:-ulticode-rustfs-smoke}"
VOLUME="${RUSTFS_SMOKE_VOLUME:-ulticode-rustfs-smoke-data-$$}"
PORT="${RUSTFS_SMOKE_PORT:-19000}"
BUCKET="${RUSTFS_BUCKET:-ulticode}"
ACCESS_KEY="${RUSTFS_SMOKE_ACCESS_KEY:-smoke-access-key}"
SECRET_KEY="${RUSTFS_SMOKE_SECRET_KEY:-smoke-secret-key-0123456789}"
ENDPOINT="http://127.0.0.1:${PORT}"
KEEP=false
case "${1:-}" in
  "")
    ;;
  --keep)
    KEEP=true
    shift
    ;;
  *)
    echo "Usage: ${BASH_SOURCE[0]} [--keep]" >&2
    exit 2
    ;;
esac
if (($# > 0)); then
  echo "Usage: ${BASH_SOURCE[0]} [--keep]" >&2
  exit 2
fi
VOLUME_CLEANUP=true
if [[ -n "${RUSTFS_SMOKE_VOLUME:-}" ]] && docker volume inspect "$VOLUME" >/dev/null 2>&1; then
  VOLUME_CLEANUP=false
fi

log() { printf '\n== %s\n' "$*"; }

# Unsigned raw-TCP probe: avoids depending on an HTTP client being installed.
probe() {
  timeout 5 bash -c "exec 3<>/dev/tcp/127.0.0.1/${PORT} && printf 'GET $1 HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n' >&3 && head -1 <&3" 2>/dev/null | tr -d '\r'
}

wait_ready() {
  local ready=""
  for _ in $(seq 1 60); do
    ready="$(probe /health/ready || true)"
    [[ "$ready" == *" 200 "* ]] && { echo "ready: $ready"; return 0; }
    sleep 1
  done
  echo "RustFS did not become ready: ${ready:-no response}" >&2
  docker logs "$NAME" 2>&1 | tail -20 >&2 || true
  return 1
}

cleanup() {
  if [[ "$KEEP" == "true" ]]; then
    echo "keeping container ${NAME} and volume ${VOLUME} (--keep)"
    return
  fi
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  if [[ "$VOLUME_CLEANUP" == "true" ]]; then
    docker volume rm "$VOLUME" >/dev/null 2>&1 || true
  else
    echo "preserving pre-existing volume ${VOLUME}; use a dedicated smoke volume for automatic cleanup"
  fi
}
trap cleanup EXIT

run_smoke_tests() {
  ( cd "$ROOT_DIR/services" && env \
      RUSTFS_SMOKE_ENDPOINT="$ENDPOINT" \
      RUSTFS_SMOKE_BUCKET="$BUCKET" \
      RUSTFS_SMOKE_ACCESS_KEY="$ACCESS_KEY" \
      RUSTFS_SMOKE_SECRET_KEY="$SECRET_KEY" \
      "$@" \
      ./mvnw -pl platform/storage test -B -DfailIfNoTests=false \
        -Dtest=RustFsSmokeTest -DskipITs )
}

log "starting RustFS ${IMAGE} on 127.0.0.1:${PORT}"
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" \
  -p "127.0.0.1:${PORT}:9000" \
  -e RUSTFS_VOLUMES=/data \
  -e RUSTFS_ADDRESS=:9000 \
  -e RUSTFS_CONSOLE_ENABLE=false \
  -e RUSTFS_HEALTH_ENDPOINT_ENABLE=true \
  -e RUSTFS_ACCESS_KEY="$ACCESS_KEY" \
  -e RUSTFS_SECRET_KEY="$SECRET_KEY" \
  -v "${VOLUME}:/data" \
  "$IMAGE" >/dev/null

log "waiting for /health/ready"
wait_ready

log "RustFsSmokeTest: round trip, unsigned refusal, unavailable endpoint"
run_smoke_tests

log "restart persistence: write marker, restart container, read marker"
run_smoke_tests RUSTFS_SMOKE_MARKER_MODE=write
docker restart "$NAME" >/dev/null
wait_ready
run_smoke_tests RUSTFS_SMOKE_MARKER_MODE=read

log "smoke test finished: every RustFsSmokeTest case passed"
