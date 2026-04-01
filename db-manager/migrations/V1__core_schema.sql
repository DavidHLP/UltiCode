SET FOREIGN_KEY_CHECKS=0;

-- UltiCode Migration: V1__core_schema
-- Core tables that other tables depend on
-- Tables: users (must be first due to FK dependencies)

CREATE TABLE `users` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` text COLLATE utf8mb4_unicode_ci,
  `company` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `github` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `twitter` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `website` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `preferred_language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_banned` tinyint(1) NOT NULL DEFAULT '0',
  `banned_until` datetime(3) DEFAULT NULL,
  `banned_reason` text COLLATE utf8mb4_unicode_ci,
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `users_username_key` (`username`),
  KEY `users_role_idx` (`role`),
  KEY `users_is_active_is_banned_idx` (`is_active`,`is_banned`),
  KEY `users_is_active_last_login_at_idx` (`is_active`,`last_login_at`),
  KEY `users_joined_at_idx` (`joined_at`),
  KEY `idx_users_is_deleted` (`is_deleted`)
);

CREATE TABLE `role_permissions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource` enum('USER','PROBLEM','CONTEST','SOLUTION','FORUM_POST','FORUM_COMMENT','SYSTEM','PROBLEM_LIST','TAG') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_permissions_role_action_resource_key` (`role`,`action`,`resource`),
  KEY `role_permissions_role_idx` (`role`)
);

CREATE TABLE `submission_statuses` (
  `key` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `suggestion` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_terminal` tinyint(1) NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`key`),
  KEY `submission_statuses_category_severity_idx` (`category`,`severity`)
);

CREATE TABLE `system_settings` (
  `key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`key`)
);

CREATE TABLE `problems` (
  `id` bigint NOT NULL,
  `slug` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `difficulty` enum('Easy','Medium','Hard') COLLATE utf8mb4_unicode_ci NOT NULL,
  `acceptance_rate` decimal(5,2) NOT NULL DEFAULT '0.00',
  `status` enum('solved','attempted','todo') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'todo',
  `is_premium` tinyint(1) NOT NULL DEFAULT '0',
  `has_solution` tinyint(1) NOT NULL DEFAULT '0',
  `completed_time` date DEFAULT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT '1',
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flag_notes` text COLLATE utf8mb4_unicode_ci,
  `flag_reason` text COLLATE utf8mb4_unicode_ci,
  `flag_reported_at` datetime(3) DEFAULT NULL,
  `flag_reported_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flag_reviewed_at` datetime(3) DEFAULT NULL,
  `flag_reviewed_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flag_status` enum('PENDING','REVIEWED','RESOLVED','DISMISSED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `problems_difficulty_idx` (`difficulty`),
  KEY `problems_slug_idx` (`slug`),
  KEY `problems_title_idx` (`title`),
  KEY `problems_is_published_is_deleted_idx` (`is_published`,`is_deleted`),
  KEY `problems_is_flagged_is_deleted_idx` (`is_flagged`,`is_deleted`),
  KEY `problems_created_at_idx` (`created_at`),
  KEY `problems_version_idx` (`version`)
);

CREATE TABLE `refresh_tokens` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Token哈希值',
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `rotated_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `is_revoked` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `refresh_tokens_token_key` (`token`),
  KEY `refresh_tokens_user_id_idx` (`user_id`),
  KEY `refresh_tokens_token_idx` (`token`),
  KEY `refresh_tokens_expires_at_idx` (`expires_at`),
  KEY `idx_refresh_tokens_token_hash` (`token_hash`),
  CONSTRAINT `refresh_tokens_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `password_resets` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `password_resets_token_key` (`token`),
  KEY `password_resets_token_idx` (`token`),
  KEY `password_resets_user_id_idx` (`user_id`),
  KEY `password_resets_expires_at_idx` (`expires_at`)
);

CREATE TABLE `submissions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `runtime` int NOT NULL,
  `memory` double NOT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `runtime_percentile` double DEFAULT NULL,
  `memory_percentile` double DEFAULT NULL,
  `test_details` json DEFAULT NULL,
  `memoryDistBinsMb` json DEFAULT NULL,
  `runtimeDistBinsMs` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `submissions_problem_id_user_id_idx` (`problem_id`,`user_id`),
  KEY `submissions_user_id_fkey` (`user_id`),
  KEY `submissions_created_at_idx` (`created_at`),
  KEY `submissions_user_id_status_created_at_idx` (`user_id`,`status`,`created_at`),
  KEY `submissions_problem_id_user_id_status_runtime_memory_idx` (`problem_id`,`user_id`,`status`,`runtime`,`memory`),
  CONSTRAINT `submissions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `submissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `notifications` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('COMMUNICATION','MARKETING','SECURITY','SYSTEM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `read_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `notifications_user_id_is_read_created_at_idx` (`user_id`,`is_read`,`created_at`),
  KEY `notifications_user_id_type_idx` (`user_id`,`type`),
  KEY `notifications_user_id_category_idx` (`user_id`,`category`),
  CONSTRAINT `notifications_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `notification_preferences` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `communication` tinyint(1) NOT NULL DEFAULT '1',
  `marketing` tinyint(1) NOT NULL DEFAULT '0',
  `security` tinyint(1) NOT NULL DEFAULT '1',
  `system` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `notification_preferences_user_id_key` (`user_id`),
  CONSTRAINT `notification_preferences_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `audit_logs` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `performer_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `audit_logs_performer_id_idx` (`performer_id`),
  KEY `audit_logs_user_id_idx` (`user_id`),
  KEY `audit_logs_entity_type_entity_id_idx` (`entity_type`,`entity_id`),
  KEY `audit_logs_created_at_idx` (`created_at`),
  CONSTRAINT `audit_logs_performer_id_fkey` FOREIGN KEY (`performer_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `audit_logs_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE `system_announcements` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `system_announcements_created_by_fkey` (`created_by`),
  CONSTRAINT `system_announcements_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE `system_announcement_reads` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `announcement_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '1',
  `read_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `system_announcement_reads_user_id_announcement_id_key` (`user_id`,`announcement_id`),
  KEY `system_announcement_reads_announcement_id_fkey` (`announcement_id`),
  CONSTRAINT `system_announcement_reads_announcement_id_fkey` FOREIGN KEY (`announcement_id`) REFERENCES `system_announcements` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `system_announcement_reads_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `user_permissions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource` enum('USER','PROBLEM','CONTEST','SOLUTION','FORUM_POST','FORUM_COMMENT','SYSTEM','PROBLEM_LIST','TAG') COLLATE utf8mb4_unicode_ci NOT NULL,
  `granted_by` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `granted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_permissions_user_id_action_resource_key` (`user_id`,`action`,`resource`),
  KEY `user_permissions_user_id_idx` (`user_id`),
  CONSTRAINT `user_permissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `user_bans` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_permanent` tinyint(1) NOT NULL DEFAULT '0',
  `reason` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `queue_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banned_by_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ends_at` datetime(3) DEFAULT NULL,
  `unbanned_at` datetime(3) DEFAULT NULL,
  `unbanned_by_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unban_reason` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_bans_user_id_idx` (`user_id`),
  KEY `user_bans_ends_at_idx` (`ends_at`),
  KEY `user_bans_banned_by_id_fkey` (`banned_by_id`),
  KEY `user_bans_unbanned_by_id_fkey` (`unbanned_by_id`),
  CONSTRAINT `user_bans_banned_by_id_fkey` FOREIGN KEY (`banned_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `user_bans_unbanned_by_id_fkey` FOREIGN KEY (`unbanned_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `user_bans_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
);

CREATE TABLE `user_warnings` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `queue_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `acknowledged_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_warnings_user_id_idx` (`user_id`),
  KEY `user_warnings_created_at_idx` (`created_at`),
  CONSTRAINT `user_warnings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
);

CREATE TABLE `translations` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `locale` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `created_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `translations_entity_type_entity_id_field_name_locale_key` (`entity_type`,`entity_id`,`field_name`,`locale`),
  KEY `translations_entity_type_entity_id_locale_idx` (`entity_type`,`entity_id`,`locale`),
  KEY `translations_locale_idx` (`locale`),
  KEY `translations_created_by_idx` (`created_by`),
  KEY `translations_updated_by_idx` (`updated_by`),
  CONSTRAINT `translations_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `translations_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE `views` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` enum('SOLUTION','FORUM_POST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `viewed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `views_target_type_target_id_user_id_ip_idx` (`target_type`,`target_id`,`user_id`,`ip`),
  KEY `views_user_id_fkey` (`user_id`),
  CONSTRAINT `views_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);



-- Seed Data

-- Table: users (22 rows)
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('c612f40375fb48a7b188aa4e006b682a','testuser123','testuser123','test@example.com',NULL,'$2a$10$cp0fh0J4LSSDWnG/l5c28uKQlsQRDklXKys6MaHyMsJPGNcF6pX8W',NULL,NULL,NULL,'2026-03-28 08:37:26.253',NULL,NULL,NULL,NULL,'USER',1,0,NULL,NULL,'2026-03-28 09:00:51.433',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('u-001','shadcn','Shad','m@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=shadcn','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Creator of beautiful UI components. Building shadcn/ui to make design accessible to everyone.','Vercel','shadcn','2026-03-22 05:44:30.433','San Francisco, CA','shadcn','https://ui.shadcn.com','en-US','USER',1,0,NULL,NULL,'2025-02-03 10:30:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('u-002','stack_unwind','Stack Unwind','su@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=stack_unwind','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Full-stack developer passionate about clean code and elegant solutions.','Google','stackunwind','2026-03-22 05:44:30.436','New York, NY','stackunwind','https://stackunwind.dev','en-US','USER',1,0,NULL,NULL,'2025-02-02 15:45:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('u-admin-001','admin','System Administrator','admin@ulticode.com','https://api.dicebear.com/7.x/shapes/svg?seed=admin','$2b$10$8F..IPhJH.POjm8.nvZEwOKlgglMnlcRatqaxevXYDkjNSNbm3WA.',NULL,NULL,NULL,'2026-03-22 05:44:30.501',NULL,NULL,NULL,NULL,'SUPER_ADMIN',1,0,NULL,NULL,'2026-04-01 06:36:36.736',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('u-mod-001','moderator','Content Moderator','moderator@ulticode.com','https://api.dicebear.com/7.x/shapes/svg?seed=moderator','$2b$10$vg/LOKE2c9On6iuO.o2XDewj1navuW.T6dpgb5Ozwpff7FC/PwFGq',NULL,NULL,NULL,'2026-03-22 05:44:30.540',NULL,NULL,NULL,NULL,'MODERATOR',1,0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-alex','alex_algorithm','Alex','alex@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=alex','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Algorithm enthusiast and coding interview coach.','Meta','alexalgorithm','2026-03-22 05:44:30.441','Seattle, WA','alex_algo','https://alexalgo.io','en-US','USER',1,0,NULL,NULL,'2025-02-03 12:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-benq','Benq','Ben','ben@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=benq','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','USACO champion and competitive programming legend.','MIT','bqi343','2026-03-22 05:44:30.459','Cambridge, MA','benq343','https://codeforces.com/profile/Benq','en-US','USER',1,0,NULL,NULL,'2025-02-03 01:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-chen','chen_master','Chen','chen@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=chen','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Chinese competitive programmer and software engineer.','ByteDance','chen-master','2026-03-22 05:44:30.442','Beijing, China','chenmaster','https://chenmaster.cn','zh-CN','USER',1,0,NULL,NULL,'2025-02-02 20:15:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-david','david_algo','David','david@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=david','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Algorithm specialist and competitive programming coach.','Google','david-algo','2026-03-22 05:44:30.452','Toronto, Canada','davidalgo','https://davidalgo.ca','en-US','USER',1,0,NULL,NULL,'2025-02-03 13:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-ecnerwala','ecnerwala','Andrew','ecnerwala@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=ecnerwala','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Google engineer and competitive programming expert.','Google','ecnerwala','2026-03-22 05:44:30.460','Mountain View, CA','ecnerwala','https://codeforces.com/profile/ecnerwala','en-US','USER',1,0,NULL,NULL,'2025-02-02 18:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-emma','emma_swift','Emma','emma@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=emma','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','iOS developer and Swift programming language enthusiast.','Apple','emma-swift','2026-03-22 05:44:30.453','Cupertino, CA','emmaswift','https://emmaswift.dev','en-US','USER',1,0,NULL,NULL,'2025-02-01 16:30:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-jiangly','jiangly','Jiang','jiangly@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=jiangly','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Chinese competitive programmer, ICPC World Champion 2024.','Tsinghua University','jiangly','2026-03-22 05:44:30.458','Beijing, China','jiangly','https://codeforces.com/profile/jiangly','zh-CN','USER',1,0,NULL,NULL,'2025-02-02 22:30:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-kevin','kevin_pro','Kevin','kevin@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=kevin','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Full-stack developer and DevOps engineer.','Amazon','kevin-pro','2026-03-22 05:44:30.455','Austin, TX','kevinpro','https://kevinpro.io','en-US','USER',1,0,NULL,NULL,'2025-02-03 07:15:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-lily','lily_codes','Lily','lily@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=lily','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Machine learning engineer and data scientist.','OpenAI','lily-codes','2026-03-22 05:44:30.450','Boston, MA','lilycodes','https://lilycodes.ai','en-US','USER',1,0,NULL,NULL,'2025-02-02 11:20:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-max','max_coder','Max','max@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=max','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','German software engineer specializing in distributed systems.','SAP','max-coder','2026-03-22 05:44:30.444','Berlin, Germany','maxcoder','https://maxcoder.de','de-DE','USER',1,0,NULL,NULL,'2025-02-03 06:30:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-petr','Petr','Petr','petr@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=petr','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Codeforces legend and Google software engineer.','Google','Petr','2026-03-22 05:44:30.464','San Francisco, CA','petr_mitrichev','https://codeforces.com/profile/Petr','en-US','USER',1,0,NULL,NULL,'2025-02-02 23:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-sara','sara_dev','Sara','sara@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=sara','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Frontend developer and accessibility advocate.','Adobe','sara-dev','2026-03-22 05:44:30.445','San Jose, CA','sara_dev','https://saradev.io','en-US','USER',1,0,NULL,NULL,'2025-02-01 14:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-scott','scott_wu','Scott','scott@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=scott','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','MIT graduate and competitive programming expert.','Jane Street','scottwu','2026-03-22 05:44:30.463','New York, NY','scott_wu','https://codeforces.com/profile/scott_wu','en-US','USER',1,0,NULL,NULL,'2025-02-03 05:30:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-tom','tom_quick','Tom','tom@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=tom','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','React contributor and performance optimization expert.','Vercel','tom-quick','2026-03-22 05:44:30.448','London, UK','tomquick','https://tomquick.dev','en-GB','USER',1,0,NULL,NULL,'2025-02-03 09:45:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-tourist','tourist','Gennady','tourist@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=tourist','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Legendary competitive programmer. Codeforces Grandmaster and multiple world champion.','ITMO University','tourist','2026-03-22 05:44:30.456','Saint Petersburg, Russia',' tourist','https://codeforces.com/profile/tourist','en-US','USER',1,0,NULL,NULL,'2025-02-03 04:00:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-um_nik','Um_nik','Nikolai','umnik@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=um_nik','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Russian competitive programmer and algorithm educator.','Yandex','Umnik','2026-03-22 05:44:30.462','Moscow, Russia','umnik','https://codeforces.com/profile/Um_nik','ru-RU','USER',1,0,NULL,NULL,'2025-02-01 19:45:00.000',NULL,NULL,0,NULL,NULL);
INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `password`, `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`, `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`, `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('user-yuki','yuki_codes','Yuki','yuki@example.com','https://api.dicebear.com/7.x/notionists/svg?seed=yuki','$2b$10$K8pVO3UQuW/ptltahP6IH.OgdMrSyX9rbzY8F5RytTgcR8xp7zTS.','Japanese competitive programmer and open source enthusiast.','University of Tokyo','yuki-codes','2026-03-22 05:44:30.439','Tokyo, Japan','yuki_codes','https://yuki.dev','ja-JP','USER',1,0,NULL,NULL,'2025-02-01 08:20:00.000',NULL,NULL,0,NULL,NULL);

-- Table: role_permissions (136 rows)
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('015a3674-b2ef-4cbd-813b-62551e02f853','SUPER_ADMIN','UPDATE','FORUM_POST','2026-03-22 05:44:30.977');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('026e68ab-7325-4794-8fac-914d372c0975','ADMIN','CREATE','PROBLEM','2026-03-22 05:44:31.034');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0860438d-4edf-47c0-8f27-337359994a95','SUPER_ADMIN','READ','PROBLEM','2026-03-22 05:44:30.945');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0b07188a-1b02-4262-8e0d-28ca5a93b6db','MODERATOR','READ','SOLUTION','2026-03-22 05:44:31.088');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0c0c8836-8548-49c7-9117-762105412744','SUPER_ADMIN','UPDATE','SYSTEM','2026-03-22 05:44:31.002');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0d2a6d2c-9163-440e-b664-7faa0aab1aeb','ADMIN','DELETE','FORUM_COMMENT','2026-03-22 05:44:31.064');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0e85e200-8383-434f-bb2e-716c079c644c','SUPER_ADMIN','MODERATE','FORUM_POST','2026-03-22 05:44:30.979');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0ef70d9b-0296-4418-abf4-f6cbb054d968','SUPER_ADMIN','MANAGE_USERS','SOLUTION','2026-03-22 05:44:30.972');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0f2bc4f2-96e2-46af-bab6-1dcc58b011a2','ADMIN','DELETE','PROBLEM_LIST','2026-03-22 05:44:31.079');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0f5503f5-a494-4dfa-93cf-f57a38d54a50','SUPER_ADMIN','CREATE','CONTEST','2026-03-22 05:44:30.954');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('0fd8cdf1-323d-4790-8045-1662daf21404','ADMIN','DELETE','SOLUTION','2026-03-22 05:44:31.051');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1119523d-477a-4b12-9b41-78888e93614e','ADMIN','MANAGE_USERS','FORUM_COMMENT','2026-03-22 05:44:31.067');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('111bed0a-6d1e-4cb6-a69e-aea1eec1e142','ADMIN','READ','PROBLEM_LIST','2026-03-22 05:44:31.076');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('12d2e0ad-d059-476d-a681-a48d55120ac9','ADMIN','MANAGE_USERS','SYSTEM','2026-03-22 05:44:31.074');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('14318737-be13-4349-be27-f6e808dc3d0e','SUPER_ADMIN','UPDATE','USER','2026-03-22 05:44:30.938');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('17c1ae54-1a42-4695-94cb-d161cf8690fc','ADMIN','MODERATE','PROBLEM','2026-03-22 05:44:31.038');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('19dabf8e-a114-4bf9-ba7c-2620a6a35b57','SUPER_ADMIN','PUBLISH','CONTEST','2026-03-22 05:44:30.960');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('19f5afd9-12e5-405d-a883-dcd9c83bd8f9','ADMIN','READ','FORUM_COMMENT','2026-03-22 05:44:31.062');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1ad0a7c1-ab97-46f6-b146-d2d67431db05','SUPER_ADMIN','MANAGE_USERS','CONTEST','2026-03-22 05:44:30.961');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1c65c7bd-cb6d-409f-bdac-18593eadb9af','SUPER_ADMIN','CREATE','FORUM_POST','2026-03-22 05:44:30.974');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1cb2b32e-feb1-43c9-8d1d-87f551d6387a','ADMIN','PUBLISH','SOLUTION','2026-03-22 05:44:31.052');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1d0c26db-692f-4eb9-827b-8560ec2e6b86','SUPER_ADMIN','CREATE','USER','2026-03-22 05:44:30.935');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1d8bdfc6-a646-4936-a3b4-dbc9ae0640df','SUPER_ADMIN','READ','SOLUTION','2026-03-22 05:44:30.965');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('1f793561-5e89-452f-b7b9-19df5f45b8c4','ADMIN','UPDATE','FORUM_COMMENT','2026-03-22 05:44:31.063');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('26abb6e2-36d0-4698-956d-84b1caa49ef3','SUPER_ADMIN','CREATE','PROBLEM','2026-03-22 05:44:30.944');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('28508dc5-ed0a-4157-91e4-4da0d2e20835','ADMIN','READ','USER','2026-03-22 05:44:31.027');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('2a5dd243-2a2a-4651-bc02-204ecf9ee893','SUPER_ADMIN','READ','PROBLEM_LIST','2026-03-22 05:44:31.014');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('2a95a42d-9dbb-487d-88fd-7fc3e9732c98','SUPER_ADMIN','DELETE','SOLUTION','2026-03-22 05:44:30.968');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('2be75df1-2035-4c44-a9c7-2d2a62e0acdf','ADMIN','CREATE','FORUM_POST','2026-03-22 05:44:31.054');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('2f36a283-328e-4077-a41e-62fb1a8c4920','SUPER_ADMIN','PUBLISH','SYSTEM','2026-03-22 05:44:31.008');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('30b08817-aa6f-48b5-81d5-b896290e8764','ADMIN','PUBLISH','PROBLEM_LIST','2026-03-22 05:44:31.082');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('30ca4709-2b07-4e09-a2a6-74c36820e122','MODERATOR','READ','PROBLEM','2026-03-22 05:44:31.086');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('317d753f-110c-460f-800b-6f45ea16459a','ADMIN','MODERATE','CONTEST','2026-03-22 05:44:31.045');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('31aad0e5-1db4-4cf4-ac17-41bcab1e7af6','ADMIN','MANAGE_USERS','USER','2026-03-22 05:44:31.033');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('3481358f-9ac0-43c1-9a99-cdb5022f7096','SUPER_ADMIN','DELETE','SYSTEM','2026-03-22 05:44:31.005');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('35b3a187-fe91-4249-b979-71770caa253d','ADMIN','DELETE','USER','2026-03-22 05:44:31.030');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('35cbbe40-da1c-4f59-b00f-d7c83f3c155f','SUPER_ADMIN','READ','FORUM_POST','2026-03-22 05:44:30.976');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('36087876-5a66-4ca9-a655-d615355cecfa','SUPER_ADMIN','UPDATE','SOLUTION','2026-03-22 05:44:30.967');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('381bad38-32cd-426a-adb5-bacb48c73b79','SUPER_ADMIN','READ','CONTEST','2026-03-22 05:44:30.955');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('388527a2-8d1f-4cbc-be98-3f5040bf891c','ADMIN','UPDATE','CONTEST','2026-03-22 05:44:31.043');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('3f220254-4209-4f47-853b-a6b94fa2651a','ADMIN','MODERATE','SOLUTION','2026-03-22 05:44:31.051');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('3faf20e0-2305-4cb6-abbd-937bf1e6fa76','SUPER_ADMIN','MANAGE_PERMISSIONS','SOLUTION','2026-03-22 05:44:30.973');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('3fde1e0a-0f61-4834-8353-1c7d2d9458e0','SUPER_ADMIN','PUBLISH','SOLUTION','2026-03-22 05:44:30.970');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('44161d21-c4de-43f0-bed3-28744f428464','ADMIN','PUBLISH','FORUM_COMMENT','2026-03-22 05:44:31.066');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('44635426-750c-452e-aa20-969112cb0543','MODERATOR','READ','CONTEST','2026-03-22 05:44:31.087');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('4578d142-98e3-4f92-bea9-304920f494dc','ADMIN','CREATE','SOLUTION','2026-03-22 05:44:31.047');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('458689f3-9d36-4068-a03e-95ed082fb742','MODERATOR','MODERATE','FORUM_POST','2026-03-22 05:44:31.094');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('45cb0633-ca36-4ffe-945c-ae9ab5ee3c36','SUPER_ADMIN','MODERATE','PROBLEM','2026-03-22 05:44:30.949');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('46b474d8-cb6b-4dcc-a2b6-2f63fe6f51a8','SUPER_ADMIN','MANAGE_USERS','SYSTEM','2026-03-22 05:44:31.010');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('47271053-79bd-4701-8775-1a3ff638f8e6','ADMIN','MODERATE','SYSTEM','2026-03-22 05:44:31.072');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('4903c597-d051-4b50-ade4-aba5e9b47378','ADMIN','MANAGE_USERS','FORUM_POST','2026-03-22 05:44:31.061');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('50894f75-b54c-4934-96dc-c32821aecfff','MODERATOR','READ','SYSTEM','2026-03-22 05:44:31.092');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('563e9464-46e6-4938-8d9f-87fb2fff69c1','SUPER_ADMIN','PUBLISH','FORUM_COMMENT','2026-03-22 05:44:30.996');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('56dca672-b211-4269-a2e5-256622cc27dd','MODERATOR','UPDATE','FORUM_COMMENT','2026-03-22 05:44:31.102');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('57ff6565-51ed-4541-bb14-cdf56f53ab2c','SUPER_ADMIN','MODERATE','SYSTEM','2026-03-22 05:44:31.007');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('5a73de3d-8923-4106-b9c7-d12a37b27309','ADMIN','PUBLISH','USER','2026-03-22 05:44:31.032');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('5c0c9655-d457-480c-8c96-cf855fbdfdf3','SUPER_ADMIN','PUBLISH','FORUM_POST','2026-03-22 05:44:30.981');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('60906d4f-ee7c-42be-a6b6-853c04d36e09','SUPER_ADMIN','CREATE','FORUM_COMMENT','2026-03-22 05:44:30.989');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('62c32d8c-67d8-48a0-b1c3-707e4567aea2','ADMIN','UPDATE','SOLUTION','2026-03-22 05:44:31.049');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('687ace9e-da48-4073-b6f1-abbead4becbb','SUPER_ADMIN','DELETE','PROBLEM','2026-03-22 05:44:30.948');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('6ad7e12d-99a0-4f83-a6c1-cf2a6d8a08e6','SUPER_ADMIN','MODERATE','USER','2026-03-22 05:44:30.940');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('6cbcfd27-87b4-4c4f-b3ff-e94b47c259e6','MODERATOR','DELETE','FORUM_COMMENT','2026-03-22 05:44:31.102');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('6f5a51ae-aedb-455e-b46d-b070f1f95373','MODERATOR','READ','FORUM_POST','2026-03-22 05:44:31.089');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('6f9d724f-6bdc-46d8-a935-8df1e13d0abc','MODERATOR','READ','USER','2026-03-22 05:44:31.085');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('703d3ebf-0e28-4172-9cb5-f9a752b12a3f','MODERATOR','UPDATE','SOLUTION','2026-03-22 05:44:31.096');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('705dcd6d-41b5-4899-9018-ee6ad137e12a','ADMIN','MANAGE_USERS','SOLUTION','2026-03-22 05:44:31.053');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('733a5117-62e7-47ae-8b20-65d441243d1a','SUPER_ADMIN','MANAGE_PERMISSIONS','USER','2026-03-22 05:44:30.943');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('747eb92d-d173-45db-9a0b-55ab2dc04f04','SUPER_ADMIN','READ','USER','2026-03-22 05:44:30.936');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('77742377-5764-489c-9a44-8ff2ebecc4b7','SUPER_ADMIN','MANAGE_PERMISSIONS','FORUM_POST','2026-03-22 05:44:30.987');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('778e5853-0614-47bf-93a2-ed9a0f721fb4','ADMIN','UPDATE','SYSTEM','2026-03-22 05:44:31.070');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('7af20255-1454-4060-ab41-e6de2effed61','SUPER_ADMIN','MODERATE','FORUM_COMMENT','2026-03-22 05:44:30.995');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('7b7e29d2-d9b6-491b-9d4d-31bc359439b0','SUPER_ADMIN','MODERATE','SOLUTION','2026-03-22 05:44:30.969');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('7b8f01b9-3150-440f-97a2-7aaa757d32bd','SUPER_ADMIN','CREATE','SOLUTION','2026-03-22 05:44:30.963');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('7f9d11db-011f-485b-a1a6-e6d58f921b8d','SUPER_ADMIN','MANAGE_PERMISSIONS','PROBLEM','2026-03-22 05:44:30.952');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('7fab2fe8-c0e0-4377-ba9d-aac5f1a8a773','ADMIN','MODERATE','USER','2026-03-22 05:44:31.031');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('809e0520-ecfa-4614-996b-bad9ab3e9f21','SUPER_ADMIN','MANAGE_USERS','FORUM_POST','2026-03-22 05:44:30.984');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('83c31ac1-5be3-4680-9c31-dcb75811ce56','ADMIN','CREATE','CONTEST','2026-03-22 05:44:31.041');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('8c3e6b55-2691-4a0f-9736-542f65794080','SUPER_ADMIN','READ','SYSTEM','2026-03-22 05:44:31.001');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('8d51fc74-d17d-4847-94a4-1607d47730b7','SUPER_ADMIN','PUBLISH','PROBLEM_LIST','2026-03-22 05:44:31.019');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('8f921494-cc2f-46c6-8f27-637c361d7f30','SUPER_ADMIN','MANAGE_USERS','PROBLEM_LIST','2026-03-22 05:44:31.021');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('8ff8df6f-287d-48fd-8537-374a19d084fe','ADMIN','READ','PROBLEM','2026-03-22 05:44:31.035');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('9171b1fc-d655-47ae-b5fd-7d8c67e745e9','ADMIN','CREATE','FORUM_COMMENT','2026-03-22 05:44:31.062');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('922a6930-0a53-4772-a026-981237c865e5','ADMIN','MODERATE','FORUM_COMMENT','2026-03-22 05:44:31.065');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('937f9e53-0c13-4c90-bc0a-c43501cc76e2','ADMIN','READ','SYSTEM','2026-03-22 05:44:31.069');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('944cb37d-4547-4ebb-ad73-591cf2cc1d17','SUPER_ADMIN','UPDATE','FORUM_COMMENT','2026-03-22 05:44:30.993');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('9662decc-97f2-4a4c-84ca-0aed2e183883','ADMIN','CREATE','PROBLEM_LIST','2026-03-22 05:44:31.075');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('9b862fea-cfeb-415f-b7d5-a6f0d7d2f212','SUPER_ADMIN','MODERATE','CONTEST','2026-03-22 05:44:30.959');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('9d068cd2-1bec-4149-b62f-77ec150dca8f','ADMIN','PUBLISH','FORUM_POST','2026-03-22 05:44:31.060');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('9ee8320c-f8bb-4651-893e-67df46eb1876','ADMIN','UPDATE','USER','2026-03-22 05:44:31.029');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a304d335-46eb-4c26-a5c3-d297b97392c3','ADMIN','PUBLISH','SYSTEM','2026-03-22 05:44:31.073');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a4178bda-a816-4a98-8406-66a2cdec6ea2','SUPER_ADMIN','MANAGE_PERMISSIONS','CONTEST','2026-03-22 05:44:30.962');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a5fa23af-841c-4e76-95b7-f9532450aa2f','SUPER_ADMIN','CREATE','SYSTEM','2026-03-22 05:44:30.999');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a61badb0-dfe0-4628-8897-584399623318','SUPER_ADMIN','MODERATE','PROBLEM_LIST','2026-03-22 05:44:31.018');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a710c894-b4fc-432b-8002-2bb6be2ff5c9','SUPER_ADMIN','MANAGE_USERS','FORUM_COMMENT','2026-03-22 05:44:30.997');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('a981c764-10e0-4d9e-85a6-cc0a3b1fb28c','MODERATOR','MODERATE','SOLUTION','2026-03-22 05:44:31.093');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('aa186ba6-09be-4edc-9a46-240896ecf30c','MODERATOR','MODERATE','FORUM_COMMENT','2026-03-22 05:44:31.095');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('b329dcd7-3378-407f-b4da-710b38ef2c06','ADMIN','MANAGE_USERS','PROBLEM','2026-03-22 05:44:31.040');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('b495f096-783f-4802-bdaa-a7af80f7cd09','SUPER_ADMIN','DELETE','USER','2026-03-22 05:44:30.939');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('be87c7cc-337f-4059-b257-d5ac11b5c5c0','ADMIN','UPDATE','PROBLEM_LIST','2026-03-22 05:44:31.078');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('bf79f66a-2edd-4d2e-b91b-0ef42f488225','SUPER_ADMIN','DELETE','PROBLEM_LIST','2026-03-22 05:44:31.017');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c00296b4-57b0-4f98-9748-29ed0d7d5399','SUPER_ADMIN','PUBLISH','PROBLEM','2026-03-22 05:44:30.950');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c0b41e92-d358-4228-8825-7be435d48261','SUPER_ADMIN','CREATE','PROBLEM_LIST','2026-03-22 05:44:31.012');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c1bbb5cf-987c-49bb-bad8-6839b95838a2','SUPER_ADMIN','READ','FORUM_COMMENT','2026-03-22 05:44:30.992');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c2340220-f3a6-4196-95e7-a649e9999123','SUPER_ADMIN','PUBLISH','USER','2026-03-22 05:44:30.941');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c610e798-07ce-475a-b55e-27a8bc38b62a','SUPER_ADMIN','UPDATE','PROBLEM','2026-03-22 05:44:30.947');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c838cd8c-16c0-4102-a22b-1128041141ea','ADMIN','MANAGE_USERS','CONTEST','2026-03-22 05:44:31.046');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c8c57c8a-0e92-4bd4-b710-1f3e909c1050','ADMIN','DELETE','SYSTEM','2026-03-22 05:44:31.071');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('c923842c-1572-45a0-ac85-4a3d5a4af8e5','SUPER_ADMIN','UPDATE','CONTEST','2026-03-22 05:44:30.956');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('ca6a7239-b292-40a1-9c27-8576cb69ea80','ADMIN','READ','FORUM_POST','2026-03-22 05:44:31.055');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('cbc43b38-cb98-48eb-ae83-7dc5e851c6de','SUPER_ADMIN','DELETE','CONTEST','2026-03-22 05:44:30.957');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('d47a5b2a-2148-4da7-a2b3-1e8a0ef35a8b','MODERATOR','DELETE','SOLUTION','2026-03-22 05:44:31.098');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('d6ad16ad-cf68-460b-b229-1fe153e37b66','MODERATOR','READ','FORUM_COMMENT','2026-03-22 05:44:31.090');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('d73ccead-9a3b-493e-b962-077ae33a9ad5','SUPER_ADMIN','MANAGE_PERMISSIONS','FORUM_COMMENT','2026-03-22 05:44:30.998');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('d7680c16-2bf1-49b9-b2f8-d9bbecf36b6c','ADMIN','UPDATE','FORUM_POST','2026-03-22 05:44:31.056');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('dd4c8e86-30e8-4316-b46f-101f43a85dfd','SUPER_ADMIN','MANAGE_PERMISSIONS','SYSTEM','2026-03-22 05:44:31.011');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('ddec670c-d770-4409-ac1d-b87e3379072f','SUPER_ADMIN','DELETE','FORUM_POST','2026-03-22 05:44:30.978');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e1f966e1-86c1-4dc4-9f59-cef373e80977','ADMIN','PUBLISH','CONTEST','2026-03-22 05:44:31.046');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e24c4641-31c0-4a77-8d59-3661b7c7ea52','ADMIN','READ','SOLUTION','2026-03-22 05:44:31.048');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e338ddbc-b240-42de-bada-944d4527696e','MODERATOR','UPDATE','FORUM_POST','2026-03-22 05:44:31.099');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e4f3bba5-973c-4bd4-bc6b-6bab27af2ed1','ADMIN','MODERATE','FORUM_POST','2026-03-22 05:44:31.058');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e8d88671-6547-4089-b170-83d0563b76b7','ADMIN','PUBLISH','PROBLEM','2026-03-22 05:44:31.039');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e924822a-fe84-4652-abdc-ace8455e4e9f','ADMIN','CREATE','USER','2026-03-22 05:44:31.025');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e92d9a9a-3c07-453e-b202-43aace5800d3','ADMIN','DELETE','PROBLEM','2026-03-22 05:44:31.036');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('e9392696-b43e-4da7-9882-d731b41f9329','ADMIN','MANAGE_USERS','PROBLEM_LIST','2026-03-22 05:44:31.083');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('eb6b872d-a29d-46de-af17-d93f9ed95d8e','ADMIN','CREATE','SYSTEM','2026-03-22 05:44:31.068');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('ecdaa363-bab5-4acd-9929-d15f9a4bc16e','SUPER_ADMIN','MANAGE_USERS','PROBLEM','2026-03-22 05:44:30.951');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('f4dc9591-db30-4b6b-8c54-8fbeae910859','ADMIN','READ','CONTEST','2026-03-22 05:44:31.042');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('f4fecbfe-d6f3-4000-bf9f-bc2a0ca42515','SUPER_ADMIN','MANAGE_USERS','USER','2026-03-22 05:44:30.942');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('f53e0bdc-22a3-477b-9ade-935ab4df0352','SUPER_ADMIN','DELETE','FORUM_COMMENT','2026-03-22 05:44:30.994');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('f563c082-6561-4c88-8483-25218c5c5ec5','ADMIN','DELETE','CONTEST','2026-03-22 05:44:31.044');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('f5c14b4c-deaa-4531-8401-ccee203e7c32','MODERATOR','DELETE','FORUM_POST','2026-03-22 05:44:31.100');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('fc49f393-feb2-49a1-884a-a089a2714b76','ADMIN','MODERATE','PROBLEM_LIST','2026-03-22 05:44:31.081');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('fca5dd1d-13f3-45b8-9ada-1bc053e454b7','ADMIN','UPDATE','PROBLEM','2026-03-22 05:44:31.035');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('fcf1f6d2-cd3e-44f6-b39e-5ad09cef8e0c','ADMIN','DELETE','FORUM_POST','2026-03-22 05:44:31.057');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('fda23abc-3867-466e-8a4b-c395d58e7faa','SUPER_ADMIN','MANAGE_PERMISSIONS','PROBLEM_LIST','2026-03-22 05:44:31.023');
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`, `created_at`) VALUES ('fe26b45d-8c65-49c9-8384-d037d6a1562d','SUPER_ADMIN','UPDATE','PROBLEM_LIST','2026-03-22 05:44:31.015');

-- Table: submission_statuses (11 rows)
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Accepted','AC','Accepted','All test cases passed.',NULL,'success','success',1,10,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Compile Error','CE','Compile Error','Code failed to compile or load.','Fix syntax errors and missing definitions.','error','error',1,70,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Judging','JDG','Judging','Submission is being evaluated.','Please wait.','pending','info',0,100,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Memory Limit Exceeded','MLE','Memory Limit Exceeded','Memory usage exceeded the limit.','Reduce allocations or use more memory-efficient structures.','error','warning',1,40,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Output Limit Exceeded','OLE','Output Limit Exceeded','Program produced too much output.','Remove debug logs and avoid large prints.','error','warning',1,50,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Pending','PD','Pending','Submission is waiting in the queue.','Please wait.','pending','info',0,110,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Presentation Error','PE','Presentation Error','Output format differs from expected.','Match spacing, line breaks, and formatting exactly.','error','warning',1,80,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Runtime Error','RE','Runtime Error','Program crashed or threw an exception.','Check bounds, null values, and type conversions.','error','error',1,60,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('System Error','SE','System Error','Judging system encountered an internal error.','Retry later or contact support.','system','error',1,90,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Time Limit Exceeded','TLE','Time Limit Exceeded','Execution exceeded the time limit.','Optimize the algorithm or reduce per-test overhead.','error','warning',1,30,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');
INSERT INTO `submission_statuses` (`key`, `code`, `label`, `description`, `suggestion`, `category`, `severity`, `is_terminal`, `sort_order`, `created_at`, `updated_at`) VALUES ('Wrong Answer','WA','Wrong Answer','Output does not match the expected result.','Review edge cases, input parsing, and output formatting.','error','error',1,20,'2026-03-22 05:44:30.903','2026-03-22 05:44:30.903');

-- Table: submissions - seed data skipped (code contains multi-line values requiring special handling)

-- Table: translations (216 rows)
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0026c983-9da6-4808-8a23-d76ae6d1c2cf','PROBLEM_TAG','hash-table','label','zh-CN','哈希表','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('006e1c9b-b232-478c-bb69-699c2b7ea933','SUBMISSION_STATUS','Judging','description','en-US','Your submission is currently being judged.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('01124d83-6a2c-41f4-a019-4a942c580b00','SUBMISSION_STATUS','Memory Limit Exceeded','description','en-US','Memory usage exceeded the allowed limit.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0169dfe3-39e3-40b5-af7c-5003898ffcb0','PROBLEM_EXAMPLE','ex-two-sum-1','explanation','zh-CN','因为 nums[0] + nums[1] == 9，返回 [0, 1]。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('02670c49-b8d8-4fec-8ac5-d83616ad693d','PROBLEM_TAG','database','label','zh-CN','数据库','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0392ccbf-fd4d-429d-b8b1-dbcba503f74a','PROBLEM_TAG','string','label','en-US','String','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('04267eb9-8642-4003-ab8a-245501c6691d','SUBMISSION_STATUS','Time Limit Exceeded','suggestion','en-US','Consider a more efficient algorithm or optimize data structures.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('05aa11cb-da1e-4321-bd82-299919675d5c','PROBLEM_TAG','bit-manipulation','label','en-US','Bit Manipulation','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('06496f1b-40f8-4e08-b7b7-3558d5d36650','PROBLEM_DETAIL','pd-median-two-sorted-arrays','follow_up','en-US','Can you prove why the binary search over partitions is correct?','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('06757808-3fb2-42ef-8670-9310c3eb56f6','PROBLEM_TAG','hash-table','label','en-US','Hash Table','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('06d09ae1-3438-4703-8a0b-a4a5a28e321f','PROBLEM_DETAIL','pd-longest-substring','constraints_json','zh-CN','[\"$0 \\\\leq s.length \\\\leq 5 \\\\times 10^4$\",\"s 由英文字母、数字、符号和空格组成。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('072a7e44-529a-41b1-af8d-c31e38b9335b','SUBMISSION_STATUS','Pending','description','zh-CN','你的提交正在队列中等待。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('073af9c1-d031-4529-bf40-0c9b57b52533','PROBLEM_TAG','intervals','label','zh-CN','区间','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0a188f65-41b5-4583-bd99-e8fc197ad828','SUBMISSION_STATUS','Compile Error','suggestion','zh-CN','修复编译器输出中显示的语法错误。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0ad5f042-5fab-40eb-9395-e9c8a8dcf8e2','SUBMISSION_STATUS','System Error','description','en-US','An internal error occurred during judging.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0aefaf6c-7dbd-4af0-91f8-559b45aa326b','PROBLEM_TAG','bit-manipulation','label','zh-CN','位运算','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0f65e191-eca1-41e3-934e-a2fa9108bf50','SUBMISSION_STATUS','Output Limit Exceeded','label','en-US','Output Limit Exceeded','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('0f9263e6-7758-4ea7-90ec-260cac2b8aa5','PROBLEM_TAG','heap','label','zh-CN','堆（优先队列）','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('101dc7d1-003c-4ff0-8244-071dde8a9d06','PROBLEM_EXAMPLE','ex-merge-3','explanation','zh-CN','第二个区间被包含在第一个区间内。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('10b16c46-235b-4a49-8dcb-5ffeb3b08970','PROBLEM_EXAMPLE','ex-two-sum-1','explanation','en-US','Because nums[0] + nums[1] == 9, we return [0, 1].','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('11d5a7a3-c6c5-426d-be8c-3c6988993d9b','PROBLEM_TAG','queue','label','en-US','Queue','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1367fada-7922-4d76-992e-a74cb6bf9295','SUBMISSION_STATUS','Memory Limit Exceeded','label','zh-CN','超出内存限制','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1585c994-99e6-4974-baa7-ab843852283f','PROBLEM_EXAMPLE','ex-islands-2','explanation','zh-CN','左上角有一个岛屿，中间有一个，右下角有一个。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('16bbd6ee-ffcb-4c65-9e6e-2949a2589cf0','SUBMISSION_STATUS','Pending','description','en-US','Your submission is waiting in the queue.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('18304a72-a5a9-43a5-b22d-fff89dd3d0be','PROBLEM_DETAIL','pd-two-sum','follow_up','zh-CN','你能想出一个时间复杂度小于 $O(n^2)$ 的算法吗？','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1a10f9f1-0074-4f28-b304-c9ba46ce6f05','PROBLEM','1','title','en-US','Two Sum','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1ae7f225-064f-479b-9620-46a5ac5b88b3','PROBLEM_DETAIL','pd-longest-substring','follow_up','zh-CN','你能在保持 O(n) 时间复杂度的同时返回子串本身吗？','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1b82c146-8563-476d-ac21-3bd9a5a55a76','PROBLEM','7','title','en-US','Tenth Line','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1ea2ed18-87be-41e6-8610-ef8d1e82712e','PROBLEM_DETAIL','pd-two-sum','hints','zh-CN','[\"暴力解法很简单。遍历每个元素 x，并查找是否存在另一个值等于 target - x。\",\"因此，如果我们固定一个数字，比如 x，我们必须扫描整个数组以找到下一个数字 y，即 value - x，其中 value 是输入参数。我们可以通过某种方式更改数组以使搜索更快吗？\",\"第二个思路是，在不更改数组的情况下，我们能否使用额外的空间来以某种方式加快搜索速度？这就是哈希表派上用场的地方。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1f6c14ff-b2ff-4ae0-b04f-5d0ad1b2b198','PROBLEM_DETAIL','pd-median-two-sorted-arrays','summary','zh-CN','给定两个大小分别为 `m` 和 `n` 的正序（从小到大）数组 `nums1` 和 `nums2`。请你找出并返回这两个正序数组的 **中位数**。\n\n算法的时间复杂度应该为 `O(log (m+n))`。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1fb019aa-dba7-4c7c-93ff-505081cc3400','SUBMISSION_STATUS','Memory Limit Exceeded','suggestion','zh-CN','通过优化数据结构或使用迭代方法减少内存使用。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('1fbfd2fd-c39c-4bcc-9193-a263c5d642f1','PROBLEM_DETAIL','pd-tenth-line','constraints_json','zh-CN','[\"file.txt 存在。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('202213e2-030d-4ab7-b9bd-e5cc04727f9b','PROBLEM_DETAIL','pd-merge-intervals','summary','zh-CN','以数组 `intervals` 表示若干个区间的集合，其中单个区间为 `intervals[i] = [start_i, end_i]`。请你合并所有重叠的区间，并返回一个不重叠的区间数组，该数组需恰好覆盖输入中的所有区间。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('21941f91-a8b2-43f3-acc4-5e019e0260db','CONTEST','contest-biweekly-170','description','zh-CN','难度递增的双周赛。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('21afc82c-0828-4009-abba-a182dbe8f76a','SUBMISSION_STATUS','Pending','label','en-US','Pending','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('22620422-a44d-433c-9a4e-f9cc83db0e4b','PROBLEM_DETAIL','pd-merge-intervals','constraints_json','zh-CN','[\"$1 \\\\leq intervals.length \\\\leq 10^4$\",\"intervals[i].length = 2\",\"$0 \\\\leq start_i \\\\leq end_i \\\\leq 10^4$\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('22737436-fb36-483e-a152-140d7bbef83f','PROBLEM_DETAIL','pd-median-two-sorted-arrays','constraints_json','en-US','[\"$0 \\\\leq m, n \\\\leq 10^6$\",\"$-10^6 \\\\leq nums1[i], nums2[i] \\\\leq 10^6$\",\"Runs in O(log(m + n)) time.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('22c1564a-ad20-49ab-a388-f73d5678537a','PROBLEM_DETAIL','pd-longest-substring','summary','zh-CN','给定一个字符串 `s`，请你找出其中不含有重复字符的 **最长子串** 的长度。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('23d55e2e-b355-41f9-92c2-d183a2705401','CONTEST','contest-weekly-477','description','zh-CN','参加周赛，检验你的编程能力。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('252ffc9b-0a49-4cde-accb-ecfa7520dbbf','PROBLEM_EXAMPLE','ex-islands-1','explanation','en-US','All land cells are connected into a single island.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('27835da8-87a1-45d1-8130-dd8b45447831','PROBLEM','7','title','zh-CN','第十行','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('28255ccf-a413-40e5-a193-aed4cb391357','PROBLEM_DETAIL','pd-number-of-islands','follow_up','zh-CN','如果是在线网格，单元格可以从水变成陆地，你如何计算岛屿数量？','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('29cf340b-23ef-41e8-a6b0-a4e7852f87cc','PROBLEM_TAG','divide-and-conquer','label','zh-CN','分治','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2af06b22-7cb6-418b-b364-6a91ab312a87','SUBMISSION_STATUS','System Error','description','zh-CN','评测过程中发生内部错误。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2b7a9329-ac87-43e9-a4f7-5663420925af','PROBLEM_TAG','backtracking','label','zh-CN','回溯','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2cc13638-d04b-45ed-a175-188c3a54d924','SUBMISSION_STATUS','Memory Limit Exceeded','suggestion','en-US','Reduce memory usage by optimizing data structures or using iterative approaches.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2d693b3b-e2b7-456f-a2b3-9ab22ed74904','PROBLEM_DETAIL','pd-median-two-sorted-arrays','summary','en-US','Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2d69ee50-1688-4466-ba71-0fbe12f83d57','PROBLEM_TAG','sorting','label','en-US','Sorting','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2ea99976-cf27-4d6b-a359-eccede7e8887','SUBMISSION_STATUS','Compile Error','description','en-US','Code failed to compile.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('2f0ead20-d35b-40c6-80e5-74b78d0d230f','PROBLEM_EXAMPLE','ex-two-sum-2','explanation','en-US','nums[1] + nums[2] == 6, so we return [1, 2].','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('317a72e0-ba6b-48ab-b27b-079743e441fc','PROBLEM_DETAIL','pd-number-of-islands','constraints_json','en-US','[\"$1 \\\\leq m, n \\\\leq 300$\",\"grid[i][j] is \\\"0\\\" or \\\"1\\\".\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('31c97ff5-770d-4c78-bd9c-2f7cb958d19f','SUBMISSION_STATUS','Accepted','description','en-US','All test cases passed.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('33375b3b-b50c-4332-b2cf-9cc8d4be60c0','SUBMISSION_STATUS','Output Limit Exceeded','label','zh-CN','超出输出限制','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3391d17d-cdd0-41c8-8cdf-1a438eed2e08','PROBLEM_TAG','union-find','label','en-US','Union Find','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('349a0f26-cc9b-4a32-b96a-29231fcda42e','PROBLEM_DETAIL','pd-two-sum','summary','zh-CN','给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在该数组中找出 **和为目标值** _target_ 的那 **两个** 整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案。但是，数组中同一个元素在答案里不能重复出现。\n\n你可以按任意顺序返回答案。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('34e75413-6b86-4a3b-9b8d-1ea71a6ae365','CONTEST','contest-biweekly-170','title','en-US','Biweekly Contest 170','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('36c13a75-5c3d-4f33-add7-e898a69f18d4','PROBLEM','3','title','en-US','Merge Intervals','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('372f1436-ec24-4f3b-887f-d28eaedfc977','CONTEST','contest-weekly-476','description','zh-CN','往期周赛存档。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('37ef09db-e057-4968-8c7f-ebf7e8e637dd','SUBMISSION_STATUS','Judging','description','zh-CN','你的提交正在评测中。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('39b4b953-fe17-4050-b843-49f21963ff48','PROBLEM_EXAMPLE','ex-merge-3','explanation','en-US','The second interval is contained within the first.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3a7437b9-4aa8-4d4d-9f21-28714533c6a2','PROBLEM','8','title','en-US','Print FooBar Alternately','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3c7f91ca-451a-4ae6-8ae3-0b140bdd79cf','SUBMISSION_STATUS','Wrong Answer','description','en-US','Output does not match the expected result.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3ca0f3d4-d390-40f7-9bd0-c0336b6c8874','PROBLEM_TAG','two-pointers','label','en-US','Two Pointers','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3f123bd9-9553-4e1e-820e-5c4c82a2810e','PROBLEM_EXAMPLE','ex-median-2','explanation','zh-CN','合并后数组为 [1,2,3,4]，中位数是 (2 + 3) / 2 = 2.5。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('3f652c75-702f-414a-8b66-66c953d04e7e','PROBLEM_DETAIL','pd-longest-substring','summary','en-US','Given a string s, find the length of the longest substring without repeating characters.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('40502c5e-c1c9-460b-87d0-e4606a15f75c','PROBLEM_DETAIL','pd-number-of-islands','constraints_json','zh-CN','[\"$1 \\\\leq m, n \\\\leq 300$\",\"grid[i][j] 为 \\\"0\\\" 或 \\\"1\\\"。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('43f32ae5-d92a-49b6-953f-467dc58b62fa','SUBMISSION_STATUS','Compile Error','suggestion','en-US','Fix syntax errors shown in the compiler output.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('460b1498-67a4-4b82-a43e-d2381a3ac7ca','PROBLEM_TAG','dfs','label','zh-CN','深度优先搜索','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('46ce19c8-39a8-420a-b70d-c717f73efe01','CONTEST','contest-weekly-477','description','en-US','Join this weekly contest to test your skills.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('473ca303-500f-47d5-bb09-ddc25365bcde','CONTEST','contest-weekly-476','description','en-US','Previous weekly contest archive.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('47fd71a7-3685-472f-814d-452fb23347f3','CONTEST','contest-biweekly-170','description','en-US','Biweekly contest with increasing difficulty.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('48272047-b8e7-444b-aaa3-6296990c053b','PROBLEM_TAG','divide-and-conquer','label','en-US','Divide and Conquer','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('486ef655-5d0d-4c95-99b3-67752f4a597d','SUBMISSION_STATUS','Accepted','label','zh-CN','通过','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('491e5320-46b5-40cc-a89a-1a500efbd1ef','PROBLEM_TAG','backtracking','label','en-US','Backtracking','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4941343d-2318-4589-88b5-d46f7ea2cfd0','PROBLEM_EXAMPLE','ex-islands-2','explanation','en-US','There is one island in the top-left, one in the middle, and one in the bottom-right.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4948393e-0ad8-46fc-8c18-81e71ad53109','CONTEST','contest-weekly-477','title','zh-CN','第 477 场周赛','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('49664c29-6755-4519-b361-70a4cf42ff85','PROBLEM_TAG','graph','label','en-US','Graph','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4b4154f3-4dfc-484b-b6ea-18595270f0d6','SUBMISSION_STATUS','Time Limit Exceeded','description','zh-CN','执行时间超出允许限制。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4c0affba-164a-4a5d-a703-90eb8e72a590','PROBLEM_TAG','stack','label','en-US','Stack','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4cc40e88-540c-4f00-b05d-ad25cffaeda6','PROBLEM_TAG','bfs','label','en-US','Breadth-First Search','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4ead4b2c-472d-43f2-b637-70d6ed85a662','PROBLEM_EXAMPLE','ex-merge-1','explanation','en-US','Intervals [1,3] and [2,6] overlap, merge into [1,6].','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4ecd7a08-30ae-41fa-b8ea-c479dc02b6c1','PROBLEM','6','title','zh-CN','组合两个表','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4f5f61f4-6759-4544-ad7a-8d6b855fac09','PROBLEM_EXAMPLE','ex-islands-3','explanation','en-US','No land cells, so zero islands.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4f7b1262-bd33-4956-b299-9f8a229f50a2','SUBMISSION_STATUS','Runtime Error','label','en-US','Runtime Error','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4f80dc5c-0903-4d56-b88f-bdc79f2b69ce','PROBLEM_DETAIL','pd-tenth-line','summary','en-US','Given a text file `file.txt`, print just the 10th line of the file.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('4f812fe8-f0ca-4e51-8f62-92e9d65859a2','PROBLEM_DETAIL','pd-median-two-sorted-arrays','constraints_json','zh-CN','[\"$0 \\\\leq m, n \\\\leq 10^6$\",\"$-10^6 \\\\leq nums1[i], nums2[i] \\\\leq 10^6$\",\"运行时间为 O(log(m + n))。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('502355c8-692a-4e0c-9a81-d35939e6a0bf','PROBLEM_DETAIL','pd-print-foobar','constraints_json','zh-CN','[\"n 是一个整数。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5044ab38-a376-4cf5-a3a6-84c33cced01a','PROBLEM','6','title','en-US','Combine Two Tables','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('513915e2-b272-498b-96e5-26e5ceed78a6','CONTEST','contest-weekly-476','title','zh-CN','第 476 场周赛','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('538c2ee4-d3df-4de8-8cfb-8a6c9e639e19','PROBLEM_TAG','recursion','label','en-US','Recursion','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5393345a-5f2f-4103-8684-3fd91df1a0bf','PROBLEM_EXAMPLE','ex-longest-sub-3','explanation','en-US','The answer is \"wke\", with the length of 3. Note that the answer must be a substring, \"pwke\" is a subsequence and not a substring.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('55194206-bbd9-403e-af79-59be284519d1','PROBLEM_TAG','linked-list','label','zh-CN','链表','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('56ad6598-4b41-4059-a6b9-78bdecfff1cc','PROBLEM_DETAIL','pd-two-sum','follow_up','en-US','Can you come up with an algorithm that is less than $O(n^2)$ time complexity?','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('587e3c53-a577-4c22-8fdf-2f162c7f97d7','PROBLEM_TAG','greedy','label','zh-CN','贪心','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('58873357-0d3c-4fb0-9fb4-bb89d318acb2','PROBLEM_DETAIL','pd-combine-two-tables','summary','en-US','Write a SQL query to report the first name, last name, city, and state of each person in the Person table. If the address of a personId is not present in the Address table, report null instead.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5907fb56-9505-4b46-800a-36e86ed29f17','PROBLEM_TAG','array','label','en-US','Array','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('59220c83-a8ef-4e21-beba-0cc0f0954690','SUBMISSION_STATUS','System Error','label','zh-CN','系统错误','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5a0dd1ac-3306-4a9f-92ae-a4eed627e9cb','CONTEST','contest-weekly-476','title','en-US','Weekly Contest 476','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5a34d9a6-c966-488f-a830-a09ef0ae4d86','PROBLEM_TAG','binary-search','label','zh-CN','二分查找','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5b815c2b-0147-455b-a29a-ad12a1430baf','SUBMISSION_STATUS','Memory Limit Exceeded','label','en-US','Memory Limit Exceeded','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('5d1a37a2-5fe3-4adb-b823-b159cd77b425','PROBLEM_EXAMPLE','ex-median-1','explanation','en-US','Merged array is [1,2,3] and median is 2.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6041b03d-ed05-419c-9b27-16dfc8ec7472','PROBLEM_TAG','greedy','label','en-US','Greedy','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6296b93b-861f-4bcb-b354-744f46552aac','PROBLEM_TAG','sliding-window','label','zh-CN','滑动窗口','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('642a38cd-c350-4834-9d99-62bc8e4e5613','PROBLEM_DETAIL','pd-merge-intervals','follow_up','zh-CN','如果处理流式区间数据，无法将所有区间存储在内存中，你会如何解决？','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6526fa4d-1612-45ce-ab82-08c4e9c0efea','SUBMISSION_STATUS','Wrong Answer','label','zh-CN','答案错误','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6666d2ef-f2b4-4765-863a-6848c8080eef','PROBLEM_EXAMPLE','ex-two-sum-2','explanation','zh-CN','nums[1] + nums[2] == 6，返回 [1, 2]。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('68c7a2a2-8d91-4dd7-b6f4-936a5637c032','PROBLEM','5','title','en-US','Number of Islands','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6abeb548-7c29-4813-be50-dca70f35044c','PROBLEM_TAG','tree','label','en-US','Tree','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6d9bf097-405b-44f7-8099-7250ca7ac3eb','PROBLEM_TAG','linked-list','label','en-US','Linked List','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6db89045-e18b-4251-ad96-51d815606444','SUBMISSION_STATUS','Runtime Error','description','en-US','Program crashed during execution.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6e0b294f-09a5-42be-ae9b-91cf429c3880','SUBMISSION_STATUS','Time Limit Exceeded','description','en-US','Execution time exceeded the allowed limit.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('6fdb1178-e888-4a8c-a920-69841d3a10a6','PROBLEM_DETAIL','pd-merge-intervals','summary','en-US','Given an array of intervals where intervals[i] = [start_i, end_i], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('70d9855c-1559-46a8-937e-744c7fca502a','PROBLEM_EXAMPLE','ex-longest-sub-2','explanation','en-US','The answer is \"b\", with the length of 1.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('70f422f8-bd07-4ad5-bc52-4b05fb4b68fa','PROBLEM_TAG','dynamic-programming','label','en-US','Dynamic Programming','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('71730300-57fa-4b08-9876-3e4587b64bfc','PROBLEM_DETAIL','pd-median-two-sorted-arrays','follow_up','zh-CN','你能证明为什么二分查找分区的方法是正确的吗？','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('72484ab6-8b0e-4a9e-9a90-20bd9a54ca81','PROBLEM_TAG','intervals','label','en-US','Intervals','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('727c8aac-f372-4028-9cdd-cc62aacfd80a','PROBLEM_TAG','concurrency','label','zh-CN','多线程','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('747f723c-62f5-40cf-950a-316e933669a7','PROBLEM_DETAIL','pd-print-foobar','summary','zh-CN','假设你有以下代码... 同一个 `FooBar` 实例会被传入两个不同的线程。线程 A 将会调用 `foo()` 方法，线程 B 将会调用 `bar()` 方法。请修改程序输出 `n` 次 \"foobar\"。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('756a87aa-31aa-4127-b491-809605e00063','PROBLEM_TAG','graph','label','zh-CN','图','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7913bad9-7fb5-41b1-858b-9fa66b07e8ff','PROBLEM','4','title','zh-CN','寻找两个正序数组的中位数','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7a1afc6d-6320-4d55-8d85-e305a4eb8825','PROBLEM','2','title','zh-CN','无重复字符的最长子串','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7b2d30f2-ea5a-4794-a592-25f6482ba90e','SUBMISSION_STATUS','Presentation Error','description','en-US','Output format does not match the expected format.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7be4b0a8-3934-4160-8f59-910e27b06a1a','PROBLEM_TAG','tree','label','zh-CN','树','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7e7817c6-0aa4-4d0d-bb1f-c11f8cea79f5','PROBLEM','5','title','zh-CN','岛屿数量','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('7e9279b6-73a0-4bb6-a9f0-28ffd65dac8e','PROBLEM','3','title','zh-CN','合并区间','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('80795f84-13ac-4cab-8dbb-dc7c3a9bc585','PROBLEM_DETAIL','pd-combine-two-tables','constraints_json','en-US','[\"The tables Person and Address exist.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('8267f8a7-ac82-436e-add9-f036e1500793','SUBMISSION_STATUS','Presentation Error','label','en-US','Presentation Error','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('8722dd78-16a2-485f-8121-8e8b72352aaa','PROBLEM_DETAIL','pd-longest-substring','follow_up','en-US','Can you return the substring itself while keeping O(n) time?','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('8935b0bb-b4e0-49a6-91f9-9f895d8af3d0','PROBLEM_DETAIL','pd-merge-intervals','follow_up','en-US','How would you handle streaming intervals where you cannot keep them all in memory?','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('89a8461f-de60-414b-8245-94802ae7dd80','PROBLEM_EXAMPLE','ex-longest-sub-2','explanation','zh-CN','答案是 \"b\"，长度为 1。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('8ca6ff73-0629-4f63-9ac1-b9ac433f36cd','PROBLEM_EXAMPLE','ex-merge-2','explanation','en-US','Intervals that touch at the boundary are merged.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('8cd438e6-f909-4332-8489-8ee3c826353a','CONTEST','contest-weekly-477','title','en-US','Weekly Contest 477','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('94dd9be4-3c4a-4197-a6e0-f998d096051c','PROBLEM_EXAMPLE','ex-median-3','explanation','en-US','Median is the only element 1.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('94f39bee-10fd-4582-b401-a090503ca40a','PROBLEM_TAG','dynamic-programming','label','zh-CN','动态规划','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('9572f0ff-7168-49c2-98d2-1294f38a3066','SUBMISSION_STATUS','Accepted','label','en-US','Accepted','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('9576b5a9-61d0-4465-b3de-f0d6a79ae632','PROBLEM_TAG','matrix','label','zh-CN','矩阵','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('9603394b-41df-4e94-bf75-9b3ef3f3e17e','PROBLEM_DETAIL','pd-combine-two-tables','constraints_json','zh-CN','[\"Person 和 Address 表存在。\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('96574757-5594-47b0-a641-7adaca952946','PROBLEM_TAG','concurrency','label','en-US','Concurrency','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('97037459-719b-4f7f-baa5-206f3e7eee31','PROBLEM_TAG','design','label','en-US','Design','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('99584a7a-9424-4a5f-9f66-afd69321b16d','PROBLEM_TAG','sliding-window','label','en-US','Sliding Window','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('997f1599-22b1-4fbf-a27c-9550f5ca9ff6','PROBLEM','4','title','en-US','Median of Two Sorted Arrays','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('9e2bd1f1-cadd-4240-9e7f-9e98da7d036c','PROBLEM_TAG','math','label','en-US','Math','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('a0fb6f3d-7232-4694-bc3d-4862781ab463','CONTEST','contest-biweekly-170','title','zh-CN','第 170 场双周赛','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('a2d973fe-b251-4ce0-bf03-b2eb5a911a00','PROBLEM_DETAIL','pd-two-sum','constraints_json','en-US','[\"$2 \\\\leq nums.length \\\\leq 10^4$\",\"$-10^9 \\\\leq nums[i] \\\\leq 10^9$\",\"$-10^9 \\\\leq target \\\\leq 10^9$\",\"**Only one valid answer exists.**\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('a50d71a4-3546-487e-923e-2f1b32334ae4','PROBLEM','2','title','en-US','Longest Substring Without Repeating Characters','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('a5acf554-d20f-4783-8a71-3a1cb6d18a64','SUBMISSION_STATUS','Time Limit Exceeded','label','en-US','Time Limit Exceeded','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('a75cb181-c2c0-4204-8f35-a143bf9b4373','PROBLEM_DETAIL','pd-merge-intervals','constraints_json','en-US','[\"$1 \\\\leq intervals.length \\\\leq 10^4$\",\"intervals[i].length = 2\",\"$0 \\\\leq start_i \\\\leq end_i \\\\leq 10^4$\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ac6677ed-e5be-49a1-87f4-6b5bc02d15a7','PROBLEM_TAG','two-pointers','label','zh-CN','双指针','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('af179638-99a5-4a7a-8638-c6f28f7ac84f','PROBLEM_DETAIL','pd-combine-two-tables','summary','zh-CN','编写一个SQL查询来报告 `Person` 表中每个人的姓、名、城市和州。如果 `personId` 的地址不在 `Address` 表中，则报告为 `null`。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('af5a8d91-331a-4a22-a76c-29c373a3ec11','PROBLEM_DETAIL','pd-print-foobar','summary','en-US','Suppose you are given the following code... The same instance of FooBar will be passed to two different threads. Thread A will call foo() and thread B will call bar(). Modify the program to output \"foobar\" n times.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('b3400634-9b09-42e8-8dab-2c55fffad5e8','PROBLEM_EXAMPLE','ex-two-sum-3','explanation','zh-CN','同一个元素不能使用两次，但两个值为 3 的不同元素可以使用。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('b4581ef2-be66-4b79-b603-58378e8d1c14','SUBMISSION_STATUS','Accepted','description','zh-CN','所有测试用例通过。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('b470428c-d855-44bb-bb7d-730b65e9811c','PROBLEM_TAG','math','label','zh-CN','数学','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('b4b0737e-cfab-4cdb-b12a-0bec5c26df4f','SUBMISSION_STATUS','Runtime Error','label','zh-CN','运行时错误','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('b54c4424-dc8b-49d5-9d19-df8f2636c4c6','PROBLEM_DETAIL','pd-number-of-islands','summary','en-US','Given an m x n 2D binary grid that represents a map of \"1\"s (land) and \"0\"s (water), return the number of islands. An island is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('bc13bed2-a53f-405f-9f4a-25636f1532d3','SUBMISSION_STATUS','Compile Error','label','zh-CN','编译错误','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('bcd02b64-383e-425a-bbda-92d130d71c1f','PROBLEM_EXAMPLE','ex-merge-2','explanation','zh-CN','边界相接的区间也会被合并。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('bdc59918-418c-4702-b4e0-75d072de47b6','PROBLEM_EXAMPLE','ex-two-sum-3','explanation','en-US','The same element cannot be used twice, but two different elements with value 3 can be used.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('bf05b358-df3b-4ffc-95f3-73eaedca70ce','PROBLEM_DETAIL','pd-number-of-islands','follow_up','en-US','How would you count islands in an online grid where cells flip from water to land?','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c013cdfa-9e6d-4f7d-8dfd-c20c90012533','PROBLEM_DETAIL','pd-two-sum','constraints_json','zh-CN','[\"$2 \\\\leq nums.length \\\\leq 10^4$\",\"$-10^9 \\\\leq nums[i] \\\\leq 10^9$\",\"$-10^9 \\\\leq target \\\\leq 10^9$\",\"**只会存在一个有效答案。**\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c2778fdd-bcb6-4616-adee-2668c397c29e','PROBLEM_TAG','algorithms','label','en-US','Algorithms','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c35ac00a-1ec1-4850-b293-dcce2c2055a2','PROBLEM_EXAMPLE','ex-merge-1','explanation','zh-CN','区间 [1,3] 和 [2,6] 重叠，合并为 [1,6]。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c3aacd45-a4b3-47e6-a3ab-f8228c86f4a9','PROBLEM_DETAIL','pd-two-sum','hints','en-US','[\"A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.\",\"So, if we fix one of the numbers, say x, we have to scan the entire array to find the next number y which is value - x where value is the input parameter. Can we change our array somehow so that this search becomes faster?\",\"The second train of thought is, without changing the array, can we use additional space to somehow make the search faster? This is where a hash map comes in handy.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c8a8d732-62b7-4bc0-9842-2b0322958e00','PROBLEM_EXAMPLE','ex-longest-sub-3','explanation','zh-CN','答案是 \"wke\"，长度为 3。请注意答案必须是子串，\"pwke\" 是子序列而不是子串。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c8bba8b0-7db4-4f41-a83f-639281c8c359','PROBLEM','8','title','zh-CN','交替打印FooBar','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('c8bc92d1-d60e-42e8-8317-02c1f15541cc','PROBLEM_DETAIL','pd-tenth-line','constraints_json','en-US','[\"file.txt exists.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('cd9b9045-d1cb-49c8-9bf0-f40b5475b0f9','SUBMISSION_STATUS','Runtime Error','description','zh-CN','程序在执行过程中崩溃。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('cdbca806-9a76-4c63-a882-eec57d636fb3','PROBLEM_TAG','dfs','label','en-US','Depth-First Search','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ceae311a-e87c-4265-8c1c-35944cb8bd5a','SUBMISSION_STATUS','Output Limit Exceeded','description','en-US','Program produced too much output.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('cfeff0fe-f6d5-45a2-a28b-908fff8816a1','PROBLEM_TAG','algorithms','label','zh-CN','算法','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('cff0d616-c9cf-4361-805c-57eda7c2b28d','PROBLEM_TAG','shell','label','zh-CN','Shell 脚本','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d0327a8a-e103-4dfc-9cf2-f726a4529569','PROBLEM_DETAIL','pd-two-sum','summary','en-US','Given an array of integers `nums` and an integer `target`, return _indices of the two numbers such that they add up to `target`_.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d083c462-7faf-4e22-8e7a-63edce635d78','SUBMISSION_STATUS','Wrong Answer','suggestion','en-US','Review edge cases, input parsing, and output formatting.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d13662af-5fb8-4abc-9118-d283e6bb7805','PROBLEM_TAG','array','label','zh-CN','数组','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d22e369a-73d2-43c5-b861-9b04cdcc6998','SUBMISSION_STATUS','Presentation Error','label','zh-CN','格式错误','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d70cf05d-5b6d-4337-b7be-bf74b19457bb','SUBMISSION_STATUS','Wrong Answer','label','en-US','Wrong Answer','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d715e5cf-acf3-49ac-a3a7-f7dc8fd0f615','SUBMISSION_STATUS','Output Limit Exceeded','description','zh-CN','程序产生了过多输出。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('d94e7cf3-3d28-49da-825f-f7ef5fc48bd5','PROBLEM_TAG','shell','label','en-US','Shell','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('da7d5616-b712-4836-87de-9613078c591c','SUBMISSION_STATUS','Time Limit Exceeded','suggestion','zh-CN','考虑使用更高效的算法或优化数据结构。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('db223c6e-558c-42c8-b83c-a94aea5aed03','PROBLEM_DETAIL','pd-print-foobar','constraints_json','en-US','[\"n is an integer.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('db70f57e-8c84-48f4-8382-bf7e769307a5','SUBMISSION_STATUS','Runtime Error','suggestion','en-US','Check for null pointers, array bounds, and division by zero.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('db9afe7d-69dd-466c-ae8c-c74b2580281d','PROBLEM_EXAMPLE','ex-longest-sub-1','explanation','en-US','The answer is \"abc\", with the length of 3.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('dbf5c98f-61f5-4668-95d6-982751defe55','PROBLEM_EXAMPLE','ex-longest-sub-1','explanation','zh-CN','答案是 \"abc\"，长度为 3。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('dc02ff0f-72cf-4703-9e46-977e0898cd1e','PROBLEM_DETAIL','pd-tenth-line','summary','zh-CN','给定一个文本文件 `file.txt`，请只打印出这个文件中的第十行。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('dc0c9149-a779-458e-bef9-0039027d8fc5','SUBMISSION_STATUS','Compile Error','description','zh-CN','代码编译失败。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('de7335a2-7347-4abb-8879-80420e6e9ed9','SUBMISSION_STATUS','Memory Limit Exceeded','description','zh-CN','内存使用超出允许限制。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('df7673ee-1b3a-42a3-ba3d-258b0fba6df5','PROBLEM_TAG','string','label','zh-CN','字符串','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e0843efd-4b9c-4288-810f-a5c3bfc4ea28','PROBLEM_TAG','design','label','zh-CN','设计','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e13fad60-9b2c-4a52-89ac-86e06daa57ab','SUBMISSION_STATUS','Time Limit Exceeded','label','zh-CN','超出时间限制','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e2233819-1a5f-492d-86ba-6ef95d513ea7','PROBLEM_TAG','sorting','label','zh-CN','排序','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e4673b3a-5533-432a-8661-d27ab77e8b59','PROBLEM_EXAMPLE','ex-median-2','explanation','en-US','Merged array is [1,2,3,4] and median is (2 + 3) / 2 = 2.5.','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e4cf336d-d6d7-4126-8f4d-8447f6ed5ed8','SUBMISSION_STATUS','Judging','label','zh-CN','评测中','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e6e6adc3-a9ba-4fec-9106-785785ad4627','SUBMISSION_STATUS','Wrong Answer','description','zh-CN','输出结果与预期不符。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e830ad3e-5067-460c-81ee-d06ed8cb113d','PROBLEM_TAG','recursion','label','zh-CN','递归','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e9c13057-be1e-4acf-9a42-b17546817844','PROBLEM_TAG','queue','label','zh-CN','队列','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('e9c86576-6bd4-4eb9-a9b5-f2f239e45621','PROBLEM','1','title','zh-CN','两数之和','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('eaa052b3-7b3d-490a-9841-9719d1f0dff9','SUBMISSION_STATUS','Judging','label','en-US','Judging','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('eb10e1a4-4937-45d7-bd09-42c4ed4f7814','PROBLEM_EXAMPLE','ex-median-3','explanation','zh-CN','中位数是唯一的元素 1。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ebd9b73d-8c66-4a86-be07-cbaeb984c0aa','SUBMISSION_STATUS','Wrong Answer','suggestion','zh-CN','检查边界情况、输入解析和输出格式。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ec02fdd1-6c6e-4c7e-8a5d-7a8031febb97','PROBLEM_TAG','bfs','label','zh-CN','广度优先搜索','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ee6569c1-1943-4290-ba8f-9f1d6f7ed3bd','PROBLEM_TAG','database','label','en-US','Database','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('eeeced8b-c97c-4a99-8ca1-dc324b231c8c','PROBLEM_DETAIL','pd-number-of-islands','summary','zh-CN','给你一个由 `\'1\'`（陆地）和 `\'0\'`（水）组成的的二维网格，请你计算网格中岛屿的数量。\n\n岛屿总是被水包围，并且每座岛屿只能由水平方向和/或竖直方向上相邻的陆地连接形成。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('ef796e58-cba4-486f-9cf2-06d60765d4c8','PROBLEM_EXAMPLE','ex-islands-1','explanation','zh-CN','所有陆地单元格连成了一个岛屿。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f099fce4-29c6-47ef-a656-93d29c23a60c','PROBLEM_DETAIL','pd-longest-substring','constraints_json','en-US','[\"$0 \\\\leq s.length \\\\leq 5 \\\\times 10^4$\",\"s consists of English letters, digits, symbols, and spaces.\"]','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f1c12abd-c8e3-42e6-a062-d8b9e5172909','PROBLEM_EXAMPLE','ex-median-1','explanation','zh-CN','合并后数组为 [1,2,3]，中位数是 2。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f2341747-2068-46e5-b001-297b1b0888ee','SUBMISSION_STATUS','Pending','label','zh-CN','等待中','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f371dbc9-c914-4f22-bf92-50bcb6b5db4e','PROBLEM_TAG','union-find','label','zh-CN','并查集','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f3d042ca-cf76-4c9f-ab94-3e2d83d333a5','SUBMISSION_STATUS','System Error','label','en-US','System Error','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f43b3c06-b96e-47f7-ae2b-542cb848d929','SUBMISSION_STATUS','Runtime Error','suggestion','zh-CN','检查空指针、数组越界和除零错误。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f45e73be-4fb0-4fe2-93ac-9f79abeff8a8','SUBMISSION_STATUS','Presentation Error','description','zh-CN','输出格式与预期格式不符。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('f9a20e7c-f936-42e2-9030-eba43d78d166','PROBLEM_TAG','matrix','label','en-US','Matrix','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('fa8c4c9f-7607-48b0-bec4-4bb3a05eefdf','PROBLEM_TAG','binary-search','label','en-US','Binary Search','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('fb6896c5-0d36-4a08-8512-d4884e6606ed','SUBMISSION_STATUS','Compile Error','label','en-US','Compile Error','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('fbdae632-3049-4885-823b-711660197677','PROBLEM_EXAMPLE','ex-islands-3','explanation','zh-CN','没有陆地单元格，所以岛屿数量为零。','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('fd068762-5c3e-469a-9d6c-885d1703c3d6','PROBLEM_TAG','stack','label','zh-CN','栈','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
INSERT INTO `translations` (`id`, `entity_type`, `entity_id`, `field_name`, `locale`, `content`, `created_at`, `updated_at`, `created_by`, `updated_by`) VALUES ('fd8095f4-24b0-4313-9b64-96530ce9152a','PROBLEM_TAG','heap','label','en-US','Heap (Priority Queue)','2026-03-22 05:44:30.924','2026-03-22 05:44:30.924',NULL,NULL);
SET FOREIGN_KEY_CHECKS=1;
