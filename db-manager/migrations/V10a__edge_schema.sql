SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V10a__edge_schema
-- Generated from ulticode.sql
-- Tables: 1

CREATE TABLE `edge_operations` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` enum('SOLUTION','SOLUTION_COMMENT','FORUM_POST','FORUM_COMMENT','PROBLEM','PROBLEM_LIST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_type` enum('VOTE_UP','VOTE_DOWN','ANALYZE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `edge_ops_unique` (`operator_id`,`operation_type`,`target_type`,`target_id`),
  KEY `edge_ops_target` (`target_type`,`target_id`),
  KEY `edge_ops_operation_target` (`operation_type`,`target_type`,`target_id`),
  CONSTRAINT `edge_operations_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

SET FOREIGN_KEY_CHECKS=1;
