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
  schema-checksum|preflight|record|check-rollback|mark-health|describe|verify-registry) ;;
  *)
    echo "Usage: $0 {schema-checksum|preflight|record|check-rollback|mark-health|describe|verify-registry}" >&2
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

verify_registry() {
  local matrix="$ROOT_DIR/.github/services-matrix.json"
  local compose_file="$ROOT_DIR/docker-compose.prod.yml"
  [ -f "$matrix" ] || die "services matrix is missing"
  [ -f "$compose_file" ] || die "production Compose file is missing"
  mapfile -t matrix_services < <(python3 - "$matrix" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    entries = json.load(source)
for entry in entries:
    if entry.get("name", "").startswith("backend-"):
        print(entry["name"])
PY
)
  [ "${#matrix_services[@]}" -gt 0 ] || die "services matrix contains no backend runtimes"
  for service in "${matrix_services[@]}"; do
    grep -Eq "^  ${service}:" "$compose_file" \
      || die "matrix backend is missing from production Compose: $service"
  done
  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    printf '%s\n' "${matrix_services[@]}" | grep -Fxq "$service" \
      || die "production Compose backend is missing from services matrix: $service"
  done < <(sed -n 's/^  \(backend-[a-z0-9-]*\):$/\1/p' "$compose_file")
  printf '[deployment-integrity] PASS registry services=%s\n' "${#matrix_services[@]}"
}

describe_release_set() {
  validate_inputs
  local matrix="${RELEASE_MATRIX_FILE:-$ROOT_DIR/.github/services-matrix.json}"
  case "$matrix" in
    "$ROOT_DIR"/*) ;;
    *) die "release matrix override must stay under the deployment root" ;;
  esac
  [ -f "$matrix" ] || die "release matrix file is missing"
  local pom="$ROOT_DIR/services/pom.xml"
  local output_format="${DEPLOYMENT_OUTPUT_FORMAT:-human}"
  local descriptor_status=not-recorded
  resolve_descriptor
  if [ -f "$DESCRIPTOR" ]; then
    descriptor_status="$(python3 - "$DESCRIPTOR" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    print(json.load(source).get("status", "unknown"))
PY
)"
  fi
  export MATRIX_FILE="$matrix" POM_FILE="$pom" DESCRIPTOR_STATUS="$descriptor_status"
  if [ "$output_format" = "json" ]; then
    python3 - <<'PY'
import json
import os
import re
from pathlib import Path

matrix = json.loads(Path(os.environ["MATRIX_FILE"]).read_text(encoding="utf-8"))
pom = Path(os.environ["POM_FILE"]).read_text(encoding="utf-8")
images = {}
for line in os.environ["IMAGE_REF_LIST"].splitlines():
    if "=" in line:
        name, value = line.split("=", 1)
        images[name] = value
services = []
for entry in matrix:
    property_name = entry.get("maven_version_property")
    version = None
    if property_name:
        match = re.search(
            rf"<{re.escape(property_name)}>([^<]+)</{re.escape(property_name)}>",
            pom,
        )
        version = match.group(1).strip() if match else None
    image_name = entry["name"].replace("-", "_").upper() + "_IMAGE_REF"
    services.append({
        "name": entry["name"],
        "role": entry.get("role"),
        "release_group": entry.get("release_group"),
        "health": entry.get("health"),
        "version": version,
        "image_ref": images.get(image_name),
    })
allowed_roles = {"owner", "worker", "frontend"}
allowed_groups = {"core"}
allowed_health = {"http", "disabled"}
if any(
    item["role"] not in allowed_roles
    or item["release_group"] not in allowed_groups
    or item["health"] not in allowed_health
    for item in services
):
    raise SystemExit("release matrix contains an invalid classification value")
if any(
    not item["image_ref"]
    or "@sha256:" not in item["image_ref"]
    or (item["version"] is None and item["role"] != "frontend")
    for item in services
):
    raise SystemExit("release set is missing an immutable image or version")
print(json.dumps({
    "evidence_level": "repository-static",
    "production_evidence": False,
    "deployment_commit": os.environ["DEPLOYMENT_COMMIT"],
    "schema_manifest_checksum": os.environ["EXPECTED_SCHEMA_MANIFEST_CHECKSUM"],
    "recorded_status": os.environ["DESCRIPTOR_STATUS"],
    "services": services,
}, ensure_ascii=False, sort_keys=True))
PY
    return
  fi
  printf '[deployment-integrity] PASS describe evidence=repository-static production_evidence=false\n'
  printf 'commit=%s schema=%s recorded_status=%s\n' \
    "$DEPLOYMENT_COMMIT" "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" "$descriptor_status"
  python3 - <<'PY'
import json
import os
from pathlib import Path

matrix = json.loads(Path(os.environ["MATRIX_FILE"]).read_text(encoding="utf-8"))
for entry in matrix:
    print(
        f"{entry['name']} role={entry['role']} "
        f"release_group={entry['release_group']} health={entry['health']}"
    )
PY
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
  describe)
    describe_release_set
    ;;
  verify-registry)
    verify_registry
    ;;
esac
