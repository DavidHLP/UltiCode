-- Seed Test Data: Problem Lists
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Problem Lists)
-- 维护指南: 修改 / 扩展 problem_lists 与题单-题目关联, 仅编辑本文件
--
-- 来源:  V20260531110000__Seed_ProblemLists_Data.sql
-- 特点:  使用 INSERT IGNORE 保证幂等, JOIN 过滤无效 problem_id
-- ------------------------------------------------------------

-- ---------------------------------------------------
-- 1. problem_lists 种子数据 (9 个公开/私有题单)
-- ---------------------------------------------------
INSERT IGNORE INTO problem_lists (id, name, description, author_id, is_public, created_at, updated_at, is_featured, banner_tag, banner_icon, banner_theme, banner_order, version) VALUES
('list-concurrency', '并发编程入门', '测试管理员更新', 'user-sara', 1, NOW(), NOW(), 1, '并发', 'Code2', 'emerald', 6, 1),
('list-database', '数据库专项训练', 'SQL 查询优化、索引设计、事务处理一网打尽。', 'user-david', 1, NOW(), NOW(), 1, '数据库', 'Database', 'sky', 5, 1),
('list-essentials', '必刷题单', '必知必会的算法模式，涵盖数组、哈希表、双指针等核心内容。', 'u-001', 1, NOW(), NOW(), 1, '基础', 'Trophy', 'amber', 1, 1),
('list-graph-advanced', '图论进阶', 'DFS/BFS 深入，最短路、网络流全覆盖。', 'user-chen', 1, NOW(), NOW(), 1, '图论', 'ArrowUpDown', 'slate', 7, 1),
('list-graph-dfs', '图 DFS/BFS 热身', '快速遍历练习，强化网格和图论直觉。', 'user-david', 1, NOW(), NOW(), 0, NULL, NULL, NULL, 0, 1),
('list-hard-bench', '难题基准', '精选难题，用于面试准备和竞赛训练。', 'user-petr', 0, NOW(), NOW(), 0, NULL, NULL, NULL, 0, 1),
('list-intervals', '区间与排序', '扫描线、合并区间、排序技巧，竞赛常见题型。', 'user-chen', 1, NOW(), NOW(), 1, '排序', 'ArrowUpDown', 'emerald', 3, 1),
('list-interview-100', '算法面试高频 100', '面试中最常出现的算法题，精选 100 道高效准备。', 'user-alex', 1, NOW(), NOW(), 1, '面试', 'Trophy', 'amber', 4, 1),
('list-sliding-window', '滑动窗口经典题', '固定窗口与可变窗口，字符串处理利器。', 'user-sara', 1, NOW(), NOW(), 1, '模式', 'Code2', 'sky', 2, 1);

-- ---------------------------------------------------
-- 2. problem_list_problem_relations 种子数据
-- 关联范围: 6 道保留题 (id=1, 2, 3, 4, 6, 7), 9 个题单
-- 通过子查询 JOIN 自动过滤不存在的 problem_id
-- ---------------------------------------------------
INSERT IGNORE INTO problem_list_problem_relations (list_id, problem_id, sort_order, added_at)
SELECT pl.id, p.id, plr.sort_order, plr.added_at
FROM (
  SELECT 'list-concurrency' AS id, 1 AS problem_id, 3 AS sort_order, NOW() AS added_at UNION ALL
  SELECT 'list-concurrency', 3, 4, NOW() UNION ALL
  SELECT 'list-concurrency', 4, 2, NOW() UNION ALL
  SELECT 'list-database', 6, 1, NOW() UNION ALL
  SELECT 'list-database', 7, 2, NOW() UNION ALL
  SELECT 'list-essentials', 1, 1, NOW() UNION ALL
  SELECT 'list-essentials', 4, 5, NOW() UNION ALL
  SELECT 'list-essentials', 6, 2, NOW() UNION ALL
  SELECT 'list-essentials', 7, 3, NOW() UNION ALL
  SELECT 'list-graph-advanced', 3, 2, NOW() UNION ALL
  SELECT 'list-graph-advanced', 4, 3, NOW() UNION ALL
  SELECT 'list-hard-bench', 3, 12, NOW() UNION ALL
  SELECT 'list-hard-bench', 4, 11, NOW() UNION ALL
  SELECT 'list-intervals', 1, 9, NOW() UNION ALL
  SELECT 'list-intervals', 3, 8, NOW() UNION ALL
  SELECT 'list-interview-100', 1, 1, NOW() UNION ALL
  SELECT 'list-interview-100', 2, 2, NOW() UNION ALL
  SELECT 'list-interview-100', 3, 3, NOW() UNION ALL
  SELECT 'list-sliding-window', 1, 6, NOW() UNION ALL
  SELECT 'list-sliding-window', 2, 7, NOW()
) AS plr
JOIN problem_lists pl ON pl.id = plr.id
JOIN problems p ON p.id = plr.problem_id;

-- Verify:
--   SELECT COUNT(*) FROM problem_list_problem_relations plr
--     LEFT JOIN problems p ON p.id = plr.problem_id WHERE p.id IS NULL;
--   期望: 0 (无孤儿引用)
