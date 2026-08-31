#!/usr/bin/env bash
set -euo pipefail

# P3-GRACE-001 contract: prove signal/lifecycle configuration and that every
# durable worker refuses new claims while its current bounded cycle drains.

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "graceful-drain-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file is missing: $text"
}

for config in \
  services/auth/src/main/resources/application.yml \
  services/admin/src/main/resources/application.yml \
  services/app/app-web/src/main/resources/application.yml \
  services/submission/src/main/resources/application.yml \
  services/notification/src/main/resources/application.yml \
  services/search/src/main/resources/application.yml \
  services/judge/src/main/resources/application.yml; do
  contains "$config" 'shutdown: graceful'
  contains "$config" 'timeout-per-shutdown-phase:'
done

for config in \
  services/auth/src/main/resources/application.yml \
  services/app/app-web/src/main/resources/application.yml \
  services/notification/src/main/resources/application.yml \
  services/judge/src/main/resources/application.yml; do
  contains "$config" 'await-termination: true'
  contains "$config" 'await-termination-period:'
done

for worker in \
  services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/InboxConsumer.java \
  services/admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java \
  services/admin/src/main/java/com/ulticode/modules/admin/outbox/AuditOutboxDispatcher.java \
  services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java \
  services/admin/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java \
  services/auth/src/main/java/com/ulticode/auth/audit/AuthAuditOutboxDispatcher.java \
  services/auth/src/main/java/com/ulticode/auth/search/SearchDocumentChangedOutboxDispatcher.java \
  services/app/app-web/src/main/java/com/ulticode/app/audit/AppAuditOutboxDispatcher.java \
  services/app/app-web/src/main/java/com/ulticode/modules/event/outbox/IntegrationOutboxDispatcher.java \
  services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java \
  services/submission/src/main/java/com/ulticode/modules/queue/outbox/dispatcher/JudgeOutboxDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/result/SubmissionResultDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/created/SubmissionCreatedDispatcher.java \
  services/submission/src/main/java/com/ulticode/modules/submission/reaper/JudgingLeaseReaper.java \
  services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java \
  services/notification/src/main/java/com/ulticode/modules/notification/ledger/reaper/NotificationLedgerReaper.java \
  services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java \
  services/search/src/main/java/com/ulticode/search/SearchWorkerReadinessHeartbeat.java \
  services/judge-runtime/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java \
  services/judge-runtime/src/main/java/com/ulticode/modules/queue/outbox/reaper/UnackedStreamEntriesReaper.java \
  services/judge-runtime/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerReadinessHeartbeat.java; do
  contains "$worker" 'DrainGate'
  contains "$worker" 'tryEnter'
  contains "$worker" 'beginDrain'
done

contains services/app/app-web/src/main/java/com/ulticode/app/judge/AppJudgeCompatibilityAdapter.java 'DrainGate'
contains services/app/app-web/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java 'ContextClosedEvent'
contains services/Dockerfile 'STOPSIGNAL SIGTERM'
contains services/platform/integration-inbox/pom.xml 'jdk.attach.allowAttachSelf=true'
contains services/docs/GRACEFUL_DRAIN_RUNBOOK.md 'P3-GRACE-001'

[[ "$(grep -Fc 'stop_grace_period: ${SERVICE_STOP_GRACE_PERIOD:-60s}' \
  "$ROOT_DIR/docker-compose.prod.yml")" == 7 ]] \
  || fail 'production Java service stop grace is not configured for all seven services'
[[ "$(grep -Fc 'stop_grace_period: ${FRONTEND_STOP_GRACE_PERIOD:-30s}' \
  "$ROOT_DIR/docker-compose.prod.yml")" == 2 ]] \
  || fail 'production frontend stop grace is not configured for both gateways'
[[ "$(grep -Fc 'kill_timeout:' "$ROOT_DIR/ecosystem.config.cjs")" == 9 ]] \
  || fail 'PM2 kill_timeout is not configured for all nine local processes'
printf 'graceful shutdown/lifecycle/worker wiring contract: PASS\n'

(
  cd "$ROOT_DIR/services"
  if command -v mise >/dev/null 2>&1; then
    mise exec java@zulu-17.68.203.0 -- bash ./mvnw \
      -pl platform/common,platform/integration-inbox,auth,admin,app/app-web,notification,submission,search,judge-runtime,judge -am \
      -Dtest='DrainGateTest,DrainGateSignalIT,InboxConsumerDrainTest,SearchDocumentIndexWorkerTest,AuthAuditOutboxDispatcherTest,SearchDocumentChangedOutboxDispatcherTest,AppAuditOutboxDispatcherTest,SubmissionJudgedInboxBridgeTest,SubmissionCreatedDispatcherTest,JudgeOutboxDispatcherTest,JudgingLeaseReaperTest,NotificationIntegrationInboxBridgeTest,NotificationLedgerReaperTest,JudgeWorkerProcessorTest,AdminAuditIntegrationInboxBridgeTest,AuditOutboxDispatcherTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  else
    bash ./mvnw \
      -pl platform/common,platform/integration-inbox,auth,admin,app/app-web,notification,submission,search,judge-runtime,judge -am \
      -Dtest='DrainGateTest,DrainGateSignalIT,InboxConsumerDrainTest,SearchDocumentIndexWorkerTest,AuthAuditOutboxDispatcherTest,SearchDocumentChangedOutboxDispatcherTest,AppAuditOutboxDispatcherTest,SubmissionJudgedInboxBridgeTest,SubmissionCreatedDispatcherTest,JudgeOutboxDispatcherTest,JudgingLeaseReaperTest,NotificationIntegrationInboxBridgeTest,NotificationLedgerReaperTest,JudgeWorkerProcessorTest,AdminAuditIntegrationInboxBridgeTest,AuditOutboxDispatcherTest' \
      -Dsurefire.failIfNoSpecifiedTests=false test -B
  fi
) >/dev/null
printf 'DrainGate and worker SIGTERM/no-new-claim tests: PASS\n'
printf 'graceful-drain-contract: PASS\n'
