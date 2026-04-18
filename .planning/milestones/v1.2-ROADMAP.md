# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt Remediation** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt Remediation II** — Phases 5-8 (shipped 2026-04-17)
- 🚧 **v1.2 CI/CD Pipeline** — Phases 9-11 (in progress)

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Security Filter Chain** - CSRF/XSS/JWT filter chain hardening (v1.0)
- [x] **Phase 2: Core Functionality** - Password reset, rejudge, Docker sandbox (v1.0)
- [x] **Phase 3: Test Coverage** - Unit + integration tests for security fixes (v1.0)
- [x] **Phase 4: Frontend Quality** - Oversized Vue component split (v1.0)
- [x] **Phase 5: Security Configuration** - CORS, CSP, JWT cookie, prod profile (v1.1)
- [x] **Phase 6: Admin Functionality** - Analytics, pagination, batch test execution (v1.1)
- [x] **Phase 7: Code Quality** - Catch blocks, service split, console cleanup (v1.1)
- [x] **Phase 8: Frontend Test Coverage** - Console + Management + Backend controller tests (v1.1)
- [ ] **Phase 9: Foundation + CI** - Fix blocking Dockerfile/config bugs, create CI workflow
- [ ] **Phase 10: CD Pipeline** - Docker image publish to GHCR, SSH deploy to VPS
- [x] **Phase 11: Hardening** - Dependabot, rollback workflow (completed 2026-04-18)

## Phase Details

<details>
<summary>✅ v1.0 Technical Debt Remediation (Phases 1-4) — SHIPPED 2026-04-16</summary>

### Phase 1: Security Filter Chain
**Goal**: CSRF/XSS/JWT filter chain hardening
**Plans**: 2 plans

Plans:
- [x] 01-01: Replace XssFilter with OWASP Encoder output encoding (SEC-06)
- [x] 01-02: Migrate CSRF to Spring Security CsrfValidationFilter (SEC-01)
- [x] 01-03: JWT secret fail-fast validation + remove UserDetailsServiceImpl (SEC-05, SEC-03)

### Phase 2: Core Functionality
**Goal**: Password reset, rejudge, Docker sandbox hardening
**Plans**: 2 plans

Plans:
- [x] 02-01: Password reset with BCrypt token + email delivery (SEC-02)
- [x] 02-02: Admin rejudge/batch-rejudge with rate limiting (FUNC-01)
- [x] 02-03: Docker sandbox seccomp profile + cap-drop ALL (SEC-04)

### Phase 3: Test Coverage
**Goal**: Unit + integration tests for security and core fixes
**Plans**: 2 plans

Plans:
- [x] 03-01: JWT/CSRF/Auth unit tests (48 tests)
- [x] 03-02: Submission + CodeExecution unit tests (18 tests)
- [x] 03-03: Testcontainers integration tests (5 tests)

### Phase 4: Frontend Quality
**Goal**: Oversized Vue component split
**Plans**: 2 plans

Plans:
- [x] 04-01: Console component split (14 components → 34 sub-components + 8 composables)
- [x] 04-02: Management component + Pinia store split (6 components → 25 sub-components + 6 composables + 5 store modules)

</details>

<details>
<summary>✅ v1.1 Technical Debt Remediation II (Phases 5-8) — SHIPPED 2026-04-17</summary>

### Phase 5: Security Configuration
**Goal**: Platform security configuration is externalized and production-hardened
**Plans**: 4 plans

Plans:
- [x] 05-01: Externalize CORS origins to environment variables (SEC-07)
- [x] 05-02: Stop XssFilter from cleaning request headers (SEC-08)
- [x] 05-03: Harden JWT cookie Secure flag and create application-prod.yml (CONF-01, CONF-02)
- [x] 05-04: Remove weak default passwords from docker-compose.yml (CONF-03)

### Phase 6: Admin Functionality & Performance
**Goal**: Admin panel displays real data, audit trails capture authenticated user, analytics use DB aggregation
**Plans**: 5 plans

Plans:
- [x] 06-01: Replace BackupController hardcoded "system" with actual authenticated user ID (AUDIT-01)
- [x] 06-02: Implement 5 Admin TODO stubs with real data (FUNC-02)
- [x] 06-03: Implement moderation average resolution time calculation (FUNC-03)
- [x] 06-04: Optimize admin analytics with database aggregation queries (PERF-02)
- [x] 06-05: Batch test case execution in single Docker container (PERF-01)

### Phase 7: Code Quality & Dependencies
**Goal**: Precise exception handling, service splits, debug cleanup, stable deps
**Plans**: 2 plans

Plans:
- [x] 07-01: Replace broad catch(Exception e) with specific exception types (QUAL-02)
- [x] 07-02: Split AdminAnalyticsServiceImpl into focused service classes (QUAL-03)
- [x] 07-03: Clean console.log/warn, replace SNAPSHOT deps, untrack management/.env (QUAL-04, DEP-01, DEP-02, DEP-03)

### Phase 8: Testing
**Goal**: Frontend key-path tests and backend @WebMvcTest controller tests
**Plans**: 2 plans

Plans:
- [x] 08-01: Console frontend key-path tests — API layer, auth store, problem store (TEST-02)
- [x] 08-02: Management frontend key-path tests — API layer, admin store (TEST-03)
- [x] 08-03: Backend Controller @WebMvcTest integration tests (TEST-04)

</details>

### 🚧 v1.2 CI/CD Pipeline (In Progress)

**Milestone Goal:** Automated CI/CD pipeline — every PR is linted, tested, and validated; every merge to main triggers Docker build and deployment via Docker Compose.

#### Phase 9: Foundation + CI
**Goal**: All pre-existing Dockerfile and configuration bugs are fixed, and a working CI workflow validates every PR with lint, type-check, and test across all 3 services
**Depends on**: Phase 8
**Requirements**: FOUND-01, FOUND-02, FOUND-03, FOUND-04, FOUND-05, FOUND-06, CI-01, CI-02, CI-03, CI-04, CI-05, CI-06
**Success Criteria** (what must be TRUE):
  1. `docker build` succeeds for all 3 service Dockerfiles (backend, console, management) with no JAR name mismatch or missing lockfile errors
  2. Every pull request to the repository triggers automated lint, type-check, and test jobs for the changed service(s)
  3. Backend tests pass in CI using GitHub Actions services: containers for MySQL and Redis (not Testcontainers Docker-in-Docker)
  4. Console and management frontend lint + type-check + test run only when their respective paths change
  5. A secrets mapping document exists that cross-references all configuration sources (GitHub Secrets, Docker Compose, Spring profiles, Vite env vars)
**Plans**: 2 plans

Plans:
- [x] 09-01: Fix Dockerfile bugs and create .dockerignore (FOUND-01, FOUND-02, FOUND-03, FOUND-04)
- [x] 09-02: Create application-ci.yml profile and secrets mapping document (FOUND-05, FOUND-06)
- [x] 09-03: Write ci.yml workflow with path-filtered parallel jobs and build caching (CI-01, CI-02, CI-03, CI-04, CI-05, CI-06)

#### Phase 10: CD Pipeline
**Goal**: Every merge to main automatically builds Docker images, pushes them to GHCR, and deploys to the VPS via Docker Compose with ordered service restarts
**Depends on**: Phase 9
**Requirements**: CD-01, CD-02, CD-03, CD-04, CD-05
**Success Criteria** (what must be TRUE):
  1. Merging a PR to main triggers automatic Docker image build and push to GHCR for all 3 services
  2. Each pushed Docker image is tagged with both the git SHA short hash and "latest" for traceability
  3. After a successful image push, the VPS automatically pulls new images and restarts services via Docker Compose
  4. Backend service starts and passes health checks before frontend services are restarted (ordered restart)
  5. A docker-compose.prod.yml exists that references GHCR images with a configurable IMAGE_TAG variable
**Plans**: 2 plans

Plans:
- [x] 10-01: Write docker-publish.yml with GHCR push and deterministic image tagging (CD-01, CD-02)
- [x] 10-02: Create docker-compose.prod.yml referencing GHCR images (CD-05)
- [x] 10-03: Write deploy.yml with SSH deploy and ordered health check restart (CD-03, CD-04)

#### Phase 11: Hardening
**Goal**: The CI/CD pipeline is self-maintaining with automated dependency updates and a manual rollback capability for failed deployments
**Depends on**: Phase 10
**Requirements**: HARD-01, HARD-02
**Success Criteria** (what must be TRUE):
  1. Dependabot automatically opens PRs for GitHub Actions version updates and npm/Maven dependency updates
  2. A rollback workflow exists that can be manually triggered via workflow_dispatch to redeploy a previous image tag
**Plans**: 2 plans

Plans:
- [x] 11-01: Configure Dependabot for Actions and dependency updates (HARD-01)
- [x] 11-02: Create rollback workflow with manual image tag redeployment (HARD-02)

## Progress

**Execution Order:**
Phases execute in numeric order: 9 → 10 → 11

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Security Filter Chain | v1.0 | 3/3 | Complete | 2026-04-14 |
| 2. Core Functionality | v1.0 | 3/3 | Complete | 2026-04-15 |
| 3. Test Coverage | v1.0 | 3/3 | Complete | 2026-04-15 |
| 4. Frontend Quality | v1.0 | 2/2 | Complete | 2026-04-15 |
| 5. Security Configuration | v1.1 | 4/4 | Complete | 2026-04-16 |
| 6. Admin Functionality & Performance | v1.1 | 5/5 | Complete | 2026-04-16 |
| 7. Code Quality & Dependencies | v1.1 | 3/3 | Complete | 2026-04-16 |
| 8. Testing | v1.1 | 3/3 | Complete | 2026-04-17 |
| 9. Foundation + CI | v1.2 | 3/3 | Complete   | 2026-04-18 |
| 10. CD Pipeline | v1.2 | 3/3 | Complete   | 2026-04-18 |
| 11. Hardening | v1.2 | 2/2 | Complete    | 2026-04-18 |

---
*Roadmap created: 2026-04-17*
*Last updated: 2026-04-18*
