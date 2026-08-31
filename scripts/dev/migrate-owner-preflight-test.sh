#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# Keep caller/test-environment container settings from changing fake-host cases.
unset MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT

FAKE_BIN="$TMP_DIR/bin"
ENV_FILE="$TMP_DIR/.env"
MAVEN_MARKER="$TMP_DIR/maven-marker"
mkdir -p "$FAKE_BIN"

cat >"$ENV_FILE" <<'EOF'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
NOTIFICATION_DB_USER=notification_rw
EOF

cat >"$TMP_DIR/stale.env" <<'EOF'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
MIGRATION_SCHEMA=auth
MIGRATION_DB_HOST=stale-host
MIGRATION_DB_PORT=3306
MIGRATION_DB_NAME=auth
MIGRATION_DB_USER=migration_user
MIGRATION_DB_PASSWORD=stale-secret
EOF

cat >"$TMP_DIR/override.env" <<'EOF'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
AUTH_DB_USER=auth_rw
EOF

cat >"$TMP_DIR/external.env" <<'EOF'
DB_HOST=external-host
DB_PORT=23306
DB_USER=migration_user
DB_PASSWORD=secret
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
EOF

cat >"$FAKE_BIN/mysql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
query=""
host=""
port=""
user=""
database=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --*) shift ;;
    -h) host="${2:-}"; shift 2 ;;
    -P) port="${2:-}"; shift 2 ;;
    -u) user="${2:-}"; shift 2 ;;
    -e) query="${2:-}"; shift 2 ;;
    *) database="${database:-$1}"; shift ;;
  esac
done
[[ "${MYSQL_PWD:-}" == "secret" ]] || exit 90
expected_database="auth"
if [[ "${FAKE_MYSQL_MODE:-success}" == "notification-create-user" || "${FAKE_MYSQL_MODE:-success}" == "notification-missing-grant-option" ]]; then
  expected_database="notification"
fi
if [[ "${FAKE_MYSQL_MODE:-}" != "auth-contract-gate" || -n "$database" ]]; then
  if [[ "${FAKE_MYSQL_CONTAINER_MODE:-false}" == "true" ]]; then
    [[ "$host" == "127.0.0.1" && "$port" == "3306" \
      && "$user" == "migration_user" && "$database" == "$expected_database" ]] || exit 91
  else
    [[ "$host" == "migration-host" && "$port" == "3306" \
      && "$user" == "migration_user" && "$database" == "$expected_database" ]] || exit 91
  fi
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "global-all" && "$query" == *"mysql.role_edges"* ]]; then
  exit 95
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "global-all" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT ALL PRIVILEGES ON *.* TO `migration_user`@`localhost` WITH GRANT OPTION\nGRANT SELECT ON `auth`.* TO `migration_user`@`localhost`\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "global-capabilities" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, CREATE USER ON *.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "wrong-schema" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT SELECT, CREATE, ALTER ON `other`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "table-grant" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT SELECT, CREATE, ALTER ON `auth`.`users` TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "global-option-only" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT USAGE ON *.* TO `migration_user`@`localhost` WITH GRANT OPTION\nGRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `auth`.* TO `migration_user`@`localhost`\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "missing-reload" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `auth`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "role" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT `migration_role`@`localhost` TO `migration_user`@`localhost`\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "mismatch" && "$query" == *"SUBSTRING_INDEX"* ]]; then
  printf 'unexpected_user\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "empty-grants" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "insufficient" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON `auth`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "missing-dml" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT RELOAD ON *.* TO `migration_user`@`localhost`\nGRANT SELECT, CREATE, ALTER, INDEX, REFERENCES ON `auth`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "missing-schema" && "$query" == *"information_schema.schemata"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "notification-create-user" && "$query" == *"SELECT DATABASE"* ]]; then
  printf 'notification\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "notification-missing-grant-option" && "$query" == *"SELECT DATABASE"* ]]; then
  printf 'notification\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "notification-create-user" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT CREATE USER, RELOAD ON *.* TO `migration_user`@`localhost` WITH GRANT OPTION\nGRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `notification`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "notification-missing-grant-option" && "$query" == *"SHOW GRANTS FOR CURRENT_USER"* ]]; then
  printf 'GRANT CREATE USER, RELOAD ON *.* TO `migration_user`@`localhost`\nGRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `notification`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "bootstrap-auth" && "$query" == *"table_name = 'flyway_schema_history'"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "bootstrap-auth" && "$query" == *"GROUP_CONCAT(CONCAT(table_name"* ]]; then
  printf 'search_document_changed_outbox:15\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "bootstrap-auth" && "$query" == *"GROUP_CONCAT(CONCAT(ordinal_position"* ]]; then
  printf '1:id:varchar(40):NO|2:owner:varchar(16):NO|3:aggregate_id:varchar(120):NO|4:aggregate_version:bigint:NO|5:event_type:varchar(64):NO|6:schema_version:int:NO|7:payload:json:NO|8:state:varchar(16):NO|9:attempts:int:NO|10:last_error:text:YES|11:created_at:datetime(3):NO|12:claimed_at:datetime(3):YES|13:claim_owner:varchar(80):YES|14:delivered_at:datetime(3):YES|15:next_retry_at:datetime(3):NO\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "bootstrap-auth" && "$query" == *"auth.search_document_changed_outbox"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "auth-contract-gate" && "$query" =~ information_schema\.tables ]]; then
  printf '1\n'
  exit 0
fi
if [[ "${FAKE_MYSQL_MODE:-success}" == "auth-contract-gate" && "$query" =~ information_schema\.columns ]]; then
  printf '9\n'
  exit 0
fi
case "$query" in
  *"SUBSTRING_INDEX"*) printf 'migration_user\n' ;;
  *"SELECT CURRENT_USER"*) printf 'migration_user@localhost\n' ;;
  *"SELECT DATABASE"*) printf 'auth\n' ;;
  *"information_schema.schemata"*) printf '1\n' ;;
  *"SHOW GRANTS FOR CURRENT_USER"*) printf 'GRANT RELOAD ON *.* TO `migration_user`@`localhost`\nGRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON `auth`.* TO `migration_user`@`localhost` WITH GRANT OPTION\n' ;;
  *) : ;;
esac
EOF

cat >"$FAKE_BIN/docker" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ "\${1:-}" == "inspect" ]]; then
  printf 'true\n'
  exit 0
fi
if [[ "\${1:-}" == "port" ]]; then
  [[ "\${2:-}" == "ulticode-mysql" && "\${3:-}" == "3306/tcp" ]] || exit 5
  printf '%s:23306\n' "\${FAKE_DOCKER_BIND_ADDRESS:-127.0.0.1}"
  exit 0
fi
[[ "\${1:-}" == "exec" ]] || exit 2
shift
while [[ "\${1:-}" == "-e" ]]; do
  export "\${2:?missing docker exec environment assignment}"
  shift 2
done
[[ "\${1:-}" == "ulticode-mysql" ]] || exit 3
shift
[[ "\${1:-}" == "mysql" ]] || exit 4
shift
FAKE_MYSQL_CONTAINER_MODE=true "$FAKE_BIN/mysql" "\$@"
EOF

chmod +x "$FAKE_BIN/docker"

cat >"$FAKE_BIN/mvn" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ "\${DB_NAME:-}" == "auth" ]]; then
  [[ "\${1:-}" == "flyway:validate" || "\${1:-}" == "flyway:baseline" ]] || exit 92
  [[ "\$*" == *"-Dflyway.configFiles=flyway-auth.conf"* ]] || exit 93
  if [[ "\${1:-}" == "flyway:baseline" ]]; then
    [[ "\$*" == *"-Dflyway.baselineVersion=20260729140000"* ]] || exit 94
  fi
fi
{
  printf 'args='
  printf '%q ' "\$@"
  printf ' DB_HOST=%s DB_PORT=%s DB_NAME=%s DB_USER=%s\n' \
    "\${DB_HOST:-}" "\${DB_PORT:-}" "\${DB_NAME:-}" "\${DB_USER:-}"
} >> "$MAVEN_MARKER"
EOF
chmod +x "$FAKE_BIN/mysql" "$FAKE_BIN/mvn"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  [[ "$haystack" == *"$needle"* ]] || {
    printf 'Expected output to contain: %s\nActual output:\n%s\n' "$needle" "$haystack" >&2
    exit 1
  }
}

run_expect_failure() {
  local output
  set +e
  output="$("$@" 2>&1)"
  local status=$?
  set -e
  [[ $status -ne 0 ]] || {
    printf 'Expected command to fail: %s\n' "$*" >&2
    exit 1
  }
  printf '%s' "$output"
}

missing_host_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$missing_host_output" "MIGRATION_DB_HOST is required"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when the owner migration preflight is incomplete.' >&2
  exit 1
}

stale_override_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/stale.env" \
  MIGRATION_DB_HOST= \
  MIGRATION_DB_PASSWORD= \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$stale_override_output" "MIGRATION_DB_HOST is required"

# A hostile .env must not be able to replace trusted lib helpers: they are
# frozen readonly -f before any .env is sourced, so redefinition fails closed.
rm -f "$MAVEN_MARKER"
cat >"$TMP_DIR/hostile-injection.env" <<'HOSTILE_ENV'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
apply_env_overrides() { :; }
HOSTILE_ENV

hostile_injection_status=0
env PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/hostile-injection.env" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate >/dev/null 2>&1 || hostile_injection_status=$?
[[ "$hostile_injection_status" -ne 0 ]] || {
  echo 'Hostile .env helper injection must fail closed.' >&2
  exit 1
}
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when .env tampers with trusted helpers.' >&2
  exit 1
}

# .env must not be able to redirect ENV_FILE or ROOT_DIR for later execution:
# load_env_file re-pins both, so migration still runs against the real target.
cat >"$TMP_DIR/env-redirect.env" <<'REDIRECT_ENV'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
ENV_FILE=/tmp/nonexistent-env-redirect
ROOT_DIR=/tmp/nonexistent-root-redirect
REDIRECT_ENV

env_redirect_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/env-redirect.env" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=redirect-run \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$env_redirect_output" "Migration preflight passed: run_id=redirect-run schema=auth"
rm -f "$MAVEN_MARKER"

# Clearing the captured-state arrays from .env must also fail closed: the
# arrays are frozen readonly by capture_env_vars, so the explicit-env-wins
# property cannot be bypassed by emptying them.
cat >"$TMP_DIR/hostile-capture-clear.env" <<'CAPTURE_CLEAR_ENV'
DB_HOST=runtime-host
DB_PORT=3306
DB_USER=ulticode
DB_PASSWORD=runtime-password
DB_NAME=ulticode
AUTH_DB_USER=auth_rw
MIGRATION_DB_HOST=evil-host
ULTICODE_CAPTURED_KEYS=()
CAPTURE_CLEAR_ENV

capture_clear_status=0
env PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/hostile-capture-clear.env" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate >/dev/null 2>&1 || capture_clear_status=$?
[[ "$capture_clear_status" -ne 0 ]] || {
  echo 'Hostile .env capture-array clearing must fail closed.' >&2
  exit 1
}
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when .env tampers with captured migration state.' >&2
  exit 1
}

same_account_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=auth_rw \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$same_account_output" "must differ from runtime owner account"

unsupported_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=ulticode \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=ulticode \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$unsupported_output" "unsupported MIGRATION_SCHEMA=ulticode"

mismatch_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=mismatch \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$mismatch_output" "effective account"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when CURRENT_USER does not match the requested account.' >&2
  exit 1
}

empty_grants_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=empty-grants \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$empty_grants_output" "privilege snapshot is empty"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when the privilege snapshot is empty.' >&2
  exit 1
}

insufficient_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=insufficient \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$insufficient_output" "required migration privilege missing on 'auth': ALTER"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when required migration privileges are missing.' >&2
  exit 1
}

missing_dml_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=missing-dml \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$missing_dml_output" "required migration privilege missing on 'auth': INSERT"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run without Flyway history DML privileges.' >&2
  exit 1
}

wrong_schema_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=wrong-schema \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$wrong_schema_output" "no grants on owner schema"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when grants target a different schema.' >&2
  exit 1
}

table_grant_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=table-grant \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$table_grant_output" "no grants on owner schema"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when only a table grant is supplied.' >&2
  exit 1
}

global_option_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=global-option-only \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$global_option_output" "required migration privilege missing on 'auth': GRANT OPTION"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when only a global grant option is supplied.' >&2
  exit 1
}


missing_reload_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=missing-reload \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$missing_reload_output" "required migration privilege missing: RELOAD"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run without the canonical FLUSH PRIVILEGES capability.' >&2
  exit 1
}

notification_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=notification-create-user \
  MIGRATION_SCHEMA=notification \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=notification \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=notification-create-user \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$notification_output" "Migration preflight passed: run_id=notification-create-user schema=notification database=notification account=migration_user"
assert_contains "$(cat "$MAVEN_MARKER")" "DB_HOST=migration-host DB_PORT=3306 DB_NAME=notification DB_USER=migration_user"
rm -f "$MAVEN_MARKER"

missing_schema_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=missing-schema \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$missing_schema_output" "owner schema 'auth' does not exist"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run before the explicit schema bootstrap.' >&2
  exit 1
}

global_all_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=global-all \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=global-all \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$global_all_output" "Migration preflight passed: run_id=global-all"
assert_contains "$(cat "$MAVEN_MARKER")" "DB_HOST=migration-host DB_PORT=3306 DB_NAME=auth DB_USER=migration_user"
rm -f "$MAVEN_MARKER"

global_capabilities_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=global-capabilities \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=global-capabilities \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$global_capabilities_output" "no grants on owner schema"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when only global direct capabilities are supplied.' >&2
  exit 1
}

notification_grant_option_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=notification-missing-grant-option \
  MIGRATION_SCHEMA=notification \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=notification \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$notification_grant_option_output" "global GRANT OPTION"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run without global grant option for Notification owner grants.' >&2
  exit 1
}

role_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=role \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$role_output" "role grants are not supported"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run when role grants are supplied.' >&2
  exit 1
}

baseline_missing_confirmation="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=bootstrap-auth \
  DEV_LOCAL_OWNER_BASELINE=true \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" baseline)"
assert_contains "$baseline_missing_confirmation" "DEV_LOCAL_OWNER_BASELINE_CONFIRM"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Maven must not run without explicit DEV-LOCAL baseline confirmation.' >&2
  exit 1
}

baseline_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=bootstrap-auth \
  DEV_LOCAL_OWNER_BASELINE=true \
  DEV_LOCAL_OWNER_BASELINE_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=baseline-run \
  "$ROOT_DIR/scripts/dev/migrate.sh" baseline 2>&1)"
assert_contains "$baseline_output" "DEV-LOCAL baseline preflight passed: schema=auth bootstrap=search_document_changed_outbox columns=15 baseline=20260729140000 rows=0"
baseline_marker="$(cat "$MAVEN_MARKER")"
assert_contains "$baseline_marker" "args=flyway:baseline"
assert_contains "$baseline_marker" "-Dflyway.baselineVersion=20260729140000"
[[ "$(wc -l < "$MAVEN_MARKER")" == "1" ]] || {
  echo 'DEV-LOCAL baseline must invoke exactly one Flyway goal.' >&2
  exit 1
}
rm -f "$MAVEN_MARKER"

owner_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_RUN_ID=test-run \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$owner_output" "Migration preflight passed: run_id=test-run schema=auth database=auth account=migration_user"
assert_contains "$owner_output" "Migration privilege snapshot (no password):"
assert_contains "$owner_output" "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`auth\`.* TO \`migration_user\`@\`localhost\` WITH GRANT OPTION"
[[ "$owner_output" != *"secret"* ]] || {
  echo 'Migration password must not appear in preflight output.' >&2
  exit 1
}
owner_marker="$(cat "$MAVEN_MARKER")"
assert_contains "$owner_marker" "args=flyway:validate"
assert_contains "$owner_marker" "-Dflyway.configFiles=flyway-auth.conf"
[[ "$(wc -l < "$MAVEN_MARKER")" == "1" ]] || {
  echo 'Owner validation must invoke exactly one Flyway goal.' >&2
  exit 1
}
assert_contains "$owner_marker" "DB_HOST=migration-host DB_PORT=3306 DB_NAME=auth DB_USER=migration_user"

rm -f "$MAVEN_MARKER"
container_output="$(env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=localhost \
  MIGRATION_DB_PORT=23306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  MIGRATION_RUN_ID=container-run \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate 2>&1)"
assert_contains "$container_output" "Migration preflight passed: run_id=container-run schema=auth database=auth account=migration_user"
[[ "$container_output" != *"secret"* ]] || {
  echo 'Container migration password must not appear in preflight output.' >&2
  exit 1
}
container_marker="$(cat "$MAVEN_MARKER")"
assert_contains "$container_marker" "args=flyway:validate"
assert_contains "$container_marker" "DB_HOST=localhost DB_PORT=23306 DB_NAME=auth DB_USER=migration_user"

rm -f "$MAVEN_MARKER"

container_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=external-host \
  MIGRATION_DB_PORT=23306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$container_conflict_output" "configured migration target external-host:23306 is not a published endpoint"

container_bind_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_DOCKER_BIND_ADDRESS=192.168.1.5 \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=127.0.0.1 \
  MIGRATION_DB_PORT=23306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate)"
assert_contains "$container_bind_conflict_output" "configured migration target 127.0.0.1:23306 is not a published endpoint"

backfill_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_DB_HOST=external-host \
  MIGRATION_DB_PORT=23306 \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  "$ROOT_DIR/scripts/runbooks/owner-user-profile-backfill.sh" preflight)"
assert_contains "$backfill_conflict_output" "Configured migration target external-host:23306 is not a published endpoint"

cutover_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/external.env" \
  MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" preflight)"
assert_contains "$cutover_conflict_output" "Configured database target external-host:23306 is not a published endpoint"

rehearsal_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/external.env" \
  DEV_LOCAL_OBSERVATION_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OBSERVATION_REHEARSAL \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  REDIS_CONTAINER=ulticode-redis \
  "$ROOT_DIR/scripts/runbooks/dev-local-observation-rehearsal.sh" --skip-tests)"
assert_contains "$rehearsal_conflict_output" "Configured monitoring target external-host:23306 is not a published endpoint"

monitoring_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/external.env" \
  MONITORING_DB_HOST=external-host \
  MONITORING_DB_PORT=23306 \
  MONITORING_DB_USER=migration_user \
  MONITORING_DB_PASSWORD=secret \
  MIGRATION_MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  REDIS_CONTAINER=ulticode-redis \
  REDIS_PASSWORD=secret \
  "$ROOT_DIR/scripts/runbooks/dev-local-monitoring-baseline.sh" baseline)"
assert_contains "$monitoring_conflict_output" "Configured monitoring target external-host:23306 is not a published endpoint"

notification_conflict_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/external.env" \
  MYSQL_CONTAINER=ulticode-mysql \
  MIGRATION_MYSQL_CONTAINER_PORT=3306 \
  "$ROOT_DIR/scripts/runbooks/notification-schema-cutover.sh" preflight)"
assert_contains "$notification_conflict_output" "Configured database target external-host:23306 is not a published endpoint"

contract_gate_output="$(run_expect_failure env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  FAKE_MYSQL_MODE=auth-contract-gate \
  MIGRATION_SCHEMA=auth \
  MIGRATION_DB_HOST=migration-host \
  MIGRATION_DB_PORT=3306 \
  MIGRATION_DB_NAME=auth \
  MIGRATION_DB_USER=migration_user \
  MIGRATION_DB_PASSWORD=secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" migrate)"
assert_contains "$contract_gate_output" "contract-preflight requires DEV_LOCAL_OWNER_PROFILE_CONTRACT_CONFIRM=I_UNDERSTAND_AUTH_PROFILE_CONTRACT"
[[ ! -f "$MAVEN_MARKER" ]] || {
  echo 'Auth contract migration must not run before the parity preflight.' >&2
  exit 1
}

rm -f "$MAVEN_MARKER"
env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$ENV_FILE" \
  MIGRATION_DB_USER=shared_migration \
  MIGRATION_DB_PASSWORD=shared-secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate >/dev/null
assert_contains "$(cat "$MAVEN_MARKER")" "DB_HOST=runtime-host DB_PORT=3306 DB_NAME=ulticode DB_USER=shared_migration"

rm -f "$MAVEN_MARKER"
env \
  PATH="$FAKE_BIN:$PATH" \
  ENV_FILE="$TMP_DIR/override.env" \
  MIGRATION_DB_NAME=override_db \
  MIGRATION_DB_USER=shared_migration \
  MIGRATION_DB_PASSWORD=shared-secret \
  "$ROOT_DIR/scripts/dev/migrate.sh" validate >/dev/null
assert_contains "$(cat "$MAVEN_MARKER")" "DB_HOST=runtime-host DB_PORT=3306 DB_NAME=override_db DB_USER=shared_migration"

shared_migration_line="$(awk '/MIGRATION_SCHEMA= / && $0 !~ /submission/ {print NR; exit}' "$ROOT_DIR/scripts/dev/up.sh")"
owner_loop_line="$(awk -v start="$shared_migration_line" 'NR > start && /for owner in "\${DEVSTACK_OWNER_MIGRATION_ORDER\[@\]}"; do/ {print NR; exit}' "$ROOT_DIR/scripts/dev/up.sh")"
owner_migration_line="$(awk -v start="$owner_loop_line" 'NR > start && /MIGRATION_SCHEMA="\$owner"/ {print NR; exit}' "$ROOT_DIR/scripts/dev/up.sh")"
[[ "$shared_migration_line" =~ ^[0-9]+$ && "$owner_loop_line" =~ ^[0-9]+$ \
  && "$shared_migration_line" -lt "$owner_loop_line" ]] || {
  echo 'Shared schema bootstrap must precede the Owner migration manifest.' >&2
  exit 1
}
[[ "$owner_migration_line" =~ ^[0-9]+$ ]] || {
  echo 'Owner migration manifest must invoke migrate.sh with MIGRATION_SCHEMA.' >&2
  exit 1
}
owner_migration_block="$(awk -v start="$owner_loop_line" 'NR >= start && NR <= start + 28 {print}' "$ROOT_DIR/scripts/dev/up.sh")"
case "$owner_migration_block" in
  *'owner_migration_user="$SUBMISSION_MIGRATION_DB_USER"'*) ;;
  *) echo 'Submission owner Flyway must use SUBMISSION_MIGRATION_DB_USER.' >&2; exit 1 ;;
esac
case "$owner_migration_block" in
  *'owner_migration_password="$SUBMISSION_MIGRATION_DB_PASSWORD"'*) ;;
  *) echo 'Submission owner Flyway must use SUBMISSION_MIGRATION_DB_PASSWORD.' >&2; exit 1 ;;
esac
submission_bootstrap_line="$(awk -v start="$owner_loop_line" 'NR >= start && /provision_submission_migration_principal$/ {print NR; exit}' "$ROOT_DIR/scripts/dev/up.sh")"
[[ "$submission_bootstrap_line" =~ ^[0-9]+$ && "$submission_bootstrap_line" -lt "$owner_migration_line" ]] || {
  echo 'Submission migration principal must be bootstrapped before Owner Flyway.' >&2
  exit 1
}

owner_probe_block="$(sed -n '/^verify_owner_accounts() {/,/^}/p' "$ROOT_DIR/scripts/dev/up.sh")"
case "$owner_probe_block" in
  *'MIGRATION_MYSQL_CONTAINER_PORT:-3306'*) ;;
  *) echo 'Owner readiness probe must use MIGRATION_MYSQL_CONTAINER_PORT.' >&2; exit 1 ;;
esac
case "$owner_probe_block" in
  *'-P 3306'*) echo 'Owner readiness probe must not hard-code port 3306.' >&2; exit 1 ;;
esac

echo 'migrate-owner-preflight-test: PASS'
