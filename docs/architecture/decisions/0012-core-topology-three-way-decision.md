# ADR-0012: Core Topology Three-Way Decision

- **Status:** RETAIN_TEMPORARILY_WITH_EXPIRY
- **Date:** 2026-09-05
- **Decides:** P2-TOPOLOGY-002 / P2-TOPOLOGY-003 — whether to keep, retain-with-expiry,
  or remove the Core child-context experiment.

## Context

ADR-0010 recorded three blocking gates for Core promotion: G3 (cross-owner local
Adapter parity), G4 (in-process delegation assertion coverage), and G5
(business HTTP/WS surface). The follow-up selected **explicit assembly**:
`CoreModuleRegistry` allowlists Auth/Admin, explicit scans define each child,
and the remaining registered modules stay disabled.

`CoreOwnerClassLoaders` remains a parent-first lifecycle/TCCL helper. It is not
class/resource isolation and does not prove sibling implementation invisibility.
The Core parent exposes `/api/v1/core/health/ready` only; child contexts are
non-Web. `CoreLocalAdapterWiringTest` proves one real Admin consumer injects the
local identity contract with a mocked Auth contract. No disposable enabled-owner
boot or business journey has been run.

## Three-way decision

| Option | Verdict |
|---|---|
| **PROMOTE_LATER** — flip Core to default | REJECTED. Local parity and a Core business journey are incomplete; promotion would create fail-closed or unavailable paths. |
| **RETAIN_TEMPORARILY_WITH_EXPIRY** | SELECTED. Retain the small explicit-assembly testbed with a fixed allowlist, no isolation claim, and a hard expiry. |
| **REMOVE_CORE_EXPERIMENT** | DEFERRED. Removal remains the fallback if the bounded testbed cannot justify its maintenance cost before expiry. |

## Retain terms

1. **Scope boundary:** only Auth + Admin child contexts are enabled. App,
   Submission, Notification, and Search remain `DISABLED`.
2. **Transport boundary:** Admin→Auth local adapters are explicit; uncovered
   cross-owner consumers remain on distributed Dubbo and are not silently
   treated as Core-compatible.
3. **Expiry checkpoint:** **2026-10-06**. At that checkpoint, either a new
   decision records evidence-backed continuation, or Core is removed. A status
   edit cannot renew the date.
4. **No default promotion:** generic Core owner-context and Judge-required
   properties default to `false`; only the named `core` scope opts in.
5. **Validation boundary:** `test.sh core` proves parent/config/readiness only.
   Enabled-owner wiring needs disposable Owner artifacts/infra; a Core business
   journey is unavailable until a business HTTP/WS seam exists.

## Decision record

**Distributed remains the sole default topology.** Core is an explicitly
opt-in, bounded assembly testbed with a non-renewing expiry. No new deployable
services are created and no production suitability is claimed.
