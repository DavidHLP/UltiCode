# Architecture: Seed Data Expansion

**Domain:** Flyway migration patterns for database seed data
**Researched:** 2026-04-19
**Overall confidence:** HIGH (direct evidence from existing migrations)

## Executive Summary

UltiCode uses Flyway for database migrations with a clear pattern: schema migrations (tables, indexes, constraints) are separated from seed data migrations. Seed data for Solutions, Submissions, and Collections should be added as new migration files (V23, V24) following the established `V{n}__{description}.sql` naming convention. Referential integrity is maintained through foreign key constraints that reference existing `users.id`, `problems.id`, `solutions.id`, and `collections.id` records.

## Migration Version Discovery

**Current highest version:** V22 (`V22__achievement_schema.sql`)

**Next seed migration should be:** V23

Existing seed-only migrations:
| Version | File | Content |
|---------|------|---------|
| V11 | `V11__moderation_seed_data.sql` | Moderation seed data |
| V12 | `V12__notification_seed_data.sql` | Notification seed data |
| V16 | `V16__recommendation_seed_problems.sql` | Problem seed data for recommendations |
| V17 | `V17__recommendation_seed_submissions.sql` | Submission seed data (~400 rows) |

## Migration File Pattern

### Standard Structure

Every migration file follows this pattern:

```sql
SET FOREIGN_KEY_CHECKS=0;

-- UltiCode Migration: V{n}__{description}
-- [Optional: Generated from... or Purpose comment]

-- [Schema definitions if applicable]
CREATE TABLE ...

-- [Seed Data if applicable]
INSERT INTO `table` ...

SET FOREIGN_KEY_CHECKS=1;
```

### File Naming Convention

```
V{version}__{description}.sql
```

Examples:
- `V8__collection_schema.sql` - Schema creation (tables)
- `V9__solution_schema.sql` - Schema + seed data (mixed)
- `V17__recommendation_seed_submissions.sql` - Seed data only

**Important:** The CLAUDE.md notes that `db-manager` does not support `-outOfOrder`, so migrations must be sequential. V23 must come after V22.

## Seed Data Migration Patterns

### Pattern 1: Schema + Seed Combined (V8, V9)

Used when table creation and initial data are tightly coupled.

```sql
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE `solutions` (
  `id` varchar(40) ...,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) ...,
  ...,
  CONSTRAINT `solutions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE,
  CONSTRAINT `solutions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
);

-- Seed Data
INSERT INTO `solutions` ...;
INSERT INTO `solution_comments` ...;

SET FOREIGN_KEY_CHECKS=1;
```

### Pattern 2: Seed Data Only (V11, V12, V17)

Used for standalone seed data that does not create tables.

```sql
SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V17__recommendation_seed_submissions
-- Seed submission data for recommendation engine testing
-- ~400 submissions across 19 users and 40 problems

-- Section comments for organization
-- ============================================================================

-- user-emma: mostly Easy with some WA
INSERT INTO `submissions` ...;
INSERT INTO `submissions` ...;

SET FOREIGN_KEY_CHECKS=1;
```

## Referential Integrity Requirements

### User References (user_id)

All seed data referencing users must use **existing user IDs** from V1:

| user_id | Source Migration |
|---------|------------------|
| user-chen | V1 (seed) |
| user-yuki | V1 (seed) |
| user-alex | V1 (seed) |
| user-tourist | V1 (seed) |
| user-sara | V1 (seed) |
| user-max | V1 (seed) |
| user-petr | V1 (seed) |
| user-emma | V1 (seed) |
| user-lily | V1 (seed) |
| user-scott | V1 (seed) |
| user-tom | V1 (seed) |
| user-david | V1 (seed) |
| user-kevin | V1 (seed) |
| user-benq | V1 (seed) |
| u-001 | V1 (seed) |
| u-002 | V1 (seed) |
| user-ecnerwala | V1 (seed) |
| user-jiangly | V1 (seed) |
| user-um_nik | V1 (seed) |

### Problem References (problem_id)

All seed data referencing problems must use **existing problem IDs** from V2 or V16:

| problem_id range | Source Migration |
|------------------|------------------|
| 1-40 | V16 (recommendation problems) |

V17 seed submissions use problem_id values 1-40, confirming this range is valid.

### Solution References (solution_id)

For `solution_comments`, the `solution_id` must reference an existing `solutions.id`:

```sql
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, ...) VALUES
('comment-001', 'sol-001', NULL, 'user-max', ...),
('comment-002', 'sol-001', 'comment-001', 'user-yuki', ...);  -- parent_id references comment-001
```

### Collection References

For `collection_items`, the `collection_id` must reference an existing `collections.id`:

```sql
INSERT INTO `collection_items` (`id`, `collection_id`, `target_id`, `target_type`, ...) VALUES
('3a6a9426...', 'd550db4c...', 'list-sliding-window', 'PROBLEM_LIST', ...);
```

Where `d550db4c...` is a valid collection ID from the `collections` table.

## UUID Generation Pattern

V17 uses `UUID()` for submission IDs:

```sql
INSERT INTO `submissions` (`id`,`problem_id`,`user_id`,`language`,`code`,`status`,...) VALUES
(UUID(),1,'user-emma','typescript','// two-sum solution', 'Accepted',...);
```

For new seed data, use `UUID()` for primary keys to avoid collisions.

## Timing Considerations

### Dependencies Graph

```
V1 (users) --> V9 (solutions references users)
             --> V8 (collections references users)
             --> V17 (submissions references users)

V2 (problems) --> V9 (solutions references problems)
                --> V17 (submissions references problems)

V8 (collections) --> V8 (collection_items references collections)

V9 (solutions) --> V9 (solution_comments references solutions)
```

### Seed Data Insertion Order

When adding new seed data for Solutions, Submissions, or Collections:

1. **Users must exist first** (V1) - verified by existing migrations
2. **Problems must exist first** (V16) - verified by existing migrations
3. **Solutions must exist before solution_comments** - parent solution must exist
4. **Collections must exist before collection_items** - parent collection must exist

## New Seed Migration Recommendation

For adding Solutions, Submissions, and Collections seed data:

| New Migration | Content | Dependencies |
|---------------|---------|--------------|
| V23 | Solutions seed (if expanding beyond V9) | V1 users, V2 problems |
| V24 | Submissions seed (if expanding beyond V17) | V1 users, V2 problems |
| V25 | Collection_items seed (if expanding beyond V8) | V8 collections |

## Anti-Patterns to Avoid

1. **Do not use `-outOfOrder`** - db-manager does not support it; new migrations must be sequential
2. **Do not disable foreign key checks permanently** - Only wrap seed inserts
3. **Do not reference non-existent user_id/problem_id** - Will cause FK constraint failure
4. **Do not mix schema changes with seed data unless tightly coupled** - Keep separate for clarity
5. **Do not use literal IDs for users** - Use the seed user IDs (user-yuki, user-alex, etc.)

## Sources

- `db-manager/migrations/V8__collection_schema.sql` - Collection schema + seed pattern
- `db-manager/migrations/V9__solution_schema.sql` - Solution schema + seed pattern
- `db-manager/migrations/V17__recommendation_seed_submissions.sql` - Seed-only pattern
- `db-manager/migrations/V11__moderation_seed_data.sql` - Seed-only pattern
- `db-manager/migrations/V12__notification_seed_data.sql` - Seed-only pattern
