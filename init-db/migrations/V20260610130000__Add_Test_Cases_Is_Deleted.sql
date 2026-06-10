-- Add is_deleted / deleted_at columns to test_cases
-- ------------------------------------------------------------
-- 背景: V20260610120000__Create_Test_Cases_Table.sql 创建 test_cases 表
--       时未包含 is_deleted / deleted_at 软删除字段,与 submissions /
--       problems 等其它表的模式不一致,违反项目 MySQL 规范
--       (02-mysql-coding.md 要求 is_deleted 为必备字段)。
-- 修复: 新建本迁移追加两列,NOT NULL 列给 DEFAULT '0' 避免在已有
--       数据上失败(项目规则 02-mysql-coding.md 与 01-flyway-migrations.md)。
-- 路由:  /admin/problems/{problemId}/test-cases/*
-- 风险:  低,仅追加列,默认值保证空表立即可用;已存在行 is_deleted=0
--        视为未删除,与原始迁移行为一致。
-- 回滚:  ALTER TABLE `test_cases`
--        DROP COLUMN `is_deleted`,
--        DROP COLUMN `deleted_at`;
-- ------------------------------------------------------------

ALTER TABLE `test_cases`
  ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT '0' AFTER `version`,
  ADD COLUMN `deleted_at` datetime(3) DEFAULT NULL AFTER `is_deleted`;
