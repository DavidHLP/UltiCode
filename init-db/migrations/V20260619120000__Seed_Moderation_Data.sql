-- ============================================================
-- Migration: V20260619120000__Seed_Moderation_Data.sql
-- Purpose:  为 /moderation 审核中心提供种子数据
--           (队列 / 举报 / 申诉 / 操作历史 / 封禁 / 警告)
--           让 http://localhost:9003/moderation 三个 Tab 与
--           四张统计卡片(by_status / by_category /
--           byEntityType / resolvedToday) 都有内容可看。
-- Created:  2026-06-19
--
-- Dependencies (仅依赖现有 seed，无需新造实体):
--   - V20260602_120000__Create_All_Tables.sql  (审核六表 DDL)
--   - V20260602_120100__Insert_Admin_User_And_Permissions.sql
--       (admin: username='admin' role='ADMIN', id 由 UUID() 动态生成)
--   - V20260603_120300__Seed_Users_And_Permissions.sql
--       (固定 id demo 用户，见下方 REPORTER/AUTHOR 说明)
--   - V20260603_120700__Seed_Forum_Posts_Per_User.sql  (fpost-*)
--   - V20260603_120800__Seed_Comments_And_Interactions.sql (fcmt-* / scmt-*)
--   - V20260603_120400__Seed_Solutions_Test_Data.sql  (sol-s-*)
--
-- Ground truth (author_id 取自实体真实作者):
--   fcmt-001-004 -> user-bob-002 | fpost-005 -> user-eva-005
--   scmt-001-005 -> user-bob-002 | sol-s-010 -> super-root-001
--   fcmt-001-006 -> user-carol-003 | fpost-007 -> mod-mike-001
--   scmt-001-007 -> mod-mike-001 | fcmt-001-002 -> super-root-001
--   真实 reporter 池: user-alice-001 / user-bob-002 / user-carol-003 /
--     user-david-004 / user-eva-005 / user-frank-006 / mod-mike-001 /
--     mod-nina-002 / admin-002 / super-vp-002 / super-root-001
--
-- Record counts (6 tables, 37 rows):
--   moderation_queue   : 8  (PENDING 2 / UNDER_REVIEW 2 / RESOLVED 3
--                            / APPEAL_PENDING 1)
--   reports            : 12 (PENDING 3 / REVIEWED 4 / RESOLVED 4
--                            / DISMISSED 1)
--   moderation_actions : 7  (HIDDEN / DELETED / PERM_BANNED / WARNED
--                            / TEMP_BANNED / DISMISSED / APPEAL_PENDING)
--   user_bans          : 3  (临时已解封 / 永久生效 / 历史已解封)
--   user_warnings      : 3  (未确认 / 已确认 / 已过期)
--   appeals            : 4  (PENDING / UNDER_REVIEW / APPROVED / REJECTED)
--
-- 状态机自洽性 (对照代码 ground truth):
--   - queue.status 永远只会是 PENDING/UNDER_REVIEW/RESOLVED/APPEAL_PENDING
--     (代码 RestoreDismissHandler 把 DISMISSED 落为 status=RESOLVED)。
--     故 queue-007 用 status=RESOLVED + resolution=DISMISSED。
--   - PENDING/UNDER_REVIEW 的 appeal 关联 APPEAL_PENDING 的 queue;
--     APPROVED/REJECTED 的 appeal 关联 RESOLVED 的 queue。
--   - resolvedToday 口径: status=RESOLVED AND DATE(resolved_at)=CURDATE()。
--     当天处理时间一律用 CONCAT(CURDATE(),' HH:MM:SS')，任意执行日都命中。
--     命中: queue-005 / queue-006 / queue-007 = 3。
--
-- 时间锚点:
--   历史: 2026-04 / 2026-05 / 2026-06-15 / 2026-06-18 (固定字面量)
--   当天: @t_resolve(09:30) / @t_appeal(10:00) / @t_appeal_done(11:00) 等
--         (基于 CURDATE()，对齐 resolvedToday)
--
-- Maintenance:
--   - 幂等: 每张表 DELETE WHERE id IN (...) + INSERT。
--   - admin id 动态: SET @admin_id = (SELECT id FROM users
--     WHERE username='admin' AND role='ADMIN' LIMIT 1);
--     不加 is_active 过滤 (Secure 迁移把 admin 锁定 is_active=0，
--     但行仍在 users 表，id 可取)。
--
-- Risks:
--   1. moderation_queue/reports/appeals/user_bans 的 updated_at 列
--      NOT NULL 无默认值，必须显式赋值。
--   2. moderation_actions/user_warnings 无 updated_at 列。
--   3. entity_type 必须 snake_case 小写:
--        forum_post / forum_comment / solution / solution_comment。
--   4. moderation_actions 列名是 action (不是 action_type)。
--   5. reports 唯一约束 uk_reports_reporter_entity(reporter_id,entity_type,entity_id)。
--   6. moderation_queue 唯一约束 (entity_type, entity_id) — 8 条引用不同实体。
--
-- Rollback:
--   DELETE FROM moderation_queue   WHERE id LIKE 'mod-queue-%';
--   DELETE FROM reports            WHERE id LIKE 'report-%';
--   DELETE FROM moderation_actions WHERE id LIKE 'mod-action-%';
--   DELETE FROM user_bans          WHERE id LIKE 'user-ban-%';
--   DELETE FROM user_warnings      WHERE id LIKE 'user-warn-%';
--   DELETE FROM appeals            WHERE id LIKE 'appeal-%';
-- ============================================================
SET NAMES utf8mb4;

-- admin 主账号 id 由 Insert_Admin_User 迁移用 UUID() 生成，跨环境不稳定。
-- 所有 reviewer / performed_by_id / banned_by_id 统一指向它。
-- 注意: 不加 is_active 过滤 (V20260606130000 把 admin 锁定 is_active=0，
--       但行仍存在，id 可取；加过滤会让子查询返回 NULL 导致 NOT NULL 列插入失败)。
SET @admin_id = (
    SELECT id FROM users
    WHERE username = 'admin' AND role = 'ADMIN'
    LIMIT 1
);

-- 当天时间锚点 (基于 CURDATE()，保证 resolvedToday = CURDATE() 任意天命中)。
SET @t_assign       = CONCAT(CURDATE(), ' 08:00:00');  -- 当天分配审核
SET @t_resolve_m1   = CONCAT(CURDATE(), ' 09:29:00');  -- 处理前一刻
SET @t_resolve      = CONCAT(CURDATE(), ' 09:30:00');  -- 当天处理 (resolved/dismissed)
SET @t_appeal       = CONCAT(CURDATE(), ' 10:00:00');  -- 当天申诉创建
SET @t_appeal_mid   = CONCAT(CURDATE(), ' 10:30:00');  -- 申诉分配复核
SET @t_appeal_done  = CONCAT(CURDATE(), ' 11:00:00');  -- 申诉复核完成

-- ============================================================
-- 1. moderation_queue (8 条)
--    唯一约束: (entity_type, entity_id) — 8 条引用不同实体。
--    status 仅取代码可达态: PENDING / UNDER_REVIEW / RESOLVED / APPEAL_PENDING。
-- ============================================================
DELETE FROM moderation_queue
WHERE id IN (
    'mod-queue-001', 'mod-queue-002', 'mod-queue-003', 'mod-queue-004',
    'mod-queue-005', 'mod-queue-006', 'mod-queue-007', 'mod-queue-008'
);

INSERT INTO moderation_queue (
    id, entity_type, entity_id, author_id, priority, status,
    report_count, primary_category, assigned_to_id, assigned_at,
    reviewed_by_id, reviewed_at, resolution, resolution_note,
    created_at, updated_at, resolved_at
) VALUES
    -- #1 PENDING — spam 广告评论 (待处理)
    ('mod-queue-001', 'forum_comment', 'fcmt-001-004', 'user-bob-002',
     5, 'PENDING', 1, 'SPAM',
     NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-06-15 09:00:00', '2026-06-15 09:00:00', NULL),

    -- #2 PENDING — 人身攻击帖子 (待处理)
    ('mod-queue-002', 'forum_post', 'fpost-005', 'user-eva-005',
     0, 'PENDING', 2, 'HARASSMENT',
     NULL, NULL, NULL, NULL, NULL, NULL,
     '2026-06-18 14:00:00', '2026-06-18 14:00:00', NULL),

    -- #3 UNDER_REVIEW — 错误解法评论 (已分配 mod-mike)
    ('mod-queue-003', 'solution_comment', 'scmt-001-005', 'user-bob-002',
     8, 'UNDER_REVIEW', 3, 'WRONG_ANSWER',
     'mod-mike-001', @t_assign, NULL, NULL, NULL, NULL,
     '2026-06-15 09:05:00', @t_assign, NULL),

    -- #4 UNDER_REVIEW — 抄袭他人代码 (已分配 mod-nina)
    ('mod-queue-004', 'solution', 'sol-s-010', 'super-root-001',
     3, 'UNDER_REVIEW', 1, 'COPYRIGHT',
     'mod-nina-002', @t_assign, NULL, NULL, NULL, NULL,
     '2026-06-18 14:00:00', @t_assign, NULL),

    -- #5 RESOLVED DELETED — 辱骂评论 (当天处理，命中 resolvedToday；appeal-004 REJECTED)
    ('mod-queue-005', 'forum_comment', 'fcmt-001-006', 'user-carol-003',
     10, 'RESOLVED', 2, 'HATE_SPEECH',
     'mod-mike-001', '2026-06-15 09:05:00',
     @admin_id, @t_resolve,
     'DELETED', '删除:评论中含针对特定群体的辱骂用语，违反社区公约第 3 条',
     '2026-06-15 09:00:00', @t_resolve, @t_resolve),

    -- #6 RESOLVED APPEAL_APPROVED — 广告帖子 (申诉成功，当天复核；appeal-003 APPROVED，mike 解封)
    ('mod-queue-006', 'forum_post', 'fpost-007', 'mod-mike-001',
     2, 'RESOLVED', 1, 'SPAM',
     'mod-nina-002', '2026-06-15 09:05:00',
     @admin_id, @t_appeal_done,
     'APPEAL_APPROVED', '申诉成功:经核实外链为开源项目地址，撤销处罚并恢复内容',
     '2026-06-15 09:00:00', @t_appeal_done, @t_appeal_done),

    -- #7 RESOLVED DISMISSED — 误报 (当天处理，命中 resolvedToday)
    ('mod-queue-007', 'solution_comment', 'scmt-001-007', 'mod-mike-001',
     1, 'RESOLVED', 1, 'OTHER',
     NULL, NULL,
     @admin_id, @t_resolve,
     'DISMISSED', '误报:该评论为正常算法讨论，未违反社区公约',
     '2026-06-15 09:05:00', @t_resolve, @t_resolve),

    -- #8 APPEAL_PENDING — 作者申诉中 (原已处理，后转入申诉；appeal-001/002)
    ('mod-queue-008', 'forum_comment', 'fcmt-001-002', 'super-root-001',
     6, 'APPEAL_PENDING', 1, 'MISINFORMATION',
     'mod-nina-002', '2026-06-15 09:05:00',
     @admin_id, @t_resolve,
     'APPEAL_PENDING', '作者已发起申诉，等待申诉复核结果',
     '2026-06-15 09:00:00', @t_appeal, @t_resolve);

-- ============================================================
-- 2. reports (12 条)
--    唯一约束: uk_reports_reporter_entity (reporter_id, entity_type, entity_id)。
--    reporter 全部为真实 demo 用户，且不等于被举报内容的 author。
--    status 覆盖: PENDING(3) / REVIEWED(4) / RESOLVED(4) / DISMISSED(1)。
-- ============================================================
DELETE FROM reports
WHERE id IN (
    'report-001', 'report-002', 'report-003', 'report-004',
    'report-005', 'report-006', 'report-007', 'report-008',
    'report-009', 'report-010', 'report-011', 'report-012'
);

INSERT INTO reports (
    id, reporter_id, entity_type, entity_id, category, reason, evidence,
    status, queue_id, created_at, updated_at
) VALUES
    -- queue-001 (fcmt-001-004, author bob) — PENDING
    ('report-001', 'user-carol-003',
     'forum_comment', 'fcmt-001-004', 'SPAM',
     '评论含外链广告，疑似引流刷屏，请管理员核查',
     NULL, 'PENDING', 'mod-queue-001',
     '2026-06-15 09:00:00', '2026-06-15 09:00:00'),

    -- queue-002 (fpost-005, author eva) — PENDING (2 条不同 reporter)
    ('report-002', 'user-alice-001',
     'forum_post', 'fpost-005', 'HARASSMENT',
     '帖子中对其他用户进行人身攻击，使用侮辱性词汇',
     NULL, 'PENDING', 'mod-queue-002',
     '2026-06-18 14:00:00', '2026-06-18 14:00:00'),

    ('report-003', 'user-david-004',
     'forum_post', 'fpost-005', 'OTHER',
     '同一作者短期内连续刷帖，疑似复制粘贴灌水',
     NULL, 'PENDING', 'mod-queue-002',
     '2026-06-18 14:05:00', '2026-06-18 14:05:00'),

    -- queue-003 (scmt-001-005, author bob) — REVIEWED (3 条不同 reporter)
    ('report-004', 'user-eva-005',
     'solution_comment', 'scmt-001-005', 'WRONG_ANSWER',
     '评论中提到的解法思路存在逻辑错误，会误导其他用户',
     NULL, 'REVIEWED', 'mod-queue-003',
     '2026-06-15 09:05:00', @t_assign),

    ('report-005', 'user-frank-006',
     'solution_comment', 'scmt-001-005', 'WRONG_ANSWER',
     '该解法没有处理空数组的边界情况，会导致运行时异常',
     NULL, 'REVIEWED', 'mod-queue-003',
     '2026-06-15 10:05:00', @t_assign),

    ('report-006', 'user-carol-003',
     'solution_comment', 'scmt-001-005', 'OTHER',
     '评论排版混乱，代码块未正确格式化，可读性差',
     NULL, 'REVIEWED', 'mod-queue-003',
     '2026-06-15 11:05:00', @t_assign),

    -- queue-004 (sol-s-010, author super-root) — REVIEWED
    ('report-007', 'user-alice-001',
     'solution', 'sol-s-010', 'COPYRIGHT',
     '该题解疑似抄袭他人博客文章，代码结构高度雷同',
     NULL, 'REVIEWED', 'mod-queue-004',
     '2026-06-18 14:00:00', @t_assign),

    -- queue-005 (fcmt-001-006, author carol) — RESOLVED (2 条不同 reporter)
    ('report-008', 'user-alice-001',
     'forum_comment', 'fcmt-001-006', 'HATE_SPEECH',
     '评论中包含针对特定群体的辱骂用语',
     NULL, 'RESOLVED', 'mod-queue-005',
     '2026-06-15 09:00:00', @t_resolve),

    ('report-009', 'user-david-004',
     'forum_comment', 'fcmt-001-006', 'HARASSMENT',
     '评论含人身攻击内容，针对其他用户进行言语攻击',
     NULL, 'RESOLVED', 'mod-queue-005',
     '2026-06-15 09:10:00', @t_resolve),

    -- queue-006 (fpost-007, author mike) — RESOLVED
    ('report-010', 'user-frank-006',
     'forum_post', 'fpost-007', 'SPAM',
     '帖子主体为推广外链，与社区主题无关',
     NULL, 'RESOLVED', 'mod-queue-006',
     '2026-06-15 09:00:00', @t_resolve),

    -- queue-007 (scmt-001-007, author mike) — DISMISSED
    ('report-011', 'user-eva-005',
     'solution_comment', 'scmt-001-007', 'OTHER',
     '该评论内容存在争议，可能偏离技术讨论主题',
     NULL, 'DISMISSED', 'mod-queue-007',
     '2026-06-15 09:05:00', @t_resolve),

    -- queue-008 (fcmt-001-002, author super-root) — RESOLVED (后转入申诉)
    ('report-012', 'user-david-004',
     'forum_comment', 'fcmt-001-002', 'MISINFORMATION',
     '评论中提供的性能数据与实际测试结果不符，可能误导读者',
     NULL, 'RESOLVED', 'mod-queue-008',
     '2026-06-15 09:00:00', @t_resolve);

-- ============================================================
-- 3. moderation_actions (7 条)
--    无 updated_at 列。列名是 action (不是 action_type)。
--    duration_days 仅 TEMP_BANNED 使用 (7 天)。
--    覆盖 7 种 action: HIDDEN/DELETED/PERM_BANNED/WARNED/TEMP_BANNED/DISMISSED/APPEAL_PENDING。
-- ============================================================
DELETE FROM moderation_actions
WHERE id IN (
    'mod-action-001', 'mod-action-002', 'mod-action-003', 'mod-action-004',
    'mod-action-005', 'mod-action-006', 'mod-action-007'
);

INSERT INTO moderation_actions (
    id, queue_id, action, performed_by_id, note, duration_days, created_at
) VALUES
    -- queue-005 (辱骂评论) 操作链: 先 HIDDEN 后 DELETED + PERM_BANNED(carol 永封)
    ('mod-action-001', 'mod-queue-005', 'HIDDEN',
     @admin_id, '先对评论进行隐藏处理，等待进一步审核',
     NULL, @t_resolve_m1),

    ('mod-action-002', 'mod-queue-005', 'DELETED',
     @admin_id, '删除辱骂评论:评论中含针对特定群体的辱骂用语',
     NULL, @t_resolve),

    ('mod-action-003', 'mod-queue-005', 'PERM_BANNED',
     @admin_id, '多次发布仇恨言论，永久封禁该账号',
     NULL, @t_resolve),

    -- queue-006 (广告帖子) 操作链: WARNED + TEMP_BANNED(mike 临时封，后申诉解封)
    ('mod-action-004', 'mod-queue-006', 'WARNED',
     @admin_id, '警告广告行为:请移除推广外链，遵守社区公约',
     NULL, @t_resolve),

    ('mod-action-005', 'mod-queue-006', 'TEMP_BANNED',
     @admin_id, '对账号临时封禁 7 天以观察整改情况',
     7, @t_resolve),

    -- queue-007 (误报) 操作: DISMISSED
    ('mod-action-006', 'mod-queue-007', 'DISMISSED',
     @admin_id, '误报处理:该评论为正常算法讨论，未违规',
     NULL, @t_resolve),

    -- queue-008 (申诉中) 操作: APPEAL_PENDING (作者发起申诉，转入申诉复核)
    ('mod-action-007', 'mod-queue-008', 'APPEAL_PENDING',
     @admin_id, '作者已发起申诉，转入申诉复核流程',
     NULL, @t_appeal);

-- ============================================================
-- 4. user_bans (3 条)
--    无 status 列，状态由 is_permanent / ends_at / unbanned_at 表达。
--    banned_by_id NOT NULL -> 必须指向真实存在的 admin (@admin_id)。
-- ============================================================
DELETE FROM user_bans
WHERE id IN ('user-ban-001', 'user-ban-002', 'user-ban-003');

INSERT INTO user_bans (
    id, user_id, is_permanent, reason, category, queue_id, action_id,
    banned_by_id, started_at, ends_at, unbanned_at, unbanned_by_id,
    unban_reason, created_at, updated_at
) VALUES
    -- #1 临时封禁(已通过申诉解封) — mod-mike 是 fpost-007 作者；闭环 appeal-003 APPROVED
    ('user-ban-001', 'mod-mike-001', 0,
     '临时封禁:多次发布含推广外链的帖子，违反社区公约第 5 条',
     'SPAM', 'mod-queue-006', 'mod-action-005',
     @admin_id,
     @t_resolve, '2026-06-26 09:30:00',
     @t_appeal_done, @admin_id,
     '申诉成功:经核实外链为开源项目地址，提前解除封禁',
     @t_resolve, @t_appeal_done),

    -- #2 永久封禁(生效中) — user-carol 是 fcmt-001-006 作者；appeal-004 REJECTED 维持
    ('user-ban-002', 'user-carol-003', 1,
     '永久封禁:多次发布含辱骂和仇恨言论的内容，性质恶劣',
     'HATE_SPEECH', 'mod-queue-005', 'mod-action-003',
     @admin_id,
     @t_resolve, NULL,
     NULL, NULL, NULL,
     @t_resolve, @t_resolve),

    -- #3 历史临时封禁(已解封，独立记录，不关联当前 queue)
    ('user-ban-003', 'user-bob-002', 0,
     '历史临时封禁:多次参与人身攻击讨论(已期满解除)',
     'HARASSMENT', NULL, NULL,
     @admin_id,
     '2026-05-10 10:00:00', '2026-05-17 10:00:00',
     '2026-05-17 10:00:00', @admin_id,
     '封禁期满自动解除',
     '2026-05-10 10:00:00', '2026-05-17 10:00:00');

-- ============================================================
-- 5. user_warnings (3 条)
--    无 updated_at 列。category NOT NULL 必填。
--    状态由 acknowledged_at / expires_at 表达。
-- ============================================================
DELETE FROM user_warnings
WHERE id IN ('user-warn-001', 'user-warn-002', 'user-warn-003');

INSERT INTO user_warnings (
    id, user_id, queue_id, action_id, reason, category,
    acknowledged_at, created_at, expires_at
) VALUES
    -- #1 未确认 + 30 天后过期 — 对应 mod-queue-006 广告行为 (mike)
    ('user-warn-001', 'mod-mike-001',
     'mod-queue-006', 'mod-action-004',
     '广告行为警告:请立即移除帖子中的推广外链，后续再犯将升级处理',
     'SPAM',
     NULL,
     @t_resolve,
     '2026-07-19 09:30:00'),

    -- #2 已确认 + 永不过期 — 历史警告 (独立于 queue)
    ('user-warn-002', 'user-david-004',
     NULL, NULL,
     '历史警告:在论坛讨论中使用轻微骚扰性措辞，请注意沟通方式',
     'HARASSMENT',
     '2026-05-20 09:00:00',
     '2026-05-18 09:00:00',
     NULL),

    -- #3 已确认 + 已过期 — 历史警告 (独立于 queue)
    ('user-warn-003', 'user-eva-005',
     NULL, NULL,
     '历史警告:评论中传播了未经核实的错误信息(已过期)',
     'MISINFORMATION',
     '2026-04-10 09:00:00',
     '2026-04-08 09:00:00',
     '2026-04-15 09:00:00');

-- ============================================================
-- 6. appeals (4 条)
--    status 避开 ESCALATED (Java 枚举无此值)。
--    覆盖: PENDING / UNDER_REVIEW / APPROVED / REJECTED。
--    PENDING/UNDER_REVIEW 关联 APPEAL_PENDING queue；APPROVED/REJECTED 关联 RESOLVED queue。
--    时间线: appeal.created_at (@t_appeal=10:00) > queue 首次 resolved (@t_resolve=09:30)。
-- ============================================================
DELETE FROM appeals
WHERE id IN ('appeal-001', 'appeal-002', 'appeal-003', 'appeal-004');

INSERT INTO appeals (
    id, queue_id, appellant_id, reason, evidence, status,
    reviewed_by_id, reviewed_at, response, created_at, updated_at
) VALUES
    -- #1 PENDING — 对应 mod-queue-008 (fcmt-001-002 性能数据争议) 待审
    ('appeal-001', 'mod-queue-008', 'super-root-001',
     '我的评论是对 Rust 所有权机制的实测体会，性能数据来自本地 benchmark，并非错误信息，请求恢复',
     NULL, 'PENDING',
     NULL, NULL, NULL,
     @t_appeal, @t_appeal),

    -- #2 UNDER_REVIEW — 对应 mod-queue-008 (同一作者补充材料，已分配复核中，未完成)
    ('appeal-002', 'mod-queue-008', 'super-root-001',
     '补充本地 benchmark 原始数据与测试脚本链接，请求复核团队重新评估',
     NULL, 'UNDER_REVIEW',
     NULL, NULL, NULL,
     @t_appeal, @t_appeal_mid),

    -- #3 APPROVED — 对应 mod-queue-006 (广告帖子)；闭环 user-ban-001 解封
    ('appeal-003', 'mod-queue-006', 'mod-mike-001',
     '帖子中的外链是开源项目地址，并非商业广告，属于误判，请求解除封禁',
     NULL, 'APPROVED',
     @admin_id, @t_appeal_done,
     '经核实，所发布外链确为开源项目地址，非商业推广，判定为误判，解除封禁并恢复内容',
     @t_appeal, @t_appeal_done),

    -- #4 REJECTED — 对应 mod-queue-005 (辱骂评论)；维持原判 (carol 永封)
    ('appeal-004', 'mod-queue-005', 'user-carol-003',
     '所使用的词汇是游戏圈的黑话，并非辱骂用语，属于误判，请复核',
     NULL, 'REJECTED',
     @admin_id, @t_appeal_done,
     '维持原判:经核实该用语在通用语境下具有明确的侮辱含义，且该账号有多次类似记录，不予解除',
     @t_appeal, @t_appeal_done);

-- ============================================================
-- 验证查询 (部署后取消注释执行)
-- ============================================================
-- SELECT 'moderation_queue' AS tbl, COUNT(*) AS n
--   FROM moderation_queue WHERE id LIKE 'mod-queue-%'
-- UNION ALL SELECT 'reports', COUNT(*) FROM reports WHERE id LIKE 'report-%'
-- UNION ALL SELECT 'moderation_actions', COUNT(*)
--   FROM moderation_actions WHERE id LIKE 'mod-action-%'
-- UNION ALL SELECT 'user_bans', COUNT(*)
--   FROM user_bans WHERE id LIKE 'user-ban-%'
-- UNION ALL SELECT 'user_warnings', COUNT(*)
--   FROM user_warnings WHERE id LIKE 'user-warn-%'
-- UNION ALL SELECT 'appeals', COUNT(*)
--   FROM appeals WHERE id LIKE 'appeal-%';
--
-- -- 状态分布
-- SELECT status, COUNT(*) FROM moderation_queue
--   WHERE id LIKE 'mod-queue-%' GROUP BY status;
-- SELECT status, COUNT(*) FROM reports
--   WHERE id LIKE 'report-%' GROUP BY status;
-- SELECT status, COUNT(*) FROM appeals
--   WHERE id LIKE 'appeal-%' GROUP BY status;
--
-- -- resolvedToday 命中 (期望 3: queue-005 / queue-006 / queue-007)
-- SELECT COUNT(*) AS resolved_today FROM moderation_queue
--   WHERE id LIKE 'mod-queue-%'
--     AND status = 'RESOLVED' AND DATE(resolved_at) = CURDATE();
--
-- -- 外键完整性自检 (期望全部 0)
-- SELECT 'orphan_report_reporter' AS chk, COUNT(*) FROM reports r
--   WHERE r.id LIKE 'report-%' AND NOT EXISTS
--     (SELECT 1 FROM users u WHERE u.id = r.reporter_id);
-- SELECT 'orphan_queue_author' AS chk, COUNT(*) FROM moderation_queue q
--   WHERE q.id LIKE 'mod-queue-%' AND NOT EXISTS
--     (SELECT 1 FROM users u WHERE u.id = q.author_id);
