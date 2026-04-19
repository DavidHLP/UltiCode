# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt Remediation** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt Remediation II** — Phases 5-8 (shipped 2026-04-17)
- ✅ **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- ✅ **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)

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

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 15

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

---
*Roadmap created: 2026-04-17*
*Last updated: 2026-04-19 after v1.3 milestone*
