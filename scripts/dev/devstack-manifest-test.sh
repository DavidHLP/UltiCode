#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

[[ "${DEVSTACK_OWNER_MIGRATION_ORDER[*]}" == "auth admin app notification submission" ]]
[[ "$(devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}")" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge" ]]
[[ "$(devstack_readiness ulticode-auth)" == "http|9101|/api/v1/auth/health" ]]
[[ "$(devstack_readiness ulticode-submission)" == "pm2" ]]
[[ "$(devstack_readiness ulticode-9003)" == "http|9003|/" ]]

for app in "${DEVSTACK_READINESS_APPS[@]}"; do
  devstack_readiness "$app" >/dev/null
done

echo "DevStack manifest contract: PASS"
