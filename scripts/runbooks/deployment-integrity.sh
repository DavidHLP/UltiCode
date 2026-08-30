#!/usr/bin/env bash
set -eo pipefail

# P2-DEPLOY-001: deployment-source/config/schema integrity boundary.
# This host-local runbook records only release metadata and never credentials.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
ACTION="$1"
if [ "$#" -eq 0 ]; then
  ACTION=preflight
fi
POLICY="$ROOT_DIR/scripts/runbooks/image-reference-policy.sh"
DESCRIPTOR_INPUT="$DEPLOYMENT_DESCRIPTOR_FILE"
if [ -z "$DESCRIPTOR_INPUT" ]; then
  DESCRIPTOR_INPUT=.local/deploy/deployment.json
fi
DESCRIPTOR=""

case "$ACTION" in
  schema-checksum|preflight|record|check-rollback|mark-health) ;;
  *)
    echo "Usage: $0 {schema-checksum|preflight|record|check-rollback|mark-health}" >&2
    exit 2
    ;;
esac

die() {
  echo "[deployment-integrity] FAIL: $*" >&2
  exit 1
}

resolve_descriptor() {
  local candidate="$DESCRIPTOR_INPUT"
  case "$candidate" in
    /*) ;;
    *) candidate="$ROOT_DIR/$candidate" ;;
  esac
  case "$candidate" in
    "$ROOT_DIR"/*) ;;
    *) die "deployment descriptor must stay under the deployment root" ;;
  esac
  DESCRIPTOR="$candidate"
}

schema_checksum() {
  local owner file files
  files="$(find "$ROOT_DIR/init-db/migrations" -maxdepth 1 -type f -name 'V*.sql' -print | sort)"
  for owner in auth admin app notification submission; do
    files="$files
$(find "$ROOT_DIR/init-db/migrations/$owner" -maxdepth 1 -type f -name 'V*.sql' -print | sort)"
  done
  files="$files
$ROOT_DIR/init-db/flyway-post-owner.conf
$(find "$ROOT_DIR/init-db/migrations/post-owner" -maxdepth 1 -type f -name 'V*.sql' -print | sort)"
  [ -n "$files" ] || die "canonical migration manifest is empty"
  while IFS= read -r file; do
    [ -f "$file" ] || die "canonical migration manifest contains a missing file"
  done <<EOF
$files
EOF
  sha256sum $files | sha256sum | awk '{print $1}'
}

build_image_manifest() {
  local name value
  for name in BACKEND_AUTH_IMAGE_REF BACKEND_ADMIN_IMAGE_REF BACKEND_APP_IMAGE_REF \
    BACKEND_SUBMISSION_IMAGE_REF BACKEND_SEARCH_IMAGE_REF BACKEND_NOTIFICATION_IMAGE_REF \
    BACKEND_JUDGE_IMAGE_REF CONSOLE_IMAGE_REF MANAGEMENT_IMAGE_REF; do
    value="$(printenv "$name" 2>/dev/null || true)"
    printf '%s=%s\n' "$name" "$value"
  done
}

validated_manifest() {
  local manifest="$IMAGE_REF_LIST"
  if [ -z "$manifest" ]; then
    manifest="$(build_image_manifest)"
  fi
  IMAGE_REF_LIST="$manifest" "$POLICY" validate >/dev/null
  printf '%s' "$manifest"
}

validate_inputs() {
  local actual_schema actual_commit source_commit_file source_commit manifest
  [ -x "$POLICY" ] || die "image-reference-policy.sh is missing or not executable"
  [ -n "$DEPLOYMENT_COMMIT" ] || die "DEPLOYMENT_COMMIT is required"
  printf '%s' "$DEPLOYMENT_COMMIT" | grep -Eq '^[0-9a-f]{40}$' || die "DEPLOYMENT_COMMIT must be a 40-character commit SHA"
  printf '%s' "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" | grep -Eq '^[0-9a-f]{64}$' \
    || die "EXPECTED_SCHEMA_MANIFEST_CHECKSUM must be a 64-character SHA-256"
  for required in docker-compose.yml docker-compose.prod.yml \
    scripts/runbooks/owner-migration-manifest.sh \
    scripts/runbooks/redis-acl-rotation.sh \
    scripts/runbooks/image-reference-policy.sh; do
    [ -f "$ROOT_DIR/$required" ] || die "required deployment file is missing: $required"
  done
  manifest="$(validated_manifest)"
  actual_schema="$(schema_checksum)"
  [ "$actual_schema" = "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ] \
    || die "migration manifest checksum does not match the approved release"
  actual_commit="$(git -C "$ROOT_DIR" rev-parse --verify HEAD 2>/dev/null || true)"
  if [ -n "$actual_commit" ]; then
    [ "$actual_commit" = "$DEPLOYMENT_COMMIT" ] \
      || die "deployment checkout commit does not match the approved release"
  else
    source_commit_file="$ROOT_DIR/.local/deploy/source-commit"
    [ -s "$source_commit_file" ] \
      || die "deployment source commit is unverifiable (no Git checkout or source-commit file)"
    source_commit="$(tr -d '[:space:]' < "$source_commit_file")"
    [ "$source_commit" = "$DEPLOYMENT_COMMIT" ] \
      || die "deployment source-commit file does not match the approved release"
  fi
}

descriptor_field() {
  local field="$1"
  sed -n "s/^[[:space:]]*\"$field\":[[:space:]]*\"\\([^\"]*\\)\"[,\\]*/\\1/p" "$DESCRIPTOR" | head -1
}

record_descriptor() {
  local status services manifest manifest_summary updated_at temp
  status="$DEPLOYMENT_STATUS"
  [ -n "$status" ] || status=PENDING_HEALTH
  services="$DEPLOYED_SERVICES"
  [ -n "$services" ] || services=all
  case "$status" in
    PENDING_HEALTH|HEALTHY|FAILED) ;;
    *) die "invalid DEPLOYMENT_STATUS" ;;
  esac
  printf '%s' "$services" | grep -Eq '^[A-Za-z0-9_. ,-]+$' \
    || die "DEPLOYED_SERVICES contains unsupported characters"
  manifest="$(validated_manifest)"
  manifest_summary="$(printf '%s\n' "$manifest" | tr '\n' ';')"
  updated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  resolve_descriptor
  mkdir -p "$(dirname "$DESCRIPTOR")"
  chmod 700 "$(dirname "$DESCRIPTOR")"
  temp="$(mktemp "$(dirname "$DESCRIPTOR")/.deployment.XXXXXX")"
  trap 'rm -f "$temp"' RETURN
  umask 077
  {
    printf '{\n'
    printf '  "status": "%s",\n' "$status"
    printf '  "deployment_commit": "%s",\n' "$DEPLOYMENT_COMMIT"
    printf '  "schema_manifest_checksum": "%s",\n' "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM"
    printf '  "services": "%s",\n' "$services"
    printf '  "updated_at": "%s",\n' "$updated_at"
    printf '  "image_manifest": "%s"\n' "$manifest_summary"
    printf '}\n'
  } > "$temp"
  chmod 600 "$temp"
  mv "$temp" "$DESCRIPTOR"
  trap - RETURN
  printf '[deployment-integrity] PASS descriptor=%s status=%s\n' "$DESCRIPTOR" "$status"
}

check_rollback() {
  local stored_schema stored_commit
  resolve_descriptor
  [ -f "$DESCRIPTOR" ] || die "no prior deployment descriptor exists for rollback"
  stored_schema="$(descriptor_field schema_manifest_checksum)"
  stored_commit="$(descriptor_field deployment_commit)"
  [ "$stored_schema" = "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ] \
    || die "rollback schema checksum is incompatible with the deployed database"
  printf '%s' "$stored_commit" | grep -Eq '^[0-9a-f]{40}$' \
    || die "prior deployment descriptor has no valid source commit"
  validate_inputs
  printf '[deployment-integrity] PASS rollback-compatible schema=%s prior_commit=%s\n' \
    "$stored_schema" "$stored_commit"
}

mark_health() {
  local status stored_schema stored_commit temp
  status="$DEPLOYMENT_STATUS"
  case "$status" in
    HEALTHY|FAILED) ;;
    *) die "DEPLOYMENT_STATUS must be HEALTHY or FAILED when marking health" ;;
  esac
  resolve_descriptor
  [ -f "$DESCRIPTOR" ] || die "cannot mark health without a deployment descriptor"
  printf '%s' "$DEPLOYMENT_COMMIT" | grep -Eq '^[0-9a-f]{40}$' \
    || die "DEPLOYMENT_COMMIT must be a 40-character commit SHA"
  printf '%s' "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" | grep -Eq '^[0-9a-f]{64}$' \
    || die "EXPECTED_SCHEMA_MANIFEST_CHECKSUM must be a 64-character SHA-256"
  stored_schema="$(descriptor_field schema_manifest_checksum)"
  stored_commit="$(descriptor_field deployment_commit)"
  [ "$stored_schema" = "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ] \
    || die "health result does not match the deployment schema checksum"
  [ "$stored_commit" = "$DEPLOYMENT_COMMIT" ] \
    || die "health result does not match the deployment commit"
  temp="$(mktemp "$(dirname "$DESCRIPTOR")/.health.XXXXXX")"
  trap 'rm -f "$temp"' RETURN
  sed -E "s/(^[[:space:]]*\"status\":[[:space:]]*\")[A-Z_]+(\",)$/\\1$status\\2/" \
    "$DESCRIPTOR" > "$temp"
  grep -Fq "\"status\": \"$status\"" "$temp" || die "descriptor status update failed"
  chmod 600 "$temp"
  mv "$temp" "$DESCRIPTOR"
  trap - RETURN
  printf '[deployment-integrity] PASS descriptor=%s status=%s\n' "$DESCRIPTOR" "$status"
}

case "$ACTION" in
  schema-checksum)
    schema_checksum
    ;;
  preflight)
    validate_inputs
    printf '[deployment-integrity] PASS preflight commit=%s schema=%s\n' "$DEPLOYMENT_COMMIT" "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM"
    ;;
  record)
    validate_inputs
    record_descriptor
    ;;
  check-rollback)
    check_rollback
    ;;
  mark-health)
    mark_health
    ;;
esac
