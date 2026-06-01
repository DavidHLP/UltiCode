-- ============================================================
-- V20260602030000__Seed_Forum_Test_Data.sql
-- 论坛管理后台测试数据
-- 覆盖：多种 flair 类型、pinned/locked/flagged/deleted 状态、多社区、多用户、嵌套评论
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. Forum Users (独立于主 users 表)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_users (id, username, avatar, karma) VALUES
('fu-admin-001',    'admin',           'https://api.dicebear.com/7.x/bottts/svg?seed=admin',   1250),
('fu-alice-001',    'alice_coder',     'https://api.dicebear.com/7.x/avataaars/svg?seed=alice', 890),
('fu-bob-002',      'bob_algo',        'https://api.dicebear.com/7.x/avataaars/svg?seed=bob',   720),
('fu-carol-003',    'carol_dev',       'https://api.dicebear.com/7.x/avataaars/svg?seed=carol', 650),
('fu-david-004',    'david_systems',   'https://api.dicebear.com/7.x/avataaars/svg?seed=david', 430),
('fu-eva-005',      'eva_ml',          'https://api.dicebear.com/7.x/avataaars/svg?seed=eva',   310),
('fu-frank-006',    'frank_ops',       'https://api.dicebear.com/7.x/avataaars/svg?seed=frank', 180);

-- ────────────────────────────────────────────────────────────
-- 2. Forum Communities
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_communities (id, name, slug, description, members, online, icon, color, posts_count, posts_today, posts_week, is_official, is_featured, sort_order, created_at, visibility) VALUES
('comm-general',    'General Discussion',  'general',    'Off-topic chat, introductions, and community announcements', 1200, 45, '💬', '#6366f1', 8, 2, 12,  0, 1, 1, NOW(3), 'PUBLIC'),
('comm-algorithms', 'Algorithms & Data Structures', 'algorithms', 'Discuss algorithmic problems, solutions, and competitive programming', 850, 32, '🧮', '#f59e0b', 7, 1, 8,  1, 1, 2, NOW(3), 'PUBLIC'),
('comm-languages',  'Programming Languages', 'languages', 'Deep dives into language features, idioms, and best practices', 620, 18, '🖥️', '#10b981', 5, 0, 5,  0, 0, 3, NOW(3), 'PUBLIC'),
('comm-career',     'Career & Interview',  'career',     'Job hunting, interview prep, resume reviews, and career advice', 980, 28, '🎯', '#ef4444', 6, 1, 7,  0, 1, 4, NOW(3), 'PUBLIC'),
('comm-ml',         'Machine Learning',    'ml',         'ML/AI research, model training, and practical applications', 430, 12, '🤖', '#8b5cf6', 4, 0, 3,  0, 0, 5, NOW(3), 'RESTRICTED');

-- ────────────────────────────────────────────────────────────
-- 3. Forum Tags
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_tags (id, name, slug, description, color, usage_count, created_at) VALUES
('tag-java',       'Java',         'java',         'Java language, JVM, Spring ecosystem',          '#f97316', 12, NOW(3)),
('tag-python',     'Python',       'python',       'Python programming, data science, scripting',   '#3b82f6', 10, NOW(3)),
('tag-cpp',        'C++',          'cpp',          'C++ programming, STL, performance',             '#6b7280',  8, NOW(3)),
('tag-algo',       'Algorithm',    'algorithm',    'Algorithm design and analysis',                 '#eab308', 15, NOW(3)),
('tag-dp',         'Dynamic Programming', 'dp',     'DP patterns, optimizations, and problems',      '#ec4899',  6, NOW(3)),
('tag-leetcode',   'LeetCode',     'leetcode',     'LeetCode problems and contest discussions',     '#22c55e',  9, NOW(3)),
('tag-interview',  'Interview',    'interview',    'Technical interview preparation',               '#ef4444',  7, NOW(3)),
('tag-system',     'System Design','system-design','System architecture and design patterns',       '#8b5cf6',  5, NOW(3)),
('tag-tutorial',   'Tutorial',     'tutorial',     'Step-by-step guides and learning resources',    '#06b6d4',  4, NOW(3));

-- ────────────────────────────────────────────────────────────
-- 4. Community-Tag Relations
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_community_tags (community_id, tag_id, is_featured) VALUES
('comm-algorithms', 'tag-algo',      1),
('comm-algorithms', 'tag-dp',        1),
('comm-algorithms', 'tag-leetcode',  1),
('comm-languages',  'tag-java',      1),
('comm-languages',  'tag-python',    1),
('comm-languages',  'tag-cpp',       1),
('comm-career',     'tag-interview', 1),
('comm-career',     'tag-system',    1),
('comm-ml',         'tag-python',    1);

-- ────────────────────────────────────────────────────────────
-- 5. Forum Posts (25 帖子，覆盖所有管理筛选维度)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_posts (id, community_id, user_id, permalink, title, flair_type, flair_label, tags, excerpt, media, recommendation, vote_state, is_saved, impressions, is_pinned, is_locked, created_at, stats, views, is_flagged, flagged_reason, flagged_at, is_deleted, deleted_at, deleted_by) VALUES

-- ▸ pinned 帖子 (2)
('post-001', 'comm-general',    'fu-admin-001', '/general/welcome',
 'Welcome to UltiCode Community!',
 'announcement', 'Official',
 '["announcement","community"]',
 'Welcome to the UltiCode community forum! Please read the rules before posting. This is a place for programmers of all levels to share knowledge, ask questions, and help each other grow.',
 NULL, NULL, 'neutral', 0, 5200, 1, 0,
 DATE_SUB(NOW(3), INTERVAL 10 DAY),
 '{"upvotes":45,"downvotes":1,"comments":12}', 5200, 0, NULL, NULL, 0, NULL, NULL),

('post-002', 'comm-algorithms', 'fu-admin-001', '/algorithms/weekly-challenge',
 'Weekly Algorithm Challenge #42 - Sliding Window',
 'announcement', 'Challenge',
 '["algorithm","leetcode"]',
 'This week''s challenge focuses on sliding window techniques. Solve the problems and share your approaches! Top solutions will be featured next week.',
 NULL, NULL, 'neutral', 0, 3100, 1, 0,
 DATE_SUB(NOW(3), INTERVAL 3 DAY),
 '{"upvotes":38,"downvotes":2,"comments":25}', 3100, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ locked 帖子 (2)
('post-003', 'comm-general', 'fu-david-004', '/general/old-announcement',
 'Legacy Contest Results (Archived)',
 'discussion', 'Archived',
 '["announcement"]',
 'Results from the Q1 2026 programming contest. This thread is now archived and locked.',
 NULL, NULL, 'neutral', 0, 1800, 0, 1,
 DATE_SUB(NOW(3), INTERVAL 8 DAY),
 '{"upvotes":22,"downvotes":0,"comments":8}', 1800, 0, NULL, NULL, 0, NULL, NULL),

('post-004', 'comm-algorithms', 'fu-carol-003', '/algorithms/dp-masterclass',
 'DP Masterclass Thread - All Questions Here',
 'question', 'Megathread',
 '["dp","algorithm"]',
 'Consolidated thread for all Dynamic Programming questions from the masterclass workshop. Thread locked for archival.',
 NULL, NULL, 'neutral', 0, 2400, 0, 1,
 DATE_SUB(NOW(3), INTERVAL 5 DAY),
 '{"upvotes":30,"downvotes":1,"comments":42}', 2400, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ flagged 帖子 (3)
('post-005', 'comm-general', 'fu-frank-006', '/general/spam-post',
 'FREE IPHONE CLICK HERE >>>>',
 NULL, NULL, '["spam"]',
 'Congratulations! You have been selected to receive a FREE iPhone 16 Pro Max! Click the link below to claim your prize now!!!',
 NULL, NULL, 'neutral', 0, 50, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 2 DAY),
 '{"upvotes":0,"downvotes":15,"comments":3}', 50, 1, 'Spam / scam content - not related to programming', DATE_SUB(NOW(3), INTERVAL 2 DAY), 0, NULL, NULL),

('post-006', 'comm-career', 'fu-frank-006', '/career/offtopic-rant',
 'Why I Quit Programming Forever',
 NULL, NULL, '["offtopic"]',
 'A long rant about personal frustrations that is not constructive for the community. Flagged by multiple users for being off-topic and potentially misleading.',
 NULL, NULL, 'neutral', 0, 120, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 1 DAY),
 '{"upvotes":2,"downvotes":8,"comments":6}', 120, 1, 'Off-topic / not constructive content', DATE_SUB(NOW(3), INTERVAL 1 DAY), 0, NULL, NULL),

('post-007', 'comm-languages', 'fu-bob-002', '/languages/java-sucks',
 'Java is Dead, Long Live Rust',
 'discussion', NULL,
 '["java","rust"]',
 'A provocative comparison between Java and Rust that led to heated debate. Flagged for inflammatory title.',
 NULL, NULL, 'neutral', 0, 890, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 4 DAY),
 '{"upvotes":12,"downvotes":18,"comments":35}', 890, 1, 'Inflammatory / clickbait title', DATE_SUB(NOW(3), INTERVAL 3 DAY), 0, NULL, NULL),

-- ▸ deleted (soft-deleted) 帖子 (2)
('post-008', 'comm-general', 'fu-frank-006', '/general/deleted-post-1',
 'Offensive Content Removed',
 NULL, NULL, '[]',
 'This post contained offensive content and has been removed by a moderator.',
 NULL, NULL, 'neutral', 0, 30, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 6 DAY),
 '{"upvotes":0,"downvotes":5,"comments":0}', 30, 0, NULL, NULL, 1, DATE_SUB(NOW(3), INTERVAL 6 DAY), 'fu-admin-001'),

('post-009', 'comm-career', 'fu-david-004', '/career/duplicate-post',
 '[Duplicate] How to Prepare for FAANG Interviews',
 'question', NULL,
 '["interview"]',
 'Duplicate of an existing thread. Removed by moderator to keep the forum clean.',
 NULL, NULL, 'neutral', 0, 15, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 7 DAY),
 '{"upvotes":0,"downvotes":0,"comments":0}', 15, 0, NULL, NULL, 1, DATE_SUB(NOW(3), INTERVAL 5 DAY), 'fu-admin-001'),

-- ▸ normal posts - discussion (3)
('post-010', 'comm-languages', 'fu-alice-001', '/languages/java-21-features',
 'Java 21 Features That Changed How I Code',
 'discussion', 'Deep Dive',
 '["java","tutorial"]',
 'A comprehensive walkthrough of Java 21 features including virtual threads, pattern matching for switch, record patterns, and sequenced collections. Real-world examples included.',
 NULL, NULL, 'neutral', 0, 1650, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 4 DAY),
 '{"upvotes":35,"downvotes":2,"comments":18}', 1650, 0, NULL, NULL, 0, NULL, NULL),

('post-011', 'comm-algorithms', 'fu-carol-003', '/algorithms/graph-theory-intro',
 'Graph Theory for Beginners: A Visual Approach',
 'tutorial', 'Tutorial',
 '["algorithm","tutorial"]',
 'Starting from the basics of graphs, this post covers BFS, DFS, shortest paths, and minimum spanning trees with interactive visualizations and Python code examples.',
 NULL, NULL, 'neutral', 0, 2800, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 7 DAY),
 '{"upvotes":52,"downvotes":1,"comments":22}', 2800, 0, NULL, NULL, 0, NULL, NULL),

('post-012', 'comm-general', 'fu-eva-005', '/general/monthly-review',
 'May 2026 Community Highlights',
 'discussion', NULL,
 '["community"]',
 'A roundup of the best posts, most helpful members, and community milestones from May 2026. Congratulations to this month''s top contributors!',
 NULL, NULL, 'neutral', 0, 950, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 1 DAY),
 '{"upvotes":28,"downvotes":0,"comments":14}', 950, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ normal posts - question (4)
('post-013', 'comm-algorithms', 'fu-alice-001', '/algorithms/lis-nlogn',
 'How does the O(n log n) LIS algorithm work?',
 'question', NULL,
 '["algorithm","dp"]',
 'I understand the O(n²) DP approach for Longest Increasing Subsequence, but the binary search optimization confuses me. Can someone explain the intuition behind it?',
 NULL, NULL, 'neutral', 0, 1320, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 3 DAY),
 '{"upvotes":24,"downvotes":0,"comments":15}', 1320, 0, NULL, NULL, 0, NULL, NULL),

('post-014', 'comm-languages', 'fu-david-004', '/languages/python-gil',
 'Is Python GIL still a problem in 2026?',
 'question', 'Discussion',
 '["python"]',
 'With the free-threaded CPython (PEP 703) landing in 3.13+, is the GIL still a practical concern for most Python developers? How has your experience been?',
 NULL, NULL, 'neutral', 0, 780, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 2 DAY),
 '{"upvotes":18,"downvotes":1,"comments":20}', 780, 0, NULL, NULL, 0, NULL, NULL),

('post-015', 'comm-algorithms', 'fu-bob-002', '/algorithms/two-sum-variants',
 'All Two Sum Variants Explained',
 'question', 'Guide',
 '["leetcode","algorithm"]',
 'A comprehensive guide covering Two Sum, Three Sum, Four Sum, and their optimized approaches. Includes time/space complexity analysis and common pitfalls.',
 NULL, NULL, 'neutral', 0, 2100, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 6 DAY),
 '{"upvotes":42,"downvotes":3,"comments":28}', 2100, 0, NULL, NULL, 0, NULL, NULL),

('post-016', 'comm-ml', 'fu-eva-005', '/ml/transformer-attention',
 'Understanding Multi-Head Attention from Scratch',
 'question', NULL,
 '["python","algorithm"]',
 'Step-by-step derivation of multi-head attention mechanism in Transformers. Covers the math, intuition, and PyTorch implementation.',
 NULL, NULL, 'neutral', 0, 1450, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 5 DAY),
 '{"upvotes":33,"downvotes":1,"comments":16}', 1450, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ normal posts - showcase (2)
('post-017', 'comm-general', 'fu-alice-001', '/general/leetcode-tracker',
 'I built a LeetCode progress tracker with Vue 3',
 'showcase', 'Show & Tell',
 '["leetcode","tutorial"]',
 'After 6 months of grinding LeetCode, I built a web app to track my progress, visualize patterns, and schedule reviews. Open source, link in the post!',
 NULL, NULL, 'neutral', 0, 3200, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 9 DAY),
 '{"upvotes":67,"downvotes":2,"comments":31}', 3200, 0, NULL, NULL, 0, NULL, NULL),

('post-018', 'comm-algorithms', 'fu-carol-003', '/algorithms/visualizer-tool',
 'Algorithm Visualizer - Open Source Tool',
 'showcase', 'Project',
 '["algorithm","tutorial"]',
 'Built an interactive algorithm visualization tool that supports sorting, graph traversal, and DP. Currently supports 15+ algorithms with step-by-step animation.',
 NULL, NULL, 'neutral', 0, 1890, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 2 DAY),
 '{"upvotes":41,"downvotes":0,"comments":19}', 1890, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ normal posts - hiring (1)
('post-019', 'comm-career', 'fu-admin-001', '/career/junior-dev-opening',
 '[Hiring] Junior Backend Developer - Remote',
 'hiring', 'Remote',
 '["interview","java"]',
 'We''re looking for a junior backend developer with Java/Spring Boot experience. Remote-friendly, competitive salary, great learning environment. DM for details.',
 NULL, NULL, 'neutral', 0, 1560, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 3 DAY),
 '{"upvotes":15,"downvotes":0,"comments":8}', 1560, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ 时间分布：最近 24 小时 (4 帖子)
('post-020', 'comm-algorithms', 'fu-bob-002', '/algorithms/segment-tree-guide',
 'Segment Tree Explained: Range Query Mastery',
 'tutorial', 'Tutorial',
 '["algorithm","cpp"]',
 'Deep dive into segment trees: construction, range queries, lazy propagation, and persistent variants. Includes C++ implementation and practice problems.',
 NULL, NULL, 'neutral', 0, 320, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 6 HOUR),
 '{"upvotes":12,"downvotes":0,"comments":5}', 320, 0, NULL, NULL, 0, NULL, NULL),

('post-021', 'comm-languages', 'fu-carol-003', '/languages/rust-lifetime',
 'Rust Lifetimes: The Visual Guide Everyone Needs',
 'discussion', NULL,
 '["tutorial"]',
 'After struggling with Rust lifetimes for months, I created a visual guide that finally made it click. Sharing in hopes it helps others.',
 NULL, NULL, 'neutral', 0, 180, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 4 HOUR),
 '{"upvotes":8,"downvotes":0,"comments":3}', 180, 0, NULL, NULL, 0, NULL, NULL),

('post-022', 'comm-career', 'fu-alice-001', '/career/salary-negotiation',
 'Tips for Salary Negotiation as a New Grad',
 'discussion', NULL,
 '["interview"]',
 'Sharing my experience and research on how to negotiate your first tech job salary. Includes data points and scripts for common scenarios.',
 NULL, NULL, 'neutral', 0, 95, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 2 HOUR),
 '{"upvotes":5,"downvotes":0,"comments":2}', 95, 0, NULL, NULL, 0, NULL, NULL),

('post-023', 'comm-general', 'fu-david-004', '/general/study-group',
 'Looking for LeetCode Study Group Partners',
 'question', NULL,
 '["leetcode"]',
 'Planning to do 100 LeetCode problems in June. Looking for study partners to discuss solutions and keep each other accountable. Anyone interested?',
 NULL, NULL, 'neutral', 0, 45, 0, 0,
 DATE_SUB(NOW(3), INTERVAL 30 MINUTE),
 '{"upvotes":3,"downvotes":0,"comments":1}', 45, 0, NULL, NULL, 0, NULL, NULL),

-- ▸ pinned + flagged 组合
('post-024', 'comm-general', 'fu-frank-006', '/general/controversial-take',
 'Hot Take: Competitive Programming is Overrated',
 'discussion', 'Controversial',
 '["algorithm","interview"]',
 'A controversial opinion piece arguing that competitive programming doesn''t translate to real-world engineering skills. Pinned for discussion but flagged for inflammatory language.',
 NULL, NULL, 'neutral', 0, 4500, 1, 0,
 DATE_SUB(NOW(3), INTERVAL 4 DAY),
 '{"upvotes":55,"downvotes":40,"comments":89}', 4500, 1, 'Content may be divisive - pinned for discussion', DATE_SUB(NOW(3), INTERVAL 3 DAY), 0, NULL, NULL),

-- ▸ locked + flagged 组合
('post-025', 'comm-career', 'fu-frank-006', '/career/fake-job-offers',
 'WARNING: Fake Job Offers Circulating',
 'discussion', 'Alert',
 '["interview"]',
 'Multiple users reported receiving suspicious job offer emails. Thread locked after investigation - all claims were unsubstantiated.',
 NULL, NULL, 'neutral', 0, 670, 0, 1,
 DATE_SUB(NOW(3), INTERVAL 5 DAY),
 '{"upvotes":8,"downvotes":3,"comments":12}', 670, 1, 'Unverified claims - locked after investigation', DATE_SUB(NOW(3), INTERVAL 4 DAY), 0, NULL, NULL);

-- ────────────────────────────────────────────────────────────
-- 6. Post-Tag Relations
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_post_tag_relations (post_id, tag_id) VALUES
('post-002', 'tag-algo'),
('post-002', 'tag-leetcode'),
('post-004', 'tag-dp'),
('post-004', 'tag-algo'),
('post-007', 'tag-java'),
('post-010', 'tag-java'),
('post-010', 'tag-tutorial'),
('post-011', 'tag-algo'),
('post-011', 'tag-tutorial'),
('post-013', 'tag-algo'),
('post-013', 'tag-dp'),
('post-014', 'tag-python'),
('post-015', 'tag-leetcode'),
('post-015', 'tag-algo'),
('post-016', 'tag-python'),
('post-016', 'tag-algo'),
('post-017', 'tag-leetcode'),
('post-017', 'tag-tutorial'),
('post-018', 'tag-algo'),
('post-018', 'tag-tutorial'),
('post-019', 'tag-interview'),
('post-019', 'tag-java'),
('post-020', 'tag-algo'),
('post-020', 'tag-cpp'),
('post-021', 'tag-tutorial'),
('post-022', 'tag-interview'),
('post-023', 'tag-leetcode'),
('post-024', 'tag-algo'),
('post-024', 'tag-interview'),
('post-025', 'tag-interview');

-- ────────────────────────────────────────────────────────────
-- 7. Forum Comments (嵌套评论)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_comments (id, post_id, parent_id, author_id, body, markdown, created_at, edited_at, is_pinned, is_locked, is_flagged, flagged_reason, flagged_at, is_deleted, deleted_at, deleted_by) VALUES

-- post-001 下的评论 (Welcome)
('cmt-001', 'post-001', NULL,        'fu-alice-001', 'Thanks for the warm welcome! Excited to be here.',       NULL, DATE_SUB(NOW(3), INTERVAL 9 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-002', 'post-001', 'cmt-001',   'fu-admin-001', 'Glad to have you! Feel free to ask questions anytime.',  NULL, DATE_SUB(NOW(3), INTERVAL 9 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-003', 'post-001', NULL,        'fu-bob-002',   'Great community so far. Love the algorithm challenges.',  NULL, DATE_SUB(NOW(3), INTERVAL 8 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-002 下的评论 (Weekly Challenge)
('cmt-004', 'post-002', NULL,        'fu-carol-003', 'Used a monotonic deque approach for the sliding window max. O(n) time!', NULL, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-005', 'post-002', 'cmt-004',   'fu-alice-001', 'Nice! I used a heap-based approach but it was O(n log k). Your solution is better.', NULL, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-006', 'post-002', NULL,        'fu-david-004', 'Can someone explain the second problem? I keep getting TLE.', NULL, DATE_SUB(NOW(3), INTERVAL 1 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-011 下的评论 (Graph Theory)
('cmt-007', 'post-011', NULL,        'fu-david-004', 'The visualizations are incredibly helpful. Bookmarked!',  NULL, DATE_SUB(NOW(3), INTERVAL 6 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-008', 'post-011', 'cmt-007',   'fu-carol-003', 'Thanks! I used D3.js for the interactive graphs. Source code is on my GitHub.', NULL, DATE_SUB(NOW(3), INTERVAL 6 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-009', 'post-011', NULL,        'fu-eva-005',   'Would love to see a follow-up on network flow algorithms!', NULL, DATE_SUB(NOW(3), INTERVAL 5 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-013 下的评论 (LIS question)
('cmt-010', 'post-013', NULL,        'fu-bob-002',   'Think of it this way: the tails array maintains the smallest possible tail element for each LIS length. Binary search finds where the current element fits.', NULL, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-011', 'post-013', 'cmt-010',   'fu-alice-001', 'That makes so much more sense now! The tails array is essentially a "best candidates" list.', NULL, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-015 下的评论 (Two Sum variants)
('cmt-012', 'post-015', NULL,        'fu-carol-003', 'Don''t forget the "Two Sum II - Input Array Is Sorted" variant. Two pointers is the key there!', NULL, DATE_SUB(NOW(3), INTERVAL 5 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-017 下的评论 (LeetCode tracker)
('cmt-013', 'post-017', NULL,        'fu-eva-005',   'This is amazing! Does it support exporting progress as CSV?', NULL, DATE_SUB(NOW(3), INTERVAL 8 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-014', 'post-017', 'cmt-013',   'fu-alice-001', 'Yes! CSV and JSON export are both supported. Check the Settings page.', NULL, DATE_SUB(NOW(3), INTERVAL 8 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-015', 'post-017', NULL,        'fu-frank-006', 'Can you add dark mode support?', NULL, DATE_SUB(NOW(3), INTERVAL 7 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),

-- post-024 下的评论 (Controversial - pinned + flagged)
('cmt-016', 'post-024', NULL,        'fu-bob-002',   'I partially agree. CP helps with problem-solving speed, but system design matters more in industry.', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-017', 'post-024', 'cmt-016',   'fu-carol-003', 'Fair point, but CP also teaches you to think under pressure. That''s valuable everywhere.', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-018', 'post-024', NULL,        'fu-admin-001', 'Keeping this pinned for discussion. Please keep the debate respectful and constructive.', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 1, 0, 0, NULL, NULL, 0, NULL, NULL),

-- deleted comment
('cmt-019', 'post-024', NULL,        'fu-frank-006', '[Removed by moderator]', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 1, 'Offensive language', DATE_SUB(NOW(3), INTERVAL 3 DAY), 1, DATE_SUB(NOW(3), INTERVAL 3 DAY), 'fu-admin-001'),

-- post-010 下的评论 (Java 21)
('cmt-020', 'post-010', NULL,        'fu-david-004', 'Virtual threads are a game changer for IO-bound services. We migrated our API gateway and saw 3x throughput improvement.', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-021', 'post-010', 'cmt-020',   'fu-alice-001', 'That''s impressive! Did you have to change much code or was it mostly a drop-in replacement?', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL),
('cmt-022', 'post-010', 'cmt-021',   'fu-david-004', 'Mostly drop-in. The main change was removing our thread pool executor. Structured concurrency is the next step.', NULL, DATE_SUB(NOW(3), INTERVAL 3 DAY), NULL, 0, 0, 0, NULL, NULL, 0, NULL, NULL);

-- ────────────────────────────────────────────────────────────
-- 8. Community Members (核心成员)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO forum_community_members (id, community_id, user_id, role, joined_at) VALUES
('mem-001', 'comm-general',    'fu-admin-001',  'OWNER',      DATE_SUB(NOW(3), INTERVAL 30 DAY)),
('mem-002', 'comm-general',    'fu-alice-001',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 25 DAY)),
('mem-003', 'comm-general',    'fu-bob-002',    'MEMBER',     DATE_SUB(NOW(3), INTERVAL 20 DAY)),
('mem-004', 'comm-algorithms', 'fu-admin-001',  'OWNER',      DATE_SUB(NOW(3), INTERVAL 30 DAY)),
('mem-005', 'comm-algorithms', 'fu-carol-003',  'MODERATOR',  DATE_SUB(NOW(3), INTERVAL 28 DAY)),
('mem-006', 'comm-algorithms', 'fu-alice-001',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 22 DAY)),
('mem-007', 'comm-algorithms', 'fu-bob-002',    'MEMBER',     DATE_SUB(NOW(3), INTERVAL 18 DAY)),
('mem-008', 'comm-languages',  'fu-alice-001',  'OWNER',      DATE_SUB(NOW(3), INTERVAL 28 DAY)),
('mem-009', 'comm-languages',  'fu-david-004',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 20 DAY)),
('mem-010', 'comm-languages',  'fu-carol-003',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 15 DAY)),
('mem-011', 'comm-career',     'fu-admin-001',  'OWNER',      DATE_SUB(NOW(3), INTERVAL 30 DAY)),
('mem-012', 'comm-career',     'fu-alice-001',  'MODERATOR',  DATE_SUB(NOW(3), INTERVAL 25 DAY)),
('mem-013', 'comm-career',     'fu-david-004',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 18 DAY)),
('mem-014', 'comm-ml',         'fu-eva-005',    'OWNER',      DATE_SUB(NOW(3), INTERVAL 26 DAY)),
('mem-015', 'comm-ml',         'fu-alice-001',  'MEMBER',     DATE_SUB(NOW(3), INTERVAL 15 DAY));
