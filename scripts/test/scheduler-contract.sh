#!/usr/bin/env bash
set -euo pipefail

# P3-SCHED-001 contract: prove the critical scheduler seams are explicit,
# bounded, observable, and covered by one real independent-progress test.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "scheduler-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file is missing: $text"
}

for config in \
  services/admin/src/main/java/com/ulticode/admin/config/AdminSchedulerConfiguration.java \
  services/submission/src/main/java/com/ulticode/submission/config/SubmissionSchedulerConfiguration.java \
  services/search/src/main/java/com/ulticode/search/config/SearchSchedulerConfiguration.java; do
  contains "$config" 'ThreadPoolTaskScheduler'
  contains "$config" 'setPoolSize'
  contains "$config" 'setAwaitTerminationSeconds(30)'
  contains "$config" 'ExecutorServiceMetrics.monitor'
  contains "$config" 'pool size must be between 1 and 16'
done

for source in \
  services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java \
  services/admin/src/main/java/com/ulticode/modules/admin/outbox/AuditOutboxDispatcher.java \
  services/admin/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java \
  services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java \
  services/submission/src/main/java/com/ulticode/modules/queue/outbox/dispatcher/JudgeOutboxDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/result/SubmissionResultDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/created/SubmissionCreatedDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/reaper/JudgingLeaseReaper.java \
  services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java \
  services/search/src/main/java/com/ulticode/search/SearchWorkerReadinessHeartbeat.java; do
  contains "$source" 'scheduler ='
done

for source in services/admin/src/main/java services/submission/src/main/java services/search/src/main/java; do
  if grep -R -n 'Executors\.newScheduledThreadPool' "$ROOT_DIR/$source" --include='*.java' >/dev/null 2>&1; then
    fail "$source contains an unbounded/undocumented ScheduledExecutorService"
  fi
done

contains services/admin/src/main/resources/application.yml 'ADMIN_AUDIT_SCHEDULER_POOL_SIZE:2'
contains services/submission/src/main/resources/application.yml 'SUBMISSION_LEASE_RECOVERY_SCHEDULER_POOL_SIZE:1'
contains services/search/src/main/resources/application.yml 'SEARCH_HEARTBEAT_SCHEDULER_POOL_SIZE:1'
contains services/docs/SCHEDULER_RUNBOOK.md 'P3-SCHED-001'
contains services/docs/SCHEDULER_RUNBOOK.md 'executor.queued'
printf 'scheduler source/config isolation contract: PASS\n'

(
  cd "$ROOT_DIR/services"
  if command -v mise >/dev/null 2>&1; then
    mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl admin -am \
      -Dtest=AdminSchedulerConfigurationTest \
      -Dsurefire.failIfNoSpecifiedTests=false \
      test -B
  else
    bash ./mvnw -pl admin -am \
      -Dtest=AdminSchedulerConfigurationTest \
      -Dsurefire.failIfNoSpecifiedTests=false \
      test -B
  fi
) >/dev/null
printf 'scheduler independent-progress/rejection/metrics test: PASS\n'
printf 'scheduler-contract: PASS\n'
