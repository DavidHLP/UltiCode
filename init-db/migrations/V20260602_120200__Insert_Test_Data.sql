-- V3: Insert Test Data (problems, audit logs, problem lists, solutions, forum user references)
--
-- ============================================================
-- ⚠️ 字符集说明
--   1. 容器化 MySQL 默认 `character_set_client=latin1`,若直接通过
--      `docker exec ... mysql -e "..."` 写入中文会出现双重 UTF-8 编码。
--   2. 正确做法:在 mysql 命令加 `--default-character-set=utf8mb4`
--      或先执行 `SET NAMES utf8mb4;`
--   3. 后端 JDBC URL 已包含 `useUnicode=true&characterEncoding=UTF-8`,
--      Spring Boot/Flyway 走应用连接时无需额外设置,字符正确。
--   4. 手工 SQL 修复示例:
--        docker exec ulticode-mysql mysql --default-character-set=utf8mb4 \
--          -u ulticode -p'CHANGE_ME_strong_password' ulticode
-- ============================================================

-- ===== Section: Problems =====

-- Insert test problems
-- Schema: problems (list summary) + problem_details (full content)
-- Note: problems.id is bigint, difficulty enum is 'Easy','Medium','Hard' (capitalized)

INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `created_at`, `updated_at`)
VALUES
(1, 'two-sum', '两数之和', 'Easy', 53.50, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(2, 'add-two-numbers', '两数相加', 'Medium', 41.20, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(3, 'longest-substring-without-repeating-characters', '无重复字符的最长子串', 'Medium', 38.80, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(4, 'median-of-two-sorted-arrays', '寻找两个正序数组的中位数', 'Hard', 35.50, 'todo', 0, 0, 1, NOW(3), NOW(3)),
(5, 'longest-palindromic-substring', '最长回文子串', 'Medium', 32.10, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(6, 'reverse-linked-list', '反转链表', 'Easy', 73.20, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(7, 'merge-k-sorted-lists', '合并K个升序链表', 'Hard', 28.40, 'todo', 0, 0, 1, NOW(3), NOW(3)),
(8, 'valid-parentheses', '有效的括号', 'Easy', 60.80, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(9, 'merge-two-sorted-lists', '合并两个有序链表', 'Easy', 65.30, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(10, '3sum', '三数之和', 'Medium', 30.50, 'todo', 0, 1, 1, NOW(3), NOW(3));

-- Insert problem details
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `constraints_json`, `hints`, `updated_at`)
VALUES
('pd-001', 1, 'two-sum', '在数组中找出和为目标值的两个整数',
'给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，并且不能使用同一个元素两次。\n\n你可以按任意顺序返回答案。',
'{"constraints": ["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9", "-10^9 <= target <= 10^9"]}',
'["考虑使用哈希表来减少时间复杂度"]', NOW(3)),

('pd-002', 2, 'add-two-numbers', '两个非负整数按逆序存储在链表中，求它们的和',
'给你两个非空的链表，表示两个非负的整数。它们每位数字都是按照逆序的方式存储的，并且每个节点只能存储一位数字。\n\n请你将两个数相加，并以相同形式返回一个表示和的链表。',
'{"constraints": ["每个链表中的节点数在范围 [1, 100] 内", "0 <= Node.val <= 9"]}',
'["注意处理进位", "链表长度不同时需要补零"]', NOW(3)),

('pd-003', 3, 'longest-substring-without-repeating-characters', '找出不含重复字符的最长子串的长度',
'给定一个字符串 s，请你找出其中不含有重复字符的最长子串的长度。',
'{"constraints": ["0 <= s.length <= 5 * 10^4", "s 由英文字母、数字、符号和空格组成"]}',
'["滑动窗口是经典解法"]', NOW(3)),

('pd-006', 6, 'reverse-linked-list', '反转单链表',
'给你单链表的头节点 head，请你反转链表，并返回反转后的链表。',
'{"constraints": ["链表中节点的数目范围是 [0, 5000]", "-5000 <= Node.val <= 5000"]}',
'["可以使用迭代或递归方法", "递归解法注意空间复杂度"]', NOW(3)),

('pd-007', 7, 'merge-k-sorted-lists', '合并K个升序链表',
'给你一个链表数组，每个链表都已经按升序排列。请你将所有链表合并到一个升序链表中，返回合并后的链表。',
'{"constraints": ["k == lists.length", "0 <= k <= 10^4", "0 <= lists[i].length <= 500"]}',
'["考虑使用最小堆优化", "也可以使用分治法"]', NOW(3));

-- ===== Section: Audit Logs =====

-- Migration:
--   V20260531001000__Insert_Test_Audit_Logs.sql
--
-- Purpose:
--   Insert test audit log data for PROBLEM entity to verify audit trail functionality.
--   Used for development and testing of audit log UI and API.
--
-- Risk:
--   Low. Test data only. Uses INSERT ON DUPLICATE KEY UPDATE for idempotency.
--
-- Compatibility:
--   Compatible. Test data does not affect production functionality.
--
-- Rollback:
--   DELETE FROM audit_logs WHERE id LIKE 'audit-log-%';
--
-- Verify:
--   SELECT COUNT(*) FROM audit_logs WHERE id LIKE 'audit-log-%';
--   Should return 8
--   SELECT id, action, entity_type, entity_id FROM audit_logs WHERE id LIKE 'audit-log-%';

-- Insert test audit logs for PROBLEM entity
-- Routes: /admin/audit/log?entityType=PROBLEM&entityId=1
-- Note: Assuming admin user exists with id 'admin-001' (from Insert_Admin_User migration)

-- Audit log 1: Problem creation
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-001', 'admin-001', NULL, 'CREATE', 'PROBLEM', '1', NULL, '{"title":"两数之和","difficulty":"Easy","status":"draft","isPublished":false}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-25 10:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 2: Problem published
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-002', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"isPublished":false,"status":"draft"}', '{"isPublished":true,"status":"published"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-25 10:05:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 3: Problem details updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-003', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"title":"两数之和"}', '{"title":"两数之和（已更新）"}', '192.168.1.100', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', '2026-05-26 14:30:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 4: Difficulty changed
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-004', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"difficulty":"Easy"}', '{"difficulty":"Medium"}', '192.168.1.101', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', '2026-05-27 09:15:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 5: Content moderated - approved
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-005', 'admin-001', NULL, 'MODERATE_APPROVE', 'PROBLEM', '1', '{"moderationStatus":"pending","moderationMessage":null}', '{"moderationStatus":"approved","moderationMessage":"所有检查已通过"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-28 11:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 6: Tags updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-006', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"tags":["array"],"hasSolution":false}', '{"tags":["array","hash-table"],"hasSolution":true}', '192.168.1.100', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', '2026-05-28 15:45:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 7: Constraints updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-007', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"constraints":"2 <= nums.length <= 10^4","difficultyRating":1200}', '{"constraints":"2 <= nums.length <= 10^5","difficultyRating":1350}', '192.168.1.102', 'Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X) AppleWebKit/605.1.15', '2026-05-29 08:30:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 8: Status changed to solved
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-008', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"status":"published","acceptanceRate":49.2}', '{"status":"solved","acceptanceRate":52.8}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-30 10:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- ===== Section: Problem Lists =====

-- ============================================================
-- V20260531110000__Seed_ProblemLists_Data.sql
-- 导入题单种子数据，确保所有新环境初始化后就有数据
-- 注意：使用 INSERT IGNORE 避免重复插入
-- 外键约束自动过滤无效 problem_id（problems 表中不存在的 ID）
-- ============================================================

-- ---------------------------------------------------
-- 1. problem_lists 种子数据
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
-- 通过子查询 JOIN 确保只插入有效的 (list_id, problem_id) 组合
-- 只有 problems 表中存在的 problem_id 才会被插入
-- ---------------------------------------------------
INSERT IGNORE INTO problem_list_problem_relations (list_id, problem_id, sort_order, added_at)
SELECT pl.id, p.id, plr.sort_order, plr.added_at
FROM (
  SELECT 'list-concurrency' AS id, 1 AS problem_id, 3 AS sort_order, NOW() AS added_at UNION ALL
  SELECT 'list-concurrency', 3, 4, NOW() UNION ALL
  SELECT 'list-concurrency', 4, 2, NOW() UNION ALL
  SELECT 'list-concurrency', 8, 1, NOW() UNION ALL
  SELECT 'list-database', 6, 1, NOW() UNION ALL
  SELECT 'list-database', 7, 2, NOW() UNION ALL
  SELECT 'list-database', 8, 3, NOW() UNION ALL
  SELECT 'list-essentials', 1, 1, NOW() UNION ALL
  SELECT 'list-essentials', 4, 5, NOW() UNION ALL
  SELECT 'list-essentials', 6, 2, NOW() UNION ALL
  SELECT 'list-essentials', 7, 3, NOW() UNION ALL
  SELECT 'list-essentials', 8, 4, NOW() UNION ALL
  SELECT 'list-graph-advanced', 3, 2, NOW() UNION ALL
  SELECT 'list-graph-advanced', 4, 3, NOW() UNION ALL
  SELECT 'list-graph-advanced', 5, 1, NOW() UNION ALL
  SELECT 'list-graph-dfs', 5, 10, NOW() UNION ALL
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

-- ===== Section: Solutions =====

-- ============================================================
-- V20260601000000__Seed_Solutions_Test_Data.sql
-- 为 solutions 页面设计合理的测试数据
-- 注意：使用 INSERT IGNORE 避免重复插入
-- ============================================================

-- ---------------------------------------------------
-- 1. 先确保有足够的测试用户（user_id 需要引用）
-- 说明：所有用户密码统一为各角色对应的可登录密码
--   USER         -> user123
--   MODERATOR    -> moderator123
--   ADMIN        -> admin123
--   SUPER_ADMIN  -> superadmin123
-- BCrypt cost=10，与项目现有 admin 账号保持一致
-- 注意：INSERT IGNORE 已存在用户不再覆盖（密码保持首次插入的哈希）
-- ---------------------------------------------------

-- ===== USER 角色 (2 个核心测试账号 + 4 个被 solutions 引用的辅助账号) =====
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('user-alice-001', 'alice_coder', '李晓雯', 'alice@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=alice', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '算法爱好者，专注动态规划', 'USER', 1, 0, 0, '2026-01-15 09:00:00.000'),
('user-bob-002', 'bob_dev', '张志远', 'bob@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=bob', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'Java / Python 双修，热爱系统设计', 'USER', 1, 0, 0, '2026-01-18 10:30:00.000'),
('user-carol-003', 'carol_wu', '吴晓芳', 'carol@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=carol', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '竞赛党，ACMer', 'USER', 1, 0, 0, '2026-02-02 14:20:00.000'),
('user-david-004', 'david_chen', '陈大卫', 'david@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=david', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '前端转全栈，喜欢写题解', 'USER', 1, 0, 0, '2026-02-10 08:45:00.000'),
('user-eva-005', 'eva_zhang', '张依凡', 'eva@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=eva', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'Python 达人，AI 初学者', 'USER', 1, 0, 0, '2026-02-20 16:10:00.000'),
('user-frank-006', 'frank_lee', '李明辉', 'frank@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=frank', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'C++ 手写高性能代码', 'USER', 1, 0, 0, '2026-03-01 11:00:00.000');

-- ===== MODERATOR 角色 (2 个测试账号) =====
-- 密码: moderator123
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('mod-mike-001', 'mike_mod', '王明', 'mike.mod@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=mike', '$2a$10$yglnS/kVJtwMoHBzCXcd8eN7sWc1EFC6WGRd/GZldY6L2a/LDVDC.', '社区版主，专注审核代码与题解', 'MODERATOR', 1, 0, 0, '2026-03-10 09:00:00.000'),
('mod-nina-002', 'nina_mod', '林娜', 'nina.mod@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=nina', '$2a$10$yglnS/kVJtwMoHBzCXcd8eN7sWc1EFC6WGRd/GZldY6L2a/LDVDC.', '论坛巡逻员，维护社区秩序', 'MODERATOR', 1, 0, 0, '2026-03-12 10:30:00.000');

-- ===== ADMIN 角色 (补充 1 个，搭配 V20260602_120100 中的 admin 账号凑齐 2 个) =====
-- 密码: admin123
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('admin-002', 'admin_two', '管理员乙', 'admin2@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin2', '$2a$10$Ax8Il2dhdYO67A6DWE/STeWdhca3bq/96hJDad/0Z/kEWUE34SUjS', '二级管理员，协助日常运维', 'ADMIN', 1, 0, 0, '2026-01-05 08:00:00.000');

-- ===== SUPER_ADMIN 角色 (2 个测试账号) =====
-- 密码: superadmin123
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('super-root-001', 'super_root', '超级管理员甲', 'super1@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=super1', '$2a$10$Fu4e3G6O/gH4vNQewJvj5uhtwUm.lw3ZkA0/6OUQilMnxS4ULe6fW', '系统超级管理员，拥有全部权限', 'SUPER_ADMIN', 1, 0, 0, '2025-12-01 09:00:00.000'),
('super-vp-002', 'super_vp', '超级管理员乙', 'super2@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=super2', '$2a$10$Fu4e3G6O/gH4vNQewJvj5uhtwUm.lw3ZkA0/6OUQilMnxS4ULe6fW', '运维负责人，审计与配置管理', 'SUPER_ADMIN', 1, 0, 0, '2025-12-15 14:00:00.000');

-- ===== 同步将已有 admin 账号对齐到与 admin-002 相同的密码哈希 =====
-- 避免在 V20260602_120100 中插入的 'admin' 用户依然使用旧哈希，便于测试登录
UPDATE `users`
SET `password` = '$2a$10$Ax8Il2dhdYO67A6DWE/STeWdhca3bq/96hJDad/0Z/kEWUE34SUjS'
WHERE `username` = 'admin' AND `role` = 'ADMIN';

-- ===== 角色权限授予 =====
-- MODERATOR: 拥有内容审核与论坛管理权限
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`)
SELECT UUID(), 'MODERATOR', a.action, r.resource
FROM (
    SELECT 'READ' AS action UNION ALL
    SELECT 'MODERATE' UNION ALL
    SELECT 'PUBLISH'
) a
CROSS JOIN (
    SELECT 'USER' AS resource UNION ALL
    SELECT 'PROBLEM' UNION ALL
    SELECT 'SOLUTION' UNION ALL
    SELECT 'FORUM_POST' UNION ALL
    SELECT 'FORUM_COMMENT' UNION ALL
    SELECT 'MODERATION' UNION ALL
    SELECT 'REPORT'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `role_permissions` rp
    WHERE rp.role = 'MODERATOR' AND rp.action = a.action AND rp.resource = r.resource
);

-- SUPER_ADMIN: 拥有所有资源的所有权限（涵盖 ADMIN 的全部范围 + 审计/系统/备份/账单等）
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`)
SELECT UUID(), 'SUPER_ADMIN', a.action, r.resource
FROM (
    SELECT 'CREATE' AS action UNION ALL
    SELECT 'READ' UNION ALL
    SELECT 'UPDATE' UNION ALL
    SELECT 'DELETE' UNION ALL
    SELECT 'MODERATE' UNION ALL
    SELECT 'PUBLISH' UNION ALL
    SELECT 'MANAGE_USERS' UNION ALL
    SELECT 'MANAGE_PERMISSIONS'
) a
CROSS JOIN (
    SELECT 'USER' AS resource UNION ALL
    SELECT 'PROBLEM' UNION ALL
    SELECT 'SUBMISSION' UNION ALL
    SELECT 'CONTEST' UNION ALL
    SELECT 'FORUM_POST' UNION ALL
    SELECT 'FORUM_COMMENT' UNION ALL
    SELECT 'SOLUTION' UNION ALL
    SELECT 'SOLUTION_COMMENT' UNION ALL
    SELECT 'PROBLEM_LIST' UNION ALL
    SELECT 'ROLE' UNION ALL
    SELECT 'PERMISSION' UNION ALL
    SELECT 'NOTIFICATION' UNION ALL
    SELECT 'ACHIEVEMENT' UNION ALL
    SELECT 'BILLING' UNION ALL
    SELECT 'SYSTEM' UNION ALL
    SELECT 'DASHBOARD' UNION ALL
    SELECT 'MODERATION' UNION ALL
    SELECT 'BACKUP' UNION ALL
    SELECT 'AUDIT_LOG' UNION ALL
    SELECT 'REPORT' UNION ALL
    SELECT 'SEARCH' UNION ALL
    SELECT 'TAG' UNION ALL
    SELECT 'BOOKMARK' UNION ALL
    SELECT 'FOLLOW' UNION ALL
    SELECT 'VOTE' UNION ALL
    SELECT 'EMAIL' UNION ALL
    SELECT 'QUEUE' UNION ALL
    SELECT 'RECOMMENDATION'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `role_permissions` rp
    WHERE rp.role = 'SUPER_ADMIN' AND rp.action = a.action AND rp.resource = r.resource
);

-- ---------------------------------------------------
-- 2. 插入 solutions 测试数据
-- 注意：problems 表只有 id=1 存在，其他 problem_id 使用 1
-- ---------------------------------------------------
INSERT IGNORE INTO solutions (id, problem_id, user_id, title, content, summary, language, tags, views, likes, dislikes, comment_count, is_published, published_at, published_by, is_flagged, flagged_reason, flagged_at, is_deleted, is_pinned, created_at, updated_at) VALUES

-- 正常发布的 solution（多种语言）
('sol-001', 1, 'user-alice-001', '两数之和 双指针解法', '# 两数之和 双指针解法\n\n## 思路\n\n使用双指针从数组两端向中间收缩，找到目标和。\n\n## 代码\n\n```python\ndef two_sum(nums, target):\n    left, right = 0, len(nums) - 1\n    while left < right:\n        current_sum = nums[left] + nums[right]\n        if current_sum == target:\n            return [left, right]\n        elif current_sum < target:\n            left += 1\n        else:\n            right -= 1\n    return []\n```\n\n## 复杂度\n- 时间：O(n)\n- 空间：O(1)\n\n## 注意事项\n双指针只适用于有序数组，若无序需先排序。', '详细的 Python 双指针解法，包含思路、代码、复杂度分析和注意事项', 'python', '["双指针", "数组"]', 1250, 45, 2, 3, 1, NOW(), 'user-alice-001', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-002', 1, 'user-bob-002', 'Java 哈希表解法', 'package com.ulticode.solutions;\n\nimport java.util.*;\n\npublic class TwoSum {\n    public int[] solution(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}', 'Java HashMap 解法，简洁高效', 'java', '["哈希表"]', 890, 32, 1, 2, 1, NOW(), 'user-bob-002', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-003', 1, 'user-carol-003', '链表相加算法详细图解', '# 链表相加算法\n\n## 问题描述\n\n给定两个非空链表，表示两个非负整数。每位数字都是反向存储的，每个节点包含一个数字。将这两个数相加返回一个新的链表。\n\n## 图解\n\n假设输入：\n- L1: 2 -> 4 -> 3\n- L2: 5 -> 6 -> 4\n\n结果： 7 -> 0 -> 8\n\n## 代码实现\n\n```python\nclass ListNode:\n    def __init__(self, val=0, next=None):\n        self.val = val\n        self.next = next\n\ndef add_two_numbers(l1, l2):\n    dummy = ListNode(0)\n    current, carry = dummy, 0\n    \n    while l1 or l2 or carry:\n        val1 = l1.val if l1 else 0\n        val2 = l2.val if l2 else 0\n        \n        total = val1 + val2 + carry\n        carry = total // 10\n        current.next = ListNode(total % 10)\n        \n        current = current.next\n        l1 = l1.next if l1 else None\n        l2 = l2.next if l2 else None\n    \n    return dummy.next\n```\n\n## 复杂度\n时间 O(max(m,n))，空间 O(max(m,n))', '详细的链表相加算法，包含图解、Python 实现和复杂度分析', 'python', '["链表", "数学"]', 2100, 78, 5, 8, 1, NOW(), 'user-carol-003', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-004', 1, 'user-david-004', '滑动窗口求最长无重复子串', '# 滑动窗口解法\n\n```javascript\nfunction lengthOfLongestSubstring(s) {\n    const charSet = new Set();\n    let left = 0, result = 0;\n    \n    for (let right = 0; right < s.length; right++) {\n        while (charSet.has(s[right])) {\n            charSet.delete(s[left]);\n            left++;\n        }\n        charSet.add(s[right]);\n        result = Math.max(result, right - left + 1);\n    }\n    return result;\n}\n```\n\n关键点：使用 Set 存储窗口内的字符，遇到重复字符时收缩左边界。', 'JavaScript 滑动窗口解法，简洁易懂', 'javascript', '["滑动窗口", "哈希表", "字符串"]', 567, 21, 0, 1, 1, NOW(), 'user-david-004', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-005', 1, 'user-frank-006', '中位数二分查找 - 详解', '// C++ 二分查找解法\n// 时间复杂度 O(log(m+n))\n\nclass Solution {\npublic:\n    double findMedianSortedArrays(vector<int>& nums1, vector<int>& nums2) {\n        int m = nums1.size(), n = nums2.size();\n        if (m > n) return findMedianSortedArrays(nums2, nums1);\n        \n        int left = 0, right = m;\n        int maxLeft = 0, minRight = 0;\n        \n        while (left <= right) {\n            int i = (left + right) / 2;\n            int j = (m + n + 1) / 2 - i;\n            \n            int nums1Left = (i == 0) ? INT_MIN : nums1[i-1];\n            int nums1Right = (i == m) ? INT_MAX : nums1[i];\n            int nums2Left = (j == 0) ? INT_MIN : nums2[j-1];\n            int nums2Right = (j == n) ? INT_MAX : nums2[j];\n            \n            if (nums1Left <= nums2Right && nums2Left <= nums1Right) {\n                if ((m + n) % 2 == 0) {\n                    return (max(nums1Left, nums2Left) + min(nums1Right, nums2Right)) / 2.0;\n                } else {\n                    return max(nums1Left, nums2Left);\n                }\n            } else if (nums1Left > nums2Right) {\n                right = i - 1;\n            } else {\n                left = i + 1;\n            }\n        }\n        return 0.0;\n    }\n};', 'C++ 二分查找，详细注释，适合学习', 'cpp', '["二分查找", "分治"]', 320, 15, 1, 0, 1, NOW(), 'user-frank-006', 0, NULL, NULL, 0, 0, NOW(), NOW()),

-- 被标记的 solution（待审核）
('sol-006', 1, 'user-eva-005', '两数之和 暴力枚举', '// 暴力解法 - 不推荐\n// 时间复杂度 O(n^2)\n\nclass Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        for (int i = 0; i < nums.size(); i++) {\n            for (int j = i + 1; j < nums.size(); j++) {\n                if (nums[i] + nums[j] == target) {\n                    return {i, j};\n                }\n            }\n        }\n        return {};\n    }\n};', '暴力解法，仅作为对比参考', 'cpp', '["暴力枚举"]', 156, 3, 12, 1, 1, NOW(), 'user-eva-005', 1, '低效解法，无实际参考价值，且未提供优化思路', NOW(), 0, 0, NOW(), NOW()),

('sol-007', 1, 'user-eva-005', '链表反转后相加', '// 错误的实现 - 不要使用\n// 这个解法有 bug，请参考 sol-003 的正确版本', '错误实现，仅供警示', 'python', '["链表"]', 89, 1, 8, 0, 1, NOW(), 'user-eva-005', 1, '代码存在边界条件 bug，运行时会出错', NOW(), 0, 0, NOW(), NOW()),

-- 未发布的 solution（草稿）
('sol-008', 1, 'user-alice-001', '二分查找初步思路', '# 待完成\n\n还在思考如何处理边界情况...', '还在整理思路', 'python', '["二分"]', 12, 0, 0, 0, 0, NULL, NULL, 0, NULL, NULL, 0, 0, NOW(), NOW()),

-- 已删除的 solution（回收站）
('sol-009', 1, 'user-bob-002', '滑动窗口旧版', '// 旧版本，已被新方案取代', '旧版，待删除', 'javascript', '["滑动窗口"]', 45, 2, 0, 0, 0, NULL, NULL, 0, NULL, NULL, 1, 0, NOW(), NOW()),

-- 置顶的 solution（优质内容）
('sol-010', 1, 'user-carol-003', '【精选】两数之和 最优解法汇总', '# 精选题解 - 两数之和\n\n本文整理了三种最优解法：\n\n1. **哈希表** - O(n) 时间\n2. **双指针** - O(nlogn) 时间（需排序）\n3. **暴力枚举** - O(n²) 不推荐\n\n## 哈希表解法（推荐）\n\n```python\ndef two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []\n```\n\n## 为什么哈希表最快？\n\n哈希表查找 O(1)，整体 O(n)。空间换时间的经典案例。', '精选优质题解，包含多种解法对比', 'python', '["哈希表", "双指针", "精选"]', 4500, 156, 8, 25, 1, NOW(), 'user-carol-003', 0, NULL, NULL, 0, 1, NOW(), NOW()),

-- 其他语言的 solution
('sol-011', 1, 'user-frank-006', '动态规划详解', '# 最长回文子串 - 动态规划\n\n```python\ndef longest_palindrome(s: str) -> str:\n    n = len(s)\n    if n < 2:\n        return s\n    \n    start, max_len = 0, 1\n    dp = [[False] * n for _ in range(n)]\n    \n    for i in range(n):\n        dp[i][i] = True\n    \n    for length in range(2, n + 1):\n        for i in range(n - length + 1):\n            j = i + length - 1\n            if length == 2:\n                dp[i][j] = (s[i] == s[j])\n            else:\n                dp[i][j] = (s[i] == s[j] and dp[i+1][j-1])\n            \n            if dp[i][j] and length > max_len:\n                start, max_len = i, length\n    \n    return s[start:start + max_len]\n```', '动态规划方法，详细注释', 'python', '["动态规划", "字符串"]', 780, 34, 2, 5, 1, NOW(), 'user-frank-006', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-012', 1, 'user-david-004', 'Z字形变换 Go 实现', 'package main\n\nimport (\n    "strings"\n)\n\nfunc convert(s string, numRows int) string {\n    if numRows == 1 {\n        return s\n    }\n    \n    rows := make([]string, numRows)\n    currentRow := 0\n    goingDown := false\n    \n    for _, c := range s {\n        rows[currentRow] += string(c)\n        if currentRow == 0 || currentRow == numRows-1 {\n            goingDown = !goingDown\n        }\n        if goingDown {\n            currentRow++\n        } else {\n            currentRow--\n        }\n    }\n    \n    return strings.Join(rows, "")\n}', 'Go 语言实现 Z 字形变换', 'go', '["字符串"]', 234, 12, 0, 2, 1, NOW(), 'user-david-004', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-013', 1, 'user-alice-001', '整数反转 C 语言版', '// C 语言版整数反转\n// 注意溢出检测\n\n#include <limits.h>\n\nint reverse(int x) {\n    int rev = 0;\n    while (x != 0) {\n        int pop = x % 10;\n        x /= 10;\n        \n        if (rev > INT_MAX/10 || (rev == INT_MAX/10 && pop > 7)) return 0;\n        if (rev < INT_MIN/10 || (rev == INT_MIN/10 && pop < -8)) return 0;\n        \n        rev = rev * 10 + pop;\n    }\n    return rev;\n}', 'C 语言版，注意溢出处理', 'c', '["数学"]', 189, 8, 1, 1, 1, NOW(), 'user-alice-001', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-014', 1, 'user-frank-006', '链表相加算法 Go 版本', 'package solution\n\ntype ListNode struct {\n    Val int\n    Next *ListNode\n}\n\nfunc addTwoNumbers(l1, l2 *ListNode) *ListNode {\n    head := &ListNode{}\n    current, carry := head, 0\n    \n    for l1 != nil || l2 != nil || carry != 0 {\n        val := carry\n        if l1 != nil {\n            val += l1.Val\n            l1 = l1.Next\n        }\n        if l2 != nil {\n            val += l2.Val\n            l2 = l2.Next\n        }\n        \n        carry = val / 10\n        current.Next = &ListNode{Val: val % 10}\n        current = current.Next\n    }\n    \n    return head.Next\n}', 'Go 语言版链表相加算法', 'go', '["链表", "数学"]', 445, 18, 1, 3, 1, NOW(), 'user-frank-006', 0, NULL, NULL, 0, 0, NOW(), NOW()),

('sol-015', 1, 'user-carol-003', '滑动窗口 Rust 实现', 'fn length_of_longest_substring(s: String) -> i32 {\n    let mut char_set: std::collections::HashSet<char> = std::collections::HashSet::new();\n    let mut left = 0;\n    let mut result = 0;\n    \n    for (right, c) in s.char_indices() {\n        while char_set.contains(&c) {\n            char_set.remove(&s.chars().nth(left).unwrap());\n            left += 1;\n        }\n        char_set.insert(c);\n        result = result.max(right - left + 1);\n    }\n    \n    result as i32\n}', 'Rust 实现滑动窗口', 'rust', '["滑动窗口", "哈希表"]', 156, 9, 0, 1, 1, NOW(), 'user-carol-003', 0, NULL, NULL, 0, 0, NOW(), NOW());

-- ===== Section: Forum User References =====

-- Fix Forum User References
-- forum_users only has: id, username, avatar, karma
-- forum_comments uses 'author_id' not 'user_id'
-- No user_id column in forum_users to fix

-- Step 1: Update forum_comments - fix author_id references
UPDATE `forum_comments` SET `author_id` = 'admin-001' WHERE `author_id` = 'u-admin-001';

-- Step 2: Update forum_community_members - fix user_id references
UPDATE `forum_community_members` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';

-- Step 3: Update forum_posts - fix user_id references
UPDATE `forum_posts` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';
