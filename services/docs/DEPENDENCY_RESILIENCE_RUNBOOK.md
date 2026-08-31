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
