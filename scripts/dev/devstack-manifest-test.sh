#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

assert_file_contains() {
  local file="$1" expected="$2"
  [[ -f "$ROOT_DIR/$file" ]] || { echo "missing guarded file: $file" >&2; return 1; }
  grep -F -- "$expected" "$ROOT_DIR/$file" >/dev/null
}

assert_file_not_contains() {
  local file="$1" unexpected="$2"
  [[ -f "$ROOT_DIR/$file" ]] || { echo "missing guarded file: $file" >&2; return 1; }
  ! grep -F -- "$unexpected" "$ROOT_DIR/$file" >/dev/null
}

[[ "${DEVSTACK_OWNER_MIGRATION_ORDER[*]}" == "auth admin app notification submission" ]]
[[ "$(devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}")" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge" ]]
[[ "$(devstack_apps_csv "${DEVSTACK_DEV_FULL_BACKEND_APPS[@]}")" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge,ulticode-search" ]]
[[ "$(devstack_backend_apps_for_mode dev-lite)" == "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge" ]]
[[ "$(devstack_apps_for_mode dev-full)" == \
  "ulticode-auth,ulticode-admin,ulticode-app,ulticode-submission,ulticode-notification,ulticode-judge,ulticode-search,ulticode-9002,ulticode-9003" ]]
[[ "$(devstack_readiness ulticode-auth)" == "http|9101|/api/v1/auth/health/ready" ]]
[[ "$(devstack_readiness ulticode-submission)" == "pm2" ]]
[[ "$(devstack_readiness ulticode-9003)" == "http|9003|/" ]]
[[ "$(devstack_readiness_banner ulticode-judge)" == "Started BackendJudgeApplication" ]]
[[ -z "$(devstack_readiness_banner ulticode-submission)" ]]
[[ "$DEVSTACK_SERVICE_READINESS_ATTEMPTS" == 90 ]]
[[ "$DEVSTACK_READINESS_INTERVAL_SECONDS" == 2 ]]
[[ "$DEVSTACK_BOOTSTRAP_TIMEOUT_SECONDS" == 90 ]]

# The manifest is the policy source, but direct local boot defaults must not
# silently create a different architecture when a contributor runs one Owner.
assert_file_contains services/app/app-web/src/main/resources/application.yml 'mode: ${APP_RUNTIME_MODE:dev-lite}'
assert_file_contains services/submission/src/main/resources/application.yml 'mode: ${APP_RUNTIME_MODE:dev-lite}'
assert_file_contains services/judge/src/main/resources/application.yml 'mode: ${APP_RUNTIME_MODE:dev-lite}'
assert_file_contains services/app/app-web/src/main/resources/application.yml 'use-judge-outbox: ${APP_FEATURES_USE_JUDGE_OUTBOX:true}'
assert_file_contains services/submission/src/main/resources/application.yml 'use-judge-outbox: ${APP_FEATURES_USE_JUDGE_OUTBOX:true}'
assert_file_contains services/judge/src/main/resources/application.yml 'use-judge-outbox: ${APP_FEATURES_USE_JUDGE_OUTBOX:true}'
assert_file_contains services/app/app-web/src/main/resources/application.yml 'use-port: ${APP_FEATURES_JUDGE_QUEUE_USE_PORT:true}'
assert_file_contains services/submission/src/main/resources/application.yml 'use-port: ${APP_FEATURES_JUDGE_QUEUE_USE_PORT:true}'
assert_file_contains services/judge/src/main/resources/application.yml 'use-port: ${APP_FEATURES_JUDGE_QUEUE_USE_PORT:true}'
assert_file_contains services/app/app-web/src/main/resources/application.yml 'mode: ${APP_SEARCH_READ_MODE:database}'
assert_file_contains services/app/app-web/src/main/resources/application.yml 'fallback-to-database: ${APP_SEARCH_FALLBACK_TO_DATABASE:false}'
assert_file_contains .env.example 'APP_SUBMISSION_ROUTING_MODE=local'
assert_file_contains scripts/dev/init-env.sh 'APP_SUBMISSION_ROUTING_MODE=local'
assert_file_contains scripts/dev/up.sh 'source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"'
assert_file_contains ecosystem.config.cjs "APP_FEATURES_CONTEST_DUBBO_CUTOVER: process.env.APP_FEATURES_CONTEST_DUBBO_CUTOVER || 'true'"
assert_file_contains ecosystem.config.cjs "APP_RUNTIME_MODE: process.env.APP_RUNTIME_MODE || 'dev-lite'"
assert_file_contains ecosystem.config.cjs "APP_FEATURES_USE_JUDGE_OUTBOX: process.env.APP_FEATURES_USE_JUDGE_OUTBOX || 'true'"
assert_file_contains ecosystem.config.cjs "APP_FEATURES_USE_GENERATION_FENCE: process.env.APP_FEATURES_USE_GENERATION_FENCE || 'true'"
assert_file_contains ecosystem.config.cjs "APP_FEATURES_JUDGE_QUEUE_USE_PORT: process.env.APP_FEATURES_JUDGE_QUEUE_USE_PORT || 'true'"
assert_file_contains docker-compose.prod.yml 'APP_SEARCH_READ_MODE=indexed'
assert_file_contains docker-compose.prod.yml 'APP_SEARCH_FALLBACK_TO_DATABASE=true'
assert_file_contains PROJECT_DOCUMENTATION.md 'Phase 7'
assert_file_contains README.md './scripts/dev/up.sh --mode dev-lite'
assert_file_contains README.md './scripts/dev/up.sh --mode dev-full'
assert_file_not_contains README.md 'pm2 restart ulticode-auth ulticode-admin'
assert_file_not_contains README.md 'spring-boot:run -Dmaven.test.skip=true'
if grep -Eq 'APP_FEATURES_JUDGE_QUEUE_ENVELOPE_VERSION|envelope-version:' \
  "$ROOT_DIR/services/app/app-web/src/main/resources/application.yml" \
  "$ROOT_DIR/services/judge/src/main/resources/application.yml" \
  "$ROOT_DIR/scripts/dev/devstack-manifest.sh" \
  "$ROOT_DIR/docker-compose.prod.yml"; then
  echo 'obsolete Judge envelope-version configuration remains' >&2
  exit 1
fi

(
  unset SUBMISSION_CUTOVER_COMPLETE APP_SEARCH_BACKFILL_ENABLED
  devstack_apply_mode dev-lite
  [[ "$APP_RUNTIME_MODE" == dev-lite ]]
  [[ "$APP_SUBMISSION_ROUTING_MODE" == local ]]
  [[ "$APP_FEATURES_USE_JUDGE_OUTBOX" == true ]]
  [[ "$APP_FEATURES_USE_GENERATION_FENCE" == true ]]
  [[ "$APP_FEATURES_JUDGE_QUEUE_USE_PORT" == true ]]
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
  [[ "$APP_RUNTIME_MODE" == dev-full ]]
  [[ "$APP_SUBMISSION_ROUTING_MODE" == remote ]]
  [[ "$APP_FEATURES_USE_JUDGE_OUTBOX" == true ]]
  [[ "$APP_FEATURES_USE_GENERATION_FENCE" == true ]]
  [[ "$APP_FEATURES_JUDGE_QUEUE_USE_PORT" == true ]]
  [[ "$APP_FEATURES_SUBMISSION_DUBBO_CUTOVER" == true ]]
  [[ "$APP_SEARCH_READ_MODE" == indexed ]]
  [[ "$APP_SEARCH_FALLBACK_TO_DATABASE" == true ]]
  [[ "$MEILISEARCH_ENABLED" == true ]]
  [[ "$APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED" == false ]]
  [[ "$SEARCH_WORKER_ENABLED" == true ]]
)

(
  unset SUBMISSION_CUTOVER_COMPLETE APP_SEARCH_BACKFILL_ENABLED
  devstack_apply_mode legacy-rollback
  [[ "$APP_RUNTIME_MODE" == legacy-rollback ]]
  [[ "$APP_SUBMISSION_ROUTING_MODE" == local ]]
  # Rollback topology: flag trio false (validator) + App compatibility on;
  # Judge worker must not be in the app set or the RQueue gets two consumers.
  [[ "$APP_FEATURES_USE_JUDGE_OUTBOX" == false ]]
  [[ "$APP_FEATURES_USE_GENERATION_FENCE" == false ]]
  [[ "$APP_FEATURES_JUDGE_QUEUE_USE_PORT" == false ]]
  [[ "$APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED" == true ]]
  [[ "$(devstack_apps_for_mode legacy-rollback)" != *ulticode-judge* ]]
)

for app in "${DEVSTACK_READINESS_APPS[@]}"; do
  devstack_readiness "$app" >/dev/null
done

echo "DevStack manifest contract: PASS"
