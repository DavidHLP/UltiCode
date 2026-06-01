-- ============================================================
-- V20260602050000__Seed_Solution_Comments_Test_Data.sql
-- Solution 评论测试数据 — 与真实用户强关联
-- 覆盖：多用户、嵌套回复、flagged、deleted 状态
-- ============================================================

-- 用户映射 (与 forum_comments 一致):
--   u-admin-001    → admin (SUPER_ADMIN)
--   user-alice-001 → alice_coder (Alice Johnson)
--   user-bob-002   → bob_dev (Bob Smith)
--   user-carol-003 → carol_wu (Carol Wu)
--   user-david-004 → david_chen (David Chen)
--   user-eva-005   → eva_zhang (Eva Zhang)
--   user-frank-006 → frank_lee (Frank Lee)

INSERT IGNORE INTO solution_comments (id, solution_id, parent_id, user_id, content, created_at, updated_at, is_flagged, flagged_reason, flagged_at, is_deleted, deleted_at, deleted_by) VALUES

-- ▸ sol-001 (Alice 的 Two Sum Python 解法) 下的评论
('sc-001', 'sol-001', NULL,        'user-bob-002',   'Clean solution! The hash map approach gives O(n) time complexity which is optimal.', DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-002', 'sol-001', 'sc-001',    'user-alice-001', 'Thanks! I spent quite a while optimizing it. The key insight is checking for the complement before adding the current element.', DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-003', 'sol-001', NULL,        'user-david-004', 'What about edge cases like duplicate values? Does this handle [3, 3] with target 6?', DATE_SUB(NOW(3), INTERVAL 9 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-004', 'sol-001', 'sc-003',    'user-alice-001', 'Good catch! The solution handles it correctly because we check complement before inserting. So when we see the second 3, the first 3 is already in the map.', DATE_SUB(NOW(3), INTERVAL 9 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-002 (Bob 的 Java 解法) 下的评论
('sc-005', 'sol-002', NULL,        'user-carol-003', 'Nice Java implementation! Consider using HashMap.of() for cleaner initialization if you only have a few entries.', DATE_SUB(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-006', 'sol-002', 'sc-005',    'user-bob-002',   'You mean Map.of()? That''s Java 9+ only, but yes it''s cleaner for small maps.', DATE_SUB(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-007', 'sol-002', NULL,        'user-eva-005',   'This is almost identical to the Python version. Is there a more Java-idiomatic way to solve this?', DATE_SUB(NOW(3), INTERVAL 7 DAY), DATE_SUB(NOW(3), INTERVAL 7 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-003 (Carol 的 Python 解法) 下的评论
('sc-008', 'sol-003', NULL,        'user-frank-006', 'I like how you used enumerate() here. Very Pythonic!', DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-009', 'sol-003', 'sc-008',    'user-carol-003', 'Thanks Frank! Python has great built-in functions that make these problems much easier.', DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-005 (Frank 的 C++ 解法) 下的评论
('sc-010', 'sol-005', NULL,        'user-alice-001', 'C++ unordered_map performance is great for this. Have you benchmarked it against the sorted array + two pointers approach?', DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-011', 'sol-005', 'sc-010',    'user-frank-006', 'I did! The hash map approach is consistently faster for unsorted input. Two pointers wins when the array is already sorted.', DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ sol-006 (Eva 的 C++ 解法) 下的评论
('sc-012', 'sol-006', NULL,        'user-bob-002',   'Interesting approach using a different variable naming convention. The code is very readable.', DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ flagged 评论 (2 条)
('sc-013', 'sol-001', NULL,        'user-frank-006', 'This solution is wrong! It will fail on large inputs!!!', DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY), 1, 'Unsubstantiated claim without evidence - potentially misleading', DATE_SUB(NOW(3), INTERVAL 3 DAY), 0, NULL, NULL),
('sc-014', 'sol-002', NULL,        'user-david-004', 'Just copy-pasted from LeetCode discussion board, not original work.', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 1, 'Accusation without proof - flagged for review', DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL),

-- ▸ deleted 评论 (1 条)
('sc-015', 'sol-003', NULL,        'user-frank-006', '[Removed by moderator]', DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY), 1, 'Spam / promotional content', DATE_SUB(NOW(3), INTERVAL 4 DAY), 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), 'u-admin-001'),

-- ▸ 最近 24 小时的评论 (3 条)
('sc-016', 'sol-004', NULL,        'user-alice-001', 'JavaScript Map is so clean for this problem. The destructuring assignment makes it even nicer.', DATE_SUB(NOW(3), INTERVAL 6 HOUR), DATE_SUB(NOW(3), INTERVAL 6 HOUR), 0, NULL, NULL, 0, NULL, NULL),
('sc-017', 'sol-009', NULL,        'user-carol-003', 'Your JavaScript solution uses a nice functional style. Have you tried using reduce() instead of forEach()?', DATE_SUB(NOW(3), INTERVAL 3 HOUR), DATE_SUB(NOW(3), INTERVAL 3 HOUR), 0, NULL, NULL, 0, NULL, NULL),
('sc-018', 'sol-012', NULL,        'user-eva-005',   'Go implementation looks solid! The error handling is very Go-idiomatic.', DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_SUB(NOW(3), INTERVAL 1 HOUR), 0, NULL, NULL, 0, NULL, NULL),

-- ▸ 嵌套回复链 (3 层)
('sc-019', 'sol-010', NULL,        'user-david-004', 'This approach using a generator is interesting but does it handle the case where no solution exists?', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-020', 'sol-010', 'sc-019',    'user-carol-003', 'Yes, it returns None in that case. I added a check at the end of the function.', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL),
('sc-021', 'sol-010', 'sc-020',    'user-david-004', 'Perfect, that covers the edge case. Thanks for clarifying!', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL, 0, NULL, NULL);
