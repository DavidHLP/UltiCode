# Retrospective

## Milestone: v1.0 — Technical Debt Remediation

**Shipped:** 2026-04-16
**Phases:** 4 | **Plans:** 11

### What Was Built

- Phase 1: Security filter chain overhaul (XSS output encoding, CSRF Spring Security migration, JWT validation, dead code removal)
- Phase 2: Core functionality completion (password reset email, admin rejudge with throttling, Docker sandbox seccomp hardening)
- Phase 3: Test coverage with 71 new tests including Testcontainers integration tests for auth, submission, and code execution
- Phase 4: Frontend quality — 14 oversized Vue components split into 59 sub-components + 14 composables

### What Worked

- **Severity-first ordering** ensured security vulnerabilities were fixed before quality improvements
- **Dedicated test phase** (Phase 3) validated all security fixes comprehensively before frontend work
- **Phase branching with worktree isolation** prevented conflicts between parallel work streams
- **Co-located composables pattern** established a clean, maintainable frontend architecture
- **Testcontainers for integration tests** avoided mock/prod divergence issues

### What Was Inefficient

- **ROADMAP Progress table** was not updated during execution, causing status inconsistency
- **REQUIREMENTS.md traceability** was not kept in sync — 7 items still showed Pending after completion
- **Phase 2 code review** required 3 iterations to resolve all findings, indicating initial implementation quality could improve
- **audit-open tool bug** (`output is not defined`) blocked automated pre-close verification

### Patterns Established

- Co-located composables: `views/{feature}/composables/use{Feature}.ts`
- Co-located components: `views/{feature}/components/{ChildComponent}.vue`
- Manual MyBatis-Plus SqlSessionFactory for Testcontainers (avoids full Spring context)
- Dialog state in parent, content in child components
- OWASP Encoder for output encoding (replacing input-stripping pattern)

### Key Lessons

- Keep REQUIREMENTS.md traceability table updated after each phase, not at milestone close
- Code review findings from Phase 2 show value of thorough security re-review
- Test infrastructure (Testcontainers BOM) should be set up once and shared across plans

### Cost Observations

- Model mix: ~70% sonnet (execution), ~30% opus (planning/review)
- Sessions: ~8 sessions over 3 days
- Notable: Phase 4 frontend splitting was the most file-intensive (42 files) but fastest per plan

## Cross-Milestone Trends

| Metric | v1.0 |
|--------|------|
| Phases | 4 |
| Plans | 11 |
| Tasks | 20 |
| Files changed | 378 |
| LOC added | +31,958 |
| LOC removed | -18,490 |
| Timeline (days) | 3 |
| Tests added | 71 |
| Components split | 14 → 59 |
