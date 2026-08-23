#!/usr/bin/env bash
# scripts/dev/lib/sql.sh — shared data-verification primitives for migration
# runbooks, plus a single-sourced mysql_query adapter factory.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. Helpers are frozen readonly -f so a hostile .env cannot replace them.
#
# Adapter contract: table_exists/column_signature/row_count/checksum_table
# delegate the connection to a caller-owned `mysql_query <sql>` function. That
# adapter is itself built here by define_mysql_query_adapter so runbooks no
# longer hand-roll divergent docker-exec/mysql invocations.

if ! [[ -v __ULTICODE_SQL_SOURCED ]]; then
  declare -gr __ULTICODE_SQL_SOURCED=1

  # _mysql_query_via_adapter SQL DATABASE_OVERRIDE CONTAINER CONTAINER_PORT \
  #   HOST PORT USER PASSWORD DATABASE [EXTRA_FLAGS...]
  #
  #   - CONTAINER non-empty -> docker exec -e MYSQL_PWD=PASSWORD CONTAINER mysql
  #     [EXTRA_FLAGS...] [--protocol=tcp -h 127.0.0.1 -P CONTAINER_PORT]
  #     [-u USER] [DATABASE] -e SQL
  #   - otherwise           -> MYSQL_PWD=PASSWORD mysql --protocol=tcp
  #     -h HOST -P PORT [EXTRA_FLAGS...] [-u USER] [DATABASE] -e SQL
  #
  # Empty CONTAINER_PORT keeps the in-container socket transport (no forced
  # TCP); empty DATABASE connects without a default schema;
  # non-empty DATABASE_OVERRIDE replaces DATABASE for that one query.
  _mysql_query_via_adapter() {
    local sql="$1" database_override="$2"
    shift 2
    local container="$1" container_port="$2" host="$3" port="$4"
    local user="$5" password="$6" database="$7"
    shift 7
    local -a extra=()
    [[ $# -gt 0 ]] && extra=("$@")
    local effective_database="$database"
    [[ -n "$database_override" ]] && effective_database="$database_override"

    local -a cmd
    if [[ -n "$container" ]]; then
      cmd=(docker exec -e "MYSQL_PWD=$password" "$container" mysql)
      if [[ -n "$container_port" ]]; then
        cmd+=(--protocol=tcp -h 127.0.0.1 -P "$container_port")
      fi
    else
      cmd=(env MYSQL_PWD="$password" mysql --protocol=tcp -h "$host" -P "$port")
    fi
    if [[ ${#extra[@]} -gt 0 ]]; then
      cmd+=("${extra[@]}")
    fi
    cmd+=(-u "$user")
    [[ -n "$effective_database" ]] && cmd+=("$effective_database")
    cmd+=(-e "$sql")
    "${cmd[@]}"
  }

  # define_mysql_query_adapter NAME CONTAINER CONTAINER_PORT HOST PORT \
  #   USER PASSWORD DATABASE [EXTRA_FLAGS...]
  #
  # Defines NAME as `NAME <sql> [database_override]` with the transport
  # described at _mysql_query_via_adapter. Connection values are baked into
  # the generated function by value (shell-quoted), so later .env or
  # environment changes cannot silently retarget an already-defined adapter.
  define_mysql_query_adapter() {
    local name="$1"
    valid_identifier "$name" || {
      echo "define_mysql_query_adapter: invalid adapter name: $name" >&2
      return 1
    }
    shift
    local quoted_config
    quoted_config="$(printf ' %q' "$@")"
    eval "
${name}() {
  _mysql_query_via_adapter "\"\$1\" "\"\${2:-}\"$quoted_config
}
"
  }

  # Shared data-verification primitives. They delegate the connection to the
  # caller-owned `mysql_query <sql>` adapter, so each runbook keeps its own
  # connection semantics while the verification logic stays single-source here.
  table_exists() {
    local schema="$1" table="$2"
    [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schema' AND table_name = '$table';")" == "1" ]]
  }

  column_signature() {
    local schema="$1" table="$2"
    mysql_query "SELECT COALESCE(GROUP_CONCAT(CONCAT_WS(':', ordinal_position, column_name, column_type, is_nullable, COALESCE(column_default, '<NULL>'), extra, COALESCE(character_set_name, ''), COALESCE(collation_name, '')) ORDER BY ordinal_position SEPARATOR '|'), '') FROM information_schema.columns WHERE table_schema = '$schema' AND table_name = '$table';"
  }

  row_count() {
    local schema="$1" table="$2" predicate="${3:-1=1}"
    mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\` WHERE $predicate;"
  }

  checksum_table() {
    # Strict CHECKSUM TABLE reader: refuses to return a non-numeric value so a
    # broken transport cannot silently compare empty checksums.
    local schema="$1" table="$2" result
    if ! result="$(mysql_query "CHECKSUM TABLE \`$schema\`.\`$table\`;")"; then
      return 1
    fi
    result="$(awk 'NF == 2 && $2 ~ /^[0-9]+$/ { print $2; found=1 } END { if (!found) exit 1 }' <<<"$result")" || {
      echo "Unable to read a valid checksum for $schema.$table; refusing to continue." >&2
      return 1
    }
    printf '%s\n' "$result"
  }

  readonly -f _mysql_query_via_adapter define_mysql_query_adapter \
    table_exists column_signature row_count checksum_table
fi
