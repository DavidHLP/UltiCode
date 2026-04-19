# Seed Data Generation Stack

**Project:** UltiCode v1.4 Seed Data Expansion
**Researched:** 2026-04-19
**Confidence:** HIGH

## Recommendation

**Approach:** Python script generator using `Faker` + Jinja2 templates, output as Flyway-compatible SQL migration files.

**Why not alternatives:**
- **datafaker-java**: Requires JVM runtime in db-manager's Python venv -- unnecessary complexity
- **Fully handwritten SQL**: Tedious, error-prone for 100+ rows with foreign key constraints
- **In-application seeding**: Bloats application code, should live in migrations

## Recommended Stack

### Tool: Python Faker (18.x) + Jinja2

| Component | Version | Purpose |
|-----------|---------|---------|
| `Faker` | 18.x | Generate realistic names, text, UUIDs, dates |
| `Jinja2` | 3.x | Template SQL INSERT statements |

**Installation (db-manager venv):**
```bash
db-manager/.venv/bin/pip install faker jinja2
```

### Output: Flyway SQL Migration

| Layer | Responsibility |
|-------|---------------|
| **Generator script** | `db-manager/scripts/generate_seed_data.py` -- reads templates, outputs SQL |
| **SQL output** | `db-manager/migrations/V{n}__seed_{feature}.sql` -- committed, run via Flyway |
| **Templates** | `db-manager/scripts/templates/` -- Jinja2 templates per table |

## Architecture

```
db-manager/scripts/
├── generate_seed_data.py      # Main entry point
├── faker_providers.py         # Custom Faker providers (language names, problem slugs)
└── templates/
    ├── solutions.sql.j2
    ├── submissions.sql.j2
    └── collections.sql.j2
```

### Data Flow

```
generator.py → reads existing users/problems from DB → Jinja2 template → V{n}__seed_*.sql → Flyway migrate
```

**Key insight:** The script reads `users.id` and `problems.id` at generation time to avoid hardcoding foreign keys in templates.

## Existing Patterns to Follow

V17 (`V17__recommendation_seed_submissions.sql`) provides the reference pattern:

```sql
SET FOREIGN_KEY_CHECKS=0;
-- Header comment with scope
INSERT INTO `submissions` (...) VALUES (UUID(),...);  -- UUID() for id
INSERT INTO `submissions` (...) VALUES (UUID(),...);
SET FOREIGN_KEY_CHECKS=1;
```

**Conventions confirmed:**
- `UUID()` for all `varchar(40)` primary keys
- `NOW(3)` for `datetime(3)` timestamps
- `NULL` for nullable fields (not `''` or `'NULL'`)
- `FOREIGN_KEY_CHECKS=0` wrapping all inserts
- Inline comment per user group for readability

## Seed Data Targets

| Table | Target Rows | Key Fields |
|-------|-------------|------------|
| `solutions` | ~100 | `id`, `problem_id` (FK), `user_id` (FK), `title`, `content` (markdown), `language`, `tags` (JSON), `views` |
| `submissions` | ~200 | `id`, `problem_id`, `user_id`, `language`, `code`, `status` (diverse: AC/WA/TLE/RE/MLE), `runtime`, `memory`, `created_at` (spanning months) |
| `collections` | ~50 | `id`, `user_id`, `name`, `description`, `icon`, `color`, `is_default` |
| `collection_items` | ~150 | `id`, `collection_id`, `target_id`, `target_type` (enum), `sort_order`, `note` |

## Status Distribution for Submissions

| Status | Percentage | Rationale |
|--------|-------------|-----------|
| Accepted | 60% | Realistic pass rate |
| Wrong Answer | 20% | Common beginner failure |
| Time Limit Exceeded | 10% | Algorithm inefficiency |
| Runtime Error | 5% | Edge case bugs |
| Memory Limit Exceeded | 3% | Large input handling |
| Compilation Error | 2% | Syntax mistakes |

## Implementation Notes

1. **Run generator after V17** -- depends on existing users (user-yuki, user-alex, etc.) and problems (IDs 1-40)

2. **Language distribution** -- use actual judge worker whitelist: `typescript`, `javascript`, `python`, `java`, `bash`

3. **Timestamp spanning** -- submissions should span `2025-11` to `2026-02` to test recommendation engine time-range queries

4. **Solution content** -- markdown content with code blocks, realistic Chinese commentary (Faker `locale='zh_CN'`)

5. **Collection organization** -- group by difficulty (`list-easy`, `list-medium`, `list-hard`) and topic (`list-arrays`, `list-dp`, `list-intervals`)

## Migration Ordering

```
V17__recommendation_seed_submissions.sql  (existing, ~400 rows)
     ↓
V18__add_submission_retry_count.sql        (existing, schema change)
     ↓
V19__submission_memory_nullable.sql         (existing, schema change)
     ↓
V20__add_password_reset_columns.sql         (existing, schema change)
     ↓
V21__add_contest_actual_times.sql           (existing, schema change)
     ↓
V22__achievement_schema.sql                 (existing, schema change)
     ↓
V23__seed_solutions.sql                    (NEW: ~100 solutions)
     ↓
V24__seed_submissions_expanded.sql          (NEW: ~200 diverse submissions)
     ↓
V25__seed_collections.sql                  (NEW: ~50 collections + items)
```

## Sources

- V17__recommendation_seed_submissions.sql -- existing submission seed pattern
- V8__collection_schema.sql -- existing collection seed pattern
- V9__solution_schema.sql -- existing solution seed pattern
- V16__recommendation_seed_problems.sql -- problem data for FK resolution
