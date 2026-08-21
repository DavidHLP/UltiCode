#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

[[ "${DEVSTACK_OWNER_MIGRATION_ORDER[*]}" == "auth admin app notification submission" ]]
[[ "$(devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}")" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge" ]]
[[ "$(devstack_apps_csv "${DEVSTACK_DEV_FULL_BACKEND_APPS[@]}")" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge,ulticode-search" ]]
[[ "$(devstack_backend_apps_for_mode dev-lite)" == "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge" ]]
[[ "$(devstack_apps_for_mode dev-full)" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge,ulticode-search,ulticode-9002,ulticode-9003" ]]
[[ "$(devstack_readiness ulticode-auth)" == "http|9101|/api/v1/auth/health" ]]
[[ "$(devstack_readiness ulticode-submission)" == "pm2" ]]
[[ "$(devstack_readiness ulticode-9003)" == "http|9003|/" ]]
[[ "$(devstack_readiness_banner ulticode-judge)" == "Started BackendJudgeApplication" ]]
[[ -z "$(devstack_readiness_banner ulticode-submission)" ]]
[[ "$DEVSTACK_SERVICE_READINESS_ATTEMPTS" == 90 ]]
[[ "$DEVSTACK_READINESS_INTERVAL_SECONDS" == 2 ]]
[[ "$DEVSTACK_BOOTSTRAP_TIMEOUT_SECONDS" == 90 ]]

(
  unset SUBMISSION_CUTOVER_COMPLETE APP_SEARCH_BACKFILL_ENABLED
  devstack_apply_mode dev-lite
  [[ "$APP_SUBMISSION_ROUTING_MODE" == local ]]
  [[ "$APP_FEATURES_CONTEST_DUBBO_CUTOVER" == true ]]
  [[ "$APP_FEATURES_SUBMISSION_DUBBO_CUTOVER" == false ]]
  [[ "$APP_SEARCH_READ_MODE" == database ]]
  [[ "$APP_SEARCH_FALLBACK_TO_DATABASE" == false ]]
  [[ "$MEILISEARCH_ENABLED" == false ]]
  [[ "$APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED" == false ]]
  [[ "$SEARCH_WORKER_ENABLED" == false ]]
)

(
  unset SUBMISSION_CUTOVER_COMPLETE APP_SEARCH_BACKFILL_ENABLED
  devstack_apply_mode dev-full
  [[ "$APP_SUBMISSION_ROUTING_MODE" == remote ]]
  [[ "$APP_FEATURES_USE_JUDGE_OUTBOX" == true ]]
  [[ "$APP_FEATURES_SUBMISSION_DUBBO_CUTOVER" == true ]]
  [[ "$APP_SEARCH_READ_MODE" == indexed ]]
  [[ "$APP_SEARCH_FALLBACK_TO_DATABASE" == true ]]
  [[ "$MEILISEARCH_ENABLED" == true ]]
  [[ "$APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED" == false ]]
  [[ "$SEARCH_WORKER_ENABLED" == true ]]
)

for app in "${DEVSTACK_READINESS_APPS[@]}"; do
  devstack_readiness "$app" >/dev/null
done

echo "DevStack manifest contract: PASS"
