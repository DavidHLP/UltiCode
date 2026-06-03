-- Seed Test Data: Users & Role Permissions
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Solutions 前置)
-- 维护指南: 修改测试用户 / 角色权限矩阵, 仅编辑本文件
--
-- 角色密码约定 (BCrypt cost=10):
--   USER         -> user123
--   MODERATOR    -> moderator123
--   ADMIN        -> admin123
--   SUPER_ADMIN  -> superadmin123
--
-- 注意: INSERT IGNORE 已存在用户不再覆盖 (密码保持首次插入的哈希)
-- ------------------------------------------------------------

-- ===== USER 角色 (2 核心测试账号 + 4 辅助账号) =====
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('user-alice-001', 'alice_coder', '李晓雯', 'alice@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=alice', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '算法爱好者，专注动态规划', 'USER', 1, 0, 0, '2026-01-15 09:00:00.000'),
('user-bob-002', 'bob_dev', '张志远', 'bob@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=bob', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'Java / Python 双修，热爱系统设计', 'USER', 1, 0, 0, '2026-01-18 10:30:00.000'),
('user-carol-003', 'carol_wu', '吴晓芳', 'carol@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=carol', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '竞赛党，ACMer', 'USER', 1, 0, 0, '2026-02-02 14:20:00.000'),
('user-david-004', 'david_chen', '陈大卫', 'david@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=david', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', '前端转全栈，喜欢写题解', 'USER', 1, 0, 0, '2026-02-10 08:45:00.000'),
('user-eva-005', 'eva_zhang', '张依凡', 'eva@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=eva', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'Python 达人，AI 初学者', 'USER', 1, 0, 0, '2026-02-20 16:10:00.000'),
('user-frank-006', 'frank_lee', '李明辉', 'frank@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=frank', '$2a$10$YT9BOzbnx2.oru1gP7LKNue2EVrJEvWt269.1AvhaN61jAaoyNrZy', 'C++ 手写高性能代码', 'USER', 1, 0, 0, '2026-03-01 11:00:00.000');

-- ===== MODERATOR 角色 (2 个) =====
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('mod-mike-001', 'mike_mod', '王明', 'mike.mod@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=mike', '$2a$10$yglnS/kVJtwMoHBzCXcd8eN7sWc1EFC6WGRd/GZldY6L2a/LDVDC.', '社区版主，专注审核代码与题解', 'MODERATOR', 1, 0, 0, '2026-03-10 09:00:00.000'),
('mod-nina-002', 'nina_mod', '林娜', 'nina.mod@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=nina', '$2a$10$yglnS/kVJtwMoHBzCXcd8eN7sWc1EFC6WGRd/GZldY6L2a/LDVDC.', '论坛巡逻员，维护社区秩序', 'MODERATOR', 1, 0, 0, '2026-03-12 10:30:00.000');

-- ===== ADMIN 角色 (补充 1 个, 搭配 V20260602_120100 admin 凑齐 2 个) =====
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('admin-002', 'admin_two', '管理员乙', 'admin2@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin2', '$2a$10$Ax8Il2dhdYO67A6DWE/STeWdhca3bq/96hJDad/0Z/kEWUE34SUjS', '二级管理员，协助日常运维', 'ADMIN', 1, 0, 0, '2026-01-05 08:00:00.000');

-- ===== SUPER_ADMIN 角色 (2 个) =====
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted, joined_at) VALUES
('super-root-001', 'super_root', '超级管理员甲', 'super1@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=super1', '$2a$10$Fu4e3G6O/gH4vNQewJvj5uhtwUm.lw3ZkA0/6OUQilMnxS4ULe6fW', '系统超级管理员，拥有全部权限', 'SUPER_ADMIN', 1, 0, 0, '2025-12-01 09:00:00.000'),
('super-vp-002', 'super_vp', '超级管理员乙', 'super2@ulticode.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=super2', '$2a$10$Fu4e3G6O/gH4vNQewJvj5uhtwUm.lw3ZkA0/6OUQilMnxS4ULe6fW', '运维负责人，审计与配置管理', 'SUPER_ADMIN', 1, 0, 0, '2025-12-15 14:00:00.000');

-- ===== 同步将已有 admin 账号对齐到与 admin-002 相同的密码哈希 =====
-- 便于测试登录 (V20260602_120100 中的 'admin' 用户原本使用旧哈希)
UPDATE `users`
SET `password` = '$2a$10$Ax8Il2dhdYO67A6DWE/STeWdhca3bq/96hJDad/0Z/kEWUE34SUjS'
WHERE `username` = 'admin' AND `role` = 'ADMIN';

-- ===== MODERATOR 角色权限 =====
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

-- ===== SUPER_ADMIN 角色权限 (全资源全动作) =====
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

-- Verify:
--   SELECT role, COUNT(*) FROM users GROUP BY role;
--   SELECT role, COUNT(DISTINCT resource) FROM role_permissions GROUP BY role;
