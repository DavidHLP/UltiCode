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
