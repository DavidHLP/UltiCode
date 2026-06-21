---
title: Database Schema Overview
type: overview
tags: [database, schema, map, type/overview]
status: living
updated: 2026-06-21
sources:
  - init-db/migrations/
  - backend-spring/src/main/java/com/ulticode/modules/*/entity/
---

# Database Schema Overview

> [!quote] Essence
> MySQL 9.1 (port 23306). Schema is owned by **Flyway migrations** in
> `init-db/migrations/` — the only source. This page maps the major tables by
> domain and traces the migration timeline (especially the security + idempotency
> hardening). Discipline: [[concepts/flyway-migration-discipline]].

## Conventions

- **Primary key**: `id` is a UUID `String` (not auto-increment).
- **Audit columns**: every table has `create_time`, `update_time`.
- **Soft delete**: `is_deleted` (entity property `deleted`, MyBatis maps `is_deleted`).
- **Booleans**: DB column `is_xxx`, entity property drops the `is_` prefix.
- **Naming**: table/columns `snake_case`; MyBatis-Plus maps to camelCase entities
  (`mapUnderscoreToCamelCase = true`). See [[concepts/result-envelope-and-case-mapping]].

## Tables by domain

Derived from `@TableName` annotations. Representative, not exhaustive — grep
`@TableName` in a module's `entity/` for the authoritative list.

**Judging**
- `submissions` — the core submission row (status, generation, lease).
- `judge_outbox` — outbox for at-least-once enqueue ([[concepts/exactly-once-judging]]).
- `test_cases` — per-problem cases (`V20260610120000`).

**Problems & learning**
- `problems`, `problem_details`, `problem_examples`, `problem_languages`,
  `problem_tags`, `problem_tag_relations`, `problem_versions`, `problem_notes`.
- `problem_lists`, `problem_list_problem_relations`, `problem_list_categories`,
  `problem_list_bookmarks`.
- `solutions`, `solution_comments`, `solution_topics`.

**Contests**
- `contests`, `contest_participants`, `contest_problems`, `contest_problem_results`,
  `contest_submissions`, `contest_announcements`, `contest_scoring_rules`,
  `first_solve_records`, `global_rankings`.

**Users & identity**
- `users`.
- `refresh_tokens` (hash-only — [[concepts/refresh-token-hash-only-storage]]).
- `user_permissions`, `role_permissions`.
- `user_follows`.

**Community & comms**
- `forum_posts`, `forum_comments`, `forum_communities`, `forum_community_members`,
  `forum_tags`, `forum_users`.
- `notifications`, `notification_preferences`, `notification_delivery_ledger`.
- `email_logs`, `email_templates`.

**Trust & safety**
- `reports`, `moderation_queue`, `moderation_actions`, `user_warnings`, `user_bans`,
  `appeals`.
- `achievements`, `user_achievements`.
- `edge_operations` (likes/saves — `vote`/`edgeoperations` module).
- `collections`, `collection_items` (bookmarks).

**Platform**
- `audit_logs`, `system_settings`.
- `translations` (i18n).
- `backups`.

## Migration timeline (35 files)

```
2026-06-02  V…Create_All_Tables              ← baseline schema
            V…Insert_Admin_User_And_Permissions
2026-06-03  V…Seed_*_Test_Data  (×8)          ← demo seed
2026-06-04  V…Align_Admin_User_Id
            V…Seed_Contests_Test_Data
            V…Seed_Global_Rankings_Test_Data
2026-06-06  V…Secure_Refresh_Tokens_… ★       ← SECURITY: hash-only refresh, lock seeds
2026-06-08  V…Fix_Audit_Logs_Performer_Id
2026-06-10  V…Create_Test_Cases_Table
            V…Add_Test_Cases_Is_Deleted
            V…Add_User_Permission_Expires_At
            V…Extend_Edge_Operations_For_Problem_Reactions
2026-06-11  V…Rename_Notification_Pref_System_Column
            V…Add_Notifications_Is_Deleted
            V…Create_Solution_Topics_Table
            V…Create_Problem_Notes_Table
2026-06-13  V…Create_Judge_Outbox ★           ← IDEMPOTENCY: judging outbox
            V…Add_Submission_Generation_And_Lease ★  ← generation fence + lease
            V…Create_Notification_Delivery_Ledger ★  ← notification exactly-once
2026-06-15  V…Seed_Problem_Category_Tags
2026-06-16  V…Seed_Missing_Test_Cases
            V…Add_Problem_Resource_Limits
2026-06-17  V…Contest_Scoring_Hardening ★     ← contest integrity
            V…Contest_Slug_Unique
            V…Contest_Real_Unique_And_Session_Length ★  ← virtual-contest fencing
2026-06-19  V…Seed_Moderation_Data
```

The three **★ inflection points** are the design backbone: 06-06 security,
06-13 idempotency, 06-17 contest integrity. Each has a matching concept page.

## Seed accounts

> [!warning] Don't break this chain
> - Demo/dev seed users were inserted in the 06-03 batch.
> - `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts` **locks** those
>   seed accounts afterward — keep it after any demo seed, never delete it.
> - The real admin is created only by the opt-in `AdminBootstrapRunner`
>   (dev-profile-only, off in normal startup). **Migrations must never insert a
>   usable default user or a public password.**

## Working with migrations

- New change → new `V{timestamp}__Description.sql`, timestamp larger than the last.
- **Never edit an already-applied migration** — checksum mismatch aborts migrate.
- Apply via `./scripts/dev/migrate.sh migrate` (the wrapper; raw `flyway` isn't on
  PATH). Repair: `./scripts/dev/migrate.sh repair`. See
  [[concepts/flyway-migration-discipline]].
- Verify locally: `./scripts/dev/test.sh integration` (Testcontainers MySQL 9.1).

## Links out

> [!link] Related pages
> - [[concepts/flyway-migration-discipline]]
> - [[concepts/refresh-token-hash-only-storage]] · [[concepts/exactly-once-judging]]
> - [[concepts/virtual-contest]]