# P7-GATE-001: Core Load / Parity / Journey / Topology Decision Gate

## Gate Verdict: CONDITIONAL PASS (no promotion)

Date: 2026-09-05

## Criteria evaluation

| Criterion | Evidence source | Status |
|---|---|---|
| **L1: Core parent context loads (zero-infra)** | `CoreApplicationSmokeTest`, `core-profile-contract.sh`, `test.sh core` | PASS when the recorded parent smoke commands pass |
| **L2: Class/resource isolation** | Explicit assembly decision; `CoreOwnerClassLoaders` is parent-first lifecycle/TCCL support | NOT_APPLICABLE — no class/resource isolation claim |
| **L3: Cross-owner local Adapter parity** | `CoreLocalAdapterWiringTest`, `CoreLocalAuthorizationMutationAdapter`, `CoreLocalIdentityQueryAdapter` | PARTIAL — Admin→Auth only; other paths remain distributed Dubbo |
| **L4: Enabled-owner journey** | P1-CORE-003 and current Core route surface | NOT_RUN / UNAVAILABLE — Core exposes readiness only |
| **L5: Distributed remains sole default** | DevStack resolver, PM2 descriptor, Spring defaults, ADR-0012 | PASS |

## Gate outcome

**Decision:** `RETAIN_TEMPORARILY_WITH_EXPIRY`

- Core remains an explicitly opt-in assembly testbed.
- Registry-enabled scope is **Auth + Admin**; App, Submission, Notification, and
  Search remain registered but disabled.
- Generic Core owner-context and Judge-required defaults are `false`; the named
  `core` scope is the only local opt-in.
- Expiry checkpoint: **2026-10-06**; no automatic renewal.
- Missing L3/L4 evidence blocks promotion. Distributed remains the only default
  and usable business topology.

## Rollback points

- Set `CORE_OWNER_CONTEXTS_ENABLED=false` in the PM2 descriptor.
- Stop using the named `core` scope and keep distributed scopes unchanged.
- If the experiment is removed, delete Core-only registry/context/adapter/tests,
  the Core PM2/DevStack entries, and the Core evidence links; do not restore
  retired contracts or Submission dual writers.
