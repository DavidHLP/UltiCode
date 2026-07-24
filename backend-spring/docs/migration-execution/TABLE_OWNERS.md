# Table Owner Manifest

Source: `init-db/migrations/V20260602_120000__Create_All_Tables.sql` plus
Phase 0 additions (`oauth_provider_identities`).

Target Owners per guide §5:
- **Auth** (`backend-auth`): accounts, credentials, refresh tokens, RBAC,
  OAuth identities.
- **Admin** (`backend-admin`): governance, moderation, audit, system ops.
- **App** (`backend-app`): OJ business (Problem, Submission, Contest, Forum,
  Solution, Notification, Achievement, Judge, Search).

Access method codes (per guide §5):
- **I** (Owner internal): direct DB within Owner service.
- **Q** (Query): batch query or local materialized projection; cross-Owner.
- **C** (Command): idempotent RPC cross-Owner.
- **E** (Event): downstream consumes from upstream event/outbox.
- **R** (Read-only): cross-Owner SELECT-only (Admin monitoring, App projection).

## Active Tables

| Table | Target Owner | Access Method | Notes |
|-------|--------------|---------------|-------|
| `users` | Auth | I (future: Q via projection) | Account core. Phase 5 split: `user_profiles` to App. |
| `refresh_tokens` | Auth | I | Hash-only, CAS, family revocation (V20260606130000). |
| `password_resets` | Auth | I | Auth-owned reset flow. |
| `oauth_provider_identities` | Auth | I | Provider → account link. Phase 2 write; Phase 0 schema only. |
| `role_permissions` | Auth | I | Auth-owned RBAC grant. |
| `user_permissions` | Auth | I (future: Q) | Per-user permission grants. Phase 2 Auth-only write. |
| `audit_logs` | Admin | I (future: outbox) | Audit ledger. Phase 3 outbox; Admin writer. |
| `moderation_actions` | Admin | I | Moderation decision log. |
| `moderation_queue` | Admin | E (App writes) | Admin reads App-content items. |
| `reports` | Admin | I (future: E) | User reports. Admin owned; App writes via event (Phase 3). |
| `user_warnings` | Admin | I (future: E) | Ban/warning history. Admin owned; App writes via event (Phase 3). |
| `user_bans` | Admin | I (future: E) | Ban state. Admin owned; Auth/App write via event (Phase 3). |
| `notifications` | App | I | Notification core. |
| `notification_preferences` | App | I | Per-user prefs. |
| `notification_delivery_ledger` | App | I (Admin Q) | Delivery attempt log. Admin queries运维读. |
| `problems` | App | I | Problem core. |
| `problem_details` | App | I | Rich problem content. |
| `problem_examples` | App | I | Example test cases. |
| `problem_languages` | App | I | Language metadata. |
| `problem_versions` | App | I | Problem versioning. |
| `problem_notes` | App | I | Editorial notes. Phase 0 fixed schema drift (P0-SCHEMA-002). |
| `problem_tags` | App | I | Tag taxonomy. |
| `problem_tag_relations` | App | I | Problem↔tag mapping. |
| `problem_lists` | App | I | Curated lists. |
| `problem_list_categories` | App | I | List categories. |
| `problem_list_problem_relations` | App | I | List ↔ problem mapping. |
| `problem_list_bookmarks` | App | I | User bookmarks. |
| `submissions` | App | I | Submission core. |
| `submission_statuses` | App | I | Migration-only enum table (P0-SCHEMA-003). |
| `judge_outbox` | App | I | Submit → judge worker outbox. Phase 0 dual-write legacy. |
| `result_outbox` | App | I | Verdict → downstream outbox. Phase 0 design only (ADR-MIG-JUDGE). |
| `contests` | App | I | Contest core. |
| `contest_problems` | App | I | Contest problem set. |
| `contest_participants` | App | I | Participant registry. |
| `contest_submissions` | App | I | Contest-specific submissions. |
| `contest_problem_results` | App | I | Per-problem scoreboard. |
| `contest_rankings` | App | I | Rating/ranking snapshot. |
| `contest_scoring_rules` | App | I | Scoring policy. |
| `contest_announcements` | App | I | Contest announcements. |
| `contest_analytics` | App | I | Analytics snapshot (migration-only, P0-SCHEMA-003). |
| `solutions` | App | I | Editorial solutions. |
| `solution_comments` | App | I | Solution discussion. |
| `forum_posts` | App | I | Forum posts. |
| `forum_comments` | App | I | Comment hierarchy. |
| `forum_communities` | App | I | Community metadata. |
| `forum_community_members` | App | I | Membership roster. |
| `forum_community_permissions` | App | I | Per-community ACL. |
| `forum_community_rules` | App | I | Community rules. |
| `forum_community_tags` | App | I | Community tags. |
| `forum_community_links` | App | I | Related communities. |
| `forum_users` | App | I (future: Q) | Forum identity projection. |
| `forum_tags` | App | I | Tag taxonomy. |
| `forum_post_tag_relations` | App | I | Post ↔ tag mapping. |
| `achievements` | App | I | Achievement definitions. |
| `user_achievements` | App | I | User achievement grants. |
| `user_follows` | App | I | Social graph. |
| `subscriptions` | App | I | Notification subscriptions. |
| `collections` | App | I | Problem collections. |
| `collection_items` | App | I | Collection members. |
| `global_rankings` | App | I (Admin Q) | Rating/ranking aggregation. Admin reads运维读. |
| `appeals` | App | I | Submission appeals. |
| `edge_operations` | App | I | Edge case ops log. |
| `first_solve_records` | App | I | First-solve tracking. |
| `translations` | App | I | i18n strings. |
| `system_settings` | Admin | I | System-wide settings. |
| `system_announcements` | App | I | Announcements (admin creates, App renders). |
| `system_announcement_reads` | App | I | User read receipts. |
| `backups` | Admin | I | Backup manifest. Phase 0 added migration (P0-SCHEMA-001). |
| `DailyRecommendation` | App | I | Daily recommendations (migration-only, P0-SCHEMA-003). |
| `views` | App | I | Problem view counter (migration-only, P0-SCHEMA-003). |
| `virtual_contest_sessions` | App | I | Virtual contest state (migration-only, P0-SCHEMA-003). |

## Migration-Only Tables (Phase 0 inventory, from ADR-MIG-INV)

Tables with no Java `@TableName` references; exist in schema but no active
code path. Disposition in Phase 7.

| Table | Migration Line | Java Ref | Disposition (Phase 7 candidate) |
|-------|----------------|----------|---------------------------------|
| `views` | L645 | None | Retire: `problem_views` exists |
| `virtual_contest_sessions` | L703 | None | Formalize (Virtual Contest) |
| `forum_community_profiles` | L769 | None | Retire (no read path) |
| `forum_community_settings` | L773 | None | Retire (no read path) |
| `system_announcement_notifications` | L433 | None | Retire (notifications table exists) |
| `system_announcement_read_records` | L440 | None | Retire (no read path) |
| `submission_statuses` | L797 | None | Formalize (SubmissionStatus enum) |
| `contest_rankings` | L810 | None | Formalize (Rating) |
| `contest_analytics` | L818 | None | Formalize (Analytics) |
| `DailyRecommendation` | L826 | None | Formalize (Recommendation) |
| `email_templates` | L951 | None | Bootstrap only |
| `email_logs` | L956 | None | Bootstrap only |

## Cross-Owner Access Patterns (Future)

- **Auth → App**: Auth reads App projections (e.g., `user_profiles`) via Q.
- **App → Auth**: App calls Auth RPC for account check; Auth writes RBAC
  grants via `user_permissions`.
- **Admin → App/Admin**: Admin reads App audit/event via outbox (E); Admin
  owns moderation tables, App writes via event (Phase 3).
- **Admin → Auth**: Admin queries Auth account state via RPC (Q).

Per-phase migration refines this manifest. Phase 5 adds per-owner DB
user/grants; Phase 7 enforces no cross-owner direct DB access.