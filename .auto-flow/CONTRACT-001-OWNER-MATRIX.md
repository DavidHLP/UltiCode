# CONTRACT-001 — app-api owner and release boundary matrix

Status: accepted execution baseline for CONTRACT-001-COMMON/002/003 (2026-08-17)

This matrix is the authoritative inventory for `services/api/app-api/src/main/java/com/ulticode/app/api`.
It was built from the current source tree, the `ulticode` codebase-memory graph, provider/consumer searches, the
`app-api` POM and the migration guide. A type is listed once by its current simple name. Nested types are listed
after their enclosing type and inherit the enclosing owner unless explicitly marked otherwise.

## Frozen artifacts and release boundary

| Owner | Artifact/module | Package root | Dubbo identity | Release rule |
| --- | --- | --- | --- | --- |
| App | `backend-app-api` / `services/api/app-api` | `com.ulticode.app.api` | Existing App identities remain unchanged | App-owned contracts may remain in this artifact; only the exceptions below may be consumed by other owners. |
| Submission | `backend-submission-api` / `services/api/submission-api` | `com.ulticode.submission.api` | group `backend-submission`, version `1.0.0` | Provider and every consumer/test fixture move as one matched release. No mixed old/new FQCN rollout, alias, re-export, or second group. |
| Notification | `backend-notification-api` / `services/api/notification-api` | `com.ulticode.notification.api` | group `backend-notification`, version `1.0.0` | Provider and every consumer/test fixture move as one matched release. No mixed old/new FQCN rollout, alias, re-export, or second group. |
| Auth-backed local security | `backend-common`/web-security seam (target local package, not a Dubbo provider) | `com.ulticode.common...` | None | `AccountReadPort`/`JwtValidationPort` and the credential-free `DelegationAssertionContract` are local security seams; `AccountInfo`/`JwtPayload` carry no credentials and are not App business contracts. |
| Common contract metadata | `backend-common` | `com.ulticode.common...` | None | `ActorDelegation`, `WriteCommand`, and the generic difficulty/count value shape are implementation-free shared metadata/value contracts. |
| Judge runtime internal | `services/judge-runtime` private package | runtime-owned package | None | Queue/sandbox/execution DTOs and ports do not become a business API. The current app-api copies are removed only with the bounded runtime migration. |

The existing `backend-auth-api` and `backend-admin-api` remain provider-owned precedents. No type in this inventory is
Admin-owned: Admin is a consumer of App, Submission, Notification, or Auth contracts.

FQCN relocation is a matched contract release, not an N/N-1 rolling compatibility change. A deployment that cannot
release provider and consumers together is blocked; the old FQCN is not kept as a permanent bridge.

## Service inventory

Source accounting: 219 top-level Java files (service 93, command 31, DTO 87, event 6, error 1, security 1), plus 66
nested types. The following lists cover all 219 top-level names and all nested declarations.

### App-owned services

`AchievementBadgeReadPort`, `AchievementTriggerPort`, `AdminForumReadPort`, `AppReconciliationReadPort`,
`BookmarkReadPort`, `ContentModerationService`, `ContestAchievementPort`, `ContestAdminReadPort`,
`ContestAdministrationService`, `ContestAnnouncementPushPort`, `ContestAnnouncementReadPort`,
`ContestLiveRankingReadPort`, `ContestNotificationPort`, `ContestParticipantReadPort`, `ContestRankingMarkDirtyPort`,
`ContestStatusPushPort`, `ContestSubmissionPort`, `ContestSubscriptionPolicy`, `FollowCountPort`,
`ForumCommentAdministrationService`, `ForumCommentOwnerPort`, `ForumCommentReadPort`, `ForumOwnerPort`,
`ForumPostAdministrationService`, `ForumPostReadPort`, `ForumPostVoteCountReadPort`, `ForumTagAdministrationService`,
`ForumTagReadPort`, `ForumVoteReadPort`, `ModerationAccountPort`, `ModerationContentActionPort`,
`ModerationUserReadPort`, `ProblemAdminReadPort`, `ProblemAdministrationService`, `ProblemAnalyticsReadPort`,
`ProblemDifficultyReadPort`, `ProblemExampleReadPort`, `ProblemExistencePort`, `ProblemFactsPort`,
`ProblemInteractionQueryPort`, `ProblemJudgingCaseReadPort`, `ProblemListAdministrationService`,
`ProblemListChainReadPort`, `ProblemListReadPort`, `ProblemListSearchReadPort`, `ProblemOwnerPort`,
`ProblemSearchReadPort`, `ProblemTagOwnerPort`, `ProblemTagReadPort`, `ProblemTagStatsReadPort`,
`ProfileWriteService`, `SolutionAdminReadPort`, `SolutionCommentOwnerPort`, `SolutionCommentReadPort`,
`SolutionOwnerPort`, `SolutionReadPort`, `SolutionVoteReadPort`, `SubscriptionReadPort`, `TestCaseOwnerPort`,
`UserProfileQueryService`, `UserReadPort`, `UserSearchReadPort`.

Nested App service types: `ContestParticipantReadPort.ParticipantInfo`,
`ContestSubscriptionPolicy.ContestSubscribeRequest`, `ContestSubscriptionPolicy.Verdict`,
`ContestSubscriptionPolicy.SubscriptionDecision`, `ForumCommentOwnerPort.FlagResult`,
`ForumCommentOwnerPort.DeleteResult`, `ForumCommentReadPort.ForumCommentRow`,
`ForumCommentReadPort.ForumCommentPage`, `ForumOwnerPort.FlagResult`, `ForumOwnerPort.ToggleResult`,
`ForumOwnerPort.DeleteResult`, `ProblemFactsPort.ProblemDisplayFacts`,
`ProblemFactsPort.ProblemLimits`, `ProblemFactsPort.ContestProblemFacts`, `ProblemOwnerPort.ImportWriteRequest`,
`ProblemOwnerPort.ImportWriteResult`, `ProblemTagOwnerPort.TagWrite`, `SolutionAdminReadPort.SolutionAdminRow`,
`SolutionAdminReadPort.SolutionAdminPage`, `SolutionAdminReadPort.SolutionAdminQuery`,
`SolutionCommentOwnerPort.FlagResult`, `SolutionCommentOwnerPort.DeleteResult`,
`SolutionCommentReadPort.SolutionCommentRow`, `SolutionCommentReadPort.SolutionCommentPage`,
`SolutionOwnerPort.FlagResult`, `SolutionOwnerPort.DeleteResult`, `TestCaseOwnerPort.TestCaseOrder`,
`TestCaseOwnerPort.TestCaseWrite`, `ForumTagReadPort.ForumTagRow`, `ForumTagReadPort.ForumTagPage`.

`ContestSubmissionPort`, `ContestNotificationPort`, and `SubmissionResultPushPort` are deliberately App-local
collaboration seams: Contest association/ranking and the App WebSocket are App-owned even when Submission produces the
fact. `ProblemFactsPort`, `ProblemJudgingCaseReadPort`, and `ProblemExampleReadPort` are App-provided Problem facts;
they are consumed by Submission/Judge but do not transfer Problem ownership.

### Submission-owned services

`RejudgePolicy`, `SubmissionActivityAnalyticsPort`, `SubmissionAdminReadPort`, `SubmissionAdministrationService`,
`SubmissionAnalyticsPort`, `SubmissionFencePort`, `SubmissionGenerationReadPort`, `SubmissionReadPort`,
`SubmissionStreakPort`, `SubmissionUserQueryPort`, `SubmissionUserStatsPort`, `SubmissionWritePort`,
`ProblemSubmissionStatsPort`.

There are no nested declarations in this group. `ProblemSubmissionStatsPort` is Submission-owned despite its
Problem-facing name because its provider reads `SubmissionMapper`; the App Problem analytics projection is only a
consumer.

### Notification-owned services

`NotificationAdminReadPort`, `NotificationAdministrationService`, `NotificationServiceContract`.

`SubmissionNotificationPort` is a legacy/dead Notification dispatch seam: its behavior is notification delivery, its
current source has no production implementation or caller, and no App owner remains. CONTRACT-003 removes this dead
contract; it must not remain in app-api. If a bounded caller is discovered before implementation, the task must first
re-plan the owner and contract shape rather than silently retain the app-api declaration.

### Auth-backed common/local security services

`AccountReadPort`, `DelegationAssertionContract`, `JwtValidationPort`.

These ports and the assertion-name contract are local security seams implemented or consumed separately in App, Admin
and Notification. They do not expose an Auth provider through Dubbo and therefore move with their credential-free
projection/claim metadata to the common web-security seam, while Auth remains authoritative for account/JWT facts and
the existing `backend-auth-api` remains the remote identity contract.

### App-provided user/recipient fact services

`UserExistencePort`, `SubmissionUserReadPort`, `UserNotificationReadPort`.

Nested exception: `SubmissionUserReadPort.UserSummary`. These are retained in `backend-app-api` as explicit App fact or
recipient seams. They contain no Entity/Mapper and do not grant Submission or Notification access to App tables.

### Judge-runtime internal services

`CodeExecutionPort`, `JudgeConfigPort`, `JudgeEnqueuePort`, `JudgeExecutionPort`, `JudgeFeatureFlagsPort`,
`JudgingLanguageSupport`, `QueueHealthProbePort`, `VerdictResolvePort`.

Nested Judge-runtime type: `JudgeExecutionPort.JudgeExecutionResult`.

## Command inventory

### Common metadata

`ActorDelegation`, `WriteCommand`.

They are transport-neutral command metadata, already duplicated byte-for-byte in the Auth API precedent. The canonical
target for the app-api copy is `backend-common`; owner-specific business commands below keep their own payloads.

### App-owned commands

`AddContestProblemCommand`, `ApplyModerationCommand`, `CreateContestCommand`, `CreateProblemCommand`,
`CreateProblemListCommand`, `DeleteContestCommand`, `DeleteProblemCommand`, `DeleteProblemListCommand`,
`EndContestCommand`, `ForumCommentModerationCommand`, `ForumPostModerationCommand`, `ForumTagMutationCommand`,
`PublishProblemCommand`, `RemoveContestProblemCommand`, `ReplaceListProblemsCommand`, `StartContestCommand`,
`UpdateBannerCommand`, `UpdateBasicInfoCommand`, `UpdateContestCommand`, `UpdateProblemCommand`,
`UpdateProblemListCommand`, `UpdateProfileCommand`, `UpdateVisibilityCommand`, `UploadAvatarCommand`.

Nested App command types: `ApplyModerationCommand.ModerationAction`, `ForumCommentModerationCommand.Action`,
`ForumPostModerationCommand.Action`, `ForumTagMutationCommand.Action`, `ReplaceListProblemsCommand.ProblemEntry`.

### Submission-owned commands

`BatchRejudgeCommand`, `RejudgeCommand`.

### Notification-owned commands

`CreateNotificationCommand`, `DeleteNotificationCommand`, `UpdateNotificationCommand`.

## DTO inventory

### Auth-backed common/local security DTOs

`AccountInfo`, `JwtPayload`.

### Common value DTO

`DifficultyCountDTO` is a generic `(difficulty, count)` value shape used by both App Problem difficulty reads and
Submission user-stat reads. It contains no owner state and is the only business-shaped value retained as common; all
Submission-specific statistics remain in the Submission API.

### App-owned DTOs and explicit App facts

`AdminForumCommunityDTO`, `AdminForumCommunityPage`, `AdminForumPostPage`, `AdminForumPostQuery`,
`AdminForumPostRowDTO`, `AnnouncementPayload`, `ContestAdminDTO`, `ContestAdminViewDTO`, `ContestAnnouncementDTO`,
`ContestProblemAdminDTO`, `ContestProblemInputDTO`, `ContestRankingEntryDTO`, `ContentLifecycleState`,
`ForumCommentModerationResultDTO`, `ForumPostIndexDTO`, `ForumPostModerationResultDTO`, `ForumTagDTO`,
`ModerationApplyResultDTO`, `ModerationUserInfo`, `ProblemAdminCasesDTO`, `ProblemAdminCodeDTO`,
`ProblemAdminDescriptionDTO`, `ProblemAdminExampleDTO`, `ProblemAdminLanguageDTO`, `ProblemAdminQueryDTO`,
`ProblemAdminRowDTO`, `ProblemAdminTagDTO`, `ProblemAdminTestCaseDTO`, `ProblemAdminViewDTO`,
`ProblemCompletionReportDTO`, `ProblemExampleDTO`, `ProblemIndexDTO`, `ProblemJudgingCaseDTO`,
`ProblemListDetailDTO`, `ProblemListItemDTO`, `ProblemListSummaryDTO`, `ProfileWriteResult`,
`ReconciliationOrphanCounts`, `SolutionIndexDTO`, `UserIndexDTO`, `UserProfileDTO`, `NotificationUserInfo`,
`NotificationRecipientDTO`, `VoteStatusDTO`.

Nested App DTO types: `ProblemListDetailDTO.ProblemInListDTO`, `ProblemListDetailDTO.TagDTO`,
`ProblemListDetailDTO.ProblemListStatsDTO`, `ProblemListDetailDTO.ViewerStateDTO`,
`ProblemListDetailDTO.CategoryOptionDTO`, `ProblemListItemDTO.Tag`,
`ProblemCompletionReportDTO.DifficultyStats`, `ProblemCompletionReportDTO.TagStats`,
`ProblemCompletionReportDTO.TrendingProblem`, `ProblemCompletionReportDTO.HardestProblem`.

`NotificationRecipientDTO` is the explicit recipient exception: App composes profile/account facts for Notification;
Notification preferences and delivery state stay local to Notification. `NotificationUserInfo`, `UserIndexDTO`,
`ModerationUserInfo`, and `UserProfileDTO` remain App-provided user/profile facts. `ProblemExampleDTO` and
`ProblemJudgingCaseDTO` remain App-provided Problem facts.

### Submission-owned DTOs

`BatchRejudgeResultDTO`, `CreateSubmissionDTO`, `DailyActiveUserCount`, `HourlyActiveUserCount`, `LanguageCountDTO`,
`LanguageStatsDTO`, `LearningProgressDTO`, `MonthlySubmissionStatsDTO`, `PerformanceStats`,
`ProblemDifficultyCompletion`, `ProblemTrend`, `RejudgeResult`, `RejudgeResultDTO`, `StatusCountDTO`,
`SubmissionAdminQueryDTO`, `SubmissionAdminRowDTO`, `SubmissionDateCountDTO`, `SubmissionDetailVO`,
`SubmissionHistoryDTO`, `SubmissionListItemVO`, `SubmissionQueryDTO`, `SubmissionResultPayload`,
`SubmissionStatusMeta`, `SubmissionTestCaseDetailDTO`, `SubmissionVO`, `TopActiveUserCount`, `UserBestStats`,
`WeeklyActiveUserCount`, `WeeklyProgressDTO`.

Nested Submission DTO types: `LearningProgressDTO.WeeklyProgress`, `LearningProgressDTO.DifficultyProgress`,
`SubmissionHistoryDTO.MonthlySubmission`, `SubmissionHistoryDTO.LanguageSubmission`,
`SubmissionListItemVO.ProblemSummary`, `SubmissionDetailVO.UserInfo`, `SubmissionDetailVO.ProblemInfo`,
`SubmissionDetailVO.TestResult`, `SubmissionVO.UserInfo`, `SubmissionVO.ProblemInfo`, `SubmissionVO.TestResult`,
`SubmissionTestCaseDetailDTO.InputParam`.

`SubmissionTestCaseDetailDTO` is entity-free but Submission-owned because it is the persisted `test_details` row
projection. Its DTO codec and `SubmissionStatusCatalog` follow it to the Submission contract seam; neither may move to
common or include a Submission Entity/Mapper.

### Notification-owned DTOs

`BadgeEarnedPayload`, `NotificationAdminDTO`, `NotificationAdminViewDTO`, `NotificationPayload`.

Nested Notification DTO types: `BadgeEarnedPayload.BadgeTier`, `NotificationPayload.NotificationType`.

### Judge-runtime internal DTOs

`EnvelopeDTO`, `InputSpecDTO`, `PerCaseResultDTO`, `ProbeStatus`, `QueueHealthSnapshotDTO`, `RunResultDTO`,
`RunSubmissionDTO`.

Nested Judge-runtime DTO types: `PerCaseResultDTO.ErrorDTO`, `RunResultDTO.RunCaseResult`,
`RunResultDTO.RunCaseResult.InputParam`, `RunSubmissionDTO.RunTestCase`, `RunSubmissionDTO.RunInput`.

The D-form envelope/result and sandbox run DTOs are storage/execution implementation contracts. They are not
Submission API wire contracts even though their current transitional source package is app-api.

## Event inventory

### App-owned events

`FollowDomainEvent`, `FollowEventIngestionPort`, `FollowEventPublisher`.

### Submission-owned events

`SubmissionJudgedEvent`, `SubmissionLifecycleEventContract`.

The Submission lifecycle contract retains schema version, owner, ID, generation, verdict, metadata and redaction
semantics. `SubmissionJudgedEvent` is the local/event bridge representation of the same Submission-owned fact.

### Notification-owned events

`NotificationIntentEventContract`.

The intent event is consumed by the Notification owner; its event owner/payload identity is therefore frozen in the
Notification API even while App remains the producer of App-domain intents.

## Error inventory

`AppErrorCode` remains App-owned with namespace `app`. Notification and Submission providers must introduce/use their
owner-specific error mapping in their new APIs rather than importing an App error catalog. No shared error enum is
created as a shortcut.

## Evidence and negative-scan boundaries

- Codebase-memory project: `ulticode`, current generation indexed at `2026-08-17T06:05:20Z`, 36,448 nodes and 173,902
  edges, head `dc32f114e8cf78c0643b8b407a08c5f0fb9d7024`. Relevant app-api Java scopes have no recorded coverage gap;
  excluded tests/targets/docs/scripts and parse-partial frontend/config ranges were handled by direct source fallback.
- Graph candidate/relationship evidence covered `SubmissionWritePort`, `SubmissionFencePort`,
  `SubmissionAdminReadPort`, `SubmissionUserQueryPort`, `SubmissionUserReadPort`, `NotificationAdminReadPort`,
  `NotificationAdministrationService`, `UserNotificationReadPort`, `ProblemFactsPort`,
  `ProblemJudgingCaseReadPort`, and `ProblemExampleReadPort`; implementation/provider/consumer paths were checked in
  App, Submission, Notification, Judge/Judge-runtime, Admin and tests.
- Direct fallback confirmed the `backend-app-api` POM and reactor dependencies, the Auth/Admin provider-owned API
  precedents, `backend-submission` providers/compat two-hop, `backend-notification` providers, queue/sandbox code,
  and the migration guide boundaries.
- Baseline direct import counts for `backend-app-api` were recorded as: Auth 0, Admin 435 imports/125 files, App 753
  imports/277 files, Submission 104 imports/24 files, Notification 77 imports/34 files, Judge 9 imports/5 files,
  Judge-runtime 40 imports/23 files, Search 0. These are baseline evidence, not acceptance of the final state.
- Trace-path calls for app-api interfaces returned zero `CALLS` rows because the interfaces are consumed through
  field/type usage and implementations rather than direct method-call edges; that empty call result is not used as a
  negative consumer claim.

## DEC-011 guardrails

This matrix authorizes contract relocation only. It does not authorize a database migration, route/grant/REVOKE
change, runtime default change, provider retirement, new broker, cross-service SQL/2PC, shared Entity/Mapper,
business writer, permanent alias, or mixed-version deployment. Those remain separate tasks and, for cutover, require
the recorded release authority and rollback evidence.
