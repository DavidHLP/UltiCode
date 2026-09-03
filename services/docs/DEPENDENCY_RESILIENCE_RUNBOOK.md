# P3-RES-001 — Dependency resilience

Every synchronous backend dependency must have one bounded timeout, retry,
bulkhead, circuit and fallback policy. A fallback may degrade explicitly or
fail closed; it must never return a fabricated success.

## Policy matrix

| Dependency | Timeout / total budget | Retry | Bulkhead and circuit | Fallback |
| --- | --- | --- | --- | --- |
| Dubbo query | 800 ms per attempt, 1.6 s total | one framework retry; reads only | 32 logical calls per service, opens after 5 consecutive transport failures for 30 s, one half-open probe | filter throws; adapters may only use an already-explicit degraded response |
| Dubbo write | 3 s total | zero automatic retries; caller reuses its command/idempotency key explicitly | same shared service guard | fail closed |
| Judge execution | 190 s total | zero | same guard | fail closed; no sandbox re-execution |
| JWKS | 800 ms connect/read | no immediate retry; refresh backs off 30 s | synchronized single refresh | last-known key for 300 s after TTL, then cache clears and verification fails closed |
| OAuth | 5 s connect / 10 s read | zero; token exchange is not replayed | 8 calls per provider, 5 failures / 30 s / one probe | callback fails; 4xx does not poison the circuit |
| S3-compatible storage | 10 s connect / 30 s request | GET one retry; PUT/DELETE zero | 16 calls, 5 failures / 30 s / one probe | GET 404 is absence; every other rejection fails closed |
| MeiliSearch | pinned SDK 0.20.1 OkHttp 10 s phase limits | zero in repository code | App search 16, worker writes 8, backfill/readiness 1; 5 failures / 30 s / one probe | App database fallback is allowed only when response semantics says `fallback=true`; worker failures stay in PEL; backfill fails before publishing |

The Dubbo cluster filter wraps one logical invocation, so the query retry stays
inside one circuit/bulkhead permit. Only timeout, network, serialization, no
provider and similar transport failures count. Business, validation,
authorization and ordinary `RpcResult` failures prove the provider is reachable
and do not open the circuit.

## P5-INFRA-001 — Shared-infrastructure impact matrix

This is a repository-static matrix for backend roles only. Console and management
are not added: this runbook has no source-backed 4×9 contract for those surfaces.

`docker-compose.prod.yml` declares one `mysql`, `redis`, `nacos`, and `meilisearch`
service. Owner schemas/accounts and Redis ACL users are logical ownership
boundaries. Schema/ACL ownership does not equal physical fault isolation. The
four services therefore remain one shared fault domain in the reference Compose
shape. The
optional HA overlay is a stateful reference requiring operator promotion,
endpoint changes, and client configuration; it is not transparent failover.

Evidence labels:

- `RS` — repository-static fact from source, executable configuration, or a
  runbook.
- `SI` — supported inference from those facts; no runtime fault-injection result
  is implied.
- `UE` — unresolved external evidence. Production-only claims are
  `BLOCKED_EXTERNAL` or `DEFERRED`, never `PASS`.

| Backend | MySQL | Redis/Streams | Nacos/Dubbo registry | MeiliSearch |
| --- | --- | --- | --- | --- |
| **Auth** | Owner DB is checked by readiness; an unusable DB makes `/api/v1/auth/health/ready` return 503. MySQL is the production-default account store; no DB failover is evidenced. `[RS; UE=BLOCKED_EXTERNAL]` | Readiness returns 503 on failed PING. OAuth state issue/consume requires Redis and fails closed; audit/search outbox XADD failures leave DB rows retryable. `[RS]` | Registry is outside readiness. Existing-provider continuity is only the runbook-supported inference; a new boot cannot be reported registered during registry loss. `[SI; UE=BLOCKED_EXTERNAL]` | No direct Meili client/config; Auth user changes enqueue an outbox event for the Search path. `[RS]` |
| **Admin** | Readiness returns 503 on DB failure; Admin data, fenced leases, and durable inbox rows are DB-backed. Restore authority/evidence is external. `[RS; UE=BLOCKED_EXTERNAL]` | Readiness returns 503 on failed PING. Audit stream staging pauses on Redis errors; ACK follows MySQL inbox insert, so unacknowledged entries remain recoverable and staged inbox rows are DB-local. `[RS]` | Registry is outside readiness. Selected owner reads expose `OK/PARTIAL/UNAVAILABLE` and fully unavailable reads map to 503; registry failover is not evidenced. `[SI; UE=BLOCKED_EXTERNAL]` | No direct Meili client/config; Search index recovery is not an Admin path. `[RS]` |
| **App** | Readiness returns 503 on unusable DB; App domain mappers are DB-backed and no alternate DB path is declared. `[RS; UE=BLOCKED_EXTERNAL]` | Readiness returns 503 on failed PING. Outbox/inbox rows stay durable in MySQL while stream work waits; WebSocket broadcast publish failures fall back to local push, but token-blacklist errors fail closed. `[RS]` | Registry is outside readiness. Local DB paths may remain available, but Dubbo-dependent calls have bounded failure semantics and writes do not auto-retry; no production failover evidence. `[SI; UE=BLOCKED_EXTERNAL]` | Database mode bypasses Meili. Indexed mode requires worker+client; outage uses explicit DB fallback only when enabled, otherwise returns unavailable; App readiness checks only DB/Redis. `[RS]` |
| **Submission** | Submission owner DataSource backs submissions and outbox tables. Production Compose does not gate this container on MySQL; no custom owner readiness controller is present. Outage/failover behavior beyond mapper failure is unresolved. `[RS; UE=BLOCKED_EXTERNAL]` | Judge Streams enqueue and result/created event dispatch use Redis; enqueue/XADD failures keep DB outbox rows retryable, so stored submissions persist while judging/event delivery is delayed. `[RS]` | Submission registration/references use Nacos and Compose waits for Nacos at startup; registry loss blocks new registration/discovery, while direct Redis outbox dispatch is a separate path. `[SI; UE=BLOCKED_EXTERNAL]` | No direct Meili client/config; indexing is Search-owned. `[RS]` |
| **Notification** | Readiness returns 503 on DB failure; inbox and delivery-ledger state are MySQL-backed. Restore drill is disposable only; production restore authority is absent. `[RS; UE=BLOCKED_EXTERNAL]` | Readiness returns 503 on failed PING. New stream records pause when staging cannot reach Redis; records are inserted into MySQL before ACK, while existing inbox rows can continue with DB. Notification has no stream DLQ; poison is staged in DB. `[RS; UE=BLOCKED_EXTERNAL]` | Registry is outside readiness. Recipient/owner RPC failures can make delivery or reads unavailable under bounded Dubbo policy; no blanket registry-outage fallback is proven. `[SI; UE=BLOCKED_EXTERNAL]` | No direct Meili client/config; Search index is not part of Notification persistence. `[RS]` |
| **Search** | DataSource/MyBatis/Flyway autoconfiguration is excluded; the worker has no MySQL path. `[RS]` | Redis is the stream source and a readiness input. Read/reclaim/ACK errors do not fabricate success; PEL work remains retryable and the heartbeat marker goes stale when Redis cannot answer. `[RS; UE=BLOCKED_EXTERNAL]` | No Dubbo/Nacos client or registry configuration; registry failure is outside the worker's direct dependency set. `[RS]` | Meili is the sole write target. Heartbeat requires Redis+Meili; write failure leaves events in PEL, and Owner-data backfill can rebuild the derived index. `[RS; UE=BLOCKED_EXTERNAL]` |
| **Judge** | DataSource/MyBatis/Flyway autoconfiguration is excluded; verdict persistence uses the remote Submission contract. `[RS]` | Redis Streams is the queue. Poll failure stops the worker queue path; NACK leaves entries in PEL, and the reaper reclaims or dead-letters after bounded attempts. Readiness marker proves Redis only. `[RS; UE=BLOCKED_EXTERNAL]` | Nacos is needed for App/Submission Dubbo references, but the Redis marker does not prove registry health; remote case/verdict calls therefore have registry-dependent failure semantics. `[SI; UE=BLOCKED_EXTERNAL]` | No direct Meili client/config; judging does not write the search index. `[RS]` |

Static source anchors: [`docker-compose.prod.yml`](../../docker-compose.prod.yml),
[`ReadinessChecks`](../platform/common/src/main/java/com/ulticode/common/health/ReadinessChecks.java),
[`App search projection`](../app/app-web/src/main/java/com/ulticode/modules/search/projection/DefaultSearchReadProjection.java),
[`App WebSocket bridge`](../app/app-web/src/main/java/com/ulticode/modules/websocket/broadcast/WebSocketBroadcastBridge.java),
[`Search worker`](../search/src/main/java/com/ulticode/search/SearchDocumentIndexWorker.java),
[`Search heartbeat`](../search/src/main/java/com/ulticode/search/SearchWorkerReadinessHeartbeat.java),
[`Judge Streams adapter`](../judge-runtime/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java),
[`Admin audit inbox`](../admin/src/main/java/com/ulticode/modules/admin/audit/AdminAuditIntegrationInboxBridge.java),
[`Notification inbox`](../notification/src/main/java/com/ulticode/notification/inbox/NotificationIntegrationInboxBridge.java).

Recovery entry points remain the existing [`backup and recovery`](../../docs/operations/backup-and-recovery.md),
this runbook for Meili/Nacos/Dubbo recovery, and [`Worker SLO`](WORKER_SLO_RUNBOOK.md)
for Redis PEL/DLQ and Meili replay/rebuild. These entries reuse the existing
drills; they do not grant production failover authority.

## Operator response

1. Confirm the rejection is `CIRCUIT_OPEN` or `SATURATED`; do not increase
   limits before checking dependency latency and error rate.
2. For Dubbo, inspect provider registration and the 800 ms/3 s/190 s call class.
   Writes must remain `retries=0` even when the provider is idempotent.
3. For JWKS, restore the allowlisted HTTPS endpoint before the bounded stale
   window expires. Expiry intentionally rejects RS256 tokens whose key cannot be
   re-proved.
4. For Search, keep events in Redis PEL and replay after MeiliSearch recovery.
   Do not ACK or write the version ledger on a rejected call.
5. For S3/OAuth, fix the upstream service or credentials. Circuit-open and
   saturation are availability failures, never successful business outcomes.

Validate with `bash scripts/test/dependency-resilience-contract.sh`. Production
threshold tuning and real dependency fault injection remain deployment evidence;
the repository contract does not authorize production traffic or configuration
changes.

## Cross-infrastructure recovery sequence

This section is the repository/disposable recovery map for the four shared
infrastructure dependencies. It is not production failover authority.

| Dependency | First response | Recovery proof | Stop condition |
| --- | --- | --- | --- |
| **MySQL** | Quiesce all Owner writers; do not repair a live schema from a partial dump. Select the encrypted archive and verify archive/dump SHA-256, schema set, Flyway metadata, and migration identity. | `scripts/runbooks/owner-backup-restore.sh` restores only to a disposable target in the order `ulticode → auth → admin → app → notification → submission`, then compares rows/checksums and runs owner smoke. | Missing backup/key/schema/checksum, active writer, busy fenced lease, or any smoke mismatch. Never downgrade schema; use a verified full release descriptor for rollback. |
| **Redis** | Classify the failure as ACL denial, latency/connection saturation, eviction, or restart. Preserve Streams PEL/DLQ and replay markers; do not flush keys to make a drill pass. | `scripts/test/redis-acl-contract.sh` checks scoped principals; `scripts/test/redis-role-fault-drill.sh` runs the disposable cache-pressure/backpressure drill and always destroys its project/volume. | Any stream/Judge/audit/replay control loss, forbidden cross-owner read/write, or missing role recovery. Physical split is considered only after the P1 quantitative triggers; ACL is not resource isolation. |
| **MeiliSearch** | Keep the integration event in PEL when the worker cannot write. App may use the explicit DB fallback only when its fallback contract is enabled; disclose `fallbackApplied`, source, freshness, and ordering. | `services/docs/WORKER_SLO_RUNBOOK.md` replay/DLQ steps plus Search worker version/tombstone checks; reindex from Owner data with the worker as sole writer. | Never ACK a rejected write, write the version ledger before Meili acceptance, or treat a derived index as authoritative data. |
| **Nacos/Dubbo registry** | Distinguish existing provider readiness from registry availability. Existing providers may continue serving; a new provider boot during registry outage must not be reported as registered. | `scripts/test/dubbo-nacos-smoke.sh` proves authenticated registration; opt-in `DUBBO_NACOS_SMOKE_REGISTRY_DRILL=1` stops Nacos, checks provider outage/restart/reconnect, and tears down disposable resources. | No token/endpoint/cert, registry state ambiguous, provider not ready, or registration count/metadata mismatch. HA Compose is a reference profile, not transparent failover. |

### Common evidence rule

Record source commit, exact configuration inputs (without secrets), owner/worker
impact, failure state, recovery command, checksum/watermark, and the evidence
level (`Repository Implemented`, `Disposable`, or `BLOCKED_EXTERNAL`). A
disposable `PASS` never becomes a production claim. Restore, cutover, and
schema-contraction operations additionally require backup, quiescence, checksum,
and rollback-descriptor evidence before any authorized external action.
