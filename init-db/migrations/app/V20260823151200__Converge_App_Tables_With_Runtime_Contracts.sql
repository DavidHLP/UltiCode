-- V20260823151200__Converge_App_Tables_With_Runtime_Contracts.sql
-- Second-pass repair for App-owner schema drift. The per-owner bootstrap
-- (V20260729140300) authored several tables from a pre-refactor snapshot:
-- forum_users / global_rankings / problem_details / problems / solutions are
-- missing columns the entities and mappers read and write (is_published,
-- flag_*, published_*, problem detail content columns, ...). The runtime
-- cannot operate on the stale shapes at all (any SELECT fails), so this
-- migration rebuilds them from their final contracts. It also adds the
-- integration-inbox consumer_inbox table used by the App service.
-- All five rebuilt tables are write-broken under the stale shapes, so no
-- durable rows can exist; consumer_inbox is created additively.
-- FK checks are suspended only for the drop/rebuild window below.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `consumer_inbox` (
  `id` varchar(40) NOT NULL COMMENT 'Inbox row ID (UUID)',
  `consumer` varchar(40) NOT NULL COMMENT 'Consumer name (e.g., App, Admin, Auth)',
  `event_id` varchar(40) NOT NULL COMMENT 'Event ID from integration_outbox',
  `event_type` varchar(120) NOT NULL,
  `payload` json NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/PROCESSED/DEAD',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `lease_owner` varchar(80) DEFAULT NULL COMMENT 'PID/hostname that holds the lease',
  `lease_expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_consumer_event` (`consumer`,`event_id`),
  KEY `idx_inbox_state_retry` (`state`,`next_retry_at`)
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
  KEY `global_rankings_country_global_rank_idx` (`country`,`global_rank`),
  KEY `idx_global_rankings_user_id_rating` (`user_id`,`rating`),
  KEY `idx_global_rankings_username` (`username`)
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
  `time_limit` int DEFAULT NULL COMMENT 'per-problem time limit in seconds; NULL = global sandbox default',
  `memory_limit` int DEFAULT NULL COMMENT 'per-problem memory limit in MiB; NULL = global sandbox default',
  PRIMARY KEY (`id`),
  KEY `problems_difficulty_idx` (`difficulty`),
  KEY `problems_slug_idx` (`slug`),
  KEY `problems_title_idx` (`title`),
  KEY `problems_is_published_is_deleted_idx` (`is_published`,`is_deleted`),
  KEY `problems_is_flagged_is_deleted_idx` (`is_flagged`,`is_deleted`),
  KEY `problems_created_at_idx` (`created_at`),
  KEY `problems_version_idx` (`version`)
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
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_published` tinyint(1) NOT NULL DEFAULT '1',
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` varchar(40) DEFAULT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `solutions_problem_id_fkey` (`problem_id`),
  KEY `solutions_user_id_fkey` (`user_id`),
  KEY `solutions_problem_id_created_at_idx` (`problem_id`,`created_at`),
  KEY `solutions_user_id_created_at_idx` (`user_id`,`created_at`),
  KEY `solutions_is_flagged_is_deleted_idx` (`is_flagged`,`is_deleted`),
  KEY `solutions_is_published_is_deleted_idx` (`is_published`,`is_deleted`),
  KEY `solutions_likes_idx` (`likes` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
