ALTER DATABASE ulticode CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `DailyRecommendation`;
CREATE TABLE `DailyRecommendation` (
  `id` varchar(191) NOT NULL,
  `user_id` varchar(191) NOT NULL,
  `problem_id` bigint NOT NULL,
  `problem_slug` varchar(191) NOT NULL,
  `problem_title` varchar(191) NOT NULL,
  `difficulty` varchar(191) NOT NULL,
  `score` decimal(65,30) NOT NULL,
  `tags` json NOT NULL,
  `reason` varchar(191) NOT NULL,
  `scenario` enum('DAILY','SIMILAR','WEAK_POINT','CHALLENGE') NOT NULL DEFAULT 'DAILY',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL COMMENT '推荐过期时间，插入时由应用层设置为 NOW() + 1 day',
  `is_clicked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户是否点击',
  `is_solved` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户是否完成',
  PRIMARY KEY (`id`),
  UNIQUE KEY `DailyRecommendation_user_id_problem_id_scenario_key` (`user_id`,`problem_id`,`scenario`),
  KEY `DailyRecommendation_user_id_idx` (`user_id`),
  KEY `DailyRecommendation_scenario_idx` (`scenario`),
  KEY `DailyRecommendation_created_at_idx` (`created_at`),
  KEY `DailyRecommendation_user_id_fkey` (`user_id`),
  KEY `idx_expires_at` (`expires_at`),
  KEY `idx_user_clicked` (`user_id`,`is_clicked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `achievements`;
CREATE TABLE `achievements` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `icon` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题解决、连续性、竞赛、社交、特殊',
  `tier` int NOT NULL DEFAULT '1' COMMENT '1=铜, 2=银, 3=金, 4=铂金',
  `criteria` json DEFAULT NULL COMMENT '成就条件，JSON 格式: {type, target}',
  `points` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`),
  KEY `idx_achievements_category` (`category`),
  KEY `idx_achievements_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `appeals`;
CREATE TABLE `appeals` (
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
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(100) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `audit_logs_performer_id_idx` (`performer_id`),
  KEY `audit_logs_user_id_idx` (`user_id`),
  KEY `audit_logs_entity_type_entity_id_idx` (`entity_type`,`entity_id`),
  KEY `audit_logs_created_at_idx` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `collection_items`;
CREATE TABLE `collection_items` (
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
DROP TABLE IF EXISTS `collections`;
CREATE TABLE `collections` (
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
DROP TABLE IF EXISTS `contest_analytics`;
CREATE TABLE `contest_analytics` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `total_registered` int NOT NULL DEFAULT '0',
  `total_participated` int NOT NULL DEFAULT '0',
  `completion_rate` double NOT NULL DEFAULT '0',
  `problem_stats` json DEFAULT NULL,
  `score_distribution` json DEFAULT NULL,
  `time_distribution` json DEFAULT NULL,
  `top_users` json DEFAULT NULL,
  `generated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_analytics_contest_id_key` (`contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_announcements`;
CREATE TABLE `contest_announcements` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_participants`;
CREATE TABLE `contest_participants` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `status` enum('REGISTERED','STARTED','FINISHED','DISQUALIFIED') NOT NULL DEFAULT 'REGISTERED',
  `registered_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `final_rank` int DEFAULT NULL,
  `total_penalty` int NOT NULL DEFAULT '0',
  `total_score` int NOT NULL DEFAULT '0',
  `total_attempts` int NOT NULL DEFAULT '0',
  `last_solve_time` int DEFAULT NULL,
  `virtual_session_id` varchar(40) DEFAULT NULL,
  `checked_in_at` datetime(3) DEFAULT NULL,
  `total_time` int NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_participants_contest_id_user_id_virtual_session_id_key` (`contest_id`,`user_id`,`virtual_session_id`),
  KEY `contest_participants_user_id_idx` (`user_id`),
  KEY `contest_participants_contest_id_final_rank_idx` (`contest_id`,`final_rank`),
  KEY `contest_participants_virtual_session_id_fkey` (`virtual_session_id`),
  KEY `contest_participants_user_id_status_is_virtual_idx` (`user_id`,`status`,`is_virtual`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_problem_results`;
CREATE TABLE `contest_problem_results` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `contest_problem_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `participant_id` varchar(40) NOT NULL,
  `ranking_id` varchar(40) DEFAULT NULL,
  `is_solved` tinyint(1) NOT NULL DEFAULT '0',
  `score` int NOT NULL DEFAULT '0',
  `attempts` int NOT NULL DEFAULT '0',
  `first_solve_time` int DEFAULT NULL,
  `penalty_time` int NOT NULL DEFAULT '0',
  `best_submission_id` varchar(40) DEFAULT NULL,
  `time_spent` int NOT NULL DEFAULT '0',
  `time_bonus` int NOT NULL DEFAULT '0',
  `is_first_solve` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_problem_results_participant_id_contest_problem_id_key` (`participant_id`,`contest_problem_id`),
  KEY `contest_problem_results_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `contest_problem_results_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_problem_results_ranking_id_fkey` (`ranking_id`),
  KEY `contest_problem_results_user_id_fkey` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_problems`;
CREATE TABLE `contest_problems` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `problem_index` varchar(10) NOT NULL,
  `score` int NOT NULL DEFAULT '0',
  `penalty_per_wrong` int DEFAULT NULL,
  `solved_count` int NOT NULL DEFAULT '0',
  `submission_count` int NOT NULL DEFAULT '0',
  `label` varchar(10) DEFAULT NULL,
  `base_score` int DEFAULT NULL,
  `time_bonus` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `contest_problems_contest_id_idx` (`contest_id`),
  KEY `contest_problems_problem_id_fkey` (`problem_id`),
  UNIQUE KEY `uk_contest_problem_id` (`contest_id`,`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_rankings`;
CREATE TABLE `contest_rankings` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `rank` int NOT NULL,
  `rating_before` int NOT NULL DEFAULT '1500',
  `rating_after` int NOT NULL DEFAULT '1500',
  `rating_change` int NOT NULL DEFAULT '0',
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `solved_count` int NOT NULL DEFAULT '0',
  `total_penalty` int NOT NULL DEFAULT '0',
  `total_score` int NOT NULL DEFAULT '0',
  `finish_time` int DEFAULT NULL,
  `total_attempts` int NOT NULL DEFAULT '0',
  `problem_stats_snapshot` json DEFAULT NULL,
  `is_frozen` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_rankings_contest_id_user_id_is_virtual_key` (`contest_id`,`user_id`,`is_virtual`),
  KEY `contest_rankings_contest_id_rank_idx` (`contest_id`,`rank`),
  KEY `contest_rankings_user_id_idx` (`user_id`),
  KEY `contest_rankings_contest_id_is_virtual_rank_idx` (`contest_id`,`is_virtual`,`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_scoring_rules`;
CREATE TABLE `contest_scoring_rules` (
  `id` varchar(40) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text,
  `base_score_per_problem` int NOT NULL DEFAULT '100',
  `time_bonus_per_minute` int NOT NULL DEFAULT '1',
  `wrong_answer_penalty` int NOT NULL DEFAULT '5',
  `time_limit_penalty` int NOT NULL DEFAULT '0',
  `first_solve_bonus` int NOT NULL DEFAULT '10',
  `full_score_bonus` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contest_submissions`;
CREATE TABLE `contest_submissions` (
  `id` varchar(40) NOT NULL,
  `submission_id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `contest_problem_id` varchar(40) NOT NULL,
  `participant_id` varchar(40) NOT NULL,
  `virtual_session_id` varchar(40) DEFAULT NULL,
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_from_start` int NOT NULL,
  `is_accepted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_submissions_contest_id_participant_id_idx` (`contest_id`,`participant_id`),
  KEY `contest_submissions_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_submissions_participant_id_fkey` (`participant_id`),
  KEY `contest_submissions_submission_id_fkey` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_submitted_at_idx` (`contest_id`,`participant_id`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `contests`;
CREATE TABLE `contests` (
  `id` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `contest_type` enum('ICPC','IOI','CUSTOM') NOT NULL,
  `start_time` datetime(3) NOT NULL,
  `duration_minutes` int NOT NULL,
  `status` enum('DRAFT','UPCOMING','RUNNING','FINISHED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `penalty_per_wrong` int NOT NULL DEFAULT '300',
  `scoring_mode` enum('SCORE','ICPC','IOI') NOT NULL DEFAULT 'SCORE',
  `tie_breaker` enum('LAST_SOLVE_TIME','TOTAL_ATTEMPTS','NONE') NOT NULL DEFAULT 'LAST_SOLVE_TIME',
  `registered_count` int NOT NULL DEFAULT '0',
  `participant_count` int NOT NULL DEFAULT '0',
  `is_rated` tinyint(1) NOT NULL DEFAULT '1',
  `description` text,
  `cover_image` varchar(255) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` varchar(40) DEFAULT NULL,
  `is_visible` tinyint(1) NOT NULL DEFAULT '1',
  `rules` text,
  `updated_at` datetime(3) NOT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `actual_start_time` datetime(3) DEFAULT NULL,
  `actual_end_time` datetime(3) DEFAULT NULL,
  `registration_start` datetime(3) DEFAULT NULL,
  `registration_end` datetime(3) DEFAULT NULL,
  `freeze_time` datetime(3) DEFAULT NULL,
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `max_participants` int DEFAULT NULL,
  `scoring_rule_id` varchar(40) DEFAULT NULL,
  `submission_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contests_status_start_time_idx` (`status`,`start_time`),
  KEY `contests_contest_type_idx` (`contest_type`),
  KEY `contests_slug_idx` (`slug`),
  KEY `contests_status_is_visible_start_time_idx` (`status`,`is_visible`,`start_time`),
  KEY `contests_scoring_rule_id_fkey` (`scoring_rule_id`),
  KEY `idx_contests_created_by` (`created_by`),
  KEY `idx_contests_is_virtual` (`is_virtual`),
  KEY `idx_contests_status_type` (`status`,`contest_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `edge_operations`;
CREATE TABLE `edge_operations` (
  `id` varchar(40) NOT NULL,
  `target_id` varchar(40) NOT NULL,
  `target_type` enum('SOLUTION','SOLUTION_COMMENT','FORUM_POST','FORUM_COMMENT','PROBLEM','PROBLEM_LIST') NOT NULL,
  `operator_id` varchar(40) NOT NULL,
  `operation_type` enum('VOTE_UP','VOTE_DOWN','ANALYZE') NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `edge_ops_unique` (`operator_id`,`operation_type`,`target_type`,`target_id`),
  KEY `edge_ops_target` (`target_type`,`target_id`),
  KEY `edge_ops_operation_target` (`operation_type`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `first_solve_records`;
CREATE TABLE `first_solve_records` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `solved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`,`problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`),
  KEY `first_solve_records_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_comments`;
CREATE TABLE `forum_comments` (
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
DROP TABLE IF EXISTS `forum_communities`;
CREATE TABLE `forum_communities` (
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
DROP TABLE IF EXISTS `forum_community_links`;
CREATE TABLE `forum_community_links` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `label` varchar(120) NOT NULL,
  `url` varchar(255) NOT NULL,
  `description` text,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `forum_community_links_community_id_sort_order_idx` (`community_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_community_members`;
CREATE TABLE `forum_community_members` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `role` enum('OWNER','MODERATOR','MEMBER') NOT NULL DEFAULT 'MEMBER',
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_community_members_community_id_user_id_key` (`community_id`,`user_id`),
  KEY `forum_community_members_user_id_idx` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_community_permissions`;
CREATE TABLE `forum_community_permissions` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `role` enum('OWNER','MODERATOR','MEMBER') NOT NULL,
  `can_post` tinyint(1) NOT NULL DEFAULT '1',
  `can_comment` tinyint(1) NOT NULL DEFAULT '1',
  `can_moderate` tinyint(1) NOT NULL DEFAULT '0',
  `can_manage` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_community_permissions_community_id_role_key` (`community_id`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_community_rules`;
CREATE TABLE `forum_community_rules` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `body` text NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `forum_community_rules_community_id_sort_order_idx` (`community_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_community_tags`;
CREATE TABLE `forum_community_tags` (
  `community_id` varchar(40) NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`community_id`,`tag_id`),
  KEY `forum_community_tags_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_post_tag_relations`;
CREATE TABLE `forum_post_tag_relations` (
  `post_id` varchar(40) NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`post_id`,`tag_id`),
  KEY `forum_post_tag_relations_tag_id_idx` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_posts`;
CREATE TABLE `forum_posts` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `permalink` varchar(255) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `flair_type` enum('announcement','discussion','showcase','question','hiring') DEFAULT NULL,
  `flair_label` varchar(60) DEFAULT NULL,
  `tags` json NOT NULL,
  `excerpt` text,
  `media` json DEFAULT NULL,
  `recommendation` json DEFAULT NULL,
  `vote_state` enum('upvoted','downvoted','neutral') NOT NULL DEFAULT 'neutral',
  `is_saved` tinyint(1) NOT NULL DEFAULT '0',
  `impressions` int NOT NULL DEFAULT '0',
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL,
  `stats` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `forum_posts_community_id_fkey` (`community_id`),
  KEY `forum_posts_user_id_fkey` (`user_id`),
  KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`),
  KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_tags`;
CREATE TABLE `forum_tags` (
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
DROP TABLE IF EXISTS `forum_users`;
CREATE TABLE `forum_users` (
  `username` varchar(60) NOT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `karma` int NOT NULL DEFAULT '0',
  `id` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_users_username_key` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `global_rankings`;
CREATE TABLE `global_rankings` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `username` varchar(120) NOT NULL,
  `global_rank` int NOT NULL,
  `rating` int NOT NULL DEFAULT '1500',
  `max_rating` int NOT NULL DEFAULT '1500',
  `contests_attended` int NOT NULL DEFAULT '0',
  `avatar` varchar(255) DEFAULT NULL,
  `country` varchar(10) DEFAULT NULL,
  `badge` varchar(50) DEFAULT NULL,
  `contests_rated` int NOT NULL DEFAULT '0',
  `last_contest_id` varchar(40) DEFAULT NULL,
  `max_rating_title` enum('NEWBIE','PUPIL','SPECIALIST','EXPERT','CANDIDATE_MASTER','MASTER','INTERNATIONAL_MASTER','GRANDMASTER','INTERNATIONAL_GRANDMASTER','LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
  `rating_title` enum('NEWBIE','PUPIL','SPECIALIST','EXPERT','CANDIDATE_MASTER','MASTER','INTERNATIONAL_MASTER','GRANDMASTER','INTERNATIONAL_GRANDMASTER','LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `global_rankings_user_id_key` (`user_id`),
  KEY `global_rankings_global_rank_idx` (`global_rank`),
  KEY `global_rankings_rating_idx` (`rating`),
  KEY `global_rankings_country_global_rank_idx` (`country`,`global_rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `moderation_actions`;
CREATE TABLE `moderation_actions` (
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
DROP TABLE IF EXISTS `moderation_queue`;
CREATE TABLE `moderation_queue` (
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
DROP TABLE IF EXISTS `notification_preferences`;
CREATE TABLE `notification_preferences` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `communication` tinyint(1) NOT NULL DEFAULT '1',
  `marketing` tinyint(1) NOT NULL DEFAULT '0',
  `security` tinyint(1) NOT NULL DEFAULT '1',
  `system` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `notification_preferences_user_id_key` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST') NOT NULL,
  `category` enum('COMMUNICATION','MARKETING','SECURITY','SYSTEM') NOT NULL,
  `title` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `link` varchar(255) DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `announcement_id` varchar(64) DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `read_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `notifications_user_id_is_read_created_at_idx` (`user_id`,`is_read`,`created_at`),
  KEY `notifications_user_id_type_idx` (`user_id`,`type`),
  KEY `notifications_user_id_category_idx` (`user_id`,`category`),
  KEY `idx_notifications_announcement_id` (`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `password_resets`;
CREATE TABLE `password_resets` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token` varchar(255) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `password_resets_token_key` (`token`),
  KEY `password_resets_token_idx` (`token`),
  KEY `password_resets_user_id_idx` (`user_id`),
  KEY `password_resets_expires_at_idx` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_details`;
CREATE TABLE `problem_details` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `slug` varchar(120) NOT NULL,
  `summary` text NOT NULL,
  `content` text,
  `companies` json DEFAULT NULL,
  `likes` int NOT NULL DEFAULT '0',
  `dislikes` int NOT NULL DEFAULT '0',
  `difficulty_rating` decimal(5,1) NOT NULL DEFAULT '1500.0',
  `updated_at` datetime(3) NOT NULL,
  `follow_up` text,
  `constraints_json` json NOT NULL,
  `hints` json DEFAULT NULL,
  `interactions` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_details_problem_id_key` (`problem_id`),
  KEY `problem_details_likes_idx` (`likes`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_examples`;
CREATE TABLE `problem_examples` (
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
DROP TABLE IF EXISTS `problem_languages`;
CREATE TABLE `problem_languages` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `label` varchar(50) NOT NULL,
  `value` varchar(50) NOT NULL,
  `style` varchar(20) DEFAULT NULL,
  `starter_code` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `problem_languages_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_list_bookmarks`;
CREATE TABLE `problem_list_bookmarks` (
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
DROP TABLE IF EXISTS `problem_list_categories`;
CREATE TABLE `problem_list_categories` (
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
DROP TABLE IF EXISTS `problem_list_problem_relations`;
CREATE TABLE `problem_list_problem_relations` (
  `list_id` varchar(50) NOT NULL,
  `problem_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `added_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`problem_id`,`list_id`),
  KEY `problem_list_problem_relations_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_lists`;
CREATE TABLE `problem_lists` (
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
DROP TABLE IF EXISTS `problem_notes`;
CREATE TABLE `problem_notes` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `content` text NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_notes_user_id_problem_id_key` (`user_id`,`problem_id`),
  KEY `problem_notes_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_tag_relations`;
CREATE TABLE `problem_tag_relations` (
  `problem_id` bigint NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `problem_tag_relations_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problem_tags`;
CREATE TABLE `problem_tags` (
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
DROP TABLE IF EXISTS `problem_versions`;
CREATE TABLE `problem_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `problem_id` bigint NOT NULL,
  `version_number` int NOT NULL,
  `snapshot_json` json NOT NULL COMMENT '完整题目快照',
  `change_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建 | 更新 | 回滚',
  `change_summary` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更摘要',
  `created_by` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_version` (`problem_id`,`version_number`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `problems`;
CREATE TABLE `problems` (
  `id` bigint NOT NULL,
  `slug` varchar(120) NOT NULL,
  `title` varchar(255) NOT NULL,
  `difficulty` enum('Easy','Medium','Hard') NOT NULL,
  `acceptance_rate` decimal(5,2) NOT NULL DEFAULT '0.00',
  `status` enum('solved','attempted','todo') NOT NULL DEFAULT 'todo',
  `is_premium` tinyint(1) NOT NULL DEFAULT '0',
  `has_solution` tinyint(1) NOT NULL DEFAULT '0',
  `completed_time` date DEFAULT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT '1',
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` varchar(40) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `flag_notes` text,
  `flag_reason` text,
  `flag_reported_at` datetime(3) DEFAULT NULL,
  `flag_reported_by` varchar(40) DEFAULT NULL,
  `flag_reviewed_at` datetime(3) DEFAULT NULL,
  `flag_reviewed_by` varchar(40) DEFAULT NULL,
  `flag_status` enum('PENDING','REVIEWED','RESOLVED','DISMISSED') DEFAULT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `refresh_tokens`;
CREATE TABLE `refresh_tokens` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token` varchar(255) NOT NULL,
  `token_hash` varchar(255) DEFAULT NULL COMMENT 'Token哈希值',
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
  KEY `idx_refresh_tokens_token_hash` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `reports`;
CREATE TABLE `reports` (
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
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions` (
  `id` varchar(40) NOT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  `resource` enum('USER','PROBLEM','SUBMISSION','CONTEST','FORUM_POST','FORUM_COMMENT','SOLUTION','SOLUTION_COMMENT','PROBLEM_LIST','ROLE','PERMISSION','NOTIFICATION','ACHIEVEMENT','BILLING','SYSTEM','DASHBOARD','MODERATION','BACKUP','AUDIT_LOG','REPORT','SEARCH','TAG','BOOKMARK','FOLLOW','VOTE','EMAIL','QUEUE','RECOMMENDATION') NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_permissions_role_action_resource_key` (`role`,`action`,`resource`),
  KEY `role_permissions_role_idx` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `solution_comments`;
CREATE TABLE `solution_comments` (
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
DROP TABLE IF EXISTS `solutions`;
CREATE TABLE `solutions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `summary` text,
  `language` varchar(50) NOT NULL,
  `tags` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `likes` int NOT NULL DEFAULT '0',
  `dislikes` int NOT NULL DEFAULT '0',
  `comment_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT '1',
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` varchar(40) DEFAULT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否置顶',
  PRIMARY KEY (`id`),
  KEY `solutions_problem_id_fkey` (`problem_id`),
  KEY `solutions_user_id_fkey` (`user_id`),
  KEY `solutions_problem_id_created_at_idx` (`problem_id`,`created_at`),
  KEY `solutions_user_id_created_at_idx` (`user_id`,`created_at`),
  KEY `solutions_is_flagged_is_deleted_idx` (`is_flagged`,`is_deleted`),
  KEY `solutions_is_published_is_deleted_idx` (`is_published`,`is_deleted`),
  KEY `solutions_likes_idx` (`likes` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `submission_statuses`;
CREATE TABLE `submission_statuses` (
  `key` varchar(40) NOT NULL,
  `code` varchar(10) NOT NULL,
  `label` varchar(60) NOT NULL,
  `description` text,
  `suggestion` text,
  `category` varchar(20) NOT NULL,
  `severity` varchar(20) NOT NULL,
  `is_terminal` tinyint(1) NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`key`),
  KEY `submission_statuses_category_severity_idx` (`category`,`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `submissions`;
CREATE TABLE `submissions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `language` varchar(50) NOT NULL,
  `code` text NOT NULL,
  `status` varchar(40) NOT NULL,
  `runtime` int NOT NULL,
  `memory` double DEFAULT NULL,
  `notes` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `runtime_percentile` double DEFAULT NULL,
  `memory_percentile` double DEFAULT NULL,
  `test_details` json DEFAULT NULL,
  `memoryDistBinsMb` json DEFAULT NULL,
  `runtimeDistBinsMs` json DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `submissions_problem_id_user_id_idx` (`problem_id`,`user_id`),
  KEY `submissions_user_id_fkey` (`user_id`),
  KEY `submissions_created_at_idx` (`created_at`),
  KEY `submissions_user_id_status_created_at_idx` (`user_id`,`status`,`created_at`),
  KEY `submissions_problem_id_user_id_status_runtime_memory_idx` (`problem_id`,`user_id`,`status`,`runtime`,`memory`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `subscriptions`;
CREATE TABLE `subscriptions` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `plan` enum('FREE','PREMIUM_MONTHLY','PREMIUM_YEARLY') NOT NULL DEFAULT 'FREE',
  `status` enum('ACTIVE','CANCELLED','EXPIRED','PENDING') NOT NULL DEFAULT 'ACTIVE',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `transaction_id` varchar(100) DEFAULT NULL COMMENT '支付交易ID',
  `auto_renew` tinyint(1) NOT NULL DEFAULT '1' COMMENT '自动续费标志',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '软删除标志',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间戳',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `subscriptions_user_id_idx` (`user_id`),
  KEY `subscriptions_status_idx` (`status`),
  KEY `subscriptions_is_deleted_idx` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `system_announcement_reads`;
CREATE TABLE `system_announcement_reads` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `announcement_id` varchar(40) NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '1',
  `read_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `system_announcement_reads_user_id_announcement_id_key` (`user_id`,`announcement_id`),
  KEY `system_announcement_reads_announcement_id_fkey` (`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `system_announcements`;
CREATE TABLE `system_announcements` (
  `id` varchar(40) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST') NOT NULL,
  `created_by` varchar(40) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `system_announcements_created_by_fkey` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `system_settings`;
CREATE TABLE `system_settings` (
  `key` varchar(50) NOT NULL,
  `value` text NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `translations`;
CREATE TABLE `translations` (
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
DROP TABLE IF EXISTS `user_achievements`;
CREATE TABLE `user_achievements` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `achievement_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `earned_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_id`),
  KEY `idx_user_achievements_user_id` (`user_id`),
  KEY `idx_user_achievements_achievement_id` (`achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `user_bans`;
CREATE TABLE `user_bans` (
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
DROP TABLE IF EXISTS `user_follows`;
CREATE TABLE `user_follows` (
  `follower_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关注者',
  `following_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被关注者',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`follower_id`,`following_id`),
  KEY `idx_user_follows_follower` (`follower_id`),
  KEY `idx_user_follows_following` (`following_id`),
  KEY `idx_user_follows_created` (`created_at`),
  KEY `idx_user_follows_following_created` (`following_id`,`created_at` DESC),
  KEY `idx_user_follows_follower_created` (`follower_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `user_permissions`;
CREATE TABLE `user_permissions` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  `resource` enum('USER','PROBLEM','CONTEST','SOLUTION','FORUM_POST','FORUM_COMMENT','SYSTEM','PROBLEM_LIST','TAG') NOT NULL,
  `granted_by` varchar(40) NOT NULL,
  `granted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_permissions_user_id_action_resource_key` (`user_id`,`action`,`resource`),
  KEY `user_permissions_user_id_idx` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `user_warnings`;
CREATE TABLE `user_warnings` (
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
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` varchar(40) NOT NULL,
  `username` varchar(120) NOT NULL,
  `name` varchar(120) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `bio` text,
  `company` varchar(255) DEFAULT NULL,
  `github` varchar(255) DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `location` varchar(255) DEFAULT NULL,
  `twitter` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_banned` tinyint(1) NOT NULL DEFAULT '0',
  `banned_until` datetime(3) DEFAULT NULL,
  `banned_reason` text,
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL,
  `updated_by` varchar(40) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `deleted_by` varchar(40) DEFAULT NULL COMMENT '删除人ID',
  `password_reset_token_hash` varchar(255) DEFAULT NULL,
  `password_reset_expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `users_username_key` (`username`),
  KEY `users_role_idx` (`role`),
  KEY `users_is_active_is_banned_idx` (`is_active`,`is_banned`),
  KEY `users_is_active_last_login_at_idx` (`is_active`,`last_login_at`),
  KEY `users_joined_at_idx` (`joined_at`),
  KEY `idx_users_is_deleted` (`is_deleted`),
  KEY `idx_users_password_reset_token` (`password_reset_token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `views`;
CREATE TABLE `views` (
  `id` varchar(40) NOT NULL,
  `target_id` varchar(40) NOT NULL,
  `target_type` enum('SOLUTION','FORUM_POST') NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `viewed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `views_target_type_target_id_user_id_ip_idx` (`target_type`,`target_id`,`user_id`,`ip`),
  KEY `views_user_id_fkey` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `virtual_contest_sessions`;
CREATE TABLE `virtual_contest_sessions` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `status` enum('NOT_STARTED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
  `started_at` datetime(3) DEFAULT NULL,
  `ends_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `total_score` int NOT NULL DEFAULT '0',
  `total_penalty` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `virtual_contest_sessions_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `virtual_contest_sessions_user_id_status_idx` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `email_templates` (
    `id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `body` TEXT NOT NULL,
    `variables` JSON,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `email_logs` (
    `id` VARCHAR(36) NOT NULL,
    `template_id` VARCHAR(36),
    `recipient` VARCHAR(255) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `sent_at` DATETIME,
    `error` TEXT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_email_log_template_id` (`template_id`),
    KEY `idx_email_log_recipient` (`recipient`),
    KEY `idx_email_log_status` (`status`),
    KEY `idx_email_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
