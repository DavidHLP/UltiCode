-- Create test_cases table for /admin/problems/{problemId}/test-cases/*
-- ------------------------------------------------------------
-- 背景: TestCase 实体 (@TableName("test_cases")) 早已存在,但
--       test_cases 表从未在 V20260602_120000__Create_All_Tables.sql
--       中创建,导致 AdminTestCaseService 几乎所有 MyBatis 操作
--       (selectList / selectById / insert / updateById / deleteById)
--       都返回 MyBatisSystemException,被 GlobalExceptionHandler 映射为
--       500 / code=50000 "Unknown error" (实际跑出来是 50000,见 T-01)。
-- 修复: 新建本表,所有列与 TestCase 实体一一对应,FK 引用 problems.id。
--       index 命名遵循 idx_<col> / idx_<a>_<b> 规则。
-- 路由:  GET/POST/PUT/DELETE/POST bulk/PUT reorder/GET export
--        /admin/problems/{problemId}/test-cases
-- 风险:  低,新表无现有数据,无破坏性。
-- 回滚:  DROP TABLE IF EXISTS `test_cases`;
-- ------------------------------------------------------------

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
  PRIMARY KEY (`id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_problem_id_test_order` (`problem_id`, `test_order`),
  CONSTRAINT `fk_test_cases_problem_id` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
