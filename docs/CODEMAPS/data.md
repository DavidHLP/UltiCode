<!-- Generated: 2026-05-18 | Files scanned: 46 | Token estimate: ~800 -->

# Data Architecture

## Database: MySQL 9.1 (:23306)

### Core Tables (V1)

`users`, `roles`, `permissions`, `user_roles`, `role_permissions`

### Domain Tables (V2–V108)

| Migration | Domain | Key Tables |
|-----------|--------|------------|
| V2 | Problems | problems, problem_details, problem_tags, problem_tag_relations, problem_examples, test_cases, problem_languages |
| V3 | Contests | contests, contest_problems, contest_participants, contest_submissions, contest_announcements |
| V4 | Forum | forum_posts, forum_comments, post_likes |
| V5 | Subscriptions | subscription_plans, user_subscriptions |
| V6 | Moderation | moderation_queue, reports, moderation_actions, user_bans, user_warnings, appeals |
| V7 | Recommendations | user_daily_recommendations, recommendation_feedback |
| V8 | Collections | problem_list_categories, problem_lists, problem_list_problems, problem_list_bookmarks |
| V9 | Solutions | solutions, solution_votes, solution_comments |
| V22 | Achievements | achievements, user_achievements |
| V99 | Edge | edge_interactions |
| V100–V101 | Follow | follows, follow indexes |
| V103 | Problem Versions | problem_versions |

### Seed/Data Migrations

V10 (daily recs feedback), V11 (moderation seed), V12 (notification seed), V13 (solution content), V14 (solution stats), V15 (featured lists), V16–V17 (rec seeds), V23–V25 (solutions/submissions/collections seeds)

### Fix/Alter Migrations

V18 (submission retry), V19 (memory nullable), V20 (password reset), V21 (contest actual times), V26–V29 (encoding fixes), V30 (list version), V31 (problem details content), V104 (appeal enum), V105 (moderation constraints), V106 (encoding fix), V107–V108 (audit columns)

## Migration Tool

`db-manager/` — Python CLI using Flyway adapter. Commands: `migrate`, `info`, `validate`, `repair`, `baseline`, `clean`.

## Redis (:26379)

- Session cache
- Token blacklist
- Rate limiting counters
- CSRF token store
- Recommendation cache

## Nacos (:28848)

- Service discovery for Dubbo (recommendation provider)
- Configuration management
