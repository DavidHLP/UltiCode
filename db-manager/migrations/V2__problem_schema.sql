SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V2__problem_schema
-- Generated from ulticode.sql
-- Tables: 12

CREATE TABLE `first_solve_records` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contest_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `solved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`,`problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`),
  KEY `first_solve_records_problem_id_fkey` (`problem_id`),
  CONSTRAINT `first_solve_records_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `first_solve_records_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE,
  CONSTRAINT `first_solve_records_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_details` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `slug` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `companies` json DEFAULT NULL,
  `likes` int NOT NULL DEFAULT '0',
  `dislikes` int NOT NULL DEFAULT '0',
  `difficulty_rating` decimal(5,1) NOT NULL DEFAULT '1500.0',
  `updated_at` datetime(3) NOT NULL,
  `follow_up` text COLLATE utf8mb4_unicode_ci,
  `constraints_json` json NOT NULL,
  `hints` json DEFAULT NULL,
  `interactions` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_details_problem_id_key` (`problem_id`),
  KEY `problem_details_likes_idx` (`likes`),
  CONSTRAINT `problem_details_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_examples` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `example_order` int NOT NULL DEFAULT '0',
  `input_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `inputs` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `problem_examples_problem_id_fkey` (`problem_id`),
  CONSTRAINT `problem_examples_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_languages` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `label` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `style` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `starter_code` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `problem_languages_problem_id_fkey` (`problem_id`),
  CONSTRAINT `problem_languages_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_list_bookmarks` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `list_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_list` (`user_id`,`list_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_list_id` (`list_id`),
  KEY `idx_category_id` (`category_id`)
);

CREATE TABLE `problem_list_categories` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
);

CREATE TABLE `problem_list_problem_relations` (
  `list_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `added_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`list_id`,`problem_id`),
  KEY `problem_list_problem_relations_problem_id_fkey` (`problem_id`),
  CONSTRAINT `problem_list_problem_relations_list_id_fkey` FOREIGN KEY (`list_id`) REFERENCES `problem_lists` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `problem_list_problem_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_lists` (
  `id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `author_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  `is_featured` tinyint(1) NOT NULL DEFAULT '0',
  `banner_tag` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banner_icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banner_theme` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banner_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
);

CREATE TABLE `problem_notes` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_notes_user_id_problem_id_key` (`user_id`,`problem_id`),
  KEY `problem_notes_problem_id_fkey` (`problem_id`),
  CONSTRAINT `problem_notes_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `problem_notes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_tag_relations` (
  `problem_id` bigint NOT NULL,
  `tag_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`problem_id`,`tag_id`),
  KEY `problem_tag_relations_tag_id_fkey` (`tag_id`),
  CONSTRAINT `problem_tag_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `problem_tag_relations_tag_id_fkey` FOREIGN KEY (`tag_id`) REFERENCES `problem_tags` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `problem_tags` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `usage_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `problem_tags_slug_key` (`slug`)
);

CREATE TABLE `reports` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reporter_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `evidence` text COLLATE utf8mb4_unicode_ci,
  `status` enum('PENDING','REVIEWED','RESOLVED','DISMISSED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `queue_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `reports_entity_type_entity_id_idx` (`entity_type`,`entity_id`),
  KEY `reports_reporter_id_idx` (`reporter_id`),
  KEY `reports_status_idx` (`status`),
  KEY `reports_category_idx` (`category`),
  KEY `reports_queue_id_fkey` (`queue_id`),
  CONSTRAINT `reports_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue` (`id`) ON DELETE SET NULL,
  CONSTRAINT `reports_reporter_id_fkey` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
);



-- Seed Data

-- Table: problems (8 rows)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (1,'two-sum','Two Sum','Easy',49.20,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.546','2026-03-22 05:44:30.546',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (2,'longest-substring-without-repeating-characters','Longest Substring Without Repeating Characters','Medium',36.40,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.547','2026-03-22 05:44:30.547',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (3,'merge-intervals','Merge Intervals','Medium',58.30,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.549','2026-03-22 05:44:30.549',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (4,'median-of-two-sorted-arrays','Median of Two Sorted Arrays','Hard',34.60,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.550','2026-03-22 05:44:30.550',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (5,'number-of-islands','Number of Islands','Medium',67.80,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.553','2026-03-22 05:44:30.553',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (6,'combine-two-tables','Combine Two Tables','Easy',75.10,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.554','2026-03-22 05:44:30.554',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (7,'tenth-line','Tenth Line','Easy',33.20,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.556','2026-03-22 05:44:30.556',1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (8,'print-foobar-alternately','Print FooBar Alternately','Medium',61.50,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'2026-03-22 05:44:30.558','2026-03-22 05:44:30.558',1);

-- Table: problem_details (8 rows)
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-combine-two-tables',6,'combine-two-tables','Write a SQL query to report the first name, last name, city, and state of each person in the Person table. If the address of a personId is not present in the Address table, report null instead.','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"apple\", \"name\": \"Apple\"}, {\"id\": \"google\", \"name\": \"Google\"}]',0,0,1100.0,'2024-11-01 00:00:00.000',NULL,'[\"The tables Person and Address exist.\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-longest-substring',2,'longest-substring-without-repeating-characters','Given a string s, find the length of the longest substring without repeating characters.','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"meta\", \"name\": \"Meta\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}]',0,0,1420.0,'2024-11-01 00:00:00.000','Can you return the substring itself while keeping O(n) time?','[\"$0 \\\\leq s.length \\\\leq 5 \\\\times 10^4$\", \"s consists of English letters, digits, symbols, and spaces.\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-median-two-sorted-arrays',4,'median-of-two-sorted-arrays','Given two sorted arrays nums1 and nums2 of size m and n respectively, return the median of the two sorted arrays. The overall run time complexity should be O(log (m+n)).','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"uber\", \"name\": \"Uber\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}]',0,0,1960.0,'2024-11-01 00:00:00.000','Can you prove why the binary search over partitions is correct?','[\"$0 \\\\leq m, n \\\\leq 10^6$\", \"$-10^6 \\\\leq nums1[i], nums2[i] \\\\leq 10^6$\", \"Runs in O(log(m + n)) time.\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-merge-intervals',3,'merge-intervals','Given an array of intervals where intervals[i] = [start_i, end_i], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"uber\", \"name\": \"Uber\"}]',0,0,1525.0,'2024-11-01 00:00:00.000','How would you handle streaming intervals where you cannot keep them all in memory?','[\"$1 \\\\leq intervals.length \\\\leq 10^4$\", \"intervals[i].length = 2\", \"$0 \\\\leq start_i \\\\leq end_i \\\\leq 10^4$\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-number-of-islands',5,'number-of-islands','Given an m x n 2D binary grid that represents a map of \"1\"s (land) and \"0\"s (water), return the number of islands. An island is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"bloomberg\", \"name\": \"Bloomberg\"}]',0,0,1620.0,'2024-11-01 00:00:00.000','How would you count islands in an online grid where cells flip from water to land?','[\"$1 \\\\leq m, n \\\\leq 300$\", \"grid[i][j] is \\\"0\\\" or \\\"1\\\".\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-print-foobar',8,'print-foobar-alternately','Suppose you are given the following code... The same instance of FooBar will be passed to two different threads. Thread A will call foo() and thread B will call bar(). Modify the program to output \"foobar\" n times.','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}]',0,0,1500.0,'2024-11-01 00:00:00.000',NULL,'[\"n is an integer.\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-tenth-line',7,'tenth-line','Given a text file `file.txt`, print just the 10th line of the file.','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"facebook\", \"name\": \"Facebook\"}]',0,0,1200.0,'2024-11-01 00:00:00.000',NULL,'[\"file.txt exists.\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-two-sum',1,'two-sum','Given an array of integers `nums` and an integer `target`, return _indices of the two numbers such that they add up to `target`_.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"apple\", \"name\": \"Apple\"}, {\"id\": \"adobe\", \"name\": \"Adobe\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"bloomberg\", \"name\": \"Bloomberg\"}]',54300,1800,1050.0,'2024-11-01 00:00:00.000','Can you come up with an algorithm that is less than $O(n^2)$ time complexity?','[\"$2 \\\\leq nums.length \\\\leq 10^4$\", \"$-10^9 \\\\leq nums[i] \\\\leq 10^9$\", \"$-10^9 \\\\leq target \\\\leq 10^9$\", \"**Only one valid answer exists.**\"]','[\"A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.\", \"So, if we fix one of the numbers, say x, we have to scan the entire array to find the next number y which is value - x where value is the input parameter. Can we change our array somehow so that this search becomes faster?\", \"The second train of thought is, without changing the array, can we use additional space to somehow make the search faster? This is where a hash map comes in handy.\"]',NULL);

-- Table: problem_examples (15 rows)
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-1',5,0,'grid = [[\"1\",\"1\",\"1\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\",\"0\",\"0\"]]','1','All land cells are connected into a single island.','[{\"name\": \"grid\", \"value\": \"[[\\\"1\\\",\\\"1\\\",\\\"1\\\",\\\"1\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"1\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-2',5,1,'grid = [[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"0\",\"0\",\"1\",\"0\",\"0\"],[\"0\",\"0\",\"0\",\"1\",\"1\"]]','3','There is one island in the top-left, one in the middle, and one in the bottom-right.','[{\"name\": \"grid\", \"value\": \"[[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"1\\\",\\\"1\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-3',5,2,'grid = [[\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\"]]','0','No land cells, so zero islands.','[{\"name\": \"grid\", \"value\": \"[[\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-1',2,0,'s = \"abcabcbb\"','3','The answer is \"abc\", with the length of 3.','[{\"name\": \"s\", \"value\": \"\\\"abcabcbb\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-2',2,1,'s = \"bbbbb\"','1','The answer is \"b\", with the length of 1.','[{\"name\": \"s\", \"value\": \"\\\"bbbbb\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-3',2,2,'s = \"pwwkew\"','3','The answer is \"wke\", with the length of 3. Note that the answer must be a substring, \"pwke\" is a subsequence and not a substring.','[{\"name\": \"s\", \"value\": \"\\\"pwwkew\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-1',4,0,'nums1 = [1,3], nums2 = [2]','2.00000','Merged array is [1,2,3] and median is 2.','[{\"name\": \"nums1\", \"value\": \"[1,3]\"}, {\"name\": \"nums2\", \"value\": \"[2]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-2',4,1,'nums1 = [1,2], nums2 = [3,4]','2.50000','Merged array is [1,2,3,4] and median is (2 + 3) / 2 = 2.5.','[{\"name\": \"nums1\", \"value\": \"[1,2]\"}, {\"name\": \"nums2\", \"value\": \"[3,4]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-3',4,2,'nums1 = [], nums2 = [1]','1.00000','Median is the only element 1.','[{\"name\": \"nums1\", \"value\": \"[]\"}, {\"name\": \"nums2\", \"value\": \"[1]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-1',3,0,'intervals = [[1,3],[2,6],[8,10],[15,18]]','[[1,6],[8,10],[15,18]]','Intervals [1,3] and [2,6] overlap, merge into [1,6].','[{\"name\": \"intervals\", \"value\": \"[[1,3],[2,6],[8,10],[15,18]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-2',3,1,'intervals = [[1,4],[4,5]]','[[1,5]]','Intervals that touch at the boundary are merged.','[{\"name\": \"intervals\", \"value\": \"[[1,4],[4,5]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-3',3,2,'intervals = [[1,4],[2,3]]','[[1,4]]','The second interval is contained within the first.','[{\"name\": \"intervals\", \"value\": \"[[1,4],[2,3]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-1',1,0,'nums = [2,7,11,15], target = 9','[0,1]','Because nums[0] + nums[1] == 9, we return [0, 1].','[{\"name\": \"nums\", \"value\": \"[2,7,11,15]\"}, {\"name\": \"target\", \"value\": \"9\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-2',1,1,'nums = [3,2,4], target = 6','[1,2]','nums[1] + nums[2] == 6, so we return [1, 2].','[{\"name\": \"nums\", \"value\": \"[3,2,4]\"}, {\"name\": \"target\", \"value\": \"6\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-3',1,2,'nums = [3,3], target = 6','[0,1]','The same element cannot be used twice, but two different elements with value 3 can be used.','[{\"name\": \"nums\", \"value\": \"[3,3]\"}, {\"name\": \"target\", \"value\": \"6\"}]');

-- Table: problem_languages (14 rows)
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-bash-7',7,'Bash','shell','shell','# Write your Bash code here\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js',1,'JavaScript','javascript','javascript','/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nvar twoSum = function(nums, target) {\n    // Write your code here\n    return [];\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js-2',2,'JavaScript','javascript','javascript','/**\n * @param {string} s\n * @return {number}\n */\nvar lengthOfLongestSubstring = function(s) {\n    // Write your code here\n    return 0;\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js-3',3,'JavaScript','javascript','javascript','/**\n * @param {number[][]} intervals\n * @return {number[][]}\n */\nvar merge = function(intervals) {\n    // Write your code here\n    return [];\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js-4',4,'JavaScript','javascript','javascript','/**\n * @param {number[]} nums1\n * @param {number[]} nums2\n * @return {number}\n */\nvar findMedianSortedArrays = function(nums1, nums2) {\n    // Write your code here\n    return 0;\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js-5',5,'JavaScript','javascript','javascript','/**\n * @param {character[][]} grid\n * @return {number}\n */\nvar numIslands = function(grid) {\n    // Write your code here\n    return 0;\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-js-8',8,'JavaScript','javascript','javascript','/**\n * @param {number} n\n */\nvar FooBar = function(n) {\n  this.n = n;\n};\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-mysql-6',6,'MySQL','mysql','mysql','# Write your MySQL code here\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts',1,'TypeScript','typescript','typescript','/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nfunction twoSum(nums: number[], target: number): number[] {\n    // Write your code here\n    return [];\n}\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts-2',2,'TypeScript','typescript','typescript','/**\n * @param {string} s\n * @return {number}\n */\nfunction lengthOfLongestSubstring(s: string): number {\n    // Write your code here\n    return 0;\n}\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts-3',3,'TypeScript','typescript','typescript','/**\n * @param {number[][]} intervals\n * @return {number[][]}\n */\nfunction merge(intervals: number[][]): number[][] {\n    // Write your code here\n    return [];\n}\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts-4',4,'TypeScript','typescript','typescript','/**\n * @param {number[]} nums1\n * @param {number[]} nums2\n * @return {number}\n */\nfunction findMedianSortedArrays(nums1: number[], nums2: number[]): number {\n    // Write your code here\n    return 0;\n}\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts-5',5,'TypeScript','typescript','typescript','/**\n * @param {string[][]} grid\n * @return {number}\n */\nfunction numIslands(grid: string[][]): number {\n    // Write your code here\n    return 0;\n}\n');
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES ('lang-ts-8',8,'TypeScript','typescript','typescript','/**\n * @param {number} n\n */\nclass FooBar {\n  private n: number;\n}\n');

-- Table: problem_tags (30 rows)
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('algorithms','Algorithms',NULL,NULL,NULL,0,'2026-03-22 05:44:30.559','2026-03-22 05:44:30.559');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('array','Array',NULL,NULL,NULL,0,'2026-03-22 05:44:30.561','2026-03-22 05:44:30.561');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('backtracking','Backtracking',NULL,NULL,NULL,0,'2026-03-22 05:44:30.591','2026-03-22 05:44:30.591');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('bfs','Breadth-First Search',NULL,NULL,NULL,0,'2026-03-22 05:44:30.571','2026-03-22 05:44:30.571');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('binary-search','Binary Search',NULL,NULL,NULL,0,'2026-03-22 05:44:30.568','2026-03-22 05:44:30.568');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('bit-manipulation','Bit Manipulation',NULL,NULL,NULL,0,'2026-03-22 05:44:30.587','2026-03-22 05:44:30.587');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('concurrency','Concurrency',NULL,NULL,NULL,0,'2026-03-22 05:44:30.575','2026-03-22 05:44:30.575');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('database','Database',NULL,NULL,NULL,0,'2026-03-22 05:44:30.573','2026-03-22 05:44:30.573');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('design','Design',NULL,NULL,NULL,0,'2026-03-22 05:44:30.585','2026-03-22 05:44:30.585');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('dfs','Depth-First Search',NULL,NULL,NULL,0,'2026-03-22 05:44:30.570','2026-03-22 05:44:30.570');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('divide-and-conquer','Divide and Conquer',NULL,NULL,NULL,0,'2026-03-22 05:44:30.569','2026-03-22 05:44:30.569');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('dynamic-programming','Dynamic Programming',NULL,NULL,NULL,0,'2026-03-22 05:44:30.577','2026-03-22 05:44:30.577');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('graph','Graph',NULL,NULL,NULL,0,'2026-03-22 05:44:30.583','2026-03-22 05:44:30.583');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('greedy','Greedy',NULL,NULL,NULL,0,'2026-03-22 05:44:30.578','2026-03-22 05:44:30.578');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('hash-table','Hash Table',NULL,NULL,NULL,0,'2026-03-22 05:44:30.562','2026-03-22 05:44:30.562');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('heap','Heap (Priority Queue)',NULL,NULL,NULL,0,'2026-03-22 05:44:30.582','2026-03-22 05:44:30.582');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('intervals','Intervals',NULL,NULL,NULL,0,'2026-03-22 05:44:30.567','2026-03-22 05:44:30.567');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('linked-list','Linked List',NULL,NULL,NULL,0,'2026-03-22 05:44:30.589','2026-03-22 05:44:30.589');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('math','Math',NULL,NULL,NULL,0,'2026-03-22 05:44:30.576','2026-03-22 05:44:30.576');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('matrix','Matrix',NULL,NULL,NULL,0,'2026-03-22 05:44:30.572','2026-03-22 05:44:30.572');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('queue','Queue',NULL,NULL,NULL,0,'2026-03-22 05:44:30.581','2026-03-22 05:44:30.581');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('recursion','Recursion',NULL,NULL,NULL,0,'2026-03-22 05:44:30.590','2026-03-22 05:44:30.590');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('shell','Shell',NULL,NULL,NULL,0,'2026-03-22 05:44:30.574','2026-03-22 05:44:30.574');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('sliding-window','Sliding Window',NULL,NULL,NULL,0,'2026-03-22 05:44:30.564','2026-03-22 05:44:30.564');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('sorting','Sorting',NULL,NULL,NULL,0,'2026-03-22 05:44:30.565','2026-03-22 05:44:30.565');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('stack','Stack',NULL,NULL,NULL,0,'2026-03-22 05:44:30.580','2026-03-22 05:44:30.580');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('string','String',NULL,NULL,NULL,0,'2026-03-22 05:44:30.563','2026-03-22 05:44:30.563');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('tree','Tree',NULL,NULL,NULL,0,'2026-03-22 05:44:30.584','2026-03-22 05:44:30.584');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('two-pointers','Two Pointers',NULL,NULL,NULL,0,'2026-03-22 05:44:30.579','2026-03-22 05:44:30.579');
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('union-find','Union Find',NULL,NULL,NULL,0,'2026-03-22 05:44:30.588','2026-03-22 05:44:30.588');

-- Table: problem_lists (5 rows)
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-essentials','Essential Problems','The absolute must-know patterns.','u-001',1,'2024-01-15 00:00:00.000','2024-02-02 00:00:00.000',1,'Essential','Trophy','amber',1);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-graph-dfs','Graph DFS/BFS Warm-up','Quick traversal problems to drill grid and graph intuition.','user-david',1,'2024-06-12 00:00:00.000','2024-06-20 00:00:00.000',0,NULL,NULL,NULL,0);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-hard-bench','Hard Benchmarks','Curated hard problems for interview prep and contest training.','user-petr',0,'2024-07-01 00:00:00.000','2024-07-15 00:00:00.000',0,NULL,NULL,NULL,0);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-intervals','Intervals & Sorting','Sweep line, merging, and ordering exercises seen in contests.','user-chen',1,'2024-05-08 00:00:00.000','2024-05-20 00:00:00.000',1,'Sorting','ArrowUpDown','emerald',3);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-sliding-window','Sliding Window Classics','Strings and arrays that force you to manage window boundaries correctly.','user-sara',1,'2024-03-10 00:00:00.000','2024-04-01 00:00:00.000',1,'Pattern','Code2','sky',2);

-- Table: problem_list_problem_relations (12 rows)
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',1,1,'2026-03-22 05:44:30.866');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',4,5,'2026-03-22 05:44:30.870');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',6,2,'2026-03-22 05:44:30.867');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',7,3,'2026-03-22 05:44:30.868');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',8,4,'2026-03-22 05:44:30.868');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-graph-dfs',5,10,'2026-03-22 05:44:30.877');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-hard-bench',3,12,'2026-03-22 05:44:30.880');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-hard-bench',4,11,'2026-03-22 05:44:30.879');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-intervals',1,9,'2026-03-22 05:44:30.876');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-intervals',3,8,'2026-03-22 05:44:30.874');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-sliding-window',1,6,'2026-03-22 05:44:30.871');
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-sliding-window',2,7,'2026-03-22 05:44:30.872');

-- Table: problem_tag_relations (23 rows)
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (1,'algorithms');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (2,'algorithms');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (3,'algorithms');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (4,'algorithms');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (5,'algorithms');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (1,'array');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (3,'array');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (4,'array');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (5,'bfs');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (4,'binary-search');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (8,'concurrency');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (6,'database');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (5,'dfs');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (4,'divide-and-conquer');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (1,'hash-table');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (2,'hash-table');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (3,'intervals');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (5,'matrix');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (7,'shell');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (2,'sliding-window');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (3,'sorting');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (2,'string');
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`) VALUES (5,'union-find');
SET FOREIGN_KEY_CHECKS=1;
