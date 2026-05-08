SET FOREIGN_KEY_CHECKS=0;

-- V28__fix_problem_details_encoding.sql
-- Fix corrupted Chinese text in problem_details table
-- Root cause: Missing useUnicode=true&characterEncoding=UTF-8 in JDBC URL during V16 insertion
-- This migration re-sets the corrupted fields with correct values from V16

-- ============================================================
-- Problem 1 (two-sum): constraints_json hints
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$2 \\\\leq nums.length \\\\leq 10^4$\", \"$-10^9 \\\\leq nums[i] \\\\leq 10^9$\", \"$-10^9 \\\\leq target \\\\leq 10^9$\", \"**Only one valid answer exists.**\"]',
    `hints` = '[\"A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.\", \"如果我们固定一个数 x，就需要扫描整个数组来找到另一个值 y = target - x。我们能以某种方式改变数组使搜索更快吗？\", \"另一种思路是，在不改变数组的情况下，能否使用额外空间使搜索更快？这就是哈希表派上用场的地方。\"]'
WHERE `problem_id` = 1;

-- ============================================================
-- Problem 2 (longest-substring): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$0 \\\\leq s.length \\\\leq 5 \\\\times 10^4$\", \"s 由英文字母、数字、符号和空格组成。\"]'
WHERE `problem_id` = 2;

-- ============================================================
-- Problem 4 (median-of-two-sorted-arrays): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$0 \\\\leq m, n \\\\leq 10^6$\", \"$-10^6 \\\\leq nums1[i], nums2[i] \\\\leq 10^6$\", \"时间复杂度为 O(log(m + n))。\"]'
WHERE `problem_id` = 4;

-- ============================================================
-- Problem 5 (number-of-islands): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq m, n \\\\leq 300$\", \"grid[i][j] 为 \\\"0\\\" 或 \\\"1\\\".\"]'
WHERE `problem_id` = 5;

-- ============================================================
-- Problem 6 (combine-two-tables): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"Person 表和 Address 表已存在。\"]'
WHERE `problem_id` = 6;

-- ============================================================
-- Problem 7 (tenth-line): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"file.txt 文件已存在。\"]'
WHERE `problem_id` = 7;

-- ============================================================
-- Problem 8 (print-foobar-alternately): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"n 是一个整数。\"]'
WHERE `problem_id` = 8;

-- ============================================================
-- Problem 24 (binary-tree-inorder-traversal): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"树中节点数目在范围 $[1, 100]$ 内\", \"$-100 \\\\leq Node.val \\\\leq 100$\"]'
WHERE `problem_id` = 24;

-- ============================================================
-- Problem 25 (binary-tree-level-order-traversal): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"树中节点数目在范围 $[0, 2000]$ 内\", \"$-1000 \\\\leq Node.val \\\\leq 1000$\"]'
WHERE `problem_id` = 25;

-- ============================================================
-- Problem 26 (binary-tree-maximum-path-sum): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"树中节点数目范围在 $[1, 3 \\\\times 10^4]$ 内\", \"$-1000 \\\\leq Node.val \\\\leq 1000$\"]'
WHERE `problem_id` = 26;

-- ============================================================
-- Problem 27 (clone-graph): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"节点数在 $[0, 100]$ 范围内\", \"$1 \\\\leq Node.val \\\\leq 100$\", \"Node.val 对于每个节点是唯一的。\"]'
WHERE `problem_id` = 27;

-- ============================================================
-- Problem 28 (course-schedule): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq numCourses \\\\leq 2000$\", \"$0 \\\\leq prerequisites.length \\\\leq 5000$\", \"prerequisites[i].length == 2\"]'
WHERE `problem_id` = 28;

-- ============================================================
-- Problem 29 (word-ladder): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq beginWord.length \\\\leq 10$\", \"$1 \\\\leq wordList.length \\\\leq 5000$\", \"beginWord、endWord 和 wordList[i] 由小写英文字母组成。\"]'
WHERE `problem_id` = 29;

-- ============================================================
-- Problem 30 (binary-search): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq nums.length \\\\leq 10^4$\", \"$-10^4 \\\\leq nums[i], target \\\\leq 10^4$\", \"nums 中的所有元素互不相同且按升序排列。\"]'
WHERE `problem_id` = 30;

-- ============================================================
-- Problem 31 (search-in-rotated-sorted-array): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq nums.length \\\\leq 5000$\", \"$-10^4 \\\\leq nums[i] \\\\leq 10^4$\", \"nums 中的每个值都独一无二。\"]'
WHERE `problem_id` = 31;

-- ============================================================
-- Problem 32 (find-minimum-in-rotated-sorted-array): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq nums.length \\\\leq 5000$\", \"$-5000 \\\\leq nums[i] \\\\leq 5000$\"]'
WHERE `problem_id` = 32;

-- ============================================================
-- Problem 33 (valid-parentheses): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq s.length \\\\leq 10^4$\", \"s 仅由括号字符组成。\"]'
WHERE `problem_id` = 33;

-- ============================================================
-- Problem 34 (daily-temperatures): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq temperatures.length \\\\leq 10^5$\", \"$30 \\\\leq temperatures[i] \\\\leq 100$\"]'
WHERE `problem_id` = 34;

-- ============================================================
-- Problem 35 (min-stack): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$-2^{31} \\\\leq val \\\\leq 2^{31} - 1$\", \"pop、top 和 getMin 操作总是在非空栈上调用。\"]'
WHERE `problem_id` = 35;

-- ============================================================
-- Problem 36 (jump-game): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq nums.length \\\\leq 10^4$\", \"$0 \\\\leq nums[i] \\\\leq 10^5$\"]'
WHERE `problem_id` = 36;

-- ============================================================
-- Problem 37 (task-scheduler): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$1 \\\\leq tasks.length \\\\leq 10^4$\", \"$0 \\\\leq n \\\\leq 100$\", \"tasks[i] 是大写英文字母。\"]'
WHERE `problem_id` = 37;

-- ============================================================
-- Problem 38 (candy): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$n == ratings.length$\", \"$1 \\\\leq n \\\\leq 2 \\\\times 10^4$\", \"$0 \\\\leq ratings[i] \\\\leq 2 \\\\times 10^4$\"]'
WHERE `problem_id` = 38;

-- ============================================================
-- Problem 39 (powx-n): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"$-100.0 \\\\leq x \\\\leq 100.0$\", \"$-2^{31} \\\\leq n \\\\leq 2^{31} - 1$\", \"$-10^4 \\\\leq x^n \\\\leq 10^4$\"]'
WHERE `problem_id` = 39;

-- ============================================================
-- Problem 40 (reverse-linked-list): constraints_json
-- ============================================================
UPDATE `problem_details`
SET `constraints_json` = '[\"链表中节点的数目范围在 $[0, 5000]$ 内\", \"$-5000 \\\\leq Node.val \\\\leq 5000$\"]'
WHERE `problem_id` = 40;

SET FOREIGN_KEY_CHECKS=1;