<!-- Updated: 2026-06-10 | Migrations: 20 | Baseline tables: 67 + test_cases (V20260610120000) | Token estimate: ~700 -->

# Data Architecture

## Database: MySQL 9.1 (port 23306)

### Baseline (V20260602_120000)
67 tables created in a single baseline migration, including all FKs, indexes,
and seed schema. Subsequent migrations add columns / data / constraints.

### Table Groups
```
Identity       : users, roles, user_roles, user_follows, user_achievements,
                 achievements, badges
Problem        : problems, problem_tags, problem_test_cases, problem_examples,
                 problem_versions, problem_lists, problem_lists_version,
                 problem_details_content, test_cases          [new 2026-06-10]
Submission     : submissions, submission_retry_count, submission_memory_nullable,
                 solutions, solution_comments, solution_votes
Contest        : contests, contest_problems, contest_participants,
                 contest_submissions, contest_actual_times, scoring_rules
Forum          : forum_posts, post_comments, post_likes
Moderation     : moderation_queue, reports, moderation_actions, appeals
Notification   : notifications, notification_settings
Subscription   : subscriptions, subscription_plans
Edge ops       : edge_operations (vote / analyze / bookmark)
Auth           : refresh_tokens, password_reset_tokens, oauth_states,
                 csrf_tokens
Permission     : user_permissions, user_permission_expires_at  [new column 2026-06-10]
Audit          : audit_logs
Recommendation : recommendation_seed_problems, recommendation_seed_submissions,
                 daily_recommendations_feedback                [orphaned]
```

### Migrations (`init-db/migrations/`) — 20 files
```
V20260602_120000  Create_All_Tables                          [baseline, 67 tables]
V20260602_120100  Insert_Admin_User_And_Permissions          [seed admin + roles]
V20260603_120000  Seed_Problems_Test_Data
V20260603_120100  Seed_Audit_Logs_Test_Data
V20260603_120200  Seed_Problem_Lists_Test_Data
V20260603_120300  Seed_Users_And_Permissions
V20260603_120400  Seed_Solutions_Test_Data
V20260603_120500  Fix_Forum_User_References
V20260603_120600  Seed_Submissions_Test_Data
V20260603_120601  Fix_Submission_Test_Details_Json
V20260603_120700  Seed_Forum_Posts_Per_User
V20260603_120800  Seed_Comments_And_Interactions
V20260604_110000  Align_Admin_User_Id
V20260604_120000  Seed_Contests_Test_Data
V20260604_130000  Seed_Global_Rankings_Test_Data
V20260606_130000  Secure_Refresh_Tokens_And_Lock_Seed_Accounts  [security]
V20260608_120000  Fix_Audit_Logs_Performer_Id
V20260610_120000  Create_Test_Cases_Table                       [new]
V20260610_130000  Add_Test_Cases_Is_Deleted                     [new]
V20260610_140000  Add_User_Permission_Expires_At                [new]
```

> **Security migration note**: `V20260606130000` revokes/rotates refresh tokens
> and locks seed/dev accounts after the public release. The `AdminBootstrapRunner`
> (opt-in dev profile only) handles initial admin creation. This migration is
> **mandatory** to keep in history; do not delete.

## Migration Tool

### init-db/ (Flyway)
- **Location**: `/home/davidhlp/project/UltiCode/init-db/`
- **Version**: Timestamp-based Flyway versions
- **Config**: `flyway.conf` (baselineOnMigrate=true, outOfOrder=false)
- **Execution**: Maven plugin locally or Flyway 10.17 container in CI
- **Git Hook**: `validate-migration.sh` for naming convention
```
init-db/
├── README.md
├── flyway.conf
├── pom.xml           (Maven, Flyway 10.10.0)
├── migrations/       (20 SQL files)
├── sql/              (one-time dumps)
└── validate-migration.sh
```

### Migration Naming Convention
```
V{YYYYMMDDHHMMSS}__{Description}.sql
Example: V20260602120000__Create_All_Tables.sql
         V20260610120000__Create_Test_Cases_Table.sql
```

## Key Relationships

```
users ─┬─< submissions (1:N)
       ├─< solutions (1:N)
       ├─< forum_posts (1:N)
       ├─< edge_operations (1:N — bookmark / vote / analyze)
       ├─< user_follows (1:N, both follower / following)
       ├─< user_achievements (1:N)
       └─< user_permissions (1:N, expires_at)             [new 2026-06-10]

problems ─┬─< problem_test_cases (1:N)
          ├─< test_cases (1:N, soft-deleted)              [new 2026-06-10]
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
- Rate limiting (per-IP / per-user counters)
- CSRF token store (Redis-backed)
- Recommendation cache (legacy keys — orphaned after module removal)
