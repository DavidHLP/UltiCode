-- Seed Forum Test Data
-- All columns verified against actual baseline schema

-- ============================================================
-- 1. Test Users (needed for foreign key references)
-- ============================================================
INSERT IGNORE INTO `users` (`id`, `username`, `name`, `email`, `password`, `role`, `is_active`, `joined_at`)
VALUES
('user-alice-001', 'alice', 'Alice', 'alice@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', 1, NOW(3)),
('user-bob-002', 'bob', 'Bob', 'bob@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', 1, NOW(3)),
('user-charlie-003', 'charlie', 'Charlie', 'charlie@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', 1, NOW(3));

-- ============================================================
-- 2. Forum Users
-- columns: id, username, avatar, karma
-- ============================================================
INSERT IGNORE INTO `forum_users` (`id`, `username`, `avatar`, `karma`)
VALUES
('fu-admin-001', '超级管理员', NULL, 1000),
('fu-alice-001', 'Alice', NULL, 150),
('fu-bob-002', 'Bob', NULL, 200),
('fu-charlie-003', 'Charlie', NULL, 300);

-- ============================================================
-- 3. Forum Communities
-- columns: id, name, slug, description, members, online, icon, color, banner, posts_count, posts_today, posts_week, is_official, is_featured, sort_order, created_at, visibility
-- ============================================================
INSERT IGNORE INTO `forum_communities` (`id`, `name`, `slug`, `description`, `members`, `online`, `icon`, `color`, `banner`, `posts_count`, `posts_today`, `posts_week`, `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`)
VALUES
('fc-algo', '算法讨论', 'algorithm', '算法与数据结构讨论社区', 156, 12, '🧮', '#3B82F6', NULL, 234, 5, 28, 1, 1, 1, NOW(3), 'PUBLIC'),
('fc-share', '经验分享', 'experience', '刷题经验与技巧分享', 89, 8, '💡', '#10B981', NULL, 156, 3, 15, 0, 1, 2, NOW(3), 'PUBLIC'),
('fc-contest', '竞赛专区', 'contest', 'ACM/ICPC、LeetCode 周赛等竞赛讨论', 67, 5, '🏆', '#F59E0B', NULL, 98, 2, 10, 0, 0, 3, NOW(3), 'PUBLIC'),
('fc-help', '问答求助', 'qna', '遇到问题？在这里寻求帮助', 210, 18, '❓', '#EF4444', NULL, 445, 8, 42, 0, 1, 0, NOW(3), 'PUBLIC');

-- ============================================================
-- 4. Forum Community Members
-- columns: id, community_id, user_id, role, joined_at
-- ============================================================
INSERT IGNORE INTO `forum_community_members` (`id`, `community_id`, `user_id`, `role`, `joined_at`)
VALUES
('fcm-001', 'fc-algo', 'admin-001', 'OWNER', NOW(3)),
('fcm-002', 'fc-algo', 'user-alice-001', 'MEMBER', NOW(3)),
('fcm-003', 'fc-algo', 'user-bob-002', 'MODERATOR', NOW(3)),
('fcm-004', 'fc-share', 'admin-001', 'OWNER', NOW(3)),
('fcm-005', 'fc-share', 'user-charlie-003', 'MEMBER', NOW(3)),
('fcm-006', 'fc-contest', 'admin-001', 'OWNER', NOW(3)),
('fcm-007', 'fc-contest', 'user-alice-001', 'MEMBER', NOW(3)),
('fcm-008', 'fc-help', 'admin-001', 'OWNER', NOW(3)),
('fcm-009', 'fc-help', 'user-bob-002', 'MODERATOR', NOW(3)),
('fcm-010', 'fc-help', 'user-charlie-003', 'MEMBER', NOW(3));

-- ============================================================
-- 5. Forum Community Rules
-- columns: id, community_id, title, body, sort_order, created_at
-- ============================================================
INSERT IGNORE INTO `forum_community_rules` (`id`, `community_id`, `title`, `body`, `sort_order`, `created_at`)
VALUES
('fcr-001', 'fc-algo', '尊重他人', '请保持友善和尊重，禁止人身攻击', 1, NOW(3)),
('fcr-002', 'fc-algo', '标明来源', '转载题目或题解请标明来源', 2, NOW(3)),
('fcr-003', 'fc-share', '原创优先', '鼓励分享原创经验和解题思路', 1, NOW(3)),
('fcr-004', 'fc-contest', '禁止作弊', '竞赛期间禁止讨论未结束的比赛题目', 1, NOW(3));

-- ============================================================
-- 6. Forum Tags
-- columns: id, name, slug, description, color, usage_count, created_at
-- ============================================================
INSERT IGNORE INTO `forum_tags` (`id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`)
VALUES
('ft-dp', '动态规划', 'dynamic-programming', '动态规划相关讨论', '#3B82F6', 45, NOW(3)),
('ft-graph', '图论', 'graph-theory', '图论算法讨论', '#10B981', 32, NOW(3)),
('ft-greedy', '贪心', 'greedy', '贪心算法讨论', '#F59E0B', 28, NOW(3)),
('ft-string', '字符串', 'string', '字符串处理算法', '#EF4444', 51, NOW(3)),
('ft-math', '数学', 'math', '数学相关题目讨论', '#8B5CF6', 22, NOW(3)),
('ft-contest', '竞赛', 'contest', '各类编程竞赛讨论', '#EC4899', 38, NOW(3));

-- ============================================================
-- 7. Forum Posts
-- columns: id, community_id, user_id, permalink, title, flair_type, flair_label, tags, excerpt, media, recommendation, vote_state, is_saved, impressions, is_pinned, is_locked, created_at, stats, views, is_flagged, is_deleted
-- ============================================================
INSERT IGNORE INTO `forum_posts` (`id`, `community_id`, `user_id`, `permalink`, `title`, `flair_type`, `flair_label`, `tags`, `excerpt`, `vote_state`, `impressions`, `is_pinned`, `is_locked`, `created_at`, `stats`, `views`)
VALUES
('fp-001', 'fc-algo', 'admin-001', '/forum/algorithm/posts/dp-optimization', '动态规划优化技巧总结', 'discussion', '讨论', '["动态规划"]', '总结了常见的DP优化方法，包括滚动数组、状态压缩等技巧...', 'upvoted', 156, 1, 0, NOW(3), '{"upvotes": 25, "downvotes": 1, "comments": 8}', 230),
('fp-002', 'fc-algo', 'user-alice-001', '/forum/algorithm/posts/two-pointer', '双指针法在数组问题中的应用', 'discussion', '讨论', '["字符串"]', '双指针法是解决数组/字符串问题的高效方法...', 'upvoted', 89, 0, 0, NOW(3), '{"upvotes": 15, "downvotes": 0, "comments": 5}', 145),
('fp-003', 'fc-share', 'user-charlie-003', '/forum/experience/posts/leetcode-strategy', 'LeetCode 刷题策略分享', 'showcase', '分享', '["竞赛"]', '分享一下我从 0 到 300 题的刷题经验和策略...', 'upvoted', 234, 0, 0, NOW(3), '{"upvotes": 42, "downvotes": 2, "comments": 15}', 380),
('fp-004', 'fc-contest', 'user-bob-002', '/forum/contest/posts/weekly-421', '周赛 421 题解讨论', 'question', '提问', '["竞赛","数学"]', '周赛421第四题的贪心解法讨论...', 'neutral', 67, 0, 0, NOW(3), '{"upvotes": 8, "downvotes": 0, "comments": 12}', 95),
('fp-005', 'fc-help', 'user-alice-001', '/forum/qna/posts/graph-bfs', 'BFS 遍历图的正确姿势', 'question', '提问', '["图论"]', '请问用BFS遍历图时如何处理环？', 'upvoted', 45, 0, 0, NOW(3), '{"upvotes": 5, "downvotes": 0, "comments": 3}', 78),
('fp-006', 'fc-help', 'admin-001', '/forum/qna/posts/cpp-stl', 'C++ STL 常用容器性能对比', 'discussion', '讨论', '["贪心"]', '总结了 vector、set、map、unordered_map 等容器的性能差异...', 'upvoted', 312, 1, 0, NOW(3), '{"upvotes": 55, "downvotes": 1, "comments": 20}', 520);

-- ============================================================
-- 8. Forum Post Tag Relations
-- columns: post_id, tag_id (composite primary key, no id or created_at)
-- ============================================================
INSERT IGNORE INTO `forum_post_tag_relations` (`post_id`, `tag_id`)
VALUES
('fp-001', 'ft-dp'),
('fp-002', 'ft-string'),
('fp-003', 'ft-contest'),
('fp-004', 'ft-contest'),
('fp-004', 'ft-math'),
('fp-005', 'ft-graph'),
('fp-006', 'ft-greedy');

-- ============================================================
-- 9. Forum Comments
-- columns: id, post_id, parent_id, author_id, body, markdown, created_at, edited_at, is_pinned, is_locked, is_flagged, is_deleted
-- ============================================================
INSERT IGNORE INTO `forum_comments` (`id`, `post_id`, `parent_id`, `author_id`, `body`, `markdown`, `created_at`, `is_pinned`, `is_flagged`, `is_deleted`)
VALUES
('fcmt-001', 'fp-001', NULL, 'user-alice-001', '滚动数组太有用了！之前一直不会优化空间复杂度', '滚动数组太有用了！之前一直不会优化空间复杂度', NOW(3), 0, 0, 0),
('fcmt-002', 'fp-001', 'fcmt-001', 'admin-001', '是的，滚动数组可以将 O(n) 的空间优化到 O(1)', '是的，滚动数组可以将 O(n) 的空间优化到 O(1)', NOW(3), 0, 0, 0),
('fcmt-003', 'fp-001', NULL, 'user-bob-002', '状态压缩DP也很常用，特别是集合DP', '状态压缩DP也很常用，特别是集合DP', NOW(3), 0, 0, 0),
('fcmt-004', 'fp-002', NULL, 'user-charlie-003', '快慢指针在链表问题中也很实用', '快慢指针在链表问题中也很实用', NOW(3), 0, 0, 0),
('fcmt-005', 'fp-003', NULL, 'admin-001', '非常详细的分享！推荐大家看看', '非常详细的分享！推荐大家看看', NOW(3), 1, 0, 0),
('fcmt-006', 'fp-003', NULL, 'user-alice-001', '请问一开始是按专题刷还是按难度刷？', '请问一开始是按专题刷还是按难度刷？', NOW(3), 0, 0, 0),
('fcmt-007', 'fp-003', 'fcmt-006', 'user-charlie-003', '我建议先按专题刷，每个专题掌握基本模板后再做随机练习', '我建议先按专题刷，每个专题掌握基本模板后再做随机练习', NOW(3), 0, 0, 0),
('fcmt-008', 'fp-004', NULL, 'user-alice-001', '第四题用贪心确实能过，关键是排序策略', '第四题用贪心确实能过，关键是排序策略', NOW(3), 0, 0, 0),
('fcmt-009', 'fp-004', 'fcmt-008', 'user-bob-002', '我用的二分答案也过了', '我用的二分答案也过了', NOW(3), 0, 0, 0),
('fcmt-010', 'fp-005', NULL, 'admin-001', '用 visited 数组标记已访问节点就可以避免重复访问了', '用 visited 数组标记已访问节点就可以避免重复访问了', NOW(3), 0, 0, 0),
('fcmt-011', 'fp-006', NULL, 'user-charlie-003', 'unordered_map 在最坏情况下会退化到 O(n)，要注意', 'unordered_map 在最坏情况下会退化到 O(n)，要注意', NOW(3), 0, 0, 0),
('fcmt-012', 'fp-006', 'fcmt-011', 'admin-001', '对，竞赛中建议用自定义哈希函数避免被卡', '对，竞赛中建议用自定义哈希函数避免被卡', NOW(3), 0, 0, 0);
