#!/usr/bin/env bash
set -euo pipefail

# P1-DATA-001 disposable proof: reject an App writer grant, then contract an
# upgrade-shaped source while keeping the Submission/Notification owner tables.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! java -version >/dev/null 2>&1 && command -v mise >/dev/null 2>&1; then
  exec mise exec java@zulu-17.68.203.0 -- bash "$0" "$@"
fi

MYSQL_CONTAINER="ulticode-contraction-test-mysql-$$"
TEST_DIR="$(mktemp -d)"
ROOT_PASSWORD="$(openssl rand -hex 16)"
TEST_ENV="$TEST_DIR/test.env"

cleanup() {
  docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap 'printf "owner-schema-contraction-contract: FAIL line=%s\n" "$LINENO" >&2' ERR
trap cleanup EXIT

docker run -d --name "$MYSQL_CONTAINER" -e MYSQL_ROOT_PASSWORD="$ROOT_PASSWORD" \
  -p 127.0.0.1::3306 mysql:8.0 --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci >/dev/null

for _ in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
      mysql -uroot -N -B -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot -N -B -e 'SELECT 1' >/dev/null

MYSQL_PORT="$(docker port "$MYSQL_CONTAINER" 3306/tcp)"
MYSQL_PORT="${MYSQL_PORT##*:}"
{
  printf 'DB_HOST=127.0.0.1\nDB_PORT=%s\nDB_NAME=ulticode\nDB_USER=root\nDB_PASSWORD=%s\n' \
    "$MYSQL_PORT" "$ROOT_PASSWORD"
  printf 'MIGRATION_DB_HOST=127.0.0.1\nMIGRATION_DB_PORT=%s\nMIGRATION_DB_NAME=ulticode\n' "$MYSQL_PORT"
  printf 'MIGRATION_DB_USER=root\nMIGRATION_DB_PASSWORD=%s\n' "$ROOT_PASSWORD"
  printf 'MIGRATION_MYSQL_CONTAINER=%s\nMIGRATION_MYSQL_CONTAINER_PORT=3306\n' "$MYSQL_CONTAINER"
  printf 'SUBMISSION_APP_DB_USER=app_rw\nSUBMISSION_APP_DB_HOST=%%\n'
  printf 'OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM=I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP\n'
  printf 'OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_OWNER_WRITERS\n'
  printf 'OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE=contract-test-backup\n'
} >"$TEST_ENV"

mysql_root() {
  docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot "$@"
}

mysql_root -e "
CREATE DATABASE ulticode CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE submission CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE notification CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'app_rw'@'%' IDENTIFIED BY 'test-only-password';
CREATE TABLE ulticode.owner_contraction_proof (
  owner varchar(32) NOT NULL PRIMARY KEY,
  source_schema varchar(64) NOT NULL,
  target_schema varchar(64) NOT NULL,
  source_rows bigint NOT NULL,
  target_rows bigint NOT NULL,
  snapshot_hash char(64) NOT NULL,
  app_account varchar(128) NOT NULL,
  app_dml_grants int NOT NULL,
  backup_reference varchar(255) NOT NULL,
  backup_verified_at datetime(3) NOT NULL,
  writers_quiesced_at datetime(3) NOT NULL,
  verified_at datetime(3) NOT NULL,
  verified_by varchar(128) NOT NULL
);"

SUBMISSION_TABLES=(submissions judge_outbox submission_result_outbox)
NOTIFICATION_TABLES=(notifications notification_preferences notification_delivery_ledger email_templates email_logs notification_command_receipt)
for table in "${SUBMISSION_TABLES[@]}"; do
  mysql_root -e "CREATE TABLE ulticode.$table (id varchar(40) NOT NULL PRIMARY KEY, payload varchar(255) NOT NULL); CREATE TABLE submission.$table LIKE ulticode.$table; INSERT INTO ulticode.$table VALUES ('$table-1', 'upgrade-row'); INSERT INTO submission.$table SELECT * FROM ulticode.$table; GRANT SELECT, INSERT, UPDATE, DELETE ON ulticode.$table TO 'app_rw'@'%';"
done
for table in "${NOTIFICATION_TABLES[@]}"; do
  mysql_root -e "CREATE TABLE ulticode.$table (id varchar(40) NOT NULL PRIMARY KEY, payload varchar(255) NOT NULL); CREATE TABLE notification.$table LIKE ulticode.$table; INSERT INTO ulticode.$table VALUES ('$table-1', 'upgrade-row'); INSERT INTO notification.$table SELECT * FROM ulticode.$table; GRANT SELECT, INSERT, UPDATE, DELETE ON ulticode.$table TO 'app_rw'@'%';"
done

if ENV_FILE="$TEST_ENV" bash "$ROOT_DIR/scripts/dev/migrate.sh" contract \
    >"$TEST_DIR/no-confirm.log" 2>&1; then
  echo 'contraction command bypassed confirmation gate' >&2
  exit 1
fi
grep -q 'OWNER_SCHEMA_CONTRACTION_CONFIRM' "$TEST_DIR/no-confirm.log"
printf 'contraction confirmation gate: PASS\n'

if ENV_FILE="$TEST_ENV" bash "$ROOT_DIR/scripts/runbooks/owner-schema-contraction.sh" preflight \
    >"$TEST_DIR/grant-preflight.log" 2>&1; then
  echo 'preflight accepted an active App writer grant' >&2
  exit 1
fi
grep -q 'App legacy-table privileges remain' "$TEST_DIR/grant-preflight.log"
printf 'legacy App grant rejection: PASS\n'

printf 'legacy grant remains until explicit contract: PASS\n'

mysql_root -e "GRANT SELECT ON ulticode.* TO 'app_rw'@'%';"
if ENV_FILE="$TEST_ENV" \
    OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION \
    bash "$ROOT_DIR/scripts/runbooks/owner-schema-contraction.sh" contract --execute \
    >"$TEST_DIR/broad-grant.log" 2>&1; then
  echo 'contraction accepted a non-table App privilege' >&2
  exit 1
fi
grep -q 'non-table App privileges remain' "$TEST_DIR/broad-grant.log"
SOURCE_COUNT="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ulticode' AND table_name IN ('submissions','judge_outbox','submission_result_outbox','notifications','notification_preferences','notification_delivery_ledger','email_templates','email_logs','notification_command_receipt');")"
[[ "$SOURCE_COUNT" == 9 ]]
printf 'broader App grant fail-closed gate: PASS\n'
mysql_root -e "REVOKE SELECT ON ulticode.* FROM 'app_rw'@'%';"

if ! ENV_FILE="$TEST_ENV" \
    OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION \
    bash "$ROOT_DIR/scripts/runbooks/owner-schema-contraction.sh" contract --execute \
    >"$TEST_DIR/contract.log" 2>&1; then
  tail -80 "$TEST_DIR/contract.log" >&2
  exit 1
fi
grep -q 'CONTRACT PASS' "$TEST_DIR/contract.log"
grep -q 'REVOKED_APP_TABLE_GRANT=ulticode.submissions' "$TEST_DIR/contract.log"

SOURCE_COUNT="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ulticode' AND table_name IN ('submissions','judge_outbox','submission_result_outbox','notifications','notification_preferences','notification_delivery_ledger','email_templates','email_logs','notification_command_receipt');")"
TARGET_COUNT="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('submission','notification') AND table_name IN ('submissions','judge_outbox','submission_result_outbox','notifications','notification_preferences','notification_delivery_ledger','email_templates','email_logs','notification_command_receipt');")"
PROOF_COUNT="$(mysql_root -N -B -e "SELECT COUNT(*) FROM ulticode.owner_contraction_proof WHERE app_dml_grants=0 AND source_rows=target_rows;")"
[[ "$SOURCE_COUNT" == 0 ]]
[[ "$TARGET_COUNT" == 9 ]]
[[ "$PROOF_COUNT" == 2 ]]
printf 'forward contraction and owner preservation: PASS\n'
printf 'owner-schema-contraction-contract: PASS\n'
