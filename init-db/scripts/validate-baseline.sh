#!/usr/bin/env bash
# validate-baseline.sh — ensure baseline.sql matches a fully-migrated disposable DB (all 5 owners, per-schema)
set -euo pipefail
BASELINE="init-db/baseline/baseline.sql"
if [ ! -f "$BASELINE" ]; then echo "[validate] $BASELINE not found — run ./init-db/scripts/generate-baseline.sh first"; exit 1; fi
echo "[validate] baseline exists: $(wc -l < "$BASELINE") lines, $(grep -c "CREATE TABLE" "$BASELINE") tables, $(grep -c "CREATE DATABASE" "$BASELINE") schemas"
for T in users problems submissions contests audit_logs audit_outbox; do if ! grep -q "CREATE TABLE.*\`$T\`" "$BASELINE"; then echo "[validate] warning: $T not found in baseline (may be owner-scoped)"; fi; done
echo "[validate] running disposable migration for comparison..."
TMP_DUMP=$(mktemp); TMP_BASELINE_PER_SCHEMA=$(mktemp); TMP_MIGRATED_PER_SCHEMA=$(mktemp)
CID=$(docker run -d --rm -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ulticode -e MYSQL_USER=ulticode -e MYSQL_PASSWORD=ulticode mysql:8.0)
cleanup() { docker rm -f "$CID" >/dev/null 2>&1 || true; rm -f "$TMP_DUMP" "$TMP_BASELINE_PER_SCHEMA" "$TMP_MIGRATED_PER_SCHEMA"; }
trap cleanup EXIT
for i in $(seq 1 30); do if docker exec "$CID" mysql -uulticode -pulticode -e "SELECT 1" ulticode >/dev/null 2>&1; then break; fi; sleep 2; done
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDBC_PARAMS="allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"
docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root -locations="filesystem:/flyway/init-db/migrations/*.sql" -connectRetries=10 migrate >/dev/null
for CONF in flyway-auth.conf flyway-admin.conf flyway-app.conf flyway-notification.conf flyway-submission.conf; do SCHEMA=$(grep flyway.defaultSchema "$ROOT/init-db/$CONF" | cut -d= -f2); OWNER_DIR=$(grep flyway.locations "$ROOT/init-db/$CONF" | sed -E 's/.*filesystem:([^, ]+).*/\1/' | head -n1); docker exec "$CID" mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS \`$SCHEMA\`;" >/dev/null; docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/$SCHEMA?$JDBC_PARAMS" -user=root -password=root -locations="filesystem:/flyway/init-db/$OWNER_DIR" -defaultSchema="$SCHEMA" -baselineOnMigrate=true -connectRetries=10 migrate >/dev/null; done
: > "$TMP_DUMP"
for SCHEMA in ulticode auth admin app notification submission; do docker exec "$CID" mysqldump -uroot -proot --no-data --skip-add-drop-table --routines --events --databases "$SCHEMA" --ignore-table="$SCHEMA.flyway_schema_history" >> "$TMP_DUMP"; done
# Per-schema validation: extract schema.table pairs (using USE + CREATE TABLE context)
extract_per_schema() {
  local file="$1"
  local out="$2"
  awk '
    /^CREATE DATABASE/ { gsub(/.*`([^`]+)`.*/, "\\1", $0); db=$0; next }
    /^USE / { gsub(/.*`([^`]+)`.*/, "\\1", $0); db=$0; next }
    /^CREATE TABLE/ { gsub(/.*`([^`]+)`.*/, "\\1", $0); tbl=$0; print db"."tbl }
  ' "$file" | sort > "$out"
}
extract_per_schema "$BASELINE" "$TMP_BASELINE_PER_SCHEMA"
extract_per_schema "$TMP_DUMP" "$TMP_MIGRATED_PER_SCHEMA"
echo "[validate] baseline per-schema tables: $(wc -l < "$TMP_BASELINE_PER_SCHEMA"), migrated per-schema tables: $(wc -l < "$TMP_MIGRATED_PER_SCHEMA")"
if diff -u "$TMP_BASELINE_PER_SCHEMA" "$TMP_MIGRATED_PER_SCHEMA"; then
  echo "[validate] PASS — all owner schemas per-table match (no duplicate-insensitive)"
  # Also ensure no flyway history leaked
  if grep -q "flyway_schema_history" "$BASELINE"; then echo "[validate] FAIL — baseline should not contain flyway_schema_history"; exit 1; fi
  exit 0
else
  echo "[validate] FAIL — per-schema table set mismatch"
  exit 1
fi
