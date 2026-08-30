#!/usr/bin/env bash
set -euo pipefail

# P1-AUDIT-001 disposable proof: owner migrations create local audit outboxes,
# create the Admin inbox, and remove the historical Admin-table write grants.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MYSQL_CONTAINER="ulticode-audit-boundary-test-mysql-$$"
ROOT_PASSWORD="$(openssl rand -hex 16)"

cleanup() {
  docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "$MYSQL_CONTAINER" \
  -e MYSQL_ROOT_PASSWORD="$ROOT_PASSWORD" \
  -p 127.0.0.1::3306 mysql:8.0 \
  --character-set-server=utf8mb4 \
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

mysql_root() {
  docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
    mysql -uroot --batch --skip-column-names "$@"
}

mysql_owner() {
  local user="$1" password="$2" schema="$3" sql="$4"
  docker exec -e MYSQL_PWD="$password" "$MYSQL_CONTAINER" \
    mysql -h127.0.0.1 -u"$user" --batch --skip-column-names "$schema" -e "$sql"
}

mysql_root -e "
CREATE DATABASE auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE admin.audit_outbox (
  id VARCHAR(40) NOT NULL PRIMARY KEY,
  performer_id VARCHAR(40) NOT NULL
);
CREATE USER 'auth_rw'@'%' IDENTIFIED BY 'auth-boundary-test';
CREATE USER 'app_rw'@'%' IDENTIFIED BY 'app-boundary-test';
GRANT USAGE ON *.* TO 'auth_rw'@'%';
GRANT USAGE ON *.* TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON auth.* TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON app.* TO 'app_rw'@'%';
GRANT INSERT ON admin.audit_outbox TO 'auth_rw'@'%';
GRANT INSERT ON admin.audit_outbox TO 'app_rw'@'%';
FLUSH PRIVILEGES;
"

docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot admin < "$ROOT_DIR/init-db/migrations/admin/V20260831100200__Create_Admin_Audit_Inbox.sql"
docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot auth < "$ROOT_DIR/init-db/migrations/auth/V20260831100000__Create_Auth_Audit_Outbox.sql"
docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$MYSQL_CONTAINER" \
  mysql -uroot app < "$ROOT_DIR/init-db/migrations/app/V20260831100100__Create_App_Audit_Outbox.sql"

for account in auth_rw app_rw; do
  remaining="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.table_privileges WHERE GRANTEE = '''$account''@''%''' AND TABLE_SCHEMA = 'admin' AND TABLE_NAME = 'audit_outbox' AND PRIVILEGE_TYPE = 'INSERT';")"
  [[ "$remaining" == "0" ]] || {
    echo "cross-owner audit grant remains for $account" >&2
    exit 1
  }
done

mysql_owner auth_rw auth-boundary-test auth \
  "INSERT INTO audit_outbox (id, performer_id, action, entity_type, entity_id) VALUES ('auth-audit-1', 'u-1', 'TEST', 'USER', 'u-1')"
mysql_owner app_rw app-boundary-test app \
  "INSERT INTO audit_outbox (id, performer_id, action, entity_type, entity_id) VALUES ('app-audit-1', 'u-1', 'TEST', 'USER', 'u-1')"

if mysql_owner auth_rw auth-boundary-test auth \
    "INSERT INTO admin.audit_outbox (id, performer_id) VALUES ('cross-auth', 'u-1')" \
    >/dev/null 2>&1; then
  echo 'auth_rw can still write admin.audit_outbox' >&2
  exit 1
fi
if mysql_owner app_rw app-boundary-test app \
    "INSERT INTO admin.audit_outbox (id, performer_id) VALUES ('cross-app', 'u-1')" \
    >/dev/null 2>&1; then
  echo 'app_rw can still write admin.audit_outbox' >&2
  exit 1
fi

for schema in auth app; do
  table_count="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schema' AND table_name = 'audit_outbox';")"
  [[ "$table_count" == "1" ]] || exit 1
done
inbox_count="$(mysql_root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'admin' AND table_name = 'consumer_inbox';")"
[[ "$inbox_count" == "1" ]] || exit 1

printf 'audit owner-local tables: PASS\n'
printf 'cross-owner audit grant revocation: PASS\n'
printf 'admin audit inbox migration: PASS\n'
printf 'audit-owner-boundary-contract: PASS\n'
