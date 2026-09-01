# P0-BASELINE-004 Infrastructure Workload & Failure Propagation Graph

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> deliverables: infrastructure usage graph + failure impact matrix

## 1. Topology (Compose)

- `docker-compose.yml` (base) + `docker-compose.dev.yml` (loopback-exposed) + `docker-compose.prod.yml` (secure, no published infra/backend ports) + `docker-compose.ha.yml` (reference, 10-17 no container_name, MySQL replica, not transparent failover)
- Verification: `docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null` && `... -f docker-compose.prod.yml config >/dev/null` (both must pass; no Actuator health probe)

## 2. Workload Classification

| Dependency | Workloads Sharing It | Consumer Owners/Workers | Key/Table/Index Examples | Criticality | Isolation Today |
|------------|----------------------|------------------------|--------------------------|-------------|-----------------|
| MySQL (single instance, 5 schemas) | Owner datasources | auth, admin, app, submission, notification | `accounts`, `contests`, `submissions`, `problems` | authoritative | schema+account isolated, instance shared |
| Redis (single instance) | Streams, cache, rate-limit, replay, queue, judge, Pub/Sub | app, submission, judge, admin, auth | `stream:integration:*`, `cache:*`, `rate:*`, `replay:*`, `queue:*`, `judge:*`, `pubsub:*` | mixed (event=durability, cache=eviction, coordination=latency) | ACL identity/keyspace only (`docker/redis/generate-users-acl.sh:58-74`), not memory/eviction/connection/failure |
| MeiliSearch (single) | Search index (derived) | search (writer), app (reader with DB fallback) | `problem_index`, `solution_index` | derived | single writer (`SearchDocumentIndexWorker.java:40-55`), reader fallback `DefaultSearchReadProjection.java:91-148` |
| Nacos (registry/config) | Service discovery, config | all Owners + Workers | `service-registry`, `config` | control-plane | cluster profile needs external nodes (`docker-compose.ha.yml:10-17`), fail semantics must be tested not fabricated |

## 3. Redis Keyspace & Command Inventory (source scan)

- ACL: `generate-users-acl.sh:58-74` — per-owner user, key prefix restrictions (`~cache:*`, `~stream:*`, etc.), command restrictions — **does not** isolate `maxmemory`, eviction, connections.
- Streams: `XADD`, `XREADGROUP`, `XACK`, `XPENDING` via `stream:integration` — durability, PEL/DLQ resilient
- Cache: `GET/SET` with TTL, eviction `allkeys-lru` would affect Streams if shared (fault propagation)
- Rate-limit, replay, queue, judge, Pub/Sub — each key prefix but same instance memory/connection pool

## 4. Failure & Recovery Matrix

| Workload | Failure Mode | Observed Impact (current) | Fallback | Recovery (disposable) | Gate Requirement |
|----------|--------------|---------------------------|----------|----------------------|------------------|
| MySQL primary loss | connection refused, pool exhaust | All Owners fail (shared instance) | none (authoritative) | per-Owner backup restore (`docs/operations/backup-and-recovery.md:7-23`, `scripts/dev/migrate-owner-*.sh`), checksum, HA replica reference flow (not transparent failover) | P1-INFRA-004 matrix, connection budget |
| Redis maxmemory eviction | `allkeys-lru` evicts stream/control keys | Streams PEL loss, coordination lease loss | cache miss fallback, stream replay from owner DB | disposable eviction drill (`P1-INFRA-003`) must prove no stream/control eviction | P1-INFRA-003 gate blocks if stream key evicted |
| Redis latency / connection saturation | timeout, `CLIENT PAUSE` | Dubbo RPC timeout 800ms, bulkhead 32 | retry 1, circuit open 5/30s | backpressure drill, PEL assertions | role client seam |
| MeiliSearch down | index unavailable | search degraded | DB fallback (different ordering/freshness, disclosed) | reindex from Owner data (`SearchDocumentIndexWorker`) | P1-INFRA-005 contract |
| Nacos down at startup | fail-fast vs retry vs local cache | service cannot register/discover | local cache if configured, not fabricated success | disposable Nacos stop/start drill | P1-INFRA-006 semantics |

## 5. Isolation Decision (P1-INFRA-001 input)

- **Default**: ACL + keyspace + role-named client seam (`P1-INFRA-002`), **not** physical instance split. Same instance, different logical role routing (all roles point to same instance via default adapter).
- **Trigger for split**: disposable fault drill proves mutually exclusive `maxmemory`/eviction/durability or connection recovery conflict (quantitative), not premature split.
- No new broker/platform (Kafka, etc.) — excluded_scope.

## 6. Verification

- `docker/redis/generate-users-acl.sh` inventory: 58-74 covers identity/keyspace, no `maxmemory`/`maxclients` isolation
- `grep -rn "REDIS_HOST\|RedisTemplate\|Redisson" services/**/application*.yml` shows shared `REDIS_HOST` today (role seam not yet)
- `cat docker-compose.ha.yml:10-17` confirms no transparent failover, needs external nodes
- `check_index_coverage` on `docker/redis/*`, `docker-compose*.yml` — fallback to direct read

## Evidence Level

Repository Implemented + Disposable Validatable. No production multi-host failover claim (SVC-007 DEFERRED, excluded_scope).
