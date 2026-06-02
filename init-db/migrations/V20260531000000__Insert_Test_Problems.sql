-- Insert test problems
-- Schema: problems (list summary) + problem_details (full content)
-- Note: problems.id is bigint, difficulty enum is 'Easy','Medium','Hard' (capitalized)

INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `created_at`, `updated_at`)
VALUES
(1, 'two-sum', '两数之和', 'Easy', 53.50, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(2, 'add-two-numbers', '两数相加', 'Medium', 41.20, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(3, 'longest-substring-without-repeating-characters', '无重复字符的最长子串', 'Medium', 38.80, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(4, 'median-of-two-sorted-arrays', '寻找两个正序数组的中位数', 'Hard', 35.50, 'todo', 0, 0, 1, NOW(3), NOW(3)),
(5, 'longest-palindromic-substring', '最长回文子串', 'Medium', 32.10, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(6, 'reverse-linked-list', '反转链表', 'Easy', 73.20, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(7, 'merge-k-sorted-lists', '合并K个升序链表', 'Hard', 28.40, 'todo', 0, 0, 1, NOW(3), NOW(3)),
(8, 'valid-parentheses', '有效的括号', 'Easy', 60.80, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(9, 'merge-two-sorted-lists', '合并两个有序链表', 'Easy', 65.30, 'todo', 0, 1, 1, NOW(3), NOW(3)),
(10, '3sum', '三数之和', 'Medium', 30.50, 'todo', 0, 1, 1, NOW(3), NOW(3));

-- Insert problem details
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `constraints_json`, `hints`, `updated_at`)
VALUES
('pd-001', 1, 'two-sum', '在数组中找出和为目标值的两个整数',
'给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，并且不能使用同一个元素两次。\n\n你可以按任意顺序返回答案。',
'{"constraints": ["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9", "-10^9 <= target <= 10^9"]}',
'["考虑使用哈希表来减少时间复杂度"]', NOW(3)),

('pd-002', 2, 'add-two-numbers', '两个非负整数按逆序存储在链表中，求它们的和',
'给你两个非空的链表，表示两个非负的整数。它们每位数字都是按照逆序的方式存储的，并且每个节点只能存储一位数字。\n\n请你将两个数相加，并以相同形式返回一个表示和的链表。',
'{"constraints": ["每个链表中的节点数在范围 [1, 100] 内", "0 <= Node.val <= 9"]}',
'["注意处理进位", "链表长度不同时需要补零"]', NOW(3)),

('pd-003', 3, 'longest-substring-without-repeating-characters', '找出不含重复字符的最长子串的长度',
'给定一个字符串 s，请你找出其中不含有重复字符的最长子串的长度。',
'{"constraints": ["0 <= s.length <= 5 * 10^4", "s 由英文字母、数字、符号和空格组成"]}',
'["滑动窗口是经典解法"]', NOW(3)),

('pd-006', 6, 'reverse-linked-list', '反转单链表',
'给你单链表的头节点 head，请你反转链表，并返回反转后的链表。',
'{"constraints": ["链表中节点的数目范围是 [0, 5000]", "-5000 <= Node.val <= 5000"]}',
'["可以使用迭代或递归方法", "递归解法注意空间复杂度"]', NOW(3)),

('pd-007', 7, 'merge-k-sorted-lists', '合并K个升序链表',
'给你一个链表数组，每个链表都已经按升序排列。请你将所有链表合并到一个升序链表中，返回合并后的链表。',
'{"constraints": ["k == lists.length", "0 <= k <= 10^4", "0 <= lists[i].length <= 500"]}',
'["考虑使用最小堆优化", "也可以使用分治法"]', NOW(3));
