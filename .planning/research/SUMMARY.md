# Project Research Summary

**Project:** UltiCode v1.4 Seed Data Expansion
**Domain:** Flyway database migration patterns for seed data expansion
**Researched:** 2026-04-19
**Confidence:** HIGH

## Executive Summary

UltiCode v1.4 focuses on expanding seed data for three core entities: Solutions (8 to ~100), Submissions (~400 with more diverse statuses), and Collections (6 to ~50). Research confirms that Flyway migrations are the correct approach, with seed data living in SQL migration files that run via `db-manager`. The recommended stack is Python Faker + Jinja2 to generate realistic seed data, outputting Flyway-compatible SQL migrations. Foreign key dependencies drive phase ordering: users (V1) must exist before solutions and submissions; problems (V2/V16) must exist before submissions; solutions must exist before solution_comments. The existing V9 and V17 migrations provide battle-tested patterns to follow.

Key risks include FK constraint violations (critical), invalid submission status values, and datetime precision mismatches. The known V17 bug (leading space in `' Accepted'`) must be fixed before expanding submissions seed data.

## Key Findings

### Recommended Stack

**Approach:** Python script generator using `Faker` + Jinja2 templates, output as Flyway-compatible SQL migration files. This avoids JVM complexity (datafaker-java), tedium (handwritten SQL), and application bloat (in-application seeding).

**Core technologies:**
- `Faker` 18.x — Generate realistic names, text, UUIDs, dates with `locale='zh_CN'`
- `Jinja2` 3.x — Template SQL INSERT statements
- `UUID()` — All varchar(40) primary keys
- `NOW(3)` — datetime(3) timestamp precision (not `NOW()` or `NOW(6)`)

**Output structure:**
```
db-manager/scripts/
├── generate_seed_data.py      # Main entry point
├── faker_providers.py         # Custom providers (language names, problem slugs)
└── templates/
    ├── solutions.sql.j2
    ├── submissions.sql.j2
    └── collections.sql.j2
```

### Expected Features

**Seed data targets:**

| Entity | Current | Target | Gap |
|--------|---------|--------|-----|
| Solutions | 8 | ~100 | +92 |
| Submissions | ~400 | diverse statuses | WA/TLE/MLE/RE underrepresented |
| Collections | 6 | ~50 | +44 |

**Must have:**
- Solutions distributed across all 32 problems (Easy: 2-3 each, Medium/Hard: 1-2 each)
- Submissions with diverse status distribution (AC 45-55%, WA 20-30%, TLE 8-12%, RE 5-10%, MLE 3-5%, CE 2-5%)
- Collections organized by category (difficulty, company-tagged, pattern-tagged, problem-list-backed)
- All foreign key references to existing users, problems, and solutions

**Should have:**
- Collection items minimum 3 per collection (avoid unmaintained look)
- Realistic code content in submissions (not just comments)
- Chinese commentary with markdown code blocks in solutions
- Icon and color set for collections (Lucide names, Tailwind colors)

**Defer (v2+):**
- Solution comments expansion (tightly coupled to solution growth)
- Contest-related collections (separate domain)

### Architecture Approach

Flyway migrations with strict ordering and referential integrity enforcement. Two migration patterns exist:

1. **Schema + Seed Combined** (V8, V9) — Used when table creation and initial data are tightly coupled
2. **Seed Data Only** (V11, V12, V17) — Used for standalone seed data

Every migration follows this structure:
```sql
SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;
-- INSERT statements
COMMIT;
SET FOREIGN_KEY_CHECKS=1;
```

**Migration ordering (confirmed):**
```
V22 (achievement_schema) -> V23 (solutions) -> V24 (submissions_expanded) -> V25 (collections)
```

### Critical Pitfalls

1. **Foreign Key Constraint Violations** — FK references must be verified before migration. Query users and problems first to get valid IDs.
2. **Invalid Submission Status Values** — Use exact keys from `submission_statuses` table. V17 has a bug (leading space in `' Accepted'`).
3. **Duplicate Primary Key Violations** — Use `UUID()` for submissions, prefix solution IDs (e.g., `sol-v1-001`).
4. **Datetime Precision Mismatch** — Use `NOW(3)` for `datetime(3)` columns, not `NOW()` or `NOW(6)`.
5. **JSON Column Encoding** — Ensure UTF-8 encoding for Chinese characters in `tags` JSON columns.

## Implications for Roadmap

### Phase 1: Solutions Seed Migration (V23)
**Rationale:** Solutions have FK dependencies on both users (V1) and problems (V2/V16), which already exist. No other seed data depends on solutions yet, making this safe to expand first.

**Delivers:** ~100 solutions across all 32 problems
**Uses:** Faker + Jinja2 generator, V9 as template pattern
**Avoids:** FK violations by querying existing problem IDs first

### Phase 2: Submissions Seed Expansion (V24)
**Rationale:** Submissions depend on users and problems. Status distribution must be diverse (not AC-dominant). Fix the V17 status bug first.

**Delivers:** ~200 additional submissions with diverse statuses
**Uses:** V17 pattern with corrected status values
**Avoids:** Invalid status values, datetime precision errors

### Phase 3: Collections Seed Migration (V25)
**Rationale:** Collections depend on users. Collection_items reference problem_lists (from V15) and potentially solutions. Safe to do last since nothing depends on collections.

**Delivers:** ~50 collections with ~150 items
**Uses:** V8 pattern for structure
**Avoids:** Orphaned collection_items, FK violations to problem_lists

### Phase Ordering Rationale

- **Dependencies drive order:** Users (V1) -> Problems (V2/V16) -> Solutions (V23) -> Submissions (V24) -> Collections (V25). Nothing depends on collections, so it comes last.
- **Migration file pattern:** Each phase outputs a separate V{n} migration file, following existing convention.
- **Known bug mitigation:** V17 status bug must be fixed before Phase 2.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (Submissions):** May need to verify V17 status bug fix didn't break anything; recommend running validation queries

Phases with standard patterns (skip research-phase):
- **All phases:** Flyway migration patterns are well-established by existing V8, V9, V17 migrations

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct evidence from existing V17 migration; Faker + Jinja2 is standard practice |
| Features | HIGH | Based on existing schema analysis and FEATURES.md gap analysis |
| Architecture | HIGH | Confirmed by existing migrations V8, V9, V17; FK dependencies verified |
| Pitfalls | HIGH | All pitfalls identified from actual migration analysis; V17 bug confirmed |

**Overall confidence:** HIGH

### Gaps to Address

- **V17 status bug:** Leading space in `' Accepted'` must be corrected before Phase 2. Verify via `SELECT key FROM submission_statuses` query.
- **Problem ID gaps:** V17 appears to skip problem IDs 8 and 12. Verify which IDs are valid before seeding submissions.
- **Timestamp spanning:** Submissions should span 2025-11 to 2026-02 for recommendation engine time-range queries - confirm this requirement with recommendation service owner.

## Sources

### Primary (HIGH confidence)
- `db-manager/migrations/V17__recommendation_seed_submissions.sql` - Submission seed pattern with UUID(), FK references
- `db-manager/migrations/V9__solution_schema.sql` - Solution seed pattern with markdown content
- `db-manager/migrations/V8__collection_schema.sql` - Collection/collection_item pattern
- `db-manager/migrations/V16__recommendation_seed_problems.sql` - Valid problem IDs for FK resolution

### Secondary (HIGH confidence)
- `V1__core_schema.sql` lines 511-521 - Valid submission status values
- `backend-spring/modules/submission/service/impl/SubmissionServiceImpl.java` - SubmissionStatusMeta enum values
- `db-manager/migrations/V15__featured_problem_lists.sql` - Valid problem_lists for collection_items

---
*Research completed: 2026-04-19*
*Ready for roadmap: yes*
