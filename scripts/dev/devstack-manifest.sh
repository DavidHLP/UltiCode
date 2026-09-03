#!/usr/bin/env bash

# Declarative local DevStack contract. Keep this file data-first: lifecycle
# scripts own execution, while this manifest owns the scope graph, order, and
# readiness surface. Every scope has one deterministic app/infra definition.

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
DEVSTACK_ALL_APPS=("${DEVSTACK_READINESS_APPS[@]}")

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

# Scenario matrix. Keep the named journeys explicit instead of deriving a
# scope from file count or from all available owners:
#
#   scope            PM2 apps                                  Compose infra
#   dev-lite         six existing backend apps                  mysql redis nacos
#   dev-full         nine existing apps                         + meilisearch
#   app-journey      auth app notification submission judge     mysql redis nacos
#                    console
#   admin            auth admin app notification submission     mysql redis nacos
#                    management
#   submission-judge app submission judge                       mysql redis nacos
#   search           auth app search console                    mysql redis nacos meilisearch
#   full-stack       nine existing apps                         mysql redis nacos meilisearch
#
# Observability is never part of a scope's default infra. Use the explicit
# --observability lifecycle flag to append its Compose services.
DEVSTACK_SCOPES=(
  dev-lite
  dev-full
  app-journey
  admin
  submission-judge
  search
  full-stack
)
DEVSTACK_SCOPE_APP_JOURNEY_APPS=(
  ulticode-auth
  ulticode-app
  ulticode-notification
  ulticode-submission
  ulticode-judge
  ulticode-9002
)
DEVSTACK_SCOPE_ADMIN_APPS=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-notification
  ulticode-submission
  ulticode-9003
)
DEVSTACK_SCOPE_SUBMISSION_JUDGE_APPS=(
  ulticode-app
  ulticode-submission
  ulticode-judge
)
DEVSTACK_SCOPE_SEARCH_APPS=(
  ulticode-auth
  ulticode-app
  ulticode-search
  ulticode-9002
)
DEVSTACK_OBSERVABILITY_INFRA=(
  otel-collector
  prometheus
  alertmanager
  tempo
  loki
  grafana
)

DEVSTACK_REQUIRED_BASE_VARS=(
  DB_USER DB_PASSWORD DB_NAME MYSQL_ROOT_PASSWORD JWT_SECRET
  AUTH_REDIS_PASSWORD ADMIN_REDIS_PASSWORD APP_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
  REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
  DUBBO_NAMESPACE NACOS_USERNAME NACOS_PASSWORD NACOS_AUTH_TOKEN
  AUTH_NACOS_USERNAME AUTH_NACOS_PASSWORD ADMIN_NACOS_USERNAME ADMIN_NACOS_PASSWORD \
  APP_NACOS_USERNAME APP_NACOS_PASSWORD SUBMISSION_NACOS_USERNAME SUBMISSION_NACOS_PASSWORD \
  NOTIFICATION_NACOS_USERNAME NOTIFICATION_NACOS_PASSWORD JUDGE_NACOS_USERNAME JUDGE_NACOS_PASSWORD
  NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
)

# Failure timing is part of the DevStack interface. Keep launcher loops and
# bootstrap termination policy out of the lifecycle scripts so the supported
# local contract is inspectable and table-testable.
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
    dev-lite|dev-full) return 0 ;;
    *)
      echo "--mode must be dev-lite or dev-full." >&2
      return 2
      ;;
  esac
}

devstack_validate_scope_name() {
  local scope="$1" supported
  for supported in "${DEVSTACK_SCOPES[@]}"; do
    [[ "$scope" == "$supported" ]] && return 0
  done
  echo "Unknown DevStack scope: ${scope:-<empty>}. Supported scopes: $(devstack_apps_csv "${DEVSTACK_SCOPES[@]}")" >&2
  return 2
}

devstack_scope_for_mode() {
  devstack_validate_mode_name "$1" || return $?
  printf '%s' "$1"
}

devstack_mode_for_scope() {
  devstack_validate_scope_name "$1" || return $?
  case "$1" in
    dev-full|search|full-stack) printf 'dev-full' ;;
    *) printf 'dev-lite' ;;
  esac
}

devstack_scope_apps() {
  devstack_validate_scope_name "$1" || return $?
  case "$1" in
    dev-lite)         devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}" ;;
    dev-full|full-stack) devstack_apps_csv "${DEVSTACK_DEV_FULL_APPS[@]}" ;;
    app-journey)      devstack_apps_csv "${DEVSTACK_SCOPE_APP_JOURNEY_APPS[@]}" ;;
    admin)            devstack_apps_csv "${DEVSTACK_SCOPE_ADMIN_APPS[@]}" ;;
    submission-judge) devstack_apps_csv "${DEVSTACK_SCOPE_SUBMISSION_JUDGE_APPS[@]}" ;;
    search)           devstack_apps_csv "${DEVSTACK_SCOPE_SEARCH_APPS[@]}" ;;
  esac
}

devstack_apps_for_scope() {
  devstack_scope_apps "$1"
}

devstack_backend_apps_for_scope() {
  local IFS=, app out=""
  local apps
  apps="$(devstack_scope_apps "$1")" || return $?
  for app in $apps; do
    devstack_is_frontend_app "$app" && continue
    out="${out:+$out,}$app"
  done
  printf '%s' "$out"
}

devstack_apps_for_mode() {
  case "$1" in
    dev-lite) devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}" ;;
    dev-full) devstack_apps_csv "${DEVSTACK_DEV_FULL_APPS[@]}" ;;
    *) devstack_validate_mode_name "$1" ;;
  esac
}

devstack_backend_apps_for_mode() {
  case "$1" in
    dev-lite) devstack_apps_csv "${DEVSTACK_DEV_LITE_APPS[@]}" ;;
    dev-full) devstack_apps_csv "${DEVSTACK_DEV_FULL_BACKEND_APPS[@]}" ;;
    *) devstack_validate_mode_name "$1" ;;
  esac
}

devstack_is_frontend_app() {
  case "$1" in
    ulticode-9002|ulticode-9003) return 0 ;;
    *) return 1 ;;
  esac
}

devstack_is_known_app() {
  local app
  for app in "${DEVSTACK_ALL_APPS[@]}"; do
    [[ "$1" == "$app" ]] && return 0
  done
  return 1
}

devstack_app_port() {
  case "$1" in
    ulticode-auth)         printf '9101' ;;
    ulticode-admin)        printf '9102' ;;
    ulticode-app)          printf '9103' ;;
    ulticode-judge)        printf '9104' ;;
    ulticode-notification) printf '9105' ;;
    ulticode-submission)   printf '9106' ;;
    ulticode-search)       printf '9107' ;;
    ulticode-9002)         printf '9002' ;;
    ulticode-9003)         printf '9003' ;;
    *)
      echo "Unknown DevStack app port: $1" >&2
      return 2
      ;;
  esac
}

devstack_app_label() {
  case "$1" in
    ulticode-auth)         printf 'Auth Backend' ;;
    ulticode-admin)        printf 'Admin Backend' ;;
    ulticode-app)          printf 'App Backend' ;;
    ulticode-judge)        printf 'Judge Worker' ;;
    ulticode-notification) printf 'Notification Backend' ;;
    ulticode-submission)   printf 'Submission Owner' ;;
    ulticode-search)       printf 'Search Worker' ;;
    ulticode-9002)         printf 'Console Frontend (Vite)' ;;
    ulticode-9003)         printf 'Management Frontend (Vite)' ;;
    *)
      echo "Unknown DevStack app label: $1" >&2
      return 2
      ;;
  esac
}

devstack_normalize_app() {
  case "$1" in
    auth|backend-auth|ulticode-auth|9101) printf 'ulticode-auth' ;;
    admin|backend-admin|ulticode-admin|9102) printf 'ulticode-admin' ;;
    app|backend-app|ulticode-app|9103) printf 'ulticode-app' ;;
    judge|backend-judge|ulticode-judge|9104) printf 'ulticode-judge' ;;
    notification|backend-notification|ulticode-notification|9105) printf 'ulticode-notification' ;;
    submission|backend-submission|ulticode-submission|9106) printf 'ulticode-submission' ;;
    search|backend-search|ulticode-search|9107) printf 'ulticode-search' ;;
    console|ulticode-9002|9002) printf 'ulticode-9002' ;;
    management|ulticode-9003|9003) printf 'ulticode-9003' ;;
    *)
      echo "Unknown PM2 app alias: $1" >&2
      return 2
      ;;
  esac
}

devstack_normalize_apps() {
  local IFS=, input="$1" app normalized out=""
  [[ -n "$input" ]] || {
    echo "PM2 app selection cannot be empty." >&2
    return 2
  }
  for app in $input; do
    app="${app// /}"
    [[ -z "$app" ]] && continue
    normalized="$(devstack_normalize_app "$app")" || return $?
    [[ ",$out," == *",$normalized,"* ]] || out="${out:+$out,}$normalized"
  done
  [[ -n "$out" ]] || {
    echo "PM2 app selection cannot be empty." >&2
    return 2
  }
  printf '%s' "$out"
}

# A scope may be narrowed with --only, but it may not silently gain Search,
# Judge, or a frontend that the named journey did not declare. The two legacy
# mode names remain permissive for backwards-compatible subsets.
devstack_validate_scope_selection() {
  local scope="$1" selected="$2" app
  local IFS=,
  devstack_validate_scope_name "$scope" || return $?
  [[ -n "$selected" ]] || {
    echo "Scope $scope resolved no PM2 apps." >&2
    return 2
  }
  for app in $selected; do
    devstack_is_known_app "$app" || {
      echo "Unknown PM2 app in scope $scope: $app" >&2
      return 2
    }
    if [[ "$scope" != dev-lite && "$scope" != dev-full ]]; then
      if ! [[ ",$(devstack_scope_apps "$scope")," == *",$app,"* ]]; then
        echo "Illegal scope combination: $scope does not include $app." >&2
        return 2
      fi
    fi
    if [[ "$app" == ulticode-search ]] && ! devstack_scope_feature_enabled "$scope" search; then
      echo "Illegal scope combination: Search worker requires a Search-enabled scope (dev-full, search, or full-stack)." >&2
      return 2
    fi
    if [[ "$app" == ulticode-judge ]] && ! devstack_scope_feature_enabled "$scope" judge; then
      echo "Illegal scope combination: Judge worker is not enabled for scope $scope." >&2
      return 2
    fi
  done
}

devstack_scope_features() {
  devstack_validate_scope_name "$1" || return $?
  case "$1" in
    dev-lite)         printf 'search=off;meili=off;judge=on;notification=on;frontend=off;observability=off' ;;
    dev-full)         printf 'search=on;meili=on;judge=on;notification=on;frontend=console,management;observability=off' ;;
    app-journey)      printf 'search=off;meili=off;judge=on;notification=on;frontend=console;observability=off' ;;
    admin)            printf 'search=off;meili=off;judge=off;notification=on;frontend=management;observability=off' ;;
    submission-judge) printf 'search=off;meili=off;judge=on;notification=off;frontend=off;observability=off' ;;
    search)           printf 'search=on;meili=on;judge=off;notification=off;frontend=console;observability=off' ;;
    full-stack)       printf 'search=on;meili=on;judge=on;notification=on;frontend=console,management;observability=off' ;;
  esac
}

devstack_scope_feature_enabled() {
  local scope="$1" feature="$2" features token
  features="$(devstack_scope_features "$scope")" || return $?
  IFS=';' read -ra _features <<< "$features"
  for token in "${_features[@]}"; do
    [[ "$token" == "$feature=on" ]] && return 0
    [[ "$token" == "$feature="* ]] && return 1
  done
  return 1
}

devstack_scope_has_app() {
  local scope="$1" app="$2"
  [[ ",$(devstack_scope_apps "$scope")," == *",$app,"* ]]
}

devstack_scope_infra() {
  devstack_validate_scope_name "$1" || return $?
  case "$1" in
    dev-lite|app-journey|admin|submission-judge) printf 'mysql,redis,nacos' ;;
    dev-full|search|full-stack) printf 'mysql,redis,nacos,meilisearch' ;;
  esac
}

# Resolve Compose targets from the exact selected PM2 set. Search is the only
# path that appends MeiliSearch; observability is an explicit opt-in and never
# inferred from a mode.
devstack_infra_for_selection() {
  local scope="$1" selected="$2" observability="${3:-false}"
  local IFS=, app out="" has_backend=false has_search=false
  devstack_validate_scope_selection "$scope" "$selected" || return $?
  for app in $selected; do
    if devstack_is_frontend_app "$app"; then
      continue
    fi
    has_backend=true
    [[ "$app" == ulticode-search ]] && has_search=true
  done
  if [[ "$has_backend" == true ]]; then
    out='mysql,redis,nacos'
  fi
  if [[ "$has_search" == true ]]; then
    out="${out:+$out,}meilisearch"
  fi
  if [[ "$observability" == true ]]; then
    out="${out:+$out,}$(devstack_apps_csv "${DEVSTACK_OBSERVABILITY_INFRA[@]}")"
  fi
  [[ -n "$out" ]] || {
    echo "No infrastructure is required for the selected frontend-only scope." >&2
    return 0
  }
  printf '%s' "$out"
}

devstack_infra_for_scope() {
  local scope="$1"
  devstack_infra_for_selection "$scope" "$(devstack_scope_apps "$scope")"
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

devstack_readiness_for_selection() {
  local scope="$1" selected="$2" app kind port path
  local IFS=,
  devstack_validate_scope_selection "$scope" "$selected" || return $?
  for app in $selected; do
    IFS='|' read -r kind port path <<< "$(devstack_readiness "$app")"
    printf '%s|%s|%s|%s\n' "$app" "$kind" "${port:-$(devstack_app_port "$app")}" "${path:-}"
  done
}

devstack_readiness_for_scope() {
  local scope="$1"
  devstack_readiness_for_selection "$scope" "$(devstack_scope_apps "$scope")"
}

devstack_ports_for_selection() {
  local scope="$1" selected="$2" app
  local IFS=,
  devstack_validate_scope_selection "$scope" "$selected" || return $?
  for app in $selected; do
    printf '%s|%s|%s\n' "$app" "$(devstack_app_port "$app")" "$(devstack_app_label "$app")"
  done
}

devstack_ports_for_scope() {
  local scope="$1"
  devstack_ports_for_selection "$scope" "$(devstack_scope_apps "$scope")"
}


devstack_join_lines_csv() {
  local line out=""
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    out="${out:+$out,}$line"
  done
  printf '%s' "$out"
}

# Stable machine-readable interface consumed by up/stop/doctor and contract
# tests. Each key is single-line and ordering is fixed.
devstack_resolve_scope() {
  local scope="$1"
  devstack_validate_scope_name "$scope" || return $?
  printf 'scope=%s\n' "$scope"
  printf 'apps=%s\n' "$(devstack_scope_apps "$scope")"
  printf 'infra=%s\n' "$(devstack_infra_for_scope "$scope")"
  printf 'readiness=%s\n' "$(devstack_readiness_for_scope "$scope" | devstack_join_lines_csv)"
  printf 'ports=%s\n' "$(devstack_ports_for_scope "$scope" | devstack_join_lines_csv)"
  printf 'features=%s\n' "$(devstack_scope_features "$scope")"
}

devstack_required_vars() {
  local mode="$1" frontend_only="$2"
  devstack_validate_mode_name "$mode" || return $?
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
  printf '%s\n' APP_SUBMISSION_ROUTING_MODE SUBMISSION_CUTOVER_COMPLETE
}

devstack_validate_environment() {
  local mode="$1" root_dir="$2" frontend_only="$3" prepare_owner="$4"
  devstack_validate_mode_name "$mode" || return $?
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
  if [[ "$prepare_owner" != true ]]; then
    [[ "$APP_SUBMISSION_ROUTING_MODE" == remote ]] || {
      echo "$mode requires APP_SUBMISSION_ROUTING_MODE=remote." >&2
      return 1
    }
    [[ "$SUBMISSION_CUTOVER_COMPLETE" == true ]] || {
      echo "$mode requires SUBMISSION_CUTOVER_COMPLETE=true; run the cutover gate first." >&2
      return 1
    }
  fi
}

devstack_apply_mode() {
  devstack_validate_mode_name "$1" || return $?

  case "$1" in
    dev-lite)
      APP_RUNTIME_MODE=dev-lite
      APP_SUBMISSION_ROUTING_MODE=remote
      SUBMISSION_CUTOVER_COMPLETE="${SUBMISSION_CUTOVER_COMPLETE:-false}"
      APP_FEATURES_USE_JUDGE_OUTBOX=true
      APP_FEATURES_USE_GENERATION_FENCE=true
      APP_FEATURES_JUDGE_QUEUE_USE_PORT=true
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
      APP_FEATURES_CONTEST_DUBBO_CUTOVER=true
      APP_FEATURES_SUBMISSION_DUBBO_CUTOVER=true
      APP_SEARCH_READ_MODE=indexed
      APP_SEARCH_FALLBACK_TO_DATABASE=true
      MEILISEARCH_ENABLED=true
      APP_SEARCH_BACKFILL_ENABLED="${APP_SEARCH_BACKFILL_ENABLED:-false}"
      SEARCH_WORKER_ENABLED=true
      ;;
  esac

  export APP_RUNTIME_MODE APP_SUBMISSION_ROUTING_MODE SUBMISSION_CUTOVER_COMPLETE \
    APP_FEATURES_USE_JUDGE_OUTBOX APP_FEATURES_USE_GENERATION_FENCE \
    APP_FEATURES_JUDGE_QUEUE_USE_PORT \
    APP_FEATURES_CONTEST_DUBBO_CUTOVER APP_FEATURES_SUBMISSION_DUBBO_CUTOVER \
    APP_SEARCH_READ_MODE APP_SEARCH_FALLBACK_TO_DATABASE \
    MEILISEARCH_ENABLED APP_SEARCH_BACKFILL_ENABLED SEARCH_WORKER_ENABLED
}

devstack_apply_scope() {
  local scope="$1"
  local mode
  mode="$(devstack_mode_for_scope "$scope")" || return $?
  devstack_apply_mode "$mode"
  # Scope-specific app capability overrides keep the mode adapters intact while
  # preventing a named journey from silently enabling an optional worker.
  case "$scope" in
    admin|submission-judge)
      SEARCH_WORKER_ENABLED=false
      APP_SEARCH_READ_MODE=database
      APP_SEARCH_FALLBACK_TO_DATABASE=false
      MEILISEARCH_ENABLED=false
      APP_SEARCH_BACKFILL_ENABLED=false
      ;;
    app-journey)
      SEARCH_WORKER_ENABLED=false
      APP_SEARCH_READ_MODE=database
      APP_SEARCH_FALLBACK_TO_DATABASE=false
      MEILISEARCH_ENABLED=false
      APP_SEARCH_BACKFILL_ENABLED=false
      ;;
    search|full-stack|dev-full)
      SEARCH_WORKER_ENABLED=true
      APP_SEARCH_READ_MODE=indexed
      APP_SEARCH_FALLBACK_TO_DATABASE=true
      MEILISEARCH_ENABLED=true
      ;;
  esac
  export APP_SEARCH_READ_MODE APP_SEARCH_FALLBACK_TO_DATABASE \
    MEILISEARCH_ENABLED APP_SEARCH_BACKFILL_ENABLED SEARCH_WORKER_ENABLED
}

devstack_readiness_banner() {
  case "$1" in
    ulticode-judge) printf 'Started BackendJudgeApplication' ;;
    *) printf '' ;;
  esac
}
