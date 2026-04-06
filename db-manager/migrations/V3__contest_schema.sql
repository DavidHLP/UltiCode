SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V3__contest_schema
-- Generated from ulticode.sql
-- Tables: 11

CREATE TABLE `contest_analytics` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
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
  CONSTRAINT `contest_analytics_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contest_announcements` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`,`created_at`),
  CONSTRAINT `contest_announcements_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contest_participants` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('REGISTERED','STARTED','FINISHED','DISQUALIFIED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REGISTERED',
  `registered_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `final_rank` int DEFAULT NULL,
  `total_penalty` int NOT NULL DEFAULT '0',
  `total_score` int NOT NULL DEFAULT '0',
  `total_attempts` int NOT NULL DEFAULT '0',
  `last_solve_time` int DEFAULT NULL,
  `virtual_session_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `checked_in_at` datetime(3) DEFAULT NULL,
  `total_time` int NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_participants_contest_id_user_id_virtual_session_id_key` (`contest_id`,`user_id`,`virtual_session_id`),
  KEY `contest_participants_user_id_idx` (`user_id`),
  KEY `contest_participants_contest_id_final_rank_idx` (`contest_id`,`final_rank`),
  KEY `contest_participants_virtual_session_id_fkey` (`virtual_session_id`),
  KEY `contest_participants_user_id_status_is_virtual_idx` (`user_id`,`status`,`is_virtual`),
  CONSTRAINT `contest_participants_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_participants_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_participants_virtual_session_id_fkey` FOREIGN KEY (`virtual_session_id`) REFERENCES `virtual_contest_sessions` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE `contest_problem_results` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_problem_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `participant_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ranking_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_solved` tinyint(1) NOT NULL DEFAULT '0',
  `score` int NOT NULL DEFAULT '0',
  `attempts` int NOT NULL DEFAULT '0',
  `first_solve_time` int DEFAULT NULL,
  `penalty_time` int NOT NULL DEFAULT '0',
  `best_submission_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `time_spent` int NOT NULL DEFAULT '0',
  `time_bonus` int NOT NULL DEFAULT '0',
  `is_first_solve` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_problem_results_participant_id_contest_problem_id_key` (`participant_id`,`contest_problem_id`),
  KEY `contest_problem_results_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `contest_problem_results_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_problem_results_ranking_id_fkey` (`ranking_id`),
  KEY `contest_problem_results_user_id_fkey` (`user_id`),
  CONSTRAINT `contest_problem_results_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_problem_results_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_problem_results_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_problem_results_ranking_id_fkey` FOREIGN KEY (`ranking_id`) REFERENCES `contest_rankings` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `contest_problem_results_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contest_problems` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `problem_index` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score` int NOT NULL DEFAULT '0',
  `penalty_per_wrong` int DEFAULT NULL,
  `solved_count` int NOT NULL DEFAULT '0',
  `submission_count` int NOT NULL DEFAULT '0',
  `label` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `base_score` int DEFAULT NULL,
  `time_bonus` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `contest_problems_contest_id_idx` (`contest_id`),
  KEY `contest_problems_problem_id_fkey` (`problem_id`),
  CONSTRAINT `contest_problems_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_problems_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contest_rankings` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
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
  KEY `contest_rankings_contest_id_is_virtual_rank_idx` (`contest_id`,`is_virtual`,`rank`),
  CONSTRAINT `contest_rankings_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contest_scoring_rules` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
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
);

CREATE TABLE `contest_submissions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `submission_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_problem_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `participant_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `virtual_session_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_from_start` int NOT NULL,
  `is_accepted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contest_submissions_contest_id_participant_id_idx` (`contest_id`,`participant_id`),
  KEY `contest_submissions_contest_problem_id_idx` (`contest_problem_id`),
  KEY `contest_submissions_participant_id_fkey` (`participant_id`),
  KEY `contest_submissions_submission_id_fkey` (`submission_id`),
  KEY `contest_submissions_contest_id_participant_id_submitted_at_idx` (`contest_id`,`participant_id`,`submitted_at`),
  CONSTRAINT `contest_submissions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_submissions_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_submissions_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `contest_submissions_submission_id_fkey` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `contests` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_type` enum('weekly','biweekly','special') COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_time` datetime(3) NOT NULL,
  `duration_minutes` int NOT NULL,
  `status` enum('upcoming','running','finished') COLLATE utf8mb4_unicode_ci NOT NULL,
  `penalty_per_wrong` int NOT NULL DEFAULT '300',
  `scoring_mode` enum('SCORE','ICPC') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCORE',
  `tie_breaker` enum('LAST_SOLVE_TIME','TOTAL_ATTEMPTS','NONE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LAST_SOLVE_TIME',
  `registered_count` int NOT NULL DEFAULT '0',
  `participant_count` int NOT NULL DEFAULT '0',
  `is_rated` tinyint(1) NOT NULL DEFAULT '1',
  `description` text COLLATE utf8mb4_unicode_ci,
  `cover_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_visible` tinyint(1) NOT NULL DEFAULT '1',
  `rules` text COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(3) NOT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `registration_start` datetime(3) DEFAULT NULL,
  `registration_end` datetime(3) DEFAULT NULL,
  `freeze_time` datetime(3) DEFAULT NULL,
  `is_virtual` tinyint(1) NOT NULL DEFAULT '0',
  `max_participants` int DEFAULT NULL,
  `scoring_rule_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `submission_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `contests_status_start_time_idx` (`status`,`start_time`),
  KEY `contests_contest_type_idx` (`contest_type`),
  KEY `contests_slug_idx` (`slug`),
  KEY `contests_status_is_visible_start_time_idx` (`status`,`is_visible`,`start_time`),
  KEY `contests_scoring_rule_id_fkey` (`scoring_rule_id`),
  CONSTRAINT `contests_scoring_rule_id_fkey` FOREIGN KEY (`scoring_rule_id`) REFERENCES `contest_scoring_rules` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE `global_rankings` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `global_rank` int NOT NULL,
  `rating` int NOT NULL DEFAULT '1500',
  `max_rating` int NOT NULL DEFAULT '1500',
  `contests_attended` int NOT NULL DEFAULT '0',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `country` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `badge` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contests_rated` int NOT NULL DEFAULT '0',
  `last_contest_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_rating_title` enum('NEWBIE','PUPIL','SPECIALIST','EXPERT','CANDIDATE_MASTER','MASTER','INTERNATIONAL_MASTER','GRANDMASTER','INTERNATIONAL_GRANDMASTER','LEGENDARY_GRANDMASTER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEWBIE',
  `rating_title` enum('NEWBIE','PUPIL','SPECIALIST','EXPERT','CANDIDATE_MASTER','MASTER','INTERNATIONAL_MASTER','GRANDMASTER','INTERNATIONAL_GRANDMASTER','LEGENDARY_GRANDMASTER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEWBIE',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `global_rankings_user_id_key` (`user_id`),
  KEY `global_rankings_global_rank_idx` (`global_rank`),
  KEY `global_rankings_rating_idx` (`rating`),
  KEY `global_rankings_country_global_rank_idx` (`country`,`global_rank`),
  CONSTRAINT `global_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `virtual_contest_sessions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('NOT_STARTED','IN_PROGRESS','COMPLETED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED',
  `started_at` datetime(3) DEFAULT NULL,
  `ends_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `total_score` int NOT NULL DEFAULT '0',
  `total_penalty` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `virtual_contest_sessions_contest_id_user_id_idx` (`contest_id`,`user_id`),
  KEY `virtual_contest_sessions_user_id_status_idx` (`user_id`,`status`),
  CONSTRAINT `virtual_contest_sessions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `virtual_contest_sessions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);



-- Seed Data

-- Table: contests (3 rows)
INSERT INTO `contests` (`id`, `title`, `slug`, `contest_type`, `start_time`, `duration_minutes`, `status`, `penalty_per_wrong`, `scoring_mode`, `tie_breaker`, `registered_count`, `participant_count`, `is_rated`, `description`, `cover_image`, `created_at`, `created_by`, `is_visible`, `rules`, `updated_at`, `is_deleted`, `deleted_at`, `deleted_by`, `end_time`, `registration_start`, `registration_end`, `freeze_time`, `is_virtual`, `max_participants`, `scoring_rule_id`, `submission_count`) VALUES ('contest-biweekly-170','双周赛 170','biweekly-contest-170','biweekly','2025-11-22 14:30:00.000',90,'finished',300,'SCORE','LAST_SOLVE_TIME',2876,2654,1,'难度递增的双周赛。','https://assets.leetcode.cn/aliyun-lc-upload/contest-config/contest/bc_card_img.png',NOW(3),NULL,1,NULL,NOW(3),0,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,0);
INSERT INTO `contests` (`id`, `title`, `slug`, `contest_type`, `start_time`, `duration_minutes`, `status`, `penalty_per_wrong`, `scoring_mode`, `tie_breaker`, `registered_count`, `participant_count`, `is_rated`, `description`, `cover_image`, `created_at`, `created_by`, `is_visible`, `rules`, `updated_at`, `is_deleted`, `deleted_at`, `deleted_by`, `end_time`, `registration_start`, `registration_end`, `freeze_time`, `is_virtual`, `max_participants`, `scoring_rule_id`, `submission_count`) VALUES ('contest-weekly-476','周赛 476','weekly-contest-476','weekly','2025-11-16 02:30:00.000',90,'finished',300,'SCORE','LAST_SOLVE_TIME',3120,2744,1,'往期周赛存档。','https://assets.leetcode.cn/aliyun-lc-upload/contest-config/contest/wc_card_img.png',NOW(3),NULL,1,NULL,NOW(3),0,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,0);
INSERT INTO `contests` (`id`, `title`, `slug`, `contest_type`, `start_time`, `duration_minutes`, `status`, `penalty_per_wrong`, `scoring_mode`, `tie_breaker`, `registered_count`, `participant_count`, `is_rated`, `description`, `cover_image`, `created_at`, `created_by`, `is_visible`, `rules`, `updated_at`, `is_deleted`, `deleted_at`, `deleted_by`, `end_time`, `registration_start`, `registration_end`, `freeze_time`, `is_virtual`, `max_participants`, `scoring_rule_id`, `submission_count`) VALUES ('contest-weekly-477','周赛 477','weekly-contest-477','weekly','2025-11-23 02:30:00.000',90,'upcoming',300,'SCORE','LAST_SOLVE_TIME',3245,0,1,'参加本次周赛，检验你的算法实力。','https://assets.leetcode.cn/aliyun-lc-upload/contest-config/contest/wc_card_img.png',NOW(3),NULL,1,NULL,NOW(3),0,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,0);

-- Table: contest_participants (25 rows)
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-1','contest-biweekly-170','user-jiangly','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,1,3000,18,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-2','contest-biweekly-170','user-ecnerwala','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,2,3300,18,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-3','contest-biweekly-170','user-tourist','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,3,3600,18,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-4','contest-biweekly-170','user-benq','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,4,3500,15,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-5','contest-biweekly-170','user-um_nik','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,5,4200,15,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-6','contest-biweekly-170','user-scott','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,6,3900,12,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-7','contest-biweekly-170','user-yuki','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,7,4100,12,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-8','contest-biweekly-170','user-alex','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,8,2600,7,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-1','contest-weekly-476','user-tourist','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,1,2700,18,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-10','contest-weekly-476','user-chen','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,10,2800,7,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-2','contest-weekly-476','user-jiangly','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,2,3000,18,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-3','contest-weekly-476','user-benq','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,3,4000,15,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-4','contest-weekly-476','user-ecnerwala','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,4,4200,15,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-5','contest-weekly-476','user-um_nik','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,5,4500,15,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-6','contest-weekly-476','user-petr','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,6,3800,12,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-7','contest-weekly-476','user-scott','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,7,4100,12,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-8','contest-weekly-476','user-yuki','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,8,4300,12,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-476-9','contest-weekly-476','user-alex','FINISHED',NOW(3),'2025-11-16 02:30:00.000',NOW(3),0,9,2500,7,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-1','contest-weekly-477','user-tourist','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-2','contest-weekly-477','user-jiangly','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-3','contest-weekly-477','user-yuki','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-4','contest-weekly-477','user-benq','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-5','contest-weekly-477','user-ecnerwala','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-6','contest-weekly-477','user-alex','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-477-7','contest-weekly-477','user-chen','REGISTERED',NOW(3),NULL,NULL,0,NULL,0,0,0,NULL,NULL,NULL,0,0);

-- Table: contest_problem_results (20 rows)
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('0b0cb3b1-8c49-47f4-bd7c-e16b42f8e904','contest-biweekly-170','cp-170-4','user-ecnerwala','cp-p-170-2','cr-170-2',1,6,1,2550,2550,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('178a3780-fdac-40bb-9bc3-0982e6219f35','contest-weekly-476','cp-476-3','user-tourist','cp-p-476-1','cr-476-1',1,5,1,1320,1320,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('17d82993-92c9-4ec9-9739-36a928950c7b','contest-biweekly-170','cp-170-1','user-ecnerwala','cp-p-170-2','cr-170-2',1,3,1,210,210,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('1c8dc000-e1f3-440b-834d-fa5b4691a022','contest-weekly-476','cp-476-4','user-jiangly','cp-p-476-2','cr-476-2',1,6,1,2400,2400,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('24a64822-264a-4450-9b05-ffed3b075e1e','contest-weekly-476','cp-476-3','user-jiangly','cp-p-476-2','cr-476-2',1,5,1,1440,1440,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('38f17ddb-89b4-43d9-a9e6-65934bc24fb0','contest-biweekly-170','cp-170-1','user-jiangly','cp-p-170-1','cr-170-1',1,3,1,150,150,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('484d7305-1ef3-4922-b31e-7af3a12682ff','contest-weekly-476','cp-476-3','user-benq','cp-p-476-3','cr-476-3',1,5,2,2700,2700,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('80cb1a43-f562-4aa2-aa0d-f932d97456fc','contest-biweekly-170','cp-170-3','user-jiangly','cp-p-170-1','cr-170-1',1,5,1,1200,1200,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('8b337bb4-6d8f-4762-b92f-a6c97399a781','contest-weekly-476','cp-476-1','user-jiangly','cp-p-476-2','cr-476-2',1,3,1,240,240,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('8f6e7a3c-9ab2-4f11-928d-553d33a12a87','contest-weekly-476','cp-476-2','user-jiangly','cp-p-476-2','cr-476-2',1,4,1,720,720,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('9121385e-3163-4338-a984-d2a8978a8a38','contest-weekly-476','cp-476-1','user-benq','cp-p-476-3','cr-476-3',1,3,1,300,300,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('95048f65-d604-4588-b10f-9cb1511e1d55','contest-biweekly-170','cp-170-2','user-ecnerwala','cp-p-170-2','cr-170-2',1,4,1,660,660,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('a3125ca0-7569-4e12-8667-296f108e5cec','contest-weekly-476','cp-476-4','user-tourist','cp-p-476-1','cr-476-1',1,6,1,2100,2100,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('a8f13de1-896d-4b25-980b-f901a4501398','contest-biweekly-170','cp-170-2','user-jiangly','cp-p-170-1','cr-170-1',1,4,1,540,540,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('b640fc00-e4e4-4faa-973a-e70c7a323de9','contest-weekly-476','cp-476-4','user-benq','cp-p-476-3','cr-476-3',0,0,3,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('d92d1075-df40-4e4d-a8d0-1cee6061c58e','contest-biweekly-170','cp-170-3','user-ecnerwala','cp-p-170-2','cr-170-2',1,5,1,1380,1380,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('db01c1a4-cdc7-4537-ae20-51e8b4c4c67c','contest-weekly-476','cp-476-2','user-tourist','cp-p-476-1','cr-476-1',1,4,1,600,600,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('e1b00f01-bcb2-4cd2-88cf-a9df16c749b6','contest-weekly-476','cp-476-2','user-benq','cp-p-476-3','cr-476-3',1,4,1,900,900,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('e26ab9ca-022b-47d8-b6b9-c37a12d94ab2','contest-biweekly-170','cp-170-4','user-jiangly','cp-p-170-1','cr-170-1',1,6,1,2110,2110,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('f1a78eaa-8408-464c-b4fc-f7a27f96aa62','contest-weekly-476','cp-476-1','user-tourist','cp-p-476-1','cr-476-1',1,3,1,180,180,NULL,0,0,0);

-- Table: contest_rankings (18 rows)
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-1','contest-biweekly-170','user-jiangly',1,3820,3828,8,0,4,3000,18,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-2','contest-biweekly-170','user-ecnerwala',2,3582,3590,8,0,4,3300,18,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-3','contest-biweekly-170','user-tourist',3,3985,3979,-6,0,4,3600,18,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-4','contest-biweekly-170','user-benq',4,3649,3654,5,0,3,3500,15,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-5','contest-biweekly-170','user-um_nik',5,3516,3521,5,0,3,4200,15,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-6','contest-biweekly-170','user-scott',6,3385,3389,4,0,2,3900,12,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-7','contest-biweekly-170','user-yuki',7,2862,2856,-6,0,2,4100,12,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-8','contest-biweekly-170','user-alex',8,2720,2734,14,0,1,2600,7,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-1','contest-weekly-476','user-tourist',1,3979,3985,6,0,4,2700,18,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-10','contest-weekly-476','user-chen',10,2689,2673,-16,0,1,2800,7,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-2','contest-weekly-476','user-jiangly',2,3812,3820,8,0,4,3000,18,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-3','contest-weekly-476','user-benq',3,3654,3649,-5,0,3,4000,15,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-4','contest-weekly-476','user-ecnerwala',4,3589,3582,-7,0,3,4200,15,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-5','contest-weekly-476','user-um_nik',5,3521,3516,-5,0,3,4500,15,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-6','contest-weekly-476','user-petr',6,3456,3448,-8,0,2,3800,12,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-7','contest-weekly-476','user-scott',7,3389,3385,-4,0,2,4100,12,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-8','contest-weekly-476','user-yuki',8,2856,2862,6,0,2,4300,12,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-476-9','contest-weekly-476','user-alex',9,2734,2720,-14,0,1,2500,7,NULL,0,NULL,0);

-- Table: contest_problems (11 rows)
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-170-1','contest-biweekly-170',1,'Q1',3,NULL,2150,2650,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-170-2','contest-biweekly-170',2,'Q2',4,NULL,1840,2480,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-170-3','contest-biweekly-170',3,'Q3',5,NULL,920,1985,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-170-4','contest-biweekly-170',5,'Q4',6,NULL,410,1320,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-476-1','contest-weekly-476',1,'Q1',3,NULL,2560,4100,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-476-2','contest-weekly-476',2,'Q2',4,NULL,1980,3605,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-476-3','contest-weekly-476',3,'Q3',5,NULL,1250,2800,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-476-4','contest-weekly-476',5,'Q4',6,NULL,320,1450,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-477-1','contest-weekly-477',1,'Q1',3,NULL,0,0,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-477-2','contest-weekly-477',3,'Q2',4,NULL,0,0,NULL,100,0);
INSERT INTO `contest_problems` (`id`, `contest_id`, `problem_id`, `problem_index`, `score`, `penalty_per_wrong`, `solved_count`, `submission_count`, `label`, `base_score`, `time_bonus`) VALUES ('cp-477-3','contest-weekly-477',4,'Q3',5,NULL,0,0,NULL,100,0);

-- Table: contest_scoring_rules (2 rows)
INSERT INTO `contest_scoring_rules` (`id`, `name`, `description`, `base_score_per_problem`, `time_bonus_per_minute`, `wrong_answer_penalty`, `time_limit_penalty`, `first_solve_bonus`, `full_score_bonus`, `is_default`, `is_active`, `created_at`, `updated_at`) VALUES ('default-icpc','ICPC 规则','ACM/ICPC 风格的罚时规则',100,0,20,0,0,0,0,1,NOW(3),NOW(3));
INSERT INTO `contest_scoring_rules` (`id`, `name`, `description`, `base_score_per_problem`, `time_bonus_per_minute`, `wrong_answer_penalty`, `time_limit_penalty`, `first_solve_bonus`, `full_score_bonus`, `is_default`, `is_active`, `created_at`, `updated_at`) VALUES ('default-weekly','标准周赛规则','LeetCode 风格的简单积分规则',100,1,5,0,10,0,1,1,NOW(3),NOW(3));

-- Table: virtual_contest_sessions (3 rows)
INSERT INTO `virtual_contest_sessions` (`id`, `contest_id`, `user_id`, `status`, `started_at`, `ends_at`, `finished_at`, `total_score`, `total_penalty`) VALUES ('vcs-1','contest-weekly-476','user-max','COMPLETED',NOW(3),NOW(3),NOW(3),12,3200);
INSERT INTO `virtual_contest_sessions` (`id`, `contest_id`, `user_id`, `status`, `started_at`, `ends_at`, `finished_at`, `total_score`, `total_penalty`) VALUES ('vcs-2','contest-weekly-476','user-sara','COMPLETED',NOW(3),NOW(3),NOW(3),7,2400);
INSERT INTO `virtual_contest_sessions` (`id`, `contest_id`, `user_id`, `status`, `started_at`, `ends_at`, `finished_at`, `total_score`, `total_penalty`) VALUES ('vcs-3','contest-biweekly-170','user-tom','COMPLETED',NOW(3),NOW(3),NOW(3),15,3600);

-- Table: global_rankings (10 rows)
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-1','user-tourist','tourist',1,3979,4010,156,'https://api.dicebear.com/7.x/notionists/svg?seed=tourist','BY','Legendary Grandmaster',150,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-10','user-chen','chen_master',10,2689,2756,67,'https://api.dicebear.com/7.x/notionists/svg?seed=chen','CN','Master',62,NULL,'INTERNATIONAL_GRANDMASTER','INTERNATIONAL_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-2','user-jiangly','jiangly',2,3812,3856,89,'https://api.dicebear.com/7.x/notionists/svg?seed=jiangly','CN','Legendary Grandmaster',85,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-3','user-benq','Benq',3,3654,3712,124,'https://api.dicebear.com/7.x/notionists/svg?seed=benq','US','International Grandmaster',120,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-4','user-ecnerwala','ecnerwala',4,3589,3645,98,'https://api.dicebear.com/7.x/notionists/svg?seed=ecnerwala','US','International Grandmaster',92,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-5','user-um_nik','Um_nik',5,3521,3598,112,'https://api.dicebear.com/7.x/notionists/svg?seed=um_nik','UA','International Grandmaster',108,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-6','user-petr','Petr',6,3456,3534,187,'https://api.dicebear.com/7.x/notionists/svg?seed=petr','RU','Grandmaster',180,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-7','user-scott','scott_wu',7,3389,3412,76,'https://api.dicebear.com/7.x/notionists/svg?seed=scott','US','Grandmaster',72,NULL,'LEGENDARY_GRANDMASTER','LEGENDARY_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-8','user-yuki','yuki_codes',8,2856,2912,45,'https://api.dicebear.com/7.x/notionists/svg?seed=yuki','JP','Master',42,NULL,'LEGENDARY_GRANDMASTER','INTERNATIONAL_GRANDMASTER',NOW(3));
INSERT INTO `global_rankings` (`id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`, `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`, `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`) VALUES ('gr-9','user-alex','alex_algorithm',9,2734,2801,52,'https://api.dicebear.com/7.x/notionists/svg?seed=alex','UK','Master',48,NULL,'INTERNATIONAL_GRANDMASTER','INTERNATIONAL_GRANDMASTER',NOW(3));

-- Table: contest_rankings -补充 biweekly-170 缺失排名 (2 rows)
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-9','contest-biweekly-170','user-petr',9,3456,3462,6,0,2,4800,9,NULL,0,NULL,0);
INSERT INTO `contest_rankings` (`id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`, `rating_change`, `is_virtual`, `solved_count`, `total_penalty`, `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`) VALUES ('cr-170-10','contest-biweekly-170','user-chen',10,2689,2695,6,0,1,3000,3,NULL,0,NULL,0);

-- Table: contest_participants -补充 biweekly-170 缺失参与者 (2 rows)
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-9','contest-biweekly-170','user-petr','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,9,4800,9,0,NULL,NULL,NULL,0,0);
INSERT INTO `contest_participants` (`id`, `contest_id`, `user_id`, `status`, `registered_at`, `started_at`, `finished_at`, `is_virtual`, `final_rank`, `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`, `virtual_session_id`, `checked_in_at`, `total_time`, `attempt_count`) VALUES ('cp-p-170-10','contest-biweekly-170','user-chen','FINISHED',NOW(3),'2025-11-22 14:30:00.000',NOW(3),0,10,3000,3,0,NULL,NULL,NULL,0,0);

-- Table: contest_problem_results -补充 biweekly-170 缺失结果 (14 rows)
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-benq-q1','contest-biweekly-170','cp-170-1','user-benq','cp-p-170-4','cr-170-4',1,3,1,180,180,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-benq-q2','contest-biweekly-170','cp-170-2','user-benq','cp-p-170-4','cr-170-4',1,4,1,540,540,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-benq-q3','contest-biweekly-170','cp-170-3','user-benq','cp-p-170-4','cr-170-4',1,5,1,1200,1200,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-benq-q4','contest-biweekly-170','cp-170-4','user-benq','cp-p-170-4','cr-170-4',0,0,2,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-umnik-q1','contest-biweekly-170','cp-170-1','user-um_nik','cp-p-170-5','cr-170-5',1,3,1,200,200,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-umnik-q2','contest-biweekly-170','cp-170-2','user-um_nik','cp-p-170-5','cr-170-5',1,4,2,840,840,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-umnik-q3','contest-biweekly-170','cp-170-3','user-um_nik','cp-p-170-5','cr-170-5',1,5,1,1500,1500,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-umnik-q4','contest-biweekly-170','cp-170-4','user-um_nik','cp-p-170-5','cr-170-5',0,0,1,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-scott-q1','contest-biweekly-170','cp-170-1','user-scott','cp-p-170-6','cr-170-6',1,3,1,240,240,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-scott-q2','contest-biweekly-170','cp-170-2','user-scott','cp-p-170-6','cr-170-6',1,4,2,900,900,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-scott-q3','contest-biweekly-170','cp-170-3','user-scott','cp-p-170-6','cr-170-6',0,0,2,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-scott-q4','contest-biweekly-170','cp-170-4','user-scott','cp-p-170-6','cr-170-6',0,0,0,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-yuki-q1','contest-biweekly-170','cp-170-1','user-yuki','cp-p-170-7','cr-170-7',1,3,1,300,300,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-yuki-q2','contest-biweekly-170','cp-170-2','user-yuki','cp-p-170-7','cr-170-7',1,4,2,1020,1020,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-yuki-q3','contest-biweekly-170','cp-170-3','user-yuki','cp-p-170-7','cr-170-7',0,0,3,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-yuki-q4','contest-biweekly-170','cp-170-4','user-yuki','cp-p-170-7','cr-170-7',0,0,0,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-alex-q1','contest-biweekly-170','cp-170-1','user-alex','cp-p-170-8','cr-170-8',1,3,1,420,420,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-alex-q2','contest-biweekly-170','cp-170-2','user-alex','cp-p-170-8','cr-170-8',0,0,1,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-alex-q3','contest-biweekly-170','cp-170-3','user-alex','cp-p-170-8','cr-170-8',0,0,0,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-alex-q4','contest-biweekly-170','cp-170-4','user-alex','cp-p-170-8','cr-170-8',0,0,0,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-petr-q1','contest-biweekly-170','cp-170-1','user-petr','cp-p-170-9','cr-170-9',1,3,1,360,360,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-petr-q2','contest-biweekly-170','cp-170-2','user-petr','cp-p-170-9','cr-170-9',1,4,1,780,780,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-petr-q3','contest-biweekly-170','cp-170-3','user-petr','cp-p-170-9','cr-170-9',0,0,2,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-petr-q4','contest-biweekly-170','cp-170-4','user-petr','cp-p-170-9','cr-170-9',0,0,0,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-chen-q1','contest-biweekly-170','cp-170-1','user-chen','cp-p-170-10','cr-170-10',1,3,1,480,480,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-chen-q2','contest-biweekly-170','cp-170-2','user-chen','cp-p-170-10','cr-170-10',0,0,2,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-chen-q3','contest-biweekly-170','cp-170-3','user-chen','cp-p-170-10','cr-170-10',0,0,1,NULL,0,NULL,0,0,0);
INSERT INTO `contest_problem_results` (`id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`, `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`, `penalty_time`, `best_submission_id`, `time_spent`, `time_bonus`, `is_first_solve`) VALUES ('cpr-170-chen-q4','contest-biweekly-170','cp-170-4','user-chen','cp-p-170-10','cr-170-10',0,0,0,NULL,0,NULL,0,0,0);

SET FOREIGN_KEY_CHECKS=1;
