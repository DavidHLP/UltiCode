-- =============================================================================
-- 竞赛管理测试的种子数据
-- 创建5个竞赛及真实的参赛者数据，关联到真实用户
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CONTESTS（5条记录：ICPC、IOI、自定义 x 各种状态）
-- -----------------------------------------------------------------------------
INSERT INTO contests (
  id, title, slug, contest_type, start_time, end_time, duration_minutes,
  status, penalty_per_wrong, scoring_mode, tie_breaker,
  registered_count, participant_count, submission_count, is_rated,
  description, created_by, is_visible, rules,
  created_at, updated_at, is_deleted, is_virtual, max_participants,
  registration_start, registration_end
) VALUES
-- 往期竞赛存档（FINISHED，6名参赛者，全部真实用户）
('c-archive-001', '往期竞赛存档', 'past-contest-archive', 'CUSTOM',
 DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
 120, 'FINISHED', 0, 'SCORE', 'NONE',
 6, 6, 38, 0,
 '用于练习的存档竞赛。所有提交都会被评测但不影响积分。',
 'u-admin-001', 1,
 '练习模式：提交会被评测但不影响积分。',
 NOW(), NOW(), 0, 0, NULL,
 DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),

-- 速写编程赛（RUNNING，4名参赛者正在进行）
('c-quick-fire-01', '速写编程赛', 'quick-fire-round', 'CUSTOM',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 30 MINUTE),
 60, 'RUNNING', 0, 'SCORE', 'TOTAL_ATTEMPTS',
 4, 4, 12, 0,
 '快节奏编程挑战。60分钟10道题，按得分计分并含时间奖励。',
 'u-admin-001', 1,
 '速写赛规则：1. 10道难度递增的题目。2. 基础分加时间奖励。3. 无罚时。',
 NOW(), NOW(), 0, 0, 100, NULL, NULL),

-- 第42周编程挑战赛（UPCOMING，2人已报名）
('c-weekly-042', '第42周编程挑战赛', 'weekly-programming-challenge-42', 'ICPC',
 DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
 120, 'UPCOMING', 300, 'ICPC', 'LAST_SOLVE_TIME',
 2, 0, 0, 1,
 '每周ICPC风格编程竞赛。尽可能多地解题，并尽量减少罚时。',
 'u-admin-001', 1,
 'ICPC规则：1. 每次错误提交加20分钟罚时。2. 评测结果为通过或拒绝。3. 按解题数排序，解题数相同则按罚时排序。',
 NOW(), NOW(), 0, 0, 100,
 DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY)),

-- IOI风格积分赛（DRAFT，无人报名）
('c-ioi-score-01', 'IOI风格积分赛', 'ioi-style-score-contest', 'IOI',
 DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 14 DAY), INTERVAL 3 HOUR),
 180, 'DRAFT', 0, 'IOI', 'NONE',
 0, 0, 0, 0,
 'IOI风格竞赛，采用积分制评分。每道题含多个测试点，支持部分得分。',
 'u-admin-001', 0,
 'IOI规则：1. 每道题有满分上限。2. 通过部分测试点可获得部分得分。3. 错误提交不计罚时。',
 NOW(), NOW(), 0, 0, 50,
 DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY)),

-- ICPC区域赛热身赛（UPCOMING，4人已报名）
('c-regional-warmup', 'ICPC区域赛热身赛', 'icpc-regional-warmup', 'ICPC',
 DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 30 DAY), INTERVAL 4 HOUR),
 240, 'UPCOMING', 300, 'ICPC', 'LAST_SOLVE_TIME',
 4, 0, 0, 1,
 '为即将到来的ICPC区域赛准备的练习赛。完整规则，含排行榜封榜。',
 'u-admin-001', 1,
 '标准ICPC区域赛规则适用。最后一小时排行榜封榜。',
 NOW(), NOW(), 0, 0, 200,
 DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 28 DAY));

-- -----------------------------------------------------------------------------
-- 2. CONTEST PROBLEMS（每场竞赛1道题，关联到problem id=1）
-- -----------------------------------------------------------------------------
INSERT INTO contest_problems (id, contest_id, problem_id, problem_index, score, penalty_per_wrong, solved_count, submission_count, label, base_score, time_bonus, created_at, updated_at) VALUES
('cp-arc-a', 'c-archive-001', 1, 'A', 100, 0, 5, 8, '入门', 100, 1, NOW(), NOW()),
('cp-qf-a', 'c-quick-fire-01', 1, 'A', 50, 0, 3, 6, '入门', 50, 2, NOW(), NOW()),
('cp-w042-a', 'c-weekly-042', 1, 'A', 100, 300, 0, 0, '热身', 100, 1, NOW(), NOW()),
('cp-reg-a', 'c-regional-warmup', 1, 'A', 100, 300, 0, 0, '热身', 100, 1, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 3. CONTEST PARTICIPANTS（16条记录，关联到真实用户）
-- -----------------------------------------------------------------------------

-- 往期竞赛存档：6名参赛者（全部真实用户，全部FINISHED）
INSERT INTO contest_participants (id, contest_id, user_id, status, registered_at, started_at, finished_at, is_virtual, final_rank, total_penalty, total_score, total_attempts, last_solve_time, total_time, attempt_count, created_at, updated_at) VALUES
('cpart-arc-01', 'c-archive-001', 'u-admin-001', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 95 MINUTE),
 0, 1, 600, 300, 8, 5700, 5700, 8, NOW(), NOW()),
('cpart-arc-02', 'c-archive-001', 'user-alice-001', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 100 MINUTE),
 0, 2, 900, 250, 10, 6000, 6000, 10, NOW(), NOW()),
('cpart-arc-03', 'c-archive-001', 'user-bob-002', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 105 MINUTE),
 0, 3, 1200, 200, 12, 6300, 6300, 12, NOW(), NOW()),
('cpart-arc-04', 'c-archive-001', 'user-carol-003', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 110 MINUTE),
 0, 4, 1500, 150, 6, 6600, 6600, 6, NOW(), NOW()),
('cpart-arc-05', 'c-archive-001', 'user-david-004', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 115 MINUTE),
 0, 5, 1800, 100, 4, 6900, 6900, 4, NOW(), NOW()),
('cpart-arc-06', 'c-archive-001', 'user-eva-005', 'FINISHED',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 120 MINUTE),
 0, 6, 2100, 50, 3, 7200, 7200, 3, NOW(), NOW()),

-- 速写编程赛：4名参赛者（STARTED，正在进行）
('cpart-qf-01', 'c-quick-fire-01', 'user-alice-001', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL,
 0, NULL, 0, 80, 4, NULL, 30, 4, NOW(), NOW()),
('cpart-qf-02', 'c-quick-fire-01', 'user-bob-002', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL,
 0, NULL, 0, 60, 3, NULL, 30, 3, NOW(), NOW()),
('cpart-qf-03', 'c-quick-fire-01', 'user-carol-003', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 28 MINUTE), DATE_SUB(NOW(), INTERVAL 28 MINUTE), NULL,
 0, NULL, 0, 40, 3, NULL, 28, 3, NOW(), NOW()),
('cpart-qf-04', 'c-quick-fire-01', 'user-david-004', 'STARTED',
 DATE_SUB(NOW(), INTERVAL 25 MINUTE), DATE_SUB(NOW(), INTERVAL 25 MINUTE), NULL,
 0, NULL, 0, 20, 2, NULL, 25, 2, NOW(), NOW()),

-- 第42周编程挑战赛：2名用户已报名 REGISTERED
('cpart-w042-01', 'c-weekly-042', 'user-alice-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-w042-02', 'c-weekly-042', 'user-bob-002', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),

-- ICPC区域赛热身赛：4名用户已报名 REGISTERED
('cpart-reg-01', 'c-regional-warmup', 'u-admin-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-02', 'c-regional-warmup', 'user-alice-001', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-03', 'c-regional-warmup', 'user-eva-005', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW()),
('cpart-reg-04', 'c-regional-warmup', 'user-frank-006', 'REGISTERED',
 DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL,
 0, NULL, 0, 0, 0, NULL, 0, 0, NOW(), NOW());

-- -----------------------------------------------------------------------------
-- 4. CONTEST RANKINGS（已结束竞赛的排名）
-- -----------------------------------------------------------------------------
INSERT INTO contest_rankings (id, contest_id, user_id, `rank`, rating_before, rating_after, rating_change, is_virtual, solved_count, total_penalty, total_score, finish_time, total_attempts, is_frozen) VALUES
('crank-arc-01', 'c-archive-001', 'u-admin-001', 1, 1500, 1500, 0, 0, 3, 600, 300, 5700, 8, 0),
('crank-arc-02', 'c-archive-001', 'user-alice-001', 2, 1500, 1500, 0, 0, 2, 900, 250, 6000, 10, 0),
('crank-arc-03', 'c-archive-001', 'user-bob-002', 3, 1500, 1500, 0, 0, 2, 1200, 200, 6300, 12, 0),
('crank-arc-04', 'c-archive-001', 'user-carol-003', 4, 1500, 1500, 0, 0, 1, 1500, 150, 6600, 6, 0),
('crank-arc-05', 'c-archive-001', 'user-david-004', 5, 1500, 1500, 0, 0, 1, 1800, 100, 6900, 4, 0),
('crank-arc-06', 'c-archive-001', 'user-eva-005', 6, 1500, 1500, 0, 0, 1, 2100, 50, 7200, 3, 0);