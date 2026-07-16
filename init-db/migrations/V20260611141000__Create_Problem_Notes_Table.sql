-- Create problem_notes table for /problems/{problemId}/note
-- ------------------------------------------------------------
-- 背景:  console/src/api/interaction.ts 中 fetchProblemNote / saveProblemNote
--        假定端点存在,但后端 0 实现 (docs/interaction-note-api-test-report-2026-06-11.md)
-- 修复:  新建本表,user_id × problem_id 唯一约束,内容 MEDIUMTEXT,FK 引用 problems/users。
-- 路由:  GET/POST /problems/{problemId}/note
-- 风险:  低,新表无现有数据,无破坏性。
-- 回滚:  DROP TABLE IF EXISTS `problem_notes`;
-- ------------------------------------------------------------

-- NOTE: problem_notes is also created by V20260602_120000__Create_All_Tables.sql
-- (full-schema bootstrap). CREATE TABLE IF NOT EXISTS keeps this migration
-- idempotent on a fresh migrate where the bootstrap already created the table,
-- while remaining a no-op on databases where it previously applied.
CREATE TABLE IF NOT EXISTS `problem_notes` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `problem_id` bigint NOT NULL,
  `content` mediumtext NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_problem` (`user_id`, `problem_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_problem_id` (`problem_id`),
  CONSTRAINT `fk_problem_notes_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_problem_notes_problem_id` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
