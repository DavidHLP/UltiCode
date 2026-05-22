<!-- Generated: 2026-05-23 | Migrations: 41 | Token estimate: ~800 -->

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

### Migration File List (41 files)
```
V1__core_schema.sql
V2__problem_schema.sql
V3__contest_schema.sql
V4__forum_schema.sql
V5__subscription_schema.sql
V6__moderation_schema.sql
V7__recommendation_schema.sql
V8__collection_schema.sql
V9__solution_schema.sql
V10__daily_recommendations_feedback.sql
V11__moderation_seed_data.sql
V12__notification_seed_data.sql
V13__solution_enrich_content.sql
V14__solution_stats.sql
V15__featured_problem_lists.sql
V16__recommendation_seed_problems.sql
V17__recommendation_seed_submissions.sql
V18__add_submission_retry_count.sql
V19__submission_memory_nullable.sql
V20__add_password_reset_columns.sql
V21__add_contest_actual_times.sql
V22__achievement_schema.sql
V23__solutions_seed.sql
V24__submissions_seed.sql
V25__collections_seed.sql
V26__fix_problem_lists_encoding.sql
V26.1__fix_moderation_encoding.sql
V27__solution_add_is_pinned.sql
V28__fix_two_sum_solutions.sql
V29__fix_problem_details_encoding.sql
V30__problem_lists_add_version.sql
V31__add_problem_details_content.sql
V99__edge_schema.sql
V100__follow_schema.sql
V101__follow_indexes.sql
V103__add_problem_version_table.sql
V104__add_appeal_rejected_to_moderation_enum.sql
V105__moderation_constraints.sql
V106__fix_problem_lists_banner_tag_encoding.sql
V107__add_audit_columns_to_contest_participants.sql
V108__add_audit_columns_to_contest_problems.sql
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

## Migration Tool

`db-manager/` — Flyway-based Python CLI
- 41 migrations total (V1–V108, with gap V32–V98, plus V26.1 sub-version)
- Latest: V108__add_audit_columns_to_contest_problems.sql
- Commands: `migrate`, `info`, `validate`, `repair`, `baseline`, `clean --force`
