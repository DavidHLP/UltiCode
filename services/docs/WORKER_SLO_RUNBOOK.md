# Worker SLO Runbook

Owner: platform / search / judge / notification. Source classes: `WorkerSloMeters`, `SearchDocumentIndexWorker`, `RedissonStreamsJudgeQueueAdapter`, `UnackedStreamEntriesReaper`, `NotificationIntegrationInboxBridge`.

> All thresholds in `docker/prometheus/worker-slo-alerts.yml` are initial suggestions. Must be re-tuned after 1-2 weeks of baseline (p50/p95) observation.

## 1. Metrics semantics

Metrics are registered via `com.ulticode.common.metrics.WorkerSloMeters` (plain Java, no Spring). Each worker holds one instance keyed by `(MeterRegistry, prefix)`. Gauges are backed by `AtomicLong`. Micrometer → Prometheus naming: dotted → underscore, `Counter` suffix `_total`.

| Prometheus name | Source setter | Meaning | UNKNOWN handling |
|---|---|---|---|
| `search_worker_queue_lag` | `slo.setQueueLag(...)` in `SearchDocumentIndexWorker.refreshSloGauges()` via `streamIntegrationLag()` (`XINFO GROUPS` lag, Redis ≥7; raw callback) | `XINFO GROUPS lag` for group `search-worker` on `stream:integration`. Delivered-but-not-yet-consumed count. | `-1` when broker cannot answer (pre-7.0, no group, or raw callback empty). Alerts filter `!= -1`. |
| `search_worker_queue_pel_size` | `slo.setPelSize(...)` from `StreamOperations.groups(...).pendingCount()` | PEL depth for the group | `-1` initially, `0` when empty. Stale value kept on observation failure. |
| `search_worker_queue_pel_oldest_age_seconds` | `slo.setPelOldestAgeSeconds(...)` from oldest `PendingMessage.getElapsedTimeSinceLastDelivery()` | Age of oldest PEL entry since last delivery | `-1` when `XPENDING` fails (not updated); `0` when PEL empty. Alerts filter `!= -1`. |
| `search_worker_queue_dlq_size` | `slo.setDlqSize(XLEN search:stream:dlq)` | DLQ stream length | `-1` until first successful `XLEN`; updated only on success. |
| `search_worker_last_success_timestamp` | `slo.markSuccess()` when `consume()` processed ≥1 record (not on empty poll) | Epoch millis of last successful consume cycle | `0` until first success. Alerts require `> 0`. |
| `search_worker_consume_failures_total` | `slo.incrementFailures()` on `ensureGroup` failure, stream read/reclaim failure, per-record `process()` exception | Counter of failed cycles/records | Monotonic; use `increase(...[5m])`. |
| `judge_streams_queue_lag` | `slo.setQueueLag(streamsAdapter.streamLag())` in `UnackedStreamEntriesReaper.refreshSloGauges()` via `Redisson RStream.listGroups().getLag()` | Group `judge-workers` lag on `judge:{judge-stream}:stream` | `WorkerSloMeters.UNKNOWN (-1)` when group absent or exception. |
| `judge_streams_queue_pel_size` | `slo.setPelSize(pendingCount())` from `RStream.getPendingInfo().getTotal()` | PEL depth | `-1` initially; `0` when empty. |
| `judge_streams_queue_pel_oldest_age_seconds` | `slo.setPelOldestAgeSeconds(oldestPendingIdleMs/1000)` via `RStream.listPending(..., 1)` idle time | Oldest PEL idle ms → seconds | `UNKNOWN (-1)` when observation fails (not updated). Alerts filter `!= -1`. |
| `judge_streams_queue_dlq_size` | `slo.setDlqSize(dlqSize())` via `RStream(JUDGE_STREAM_DLQ_KEY).size()` or `0` if not exists | DLQ `judge:{judge-stream}:dlq` length | `UNKNOWN (-1)` on exception. |
| `judge_streams_last_success_timestamp` | `sloMeters.markSuccess()` in `RedissonStreamsJudgeQueueAdapter.ack()` (XACK success) | Last successful ack | `0` until first ack. |
| `judge_streams_consume_failures_total` | `markConsumeFailure()` on `poll` exception/no-group fallback, poison decode, `ack` exception | Counter | `increase(...[5m])`. |
| `notification_inbox_queue_lag` | `slo.setQueueLag(streamLag(fallback))` in `NotificationIntegrationInboxBridge.refreshSloGauges()` (XINFO GROUPS lag with stream length fallback) | `App-Notification` group lag on `stream:integration` | `-1` only if both `XINFO` and fallback `XLEN` fail; otherwise fallback to stream length. |
| `notification_inbox_queue_pel_size` | `slo.setPelSize(pending)` from `groups.pendingCount()` | PEL depth for `App-Notification` | `0` when no group info. |
| `notification_inbox_queue_pel_oldest_age_seconds` | `slo.setPelOldestAgeSeconds(oldestPendingAgeSeconds())` from `XPENDING` oldest entry | PEL oldest age | `-1` on exception (not updated); `0` when empty. |
| `notification_inbox_queue_dlq_size` | **Not applicable** — notification has no stream DLQ; gauge stays `UNKNOWN (-1)`. Poison goes to DB (see §3). | — | Always `-1`; no alert on it. |
| `notification_inbox_last_success_timestamp` | `slo.markSuccess()` at end of `consume()` (stage+process success) | Last success | `0` until first. |
| `notification_inbox_consume_failures_total` | `slo.incrementFailures()` on `reclaim`/`read`/`stageRecord` exception | Counter | `increase(...[5m])`. |

Additional counters (not WorkerSloMeters, but visible in `/actuator/prometheus`):
- `judge_streams_poison_total` — poison decode skip (Redisson adapter).
- `judge_streams_dlq_total` — dead-lettered entries (adapter `incrementDeadLetterCounter`).
- `search_worker_processed_total`, `search_worker_deadlettered_total`, `search_worker_stale_skipped_total` — Search worker lifecycle.

### UNKNOWN=-1 operational meaning
- `queue.lag = -1` / `pel.oldest = -1` / `dlq.size = -1` means the observation cycle failed (Redis down, pre-7.0, key evicted). The worker keeps the previous gauge value and never breaks consumption for it. **Do not page on `-1` alone**; page on sustained `!= -1` breaches plus `consume_failures` / `last_success` stall.
- `last.success.timestamp = 0` means the worker has never marked success since boot (empty stream or immediate failures).

## 2. Alert → response

All alerts carry `threshold_note` annotation reminding they are initial suggestions.

| Alert | Severity / For | Triage |
|---|---|---|
| `*QueueLagHigh/Warning` (search 500/10m, judge 200/10m, notif 500/10m) | warning | 1. `PM2`: `pm2 status` + `pm2 logs <app> --lines 50`. 2. Redis: `XLEN stream:integration`, `XINFO GROUPS stream:integration`, `XPENDING` depth. 3. Downstream: MeiliSearch `/health` (search), DB/Nacos (notification), judge concurrency `JUDGE_MAX_CONCURRENT_JOBS`. No restart yet; observe 10m. |
| `*QueueLagCritical` (search 2000/5m, judge 1000/5m, notif 2000/5m) | critical | Same as above + check `search_worker_last_success_timestamp` staleness. If MeiliSearch/DB healthy and lag still climbing, scale consumer count or increase `batchSize` (currently 50). Requires no stop-write window for lag alone. |
| `*PelOldestHigh` (300s search/notif, 600s judge) | warning | PEL entries not making progress. `XPENDING` → `XRANGE` sample ids → `XINFO STREAM` inspect `deliveryCount`. Search: claim window is `CLAIM_MIN_IDLE=30s` and `maxAttempts=5` before DLQ. Judge: visibility `1800000ms (30m)` before reaper `claimIdle`. Check poison/retry logs. |
| `*PelOldestCritical` (600s / 1800s) | critical | Likely poison loop. Pull one PEL entry payload and validate (`eventType`, `aggregateId`). If malformed, it will dead-letter after max deliveries; if downstream down, fix downstream first — reclaim will self-heal (see §4). |
| `*ConsumeFailures` (>5/5m warning, >20/5m critical) | warning→critical | `increase(..._consume_failures_total[5m])`. Correlate with error logs: `slo.incrementFailures()` sites = group creation / stream read / claim / ack / decode. Check Redis ACL (`REDIS_USERNAME`) and `XGROUP` existence. |
| `*Stalled` (>600s warning, >1800s critical) | warning→critical | `time() - last_success/1000 > threshold` with `>0` guard. Worker has not advanced since boot or since last batch. `pm2 describe ulticode-<app>`; if process `online` but stalled, restart worker. If stream empty, this alert is expected to be silent (0 → filtered). |

## 3. DLQ / poison operations

### Search: `search:stream:dlq`
- **Structure**: Redis Stream `search:stream:dlq` (config `search.worker.dlq-key`, default `search:stream:dlq`). Each dead-lettered record is `XADD`ed with the original event fields (`eventId`, `owner`, `eventType`, `aggregateId`, `aggregateVersion`, … `payload`). Atomicity: Lua script `ATOMIC_DEAD_LETTER_SCRIPT` does `SET NX EX 86400` on marker `search:stream:dlq:seen:<PEL-id>` then `XADD` + `XACK`; marker prevents duplicate DLQ writes.
- **Inspect**: `XLEN search:stream:dlq` (also gauge `search_worker_queue_dlq_size`), `XRANGE search:stream:dlq - + COUNT 10`, `XINFO STREAM search:stream:dlq`.
- **Marker**: `GET search:stream:dlq:seen:<id>` shows `1` with 86400s TTL. Exists = already dead-lettered.
- **Replay** (requires stop-write window if version-ledger interaction matters — see §5): `XREAD` the DLQ entry → re-`XADD` to `stream:integration` with same fields (or call the App outbox redispatch), then `XDEL search:stream:dlq <dlq-id>` if re-enqueued successfully. Do not delete the `seen:` marker until replay succeeds; the 24h TTL auto-clears it. Alternative discard: `XDEL search:stream:dlq <id>`.
- **When to replay**: only after fixing root cause (MeiliSearch mapping, allowlist, payload schema). Replaying a true poison (unsupported index) will dead-letter again after 5 deliveries — prefer discard + fix publisher.

### Judge: `judge:{judge-stream}:dlq`
- **Structure**: Stream `judge:{judge-stream}:dlq` (`JudgeStreamKeys.JUDGE_STREAM_DLQ_KEY`). Dead-letter triggered in `RedissonStreamsJudgeQueueAdapter.claimIdle()` **before** `XCLAIM` when `deliveryCount >= maxDeliveryAttempts (5)`. Lua `ATOMIC_DEAD_LETTER_SCRIPT` writes marker `judge:{judge-stream}:dlq:seen:<sourceId>` (`JUDGE_STREAM_DLQ_SEEN_PREFIX + sourceId`) with `NX EX max(3600, visibilityTimeout/1000)`, then `XADD` fields `payload, sourceId, deliveryCount, consumer, reason=max-delivery-attempts`, then `XACK` original.
- **Inspect**: `XLEN judge:{judge-stream}:dlq`, `XRANGE judge:{judge-stream}:dlq - + COUNT 10`. DLQ size gauge: `judge_streams_queue_dlq_size`.
- **Marker**: `GET judge:{judge-stream}:dlq:seen:<streamId>` TTL ≥3600s.
- **Replay/discard**: Same stop-write consideration as search (generation fence CAS makes replay safe but wasteful). Replay = `XADD` envelope payload back to `judge:{judge-stream}:stream` (or via `JudgeEnqueueAdapter` port), then `XDEL judge:{judge-stream}:dlq <id>`. Discard = `XDEL`. Poison counter `judge_streams_poison_total` (decode failures) are acked immediately without DLQ — inspect PM2 logs for `Streams poison message`.
- **Dedup key**: `judge:{judge-stream}:dispatch:seen:<submissionId>:<generation>` (TTL `visibilityTimeout*5/1000`) prevents duplicate enqueue on replay; a fresh generation must be used for rejudge.

### Notification: poison inbox (no stream DLQ)
- **Structure**: No Redis DLQ. Malformed integration events are staged as **DB inbox rows** via `ConsumerInboxMapper.insertIfAbsent()` with `eventType=IntegrationEventPoison` (`POISON_EVENT_TYPE`) and `poison:<recordId>` or `poison:<redisStreamId>` as `eventId` when the original `eventId` is missing/invalid. Fields: `streamId=record.getId().getValue()`, `fields=record.getValue()`, `error=Exception: message` serialized as JSON payload. The bridge (`NotificationIntegrationInboxBridge.stagePoison()`) **still `XACK`s** the original stream record after staging poison, so the PEL does not grow.
- **Handler**: `InboxConsumer` registers `rejectPoison` for `IntegrationEventPoison` which throws `IllegalArgumentException("Poison integration event: ...")` — the row stays in inbox as failed and is not retried silently.
- **Inspect**: `SELECT * FROM consumer_inbox WHERE consumer_group='App-Notification' AND event_type='IntegrationEventPoison' ORDER BY created_at DESC LIMIT 20;` Check `payload` JSON for `streamId`/`fields`/`error`.
- **Replay**: Fix publisher schema, then insert a corrected event with a **new `eventId`** (≤40 chars) into the inbox or republish to `stream:integration` via the outbox. Do not reuse the `poison:*` eventId; the inbox dedup is on `(consumer_group, eventId)` via `insertIfAbsent`.
- **Discard**: `DELETE FROM consumer_inbox WHERE event_id = 'poison:...'` after confirming. No Redis `XDEL` needed (already acked). This path never needs a stop-write window (consumer-group ack isolates it).

## 4. Backlog recovery

### Search worker (`SearchDocumentIndexWorker`)
- **Poll loop**: `@Scheduled(fixedDelay= ${search.worker.interval-ms:2000})` → `ensureGroup()` → `refreshSloGauges()` → `drainPending()` then `drainNew()`. `markSuccess()` only when `processed >0`.
- **PEL recovery**: `drainPending()` does `XPENDING` (up to `batchSize=50`) → for each `PendingMessage`: if `deliveryCount > maxAttempts (5)` → `deadLetter()` (Lua atomic, increments `search_worker_deadlettered_total`), else collect `reclaimIds`. Then single `XCLAIM` with `CLAIM_MIN_IDLE=30s` reassigns remaining ids to `consumerName` (per-replica `group-hostname` or `SEARCH_WORKER_CONSUMER_NAME` override) and reprocesses. Entries that fail `process()` stay in PEL for next cycle.
- **Readiness heartbeat**: `SearchWorkerReadinessHeartbeat` (`fixedDelay 10000ms`) pings Redis `PING` and `meilisearch.health()`; only while both answer it refreshes `${search.worker.ready-file}` marker (stale after 2m). A dead MeiliSearch removes the replica from `service_healthy` without faking health; indexing failures alone do **not** fail the heartbeat.
- **Operator action on backlog**: 1. Fix downstream (MeiliSearch). 2. Live replicas auto-reclaim after 30s idle; no manual `XCLAIM` needed unless all replicas died. 3. If all replicas died, restart one — its `drainPending()` will reclaim the orphaned PEL entries. No lease reaper.

### Judge worker (`RedissonStreamsJudgeQueueAdapter` + `UnackedStreamEntriesReaper`)
- **Poll**: `XREADGROUP > COUNT 1` with `BLOCK timeout` via Redisson `RStream<String,String>` + `StringCodec`. NOGROUP auto-recovers via `ensureGroup()` (group `judge-workers` from `StreamMessageId.ALL` = `0-0`, so pre-group entries replay idempotently; uses `SETNX dispatch:seen` fence).
- **Reaper**: `UnackedStreamEntriesReaper.recoverUnackedStreamEntries()` (`fixedDelay 10000ms`, `initialDelay 15000ms`) — only when `app.features.judge-queue.use-port=true` and `app.runtime.role=judge`. Each sweep: `pendingCount()` → update `judge.streams.pending` gauge → `refreshSloGauges()` → if `pending>0` and worker `hasCapacity()` → `claimIdle(visibilityTimeoutMs=1800000)` (30m). `claimIdle` lists `XPENDING` idle ≥ minIdle, one entry at a time (paged by idle, not by ID, to avoid head-of-line blocking by a recently re-claimed oldest ID). If `deliveryCount >= maxDeliveryAttempts` → dead-letter via Lua before claiming (no idle increment burn). Else `XCLAIM` to `consumerId` (prefix `ulticode-judge`) and routes to `JudgeWorkerProcessor.processReclaimedHandle()` for fenced `acquireLease → heartbeat → execute → writeVerdictFenced → XACK`.
- **Lease heartbeat**: `DefaultJudgeAttemptExecutor` holds a per-attempt `heartbeatExecutor` that renews the DB lease (`judge:lease:`) for the running attempt; `nack` leaves the PEL entry for the reaper (no re-enqueue, dedup would reject it).
- **Operator action**: replicas auto-reclaim after 30m idle; reaper paces at 1 entry / 10s so natural drain ≈ visibility timeout. If judge capacity is full, reaper backs off (`hasCapacity` guard).

### Notification bridge (`NotificationIntegrationInboxBridge`)
- **Loop**: `@Scheduled(fixedDelay= ${integration.inbox.consumer.interval-ms:2000})` → per-binding `stage()` then `inboxConsumer.consume()`. `stage()` does `reclaim()` (claim `30s` idle, batch `50`) + `read(0-0)` + `read(lastConsumed)`, deduped by Redis stream id. `consume()` runs the DB inbox consumer (Tx boundary via `InboxConsumer`).
- **No DLQ stream**: poison → DB inbox row (see §3), already acked. PEL backlog therefore only means staging/ack failures, not poison.
- **Recovery**: same 30s claim window as search; live bridge replicas reclaim automatically. DB inbox retries are governed by `InboxConsumer` polling, not Redis.

## 5. Stop-write windows

| Operation | Stop-write needed? | Reason |
|---|---|---|
| Tuning thresholds / Prometheus `rule_files` reload | No | Read-only. `kill -HUP` / `/-/reload`. |
| `XLEN` / `XINFO` / `XRANGE` inspection, DLQ list | No | Read-only Redis. |
| `XDEL` discard of a DLQ entry | No, unless you plan to replay with same `eventId`/`sourceId` and dedup marker still live | DLQ is not consumed by workers. |
| Replay search DLQ entry to `stream:integration` | **Yes — short window recommended** | Version ledger `search:doc-version:<index>` hash is `HSET` by document id; stale snapshot racing a live write can flip the ledger check (`isStale`). Use generation/version fencing; coordinate with App outbox pause if the replayed doc version is old. The `seen:` marker TTL 24h prevents dup DLQ write but not dup index write. |
| Replay judge DLQ entry to `judge:{judge-stream}:stream` | **Yes — if reusing same generation** | Dedup `judge:{judge-stream}:dispatch:seen:<id>:<gen>` `SET NX` will reject same-gen replay. Need new generation via rejudge/outbox. Without new generation, stop dispatch briefly or the replay is silently dropped. |
| Replay notification poison via DB inbox `insertIfAbsent` | No | `consumer_group + eventId` dedup isolates it; stream already acked. |
| `XCLAIM` manual reclaim of PEL entries (all workers dead) | No, but prefer restarting a worker | Worker `drainPending`/`reclaim` already does it. Manual `XCLAIM` needs correct `consumerName` and `min-idle`. |
| Resetting version ledger `HDEL search:doc-version:*` | **Yes** | Destructive; pause App search mutations and backfill. |
| Flushing a stream (`DEL stream:integration`) | **Yes — never do in prod** | Loses `XINFO GROUPS` lag and group offsets. Requires `XGROUP CREATE` from `0-0` and may replay trimmed history. |

## 6. Quick commands

```bash
# Search lag / PEL
redis-cli -h $REDIS_HOST -p $REDIS_PORT --user ulticode-search --pass "$SEARCH_REDIS_PASSWORD" XLEN stream:integration
redis-cli ... XINFO GROUPS stream:integration
redis-cli ... XPENDING stream:integration search-worker
redis-cli ... XLEN search:stream:dlq

# Judge lag / PEL / DLQ (note hashtag key)
redis-cli -h $REDIS_HOST -p $REDIS_PORT --user ulticode-judge --pass "$JUDGE_REDIS_PASSWORD" XLEN "judge:{judge-stream}:stream"
redis-cli ... XINFO GROUPS "judge:{judge-stream}:stream"
redis-cli ... XLEN "judge:{judge-stream}:dlq"

# Notification bridge
redis-cli -h $REDIS_HOST -p $REDIS_PORT --user ulticode-notification --pass "$NOTIFICATION_REDIS_PASSWORD" XINFO GROUPS stream:integration
psql "$NOTIFICATION_DB_URL" -c "select event_id, event_type, left(payload::text,200), created_at from consumer_inbox where consumer_group='App-Notification' order by created_at desc limit 20;"

# Prometheus metrics (per service)
curl -s http://localhost:9107/actuator/prometheus | grep -E 'search_worker|judge_streams|notification_inbox'
```

## 7. Verification checklist (for this runbook)

- Class/field cross-check: `WorkerSloMeters.UNKNOWN=-1`, `SearchDocumentIndexWorker.props.streamKey=stream:integration / group=search-worker / dlqKey=search:stream:dlq / CLAIM_MIN_IDLE=30s / maxAttempts 5 / versionKeyPrefix search:doc-version`, `JudgeStreamKeys` stream `judge:{judge-stream}:stream` / dlq `judge:{judge-stream}:dlq` / seen prefixes / group `judge-workers` / visibility `1800000`, `NotificationIntegrationInboxBridge.STREAM_KEY=stream:integration / group App-Notification / POISON_EVENT_TYPE=IntegrationEventPoison / poison: prefix`.
- Alert ↔ metric mapping verified against `WorkerSloMeters` registration prefixes `search.worker` / `judge.streams` / `notification.inbox`.
