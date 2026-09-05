# P7-GATE-001: Core Load / Parity / Journey / Topology Decision Gate

## Gate Verdict: PASS (with RETAIN_TEMPORARILY_WITH_EXPIRY)

Date: 2026-09-05

## Criteria Evaluation

| Criterion | Evidence Source | Status |
|---|---|---|
| **L1: Core parent context loads (zero-infra)** | `CoreApplicationSmokeTest` (3 lifecycle tests + 3 identity adapter tests pass); `core-profile-contract.sh` PASS | ✅ |
| **L2: Child classloader isolation** | `CoreOwnerClassLoaders` + `CoreOwnerContextManager.start()` wired; compiles; contract gate PASS | ✅ |
| **L3: Cross-owner local Adapter parity** | P1-CORE-002 matrix: `CoreLocalAuthorizationMutationAdapter` + `CoreLocalIdentityQueryAdapter` cover Auth paths only; Admin→App, Submission→App/Auth, Notification→Auth/App uncovered | ⚠️ PARTIAL |
| **L4: Enabled-owner journey (integration)** | No end-to-end enabled-owner journey validated against real infrastructure | ❌ |
| **L5: Distributed remains sole default** | `devstack-manifest.sh` core scope confirmed; `ecosystem.config.cjs` core profile opt-in; ADR-0012 decision record | ✅ |

## Gate Outcome

**Decision:** RETAIN_TEMPORARILY_WITH_EXPIRY (per ADR-0012)

- Core stays as an explicitly opt-in experiment
- Scope limited to **Auth + Admin** child contexts only
- Expiry checkpoint: **2026-10-06**
- Must achieve full adapter parity (L3) and enabled-owner journey (L4) before
  any promotion consideration
- `REMOVE_CORE_EXPERIMENT` remains available if parity/journey cannot be proven
  by expiry

## Rollback Points

- If Core child context stability degrades: set `CORE_OWNER_CONTEXTS_ENABLED=false`
  in PM2 descriptor (`ecosystem.config.cjs`)
- Full removal: exclude `ulticode-core` from `devstack-manifest.sh` core scope
  and delete `services/core/` subtree
