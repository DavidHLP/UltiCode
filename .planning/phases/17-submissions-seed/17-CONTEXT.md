# Phase 17: Submissions Seed (V24) - Context

**Gathered:** 2026-04-19
**Status:** Ready for planning
**Source:** ROADMAP.md + V17/V23 migrations analysis

<domain>
## Phase Boundary

Seed ~200 submissions for the submissions table, covering all 32 problems with realistic status distribution (AC/WA/TLE/MLE/RE/CE). This is the V24 migration.

</domain>

<decisions>
## Implementation Decisions

### SUB-03: Whitespace Bug Fix
- **Problem**: V17 migration used status values with leading/trailing whitespace (`' Accepted'`, `'Wrong Answer'`), causing SUB-03 failure
- **Fix**: V24 must use TRIM() on all status values OR insert enum-compliant strings
- **Verification**: `SELECT DISTINCT status FROM submissions` must return exactly: AC, WA, TLE, MLE, RE, CE (no whitespace)

### SUB-02: Status Distribution
- Distribution must approximate:
  - AC: 45-55%
  - WA: 20-30%
  - TLE: 8-12%
  - RE: 5-10%
  - MLE: 3-5%
  - CE: 2-5%
- For ~200 submissions: ~105 AC, ~50 WA, ~20 TLE, ~15 RE, ~8 MLE, ~6 CE

### SUB-01: Valid Status Values
- Enum values: AC, WA, TLE, MLE, RE, CE (from submission_statuses table)
- No leading/trailing whitespace
- V24 migration inserts must use these exact values

### SUB-04: Valid FK References
- user_id must reference existing users (user-emma, user-yuki, user-sara, user-lily, user-max, user-alex, etc.)
- problem_id must reference 1-32 (existing problems)
- No orphaned FKs

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `db-manager/migrations/V23__solutions_seed.sql` — Phase 16 output (97 solutions, valid FKs pattern)
- `db-manager/migrations/V17__recommendation_seed_submissions.sql` — Previous submissions seed (HAS whitespace bug, SUB-03)
- `backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java` — Entity for column names
- `.planning/ROADMAP.md` — Phase 17 success criteria

</canonical_refs>

<specifics>
## Specific Ideas

### V24 Migration Structure
- File: `db-manager/migrations/V24__submissions_seed.sql`
- ~200 INSERT statements covering all 32 problems
- Each problem gets 5-8 submissions across different users
- Languages: typescript, javascript, python, java, cpp, bash

### V17 Whitespace Bug Reference
```sql
-- WRONG (V17):
VALUES (UUID(),1,'user-emma','typescript','// two-sum',  ' Accepted',65,42.3,NULL,...)
-- CORRECT (V24):
VALUES (UUID(),1,'user-emma','typescript','// two-sum',  'AC',65,42.3,NULL,...)
```

### Valid Users for FK
user-emma, user-yuki, user-sara, user-lily, user-max, user-alex, user-chen, user-raj, user-kim, user-sophie, user-john, user-mike, user-lisa, user-bob, user-alice, admin

</specifics>

<deferred>
## Deferred Ideas

None — Phase 17 scope is well-defined

</deferred>

---
*Phase: 17-submissions-seed*
*Context gathered: 2026-04-19*
