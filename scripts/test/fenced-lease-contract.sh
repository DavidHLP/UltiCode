#!/usr/bin/env bash
set -euo pipefail

# P3-LEASE-001 contract: prove the shared lease protocol is wired to the
# singleton callers and that the real MySQL runner tests are available.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "fenced-lease-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file is missing: $text"
}

not_contains() {
  local file="$1" text="$2"
  ! grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file must not contain: $text"
}

for source in \
  services/platform/common/src/main/java/com/ulticode/common/lease/FencedLease.java \
  services/admin/src/main/java/com/ulticode/modules/lease/FencedJobLease.java \
  services/admin/src/main/java/com/ulticode/modules/lease/FencedJobLeaseMapper.java \
  services/admin/src/main/java/com/ulticode/modules/lease/FencedJobLeaseService.java \
  init-db/migrations/V20260831110000__Create_Fenced_Job_Leases.sql \
  init-db/migrations/admin/V20260831110001__Add_Fence_Token_To_Reconciliation_Runs.sql \
  scripts/runbooks/lib/fenced-lease.sh; do
  [[ -f "$ROOT_DIR/$source" ]] || fail "missing lease source: $source"
done

contains services/platform/common/src/main/java/com/ulticode/common/lease/FencedLease.java 'isExpiredAt'
contains services/platform/common/src/main/java/com/ulticode/common/lease/FencedLease.java 'maxClockSkew'
contains services/admin/src/main/java/com/ulticode/modules/lease/FencedJobLeaseMapper.java 'TIMESTAMPADD(MICROSECOND'
contains services/admin/src/main/java/com/ulticode/modules/lease/FencedJobLeaseMapper.java 'fence_token = #{fenceToken}'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/ReconciliationRunMapper.java 'leased_until > CURRENT_TIMESTAMP(3)'
not_contains services/admin/src/main/java/com/ulticode/modules/reconciliation/ReconciliationRunMapper.java 'GET_LOCK'
not_contains services/admin/src/main/java/com/ulticode/modules/reconciliation/ReconciliationRunMapper.java 'RELEASE_LOCK'
contains services/admin/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java 'admin:scheduled-backup'
contains services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java 'admin:reconciliation'
contains services/admin/src/main/resources/application.yml 'ADMIN_FENCED_LEASE_TTL_MS:600000'
contains services/admin/src/main/java/com/ulticode/BackendAdminApplication.java 'com.ulticode.modules.lease'
contains scripts/runbooks/owner-migration-manifest.sh 'admin:owner-migration'
contains scripts/runbooks/owner-backup-restore.sh 'admin:owner-backup'
contains scripts/runbooks/owner-backup-restore.sh 'excluded_operational_tables'
contains .github/actions/host-deploy/action.yml 'migration_mysql_bin'
contains .github/actions/host-deploy/action.yml 'OWNER_MIGRATION_MYSQL_CONTAINER'
contains services/submission/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java 'current_attempt_id = #{attemptId}'
printf 'fenced lease wiring and non-singleton claim/CAS contract: PASS\n'

(
  cd "$ROOT_DIR/services"
  if command -v mise >/dev/null 2>&1; then
    mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl admin -am \
      -Dtest='FencedLeaseTest,OwnerReconcilerTest,BackupSchedulerTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  else
    bash ./mvnw -pl admin -am \
      -Dtest='FencedLeaseTest,OwnerReconcilerTest,BackupSchedulerTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  fi
) >/dev/null
printf 'fenced lease clock/lost-lease/singleton unit tests: PASS\n'

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  (
    cd "$ROOT_DIR/services"
    if command -v mise >/dev/null 2>&1; then
      mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl admin -am \
        -Dtest='FencedJobLeaseIT,OwnerReconcilerIT' \
        -Dsurefire.failIfNoSpecifiedTests=false test -B
    else
      bash ./mvnw -pl admin -am \
        -Dtest='FencedJobLeaseIT,OwnerReconcilerIT' \
        -Dsurefire.failIfNoSpecifiedTests=false test -B
    fi
  ) >/dev/null
  printf 'fenced lease two-runner/expiry MySQL integration tests: PASS\n'
else
  printf 'fenced lease two-runner/expiry MySQL integration tests: BLOCKED_EXTERNAL (Docker unavailable)\n'
fi

printf 'fenced-lease-contract: PASS\n'
