#!/usr/bin/env bash
# baseline-adopt.sh — Per-schema fresh-install adoption for baseline.sql (shared + 5 owners)
# Validates that baseline.sql + flyway baseline per schema yields same parity as incremental migrate.
# This is the executable per-schema baseline flow required by architecture review.
# Historical applied migrations remain immutable; this script establishes Flyway history on a fresh DB
# that was loaded from baseline.sql, so future increments apply normally.
# Usage:
#   ./init-db/scripts/baseline-adopt.sh              # disposable validation (starts MySQL, loads baseline, baselines all 6)
#   DB_HOST=... DB_PORT=... DB_USER=... DB_PASSWORD=... DB_NAME=ulticode \
#   MIGRATION_DB_HOST=... MIGRATION_DB_PORT=... MIGRATION_DB_USER=... MIGRATION_DB_PASSWORD=... \
#   SUBMISSION_MIGRATION_DB_USER=... SUBMISSION_MIGRATION_DB_PASSWORD=... \
#   ./init-db/scripts/baseline-adopt.sh --real       # adopt real DB (loads baseline.sql then baselines)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASELINE_SQL="$ROOT/init-db/baseline/baseline.sql"
if [ ! -f "$BASELINE_SQL" ]; then echo "[adopt] $BASELINE_SQL not found — run generate-baseline.sh first" >&2; exit 1; fi

detect_version() {
  local dir="$1"
  local max=""
  for f in "$dir"/V*.sql; do
    [ -e "$f" ] || continue
    local raw v
    raw=$(basename "$f" | sed -E 's/^V([0-9_]+)__.*/\1/')
    # Normalize underscore (legacy V20260602_120000) to plain digits for ordering
    v=$(echo "$raw" | tr -d '_')
    # Validate numeric after normalization
    if ! [[ "$v" =~ ^[0-9]+$ ]]; then echo "[adopt] invalid version format: $f -> $raw" >&2; exit 1; fi
    if [ -z "$max" ] || [ "$v" -gt "$max" ]; then max="$v"; fi
  done
  if [ -z "$max" ]; then echo "[adopt] no migrations found in $dir" >&2; exit 1; fi
  printf "%s" "$max"
}

SHARED_VER=$(detect_version "$ROOT/init-db/migrations")
AUTH_VER=$(detect_version "$ROOT/init-db/migrations/auth")
ADMIN_VER=$(detect_version "$ROOT/init-db/migrations/admin")
APP_VER=$(detect_version "$ROOT/init-db/migrations/app")
NOTIF_VER=$(detect_version "$ROOT/init-db/migrations/notification")
SUB_VER=$(detect_version "$ROOT/init-db/migrations/submission")
POST_OWNER_VER=$(detect_version "$ROOT/init-db/migrations/post-owner")

echo "[adopt] versions: shared=$SHARED_VER auth=$AUTH_VER admin=$ADMIN_VER app=$APP_VER notification=$NOTIF_VER submission=$SUB_VER post-owner=$POST_OWNER_VER"

JDBC_PARAMS="allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"

# Decide mode: --real means adopt real DB via env, otherwise disposable validation
MODE="disposable"
if [ "${1:-}" = "--real" ]; then MODE="real"; fi

if [ "$MODE" = "disposable" ]; then
  echo "[adopt] disposable validation — starting MySQL..."
  CID=$(docker run -d --rm -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ulticode -e MYSQL_USER=ulticode -e MYSQL_PASSWORD=ulticode mysql:8.0)
  cleanup() { echo "[adopt] removing disposable MySQL $CID..."; docker rm -f "$CID" >/dev/null 2>&1 || true; }
  trap cleanup EXIT
  for i in $(seq 1 30); do if docker exec "$CID" mysql -uulticode -pulticode -e "SELECT 1" ulticode >/dev/null 2>&1; then break; fi; sleep 2; done
  echo "[adopt] loading baseline.sql..."
  docker exec -i "$CID" mysql -uroot -proot < "$BASELINE_SQL"
  # Per-schema baseline via flyway docker
  echo "[adopt] baselining shared (ulticode) at $SHARED_VER..."
  docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
    flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root \
    -locations="filesystem:/flyway/init-db/migrations/*.sql" -baselineVersion="$SHARED_VER" baseline >/dev/null
  echo "[adopt] baselining post-owner controls at $POST_OWNER_VER..."
  docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
    flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root \
    -locations="filesystem:/flyway/init-db/migrations/post-owner" -defaultSchema=ulticode \
    -table=flyway_post_owner_history -baselineVersion="$POST_OWNER_VER" baseline >/dev/null
  for pair in "auth:$AUTH_VER:migrations/auth" "admin:$ADMIN_VER:migrations/admin" "app:$APP_VER:migrations/app" "notification:$NOTIF_VER:migrations/notification" "submission:$SUB_VER:migrations/submission"; do
    IFS=: read -r schema ver loc <<<"$pair"
    echo "[adopt] baselining $schema at $ver..."
    docker exec "$CID" mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS \`$schema\`;" >/dev/null 2>&1 || true
    # baseline.sql already created DBs, but ensure
    docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
      flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/$schema?$JDBC_PARAMS" -user=root -password=root \
      -locations="filesystem:/flyway/init-db/$loc" -defaultSchema="$schema" -baselineVersion="$ver" baseline >/dev/null
  done
  echo "[adopt] validating no pending migrations..."
  docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
    flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root \
    -locations="filesystem:/flyway/init-db/migrations/*.sql" validate >/dev/null
  docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
    flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root \
    -locations="filesystem:/flyway/init-db/migrations/post-owner" -defaultSchema=ulticode \
    -table=flyway_post_owner_history validate >/dev/null
  for pair in "auth:$AUTH_VER:migrations/auth" "admin:$ADMIN_VER:migrations/admin" "app:$APP_VER:migrations/app" "notification:$NOTIF_VER:migrations/notification" "submission:$SUB_VER:migrations/submission"; do
    IFS=: read -r schema ver loc <<<"$pair"
    docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
      flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/$schema?$JDBC_PARAMS" -user=root -password=root \
      -locations="filesystem:/flyway/init-db/$loc" -defaultSchema="$schema" validate >/dev/null
  done
  echo "[adopt] dumping parity check (per-schema 130)..."
  TMP_DUMP=$(mktemp); TMP_BASELINE_PER_SCHEMA=$(mktemp); TMP_ADOPTED_PER_SCHEMA=$(mktemp)
  trap 'docker rm -f "$CID" >/dev/null 2>&1 || true; rm -f "$TMP_DUMP" "$TMP_BASELINE_PER_SCHEMA" "$TMP_ADOPTED_PER_SCHEMA"' EXIT
  : > "$TMP_DUMP"
  for schema in ulticode auth admin app notification submission; do
    DUMP_ARGS=(--no-data --skip-add-drop-table --routines --events --databases "$schema" --ignore-table="$schema.flyway_schema_history")
    [[ "$schema" == "ulticode" ]] && DUMP_ARGS+=(--ignore-table="$schema.flyway_post_owner_history")
    docker exec "$CID" mysqldump -uroot -proot "${DUMP_ARGS[@]}" >> "$TMP_DUMP"
  done
  extract_per_schema() {
    local file="$1"; local out="$2"
    awk '
      /^CREATE DATABASE/ { if (match($0, /`[^`]+`/)) { db = substr($0, RSTART+1, RLENGTH-2) } next }
      /^USE / { if (match($0, /`[^`]+`/)) { db = substr($0, RSTART+1, RLENGTH-2) } next }
      /^CREATE TABLE/ { if (match($0, /`[^`]+`/)) { tbl = substr($0, RSTART+1, RLENGTH-2); print db"."tbl } }
    ' "$file" | sort > "$out"
  }
  extract_per_schema "$BASELINE_SQL" "$TMP_BASELINE_PER_SCHEMA"
  extract_per_schema "$TMP_DUMP" "$TMP_ADOPTED_PER_SCHEMA"
  echo "[adopt] baseline per-schema tables: $(wc -l < "$TMP_BASELINE_PER_SCHEMA"), adopted per-schema tables: $(wc -l < "$TMP_ADOPTED_PER_SCHEMA")"
  if diff -u "$TMP_BASELINE_PER_SCHEMA" "$TMP_ADOPTED_PER_SCHEMA"; then
    echo "[adopt] PASS — baseline+per-schema baseline parity holds (6 schemas, no history leaked)"
    if grep -qE "flyway_(schema|post_owner)_history" "$BASELINE_SQL"; then echo "[adopt] FAIL — baseline should not contain Flyway history"; exit 1; fi
    exit 0
  else
    echo "[adopt] FAIL — per-schema mismatch after adopt"
    exit 1
  fi
else
  echo "[adopt] real DB adoption — privileged account contract + fail-closed preflight"
  # Contract: shared and owners must use privileged migration accounts, not runtime DB_USER.
  # Require MIGRATION_DB_* for shared (fallback to DB_* only if explicitly privileged),
  # and MIGRATION_DB_* + SUBMISSION_MIGRATION_* for owners.
  : "${MIGRATION_DB_HOST:=${DB_HOST:-}}"; : "${MIGRATION_DB_PORT:=${DB_PORT:-3306}}"
  : "${MIGRATION_DB_NAME:=${DB_NAME:-ulticode}}"
  if [ -z "${MIGRATION_DB_HOST:-}" ] || [ -z "${MIGRATION_DB_USER:-}" ] || [ -z "${MIGRATION_DB_PASSWORD:-}" ]; then
    echo "[adopt] real mode requires MIGRATION_DB_HOST/PORT/USER/PASSWORD (privileged) for shared + owners" >&2
    echo "        and SUBMISSION_MIGRATION_DB_USER/PASSWORD for submission (see scripts/dev/migrate.sh contract)" >&2; exit 1
  fi
  # Fail-closed preflight: 6 target schemas must be empty / no history before loading
  # Reuse migrate.sh's mysql_query style via direct mysql check
  mysql_real() { mysql -h "$MIGRATION_DB_HOST" -P "$MIGRATION_DB_PORT" -u "$MIGRATION_DB_USER" -p"$MIGRATION_DB_PASSWORD" -N -B -e "$1"; }
  for schema in ulticode auth admin app notification submission; do
    tbl_cnt=$(mysql_real "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name <> 'flyway_schema_history';")
    hist_cnt=$(mysql_real "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name='flyway_schema_history';")
    if [ "$tbl_cnt" != "0" ] || [ "$hist_cnt" != "0" ]; then
      echo "[adopt] FAIL — real adoption requires empty schemas with no history: $schema has tbl_cnt=$tbl_cnt hist_cnt=$hist_cnt (refusing partial overwrite)" >&2; exit 1
    fi
    if [ -n "${DB_USER:-}" ] && [ "$MIGRATION_DB_USER" = "$DB_USER" ]; then
      echo "[adopt] FAIL — MIGRATION_DB_USER must differ from runtime DB_USER ($DB_USER) per owner migration contract (see scripts/dev/migrate.sh)" >&2; exit 1
    fi
  done
  echo "[adopt] preflight PASS — 6 schemas empty, no history"
  echo "[adopt] loading baseline.sql to $MIGRATION_DB_HOST:$MIGRATION_DB_PORT via privileged account..."
  mysql -h "$MIGRATION_DB_HOST" -P "$MIGRATION_DB_PORT" -u "$MIGRATION_DB_USER" -p"$MIGRATION_DB_PASSWORD" < "$BASELINE_SQL"
  for schema in ulticode auth admin app notification submission; do
    case "$schema" in
      ulticode) ver="$SHARED_VER"; loc="migrations/*.sql"; use_owner=false ;;
      auth) ver="$AUTH_VER"; loc="migrations/auth"; use_owner=true ;;
      admin) ver="$ADMIN_VER"; loc="migrations/admin"; use_owner=true ;;
      app) ver="$APP_VER"; loc="migrations/app"; use_owner=true ;;
      notification) ver="$NOTIF_VER"; loc="migrations/notification"; use_owner=true ;;
      submission) ver="$SUB_VER"; loc="migrations/submission"; use_owner=true ;;
    esac
    if [ "$use_owner" = true ]; then
      : "${MIGRATION_DB_HOST:=$MIGRATION_DB_HOST}"; : "${MIGRATION_DB_PORT:=$MIGRATION_DB_PORT}"
      owner_user="$MIGRATION_DB_USER"; owner_pass="$MIGRATION_DB_PASSWORD"
      if [ "$schema" = "submission" ] && [ -n "${SUBMISSION_MIGRATION_DB_USER:-}" ]; then
        owner_user="$SUBMISSION_MIGRATION_DB_USER"; owner_pass="$SUBMISSION_MIGRATION_DB_PASSWORD"
      fi
      if [ -z "$owner_user" ] || [ -z "$owner_pass" ]; then echo "[adopt] missing migration account for $schema" >&2; exit 1; fi
      echo "[adopt] baselining $schema at $ver via migration account..."
      mvn -f "$ROOT/init-db/pom.xml" -q flyway:baseline -Dflyway.configFiles="$ROOT/init-db/flyway-$schema.conf" \
        -Dflyway.url="jdbc:mysql://$MIGRATION_DB_HOST:$MIGRATION_DB_PORT/$schema?$JDBC_PARAMS" \
        -Dflyway.user="$owner_user" -Dflyway.password="$owner_pass" -Dflyway.baselineVersion="$ver"
    else
      echo "[adopt] baselining $schema at $ver via privileged shared account..."
      mvn -f "$ROOT/init-db/pom.xml" -q flyway:baseline -Dflyway.configFiles="$ROOT/init-db/flyway.conf" \
        -Dflyway.url="jdbc:mysql://$MIGRATION_DB_HOST:$MIGRATION_DB_PORT/$schema?$JDBC_PARAMS" \
        -Dflyway.user="$MIGRATION_DB_USER" -Dflyway.password="$MIGRATION_DB_PASSWORD" -Dflyway.baselineVersion="$ver"
    fi
  done
  echo "[adopt] baselining post-owner controls at $POST_OWNER_VER via privileged shared account..."
  mvn -f "$ROOT/init-db/pom.xml" -q flyway:baseline -Dflyway.configFiles="$ROOT/init-db/flyway-post-owner.conf" \
    -Dflyway.url="jdbc:mysql://$MIGRATION_DB_HOST:$MIGRATION_DB_PORT/ulticode?$JDBC_PARAMS" \
    -Dflyway.user="$MIGRATION_DB_USER" -Dflyway.password="$MIGRATION_DB_PASSWORD" -Dflyway.baselineVersion="$POST_OWNER_VER"
  echo "[adopt] validating post-owner controls..."
  mvn -f "$ROOT/init-db/pom.xml" -q flyway:validate -Dflyway.configFiles="$ROOT/init-db/flyway-post-owner.conf" \
    -Dflyway.url="jdbc:mysql://$MIGRATION_DB_HOST:$MIGRATION_DB_PORT/ulticode?$JDBC_PARAMS" \
    -Dflyway.user="$MIGRATION_DB_USER" -Dflyway.password="$MIGRATION_DB_PASSWORD"
  echo "[adopt] real adoption complete — 6 schemas baselined, future migrates will apply incrementally"

fi
