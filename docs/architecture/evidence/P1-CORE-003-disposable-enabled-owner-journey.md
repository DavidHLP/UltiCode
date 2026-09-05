# P1-CORE-003: Core Enabled-Owner Disposable Journey

## Journey Scope

**Goal:** Verify that two enabled Owner child contexts in Core can cooperate in-process
without Dubbo, exercising the mutation path (Admin → Auth) and the read path
(Auth → Admin identity resolution).

**Owners enabled:** `auth` + `admin`
**Cross-owner flows exercised:**
1. Admin calls Auth mutation via `CoreLocalAuthorizationMutationAdapter` (delegates through
   `BackendAuthorizationMutationPort` in the Auth child)
2. Admin reads Auth identity via `IdentityQueryService` — **requires a local adapter**
   since `dubbo.enabled=false` in child contexts

**Owners NOT enabled:** `app`, `submission`, `notification`, `search`
Rationale: App/Submission/Notification require their own local adapters (see
P1-CORE-002) which are out of scope for the minimal disposable journey. Search
is database-free and has no cross-owner deps.

## Required Additions

| Component | Purpose | Location |
|---|---|---|
| `CoreLocalIdentityQueryAdapter` | Local adapter implementing `IdentityQueryService` by delegating to the Auth child context's bean via `CoreOwnerContextManager.bean("auth", ...)` | `services/core/src/main/java/com/ulticode/core/` |

## Cost Budget

| Item | Estimated |
|---|---|
| New source files | 1 Java class + 1 test |
| New @Import wiring | 1 `@Import` in `CoreOwnerBootConfigurations.Admin` |
| New test | 1 smoke test asserting Auth identity resolution from Admin context |
| Risk surface | Low — mirrors existing `CoreLocalAuthorizationMutationAdapter` pattern |

## Expiry

This disposable journey is a **validation-only artifact**. It must be removed
(or the Core profile disabled) before any production deployment of Core.
Expiry condition: P7-GATE-001 decision gate — if Core is decided REMOVABLE,
all Core-local adapters (`CoreLocalAuthorizationMutationAdapter`,
`CoreLocalIdentityQueryAdapter`) and the enabled-owner tests are removed.

## Validation Steps

1. `bash scripts/dev/test.sh static` — static contract gate passes
2. `bash scripts/dev/test.sh core` — Core smoke passes with `CORE_OWNER_CONTEXTS_ENABLED=true`
3. Journey test: enable auth+admin contexts, verify mutation + identity read paths
4. `bash scripts/test/core-profile-contract.sh` — isolation contract preserved
