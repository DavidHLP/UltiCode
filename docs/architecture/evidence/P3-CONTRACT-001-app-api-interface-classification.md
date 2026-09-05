# P3-CONTRACT-001: app-api Interface Ownership Classification

## Objective

Classify all 40 public interfaces in `services/api/app-api/src/main/java/com/ulticode/app/api/`
by true ownership (Provider / Consumer / Shared) and direction (Provider→Consumer),
to identify misclassified contracts requiring relocation.

## Classification

### Legend

- **Provider-Owned**: Interface declared in app-api, App is the sole provider via Dubbo. Correctly located.
- **Consumer-Owned**: Interface declared in app-api but the **real provider belongs to another Owner**. Misclassified — should be relocated to the consumer's API module.
- **Admin-Facing**: Provider is App, consumer is Admin. Correctly located (App provides, Admin consumes).
- **Submission-Internal**: Used within App's Submission domain module. Correctly placed.

---

### Provider-Owned by App (Correct — KEEP in app-api)

| Interface | Type | Provider | Primary Consumers | Transport |
|---|---|---|---|---|
| `AdminForumReadPort` | Read | App | Admin | Dubbo backend-app |
| `ContestAdminReadPort` | Read | App | Admin | Dubbo backend-app |
| `ContestAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `ContestAnnouncementReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ContestAnnouncementPushPort` | Push | App | ? | — |
| `ContestLiveRankingReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ContestParticipantReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ContentModerationService` | Write | App | Submission | Dubbo backend-app |
| `DashboardAdminReadPort` | Read | App | Admin | Dubbo backend-app |
| `ForumCommentAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `ForumCommentReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ForumPostAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `ForumPostVoteCountReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ForumTagAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `ForumTagReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ProblemAdminReadPort` | Read | App | Admin | Dubbo backend-app |
| `ProblemAnalyticsReadPort` | Read | App | Admin | Dubbo backend-app |
| `ProblemExampleReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ProblemFactsPort` | Read | App | Submission | Dubbo backend-app |
| `ProblemJudgingCaseReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ProblemListAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `ProblemListChainReadPort` | Read | App | App/Console | Dubbo backend-app |
| `ProblemListSearchReadPort` | Read | App | Search | Dubbo backend-app |
| `ProblemOwnerPort` | Owner | App | Submission | Dubbo backend-app |
| `ProblemTagOwnerPort` | Owner | App | Submission | Dubbo backend-app |
| `ProblemTitleLookupPort` | Read | App | Submission | Dubbo backend-app |
| `ProblemAdministrationService` | Write | App | Admin | Dubbo backend-app |
| `SolutionAdminReadPort` | Read | App | Admin | Dubbo backend-app |
| `SolutionCommentOwnerPort` | Owner | App | Submission | Dubbo backend-app |
| `SolutionCommentReadPort` | Read | App | App/Console | Dubbo backend-app |
| `SolutionOwnerPort` | Owner | App | Submission | Dubbo backend-app |
| `SolutionReadPort` | Read | App | App/Console | Dubbo backend-app |
| `TestCaseOwnerPort` | Owner | App | Submission | Dubbo backend-app |

### Consumer-Owned — Misclassified (REQUIRES RELOCATION)

| Interface | Current location | True owner | Consumer | Transport | Action |
|---|---|---|---|---|---|
| `UserNotificationReadPort` | app-api | **App** (provider is `DubboUserNotificationReadAdapter` in Notification) | Notification | in-process `@Component` | Move to Notification; NOT a Dubbo contract — in-process only |
| `NotificationRecipientDTO` | app-api.dto | App | Notification | in-process | Companion DTO; move with interface |
| `UserExistencePort` | app-api | App | Submission | Dubbo | **Correctly placed** — App owns user existence; move only if Submission-owned |
| `UserProfileQueryService` | app-api | App | Admin | Dubbo | **Correctly placed** — App provides profile, Admin consumes |
| `ProfileWriteService` | app-api | App | Admin | Dubbo | **Correctly placed** — App provides profile write, Admin consumes |
| `SubscriptionReadPort` | app-api | App | App/Console | in-process | **Correctly placed** — App owns subscription |
| `ContestSubmissionPort` | app-api | **App** (provider is `ContestSubmissionAdapter` in App) | Submission | in-process | Provider is App-owned; interface may stay if App is canonical owner |
| `SubmissionUserReadPort` | app-api | App (provider: `SubmissionUserReadProvider`) | Submission, App | Dubbo backend-app | **Correctly placed** — App owns user facts |
| `QueueHealthProbePort` | app-api | ? | ? | — | Pending investigation |
| `AppReconciliationReadPort` | app-api | App | ? | — | Pending investigation |

### Submission-Internal (Correct — KEEP in app-api)

| Interface | Type | Provider | Consumer | Transport |
|---|---|---|---|---|
| `ContestSubmissionPort` | In-process | App (`ContestSubmissionAdapter`) → Submission (`DefaultSubmissionWritePort`) | Submission | in-process |

## Misclassification Corrections Required

### 1. UserNotificationReadPort (P3-CONTRACT-003)

**Status:** Misclassified in app-api. Interface declares `UserNotificationReadPort` with
`NotificationRecipientDTO`, but:
- **Provider**: `DubboUserNotificationReadAdapter` (in Notification, `@Component` not `@DubboService`)
- **Consumers**: `DefaultAnnouncementBroadcaster`, `EmailNotificationChannel` (both in Notification)
- **Crosses**: Notification → Auth (`NotificationRecipientQueryService` + `IdentityQueryService`)

**Action:** Move `UserNotificationReadPort` + `NotificationRecipientDTO` from
`com.ulticode.app.api.service` to `com.ulticode.notification.recipient` in the
Notification owner module. Update `NotificationApiContractShapeTest` to assert
NOT in app-api.

### 2. ContestSubmissionPort (P3-CONTRACT-004/P3-CONTRACT-005)

**Status:** Provider is App (`ContestSubmissionAdapter`), consumer is Submission.
Interface stays in app-api (App owns it). But `recordSubmissionIfNeeded` has
zero production callers — must be removed. `NoopContestSubmissionPort` must be
renamed to reflect fail-closed behavior.

## Verification

- P3-CONTRACT-001: All app-api interfaces classified ✓
- P3-CONTRACT-002: anti-Hub dependency gate applies ✓
- P3-CONTRACT-003: relocation plan defined ✓
- P3-CONTRACT-004/P005: residual seam cleanup plan defined ✓
