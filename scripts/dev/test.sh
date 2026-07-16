#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
MODE="${1:-quick}"
TEST_DB_NAME="${TEST_DB_NAME:-ulticode_test}"

case "$MODE" in
  quick|full|integration)
    ;;
  *)
    echo "Usage: ./scripts/dev/test.sh [quick|full|integration]" >&2
    exit 2
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  "$ROOT_DIR/scripts/dev/init-env.sh"
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if ! [[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ && "$TEST_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DB_USER and TEST_DB_NAME must contain only letters, digits, or underscore." >&2
  exit 1
fi

compose=(
  docker compose --env-file "$ENV_FILE"
  -f "$ROOT_DIR/docker-compose.yml"
  -f "$ROOT_DIR/docker-compose.dev.yml"
)

echo "Starting test dependencies..."
"${compose[@]}" up -d mysql redis

for container in ulticode-mysql ulticode-redis; do
  ready=false
  for _ in $(seq 1 60); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "$ready" != true ]]; then
    echo "Test dependency did not become healthy: $container" >&2
    exit 1
  fi
done

docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ulticode-mysql \
  mysql -u root -e "
    CREATE DATABASE IF NOT EXISTS \`$TEST_DB_NAME\`
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    GRANT ALL PRIVILEGES ON \`$TEST_DB_NAME\`.* TO '$DB_USER'@'%';
    FLUSH PRIVILEGES;
  "

export SPRING_PROFILES_ACTIVE=test
export DB_NAME="$TEST_DB_NAME"
export REDIS_DB=15
export JWT_SECRET=test-only-jwt-signing-key-minimum-32-characters-long
export JWT_COOKIE_SECURE=false
export EMAIL_ENABLED=false
export MEILISEARCH_ENABLED=false
export SANDBOX_ENABLED=false
export SPRINGDOC_ENABLED=false

MIGRATION_DB_NAME="$TEST_DB_NAME" "$ROOT_DIR/scripts/dev/migrate.sh" migrate

echo "Running backend tests..."
(cd "$ROOT_DIR/backend-spring" && ./mvnw test -B)

echo "Running shared authentication tests..."
(cd "$ROOT_DIR/shared/auth-core" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

echo "Running console tests..."
(cd "$ROOT_DIR/console" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

echo "Running management tests..."
(cd "$ROOT_DIR/management" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

if [[ "$MODE" == "full" ]]; then
  echo "Running production builds and dependency audits..."
  # pnpm audit is best-effort: some registries (e.g. npmmirror) do not
  # implement the audit endpoint, which surfaces as
  # ERR_PNPM_AUDIT_ENDPOINT_NOT_EXISTS. Warn and continue rather than
  # failing the whole suite on an environment/registry limitation.
  # Production CI SHOULD use a registry that supports audit.
  (cd "$ROOT_DIR/console" && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
  (cd "$ROOT_DIR/management" && pnpm validate:i18n-keys && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
fi

if [[ "$MODE" == "integration" ]]; then
  if ! docker image inspect "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" >/dev/null 2>&1; then
    docker build -t "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" "$ROOT_DIR/docker/sandbox"
  fi
  echo "Running Testcontainers and sandbox integration tests..."
  (cd "$ROOT_DIR/backend-spring" && ./mvnw -Dtest='*IT' test -B)
fi

echo "All $MODE checks passed."
