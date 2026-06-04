-- ============================================================
-- Seed Test Data: Contests
-- ============================================================
-- 维护指南: 修改 contests / contest_problems / contest_participants
--          / contest_rankings / contest_problem_results 测试数据时, 仅编辑本文件
--
-- 设计原则: 5 场比赛, 覆盖三种状态
--   UPCOMING  : 2 场 (id=contest-upcoming-001/002) — start_time 在 NOW() 之后
--   RUNNING   : 1 场 (id=contest-running-001)     — NOW() 在 [start_time, end_time] 之间
--   FINISHED  : 2 场 (id=contest-finished-001/002) — end_time 在 NOW() 之前
--
-- 关联真实数据:
--   题目: id=1(两数之和),2(两数相加),3(无重复字符的最长子串),
--         4(寻找两个正序数组的中位数),6(反转链表),7(合并K个升序链表)
--   用户: alice_coder, bob_dev, carol_wu, david_chen, eva_zhang, frank_lee,
--         mike_mod, admin, admin_two
--
-- 时间设计: 所有时间基于迁移执行时的 NOW(), 使用 DATE_ADD/DATE_SUB 计算,
--   确保无论何时执行迁移, 状态始终正确
--
-- 字符集说明: 后端 JDBC URL 已包含 useUnicode=true&characterEncoding=UTF-8,
--   Flyway 走应用连接字符正常; 若手动 docker exec mysql 写入中文,
--   必须加 --default-character-set=utf8mb4
-- ============================================================

SET NAMES utf8mb4;

-- 幂等清理: 先删除关联表再删除主表
DELETE FROM `contest_problem_results` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_rankings` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_participants` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_problems` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_announcements` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_submissions` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contest_analytics` WHERE `contest_id` LIKE 'contest-%';
DELETE FROM `contests` WHERE `id` LIKE 'contest-%';

-- ============================================================
-- 1. contests (比赛主表)
-- ============================================================
INSERT INTO `contests` (
  `id`, `title`, `slug`, `contest_type`, `start_time`, `duration_minutes`,
  `status`, `penalty_per_wrong`, `scoring_mode`, `tie_breaker`,
  `registered_count`, `participant_count`, `is_rated`, `description`,
  `created_by`, `is_visible`, `rules`, `created_at`, `updated_at`,
  `is_deleted`, `end_time`, `registration_start`, `registration_end`
) VALUES

-- ==================== FINISHED 比赛 (2 场) ====================

-- 1) 已结束: UltiCode 春季邀请赛 (ICPC 赛制, 已结束 7 天)
--    start_time = NOW() - 10天, duration=180分钟, end_time = start + 180分钟
(
  'contest-finished-001',
  'UltiCode 春季邀请赛',
  'ulticode-spring-invitational',
  'ICPC',
  DATE_SUB(NOW(3), INTERVAL 10 DAY),         -- start_time: 10天前
  180,                                         -- 3小时
  'FINISHED',
  300,                                         -- 每次错误 300秒罚时
  'ICPC',
  'LAST_SOLVE_TIME',
  6,                                           -- 6人报名
  6,                                           -- 6人参加
  1,                                           -- 计分
  'UltiCode 平台首届春季邀请赛，涵盖数组、链表、字符串等经典算法题型。ICPC 赛制，按解题数排名，同解数按罚时排名。',
  'super-root-001',                            -- 超级管理员创建
  1,
  '1. ICPC 赛制：按解题数排名，同解数按总罚时排名\n2. 每次错误提交增加 300 秒罚时\n3. 允许使用 C++、Java、Python\n4. 禁止使用外部资料和AI辅助',
  DATE_SUB(NOW(3), INTERVAL 15 DAY),          -- created_at: 15天前
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 180 MINUTE),  -- updated_at: 比赛结束时
  0,
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 180 MINUTE),  -- end_time
  DATE_SUB(NOW(3), INTERVAL 15 DAY),          -- 报名开始: 15天前
  DATE_SUB(NOW(3), INTERVAL 11 DAY)           -- 报名结束: 11天前
),

-- 2) 已结束: 新手入门赛 (IOI 赛制, 已结束 3 天)
--    start_time = NOW() - 4天, duration=120分钟
(
  'contest-finished-002',
  '新手入门赛 Vol.1',
  'beginner-contest-vol1',
  'IOI',
  DATE_SUB(NOW(3), INTERVAL 4 DAY),           -- start_time: 4天前
  120,                                         -- 2小时
  'FINISHED',
  0,                                           -- IOI 赛制无罚时
  'IOI',
  'NONE',
  5,                                           -- 5人报名
  5,                                           -- 5人参加
  0,                                           -- 不计分
  '面向初学者的入门级比赛，题目难度较低，适合刚接触算法竞赛的同学。IOI 赛制，按总分排名。',
  'admin-002',                                 -- 管理员乙创建
  1,
  '1. IOI 赛制：按总分排名，无罚时\n2. 题目难度为 Easy ~ Medium\n3. 每题可多次提交，取最高分\n4. 鼓励新手参与',
  DATE_SUB(NOW(3), INTERVAL 8 DAY),           -- created_at: 8天前
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 120 MINUTE),
  0,
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 120 MINUTE),
  DATE_SUB(NOW(3), INTERVAL 8 DAY),
  DATE_SUB(NOW(3), INTERVAL 5 DAY)
),

-- ==================== RUNNING 比赛 (1 场) ====================

-- 3) 进行中: UltiCode 周赛 #42 (ICPC 赛制, 已开始 1 小时)
--    start_time = NOW() - 1小时, duration=150分钟
(
  'contest-running-001',
  'UltiCode 周赛 #42',
  'ulticode-weekly-42',
  'ICPC',
  DATE_SUB(NOW(3), INTERVAL 60 MINUTE),       -- start_time: 1小时前
  150,                                         -- 2.5小时
  'RUNNING',
  300,
  'ICPC',
  'LAST_SOLVE_TIME',
  6,                                           -- 6人报名
  4,                                           -- 4人已开始
  1,                                           -- 计分
  'UltiCode 每周编程竞赛第 42 期，包含动态规划、图论等进阶题型。欢迎各位选手挑战！',
  'super-root-001',
  1,
  '1. ICPC 赛制：按解题数排名\n2. 每次错误提交增加 300 秒罚时\n3. 比赛期间可查看排名（无封榜）\n4. 比赛结束后公布题解',
  DATE_SUB(NOW(3), INTERVAL 3 DAY),           -- created_at: 3天前
  NOW(3),
  0,
  DATE_ADD(NOW(3), INTERVAL 90 MINUTE),       -- end_time: 90分钟后 (150-60)
  DATE_SUB(NOW(3), INTERVAL 3 DAY),
  DATE_SUB(NOW(3), INTERVAL 30 MINUTE)        -- 报名截止: 30分钟前
),

-- ==================== UPCOMING 比赛 (2 场) ====================

-- 4) 即将开始: 算法马拉松 (CUSTOM 赛制, 2 天后开始)
--    start_time = NOW() + 2天, duration=300分钟
(
  'contest-upcoming-001',
  '算法马拉松 2026',
  'algorithm-marathon-2026',
  'CUSTOM',
  DATE_ADD(NOW(3), INTERVAL 2 DAY),           -- start_time: 2天后
  300,                                         -- 5小时
  'UPCOMING',
  300,
  'SCORE',
  'TOTAL_ATTEMPTS',
  3,                                           -- 3人已报名
  0,                                           -- 未开始
  1,                                           -- 计分
  '年度算法马拉松，5 小时长赛制，包含从 Easy 到 Hard 的多种题型。按积分排名，时间越快奖励越高。奖金：前三名获得平台高级会员。',
  'super-root-001',
  1,
  '1. 积分赛制：每题有基础分 + 时间奖励\n2. 每次错误提交扣 5 分\n3. 最快解题额外奖励 10 分\n4. 按总分排名，同分按提交次数\n5. 比赛期间封榜，最后一小时解封',
  DATE_SUB(NOW(3), INTERVAL 5 DAY),
  NOW(3),
  0,
  DATE_ADD(DATE_ADD(NOW(3), INTERVAL 2 DAY), INTERVAL 300 MINUTE),  -- end_time
  DATE_SUB(NOW(3), INTERVAL 5 DAY),           -- 报名开始
  DATE_ADD(NOW(3), INTERVAL 2 DAY)            -- 报名截止: 比赛开始时
),

-- 5) 即将开始: 链表专题赛 (ICPC 赛制, 5 天后开始)
--    start_time = NOW() + 5天, duration=90分钟
(
  'contest-upcoming-002',
  '链表专题赛',
  'linked-list-special',
  'ICPC',
  DATE_ADD(NOW(3), INTERVAL 5 DAY),           -- start_time: 5天后
  90,                                          -- 1.5小时
  'UPCOMING',
  300,
  'ICPC',
  'LAST_SOLVE_TIME',
  2,                                           -- 2人已报名
  0,
  0,                                           -- 不计分
  '链表专题训练赛，专注链表相关算法：反转、合并、快慢指针等。适合巩固链表基础，为进阶竞赛做准备。',
  'admin-002',
  1,
  '1. ICPC 赛制：按解题数排名\n2. 每次错误提交增加 300 秒罚时\n3. 题目均为链表相关\n4. 不影响 Rating',
  DATE_SUB(NOW(3), INTERVAL 2 DAY),
  NOW(3),
  0,
  DATE_ADD(DATE_ADD(NOW(3), INTERVAL 5 DAY), INTERVAL 90 MINUTE),
  DATE_SUB(NOW(3), INTERVAL 2 DAY),
  DATE_ADD(NOW(3), INTERVAL 5 DAY)
);


-- ============================================================
-- 2. contest_problems (比赛关联题目)
-- ============================================================
INSERT INTO `contest_problems` (
  `id`, `contest_id`, `problem_id`, `problem_index`, `score`,
  `penalty_per_wrong`, `solved_count`, `submission_count`, `label`,
  `base_score`, `time_bonus`, `created_at`, `updated_at`
) VALUES

-- ---- contest-finished-001: 春季邀请赛 (6题, A~F) ----
('cp-f1-A', 'contest-finished-001', 1, 'A', 100, 300, 5, 12, 'A', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),
('cp-f1-B', 'contest-finished-001', 2, 'B', 100, 300, 3, 15, 'B', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),
('cp-f1-C', 'contest-finished-001', 3, 'C', 100, 300, 2, 10, 'C', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),
('cp-f1-D', 'contest-finished-001', 4, 'D', 100, 300, 1,  8, 'D', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),
('cp-f1-E', 'contest-finished-001', 6, 'E', 100, 300, 4,  9, 'E', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),
('cp-f1-F', 'contest-finished-001', 7, 'F', 100, 300, 0,  6, 'F', 100, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), NOW(3)),

-- ---- contest-finished-002: 新手入门赛 (3题, A~C) ----
('cp-f2-A', 'contest-finished-002', 1, 'A', 100, 0, 5, 8,  'A', 100, 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), NOW(3)),
('cp-f2-B', 'contest-finished-002', 6, 'B', 150, 0, 4, 10, 'B', 150, 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), NOW(3)),
('cp-f2-C', 'contest-finished-002', 3, 'C', 200, 0, 2, 7,  'C', 200, 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), NOW(3)),

-- ---- contest-running-001: 周赛 #42 (4题, A~D) ----
('cp-r1-A', 'contest-running-001', 1, 'A', 100, 300, 3, 7,  'A', 100, 1, DATE_SUB(NOW(3), INTERVAL 60 MINUTE), NOW(3)),
('cp-r1-B', 'contest-running-001', 3, 'B', 100, 300, 1, 5,  'B', 100, 1, DATE_SUB(NOW(3), INTERVAL 60 MINUTE), NOW(3)),
('cp-r1-C', 'contest-running-001', 2, 'C', 100, 300, 0, 3,  'C', 100, 1, DATE_SUB(NOW(3), INTERVAL 60 MINUTE), NOW(3)),
('cp-r1-D', 'contest-running-001', 7, 'D', 100, 300, 0, 2,  'D', 100, 1, DATE_SUB(NOW(3), INTERVAL 60 MINUTE), NOW(3)),

-- ---- contest-upcoming-001: 算法马拉松 (6题, A~F) ----
('cp-u1-A', 'contest-upcoming-001', 1, 'A', 100, 5, 0, 0, 'A', 100, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),
('cp-u1-B', 'contest-upcoming-001', 2, 'B', 150, 5, 0, 0, 'B', 150, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),
('cp-u1-C', 'contest-upcoming-001', 3, 'C', 200, 5, 0, 0, 'C', 200, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),
('cp-u1-D', 'contest-upcoming-001', 4, 'D', 250, 5, 0, 0, 'D', 250, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),
('cp-u1-E', 'contest-upcoming-001', 6, 'E', 150, 5, 0, 0, 'E', 150, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),
('cp-u1-F', 'contest-upcoming-001', 7, 'F', 300, 5, 0, 0, 'F', 300, 2, DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),

-- ---- contest-upcoming-002: 链表专题赛 (3题, A~C) ----
('cp-u2-A', 'contest-upcoming-002', 6, 'A', 100, 300, 0, 0, 'A', 100, 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW(3)),
('cp-u2-B', 'contest-upcoming-002', 2, 'B', 100, 300, 0, 0, 'B', 100, 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW(3)),
('cp-u2-C', 'contest-upcoming-002', 7, 'C', 100, 300, 0, 0, 'C', 100, 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW(3));


-- ============================================================
-- 3. contest_participants (参赛者)
-- ============================================================
INSERT INTO `contest_participants` (
  `id`, `contest_id`, `user_id`, `status`, `registered_at`,
  `started_at`, `finished_at`, `is_virtual`, `final_rank`,
  `total_penalty`, `total_score`, `total_attempts`, `last_solve_time`,
  `total_time`, `attempt_count`, `created_at`, `updated_at`
) VALUES

-- ---- contest-finished-001: 春季邀请赛 (6人参加) ----
-- 排名: carol(4题) > alice(3题) > bob(3题) > frank(2题) > david(2题) > eva(1题)
('pt-f1-01', 'contest-finished-001', 'user-carol-003', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 12 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 165 MINUTE),
  0, 1, 1800, 400, 8, 9900, 165, 8,
  DATE_SUB(NOW(3), INTERVAL 12 DAY), NOW(3)),

('pt-f1-02', 'contest-finished-001', 'user-alice-001', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 13 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 150 MINUTE),
  0, 2, 2700, 300, 9, 9000, 150, 9,
  DATE_SUB(NOW(3), INTERVAL 13 DAY), NOW(3)),

('pt-f1-03', 'contest-finished-001', 'user-bob-002', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 11 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 155 MINUTE),
  0, 3, 3600, 300, 11, 9300, 155, 11,
  DATE_SUB(NOW(3), INTERVAL 11 DAY), NOW(3)),

('pt-f1-04', 'contest-finished-001', 'user-frank-006', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 12 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 140 MINUTE),
  0, 4, 1200, 200, 6, 8400, 140, 6,
  DATE_SUB(NOW(3), INTERVAL 12 DAY), NOW(3)),

('pt-f1-05', 'contest-finished-001', 'user-david-004', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 14 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 175 MINUTE),
  0, 5, 3300, 200, 10, 10500, 175, 10,
  DATE_SUB(NOW(3), INTERVAL 14 DAY), NOW(3)),

('pt-f1-06', 'contest-finished-001', 'user-eva-005', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 11 DAY),
  DATE_SUB(NOW(3), INTERVAL 10 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 120 MINUTE),
  0, 6, 900, 100, 4, 7200, 120, 4,
  DATE_SUB(NOW(3), INTERVAL 11 DAY), NOW(3)),

-- ---- contest-finished-002: 新手入门赛 (5人参加) ----
-- 排名: alice(满分解3题) > carol(2题) > david(2题) > eva(2题) > bob(1题)
('pt-f2-01', 'contest-finished-002', 'user-alice-001', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 6 DAY),
  DATE_SUB(NOW(3), INTERVAL 4 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 90 MINUTE),
  0, 1, 0, 450, 5, 5400, 90, 5,
  DATE_SUB(NOW(3), INTERVAL 6 DAY), NOW(3)),

('pt-f2-02', 'contest-finished-002', 'user-carol-003', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 7 DAY),
  DATE_SUB(NOW(3), INTERVAL 4 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 80 MINUTE),
  0, 2, 0, 250, 4, 4800, 80, 4,
  DATE_SUB(NOW(3), INTERVAL 7 DAY), NOW(3)),

('pt-f2-03', 'contest-finished-002', 'user-david-004', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 5 DAY),
  DATE_SUB(NOW(3), INTERVAL 4 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 95 MINUTE),
  0, 3, 0, 200, 6, 5700, 95, 6,
  DATE_SUB(NOW(3), INTERVAL 5 DAY), NOW(3)),

('pt-f2-04', 'contest-finished-002', 'user-eva-005', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 6 DAY),
  DATE_SUB(NOW(3), INTERVAL 4 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 100 MINUTE),
  0, 4, 0, 180, 5, 6000, 100, 5,
  DATE_SUB(NOW(3), INTERVAL 6 DAY), NOW(3)),

('pt-f2-05', 'contest-finished-002', 'user-bob-002', 'FINISHED',
  DATE_SUB(NOW(3), INTERVAL 7 DAY),
  DATE_SUB(NOW(3), INTERVAL 4 DAY),
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 70 MINUTE),
  0, 5, 0, 100, 3, 4200, 70, 3,
  DATE_SUB(NOW(3), INTERVAL 7 DAY), NOW(3)),

-- ---- contest-running-001: 周赛 #42 (6人报名, 4人已开始) ----
('pt-r1-01', 'contest-running-001', 'user-carol-003', 'STARTED',
  DATE_SUB(NOW(3), INTERVAL 2 DAY),
  DATE_SUB(NOW(3), INTERVAL 55 MINUTE),
  NULL, 0, NULL, 900, 200, 4, 3300, 55, 4,
  DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW(3)),

('pt-r1-02', 'contest-running-001', 'user-alice-001', 'STARTED',
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  DATE_SUB(NOW(3), INTERVAL 50 MINUTE),
  NULL, 0, NULL, 600, 100, 3, 3000, 50, 3,
  DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW(3)),

('pt-r1-03', 'contest-running-001', 'user-frank-006', 'STARTED',
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  DATE_SUB(NOW(3), INTERVAL 45 MINUTE),
  NULL, 0, NULL, 300, 100, 2, 2700, 45, 2,
  DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW(3)),

('pt-r1-04', 'contest-running-001', 'user-bob-002', 'STARTED',
  DATE_SUB(NOW(3), INTERVAL 2 DAY),
  DATE_SUB(NOW(3), INTERVAL 40 MINUTE),
  NULL, 0, NULL, 0, 0, 1, 2400, 40, 1,
  DATE_SUB(NOW(3), INTERVAL 2 DAY), NOW(3)),

('pt-r1-05', 'contest-running-001', 'user-david-004', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW(3)),

('pt-r1-06', 'contest-running-001', 'user-eva-005', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 3 HOUR),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 3 HOUR), NOW(3)),

-- ---- contest-upcoming-001: 算法马拉松 (3人报名) ----
('pt-u1-01', 'contest-upcoming-001', 'user-carol-003', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 3 DAY),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 3 DAY), NOW(3)),

('pt-u1-02', 'contest-upcoming-001', 'user-alice-001', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW(3)),

('pt-u1-03', 'contest-upcoming-001', 'user-bob-002', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 5 HOUR),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 5 HOUR), NOW(3)),

-- ---- contest-upcoming-002: 链表专题赛 (2人报名) ----
('pt-u2-01', 'contest-upcoming-002', 'user-david-004', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 1 DAY), NOW(3)),

('pt-u2-02', 'contest-upcoming-002', 'user-eva-005', 'REGISTERED',
  DATE_SUB(NOW(3), INTERVAL 12 HOUR),
  NULL, NULL, 0, NULL, 0, 0, 0, NULL, 0, 0,
  DATE_SUB(NOW(3), INTERVAL 12 HOUR), NOW(3));


-- ============================================================
-- 4. contest_rankings (排名 — 仅 FINISHED 比赛)
-- ============================================================
INSERT INTO `contest_rankings` (
  `id`, `contest_id`, `user_id`, `rank`, `rating_before`, `rating_after`,
  `rating_change`, `is_virtual`, `solved_count`, `total_penalty`,
  `total_score`, `finish_time`, `total_attempts`, `problem_stats_snapshot`, `is_frozen`
) VALUES

-- ---- contest-finished-001: 春季邀请赛排名 ----
('rk-f1-01', 'contest-finished-001', 'user-carol-003', 1, 1500, 1620, 120, 0, 4, 1800, 400, 9900, 8,
  '{"A":{"solved":true,"attempts":1,"time":600},"B":{"solved":true,"attempts":2,"time":2400},"E":{"solved":true,"attempts":2,"time":5700},"C":{"solved":true,"attempts":3,"time":9900},"D":{"solved":false},"F":{"solved":false}}',
  0),

('rk-f1-02', 'contest-finished-001', 'user-alice-001', 2, 1500, 1560, 60, 0, 3, 2700, 300, 9000, 9,
  '{"A":{"solved":true,"attempts":1,"time":300},"E":{"solved":true,"attempts":1,"time":3600},"B":{"solved":true,"attempts":3,"time":9000},"C":{"solved":false},"D":{"solved":false},"F":{"solved":false}}',
  0),

('rk-f1-03', 'contest-finished-001', 'user-bob-002', 3, 1500, 1540, 40, 0, 3, 3600, 300, 9300, 11,
  '{"A":{"solved":true,"attempts":2,"time":900},"E":{"solved":true,"attempts":1,"time":4200},"C":{"solved":true,"attempts":4,"time":9300},"B":{"solved":false},"D":{"solved":false},"F":{"solved":false}}',
  0),

('rk-f1-04', 'contest-finished-001', 'user-frank-006', 4, 1500, 1480, -20, 0, 2, 1200, 200, 8400, 6,
  '{"A":{"solved":true,"attempts":1,"time":450},"E":{"solved":true,"attempts":2,"time":8400},"B":{"solved":false},"C":{"solved":false},"D":{"solved":false},"F":{"solved":false}}',
  0),

('rk-f1-05', 'contest-finished-001', 'user-david-004', 5, 1500, 1460, -40, 0, 2, 3300, 200, 10500, 10,
  '{"A":{"solved":true,"attempts":2,"time":1200},"E":{"solved":true,"attempts":3,"time":10500},"B":{"solved":false},"C":{"solved":false},"D":{"solved":false},"F":{"solved":false}}',
  0),

('rk-f1-06', 'contest-finished-001', 'user-eva-005', 6, 1500, 1440, -60, 0, 1, 900, 100, 7200, 4,
  '{"A":{"solved":true,"attempts":1,"time":900},"B":{"solved":false},"C":{"solved":false},"D":{"solved":false},"E":{"solved":false},"F":{"solved":false}}',
  0),

-- ---- contest-finished-002: 新手入门赛排名 ----
('rk-f2-01', 'contest-finished-002', 'user-alice-001', 1, 1560, 1600, 40, 0, 3, 0, 450, 5400, 5,
  '{"A":{"solved":true,"attempts":1,"score":100},"B":{"solved":true,"attempts":2,"score":150},"C":{"solved":true,"attempts":2,"score":200}}',
  0),

('rk-f2-02', 'contest-finished-002', 'user-carol-003', 2, 1620, 1630, 10, 0, 2, 0, 250, 4800, 4,
  '{"A":{"solved":true,"attempts":1,"score":100},"B":{"solved":true,"attempts":2,"score":150},"C":{"solved":false}}',
  0),

('rk-f2-03', 'contest-finished-002', 'user-david-004', 3, 1460, 1460, 0, 0, 2, 0, 200, 5700, 6,
  '{"A":{"solved":true,"attempts":2,"score":100},"B":{"solved":true,"attempts":3,"score":100},"C":{"solved":false}}',
  0),

('rk-f2-04', 'contest-finished-002', 'user-eva-005', 4, 1440, 1440, 0, 0, 2, 0, 180, 6000, 5,
  '{"A":{"solved":true,"attempts":1,"score":100},"C":{"solved":true,"attempts":3,"score":80},"B":{"solved":false}}',
  0),

('rk-f2-05', 'contest-finished-002', 'user-bob-002', 5, 1540, 1520, -20, 0, 1, 0, 100, 4200, 3,
  '{"A":{"solved":true,"attempts":2,"score":100},"B":{"solved":false},"C":{"solved":false}}',
  0);


-- ============================================================
-- 5. contest_problem_results (参赛者每题结果 — 仅 FINISHED 比赛)
-- ============================================================
INSERT INTO `contest_problem_results` (
  `id`, `contest_id`, `contest_problem_id`, `user_id`, `participant_id`,
  `ranking_id`, `is_solved`, `score`, `attempts`, `first_solve_time`,
  `penalty_time`, `time_spent`, `time_bonus`, `is_first_solve`
) VALUES

-- ---- contest-finished-001: 春季邀请赛 per-problem results ----
-- Carol: A✓ B✓ C✓ D✗ E✓ F✗
('cpr-f1-01A', 'contest-finished-001', 'cp-f1-A', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 1, 100, 1, 600,  0,   600, 30, 0),
('cpr-f1-01B', 'contest-finished-001', 'cp-f1-B', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 1, 100, 2, 2400, 300, 2400, 15, 0),
('cpr-f1-01C', 'contest-finished-001', 'cp-f1-C', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 1, 100, 3, 9900, 900, 9900, 5, 0),
('cpr-f1-01D', 'contest-finished-001', 'cp-f1-D', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 0, 0,   2, NULL,  600, 0,    0,  0),
('cpr-f1-01E', 'contest-finished-001', 'cp-f1-E', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 1, 100, 2, 5700, 300, 5700, 10, 0),
('cpr-f1-01F', 'contest-finished-001', 'cp-f1-F', 'user-carol-003', 'pt-f1-01', 'rk-f1-01', 0, 0,   0, NULL,  0,   0,    0,  0),

-- Alice: A✓ B✓ E✓ (未解 C/D/F)
('cpr-f1-02A', 'contest-finished-001', 'cp-f1-A', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 1, 100, 1, 300,  0,   300,  40, 1),
('cpr-f1-02B', 'contest-finished-001', 'cp-f1-B', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 1, 100, 3, 9000, 900, 9000, 5,  0),
('cpr-f1-02C', 'contest-finished-001', 'cp-f1-C', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 0, 0,   2, NULL,  600, 0,    0,  0),
('cpr-f1-02D', 'contest-finished-001', 'cp-f1-D', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 0, 0,   1, NULL,  300, 0,    0,  0),
('cpr-f1-02E', 'contest-finished-001', 'cp-f1-E', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 1, 100, 1, 3600, 0,   3600, 20, 0),
('cpr-f1-02F', 'contest-finished-001', 'cp-f1-F', 'user-alice-001', 'pt-f1-02', 'rk-f1-02', 0, 0,   2, NULL,  600, 0,    0,  0),

-- Bob: A✓ C✓ E✓ (未解 B/D/F)
('cpr-f1-03A', 'contest-finished-001', 'cp-f1-A', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 1, 100, 2, 900,  300, 900,  25, 0),
('cpr-f1-03B', 'contest-finished-001', 'cp-f1-B', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 0, 0,   3, NULL,  900, 0,    0,  0),
('cpr-f1-03C', 'contest-finished-001', 'cp-f1-C', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 1, 100, 4, 9300, 1200, 9300, 5, 0),
('cpr-f1-03D', 'contest-finished-001', 'cp-f1-D', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 0, 0,   2, NULL,  600, 0,    0,  0),
('cpr-f1-03E', 'contest-finished-001', 'cp-f1-E', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 1, 100, 1, 4200, 0,   4200, 15, 0),
('cpr-f1-03F', 'contest-finished-001', 'cp-f1-F', 'user-bob-002', 'pt-f1-03', 'rk-f1-03', 0, 0,   1, NULL,  300, 0,    0,  0),

-- Frank: A✓ E✓ (未解 B/C/D/F)
('cpr-f1-04A', 'contest-finished-001', 'cp-f1-A', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 1, 100, 1, 450,  0,   450,  30, 0),
('cpr-f1-04B', 'contest-finished-001', 'cp-f1-B', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 0, 0,   2, NULL,  600, 0,    0,  0),
('cpr-f1-04C', 'contest-finished-001', 'cp-f1-C', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 0, 0,   1, NULL,  300, 0,    0,  0),
('cpr-f1-04D', 'contest-finished-001', 'cp-f1-D', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 0, 0,   0, NULL,  0,   0,    0,  0),
('cpr-f1-04E', 'contest-finished-001', 'cp-f1-E', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 1, 100, 2, 8400, 300, 8400, 5,  0),
('cpr-f1-04F', 'contest-finished-001', 'cp-f1-F', 'user-frank-006', 'pt-f1-04', 'rk-f1-04', 0, 0,   0, NULL,  0,   0,    0,  0),

-- David: A✓ E✓ (未解 B/C/D/F)
('cpr-f1-05A', 'contest-finished-001', 'cp-f1-A', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 1, 100, 2, 1200, 300, 1200, 20, 0),
('cpr-f1-05B', 'contest-finished-001', 'cp-f1-B', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 0, 0,   2, NULL,  600, 0,    0,  0),
('cpr-f1-05C', 'contest-finished-001', 'cp-f1-C', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 0, 0,   3, NULL,  900, 0,    0,  0),
('cpr-f1-05D', 'contest-finished-001', 'cp-f1-D', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 0, 0,   1, NULL,  300, 0,    0,  0),
('cpr-f1-05E', 'contest-finished-001', 'cp-f1-E', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 1, 100, 3, 10500, 900, 10500, 3, 0),
('cpr-f1-05F', 'contest-finished-001', 'cp-f1-F', 'user-david-004', 'pt-f1-05', 'rk-f1-05', 0, 0,   2, NULL,  600, 0,    0,  0),

-- Eva: A✓ (未解 B/C/D/E/F)
('cpr-f1-06A', 'contest-finished-001', 'cp-f1-A', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 1, 100, 1, 900,  0,   900, 20, 0),
('cpr-f1-06B', 'contest-finished-001', 'cp-f1-B', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 0, 0,   1, NULL,  300, 0,   0,  0),
('cpr-f1-06C', 'contest-finished-001', 'cp-f1-C', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 0, 0,   1, NULL,  300, 0,   0,  0),
('cpr-f1-06D', 'contest-finished-001', 'cp-f1-D', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 0, 0,   0, NULL,  0,   0,   0,  0),
('cpr-f1-06E', 'contest-finished-001', 'cp-f1-E', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 0, 0,   1, NULL,  300, 0,   0,  0),
('cpr-f1-06F', 'contest-finished-001', 'cp-f1-F', 'user-eva-005', 'pt-f1-06', 'rk-f1-06', 0, 0,   0, NULL,  0,   0,   0,  0),

-- ---- contest-finished-002: 新手入门赛 per-problem results ----
-- Alice: A✓ B✓ C✓
('cpr-f2-01A', 'contest-finished-002', 'cp-f2-A', 'user-alice-001', 'pt-f2-01', 'rk-f2-01', 1, 100, 1, 1200, 0, 1200, 10, 0),
('cpr-f2-01B', 'contest-finished-002', 'cp-f2-B', 'user-alice-001', 'pt-f2-01', 'rk-f2-01', 1, 150, 2, 3600, 0, 3600, 8,  0),
('cpr-f2-01C', 'contest-finished-002', 'cp-f2-C', 'user-alice-001', 'pt-f2-01', 'rk-f2-01', 1, 200, 2, 5400, 0, 5400, 5,  0),

-- Carol: A✓ B✓ C✗
('cpr-f2-02A', 'contest-finished-002', 'cp-f2-A', 'user-carol-003', 'pt-f2-02', 'rk-f2-02', 1, 100, 1, 600,  0, 600,  15, 0),
('cpr-f2-02B', 'contest-finished-002', 'cp-f2-B', 'user-carol-003', 'pt-f2-02', 'rk-f2-02', 1, 150, 2, 4800, 0, 4800, 5,  0),
('cpr-f2-02C', 'contest-finished-002', 'cp-f2-C', 'user-carol-003', 'pt-f2-02', 'rk-f2-02', 0, 0,   1, NULL,  0, 0,    0,  0),

-- David: A✓ B✓ C✗
('cpr-f2-03A', 'contest-finished-002', 'cp-f2-A', 'user-david-004', 'pt-f2-03', 'rk-f2-03', 1, 100, 2, 1800, 0, 1800, 8,  0),
('cpr-f2-03B', 'contest-finished-002', 'cp-f2-B', 'user-david-004', 'pt-f2-03', 'rk-f2-03', 1, 100, 3, 5700, 0, 5700, 3,  0),
('cpr-f2-03C', 'contest-finished-002', 'cp-f2-C', 'user-david-004', 'pt-f2-03', 'rk-f2-03', 0, 0,   1, NULL,  0, 0,    0,  0),

-- Eva: A✓ C✓ B✗
('cpr-f2-04A', 'contest-finished-002', 'cp-f2-A', 'user-eva-005', 'pt-f2-04', 'rk-f2-04', 1, 100, 1, 900,  0, 900,  12, 0),
('cpr-f2-04B', 'contest-finished-002', 'cp-f2-B', 'user-eva-005', 'pt-f2-04', 'rk-f2-04', 0, 0,   1, NULL,  0, 0,    0,  0),
('cpr-f2-04C', 'contest-finished-002', 'cp-f2-C', 'user-eva-005', 'pt-f2-04', 'rk-f2-04', 1, 80,  3, 6000, 0, 6000, 2,  0),

-- Bob: A✓ B✗ C✗
('cpr-f2-05A', 'contest-finished-002', 'cp-f2-A', 'user-bob-002', 'pt-f2-05', 'rk-f2-05', 1, 100, 2, 4200, 0, 4200, 5, 0),
('cpr-f2-05B', 'contest-finished-002', 'cp-f2-B', 'user-bob-002', 'pt-f2-05', 'rk-f2-05', 0, 0,   1, NULL,  0, 0,    0, 0),
('cpr-f2-05C', 'contest-finished-002', 'cp-f2-C', 'user-bob-002', 'pt-f2-05', 'rk-f2-05', 0, 0,   0, NULL,  0, 0,    0, 0);


-- ============================================================
-- 6. contest_announcements (比赛公告)
-- ============================================================
INSERT INTO `contest_announcements` (`id`, `contest_id`, `title`, `content`, `created_at`, `is_pinned`) VALUES

-- 春季邀请赛公告
('ann-f1-01', 'contest-finished-001', '比赛已结束',
  'UltiCode 春季邀请赛已圆满结束！恭喜 carol_wu 获得第一名，解出 4 题用时最短。感谢所有参赛选手！',
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 185 MINUTE), 1),

('ann-f1-02', 'contest-finished-001', '题解已发布',
  '本次比赛全部题解已发布，欢迎前往查看和讨论。',
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 10 DAY), INTERVAL 1440 MINUTE), 0),

-- 新手入门赛公告
('ann-f2-01', 'contest-finished-002', '比赛已结束',
  '新手入门赛 Vol.1 已结束！恭喜 alice_coder 满分夺冠！下次新手赛将在两周后举行，敬请期待。',
  DATE_ADD(DATE_SUB(NOW(3), INTERVAL 4 DAY), INTERVAL 130 MINUTE), 1),

-- 周赛公告
('ann-r1-01', 'contest-running-001', '比赛进行中',
  'UltiCode 周赛 #42 正在进行中！距离结束还有约 90 分钟，加油！',
  DATE_SUB(NOW(3), INTERVAL 30 MINUTE), 1),

-- 算法马拉松公告
('ann-u1-01', 'contest-upcoming-001', '报名已开放',
  '算法马拉松 2026 报名已开放！5 小时长赛制，前三名获得平台高级会员。快来报名吧！',
  DATE_SUB(NOW(3), INTERVAL 5 DAY), 1),

('ann-u1-02', 'contest-upcoming-001', '赛制说明',
  '本次比赛采用积分赛制 (SCORE)：每题基础分 + 时间奖励，错误提交扣 5 分，最快解题额外 10 分奖励。最后一小时解封排行榜。',
  DATE_SUB(NOW(3), INTERVAL 2 DAY), 0),

-- 链表专题赛公告
('ann-u2-01', 'contest-upcoming-002', '比赛预告',
  '链表专题训练赛即将开始！专注链表算法，适合巩固基础。不计 Rating，轻松参赛。',
  DATE_SUB(NOW(3), INTERVAL 2 DAY), 1);


-- ============================================================
-- Verify:
--   SELECT status, COUNT(*) FROM contests GROUP BY status;
--   期望: FINISHED=2, RUNNING=1, UPCOMING=2
--
--   SELECT c.status, c.title, COUNT(cp.id) AS problem_count
--   FROM contests c LEFT JOIN contest_problems cp ON c.id = cp.contest_id
--   GROUP BY c.id, c.status, c.title ORDER BY c.start_time;
--   期望: 每场比赛的题目数量
--
--   SELECT c.status, c.title, COUNT(pt.id) AS participant_count
--   FROM contests c LEFT JOIN contest_participants pt ON c.id = pt.contest_id
--   GROUP BY c.id, c.status, c.title ORDER BY c.start_time;
--   期望: FINISHED(6,5), RUNNING(6), UPCOMING(3,2)
--
--   SELECT contest_id, `rank`, user_id, solved_count, total_score
--   FROM contest_rankings ORDER BY contest_id, `rank`;
--   期望: FINISHED 比赛有排名数据
-- ============================================================
