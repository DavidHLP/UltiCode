# P1-CORE-002: Core Local/Dubbo Adapter Parity Matrix

## Objective

Determine which cross-owner adapters Core needs as local (in-process) equivalents,
since `dubbo.enabled=false` in all child contexts disables `@DubboReference`.

## Current State (2026-09-05)

Core parent implements three Auth-contract local adapters and explicitly registers
them into the Admin child during startup:

- `CoreLocalIdentityQueryAdapter` (implements Auth `IdentityQueryService`)
- `CoreLocalAuthorizationMutationAdapter` (implements Auth `AuthorizationMutationService`)
- `CoreLocalAccountQueryAdapter` (implements Auth `AccountQueryService`)

Registration happens in `CoreOwnerContextManager.registerChildContracts`
for the Admin child only.

## Consumer → Local Seam Coverage (Admin)

| Consumer (Admin) | Contract | Local seam in Core | Status |
|---|---|---|---|
| `AccountReadAdapter.identityQueryService` | Auth `IdentityQueryService` | `CoreLocalIdentityQueryAdapter` | **covered** — `CoreLocalAdapterWiringTest` resolves a real Admin consumer against the registered local contract (mock Auth provider) |
| `UserPermissionServiceImpl.performPermissionChange` → `authorizationMutationService.mutatePermission` | Auth `AuthorizationMutationService` | `CoreLocalAuthorizationMutationAdapter` | **covered (unit) / wiring proven** — `CoreLocalAdapterWiringTest.adminPermissionMutationSucceedsThroughLocalAccountQueryAndMutationSeams` drives the real `UserPermissionServiceImpl` through both registered local seams and asserts the returned `AuthorizationMutationDTO` |
| `UserPermissionServiceImpl.performPermissionChange` → `requireAccount(id)` → `accountQueryService` | Auth `AccountQueryService` | `CoreLocalAccountQueryAdapter` | **covered** — same wiring test proves the legal grant path resolves `requireAccount(id)` through the registered local seam (mock Auth provider) |
| `DefaultAdminAnalyticsPortAdapter.queryAccounts` | Auth `AccountQueryService` | `CoreLocalAccountQueryAdapter` | **covered (delegation)** — full contract is delegated; the Admin child injects this contract into analytics, dashboard, provisioning, enricher and user-management consumers as well, so a partial implementation would turn their calls into runtime 500s |
| `DefaultAdminDashboardReadAdapter` / `UserProvisioningAdapter` / `AdminUserEnricher` / `UserManagementServiceImpl` | Auth `AccountQueryService` | `CoreLocalAccountQueryAdapter` | **covered (delegation)** — same full-contract seam |

The Admin permission mutation legal path requires **two** Auth contracts
(`AuthorizationMutationService` plus `AccountQueryService` for `requireAccount`).
Both are now registered; the wiring test proves a legal grant succeeds and
(missing signer) stays fail-closed.

## Remaining Cross-Owner Consumers (not enabled in Core)

| Owner Module | `@DubboReference` cross-owner consumers | Local adapter parity |
|---|---|---|
| **Auth** | none (Auth is a provider only) | — |
| **Admin** | `UserProfilePort` → App, `IdentityQueryService` → Auth | App profile: **uncovered**; Auth identity read: **covered** (see above); Auth permission mutation: **wiring proven** (see above) |
| **App** | submission/controller, submission/event, submission/port, submission/outbox | All within-App: no cross-owner gap |
| **Submission** | `ProblemFactsPort`/`ProblemTitleLookupPort`/`SubmissionUserReadPort` → App; `IdentityQueryService` → Auth | App problem/title/user: **uncovered**; Auth identity: **uncovered** |
| **Notification** | `UserNotificationReadPort` → App; `IdentityQueryService`/`NotificationRecipientQueryService` → Auth | App notification read: **uncovered**; Auth identity: **uncovered** |
| **Search** | none | N/A — no persistence, no RPC |

## Missing Local Adapters (Required for Enabled-Owner Journey)

| Adapter Needed | Interface | Provider Owner | Consumer Owner | Failure Mode in Core (no adapter) |
|---|---|---|---|---|
| `UserProfilePort` local impl | `UserProfilePort` | App | Admin | Admin profile writes fail closed |
| `IdentityQueryService` local impl for other children | `IdentityQueryService` | Auth | Submission, Notification | Identity resolution fails closed |
| `ProblemFactsPort` local impl | `ProblemFactsPort` | App | Submission | Problem facts resolution fails closed |
| `ProblemTitleLookupPort` local impl | `ProblemTitleLookupPort` | App | Submission | Title lookup fails closed |
| `SubmissionUserReadPort` local impl | `SubmissionUserReadPort` | App | Submission | User data fails closed |
| `UserExistencePort` local impl | `UserExistencePort` | App | Submission | User existence check fails closed |
| `UserNotificationReadPort` local impl | `UserNotificationReadPort` | App | Notification | Notification recipient resolution fails closed |
| `NotificationRecipientQueryService` local impl | `NotificationRecipientQueryService` | Auth | Notification | Recipient identity fails closed |

## Decision

- The Admin identity **read** path is proven by a wiring test.
- The Admin permission **mutation** path is now proven through the real service:
  both required Auth local seams (`AuthorizationMutationService` +
  `AccountQueryService`) are registered and a wiring test drives a legal grant to a
  successful `AuthorizationMutationDTO`, while a missing signer stays fail-closed.
- The disposable enabled-owner journey (P1-CORE-003) remains deferred; the
  mutation `requireAccount` path is now covered by wiring evidence, but real Auth
  provider boot and the business HTTP/WS journey are still not run.
- Submission/Notification/App adapters remain out of scope while those Owners
  stay disabled.

## Evidence

- `CoreOwnerContextManager.registerChildContracts` —
  `services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java:364-383`
- `CoreLocalAccountQueryAdapter` (full `AccountQueryService` delegation) —
  `services/core/src/main/java/com/ulticode/core/CoreLocalAccountQueryAdapter.java`
- `CoreLocalAdapterWiringTest` (3 tests: identity contract; legal grant through
  both local seams; fail-closed without signer) —
  `services/core/src/test/java/com/ulticode/core/CoreLocalAdapterWiringTest.java`
- `UserPermissionServiceImpl.performPermissionChange` null-checks both
  `authorizationMutationService` and `accountQueryService` and calls
  `requireAccount(id)` (`accountQueryService.getAccountById`) —
  `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/UserPermissionServiceImpl.java:84-89,130-133`
- `dubbo.enabled=false` in `CoreOwnerContextManager.start()` (line ~322)
- `CoreLocalIdentityQueryAdapter` / `CoreLocalAuthorizationMutationAdapter` at
  `services/core/src/main/java/com/ulticode/core/`
