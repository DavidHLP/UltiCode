# P5-GATE-002 Infrastructure Degradation and Recovery Gate

> status: PASS (repository/disposable run)
> owner: CROSS / INFRA
> entrypoint: [`scripts/test/gate-infra-isolation.sh`](../../../scripts/test/gate-infra-isolation.sh)
> evidence_level: repository source + disposable Docker scenarios; no production evidence

This gate validates the current single-instance topology without claiming
production HA, failover, capacity, RPO/RTO, or SLO performance. A missing
external prerequisite is a non-zero `BLOCKED_EXTERNAL` result; it is never
rewritten as `PASS`.

## Scenario matrix

| Dependency/workload | Stop/degradation scenario | Recovery assertion | Evidence |
| --- | --- | --- | --- |
| Redis cache/control/Streams | Owner-scoped ACL checks, disposable maxmemory/no-eviction cache pressure, `CLIENT PAUSE`, and control-key preservation | ACL denials remain enforced; stream/control keys survive pressure and pause | `scripts/test/redis-acl-contract.sh`, `scripts/test/redis-role-fault-drill.sh` |
| Redis audit Streams | Migrate pending `AuditRecorded` entries from the legacy stream with a held `flock` and bounded Lua batches | Owner streams contain each event once, legacy PEL is empty, checkpoint metadata is retained, legacy stream is not deleted | `scripts/test/admin-audit-stream-migration-contract.sh`, `scripts/runbooks/admin-audit-stream-migration.sh` |
| Redis Judge transport | Disposable Redis-backed Judge Streams enqueue/poll/ACK, reclaim, poison, dedup, and DLQ integration | Replacement consumer reclaims pending work; exhausted/malformed entries reach DLQ; successful entries have empty PEL | `scripts/test/stream-resilience-contract.sh` |
| MySQL Owner schemas | Disposable five-owner backup/archive and restore/checksum workflow | Encrypted archive decrypts with the right key; wrong key fails; restore, checksum, retention, and singleton-lock assertions pass | `scripts/test/owner-backup-restore-contract.sh`, `docs/architecture/evidence/P1-INFRA-004-mysql-owner-matrix.md` |
| MeiliSearch derived index | Standalone MeiliSearch health endpoint is stopped and restarted | Health becomes unreachable during stop and returns after restart; index remains treated as derived | `scripts/test/meilisearch-recovery-contract.sh`, `docs/architecture/evidence/P1-INFRA-005-search-recovery-contract.md` |
| Nacos registry/config center | Disposable authenticated Nacos/Dubbo smoke registers Auth, stops Nacos, checks live-provider readiness and failed new boot classification, then restarts Nacos | Authenticated registry/API recovers; provider re-registers with metadata; provider restart reaches readiness and registration; required-config startup failure is recorded as `FAIL_START`, not success | `scripts/test/dubbo-nacos-smoke.sh`, `docs/architecture/evidence/P1-INFRA-006-nacos-failure-contract.md` |

## Verified run

The owner-only `.env` was copied to a protected temporary file and the
smoke-specific MySQL, Auth DB, Redis, Nacos HTTP/gRPC, HTTP, and Dubbo host
ports were isolated from the running local dev stack. The gate completed with:

```text
redis-role-fault-drill: PASS (disposable cache pressure/backpressure; control keys preserved; no production claim)
admin-audit-stream-migration-contract: PASS (migrate, preserve event id, ACK legacy PEL)
real Redis crash/reclaim/dedup/DLQ integration tests: PASS
Nacos security contract: PASS
provider boot while registry stopped: FAIL_START (expected; registry is a required startup dependency)
registry recovery and provider registration: PASS
meilisearch-recovery-contract: PASS (stop detected; health recovered; derived index boundary preserved)
dependency-resilience-contract: PASS
owner-backup-restore-contract: PASS
GATE-INFRA-ISOLATION: PASS (repository/disposable scenarios; no production claim)
```

`FAIL_START` above is an expected classification inside the Nacos outage
scenario. It is not a green provider-readiness result. The successful recovery
assertion is the later authenticated registry restart, provider registration,
and healthy provider restart.

## Stop conditions and boundaries

- Redis ACL/keyspace isolation is not physical process or failure-domain isolation. The approved topology remains one Redis instance until the quantitative split triggers in `P1-INFRA-001` are demonstrated.
- MeiliSearch is derived. Owner tables, outbox rows, Redis stream history, PEL, and version ledgers remain the recovery authority.
- Nacos health alone is not provider recovery; the gate requires namespace/group visibility and provider metadata after restart.
- MySQL restore assertions target disposable resources only. No production database was modified.
- No external deployment, production traffic, production failover, production capacity, or production SLO evidence is claimed.
