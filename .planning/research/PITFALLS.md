# Domain Pitfalls: Seed Data Expansion (v1.4)

**Domain:** Flyway SQL seed migrations for Solutions, Submissions, Collections
**Researched:** 2026-04-19
**Confidence:** HIGH

---

## Critical Pitfalls

Mistakes that cause migration failures or data integrity violations.

### Pitfall 1: Foreign Key Constraint Violations

**What goes wrong:** `Cannot add or update a child row: a foreign key constraint fails`

**Why it happens:** Referenced entity (user, problem, solution) does not exist in parent table.

**Consequences:** Migration halts. Flyway marks migration as failed. Requires `db-manager repair` to recover.

**Prevention:**

```sql
-- BEFORE inserting child rows, verify parent exists
-- User FK reference check
SELECT id FROM users WHERE id = 'user-emma';  -- Must return exactly 1 row

-- Problem FK reference check
SELECT id FROM problems WHERE id = 1;  -- Must return exactly 1 row

-- Solution FK reference check (for solution_comments)
SELECT id FROM solutions WHERE id = 'sol-001';  -- Must return exactly 1 row
```

**Detection:** Run this query before migration:

```sql
-- Verify all user FKs in seed data exist
SELECT DISTINCT user_id FROM (
    SELECT DISTINCT user_id FROM solutions
    UNION ALL
    SELECT DISTINCT user_id FROM solution_comments
    UNION ALL
    SELECT DISTINCT user_id FROM submissions
) AS seed_users
WHERE user_id NOT IN (SELECT id FROM users);
-- Expected result: Empty (0 rows)

-- Verify all problem FKs in seed data exist
SELECT DISTINCT problem_id FROM submissions
WHERE problem_id NOT IN (SELECT id FROM problems);
-- Expected result: Empty (0 rows)
```

---

### Pitfall 2: Invalid Submission Status Values

**What goes wrong:** `Duplicate entry for key 'PRIMARY'` if status key is wrong, or silent acceptance of invalid statuses.

**Why it happens:** Status column references `submission_statuses(key)` as FK, but seed data uses values not in that table.

**Valid status keys** (from `V1__core_schema.sql` lines 511-521):

| Key | Code | Category |
|-----|------|----------|
| Accepted | AC | success |
| Compile Error | CE | error |
| Judging | JDG | pending |
| Memory Limit Exceeded | MLE | error |
| Output Limit Exceeded | OLE | error |
| Pending | PD | pending |
| Presentation Error | PE | error |
| Runtime Error | RE | error |
| System Error | SE | system |
| Time Limit Exceeded | TLE | error |
| Wrong Answer | WA | error |

**Prevention:** Always use exact key values from `submission_statuses` table. Query before writing:

```sql
SELECT `key` FROM submission_statuses;
```

**Existing migration bug in V17:** Line 12 has leading space in `' Accepted'` -- this is incorrect. Must match exactly.

---

### Pitfall 3: Duplicate Primary Key Violations

**What goes wrong:** `Duplicate entry for key 'PRIMARY'` when ID already exists.

**Why it happens:**
- Re-running migration with same ID values
- ID collision with existing seed data
- Copy-paste error with duplicate ID

**Prevention:**
- Use `UUID()` for submission IDs (already used in V17)
- Prefix solution IDs with version: `sol-v1-001`
- Check max existing ID before inserting:

```sql
-- For solutions
SELECT id FROM solutions ORDER BY id DESC LIMIT 5;

-- For submissions
SELECT id FROM submissions ORDER BY id DESC LIMIT 5;
```

---

### Pitfall 4: Non-Existent Problem ID References

**What goes wrong:** Submission or solution references a problem ID that does not exist.

**Why it happens:** Problem IDs in seed data do not match existing problems.

**Prevention:** Query available problem IDs before writing seed:

```sql
SELECT id, slug, difficulty FROM problems ORDER BY id;
```

**Known valid problem IDs** (from V2 seed): 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 13, 14, 16, 17, 20, 22, 23, 24, 30, 31, 33, 35, 36, 40

**Note:** Problem ID 8 and 12 appear to be missing from V17 seed data. Verify before adding submissions for them.

---

## Moderate Pitfalls

### Pitfall 5: Foreign Key Check Toggle Errors

**What goes wrong:** `SET FOREIGN_KEY_CHECKS=0` does not disable all constraints, or migration fails if toggle is not properly reset.

**Why it happens:** `FOREIGN_KEY_CHECKS=0` only affects the current session. If connection resets or migration runs in separate transactions, constraints still apply.

**Prevention:**
- Always wrap in transaction:

```sql
SET FOREIGN_KEY_CHECKS=0;
START TRANSACTION;
-- INSERT statements
COMMIT;
SET FOREIGN_KEY_CHECKS=1;
```

- Use `START TRANSACTION` before inserts, `COMMIT` after all, then re-enable

---

### Pitfall 6: Incorrect Datetime Precision

**What goes wrong:** `Incorrect datetime value` or truncation warnings.

**Why it happens:** Tables use `datetime(3)` (millisecond precision) but seed data uses `NOW()` instead of `NOW(3)`.

**Prevention:** Always use `NOW(3)` for datetime(3) columns:

```sql
-- Correct
created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)

-- Use
NOW(3)

-- Not
NOW()          -- This is second precision only
NOW(6)         -- This is microsecond, will truncate
```

---

### Pitfall 7: JSON Column Encoding Issues

**What goes wrong:** Chinese characters in JSON columns appear as garbage or `null`.

**Why it happens:** Migration file encoding mismatch (UTF-8 vs Latin-1) or missing `CHARSET=utf8mb4`.

**Prevention:**
- Ensure migration file is saved as UTF-8
- Include `COLLATE utf8mb4_unicode_ci` in column definitions
- Verify with:

```sql
SELECT id, tags FROM solutions WHERE JSON_VALID(tags) = 0;
-- Should return 0 rows
```

---

### Pitfall 8: Parent-Child Table Ordering

**What goes wrong:** `Cannot add or update a child row` when child table is migrated before parent.

**Why it happens:** Inserting `solution_comments` before `solutions`, or `submissions` before `problems`.

**Prevention:** Follow dependency order:

```
1. users (V1)
2. problems (V2)
3. solutions (V9)
4. solution_comments (V9, after solutions)
5. submissions (V17)
```

---

### Pitfall 9: ID Format Mismatch

**What goes wrong:** `id` column is `varchar(40)` but using numeric ID causes silent truncation or comparison issues.

**Why it happens:** Solutions use string IDs (`sol-001`) while submissions use `UUID()`. Both are `varchar(40)`.

**Prevention:**
- Use consistent ID format per table
- For solutions: `sol-{number}` format
- For comments: `comment-{number}` format
- For submissions: `UUID()` (already used in V17)

---

### Pitfall 10: Logical Deletion Mismatch

**What goes wrong:** Seed data has `is_deleted=0` but parent entity is soft-deleted, causing orphaned relationships.

**Why it happens:** Queries filter out `is_deleted=1` but seed data was inserted without checking parent deletion status.

**Prevention:** Verify parent `is_deleted=0` before inserting child:

```sql
SELECT id, is_deleted FROM solutions WHERE id = 'sol-001';
-- Verify is_deleted = 0 before inserting comments
```

---

## Minor Pitfalls

### Pitfall 11: Column Order Mismatch

**What goes wrong:** `Column count doesn't match` error in INSERT.

**Why it happens:** INSERT column list does not match VALUES tuple count.

**Prevention:** Always specify columns explicitly:

```sql
-- GOOD
INSERT INTO solutions (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`)
VALUES (...);

-- BAD (depends on column order)
INSERT INTO solutions VALUES (...);
```

---

### Pitfall 12: Missing Optional Fields

**What goes wrong:** `Field 'xxx' doesn't have a default value`.

**Why it happens:** Column is NOT NULL but no default, and not included in INSERT.

**Prevention:** Check schema before inserting:

```sql
DESCRIBE solutions;
-- Note which columns are NOT NULL without default
```

---

### Pitfall 13: Runtime/Memory Zero for Non-Accepted

**What goes wrong:** In V17, non-Accepted submissions have `runtime=0` and `memory=0`. This may be semantically incorrect (indicating the judge never ran).

**Why it happens:** Placeholder values used for failed submissions.

**Prevention:** Consider using `NULL` for runtime/memory when submission failed before execution:

```sql
-- For WA/TLE/RE submissions where judge ran
VALUES (UUID(), 1, 'user-emma', 'typescript', '// code', 'Wrong Answer', 45, 42.3, NULL, ...)

-- For submissions that errored before judge ran
VALUES (UUID(), 1, 'user-emma', 'typescript', '// code', 'System Error', NULL, NULL, NULL, ...)
```

---

## Validation Checklist

Run before executing seed migration:

```sql
-- 1. Verify all referenced users exist
SELECT DISTINCT user_id FROM (
    SELECT user_id FROM solutions
    UNION SELECT user_id FROM solution_comments
    UNION SELECT user_id FROM submissions
) t
WHERE user_id NOT IN (SELECT id FROM users);

-- 2. Verify all referenced problems exist
SELECT DISTINCT problem_id FROM submissions
WHERE problem_id NOT IN (SELECT id FROM problems);

-- 3. Verify all solution_ids exist for solution_comments
SELECT DISTINCT solution_id FROM solution_comments
WHERE solution_id NOT IN (SELECT id FROM solutions);

-- 4. Verify status values are valid
SELECT DISTINCT status FROM submissions
WHERE status NOT IN (SELECT `key` FROM submission_statuses);

-- 5. Verify no duplicate IDs
SELECT id, COUNT(*) as cnt FROM solutions GROUP BY id HAVING cnt > 1;
SELECT id, COUNT(*) as cnt FROM submissions GROUP BY id HAVING cnt > 1;

-- 6. Verify JSON validity
SELECT id, tags FROM solutions WHERE JSON_VALID(tags) = 0;

-- 7. Verify no orphaned deleted parents
SELECT s.id FROM solutions s WHERE s.is_deleted = 1
AND EXISTS (SELECT 1 FROM solution_comments c WHERE c.solution_id = s.id AND c.is_deleted = 0);
```

Expected result for all checks: **Empty set (0 rows)**

---

## Phase-Specific Warnings

| Phase | Pitfall | Mitigation |
|-------|---------|------------|
| Solutions seed | FK to problems.users | Query problem IDs first |
| Solution comments seed | FK to solutions, users | Seed solutions before comments |
| Submissions seed | FK to users, problems | Query both tables first |
| Collections seed | FK to users, problems, tags | Verify tag entity exists |

---

## Known Issues in Existing Migrations

| Migration | Issue | Severity |
|-----------|-------|----------|
| V17__recommendation_seed_submissions.sql:12 | Leading space in `' Accepted'` | HIGH - will cause FK error |
| V17 | TLE/MLE/RE have `runtime=0, memory=0` | MEDIUM - unclear if judge ran |
| V9 | Chinese comments use emoji | LOW - ensure utf8mb4 encoding |

---

## Sources

- `V1__core_schema.sql` lines 511-521: Valid submission status values
- `V1__core_schema.sql` lines 349-370: Valid user IDs
- `V9__solution_schema.sql`: Solution schema with FK constraints
- `V17__recommendation_seed_submissions.sql`: Existing submission seed pattern
