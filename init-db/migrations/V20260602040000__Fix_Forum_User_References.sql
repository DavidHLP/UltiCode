-- ============================================================
-- V20260602040000__Fix_Forum_User_References.sql
-- 修复论坛测试数据：将 forum_users ID 映射到真实 users 表 ID
-- 使论坛数据与系统用户强关联
-- ============================================================

-- 映射关系:
--   fu-admin-001   → u-admin-001     (admin, SUPER_ADMIN)
--   fu-alice-001   → user-alice-001  (alice_coder, Alice Johnson)
--   fu-bob-002     → user-bob-002    (bob_dev, Bob Smith)
--   fu-carol-003   → user-carol-003  (carol_wu, Carol Wu)
--   fu-david-004   → user-david-004  (david_chen, David Chen)
--   fu-eva-005     → user-eva-005    (eva_zhang, Eva Zhang)
--   fu-frank-006   → user-frank-006  (frank_lee, Frank Lee)

-- 临时禁用 FK 检查，避免循环依赖 (forum_posts/forum_comments → forum_users)
SET FOREIGN_KEY_CHECKS = 0;

-- ────────────────────────────────────────────────────────────
-- 1. 更新 forum_community_members.user_id
-- ────────────────────────────────────────────────────────────
UPDATE forum_community_members SET user_id = 'u-admin-001'    WHERE user_id = 'fu-admin-001';
UPDATE forum_community_members SET user_id = 'user-alice-001' WHERE user_id = 'fu-alice-001';
UPDATE forum_community_members SET user_id = 'user-bob-002'   WHERE user_id = 'fu-bob-002';
UPDATE forum_community_members SET user_id = 'user-carol-003' WHERE user_id = 'fu-carol-003';
UPDATE forum_community_members SET user_id = 'user-david-004' WHERE user_id = 'fu-david-004';
UPDATE forum_community_members SET user_id = 'user-eva-005'   WHERE user_id = 'fu-eva-005';
UPDATE forum_community_members SET user_id = 'user-frank-006' WHERE user_id = 'fu-frank-006';

-- ────────────────────────────────────────────────────────────
-- 2. 更新 forum_comments.author_id 和 deleted_by
-- ────────────────────────────────────────────────────────────
UPDATE forum_comments SET author_id = 'u-admin-001'    WHERE author_id = 'fu-admin-001';
UPDATE forum_comments SET author_id = 'user-alice-001' WHERE author_id = 'fu-alice-001';
UPDATE forum_comments SET author_id = 'user-bob-002'   WHERE author_id = 'fu-bob-002';
UPDATE forum_comments SET author_id = 'user-carol-003' WHERE author_id = 'fu-carol-003';
UPDATE forum_comments SET author_id = 'user-david-004' WHERE author_id = 'fu-david-004';
UPDATE forum_comments SET author_id = 'user-eva-005'   WHERE author_id = 'fu-eva-005';
UPDATE forum_comments SET author_id = 'user-frank-006' WHERE author_id = 'fu-frank-006';
UPDATE forum_comments SET deleted_by = 'u-admin-001' WHERE deleted_by = 'fu-admin-001';

-- ────────────────────────────────────────────────────────────
-- 3. 更新 forum_posts.user_id 和 deleted_by
-- ────────────────────────────────────────────────────────────
UPDATE forum_posts SET deleted_by = 'u-admin-001' WHERE deleted_by = 'fu-admin-001';
UPDATE forum_posts SET user_id = 'u-admin-001'    WHERE user_id = 'fu-admin-001';
UPDATE forum_posts SET user_id = 'user-alice-001' WHERE user_id = 'fu-alice-001';
UPDATE forum_posts SET user_id = 'user-bob-002'   WHERE user_id = 'fu-bob-002';
UPDATE forum_posts SET user_id = 'user-carol-003' WHERE user_id = 'fu-carol-003';
UPDATE forum_posts SET user_id = 'user-david-004' WHERE user_id = 'fu-david-004';
UPDATE forum_posts SET user_id = 'user-eva-005'   WHERE user_id = 'fu-eva-005';
UPDATE forum_posts SET user_id = 'user-frank-006' WHERE user_id = 'fu-frank-006';

-- ────────────────────────────────────────────────────────────
-- 4. 重建 forum_users：删除旧数据，插入真实用户映射
-- ────────────────────────────────────────────────────────────
DELETE FROM forum_users;

INSERT INTO forum_users (id, username, avatar, karma) VALUES
('u-admin-001',    'admin',       'https://api.dicebear.com/7.x/shapes/svg?seed=admin',      1250),
('user-alice-001', 'alice_coder', 'https://api.dicebear.com/7.x/avataaars/svg?seed=alice',    890),
('user-bob-002',   'bob_dev',     'https://api.dicebear.com/7.x/avataaars/svg?seed=bob',      720),
('user-carol-003', 'carol_wu',    'https://api.dicebear.com/7.x/avataaars/svg?seed=carol',    650),
('user-david-004', 'david_chen',  'https://api.dicebear.com/7.x/avataaars/svg?seed=david',    430),
('user-eva-005',   'eva_zhang',   'https://api.dicebear.com/7.x/avataaars/svg?seed=eva',      310),
('user-frank-006', 'frank_lee',   'https://api.dicebear.com/7.x/avataaars/svg?seed=frank',    180);

SET FOREIGN_KEY_CHECKS = 1;
