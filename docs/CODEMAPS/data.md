<!-- Generated: 2026-05-30 | Migrations: 42 (+init-db) | Token estimate: ~850 -->

# Data Architecture

## Database: MySQL 9.1 (port 23306)

### Core Tables (V1–V8)
```
users, roles, user_roles
problems, problem_tags, problem_test_cases, problem_examples
contests, contest_problems, contest_participants, contest_submissions
forum_posts, post_comments, post_likes
subscriptions, subscription_plans
moderation_queue, reports, moderation_actions, appeals
```

### Extended Tables (V9–V31)
```
solutions, solution_comments, solution_votes           (V9, V13–V14, V27–V28)
daily_recommendations_feedback                         (V10)
moderation_seed_data, notification_seed_data           (V11–V12)
featured_problem_lists                                 (V15)
recommendation_seed_problems, recommendation_seed_submissions (V16–V17)
submission_retry_count, submission_memory_nullable     (V18–V19)
password_reset_columns                                 (V20)
contest_actual_times                                   (V21)
achievements, user_achievements                        (V22)
solutions_seed, submissions_seed, collections_seed     (V23–V25)
problem_lists_version, problem_details_content         (V30–V31)
```

### New Tables (V99–V108)
```
edge_operations                                        (V99) — vote/analyze ops on solutions, comments, posts, problems, lists
user_follows                                           (V100–V101) — follow relationships + composite indexes
problem_versions                                       (V103) — version snapshots with snapshot_json, change_type, change_summary
```

### Recent Alterations
```
V104: moderation_actions/queue — added APPEAL_REJECTED to enum
V105: reports/moderation_queue/appeals — unique + CHECK constraints
V106: problem_lists — fixed double-encoded banner_tag
V107: contest_participants — added created_at, updated_at
V108: contest_problems — added created_at, updated_at
```

## Migration Tools

### 1. db-manager/ (Spring Boot integrated)
- 41 migrations total (V1–V108, with gap V32–V98, plus V26.1 sub-version)
- Latest: V108__add_audit_columns_to_contest_problems.sql
- Commands: `migrate`, `info`, `validate`, `repair`, `baseline`, `clean --force`

### 2. init-db/ (Standalone Flyway - NEW)
- **Location**: `/home/david/project/UltiCode-Public-Next/init-db/`
- **Version**: Timestamp-based `V{YYYYMMDDHHMMSS}` format
- **Baseline**: `V20260530130501__Baseline.sql` — 67 tables from existing database
- **Config**: `flyway.conf` (baselineOnMigrate=true, outOfOrder=false)
- **Maven**: `pom.xml` with Flyway 10.10.0
- **Git Hook**: `validate-migration.sh` for naming convention
```
init-db/
├── README.md
├── flyway.conf
├── pom.xml
├── migrations/
│   └── V20260530130501__Baseline.sql  (67 tables, 1258 lines)
└── sql/
    └── 20260530_ulticode_dump.sql     (original backup)
```

### Migration Naming Convention
```
V{YYYYMMDDHHMMSS}__{Description}.sql
Example: V20260530130501__Baseline.sql
         V20260601120000__AddNewFeature.sql
```

## Key Relationships

```
users ─┬─< submissions (1:N)
       ├─< solutions (1:N)
       ├─< forum_posts (1:N)
       ├─< bookmarks (1:N via edge_operations)
       ├─< user_follows (1:N, both follower/following)
       └─< user_achievements (1:N)

problems ─┬─< problem_test_cases (1:N)
          ├─< problem_tags (M:N via join)
          ├─< problem_versions (1:N)
          └─< submissions (1:N)

contests ─┬─< contest_problems (1:N)
          └─< contest_participants (M:N)

solutions ─┬─< solution_comments (1:N)
           └─< solution_votes (1:N via edge_operations)
```

## Redis (port 26379)

- Session store (Redisson)
- Rate limiting
- Recommendation cache (RedisRecommendationStore)
- CSRF token store