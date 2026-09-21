#!/usr/bin/env bash
# RustFS instance-level smoke test.
#
# Starts a throwaway RustFS container from the pinned image with the same
# credential/volume wiring as the Compose service and runs RustFsSmokeTest
# against it through the real S3Storage adapter:
#   put / get / openStream / putFile / delete, unsigned read refused,
#   restart persistence, a clear error when the endpoint is unavailable, and
#   IAM App/Admin prefix isolation.
#
# Requirements: docker, a JDK 17 toolchain and the repository Maven wrapper.
# Usage: scripts/dev/rustfs-smoke-test.sh [--keep]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${RUSTFS_IMAGE_REF:-rustfs/rustfs:1.0.0@sha256:8cc9801755448b71a786705ce76692c77e14936cccd87cf2fc31842e58f4d1ff}"
RC_IMAGE="${RUSTFS_RC_IMAGE_REF:-rustfs/rc:v0.1.36@sha256:ab024bfebee49a750ce886b4c70963ccd9ddaa03f491704a90710641d7a26699}"
AWS_CLI_IMAGE="${AWS_CLI_IMAGE_REF:-amazon/aws-cli:2.31.0@sha256:d5f18fde2ba3f9205e75d511ca3e6185c144e55df07e50eee16d940994557b40}"
NAME="${RUSTFS_SMOKE_NAME:-ulticode-rustfs-smoke}"
VOLUME="${RUSTFS_SMOKE_VOLUME:-ulticode-rustfs-smoke-data-$$}"
PORT="${RUSTFS_SMOKE_PORT:-19000}"
BUCKET="${RUSTFS_BUCKET:-ulticode}"
ACCESS_KEY="${RUSTFS_SMOKE_ACCESS_KEY:-smoke-access-key}"
SECRET_KEY="${RUSTFS_SMOKE_SECRET_KEY:-smoke-secret-key-0123456789}"
APP_ACCESS_KEY="${RUSTFS_SMOKE_APP_ACCESS_KEY:-smoke-app-access-key}"
APP_SECRET_KEY="${RUSTFS_SMOKE_APP_SECRET_KEY:-smoke-app-secret-key-0123456789}"
ADMIN_ACCESS_KEY="${RUSTFS_SMOKE_ADMIN_ACCESS_KEY:-smoke-admin-access-key}"
ADMIN_SECRET_KEY="${RUSTFS_SMOKE_ADMIN_SECRET_KEY:-smoke-admin-secret-key-0123456789}"
ENDPOINT="http://127.0.0.1:${PORT}"
IN_CONTAINER_ENDPOINT="http://127.0.0.1:9000"
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
if docker volume inspect "$VOLUME" >/dev/null 2>&1; then
  if [[ -z "${RUSTFS_SMOKE_VOLUME:-}" ]]; then
    echo "Generated smoke volume already exists: ${VOLUME}; refusing to reuse stale test data." >&2
    exit 2
  fi
  VOLUME_CLEANUP=false
fi
POLICY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-rustfs-iam.XXXXXX")"
chmod 755 "$POLICY_DIR"
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
  else
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    if [[ "$VOLUME_CLEANUP" == "true" ]]; then
      docker volume rm "$VOLUME" >/dev/null 2>&1 || true
    else
      echo "preserving pre-existing volume ${VOLUME}; use a dedicated smoke volume for automatic cleanup"
    fi
  fi
  rm -rf "$POLICY_DIR"
}
trap cleanup EXIT

aws_s3api() {
  local access_key="$1" secret_key="$2"
  shift 2
  docker run --rm --network "container:${NAME}" \
    -e "AWS_ACCESS_KEY_ID=${access_key}" \
    -e "AWS_SECRET_ACCESS_KEY=${secret_key}" \
    -e AWS_DEFAULT_REGION=us-east-1 \
    "$AWS_CLI_IMAGE" s3api "$@" --endpoint-url "$IN_CONTAINER_ENDPOINT"
}

provision_iam() {
  cat >"$POLICY_DIR/app-storage-policy.json" <<EOF_POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": ["arn:aws:s3:::${BUCKET}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::${BUCKET}"],
      "Condition": {"StringLike": {"s3:prefix": ["app/avatars", "app/avatars/*"]}}
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::${BUCKET}/app/avatars/*"]
    }
  ]
}
EOF_POLICY
  cat >"$POLICY_DIR/admin-storage-policy.json" <<EOF_POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": ["arn:aws:s3:::${BUCKET}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::${BUCKET}"],
      "Condition": {
        "StringLike": {
          "s3:prefix": [
            "app/avatars",
            "app/avatars/*",
            "admin/backups",
            "admin/backups/*"
          ]
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::${BUCKET}/app/avatars/*",
        "arn:aws:s3:::${BUCKET}/admin/backups/*"
      ]
    }
  ]
}
EOF_POLICY
  chmod 644 "$POLICY_DIR"/*.json
  if ! aws_s3api "$ACCESS_KEY" "$SECRET_KEY" head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
    aws_s3api "$ACCESS_KEY" "$SECRET_KEY" create-bucket --bucket "$BUCKET" >/dev/null
  fi
  docker run --rm --network "container:${NAME}" \
    -e "RUSTFS_ENDPOINT=${IN_CONTAINER_ENDPOINT}" \
    -e "RUSTFS_ACCESS_KEY=${ACCESS_KEY}" \
    -e "RUSTFS_SECRET_KEY=${SECRET_KEY}" \
    -e "RUSTFS_APP_ACCESS_KEY=${APP_ACCESS_KEY}" \
    -e "RUSTFS_APP_SECRET_KEY=${APP_SECRET_KEY}" \
    -e "RUSTFS_ADMIN_ACCESS_KEY=${ADMIN_ACCESS_KEY}" \
    -e "RUSTFS_ADMIN_SECRET_KEY=${ADMIN_SECRET_KEY}" \
    -v "$POLICY_DIR:/policies:ro" \
    --entrypoint /bin/sh "$RC_IMAGE" -ec '
      set -eu
      rc alias set root "$RUSTFS_ENDPOINT" "$RUSTFS_ACCESS_KEY" "$RUSTFS_SECRET_KEY"
      rc admin policy create root app-storage /policies/app-storage-policy.json
      rc admin policy create root admin-storage /policies/admin-storage-policy.json
      if rc admin user info root "$RUSTFS_APP_ACCESS_KEY" >/dev/null 2>&1; then
        rc admin user passwd root "$RUSTFS_APP_ACCESS_KEY" \
          --password-from-env RUSTFS_APP_SECRET_KEY
      else
        rc admin user add root "$RUSTFS_APP_ACCESS_KEY" "$RUSTFS_APP_SECRET_KEY"
      fi
      rc admin user enable root "$RUSTFS_APP_ACCESS_KEY"
      if rc admin user info root "$RUSTFS_ADMIN_ACCESS_KEY" >/dev/null 2>&1; then
        rc admin user passwd root "$RUSTFS_ADMIN_ACCESS_KEY" \
          --password-from-env RUSTFS_ADMIN_SECRET_KEY
      else
        rc admin user add root "$RUSTFS_ADMIN_ACCESS_KEY" "$RUSTFS_ADMIN_SECRET_KEY"
      fi
      rc admin user enable root "$RUSTFS_ADMIN_ACCESS_KEY"
      rc admin policy attach root app-storage --user "$RUSTFS_APP_ACCESS_KEY"
      rc admin policy attach root admin-storage --user "$RUSTFS_ADMIN_ACCESS_KEY"
    '
}

run_iam_scope_tests() {
  log "IAM scope: App is limited to app/avatars and Admin covers both prefixes"
  aws_s3api "$APP_ACCESS_KEY" "$APP_SECRET_KEY" get-bucket-location \
    --bucket "$BUCKET" >/dev/null
  aws_s3api "$APP_ACCESS_KEY" "$APP_SECRET_KEY" put-object \
    --bucket "$BUCKET" --key app/avatars/scope-app.txt --body fileb://dev/null >/dev/null
  aws_s3api "$APP_ACCESS_KEY" "$APP_SECRET_KEY" get-object \
    --bucket "$BUCKET" --key app/avatars/scope-app.txt /dev/null >/dev/null
  aws_s3api "$ADMIN_ACCESS_KEY" "$ADMIN_SECRET_KEY" put-object \
    --bucket "$BUCKET" --key app/avatars/scope-admin.txt --body fileb://dev/null >/dev/null
  aws_s3api "$ADMIN_ACCESS_KEY" "$ADMIN_SECRET_KEY" put-object \
    --bucket "$BUCKET" --key admin/backups/scope-admin.sql --body fileb://dev/null >/dev/null
  aws_s3api "$ADMIN_ACCESS_KEY" "$ADMIN_SECRET_KEY" get-object \
    --bucket "$BUCKET" --key admin/backups/scope-admin.sql /dev/null >/dev/null
  if aws_s3api "$APP_ACCESS_KEY" "$APP_SECRET_KEY" put-object \
      --bucket "$BUCKET" --key admin/backups/scope-app.sql --body fileb://dev/null >/dev/null 2>&1; then
    echo "App IAM user unexpectedly wrote admin/backups" >&2
    return 1
  fi
  if aws_s3api "$APP_ACCESS_KEY" "$APP_SECRET_KEY" get-object \
      --bucket "$BUCKET" --key admin/backups/scope-admin.sql /dev/null >/dev/null 2>&1; then
    echo "App IAM user unexpectedly read admin/backups" >&2
    return 1
  fi
  echo "RustFS IAM prefix scope: PASS"
}

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
  --user "10001:10001" \
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

log "provisioning RustFS IAM users and checking prefix boundaries"
provision_iam
log "reapplying RustFS IAM configuration to verify convergence"
provision_iam
run_iam_scope_tests

log "RustFsSmokeTest: round trip, unsigned refusal, unavailable endpoint"
run_smoke_tests

log "restart persistence: write marker, restart container, read marker"
run_smoke_tests RUSTFS_SMOKE_MARKER_MODE=write
docker restart "$NAME" >/dev/null
wait_ready
run_smoke_tests RUSTFS_SMOKE_MARKER_MODE=read

log "smoke test finished: every RustFsSmokeTest case passed"
