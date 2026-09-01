# P1-INFRA-003 Redis Failure, Eviction & Backpressure Drill

> status: EXECUTABLE; default run BLOCKED_EXTERNAL
> evidence level: Repository Implemented + Disposable Validatable
> production evidence: NONE

## Entrypoint

`scripts/test/redis-role-fault-drill.sh` accepts only `REDIS_ROLE_DRILL_ENV_FILE`, an explicitly supplied owner-only (mode 600 or stricter) env file. It creates a unique Compose project, renders ACLs into a temporary directory, starts only the base+dev Redis service, and always executes `docker compose down -v --remove-orphans` plus temporary-file cleanup on exit.

With no authorized disposable env file the script fails closed:

```text
redis-role-fault-drill: BLOCKED_EXTERNAL (set REDIS_ROLE_DRILL_ENV_FILE to an authorized disposable env file)
```

It never falls back to `.env`, never accepts a symlink/non-owned env file, and never prints credentials.

## Exercise

The disposable run:

1. Authenticates each tested operation with its owner ACL principal.
2. Writes one entry to each owner-specific audit stream and a Judge control marker through the disposable Ops principal; seeds an App revocation key only through Ops because the App service principal has a read-only blacklist selector.
3. Starts Redis with temporary `maxmemory=4mb` and `noeviction` in a generated Compose override; the override is disposable and the Ops principal is not granted `CONFIG`.
4. Fills `userStats::drill:*` cache keys via Redis pipelining and injects `CLIENT PAUSE 250` to exercise pressure/backpressure. Cache writes may fail with OOM; that is acceptable derived-cache loss.
5. Verifies both owner audit streams and the Submission control marker survive pressure, verifies the App principal can read but cannot write its blacklist selector, and checks cross-owner rate-limit denial.
6. Reports `FAIL` if control data is evicted, a forbidden cross-owner read/write succeeds, or a required owner read fails; otherwise reports disposable `PASS`.

The script does not claim to prove production capacity or choose a physical split by itself. A hard `T-EVICT` result blocks the infrastructure Gate and requires separating cache from control/stream roles before any retry. Latency, connection, recovery, and retention triggers are defined in `P1-INFRA-001-redis-role-decision.md`.

## Expected workload outcomes

| Workload | Injected failure | Expected result | Gate consequence |
|---|---|---|---|
| cache | temporary memory pressure / eviction | cache entries may be lost; source recomputes | acceptable only when control data survives |
| streams | cache pressure + pause | stream and PEL/control remain inspectable | any stream/control loss = fail |
| replay/revocation | cache pressure | non-expired marker remains; lookup stays fail-closed | marker loss = fail |
| rate-limit | bounded pause | caller sees existing timeout/failure semantics | no silent fail-open |
| queue/judge | no normal producer added | compatibility-only; active Judge Streams remain control data | no eviction accepted for active data |
| pubsub | process pause | best-effort delivery may miss messages; no durable claim | not a stream recovery signal |

## Verification

- `rtk bash scripts/test/redis-role-fault-drill.sh` with the repository's owner-only `.env` => PASS (disposable cache pressure/backpressure; control keys preserved; no production claim).
- `rtk bash scripts/test/redis-acl-contract.sh` => PASS; scoped selectors and owner-prefixed rate-limit grants are rendered without plaintext credentials.
- `rtk bash scripts/test/admin-audit-stream-migration-contract.sh` => PASS (nonzero DB 7, bounded checkpoint resume, owner routing, old PEL empty).

The migration runbook cannot recover entries already trimmed from the legacy
stream; source outbox/checksum evidence is therefore a required precondition,
and the runbook deliberately makes no historical-completeness or production
claim.

No production Redis, remote credential, or production fault injection is used or claimed.
