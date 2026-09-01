# P2-APP-001 app-api Contract Catalog

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207 (baseline); Batch B interface retirement is recorded below
> source: `services/api/app-api/src/main/java`
> transport rule: `Dubbo` for cross-owner provider references; `in-process` for App module seams; `judge-runtime-internal` for private worker seams

## Classification

The baseline source contained 75 public app-api interfaces. The current source contains 71 after P2-APP-003 retired four internal seams. The stale graph returned 78 because it retained deleted symbols and predates this working tree. P2-APP-003 removed those four without compatibility facades.

`KEEP` means the interface has a cross-owner consumer or is an explicitly documented provider-owned contract. `INTERNALIZED` means it no longer belongs in app-api. No `SPLIT` or `DELETE` recommendation is made solely from method count.

## Catalog

| Interface | Provider owner | Main consumer | Transport | Lifecycle | Decision |
|---|---|---|---|---|---|
| AchievementBadgeReadPort | App/Achievement | App Solution projection | in-process | stable | KEEP |
| AdminForumReadPort | App/Forum | Admin forum projection | Dubbo | stable | KEEP |
| AppReconciliationReadPort | App | Admin reconciliation | Dubbo | stable | KEEP |
| BookmarkReadPort | App | legacy edge inspector | Dubbo | stable | KEEP |
| CodeExecutionPort | Judge | App submission/run path | Dubbo | stable | KEEP |
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
| FollowCountPort | App/Follow | App projections | in-process | stable | KEEP |
| FollowEventPublisher | App/Follow | Notification/Achievement consumers | Dubbo/event | stable | KEEP |
| ForumCommentAdministrationService | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumCommentOwnerPort | App/Forum | Moderation/Search | Dubbo/in-process | stable | KEEP |
| ForumCommentReadPort | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumOwnerPort | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumPostAdministrationService | App/Forum | Admin moderation | Dubbo | stable | KEEP |
| ForumPostReadPort | App/Forum | Search | Dubbo | stable | KEEP |
| ForumPostVoteCountReadPort | App/Vote | Admin forum projection | Dubbo | stable | KEEP |
| ForumTagAdministrationService | App/Forum | Admin tag commands | Dubbo | stable | KEEP |
| ForumTagReadPort | App/Forum | Admin tag projection | Dubbo | stable | KEEP |
| ForumVoteReadPort | App/Vote | App forum projection | in-process | stable | KEEP |
| JudgeFeatureFlagsPort | Judge runtime | Judge runtime configuration | judge-runtime-internal (candidate remaining) | stable | KEEP pending future internalization |
| JudgingLanguageSupport | Judge runtime | App problem/sandbox seams | in-process/Dubbo | stable | KEEP |
| ModerationAccountPort | Auth | App moderation | Dubbo | stable | KEEP |
| ModerationContentActionPort | App | App moderation | in-process | stable | KEEP |
| ProblemAdminReadPort | App/Problem | Admin problem projection | Dubbo | stable | KEEP deep module |
| ProblemAdministrationService | App/Problem | Admin problem commands | Dubbo | stable | KEEP |
| ProblemAnalyticsReadPort | App/Problem | Admin analytics | Dubbo | stable | KEEP |
| ProblemDifficultyReadPort | App/Problem | Admin/user projections | Dubbo/in-process | stable | KEEP |
| ProblemExampleReadPort | App/Problem | Judge source adapter | in-process/Dubbo | stable | KEEP |
| ProblemExistencePort | App/Problem | App Solution service | in-process | stable | KEEP |
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
| ProblemTagReadPort | App/Problem | App Solution projection | in-process | stable | KEEP |
| ProblemTagStatsReadPort | App/Problem | App User projection | in-process | stable | KEEP |
| ProblemTitleLookupPort | App/Problem | Submission admin search | Dubbo | stable | KEEP |
| ProfileWriteService | App/Profile | App user profile | Dubbo/in-process | stable | KEEP |
| QueueHealthProbePort | Judge runtime | Admin monitoring | Dubbo | stable | KEEP |
| SolutionAdminReadPort | App/Solution | Admin solution projection | Dubbo | stable | KEEP |
| SolutionCommentOwnerPort | App/Solution | Admin moderation | Dubbo | stable | KEEP |
| SolutionCommentReadPort | App/Solution | Admin moderation | Dubbo | stable | KEEP |
| SolutionOwnerPort | App/Solution | Admin commands | Dubbo | stable | KEEP |
| SolutionReadPort | App/Solution | Search/Problem | Dubbo | stable | KEEP |
| SolutionVoteReadPort | App/Vote | App Solution projection | in-process | stable | KEEP |
| SubmissionResultPushPort | App/WebSocket | App WebSocket consumer | in-process/event | stable | KEEP; app-api is the owner contract |
| SubmissionUserReadPort | App/User | Submission owner | Dubbo/in-process | stable | KEEP |
| SubscriptionReadPort | App/Subscription | Admin analytics | Dubbo | stable | KEEP |
| TestCaseOwnerPort | App/Problem | Admin problem commands | Dubbo | stable | KEEP |
| UserExistencePort | Auth/App | Submission owner | Dubbo/in-process | stable | KEEP |
| UserNotificationReadPort | App | Notification owner | Dubbo | stable | KEEP |
| UserProfileQueryService | App/Profile | Admin/Auth projections | Dubbo | stable | KEEP |
| UserReadPort | Auth | Notification owner | Dubbo | stable | KEEP |
| UserSearchReadPort | Auth/App | Search | Dubbo | stable | KEEP |

## Retired by P2-APP-003

| Retired interface | Replacement | Evidence |
|---|---|---|
| `JudgeConfigPort` | `com.ulticode.modules.submission.port.JudgeConfigPort` | `services/judge-runtime/src/main/java/.../JudgeConfigPort.java` |
| `JudgeEnqueuePort` | `com.ulticode.modules.queue.port.JudgeEnqueuePort` | `services/judge-runtime/src/main/java/.../JudgeEnqueuePort.java` |
| `VerdictResolvePort` | `com.ulticode.modules.submission.port.VerdictResolvePort` | `services/judge-runtime/src/main/java/.../VerdictResolvePort.java` |
| `ModerationUserReadPort` | `com.ulticode.modules.moderation.port.ModerationUserReadPort` | `services/app/app-web/src/main/java/.../ModerationUserReadPort.java` |

## Coverage and deletion test

- Source inventory after P2-APP-003: `find services/api/app-api -name '*.java'` + `grep 'public interface'` returns 71 current public interfaces; baseline was 75 and the graph's 78 result was stale.
- Same-package consumers were checked with package and fully-qualified symbol searches; test-only references in `BackendAppApplicationTest` are not production consumers.
- `scripts/test/api-contract-boundary-contract.sh` remains the implementation-leakage and compatibility gate; P2-APP-006 adds ownership metadata validation rather than blocking additive DTO fields.
- Four retired files are absent, and no `com.ulticode.app.api.service.<retired>` source reference remains.

## Evidence Level

Repository Implemented. Catalog is source inventory, not a production traffic or deployment claim.
