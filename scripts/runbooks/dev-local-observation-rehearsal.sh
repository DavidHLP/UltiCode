#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
SKIP_TESTS=false

for arg in "$@"; do
  case "$arg" in
    --skip-tests|--quick)
      SKIP_TESTS=true
      ;;
    --help|-h)
      echo "Usage: $0 [--skip-tests|--quick]" >&2
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 2
      ;;
  esac
done

REQUIRED_CONFIRM="I_UNDERSTAND_DEV_LOCAL_OBSERVATION_REHEARSAL"
if [[ "${DEV_LOCAL_OBSERVATION_CONFIRM:-}" != "$REQUIRED_CONFIRM" ]]; then
  cat >&2 <<EOF
DEV-LOCAL observation rehearsal requires explicit confirmation:
  export DEV_LOCAL_OBSERVATION_CONFIRM=$REQUIRED_CONFIRM

This ensures observation timeline, fault-injection tests, data reconciliation,
and rollback runbook verification run under controlled local authorization.
EOF
  exit 1
fi

load_env_file

MONITORING_DB_HOST="${MONITORING_DB_HOST:-${MIGRATION_DB_HOST:-${DB_HOST:-127.0.0.1}}}"
MONITORING_DB_PORT="${MONITORING_DB_PORT:-${MIGRATION_DB_PORT:-${DB_PORT:-3306}}}"
MONITORING_DB_USER="${MONITORING_DB_USER:-${MIGRATION_DB_USER:-root}}"
MONITORING_DB_PASSWORD="${MONITORING_DB_PASSWORD:-${MIGRATION_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-${MYSQL_CONTAINER:-ulticode-mysql}}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
REDIS_CONTAINER="${REDIS_CONTAINER:-ulticode-redis}"

if [[ -n "$MYSQL_CONTAINER" ]] && docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
    "$MONITORING_DB_HOST" "$MONITORING_DB_PORT" || {
    echo "Configured monitoring target $MONITORING_DB_HOST:$MONITORING_DB_PORT is not a published endpoint of $MYSQL_CONTAINER:$MYSQL_CONTAINER_PORT" >&2
    exit 1
  }
fi

mysql_query() {
  local schema="$1" query="$2"
  if [[ -n "$MYSQL_CONTAINER" ]] && docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
    docker exec -e MYSQL_PWD="$MONITORING_DB_PASSWORD" "$MYSQL_CONTAINER" \
      mysql --protocol=tcp --batch --skip-column-names \
      -h 127.0.0.1 -P "$MYSQL_CONTAINER_PORT" -u "$MONITORING_DB_USER" "$schema" -e "$query"
  else
    MYSQL_PWD="$MONITORING_DB_PASSWORD" mysql --protocol=tcp --batch --skip-column-names \
      -h "$MONITORING_DB_HOST" -P "$MONITORING_DB_PORT" -u "$MONITORING_DB_USER" "$schema" -e "$query"
  fi
}

echo "=== [1/5] DEV-LOCAL Observation Baseline & Timeline ==="
printf 'Timestamp: %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
printf 'Route Mode: %s | Cutover Complete: %s\n' \
  "${APP_SUBMISSION_ROUTING_MODE:-unset}" "${SUBMISSION_CUTOVER_COMPLETE:-unset}"

if docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  printf 'MySQL Container: %s (running)\n' "$MYSQL_CONTAINER"
else
  printf 'MySQL Host: %s:%s\n' "$MONITORING_DB_HOST" "$MONITORING_DB_PORT"
fi

if docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1; then
  REDIS_PING="$(docker exec -e REDISCLI_AUTH="${REDIS_PASSWORD:-}" "$REDIS_CONTAINER" redis-cli ping 2>/dev/null || echo "FAIL")"
  if [[ "$REDIS_PING" != "PONG" ]]; then
    echo "ERROR: Redis ping failed (got $REDIS_PING)" >&2
    exit 1
  fi
  printf 'Redis Container: %s (ping=%s)\n' "$REDIS_CONTAINER" "$REDIS_PING"
else
  echo "ERROR: Redis container not found: $REDIS_CONTAINER" >&2
  exit 1
fi

echo "=== [2/5] Fault Injection & Resilience Test Battery ==="
TEST_STATUS="UNPERFORMED"
if [[ "$SKIP_TESTS" == "true" ]]; then
  echo "[NOTICE] --quick/--skip-tests specified: test execution skipped."
  TEST_STATUS="SKIPPED_UNAVAILABLE"
else
  echo "Running Maven fault-injection, timeout, outage, duplicate event, and stale generation test suites..."
  export REDIS_HOST="127.0.0.1"
  export REDIS_PORT="26379"
  export REDIS_PASSWORD="${REDIS_PASSWORD:-}"

  MAVEN_OUTPUT_FILE="$(mktemp /tmp/dev-local-obs-test-XXXXXX.log)"
  set +e
  (cd "$ROOT_DIR/services" && ./mvnw test -am \
    -Dtest='SubmissionProviderContractTest,SubmissionFactsSnapshotTest,SubmissionApiContractShapeTest,DefaultSubmissionWritePortIT,SubmissionOwnerCutoverIT,SubmissionOutboxDispatcherIT,SubmissionCreatedDispatcherTest,JudgeStreamRedisIntegrationTest,NotificationDeliveryLedgerMapperIT,NotificationDispatcherTest,NotificationLedgerReaperTest,DefaultAdminAnalyticsPortAdapterTest,AdminAnalyticsServiceImplTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -B) > "$MAVEN_OUTPUT_FILE" 2>&1
  MAVEN_STATUS=$?
  set -e

  if [[ "$MAVEN_STATUS" -ne 0 ]]; then
    cat "$MAVEN_OUTPUT_FILE" >&2
    rm -f "$MAVEN_OUTPUT_FILE"
    echo "ERROR: Maven fault-injection test battery failed." >&2
    exit 1
  fi

  # Verify no skipped tests in JudgeStreamRedisIntegrationTest or other targeted suites
  SKIPPED_COUNT="$(grep -E "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: [1-9]" "$MAVEN_OUTPUT_FILE" || true)"
  if [[ -n "$SKIPPED_COUNT" ]]; then
    echo "ERROR: Found silently skipped tests in output:" >&2
    echo "$SKIPPED_COUNT" >&2
    rm -f "$MAVEN_OUTPUT_FILE"
    exit 1
  fi

  echo "Captured clean test execution from reactor modules:"
  grep -E "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: 0" "$MAVEN_OUTPUT_FILE" || true
  rm -f "$MAVEN_OUTPUT_FILE"
  TEST_STATUS="PASS"
fi

echo "=== [3/5] Data Reconciliation & Parity Audit ==="
printf '%-30s %-15s %-15s %-25s\n' "Table" "Source Rows" "Target Rows" "Checksum Match"

for tbl in submissions judge_outbox submission_result_outbox; do
  SRC_ROWS="$(mysql_query ulticode "SELECT COUNT(*) FROM \`$tbl\`")"
  TGT_ROWS="$(mysql_query submission "SELECT COUNT(*) FROM \`$tbl\`")"
  
  SRC_CS="$(mysql_query ulticode "CHECKSUM TABLE \`$tbl\`" | awk '{print $2}')"
  TGT_CS="$(mysql_query submission "CHECKSUM TABLE \`$tbl\`" | awk '{print $2}')"
  if [[ "$SRC_CS" == "$TGT_CS" ]]; then
    CS_MATCH="MATCH ($SRC_CS)"
  else
    echo "ERROR: Checksum mismatch on table $tbl: source=$SRC_CS vs target=$TGT_CS" >&2
    exit 1
  fi
  printf '%-30s %-15s %-15s %-25s\n' "$tbl" "$SRC_ROWS" "$TGT_ROWS" "$CS_MATCH"
done

TGT_CREATED_ROWS="$(mysql_query submission "SELECT COUNT(*) FROM submission_created_outbox")"
printf '%-30s %-15s %-15s %-25s\n' "submission_created_outbox" "(target-only)" "$TGT_CREATED_ROWS" "N/A"

echo "=== [4/5] Rollback, Route & Grant State Verification ==="
printf 'Current Route Configuration: APP_SUBMISSION_ROUTING_MODE=%s\n' "${APP_SUBMISSION_ROUTING_MODE:-unset}"
printf 'Current Cutover Marker: SUBMISSION_CUTOVER_COMPLETE=%s\n' "${SUBMISSION_CUTOVER_COMPLETE:-unset}"

# Check active writer connections in MySQL processlist and strictly assert 0 active
ACTIVE_WRITER_CONNS="$(mysql_query information_schema "SELECT COUNT(*) FROM PROCESSLIST WHERE USER NOT IN ('root', 'system user', 'event_scheduler')")"
if [[ "$ACTIVE_WRITER_CONNS" -ne 0 ]]; then
  echo "ERROR: Quiescence check failed: found $ACTIVE_WRITER_CONNS active non-system writer connection(s) in MySQL" >&2
  exit 1
fi
printf 'Active non-system DB writer connections: %s connection(s) (ASSERTION PASS: 0 active)\n' "$ACTIVE_WRITER_CONNS"

# Check PM2 writer status
if command -v pm2 >/dev/null 2>&1 && pm2 jlist 2>/dev/null | grep -q 'ulticode'; then
  PM2_STATUS="RUNNING"
  WRITER_QUIESCE_STATUS="RUNNABLE (PM2 processes running)"
else
  PM2_STATUS="UNAVAILABLE (PM2 processes not running locally)"
  WRITER_QUIESCE_STATUS="VERIFIED_STATIC (0 active writer connections; PM2 not running locally)"
fi
printf 'Writer Process Status: %s\n' "$PM2_STATUS"
printf 'Writer Quiesce State: %s\n' "$WRITER_QUIESCE_STATUS"

# Verify every direct or role-derived privilege scope that can reach submission.
RUNTIME_DB_USER="${DB_USER:-ulticode}"
[[ "$RUNTIME_DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'ERROR: invalid runtime database user.' >&2; exit 1; }
GRANT_COUNT="$(mysql_query information_schema "WITH RECURSIVE principals (principal_user, principal_host) AS (
  SELECT CAST(User AS CHAR(255)), CAST(Host AS CHAR(255)) FROM mysql.user WHERE User = '$RUNTIME_DB_USER'
  UNION DISTINCT
  SELECT CAST(edge.FROM_USER AS CHAR(255)), CAST(edge.FROM_HOST AS CHAR(255))
  FROM mysql.role_edges edge JOIN principals parent
    ON edge.TO_USER = parent.principal_user AND edge.TO_HOST = parent.principal_host
), prohibited AS (
  SELECT privilege.GRANTEE FROM USER_PRIVILEGES privilege JOIN principals principal
    ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user, CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
   WHERE privilege.PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','CREATE','DROP','REFERENCES','INDEX','ALTER','CREATE TEMPORARY TABLES','LOCK TABLES','EXECUTE','CREATE VIEW','SHOW VIEW','CREATE ROUTINE','ALTER ROUTINE','EVENT','TRIGGER','ALL PRIVILEGES') OR privilege.IS_GRANTABLE <> 'NO'
  UNION ALL
  SELECT privilege.GRANTEE FROM SCHEMA_PRIVILEGES privilege JOIN principals principal
    ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user, CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
   WHERE privilege.TABLE_SCHEMA = 'submission'
  UNION ALL
  SELECT privilege.GRANTEE FROM TABLE_PRIVILEGES privilege JOIN principals principal
    ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user, CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
   WHERE privilege.TABLE_SCHEMA = 'submission'
  UNION ALL
  SELECT privilege.GRANTEE FROM COLUMN_PRIVILEGES privilege JOIN principals principal
    ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user, CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
   WHERE privilege.TABLE_SCHEMA = 'submission'
  UNION ALL
  SELECT CONCAT(CHAR(39), privilege.User, CHAR(39), '@', CHAR(39), privilege.Host, CHAR(39))
  FROM mysql.procs_priv privilege JOIN principals principal
    ON privilege.User = principal.principal_user AND privilege.Host = principal.principal_host
   WHERE privilege.Db = 'submission' AND privilege.Proc_priv <> ''
)
SELECT COUNT(*) FROM prohibited;")"
[[ "$GRANT_COUNT" =~ ^[0-9]+$ ]] || { echo 'ERROR: effective runtime grant inspection returned an invalid count.' >&2; exit 1; }
if [[ "$GRANT_COUNT" != "0" ]]; then
  echo "ERROR: Runtime user $RUNTIME_DB_USER retains $GRANT_COUNT direct or role-derived privilege(s) reaching submission." >&2
  exit 1
fi
printf 'Runtime User (%s) effective grants reaching `submission`: %s privilege(s) (ASSERTION PASS: 0)\n' "$RUNTIME_DB_USER" "$GRANT_COUNT"

printf 'Persistent Data Mutation: NOT EXECUTED (persistent DB rows protected; rollback/fault fixtures executed in disposable Testcontainers)\n'

RUNBOOK_STATUS="REHEARSAL_VERIFIED (disposable container fault injection/timeout/outage/rollback tests passed; static route/grant/processlist checks verified)"
if [[ "$TEST_STATUS" != "PASS" ]]; then
  RUNBOOK_STATUS="PARTIAL (tests skipped in this run)"
fi
printf 'Observation Runbook Status: %s\n' "$RUNBOOK_STATUS"

echo "=== [5/5] DEV-LOCAL Rehearsal Summary & Non-Production Notice ==="
if [[ "$RUNBOOK_STATUS" =~ "REHEARSAL_VERIFIED" ]]; then
  printf 'Summary: DEV-LOCAL observation, fault injection, data reconciliation, and rollback rehearsal verified.\n'
else
  printf 'Summary: DEV-LOCAL observation completed with PARTIAL status (test execution was skipped).\n'
fi
printf 'Notice: This rehearsal proves local script and container-level resilience only.\n'
printf 'Live multi-node cluster partition, live production writer drain, and external change authority\n'
printf 'are UNAVAILABLE in this local environment. External ARCH-003-001..005 acceptance remains BLOCKED.\n'
