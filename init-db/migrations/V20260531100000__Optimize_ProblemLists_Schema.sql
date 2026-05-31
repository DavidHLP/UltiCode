-- ============================================================
-- V20260531100000__Optimize_ProblemLists_Schema.sql
-- 修正 problem_lists 相关表结构，遵循 MySQL 建表规约
-- ============================================================

-- 1. problem_lists: banner_order 改为 unsigned
ALTER TABLE problem_lists
  MODIFY COLUMN banner_order int unsigned NOT NULL DEFAULT '0' COMMENT 'Banner显示顺序';

-- 2. problem_lists: 添加 author_id 索引（按作者查询）
ALTER TABLE problem_lists
  ADD INDEX idx_author_id (author_id);

-- 3. problem_lists: 添加 is_featured 索引（后台过滤）
ALTER TABLE problem_lists
  ADD INDEX idx_is_featured (is_featured);

-- 4. problem_list_problem_relations: 调整主键顺序（区分度高的字段在前）
--   添加 list_id 单字段索引（"获取某题单所有题目"为高频查询）
ALTER TABLE problem_list_problem_relations
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (problem_id, list_id),
  ADD INDEX idx_list_id (list_id);