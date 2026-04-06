SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V12__notification_seed_data
-- Seed data for notification system
-- Tables affected: notifications, notification_preferences, system_announcements


-- ============================================================================
-- Table: notification_preferences (8 rows)
-- ============================================================================
-- Different preference combinations for active users to showcase admin UI

INSERT INTO `notification_preferences` (`id`, `user_id`, `communication`, `marketing`, `security`, `system`, `created_at`, `updated_at`) VALUES
('np-001','user-tourist',1,0,1,1,NOW(3),NOW(3)),
('np-002','user-benq',1,1,1,1,NOW(3),NOW(3)),
('np-003','user-jiangly',0,0,1,0,NOW(3),NOW(3)),
('np-004','user-alex',1,0,1,1,NOW(3),NOW(3)),
('np-005','user-max',1,0,1,1,NOW(3),NOW(3)),
('np-006','user-sara',1,1,1,0,NOW(3),NOW(3)),
('np-007','user-david',1,0,1,1,NOW(3),NOW(3)),
('np-008','user-ecnerwala',0,0,1,1,NOW(3),NOW(3));


-- ============================================================================
-- Table: notifications (30 rows)
-- ============================================================================
-- Covers all 8 types: COMMENT(4), REPLY(3), MENTION(3), UPVOTE(4), FOLLOW(3),
--   SUBMISSION(5), CONTEST(4), SYSTEM(4)
-- Covers all 4 categories: COMMUNICATION(14), SYSTEM(13), SECURITY(2), MARKETING(1)
-- Read status mix: ~60% unread, ~40% read
-- All foreign keys reference real entities from V1-V11 seed data


-- ── COMMENT (4 rows, category: COMMUNICATION) ──────────────────────────────

-- notif-cmt-001: stack_unwind commented on tourist's segment tree visualization post
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-cmt-001','user-tourist','COMMENT','COMMUNICATION','stack_unwind 评论了你的帖子「Segment Tree Beats 可视化指南」','"Lazy propagation 的可视化太清晰了，请问这个是用 D3.js 画的吗？想参考一下你的渲染逻辑。"','/forum/post-segtree-visual','{"entity_type":"forum_post","entity_id":"post-segtree-visual","commenter_id":"u-002","commenter_username":"stack_unwind"}',0,NULL,NOW(3),NOW(3));

-- notif-cmt-002: chen_master commented on alex's brute force Two Sum solution
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-cmt-002','user-alex','COMMENT','COMMUNICATION','chen_master 评论了你的题解「Two Sum 暴力解法」','"暴力解法确实是最直观的入门方式，建议加一句时间复杂度 O(n²) 的提示，方便新手理解优化方向。"','/solutions/sol-002','{"entity_type":"solution","entity_id":"sol-002","problem_id":1,"problem_slug":"two-sum","commenter_id":"user-chen","commenter_username":"chen_master"}',1,NOW(3),NOW(3),NOW(3));

-- notif-cmt-003: jiangly commented on david's contest tilt post
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-cmt-003','user-david','COMMENT','COMMUNICATION','jiangly 评论了你的帖子「比赛心态调整」','"深有同感。我之前 CF 连掉两场 Div1 之后也是靠暂停一周恢复的。分享一个方法：每场赛前写 3 个具体目标，不关注排名。"','/forum/post-contest-tilt','{"entity_type":"forum_post","entity_id":"post-contest-tilt","commenter_id":"user-jiangly","commenter_username":"jiangly"}',0,NULL,NOW(3),NOW(3));

-- notif-cmt-004: sara_dev commented on max's merge intervals solution (after appeal)
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-cmt-004','user-max','COMMENT','COMMUNICATION','sara_dev 评论了你的题解「合并区间 Sort-and-Sweep」','"申诉通过了！恭喜。边界条件确实处理得很干净，我之前的报告是误解了 merge 逻辑。"','/solutions/sol-006','{"entity_type":"solution","entity_id":"sol-006","problem_id":3,"problem_slug":"merge-intervals","commenter_id":"user-sara","commenter_username":"sara_dev"}',0,NULL,NOW(3),NOW(3));


-- ── REPLY (3 rows, category: COMMUNICATION) ────────────────────────────────

-- notif-reply-001: benq replied to kevin's comment on Rust HashMap post
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-reply-001','user-kevin','REPLY','COMMUNICATION','Benq 回复了你在「Rust HashMap 性能深度对比」下的评论','"V8 的 hidden class 机制确实让 object key 的类型问题没那么严重了，但在 HashMap 场景下 Rust 的 typed key 还是更安全。"','/forum/post-rust-hashmap','{"entity_type":"forum_comment","entity_id":"c-rust-3","post_id":"post-rust-hashmap","replier_id":"user-benq","replier_username":"Benq"}',1,NOW(3),NOW(3),NOW(3));

-- notif-reply-002: david_algo replied to sara's comment on contest tilt post
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-reply-002','user-sara','REPLY','COMMUNICATION','david_algo 回复了你的评论','"冷水洗脸确实有生理学依据——mammalian dive reflex 会激活副交感神经。不过比赛时更推荐 4-7-8 呼吸法，不需要离开键盘。"','/forum/post-contest-tilt','{"entity_type":"forum_comment","entity_id":"c-tilt-2","post_id":"post-contest-tilt","replier_id":"user-david","replier_username":"david_algo"}',0,NULL,NOW(3),NOW(3));

-- notif-reply-003: tom_quick replied to lily's follow-up question on solution
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-reply-003','user-lily','REPLY','COMMUNICATION','tom_quick 回复了你对题解的追问','"这道题确实可以不处理 duplicates。题目保证了每个元素只有唯一解，所以 map 里不会出现覆盖的情况。"','/solutions/sol-001','{"entity_type":"solution_comment","entity_id":"comment-003","solution_id":"sol-001","replier_id":"user-tom","replier_username":"tom_quick"}',1,NOW(3),NOW(3),NOW(3));


-- ── MENTION (3 rows, category: COMMUNICATION) ──────────────────────────────

-- notif-ment-001: tourist mentioned benq in the Rust HashMap post
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-ment-001','user-benq','MENTION','COMMUNICATION','tourist 在帖子中提到了你','"@Benq 之前你在 CF blog 里分析的 SwissTable 实现我也参考了，特别是 SIMD probing 那部分。"','/forum/post-rust-hashmap','{"entity_type":"forum_post","entity_id":"post-rust-hashmap","mentioner_id":"user-tourist","mentioner_username":"tourist"}',0,NULL,NOW(3),NOW(3));

-- notif-ment-002: jiangly mentioned ecnerwala in Two Sum solution discussion
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-ment-002','user-ecnerwala','MENTION','COMMUNICATION','jiangly 在题解中提到了你','"@ecnerwala 你之前 AtCoder ABC 上的 one-pass hash 解法非常优雅，我把思路整理进来了。"','/solutions/sol-001','{"entity_type":"solution","entity_id":"sol-001","problem_id":1,"problem_slug":"two-sum","mentioner_id":"user-jiangly","mentioner_username":"jiangly"}',1,NOW(3),NOW(3),NOW(3));

-- notif-ment-003: ecnerwala mentioned jiangly in segment tree comment
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-ment-003','user-jiangly','MENTION','COMMUNICATION','ecnerwala 在评论中提到了你','"@jiangly 这个 lazy tag 的处理方式和你在 Yandex.Algorithm 2023 决赛用的那个 segment tree beats 是不是同一个思路？"','/forum/post-segtree-visual','{"entity_type":"forum_comment","entity_id":"c-seg-4","post_id":"post-segtree-visual","mentioner_id":"user-ecnerwala","mentioner_username":"ecnerwala"}',0,NULL,NOW(3),NOW(3));


-- ── UPVOTE (4 rows, category: COMMUNICATION) ───────────────────────────────

-- notif-up-001: tourist's JS HashMap solution reached 50 upvotes
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-up-001','user-tourist','UPVOTE','COMMUNICATION','你的题解「Two Sum JS HashMap 解法」获得了 50 个赞','你的 Two Sum 题解持续受到社区好评，目前累计 50 个赞。','/solutions/sol-004','{"entity_type":"solution","entity_id":"sol-004","problem_id":1,"problem_slug":"two-sum","upvote_count":50}',1,NOW(3),NOW(3),NOW(3));

-- notif-up-002: david's contest tilt post reached 128 upvotes
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-up-002','user-david','UPVOTE','COMMUNICATION','你的帖子「比赛心态调整」获得了 128 个赞','这个话题引发了广泛共鸣，你的分享帮助了很多 Competitive Programmer。','/forum/post-contest-tilt','{"entity_type":"forum_post","entity_id":"post-contest-tilt","upvote_count":128}',1,NOW(3),NOW(3),NOW(3));

-- notif-up-003: benq's Two Sum O(n) solution reached 200 upvotes
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-up-003','user-benq','UPVOTE','COMMUNICATION','你的题解「Two Sum O(n) HashMap 解法」获得了 200 个赞','恭喜！你的题解成为 Two Sum 题目下最受欢迎的解法之一。','/solutions/sol-001','{"entity_type":"solution","entity_id":"sol-001","problem_id":1,"problem_slug":"two-sum","upvote_count":200}',0,NULL,NOW(3),NOW(3));

-- notif-up-004: chen's Rust performance post reached 85 upvotes
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-up-004','user-chen','UPVOTE','COMMUNICATION','你的帖子「Rust HashMap 性能深度对比」获得了 85 个赞','性能对比数据受到社区认可，很多用户表示对选型很有帮助。','/forum/post-rust-hashmap','{"entity_type":"forum_post","entity_id":"post-rust-hashmap","upvote_count":85}',1,NOW(3),NOW(3),NOW(3));


-- ── FOLLOW (3 rows, category: COMMUNICATION) ───────────────────────────────

-- notif-fol-001: Benq followed tourist
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-fol-001','user-tourist','FOLLOW','COMMUNICATION','Benq 关注了你','Benq 开始关注你的动态，你可以在个人主页查看互关状态。','/users/Benq','{"follower_id":"user-benq","follower_username":"Benq","total_followers":1523}',0,NULL,NOW(3),NOW(3));

-- notif-fol-002: ecnerwala followed benq
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-fol-002','user-benq','FOLLOW','COMMUNICATION','ecnerwala 关注了你','ecnerwala 开始关注你的动态。','/users/ecnerwala','{"follower_id":"user-ecnerwala","follower_username":"ecnerwala","total_followers":2341}',1,NOW(3),NOW(3),NOW(3));

-- notif-fol-003: Petr followed jiangly
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-fol-003','user-jiangly','FOLLOW','COMMUNICATION','Petr 关注了你','Petr 开始关注你的动态。','/users/Petr','{"follower_id":"user-petr","follower_username":"Petr","total_followers":1897}',0,NULL,NOW(3),NOW(3));


-- ── SUBMISSION (5 rows, category: SYSTEM) ──────────────────────────────────

-- notif-sub-001: alex passed Two Sum
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sub-001','user-alex','SUBMISSION','SYSTEM','提交通过：Two Sum','Runtime: 2ms | Memory: 42.1MB | 击败 89.7% 的用户','/problems/two-sum/submissions/latest','{"entity_type":"submission","problem_id":1,"problem_slug":"two-sum","status":"Accepted","runtime_ms":2,"memory_mb":42.1,"beat_percent":89.7,"language":"Java"}',1,NOW(3),NOW(3),NOW(3));

-- notif-sub-002: max got TLE on Median of Two Sorted Arrays
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sub-002','user-max','SUBMISSION','SYSTEM','提交失败：Median of Two Sorted Arrays — Time Limit Exceeded','Test Case #26 超时。提示：考虑 O(log(min(m,n))) 的二分解法。','/problems/median-of-two-sorted-arrays/submissions/latest','{"entity_type":"submission","problem_id":4,"problem_slug":"median-of-two-sorted-arrays","status":"Time Limit Exceeded","failed_test":26,"language":"Python"}',0,NULL,NOW(3),NOW(3));

-- notif-sub-003: sara passed Number of Islands with great performance
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sub-003','user-sara','SUBMISSION','SYSTEM','提交通过：Number of Islands','Runtime: 4ms | Memory: 38.5MB | 击败 92.3% 的用户 — BFS 解法表现优异','/problems/number-of-islands/submissions/latest','{"entity_type":"submission","problem_id":5,"problem_slug":"number-of-islands","status":"Accepted","runtime_ms":4,"memory_mb":38.5,"beat_percent":92.3,"language":"Java"}',1,NOW(3),NOW(3),NOW(3));

-- notif-sub-004: emma got WA on Merge Intervals
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sub-004','user-emma','SUBMISSION','SYSTEM','提交失败：Merge Intervals — Wrong Answer on Test #47','预期输出 [[1,6],[8,10],[15,18]]，实际输出 [[1,5],[8,10],[15,18]]。提示：检查区间合并时的右边界取值。','/problems/merge-intervals/submissions/latest','{"entity_type":"submission","problem_id":3,"problem_slug":"merge-intervals","status":"Wrong Answer","failed_test":47,"expected":"[[1,6],[8,10],[15,18]]","actual":"[[1,5],[8,10],[15,18]]","language":"C++"}',0,NULL,NOW(3),NOW(3));

-- notif-sub-005: kevin passed Longest Substring Without Repeating Characters
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sub-005','user-kevin','SUBMISSION','SYSTEM','提交通过：Longest Substring Without Repeating Characters','Runtime: 4ms | Memory: 40.2MB | 击败 85.1% 的用户','/problems/longest-substring-without-repeating-characters/submissions/latest','{"entity_type":"submission","problem_id":2,"problem_slug":"longest-substring-without-repeating-characters","status":"Accepted","runtime_ms":4,"memory_mb":40.2,"beat_percent":85.1,"language":"Go"}',1,NOW(3),NOW(3),NOW(3));


-- ── CONTEST (4 rows, category: SYSTEM) ─────────────────────────────────────

-- notif-con-001: Weekly 477 starts in 1 hour (reminder)
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-con-001','user-tourist','CONTEST','SYSTEM','周赛 477 即将开始','距离 UltiCode 周赛 477 开赛还有 1 小时。本次共 4 道题，难度递增。准备好你的编程环境！','/contests/contest-weekly-477','{"entity_type":"contest","entity_id":"contest-weekly-477","contest_name":"UltiCode Weekly Contest 477","start_time":"2026-04-06T10:00:00","duration_minutes":90,"problem_count":4}',0,NULL,NOW(3),NOW(3));

-- notif-con-002: benq won Weekly 476
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-con-002','user-benq','CONTEST','SYSTEM','恭喜获得 UltiCode 周赛 476 第 1 名','你以 4 题全通、用时 78 分钟的成绩夺得周赛 476 冠军。Rating +42。','/contests/contest-weekly-476/ranking','{"entity_type":"contest","entity_id":"contest-weekly-476","contest_name":"UltiCode Weekly Contest 476","rank":1,"solved":4,"total_time_min":78,"rating_change":42}',1,NOW(3),NOW(3),NOW(3));

-- notif-con-003: jiangly's ranking updated in Biweekly 170
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-con-003','user-jiangly','CONTEST','SYSTEM','双周赛 170 排名已更新','你在 UltiCode 双周赛 170 中获得第 5 名（3/4 题，用时 65 分钟）。Rating +18。','/contests/contest-biweekly-170/ranking','{"entity_type":"contest","entity_id":"contest-biweekly-170","contest_name":"UltiCode Biweekly Contest 170","rank":5,"solved":3,"total_problems":4,"total_time_min":65,"rating_change":18}',1,NOW(3),NOW(3),NOW(3));

-- notif-con-004: Weekly 477 ended, ecnerwala can check rankings
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-con-004','user-ecnerwala','CONTEST','SYSTEM','周赛 477 已结束，查看排行榜','UltiCode 周赛 477 已结束，你获得第 3 名。查看完整排行榜和题解讨论。','/contests/contest-weekly-477/ranking','{"entity_type":"contest","entity_id":"contest-weekly-477","contest_name":"UltiCode Weekly Contest 477","rank":3,"solved":4,"rating_change":28}',0,NULL,NOW(3),NOW(3));


-- ── SYSTEM (4 rows, category: SECURITY / SYSTEM / MARKETING) ────────────────

-- notif-sys-001: alex's password changed (category: SECURITY)
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sys-001','user-alex','SYSTEM','SECURITY','密码修改成功','你的 UltiCode 账户密码已于 2026-04-02 09:30 成功修改。如果这不是你本人操作，请立即联系管理员。','/settings/security','{"action":"password_changed","ip":"203.0.113.42","user_agent":"Chrome 124 / macOS","timestamp":"2026-04-02T09:30:00"}',1,NOW(3),NOW(3),NOW(3));

-- notif-sys-002: max's new device login detected (category: SECURITY)
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sys-002','user-max','SYSTEM','SECURITY','检测到新设备登录','你的账户在新设备上登录。设备：Windows 11 / Firefox 125，IP：198.51.100.17，位置：上海。如果这不是你本人操作，请立即修改密码。','/settings/security','{"action":"new_device_login","ip":"198.51.100.17","location":"Shanghai","device":"Windows 11 / Firefox 125","timestamp":"2026-04-04T18:20:00"}',0,NULL,NOW(3),NOW(3));

-- notif-sys-003: alex's solution hidden by admin (after mq-003 review)
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sys-003','user-alex','SYSTEM','SYSTEM','你的题解已被管理员隐藏','你的题解「Two Sum 暴力解法」因缺少时间复杂度警告被管理员隐藏。请在添加 O(n²) 性能限制说明后提交申诉恢复。','/solutions/sol-002','{"action":"content_hidden","entity_type":"solution","entity_id":"sol-002","reason":"缺少时间复杂度警告，可能误导初学者","moderation_queue_id":"mq-003"}',1,NOW(3),NOW(3),NOW(3));

-- notif-sys-004: tourist's appeal approved, content restored
INSERT INTO `notifications` (`id`, `user_id`, `type`, `category`, `title`, `body`, `link`, `metadata`, `is_read`, `read_at`, `created_at`, `updated_at`) VALUES
('notif-sys-004','user-tourist','SYSTEM','SYSTEM','你的申诉已通过，内容已恢复','你针对「Segment Tree Beats 可视化指南」的版权申诉已通过审核。管理员确认内容为原创，帖子已恢复。','/forum/post-segtree-visual','{"action":"appeal_approved","entity_type":"forum_post","entity_id":"post-segtree-visual","appeal_id":"appeal-002","moderation_queue_id":"mq-004"}',0,NULL,NOW(3),NOW(3));


-- ============================================================================
-- Table: system_announcements (5 rows)
-- ============================================================================
-- Platform-level announcements created by admin

-- ann-001: Major platform upgrade
INSERT INTO `system_announcements` (`id`, `title`, `content`, `type`, `created_by`, `created_at`, `updated_at`) VALUES
('ann-001','UltiCode v2.0 全新升级','我们很高兴地宣布 UltiCode v2.0 正式发布！本次更新包括：\n\n1. 全新的代码编辑器，支持 15+ 编程语言\n2. 实时协作解题功能（Beta）\n3. 重新设计的比赛系统和排行榜\n4. 改进的题解和讨论区体验\n5. 全新的深色模式\n\n感谢社区每一位成员的反馈和建议，让我们一起把 UltiCode 变得更好！','SYSTEM','u-admin-001',NOW(3),NOW(3));

-- ann-002: Weekly 477 problem type preview
INSERT INTO `system_announcements` (`id`, `title`, `content`, `type`, `created_by`, `created_at`, `updated_at`) VALUES
('ann-002','周赛 477 题目预告','UltiCode 周赛 477 将于本周日 10:00 开赛，共 4 道题：\n\n- 第 1 题：数组 / 模拟（Easy）\n- 第 2 题：贪心 / 排序（Medium）\n- 第 3 题：动态规划（Medium）\n- 第 4 题：图论 / 树形 DP（Hard）\n\n比赛时长 90 分钟，建议提前准备好编程环境。','CONTEST','u-admin-001',NOW(3),NOW(3));

-- ann-003: Security policy update
INSERT INTO `system_announcements` (`id`, `title`, `content`, `type`, `created_by`, `created_at`, `updated_at`) VALUES
('ann-003','安全更新：密码策略变更','为了提升账户安全性，我们将于 2026-04-10 起实施新的密码策略：\n\n1. 密码最低长度从 8 位提升至 10 位\n2. 必须包含大小写字母、数字和特殊字符\n3. 不允许使用近 5 次使用过的密码\n4. 连续登录失败 5 次将锁定账户 30 分钟\n\n请在生效日期前更新你的密码。如有疑问，请联系 security@ulticode.com。','SYSTEM','u-admin-001',NOW(3),NOW(3));

-- ann-004: New Rust language support
INSERT INTO `system_announcements` (`id`, `title`, `content`, `type`, `created_by`, `created_at`, `updated_at`) VALUES
('ann-004','新增 Rust 语言支持','UltiCode 现已全面支持 Rust 编程语言！\n\n- 支持 Rust 2021 edition\n- 内置常用 crate：serde, itertools, regex\n- 新增 10 道 Rust 专属练习题\n- Rust 题解区已开放\n\n感谢社区中 Rust 爱好者的长期呼吁和贡献。','SYSTEM','u-admin-001',NOW(3),NOW(3));

-- ann-005: Community guidelines update
INSERT INTO `system_announcements` (`id`, `title`, `content`, `type`, `created_by`, `created_at`, `updated_at`) VALUES
('ann-005','UltiCode 社区规范更新','我们更新了社区行为准则，主要变更如下：\n\n1. 新增「题解质量标准」章节，明确题解应包含思路说明和复杂度分析\n2. 完善举报和申诉流程，新增管理员响应时间承诺（48 小时内）\n3. 强化讨论区礼仪规范，禁止人身攻击和无意义灌水\n4. 新增 AI 辅助生成内容的标注要求\n\n完整规范请查看：/community/guidelines\n\n更新将于 2026-04-15 生效。','SYSTEM','u-admin-001',NOW(3),NOW(3));

SET FOREIGN_KEY_CHECKS=1;
