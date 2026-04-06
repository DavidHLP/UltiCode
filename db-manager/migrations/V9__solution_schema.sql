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
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-001',1,'user-yuki','哈希表解法 — O(n) 时间复杂度','# 哈希表解法\n\n使用哈希表在 O(n) 时间内解决两数之和。','O(n) 时间复杂度的哈希表解法。','typescript','["hash-map","array"]',150,NOW(3),NOW(3),1,NOW(3),'user-yuki',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-002',1,'user-alex','暴力解法','# 暴力枚举\n\n检查所有可能的配对。','O(n²) 暴力枚举解法。','javascript','["brute-force"]',80,NOW(3),NOW(3),1,NOW(3),'user-alex',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-003',1,'user-chen','TypeScript Map 解法','# TypeScript Map\n\n使用 Map 实现简洁解法。','TypeScript Map 解法。','typescript','["map","typescript"]',120,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-004',1,'user-tourist','JavaScript 哈希表','# JS 哈希表\n\n使用原生 Map 对象。','JavaScript Map 实现。','javascript','["map","javascript"]',95,NOW(3),NOW(3),1,NOW(3),'user-tourist',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-005',2,'user-sara','滑动窗口','# 滑动窗口\n\n使用左右指针配合哈希表。','O(n) 滑动窗口解法。','typescript','["sliding-window","hash-map"]',200,NOW(3),NOW(3),1,NOW(3),'user-sara',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-006',3,'user-max','排序扫描合并','# 排序扫描\n\n排序区间后合并重叠部分。','O(n log n) 排序解法。','javascript','["sorting","intervals"]',175,NOW(3),NOW(3),1,NOW(3),'user-max',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-007',4,'user-petr','二分查找 partition','# 二分查找\n\n在较短的数组上进行二分。','O(log(min(m,n))) 二分查找解法。','typescript','["binary-search","divide-and-conquer"]',130,NOW(3),NOW(3),1,NOW(3),'user-petr',0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solutions` (`id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`, `views`, `created_at`, `updated_at`, `is_published`, `published_at`, `published_by`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('sol-008',5,'user-chen','迭代 DFS 洪水填充','# DFS 洪水填充\n\n使用显式栈的迭代 DFS。','基于栈的 O(mn) DFS 解法。','typescript','["dfs","graph"]',110,NOW(3),NOW(3),1,NOW(3),'user-chen',0,NULL,NULL,0,NULL,NULL);

-- Table: solution_comments (10 rows)
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-001','sol-001',NULL,'user-max','讲解很棒！帮我更好地理解了哈希表。',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-002','sol-001','comment-001','user-yuki','谢谢！很高兴对你有帮助 😊',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-003','sol-001',NULL,'user-sara','如果数组中有重复数字怎么办？',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-004','sol-001','comment-003','user-yuki','好问题！哈希表会覆盖之前的索引，但这没问题，因为我们只需要找到一对有效的解。',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-005','sol-002',NULL,'user-lily','对初学者来说是个很好的入门起点！',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-006','sol-003',NULL,'user-david','很喜欢 TypeScript Map 的解法，非常简洁！',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-007','sol-005',NULL,'user-tom','左指针移动方式的讲解很到位。',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-008','sol-006',NULL,'user-lily','先排序这个思路被低估了。',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-009','sol-007',NULL,'user-scott','二分查找的证明思路很有用，谢谢！',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
INSERT INTO `solution_comments` (`id`, `solution_id`, `parent_id`, `user_id`, `content`, `created_at`, `updated_at`, `is_flagged`, `flagged_reason`, `flagged_at`, `is_deleted`, `deleted_at`, `deleted_by`) VALUES ('comment-010','sol-008',NULL,'user-emma','基于栈的 DFS 避免了递归栈溢出。好技巧。',NOW(3),NOW(3),0,NULL,NULL,0,NULL,NULL);
SET FOREIGN_KEY_CHECKS=1;
