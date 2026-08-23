-- V20260823151000__Create_Missing_App_Owner_Tables.sql
-- The per-owner App bootstrap (V20260729140300) was authored from a partial
-- snapshot and omitted tables that the legacy single-database chain
-- (V20260602_120000 .. V20260729170000) had already finalized. This additive
-- migration recreates them for the app owner from their final contracts.
-- CREATE TABLE IF NOT EXISTS keeps it safe on schemas that already have any.

CREATE TABLE IF NOT EXISTS `achievements` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `icon` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?????????????????',
  `tier` int NOT NULL DEFAULT '1' COMMENT '1=?, 2=?, 3=?, 4=??',
  `criteria` json DEFAULT NULL COMMENT '?????JSON ??: {type, target}',
  `points` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`),
  KEY `idx_achievements_category` (`category`),
  KEY `idx_achievements_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_achievements` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `achievement_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `earned_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_id`),
  KEY `idx_user_achievements_user_id` (`user_id`),
  KEY `idx_user_achievements_achievement_id` (`achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `appeals` (
  `id` varchar(40) NOT NULL,
  `queue_id` varchar(40) NOT NULL,
  `appellant_id` varchar(40) NOT NULL,
  `reason` text NOT NULL,
  `evidence` text,
  `status` enum('PENDING','UNDER_REVIEW','APPROVED','REJECTED','ESCALATED') NOT NULL DEFAULT 'PENDING',
  `reviewed_by_id` varchar(40) DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `response` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `appeals_queue_id_idx` (`queue_id`),
  KEY `appeals_appellant_id_idx` (`appellant_id`),
  KEY `appeals_status_idx` (`status`),
  KEY `appeals_reviewed_by_id_fkey` (`reviewed_by_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `moderation_queue` (
  `id` varchar(40) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `author_id` varchar(40) NOT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `status` enum('PENDING','UNDER_REVIEW','RESOLVED','DISMISSED','APPEAL_PENDING') NOT NULL DEFAULT 'PENDING',
  `report_count` int NOT NULL DEFAULT '0',
  `primary_category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') DEFAULT NULL,
  `assigned_to_id` varchar(40) DEFAULT NULL,
  `assigned_at` datetime(3) DEFAULT NULL,
  `reviewed_by_id` varchar(40) DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `resolution` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED','APPEAL_REJECTED') DEFAULT NULL,
  `resolution_note` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `moderation_queue_entity_type_entity_id_key` (`entity_type`,`entity_id`),
  KEY `moderation_queue_status_idx` (`status`),
  KEY `moderation_queue_assigned_to_id_idx` (`assigned_to_id`),
  KEY `moderation_queue_priority_idx` (`priority`),
  KEY `moderation_queue_author_id_idx` (`author_id`),
  KEY `moderation_queue_reviewed_by_id_fkey` (`reviewed_by_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `moderation_actions` (
  `id` varchar(40) NOT NULL,
  `queue_id` varchar(40) NOT NULL,
  `action` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED','APPEAL_REJECTED') NOT NULL,
  `performed_by_id` varchar(40) NOT NULL,
  `note` text,
  `duration_days` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `moderation_actions_queue_id_idx` (`queue_id`),
  KEY `moderation_actions_performed_by_id_idx` (`performed_by_id`),
  KEY `moderation_actions_action_idx` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `reports` (
  `id` varchar(40) NOT NULL,
  `reporter_id` varchar(40) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') NOT NULL,
  `reason` text,
  `evidence` text,
  `status` enum('PENDING','REVIEWED','RESOLVED','DISMISSED') NOT NULL DEFAULT 'PENDING',
  `queue_id` varchar(40) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reports_reporter_entity` (`reporter_id`,`entity_type`,`entity_id`),
  KEY `reports_entity_type_entity_id_idx` (`entity_type`,`entity_id`),
  KEY `reports_reporter_id_idx` (`reporter_id`),
  KEY `reports_status_idx` (`status`),
  KEY `reports_category_idx` (`category`),
  KEY `reports_queue_id_fkey` (`queue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_bans` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `is_permanent` tinyint(1) NOT NULL DEFAULT '0',
  `reason` text NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') DEFAULT NULL,
  `queue_id` varchar(40) DEFAULT NULL,
  `action_id` varchar(40) DEFAULT NULL,
  `banned_by_id` varchar(40) NOT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ends_at` datetime(3) DEFAULT NULL,
  `unbanned_at` datetime(3) DEFAULT NULL,
  `unbanned_by_id` varchar(40) DEFAULT NULL,
  `unban_reason` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_bans_user_id_idx` (`user_id`),
  KEY `user_bans_ends_at_idx` (`ends_at`),
  KEY `user_bans_banned_by_id_fkey` (`banned_by_id`),
  KEY `user_bans_unbanned_by_id_fkey` (`unbanned_by_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_warnings` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `queue_id` varchar(40) DEFAULT NULL,
  `action_id` varchar(40) DEFAULT NULL,
  `reason` text NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') NOT NULL,
  `acknowledged_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_warnings_user_id_idx` (`user_id`),
  KEY `user_warnings_created_at_idx` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `collection_items` (
  `id` varchar(40) NOT NULL,
  `collection_id` varchar(40) NOT NULL,
  `target_id` varchar(50) NOT NULL,
  `target_type` enum('PROBLEM','SOLUTION','FORUM_POST','PROBLEM_LIST','SOLUTION_COMMENT','FORUM_COMMENT') NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `note` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `collection_items_collection_id_target_type_target_id_key` (`collection_id`,`target_type`,`target_id`),
  KEY `collection_items_target_type_target_id_idx` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `collections` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` text,
  `icon` varchar(50) DEFAULT NULL,
  `color` varchar(20) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `collections_user_id_name_key` (`user_id`,`name`),
  KEY `collections_user_id_idx` (`user_id`),
  KEY `collections_user_id_is_default_idx` (`user_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `edge_operations` (
  `id` varchar(40) NOT NULL,
  `target_id` varchar(40) NOT NULL,
  `target_type` enum('SOLUTION','SOLUTION_COMMENT','FORUM_POST','FORUM_COMMENT','PROBLEM','PROBLEM_LIST') NOT NULL,
  `operator_id` varchar(40) NOT NULL,
  `operation_type` enum('VOTE_UP','VOTE_DOWN','ANALYZE','VIEW','LIKE','DISLIKE','FAVORITE') NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `edge_ops_unique` (`operator_id`,`operation_type`,`target_type`,`target_id`),
  KEY `edge_ops_target` (`target_type`,`target_id`),
  KEY `edge_ops_operation_target` (`operation_type`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_users` (
  `username` varchar(60) NOT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `karma` int NOT NULL DEFAULT '0',
  `id` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_users_username_key` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_communities` (
  `id` varchar(40) NOT NULL,
  `name` varchar(120) NOT NULL,
  `slug` varchar(60) NOT NULL,
  `description` text NOT NULL,
  `members` int NOT NULL DEFAULT '0',
  `online` int NOT NULL DEFAULT '0',
  `icon` varchar(255) DEFAULT NULL,
  `color` varchar(20) DEFAULT NULL,
  `banner` varchar(255) DEFAULT NULL,
  `posts_count` int NOT NULL DEFAULT '0',
  `posts_today` int NOT NULL DEFAULT '0',
  `posts_week` int NOT NULL DEFAULT '0',
  `is_official` tinyint(1) NOT NULL DEFAULT '0',
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `visibility` enum('PUBLIC','RESTRICTED','PRIVATE') NOT NULL DEFAULT 'PUBLIC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_communities_slug_key` (`slug`),
  KEY `forum_communities_slug_idx` (`slug`),
  KEY `forum_communities_visibility_is_featured_idx` (`visibility`,`is_featured`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_community_members` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `role` enum('OWNER','MODERATOR','MEMBER') NOT NULL DEFAULT 'MEMBER',
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_community_members_community_id_user_id_key` (`community_id`,`user_id`),
  KEY `forum_community_members_user_id_idx` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_comments` (
  `id` varchar(40) NOT NULL,
  `post_id` varchar(40) NOT NULL,
  `parent_id` varchar(40) DEFAULT NULL,
  `author_id` varchar(40) NOT NULL,
  `body` text NOT NULL,
  `markdown` text,
  `created_at` datetime(3) NOT NULL,
  `edited_at` datetime(3) DEFAULT NULL,
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `forum_comments_author_id_fkey` (`author_id`),
  KEY `forum_comments_parent_id_fkey` (`parent_id`),
  KEY `forum_comments_post_id_fkey` (`post_id`),
  KEY `forum_comments_post_id_created_at_idx` (`post_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_tags` (
  `id` varchar(40) NOT NULL,
  `name` varchar(60) NOT NULL,
  `slug` varchar(60) NOT NULL,
  `description` text,
  `color` varchar(20) DEFAULT NULL,
  `usage_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_tags_name_key` (`name`),
  UNIQUE KEY `forum_tags_slug_key` (`slug`),
  KEY `forum_tags_slug_idx` (`slug`),
  KEY `forum_tags_usage_count_idx` (`usage_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_examples` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `example_order` int NOT NULL DEFAULT '0',
  `input_text` text NOT NULL,
  `output_text` text NOT NULL,
  `explanation` text,
  `inputs` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `problem_examples_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_languages` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `label` varchar(50) NOT NULL,
  `value` varchar(50) NOT NULL,
  `style` varchar(20) DEFAULT NULL,
  `starter_code` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `problem_languages_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_notes` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `content` mediumtext NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_notes_user_id_problem_id_key` (`user_id`,`problem_id`),
  KEY `problem_notes_problem_id_fkey` (`problem_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_tag_relations` (
  `problem_id` bigint NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `problem_tag_relations_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_tags` (
  `id` varchar(40) NOT NULL,
  `label` varchar(120) NOT NULL,
  `slug` varchar(120) DEFAULT NULL,
  `color` varchar(20) DEFAULT NULL,
  `description` text,
  `usage_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_tags_slug_key` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `problem_id` bigint NOT NULL,
  `version_number` int NOT NULL,
  `snapshot_json` json NOT NULL COMMENT '??????',
  `change_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?? | ?? | ??',
  `change_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????',
  `created_by` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_version` (`problem_id`,`version_number`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_lists` (
  `id` varchar(50) NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` text,
  `author_id` varchar(40) NOT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  `banner_tag` varchar(30) DEFAULT NULL,
  `banner_icon` varchar(50) DEFAULT NULL,
  `banner_theme` varchar(30) DEFAULT NULL,
  `banner_order` int unsigned NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_version` (`version`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_is_featured` (`is_featured`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_list_categories` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text,
  `icon` varchar(50) DEFAULT NULL,
  `color` varchar(20) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_list_bookmarks` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `list_id` varchar(36) NOT NULL,
  `category_id` varchar(36) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_list` (`user_id`,`list_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_list_id` (`list_id`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_list_problem_relations` (
  `list_id` varchar(50) NOT NULL,
  `problem_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `added_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`problem_id`,`list_id`),
  KEY `problem_list_problem_relations_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `solution_comments` (
  `id` varchar(40) NOT NULL,
  `solution_id` varchar(40) NOT NULL,
  `parent_id` varchar(40) DEFAULT NULL,
  `user_id` varchar(40) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `solution_comments_parent_id_fkey` (`parent_id`),
  KEY `solution_comments_solution_id_fkey` (`solution_id`),
  KEY `solution_comments_user_id_fkey` (`user_id`),
  KEY `solution_comments_solution_id_created_at_idx` (`solution_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `solution_topics` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_solution_topics_slug` (`slug`),
  KEY `idx_solution_topics_active_deleted_sort` (`is_active`,`is_deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `subscriptions` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `plan` enum('FREE','PREMIUM_MONTHLY','PREMIUM_YEARLY') NOT NULL DEFAULT 'FREE',
  `status` enum('ACTIVE','CANCELLED','EXPIRED','PENDING') NOT NULL DEFAULT 'ACTIVE',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `transaction_id` varchar(100) DEFAULT NULL COMMENT '????ID',
  `auto_renew` tinyint(1) NOT NULL DEFAULT '1' COMMENT '??????',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '?????',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '??????',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `subscriptions_user_id_idx` (`user_id`),
  KEY `subscriptions_status_idx` (`status`),
  KEY `subscriptions_is_deleted_idx` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_cases` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `is_sample` tinyint(1) NOT NULL DEFAULT '0',
  `is_hidden` tinyint(1) NOT NULL DEFAULT '0',
  `test_order` int NOT NULL DEFAULT '0',
  `input_text` text NOT NULL,
  `output_text` text NOT NULL,
  `inputs` json DEFAULT NULL,
  `explanation` text,
  `constraints` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` int NOT NULL DEFAULT '1',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_problem_id_test_order` (`problem_id`,`test_order`),
  CONSTRAINT `fk_test_cases_problem_id` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `translations` (
  `id` varchar(40) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `field_name` varchar(50) NOT NULL,
  `locale` varchar(10) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `created_by` varchar(40) DEFAULT NULL,
  `updated_by` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `translations_entity_type_entity_id_field_name_locale_key` (`entity_type`,`entity_id`,`field_name`,`locale`),
  KEY `translations_entity_type_entity_id_locale_idx` (`entity_type`,`entity_id`,`locale`),
  KEY `translations_locale_idx` (`locale`),
  KEY `translations_created_by_idx` (`created_by`),
  KEY `translations_updated_by_idx` (`updated_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_follows` (
  `follower_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???',
  `following_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`follower_id`,`following_id`),
  KEY `idx_user_follows_follower` (`follower_id`),
  KEY `idx_user_follows_following` (`following_id`),
  KEY `idx_user_follows_created` (`created_at`),
  KEY `idx_user_follows_following_created` (`following_id`,`created_at` DESC),
  KEY `idx_user_follows_follower_created` (`follower_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `submission_result_outbox` (
  `id` varchar(40) NOT NULL COMMENT 'Outbox row ID (UUID)',
  `submission_id` varchar(40) NOT NULL,
  `generation` bigint NOT NULL DEFAULT '0' COMMENT 'Fence generation (monotonic rejudge key); legacy path uses 0',
  `user_id` varchar(40) NOT NULL,
  `problem_id` varchar(120) NOT NULL,
  `verdict` varchar(30) NOT NULL COMMENT 'Wire-format verdict (ACCEPTED, WRONG_ANSWER, ...)',
  `runtime_ms` int NOT NULL DEFAULT '0',
  `memory_mb` double NOT NULL DEFAULT '0',
  `contest_id` varchar(40) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DELIVERED/DEAD',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL COMMENT 'Dispatcher lease owner',
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_result_sub_gen` (`submission_id`,`generation`) COMMENT 'One result event per (submission, generation)',
  KEY `idx_result_state_retry` (`state`,`next_retry_at`),
  KEY `idx_submission_result_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `judge_outbox` (
  `id` varchar(40) NOT NULL,
  `submission_id` varchar(40) NOT NULL,
  `generation` bigint NOT NULL,
  `payload` json NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `is_shadow` tinyint(1) NOT NULL DEFAULT '1',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_dispatch` (`submission_id`,`generation`),
  KEY `idx_state_retry` (`state`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `integration_outbox` (
  `event_id` varchar(40) NOT NULL COMMENT 'Unique event identifier (UUID)',
  `owner` varchar(20) NOT NULL COMMENT 'Publishing Owner: Auth/Admin/App',
  `aggregate_id` varchar(255) NOT NULL COMMENT 'Root aggregate identifier',
  `aggregate_version` bigint NOT NULL DEFAULT '0' COMMENT 'Aggregate version for ordering',
  `causation_id` varchar(40) DEFAULT NULL COMMENT 'Causation event ID (saga chaining)',
  `trace_id` varchar(40) DEFAULT NULL COMMENT 'OpenTelemetry trace ID',
  `event_type` varchar(120) NOT NULL COMMENT 'Domain event type (e.g., UserRegistered)',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT 'Payload schema version',
  `payload` json NOT NULL COMMENT 'Event payload as JSON',
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DELIVERED/DEAD',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `stream_id` varchar(80) DEFAULT NULL COMMENT 'Redis Streams XADD return ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL COMMENT 'Dispatcher lease owner',
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  KEY `idx_outbox_state_retry` (`state`,`next_retry_at`),
  KEY `idx_outbox_aggregate` (`aggregate_id`,`aggregate_version`),
  KEY `idx_outbox_owner_type` (`owner`,`event_type`),
  KEY `idx_integration_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
