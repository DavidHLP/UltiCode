#!/usr/bin/env bash
set -euo pipefail

# Contract for scripts/runbooks/assert-legacy-objects-migrated.sh: the deploy
# gate must fail closed while any legacy row still needs its object upload,
# and a broken probe must never read as "nothing left to migrate". Runs with a
# canned mysql client; no database or Docker is touched.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GATE="$ROOT_DIR/scripts/runbooks/assert-legacy-objects-migrated.sh"
FAILURE_PREFIX='legacy-object-gate-contract: FAIL'
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

fail() { echo "$FAILURE_PREFIX $*" >&2; exit 1; }

[[ -x "$GATE" ]] || fail "$GATE is missing or not executable"

mkdir -p "$WORK_DIR/bin"
cat > "$WORK_DIR/bin/mysql" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
sql=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -e) sql="$2"; shift 2 ;;
    *) shift ;;
  esac
done
if [[ -n "${FAKE_SQL_LOG:-}" ]]; then printf '%s\n' "$sql" >>"$FAKE_SQL_LOG"; fi
if [[ "$sql" == *information_schema.tables* ]]; then
  table=""
  case "$sql" in
    *table_name=\'user_profiles\'*) table=user_profiles ;;
    *table_name=\'backups\'*) table=backups ;;
    *table_name=\'storage_migration_state\'*) table=storage_migration_state ;;
  esac
  [[ -n "$table" ]] || { echo 'unexpected table probe' >&2; exit 3; }
  if [[ ",${FAKE_MISSING_TABLES:-}," == *",$table,"* ]]; then echo 0; else echo 1; fi
  exit 0
fi
if [[ "$sql" == *storage_migration_state* ]]; then echo "${FAKE_UNCONFIRMED_INDEX:-0}"; exit 0; fi
if [[ "$sql" == *user_profiles* ]]; then echo "${FAKE_AVATARS:-0}"; exit 0; fi
if [[ "$sql" == *backups* ]]; then echo "${FAKE_BACKUPS:-0}"; exit 0; fi
echo 'unexpected query' >&2
exit 3
SHIM
chmod +x "$WORK_DIR/bin/mysql"
: > "$WORK_DIR/empty.env"

run_gate() {
  GATE_STATUS=0
  GATE_STDOUT="$WORK_DIR/stdout"
  GATE_STDERR="$WORK_DIR/stderr"
  MIGRATION_DB_HOST=127.0.0.1 \
    MIGRATION_DB_PORT=3306 \
    MIGRATION_DB_NAME=ulticode \
    MIGRATION_DB_USER=migrator \
    MIGRATION_DB_PASSWORD=contract-secret \
    MIGRATION_MYSQL_BIN="$WORK_DIR/bin/mysql" \
    ENV_FILE="$WORK_DIR/empty.env" \
    "$GATE" "$@" >"$GATE_STDOUT" 2>"$GATE_STDERR" || GATE_STATUS=$?
}

expect_failure() {
  local expected="$1"
  [[ "$GATE_STATUS" == 1 ]] || fail "expected exit 1 (got $GATE_STATUS): $(cat "$GATE_STDERR")"
  grep -Fq -- "$expected" "$GATE_STDERR" \
    || fail "stderr is missing '$expected': $(cat "$GATE_STDERR")"
}

FAKE_AVATARS=3 FAKE_BACKUPS=0 run_gate
expect_failure '3 legacy avatar row(s)'
grep -Fq 'migrate-object-storage.sh' "$GATE_STDERR" \
  || fail 'the failure must name the migration tool an operator has to run'

FAKE_AVATARS=0 FAKE_BACKUPS=2 run_gate
expect_failure '2 legacy backup row(s)'

FAKE_AVATARS=garbage FAKE_BACKUPS=0 run_gate
expect_failure 'invalid app.user_profiles row probe'

FAKE_AVATARS=0 FAKE_BACKUPS=garbage run_gate
expect_failure 'invalid admin.backups row probe'

FAKE_AVATARS=0 FAKE_BACKUPS=0 FAKE_UNCONFIRMED_INDEX=1 run_gate
expect_failure '1 unconfirmed avatar rewrite(s)'

FAKE_AVATARS=0 FAKE_BACKUPS=0 FAKE_UNCONFIRMED_INDEX=garbage run_gate
expect_failure 'invalid app.storage_migration_state probe'

FAKE_AVATARS=0 FAKE_BACKUPS=0 run_gate
[[ "$GATE_STATUS" == 0 ]] || fail "a backfilled database must pass (got $GATE_STATUS)"
grep -Fq 'LEGACY_OBJECT_MIGRATION status=PASS legacy_avatars=0 legacy_backups=0 unconfirmed_index_backfills=0' "$GATE_STDOUT" \
  || fail "PASS line is missing: $(cat "$GATE_STDOUT")"

FAKE_MISSING_TABLES=user_profiles,backups,storage_migration_state FAKE_AVATARS=9 FAKE_BACKUPS=9 run_gate
[[ "$GATE_STATUS" == 0 ]] || fail "an absent table has nothing to migrate (got $GATE_STATUS)"

# A custom prefix may contain `_`, which LIKE treats as a single-character
# wildcard: /media_v1 must never match /mediaXv1/avatars/a.png.
: > "$WORK_DIR/avatar-sql.log"
FAKE_AVATARS=0 FAKE_BACKUPS=0 LEGACY_AVATAR_URL_PREFIX=/media_v1 \
  FAKE_SQL_LOG="$WORK_DIR/avatar-sql.log" run_gate
[[ "$GATE_STATUS" == 0 ]] || fail "an escaped predicate must still pass a clean database (got $GATE_STATUS)"
grep -Fq "avatar LIKE '/media!_v1/avatars/%' ESCAPE '!'" "$WORK_DIR/avatar-sql.log" \
  || fail "the avatar probe must escape '_' and declare ESCAPE: $(cat "$WORK_DIR/avatar-sql.log")"

# A scoped start gates only the owners it selects: a local App-only scope has no
# Admin, so a legacy completed backup must not block it, and the owner that is
# not selected must not be probed at all.
: > "$WORK_DIR/scoped-sql.log"
FAKE_AVATARS=0 FAKE_BACKUPS=2 FAKE_SQL_LOG="$WORK_DIR/scoped-sql.log" run_gate --owners app
[[ "$GATE_STATUS" == 0 ]] || fail "an App-only scope must not be blocked by Admin backups (got $GATE_STATUS): $(cat "$GATE_STDERR")"
grep -Fq 'LEGACY_OBJECT_MIGRATION status=PASS legacy_avatars=0 legacy_backups=skipped' "$GATE_STDOUT" \
  || fail "an App-only PASS line must report the skipped owner: $(cat "$GATE_STDOUT")"
! grep -Fq 'backups' "$WORK_DIR/scoped-sql.log" \
  || fail "an App-only gate must not probe admin.backups: $(cat "$WORK_DIR/scoped-sql.log")"

FAKE_AVATARS=3 FAKE_BACKUPS=0 FAKE_SQL_LOG="$WORK_DIR/scoped-sql.log" run_gate --owners app
expect_failure '3 legacy avatar row(s)'

: > "$WORK_DIR/scoped-sql.log"
FAKE_AVATARS=3 FAKE_BACKUPS=0 FAKE_SQL_LOG="$WORK_DIR/scoped-sql.log" run_gate --owners admin
[[ "$GATE_STATUS" == 0 ]] || fail "an Admin-only scope must not be blocked by avatars (got $GATE_STATUS): $(cat "$GATE_STDERR")"
! grep -Fq 'user_profiles' "$WORK_DIR/scoped-sql.log" \
  || fail "an Admin-only gate must not probe app.user_profiles: $(cat "$WORK_DIR/scoped-sql.log")"

FAKE_AVATARS=0 FAKE_BACKUPS=2 run_gate --owners admin
expect_failure '2 legacy backup row(s)'

# The owner selection is an argument, and an unusable one fails closed rather
# than falling back to the full deploy gate or to no gate at all.
FAKE_AVATARS=0 FAKE_BACKUPS=0 run_gate --owners app,search
expect_failure '--owners must be app, admin or both'
FAKE_AVATARS=0 FAKE_BACKUPS=0 run_gate --owners
expect_failure '--owners requires a value'
FAKE_AVATARS=0 FAKE_BACKUPS=0 run_gate --owner app
expect_failure 'unknown argument --owner'

echo 'legacy-object-gate-contract: PASS'
