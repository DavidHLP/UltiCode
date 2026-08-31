# P3-GRACE-001 — Graceful service drain

## Shutdown contract

All HTTP owner services set `server.shutdown=graceful` and
`spring.lifecycle.timeout-per-shutdown-phase` to the same 45-second default.
Spring stops accepting new HTTP work during context close; the Dubbo provider
lifecycle is closed with the same application context, so no new RPC work is
started after the shutdown event.

The bounded scheduling pools either use the P3-SCHED-001 shutdown policy or
Spring's `await-termination` settings. The process image declares
`STOPSIGNAL SIGTERM`. Production Compose allows 60 seconds for Java services,
30 seconds for frontend gateways and infrastructure, and local PM2 gives Java
processes a 60-second `kill_timeout`.

## Worker behavior

Redis stream bridges, Search, Judge, and durable outbox/reaper jobs use the
common process-local `DrainGate`. Their `ContextClosedEvent` handler flips the
gate before scheduler shutdown:

1. New scheduled cycles return without calling a claim/read/reclaim operation.
2. A cycle that already entered may finish its bounded batch.
3. Inbox row leases continue to heartbeat while the current handler drains.
4. A process killed before ACK or terminal CAS leaves the Redis PEL/owner row
   recoverable by the existing claim/reaper path.
5. Judge reclaimed handles discovered during the race are NACKed back to the
   PEL instead of being silently discarded.

Readiness marker files are removed when Search/Judge workers receive the close
event, so a draining worker cannot remain healthy until the stale-file timeout.
Spring-managed Redis, database, Dubbo, MeiliSearch and telemetry clients close
through the normal context lifecycle after bounded work has drained.

## Validation

```bash
bash scripts/test/graceful-drain-contract.sh
```

The contract checks every service's graceful settings, Compose/PM2/PID1
termination budgets, and runs the common `DrainGate` unit test, an Inbox no-new-
claim test, worker no-new-claim coverage, and a real child-process SIGTERM
probe. Production traffic drain, load, and orchestration authority remain
external.
