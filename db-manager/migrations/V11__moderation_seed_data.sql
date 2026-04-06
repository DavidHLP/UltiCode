SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V11__moderation_seed_data
-- Seed data for moderation system
-- Tables affected: moderation_queue, reports, moderation_actions, appeals

-- ============================================================================
-- Table: moderation_queue (12 rows)
-- ============================================================================
-- Covers all 5 statuses: PENDING(3), UNDER_REVIEW(2), RESOLVED(2), DISMISSED(3), APPEAL_PENDING(1)
-- References real entities: forum_posts, forum_comments, solutions, solution_comments

-- mq-001: SPAM report on forum post about JS Map performance (u-002's post)
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-001','forum_post','post-rust-hashmap','u-002',3,'UNDER_REVIEW',3,'SPAM','u-mod-001','2026-04-02 10:00:00.000',NULL,NULL,NULL,'管理员正在审查帖子中的推广链接。基准测试数据可能包含赞助内容。','2026-04-01 22:30:00.000','2026-04-02 10:00:00.000',NULL);

-- mq-002: HARASSMENT report on contest tilt comment (user-scott's "rage quit" comment)
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-002','forum_comment','c-tilt-4','user-scott',5,'PENDING',2,'HARASSMENT',NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-03 08:15:00.000','2026-04-03 08:15:00.000',NULL);

-- mq-003: WRONG_ANSWER report on brute force Two Sum solution (user-alex's sol-002)
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-003','solution','sol-002','user-alex',2,'RESOLVED',2,'WRONG_ANSWER',NULL,NULL,'u-mod-001','2026-04-02 14:30:00.000','HIDDEN','题解已隐藏：O(n²) 暴力解法可能误导初学者。已通知作者添加时间复杂度声明。','2026-04-01 18:00:00.000','2026-04-02 14:30:00.000','2026-04-02 14:30:00.000');

-- mq-004: COPYRIGHT claim on segment tree visualization (user-tourist's post) — dismissed, original content
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-004','forum_post','post-segtree-visual','user-tourist',4,'DISMISSED',2,'COPYRIGHT',NULL,NULL,'u-admin-001','2026-04-02 09:00:00.000','DISMISSED','内容已核实为原创。可视化风格和代码均为作者原创。','2026-04-01 20:00:00.000','2026-04-02 09:00:00.000','2026-04-02 09:00:00.000');

-- mq-005: OTHER report on solution comment asking about duplicates — dismissed as valid question
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-005','solution_comment','comment-003','user-sara',1,'DISMISSED',1,'OTHER',NULL,NULL,'u-mod-001','2026-04-01 16:00:00.000','DISMISSED','关于重复元素处理的合理提问。不构成违规。','2026-04-01 12:00:00.000','2026-04-01 16:00:00.000','2026-04-01 16:00:00.000');

-- mq-006: MISINFORMATION report on JS object performance advice (user-petr's comment)
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-006','forum_comment','c-rust-3','user-petr',3,'PENDING',2,'MISINFORMATION',NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-04 11:20:00.000','2026-04-04 11:20:00.000',NULL);

-- mq-007: WRONG_ANSWER on sort-and-sweep merge intervals — hidden then appealed by author
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-007','solution','sol-006','user-max',2,'APPEAL_PENDING',2,'WRONG_ANSWER',NULL,NULL,'u-mod-001','2026-04-03 16:00:00.000','HIDDEN','因边界条件处理问题被举报后隐藏题解。作者已提交申诉。','2026-04-02 20:00:00.000','2026-04-04 09:00:00.000','2026-04-03 16:00:00.000');

-- mq-008: SPAM report on unscientific cold water claim (user-sara's comment)
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-008','forum_comment','c-tilt-6','user-sara',4,'PENDING',1,'SPAM',NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-05 07:30:00.000','2026-04-05 07:30:00.000',NULL);

-- mq-009: HARASSMENT report on "good starting point" comment perceived as condescending
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-009','solution_comment','comment-005','user-lily',5,'UNDER_REVIEW',1,'HARASSMENT','u-admin-001','2026-04-05 10:00:00.000',NULL,NULL,NULL,'题解作者认为评论居高临下。正在审查语境和语气。','2026-04-04 22:00:00.000','2026-04-05 10:00:00.000',NULL);

-- mq-010: OTHER report on mental health discussion post — resolved, no violation
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-010','forum_post','post-contest-tilt','user-david',1,'RESOLVED',1,'OTHER',NULL,NULL,'u-mod-001','2026-04-01 11:00:00.000','RESOLVED','关于比赛心态的建设性讨论。未发现违规。','2026-04-01 09:00:00.000','2026-04-01 11:00:00.000','2026-04-01 11:00:00.000');

-- mq-011: COPYRIGHT report on tourist's JS Hash Map solution — suspected plagiarism
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-011','solution','sol-004','user-tourist',3,'PENDING',2,'COPYRIGHT',NULL,NULL,NULL,NULL,NULL,NULL,'2026-04-05 14:00:00.000','2026-04-05 14:00:00.000',NULL);

-- mq-012: OTHER report on segment tree beats question — dismissed as legitimate
INSERT INTO `moderation_queue` (`id`, `entity_type`, `entity_id`, `author_id`, `priority`, `status`, `report_count`, `primary_category`, `assigned_to_id`, `assigned_at`, `reviewed_by_id`, `reviewed_at`, `resolution`, `resolution_note`, `created_at`, `updated_at`, `resolved_at`) VALUES
('mq-012','forum_comment','c-seg-3','user-kevin',1,'DISMISSED',1,'OTHER',NULL,NULL,'u-mod-001','2026-04-01 19:00:00.000','DISMISSED','关于线段树 beats 支持的合理提问。轻微拼写错误不构成违规。','2026-04-01 15:00:00.000','2026-04-01 19:00:00.000','2026-04-01 19:00:00.000');


-- ============================================================================
-- Table: reports (20 rows)
-- ============================================================================
-- Each queue item has 1-3 reports from different users

-- Reports for mq-001 (forum_post SPAM — post-rust-hashmap)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-001','user-benq','forum_post','post-rust-hashmap','SPAM','帖子包含疑似推广的外部链接。基准测试数据引用了商业产品。','https://i.imgur.com/example1.png','REVIEWED','mq-001','2026-04-01 20:00:00.000','2026-04-02 10:00:00.000'),
('rpt-002','user-jiangly','forum_post','post-rust-hashmap','SPAM','疑似以性能测试为掩护的赞助内容。','User has posted similar promotional content on other platforms.','REVIEWED','mq-001','2026-04-01 21:15:00.000','2026-04-02 10:00:00.000'),
('rpt-003','user-kevin','forum_post','post-rust-hashmap','OTHER','几乎相同的帖子因自我推广已从 LeetCode 讨论区被删除。','https://leetcode.com/discuss/removed-example','REVIEWED','mq-001','2026-04-01 22:30:00.000','2026-04-02 10:00:00.000');

-- Reports for mq-002 (forum_comment HARASSMENT — c-tilt-4)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-004','user-david','forum_comment','c-tilt-4','HARASSMENT','该评论将比赛中的退赛行为正常化，对新手竞技编程选手产生不良影响。','Post context: discussion about mental reset during contests','PENDING','mq-002','2026-04-03 07:30:00.000','2026-04-03 07:30:00.000'),
('rpt-005','user-lily','forum_comment','c-tilt-4','HARASSMENT','有害评论，打击坚持精神。括号中的「别学我」无法抵消负面影响。',NULL,'PENDING','mq-002','2026-04-03 08:15:00.000','2026-04-03 08:15:00.000');

-- Reports for mq-003 (solution WRONG_ANSWER — sol-002)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-006','user-benq','solution','sol-002','WRONG_ANSWER','暴力 O(n²) 在大数据量下会 TLE。这不是两数之和的有效解法，会误导初学者。','Problem constraints: n up to 10^4','RESOLVED','mq-003','2026-04-01 17:00:00.000','2026-04-02 14:30:00.000'),
('rpt-007','user-petr','solution','sol-002','WRONG_ANSWER','该解法声称能解决问题，但在最大约束下会失败。应标记为仅供学习参考。','NULL','RESOLVED','mq-003','2026-04-01 18:00:00.000','2026-04-02 14:30:00.000');

-- Reports for mq-004 (forum_post COPYRIGHT — post-segtree-visual)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-008','user-um_nik','forum_post','post-segtree-visual','COPYRIGHT','懒标记传播可视化疑似抄袭自 cp-algorithms.com。相同的配色方案和布局。','https://cp-algorithms.com/data_structures/segment_tree.html','DISMISSED','mq-004','2026-04-01 19:30:00.000','2026-04-02 09:00:00.000'),
('rpt-009','user-ecnerwala','forum_post','post-segtree-visual','COPYRIGHT','线段树懒标记传播的解释与我的 2023 年博客文章高度相似。','https://codeforces.com/blog/entry/12345','DISMISSED','mq-004','2026-04-01 20:00:00.000','2026-04-02 09:00:00.000');

-- Report for mq-005 (solution_comment OTHER — comment-003)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-010','user-tom','solution_comment','comment-003','OTHER','关于重复数字的离题问题。应该开单独的讨论帖，而非在该题解下评论。','NULL','DISMISSED','mq-005','2026-04-01 12:00:00.000','2026-04-01 16:00:00.000');

-- Reports for mq-006 (forum_comment MISINFORMATION — c-rust-3)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-011','user-benq','forum_comment','c-rust-3','MISINFORMATION','JS 对象键的字符串化并非现代 V8 引擎的主要性能问题。该建议已过时。','V8 blog: https://v8.dev/blog/hash-code','PENDING','mq-006','2026-04-04 10:45:00.000','2026-04-04 10:45:00.000'),
('rpt-012','user-alex','forum_comment','c-rust-3','MISINFORMATION','关于混合键类型的说法有误导性。V8 通过 hidden class 高效处理这种情况。','NULL','PENDING','mq-006','2026-04-04 11:20:00.000','2026-04-04 11:20:00.000');

-- Reports for mq-007 (solution WRONG_ANSWER — sol-006)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-013','user-sara','solution','sol-006','WRONG_ANSWER','排序扫描实现在区间端点重合时会失败。缺少边界条件处理。','Test case: [[1,4],[4,5]] should merge but this solution may not.','REVIEWED','mq-007','2026-04-02 18:30:00.000','2026-04-03 16:00:00.000'),
('rpt-014','user-david','solution','sol-006','WRONG_ANSWER','合并逻辑使用了严格小于比较，可能遗漏边界相等的重叠区间。','NULL','REVIEWED','mq-007','2026-04-02 20:00:00.000','2026-04-03 16:00:00.000');

-- Report for mq-008 (forum_comment SPAM — c-tilt-6)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-015','user-scott','forum_comment','c-tilt-6','SPAM','关于冷水「唤醒前额叶皮层」的不科学说法。这是伪科学。','https://www.healthline.com/health/cold-water','PENDING','mq-008','2026-04-05 07:30:00.000','2026-04-05 07:30:00.000');

-- Report for mq-009 (solution_comment HARASSMENT — comment-005)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-016','user-alex','solution_comment','comment-005','HARASSMENT','「对初学者来说是个很好的入门起点」这句话居高临下。暗示该解法不够专业。作者应尊重他人。','NULL','REVIEWED','mq-009','2026-04-04 22:00:00.000','2026-04-05 10:00:00.000');

-- Report for mq-010 (forum_post OTHER — post-contest-tilt)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-017','user-scott','forum_post','post-contest-tilt','OTHER','关于心理健康和心态崩溃的讨论不属于竞技编程平台。请保持技术讨论。','NULL','RESOLVED','mq-010','2026-04-01 09:00:00.000','2026-04-01 11:00:00.000');

-- Reports for mq-011 (solution COPYRIGHT — sol-004)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-018','user-chen','solution','sol-004','COPYRIGHT','该解法的变量命名和结构与我的 Codeforces 1791E 题解几乎完全相同。','https://codeforces.com/blog/entry/123456','PENDING','mq-011','2026-04-05 13:30:00.000','2026-04-05 13:30:00.000'),
('rpt-019','user-yuki','solution','sol-004','COPYRIGHT','与我的已发表题解使用相同的 Map 方法和注释风格。代码结构过于相似，不可能是巧合。','NULL','PENDING','mq-011','2026-04-05 14:00:00.000','2026-04-05 14:00:00.000');

-- Report for mq-012 (forum_comment OTHER — c-seg-3)
INSERT INTO `reports` (`id`, `reporter_id`, `entity_type`, `entity_id`, `category`, `reason`, `evidence`, `status`, `queue_id`, `created_at`, `updated_at`) VALUES
('rpt-020','user-jiangly','forum_comment','c-seg-3','OTHER','低质量评论，"beatbeats" 是拼写错误。如果问的是线段树 beats，至少把名字拼对。','NULL','DISMISSED','mq-012','2026-04-01 15:00:00.000','2026-04-01 19:00:00.000');


-- ============================================================================
-- Table: moderation_actions (15 rows)
-- ============================================================================
-- Action history for queue items that have been reviewed

-- Actions for mq-001 (UNDER_REVIEW)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-001','mq-001','RESOLVED','u-mod-001','已认领审核。正在检查帖子中的外部链接是否为推广内容。',NULL,'2026-04-02 10:00:00.000');

-- Actions for mq-003 (RESOLVED → HIDDEN)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-002','mq-003','WARNED','u-mod-001','已通知题解作者：暴力解法需要在教育语境下添加明确的时间复杂度声明。',NULL,'2026-04-02 14:00:00.000'),
('ma-003','mq-003','HIDDEN','u-mod-001','题解已隐藏。缺少适当声明的 O(n²) 解法会误导初学者。',NULL,'2026-04-02 14:30:00.000');

-- Actions for mq-004 (DISMISSED)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-004','mq-004','DISMISSED','u-admin-001','版权投诉已调查。可视化为原创——配色、布局和代码与引用来源均不同。',NULL,'2026-04-02 09:00:00.000');

-- Actions for mq-005 (DISMISSED)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-005','mq-005','DISMISSED','u-mod-001','该评论是对哈希表解法中重复元素处理的合理提问。',NULL,'2026-04-01 16:00:00.000');

-- Actions for mq-007 (APPEAL_PENDING)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-006','mq-007','WARNED','u-mod-001','已通知作者关于合并区间解法中被举报的边界条件问题。',NULL,'2026-04-03 10:00:00.000'),
('ma-007','mq-007','HIDDEN','u-mod-001','题解已隐藏待审。多条举报涉及边界条件处理。',NULL,'2026-04-03 16:00:00.000'),
('ma-008','mq-007','APPEAL_PENDING','u-mod-001','作者提交申诉称解法正确。升级为高级审核。',NULL,'2026-04-04 09:00:00.000');

-- Actions for mq-009 (UNDER_REVIEW)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-009','mq-009','RESOLVED','u-admin-001','已认领管理员审核。评估评论语气是否构成骚扰或仅为真诚的赞美。',NULL,'2026-04-05 10:00:00.000');

-- Actions for mq-010 (RESOLVED)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-010','mq-010','RESOLVED','u-mod-001','心理健康讨论具有建设性，与竞技编程相关。未发现违规。',NULL,'2026-04-01 11:00:00.000');

-- Actions for mq-012 (DISMISSED)
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-011','mq-012','DISMISSED','u-mod-001','"segment tree beats" 的拼写错误是轻微的。关于 beats 支持的问题是合理的。',NULL,'2026-04-01 19:00:00.000');

-- Additional context actions for completed items
INSERT INTO `moderation_actions` (`id`, `queue_id`, `action`, `performed_by_id`, `note`, `duration_days`, `created_at`) VALUES
('ma-012','mq-001','HIDDEN','u-mod-001','帖子已隐藏待链接审查。外部 URL 已标记安全扫描。',NULL,'2026-04-02 11:30:00.000'),
('ma-013','mq-004','RESTORED','u-admin-001','帖子已完全恢复。内容作者（tourist）的申诉已通过——确认为原创内容。',NULL,'2026-04-02 15:00:00.000'),
('ma-014','mq-003','RESOLVED','u-admin-001','申诉已审核。维持原决定——作者应在重新发布前添加复杂度警告。',NULL,'2026-04-03 09:00:00.000'),
('ma-015','mq-007','APPEAL_APPROVED','u-admin-001','申诉通过。解法逻辑对于所述方法正确。边界条件处理得当。恢复题解。',NULL,'2026-04-05 11:00:00.000');


-- ============================================================================
-- Table: appeals (3 rows)
-- ============================================================================

-- appeal-001: user-max appeals the hidden sort-and-sweep solution (mq-007)
INSERT INTO `appeals` (`id`, `queue_id`, `appellant_id`, `reason`, `evidence`, `status`, `reviewed_by_id`, `reviewed_at`, `response`, `created_at`, `updated_at`) VALUES
('appeal-001','mq-007','user-max','我的排序扫描解法正确处理了所有边界条件，包括共享端点 [[1,4],[4,5]]、嵌套区间和空输入。排序扫描是 CLRS 教材推荐的 O(n log n) 标准算法。我认为这些举报是出于恶意的竞争对手行为。','参考：CLRS 第 14.3 章——区间树。另见 LeetCode 官方题解使用了相同方法。','APPROVED','u-admin-001','2026-04-05 11:00:00.000','申诉通过，代码审查确认解法正确处理了边界条件。举报基于对合并逻辑的误解。题解已恢复。','2026-04-04 08:30:00.000','2026-04-05 11:00:00.000');

-- appeal-002: user-tourist appeals copyright dismissal on segment tree post (mq-004)
INSERT INTO `appeals` (`id`, `queue_id`, `appellant_id`, `reason`, `evidence`, `status`, `reviewed_by_id`, `reviewed_at`, `response`, `created_at`, `updated_at`) VALUES
('appeal-002','mq-004','user-tourist','所有可视化都是我使用 D3.js 自己创作的。cp-algorithms.com 的可视化使用完全不同的渲染方式（SVG vs Canvas）和不同配色。我可以提供原始 D3.js 源码和 commit 历史作为证明。','包含逐步开发过程的 commit 历史的 GitHub 仓库。','APPROVED','u-admin-001','2026-04-02 15:00:00.000','内容已核实为原创。版权投诉缺乏依据。帖子已恢复，已通知举报人。','2026-04-02 10:00:00.000','2026-04-02 15:00:00.000');

-- appeal-003: user-alex appeals hidden brute force solution (mq-003)
INSERT INTO `appeals` (`id`, `queue_id`, `appellant_id`, `reason`, `evidence`, `status`, `reviewed_by_id`, `reviewed_at`, `response`, `created_at`, `updated_at`) VALUES
('appeal-003','mq-003','user-alex','暴力枚举是有效的教育起点。我的解法明确标注了这是暴力方法。每本算法教材都在优化之前先教暴力。初学者需要理解为什么暴力不够好，才能体会 O(n) 解法的价值。','解法标题明确写了「暴力解法」——意图是教育性的。','REJECTED','u-admin-001','2026-04-03 09:00:00.000','虽然教育意图得到认可，但该解法缺少突出的时间复杂度警告。多位用户举报被误导。请在添加 O(n²) 性能限制声明后再申请重新发布。','2026-04-02 15:30:00.000','2026-04-03 09:00:00.000');

SET FOREIGN_KEY_CHECKS=1;
