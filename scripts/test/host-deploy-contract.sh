#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SELECTOR="$ROOT_DIR/scripts/deploy/service-selection.sh"
ACTION="$ROOT_DIR/.github/actions/host-deploy/action.yml"
ROLLBACK="$ROOT_DIR/.github/workflows/cd-rollback.yml"

"$SELECTOR" validate all true false
"$SELECTOR" validate "" true false
GITHUB_WORKSPACE="$ROOT_DIR" DEPLOY_SERVICES='' SKIP_MIGRATIONS=false \
  "$ROOT_DIR/scripts/deploy/deploy.sh" validate-deploy-service-compatibility
"$SELECTOR" validate
"$SELECTOR" validate 'backend-admin, backend-app' true false
"$SELECTOR" validate backend-auth true false
"$SELECTOR" validate backend-auth true false

# shellcheck disable=SC2016
for invalid in \
  ' , ' \
  '   ' \
  $'backend-auth\ninvalid-service' \
  $'backend-auth\nbackend-auth' \
  'backend-admin' \
  'backend-app' \
  'backend-admin backend-auth' \
  'backend-auth;touch /tmp/host-deploy-contract-pwned' \
  'backend-auth $(id)' \
  'backend-auth backend-auth' \
  $'backend-auth\nbackend-app' \
  $'backend-auth\rbackend-app' \
  'backend-admin,backend-admin' \
  'backend-admin backend-app backend-app'; do
  if "$SELECTOR" validate "$invalid" true false >/dev/null 2>&1; then
    printf 'host-deploy-contract: accepted invalid service selection: %s\n' "$invalid" >&2
    exit 1
  fi
done

if "$SELECTOR" validate backend-auth true true >/dev/null 2>&1; then
  echo 'host-deploy-contract: migration selection omitted backend-admin' >&2
  exit 1
fi

grep -Fq '"$GITHUB_WORKSPACE/scripts/deploy/deploy.sh" validate-deploy-service-compatibility' "$ACTION"
grep -Fq 'service-selection.sh" validate "$DEPLOY_SERVICES" true "$require_admin"' "$ROOT_DIR/scripts/deploy/deploy.sh"
grep -Fq 'scripts/deploy/service-selection.sh validate "$SELECTED_SERVICES" true' "$ROLLBACK"
if awk '/name: Validate deploy service compatibility/ { checking=1; next } checking && /if:.*rollback/ { exit 1 } checking && /Quiesce legacy owner writers/ { exit 0 }' "$ACTION"; then
  :
else
  echo 'host-deploy-contract: service compatibility is skipped for rollback' >&2
  exit 1
fi

printf 'host-deploy-contract: PASS\n'
