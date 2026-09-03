#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

MODE="${1:-quick}"
TEST_DB_NAME="${TEST_DB_NAME:-ulticode_test}"
TEST_MYSQL_IMAGE="${TEST_MYSQL_IMAGE:-mysql:8.0}"
TEST_MYSQL_DB_NAME="${TEST_MYSQL_DB_NAME:-ulticode}"
TEST_MYSQL_CONTAINER=""
TEST_ENV_FILE=""
MAVEN=()

usage() {
  cat >&2 <<'USAGE'
Usage: ./scripts/dev/test.sh [--describe|quick|full-local|full|integration|static|unit]

Modes:
  static       Read-only zero-infrastructure guardrails and contracts.
  unit         static plus frontend unit checks; backend unit is fail-closed
               until its zero-infrastructure allowlist is defined.
  quick        Compatibility alias for static + unit (deprecated).
  full-local   Containers, migration, Maven verify, and installed frontend
               coverage checks.
  full         full-local plus production builds, audits, and i18n checks.
  integration  full-local plus sandbox/Testcontainers and migration drills.
USAGE
}

describe() {
  cat <<'TABLE'
mode|infrastructure|frontend_dependencies|backend|extras
static|none (no Docker/DB/services/Testcontainers)|none|source/config contracts only|shellcheck, theme, migration-preflight, coverage wiring
unit|none (until U-03 allowlist exists)|existing node_modules only|fail-closed until unit profile; then *IT excluded|type-check, lint:check, unit tests
quick|same as static + unit|existing node_modules only|fail-closed until unit profile; then *IT excluded|deprecated compatibility alias
full-local|MySQL + Redis Compose|pnpm install allowed|Maven verify|migration and coverage
full|MySQL + Redis Compose|pnpm install allowed|Maven verify|build, audit, i18n
integration|MySQL + Redis + sandbox/Testcontainers|pnpm install allowed|Maven verify + *IT|migration safety drill
TABLE
}

die_usage() {
  echo "test.sh: $*" >&2
  usage
  exit 2
}

if [[ "$MODE" == "--describe" ]]; then
  [[ "$#" -eq 1 ]] || die_usage "--describe does not accept additional arguments"
  describe
  exit 0
fi

if [[ "$#" -gt 1 ]]; then
  die_usage "expected one mode argument"
fi

case "$MODE" in
  static|unit|quick|full-local|full|integration)
    ;;
  *)
    die_usage "unknown mode: $MODE"
    ;;
esac

require_command() {
  local command="$1"
  local purpose="$2"
  command -v "$command" >/dev/null 2>&1 \
    || die_usage "$purpose requires '$command' on PATH"
}

require_static_toolchain() {
  require_command node "Static validation"
  require_command python3 "Static validation"
  require_command awk "Static validation"
  require_command find "Static validation"
  require_command xargs "Static validation"
}

require_maven_toolchain() {
  require_command mise "Backend validation"
  [[ -x "$ROOT_DIR/services/mvnw" ]] \
    || die_usage "Backend validation requires executable services/mvnw"
  if ! mise exec java@zulu-17.68.203.0 -- java -version >/dev/null 2>&1; then
    die_usage "Backend validation requires the Java 17 mise toolchain (java@zulu-17.68.203.0)"
  fi
  MAVEN=(mise exec java@zulu-17.68.203.0 -- ./mvnw)
}

bootstrap_message() {
  local relative="$1"
  printf 'Bootstrap command: pnpm --dir %s install --frozen-lockfile\n' "$relative" >&2
}

require_node_modules() {
  local relative="$1"
  local directory="$ROOT_DIR/$relative"
  if [[ ! -d "$directory/node_modules" ]]; then
    echo "Missing frontend dependencies: $relative/node_modules" >&2
    bootstrap_message "$relative"
    die_usage "Frontend unit validation requires existing dependencies for $relative"
  fi
}

list_shared_packages() {
  python3 - "$ROOT_DIR" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
for package_json in sorted((root / "packages").glob("*/package.json")):
    package = json.loads(package_json.read_text(encoding="utf-8"))
    scripts = package.get("scripts", {})
    if scripts.get("type-check") and scripts.get("test"):
        print(package_json.parent.relative_to(root))
PY
}

require_frontend_toolchain() {
  require_command pnpm "Frontend unit validation"
  require_node_modules apps/console
  require_node_modules apps/management
  for binary in eslint vue-tsc vitest; do
    for relative in apps/console apps/management; do
      if [[ ! -x "$ROOT_DIR/$relative/node_modules/.bin/$binary" ]]; then
        echo "Missing frontend tool '$binary' in $relative/node_modules" >&2
        bootstrap_message "$relative"
        die_usage "Frontend unit validation requires '$binary' for $relative"
      fi
    done
  done

  mapfile -t shared_packages < <(list_shared_packages)
  for relative in "${shared_packages[@]}"; do
    require_node_modules "$relative"
  done
}

run_architecture_contract() {
  local static_only="$1"
  if [[ "$static_only" == "1" ]]; then
    (
      export ULTI_STATIC_ONLY=1
      # Optional drills are never inherited by the zero-infrastructure gate.
      unset SCALE_COMPOSE_ENV_FILE DUBBO_NACOS_SMOKE_ENV_FILE \
        HA_COMPOSE_ENV_FILE HA_COMPOSE_REQUIRED HA_RECONNECT_DRILL
      "$ROOT_DIR/scripts/dev/architecture-contract-test.sh"
    )
  else
    (
      unset ULTI_STATIC_ONLY
      "$ROOT_DIR/scripts/dev/architecture-contract-test.sh"
    )
  fi
}

run_theme_checks() {
  # Fast static guardrails first: theme/FOUC sync and typography tokens are
  # pure node checks and do not wait behind dependency installation.
  node "$ROOT_DIR/packages/theme/scripts/verify-theme-sync.mjs"
  node "$ROOT_DIR/packages/theme/scripts/verify-typography-tokens.mjs"
}

run_shell_analysis() {
  # Static shell analysis: fail the gate when shellcheck is available; skip
  # with a notice otherwise so environments without it stay usable.
  if command -v shellcheck >/dev/null 2>&1; then
    echo "Running shellcheck over scripts/..."
    find "$ROOT_DIR/scripts" -name '*.sh' -type f -print0 \
      | xargs -0 -n1 shellcheck --severity=error || exit 1
  else
    echo "shellcheck not installed; skipping static shell analysis." >&2
  fi
}

run_continuation_guard() {
  # A comment directly after a continued code line becomes part of the
  # command, silently swallowing following arguments.
  echo "Checking for comment lines after code line continuations..."
  while IFS= read -r script_file; do
    awk -v F="$script_file" '
      {
        is_cont = ($0 ~ /\\[ \t]*$/)
        is_comment = ($0 ~ /^[ \t]*#/)
        if (pending_code && is_comment && !is_cont) {
          print F ": line " NR " is a comment directly after a code line continuation (line " cont_line ")" > "/dev/stderr"
          bad = 1
        }
        if (is_cont) {
          if (cont_line == "") cont_line = NR
          if (cont_line == NR || pending_code == 0) pending_code = (!is_comment)
        } else {
          cont_line = ""; pending_code = 0
        }
      }
      END { exit bad ? 1 : 0 }' "$script_file" || exit 1
  done < <(find "$ROOT_DIR/scripts" -name '*.sh' -type f)
}

run_static_guardrails() {
  local static_only="$1"
  run_architecture_contract "$static_only"
  run_theme_checks
  run_shell_analysis
  run_continuation_guard

  echo "Running owner migration preflight tests..."
  "$ROOT_DIR/scripts/dev/migrate-owner-preflight-test.sh"

  echo "Running coverage gate contract..."
  if [[ "$static_only" == "1" ]]; then
    ULTI_STATIC_ONLY=1 "$ROOT_DIR/scripts/test/coverage-contract.sh"
  else
    (
      unset ULTI_STATIC_ONLY
      "$ROOT_DIR/scripts/test/coverage-contract.sh"
    )
  fi
}

run_static_checks() {
  require_static_toolchain
  run_static_guardrails 1
}

run_frontend_unit_checks() {
  for relative in apps/console apps/management; do
    echo "Running $relative type-check..."
    (cd "$ROOT_DIR" && pnpm --dir "$relative" type-check)
    echo "Running $relative lint:check..."
    (cd "$ROOT_DIR" && pnpm --dir "$relative" lint:check)
    echo "Running $relative unit tests..."
    (cd "$ROOT_DIR" && pnpm --dir "$relative" test)
  done

  mapfile -t shared_packages < <(list_shared_packages)
  for relative in "${shared_packages[@]}"; do
    echo "Running $relative type-check..."
    (cd "$ROOT_DIR" && pnpm --dir "$relative" type-check)
    echo "Running $relative unit tests..."
    (cd "$ROOT_DIR" && pnpm --dir "$relative" test)
  done
}

run_backend_unit_checks() {
  if ! grep -Fq '<id>unit</id>' "$ROOT_DIR/services/pom.xml"; then
    die_usage "Backend zero-infrastructure unit allowlist is unresolved (U-03); refusing to claim safe Spring-slice coverage. Add the unit Maven profile, then retry."
  fi
  require_maven_toolchain
  echo "Running backend unit tests (excluding *IT)..."
  (cd "$ROOT_DIR/services" && "${MAVEN[@]}" test -Punit -Dtest='!*IT' -Dsurefire.failIfNoSpecifiedTests=false -B)
}

run_unit_checks() {
  require_frontend_toolchain
  run_frontend_unit_checks
  run_backend_unit_checks
}

run_full_local() {
  require_static_toolchain
  require_maven_toolchain
  require_command docker "Full-local validation"
  require_command pnpm "Full-local validation"
  require_command openssl "Full-local validation"
  run_static_guardrails 0

  if [[ ! -f "$ENV_FILE" ]]; then
    "$ROOT_DIR/scripts/dev/init-env.sh"
  fi

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  # Compose validates the Meili service even when the full-local gate does not
  # start it. Keep missing values disposable and in-memory.
  if [[ -z "${MEILI_MASTER_KEY:-}" ]]; then
    MEILI_MASTER_KEY="$(openssl rand -hex 32)"
    export MEILI_MASTER_KEY
  fi

  # Supply missing health/HA Redis principals in-memory for this run only.
  for ephemeral_redis_var in HEALTH_REDIS_PASSWORD REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD; do
    if [[ -z "${!ephemeral_redis_var:-}" ]]; then
      printf -v "$ephemeral_redis_var" '%s' "$(openssl rand -hex 32)"
      export "$ephemeral_redis_var"
    fi
  done

  REDIS_ACL_DIR="$ROOT_DIR/.local/test-redis-acl"
  [[ "$REDIS_ACL_DIR" == /* ]] || REDIS_ACL_DIR="$ROOT_DIR/$REDIS_ACL_DIR"
  mkdir -p "$REDIS_ACL_DIR"
  chmod 755 "$REDIS_ACL_DIR"
  REDIS_ACL_FILE="$REDIS_ACL_DIR/users.acl"
  [[ "$REDIS_ACL_FILE" == /* ]] || REDIS_ACL_FILE="$ROOT_DIR/$REDIS_ACL_FILE"
  export REDIS_ACL_DIR REDIS_ACL_FILE
  if [[ ! -x "$ROOT_DIR/docker/redis/generate-users-acl.sh" ]]; then
    echo "Missing Redis ACL generator: docker/redis/generate-users-acl.sh" >&2
    exit 1
  fi
  "$ROOT_DIR/docker/redis/generate-users-acl.sh" "$REDIS_ACL_FILE"

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

  MYSQL_CONTAINER="$(compose_service_container compose mysql)"
  REDIS_CONTAINER="$(compose_service_container compose redis)"
  for container in "$MYSQL_CONTAINER" "$REDIS_CONTAINER"; do
    await_container_health "$container" 60 2 || exit 1
  done

  MYSQL_ADMIN_CONTAINER="$MYSQL_CONTAINER"
  if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" \
    mysql -u root --batch --skip-column-names -e "SELECT 1" >/dev/null 2>&1; then
    echo "Existing MySQL service rejected the configured root password; using a disposable local MySQL for this run." >&2
    if ! docker image inspect "$TEST_MYSQL_IMAGE" >/dev/null 2>&1; then
      echo "Cannot isolate the test database: local image '$TEST_MYSQL_IMAGE' is unavailable and no registry pull is attempted." >&2
      echo "Set TEST_MYSQL_IMAGE to an existing local MySQL-compatible image, or restore the image/registry before retrying." >&2
      exit 1
    fi

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
  if [[ -z "${OPS_REDIS_PASSWORD:-}" ]]; then
    echo "OPS_REDIS_PASSWORD is required in $ENV_FILE for the Redis ACL boundary." >&2
    exit 1
  fi
  export REDIS_USERNAME=ulticode-ops
  export REDIS_PASSWORD="$OPS_REDIS_PASSWORD"
  export JWT_SECRET=test-only-jwt-signing-key-minimum-32-characters-long
  export JWT_COOKIE_SECURE=false
  export EMAIL_ENABLED=false
  export MEILISEARCH_ENABLED=false
  export SANDBOX_ENABLED=false
  export SPRINGDOC_ENABLED=false

  export MIGRATION_DB_USER=root
  export MIGRATION_DB_PASSWORD="$MYSQL_ROOT_PASSWORD"
  MIGRATION_DB_NAME="$TEST_DB_NAME" "$ROOT_DIR/scripts/dev/migrate.sh" migrate

  echo "Running backend tests..."
  (cd "$ROOT_DIR/services" && "${MAVEN[@]}" verify -B)

  echo "Running shared authentication tests..."
  (cd "$ROOT_DIR/packages/auth-core" && pnpm install --frozen-lockfile && pnpm test:coverage && pnpm type-check)

  echo "Running console tests..."
  (cd "$ROOT_DIR/apps/console" && pnpm install --frozen-lockfile && pnpm test:coverage && pnpm type-check)

  echo "Running management tests..."
  (cd "$ROOT_DIR/apps/management" && pnpm install --frozen-lockfile && pnpm test:coverage && pnpm type-check)
}

run_full_extras() {
  echo "Running production builds and dependency audits..."
  # pnpm audit is best-effort because some registries do not implement the
  # audit endpoint; warn and continue on that environment limitation.
  (cd "$ROOT_DIR/apps/console" && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
  (cd "$ROOT_DIR/apps/management" && pnpm validate:i18n-keys && pnpm build && { pnpm audit --prod --audit-level high || echo "WARN: pnpm audit unavailable; skipped" >&2; })
}

run_integration_extras() {
  if ! docker image inspect "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" >/dev/null 2>&1; then
    docker build -t "${SANDBOX_IMAGE:-ulticode-sandbox:latest}" "$ROOT_DIR/docker/sandbox"
  fi
  echo "Running Testcontainers and sandbox integration tests..."
  (cd "$ROOT_DIR/services" && "${MAVEN[@]}" -Dtest='*IT,*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test -B)
  echo "Running owner migration safety integration test (disposable MySQL/Redis)..."
  "$ROOT_DIR/scripts/dev/owner-migration-safety-integration-test.sh"
}

case "$MODE" in
  static)
    run_static_checks
    ;;
  unit)
    run_static_checks
    run_unit_checks
    ;;
  quick)
    echo "DEPRECATION: quick now means static + unit; heavy coverage moved to full-local." >&2
    run_static_checks
    run_unit_checks
    ;;
  full-local)
    run_full_local
    ;;
  full)
    run_full_local
    run_full_extras
    ;;
  integration)
    run_full_local
    run_integration_extras
    ;;
esac

echo "All $MODE checks passed."
