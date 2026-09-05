# ADR-0012: Core Topology Three-Way Decision

- **Status:** RETAIN_TEMPORARILY_WITH_EXPIRY
- **Date:** 2026-09-05
- **Decides:** P2-TOPOLOGY-002 / P2-TOPOLOGY-003 — whether to keep, retain-with-expiry,
  or remove the Core child-context experiment.

## Context

ADR-0010 recorded three blocking gates for Core promotion: G3 (cross-owner local
Adapter parity), G4 (in-process delegation assertion coverage), G5 (unified
HTTP/WS readiness). P0–P2 baseline work (P1-CORE-001, P1-CORE-002, P1-CORE-003)
addressed the classpath-isolation blocker and added two local adapters.

**Current state after P0–P2 baseline:**

| Gate | Status |
|---|---|
| G1: Owner startup shell / package scan | LOCAL PROOF (zero-infra) |
| G2: Per-owner DataSource / SqlSessionFactory / TxManager | LOCAL PROOF (zero-infra) |
| G3: Cross-owner local Adapter parity | PARTIAL — `CoreLocalAuthorizationMutationAdapter` + `CoreLocalIdentityQueryAdapter` only cover Auth paths; Admin→App, Submission→App/Auth, Notification→Auth/App remain uncovered |
| G4: In-process delegation assertions | PARTIAL — Auth path only; other consumers still use `@DubboReference` which fails closed in Core |
| G5: Unified HTTP/WS readiness | NOT IMPLEMENTED — child contexts have no HTTP surface; Core provides `/api/v1/core/health/ready` only |
| G6: Judge isolation | PROVEN — Core classpath excludes judge-runtime |

The enabled-owner disposable journey (P1-CORE-003) has a new
`CoreLocalIdentityQueryAdapter` + unit test, but no end-to-end integration
journey validated against real infrastructure.

## Three-Way Decision

| Option | Verdict |
|---|---|
| **PROMOTE_LATER** — flip Core to default | REJECTED. Cross-owner Adapter parity is incomplete; a promotion would create silent fail-closed paths for Admin→App, Submission↔App/Auth, Notification↔Auth. |
| **RETAIN_TEMPORARILY_WITH_EXPIRY** | SELECTED. Core provides genuine value as an isolation testbed and has proven the classloader separation. Retain with explicit scope limitations and an expiry checkpoint. |
| **REMOVE_CORE_EXPERIMENT** | REJECTED. The classloader isolation (P1-CORE-001) is a real technical contribution that resolves G5's classpath-leakage blocker; deletion would discard this. |

## RETAIN_TEMPORARILY_WITH_EXPIRY Terms

1. **Scope boundary:** Core may only enable Owner child contexts that have full
   local Adapter parity. Currently that means: **Auth + Admin only** (the two
   paths covered by `CoreLocalAuthorizationMutationAdapter` and
   `CoreLocalIdentityQueryAdapter`). App, Submission, Notification, and Search
   child contexts must remain `DISABLED` in Core until P1-CORE-002 is fully
   addressed.

2. **Expiry checkpoint:** 2026-10-06 (30 days). At that checkpoint, either:
   - P1-CORE-002 full parity is achieved → proceed to G7-GATE-001 decision
     gate, or
   - Core is demoted to `REMOVE_CORE_EXPERIMENT`.

3. **No default promotion:** Core remains behind
   `CORE_OWNER_CONTEXTS_ENABLED=false` by default in all profiles. The
   `core` Spring profile is opt-in via PM2 `ulticode-core` app descriptor.

4. **Zero-infra validation:** `bash scripts/dev/test.sh static` — zero-infra
   static gates — must continue to pass for Core. Enabled-owner integration
   requires real infrastructure (MySQL, Redis, Nacos) and is NOT zero-infra.

## Decision Record

**Distributed remains the sole default topology.** Core is explicitly an
opt-in experiment with bounded scope and a hard expiry. No new deployable
services are created by this experiment.
