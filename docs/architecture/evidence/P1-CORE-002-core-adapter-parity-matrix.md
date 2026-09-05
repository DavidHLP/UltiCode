# P1-CORE-002: Core Local/Dubbo Adapter Parity Matrix

## Objective

Determine which cross-owner adapters Core needs as local (in-process) equivalents,
since `dubbo.enabled=false` in all child contexts disables `@DubboReference`.

## Current State

| Owner Module | `@Import` local adapters (core/main) | `@DubboReference` cross-owner consumers | Local adapter parity |
|---|---|---|---|
| **Auth** | none | Consumer via `AuthorizationMutationService` (used by Admin) | — |
| **Admin** | none (relies on `CoreLocalAuthorizationMutationAdapter` in Core) | `UserProfilePort` → App, `IdentityQueryService` → Auth | Auth mutation: **covered** (`CoreLocalAuthorizationMutationAdapter`); App profile: **uncovered** |
| **App** | `MapperScanConfig` | submission/controller, submission/event, submission/port, submission/outbox | All within-App: no cross-owner gap |
| **Submission** | `DefaultSubmissionFencePort`, `DefaultSubmissionWritePort` | `ProblemFactsPort`/`ProblemTitleLookupPort`/`SubmissionUserReadPort` → App (via `@DubboReference`); `IdentityQueryService` → Auth | App problem/title/user: **uncovered**; Auth identity: **uncovered** |
| **Notification** | `DefaultNotificationAdminReadAdapter` | `UserNotificationReadPort` → App; `IdentityQueryService`/`NotificationRecipientQueryService` → Auth | App notification read: **uncovered**; Auth identity: **uncovered** |
| **Search** | none | Database-free, no cross-owner deps | N/A — no persistence, no RPC |

## Local Adapters Already Present in Core

| Adapter Class | Interface | Direction | Used By |
|---|---|---|---|
| `CoreLocalAuthorizationMutationAdapter` | `AuthorizationMutationService` | Core → Auth (Admin calls Auth mutation) | `AuthorizationMutationService` consumers in Admin child |

## Missing Local Adapters (Required for Enabled-Owner Journey)

| Adapter Needed | Interface | Provider Owner | Consumer Owner | Failure Mode in Core (no adapter) |
|---|---|---|---|---|
| `UserProfilePort` local impl | `UserProfilePort` | App | Admin | Admin profile writes fail closed |
| `IdentityQueryService` local impl | `IdentityQueryService` | Auth | Admin, Submission, Notification | Identity resolution fails closed |
| `ProblemFactsPort` local impl | `ProblemFactsPort` | App | Submission | Problem facts resolution fails closed |
| `ProblemTitleLookupPort` local impl | `ProblemTitleLookupPort` | App | Submission | Title lookup fails closed |
| `SubmissionUserReadPort` local impl | `SubmissionUserReadPort` | App | Submission (App child imports `submission/user` package) | User data fails closed |
| `UserExistencePort` local impl | `UserExistencePort` | App | Submission | User existence check fails closed |
| `UserNotificationReadPort` local impl | `UserNotificationReadPort` | App | Notification | Notification recipient resolution fails closed |
| `NotificationRecipientQueryService` local impl | `NotificationRecipientQueryService` | Auth | Notification | Recipient identity fails closed |

## Decision

Most of the missing adapters are **NOT required** for the disposable enabled-owner
journey (P1-CORE-003) because:

1. **`IdentityQueryService`** is the most critical — it's consumed by Admin, Submission,
   and Notification. A local adapter resolving identity within the Core context
   (bypassing the CoreLocalAuthorizationMutationAdapter's delegation path) would
   be needed for any journey involving identity.

2. **`UserProfilePort`** is needed only if Admin profile writes are exercised.

3. **Submission → App adapters** are needed only if submission intake is exercised.

4. **Notification → App/Auth adapters** are needed only if notification dispatch
   is exercised.

The disposable journey will be scoped to **identity resolution + mutation only**,
requiring only a local `IdentityQueryService` adapter. This matches the one
existing local adapter (`CoreLocalAuthorizationMutationAdapter`) and adds one
sibling for the read path.

## Evidence

- `@DubboReference` consumers: found in `services/admin/src/main/java/.../AccountReadAdapter.java`,
  `services/notification/src/main/java/.../DubboUserNotificationReadAdapter.java`,
  `services/submission/src/main/java/.../ProblemFactsDubboAdapter.java`,
  `services/submission/src/main/java/.../ProblemTitleLookupDubboAdapter.java`,
  `services/submission/src/main/java/.../SubmissionUserReadDubboAdapter.java`,
  `services/submission/src/main/java/.../UserExistenceDubboAdapter.java`
- `dubbo.enabled=false` in `CoreOwnerContextManager.start()` at line 256
- `@Import` statements in `CoreOwnerBootConfigurations.java`: lines 94, 114, 139
- `CoreLocalAuthorizationMutationAdapter` at `services/core/src/main/java/com/ulticode/core/`
