#!/usr/bin/env bash

# Declarative local DevStack contract. Keep this file data-first: up.sh owns
# execution, while this manifest owns the order and the supported readiness
# surface for each local mode.

DEVSTACK_OWNER_MIGRATION_ORDER=(auth admin app notification submission)
DEVSTACK_BACKEND_APPS=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-submission
  ulticode-notification
  ulticode-judge
)
DEVSTACK_DEV_LITE_APPS=("${DEVSTACK_BACKEND_APPS[@]}")
DEVSTACK_DEV_FULL_APPS=(
  "${DEVSTACK_BACKEND_APPS[@]}"
  ulticode-9002
  ulticode-9003
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

devstack_apps_csv() {
  local IFS=,
  printf '%s' "$*"
}

devstack_readiness() {
  case "$1" in
    ulticode-auth)         printf 'http|9101|/api/v1/auth/health' ;;
    ulticode-admin)        printf 'http|9102|/api/v1/admin/health' ;;
    ulticode-app)          printf 'http|9103|/api/v1/app/health' ;;
    ulticode-notification) printf 'http|9105|/api/v1/notification/health' ;;
    ulticode-submission|ulticode-judge|ulticode-search) printf 'pm2' ;;
    ulticode-9002)         printf 'http|9002|/' ;;
    ulticode-9003)         printf 'http|9003|/' ;;
    *)
      echo "Unknown DevStack readiness app: $1" >&2
      return 2
      ;;
  esac
}
