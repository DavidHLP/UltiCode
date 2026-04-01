SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V7__recommendation_schema
-- Generated from ulticode.sql
-- Tables: 1

CREATE TABLE `DailyRecommendation` (
  `id` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `problem_slug` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_title` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `difficulty` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score` decimal(65,30) NOT NULL,
  `tags` json NOT NULL DEFAULT (_utf8mb4'[]'),
  `reason` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scenario` enum('DAILY','SIMILAR','WEAK_POINT','CHALLENGE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DAILY',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `DailyRecommendation_user_id_problem_id_scenario_key` (`user_id`,`problem_id`,`scenario`),
  KEY `DailyRecommendation_user_id_idx` (`user_id`),
  KEY `DailyRecommendation_scenario_idx` (`scenario`),
  KEY `DailyRecommendation_created_at_idx` (`created_at`),
  KEY `DailyRecommendation_user_id_fkey` (`user_id`),
  CONSTRAINT `DailyRecommendation_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

SET FOREIGN_KEY_CHECKS=1;
