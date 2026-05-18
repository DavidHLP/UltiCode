<!-- Generated: 2026-05-18 | Files scanned: 587 | Token estimate: ~900 -->

# Backend Architecture

## API Routes

### Public/User APIs

| Prefix | Controller | Key Operations |
|--------|-----------|----------------|
| `/auth` | AuthController | login, register, OAuth, refresh, me, forgot-password |
| `/problems` | ProblemController | list, detail, adjacent, by-tag |
| `/contest` | ContestController | list, detail, register, ranking, calendar, announcements |
| `/forum` | ForumController | posts CRUD, communities, comments, thread |
| `/solutions` | SolutionController | CRUD, comments, pin, best |
| `/submissions` | SubmissionController | list, detail, retry |
| `/problems/{id}/submissions` | ProblemSubmissionController | per-problem submissions |
| `/users` | UserController + FollowController | profile, by-username, follow/unfollow |
| `/bookmarks` | BookmarkController | folders CRUD, items, quick-favorite |
| `/vote` | VoteController | upvote/downvote |
| `/notifications` | NotificationController | list, mark-read, clear, preferences |
| `/achievements` | AchievementController | list, progress, points |
| `/subscriptions` | UserSubscriptionController | check-premium, premium-content |
| `/recommendations` | RecommendationController | daily, challenge, weak-points, similar |
| `/problem-lists` | ProblemListController | CRUD, fork, categories, save, add-problem |
| `/search` | SearchController | problems, solutions, users |
| `/i18n` | I18nController | locales, enums |
| `/edge-operations` | EdgeOperationsController | interactions, learning-progress |
| `/monitoring` | MonitoringController | health |

### Admin APIs

| Prefix | Controller | Key Operations |
|--------|-----------|----------------|
| `/admin/users` | AdminUserController | list, detail, create, update, delete |
| `/admin/problems` | AdminProblemController + AdminProblemVersionController | CRUD, versions |
| `/admin/contests` | AdminContestController | CRUD, scoring |
| `/admin/submissions` | AdminSubmissionController | list, rejudge |
| `/admin/solutions` | AdminSolutionController | list, flag, bulk-action |
| `/admin/forum` | AdminForumController | posts, communities |
| `/admin/comments` | AdminCommentController | list, delete |
| `/admin/notifications` | AdminNotificationController | create, list |
| `/admin/problem-lists` | AdminProblemListController | list, update |
| `/admin/tags` | AdminTagController | CRUD, merge |
| `/admin/audit` | AuditController | logs, report |
| `/admin/dashboard` | DashboardController | overview, charts |
| `/admin/analytics` | AdminAnalyticsController | performance, contest, revenue |
| `/admin/settings` | AdminSettingsController | general, features, maintenance |
| `/admin/backups` | BackupController | list, create, restore |
| `/admin/subscriptions` | SubscriptionController | manage subscriptions |
| `/admin/scoring-rules` | ScoringRuleController | CRUD |
| `/recommendations/admin` | RecommendationDataController | data management |
| `/email` | EmailController | templates, logs, send |
| `/moderation` | ModerationController | queue, reports, appeals, actions |

## Module Layering

Each module: `controller → service (impl) → mapper (MyBatis-Plus) → entity`
DTOs via MapStruct. Common: `common/` (config, exception, annotation, aspect, util, filter).

## Security Stack

- JWT: `security/jwt/` (JwtTokenProvider, JwtAuthenticationFilter)
- CSRF: `security/csrf/` (CsrfService, CsrfValidationFilter)
- OAuth: `security/oauth/` (OAuthProperties — GitHub, Google)
- Annotations: `@RateLimit`, `@RequireRole`, `@CheckBan`, `@Audited`, `@CurrentUser`
- Aspects: `RateLimitAspect`, `BanCheckAspect`, `AuditAspect`
- XSS: `XssFilter`
- Token blacklist: `TokenBlacklistService` (Redis)
