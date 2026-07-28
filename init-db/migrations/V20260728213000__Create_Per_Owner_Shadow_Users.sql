-- Per-Owner DB user shadow + table grants definition (P3-DBPERM-001)
-- Credentials are not hardcoded in Flyway migrations (AGENTS.md security invariant).
-- Flyway placeholder ${flyway:defaultSchema} handles multi-environment schema names (CI, dev, prod).

CREATE USER IF NOT EXISTS 'auth_rw'@'%';
CREATE USER IF NOT EXISTS 'admin_rw'@'%';
CREATE USER IF NOT EXISTS 'app_rw'@'%';

-- Grants for auth_rw
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`users` TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`refresh_tokens` TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`password_resets` TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`role_permissions` TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`user_permissions` TO 'auth_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`oauth_provider_identities` TO 'auth_rw'@'%';

-- Grants for admin_rw
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`audit_logs` TO 'admin_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`audit_outbox` TO 'admin_rw'@'%';
-- audit_outbox is a cross-domain integration seam (P3-AUDIT-001): every domain appends audit
-- rows inside its own business transaction; the admin dispatcher consumes them. Auth/App shadow
-- users therefore receive append-only INSERT grants in addition to admin_rw's full grant.
GRANT INSERT ON `${flyway:defaultSchema}`.`audit_outbox` TO 'auth_rw'@'%';
GRANT INSERT ON `${flyway:defaultSchema}`.`audit_outbox` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`system_settings` TO 'admin_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`moderation_queue` TO 'admin_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`moderation_actions` TO 'admin_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`user_warnings` TO 'admin_rw'@'%';

-- Grants for app_rw
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problems` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_details` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_examples` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_languages` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_notes` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_lists` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`problem_list_items` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`test_cases` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`contests` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`contest_problems` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`contest_participants` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`contest_announcements` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`submissions` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`submission_test_details` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`judge_outbox` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`solutions` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`solution_comments` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`solution_topics` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`forum_posts` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`forum_comments` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`forum_communities` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`notifications` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`notification_delivery_ledger` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`achievements` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`bookmarks` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`votes` TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `${flyway:defaultSchema}`.`backups` TO 'app_rw'@'%';

FLUSH PRIVILEGES;
