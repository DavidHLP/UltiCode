#!/usr/bin/env bash
set -euo pipefail

# DEV-LOCAL only: hydrate the App Owner's problemset from the immutable legacy
# test-data sources after the App Owner schema has been migrated.
# This is deliberately a seed adapter, not a production migration.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
COMMAND="${1:-migrate}"

case "$COMMAND" in
  migrate|validate|info) ;;
  *)
    echo "Usage: $0 [migrate|validate|info]" >&2
    exit 2
    ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
capture_env_vars DEV_LOCAL_SEED_DATA_ENABLED MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_USER \
  MIGRATION_DB_PASSWORD MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT
load_env_file
apply_env_overrides

[[ "${DEV_LOCAL_SEED_DATA_ENABLED:-}" == true ]] || {
  echo "Refusing App Owner seed: DEV_LOCAL_SEED_DATA_ENABLED=true is required." >&2
  exit 2
}

for variable in MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    echo "App Owner seed preflight failed: $variable is required." >&2
    exit 1
  }
done

if [[ -n "${APP_DB_USER:-}" && "$MIGRATION_DB_USER" == "$APP_DB_USER" ]]; then
  echo "App Owner seed preflight failed: migration user must differ from APP_DB_USER." >&2
  exit 1
fi

if [[ -z "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
  for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT; do
    [[ -n "${!variable:-}" ]] || {
      echo "App Owner seed preflight failed: $variable is required without MIGRATION_MYSQL_CONTAINER." >&2
      exit 1
    }
  done
fi

MIGRATION_MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
TARGET_SCHEMA=app
SEED_FILES=(
  "$ROOT_DIR/init-db/migrations/V20260603_120000__Seed_Problems_Test_Data.sql"
  "$ROOT_DIR/init-db/migrations/V20260615140000__Seed_Problem_Category_Tags.sql"
  "$ROOT_DIR/init-db/migrations/V20260603_120200__Seed_Problem_Lists_Test_Data.sql"
)
SEED_TABLES=(
  problems problem_details problem_tags problem_tag_relations problem_examples
  problem_languages problem_lists problem_list_problem_relations
)

for seed_file in "${SEED_FILES[@]}"; do
  [[ -f "$seed_file" ]] || {
    echo "App Owner seed source is missing: $seed_file" >&2
    exit 1
  }
done

mysql_command() {
  local database="$1"
  if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
    command -v docker >/dev/null 2>&1 || {
      echo "App Owner seed preflight failed: docker is required with MIGRATION_MYSQL_CONTAINER." >&2
      return 1
    }
    docker exec -i -e "MYSQL_PWD=$MIGRATION_DB_PASSWORD" "$MIGRATION_MYSQL_CONTAINER" \
      mysql --default-character-set=utf8mb4 --batch --skip-column-names \
      -u "$MIGRATION_DB_USER" "$database"
  else
    command -v mysql >/dev/null 2>&1 || {
      echo "App Owner seed preflight failed: mysql client is required without MIGRATION_MYSQL_CONTAINER." >&2
      return 1
    }
    MYSQL_PWD="$MIGRATION_DB_PASSWORD" mysql --protocol=tcp \
      --default-character-set=utf8mb4 --batch --skip-column-names \
      -h "$MIGRATION_DB_HOST" -P "$MIGRATION_DB_PORT" \
      -u "$MIGRATION_DB_USER" "$database"
  fi
}

mysql_query() {
  mysql_command "$TARGET_SCHEMA" <<<"$1"
}

require_table() {
  local table="$1"
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET_SCHEMA' AND table_name='$table';")" == 1 ]] || {
    echo "App Owner seed preflight failed: missing table $TARGET_SCHEMA.$table." >&2
    exit 1
  }
}

seed_row_count() {
  mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`$1\`;"
}

print_snapshot() {
  local problems lists published featured
  problems="$(seed_row_count problems)"
  lists="$(seed_row_count problem_lists)"
  published="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problems\` WHERE is_published=1;")"
  featured="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problem_lists\` WHERE is_featured=1 AND is_public=1;")"
  echo "App Owner seed snapshot: problems=$problems published=$published problem_lists=$lists featured=$featured"
}

preflight() {
  local table
  for table in "${SEED_TABLES[@]}"; do
    require_table "$table"
  done
}

seed_if_empty() {
  local problems lists table count any_seed_rows=false all_seed_rows=true
  problems="$(seed_row_count problems)"
  lists="$(seed_row_count problem_lists)"
  for table in "${SEED_TABLES[@]}"; do
    count="$(seed_row_count "$table")"
    if [[ "$count" == 0 ]]; then
      all_seed_rows=false
    else
      any_seed_rows=true
    fi
  done
  if [[ "$any_seed_rows" == true ]]; then
    if [[ "$all_seed_rows" == true && "$problems" != 0 && "$lists" != 0 ]]; then
      echo "App Owner seed already present; preserving existing data."
      return 0
    fi
    echo "Refusing App Owner seed: partial problemset data exists (problems=$problems, problem_lists=$lists)." >&2
    echo "Reconcile the DEV-LOCAL database explicitly before retrying." >&2
    return 1
  fi

  echo "Seeding DEV-LOCAL App Owner problemset data..."
  {
    printf 'START TRANSACTION;\n'
    for seed_file in "${SEED_FILES[@]}"; do
      cat "$seed_file"
      printf '\n'
    done
    printf 'COMMIT;\n'
  } | mysql_command "$TARGET_SCHEMA"
}

preflight
case "$COMMAND" in
  info|validate)
    print_snapshot
    ;;
  migrate)
    seed_if_empty
    print_snapshot
    ;;
esac
