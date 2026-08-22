#!/usr/bin/env bash
# validate-baseline.sh — ensure baseline.sql matches a fully-migrated disposable DB (all 5 owners)
set -euo pipefail
BASELINE="init-db/baseline/baseline.sql"
if [ ! -f "$BASELINE" ]; then echo "[validate] $BASELINE not found — run ./init-db/scripts/generate-baseline.sh first"; exit 1; fi
echo "[validate] baseline exists: $(wc -l < "$BASELINE") lines, $(grep -c "CREATE TABLE" "$BASELINE") tables"
for T in users problems submissions contests audit_logs audit_outbox; do if ! grep -q "CREATE TABLE.*\`$T\`" "$BASELINE"; then echo "[validate] warning: $T not found in baseline (may be owner-scoped)"; fi; done
echo "[validate] running disposable migration for comparison..."
TMP_DUMP=$(mktemp); TMP_BASELINE_TABLES=$(mktemp); TMP_MIGRATED_TABLES=$(mktemp)
CID=$(docker run -d --rm -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ulticode -e MYSQL_USER=ulticode -e MYSQL_PASSWORD=ulticode mysql:8.0)
cleanup() { docker rm -f "$CID" >/dev/null 2>&1 || true; rm -f "$TMP_DUMP" "$TMP_BASELINE_TABLES" "$TMP_MIGRATED_TABLES"; }
trap cleanup EXIT
for i in $(seq 1 30); do if docker exec "$CID" mysql -uulticode -pulticode -e "SELECT 1" ulticode >/dev/null 2>&1; then break; fi; sleep 2; done
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDBC_PARAMS="allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"
docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root  -locations="filesystem:/flyway/init-db/migrations/*.sql" -connectRetries=10 migrate >/dev/null
for CONF in flyway-auth.conf flyway-admin.conf flyway-app.conf flyway-notification.conf flyway-submission.conf; do SCHEMA=$(grep flyway.defaultSchema "$ROOT/init-db/$CONF" | cut -d= -f2); OWNER_DIR=$(grep flyway.locations "$ROOT/init-db/$CONF" | sed -E 's/.*filesystem:([^, ]+).*/\1/' | head -n1); docker exec "$CID" mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS \`$SCHEMA\`;" >/dev/null; docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/$SCHEMA?$JDBC_PARAMS" -user=root -password=root -locations="filesystem:/flyway/init-db/$OWNER_DIR" -defaultSchema="$SCHEMA" -baselineOnMigrate=true -connectRetries=10 migrate >/dev/null; done
docker exec "$CID" mysqldump -uroot -proot --no-data --skip-add-drop-table --routines --events ulticode > "$TMP_DUMP"
for SCHEMA in auth admin app notification submission; do echo "-- Owner schema: $SCHEMA" >> "$TMP_DUMP"; docker exec "$CID" mysqldump -uroot -proot --no-data --skip-add-drop-table "$SCHEMA" >> "$TMP_DUMP"; done
grep "CREATE TABLE" "$BASELINE" | sed -E "s/.*CREATE TABLE \`([^\\\`]+)\`.*/\1/" | sort > "$TMP_BASELINE_TABLES"
grep "CREATE TABLE" "$TMP_DUMP" | sed -E "s/.*CREATE TABLE \`([^\\\`]+)\`.*/\1/" | sort > "$TMP_MIGRATED_TABLES"
echo "[validate] baseline tables: $(wc -l < "$TMP_BASELINE_TABLES"), migrated tables: $(wc -l < "$TMP_MIGRATED_TABLES")"
if diff -u "$TMP_BASELINE_TABLES" "$TMP_MIGRATED_TABLES"; then echo "[validate] PASS — all owner schemas match"; exit 0; else echo "[validate] FAIL — table set mismatch"; exit 1; fi
