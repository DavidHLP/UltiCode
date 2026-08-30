#!/usr/bin/env bash
set -eo pipefail

# P2-DEPLOY-001 contract: source/schema/image integrity, atomic descriptor
# recording, rollback compatibility, and system-health failure semantics.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
RUNBOOK="$ROOT_DIR/scripts/runbooks/deployment-integrity.sh"
HOST_HEALTH="$ROOT_DIR/.github/actions/host-health/action.yml"
HOST_DEPLOY="$ROOT_DIR/.github/actions/host-deploy/action.yml"
CD_DEPLOY="$ROOT_DIR/.github/workflows/cd-deploy.yml"
CD_ROLLBACK="$ROOT_DIR/.github/workflows/cd-rollback.yml"
valid_digest=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
valid_refs=""
for service in backend-auth backend-admin backend-app backend-submission backend-search backend-notification backend-judge console management; do
  variable="$(printf '%s' "$service" | tr '[:lower:]-' '[:upper:]_')_IMAGE_REF"
  valid_refs="$valid_refs$variable=ghcr.io/example/ulticode/$service@sha256:$valid_digest"$'\n'
done

fail() {
  echo "deployment-integrity-contract: FAIL: $*" >&2
  exit 1
}

[ -x "$RUNBOOK" ] || fail "deployment-integrity.sh is not executable"
[ -x "$ROOT_DIR/scripts/runbooks/image-reference-policy.sh" ] || fail "image policy is not executable"
grep -Fq 'Validate release and deployment integrity before mutation' "$HOST_DEPLOY" \
  || fail "host-deploy does not preflight before mutation"
grep -Fq 'deployment-integrity.sh preflight' "$HOST_DEPLOY" \
  || fail "host-deploy does not validate deployment integrity"
grep -Fq 'docker-compose.prod.yml config --quiet' "$HOST_DEPLOY" \
  || fail "host-deploy does not validate merged Compose configuration"
grep -Fq 'deployment-integrity.sh check-rollback' "$HOST_DEPLOY" \
  || fail "host-deploy does not gate schema-compatible rollback"
grep -Fq 'DEPLOYMENT_STATUS=PENDING_HEALTH' "$HOST_DEPLOY" \
  || fail "host-deploy does not record pending health state"
preflight_line="$(grep -n 'Validate release and deployment integrity before mutation' "$HOST_DEPLOY" | head -1 | cut -d: -f1)"
migration_line="$(grep -n 'Run ordered owner database migrations' "$HOST_DEPLOY" | head -1 | cut -d: -f1)"
config_line="$(grep -n 'docker-compose.prod.yml config --quiet' "$HOST_DEPLOY" | head -1 | cut -d: -f1)"
[[ "$preflight_line" -lt "$migration_line" && "$config_line" -lt "$migration_line" ]] \
  || fail "deployment config preflight occurs after migration mutation"
grep -Fq 'flyway/flyway@sha256:' "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" \
  || fail "owner migration runbook uses a mutable Flyway image"
grep -Fq 'flyway/flyway@sha256:' "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh" \
  || fail "owner backup runbook uses a mutable Flyway image"
grep -Fq 'source_commit:' "$CD_DEPLOY" \
  || fail "cd-deploy does not pass source commit"
grep -Fq 'schema_manifest_checksum:' "$CD_DEPLOY" \
  || fail "cd-deploy does not pass schema checksum"
grep -Fq 'schema_manifest_checksum:' "$CD_ROLLBACK" \
  || fail "cd-rollback does not require schema checksum"
schema_checksum="$("$RUNBOOK" schema-checksum)"
printf '%s' "$schema_checksum" | grep -Eq '^[0-9a-f]{64}$' \
  || fail "schema-checksum did not return a SHA-256"

mkdir -p "$ROOT_DIR/.local"
test_dir="$(mktemp -d "$ROOT_DIR/.local/deploy-integrity-contract.XXXXXX")"
descriptor="$test_dir/deployment.json"
trap 'rm -rf "$test_dir"' EXIT
commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"

IMAGE_REF_LIST="$valid_refs" \
DEPLOYMENT_COMMIT="$commit" \
EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$schema_checksum" \
DEPLOYMENT_DESCRIPTOR_FILE="$descriptor" \
"$RUNBOOK" preflight >/dev/null

IMAGE_REF_LIST="$valid_refs" \
DEPLOYMENT_COMMIT="$commit" \
EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$schema_checksum" \
DEPLOYED_SERVICES=backend-auth \
DEPLOYMENT_STATUS=PENDING_HEALTH \
DEPLOYMENT_DESCRIPTOR_FILE="$descriptor" \
"$RUNBOOK" record >/dev/null
[ -s "$descriptor" ] || fail "deployment descriptor was not written"
python3 - "$descriptor" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    descriptor = json.load(source)
assert descriptor["status"] == "PENDING_HEALTH"
assert descriptor["deployment_commit"]
assert len(descriptor["schema_manifest_checksum"]) == 64
assert "backend-auth@sha256:" in descriptor["image_manifest"]
assert descriptor["services"] == "backend-auth"
print("descriptor JSON and traceability fields: PASS")
PY

IMAGE_REF_LIST="$valid_refs" \
DEPLOYMENT_COMMIT="$commit" \
EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$schema_checksum" \
DEPLOYMENT_DESCRIPTOR_FILE="$descriptor" \
"$RUNBOOK" check-rollback >/dev/null
printf 'rollback compatibility check: PASS\n'

if IMAGE_REF_LIST="$valid_refs" \
  DEPLOYMENT_COMMIT="$commit" \
  EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$(printf 'b%.0s' $(seq 1 64))" \
  DEPLOYMENT_DESCRIPTOR_FILE="$descriptor" \
  "$RUNBOOK" check-rollback >/dev/null 2>&1; then
  fail "schema-incompatible rollback was accepted"
fi
printf 'schema-incompatible rollback rejection: PASS\n'

DEPLOYMENT_COMMIT="$commit" \
EXPECTED_SCHEMA_MANIFEST_CHECKSUM="$schema_checksum" \
DEPLOYMENT_STATUS=HEALTHY \
DEPLOYMENT_DESCRIPTOR_FILE="$descriptor" \
"$RUNBOOK" mark-health >/dev/null
grep -Fq '"status": "HEALTHY"' "$descriptor" \
  || fail "healthy descriptor state was not recorded"
printf 'atomic health-state update: PASS\n'

grep -Fq 'curl-host-https 9443 -' "$HOST_HEALTH" \
  || fail "console health does not verify HTTPS"
grep -Fq 'SYSTEM HEALTH FAILED' "$HOST_HEALTH" \
  || fail "host-health has no system-level failure summary"
grep -Fq 'SYSTEM HEALTH PASS' "$HOST_HEALTH" \
  || fail "host-health has no system-level success summary"
grep -Fq 'deployment-integrity.sh mark-health' "$HOST_HEALTH" \
  || fail "host-health does not persist descriptor health"
printf 'host-health system summary and descriptor wiring: PASS\n'
printf 'deployment-integrity-contract: PASS\n'
