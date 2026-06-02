-- ============================================================
-- V20260602050000__Seed_Solution_Comments_Test_Data.sql
-- Solution 评论测试数据 — 与真实用户强关联
-- 覆盖：多用户、嵌套回复、flagged、deleted 状态
-- ============================================================

-- 用户映射 (与 forum_comments 一致):
--   admin-001    → admin (SUPER_ADMIN)
--   user-alice-001 → alice_coder (Alice Johnson)
--   user-bob-002   → bob_dev (Bob Smith)
--   user-carol-003 → carol_wu (Carol Wu)
--   user-david-004 → david_chen (David Chen)
--   user-eva-005   → eva_zhang (Eva Zhang)
--   user-frank-006 → frank_lee (Frank Lee)

INSERT IGNORE INTO solution_comments (id, solution_id, parent_id, user_id, content, created_at, updated_at, is_flagged, flagged_reason, flagged_at, is_deleted, deleted_at, deleted_by) VALUES

-- ▸ sol-001 (Alice 的 Two Sum Python 解法) 下的评论
('sc-001', 'sol-001', NULL,        'user-bob-002',   '很干净的解法！哈希表方法的时间复杂度是 O(n)，已经是最优了。', DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-002', 'sol-001', 'sc-001',    'user-alice-001', '谢谢！我花了不少时间优化。关键思路是在插入当前元素之前先检查补数是否已经存在。', DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-003', 'sol-001', NULL,        'user-david-004', '边界情况比如重复值怎么办？比如 [3, 3] 目标是 6，能处理吗？', DATE_SUB(NOW(3), INTERVAL 9 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-004', 'sol-001', 'sc-003',    'user-alice-001', '好眼力！这个解法可以正确处理，因为我们在插入之前先检查补数。所以当遇到第二个 3 时，第一个 3 已经在哈希表中了。', DATE_SUB(NOW(3), INTERVAL 9 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-002 (Bob 的 Java 解法) 下的评论
('sc-005', 'sol-002', NULL,        'user-carol-003', 'Java 实现写得不错！如果只有少量映射关系，可以考虑用 HashMap.of() 初始化，代码会更简洁。', DATE_SUB(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-006', 'sol-002', 'sc-005',    'user-bob-002',   '你是指 Map.of() 吧？那个只有 Java 9+ 才支持，不过确实在少量映射场景下更简洁。', DATE_SUB(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-007', 'sol-002', NULL,        'user-eva-005',   '这跟 Python 版本几乎一模一样。有没有更符合 Java 惯用写法的解法？', DATE_SUB(NOW(3), INTERVAL 7 DAY), DATE_SUB(NOW(3), INTERVAL 7 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-003 (Carol 的 Python 解法) 下的评论
('sc-008', 'sol-003', NULL,        'user-frank-006', '很喜欢你用 enumerate() 的写法，非常 Pythonic！', DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-009', 'sol-003', 'sc-008',    'user-carol-003', '谢谢！Python 的内置函数确实很强大，解这类题方便多了。', DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-005 (Frank 的 C++ 解法) 下的评论
('sc-010', 'sol-005', NULL,        'user-alice-001', 'C++ 的 unordered_map 在这题上性能很好。你有和排序数组+双指针方法做过性能对比吗？', DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-011', 'sol-005', 'sc-010',    'user-frank-006', '做过对比！对于无序输入，哈希表方法始终更快。双指针在数组已排序时才占优势。', DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-006 (Eva 的 C++ 解法) 下的评论
('sc-012', 'sol-006', NULL,        'user-bob-002',   '这个变量命名风格挺有意思。代码可读性非常好。', DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ flagged 评论 (2 条)
('sc-013', 'sol-001', NULL,        'user-frank-006', '这个解法是错的！大输入肯定会失败！！！', DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY), 1, '无根据的断言，缺乏证据，可能误导他人', DATE_SUB(NOW(3), INTERVAL 3 DAY), 0, NULL, NULL),
('sc-014', 'sol-002', NULL,        'user-david-004', '直接从 LeetCode 讨论板复制粘贴的，不是原创。', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 1, '指控缺乏证据，标记待审核', DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL),

-- ▸ deleted 评论 (1 条)
('sc-015', 'sol-003', NULL,        'user-frank-006', '[已被管理员删除]', DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY), 1, '垃圾信息 / 推广内容', DATE_SUB(NOW(3), INTERVAL 4 DAY), 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), 'admin-001'),

-- ▸ 最近 24 小时的评论 (3 条)
('sc-016', 'sol-004', NULL,        'user-alice-001', 'JavaScript 的 Map 解这道题特别优雅，解构赋值更是加分。', DATE_SUB(NOW(3), INTERVAL 6 HOUR), DATE_SUB(NOW(3), INTERVAL 6 HOUR), 0, NULL, NULL, 0, NULL, NULL),
('sc-017', 'sol-009', NULL,        'user-carol-003', '你的 JavaScript 解法函数式风格很赞。有没有试过用 reduce() 代替 forEach()？', DATE_SUB(NOW(3), INTERVAL 3 HOUR), DATE_SUB(NOW(3), INTERVAL 3 HOUR), 0, NULL, NULL, 0, NULL, NULL),
('sc-018', 'sol-012', NULL,        'user-eva-005',   'Go 实现很扎实！错误处理非常符合 Go 的惯用写法。', DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_SUB(NOW(3), INTERVAL 1 HOUR), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ 嵌套回复链 (3 层)
('sc-019', 'sol-010', NULL,        'user-david-004', '用生成器的思路挺有意思，不过无解的情况能处理吗？', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-020', 'sol-010', 'sc-019',    'user-carol-003', '可以的，那种情况会返回 None。我在函数末尾加了检查。', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-021', 'sol-010', 'sc-020',    'user-david-004', '完美，边界情况就覆盖了。感谢补充说明！', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL);