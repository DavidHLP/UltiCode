---
title: Data Model (MySQL 9.1 + Flyway)
tags: [reference, database, architecture, living]
status: living
updated: 2026-06-19
owner: backend
generator: ecc:update-codemaps
---

# Data Model (MySQL 9.1 + Flyway)

<!-- Generated: 2026-06-19 | Migrations: 34 | DB: ulticode | Port: 23306 | Token estimate: ~900 -->

## Schema Source of Truth

`init-db/migrations/V*.sql` (append-only). Applied by `ulticode-init-db` PM2 one-shot before backend boot. **Never edit applied migrations** — always add a new timestamped one.

## Table Groups

### Identity & Permissions
`users` · `user_permissions` · `user_permission_expires_at` (added V20260610) · `user_follow` · `user_bans` · `oauth_states` · `refresh_tokens` (hash-only since V20260606) · `bookmarks` · `bookmark_folders`

### Forum & Community
`forum_communities` · `forum_community_members` · `forum_posts` · `forum_comments` · `forum_tags` · `forum_post_tags` · `forum_users` · `forum_user_references` (seed-fixed V20260603_120500)

### Problems & Solutions
`problems` (+ `time_limit_ms`/`memory_limit_kb`/`cpus` from V20260616120000) · `problem_versions` · `problem_notes` (V20260611141000) · `problem_lists` · `problem_list_items` · `test_cases` (+ `is_deleted` from V20260610130000) · `solutions` · `solution_topics` (V20260611140000) · `problem_tags` (seeded V20260615140000)

### Submissions & Judging (outbox pattern, ADR-003)
`submissions` (+ `generation` + `lease` + `lease_expires_at` from V20260613110000) · `judge_outbox` (V20260613100000) · `submission_verdicts`

### Contests (ADR-006, ADR-007)
`contests` (+ unique `slug` V20260617130000, unique `real_contest_id` V20260617140000) · `contest_problems` · `contest_participants` · `contest_participant_status` · `contest_submissions` · `contest_problem_results` · `contest_announcements` · `scoring_rules` (+ activation hardening V20260617120000) · `virtual_contest_sessions` (V20260617140000) · `first_solve_records` · `global_rankings` · `rating_titles`

### Notifications & Delivery
`notifications` (+ `is_deleted` V20260611130000, `is_system` renamed V20260611120000) · `notification_delivery_ledger` (V20260613120000) · `email_logs` · `email_templates`

### Moderation & Audit
`moderation_queue` · `reports` · `moderation_actions` · `appeals` · `audit_logs` (`performer_id` fixed V20260608120000)

### Edge / Reactions (V20260610150000)
`edge_operations` — LIKE / DISLIKE / FAVORITE / SAVE on problems/posts/comments

### System / Admin
`system_settings` · `backups` (+ `status`, `type` enums) · `i18n_translations` · `achievements` · `user_achievements`

## Key Relationships (simplified)

```
users ─┬─ user_permissions ─ user_bans
       ├─ user_follow (self-join)
       ├─ submissions ─ judge_outbox
       ├─ contest_participants ─ contest_submissions ─ contest_problem_results
       │                       └─ virtual_contest_sessions (isolated rating)
       ├─ forum_posts ─ forum_comments ─ forum_post_tags
       ├─ bookmarks / bookmark_folders
       └─ notifications ─ notification_delivery_ledger

contests ─┬─ contest_problems ─ contest_problem_results
          ├─ scoring_rules
          ├─ contest_announcements
          └─ contest_participants

problems ─┬─ problem_versions
          ├─ problem_notes
          ├─ test_cases
          ├─ problem_lists ─ problem_list_items
          └─ edge_operations

solutions ─ solution_topics
audit_logs (actor → action → target) [performer_id fixed]
```

## Contest Rating Isolation (ADR-007, ADR-009)

- `contests.is_rated` gates rating update
- `virtual_contest_sessions.real_contest_id` ties virtual to real contest
- `global_rankings` updated only on `FINISHED` + `is_rated=true` + not virtual replay

## Flyway Migration Index (34)

Baseline `V20260602_120000__Create_All_Tables.sql` (full schema bootstrap) — see `init-db/migrations/` for full list. Notable post-baseline:

| Migration                                  | Purpose                                  |
| ------------------------------------------ | ---------------------------------------- |
| V20260606130000__Secure_Refresh_Tokens_... | bcrypt + lock seed accounts (security)   |
| V20260608120000__Fix_Audit_Logs_Performer_Id | FK correction                          |
| V20260610120000__Create_Test_Cases_Table   | Standalone test-case CRUD                |
| V20260610140000__Add_User_Permission_Expires_At | TTL on user perms                |
| V20260610150000__Extend_Edge_Operations_For_Problem_Reactions | LIKE/FAVORITE |
| V20260611140000__Create_Solution_Topics_Table | Solution topic tags                |
| V20260611141000__Create_Problem_Notes_Table | User problem notes                    |
| V20260613100000__Create_Judge_Outbox       | ADR-003 outbox                            |
| V20260613110000__Add_Submission_Generation_And_Lease | Outbox fencing                  |
| V20260613120000__Create_Notification_Delivery_Ledger | Per-channel delivery tracking    |
| V20260615140000__Seed_Problem_Category_Tags | Seed                                    |
| V20260616000000__Seed_Missing_Test_Cases   | Seed                                     |
| V20260616120000__Add_Problem_Resource_Limits | time/memory/cpus                       |
| V20260617120000__Contest_Scoring_Hardening  | ADR-006 activation                       |
| V20260617130000__Contest_Slug_Unique        | Unique slug                              |
| V20260617140000__Contest_Real_Unique_And_Session_Length | ADR-007 virtual sessions   |

## Container Character Set

Container defaults to `character_set_client=latin1` — **always** `mysql --default-character-set=utf8mb4` for `docker exec` (CLAUDE.md "MySQL 容器化操作"). Application JDBC uses `useUnicode=true&characterEncoding=UTF-8`.
