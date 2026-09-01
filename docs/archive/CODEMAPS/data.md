<!-- Updated: 2026-06-18 | Migrations: 34 | Baseline tables: 67 + test_cases + problem_notes + solution_topics + edge_operations enum + judge_outbox + notification_delivery_ledger + virtual_contest_sessions + contest_rankings/analytics/scoring_rules + problem resource limits | Token estimate: ~740 -->

# Data Architecture

## Database: MySQL 9.1 (port 23306)

### Baseline (V20260602_120000)
67 tables created in a single baseline migration, including all FKs, indexes,
and seed schema. Subsequent migrations add columns / data / constraints.

### Table Groups
```
Identity       : users, roles, user_roles, user_follows, user_achievements,
                 achievements, badges
Problem        : problems (+ time/memory/cpus resource limits 2026-06-16),
                 problem_tags, problem_test_cases, problem_examples,
                 problem_versions, problem_lists, problem_lists_version,
                 problem_details_content, test_cases,
                 problem_notes                                                [new 2026-06-11]
Submission     : submissions, submission_retry_count, submission_memory_nullable,
                 solutions, solution_comments, solution_votes,
                 solution_topics,                                             [new 2026-06-11]
                 judge_outbox                                                 [new 2026-06-13 — verdict delivery, ADR-003]
Contest        : contests (+ slug/real unique 2026-06-17), contest_problems,
                 contest_participants, contest_submissions, contest_actual_times,
                 scoring_rules, contest_scoring_rules, contest_rankings,
                 contest_analytics, virtual_contest_sessions                  [scoring+virtual new 2026-06-13→17, ADR-006/007]
Forum          : forum_posts, post_comments, post_likes
Moderation     : moderation_queue, reports, moderation_actions, appeals
Notification   : notifications (is_deleted added 2026-06-11),
                 notification_preferences (system → system_enabled 2026-06-11),
                 notification_delivery_ledger                                 [new 2026-06-13]
Subscription   : subscriptions, subscription_plans
Edge ops       : edge_operations (enum extended: LIKE/DISLIKE/FAVORITE 2026-06-10)
Auth           : refresh_tokens, password_reset_tokens, oauth_states,
                 csrf_tokens
Permission     : user_permissions, user_permission_expires_at
Audit          : audit_logs
Recommendation : recommendation_seed_problems, recommendation_seed_submissions,
                 daily_recommendations_feedback                                [orphaned]
```

### Migrations (`init-db/migrations/`) — 34 files
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
V20260610_150000  Extend_Edge_Operations_For_Problem_Reactions [new — enum +LIKE/+DISLIKE/+FAVORITE]
V20260611_120000  Rename_Notification_Pref_System_Column       [new — MySQL 9.x reserved keyword]
V20260611_130000  Add_Notifications_Is_Deleted                 [new — logical delete]
V20260611_140000  Create_Solution_Topics_Table                 [new — 8 seeded topics]
V20260611_141000  Create_Problem_Notes_Table                   [new — user×problem 1:1]
V20260613_100000  Create_Judge_Outbox                          [new — verdict delivery outbox, ADR-003]
V20260613_110000  Add_Submission_Generation_And_Lease          [new — generation counter + queue lease]
V20260613_120000  Create_Notification_Delivery_Ledger          [new — delivery tracking]
V20260615_140000  Seed_Problem_Category_Tags                   [seed]
V20260616_000000  Seed_Missing_Test_Cases                      [seed]
V20260616_120000  Add_Problem_Resource_Limits                  [new — time/memory/cpus columns]
V20260617_120000  Contest_Scoring_Hardening                    [new — ADR-006 scoring engine]
V20260617_130000  Contest_Slug_Unique                          [new — unique constraint]
V20260617_140000  Contest_Real_Unique_And_Session_Length       [new — virtual_contest_sessions, ADR-007]
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
├── migrations/       (34 SQL files)
├── sql/              (one-time dumps)
└── validate-migration.sh
```

### Migration Naming Convention
```
V{YYYYMMDDHHMMSS}__{Description}.sql
Example: V20260602120000__Create_All_Tables.sql
         V20260610150000__Extend_Edge_Operations_For_Problem_Reactions.sql
```

## Key Relationships

```
users ─┬─< submissions (1:N)
       ├─< solutions (1:N)
       ├─< forum_posts (1:N)
       ├─< edge_operations (1:N — bookmark / vote / analyze / LIKE / DISLIKE / FAVORITE)
       ├─< user_follows (1:N, both follower / following)
       ├─< user_achievements (1:N)
       ├─< user_permissions (1:N, expires_at)
       └─< problem_notes (1:1 per user×problem)                          [new 2026-06-11]

problems ─┬─< problem_test_cases (1:N)
          ├─< test_cases (1:N, soft-deleted)
          ├─< problem_tags (M:N via join)
          ├─< problem_versions (1:N)
          ├─< submissions (1:N)
          └─< problem_notes (1:1 per user)                                [new 2026-06-11]

contests ─┬─< contest_problems (1:N)
          ├─< contest_participants (M:N)
          ├─< contest_rankings (1:N, scored)                              [new 2026-06-17]
          └─< virtual_contest_sessions (1:N, virtual replay)              [new 2026-06-17]

solutions ─┬─< solution_comments (1:N)
           ├─< solution_votes (1:N via edge_operations)
           └─< solution_topics (M:N, taxonomy)                             [new 2026-06-11]
```

## Redis (port 26379)

- Session store (Redisson)
- Rate limiting (per-IP / per-user counters)
- CSRF token store (Redis-backed)
- Recommendation cache (legacy keys — orphaned after module removal)
