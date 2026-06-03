-- Fix Forum User References
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Forum User References)
-- 维护指南: 仅当 forum 表结构或 user id 命名变更时, 评估是否需要重跑本脚本
--
-- 历史问题: forum_users 仅含 (id, username, avatar, karma), 没有 user_id 列
--           forum_comments 使用 author_id, forum_community_members / forum_posts 使用 user_id
-- 修复: 将早期使用的 'u-admin-001' 统一更新为 'admin-001'
-- ------------------------------------------------------------

-- Step 1: Update forum_comments - fix author_id references
UPDATE `forum_comments` SET `author_id` = 'admin-001' WHERE `author_id` = 'u-admin-001';

-- Step 2: Update forum_community_members - fix user_id references
UPDATE `forum_community_members` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';

-- Step 3: Update forum_posts - fix user_id references
UPDATE `forum_posts` SET `user_id` = 'admin-001' WHERE `user_id` = 'u-admin-001';

-- Verify:
--   SELECT 'forum_comments' AS tbl, COUNT(*) AS need_fix FROM forum_comments WHERE author_id = 'u-admin-001'
--   UNION ALL SELECT 'forum_community_members', COUNT(*) FROM forum_community_members WHERE user_id = 'u-admin-001'
--   UNION ALL SELECT 'forum_posts', COUNT(*) FROM forum_posts WHERE user_id = 'u-admin-001';
--   期望: 三行 count 均为 0
