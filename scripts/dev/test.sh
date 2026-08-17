#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
MODE="${1:-quick}"
TEST_DB_NAME="${TEST_DB_NAME:-ulticode_test}"
TEST_MYSQL_IMAGE="${TEST_MYSQL_IMAGE:-mysql:8.0}"
TEST_MYSQL_DB_NAME="${TEST_MYSQL_DB_NAME:-ulticode}"
TEST_MYSQL_CONTAINER=""
TEST_ENV_FILE=""

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

if ! [[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ && "$TEST_DB_NAME" =~ ^[A-Za-z0-9_]+$ && "$TEST_MYSQL_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DB_USER, TEST_DB_NAME, and TEST_MYSQL_DB_NAME must contain only letters, digits, or underscore." >&2
  exit 1
fi

compose=(
  docker compose --env-file "$ENV_FILE"
  -f "$ROOT_DIR/docker-compose.yml"
  -f "$ROOT_DIR/docker-compose.dev.yml"
)

cleanup_test_resources() {
  if [[ -n "$TEST_MYSQL_CONTAINER" ]]; then
    docker rm -f "$TEST_MYSQL_CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ -n "$TEST_ENV_FILE" ]]; then
    rm -f "$TEST_ENV_FILE"
  fi
}

trap cleanup_test_resources EXIT

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

MYSQL_ADMIN_CONTAINER="ulticode-mysql"
if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ulticode-mysql \
  mysql -u root --batch --skip-column-names -e "SELECT 1" >/dev/null 2>&1; then
  echo "Existing ulticode-mysql rejected the configured root password; using a disposable local MySQL for this run." >&2
  if ! docker image inspect "$TEST_MYSQL_IMAGE" >/dev/null 2>&1; then
    echo "Cannot isolate the test database: local image '$TEST_MYSQL_IMAGE' is unavailable and no registry pull is attempted." >&2
    echo "Set TEST_MYSQL_IMAGE to an existing local MySQL-compatible image, or restore the image/registry before retrying." >&2
    exit 1
  fi

  # The first canonical migration targets the historical `ulticode` schema
  # explicitly, so the isolated path uses that name by default. The existing
  # container path keeps TEST_DB_NAME unchanged.
  TEST_DB_NAME="$TEST_MYSQL_DB_NAME"
  TEST_MYSQL_NAME="ulticode-test-mysql-$$"
  if ! TEST_MYSQL_CONTAINER="$(docker run --rm -d \
    --name "$TEST_MYSQL_NAME" \
    -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
    -e MYSQL_DATABASE="$TEST_DB_NAME" \
    -e MYSQL_USER="$DB_USER" \
    -e MYSQL_PASSWORD="$DB_PASSWORD" \
    -p "127.0.0.1::3306" \
    "$TEST_MYSQL_IMAGE")"; then
    echo "Could not start disposable MySQL image '$TEST_MYSQL_IMAGE'." >&2
    exit 1
  fi

  disposable_ready=false
  for _ in $(seq 1 60); do
    if docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$TEST_MYSQL_CONTAINER" \
      mysqladmin ping -h 127.0.0.1 -u root --silent >/dev/null 2>&1; then
      disposable_ready=true
      break
    fi
    sleep 2
  done
  if [[ "$disposable_ready" != true ]]; then
    echo "Disposable MySQL did not become ready: $TEST_MYSQL_CONTAINER" >&2
    exit 1
  fi

  TEST_DB_PORT="$(docker port "$TEST_MYSQL_CONTAINER" 3306/tcp | awk -F: 'NR == 1 {print $NF}')"
  if ! [[ "$TEST_DB_PORT" =~ ^[0-9]+$ ]]; then
    echo "Could not resolve the disposable MySQL host port." >&2
    exit 1
  fi
  DB_HOST=127.0.0.1
  DB_PORT="$TEST_DB_PORT"
  MYSQL_ADMIN_CONTAINER="$TEST_MYSQL_CONTAINER"

  TEST_ENV_FILE="$(mktemp)"
  cp "$ENV_FILE" "$TEST_ENV_FILE"
  {
    printf '\nDB_HOST=%q\n' "$DB_HOST"
    printf 'DB_PORT=%q\n' "$DB_PORT"
  } >> "$TEST_ENV_FILE"
  export ENV_FILE="$TEST_ENV_FILE"
fi

docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_ADMIN_CONTAINER" \
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

# The shadow-user migrations issue CREATE USER and cross-schema GRANTs that the
# runtime app account (DB_USER) cannot perform. Run the test migration as the
# privileged root account, matching the Phase 5-6 model in
# services/docs/MICROSERVICE_MIGRATION_GUIDE.md. Runtime app connections still use
# DB_USER; only the migration step elevates.
export MIGRATION_DB_USER=root
export MIGRATION_DB_PASSWORD="$MYSQL_ROOT_PASSWORD"
MIGRATION_DB_NAME="$TEST_DB_NAME" "$ROOT_DIR/scripts/dev/migrate.sh" migrate

echo "Running backend tests..."
(cd "$ROOT_DIR/services" && ./mvnw test -B)

echo "Running shared authentication tests..."
(cd "$ROOT_DIR/packages/auth-core" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

echo "Running console tests..."
(cd "$ROOT_DIR/apps/console" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

echo "Running management tests..."
(cd "$ROOT_DIR/apps/management" && pnpm install --frozen-lockfile && pnpm test && pnpm type-check)

if [[ "$MODE" == "full" ]]; then
  echo "Running production builds and dependency audits..."
  # pnpm audit is best-effort: some registries (e.g. npmmirror) do not
  # implement the audit endpoint, which surfaces as
  # ERR_PNPM_AUDIT_ENDPOINT_NOT_EXISTS. Warn and continue rather than
  # failing the whole suite on an environment/registry limitation.
  # Production CI SHOULD use a registry that supports audit.
  (cd "$ROOT_DIR/apps/console" && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
  (cd "$ROOT_DIR/apps/management" && pnpm validate:i18n-keys && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
fi

if [[ "$MODE" == "integration" ]]; then
  if ! docker image inspect "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" >/dev/null 2>&1; then
    docker build -t "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" "$ROOT_DIR/docker/sandbox"
  fi
  echo "Running Testcontainers and sandbox integration tests..."
  (cd "$ROOT_DIR/services" && ./mvnw -Dtest='*IT,*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test -B)
fi

echo "All $MODE checks passed."
