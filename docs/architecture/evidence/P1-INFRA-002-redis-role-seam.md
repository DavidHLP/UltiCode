# P1-INFRA-002 Redis Role-Named Client/Config Seam

> status: COMPLETE
> head: current Batch B/C worktree
> decision: logical role aliases first; all roles still use the existing Redis endpoint
> evidence level: Repository Implemented; no production resource-isolation claim

## Seam

`com.ulticode.common.redis.RedisWorkloadRole` is the dependency-free role vocabulary:
`STREAMS`, `CACHE`, `RATE_LIMIT`, `REPLAY`, `QUEUE`, `JUDGE`, and `PUBSUB`.

`services/app/app-web/src/main/java/com/ulticode/app/config/AppRedisRoleConfig.java` exposes role-named `RedisConnectionFactory` aliases (`redisStreamsConnectionFactory`, `redisCacheConnectionFactory`, `redisRateLimitConnectionFactory`, `redisReplayConnectionFactory`, `redisQueueConnectionFactory`, `redisJudgeConnectionFactory`, `redisPubsubConnectionFactory`). Every alias delegates to the existing Boot `redisConnectionFactory` and is `@Primary`, preserving all unqualified legacy injections while making future routing a configuration change.

The App cache manager is the first pilot: `AppCacheConfig.cacheManager` consumes the `redisCacheConnectionFactory` qualifier. This is a low-risk derived workload; cache loss remains recoverable from App-owned source data. No business caller selects an arbitrary ACL principal or keyspace.

## Per-role initial budgets

| Role | Initial endpoint | Initial client budget | Durability/failure policy |
|---|---|---|---|
| streams | `REDIS_HOST:REDIS_PORT/REDIS_DB` | current App Lettuce pool (8 active, 10s timeout) | no eviction; outbox/inbox/PEL recovery |
| cache | same | current App Lettuce pool (8 active, 10s timeout) | derived, 300s TTL; loss recomputes |
| rate-limit | same | current pool; synchronous | ephemeral counters; no silent fail-open |
| replay | same | current pool; synchronous | fail closed; one-shot markers not evictable |
| queue | same | compatibility-only | legacy RQueue/status, never normal producer |
| judge | same | current Judge Redisson/Redis budgets | fenced Streams, dedup/DLQ/control keys no eviction |
| pubsub | same | listener/publisher connections | best-effort WebSocket relay, local fallback where existing |

These are role budgets and policies for the current single-instance adapter, not production SLOs or capacity proof. Physical instance separation requires a P1-INFRA-003 trigger (`T-EVICT`, `T-LATENCY`, `T-CONNECTION`, `T-RECOVERY`, `T-RESOURCE`, or `T-RETENTION`).

## ACL alignment repaired with the seam

`docker/redis/generate-users-acl.sh` now aligns the live consumers without widening an owner beyond its role:

- `ulticode-auth`: `(+xadd ~stream:auth-audit)`; App/Auth cannot read or mutate the other owner's audit stream.
- `ulticode-app`: `(+xadd ~stream:app-audit)` plus `(+exists ~blacklist:token:*)`; blacklist writes are denied.
- `ulticode-admin`: command-limited read/ack/group-create selectors for `~stream:app-audit` and `~stream:auth-audit`; its main selector has no audit-stream key pattern.
- `ulticode-notification`: `~rate-limit:email:*` and `~rate-limit:notification:*`, matching its live buckets rather than the global rate-limit namespace.
- `ulticode-admin` has no shared `stream:integration` access. Pre-cutover shared audit events require the Ops-only `admin-audit-stream-migration.sh` runbook before the new owner-specific streams are enabled.

The Redis ACL contract asserts these scoped selectors, deny-by-default, no unrestricted key/channel pattern, no plaintext password, and forbidden administrative commands.

## Verification

- `rtk bash scripts/test/redis-acl-contract.sh` => PASS.
- `rtk ./scripts/dev/test.sh quick` => PASS (all static contracts, Maven reactor, frontend checks; external-only drills remain BLOCKED_EXTERNAL as designed).
- 历史时态：上一条 `quick` PASS 记录于语义收敛前；`quick` 已于 2026-09-03 收敛为 `static + unit` 的弃用别名，同日 U-03/SVC-020 以根 POM `unit` profile + deny 环境实证关闭（5786 测试零失败、零 Testcontainers/IT），后端 `unit` 不再 fail closed。当时的 Maven reactor/frontend 全量语义现由 `full-local` 承担（见 `docs/development/testing.md`、`scripts/dev/test.sh --describe` 与 `current-status.md`）。
- `AppRedisRoleConfigTest` covers delegate identity and complete seven-role vocabulary; full App module test remains the behavioral regression surface.
- `git diff --check` required before commit.

## Explicit non-goals

No Redis physical split, new broker, Kubernetes, MQ, or service mesh. ACL/keyspace identity separation is not resource/failure isolation. This record does not claim production eviction, latency, connection, recovery, or failover evidence.
