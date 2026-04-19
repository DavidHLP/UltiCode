# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt Remediation** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt Remediation II** — Phases 5-8 (shipped 2026-04-17)
- ✅ **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- ✅ **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)
- [ ] **v1.4 Seed Data Expansion** — Phases 16-18 (in progress)

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
- [x] **Phase 9: Foundation + CI** - Fix blocking Dockerfile/config bugs, create CI workflow (v1.2)
- [x] **Phase 10: CD Pipeline** - Docker image publish to GHCR, SSH deploy to VPS (v1.2)
- [x] **Phase 11: Hardening** - Dependabot, rollback workflow (v1.2)
- [x] **Phase 12: Judge Worker** - Auto-judge via Redis queue, cgroup v2 memory, language whitelist (v1.3)
- [x] **Phase 13: Contest Data Layer** - Contest entities, admin CRUD, announcement system (v1.3)
- [x] **Phase 14: Contest Engine** - Scheduler, CF Elo rating, real-time ranking throttle (v1.3)
- [x] **Phase 15: Problem + User Enhancements** - Random problems, acceptance rates, admin bulk ops, globalRank (v1.3)
- [x] **Phase 16: Solutions Seed (V23)** - ~100 solutions, 1-3 per problem, Chinese + Markdown (v1.4)
- [x] **Phase 17: Submissions Seed (V24)** - ~200 submissions, varied statuses (AC/WA/TLE/MLE/RE/CE) (v1.4)
- [ ] **Phase 18: Collections Seed (V25)** - ~50 collections by scenario (difficulty/tags/companies) (v1.4)

## Phase Details

### Phase 16: Solutions Seed (V23)

**Goal**: Platform has rich solution content for problems, with each problem having at least 1 solution and medium difficulty problems having 2-3

**Depends on**: Phase 15 (v1.3 completed)

**Requirements**: SOL-01, SOL-02, SOL-03

**Success Criteria** (what must be TRUE):
1. User can browse solutions and see them organized by problem (each problem has at least 1 solution)
2. User can view a solution with Chinese comments, Markdown formatting (headings, lists), and syntax-highlighted code blocks
3. User can verify each solution references a valid problem_id and user_id (no orphaned FKs)
4. Medium difficulty problems have 2-3 solutions, totaling approximately 100 solutions across all 32 problems

**Plans**: 1 plan
- [x] 16-01-PLAN.md -- Generate ~92 solution INSERTs with Chinese Markdown content, valid FKs (COMPLETE: 97 solutions)

---

### Phase 17: Submissions Seed (V24)

**Goal**: Platform has realistic submission data with varied outcomes, reflecting real-world judging results

**Depends on**: Phase 16

**Requirements**: SUB-01, SUB-02, SUB-03, SUB-04

**Success Criteria** (what must be TRUE):
1. User can view submission history and see correct status values (AC/WA/TLE/MLE/RE/CE) matching enum exactly
2. User can verify status distribution approximates: AC 45-55%, WA 20-30%, TLE 8-12%, RE 5-10%, MLE 3-5%, CE 2-5%
3. User can confirm all status values have no leading/trailing whitespace (exact match to submission_statuses table)
4. User can verify each submission references a valid user_id and problem_id (no orphaned FKs)

**Plans**: 1 plan
- [x] 17-01-PLAN.md -- Generate ~200 submission INSERTs with status distribution, valid FKs

---

### Phase 18: Collections Seed (V25)

**Goal**: Platform has scenario-based problem collections organized by difficulty, tags, and interview companies

**Depends on**: Phase 17

**Requirements**: COL-01, COL-02, COL-03, COL-04

**Success Criteria** (what must be TRUE):
1. User can browse collections page and see approximately 50 collections organized by scenario type
2. User can view a collection with proper icon (Lucide name) and color (Tailwind color) styling
3. User can verify each collection contains at least 3 problem items
4. User can verify each collection item references a valid problem_list_id from the database

**Plans**: 1 plan
- [x] 16-01-PLAN.md -- Generate ~92 solution INSERTs with Chinese Markdown content, valid FKs

---

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 18

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
| 9. Foundation + CI | v1.2 | 3/3 | Complete | 2026-04-18 |
| 10. CD Pipeline | v1.2 | 3/3 | Complete | 2026-04-18 |
| 11. Hardening | v1.2 | 2/2 | Complete | 2026-04-18 |
| 12. Judge Worker | v1.3 | 2/2 | Complete | 2026-04-18 |
| 13. Contest Data Layer | v1.3 | 2/2 | Complete | 2026-04-18 |
| 14. Contest Engine | v1.3 | 2/2 | Complete | 2026-04-19 |
| 15. Problem + User Enhancements | v1.3 | 2/2 | Complete | 2026-04-19 |
| 16. Solutions Seed (V23) | v1.4 | 1/1 | Complete | 2026-04-19 |
| 17. Submissions Seed (V24) | v1.4 | 1/? | Planned | - |
| 18. Collections Seed (V25) | v1.4 | 0/? | Not started | - |

## Coverage

**Requirements:** 11 total (SOL-01, SOL-02, SOL-03, SUB-01, SUB-02, SUB-03, SUB-04, COL-01, COL-02, COL-03, COL-04)

| Requirement | Phase | Status |
|-------------|-------|--------|
| SOL-01 | Phase 16 | Done |
| SOL-02 | Phase 16 | Done |
| SOL-03 | Phase 16 | Done |
| SUB-01 | Phase 17 | Pending |
| SUB-02 | Phase 17 | Pending |
| SUB-03 | Phase 17 | Pending |
| SUB-04 | Phase 17 | Pending |
| COL-01 | Phase 18 | Pending |
| COL-02 | Phase 18 | Pending |
| COL-03 | Phase 18 | Pending |
| COL-04 | Phase 18 | Pending |

**Coverage:** 11/11 requirements mapped

---
*Roadmap created: 2026-04-17*
*Last updated: 2026-04-19 with v1.4 phases 16-18*
