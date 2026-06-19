# Backend Architecture (Spring Boot 3.2.5 / Java 17)

<!-- Generated: 2026-06-19 | Java files: 588 | Modules: 26 | Controllers: 43 | Token estimate: ~850 -->

## Layering

`Controller → Service → Mapper (MyBatis-Plus) → Entity`  ·  DTO mapping via MapStruct  ·  Response wrapper: `Result<T>` (`{code, message, data, traceId}`)

## Top-level Packages

| Package          | Contents                                                                |
| ---------------- | ----------------------------------------------------------------------- |
| `common/`        | Annotations, aspects, config, constants, DTOs, exceptions, filters, response wrappers, services, utilities |
| `infrastructure/`| Redis service (`RedisService`), cache constants, outbox pollers         |
| `security/`      | JWT (jjwt 0.13), CSRF (Redis-backed), OAuth, auth entry point           |
| `websocket/`     | Notification STOMP service, auth interceptor, DTOs                     |
| `modules/`       | 26 business modules (see below)                                         |

## Module Map (26 modules)

```
modules/
├── achievement/      → /achievements
├── admin/            → /admin/*  (15 controllers: account, analytics, comment, contest, forum,
│                                    notification, problem, problem-list, settings, solution,
│                                    submission, tag, test-case + audit + dashboard)
├── auth/             → /auth (login, refresh, logout, OAuth, password-reset)
├── backup/           → /admin/backups
├── bookmark/         → /bookmarks  (+ folder)
├── contest/          → /contest, /admin/contest, /admin/scoring-rules  [R1-R9 closed 2026-06-18]
├── edgeoperations/   → /edge-operations  (LIKE/DISLIKE/FAVORITE/SAVE  — added 2026-06-10)
├── email/            → /admin/email (template + log)
├── follow/           → /users/{id}/follow
├── forum/            → /forum  (post / community / comment / tag)
├── i18n/             → /i18n  (translation CRUD)
├── moderation/       → /moderation  (queue / reports / actions / bans / appeals)
├── monitoring/       → /monitoring  (admin ops)
├── notification/     → /notifications  (intents + delivery ledger)
├── permission/       → (entity/service only — backs admin user-permission endpoints)
├── problem/          → /problems, /admin/problems  (+ versions + test_cases + notes)
├── problemlist/      → /problem-lists
├── queue/            → (background job processors — no REST)
├── refreshtoken/     → (entity/service only — no REST; rotation/revoke)
├── search/           → /search  (Meilisearch-backed)
├── solution/         → /api/solutions, /api/solutions/topics
├── submission/       → /submissions, /problems/{id}/submissions
├── subscription/     → /subscriptions, /admin/subscriptions
├── user/             → /users
├── vote/             → /vote
└── websocket/        → (STOMP endpoints, no REST)
```

## Key Services (selection)

- `AuthService` — login / refresh / OAuth state consume
- `SubmissionService` — write submission + judge_outbox row (ADR-003)
- `ContestService` + `ContestScoringService` + `ContestSchedulerService` + `RankingService` + `RatingCalculationService` + `ScoringRuleService` — full contest pipeline
- `OAuthService`, `PasswordResetService` — auth
- `BookmarkService`, `EdgeOperationsService` — user actions
- `ModerationQueueService`, `AppealService`, `ReportService` — moderation
- `NotificationService` + `NotificationDeliveryWorker` — ledger-driven
- `SandboxRunnerService` — fork sandbox container, poll verdict
- `AdminProblemService`, `AdminSubmissionService`, `AdminUserService` — admin CRUD
- `AuditService` — admin action log
- `RedisService` (infrastructure) — typed Redis wrapper

## Middleware Chain

```
Request
  → CorsFilter
  → SecurityFilterChain (JWT decode, role check, CSRF for state-changing methods)
  → MethodArgumentResolver (@CurrentUser, @RequireRole)
  → @Valid on DTO
  → Controller
  → Service (@Transactional where state changes)
  → Mapper (MyBatis-Plus BaseMapper)
  → MySQL
Response
  ← GlobalExceptionHandler (BusinessException → Result.error; MethodArgumentNotValid → 400)
  ← ResponseResultAdvice (wraps return type in Result<T>)
  ← TraceIdFilter (sets `traceId` for eagleeye correlation)
```

## Background Workers (`infrastructure/`)

- `JudgeOutboxPoller` — claims rows via `submission_lease` + generation guard, calls sandbox
- `NotificationDeliveryWorker` — drains `notification_delivery_ledger` per channel
- `ContestSchedulerService` — runs scheduled contest transitions (UPCOMING→LIVE→FINISHED)
- `EmailQueueWorker` — drains `email_log` pending
- `BackupScheduler` — periodic DB dumps

## Build & Test

- `./mvnw compile` / `./mvnw test` (excludes `*IT.java`) / `./mvnw verify` (JaCoCo)
- `./mvnw -Dtest='*IT' test` for integration tests (Testcontainers MySQL 9.1 + Redis 7)
- MapStruct processor wired via `lombok-mapstruct-binding`; Lombok via `lombok` (compile-only)
- DTO enum fields still raw `String` (TS enum mismatch known; prefer backend enum migration)
