#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROD="$ROOT_DIR/docker-compose.prod.yml"
BASE="$ROOT_DIR/docker-compose.yml"
DUPLICATE_OUTPUT="$(mktemp)"
trap 'rm -f "$DUPLICATE_OUTPUT"' EXIT

# Production must be cluster-only with an operator-supplied peer list.
grep -Fq 'MODE: cluster' "$PROD"
grep -Fq 'NACOS_SERVERS: ${NACOS_SERVERS:?NACOS_SERVERS is required for production cluster mode}' "$PROD"
if grep -Fq 'standalone' "$PROD"; then
  echo "production Nacos override mentions standalone mode" >&2
  exit 1
fi

grep -Fq 'MODE: standalone' "$BASE"
grep -Fq 'NACOS_AUTH_ENABLE: "true"' "$BASE"
grep -Fq 'NACOS_AUTH_ENABLE: "true"' "$PROD"
grep -Fq 'MYSQL_SERVICE_USER: ${NACOS_DB_USER:?NACOS_DB_USER is required}' "$PROD"

authored_services=(auth admin app submission notification judge)
for service in "${authored_services[@]}"; do
  upper="${service^^}"
  grep -Fq "DUBBO_APPLICATION_NAME=backend-${service}" "$PROD"
  grep -Fq 'DUBBO_NAMESPACE=${DUBBO_NAMESPACE:?DUBBO_NAMESPACE is required}' "$PROD"
  grep -Fq 'DUBBO_REGISTRY_USERNAME=${'"$upper"'_NACOS_USERNAME:?' "$PROD"
  grep -Fq 'DUBBO_REGISTRY_PASSWORD=${'"$upper"'_NACOS_PASSWORD:?' "$PROD"
  grep -Fq "DUBBO_APPLICATION_NAME: 'backend-${service}'" "$ROOT_DIR/ecosystem.config.cjs"
  grep -Fq 'DUBBO_NAMESPACE: process.env.DUBBO_NAMESPACE' "$ROOT_DIR/ecosystem.config.cjs"
  grep -Fq "DUBBO_REGISTRY_USERNAME: process.env.${upper}_NACOS_USERNAME" "$ROOT_DIR/ecosystem.config.cjs"
  grep -Fq "DUBBO_REGISTRY_PASSWORD: process.env.${upper}_NACOS_PASSWORD" "$ROOT_DIR/ecosystem.config.cjs"
done
grep -Fq 'NACOS_NAMESPACE=dev' "$ROOT_DIR/.env.example"
grep -Fq 'DUBBO_NAMESPACE=dev' "$ROOT_DIR/.env.example"
grep -Fq 'DUBBO_NAMESPACE=dev' "$ROOT_DIR/scripts/dev/init-env.sh"
if grep -Fq 'DUBBO_REGISTRY_USERNAME=${NACOS_USERNAME' "$PROD" || \
   grep -Fq 'DUBBO_REGISTRY_PASSWORD=${NACOS_PASSWORD' "$PROD"; then
  echo "production service uses shared Nacos credentials" >&2
  exit 1
fi

grep -Fq 'ROLE_ULTICODE_' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'NACOS_SERVICE_USER_PREFIXES' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
for owner in AUTH ADMIN APP SUBMISSION NOTIFICATION JUDGE; do
  grep -Fq "[$owner]='com.ulticode.${owner,,}'" "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" \
    || { echo "Nacos metadata owner prefix is missing: $owner" >&2; exit 1; }
done
grep -Fq 'NACOS_RESOURCE_NAMESPACE=' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq "NACOS_LONGEST_RESOURCE_SUFFIX=':DEFAULT_GROUP:naming/providers:com.ulticode.notification*'" \
  "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
for resource in ':Dubbo-Nacos-Test:config/*' ':DEFAULT_GROUP:config/*' ':dubbo:config/*' ':backend-${prefix,,}:config/*' \
  ':mapping:config/*' ':mapping:config/' \
  ':DEFAULT_GROUP:naming/*' ':DEFAULT_GROUP:naming/backend-' ':DEFAULT_GROUP:naming/providers:'; do
  grep -Fq -- "$resource" "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" \
    || { echo "Nacos service resource scope is missing: $resource" >&2; exit 1; }
done
if grep -Fq 'nacos:config:*' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" || \
   grep -Fq 'nacos:service:*' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"; then
  echo "Nacos service resources must use namespace/group/type paths" >&2
  exit 1
fi
for permission in \
  "\$config_test_resource', 'r'" \
  "\$config_default_resource', 'r'" \
  "\$config_dubbo_resource', 'r'" \
  "\$config_application_resource', 'r'" \
  "\$metadata_read_resource', 'r'" \
  "\$metadata_write_resource', 'w'" \
  "\$metadata_empty_read_resource', 'r'" \
  "\$metadata_empty_write_resource', 'w'" \
  "\$config_test_default_resource', 'r'" \
  "\$naming_read_resource', 'r'" \
  "\$naming_app_write_resource', 'w'" \
  "\$naming_provider_write_resource', 'w'"; do
  grep -Fq -- "$permission" "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" \
    || { echo "Nacos service permission is missing: $permission" >&2; exit 1; }
done
for permission in \
  "\$config_test_resource', 'w'" \
  "\$config_test_default_resource', 'w'" \
  "\$config_default_resource', 'w'" \
  "\$config_dubbo_resource', 'w'" \
  "\$config_application_resource', 'w'" \
  "\$naming_read_resource', 'w'" \
  "\$config_test_resource', 'rw'" \
  "\$config_test_default_resource', 'rw'" \
  "\$config_dubbo_resource', 'rw'" \
  "\$config_application_resource', 'rw'" \
  "\$metadata_read_resource', 'w'" \
  "\$metadata_read_resource', 'rw'" \
  "\$metadata_empty_read_resource', 'w'" \
  "\$metadata_empty_read_resource', 'rw'" \
  "\$naming_read_resource', 'rw'" \
  "\$naming_app_write_resource', 'rw'" \
  "\$naming_provider_write_resource', 'rw'"; do
  if grep -Fq -- "$permission" "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"; then
    echo "Nacos service permission is too broad: $permission" >&2
    exit 1
  fi
done

grep -Fq 'seen_nacos_users' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'service usernames must be unique' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'disabled built-in Nacos username' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'MYSQL_CONTAINER must be the running Compose mysql service' \
  "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'NACOS_EXPECTED_DOCKER_PROJECT' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
grep -Fq 'com.docker.compose.project' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"
for smoke_guard in 'provision_auth_owner_account' '/api/v1/auth/health/ready' 'redact_smoke_output' \
  'NACOS_EXPECTED_DOCKER_PROJECT' 'MIGRATION_ENV_FILE' 'DB_HOST=%q' 'Nacos access token contains unsupported'; do
  grep -Fq -- "$smoke_guard" "$ROOT_DIR/scripts/test/dubbo-nacos-smoke.sh" \
    || { echo "Dubbo/Nacos smoke guard is missing: $smoke_guard" >&2; exit 1; }
done
if grep -Fq 'provider_service_name' "$ROOT_DIR/scripts/test/dubbo-nacos-smoke.sh"; then
  echo "instance-level Dubbo smoke must not assert an interface-level provider service" >&2
  exit 1
fi
grep -Fq 'NACOS_EXPECTED_DOCKER_PROJECT' "$ROOT_DIR/scripts/dev/up.sh"
grep -Fq 'Container did not become healthy: $container' "$ROOT_DIR/scripts/dev/lib/docker.sh"
if grep -Fq 'docker logs --tail 100 "$container"' "$ROOT_DIR/scripts/dev/lib/docker.sh"; then
  echo "shared container health helper must not print raw container logs" >&2
  exit 1
fi
fixture_password="$(openssl rand -hex 16)"
if env \
  MYSQL_ROOT_PASSWORD="$fixture_password" MYSQL_CONTAINER=not-used \
  NACOS_USERNAME=ulticode-admin-user NACOS_PASSWORD="$fixture_password" \
  DUBBO_NAMESPACE=dev \
  NACOS_EXPECTED_DOCKER_PROJECT=ulticode \
  AUTH_NACOS_USERNAME=duplicate-owner AUTH_NACOS_PASSWORD="$fixture_password" \
  ADMIN_NACOS_USERNAME=Duplicate-Owner ADMIN_NACOS_PASSWORD="$fixture_password" \
  APP_NACOS_USERNAME=ulticode-app APP_NACOS_PASSWORD="$fixture_password" \
  SUBMISSION_NACOS_USERNAME=ulticode-submission SUBMISSION_NACOS_PASSWORD="$fixture_password" \
  NOTIFICATION_NACOS_USERNAME=ulticode-notification NOTIFICATION_NACOS_PASSWORD="$fixture_password" \
  JUDGE_NACOS_USERNAME=ulticode-judge JUDGE_NACOS_PASSWORD="$fixture_password" \
  bash "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" >"$DUPLICATE_OUTPUT" 2>&1; then
  echo "duplicate Nacos service username was accepted" >&2
  exit 1
fi
grep -Fq 'service usernames must be unique' "$DUPLICATE_OUTPUT" \
  || { echo "duplicate Nacos service username did not fail before provisioning" >&2; exit 1; }
if ! grep -Fq 'START TRANSACTION;' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" || \
   ! grep -Fq 'COMMIT;' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" || \
   ! grep -Fq 'httpd@sha256:' "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"; then
  echo "Nacos bootstrap must be transactional and use a pinned hash image" >&2
  exit 1
fi
if ! grep -Eq "VALUES[[:space:]]*\('nacos',.*,[[:space:]]*FALSE\)" "$ROOT_DIR/docker/initdb/01-nacos-init.sql"; then
  echo "Nacos initialization must keep the built-in account disabled" >&2
  exit 1
fi
if grep -Eq "VALUES[[:space:]]*\('nacos',.*,[[:space:]]*TRUE\)" "$ROOT_DIR/docker/initdb/01-nacos-init.sql"; then
  echo "default Nacos account is enabled in initialization SQL" >&2
  exit 1
fi

echo "Nacos security contract: PASS"
