# Milestones

## v1.4 Seed Data Expansion (Shipped: 2026-04-19)

**Phases completed:** 3 phases, 3 plans, 0 tasks

**Key accomplishments:**

- 1. [Rule 3 - Blocking] Fixed SQL syntax errors

---

## v1.3 Core Features — 2026-04-19

**Shipped:** 2026-04-19
**Phases:** 12-15 (4 phases, 8 plans)
**Files changed:** 351 files, +20,288 / -28,279 LOC

**Key accomplishments:**

- Judge Worker: 自动判题 via Redis queue, cgroup v2 内存测量, 5语言白名单
- Contest Data Layer: 实体/CRUD/公告/跨模块提交记录
- Contest Engine: CF Elo评分, 实时排名throttle基础设施
- Problem + User: Random题目, acceptance rate, globalRank AC排名, admin bulk ops

**Deferred to v1.4:** JUDGE-04, CONTEST-03, CONTEST-06, PROB-04, USER-03

---

## v1.2 CI/CD Pipeline (Shipped: 2026-04-18)

**Phases completed:** 3 phases, 8 plans, 9 tasks

**Key accomplishments:**

- Unified ci.yml with dorny/paths-filter@v4 replacing separate ci-backend.yml and ci-frontend.yml, with path-filtered parallel jobs for all 3 services
- GHCR image push workflow with SHA+latest tagging via docker/metadata-action@v5 and matrix strategy for 3 services
- Verified docker-compose.prod.yml with 3 GHCR image refs, IMAGE_TAG interpolation, and ordered depends_on health check chain -- added IMAGE_TAG usage documentation
- Ordered health check verification with backend-first fail fast, IMAGE_TAG SSH export fix, and corrected frontend ports (9002/9003)
- Dependabot v2 config covering github-actions, npm (console + management), and Maven (backend-spring) with weekly grouped PRs and 5-PR limits

---

## v1.1 Technical Debt Remediation II (Shipped: 2026-04-17)

**Phases completed:** 4 phases, 15 plans, 20 tasks

**Key accomplishments:**

- CorsProperties.java (new):
- Verified XssFilter is a pure pass-through that does not modify request headers, confirming SEC-08 is already satisfied
- Actuator endpoints restricted to health-only in production profile with no detail leakage; JWT cookie secure flags verified as pre-existing
- Removed all weak password fallbacks from docker-compose.yml, enforcing required env vars with ${VAR:?message} syntax and adding CHANGE_ME placeholders to .env.example
- Forum communities pagination via service layer, real problem counts via countByListId, and JVM heap memory metrics replacing all hardcoded placeholders in admin analytics performance report
- SQL AVG(TIMESTAMPDIFF) aggregation with COALESCE NULL-safety replacing hardcoded 0.0 in moderation stats
- SQL aggregation queries replacing N+1 loops and fixing retention rate bug in AdminAnalyticsServiceImpl
- 1. [Rule 3 - Blocking] Added buildWrapperScript stub to enable compilation in Task 1
- 12 files, 26 catch blocks addressed:
- Split 495-line monolithic analytics service into 3 focused services + facade, reducing complexity while preserving the public interface unchanged
- Removed 9 unguarded console.warn statements from production frontend, stabilized all Maven SNAPSHOT versions to 1.0.0, and untracked management/.env from git
- 35 console frontend tests covering auth API boundary, problem-detail routing logic, and auth store state machine transitions
- Management frontend vitest configuration with admin problems API and store CRUD test suites (23 new tests)
- 1. [Rule 3 - Blocking] Pre-existing CodeExecutionServiceTest compilation error

---

## v1.0 Technical Debt Remediation (Shipped: 2026-04-16)

**Phases completed:** 4 phases, 11 plans, 20 tasks

**Key accomplishments:**

- Replaced broken regex-based XssFilter input stripping with pass-through filter; added OWASP Java Encoder dependency for future output encoding (SEC-06)
- CsrfValidationFilter servlet filter validates CSRF tokens after JWT auth in the Spring Security chain, replacing the WebMvc-layer CsrfInterceptor
- @PostConstruct fail-fast validation on JwtProperties and removal of dead UserDetailsServiceImpl placeholder
- Password reset flow migrated from Redis to DB-stored BCrypt token hashes with EmailServiceImpl integration for actual email delivery
- Admin rejudge/batch-rejudge endpoints enqueue LOW-priority judge jobs with retryCount tracking, batch size limit of 50, and 5 req/min rate limiting
- Docker sandbox hardened with --cap-drop ALL and custom seccomp profile blocking ptrace/mount/keyctl/unshare/setns/clone-namespaces for all 5 supported languages
- 48 unit tests covering JWT token generation/validation, CSRF lifecycle, login/register/refresh flows, and password reset with session revocation
- Testcontainers BOM 1.21.3 in pom.xml, 18 new unit tests for SubmissionServiceImpl (8) and CodeExecutionService (10), AdminSubmissionServiceImplTest verified complete for Phase 2
- 5 Testcontainers integration tests for SubmissionServiceImpl verifying persistence to real MySQL and queue failure fallback, with manual MyBatis-Plus SqlSessionFactory setup avoiding full Spring context
- 8 oversized console Vue components split into 34 co-located sub-components and 8 composables with all parents under 500 lines
- Split 5 oversized management components (1224, 881, 768, 627, 602 lines) and 1 Pinia store (600 lines) into 25 co-located sub-components, 6 composables, and 5 domain store modules, all under 500 lines

---

---

## v1.4 Seed Data Expansion (Shipped: 2026-04-19)

**Phases completed:** 3 phases, 3 plans

**Key accomplishments:**

- Solutions Seed (V23): 97 solutions across 32 problems with Chinese Markdown content
- Submissions Seed (V24): ~200 submissions with realistic status distribution (AC/WA/TLE/MLE/RE/CE)
- Collections Seed (V25): ~50 scenario-based collections organized by difficulty/tags/interview companies
- SQL syntax errors in Phase 17 seed fixed before execution
