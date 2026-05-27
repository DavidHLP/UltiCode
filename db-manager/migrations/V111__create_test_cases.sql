-- UltiCode Migration: V111__create_test_cases
-- Creates the test_cases table for official judge test cases.

CREATE TABLE `test_cases` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `is_sample` tinyint(1) NOT NULL DEFAULT '0',
  `is_hidden` tinyint(1) NOT NULL DEFAULT '0',
  `test_order` int NOT NULL DEFAULT '0',
  `input_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci,
  `inputs` json DEFAULT NULL,
  `constraints` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `test_cases_problem_id_fkey` (`problem_id`),
  CONSTRAINT `test_cases_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);
