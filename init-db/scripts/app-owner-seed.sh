#!/usr/bin/env bash
set -euo pipefail

# DEV-LOCAL only: hydrate the App Owner's problemset, forum, contest and
# solution domains
# from immutable legacy test-data sources after the App Owner schema is migrated.
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
capture_env_vars DEV_LOCAL_SEED_DATA_ENABLED DEV_LOCAL_SEED_ALLOW_REMOTE MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_USER \
  MIGRATION_DB_PASSWORD MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT APP_DB_USER
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

valid_identifier "$MIGRATION_DB_USER" || {
  echo "App Owner seed preflight failed: invalid MIGRATION_DB_USER '$MIGRATION_DB_USER'." >&2
  exit 1
}

APP_DB_USER="${APP_DB_USER:-app_rw}"
valid_identifier "$APP_DB_USER" || {
  echo "App Owner seed preflight failed: invalid APP_DB_USER '$APP_DB_USER'." >&2
  exit 1
}

if [[ "$MIGRATION_DB_USER" == "$APP_DB_USER" ]]; then
  echo "App Owner seed preflight failed: migration user must differ from APP_DB_USER '$APP_DB_USER'." >&2
  exit 1
fi

MIGRATION_MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
TARGET_SCHEMA=app
valid_identifier "$TARGET_SCHEMA" || {
  echo "App Owner seed preflight failed: invalid TARGET_SCHEMA '$TARGET_SCHEMA'." >&2
  exit 1
}

if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo "App Owner seed preflight failed: docker CLI is required with MIGRATION_MYSQL_CONTAINER." >&2
    exit 1
  }
  valid_container_ref "$MIGRATION_MYSQL_CONTAINER" || {
    echo "App Owner seed preflight failed: invalid container reference '$MIGRATION_MYSQL_CONTAINER'." >&2
    exit 1
  }
  container_running "$MIGRATION_MYSQL_CONTAINER" || {
    echo "App Owner seed preflight failed: container '$MIGRATION_MYSQL_CONTAINER' is not running." >&2
    exit 1
  }
  if [[ -n "${MIGRATION_DB_HOST:-}" && -n "${MIGRATION_DB_PORT:-}" ]]; then
    mysql_container_targets_configured_host "$MIGRATION_MYSQL_CONTAINER" "$MIGRATION_MYSQL_CONTAINER_PORT" \
      "$MIGRATION_DB_HOST" "$MIGRATION_DB_PORT" || {
      echo "App Owner seed preflight failed: configured migration target $MIGRATION_DB_HOST:$MIGRATION_DB_PORT is not a published endpoint of container $MIGRATION_MYSQL_CONTAINER:$MIGRATION_MYSQL_CONTAINER_PORT." >&2
      exit 1
    }
  fi
else
  for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT; do
    [[ -n "${!variable:-}" ]] || {
      echo "App Owner seed preflight failed: $variable is required without MIGRATION_MYSQL_CONTAINER." >&2
      exit 1
    }
  done
  valid_port "$MIGRATION_DB_PORT" || {
    echo "App Owner seed preflight failed: invalid MIGRATION_DB_PORT '$MIGRATION_DB_PORT'." >&2
    exit 1
  }
  case "$MIGRATION_DB_HOST" in
    127.0.0.1|localhost|::1) ;;
    *)
      if [[ "${DEV_LOCAL_SEED_ALLOW_REMOTE:-}" != "true" ]]; then
        echo "App Owner seed preflight failed: refusing to run DEV-LOCAL seed on non-local target '$MIGRATION_DB_HOST'." >&2
        echo "Set DEV_LOCAL_SEED_ALLOW_REMOTE=true if seeding a remote test database is intentional." >&2
        exit 1
      fi
      ;;
  esac
fi

SEED_FILES=(
  "$ROOT_DIR/init-db/migrations/V20260603_120000__Seed_Problems_Test_Data.sql"
  "$ROOT_DIR/init-db/migrations/V20260615140000__Seed_Problem_Category_Tags.sql"
  "$ROOT_DIR/init-db/migrations/V20260603_120200__Seed_Problem_Lists_Test_Data.sql"
)
FORUM_SEED_FILE="$ROOT_DIR/init-db/migrations/V20260603_120700__Seed_Forum_Posts_Per_User.sql"
CONTEST_SEED_FILE="$ROOT_DIR/init-db/migrations/V20260604120000__Seed_Contests_Test_Data.sql"
GLOBAL_RANKING_SEED_FILE="$ROOT_DIR/init-db/migrations/V20260604_130000__Seed_Global_Rankings_Test_Data.sql"
SOLUTION_SEED_FILE="$ROOT_DIR/init-db/migrations/V20260603_120400__Seed_Solutions_Test_Data.sql"
SEED_TABLES=(
  problems problem_details problem_tags problem_tag_relations problem_examples
  problem_languages problem_lists problem_list_problem_relations
)
FORUM_SEED_TABLES=(forum_posts forum_communities forum_tags forum_users)
CONTEST_SEED_TABLES=(
  contests contest_problems contest_participants contest_rankings
  contest_problem_results contest_announcements contest_submissions
  contest_analytics global_rankings
)
CONTEST_DATA_TABLES=(
  contests contest_problems contest_participants contest_rankings
  contest_problem_results contest_announcements global_rankings
)
SOLUTION_SEED_TABLES=(solutions)

for seed_file in "${SEED_FILES[@]}"; do
  [[ -f "$seed_file" ]] || {
    echo "App Owner seed source is missing: $seed_file" >&2
    exit 1
  }
done
[[ -f "$FORUM_SEED_FILE" ]] || {
  echo "App Owner forum seed source is missing: $FORUM_SEED_FILE" >&2
  exit 1
}
for seed_file in "$CONTEST_SEED_FILE" "$GLOBAL_RANKING_SEED_FILE"; do
  [[ -f "$seed_file" ]] || {
    echo "App Owner contest seed source is missing: $seed_file" >&2
    exit 1
  }
done
[[ -f "$SOLUTION_SEED_FILE" ]] || {
  echo "App Owner solution seed source is missing: $SOLUTION_SEED_FILE" >&2
  exit 1
}

mysql_command() {
  local database="$1"
  if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
    command -v docker >/dev/null 2>&1 || {
      echo "App Owner seed preflight failed: docker is required with MIGRATION_MYSQL_CONTAINER." >&2
      return 1
    }
    local -a container_flags=()
    if [[ -n "${MIGRATION_MYSQL_CONTAINER_PORT:-}" ]]; then
      container_flags+=(--protocol=tcp -h 127.0.0.1 -P "$MIGRATION_MYSQL_CONTAINER_PORT")
    fi
    docker exec -i -e "MYSQL_PWD=$MIGRATION_DB_PASSWORD" "$MIGRATION_MYSQL_CONTAINER" \
      mysql --default-character-set=utf8mb4 --batch --skip-column-names \
      "${container_flags[@]}" \
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
  local problems lists published featured forum_posts forum_communities forum_tags forum_users
  local contests contest_problems contest_participants contest_rankings contest_problem_results contest_announcements global_rankings
  local solutions
  problems="$(seed_row_count problems)"
  lists="$(seed_row_count problem_lists)"
  published="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problems\` WHERE is_published=1;")"
  featured="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problem_lists\` WHERE is_featured=1 AND is_public=1;")"
  forum_posts="$(seed_row_count forum_posts)"
  forum_communities="$(seed_row_count forum_communities)"
  forum_tags="$(seed_row_count forum_tags)"
  forum_users="$(seed_row_count forum_users)"
  contests="$(seed_row_count contests)"
  contest_problems="$(seed_row_count contest_problems)"
  contest_participants="$(seed_row_count contest_participants)"
  contest_rankings="$(seed_row_count contest_rankings)"
  contest_problem_results="$(seed_row_count contest_problem_results)"
  contest_announcements="$(seed_row_count contest_announcements)"
  global_rankings="$(seed_row_count global_rankings)"
  solutions="$(seed_row_count solutions)"
  echo "App Owner seed snapshot: problems=$problems published=$published problem_lists=$lists featured=$featured forum_posts=$forum_posts forum_communities=$forum_communities forum_tags=$forum_tags forum_users=$forum_users contests=$contests contest_problems=$contest_problems contest_participants=$contest_participants contest_rankings=$contest_rankings contest_problem_results=$contest_problem_results contest_announcements=$contest_announcements global_rankings=$global_rankings solutions=$solutions"
}

preflight() {
  local table
  for table in "${SEED_TABLES[@]}" "${FORUM_SEED_TABLES[@]}" "${CONTEST_SEED_TABLES[@]}" "${SOLUTION_SEED_TABLES[@]}"; do
    require_table "$table"
  done
}

seed_problemset_if_empty() {
  local problems lists published featured table count any_seed_rows=false all_seed_rows=true
  problems="$(seed_row_count problems)"
  lists="$(seed_row_count problem_lists)"
  published="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problems\` WHERE is_published=1;")"
  featured="$(mysql_query "SELECT COUNT(*) FROM \`$TARGET_SCHEMA\`.\`problem_lists\` WHERE is_featured=1 AND is_public=1;")"

  for table in "${SEED_TABLES[@]}"; do
    count="$(seed_row_count "$table")"
    if [[ "$count" == 0 ]]; then
      all_seed_rows=false
    else
      any_seed_rows=true
    fi
  done

  if [[ "$any_seed_rows" == true ]]; then
    if [[ "$all_seed_rows" == true && "$problems" -ge 6 && "$published" -ge 6 && "$lists" -ge 9 && "$featured" -ge 7 ]]; then
      echo "App Owner seed already present; preserving existing data."
      return 0
    fi
    echo "Refusing App Owner seed: partial or incomplete problemset data exists (problems=$problems, published=$published, problem_lists=$lists, featured=$featured)." >&2
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

forum_seed_sql() {
  local legacy_admin_subquery='(SELECT `id` FROM `users` WHERE `username` = '\''admin'\'' AND `role` = '\''ADMIN'\'' LIMIT 1)'
  local local_admin_id="'9f6bc78a-5f21-11f1-950a-8ef0eeeb1ca8'"
  # The immutable legacy seed resolves one admin through the old shared users
  # table. App Owner no longer owns that table, so map only this DEV-LOCAL
  # fixture reference to the stable forum_users row declared by the same seed.
  sed "s#${legacy_admin_subquery}#${local_admin_id}#g" "$FORUM_SEED_FILE"
}

seed_forum_if_empty() {
  local posts communities tags users table count any_seed_rows=false all_seed_rows=true
  posts="$(seed_row_count forum_posts)"
  communities="$(seed_row_count forum_communities)"
  tags="$(seed_row_count forum_tags)"
  users="$(seed_row_count forum_users)"

  for table in "${FORUM_SEED_TABLES[@]}"; do
    count="$(seed_row_count "$table")"
    if [[ "$count" == 0 ]]; then
      all_seed_rows=false
    else
      any_seed_rows=true
    fi
  done

  if [[ "$any_seed_rows" == true ]]; then
    if [[ "$all_seed_rows" == true && "$posts" -ge 12 && "$communities" -ge 3 && "$tags" -ge 6 && "$users" -ge 12 ]]; then
      echo "App Owner forum seed already present; preserving existing data."
      return 0
    fi
    echo "Refusing App Owner forum seed: partial or incomplete forum data exists (posts=$posts, communities=$communities, tags=$tags, users=$users)." >&2
    echo "Reconcile the DEV-LOCAL database explicitly before retrying." >&2
    return 1
  fi

  echo "Seeding DEV-LOCAL App Owner forum data..."
  {
    printf 'START TRANSACTION;\n'
    forum_seed_sql
    printf '\nCOMMIT;\n'
  } | mysql_command "$TARGET_SCHEMA"
}

contest_seed_sql() {
  # App JDBC uses Asia/Shanghai while the local MySQL container commonly uses
  # SYSTEM/UTC. Align only this DEV-LOCAL seed session so NOW()-relative
  # contest windows match the App lifecycle clock without changing global DB
  # time or production configuration.
  printf "SET time_zone = '+08:00';\n"
  cat "$CONTEST_SEED_FILE"
  printf '\n'
  global_ranking_seed_sql
}

global_ranking_seed_sql() {
  # The immutable legacy source contains demo DiceBear URLs. Keep the source
  # unchanged, but do not persist those placeholders in the App Owner; public
  # ranking reads resolve avatars from App-owned user_profiles instead.
  sed -E "s#'https://api\\.dicebear\\.com/[^']*'#NULL#g" "$GLOBAL_RANKING_SEED_FILE"
}

clear_global_ranking_fixture_avatars() {
  mysql_query "START TRANSACTION; UPDATE \`$TARGET_SCHEMA\`.\`global_rankings\` SET avatar=NULL WHERE avatar LIKE 'https://api.dicebear.com/%' AND (user_id LIKE 'user-%' OR user_id LIKE 'mod-%' OR user_id IN ('admin-002', 'super-root-001', 'super-vp-002')); COMMIT;"
}

seed_contest_if_empty() {
  local contests contest_problems contest_participants contest_rankings contest_problem_results contest_announcements global_rankings
  local table count any_seed_rows=false all_seed_rows=true
  contests="$(seed_row_count contests)"
  contest_problems="$(seed_row_count contest_problems)"
  contest_participants="$(seed_row_count contest_participants)"
  contest_rankings="$(seed_row_count contest_rankings)"
  contest_problem_results="$(seed_row_count contest_problem_results)"
  contest_announcements="$(seed_row_count contest_announcements)"
  global_rankings="$(seed_row_count global_rankings)"

  for table in "${CONTEST_DATA_TABLES[@]}"; do
    count="$(seed_row_count "$table")"
    if [[ "$count" == 0 ]]; then
      all_seed_rows=false
    else
      any_seed_rows=true
    fi
  done

  if [[ "$any_seed_rows" == true ]]; then
    if [[ "$all_seed_rows" == true \
      && "$contests" -ge 5 \
      && "$contest_problems" -ge 22 \
      && "$contest_participants" -ge 22 \
      && "$contest_rankings" -ge 11 \
      && "$contest_problem_results" -ge 51 \
      && "$contest_announcements" -ge 7 \
      && "$global_rankings" -ge 10 ]]; then
      clear_global_ranking_fixture_avatars
      echo "App Owner contest seed already present; preserving existing data."
      return 0
    fi
    echo "Refusing App Owner contest seed: partial or incomplete contest data exists (contests=$contests, contest_problems=$contest_problems, contest_participants=$contest_participants, contest_rankings=$contest_rankings, contest_problem_results=$contest_problem_results, contest_announcements=$contest_announcements, global_rankings=$global_rankings)." >&2
    echo "Reconcile the DEV-LOCAL database explicitly before retrying." >&2
    return 1
  fi

  echo "Seeding DEV-LOCAL App Owner contest and global ranking data..."
  {
    printf 'START TRANSACTION;\n'
    contest_seed_sql
    printf '\nCOMMIT;\n'
  } | mysql_command "$TARGET_SCHEMA"
}

solution_seed_sql() {
  local legacy_admin_subquery='(SELECT `id` FROM `users` WHERE `username` = '\''admin'\'' AND `role` = '\''ADMIN'\'' LIMIT 1)'
  local local_admin_id="'admin-002'"
  # The immutable solution seed resolves one admin through the legacy users
  # table. Keep the App Owner path self-contained by using its stable fixture
  # identity instead of restoring a cross-Owner users lookup.
  sed "s#${legacy_admin_subquery}#${local_admin_id}#g" "$SOLUTION_SEED_FILE"
}

seed_solution_if_empty() {
  local solutions
  solutions="$(seed_row_count solutions)"

  if [[ "$solutions" != 0 ]]; then
    if [[ "$solutions" -ge 12 ]]; then
      echo "App Owner solution seed already present; preserving existing data."
      return 0
    fi
    echo "Refusing App Owner solution seed: partial or incomplete solution data exists (solutions=$solutions)." >&2
    echo "Reconcile the DEV-LOCAL database explicitly before retrying." >&2
    return 1
  fi

  echo "Seeding DEV-LOCAL App Owner solution data..."
  {
    printf 'START TRANSACTION;\n'
    solution_seed_sql
    printf '\nCOMMIT;\n'
  } | mysql_command "$TARGET_SCHEMA"
}

preflight
case "$COMMAND" in
  info|validate)
    print_snapshot
    ;;
  migrate)
    seed_problemset_if_empty
    seed_forum_if_empty
    seed_contest_if_empty
    seed_solution_if_empty
    print_snapshot
    ;;
esac
