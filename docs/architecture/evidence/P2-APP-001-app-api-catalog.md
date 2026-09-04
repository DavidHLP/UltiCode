# P2-APP-001 app-api Contract Catalog

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207 (baseline); Batch B interface retirement is recorded below
> source: `services/api/app-api/src/main/java`
> transport rule: `Dubbo` for cross-owner provider references; `in-process` for App module seams; `judge-runtime-internal` for private worker seams

## Classification

This catalog records ownership and transport intent, not a permanent
interface count. `KEEP` means the interface has a cross-owner consumer or is
an explicitly documented provider-owned contract. `INTERNALIZED` means the
interface is private to an App logical Module and no longer belongs in
`app-api`.

## Catalog

`JudgeRunService` and its `JudgeRunCommand`/`JudgeRunResult` are owned by
`services/api/judge-api`; they are intentionally outside this App contract
catalog.

| Interface | Provider owner | Main consumer | Transport | Lifecycle | Decision |
|---|---|---|---|---|---|
| AchievementBadgeReadPort | App/Achievement | App Solution projection | in-process | current | INTERNALIZED |
| AdminForumReadPort | App/Forum | Admin forum projection | Dubbo | stable | KEEP |
| AppReconciliationReadPort | App | Admin reconciliation | Dubbo | stable | KEEP |
| BookmarkReadPort | App/Bookmark | App edge inspector | in-process | current | INTERNALIZED |
| ContentModerationService | App | Admin moderation | Dubbo | stable | KEEP |
| ContestAchievementPort | App/Contest | App contest lifecycle | in-process | stable | KEEP |
| ContestAdminReadPort | App/Contest | Admin contest projection | Dubbo | stable | KEEP |
| ContestAdministrationService | App/Contest | Admin contest commands | Dubbo | stable | KEEP |
| ContestAnnouncementPushPort | App/Contest | App WebSocket | in-process | stable | KEEP |
| ContestAnnouncementReadPort | App/Contest | Admin/WebSocket readers | Dubbo/in-process | stable | KEEP |
| ContestLiveRankingReadPort | App/Contest | App WebSocket + Admin | Dubbo/in-process | stable | KEEP |
| ContestNotificationPort | App/Contest | Notification integration | Dubbo | stable | KEEP |
| ContestParticipantReadPort | App/Contest | Admin analytics | Dubbo | stable | KEEP |
| ContestRankingMarkDirtyPort | App/Contest | App WebSocket | in-process | stable | KEEP |
| ContestStatusPushPort | App/Contest | App WebSocket | in-process | stable | KEEP |
| ContestSubmissionPort | App/Contest | Submission contest effects | Dubbo/in-process | stable | KEEP |
| ContestSubscriptionPolicy | App/Contest | App WebSocket | in-process | stable | KEEP |
| DashboardAdminReadPort | App | Admin dashboard | Dubbo | stable | KEEP |
| FollowCountPort | App/Follow | App projections | in-process | current | INTERNALIZED |
| FollowEventPublisher | App/Follow | App follow module | in-process | current | INTERNALIZED |
| ForumCommentAdministrationService | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumCommentOwnerPort | App/Forum | App moderation adapters | in-process | current | INTERNALIZED |
| ForumCommentReadPort | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumOwnerPort | App/Forum | App moderation adapters | in-process | current | INTERNALIZED |
| ForumPostAdministrationService | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumPostReadPort | App/Forum | App search source | in-process | current | INTERNALIZED |
| ForumPostVoteCountReadPort | App/Vote | Admin forum projection | Dubbo | stable | KEEP |
| ForumTagAdministrationService | App/Forum | Admin tag commands | Dubbo | stable | KEEP |
| ForumTagReadPort | App/Forum | Admin tag projection | Dubbo | stable | KEEP |
| ForumVoteReadPort | App/Vote | App forum projection | in-process | current | INTERNALIZED |
| ModerationAccountPort | App/Moderation | App moderation | in-process | current | INTERNALIZED |
| ModerationContentActionPort | App/Moderation | App moderation | in-process | current | INTERNALIZED |
| ProblemAdminReadPort | App/Problem | Admin problem projection | Dubbo | stable | KEEP deep module |
| ProblemAdministrationService | App/Problem | Admin problem commands | Dubbo | stable | KEEP |
| ProblemAnalyticsReadPort | App/Problem | Admin analytics | Dubbo | stable | KEEP |
| ProblemDifficultyReadPort | App/Problem | Admin/user projections | Dubbo/in-process | stable | KEEP |
| ProblemExampleReadPort | App/Problem | Judge source adapter | in-process/Dubbo | stable | KEEP |
| ProblemExistencePort | App/Problem | App Solution + ProblemList | in-process | current | INTERNALIZED |
| ProblemFactsPort | App/Problem | Submission/Judge | Dubbo/in-process | stable | KEEP |
| ProblemInteractionQueryPort | App/Problem | App problem projection | in-process | stable | KEEP |
| ProblemJudgingCaseReadPort | App/Problem | Judge worker | Dubbo | stable | KEEP |
| ProblemListAdministrationService | App/ProblemList | Admin commands | Dubbo | stable | KEEP |
| ProblemListChainReadPort | App/ProblemList | Admin projection | Dubbo | stable | KEEP |
| ProblemListReadPort | App/ProblemList | App list projection | in-process | stable | KEEP |
| ProblemListSearchReadPort | App/ProblemList | Admin projection | Dubbo | stable | KEEP |
| ProblemOwnerPort | App/Problem | Admin commands | Dubbo | stable | KEEP |
| ProblemSearchReadPort | App/Problem | Search | Dubbo | stable | KEEP |
| ProblemTagOwnerPort | App/Problem | Admin commands | Dubbo | stable | KEEP |
| ProblemTagReadPort | App/Problem/Solution | App Solution projection | in-process | current | INTERNALIZED |
| ProblemTagStatsReadPort | App/Problem | App User projection | in-process | stable | KEEP |
| ProblemTitleLookupPort | App/Problem | Submission admin search | Dubbo | stable | KEEP |
| ProfileWriteService | App/Profile | App user profile | Dubbo/in-process | stable | KEEP |
| QueueHealthProbePort | Judge runtime | Admin monitoring | Dubbo | stable | KEEP |
| SolutionAdminReadPort | App/Solution | Admin solution projection | Dubbo | stable | KEEP |
| SolutionCommentOwnerPort | App/Solution | Admin moderation | Dubbo | stable | KEEP |
| SolutionCommentReadPort | App/Solution | Admin moderation | Dubbo | stable | KEEP |
| SolutionOwnerPort | App/Solution | Admin commands | Dubbo | stable | KEEP |
| SolutionReadPort | App/Solution | Search/Problem | Dubbo | stable | KEEP |
| SolutionVoteReadPort | App/Vote | App Solution projection | in-process | current | INTERNALIZED |
| SubmissionResultPushPort | App/WebSocket | App WebSocket consumer | in-process | current | INTERNALIZED |
| SubmissionUserReadPort | App/User | Submission owner | Dubbo/in-process | stable | KEEP |
| SubscriptionReadPort | App/Subscription | Admin analytics | Dubbo | stable | KEEP |
| TestCaseOwnerPort | App/Problem | Admin problem commands | Dubbo | stable | KEEP |
| UserExistencePort | Auth/App | Submission owner | Dubbo/in-process | stable | KEEP |
| UserNotificationReadPort | App | Notification owner | Dubbo | stable | KEEP |
| UserProfileQueryService | App/Profile | Admin/Auth projections | Dubbo | stable | KEEP |
| UserReadPort | App/User | App projections | in-process | current | INTERNALIZED |
| UserSearchReadPort | App/Search | App search source | in-process | current | INTERNALIZED |

## Retired by P2-APP-003

| Retired interface | Replacement | Evidence |
|---|---|---|
| `JudgeConfigPort` | `com.ulticode.modules.submission.port.JudgeConfigPort` | `services/judge-runtime/src/main/java/.../JudgeConfigPort.java` |
| `JudgeEnqueuePort` | `com.ulticode.modules.queue.port.JudgeEnqueuePort` | `services/judge-runtime/src/main/java/.../JudgeEnqueuePort.java` |
| `VerdictResolvePort` | `com.ulticode.modules.submission.port.VerdictResolvePort` | `services/judge-runtime/src/main/java/.../VerdictResolvePort.java` |
| `ModerationUserReadPort` | `com.ulticode.modules.moderation.port.ModerationUserReadPort` | `services/app/app-web/src/main/java/.../ModerationUserReadPort.java` |
| `JudgeFeatureFlagsPort` | `com.ulticode.modules.submission.port.JudgeFeatureFlagsPort` | `services/judge-runtime/src/main/java/.../JudgeFeatureFlagsPort.java` |
| `JudgingLanguageSupport` | `com.ulticode.modules.submission.port.JudgingLanguageSupport` + App `ProblemLanguageCatalog` | `services/judge-runtime/src/main/java/.../JudgingLanguageSupport.java` |

## Coverage and deletion test

- The source inventory and deletion test are executed by
  `scripts/test/api-contract-boundary-contract.sh`.
- App-private interfaces now live beside the logical Module that owns their
  Implementation; only cross-owner contracts remain in `app-api`.
- The catalog is source inventory, not a production traffic or deployment
  claim.

## Evidence Level

Repository Implemented. Catalog is source inventory, not a production traffic or deployment claim.
