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
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (1,'two-sum','两数之和','Easy',49.20,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (2,'longest-substring-without-repeating-characters','无重复字符的最长子串','Medium',36.40,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (3,'merge-intervals','合并区间','Medium',58.30,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (4,'median-of-two-sorted-arrays','寻找两个正序数组的中位数','Hard',34.60,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (5,'number-of-islands','岛屿数量','Medium',67.80,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (6,'combine-two-tables','合并两个表','Easy',75.10,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (7,'tenth-line','第十行','Easy',33.20,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `completed_time`, `is_published`, `published_at`, `published_by`, `is_deleted`, `deleted_at`, `deleted_by`, `flag_notes`, `flag_reason`, `flag_reported_at`, `flag_reported_by`, `flag_reviewed_at`, `flag_reviewed_by`, `flag_status`, `is_flagged`, `created_at`, `updated_at`, `version`) VALUES (8,'print-foobar-alternately','交替打印 FooBar','Medium',61.50,'todo',0,1,NULL,1,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(3),NOW(3),1);

-- Table: problem_details (8 rows)
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-combine-two-tables',6,'combine-two-tables','编写一个 SQL 查询，报告 Person 表中每个人的姓氏、名字、城市和州。如果 Address 表中不存在某个 personId 对应的地址，则报告 null。','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"apple\", \"name\": \"Apple\"}, {\"id\": \"google\", \"name\": \"Google\"}]',0,0,1100.0,NOW(3),NULL,'[\"Person 表和 Address 表已存在。\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-longest-substring',2,'longest-substring-without-repeating-characters','给定一个字符串 s，找出其中不含有重复字符的最长子串的长度。','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"meta\", \"name\": \"Meta\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}]',0,0,1420.0,NOW(3),'你能在保持 O(n) 时间复杂度的同时返回子串本身吗？','[\"$0 \\\\leq s.length \\\\leq 5 \\\\times 10^4$\", \"s 由英文字母、数字、符号和空格组成。\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-median-two-sorted-arrays',4,'median-of-two-sorted-arrays','给定两个大小分别为 m 和 n 的正序数组 nums1 和 nums2，找出这两个正序数组的中位数。算法的时间复杂度应该为 O(log (m+n))。','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"uber\", \"name\": \"Uber\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}]',0,0,1960.0,NOW(3),'你能证明为什么在 partition 上进行二分查找是正确的吗？','[\"$0 \\\\leq m, n \\\\leq 10^6$\", \"$-10^6 \\\\leq nums1[i], nums2[i] \\\\leq 10^6$\", \"时间复杂度为 O(log(m + n))。\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-merge-intervals',3,'merge-intervals','给定一个区间数组 intervals，其中 intervals[i] = [start_i, end_i]，合并所有重叠的区间，并返回一个不重叠的区间数组，该数组需恰好覆盖输入中的所有区间。','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"uber\", \"name\": \"Uber\"}]',0,0,1525.0,NOW(3),'如果区间是流式到达、无法全部保存在内存中，你该如何处理？','[\"$1 \\\\leq intervals.length \\\\leq 10^4$\", \"intervals[i].length = 2\", \"$0 \\\\leq start_i \\\\leq end_i \\\\leq 10^4$\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-number-of-islands',5,'number-of-islands','给定一个 m x n 的二维二进制矩阵 grid，其中 \"1\" 代表陆地，\"0\" 代表水域，计算岛屿的数量。岛屿被水包围，并且通过水平或垂直方向上相邻的陆地连接而成。','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"bloomberg\", \"name\": \"Bloomberg\"}]',0,0,1620.0,NOW(3),'在一个单元格会从水变为陆地的在线网格中，你该如何计算岛屿数量？','[\"$1 \\\\leq m, n \\\\leq 300$\", \"grid[i][j] 为 \\\"0\\\" 或 \\\"1\\\".\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-print-foobar',8,'print-foobar-alternately','假设有如下代码... 同一个 FooBar 实例会被传递给两个不同的线程。线程 A 将调用 foo()，线程 B 将调用 bar()。修改程序使其输出 \"foobar\" 共 n 次。','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}]',0,0,1500.0,NOW(3),NULL,'[\"n 是一个整数。\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-tenth-line',7,'tenth-line','给定一个文本文件 `file.txt`，只打印第 10 行。','[{\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"facebook\", \"name\": \"Facebook\"}]',0,0,1200.0,NOW(3),NULL,'[\"file.txt 文件已存在。\"]',NULL,NULL);
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `companies`, `likes`, `dislikes`, `difficulty_rating`, `updated_at`, `follow_up`, `constraints_json`, `hints`, `interactions`) VALUES ('pd-two-sum',1,'two-sum','给定一个整数数组 `nums` 和一个整数 `target`，返回_和为 `target` 的两个整数的下标_。\n\nYou may assume that each input would have **exactly one solution**, and you may not use the *same* element twice.\n\nYou can return the answer in any order.','[{\"id\": \"amazon\", \"name\": \"Amazon\"}, {\"id\": \"google\", \"name\": \"Google\"}, {\"id\": \"apple\", \"name\": \"Apple\"}, {\"id\": \"adobe\", \"name\": \"Adobe\"}, {\"id\": \"microsoft\", \"name\": \"Microsoft\"}, {\"id\": \"bloomberg\", \"name\": \"Bloomberg\"}]',54300,1800,1050.0,NOW(3),'Can you come up with an algorithm that is less than $O(n^2)$ time complexity?','[\"$2 \\\\leq nums.length \\\\leq 10^4$\", \"$-10^9 \\\\leq nums[i] \\\\leq 10^9$\", \"$-10^9 \\\\leq target \\\\leq 10^9$\", \"**Only one valid answer exists.**\"]','[\"A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.\", \"如果我们固定一个数 x，就需要扫描整个数组来找到另一个值 y = target - x。我们能以某种方式改变数组使搜索更快吗？\", \"另一种思路是，在不改变数组的情况下，能否使用额外空间使搜索更快？这就是哈希表派上用场的地方。\"]',NULL);

-- Table: problem_examples (15 rows)
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-1',5,0,'grid = [[\"1\",\"1\",\"1\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"1\",\"0\"],[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\",\"0\",\"0\"]]','1','所有陆地单元格连接成一个岛屿。','[{\"name\": \"grid\", \"value\": \"[[\\\"1\\\",\\\"1\\\",\\\"1\\\",\\\"1\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"1\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-2',5,1,'grid = [[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"1\",\"1\",\"0\",\"0\",\"0\"],[\"0\",\"0\",\"1\",\"0\",\"0\"],[\"0\",\"0\",\"0\",\"1\",\"1\"]]','3','左上角一个岛屿，中间一个，右下角一个。','[{\"name\": \"grid\", \"value\": \"[[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"1\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"1\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\",\\\"1\\\",\\\"1\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-islands-3',5,2,'grid = [[\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\"],[\"0\",\"0\",\"0\"]]','0','没有陆地单元格，所以零个岛屿。','[{\"name\": \"grid\", \"value\": \"[[\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\"],[\\\"0\\\",\\\"0\\\",\\\"0\\\"]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-1',2,0,'s = \"abcabcbb\"','3','答案是 \"abc\"，长度为 3。','[{\"name\": \"s\", \"value\": \"\\\"abcabcbb\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-2',2,1,'s = \"bbbbb\"','1','答案是 \"b\"，长度为 1。','[{\"name\": \"s\", \"value\": \"\\\"bbbbb\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-longest-sub-3',2,2,'s = \"pwwkew\"','3','答案是 \"wke\"，长度为 3。注意答案必须是子串，\"pwke\" 是子序列而非子串。','[{\"name\": \"s\", \"value\": \"\\\"pwwkew\\\"\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-1',4,0,'nums1 = [1,3], nums2 = [2]','2.00000','合并后的数组为 [1,2,3]，中位数为 2。','[{\"name\": \"nums1\", \"value\": \"[1,3]\"}, {\"name\": \"nums2\", \"value\": \"[2]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-2',4,1,'nums1 = [1,2], nums2 = [3,4]','2.50000','合并后的数组为 [1,2,3,4]，中位数为 (2 + 3) / 2 = 2.5。','[{\"name\": \"nums1\", \"value\": \"[1,2]\"}, {\"name\": \"nums2\", \"value\": \"[3,4]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-median-3',4,2,'nums1 = [], nums2 = [1]','1.00000','中位数是唯一的元素 1。','[{\"name\": \"nums1\", \"value\": \"[]\"}, {\"name\": \"nums2\", \"value\": \"[1]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-1',3,0,'intervals = [[1,3],[2,6],[8,10],[15,18]]','[[1,6],[8,10],[15,18]]','区间 [1,3] 和 [2,6] 重叠，合并为 [1,6]。','[{\"name\": \"intervals\", \"value\": \"[[1,3],[2,6],[8,10],[15,18]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-2',3,1,'intervals = [[1,4],[4,5]]','[[1,5]]','边界相接的区间会被合并。','[{\"name\": \"intervals\", \"value\": \"[[1,4],[4,5]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-merge-3',3,2,'intervals = [[1,4],[2,3]]','[[1,4]]','第二个区间被包含在第一个区间内。','[{\"name\": \"intervals\", \"value\": \"[[1,4],[2,3]]\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-1',1,0,'nums = [2,7,11,15], target = 9','[0,1]','因为 nums[0] + nums[1] == 9，所以返回 [0, 1]。','[{\"name\": \"nums\", \"value\": \"[2,7,11,15]\"}, {\"name\": \"target\", \"value\": \"9\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-2',1,1,'nums = [3,2,4], target = 6','[1,2]','nums[1] + nums[2] == 6，所以返回 [1, 2]。','[{\"name\": \"nums\", \"value\": \"[3,2,4]\"}, {\"name\": \"target\", \"value\": \"6\"}]');
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`) VALUES ('ex-two-sum-3',1,2,'nums = [3,3], target = 6','[0,1]','不能使用同一元素两次，但两个值都为 3 的不同元素可以使用。','[{\"name\": \"nums\", \"value\": \"[3,3]\"}, {\"name\": \"target\", \"value\": \"6\"}]');

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
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('algorithms','算法',NULL,NULL,NULL,5,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('array','数组',NULL,NULL,NULL,3,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('backtracking','回溯',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('bfs','广度优先搜索',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('binary-search','二分查找',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('bit-manipulation','位运算',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('concurrency','并发',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('database','数据库',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('design','设计',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('dfs','深度优先搜索',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('divide-and-conquer','分治',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('dynamic-programming','动态规划',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('graph','图',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('greedy','贪心',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('hash-table','哈希表',NULL,NULL,NULL,2,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('heap','堆（优先队列）',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('intervals','区间',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('linked-list','链表',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('math','数学',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('matrix','矩阵',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('queue','队列',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('recursion','递归',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('shell','Shell',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('sliding-window','滑动窗口',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('sorting','排序',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('stack','栈',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('string','字符串',NULL,NULL,NULL,1,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('tree','树',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('two-pointers','双指针',NULL,NULL,NULL,0,NOW(3),NOW(3));
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`, `created_at`, `updated_at`) VALUES ('union-find','并查集',NULL,NULL,NULL,1,NOW(3),NOW(3));

-- Table: problem_lists (5 rows)
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-essentials','必刷题单','绝对必知的算法模式。','u-001',1,NOW(3),NOW(3),1,'Essential','Trophy','amber',1);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-graph-dfs','图 DFS/BFS 热身','快速遍历练习，强化网格和图论直觉。','user-david',1,NOW(3),NOW(3),0,NULL,NULL,NULL,0);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-hard-bench','难题基准','精选难题，用于面试准备和竞赛训练。','user-petr',0,NOW(3),NOW(3),0,NULL,NULL,NULL,0);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-intervals','区间与排序','竞赛中常见的扫描线、合并和排序练习。','user-chen',1,NOW(3),NOW(3),1,'排序','ArrowUpDown','emerald',3);
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `created_at`, `updated_at`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`) VALUES ('list-sliding-window','滑动窗口经典题','强制你正确管理窗口边界的字符串和数组题目。','user-sara',1,NOW(3),NOW(3),1,'Pattern','Code2','sky',2);

-- Table: problem_list_problem_relations (12 rows)
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',1,1,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',4,5,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',6,2,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',7,3,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-essentials',8,4,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-graph-dfs',5,10,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-hard-bench',3,12,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-hard-bench',4,11,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-intervals',1,9,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-intervals',3,8,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-sliding-window',1,6,NOW(3));
INSERT INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`) VALUES ('list-sliding-window',2,7,NOW(3));

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
