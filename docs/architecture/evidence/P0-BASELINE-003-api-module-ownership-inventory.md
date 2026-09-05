# P0-BASELINE-003 API Module and Contract Ownership Inventory

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> scope: all `services/api/*` contract modules
> evidence: Repository Implemented

## 1. Reactor API modules

```
api/auth-api (backend-auth-api)
api/submission-api (backend-submission-api)
api/notification-api (backend-notification-api)
api/app-api (backend-app-api)
api/judge-api (backend-judge-api)
```

No `api/admin-api` module exists (confirmed: `services/api/admin-api` absent
from filesystem and git).

## 2. app-api public interface inventory

**Source**: `services/api/app-api/src/main/java` — **75 public interface
declarations** across `com.ulticode.app.api.service` and
`com.ulticode.app.api.dto` packages. (Baseline P0 graph reported 78 labels;
3 were stale graph records deleted by P2-APP-003. P2-APP-001 catalog records
71 current exports after 4 internalizations.)

### Dependency boundary

`services/api/app-api/pom.xml` depends only on:
- `backend-common` (platform/shared contract)
- `backend-submission-api` (Submission result payload seam)
- `lombok` (provided), `jakarta.validation-api`, `swagger-annotations`

`BackendAppApiArchTest.java` enforces: no MyBatis, no Spring beans, no Spring
Security, no Entity/Mapper/ServiceImpl/Repository classes, and the contract
module may only depend on `com.ulticode.app.api..`, `com.ulticode.common..`,
`com.ulticode.submission.api..`, `com.ulticode.domain..`,
`java..`, `javax..`, `jakarta..`, `lombok..`,
`com.fasterxml.jackson.annotation..`, `io.swagger.v3.oas.annotations..`.

**Confirmed**: Zero production imports of `com.ulticode.submission.api.*`
in app-api main sources. The Maven dependency on `backend-submission-api`
exists but is unused at source level — the `BackendAppApiArchTest` allowlist
and `BackendAppApiContractShapeTest` permit it but no class currently
references it.

### Misplaced interfaces (already internalized by P2-APP-003)

| Interface | Former location | New location | Retirement evidence |
|---|---|---|---|
| JudgeConfigPort | app-api | judge-runtime | P2-APP-001 §"Retired by P2-APP-003" |
| JudgeEnqueuePort | app-api | judge-runtime | same |
| VerdictResolvePort | app-api | judge-runtime | same |
| ModerationUserReadPort | app-api | app-web | same |
| JudgeFeatureFlagsPort | app-api | judge-runtime | same |
| JudgingLanguageSupport | app-api | judge-runtime | same |

### Interfaces flagged for ownership correction in this plan

| Interface | Current owner (per app-api) | Consumer | Transport | Problem |
|---|---|---|---|---|
| `UserNotificationReadPort` | App (app-api) | Notification (`DefaultAnnouncementBroadcaster`, `EmailNotificationChannel`) | in-process `@Component` | Owned by Auth; provider is `DubboUserNotificationReadAdapter` in Notification calling Auth `NotificationRecipientQueryService` + `IdentityQueryService` |
| `NotificationRecipientDTO` | App (app-api.dto) | Notification (same consumers) | in-process | Companion DTO to `UserNotificationReadPort`; same ownership mismatch |
| `ContestSubmissionPort` | App (app-api) | Submission (`DefaultSubmissionWritePort`, `SubmissionJudgedAchievementConsumer`) | in-process | `recordSubmissionIfNeeded` has zero production callers — dead method |
| `SubmissionUserReadPort` | App (app-api) | Submission (`DefaultSubmissionProjection`), App (`DefaultContestProjection`) | Dubbo (App→Submission) | Correctly placed — App owns user profile facts, Submission consumes |

### app-api dependency on backend-submission-api

`BackendAppApiArchTest.java:124` allows `com.ulticode.submission.api..`.
Source-level grep finds **zero** imports of `com.ulticode.submission.api.*`
in `services/api/app-api/src/main/java`. The Maven dependency is declared
but unused — candidate for removal once the arch-test is updated.

## 3. Auth-owned recipient contract (correct home)

`services/api/auth-api/`:
- `NotificationRecipientQueryService` — provider-owned remote contract,
  returns `RpcResult<List<AuthNotificationRecipientDTO>>`
- `IdentityQueryService` — provider-owned identity contract, returns
  `RpcResult<UserIdentityDTO>`
- `AuthNotificationRecipientDTO` — `accountId, email, active, banned` (4 fields)

`NotificationRecipientApiContractShapeTest.java` locks the Auth DTO shape:
exactly `accountId, email, active, banned`.

## 4. Submission contract

`services/api/submission-api/` (51 files): `SubmissionIntakePort`,
`SubmissionReadPort`, `SubmissionAdministrationService`, plus DTOs,
commands, events, enums. All in `com.ulticode.submission.api.*`.

`BackendSubmissionApiArchTest` (if present) enforces implementation-free
contract module constraints, mirroring the app-api pattern.

## 5. Notification contract

`services/api/notification-api/`: `NotificationIntentEventContract`,
notification DTOs/commands. No `UserNotificationReadPort` here currently.

## 6. Consumer mapping (source-anchored)

| Interface | Interface location | Provider | Consumer file | Transport |
|---|---|---|---|---|
| UserNotificationReadPort | `com.ulticode.app.api.service` | `DubboUserNotificationReadAdapter` (Notification) | `DefaultAnnouncementBroadcaster`, `EmailNotificationChannel` | in-process `@Component` |
| ContestSubmissionPort | `com.ulticode.app.api.service` | `ContestSubmissionAdapter` (App), `NoopContestSubmissionPort` (Submission) | `DefaultSubmissionWritePort` (lines 212, 255), `SubmissionJudgedAchievementConsumer` (line 43) | in-process |
| SubmissionUserReadPort | `com.ulticode.app.api.service` | `SubmissionUserReadProvider` (App) | `DefaultSubmissionProjection`, `DefaultContestProjection`, `DefaultSubmissionUserReadAdapter` | Dubbo group=backend-app |

## 7. Evidence Level

Repository Implemented + Disposable Validatable. All facts are source-anchored
via direct reads and `grep -rn` consumer tracing. No production traffic claim.

## Verification

- `grep -rn "UserNotificationReadPort" services/` — confirms in-process
  consumption only (no `@DubboService`, no `@DubboReference`)
- `grep -rn "recordSubmissionIfNeeded" services/` — confirms test-only
  callers in ContestSubmissionAdapterTest
- `find services/api/app-api -name "*.java" | wc -l` = 153 (source file count)
- `grep -rn "import com.ulticode.submission.api" services/api/app-api/src/main/java` = 0 (no source usage)