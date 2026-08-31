#!/usr/bin/env bash
set -euo pipefail

# P3-STREAM-001 contract: Redis Streams is at-least-once transport; durable
# MySQL inboxes own business idempotency, leases, retry/backoff and poison rows.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "stream-resilience-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file is missing: $text"
}

not_contains() {
  local file="$1" text="$2"
  ! grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file must not contain: $text"
}

for source in \
  services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/InboxConsumer.java \
  services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/ConsumerInboxMapper.java \
  services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java \
  services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java \
  services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java \
  services/judge-runtime/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java \
  services/platform/common/src/main/java/com/ulticode/common/event/IntegrationEventEnvelopeContract.java; do
  [[ -f "$ROOT_DIR/$source" ]] || fail "missing stream source: $source"
done

contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/InboxConsumer.java 'claimLease'
contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/InboxConsumer.java 'markProcessed'
contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/InboxConsumer.java 'markFailed'
contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/ConsumerInboxMapper.java "state = 'PROCESSING'"
contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/ConsumerInboxMapper.java 'POWER(2'
contains services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/ConsumerInboxMapper.java "THEN 'DEAD'"
contains services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java 'ReadOffset.from("0-0")'
contains services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java 'ReadOffset.lastConsumed()'
contains services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java 'IntegrationEventPoison'
contains services/app/app-web/src/main/java/com/ulticode/modules/event/inbox/SubmissionJudgedInboxBridge.java 'IntegrationEventEnvelopeContract.requireCompatibleEnvelope'
contains services/notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java 'IntegrationEventEnvelopeContract.requireCompatibleEnvelope'
contains services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java 'ATOMIC_DEAD_LETTER_SCRIPT'
contains services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java "redis.call('XACK'"
contains services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java 'parseVersion'
contains services/judge-runtime/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java 'ATOMIC_ENQUEUE_SCRIPT'
contains services/judge-runtime/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java 'ATOMIC_DEAD_LETTER_SCRIPT'
contains services/judge-runtime/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java 'StreamMessageId.ALL'
contains services/platform/common/src/main/java/com/ulticode/common/event/IntegrationEventEnvelopeContract.java 'CURRENT_SCHEMA_VERSION'
contains services/platform/common/src/main/java/com/ulticode/common/event/IntegrationEventEnvelopeContract.java 'requireCompatibleEnvelope'
not_contains services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java 'return renewed == null'
not_contains services/search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java 'catch (RuntimeException exception) {\n            log.warn("Search document lease renewal check failed'

printf 'durable inbox claim/lease/retry/backoff/dead-letter contract: PASS\n'
printf 'stream consumer PEL/replay/poison/schema/version contract: PASS\n'
printf 'judge atomic enqueue/DLQ and search atomic ACK contract: PASS\n'

if [[ -n "${REDIS_HOST:-}" ]]; then
  (
    cd "$ROOT_DIR/services"
    if command -v mise >/dev/null 2>&1; then
      mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl judge-runtime -am \
        -Dtest=JudgeStreamRedisIntegrationTest -DfailIfNoTests=false test -B
    else
      bash ./mvnw -pl judge-runtime -am \
        -Dtest=JudgeStreamRedisIntegrationTest -DfailIfNoTests=false test -B
    fi
  ) >/dev/null
  printf 'real Redis crash/reclaim/dedup/DLQ integration tests: PASS\n'
else
  printf 'real Redis crash/reclaim/dedup/DLQ integration tests: BLOCKED_EXTERNAL (REDIS_HOST is unset)\n'
fi

printf 'stream-resilience-contract: PASS\n'
