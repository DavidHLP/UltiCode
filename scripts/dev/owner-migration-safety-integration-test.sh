#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MYSQL_TEST_CONTAINER="ulticode-owner-migration-test-mysql-$$"
REDIS_TEST_CONTAINER="ulticode-owner-migration-test-redis-$$"
TEST_DIR="$(mktemp -d)"
ROOT_PASSWORD="$(openssl rand -hex 16)"
MIGRATION_PASSWORD="$(openssl rand -hex 16)"
RUNTIME_PASSWORD="$(openssl rand -hex 16)"

cleanup() {
  docker rm -f "$MYSQL_TEST_CONTAINER" "$REDIS_TEST_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT
trap 'printf "owner-migration-safety-integration-test: FAIL line=%s\n" "$LINENO" >&2' ERR

docker run -d --name "$MYSQL_TEST_CONTAINER" -e MYSQL_ROOT_PASSWORD="$ROOT_PASSWORD" \
  -p 127.0.0.1::3306 mysql:9.1 --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci >/dev/null
docker run -d --name "$REDIS_TEST_CONTAINER" redis:7-alpine >/dev/null

for _ in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_TEST_CONTAINER" \
      mysql -uroot -N -B -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_TEST_CONTAINER" \
  mysql -uroot -N -B -e 'SELECT 1' >/dev/null

PUBLISHED_ENDPOINT="$(docker port "$MYSQL_TEST_CONTAINER" 3306/tcp)"
MYSQL_TEST_PORT="${PUBLISHED_ENDPOINT##*:}"
TEST_ENV="$TEST_DIR/test.env"
{
  printf 'DB_HOST=127.0.0.1\nDB_PORT=%s\nDB_NAME=ulticode\nDB_USER=ulticode\nDB_PASSWORD=%s\n' "$MYSQL_TEST_PORT" "$RUNTIME_PASSWORD"
  printf 'AUTH_DB_USER=auth_rw\nAPP_DB_USER=app_rw\n'
  printf 'MIGRATION_DB_HOST=127.0.0.1\nMIGRATION_DB_PORT=%s\nMIGRATION_DB_USER=root\nMIGRATION_DB_PASSWORD=%s\n' "$MYSQL_TEST_PORT" "$ROOT_PASSWORD"
  printf 'MIGRATION_MYSQL_CONTAINER=%s\nMIGRATION_MYSQL_CONTAINER_PORT=3306\n' "$MYSQL_TEST_CONTAINER"
  printf 'APP_SUBMISSION_ROUTING_MODE=local\nSUBMISSION_CUTOVER_COMPLETE=false\n'
} > "$TEST_ENV"

mysql_root() {
  docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_TEST_CONTAINER" mysql -uroot "$@"
}

mysql_root -e "
CREATE DATABASE auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE ulticode CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE submission CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'auth_rw'@'%' IDENTIFIED BY '$RUNTIME_PASSWORD';
CREATE USER 'app_rw'@'%' IDENTIFIED BY '$RUNTIME_PASSWORD';
CREATE USER 'migration_missing'@'%' IDENTIFIED BY '$MIGRATION_PASSWORD';
CREATE USER 'migration_full'@'%' IDENTIFIED BY '$MIGRATION_PASSWORD';
GRANT SELECT, CREATE, ALTER, INDEX, REFERENCES ON auth.* TO 'migration_missing'@'%' WITH GRANT OPTION;
GRANT RELOAD ON *.* TO 'migration_missing'@'%';
GRANT SELECT ON auth.* TO 'migration_full'@'%' WITH GRANT OPTION;
GRANT INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON auth.* TO 'migration_full'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON app.* TO 'migration_full'@'%' WITH GRANT OPTION;
GRANT RELOAD ON *.* TO 'migration_full'@'%';"

set +e
env ENV_FILE="$TEST_ENV" MIGRATION_SCHEMA=auth MIGRATION_DB_HOST=127.0.0.1 \
  MIGRATION_DB_PORT="$MYSQL_TEST_PORT" MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_missing MIGRATION_DB_PASSWORD="$MIGRATION_PASSWORD" \
  MIGRATION_MYSQL_CONTAINER="$MYSQL_TEST_CONTAINER" MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  bash "$ROOT_DIR/scripts/dev/migrate.sh" validate > "$TEST_DIR/missing-dml.log" 2>&1
MISSING_DML_STATUS=$?
set -e
[[ "$MISSING_DML_STATUS" -ne 0 ]]
grep -q "required migration privilege missing on 'auth': INSERT" "$TEST_DIR/missing-dml.log"
printf 'missing Flyway DML rejection: PASS\n'

for OWNER_SCHEMA in auth app; do
  env ENV_FILE="$TEST_ENV" MIGRATION_SCHEMA="$OWNER_SCHEMA" MIGRATION_DB_HOST=127.0.0.1 \
    MIGRATION_DB_PORT="$MYSQL_TEST_PORT" MIGRATION_DB_NAME="$OWNER_SCHEMA" \
    MIGRATION_DB_USER=migration_full MIGRATION_DB_PASSWORD="$MIGRATION_PASSWORD" \
    MIGRATION_MYSQL_CONTAINER="$MYSQL_TEST_CONTAINER" MIGRATION_MYSQL_CONTAINER_PORT=3306 \
    bash "$ROOT_DIR/scripts/dev/migrate.sh" migrate > "$TEST_DIR/migrate-$OWNER_SCHEMA.log" 2>&1
  grep -q 'BUILD SUCCESS' "$TEST_DIR/migrate-$OWNER_SCHEMA.log"
done
printf 'Auth/App owner migrations: PASS\n'

mysql_root -e "
CREATE TABLE ulticode.users LIKE auth.users;
CREATE TABLE ulticode.user_profiles LIKE app.user_profiles;
INSERT INTO ulticode.users (id,username,email,password,is_deleted)
VALUES ('active-1','active-user','active@example.invalid','hash-active',0);
INSERT INTO ulticode.users (id,username,email,password,is_deleted,deleted_at,deleted_by)
VALUES ('deleted-1','deleted-user','deleted@example.invalid','hash-deleted',1,NOW(),'system');
INSERT INTO ulticode.user_profiles (account_id,name,preferred_language)
VALUES ('active-1','Active User','en-US'),('deleted-1','Deleted User','zh-CN');"

env ENV_FILE="$TEST_ENV" OWNER_BACKFILL_MANIFEST="$TEST_DIR/backfill.manifest" \
  DEV_LOCAL_OWNER_BACKFILL_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BACKFILL \
  DEV_LOCAL_OWNER_BACKFILL_QUIESCE_CONFIRM=I_UNDERSTAND_OWNER_PROFILE_QUIESCE \
  bash "$ROOT_DIR/scripts/dev/owner-user-profile-backfill.sh" backfill > "$TEST_DIR/backfill.log" 2>&1
BACKFILL_COUNTS="$(mysql_root -N -B -e "SELECT (SELECT COUNT(*) FROM auth.users),(SELECT COUNT(*) FROM auth.users WHERE is_deleted=1),(SELECT COUNT(*) FROM app.user_profiles),(SELECT COUNT(*) FROM app.user_profiles p JOIN auth.users u ON u.id=p.account_id WHERE u.is_deleted=1);")"
[[ "$BACKFILL_COUNTS" == $'2\t1\t2\t1' ]]
printf 'soft-deleted account/profile preservation: PASS\n'

mysql_root -e "
CREATE TABLE ulticode.submissions (id VARCHAR(40) PRIMARY KEY);
CREATE TABLE ulticode.judge_outbox (id VARCHAR(40) PRIMARY KEY);
CREATE TABLE ulticode.submission_result_outbox (id VARCHAR(40) PRIMARY KEY);
CREATE TABLE submission.submissions LIKE ulticode.submissions;
CREATE TABLE submission.judge_outbox LIKE ulticode.judge_outbox;
CREATE TABLE submission.submission_result_outbox LIKE ulticode.submission_result_outbox;
CREATE TABLE submission.submission_created_outbox (id VARCHAR(40) PRIMARY KEY);
CREATE USER 'ulticode'@'%' IDENTIFIED BY '$RUNTIME_PASSWORD';"
printf 'grant isolation fixture setup: PASS\n'

expect_rehearsal_grant_failure() {
  local label="$1"
  local status=0
  env ENV_FILE="$TEST_ENV" REDIS_CONTAINER="$REDIS_TEST_CONTAINER" REDIS_PASSWORD= \
    MONITORING_DB_HOST=127.0.0.1 MONITORING_DB_PORT="$MYSQL_TEST_PORT" \
    MONITORING_DB_USER=root MONITORING_DB_PASSWORD="$ROOT_PASSWORD" \
    MIGRATION_MYSQL_CONTAINER="$MYSQL_TEST_CONTAINER" MIGRATION_MYSQL_CONTAINER_PORT=3306 \
    DEV_LOCAL_OBSERVATION_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OBSERVATION_REHEARSAL \
    bash "$ROOT_DIR/scripts/dev/dev-local-observation-rehearsal.sh" --skip-tests > "$TEST_DIR/rehearsal-$label.log" 2>&1 || status=$?
  if [[ "$status" -eq 0 ]] || ! grep -q 'retains .* direct or role-derived privilege' "$TEST_DIR/rehearsal-$label.log"; then
    printf 'grant rejection fixture failed: scope=%s exit=%s\n' "$label" "$status"
    tail -40 "$TEST_DIR/rehearsal-$label.log" || true
    return 1
  fi
  printf '%s grant rejection: PASS\n' "$label"
}

mysql_root -e "GRANT INSERT ON submission.submissions TO 'ulticode'@'%';"
printf 'table grant fixture: READY\n'
expect_rehearsal_grant_failure table || exit 1
mysql_root -e "REVOKE INSERT ON submission.submissions FROM 'ulticode'@'%'; GRANT SELECT ON *.* TO 'ulticode'@'%';"
expect_rehearsal_grant_failure global || exit 1
mysql_root -e "REVOKE SELECT ON *.* FROM 'ulticode'@'%'; CREATE ROLE 'submission_writer'; GRANT UPDATE ON submission.judge_outbox TO 'submission_writer'; GRANT 'submission_writer' TO 'ulticode'@'%';"
expect_rehearsal_grant_failure role || exit 1
mysql_root -e "REVOKE 'submission_writer' FROM 'ulticode'@'%'; CREATE PROCEDURE submission.owner_probe() SQL SECURITY DEFINER SELECT 1; GRANT EXECUTE ON PROCEDURE submission.owner_probe TO 'ulticode'@'%';"
expect_rehearsal_grant_failure routine || exit 1
mysql_root -e "REVOKE EXECUTE ON PROCEDURE submission.owner_probe FROM 'ulticode'@'%';"

env ENV_FILE="$TEST_ENV" REDIS_CONTAINER="$REDIS_TEST_CONTAINER" REDIS_PASSWORD= \
  MONITORING_DB_HOST=127.0.0.1 MONITORING_DB_PORT="$MYSQL_TEST_PORT" \
  MONITORING_DB_USER=root MONITORING_DB_PASSWORD="$ROOT_PASSWORD" \
  MIGRATION_MYSQL_CONTAINER="$MYSQL_TEST_CONTAINER" MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  DEV_LOCAL_OBSERVATION_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OBSERVATION_REHEARSAL \
  bash "$ROOT_DIR/scripts/dev/dev-local-observation-rehearsal.sh" --skip-tests > "$TEST_DIR/rehearsal-zero.log" 2>&1
grep -q 'effective grants reaching .*ASSERTION PASS: 0' "$TEST_DIR/rehearsal-zero.log"
printf 'table/global/role/routine grant isolation: PASS\n'

printf 'owner-migration-safety-integration-test: PASS\n'
