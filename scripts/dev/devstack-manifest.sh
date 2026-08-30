#!/usr/bin/env bash

# Declarative local DevStack contract. Keep this file data-first: up.sh owns
# execution, while this manifest owns the order and the supported readiness
# surface for each local mode.

DEVSTACK_OWNER_MIGRATION_ORDER=(auth admin app notification submission)
DEVSTACK_OWNER_APPS=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-submission
  ulticode-notification
)
DEVSTACK_WORKER_APPS=(ulticode-judge ulticode-search)
DEVSTACK_FRONTEND_APPS=(ulticode-9002 ulticode-9003)
DEVSTACK_BACKEND_APPS=("${DEVSTACK_OWNER_APPS[@]}" "${DEVSTACK_WORKER_APPS[0]}")
DEVSTACK_DEV_LITE_APPS=("${DEVSTACK_BACKEND_APPS[@]}")
DEVSTACK_DEV_FULL_BACKEND_APPS=(
  "${DEVSTACK_BACKEND_APPS[@]}"
  "${DEVSTACK_WORKER_APPS[1]}"
)
DEVSTACK_DEV_FULL_APPS=(
  "${DEVSTACK_DEV_FULL_BACKEND_APPS[@]}"
  "${DEVSTACK_FRONTEND_APPS[@]}"
)
DEVSTACK_READINESS_APPS=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-notification
  ulticode-submission
  ulticode-judge
  ulticode-search
  ulticode-9002
  ulticode-9003
)
DEVSTACK_ROLLBACK_APPS=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-submission
  ulticode-notification
)

DEVSTACK_REQUIRED_BASE_VARS=(
  DB_USER DB_PASSWORD DB_NAME MYSQL_ROOT_PASSWORD JWT_SECRET
  AUTH_REDIS_PASSWORD ADMIN_REDIS_PASSWORD APP_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
  DUBBO_NAMESPACE NACOS_USERNAME NACOS_PASSWORD NACOS_AUTH_TOKEN
  AUTH_NACOS_USERNAME AUTH_NACOS_PASSWORD ADMIN_NACOS_USERNAME ADMIN_NACOS_PASSWORD \
  APP_NACOS_USERNAME APP_NACOS_PASSWORD SUBMISSION_NACOS_USERNAME SUBMISSION_NACOS_PASSWORD \
  NOTIFICATION_NACOS_USERNAME NOTIFICATION_NACOS_PASSWORD JUDGE_NACOS_USERNAME JUDGE_NACOS_PASSWORD
  NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
)

# Failure timing is part of the DevStack interface. Keep launcher loops and
# bootstrap termination policy out of up.sh so the supported local contract is
# inspectable and table-testable.
DEVSTACK_INFRA_READINESS_ATTEMPTS=60
DEVSTACK_SERVICE_READINESS_ATTEMPTS=90
DEVSTACK_READINESS_INTERVAL_SECONDS=2
DEVSTACK_DUBBO_REGISTRATION_DELAY_SECONDS=5
DEVSTACK_BOOTSTRAP_TIMEOUT_SECONDS=90
DEVSTACK_BOOTSTRAP_KILL_AFTER_SECONDS=15

devstack_apps_csv() {
  local IFS=,
  printf '%s' "$*"
}

devstack_validate_mode_name() {
  case "$1" in
    dev-lite|dev-full|legacy-rollback) return 0 ;;
    *)
      echo "--mode must be dev-lite, dev-full or legacy-rollback." >&2
      return 2
      ;;
  esac
}

devstack_apps_for_mode() {
  case "$1" in
    dev-lite) devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}" ;;
    dev-full) devstack_apps_csv "${DEVSTACK_DEV_FULL_APPS[@]}" ;;
    # Rollback topology: App writes locally and consumes the legacy RQueue
    # itself (judge-compatibility-enabled=true). The Judge worker must NOT
    # run: with use-port=false it would poll the same RQueue and double-judge.
    legacy-rollback) devstack_apps_csv "${DEVSTACK_ROLLBACK_APPS[@]}" ;;
    *) devstack_validate_mode_name "$1" ;;
  esac
}

devstack_backend_apps_for_mode() {
  case "$1" in
    dev-lite) devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}" ;;
    dev-full) devstack_apps_csv "${DEVSTACK_DEV_FULL_BACKEND_APPS[@]}" ;;
    legacy-rollback) devstack_apps_csv "${DEVSTACK_ROLLBACK_APPS[@]}" ;;
    *) devstack_validate_mode_name "$1" ;;
  esac
}

devstack_required_vars() {
  local mode="$1" frontend_only="$2"
  printf '%s\n' "${DEVSTACK_REQUIRED_BASE_VARS[@]}"
  [[ "$frontend_only" == true ]] && return 0

  local owner owner_prefix
  for owner in "${DEVSTACK_OWNER_MIGRATION_ORDER[@]}"; do
    owner_prefix="${owner^^}"
    printf '%s\n' \
      "${owner_prefix}_DB_HOST" "${owner_prefix}_DB_PORT" \
      "${owner_prefix}_DB_NAME" "${owner_prefix}_DB_USER" \
      "${owner_prefix}_DB_PASSWORD"
  done
  printf '%s\n' SUBMISSION_MIGRATION_DB_USER SUBMISSION_MIGRATION_DB_PASSWORD
  if [[ "$mode" == dev-full ]]; then
    printf '%s\n' APP_SUBMISSION_ROUTING_MODE SUBMISSION_CUTOVER_COMPLETE
  fi
}

devstack_validate_environment() {
  local mode="$1" root_dir="$2" frontend_only="$3" prepare_owner="$4"
  [[ "$frontend_only" == true ]] && return 0

  local owner owner_prefix db_name_var
  for owner in "${DEVSTACK_OWNER_MIGRATION_ORDER[@]}"; do
    owner_prefix="${owner^^}"
    db_name_var="${owner_prefix}_DB_NAME"
    [[ "${!db_name_var}" == "$owner" ]] || {
      echo "${db_name_var} must be $owner for the local owner runtime." >&2
      return 1
    }
    [[ -f "$root_dir/init-db/flyway-$owner.conf" ]] || {
      echo "Missing owner Flyway manifest entry: init-db/flyway-$owner.conf" >&2
      return 1
    }
  done
  [[ "$SUBMISSION_DB_USER" == submission_rw ]] || {
    echo "Local PM2 requires SUBMISSION_DB_USER=submission_rw; provision custom production accounts outside up.sh." >&2
    return 1
  }
  if [[ "$prepare_owner" != true && "$mode" == dev-full ]]; then
    [[ "$APP_SUBMISSION_ROUTING_MODE" == remote ]] || {
      echo "dev-full requires APP_SUBMISSION_ROUTING_MODE=remote." >&2
      return 1
    }
    [[ "$SUBMISSION_CUTOVER_COMPLETE" == true ]] || {
      echo "dev-full requires SUBMISSION_CUTOVER_COMPLETE=true; run the cutover gate first." >&2
      return 1
    }
  fi
}

devstack_apply_mode() {
  case "$1" in
    dev-lite)
      APP_RUNTIME_MODE=dev-lite
      APP_SUBMISSION_ROUTING_MODE=local
      SUBMISSION_CUTOVER_COMPLETE=false
      APP_FEATURES_USE_JUDGE_OUTBOX=true
      APP_FEATURES_USE_GENERATION_FENCE=true
      APP_FEATURES_JUDGE_QUEUE_USE_PORT=true
      APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED=false
      APP_FEATURES_CONTEST_DUBBO_CUTOVER=true
      APP_FEATURES_SUBMISSION_DUBBO_CUTOVER=false
      APP_SEARCH_READ_MODE=database
      APP_SEARCH_FALLBACK_TO_DATABASE=false
      MEILISEARCH_ENABLED=false
      APP_SEARCH_BACKFILL_ENABLED=false
      SEARCH_WORKER_ENABLED=false
      ;;
    dev-full)
      APP_RUNTIME_MODE=dev-full
      APP_SUBMISSION_ROUTING_MODE=remote
      SUBMISSION_CUTOVER_COMPLETE="${SUBMISSION_CUTOVER_COMPLETE:-false}"
      APP_FEATURES_USE_JUDGE_OUTBOX=true
      APP_FEATURES_USE_GENERATION_FENCE=true
      APP_FEATURES_JUDGE_QUEUE_USE_PORT=true
      APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED=false
      APP_FEATURES_CONTEST_DUBBO_CUTOVER=true
      APP_FEATURES_SUBMISSION_DUBBO_CUTOVER=true
      APP_SEARCH_READ_MODE=indexed
      APP_SEARCH_FALLBACK_TO_DATABASE=true
      MEILISEARCH_ENABLED=true
      APP_SEARCH_BACKFILL_ENABLED="${APP_SEARCH_BACKFILL_ENABLED:-false}"
      SEARCH_WORKER_ENABLED=true
      ;;
    # Rollback-only mode: App writes submissions locally and consumes the
    # legacy RQueue itself (judge-compatibility-enabled=true). Judge worker
    # is not started (see DEVSTACK_ROLLBACK_APPS) so the RQueue has exactly
    # one consumer. Flag trio false satisfies FlagCombinationValidator.
    legacy-rollback)
      APP_RUNTIME_MODE=legacy-rollback
      APP_SUBMISSION_ROUTING_MODE=local
      SUBMISSION_CUTOVER_COMPLETE=false
      APP_FEATURES_USE_JUDGE_OUTBOX=false
      APP_FEATURES_USE_GENERATION_FENCE=false
      APP_FEATURES_JUDGE_QUEUE_USE_PORT=false
      APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED=true
      APP_FEATURES_CONTEST_DUBBO_CUTOVER=false
      APP_FEATURES_SUBMISSION_DUBBO_CUTOVER=false
      APP_SEARCH_READ_MODE=database
      APP_SEARCH_FALLBACK_TO_DATABASE=false
      MEILISEARCH_ENABLED=false
      APP_SEARCH_BACKFILL_ENABLED=false
      SEARCH_WORKER_ENABLED=false
      ;;
    *)
      echo "--mode must be dev-lite, dev-full or legacy-rollback." >&2
      return 2
      ;;
  esac

  export APP_RUNTIME_MODE APP_SUBMISSION_ROUTING_MODE SUBMISSION_CUTOVER_COMPLETE \
    APP_FEATURES_USE_JUDGE_OUTBOX APP_FEATURES_USE_GENERATION_FENCE \
    APP_FEATURES_JUDGE_QUEUE_USE_PORT \
    APP_FEATURES_JUDGE_COMPATIBILITY_ENABLED \
    APP_FEATURES_CONTEST_DUBBO_CUTOVER APP_FEATURES_SUBMISSION_DUBBO_CUTOVER \
    APP_SEARCH_READ_MODE APP_SEARCH_FALLBACK_TO_DATABASE \
    MEILISEARCH_ENABLED APP_SEARCH_BACKFILL_ENABLED SEARCH_WORKER_ENABLED
}

devstack_readiness() {
  case "$1" in
    # Readiness endpoints (review 2026-08-25 P0): verify DB + Redis.
    ulticode-auth)         printf 'http|9101|/api/v1/auth/health/ready' ;;
    ulticode-admin)        printf 'http|9102|/api/v1/admin/health/ready' ;;
    ulticode-app)          printf 'http|9103|/api/v1/app/health/ready' ;;
    ulticode-notification) printf 'http|9105|/api/v1/notification/health/ready' ;;
    ulticode-submission|ulticode-judge|ulticode-search) printf 'pm2' ;;
    ulticode-9002)         printf 'http|9002|/' ;;
    ulticode-9003)         printf 'http|9003|/' ;;
    *)
      echo "Unknown DevStack readiness app: $1" >&2
      return 2
      ;;
  esac
}

devstack_readiness_banner() {
  case "$1" in
    ulticode-judge) printf 'Started BackendJudgeApplication' ;;
    *) printf '' ;;
  esac
}
