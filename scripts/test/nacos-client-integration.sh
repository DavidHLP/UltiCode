#!/usr/bin/env bash
# Required disposable authenticated-registry compatibility gate. No .env,
# existing containers/accounts, privileged networking or remote daemon is used.
set +x
set -euo pipefail
umask 077
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for command in docker python3 openssl java timeout; do
  command -v "$command" >/dev/null || { echo "Required integration tool unavailable: $command" >&2; exit 1; }
done
if [[ -n "${DOCKER_HOST:-}" && "${DOCKER_HOST}" != unix://* ]]; then
  echo 'Nacos integration requires a local Unix-socket Docker daemon' >&2
  exit 1
fi
endpoint="$(docker context inspect --format '{{(index .Endpoints "docker").Host}}')"
[[ "$endpoint" == unix://* ]] || { echo 'Remote Docker contexts are not permitted' >&2; exit 1; }
docker compose version >/dev/null

tmp="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-nacos-client.XXXXXX")"
readonly tmp
project="ulticode-nacos-client-$(openssl rand -hex 8)"
readonly project
compose=(docker compose --project-directory "$ROOT_DIR" --project-name "$project"
  --env-file "$tmp/fixture.env" -f "$ROOT_DIR/scripts/test/fixtures/nacos-client.compose.yml")
client_pid=''
phase='prepare'
cleanup() {
  local rc=$?
  trap - EXIT INT TERM
  if [[ -n "$client_pid" ]] && kill -0 "$client_pid" 2>/dev/null; then
    kill -TERM -- "-$client_pid" 2>/dev/null || kill -TERM "$client_pid" 2>/dev/null || true
    for _ in {1..10}; do
      kill -0 "$client_pid" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$client_pid" 2>/dev/null; then kill -KILL -- "-$client_pid" 2>/dev/null || kill -KILL "$client_pid" 2>/dev/null || true; fi
    wait "$client_pid" 2>/dev/null || true
  fi
  if [[ -f "$tmp/fixture.env" ]] && ! "${compose[@]}" down --volumes --remove-orphans >"$tmp/cleanup.log" 2>&1; then
    printf 'Failed to remove disposable Nacos project %s\n' "$project" >&2
    rc=1
  fi
  if (( rc != 0 )); then
    # Never emit raw client/server/build logs: those can contain auth tokens.
    printf 'Nacos integration failed during %s (exit %s).\n' "$phase" "$rc" >&2
  fi
  # Only the typed numeric/static evidence is public, including on success.
  for report in evidence failure; do
    if [[ -f "$tmp/control/$report" ]]; then
      python3 - "$tmp/control/$report" <<'PY_DIAGNOSTIC'
from pathlib import Path
import re
import sys
for line in Path(sys.argv[1]).read_text().splitlines():
    if re.fullmatch(r'(?:phase=[a-z-]+|exception=[A-Za-z0-9_.$]+|line=[0-9]+|(?:nacos-error-code|workload-batch-result-code|workload-batch-error-code)=-?[0-9]+|config-retained-listener-pools=[0-9]+|config-declared-resources-closed=true|callback-server-readback=(?:not-run|matches|differs|request-failed)|classification=(?:client-disconnected|authorization-rejected|server-not-ready|resource-not-found|nacos-request-failed))', line):
        print('Client diagnostic: ' + line, file=sys.stderr)
PY_DIAGNOSTIC
    fi
  done
  if ! rm -rf -- "$tmp"; then rc=1; fi
  if (( rc == 0 )); then
    echo 'PASS: isolated Nacos client compatibility, naming/discovery, dedicated-namespace config callbacks, auth/scope rejection, reconnect/resubscribe, declared-resource closure and single patched API provenance; original workload batch-listen remains denied (403), unresolved; upstream listener-pool retention remains unresolved'
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
NACOS_AUTH_TOKEN="$(openssl rand -base64 48 | tr -d '\n')"
NACOS_AUTH_IDENTITY_KEY="fixture-$(openssl rand -hex 16)"
NACOS_AUTH_IDENTITY_VALUE="$(openssl rand -hex 32)"
NACOS_USERNAME="fixture-admin-$(openssl rand -hex 8)"
NACOS_PASSWORD="$(openssl rand -hex 32)"
NACOS_FIXTURE_WRONG_PASSWORD="$(openssl rand -hex 32)"
export MYSQL_ROOT_PASSWORD NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
export NACOS_USERNAME NACOS_PASSWORD NACOS_FIXTURE_WRONG_PASSWORD
export DUBBO_NAMESPACE='protobuf-fixture'
export NACOS_NAMESPACE="$DUBBO_NAMESPACE"
export NACOS_FIXTURE_NAMESPACE="$DUBBO_NAMESPACE"
export NACOS_FIXTURE_CONFIG_NAMESPACE='protobuf-config-fixture'
NACOS_FIXTURE_CONFIG_USERNAME="fixture-config-$(openssl rand -hex 8)"
NACOS_FIXTURE_CONFIG_PASSWORD="$(openssl rand -hex 32)"
export NACOS_FIXTURE_CONFIG_USERNAME NACOS_FIXTURE_CONFIG_PASSWORD
export NACOS_EXPECTED_DOCKER_PROJECT="$project"
export NACOS_FIXTURE_SCHEMA="$ROOT_DIR/docker/initdb/01-nacos-init.sql"
for prefix in AUTH ADMIN APP SUBMISSION NOTIFICATION JUDGE; do
  user_suffix="$(openssl rand -hex 6)"
  user_password="$(openssl rand -hex 32)"
  printf -v "${prefix}_NACOS_USERNAME" 'fixture-%s-%s' "${prefix,,}" "$user_suffix"
  printf -v "${prefix}_NACOS_PASSWORD" '%s' "$user_password"
  export "${prefix}_NACOS_USERNAME" "${prefix}_NACOS_PASSWORD"
done
NACOS_FIXTURE_HTTP_PORT="$(python3 - <<'PY'
import secrets
import socket
for _ in range(64):
    port = 20000 + secrets.randbelow(30000)
    with socket.socket() as http, socket.socket() as grpc:
        try:
            http.bind(('127.0.0.1', port))
            grpc.bind(('127.0.0.1', port + 1000))
        except OSError:
            continue
        print(port)
        break
else:
    raise SystemExit('No free loopback Nacos port pair')
PY
)"
export NACOS_FIXTURE_HTTP_PORT
export NACOS_FIXTURE_GRPC_PORT="$((NACOS_FIXTURE_HTTP_PORT + 1000))"
export NACOS_FIXTURE_SERVER="127.0.0.1:$NACOS_FIXTURE_HTTP_PORT"
export NACOS_FIXTURE_CONTROL="$tmp/control"
mkdir "$NACOS_FIXTURE_CONTROL" "$tmp/home"
# Compose's explicit env file suppresses implicit .env discovery. All values
# are generated above or an owned fixture path, not sourced as shell code.
python3 - "$tmp/fixture.env" <<'PY'
import os
import pathlib
import sys
keys = ('MYSQL_ROOT_PASSWORD', 'NACOS_AUTH_TOKEN', 'NACOS_AUTH_IDENTITY_KEY',
        'NACOS_AUTH_IDENTITY_VALUE', 'NACOS_FIXTURE_SCHEMA',
        'NACOS_FIXTURE_HTTP_PORT', 'NACOS_FIXTURE_GRPC_PORT')
with pathlib.Path(sys.argv[1]).open('w') as output:
    for key in keys:
        value = os.environ[key]
        if '\n' in value or '\r' in value or "'" in value:
            raise SystemExit('Unsafe fixture environment value')
        output.write(f"{key}='{value}'\n")
PY

phase='compile isolated client'
(
  cd "$ROOT_DIR/services"
  ./mvnw -B -pl platform/rpc-resilience -am -DskipTests install
  ./mvnw -B -pl platform/rpc-resilience dependency:build-classpath \
    -Dmdep.includeScope=test -Dmdep.outputFile="$tmp/classpath"
) >"$tmp/build.log" 2>&1
[[ -s "$tmp/classpath" ]] || { echo 'Missing client test classpath' >&2; exit 1; }

phase='start fixture database'
"${compose[@]}" up -d --wait --wait-timeout 120 mysql >"$tmp/mysql.log" 2>&1
MYSQL_CONTAINER="$("${compose[@]}" ps -q mysql)"
export MYSQL_CONTAINER
phase='bootstrap disposable principals'
# Reuse the production bootstrap's validated Compose ownership, password
# hashing, disabled built-in user and per-service least-privilege ACLs.
bash "$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh" >"$tmp/bootstrap.log" 2>&1
# Test-only principal, with the same pinned bcrypt tool as the unchanged
# bootstrap. No password in argv/logs, and no global/admin or workload grants.
config_password_hash="$(printf '%s\n' "$NACOS_FIXTURE_CONFIG_PASSWORD" \
  | docker run --rm --network none -i \
    httpd@sha256:1b766f17b84026429b7cb243317b142921b24432336e798bc881c43f45ed9567 \
    htpasswd -niBC 12 "$NACOS_FIXTURE_CONFIG_USERNAME" 2>"$tmp/config-principal.log" \
  | sed 's/^[^:]*://' | tr -d '\r\n')"
[[ "$config_password_hash" =~ ^\$2[aby]\$12\$[./A-Za-z0-9]{53}$ ]] || {
  echo 'Invalid fixture principal password hash' >&2
  exit 1
}
{
  printf '%s\n' "$MYSQL_ROOT_PASSWORD"
  # Server 2.3.2 batch auth requires namespace-wide config read. This new
  # namespace contains synthetic fixture data only; this is NOT a production
  # permission fix. The exact write identifiers contain no regex metacharacters.
  cat <<SQL
START TRANSACTION;
INSERT INTO tenant_info (kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES
  ('1','protobuf-fixture','protobuf-fixture','Disposable workload compatibility fixture','test',0,0),
  ('1','$NACOS_FIXTURE_CONFIG_NAMESPACE','$NACOS_FIXTURE_CONFIG_NAMESPACE','Disposable config protocol fixture','test',0,0);
INSERT INTO users (username,password,enabled) VALUES ('$NACOS_FIXTURE_CONFIG_USERNAME','$config_password_hash',TRUE);
INSERT INTO roles (username,role) VALUES ('$NACOS_FIXTURE_CONFIG_USERNAME','ROLE_PROTOBUF_CONFIG_FIXTURE');
INSERT INTO permissions (role,resource,action) VALUES
  ('ROLE_PROTOBUF_CONFIG_FIXTURE','$NACOS_FIXTURE_CONFIG_NAMESPACE:*:config/*','r'),
  ('ROLE_PROTOBUF_CONFIG_FIXTURE','$NACOS_FIXTURE_CONFIG_NAMESPACE:FIXTURE_CONFIG:config/protobuf-fixture-metadata','w');
COMMIT;
SQL
} | docker exec -i "$MYSQL_CONTAINER" sh -c \
  'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot nacos_config' >"$tmp/namespace.log" 2>&1
phase='start authenticated registry'
"${compose[@]}" up -d --wait --wait-timeout 120 nacos >"$tmp/nacos.log" 2>&1

phase='authenticated client and negative auth checks'
classpath="$ROOT_DIR/services/platform/rpc-resilience/target/test-classes:$ROOT_DIR/services/platform/rpc-resilience/target/classes:$(cat "$tmp/classpath")"
# HOME and Java's user.home keep SDK logs/snapshots away from developer state.
# The timeout is a failing outer bound, never a success/skip fallback.
HOME="$tmp/home" timeout --signal=TERM --kill-after=10s 360s java \
  -Duser.home="$tmp/home" \
  -Dcom.google.protobuf.error_on_unsafe_pre22_gencode=true \
  -cp "$classpath" com.ulticode.rpc.resilience.NacosRegistryIT >"$tmp/client.log" 2>&1 &
client_pid=$!
wait_for_marker() {
  local marker="$1"
  for _ in {1..120}; do
    [[ -f "$NACOS_FIXTURE_CONTROL/$marker" ]] && return 0
    if ! kill -0 "$client_pid" 2>/dev/null; then
      wait "$client_pid"
      echo 'Client exited before completing the registry drill' >&2
      return 1
    fi
    sleep 1
  done
  echo "Client did not reach required fixture phase: $marker" >&2
  return 1
}
wait_for_marker ready-for-outage
phase='disconnect detection'
"${compose[@]}" stop --timeout 10 nacos >"$tmp/stop.log" 2>&1
touch "$NACOS_FIXTURE_CONTROL/server-stopped"
wait_for_marker observed-disconnect
phase='reconnect and resubscribe'
"${compose[@]}" up -d --wait --wait-timeout 120 nacos >"$tmp/restart.log" 2>&1
touch "$NACOS_FIXTURE_CONTROL/server-restarted"
wait "$client_pid"
client_pid=''
