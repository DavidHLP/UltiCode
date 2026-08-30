#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROD="$ROOT_DIR/docker-compose.prod.yml"
BASE="$ROOT_DIR/docker-compose.yml"

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
if ! grep -Eq "VALUES[[:space:]]*\('nacos',.*,[[:space:]]*FALSE\)" "$ROOT_DIR/docker/initdb/01-nacos-init.sql"; then
  echo "Nacos initialization must keep the built-in account disabled" >&2
  exit 1
fi
if grep -Eq "VALUES[[:space:]]*\('nacos',.*,[[:space:]]*TRUE\)" "$ROOT_DIR/docker/initdb/01-nacos-init.sql"; then
  echo "default Nacos account is enabled in initialization SQL" >&2
  exit 1
fi

echo "Nacos security contract: PASS"
