#!/usr/bin/env bash
set -euo pipefail

stage="${1:-}"
case "$stage" in
  install-ssh-key)
set -euo pipefail
[[ -n "$DEPLOY_KEY" ]] || { echo "::error::deploy SSH key is empty" >&2; exit 1; }
[[ -n "$DEPLOY_KNOWN_HOSTS" ]] || { echo "::error::deploy known_hosts is empty" >&2; exit 1; }
[[ "$DEPLOY_HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "::error::invalid deploy host" >&2; exit 1; }
[[ "$DEPLOY_USER" =~ ^[A-Za-z_][A-Za-z0-9_.-]*$ ]] || { echo "::error::invalid deploy user" >&2; exit 1; }
if [[ ! "$DEPLOY_PORT" =~ ^[0-9]{1,5}$ ]] || (( 10#$DEPLOY_PORT < 1 || 10#$DEPLOY_PORT > 65535 )); then
  echo "::error::invalid deploy port" >&2
  exit 1
fi
[[ "$DEPLOY_PATH" == /* && "$DEPLOY_PATH" != *$'\n'* && "$DEPLOY_PATH" != *$'\r'* && "/$DEPLOY_PATH/" != */../* ]] || { echo "::error::invalid deploy path" >&2; exit 1; }
printf 'DEPLOY_HOST=%s\nDEPLOY_PORT=%s\nDEPLOY_USER=%s\nDEPLOY_PATH=%s\n' \
  "$DEPLOY_HOST" "$DEPLOY_PORT" "$DEPLOY_USER" "$DEPLOY_PATH" >> "$GITHUB_ENV"
install -d -m 700 ~/.ssh
printf '%s\n' "$DEPLOY_KEY" > ~/.ssh/deploy_key
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > ~/.ssh/known_hosts
chmod 600 ~/.ssh/deploy_key ~/.ssh/known_hosts
host_lookup="$DEPLOY_HOST"
[[ "$DEPLOY_PORT" == "22" ]] || host_lookup="[$DEPLOY_HOST]:$DEPLOY_PORT"
ssh-keygen -F "$host_lookup" -f ~/.ssh/known_hosts >/dev/null \
  || { echo "::error::known_hosts has no pinned key for $host_lookup" >&2; exit 1; }
    ;;
  validate-release-and-deployment-integrity-before-mutation)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
[[ -n "$IMAGE_REF_LIST" ]] || { echo "::error::image_refs is required" >&2; exit 1; }
[[ -n "$DEPLOYMENT_COMMIT" ]] || { echo "::error::source_commit is required" >&2; exit 1; }
[[ -n "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ]] || { echo "::error::schema_manifest_checksum is required" >&2; exit 1; }
[[ -n "$COSIGN_CERT_IDENTITY" ]] || { echo "::error::supply_chain_identity is required" >&2; exit 1; }
IMAGE_REF_LIST="$IMAGE_REF_LIST" \
  "$GITHUB_WORKSPACE/scripts/runbooks/image-reference-policy.sh" validate

SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
EXPORTS=""
while IFS= read -r pair; do
  [[ -z "$pair" ]] && continue
  NAME="${pair%%=*}"
  VALUE="${pair#*=}"
  EXPORTS+=" $(shell_quote "$NAME")=$(shell_quote "$VALUE")"
done <<< "$IMAGE_REF_LIST"
REMOTE_ENV="$EXPORTS"
REMOTE_ENV+=" DEPLOYMENT_COMMIT=$(shell_quote "$DEPLOYMENT_COMMIT")"
REMOTE_ENV+=" EXPECTED_SCHEMA_MANIFEST_CHECKSUM=$(shell_quote "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM")"
REMOTE_ENV+=" DEPLOYMENT_DESCRIPTOR_FILE=$(shell_quote "$DEPLOYMENT_DESCRIPTOR_FILE")"
REMOTE_ENV+=" COSIGN_CERT_IDENTITY=$(shell_quote "$COSIGN_CERT_IDENTITY")"
REMOTE_ENV+=" COSIGN_CERT_ISSUER=$(shell_quote "$COSIGN_CERT_ISSUER")"
REMOTE_ENV+=" SUPPLY_CHAIN_EXCEPTION_FILE=$(shell_quote "$SUPPLY_CHAIN_EXCEPTION_FILE")"
REMOTE_ENV+=" SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT=$(shell_quote "$SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT")"

printf '%s\n' "$GITHUB_TOKEN_VALUE" | "${SSH_CMD[@]}" \
  "docker login $(shell_quote "$DEPLOY_REGISTRY") -u $(shell_quote "$DEPLOY_ACTOR") --password-stdin"

remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
remote_command+=" $REMOTE_ENV ./scripts/runbooks/deployment-integrity.sh preflight &&"
if [[ "$SKIP_MIGRATIONS" == "true" ]]; then
  remote_command+=" $REMOTE_ENV ./scripts/runbooks/deployment-integrity.sh check-rollback &&"
fi
remote_command+=" $REMOTE_ENV docker compose --project-directory . -f docker/docker-compose.yml -f docker/docker-compose.prod.yml config --quiet &&"
remote_command+=" $REMOTE_ENV ./scripts/runbooks/image-reference-policy.sh verify"
"${SSH_CMD[@]}" "$remote_command"
    ;;
  validate-remote-sandbox-prerequisites)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
remote_command+=" IMAGE_REF_LIST=$(shell_quote "$IMAGE_REF_LIST") ENV_FILE=.env ./scripts/runbooks/judge-sandbox-preflight.sh"
"${SSH_CMD[@]}" "$remote_command"
    ;;
  validate-deploy-service-compatibility)
set -euo pipefail
require_admin=false
[[ "$SKIP_MIGRATIONS" == true ]] || require_admin=true
"$GITHUB_WORKSPACE/scripts/deploy/service-selection.sh" validate "$DEPLOY_SERVICES" true "$require_admin"
    ;;
  quiesce-legacy-owner-writers)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
STATE_FILE="${RUNNER_TEMP:-/tmp}/ulticode-backend-admin-quiesce-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}.state"
rm -f -- "$STATE_FILE"
SERVICES_NORMALIZED="$(printf '%s' "$DEPLOY_SERVICES" | tr ',' ' ' | xargs)"

[[ -n "$IMAGE_REF_LIST" ]] || { echo "::error::image_refs is required" >&2; exit 1; }
EXPORTS=""
while IFS= read -r pair; do
  [[ -z "$pair" ]] && continue
  NAME="${pair%%=*}"
  VALUE="${pair#*=}"
  EXPORTS+=" $(shell_quote "$NAME")=$(shell_quote "$VALUE")"
done <<< "$IMAGE_REF_LIST"
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
compose_prefix="docker compose --project-directory . -f docker/docker-compose.yml -f docker/docker-compose.prod.yml"
umask 077
mkdir -p "$(dirname "$STATE_FILE")"
printf 'phase=quiesce-pending\n' >"$STATE_FILE"

# The running (pre-upgrade) image has no drain-aware executor, so an
# in-flight mysqldump must reach a terminal row before SIGTERM instead
# of being killed by the stop grace period.
drain_legacy_writer() {
for required in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!required}" ]] || {
    echo "::error::$required is required to drain the legacy Admin backup writer before stopping it" >&2
    exit 1
  }
done
drain_deadline=$(( SECONDS + ${ADMIN_BACKUP_DRAIN_TIMEOUT_SECONDS:-3660} ))
while true; do
  drain_command="cd $(shell_quote "$DEPLOY_PATH") &&"
  for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
    MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
    drain_command+=" $variable=$(shell_quote "${!variable}")"
  done
  drain_command+=" MIGRATION_MYSQL_BIN=$(shell_quote "$MIGRATION_MYSQL_BIN")"
  drain_command+=" MIGRATION_MYSQL_CONTAINER=$(shell_quote "$MIGRATION_MYSQL_CONTAINER")"
  drain_command+=" MIGRATION_MYSQL_CONTAINER_PORT=$(shell_quote "$MIGRATION_MYSQL_CONTAINER_PORT")"
  drain_command+=" ./scripts/runbooks/assert-admin-backup-drained.sh"
  if drain_output="$("${SSH_CMD[@]}" "$drain_command" 2>&1)"; then
    break
  fi
  if (( SECONDS >= drain_deadline )); then
    echo "::error::legacy backend-admin still has PENDING/IN_PROGRESS backups after the drain budget; refusing to stop it mid-dump. Last probe: ${drain_output}" >&2
    exit 1
  fi
  sleep 15
done
}

# Records the previously running containers of one compose service and
# stops it; the failure path restores exactly this set.
quiesce_service() {
  local service="$1" prefix="$2" container_list container_id running ids="" running_flag=0
  remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
  remote_command+=" $EXPORTS $compose_prefix ps -q $service"
  container_list="$("${SSH_CMD[@]}" "$remote_command")"
  while IFS= read -r container_id; do
    container_id="${container_id//$'\r'/}"
    [[ -z "$container_id" ]] && continue
    [[ "$container_id" =~ ^[0-9a-f]+$ ]] || {
      echo "::error::Compose returned an invalid $service container id" >&2
      exit 1
    }
    remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
    remote_command+=" $EXPORTS docker inspect -f '{{.State.Running}}' $(shell_quote "$container_id")"
    running="$("${SSH_CMD[@]}" "$remote_command")"
    if [[ "$running" == true ]]; then
      ids+=" $container_id"
    fi
  done <<< "$container_list"
  ids="${ids# }"
  [[ -n "$ids" ]] && running_flag=1
  printf '%s_container_ids=%s\n%s_prior_running=%s\n' \
    "$prefix" "$ids" "$prefix" "$running_flag" >>"$STATE_FILE"
  if [[ "$running_flag" == 1 ]]; then
    remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
    # Compose applies the service stop grace period while an upgraded
    # image drains its in-flight work.
    remote_command+=" $EXPORTS $compose_prefix stop $service"
    "${SSH_CMD[@]}" "$remote_command"
  fi
  echo "$service quiesced; prior_running=$running_flag"
}

# backend-app must stop before the cutover gate: it still writes legacy
# /uploads/avatars rows until the new image replaces it, and a row
# committed after the gate would resolve to a missing object.
admin_replaced=false
if [[ "$SKIP_MIGRATIONS" != 'true' ]]; then
  # Migrations need the legacy backup writer quiesced.
  admin_replaced=true
elif [[ -z "$SERVICES_NORMALIZED" || "$SERVICES_NORMALIZED" == all ]]; then
  admin_replaced=true
else
  for service in $SERVICES_NORMALIZED; do
    [[ "$service" == backend-admin ]] && admin_replaced=true
  done
fi
if [[ "$admin_replaced" == true ]]; then
  drain_legacy_writer
  quiesce_service backend-admin admin
fi
app_replaced=false
if [[ -z "$SERVICES_NORMALIZED" || "$SERVICES_NORMALIZED" == all ]]; then
  app_replaced=true
else
  for service in $SERVICES_NORMALIZED; do
    [[ "$service" == backend-app ]] && app_replaced=true
  done
fi
if [[ "$app_replaced" == true ]]; then
  # The legacy App keeps writing /uploads/avatars rows until the new
  # image replaces it; a row committed after the cutover gate would
  # resolve to a missing object.
  quiesce_service backend-app app
fi
printf 'phase=stopped\n' >>"$STATE_FILE"
    ;;
  verify-admin-backup-writer-drained)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  remote_command+=" $variable=$(shell_quote "${!variable}")"
done
remote_command+=" MIGRATION_MYSQL_BIN=$(shell_quote "$MIGRATION_MYSQL_BIN")"
remote_command+=" MIGRATION_MYSQL_CONTAINER=$(shell_quote "$MIGRATION_MYSQL_CONTAINER")"
remote_command+=" MIGRATION_MYSQL_CONTAINER_PORT=$(shell_quote "$MIGRATION_MYSQL_CONTAINER_PORT")"
remote_command+=" ./scripts/runbooks/assert-admin-backup-drained.sh"
"${SSH_CMD[@]}" "$remote_command"
    ;;
  run-ordered-owner-database-migrations)
set -euo pipefail
for required in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD SUBMISSION_MIGRATION_DB_USER \
  SUBMISSION_MIGRATION_DB_PASSWORD AUTH_DB_USER ADMIN_DB_USER APP_DB_USER \
  NOTIFICATION_DB_USER SUBMISSION_DB_USER; do
  [[ -n "${!required}" ]] || { echo "::error::$required is required for owner migrations" >&2; exit 1; }
done
STATE_FILE="${RUNNER_TEMP:-/tmp}/ulticode-backend-admin-quiesce-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}.state"
printf 'phase=migration-running\n' >>"$STATE_FILE"

shell_quote() { printf '%q' "$1"; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
remote_command+=" MIGRATION_DB_HOST=$(shell_quote "$MIGRATION_DB_HOST")"
remote_command+=" MIGRATION_DB_PORT=$(shell_quote "$MIGRATION_DB_PORT")"
remote_command+=" MIGRATION_DB_NAME=$(shell_quote "$MIGRATION_DB_NAME")"
remote_command+=" MIGRATION_DB_USER=$(shell_quote "$MIGRATION_DB_USER")"
remote_command+=" MIGRATION_DB_PASSWORD=$(shell_quote "$MIGRATION_DB_PASSWORD")"
remote_command+=" SUBMISSION_MIGRATION_DB_USER=$(shell_quote "$SUBMISSION_MIGRATION_DB_USER")"
remote_command+=" SUBMISSION_MIGRATION_DB_PASSWORD=$(shell_quote "$SUBMISSION_MIGRATION_DB_PASSWORD")"
remote_command+=" AUTH_DB_NAME=auth AUTH_DB_USER=$(shell_quote "$AUTH_DB_USER")"
remote_command+=" ADMIN_DB_NAME=admin ADMIN_DB_USER=$(shell_quote "$ADMIN_DB_USER")"
remote_command+=" APP_DB_NAME=app APP_DB_USER=$(shell_quote "$APP_DB_USER")"
remote_command+=" NOTIFICATION_DB_NAME=notification NOTIFICATION_DB_USER=$(shell_quote "$NOTIFICATION_DB_USER")"
remote_command+=" SUBMISSION_DB_NAME=submission SUBMISSION_DB_USER=$(shell_quote "$SUBMISSION_DB_USER")"
remote_command+=" OWNER_MIGRATION_REPORT_DIR=$(shell_quote "$MIGRATION_REPORT_DIR")"
remote_command+=" OWNER_MIGRATION_LOCK_FILE=$(shell_quote "$MIGRATION_LOCK_FILE")"
remote_command+=" OWNER_MIGRATION_MYSQL_BIN=$(shell_quote "$MIGRATION_MYSQL_BIN")"
remote_command+=" OWNER_MIGRATION_MYSQL_CONTAINER=$(shell_quote "$MIGRATION_MYSQL_CONTAINER")"
remote_command+=" OWNER_MIGRATION_MYSQL_CONTAINER_PORT=$(shell_quote "$MIGRATION_MYSQL_CONTAINER_PORT")"
remote_command+=" ./scripts/runbooks/owner-migration-manifest.sh migrate"
"${SSH_CMD[@]}" "$remote_command"
printf 'phase=migration-complete\n' >>"$STATE_FILE"
    ;;
  verify-legacy-object-backfill-before-serving)
set -euo pipefail
# The gate guards any rollout of this release, including one that skips
# Flyway: skipping migrations must not also skip the cutover check.
for required in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!required}" ]] || {
    echo "::error::$required is required so the object cutover gate can run; a rollback must use CD Rollback, which sets rollback=true" >&2
    exit 1
  }
done
shell_quote() { printf '%q' "$1"; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  remote_command+=" $variable=$(shell_quote "${!variable}")"
done
remote_command+=" MIGRATION_MYSQL_BIN=$(shell_quote "$MIGRATION_MYSQL_BIN")"
remote_command+=" MIGRATION_MYSQL_CONTAINER=$(shell_quote "$MIGRATION_MYSQL_CONTAINER")"
remote_command+=" MIGRATION_MYSQL_CONTAINER_PORT=$(shell_quote "$MIGRATION_MYSQL_CONTAINER_PORT")"
remote_command+=" ./scripts/runbooks/assert-legacy-objects-migrated.sh"
"${SSH_CMD[@]}" "$remote_command"
    ;;
  materialize-runtime-redis-acl)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"
remote_command+=" ./scripts/runbooks/redis-acl-rotation.sh materialize"
"${SSH_CMD[@]}" "$remote_command"
    ;;
  pull-and-deploy-services)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
[[ -n "$IMAGE_REF_LIST" ]] || { echo "::error::image_refs is required" >&2; exit 1; }
[[ -n "$DEPLOYMENT_COMMIT" ]] || { echo "::error::source_commit is required" >&2; exit 1; }
[[ -n "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM" ]] || { echo "::error::schema_manifest_checksum is required" >&2; exit 1; }
[[ -n "$COSIGN_CERT_IDENTITY" ]] || { echo "::error::supply_chain_identity is required" >&2; exit 1; }

SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")

# Turn the validated manifest into shell assignments. The same
# assignments feed the remote verifier and Compose, so no tag fallback
# or independently parsed service override can bypass the policy.
EXPORTS=""
while IFS= read -r pair; do
  [[ -z "$pair" ]] && continue
  NAME="${pair%%=*}"
  VALUE="${pair#*=}"
  EXPORTS+=" $(shell_quote "$NAME")=$(shell_quote "$VALUE")"
done <<< "$IMAGE_REF_LIST"
REMOTE_ENV="$EXPORTS"
REMOTE_ENV+=" DEPLOYMENT_COMMIT=$(shell_quote "$DEPLOYMENT_COMMIT")"
REMOTE_ENV+=" EXPECTED_SCHEMA_MANIFEST_CHECKSUM=$(shell_quote "$EXPECTED_SCHEMA_MANIFEST_CHECKSUM")"
REMOTE_ENV+=" DEPLOYMENT_DESCRIPTOR_FILE=$(shell_quote "$DEPLOYMENT_DESCRIPTOR_FILE")"
REMOTE_ENV+=" COSIGN_CERT_IDENTITY=$(shell_quote "$COSIGN_CERT_IDENTITY")"
REMOTE_ENV+=" COSIGN_CERT_ISSUER=$(shell_quote "$COSIGN_CERT_ISSUER")"
REMOTE_ENV+=" SUPPLY_CHAIN_EXCEPTION_FILE=$(shell_quote "$SUPPLY_CHAIN_EXCEPTION_FILE")"
REMOTE_ENV+=" SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT=$(shell_quote "$SUPPLY_CHAIN_EXCEPTION_EXPIRES_AT")"

# The manifest was verified before mutation; pull/up now records a
# pending descriptor atomically, and host-health marks it final.
remote_command="cd $(shell_quote "$DEPLOY_PATH") &&"

# Optional selective rollout/rollback: restrict pull/up to an explicit
# allowlisted subset instead of reconciling the whole stack.
TARGETS=""
if [ -n "$DEPLOY_SERVICES" ] && [ "$DEPLOY_SERVICES" != "all" ]; then
  for svc in $(printf '%s' "$DEPLOY_SERVICES" | tr ',' ' ' | xargs); do
    case "$svc" in
      backend-auth|backend-admin|backend-app|backend-submission|backend-notification|backend-search|backend-judge|console|management) ;;
      *) echo "::error::Invalid deployable service: ${svc}" >&2; exit 1 ;;
    esac
    TARGETS="$TARGETS $svc"
  done
fi

remote_command+=" $REMOTE_ENV docker compose --project-directory . -f docker/docker-compose.yml -f docker/docker-compose.prod.yml pull$TARGETS &&"
remote_command+=" $REMOTE_ENV docker compose --project-directory . -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d --remove-orphans$TARGETS &&"
remote_command+=" $REMOTE_ENV DEPLOYED_SERVICES=$(shell_quote "${DEPLOY_SERVICES:-all}") DEPLOYMENT_STATUS=PENDING_HEALTH ./scripts/runbooks/deployment-integrity.sh record"
"${SSH_CMD[@]}" "$remote_command"
STATE_FILE="${RUNNER_TEMP:-/tmp}/ulticode-backend-admin-quiesce-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}.state"
rm -f -- "$STATE_FILE"
    ;;
  restore-quiesced-services-after-deploy-failure)
set -euo pipefail
shell_quote() { printf '%q' "$1"; }
STATE_FILE="${RUNNER_TEMP:-/tmp}/ulticode-backend-admin-quiesce-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}.state"
[[ -s "$STATE_FILE" ]] || exit 0
phase=""
admin_container_ids=""
admin_prior_running=0
app_container_ids=""
app_prior_running=0
while IFS='=' read -r key value; do
  case "$key" in
    phase) phase="$value" ;;
    admin_container_ids) admin_container_ids="$value" ;;
    admin_prior_running) admin_prior_running="$value" ;;
    app_container_ids) app_container_ids="$value" ;;
    app_prior_running) app_prior_running="$value" ;;
  esac
done <"$STATE_FILE"

SSH_CMD=(ssh -i ~/.ssh/deploy_key -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts" -p "$DEPLOY_PORT"
  "$DEPLOY_USER@$DEPLOY_HOST")

restore_service() {
  local service="$1" container_ids="$2" prior_running="$3" START_ARGS="" container_id
  [[ "$prior_running" == 1 ]] || return 0
  [[ -n "$container_ids" ]] || {
    echo "::error::$service recovery state has no prior container ids" >&2
    exit 1
  }
  for container_id in $container_ids; do
    [[ "$container_id" =~ ^[0-9a-f]+$ ]] || {
      echo "::error::$service recovery state has an invalid container id" >&2
      exit 1
    }
    START_ARGS+=" $(shell_quote "$container_id")"
  done
  remote_command="cd $(shell_quote "$DEPLOY_PATH") && docker start$START_ARGS"
  if ! "${SSH_CMD[@]}" "$remote_command"; then
    echo "::error::Failed to restore the previously running $service container(s)" >&2
    exit 1
  fi
  for container_id in $container_ids; do
    remote_command="cd $(shell_quote "$DEPLOY_PATH") && docker inspect -f '{{.State.Running}}' $(shell_quote "$container_id")"
    [[ "$("${SSH_CMD[@]}" "$remote_command")" == true ]] || {
      echo "::error::Restored $service container is not running: $container_id" >&2
      exit 1
    }
  done
  echo "Restored $service to its pre-migration running state."
}

case "$phase" in
  quiesce-pending|stopped)
    restore_service backend-admin "$admin_container_ids" "$admin_prior_running"
    restore_service backend-app "$app_container_ids" "$app_prior_running"
    rm -f -- "$STATE_FILE"
    ;;
  migration-running|migration-complete)
    rm -f -- "$STATE_FILE"
    echo "::error::backend-admin remains stopped: owner migration began; inspect the migration/deployment report before any manual restart" >&2
    exit 1
    ;;
  *)
    rm -f -- "$STATE_FILE"
    echo "::error::Unknown backend-admin quiesce phase: ${phase:-missing}" >&2
    exit 1
    ;;
esac
    ;;
  *) echo "usage: deploy.sh {install-ssh-key|validate-release-and-deployment-integrity-before-mutation|validate-remote-sandbox-prerequisites|validate-deploy-service-compatibility|quiesce-legacy-owner-writers|verify-admin-backup-writer-drained|run-ordered-owner-database-migrations|verify-legacy-object-backfill-before-serving|materialize-runtime-redis-acl|pull-and-deploy-services|restore-quiesced-services-after-deploy-failure}" >&2; exit 2 ;;
esac
