--
-- Dumping schema: ulticode
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: ulticode
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `ulticode`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ulticode` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `ulticode`;

--
-- Table structure for table `DailyRecommendation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `achievements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `achievements` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `icon` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题解决、连续性、竞赛、社交、特殊',
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `appeals`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) DEFAULT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) DEFAULT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(64) DEFAULT NULL,
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_state_created` (`state`,`created_at`),
  KEY `idx_state_claimed` (`state`,`claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `backups`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backups` (
  `id` varchar(40) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `size` bigint NOT NULL DEFAULT '0',
  `type` enum('FULL','INCREMENTAL') NOT NULL,
  `status` enum('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL,
  `created_by` varchar(40) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `error` text,
  PRIMARY KEY (`id`),
  KEY `idx_status_created_at` (`status`,`created_at`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collection_items`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collections`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consumer_inbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_inbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_adjudication_receipts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_adjudication_receipts` (
  `id` varchar(40) NOT NULL COMMENT 'Receipt row ID (UUID)',
  `submission_id` varchar(40) NOT NULL,
  `generation` bigint NOT NULL COMMENT 'Monotonic judge generation',
  `verdict` varchar(30) NOT NULL COMMENT 'Terminal submission verdict',
  `is_accepted` tinyint(1) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_contest_adjudication_receipt` (`submission_id`,`generation`),
  CONSTRAINT `fk_contest_adjudication_receipts_submission` FOREIGN KEY (`submission_id`) REFERENCES `contest_submissions` (`submission_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_analytics`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `contest_analytics_contest_id_key` (`contest_id`),
  CONSTRAINT `fk_contest_analytics_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_announcements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_announcements` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`,`created_at`),
  CONSTRAINT `fk_contest_announcements_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_participants`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `virtual_session_id` varchar(64) DEFAULT NULL,
  `checked_in_at` datetime(3) DEFAULT NULL,
  `total_time` int NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_real_active` tinyint GENERATED ALWAYS AS ((case when ((`is_virtual` = 0) and (`status` = _utf8mb4'STARTED')) then 1 else NULL end)) VIRTUAL,
  `active_virtual_key` varchar(128) GENERATED ALWAYS AS ((case when ((`is_virtual` = 1) and (`status` = _utf8mb4'STARTED')) then concat(`contest_id`,_utf8mb4'-',`user_id`) else NULL end)) VIRTUAL,
  `real_registration_key` varchar(81) GENERATED ALWAYS AS ((case when (`is_virtual` = 0) then concat(`contest_id`,_utf8mb4':',`user_id`) else NULL end)) VIRTUAL,
  `virtual_active_key` varchar(81) GENERATED ALWAYS AS ((case when ((`is_virtual` = 1) and (`status` = _utf8mb4'STARTED')) then concat(`contest_id`,_utf8mb4':',`user_id`) else NULL end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_participants_id_contest` (`id`,`contest_id`),
  UNIQUE KEY `contest_participants_contest_id_user_id_virtual_session_id_key` (`contest_id`,`user_id`,`virtual_session_id`),
  UNIQUE KEY `uk_real_active` (`contest_id`,`user_id`,`is_real_active`),
  UNIQUE KEY `uk_virtual_active` (`active_virtual_key`),
  UNIQUE KEY `uk_real_registration` (`real_registration_key`),
  UNIQUE KEY `uk_virtual_active_admission` (`virtual_active_key`),
  KEY `contest_participants_user_id_idx` (`user_id`),
  KEY `contest_participants_contest_id_final_rank_idx` (`contest_id`,`final_rank`),
  KEY `contest_participants_virtual_session_id_fkey` (`virtual_session_id`),
  KEY `contest_participants_user_id_status_is_virtual_idx` (`user_id`,`status`,`is_virtual`),
  CONSTRAINT `fk_contest_participants_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_problem_results`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_problem_results_participant_id_contest_problem_id_key` (`participant_id`,`contest_problem_id`),
  KEY `contest_problem_results_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `contest_problem_results_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_problem_results_ranking_id_fkey` (`ranking_id`),
  KEY `contest_problem_results_user_id_fkey` (`user_id`),
  KEY `fk_contest_problem_results_problem_contest` (`contest_problem_id`,`contest_id`),
  KEY `fk_contest_problem_results_participant_contest` (`participant_id`,`contest_id`),
  KEY `fk_contest_problem_results_ranking_contest` (`ranking_id`,`contest_id`),
  CONSTRAINT `fk_contest_problem_results_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contest_problem_results_participant_contest` FOREIGN KEY (`participant_id`, `contest_id`) REFERENCES `contest_participants` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contest_problem_results_problem_contest` FOREIGN KEY (`contest_problem_id`, `contest_id`) REFERENCES `contest_problems` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contest_problem_results_ranking_contest` FOREIGN KEY (`ranking_id`, `contest_id`) REFERENCES `contest_rankings` (`id`, `contest_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_problems`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_contest_problem_id` (`contest_id`,`problem_id`),
  UNIQUE KEY `uk_contest_problems_id_contest` (`id`,`contest_id`),
  KEY `contest_problems_contest_id_idx` (`contest_id`),
  KEY `contest_problems_problem_id_fkey` (`problem_id`),
  CONSTRAINT `fk_contest_problems_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_rankings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_contest_rankings_id_contest` (`id`,`contest_id`),
  KEY `contest_rankings_contest_id_rank_idx` (`contest_id`,`rank`),
  KEY `contest_rankings_user_id_idx` (`user_id`),
  KEY `contest_rankings_contest_id_is_virtual_rank_idx` (`contest_id`,`is_virtual`,`rank`),
  CONSTRAINT `fk_contest_rankings_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_rating_calculations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_rating_calculations` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `calculated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_rating_calculations_contest_id` (`contest_id`),
  CONSTRAINT `fk_contest_rating_calculations_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_scoring_rules`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_submission_submission_id` (`submission_id`),
  UNIQUE KEY `uk_contest_submissions_submission_id` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_idx` (`contest_id`,`participant_id`),
  KEY `contest_submissions_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_submissions_participant_id_fkey` (`participant_id`),
  KEY `contest_submissions_submission_id_fkey` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_submitted_at_idx` (`contest_id`,`participant_id`,`submitted_at`),
  KEY `idx_contest_submissions_submission_id` (`submission_id`),
  KEY `fk_contest_submissions_problem_contest` (`contest_problem_id`,`contest_id`),
  KEY `fk_contest_submissions_participant_contest` (`participant_id`,`contest_id`),
  CONSTRAINT `fk_contest_submissions_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contest_submissions_participant_contest` FOREIGN KEY (`participant_id`, `contest_id`) REFERENCES `contest_participants` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_contest_submissions_problem_contest` FOREIGN KEY (`contest_problem_id`, `contest_id`) REFERENCES `contest_problems` (`id`, `contest_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contests`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contests` (
  `id` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `contest_type` enum('ICPC','IOI','CUSTOM') NOT NULL,
  `start_time` datetime(3) NOT NULL,
  `duration_minutes` int NOT NULL,
  `status` enum('DRAFT','UPCOMING','RUNNING','FINISHING','FINISHED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `penalty_per_wrong` int NOT NULL DEFAULT '300',
  `scoring_mode` enum('SCORE','ICPC','IOI') NOT NULL DEFAULT 'SCORE',
  `tie_breaker` enum('LAST_SOLVE_TIME','TOTAL_TIME','TOTAL_ATTEMPTS','NONE') NOT NULL DEFAULT 'LAST_SOLVE_TIME',
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
  UNIQUE KEY `uk_contest_slug` (`slug`),
  KEY `contests_status_start_time_idx` (`status`,`start_time`),
  KEY `contests_contest_type_idx` (`contest_type`),
  KEY `contests_status_is_visible_start_time_idx` (`status`,`is_visible`,`start_time`),
  KEY `contests_scoring_rule_id_fkey` (`scoring_rule_id`),
  KEY `idx_contests_created_by` (`created_by`),
  KEY `idx_contests_is_virtual` (`is_virtual`),
  KEY `idx_contests_status_type` (`status`,`contest_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `edge_operations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `edge_operations` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `email_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_logs` (
  `id` varchar(36) NOT NULL,
  `template_id` varchar(36) DEFAULT NULL,
  `recipient` varchar(255) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `sent_at` datetime DEFAULT NULL,
  `error` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_log_template_id` (`template_id`),
  KEY `idx_email_log_recipient` (`recipient`),
  KEY `idx_email_log_status` (`status`),
  KEY `idx_email_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `email_templates`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_templates` (
  `id` varchar(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `variables` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `first_solve_records`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `first_solve_records` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `solved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` int NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`,`problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`),
  KEY `first_solve_records_problem_id_fkey` (`problem_id`),
  CONSTRAINT `fk_first_solve_records_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_communities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_links`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_members`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_rules`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_community_tags` (
  `community_id` varchar(40) NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`community_id`,`tag_id`),
  KEY `forum_community_tags_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_post_tag_relations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_post_tag_relations` (
  `post_id` varchar(40) NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`post_id`,`tag_id`),
  KEY `forum_post_tag_relations_tag_id_idx` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_posts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `forum_posts_community_id_fkey` (`community_id`),
  KEY `forum_posts_user_id_fkey` (`user_id`),
  KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`),
  KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_users` (
  `username` varchar(60) NOT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `karma` int NOT NULL DEFAULT '0',
  `id` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_users_username_key` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `global_rankings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `judge_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `judge_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_actions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_queue`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_command_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_command_receipt` (
  `id` varchar(40) NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id` varchar(40) NOT NULL COMMENT 'RPC command ID',
  `service` varchar(80) NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation` varchar(80) NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key` varchar(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` varchar(64) DEFAULT NULL COMMENT 'SHA-256 digest of request business payload',
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS or PROCESSING',
  `error_code` varchar(80) DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload` json DEFAULT NULL COMMENT 'Serialized result for replay',
  `actor_type` varchar(30) DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id` varchar(40) DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id` varchar(80) DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_command_receipt` (`service`,`operation`,`idempotency_key`),
  KEY `idx_notification_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_delivery_ledger`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_delivery_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `intent_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '"in_app" / "email" / "websocket"',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'recipient — denormalized for ops queries',
  `intent_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'record class simpleName (SubmissionCompletedIntent, ...)',
  `delivery_state` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CLAIMED / DELIVERED / SKIPPED / FAILED',
  `failure_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'truncated error message on FAILED; null otherwise',
  `reclaim_attempts` int NOT NULL DEFAULT '0',
  `delivered_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'row creation = ledger claim time',
  `claimed_at` datetime(3) DEFAULT NULL COMMENT 'Current delivery lease timestamp',
  `claim_owner` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Dispatcher lease owner',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'state transition time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_delivery_ledger_intent_channel` (`intent_id`,`channel_id`),
  KEY `idx_notification_delivery_ledger_user_time` (`user_id`,`delivered_at`),
  KEY `idx_notification_delivery_ledger_state` (`delivery_state`,`delivered_at`),
  KEY `idx_notification_delivery_ledger_claim` (`delivery_state`,`claimed_at`,`claim_owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_preferences`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_preferences` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `communication` tinyint(1) NOT NULL DEFAULT '1',
  `marketing` tinyint(1) NOT NULL DEFAULT '0',
  `security` tinyint(1) NOT NULL DEFAULT '1',
  `system_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `notification_preferences_user_id_key` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST','CONTEST_REMINDER','ACHIEVEMENT') NOT NULL,
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
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `notifications_user_id_is_read_created_at_idx` (`user_id`,`is_read`,`created_at`),
  KEY `notifications_user_id_type_idx` (`user_id`,`type`),
  KEY `notifications_user_id_category_idx` (`user_id`,`category`),
  KEY `idx_notifications_announcement_id` (`announcement_id`),
  KEY `idx_notifications_user_deleted` (`user_id`,`is_deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `oauth_provider_identities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oauth_provider_identities` (
  `id` varchar(40) NOT NULL,
  `account_id` varchar(40) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `provider_user_id` varchar(128) NOT NULL,
  `linked_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `unlinked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_identity` (`provider`,`provider_user_id`),
  KEY `idx_oauth_provider_account` (`account_id`),
  KEY `idx_oauth_provider_unlinked` (`provider`,`unlinked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `owner_contraction_proof`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `owner_contraction_proof` (
  `owner` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_schema` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_schema` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_rows` bigint NOT NULL,
  `target_rows` bigint NOT NULL,
  `snapshot_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `app_account` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `app_dml_grants` int NOT NULL,
  `backup_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `backup_verified_at` datetime(3) NOT NULL,
  `writers_quiesced_at` datetime(3) NOT NULL,
  `verified_at` datetime(3) NOT NULL,
  `verified_by` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`owner`),
  KEY `idx_owner_contraction_proof_verified_at` (`verified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `password_resets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_details`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_examples`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_languages`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_bookmarks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_problem_relations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_list_problem_relations` (
  `list_id` varchar(50) NOT NULL,
  `problem_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `added_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`problem_id`,`list_id`),
  KEY `problem_list_problem_relations_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_lists`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_notes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_notes` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_tag_relations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_tag_relations` (
  `problem_id` bigint NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `problem_tag_relations_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `problem_id` bigint NOT NULL,
  `version_number` int NOT NULL,
  `snapshot_json` json NOT NULL COMMENT '完整题目快照',
  `change_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建 | 更新 | 回滚',
  `change_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更摘要',
  `created_by` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_version` (`problem_id`,`version_number`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problems`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reconciliation_runs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_runs` (
  `run_id` varchar(40) NOT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `owner` varchar(20) NOT NULL COMMENT 'Auth/Admin/App/ALL',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED',
  `divergence_count` int NOT NULL DEFAULT '0',
  `orphan_count` int NOT NULL DEFAULT '0',
  `detail` text COMMENT 'JSON summary of reconciliation results',
  PRIMARY KEY (`run_id`),
  KEY `idx_recon_runs_started_at` (`started_at`),
  KEY `idx_recon_runs_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refresh_tokens`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token_hash` varchar(64) NOT NULL COMMENT 'SHA-256 refresh token hash',
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `rotated_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `is_revoked` tinyint(1) NOT NULL DEFAULT '0',
  `family_id` varchar(40) DEFAULT NULL COMMENT 'Opaque id grouping all rotation-chain siblings from one login. NULL in the EXPAND phase (legacy rows have no known family); backfilled by P2-AUTH-001-G.',
  `replaced_by_token_id` varchar(40) DEFAULT NULL COMMENT 'Id of the sibling row that replaced this one (rotation chain forward link). NULL in the EXPAND phase.',
  `previous_token_id` varchar(40) DEFAULT NULL COMMENT 'Id of the sibling row that preceded this one (rotation chain backward link). NULL in the EXPAND phase.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `refresh_tokens_token_hash_key` (`token_hash`),
  KEY `refresh_tokens_user_id_idx` (`user_id`),
  KEY `refresh_tokens_expires_at_idx` (`expires_at`),
  KEY `idx_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_family` (`family_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reports`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solution_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solution_topics`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solution_topics` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solutions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_result_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_result_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_statuses`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `generation` bigint NOT NULL DEFAULT '1',
  `current_attempt_id` varchar(40) DEFAULT NULL,
  `judging_lease_expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `submissions_problem_id_user_id_idx` (`problem_id`,`user_id`),
  KEY `submissions_user_id_fkey` (`user_id`),
  KEY `submissions_created_at_idx` (`created_at`),
  KEY `submissions_user_id_status_created_at_idx` (`user_id`,`status`,`created_at`),
  KEY `submissions_problem_id_user_id_status_runtime_memory_idx` (`problem_id`,`user_id`,`status`,`runtime`,`memory`),
  KEY `idx_lease_expiry` (`status`,`judging_lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscriptions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_announcement_reads`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_announcements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_settings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `key` varchar(50) NOT NULL,
  `value` text NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_cases`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_cases` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `translations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_achievements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_achievements` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_bans`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_follows`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_follows` (
  `follower_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关注者',
  `following_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被关注者',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`follower_id`,`following_id`),
  KEY `idx_user_follows_follower` (`follower_id`),
  KEY `idx_user_follows_following` (`following_id`),
  KEY `idx_user_follows_created` (`created_at`),
  KEY `idx_user_follows_following_created` (`following_id`,`created_at` DESC),
  KEY `idx_user_follows_follower_created` (`follower_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_profiles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profiles` (
  `account_id` varchar(40) NOT NULL COMMENT 'FK to users.id',
  `name` varchar(120) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `bio` text,
  `company` varchar(255) DEFAULT NULL,
  `github` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `twitter` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_warnings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(40) NOT NULL,
  `username` varchar(120) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
  `authz_version` bigint NOT NULL DEFAULT '0' COMMENT 'Bumped on every role/permission change. App/Admin cache (sub, authzVersion) and invalidate on mismatch. Default 0 = "unknown version, force fresh snapshot".',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `users_username_key` (`username`),
  KEY `users_role_idx` (`role`),
  KEY `users_is_active_is_banned_idx` (`is_active`,`is_banned`),
  KEY `users_is_active_last_login_at_idx` (`is_active`,`last_login_at`),
  KEY `users_joined_at_idx` (`joined_at`),
  KEY `idx_users_is_deleted` (`is_deleted`),
  KEY `idx_users_password_reset_token` (`password_reset_token_hash`),
  KEY `idx_users_authz_version` (`authz_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `views`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `virtual_contest_sessions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_virtual_contest_sessions_id_contest` (`id`,`contest_id`),
  KEY `virtual_contest_sessions_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `virtual_contest_sessions_user_id_status_idx` (`user_id`,`status`),
  CONSTRAINT `fk_virtual_contest_sessions_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'ulticode'
--

--
-- Dumping routines for database 'ulticode'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:15
--
-- Dumping schema: auth
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: auth
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `auth`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `auth` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `auth`;

--
-- Table structure for table `audit_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) NOT NULL DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_outbox_state_retry` (`state`,`next_retry_at`),
  KEY `idx_audit_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_command_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_command_receipt` (
  `id` varchar(40) NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id` varchar(40) NOT NULL COMMENT 'RPC command ID',
  `service` varchar(80) NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation` varchar(80) NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key` varchar(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` varchar(64) DEFAULT NULL COMMENT 'SHA-256 digest of request payload',
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS or FAILED',
  `error_code` varchar(80) DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload` json DEFAULT NULL COMMENT 'Serialized result or error payload for replay',
  `actor_type` varchar(30) DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id` varchar(40) DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id` varchar(80) DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_auth_command_receipt` (`service`,`operation`,`idempotency_key`),
  KEY `idx_auth_cmd_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `oauth_provider_identities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oauth_provider_identities` (
  `id` varchar(40) NOT NULL,
  `account_id` varchar(40) NOT NULL,
  `provider` varchar(50) NOT NULL,
  `provider_user_id` varchar(255) NOT NULL,
  `linked_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `unlinked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_user` (`provider`,`provider_user_id`),
  KEY `idx_oauth_provider_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `password_resets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_resets` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token` varchar(255) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refresh_tokens`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token_hash` varchar(64) NOT NULL COMMENT 'SHA-256 refresh token hash',
  `family_id` varchar(40) DEFAULT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `rotated_at` datetime(3) DEFAULT NULL,
  `is_revoked` tinyint(1) NOT NULL DEFAULT '0',
  `replaced_by_token_id` varchar(40) DEFAULT NULL,
  `previous_token_id` varchar(40) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `refresh_tokens_token_hash_key` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  KEY `idx_refresh_tokens_family` (`family_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `id` varchar(40) NOT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  `resource` enum('PROBLEM','CONTEST','SUBMISSION','SOLUTION','FORUM','USER','SYSTEM','AUDIT','COMMUNITY','ALL') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `search_document_changed_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `search_document_changed_outbox` (
  `id` varchar(40) NOT NULL,
  `owner` varchar(16) NOT NULL DEFAULT 'Auth',
  `aggregate_id` varchar(120) NOT NULL,
  `aggregate_version` bigint NOT NULL DEFAULT '0',
  `event_type` varchar(64) NOT NULL DEFAULT 'SearchDocumentChanged',
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` json NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_state_retry` (`state`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_permissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(40) NOT NULL,
  `username` varchar(120) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_banned` tinyint(1) NOT NULL DEFAULT '0',
  `banned_until` datetime(3) DEFAULT NULL,
  `banned_reason` text,
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL,
  `updated_by` varchar(40) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `password_reset_token_hash` varchar(255) DEFAULT NULL,
  `password_reset_expires_at` datetime(3) DEFAULT NULL,
  `authz_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'auth'
--

--
-- Dumping routines for database 'auth'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:15
--
-- Dumping schema: admin
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: admin
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `admin`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `admin` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `admin`;

--
-- Table structure for table `audit_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) DEFAULT NULL,
  `entity_id` varchar(64) DEFAULT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `resource_type` varchar(60) DEFAULT NULL,
  `resource_id` varchar(60) DEFAULT NULL,
  `details` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) DEFAULT NULL,
  `entity_id` varchar(64) DEFAULT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `resource_type` varchar(60) DEFAULT NULL,
  `resource_id` varchar(60) DEFAULT NULL,
  `details` text,
  `status` enum('PENDING','PROCESSED','FAILED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(64) DEFAULT NULL,
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_state_claimed` (`state`,`claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consumer_inbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_inbox` (
  `id` varchar(40) NOT NULL,
  `consumer` varchar(40) NOT NULL,
  `event_id` varchar(40) NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `payload` json NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `lease_owner` varchar(80) DEFAULT NULL,
  `lease_expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_consumer_event` (`consumer`,`event_id`),
  KEY `idx_inbox_state_retry` (`state`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_actions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moderation_actions` (
  `id` varchar(40) NOT NULL,
  `queue_id` varchar(40) NOT NULL,
  `action` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED','APPEAL_REJECTED') NOT NULL,
  `moderator_id` varchar(40) NOT NULL,
  `reason` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_queue`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moderation_queue` (
  `id` varchar(40) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reconciliation_runs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_runs` (
  `run_id` varchar(40) NOT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `owner` varchar(20) NOT NULL COMMENT 'Auth/Admin/App/ALL',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED',
  `divergence_count` int NOT NULL DEFAULT '0',
  `orphan_count` int NOT NULL DEFAULT '0',
  `detail` text COMMENT 'JSON summary of reconciliation results',
  PRIMARY KEY (`run_id`),
  KEY `idx_recon_runs_started_at` (`started_at`),
  KEY `idx_recon_runs_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_settings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `key` varchar(50) NOT NULL,
  `value` text NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_warnings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_warnings` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `queue_id` varchar(40) DEFAULT NULL,
  `reason` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'admin'
--

--
-- Dumping routines for database 'admin'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:15
--
-- Dumping schema: app
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: app
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `app`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `app` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `app`;

--
-- Table structure for table `achievements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `achievements` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_command_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_command_receipt` (
  `id` varchar(40) NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id` varchar(40) NOT NULL COMMENT 'RPC command ID',
  `service` varchar(80) NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation` varchar(80) NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key` varchar(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` varchar(64) DEFAULT NULL COMMENT 'SHA-256 digest of request business payload',
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS or FAILED',
  `error_code` varchar(80) DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload` json DEFAULT NULL COMMENT 'Serialized result for replay',
  `actor_type` varchar(30) DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id` varchar(40) DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id` varchar(80) DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_app_command_receipt` (`service`,`operation`,`idempotency_key`),
  KEY `idx_app_cmd_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `appeals`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) NOT NULL DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_outbox_state_retry` (`state`,`next_retry_at`),
  KEY `idx_audit_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collection_items`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collections`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consumer_inbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_inbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_adjudication_receipts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_adjudication_receipts` (
  `id` varchar(40) NOT NULL,
  `submission_id` varchar(40) NOT NULL,
  `generation` bigint NOT NULL,
  `verdict` varchar(30) NOT NULL,
  `is_accepted` tinyint(1) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_contest_adjudication_receipt` (`submission_id`,`generation`),
  CONSTRAINT `fk_app_contest_adjudication_receipts_submission` FOREIGN KEY (`submission_id`) REFERENCES `contest_submissions` (`submission_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_analytics`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `contest_analytics_contest_id_key` (`contest_id`),
  CONSTRAINT `fk_app_contest_analytics_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_announcements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_announcements` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`,`created_at`),
  CONSTRAINT `fk_app_contest_announcements_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_participants`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `virtual_session_id` varchar(64) DEFAULT NULL,
  `checked_in_at` datetime(3) DEFAULT NULL,
  `total_time` int NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_real_active` tinyint GENERATED ALWAYS AS ((case when ((`is_virtual` = 0) and (`status` = _utf8mb4'STARTED')) then 1 else NULL end)) VIRTUAL,
  `active_virtual_key` varchar(128) GENERATED ALWAYS AS ((case when ((`is_virtual` = 1) and (`status` = _utf8mb4'STARTED')) then concat(`contest_id`,_utf8mb4'-',`user_id`) else NULL end)) VIRTUAL,
  `real_registration_key` varchar(81) GENERATED ALWAYS AS ((case when (`is_virtual` = 0) then concat(`contest_id`,_utf8mb4':',`user_id`) else NULL end)) VIRTUAL,
  `virtual_active_key` varchar(81) GENERATED ALWAYS AS ((case when ((`is_virtual` = 1) and (`status` = _utf8mb4'STARTED')) then concat(`contest_id`,_utf8mb4':',`user_id`) else NULL end)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_contest_participants_id_contest` (`id`,`contest_id`),
  UNIQUE KEY `contest_participants_contest_id_user_id_virtual_session_id_key` (`contest_id`,`user_id`,`virtual_session_id`),
  UNIQUE KEY `uk_real_active` (`contest_id`,`user_id`,`is_real_active`),
  UNIQUE KEY `uk_virtual_active` (`active_virtual_key`),
  UNIQUE KEY `uk_real_registration` (`real_registration_key`),
  UNIQUE KEY `uk_virtual_active_admission` (`virtual_active_key`),
  KEY `contest_participants_user_id_idx` (`user_id`),
  KEY `contest_participants_contest_id_final_rank_idx` (`contest_id`,`final_rank`),
  KEY `contest_participants_virtual_session_id_fkey` (`virtual_session_id`),
  KEY `contest_participants_user_id_status_is_virtual_idx` (`user_id`,`status`,`is_virtual`),
  CONSTRAINT `fk_app_contest_participants_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_problem_results`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_problem_results_participant_id_contest_problem_id_key` (`participant_id`,`contest_problem_id`),
  KEY `contest_problem_results_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `contest_problem_results_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_problem_results_ranking_id_fkey` (`ranking_id`),
  KEY `contest_problem_results_user_id_fkey` (`user_id`),
  KEY `fk_app_contest_problem_results_problem_contest` (`contest_problem_id`,`contest_id`),
  KEY `fk_app_contest_problem_results_participant_contest` (`participant_id`,`contest_id`),
  KEY `fk_app_contest_problem_results_ranking_contest` (`ranking_id`,`contest_id`),
  CONSTRAINT `fk_app_contest_problem_results_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_app_contest_problem_results_participant_contest` FOREIGN KEY (`participant_id`, `contest_id`) REFERENCES `contest_participants` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_app_contest_problem_results_problem_contest` FOREIGN KEY (`contest_problem_id`, `contest_id`) REFERENCES `contest_problems` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_app_contest_problem_results_ranking_contest` FOREIGN KEY (`ranking_id`, `contest_id`) REFERENCES `contest_rankings` (`id`, `contest_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_problems`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_contest_problem_id` (`contest_id`,`problem_id`),
  UNIQUE KEY `uk_app_contest_problems_id_contest` (`id`,`contest_id`),
  KEY `contest_problems_contest_id_idx` (`contest_id`),
  KEY `contest_problems_problem_id_fkey` (`problem_id`),
  CONSTRAINT `fk_app_contest_problems_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_rankings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_app_contest_rankings_id_contest` (`id`,`contest_id`),
  KEY `contest_rankings_contest_id_rank_idx` (`contest_id`,`rank`),
  KEY `contest_rankings_user_id_idx` (`user_id`),
  KEY `contest_rankings_contest_id_is_virtual_rank_idx` (`contest_id`,`is_virtual`,`rank`),
  CONSTRAINT `fk_app_contest_rankings_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_rating_calculations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_rating_calculations` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `calculated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_rating_calculations_contest_id` (`contest_id`),
  CONSTRAINT `fk_app_contest_rating_calculations_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_scoring_rules`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contest_submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_contest_submissions_submission_id` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_idx` (`contest_id`,`participant_id`),
  KEY `contest_submissions_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_submissions_participant_id_fkey` (`participant_id`),
  KEY `contest_submissions_submission_id_fkey` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_submitted_at_idx` (`contest_id`,`participant_id`,`submitted_at`),
  KEY `fk_app_contest_submissions_problem_contest` (`contest_problem_id`,`contest_id`),
  KEY `fk_app_contest_submissions_participant_contest` (`participant_id`,`contest_id`),
  CONSTRAINT `fk_app_contest_submissions_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_app_contest_submissions_participant_contest` FOREIGN KEY (`participant_id`, `contest_id`) REFERENCES `contest_participants` (`id`, `contest_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_app_contest_submissions_problem_contest` FOREIGN KEY (`contest_problem_id`, `contest_id`) REFERENCES `contest_problems` (`id`, `contest_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `contests`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contests` (
  `id` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `status` enum('DRAFT','UPCOMING','RUNNING','FINISHING','FINISHED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `start_time` datetime(3) NOT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `contest_type` enum('ICPC','IOI','CUSTOM') NOT NULL DEFAULT 'CUSTOM',
  `duration_minutes` int NOT NULL DEFAULT '0',
  `actual_start_time` datetime(3) DEFAULT NULL,
  `actual_end_time` datetime(3) DEFAULT NULL,
  `registration_start` datetime(3) DEFAULT NULL,
  `registration_end` datetime(3) DEFAULT NULL,
  `freeze_time` datetime(3) DEFAULT NULL,
  `penalty_per_wrong` int NOT NULL DEFAULT '300',
  `scoring_mode` enum('SCORE','ICPC','IOI') NOT NULL DEFAULT 'SCORE',
  `tie_breaker` enum('LAST_SOLVE_TIME','TOTAL_TIME','TOTAL_ATTEMPTS','NONE') NOT NULL DEFAULT 'LAST_SOLVE_TIME',
  `registered_count` int NOT NULL DEFAULT '0',
  `participant_count` int NOT NULL DEFAULT '0',
  `is_rated` tinyint(1) NOT NULL DEFAULT '1',
  `description` text,
  `cover_image` varchar(255) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL,
  `is_visible` tinyint(1) NOT NULL DEFAULT '1',
  `rules` text,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `max_participants` int DEFAULT NULL,
  `scoring_rule_id` varchar(40) DEFAULT NULL,
  `submission_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contests_slug` (`slug`),
  KEY `contests_status_start_time_idx` (`status`,`start_time`),
  KEY `contests_contest_type_idx` (`contest_type`),
  KEY `contests_status_is_visible_start_time_idx` (`status`,`is_visible`,`start_time`),
  KEY `contests_scoring_rule_id_fkey` (`scoring_rule_id`),
  KEY `idx_contests_created_by` (`created_by`),
  KEY `idx_contests_is_virtual` (`is_virtual`),
  KEY `idx_contests_status_type` (`status`,`contest_type`),
  CONSTRAINT `fk_app_contests_scoring_rule` FOREIGN KEY (`scoring_rule_id`) REFERENCES `contest_scoring_rules` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `edge_operations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `edge_operations` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `first_solve_records`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `first_solve_records` (
  `id` varchar(40) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `solved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` int NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`,`problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`),
  KEY `first_solve_records_problem_id_fkey` (`problem_id`),
  CONSTRAINT `fk_app_first_solve_records_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_communities`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_community_members`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_posts`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_posts` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `permalink` varchar(255) DEFAULT NULL,
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
  `stats` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`),
  KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forum_users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_users` (
  `username` varchar(60) NOT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `karma` int NOT NULL DEFAULT '0',
  `id` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `forum_users_username_key` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `global_rankings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `integration_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `integration_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `judge_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `judge_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_actions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moderation_queue`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_details`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_examples`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_languages`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_bookmarks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_categories`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_list_problem_relations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_list_problem_relations` (
  `list_id` varchar(50) NOT NULL,
  `problem_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `added_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`problem_id`,`list_id`),
  KEY `problem_list_problem_relations_problem_id_fkey` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_lists`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_notes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_notes` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_tag_relations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_tag_relations` (
  `problem_id` bigint NOT NULL,
  `tag_id` varchar(40) NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `problem_tag_relations_tag_id_fkey` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_tags`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problem_versions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_versions` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `problems`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reports`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solution_comments`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solution_topics`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solution_topics` (
  `id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_solution_topics_slug` (`slug`),
  KEY `idx_solution_topics_active_deleted_sort` (`is_active`,`is_deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solutions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_result_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_result_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submissions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `language` varchar(30) NOT NULL,
  `code` text NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `generation` bigint NOT NULL DEFAULT '1',
  `current_attempt_id` varchar(40) DEFAULT NULL,
  `judging_lease_expires_at` datetime(3) DEFAULT NULL,
  `runtime` int NOT NULL DEFAULT '0',
  `memory` double DEFAULT NULL,
  `notes` text,
  `runtime_percentile` double DEFAULT NULL,
  `memory_percentile` double DEFAULT NULL,
  `test_details` json DEFAULT NULL,
  `memoryDistBinsMb` json DEFAULT NULL,
  `runtimeDistBinsMs` json DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_submissions_user_id` (`user_id`),
  KEY `idx_submissions_problem_id` (`problem_id`),
  KEY `idx_lease_expiry` (`status`,`judging_lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscriptions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscriptions` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `test_cases`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_cases` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `translations`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_achievements`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_achievements` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_bans`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_follows`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_follows` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_profiles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profiles` (
  `account_id` varchar(40) NOT NULL COMMENT 'FK to auth.users.id',
  `name` varchar(120) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `bio` text,
  `company` varchar(255) DEFAULT NULL,
  `github` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `twitter` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_warnings`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `virtual_contest_sessions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  UNIQUE KEY `uk_app_virtual_contest_sessions_id_contest` (`id`,`contest_id`),
  KEY `virtual_contest_sessions_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `virtual_contest_sessions_user_id_status_idx` (`user_id`,`status`),
  CONSTRAINT `fk_app_virtual_contest_sessions_contest` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'app'
--

--
-- Dumping routines for database 'app'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:15
--
-- Dumping schema: notification
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: notification
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `notification`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `notification` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `notification`;

--
-- Table structure for table `consumer_inbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_inbox` (
  `id` varchar(40) NOT NULL,
  `consumer` varchar(40) NOT NULL,
  `event_id` varchar(40) NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `payload` json NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `lease_owner` varchar(80) DEFAULT NULL,
  `lease_expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_inbox_consumer_event` (`consumer`,`event_id`),
  KEY `idx_notification_inbox_state_retry` (`state`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `email_logs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_logs` (
  `id` varchar(36) NOT NULL,
  `template_id` varchar(36) DEFAULT NULL,
  `recipient` varchar(255) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `sent_at` datetime DEFAULT NULL,
  `error` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_log_template` (`template_id`),
  KEY `idx_email_log_recipient` (`recipient`),
  KEY `idx_email_log_status` (`status`),
  KEY `idx_email_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `email_templates`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_templates` (
  `id` varchar(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `variables` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_command_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_command_receipt` (
  `id` varchar(40) NOT NULL,
  `command_id` varchar(40) NOT NULL,
  `service` varchar(80) NOT NULL,
  `operation` varchar(80) NOT NULL,
  `idempotency_key` varchar(120) NOT NULL,
  `request_fingerprint` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `result_payload` json DEFAULT NULL,
  `actor_type` varchar(30) DEFAULT NULL,
  `actor_id` varchar(40) DEFAULT NULL,
  `trace_id` varchar(80) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_command_receipt` (`service`,`operation`,`idempotency_key`),
  KEY `idx_notification_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_delivery_ledger`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_delivery_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `intent_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `intent_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivery_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reclaim_attempts` int NOT NULL DEFAULT '0',
  `delivered_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_ledger_intent_channel` (`intent_id`,`channel_id`),
  KEY `idx_notification_ledger_user_time` (`user_id`,`delivered_at`),
  KEY `idx_notification_ledger_state` (`delivery_state`,`delivered_at`),
  KEY `idx_notification_ledger_claim` (`delivery_state`,`claimed_at`,`claim_owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_preferences`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_preferences` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `communication` tinyint(1) NOT NULL DEFAULT '1',
  `marketing` tinyint(1) NOT NULL DEFAULT '0',
  `security` tinyint(1) NOT NULL DEFAULT '1',
  `system_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_preferences_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `type` enum('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM','SUBMISSION','CONTEST','CONTEST_REMINDER','ACHIEVEMENT') NOT NULL,
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
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_read_created` (`user_id`,`is_read`,`created_at`),
  KEY `idx_notifications_user_type` (`user_id`,`type`),
  KEY `idx_notifications_user_category` (`user_id`,`category`),
  KEY `idx_notifications_announcement` (`announcement_id`),
  KEY `idx_notifications_user_deleted` (`user_id`,`is_deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'notification'
--

--
-- Dumping routines for database 'notification'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:16
--
-- Dumping schema: submission
--
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: submission
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `submission`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `submission` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `submission`;

--
-- Table structure for table `judge_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `judge_outbox` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_command_receipt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_command_receipt` (
  `id` varchar(40) NOT NULL,
  `command_id` varchar(40) NOT NULL,
  `service` varchar(80) NOT NULL,
  `operation` varchar(80) NOT NULL,
  `idempotency_key` varchar(120) NOT NULL,
  `request_fingerprint` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL COMMENT 'PROCESSING or SUCCESS',
  `result_payload` json DEFAULT NULL,
  `actor_type` varchar(30) NOT NULL,
  `actor_id` varchar(40) NOT NULL,
  `trace_id` varchar(80) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_command_receipt` (`service`,`operation`,`idempotency_key`),
  KEY `idx_submission_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_created_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_created_outbox` (
  `id` varchar(40) NOT NULL,
  `submission_id` varchar(40) NOT NULL,
  `generation` bigint NOT NULL DEFAULT '1',
  `user_id` varchar(40) NOT NULL,
  `problem_id` varchar(120) NOT NULL,
  `contest_id` varchar(40) NOT NULL,
  `virtual_session_id` varchar(40) DEFAULT NULL,
  `language` varchar(50) NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_created_sub_gen` (`submission_id`,`generation`),
  KEY `idx_created_state_retry` (`state`,`next_retry_at`),
  KEY `idx_created_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission_result_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_result_outbox` (
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
  UNIQUE KEY `uniq_result_sub_gen` (`submission_id`,`generation`),
  KEY `idx_result_state_retry` (`state`,`next_retry_at`),
  KEY `idx_submission_result_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submissions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `generation` bigint NOT NULL DEFAULT '1',
  `current_attempt_id` varchar(40) DEFAULT NULL,
  `judging_lease_expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `submissions_problem_id_user_id_idx` (`problem_id`,`user_id`),
  KEY `submissions_user_id_fkey` (`user_id`),
  KEY `submissions_created_at_idx` (`created_at`),
  KEY `submissions_user_id_status_created_at_idx` (`user_id`,`status`,`created_at`),
  KEY `submissions_problem_id_user_id_status_runtime_memory_idx` (`problem_id`,`user_id`,`status`,`runtime`,`memory`),
  KEY `idx_lease_expiry` (`status`,`judging_lease_expires_at`),
  KEY `submissions_problem_id_user_created_idx` (`problem_id`,`user_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'submission'
--

--
-- Dumping routines for database 'submission'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-30 19:34:16
