-- V20260729140300__Create_App_Schema_Tables.sql
-- App Owner Schema DDL (P5-SCHEMA-001)

CREATE TABLE IF NOT EXISTS `problems` (
  `id` bigint NOT NULL,
  `slug` varchar(120) NOT NULL,
  `title` varchar(255) NOT NULL,
  `difficulty` enum('EASY','MEDIUM','HARD') NOT NULL,
  `description` text NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problems_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `problem_details` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `slug` varchar(120) NOT NULL,
  `time_limit_ms` int NOT NULL DEFAULT '1000',
  `memory_limit_mb` int NOT NULL DEFAULT '256',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `contests` (
  `id` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `status` enum('DRAFT','UPCOMING','RUNNING','FINISHED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `start_time` datetime(3) NOT NULL,
  `end_time` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contests_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `submissions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `language` varchar(30) NOT NULL,
  `code` text NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `verdict` varchar(30) DEFAULT NULL,
  `execution_time_ms` int DEFAULT NULL,
  `memory_used_kb` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_submissions_user_id` (`user_id`),
  KEY `idx_submissions_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `solutions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `forum_posts` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST') NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
