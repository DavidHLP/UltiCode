#!/usr/bin/env bash
# generate-baseline.sh — produce init-db/baseline/baseline.sql from current Flyway history
# Preserves immutable migration history: no file under migrations/ is edited or moved.
set -euo pipefail
OUTPUT="${1:-init-db/baseline/baseline.sql}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASELINE_DIR="$(dirname "$OUTPUT")"
mkdir -p "$BASELINE_DIR"
echo "[baseline] starting disposable MySQL..."
CID=$(docker run -d --rm -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ulticode -e MYSQL_USER=ulticode -e MYSQL_PASSWORD=ulticode mysql:8.0)
cleanup() { echo "[baseline] removing disposable MySQL $CID..."; docker rm -f "$CID" >/dev/null 2>&1 || true; }
trap cleanup EXIT
echo "[baseline] waiting for MySQL to be ready..."
for i in $(seq 1 30); do if docker exec "$CID" mysql -uulticode -pulticode -e "SELECT 1" ulticode >/dev/null 2>&1; then break; fi; sleep 2; done
JDBC_PARAMS="allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"
echo "[baseline] running shared Flyway chain..."
docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
  flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/ulticode?$JDBC_PARAMS" -user=root -password=root -locations="filesystem:/flyway/init-db/migrations/*.sql" -connectRetries=10 migrate
for CONF in flyway-auth.conf flyway-admin.conf flyway-app.conf flyway-notification.conf flyway-submission.conf; do
  SCHEMA=$(grep flyway.defaultSchema "$ROOT/init-db/$CONF" | cut -d= -f2)
  OWNER_DIR=$(grep flyway.locations "$ROOT/init-db/$CONF" | sed -E 's/.*filesystem:([^, ]+).*/\1/' | head -n1)
  echo "[baseline] migrating owner $SCHEMA via $CONF (locations=$OWNER_DIR)..."
  docker exec "$CID" mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS \`$SCHEMA\`;" >/dev/null
  docker run --rm --network "container:$CID" -v "$ROOT/init-db:/flyway/init-db:ro" \
    flyway/flyway:10.17.0 -url="jdbc:mysql://127.0.0.1:3306/$SCHEMA?$JDBC_PARAMS" -user=root -password=root -locations="filesystem:/flyway/init-db/$OWNER_DIR" -defaultSchema="$SCHEMA" -baselineOnMigrate=true -connectRetries=10 migrate
done
echo "[baseline] dumping --no-data schema to $OUTPUT (per-schema, no flyway history)..."
# Use --databases to emit CREATE DATABASE / USE boundaries and omit Flyway history
: > "$OUTPUT"
for SCHEMA in ulticode auth admin app notification submission; do
  echo "--" >> "$OUTPUT"
  echo "-- Dumping schema: $SCHEMA" >> "$OUTPUT"
  echo "--" >> "$OUTPUT"
  docker exec "$CID" mysqldump -uroot -proot --no-data --skip-add-drop-table --routines --events --databases "$SCHEMA" --ignore-table="$SCHEMA.flyway_schema_history" >> "$OUTPUT"
done
echo "[baseline] generated $OUTPUT ($(wc -l < "$OUTPUT") lines, $(grep -c "CREATE TABLE" "$OUTPUT") tables, $(grep -c "CREATE DATABASE" "$OUTPUT") schemas)"
