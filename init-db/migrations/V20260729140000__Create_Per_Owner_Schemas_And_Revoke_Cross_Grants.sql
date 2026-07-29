-- P5-SCHEMA-001: Per-Owner schema definitions and cross-owner grant restriction policy
--
-- Phase 5 / MICROSERVICE_MIGRATION_GUIDE.md §5.2 & §9 Phase 5:
-- Establishes the target per-owner schemas (`auth`, `admin`, `app`)
-- and enforces DB user boundary isolation for `auth_rw`, `admin_rw`, `app_rw`.
--
-- 1. Per-Owner Schemas:
--    CREATE SCHEMA IF NOT EXISTS `auth`;
--    CREATE SCHEMA IF NOT EXISTS `admin`;
--    CREATE SCHEMA IF NOT EXISTS `app`;
--
-- 2. Schema Table Allocations:
--    - Schema `auth`:  users, refresh_tokens, password_resets, role_permissions, user_permissions, oauth_provider_identities
--    - Schema `admin`: audit_logs, audit_outbox, system_settings, moderation_queue, moderation_actions, user_warnings
--    - Schema `app`:   problems, problem_details, problem_examples, problem_languages, problem_notes, problem_lists, problem_list_items,
--                      test_cases, contests, contest_problems, contest_participants, contest_announcements, submissions,
--                      submission_test_details, judge_outbox, solutions, solution_comments, solution_topics, forum_posts,
--                      forum_comments, forum_communities, notifications, notification_delivery_ledger, achievements, bookmarks, votes, backups
--
-- 3. DB User Grant Restrictions:
--    - auth_rw:  Granted SELECT, INSERT, UPDATE, DELETE ON `auth`.* (plus INSERT ON `admin`.`audit_outbox`)
--    - admin_rw: Granted SELECT, INSERT, UPDATE, DELETE ON `admin`.*
--    - app_rw:   Granted SELECT, INSERT, UPDATE, DELETE ON `app`.* (plus INSERT ON `admin`.`audit_outbox`)
--    - Cross-schema access (e.g. auth_rw accessing app.*, app_rw accessing auth.*) is strictly REVOKED.
--
-- 4. Legacy Migration Account Deprecation Schedule:
--    - Legacy superuser/root migration account (`ulticode` / `root`) remains active during Phase 5 & 6 transition for Flyway schema migrations.
--    - Scheduled for complete removal in Phase 7 (P7-DB-001 / P7-LEGACY-001).

CREATE SCHEMA IF NOT EXISTS `auth`;
CREATE SCHEMA IF NOT EXISTS `admin`;
CREATE SCHEMA IF NOT EXISTS `app`;

-- DB user shadow creation and schema-level isolation
CREATE USER IF NOT EXISTS 'auth_rw'@'%';
CREATE USER IF NOT EXISTS 'admin_rw'@'%';
CREATE USER IF NOT EXISTS 'app_rw'@'%';

-- Revoke legacy global / single-schema grants on current default schema
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'auth_rw'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'admin_rw'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'app_rw'@'%';

-- Strict per-schema grants
GRANT SELECT, INSERT, UPDATE, DELETE ON `auth`.* TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `admin`.* TO 'admin_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `app`.* TO 'app_rw'@'%';

-- Cross-domain integration outbox seam (P3-AUDIT-001): append-only audit event writing
GRANT INSERT ON `admin`.`audit_outbox` TO 'auth_rw'@'%';
GRANT INSERT ON `admin`.`audit_outbox` TO 'app_rw'@'%';

FLUSH PRIVILEGES;
