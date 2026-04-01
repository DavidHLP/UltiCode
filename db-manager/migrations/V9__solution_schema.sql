SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V9__solution_schema
-- Generated from ulticode.sql
-- Tables: 2

CREATE TABLE `solution_comments` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `solution_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text COLLATE utf8mb4_unicode_ci,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `solution_comments_parent_id_fkey` (`parent_id`),
  KEY `solution_comments_solution_id_fkey` (`solution_id`),
  KEY `solution_comments_user_id_fkey` (`user_id`),
  KEY `solution_comments_solution_id_created_at_idx` (`solution_id`,`created_at`),
  CONSTRAINT `solution_comments_parent_id_fkey` FOREIGN KEY (`parent_id`) REFERENCES `solution_comments` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `solution_comments_solution_id_fkey` FOREIGN KEY (`solution_id`) REFERENCES `solutions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `solution_comments_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `solutions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci,
  `language` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT '1',
  `published_at` datetime(3) DEFAULT NULL,
  `published_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text COLLATE utf8mb4_unicode_ci,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `solutions_problem_id_fkey` (`problem_id`),
  KEY `solutions_user_id_fkey` (`user_id`),
  KEY `solutions_problem_id_created_at_idx` (`problem_id`,`created_at`),
  KEY `solutions_user_id_created_at_idx` (`user_id`,`created_at`),
  KEY `solutions_is_flagged_is_deleted_idx` (`is_flagged`,`is_deleted`),
  KEY `solutions_is_published_is_deleted_idx` (`is_published`,`is_deleted`),
  CONSTRAINT `solutions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `solutions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);



-- Seed Data

-- Table: solutions (8 rows)
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-001',1,'user-yuki','Hash Map Approach - O(n) Time Complexity','# Hash Map Solution\n\nUses a hash map to solve Two Sum in O(n) time.','Hash map approach for O(n) time complexity.','typescript','["hash-map","array"]',150,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-002',1,'user-alex','Brute Force Solution','# Brute Force\n\nCheck every possible pair.','Brute force O(n^2) approach.','javascript','["brute-force"]',80,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-003',1,'user-chen','TypeScript Map Solution','# TypeScript Map\n\nUsing Map for a clean solution.','TypeScript Map approach.','typescript','["map","typescript"]',120,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-004',1,'user-tourist','JavaScript Hash Map','# JS Hash Map\n\nUsing native Map object.','JavaScript Map implementation.','javascript','["map","javascript"]',95,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-005',2,'user-sara','Sliding Window','# Sliding Window\n\nUse left/right pointers with hashmap.','Sliding window O(n) approach.','typescript','["sliding-window","hash-map"]',200,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-006',3,'user-max','Sort and Sweep Merge','# Sort and Sweep\n\nSort intervals then merge overlaps.','Sort-based O(n log n) approach.','javascript','["sorting","intervals"]',175,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-007',4,'user-petr','Binary Search on Partitions','# Binary Search\n\nBinary search on the shorter array.','Binary search O(log(min(m,n))) approach.','typescript','["binary-search","divide-and-conquer"]',130,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-008',5,'user-chen','Iterative DFS Flood Fill','# DFS Flood Fill\n\nIterative DFS with explicit stack.','Stack-based DFS O(mn) approach.','typescript','["dfs","graph"]',110,'2026-03-22 05:44:30.900','2026-03-22 05:44:30.900',1,'2026-03-22 05:44:30.900','user-chen',0,NULL,NULL,0,NULL,NULL);

-- Table: solution_comments (10 rows)
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-001','sol-001',NULL,'user-max','Great explanation! This helped me understand hash maps better.','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-002','sol-001','comment-001','user-yuki','Thanks! Glad it was helpful 😊','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-003','sol-001',NULL,'user-sara','What if there are duplicate numbers in the array?','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-004','sol-001','comment-003','user-yuki','Good question! The hash map will overwrite the previous index, but that\'s fine since we only need to find one valid pair.','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-005','sol-002',NULL,'user-lily','This is a good starting point for beginners!','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-006','sol-003',NULL,'user-david','Love the TypeScript Map approach, very clean!','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-007','sol-005',NULL,'user-tom','Nice explanation of how to move the left pointer.','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-008','sol-006',NULL,'user-lily','Sorting first is underrated here.','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-009','sol-007',NULL,'user-scott','Binary search proof sketch was useful, thanks!','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-010','sol-008',NULL,'user-emma','Stack-based DFS kept my recursion stack from blowing up. Good tip.','2026-03-22 05:44:30.901','2026-03-22 05:44:30.901',0,NULL,NULL,0,NULL,NULL);
SET FOREIGN_KEY_CHECKS=1;
