#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
FAKE_BIN="$TMP_DIR/bin"
FAKE_LOG="$TMP_DIR/mysql.log"
FAKE_STATE="$TMP_DIR/mysql.state"
ENV_FILE="$TMP_DIR/test.env"
mkdir -p "$FAKE_BIN"

cat >"$ENV_FILE" <<'EOF'
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ulticode
DB_USER=migration
DB_PASSWORD=test-password
SUBMISSION_SOURCE_SCHEMA=ulticode
SUBMISSION_DB_NAME=submission
SUBMISSION_APP_DB_USER=app_rw
SUBMISSION_APP_DB_HOST=%
EOF

cat >"$FAKE_BIN/mysql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
query=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -e) query="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done
printf '%s\n' "$query" >> "${FAKE_LOG:?}"

if [[ "${FAKE_MODE:-success}" == "conflict" && "$query" == *"JOIN submission."* && "$query" == *"NOT ("* ]]; then
  printf '1\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"LEFT JOIN submission."* && "$query" == *"t.id IS NULL"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"CHECKSUM TABLE"* ]]; then
  printf 'table\t123\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"FROM mysql.user AS account"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"FROM mysql.user WHERE User"* ]]; then
  printf '1\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"mysql.role_edges"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"information_schema.USER_PRIVILEGES"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"information_schema.SCHEMA_PRIVILEGES"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"COUNT(DISTINCT PRIVILEGE_TYPE)"* ]]; then
  printf '4\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"information_schema.table_privileges"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "${FAKE_MODE:-success}" == "verify" && "$query" == *"information_schema.COLUMN_PRIVILEGES"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "$query" == *"SELECT COALESCE(MAX(id)"* ]]; then
  table="${query#*FROM ulticode.}"
  table="${table%% *}"
  key="$table"
  count=0
  if [[ -f "${FAKE_STATE:?}" ]]; then
    count="$(awk -F $'\t' -v key="$key" '$1 == key { print $2 }' "$FAKE_STATE" || true)"
  fi
  count="${count:-0}"
  if [[ "$count" == "0" ]]; then
    printf '%s\n' "${table}-boundary"
  else
    printf '\n'
  fi
  awk -F $'\t' -v key="$key" '$1 != key' "${FAKE_STATE}" 2>/dev/null > "${FAKE_STATE}.tmp" || true
  printf '%s\t%s\n' "$key" "$((count + 1))" >> "${FAKE_STATE}.tmp"
  mv "${FAKE_STATE}.tmp" "${FAKE_STATE}"
  exit 0
fi
if [[ "$query" == *"SELECT COLUMN_NAME FROM information_schema.columns"* ]]; then
  printf 'id\n'
  exit 0
fi
if [[ "$query" == *"information_schema.tables"* ]]; then
  printf '1\n'
  exit 0
fi
if [[ "$query" == *"GROUP_CONCAT(CONCAT_WS"* ]]; then
  printf 'signature\n'
  exit 0
fi
if [[ "$query" == *"LEFT JOIN submission."* && "$query" == *"t.id IS NULL"* ]]; then
  printf '2\n'
  exit 0
fi
if [[ "$query" == *"SELECT COUNT(*)"* ]]; then
  printf '0\n'
  exit 0
fi
if [[ "$query" == *"INSERT INTO"* ]]; then
  printf '\n'
  exit 0
fi
printf '0\n'
EOF
chmod +x "$FAKE_BIN/mysql"

assert_contains() {
  local file="$1" text="$2"
  grep -F -- "$text" "$file" >/dev/null || {
    echo "missing expected text: $text" >&2
    exit 1
  }
}

assert_not_contains() {
  local file="$1" text="$2"
  ! grep -F -- "$text" "$file" >/dev/null || {
    echo "unexpected text: $text" >&2
    exit 1
  }
}

run_backfill() {
  PATH="$FAKE_BIN:$PATH" \
    FAKE_LOG="$FAKE_LOG" \
    FAKE_STATE="$FAKE_STATE" \
    FAKE_MODE=success \
    ENV_FILE="$ENV_FILE" \
    BACKFILL_BATCH_SIZE=2 \
    BACKFILL_CHECKPOINT_FILE="$TMP_DIR/checkpoint" \
    BACKFILL_DRY_RUN_CHECKPOINT_FILE="$TMP_DIR/dry-checkpoint" \
    BACKFILL_FAILURE_FILE="$TMP_DIR/failures.tsv" \
    bash "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" backfill --dry-run
}

run_verify() {
  PATH="$FAKE_BIN:$PATH" \
    FAKE_LOG="$FAKE_LOG" \
    FAKE_STATE="$FAKE_STATE" \
    FAKE_MODE=verify \
    ENV_FILE="$ENV_FILE" \
    BACKFILL_FAILURE_FILE="$TMP_DIR/verify-failures.tsv" \
    bash "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" verify
}

: > "$FAKE_LOG"
: > "$FAKE_STATE"
run_backfill > "$TMP_DIR/dry-run.log"
assert_contains "$TMP_DIR/dry-run.log" 'DRY-RUN table=submissions'
assert_contains "$TMP_DIR/dry-run.log" 'Backfill complete: mode=dry-run'
assert_not_contains "$FAKE_LOG" '1=1s.'
assert_contains "$TMP_DIR/dry-checkpoint" $'submissions\tsubmissions-boundary'
assert_contains "$TMP_DIR/dry-checkpoint" $'judge_outbox\tjudge_outbox-boundary'
assert_contains "$TMP_DIR/dry-checkpoint" $'submission_result_outbox\tsubmission_result_outbox-boundary'
assert_not_contains "$FAKE_LOG" 'INSERT INTO'

run_backfill > "$TMP_DIR/dry-resume.log"
assert_not_contains "$TMP_DIR/dry-resume.log" 'DRY-RUN table='
printf 'dry-run checkpoint resume and no-write behavior: PASS\n'
run_verify > "$TMP_DIR/verify.log"
assert_contains "$TMP_DIR/verify.log" 'VERIFY PASS: count/checksum/field/writer differences are zero.'
assert_contains "$TMP_DIR/verify.log" 'WRITER_DIFF=0 state=PRE_CUTOVER'
printf 'count/checksum/field/writer parity gate: PASS\n'
rm -f "$FAKE_LOG" "$FAKE_STATE" "$TMP_DIR/failures.tsv" "$TMP_DIR/failure-checkpoint"
set +e
PATH="$FAKE_BIN:$PATH" \
  FAKE_LOG="$FAKE_LOG" \
  FAKE_STATE="$FAKE_STATE" \
  FAKE_MODE=conflict \
  ENV_FILE="$ENV_FILE" \
  BACKFILL_BATCH_SIZE=2 \
  BACKFILL_DRY_RUN_CHECKPOINT_FILE="$TMP_DIR/failure-checkpoint" \
  BACKFILL_FAILURE_FILE="$TMP_DIR/failures.tsv" \
  bash "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" backfill --dry-run > "$TMP_DIR/conflict.log" 2>&1
CONFLICT_STATUS=$?
set -e
[[ "$CONFLICT_STATUS" -ne 0 ]] || { echo 'conflict dry-run unexpectedly passed' >&2; exit 1; }
assert_contains "$TMP_DIR/conflict.log" 'BACKFILL CONFLICT'
assert_contains "$TMP_DIR/failures.tsv" 'field conflicts=1; newer owner rows are never overwritten'
assert_not_contains "$TMP_DIR/failures.tsv" $'submissions\tsubmissions-boundary'
assert_not_contains "$FAKE_LOG" 'INSERT INTO'
printf 'failure export and newer-owner protection: PASS\n'

set +e
PATH="$FAKE_BIN:$PATH" \
  FAKE_LOG="$FAKE_LOG" \
  FAKE_STATE="$FAKE_STATE" \
  ENV_FILE="$ENV_FILE" \
  BACKFILL_CHECKPOINT_FILE="$TMP_DIR/execute-checkpoint" \
  BACKFILL_FAILURE_FILE="$TMP_DIR/execute-failures.tsv" \
  bash "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" backfill --execute > "$TMP_DIR/execute-gate.log" 2>&1
EXECUTE_STATUS=$?
set -e
[[ "$EXECUTE_STATUS" -ne 0 ]] || { echo 'backfill execute gate unexpectedly passed' >&2; exit 1; }
assert_contains "$TMP_DIR/execute-gate.log" 'SUBMISSION_BACKFILL_CONFIRM'
printf 'backfill execute confirmation gate: PASS\n'

assert_contains "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" 'VERIFY PASS: count/checksum/field/writer differences are zero.'
assert_contains "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" 'NOT EXISTS (SELECT 1 FROM $TARGET_SCHEMA.$table t WHERE t.id = s.id)'
assert_contains "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" 'field_differences'
assert_contains "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" 'WRITER_DIFF=0'
printf 'backfill contract: PASS\n'
