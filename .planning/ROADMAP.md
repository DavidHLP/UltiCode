# Roadmap: UltiCode Technical Debt Remediation

## Milestones

- ✅ **v1.0 Technical Debt Remediation** — Phases 1-4 (shipped 2026-04-16)
- 🚧 **v1.1 Technical Debt Remediation II** — Phases 5-8 (in progress)

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

<details>
<summary>✅ v1.0 Technical Debt Remediation (Phases 1-4) — SHIPPED 2026-04-16</summary>

- [x] Phase 1: Security Filter Chain (3/3 plans) — completed 2026-04-14
- [x] Phase 2: Core Functionality (3/3 plans) — completed 2026-04-15
- [x] Phase 3: Test Coverage (3/3 plans) — completed 2026-04-15
- [x] Phase 4: Frontend Quality (2/2 plans) — completed 2026-04-15

</details>

### 🚧 v1.1 Technical Debt Remediation II (In Progress)

**Milestone Goal:** Clear all 19 remaining MEDIUM/LOW technical debt items deferred from v1.0, bringing the platform to production-ready state.

- [x] **Phase 5: Security Configuration** — Externalize CORS, harden JWT cookies, create production profile, remove default passwords (completed 2026-04-16)
- [ ] **Phase 6: Admin Functionality & Performance** — Real admin data, audit trails, DB aggregation, batch test execution
- [ ] **Phase 7: Code Quality & Dependencies** — Precise exception handling, service splits, debug cleanup, stable deps
- [ ] **Phase 8: Testing** — Frontend key-path tests, backend @WebMvcTest controller tests

## Phase Details

### Phase 5: Security Configuration
**Goal**: Platform security configuration is externalized and production-hardened — CORS origins, JWT cookie flags, actuator endpoints, and Docker credentials are all driven by environment variables with secure defaults
**Depends on**: Phase 4
**Requirements**: SEC-07, SEC-08, CONF-01, CONF-02, CONF-03
**Success Criteria** (what must be TRUE):
  1. CORS allowed origins are loaded from environment variables (not hardcoded), and the application rejects requests from origins not in the allowed list
  2. XssFilter no longer strips or modifies request headers, so CSRF tokens in headers pass through unmodified
  3. In production profile, JWT cookies are sent with `Secure=true` and Swagger UI is inaccessible
  4. docker-compose.yml contains no plaintext passwords; all credentials are injected via environment variables or .env files
**Plans**: 5 plans

Plans:
- [x] 05-01: Externalize CORS origins to environment variables (SEC-07)
- [x] 05-02: Stop XssFilter from cleaning request headers (SEC-08)
- [x] 05-03: Harden JWT cookie Secure flag and create application-prod.yml (CONF-01, CONF-02)
- [x] 05-04: Remove weak default passwords from docker-compose.yml (CONF-03)

### Phase 6: Admin Functionality & Performance
**Goal**: Admin panel displays real data instead of TODO stubs, audit trails capture the actual authenticated user, analytics use efficient database queries, and test case execution is faster through batch processing
**Depends on**: Phase 5
**Requirements**: AUDIT-01, FUNC-02, FUNC-03, PERF-01, PERF-02
**Success Criteria** (what must be TRUE):
  1. Backup audit logs show the actual admin username (not "system") who triggered the backup
  2. Admin analytics pages display real computed values (forum community stats, problem counts, moderation average resolution time) instead of placeholder zeros or TODO stubs
  3. Admin analytics dashboard loads without loading the entire database into memory — response time improves measurably for large datasets
  4. Code submission test cases execute in a single Docker container startup rather than one container per test case, reducing total judging time
**Plans**: 5 plans

Plans:
- [x] 06-01: Replace BackupController hardcoded "system" with actual authenticated user ID (AUDIT-01)
- [x] 06-02: Implement 5 Admin TODO stubs with real data (FUNC-02)
- [x] 06-03: Implement moderation average resolution time calculation (FUNC-03)
- [x] 06-04: Optimize admin analytics with database aggregation queries (PERF-02)
- [x] 06-05: Batch test case execution in single Docker container (PERF-01)

### Phase 7: Code Quality & Dependencies
**Goal**: Backend exception handling is precise (no broad `catch(Exception e)`), oversized service classes are split, debug logging is cleaned from production code, and all dependencies are stable versions with no git-tracked secrets
**Depends on**: Phase 6
**Requirements**: QUAL-02, QUAL-03, QUAL-04, DEP-01, DEP-02, DEP-03
**Success Criteria** (what must be TRUE):
  1. No `catch(Exception e)` or `catch(Throwable e)` blocks remain in production backend code — all catches target specific exception types
  2. AdminAnalyticsServiceImpl is split into focused service classes, each under 300 lines, with clear single responsibilities
  3. No `console.log` or `console.warn` statements exist in production frontend code (console.error for error logging is acceptable)
  4. `management/.env` is not tracked by git, and `pom.xml` contains no SNAPSHOT dependencies
**Plans**: 5 plans

Plans:
- [ ] 07-01: Replace broad catch(Exception e) with specific exception types (QUAL-02)
- [ ] 07-02: Split AdminAnalyticsServiceImpl into focused service classes (QUAL-03)
- [ ] 07-03: Clean console.log statements from production code (QUAL-04)
- [ ] 07-04: Remove git-tracked .env, replace SNAPSHOT deps, evaluate SockJS (DEP-01, DEP-02, DEP-03)

### Phase 8: Testing
**Goal**: Frontend Console and Management apps have key-path tests covering API layers and stores, and backend critical controllers have @WebMvcTest integration tests validating request/response contracts
**Depends on**: Phase 7
**Requirements**: TEST-02, TEST-03, TEST-04
**Success Criteria** (what must be TRUE):
  1. Console frontend has tests covering the request API layer, auth store login/refresh flow, and problem store data fetching
  2. Management frontend has tests covering the admin API layer and at least one admin store with CRUD operations
  3. Backend AuthController and ProblemController have @WebMvcTest integration tests verifying endpoint authentication, request validation, and response format
**Plans**: 5 plans

Plans:
- [ ] 08-01: Console frontend key-path tests — API layer, auth store, problem store (TEST-02)
- [ ] 08-02: Management frontend key-path tests — API layer, admin store (TEST-03)
- [ ] 08-03: Backend Controller @WebMvcTest integration tests (TEST-04)

## Progress

**Execution Order:**
Phases execute in numeric order: 5 → 6 → 7 → 8

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Security Filter Chain | v1.0 | 3/3 | Complete | 2026-04-14 |
| 2. Core Functionality | v1.0 | 3/3 | Complete | 2026-04-15 |
| 3. Test Coverage | v1.0 | 3/3 | Complete | 2026-04-15 |
| 4. Frontend Quality | v1.0 | 2/2 | Complete | 2026-04-15 |
| 5. Security Configuration | v1.1 | 4/4 | Complete    | 2026-04-16 |
| 6. Admin Functionality & Performance | v1.1 | 0/5 | Not started | - |
| 7. Code Quality & Dependencies | v1.1 | 0/4 | Not started | - |
| 8. Testing | v1.1 | 0/3 | Not started | - |
