-- ============================================================
-- Seed Test Data: Global Rankings
-- ============================================================
-- 维护指南: 修改 global_rankings 测试数据时, 仅编辑本文件
--
-- 设计原则: 基于竞赛结果生成全球排名数据
--   数据来源: contest_rankings (FINISHED 比赛)
--   排名计算: 综合 rating 变化 + 参赛次数
--
-- 关联真实数据:
--   用户: alice_coder, bob_dev, carol_wu, david_chen, eva_zhang, frank_lee
--   比赛: contest-finished-001 (春季邀请赛), contest-finished-002 (新手入门赛)
--
-- 排名逻辑:
--   carol_wu:  春季邀请赛第1名 (+120 rating) -> 全球第1
--   alice_coder: 新手赛第1名 (+40), 春季邀请赛第2名 (+60) -> 全球第2
--   bob_dev:   春季邀请赛第3名 (+40) -> 全球第3
--   frank_lee: 春季邀请赛第4名 (-20) -> 全球第4
--   david_chen: 春季邀请赛第5名 (-40) -> 全球第5
--   eva_zhang: 春季邀请赛第6名 (-60) -> 全球第6
--
-- 字符集说明: 后端 JDBC URL 已包含 useUnicode=true&characterEncoding=UTF-8,
--   Flyway 走应用连接字符正常; 若手动 docker exec mysql 写入中文,
--   必须加 --default-character-set=utf8mb4
-- ============================================================

SET NAMES utf8mb4;

-- 幂等清理: 先删除已有测试数据
DELETE FROM `global_rankings` WHERE `user_id` LIKE 'user-%';

-- ============================================================
-- 1. global_rankings (全球排名)
-- ============================================================
INSERT INTO `global_rankings` (
  `id`, `user_id`, `username`, `global_rank`, `rating`, `max_rating`,
  `contests_attended`, `avatar`, `country`, `badge`, `contests_rated`,
  `last_contest_id`, `max_rating_title`, `rating_title`, `updated_at`
) VALUES

-- ---- 第1名: carol_wu (吴晓芳) ----
-- 春季邀请赛第1名: 1500 -> 1620 (+120)
('gr-carol-001', 'user-carol-003', 'carol_wu', 1, 1620, 1620,
  2, 'https://api.dicebear.com/7.x/avataaars/svg?seed=carol', 'CN', '🏆', 2,
  'contest-finished-001', 'SPECIALIST', 'SPECIALIST', NOW(3)),

-- ---- 第2名: alice_coder (李晓雯) ----
-- 春季邀请赛第2名: 1500 -> 1560 (+60)
-- 新手入门赛第1名: 1560 -> 1600 (+40)
('gr-alice-001', 'user-alice-001', 'alice_coder', 2, 1600, 1600,
  2, 'https://api.dicebear.com/7.x/avataaars/svg?seed=alice', 'CN', '🥈', 2,
  'contest-finished-002', 'SPECIALIST', 'SPECIALIST', NOW(3)),

-- ---- 第3名: bob_dev (张志远) ----
-- 春季邀请赛第3名: 1500 -> 1540 (+40)
('gr-bob-001', 'user-bob-002', 'bob_dev', 3, 1540, 1540,
  2, 'https://api.dicebear.com/7.x/avataaars/svg?seed=bob', 'CN', '🥉', 2,
  'contest-finished-001', 'SPECIALIST', 'SPECIALIST', NOW(3)),

-- ---- 第4名: frank_lee (李明辉) ----
-- 春季邀请赛第4名: 1500 -> 1480 (-20)
('gr-frank-001', 'user-frank-006', 'frank_lee', 4, 1480, 1500,
  1, 'https://api.dicebear.com/7.x/avataaars/svg?seed=frank', 'CN', NULL, 1,
  'contest-finished-001', 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第5名: david_chen (陈大卫) ----
-- 春季邀请赛第5名: 1500 -> 1460 (-40)
('gr-david-001', 'user-david-004', 'david_chen', 5, 1460, 1500,
  2, 'https://api.dicebear.com/7.x/avataaars/svg?seed=david', 'CN', NULL, 2,
  'contest-finished-001', 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第6名: eva_zhang (张依凡) ----
-- 春季邀请赛第6名: 1500 -> 1440 (-60)
('gr-eva-001', 'user-eva-005', 'eva_zhang', 6, 1440, 1500,
  2, 'https://api.dicebear.com/7.x/avataaars/svg?seed=eva', 'CN', NULL, 2,
  'contest-finished-001', 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第7名: mike_mod (王明) - 版主, 未参赛但有排名基础 ----
('gr-mike-001', 'mod-mike-001', 'mike_mod', 7, 1500, 1500,
  0, 'https://api.dicebear.com/7.x/avataaars/svg?seed=mike', 'CN', NULL, 0,
  NULL, 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第8名: nina_mod (林娜) - 版主, 未参赛但有排名基础 ----
('gr-nina-001', 'mod-nina-002', 'nina_mod', 8, 1500, 1500,
  0, 'https://api.dicebear.com/7.x/avataaars/svg?seed=nina', 'CN', NULL, 0,
  NULL, 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第9名: admin_two (管理员乙) - 未参赛 ----
('gr-admin2-001', 'admin-002', 'admin_two', 9, 1500, 1500,
  0, 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin2', 'CN', NULL, 0,
  NULL, 'NEWBIE', 'NEWBIE', NOW(3)),

-- ---- 第10名: super_root (超级管理员甲) - 未参赛 ----
('gr-super1-001', 'super-root-001', 'super_root', 10, 1500, 1500,
  0, 'https://api.dicebear.com/7.x/avataaars/svg?seed=super1', 'CN', NULL, 0,
  NULL, 'NEWBIE', 'NEWBIE', NOW(3));


-- ============================================================
-- Verify:
--   SELECT global_rank, username, rating, contests_attended
--   FROM global_rankings ORDER BY global_rank;
--   期望: 10 条记录, carol_wu 第1名 (1620), alice_coder 第2名 (1600)
--
--   SELECT rating_title, COUNT(*) FROM global_rankings GROUP BY rating_title;
--   期望: SPECIALIST=3, NEWBIE=7
-- ============================================================
