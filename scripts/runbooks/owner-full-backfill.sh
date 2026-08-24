#!/usr/bin/env bash
# Full-volume legacy -> owner-schema backfill (non-destructive, idempotent).
#
# Converts every legacy `ulticode` business table with data into its runtime
# owner schema per the migration mapping audit (.local/migration-audit/).
# Follows the repo methodology: expand -> backfill -> verify -> cutover.
#
# Actions:
#   preflight        validate env, shapes and quiesce state
#   backfill         idempotent INSERT..SELECT per pair, manifest recorded
#   verify           count + PK checksum equality per pair
#   rollback         delete target rows whose PK exists in source (reverse order)
#   archive-retired  mysqldump zero-row retired legacy tables for the record
#   converge         make pre-populated owner tables byte-identical to legacy
#                    (same-PK seed drift: delete extras, insert missing, update changed)
#
# Never edits applied migrations; never writes to the shared `ulticode` source.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-preflight}"
AUDIT_DIR="${AUDIT_DIR:-$ROOT_DIR/.local/migration-audit}"
MANIFEST_FILE="${BACKFILL_MANIFEST:-$AUDIT_DIR/owner-full-backfill.manifest}"

case "$ACTION" in
  preflight|backfill|verify|rollback|archive-retired|converge) ;;
  *) echo "Usage: $0 [preflight|backfill|verify|rollback|archive-retired]" >&2; exit 2 ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

capture_env_vars MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_USER \
  MIGRATION_DB_PASSWORD MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT

load_env_file
apply_env_overrides

# Migration principal: a privileged direct-grant account. Defaults follow
# scripts/dev/migrate.sh conventions; root is the dev fallback.
: "${MIGRATION_DB_HOST:=$DB_HOST}"
: "${MIGRATION_DB_PORT:=$DB_PORT}"
: "${MIGRATION_DB_USER:=root}"
: "${MIGRATION_DB_PASSWORD:=${MYSQL_ROOT_PASSWORD:-$DB_ROOT_PASSWORD}}"
: "${MIGRATION_MYSQL_CONTAINER:=${MYSQL_CONTAINER:-}}"
: "${MIGRATION_MYSQL_CONTAINER_PORT:=3306}"
SOURCE_SCHEMA="${SOURCE_SCHEMA:-ulticode}"

for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || { echo "$variable is required" >&2; exit 1; }
done

_fb_valid_id() { [[ "$1" =~ ^[A-Za-z0-9_]+$ ]]; }
for identifier in "$SOURCE_SCHEMA" "$MIGRATION_DB_USER"; do
  _fb_valid_id "$identifier" || { echo "Invalid identifier: $identifier" >&2; exit 1; }
done

define_mysql_query_adapter _fbq \
  "${MIGRATION_MYSQL_CONTAINER:-}" "${MIGRATION_MYSQL_CONTAINER_PORT}" \
  "${MIGRATION_DB_HOST}" "${MIGRATION_DB_PORT}" \
  "${MIGRATION_DB_USER}" "${MIGRATION_DB_PASSWORD}" \
  "" --default-character-set=utf8mb4 --batch --skip-column-names

fbq() {
  local sql="$1"
  _fbq "SET SESSION group_concat_max_len=16777216; $sql"
}

_fb_table_exists() { # schema table -> 0/1
  [[ "$(fbq "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1' AND table_name='$2';")" == "1" ]]
}

_fb_columns_of() { # schema table -> newline list in ordinal order (generated cols excluded)
  fbq "SELECT COLUMN_NAME FROM information_schema.columns
        WHERE table_schema='$1' AND table_name='$2'
          AND (EXTRA IS NULL OR EXTRA NOT LIKE '%VIRTUAL GENERATED%' AND EXTRA NOT LIKE '%STORED GENERATED%')
        ORDER BY ORDINAL_POSITION;"
}

_fb_common_cols() { # src_table tgt_schema tgt_table pk -> space list of shared columns (tgt order, pk last)
  local src="$1" ts="$2" tt="$3"
  comm -12 \
    <(_fb_columns_of "$ts" "$tt" | sort) \
    <(_fb_columns_of "$SOURCE_SCHEMA" "$src" | sort) \
    | paste -sd ',' -
}

validate_pair_ids() {
  local t; for t in "$@"; do _fb_valid_id "$t" || { echo "Invalid table name: $t" >&2; exit 1; }; done
}

# ---- pair map: SRC_TABLE TGT_SCHEMA TGT_TABLE PK [EXTRA_MODE] -----------------
# EXTRA_MODE: users = username/email collision-guarded rename (_legacy suffix)
PAIRS=(
  "users              auth    users               id            users"
  "role_permissions   auth    role_permissions    id          roles"
  "user_profiles      app     user_profiles       account_id"
  "submissions        submission submissions       id"
  "submissions        app     submissions         id"
  "solution_comments  app     solution_comments   id"
  "solution_topics    app     solution_topics     id"
  "forum_comments     app     forum_comments      id"
  "test_cases         app     test_cases          id"
  "reports            app     reports             id"
  "appeals            app     appeals             id"
  "user_bans          app     user_bans           id"
  "moderation_queue   app     moderation_queue    id"
  "moderation_actions app     moderation_actions  id"
  "user_warnings      app     user_warnings       id"
  "audit_logs         admin   audit_logs          id"
)

RETIRED_TABLES=(
  DailyRecommendation system_announcements system_announcement_reads submission_statuses
  forum_community_links forum_community_rules forum_community_tags
  forum_community_permissions forum_post_tag_relations
)

_fb_pair_count() { fbq "SELECT COUNT(*) FROM \`$SOURCE_SCHEMA\`.\`$1\`;"; }
_fb_target_matched_count() { fbq "SELECT COUNT(*) FROM \`$2\`.\`$3\` WHERE \`$4\` IN (SELECT \`$4\` FROM \`$SOURCE_SCHEMA\`.\`$1\`);"; }

_fb_checksum_expr() { # cols (newline separated) -> SQL expression
  echo "BIT_XOR(CRC32(CONCAT_WS('#', $(echo "$1" | sed 's/\([A-Za-z0-9_]*\)/`\1`/g' | paste -sd ',' -))))"
}

_fb_pair_checksum() { # src tgt_schema tgt_table pk exclude_cols
  local src="$1" ts="$2" tt="$3" pk="$4"; shift 4
  local cols excl_pattern=""
  cols=$(comm -12 <(_fb_columns_of "$ts" "$tt" | sort) <(_fb_columns_of "$SOURCE_SCHEMA" "$src" | sort))
  if [[ $# -gt 0 ]]; then
    excl_pattern="$(IFS='|'; echo "^($*)$")"
    cols=$(echo "$cols" | grep -Ev "$excl_pattern" | sort)
  fi
  local expr; expr=$(_fb_checksum_expr "$cols" )
  local s t
  s=$(fbq "SELECT COALESCE($expr,0) FROM \`$SOURCE_SCHEMA\`.\`$src\`;")
  t=$(fbq "SELECT COALESCE(BIT_XOR(CRC32(CONCAT_WS('#', $(echo "$cols" | sed 's/\([A-Za-z0-9_]*\)/`\1`/g' | paste -sd ',' - )))),0) FROM \`$ts\`.\`$tt\` WHERE \`$pk\` IN (SELECT \`$pk\` FROM \`$SOURCE_SCHEMA\`.\`$src\`);")
  echo "$s:$t"
}

# Single definition of the legacy->coarse role resource mapping.
_fb_role_case() { # <column-ref> -> SQL CASE expression
  local c="$1"
  echo "CASE $c
    WHEN 'USER' THEN 'USER'
    WHEN 'NOTIFICATION' THEN 'USER'
    WHEN 'ACHIEVEMENT' THEN 'USER'
    WHEN 'PROBLEM' THEN 'PROBLEM'
    WHEN 'PROBLEM_LIST' THEN 'PROBLEM'
    WHEN 'SEARCH' THEN 'PROBLEM'
    WHEN 'RECOMMENDATION' THEN 'PROBLEM'
    WHEN 'CONTEST' THEN 'CONTEST'
    WHEN 'SUBMISSION' THEN 'SUBMISSION'
    WHEN 'SOLUTION' THEN 'SOLUTION'
    WHEN 'SOLUTION_COMMENT' THEN 'SOLUTION'
    WHEN 'FORUM_POST' THEN 'FORUM'
    WHEN 'FORUM_COMMENT' THEN 'FORUM'
    WHEN 'TAG' THEN 'FORUM'
    WHEN 'BOOKMARK' THEN 'FORUM'
    WHEN 'FOLLOW' THEN 'FORUM'
    WHEN 'VOTE' THEN 'FORUM'
    WHEN 'AUDIT_LOG' THEN 'AUDIT'
    WHEN 'BACKUP' THEN 'AUDIT'
    WHEN 'REPORT' THEN 'COMMUNITY'
    WHEN 'MODERATION' THEN 'COMMUNITY'
    ELSE 'SYSTEM'
  END"
}

_fb_mapped_counts() { # src tgt_schema tgt_table -> "mapped_src_distinct:tgt_rows"
  local expr; expr=$(_fb_role_case "s.resource")
  fbq "SELECT CONCAT(
      (SELECT COUNT(DISTINCT CONCAT(s.role,'|',s.action,'|',$expr)) FROM \`$SOURCE_SCHEMA\`.\`$1\` s), ':',
      (SELECT COUNT(*) FROM \`$2\`.\`$3\`));"
}

# Owner tables that were seeded independently of the shared chain and can
# drift from the legacy dataset (same logical rows, different timestamps/ids).
# Legacy `ulticode` remains the source of truth until cutover.
CONVERGE_TABLES=(
  contests contest_problems contest_participants contest_problem_results
  contest_rankings contest_announcements contest_rating_calculations
  problems problem_details problem_tags problem_tag_relations problem_examples
  problem_languages problem_lists problem_list_problem_relations
  forum_posts forum_communities forum_tags forum_users solutions global_rankings
)

_fb_pk_cols() { # schema table -> newline list of primary key columns in key order
  fbq "SELECT COLUMN_NAME FROM information_schema.key_column_usage
        WHERE table_schema='$1' AND table_name='$2' AND constraint_name='PRIMARY'
        ORDER BY ordinal_position;"
}

do_converge() {
  echo "== converge owner tables to legacy source of truth"
  local failures=0
  while read -r t ts; do
    [[ -z "$t" ]] && continue
    _fb_valid_id "$t" || { echo "Invalid table: $t"; exit 1; }
    local pkcols
    pkcols=$(_fb_pk_cols "$SOURCE_SCHEMA" "$t")
    [[ -z "$pkcols" ]] && { echo "SKIP $ts.$t (no primary key)"; continue; }
    local allcols
    # _fb_common_cols returns a comma list; normalise to one column per line
    allcols=$(comm -12 <(_fb_columns_of "$ts" "$t" | sort) <(_fb_columns_of "$SOURCE_SCHEMA" "$t" | sort))
    [[ -z "$allcols" ]] && { echo "SKIP $ts.$t (no common columns)"; continue; }
    local collist join_pred del_pred notexists_pred assign
    collist=$(echo "$allcols" | tr ',\n' '\n\n' | sed '/^$/d' | sed 's/\([A-Za-z0-9_]*\)/`\1`/g' | paste -sd ',' -)
    # compound predicates over the shared pk subset
    join_pred=""; del_pred=""; notexists_pred=""
    while read -r pc; do
      [[ -z "$pc" ]] && continue
      if comm -12 <(echo "$allcols" | sort) <(echo "$pc" | sort) | grep -qx "$pc"; then
        join_pred="${join_pred} AND t.\`$pc\` = s.\`$pc\`"
        del_pred="${del_pred} AND \`$pc\` NOT IN (SELECT \`$pc\` FROM \`$SOURCE_SCHEMA\`.\`$t\`)"
        notexists_pred="${notexists_pred} AND t2.\`$pc\` = s.\`$pc\`"
      fi
    done <<< "$pkcols"
    join_pred="${join_pred# AND}"
    del_pred="${del_pred# AND}"
    notexists_pred="${notexists_pred# AND}"
    # build "t.`c`=s.`c`" assignments for non-pk common columns
    assign=$(echo "$allcols" | while read -r ac; do
      grep -qx "$ac" <<< "$pkcols" || echo "$ac"
    done | sed 's/\([A-Za-z0-9_]*\)/`t`.`\1`=`s`.`\1`/' | sed 's/$/,/' | tr -d '\n' | sed 's/,$//')
    # 1) delete target rows unknown to source
    fbq "DELETE FROM \`$ts\`.\`$t\` WHERE $del_pred;" >/dev/null
    # 2) insert missing source rows
    fbq "INSERT INTO \`$ts\`.\`$t\` ($collist) SELECT $collist FROM \`$SOURCE_SCHEMA\`.\`$t\` s
          WHERE NOT EXISTS (SELECT 1 FROM \`$ts\`.\`$t\` t2 WHERE $notexists_pred);" >/dev/null
    # 3) update changed values on shared pks
    [[ -n "$assign" ]] && fbq "UPDATE \`$ts\`.\`$t\` t JOIN \`$SOURCE_SCHEMA\`.\`$t\` s ON $join_pred SET $assign;" >/dev/null
    # verify
    local expr sum_s sum_t n
    n=$(fbq "SELECT COUNT(*) FROM \`$SOURCE_SCHEMA\`.\`$t\`;")
    expr=$(echo "$allcols" | tr ',' '\n' | sed 's/\([A-Za-z0-9_]*\)/`\1`/g' | paste -sd ',' -)
    sum_s=$(fbq "SELECT COALESCE(BIT_XOR(CRC32(CONCAT_WS('#',$expr))),0) FROM \`$SOURCE_SCHEMA\`.\`$t\`;")
    sum_t=$(fbq "SELECT COALESCE(BIT_XOR(CRC32(CONCAT_WS('#',$expr))),0) FROM \`$ts\`.\`$t\`;")
    if [[ "$sum_s" == "$sum_t" ]]; then
      echo "OK   converged $ts.$t (rows=$n)"
    else
      echo "FAIL converge $ts.$t src=$sum_s tgt=$sum_t"
      failures=$((failures+1))
    fi
  done < <(for tb in "${CONVERGE_TABLES[@]}"; do echo "$tb app"; done)
  return $failures
}

do_preflight() {
  echo "== preflight"
  if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
    container_running "$MIGRATION_MYSQL_CONTAINER" || { echo "MySQL container not running: $MIGRATION_MYSQL_CONTAINER" >&2; exit 1; }
  fi
  local failures=0
  # every source/target table must exist
  while read -r src ts tt pk extra; do
    [[ -z "$src" ]] && continue
    validate_pair_ids "$src" "$ts" "$tt" "$pk"
    _fb_table_exists "$SOURCE_SCHEMA" "$src" || { echo "MISSING source $SOURCE_SCHEMA.$src"; failures=$((failures+1)); }
    _fb_table_exists "$ts" "$tt" || { echo "MISSING target $ts.$tt (run owner migrations first)"; failures=$((failures+1)); }
  done <<< "$(printf '%s\n' "${PAIRS[@]}")"
  # writers quiesce check
  local writers="-"
  if command -v pm2 >/dev/null 2>&1; then
    writers="$(pm2 jlist 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(sum(1 for p in d if p.get("pm2_env",{}).get("status")=="online"))' 2>/dev/null || echo "-")"
  fi
  echo "PM2 online processes: $writers"
  if [[ "$writers" != "0" && "$writers" != "-" && -z "${BACKFILL_ALLOW_WRITERS:-}" ]]; then
    echo "Writers detected. Set BACKFILL_ALLOW_WRITERS=1 to override." >&2
    exit 1
  fi
  [[ "$failures" == "0" ]] || exit 1
  echo "preflight OK"
}

do_backfill() {
  mkdir -p "$AUDIT_DIR"
  : > "$MANIFEST_FILE"
  echo "source_table	target	target_before	inserted	target_after	ts" >> "$MANIFEST_FILE"
  while read -r src ts tt pk extra; do
    [[ -z "$src" ]] && continue
    local before inserted after
    before="$(_fb_target_matched_count "$src" "$ts" "$tt" "$pk")"
    local cols; cols=$(_fb_common_cols "$src" "$ts" "$tt" "$pk")
    local collist; collist=$(echo "$cols" | sed 's/\([A-Za-z0-9_]*\)/`\1`/g')
    local sql
    if [[ "${extra:-}" == "roles" ]]; then
      # Coarse template vocabulary of auth.role_permissions; legacy fine-grained
      # resources are mapped semantically (template feeds the admin projection,
      # not enforcement). One target row per distinct (role, action, coarse):
      # intra-statement dedupe via lowest-id anti-join on the source.
      local map_expr map_s2
      map_expr=$(_fb_role_case "s.resource")
      map_s2=$(_fb_role_case "s2.resource")
      sql="INSERT INTO \`$ts\`.\`$tt\` (id, role, action, resource)
        SELECT s.id, s.role, s.action, $map_expr
        FROM \`$SOURCE_SCHEMA\`.\`$src\` s
        WHERE NOT EXISTS (
              SELECT 1 FROM \`$ts\`.\`$tt\` t
               WHERE t.\`$pk\` = s.\`$pk\`)
          AND NOT EXISTS (
              SELECT 1 FROM \`$ts\`.\`$tt\` d
               WHERE d.role = s.role AND d.action = s.action
                 AND d.resource = $map_expr
                 AND d.\`$pk\` <> s.\`$pk\`)
          AND NOT EXISTS (
              SELECT 1 FROM \`$SOURCE_SCHEMA\`.\`$src\` s2
               WHERE s2.role = s.role AND s2.action = s.action
                 AND s2.id < s.id
                 AND $map_s2 = $map_expr);"
    elif [[ "${extra:-}" == "users" ]]; then
      sql="INSERT INTO \`$ts\`.\`$tt\` ($collist) SELECT $(echo "$collist" | sed \
            -e 's/`username`/CASE WHEN EXISTS (SELECT 1 FROM `'"$ts"'`.`users` a2u WHERE a2u.username = s.username AND a2u.id <> s.id) THEN CONCAT(s.username, '"'"'_legacy'"'"') ELSE s.username END/' \
            -e 's/`email`/CASE WHEN s.email IS NULL THEN NULL WHEN EXISTS (SELECT 1 FROM `'"$ts"'`.`users` a2e WHERE a2e.email = s.email AND a2e.id <> s.id) THEN NULL ELSE s.email END/') \
          FROM \`$SOURCE_SCHEMA\`.\`$src\` s WHERE NOT EXISTS (SELECT 1 FROM \`$ts\`.\`$tt\` t WHERE t.\`$pk\` = s.\`$pk\`);"
    else
      sql="INSERT INTO \`$ts\`.\`$tt\` ($collist) SELECT $collist FROM \`$SOURCE_SCHEMA\`.\`$src\` s WHERE NOT EXISTS (SELECT 1 FROM \`$ts\`.\`$tt\` t WHERE t.\`$pk\` = s.\`$pk\`);"
    fi
    fbq "$sql" >/dev/null
    after="$(_fb_target_matched_count "$src" "$ts" "$tt" "$pk")"
    inserted=$((after - before))
    echo -e "$src\t$ts.$tt\t$before\t$inserted\t$after\t$(date -Is)" >> "$MANIFEST_FILE"
    echo "backfilled $src -> $ts.$tt (+$inserted, matched=$after)"
  done < <(printf '%s\n' "${PAIRS[@]}")
  echo "manifest: $MANIFEST_FILE"
}

do_verify() {
  echo "== verify"
  local failures=0
  while read -r src ts tt pk extra; do
    [[ -z "$src" ]] && continue
    local sc tc sum
    sc="$(_fb_pair_count "$src")"
    tc="$(_fb_target_matched_count "$src" "$ts" "$tt" "$pk")"
    if [[ "${extra:-}" == "roles" ]]; then
      : # raw counts differ by design (dedupe); mapped-triple gate below covers it
    elif [[ "$sc" != "$tc" ]]; then
      echo "FAIL count $src -> $ts.$tt: source=$sc matched=$tc"
      failures=$((failures+1))
      continue
    fi
    if [[ "${extra:-}" == "users" ]]; then
      sum="$(_fb_pair_checksum "$src" "$ts" "$tt" "$pk" username email)"
    elif [[ "${extra:-}" == "roles" ]]; then
      # value-mapping pair: distinct mapped triples must equal target rows exactly
      sum="$(_fb_mapped_counts "$src" "$ts" "$tt")"
    else
      sum="$(_fb_pair_checksum "$src" "$ts" "$tt" "$pk")"
    fi
    if [[ -n "${sum%%:*}" && -n "${sum##*:}" && "${sum%%:*}" == "${sum##*:}" ]]; then
      echo "OK   $src -> $ts.$tt (rows=$sc checksum=${sum%%:*})"
    else
      echo "FAIL checksum $src -> $ts.$tt: ${sum}"
      failures=$((failures+1))
    fi
  done < <(printf '%s\n' "${PAIRS[@]}")
  # cross-schema referential integrity is covered by per-domain smoke tests
  # and the checksum gate above.
  return $failures
}

do_rollback() {
  echo "== rollback (delete migrated target rows; source untouched)"
  for (( idx=${#PAIRS[@]}-1 ; idx>=0 ; idx-- )) ; do
    read -r src ts tt pk extra <<< "${PAIRS[$idx]}"
    [[ -z "$src" ]] && continue
    local n
    n="$(fbq "SELECT COUNT(*) FROM \`$ts\`.\`$tt\` WHERE \`$pk\` IN (SELECT \`$pk\` FROM \`$SOURCE_SCHEMA\`.\`$src\`);")"
    if [[ "$n" == "0" ]]; then echo "skip  $ts.$tt (0 rows)"; continue; fi
    fbq "DELETE FROM \`$ts\`.\`$tt\` WHERE \`$pk\` IN (SELECT \`$pk\` FROM \`$SOURCE_SCHEMA\`.\`$src\`);" >/dev/null
    echo "rolled back $ts.$tt (-$n)"
  done
}

do_archive_retired() {
  mkdir -p "$AUDIT_DIR"
  local out="$AUDIT_DIR/retired-tables-archive.sql"
  echo "-- Retired legacy tables archive (structure + data). Disposition: retire," > "$out"
  echo "-- not carried into owner schemas. Generated $(date -Is)." >> "$out"
  local t
  for t in "${RETIRED_TABLES[@]}"; do
    if _fb_table_exists "$SOURCE_SCHEMA" "$t"; then
      docker exec "$MIGRATION_MYSQL_CONTAINER" mysqldump -uroot -p"${MIGRATION_DB_PASSWORD}" \
        --no-tablespaces "$SOURCE_SCHEMA" "$t" >> "$out" 2>/dev/null \
        && echo "archived $SOURCE_SCHEMA.$t" || echo "WARN dump failed for $t"
    else
      echo "absent $t (nothing to archive)"
    fi
  done
  echo "archive: $out"
}

case "$ACTION" in
  preflight)       do_preflight ;;
  backfill)        do_preflight; do_backfill ;;
  verify)          do_verify ;;
  rollback)        do_preflight; do_rollback ;;
  archive-retired) do_archive_retired ;;
  converge)        do_preflight; do_converge ;;
esac
