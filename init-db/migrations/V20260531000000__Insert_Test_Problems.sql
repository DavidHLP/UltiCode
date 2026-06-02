-- Seed test problems for development
-- Note: IDs use deterministic UUIDs for referential integrity

INSERT INTO `problems` (`id`, `slug`, `title`, `description`, `difficulty`, `is_active`, `time_limit`, `memory_limit`, `accepted_count`, `submission_count`, `created_at`, `updated_at`)
VALUES
-- 两数之和
('prob-001', 'two-sum', '两数之和',
'给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，并且不能使用同一个元素两次。\n\n你可以按任意顺序返回答案。',
'EASY', 1, 1000, 128, 15234, 28456, NOW(3), NOW(3)),

-- 两数相加
('prob-002', 'add-two-numbers', '两数相加',
'给你两个非空的链表，表示两个非负的整数。它们每位数字都是按照逆序的方式存储的，并且每个节点只能存储一位数字。\n\n请你将两个数相加，并以相同形式返回一个表示和的链表。\n\n你可以假设除了数字 0 之外，这两个数都不会以 0 开头。',
'MEDIUM', 1, 1000, 256, 8912, 21543, NOW(3), NOW(3)),

-- 无重复字符的最长子串
('prob-003', 'longest-substring-without-repeating-characters', '无重复字符的最长子串',
'给定一个字符串 s，请你找出其中不含有重复字符的最长子串的长度。',
'MEDIUM', 1, 1000, 256, 7654, 19876, NOW(3), NOW(3)),

-- 寻找两个正序数组的中位数
('prob-004', 'median-of-two-sorted-arrays', '寻找两个正序数组的中位数',
'给定两个大小分别为 m 和 n 的正序（从小到大）数组 nums1 和 nums2。\n\n请你找出并返回这两个正序数组的中位数。\n\n算法的时间复杂度应为 O(log(m+n))。',
'HARD', 1, 2000, 256, 3456, 18765, NOW(3), NOW(3)),

-- 最长回文子串
('prob-005', 'longest-palindromic-substring', '最长回文子串',
'给你一个字符串 s，找到 s 中最长的回文子串。\n\n如果字符串的反序与原始字符串相同，则该字符串称为回文字符串。',
'MEDIUM', 1, 1000, 256, 6789, 16543, NOW(3), NOW(3)),

-- 正则表达式匹配
('prob-006', 'regular-expression-matching', '正则表达式匹配',
'给你一个字符串 s 和一个字符规律 p，请你来实现一个支持 \'.\' 和 \'*\' 的正则表达式匹配。\n\n\'.\' 匹配任意单个字符\n\'*\' 匹配零个或多个前面的那一个元素\n\n所谓匹配，是要涵盖整个字符串 s 的，而不是部分字符串。',
'HARD', 1, 1000, 256, 2345, 14321, NOW(3), NOW(3)),

-- 盛最多水的容器
('prob-007', 'container-with-most-water', '盛最多水的容器',
'给定一个长度为 n 的整数数组 height。有 n 条垂线，第 i 条线的两个端点是 (i, 0) 和 (i, height[i])。\n\n找出其中的两条线，使得它们与 x 轴共同构成的容器可以容纳最多的水。\n\n返回容器可以储存的最大水量。',
'MEDIUM', 1, 1000, 128, 8765, 17654, NOW(3), NOW(3)),

-- 三数之和
('prob-008', '3sum', '三数之和',
'给你一个整数数组 nums，判断是否存在三元组 [nums[i], nums[j], nums[k]] 满足 i != j、i != k 且 j != k，同时还满足 nums[i] + nums[j] + nums[k] == 0。\n\n请你返回所有和为 0 且不重复的三元组。',
'MEDIUM', 1, 1000, 256, 5432, 15432, NOW(3), NOW(3)),

-- 最接近的三数之和
('prob-009', '3sum-closest', '最接近的三数之和',
'给你一个长度为 n 的整数数组 nums 和一个目标值 target。\n\n请你从 nums 中选出三个整数，使它们的和与 target 最接近。\n\n返回这三个数的和。\n\n假定每组输入只存在恰好一个解。',
'MEDIUM', 1, 1000, 256, 4321, 13210, NOW(3), NOW(3)),

-- 删除链表的倒数第 N 个结点
('prob-010', 'remove-nth-node-from-end-of-list', '删除链表的倒数第 N 个结点',
'给你一个链表，删除链表的倒数第 n 个结点，并且返回链表的头结点。',
'MEDIUM', 1, 1000, 128, 7654, 14321, NOW(3), NOW(3)),

-- 有效的括号
('prob-011', 'valid-parentheses', '有效的括号',
'给定一个只包括 \'(\'，\')\'，\'{\'，\'}\'，\'[\'，\']\' 的字符串 s，判断字符串是否有效。\n\n有效字符串需满足：\n1. 左括号必须用相同类型的右括号闭合。\n2. 左括号必须以正确的顺序闭合。\n3. 每个右括号都有一个对应的相同类型的左括号。',
'EASY', 1, 1000, 128, 12345, 18765, NOW(3), NOW(3)),

-- 合并两个有序链表
('prob-012', 'merge-two-sorted-lists', '合并两个有序链表',
'将两个升序链表合并为一个新的升序链表并返回。新链表是通过拼接给定的两个链表的所有节点组成的。',
'EASY', 1, 1000, 128, 10987, 16543, NOW(3), NOW(3)),

-- 括号生成
('prob-013', 'generate-parentheses', '括号生成',
'数字 n 代表生成括号的对数，请你设计一个函数，用于能够生成所有可能的并且有效的括号组合。',
'MEDIUM', 1, 1000, 128, 5432, 13210, NOW(3), NOW(3)),

-- 合并 K 个升序链表
('prob-014', 'merge-k-sorted-lists', '合并 K 个升序链表',
'给你一个链表数组，每个链表都已经按升序排列。\n\n请你将所有链表合并到一个升序链表中，返回合并后的链表。',
'HARD', 1, 2000, 256, 3210, 12100, NOW(3), NOW(3)),

-- 下一个排列
('prob-015', 'next-permutation', '下一个排列',
'整数数组的一个排列就是将其所有成员以序列或线性顺序排列。\n\n给定一个整数数组的排列，将其重新排列成字典序中下一个更大的排列。\n\n如果不存在下一个更大的排列，则将数组重新排列成最小的排列（即升序排列）。',
'MEDIUM', 1, 1000, 128, 4321, 12100, NOW(3), NOW(3)),

-- 最长有效括号
('prob-016', 'longest-valid-parentheses', '最长有效括号',
'给你一个只包含 \'(\' 和 \')\' 的字符串，找出最长有效（格式正确且连续）括号子串的长度。',
'HARD', 1, 1000, 256, 2109, 10987, NOW(3), NOW(3)),

-- 搜索旋转排序数组
('prob-017', 'search-in-rotated-sorted-array', '搜索旋转排序数组',
'整数数组 nums 按升序排列，数组中的值互不相同。\n\n在传递给函数之前，nums 在预先未知的某个下标 k（0 <= k < nums.length）上进行了旋转，使数组变为 [nums[k], nums[k+1], ..., nums[n-1], nums[0], nums[1], ..., nums[k-1]]。\n\n给你旋转后的数组 nums 和一个整数 target，如果 nums 中存在这个目标值，则返回它的下标，否则返回 -1。',
'MEDIUM', 1, 1000, 128, 6543, 14321, NOW(3), NOW(3)),

-- 在排序数组中查找元素的第一个和最后一个位置
('prob-018', 'find-first-and-last-position-of-element-in-sorted-array', '在排序数组中查找元素的第一个和最后一个位置',
'给你一个按照非递减顺序排列的整数数组 nums，和一个目标值 target。\n\n请你找出给定目标值在数组中的开始位置和结束位置。\n\n如果数组中不存在目标值 target，返回 [-1, -1]。',
'MEDIUM', 1, 1000, 128, 5432, 13210, NOW(3), NOW(3)),

-- 组合总和
('prob-019', 'combination-sum', '组合总和',
'给你一个无重复元素的整数数组 candidates 和一个目标整数 target，找出 candidates 中可以使数字和为目标数 target 的所有不同组合。\n\ncandidates 中的同一个数字可以无限制重复被选取。',
'MEDIUM', 1, 1000, 256, 4321, 12100, NOW(3), NOW(3)),

-- 接雨水
('prob-020', 'trapping-rain-water', '接雨水',
'给定 n 个非负整数表示每个宽度为 1 的柱子的高度图，计算按此排列的柱子，下雨之后能接多少雨水。',
'HARD', 1, 1000, 256, 3210, 10987, NOW(3), NOW(3))

ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- Insert problem tags
INSERT INTO `problem_tags` (`id`, `problem_id`, `name`, `created_at`)
VALUES
-- 两数之和标签
('ptag-001', 'prob-001', '数组', NOW(3)),
('ptag-002', 'prob-001', '哈希表', NOW(3)),
-- 两数相加标签
('ptag-003', 'prob-002', '链表', NOW(3)),
('ptag-004', 'prob-002', '递归', NOW(3)),
-- 无重复字符的最长子串标签
('ptag-005', 'prob-003', '字符串', NOW(3)),
('ptag-006', 'prob-003', '滑动窗口', NOW(3)),
-- 寻找两个正序数组的中位数标签
('ptag-007', 'prob-004', '数组', NOW(3)),
('ptag-008', 'prob-004', '二分查找', NOW(3)),
-- 最长回文子串标签
('ptag-009', 'prob-005', '字符串', NOW(3)),
('ptag-010', 'prob-005', '动态规划', NOW(3)),
-- 正则表达式匹配标签
('ptag-011', 'prob-006', '字符串', NOW(3)),
('ptag-012', 'prob-006', '动态规划', NOW(3)),
-- 盛最多水的容器标签
('ptag-013', 'prob-007', '数组', NOW(3)),
('ptag-014', 'prob-007', '双指针', NOW(3)),
-- 三数之和标签
('ptag-015', 'prob-008', '数组', NOW(3)),
('ptag-016', 'prob-008', '双指针', NOW(3)),
-- 最接近的三数之和标签
('ptag-017', 'prob-009', '数组', NOW(3)),
('ptag-018', 'prob-009', '双指针', NOW(3)),
-- 删除链表的倒数第 N 个结点标签
('ptag-019', 'prob-010', '链表', NOW(3)),
('ptag-020', 'prob-010', '双指针', NOW(3)),
-- 有效的括号标签
('ptag-021', 'prob-011', '栈', NOW(3)),
('ptag-022', 'prob-011', '字符串', NOW(3)),
-- 合并两个有序链表标签
('ptag-023', 'prob-012', '链表', NOW(3)),
('ptag-024', 'prob-012', '递归', NOW(3)),
-- 括号生成标签
('ptag-025', 'prob-013', '字符串', NOW(3)),
('ptag-026', 'prob-013', '回溯', NOW(3)),
-- 合并 K 个升序链表标签
('ptag-027', 'prob-014', '链表', NOW(3)),
('ptag-028', 'prob-014', '堆', NOW(3)),
-- 下一个排列标签
('ptag-029', 'prob-015', '数组', NOW(3)),
('ptag-030', 'prob-015', '双指针', NOW(3)),
-- 最长有效括号标签
('ptag-031', 'prob-016', '栈', NOW(3)),
('ptag-032', 'prob-016', '字符串', NOW(3)),
-- 搜索旋转排序数组标签
('ptag-033', 'prob-017', '数组', NOW(3)),
('ptag-034', 'prob-017', '二分查找', NOW(3)),
-- 在排序数组中查找元素的第一个和最后一个位置标签
('ptag-035', 'prob-018', '数组', NOW(3)),
('ptag-036', 'prob-018', '二分查找', NOW(3)),
-- 组合总和标签
('ptag-037', 'prob-019', '数组', NOW(3)),
('ptag-038', 'prob-019', '回溯', NOW(3)),
-- 接雨水标签
('ptag-039', 'prob-020', '数组', NOW(3)),
('ptag-040', 'prob-020', '双指针', NOW(3))
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
