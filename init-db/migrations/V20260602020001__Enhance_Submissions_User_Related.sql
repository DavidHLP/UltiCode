-- ============================================================
-- V20260602020001__Enhance_Submissions_User_Related.sql
-- 增强 submissions 测试数据：与用户强相关
-- 目标：每个用户有独特的提交画像、学习轨迹、多题目关联
-- ============================================================

-- ============================================================
-- 1. 先插入更多测试题目（submissions 需要引用）
-- ============================================================

-- Problem 2: Reverse Linked List (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(2, 'reverse-linked-list', 'Reverse Linked List', 'Easy', 72.50, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-2', 2, 'reverse-linked-list', 'Reverse a singly linked list.', '<p>Given the head of a singly linked list, reverse the list, and return the reversed list.</p>', 1200.0, '["0 <= Node count <= 5000", "-5000 <= Node.val <= 5000"]', '["Use three pointers: prev, curr, next"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-2-python', 2, 'Python3', 'python3', 'python', '# Definition for singly-linked list.\n# class ListNode:\n#     def __init__(self, val=0, next=None):\n#         self.val = val\n#         self.next = None\nclass Solution:\n    def reverseList(self, head: Optional[ListNode]) -> Optional[ListNode]:\n        pass'),
('pl-2-java', 2, 'Java', 'java', 'java', '/* Definition for singly-linked list. */\nclass Solution {\n    public ListNode reverseList(ListNode head) {\n        // Your code here\n    }\n}'),
('pl-2-cpp', 2, 'C++', 'cpp17', 'cpp', 'class Solution {\npublic:\n    ListNode* reverseList(ListNode* head) {\n        // Your code here\n    }\n};')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Problem 3: Valid Parentheses (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(3, 'valid-parentheses', 'Valid Parentheses', 'Easy', 40.80, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-3', 3, 'valid-parentheses', 'Determine if input string has valid parentheses.', '<p>Given a string s containing just the characters ''('', '')'', ''{'', ''}'', ''['' and '']'', determine if the input string is valid.</p>', 1300.0, '["1 <= s.length <= 10^4", "s consists of parentheses only ''()[]{}''"]', '["Use a stack data structure"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-3-python', 3, 'Python3', 'python3', 'python', 'class Solution:\n    def isValid(self, s: str) -> bool:\n        pass'),
('pl-3-java', 3, 'Java', 'java', 'java', 'class Solution {\n    public boolean isValid(String s) {\n        // Your code here\n    }\n}'),
('pl-3-cpp', 3, 'C++', 'cpp17', 'cpp', 'class Solution {\npublic:\n    bool isValid(string s) {\n        // Your code here\n    }\n};')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Problem 4: Binary Tree Inorder Traversal (Medium)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(4, 'binary-tree-inorder', 'Binary Tree Inorder Traversal', 'Medium', 72.10, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-4', 4, 'binary-tree-inorder', 'Return inorder traversal of binary tree nodes.', '<p>Given the root of a binary tree, return the inorder traversal of its nodes'' values.</p>', 1500.0, '["0 <= Node count <= 100", "-100 <= Node.val <= 100"]', '["Try both recursive and iterative approaches"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-4-python', 4, 'Python3', 'python3', 'python', '# Definition for a binary tree node.\n# class TreeNode:\n#     def __init__(self, val=0, left=None, right=None):\n#         self.val = val\n#         self.left = left\n#         self.right = right\nclass Solution:\n    def inorderTraversal(self, root: Optional[TreeNode]) -> List[int]:\n        pass'),
('pl-4-java', 4, 'Java', 'java', 'java', 'class Solution {\n    public List<Integer> inorderTraversal(TreeNode root) {\n        // Your code here\n    }\n}')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Problem 5: Merge Two Sorted Lists (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(5, 'merge-two-sorted-lists', 'Merge Two Sorted Lists', 'Easy', 62.30, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-5', 5, 'merge-two-sorted-lists', 'Merge two sorted linked lists into one sorted list.', '<p>You are given the heads of two sorted linked lists list1 and list2. Merge the two lists into one sorted list.</p>', 1200.0, '["0 <= Node count per list <= 50", "-100 <= Node.val <= 100"]', '["Use a dummy head node to simplify logic"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-5-python', 5, 'Python3', 'python3', 'python', 'class Solution:\n    def mergeTwoLists(self, list1: Optional[ListNode], list2: Optional[ListNode]) -> Optional[ListNode]:\n        pass'),
('pl-5-java', 5, 'Java', 'java', 'java', 'class Solution {\n    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {\n        // Your code here\n    }\n}')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Problem 10: Climbing Stairs (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(10, 'climbing-stairs', 'Climbing Stairs', 'Easy', 51.50, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-10', 10, 'climbing-stairs', 'Count distinct ways to climb stairs taking 1 or 2 steps.', '<p>You are climbing a staircase. It takes n steps to reach the top. Each time you can either climb 1 or 2 steps. In how many distinct ways can you climb to the top?</p>', 1300.0, '["1 <= n <= 45"]', '["This is a Fibonacci sequence in disguise"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-10-python', 10, 'Python3', 'python3', 'python', 'class Solution:\n    def climbStairs(self, n: int) -> int:\n        pass'),
('pl-10-java', 10, 'Java', 'java', 'java', 'class Solution {\n    public int climbStairs(int n) {\n        // Your code here\n    }\n}')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- Problem 20: Best Time to Buy and Sell Stock (Easy)
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `published_by`, `created_at`, `updated_at`, `version`) VALUES
(20, 'best-time-to-buy-sell-stock', 'Best Time to Buy and Sell Stock', 'Easy', 54.20, 'todo', 0, 1, 1, NOW(), 'u-admin-001', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `difficulty_rating`, `constraints_json`, `hints`, `updated_at`) VALUES
('pd-20', 20, 'best-time-to-buy-sell-stock', 'Find maximum profit from one buy-sell transaction.', '<p>You are given an array prices where prices[i] is the price of a given stock on the ith day. You want to maximize your profit by choosing a single day to buy one stock and choosing a different day in the future to sell that stock.</p>', 1300.0, '["1 <= prices.length <= 10^5", "0 <= prices[i] <= 10^4"]', '["Track the minimum price seen so far"]', NOW())
ON DUPLICATE KEY UPDATE `summary` = VALUES(`summary`);

INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`) VALUES
('pl-20-python', 20, 'Python3', 'python3', 'python', 'class Solution:\n    def maxProfit(self, prices: List[int]) -> int:\n        pass'),
('pl-20-java', 20, 'Java', 'java', 'java', 'class Solution {\n    public int maxProfit(int[] prices) {\n        // Your code here\n    }\n}')
ON DUPLICATE KEY UPDATE `starter_code` = VALUES(`starter_code`);

-- ============================================================
-- 2. 清空旧的 submissions 测试数据（仅测试环境）
-- ============================================================
DELETE FROM submissions WHERE id LIKE 'sub-%';

-- ============================================================
-- 3. 插入与用户强相关的 submissions 测试数据
-- ============================================================

-- 用户画像说明：
-- Alice (alice_coder): 算法爱好者，专注动态规划，Python 为主，偶尔 Java
--   特征：善于一次通过，代码简洁优雅，高 AC 率
-- Bob (bob_dev): Java/Python 双修，系统设计爱好者
--   特征：喜欢先暴力再优化，经常提交多次才 AC
-- Carol (carol_wu): 竞赛党 ACMer，C++ 为主
--   特征：追求极致性能，代码风格竞赛化，解题速度快
-- David (david_chen): 前端转全栈，JS/Python
--   特征：偶尔犯低级错误，但进步明显，喜欢尝试不同解法
-- Eva (eva_zhang): Python 达人，AI 初学者
--   特征：代码 Pythonic，有时过于简洁导致边界错误
-- Frank (frank_lee): C++ 高性能追求者
--   特征：从暴力到优化的学习曲线明显，内存控制好

INSERT IGNORE INTO submissions (id, problem_id, user_id, language, code, status, runtime, memory, notes, created_at, runtime_percentile, memory_percentile, test_details, memoryDistBinsMb, runtimeDistBinsMs, retry_count) VALUES

-- ============================================================
-- Alice: 算法爱好者，高 AC 率，多题目覆盖
-- ============================================================

-- Alice P1: Two Sum - Python 哈希表，首次 AC
('sub-a01', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []',
'Accepted', 36, 16.2, '哈希表 O(n) 解法，一次通过',
DATE_SUB(NOW(), INTERVAL 14 DAY), 95.5, 88.3,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.1), JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 15.8)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,10,20,30,40,50,80,100,150,200),
0),

-- Alice P2: Reverse Linked List - Python 迭代法
('sub-a02', 2, 'user-alice-001', 'python3',
'class Solution:\n    def reverseList(self, head):\n        prev, curr = None, head\n        while curr:\n            nxt = curr.next\n            curr.next = prev\n            prev = curr\n            curr = nxt\n        return prev',
'Accepted', 32, 18.5, '三指针迭代法，简洁',
DATE_SUB(NOW(), INTERVAL 12 DAY), 94.0, 85.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 17.0)),
JSON_ARRAY(0,2,4,7,10,14,18,22,26,30),
JSON_ARRAY(0,5,10,15,20,25,30,40,50,60),
0),

-- Alice P3: Valid Parentheses - Python 栈
('sub-a03', 3, 'user-alice-001', 'python3',
'class Solution:\n    def isValid(self, s):\n        stack = []\n        mapping = {")":"(", "}":"{", "]":"["}\n        for char in s:\n            if char in mapping:\n                if not stack or stack[-1] != mapping[char]:\n                    return False\n                stack.pop()\n            else:\n                stack.append(char)\n        return not stack',
'Accepted', 28, 15.8, '栈解法，经典',
DATE_SUB(NOW(), INTERVAL 10 DAY), 96.2, 90.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 14.5)),
JSON_ARRAY(0,1,3,5,8,11,14,17,20,24),
JSON_ARRAY(0,5,10,15,20,25,30,40,50,60),
0),

-- Alice P10: Climbing Stairs - Python DP
('sub-a04', 10, 'user-alice-001', 'python3',
'class Solution:\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        a, b = 1, 2\n        for _ in range(3, n + 1):\n            a, b = b, a + b\n        return b',
'Accepted', 24, 14.2, '滚动变量优化空间 O(1)',
DATE_SUB(NOW(), INTERVAL 8 DAY), 97.8, 92.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 13.8)),
JSON_ARRAY(0,1,2,4,6,8,10,12,14,16),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,50),
0),

-- Alice P20: Best Time to Buy and Sell Stock - Python 一次遍历
('sub-a05', 20, 'user-alice-001', 'python3',
'class Solution:\n    def maxProfit(self, prices):\n        min_price = float('inf')\n        max_profit = 0\n        for price in prices:\n            min_price = min(min_price, price)\n            max_profit = max(max_profit, price - min_price)\n        return max_profit',
'Accepted', 68, 22.0, '一次遍历，O(n) 时间 O(1) 空间',
DATE_SUB(NOW(), INTERVAL 5 DAY), 93.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 5, 'memory', 20.5)),
JSON_ARRAY(0,5,12,20,30,42,55,68,80,100),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Alice P1: Two Sum - Java 版本（跨语言尝试）
('sub-a06', 1, 'user-alice-001', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Accepted', 2, 44.0, 'Java 版本，对比 Python 性能',
DATE_SUB(NOW(), INTERVAL 13 DAY), 92.0, 73.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 42.0)),
JSON_ARRAY(0,2,4,8,12,18,25,32,40,48),
JSON_ARRAY(0,2,4,6,8,10,15,20,30,40),
0),

-- Alice P4: Binary Tree Inorder - Python 递归
('sub-a07', 4, 'user-alice-001', 'python3',
'class Solution:\n    def inorderTraversal(self, root):\n        res = []\n        def inorder(node):\n            if not node:\n                return\n            inorder(node.left)\n            res.append(node.val)\n            inorder(node.right)\n        inorder(root)\n        return res',
'Accepted', 20, 16.0, '经典递归中序遍历',
DATE_SUB(NOW(), INTERVAL 3 DAY), 95.0, 89.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 15.2)),
JSON_ARRAY(0,1,2,4,6,8,10,12,14,16),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Alice P5: Merge Two Sorted Lists - Python 递归
('sub-a08', 5, 'user-alice-001', 'python3',
'class Solution:\n    def mergeTwoLists(self, list1, list2):\n        if not list1:\n            return list2\n        if not list2:\n            return list1\n        if list1.val <= list2.val:\n            list1.next = self.mergeTwoLists(list1.next, list2)\n            return list1\n        else:\n            list2.next = self.mergeTwoLists(list1, list2.next)\n            return list2',
'Accepted', 28, 16.5, '递归解法，优雅简洁',
DATE_SUB(NOW(), INTERVAL 2 DAY), 94.5, 87.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.8)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- ============================================================
-- Bob: Java/Python 双修，先暴力再优化，多次提交
-- ============================================================

-- Bob P1: Two Sum - Java 暴力（首次尝试）
('sub-b01', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[]{i, j};\n                }\n            }\n        }\n        return new int[]{};\n    }\n}',
'Accepted', 120, 42.0, '暴力 O(n^2)，先跑通再说',
DATE_SUB(NOW(), INTERVAL 14 DAY), 15.0, 76.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 110, 'memory', 40.0)),
JSON_ARRAY(0,20,40,60,80,100,120,140,160,180),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob P1: Two Sum - Java 优化版（第二次提交）
('sub-b02', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Accepted', 2, 42.5, '优化为 HashMap O(n)',
DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 30 MINUTE, 92.1, 75.6,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 40.2)),
JSON_ARRAY(0,3,6,10,15,20,28,35,42,50),
JSON_ARRAY(0,1,2,3,4,5,8,10,15,20),
0),

-- Bob P2: Reverse Linked List - Java 迭代
('sub-b03', 2, 'user-bob-002', 'java',
'class Solution {\n    public ListNode reverseList(ListNode head) {\n        ListNode prev = null;\n        ListNode curr = head;\n        while (curr != null) {\n            ListNode next = curr.next;\n            curr.next = prev;\n            prev = curr;\n            curr = next;\n        }\n        return prev;\n    }\n}',
'Accepted', 0, 42.8, '三指针迭代，Java 标准写法',
DATE_SUB(NOW(), INTERVAL 11 DAY), 96.0, 72.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 41.0)),
JSON_ARRAY(0,1,2,3,5,7,9,11,13,15),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob P3: Valid Parentheses - Java 栈（首次 WA）
('sub-b04', 3, 'user-bob-002', 'java',
'class Solution {\n    public boolean isValid(String s) {\n        Stack<Character> stack = new Stack<>();\n        for (char c : s.toCharArray()) {\n            if (c == ''('' || c == ''{'' || c == ''['') {\n                stack.push(c);\n            } else {\n                if (stack.isEmpty()) return false;\n                char top = stack.pop();\n                if (c == '')'' && top != ''('') return false;\n                if (c == ''}'' && top != ''{'') return false;\n                if (c == '']'' && top != ''['') return false;\n            }\n        }\n        return true;\n    }\n}',
'Wrong Answer', 3, 41.0, '忘记处理栈为空时的右括号',
DATE_SUB(NOW(), INTERVAL 9 DAY) - INTERVAL 2 HOUR, NULL, NULL, NULL, NULL, NULL, 0),

-- Bob P3: Valid Parentheses - Java 栈（修复后 AC）
('sub-b05', 3, 'user-bob-002', 'java',
'class Solution {\n    public boolean isValid(String s) {\n        Stack<Character> stack = new Stack<>();\n        for (char c : s.toCharArray()) {\n            if (c == ''('') stack.push('')'');\n            else if (c == ''{'') stack.push(''}'');\n            else if (c == ''['') stack.push('']'');\n            else if (stack.isEmpty() || stack.pop() != c) return false;\n        }\n        return stack.isEmpty();\n    }\n}',
'Accepted', 2, 40.5, '反向压入匹配字符，更简洁',
DATE_SUB(NOW(), INTERVAL 9 DAY), 95.0, 78.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 39.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob P10: Climbing Stairs - Python DP
('sub-b06', 10, 'user-bob-002', 'python3',
'class Solution:\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        dp = [0] * (n + 1)\n        dp[1] = 1\n        dp[2] = 2\n        for i in range(3, n + 1):\n            dp[i] = dp[i-1] + dp[i-2]\n        return dp[n]',
'Accepted', 32, 16.0, '标准 DP 数组写法',
DATE_SUB(NOW(), INTERVAL 7 DAY), 88.0, 86.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob P1: Two Sum - Python 版本
('sub-b07', 1, 'user-bob-002', 'python3',
'def two_sum(nums, target):\n    lookup = {}\n    for i, num in enumerate(nums):\n        if target - num in lookup:\n            return [lookup[target - num], i]\n        lookup[num] = i\n    return []',
'Accepted', 38, 16.5, 'Python 版本对比',
DATE_SUB(NOW(), INTERVAL 6 DAY), 94.0, 87.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 15.5)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
0),

-- Bob P5: Merge Two Sorted Lists - Java 迭代
('sub-b08', 5, 'user-bob-002', 'java',
'class Solution {\n    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {\n        ListNode dummy = new ListNode(0);\n        ListNode curr = dummy;\n        while (list1 != null && list2 != null) {\n            if (list1.val <= list2.val) {\n                curr.next = list1;\n                list1 = list1.next;\n            } else {\n                curr.next = list2;\n                list2 = list2.next;\n            }\n            curr = curr.next;\n        }\n        curr.next = (list1 != null) ? list1 : list2;\n        return dummy.next;\n    }\n}',
'Accepted', 0, 43.0, 'dummy head 迭代法',
DATE_SUB(NOW(), INTERVAL 4 DAY), 97.0, 70.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 42.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob P20: Best Time to Buy and Sell Stock - Java（TLE 首次）
('sub-b09', 20, 'user-bob-002', 'java',
'class Solution {\n    public int maxProfit(int[] prices) {\n        int maxProfit = 0;\n        for (int i = 0; i < prices.length; i++) {\n            for (int j = i + 1; j < prices.length; j++) {\n                maxProfit = Math.max(maxProfit, prices[j] - prices[i]);\n            }\n        }\n        return maxProfit;\n    }\n}',
'Time Limit Exceeded', 2000, 42.0, '暴力 O(n^2) 超时',
DATE_SUB(NOW(), INTERVAL 3 DAY) - INTERVAL 1 HOUR, NULL, NULL, NULL, NULL, NULL, 0),

-- Bob P20: Best Time to Buy and Sell Stock - Java 优化 AC
('sub-b10', 20, 'user-bob-002', 'java',
'class Solution {\n    public int maxProfit(int[] prices) {\n        int minPrice = Integer.MAX_VALUE;\n        int maxProfit = 0;\n        for (int price : prices) {\n            minPrice = Math.min(minPrice, price);\n            maxProfit = Math.max(maxProfit, price - minPrice);\n        }\n        return maxProfit;\n    }\n}',
'Accepted', 1, 42.5, '一次遍历优化，从暴力到最优',
DATE_SUB(NOW(), INTERVAL 3 DAY), 96.5, 74.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 41.0)),
JSON_ARRAY(0,1,2,3,4,5,6,8,10,12),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- ============================================================
-- Carol: 竞赛党 ACMer，C++ 为主，追求极致性能
-- ============================================================

-- Carol P1: Two Sum - C++ 竞赛写法，0ms
('sub-c01', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> mp;\n        for (int i = 0; i < nums.size(); ++i) {\n            if (mp.count(target - nums[i]))\n                return {mp[target - nums[i]], i};\n            mp[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 0, 12.4, 'C++ 竞赛写法，0ms',
DATE_SUB(NOW(), INTERVAL 15 DAY), 99.8, 96.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 11.5)),
JSON_ARRAY(0,1,3,5,8,10,12,14,16,20),
JSON_ARRAY(0,0,1,1,2,2,3,4,5,8),
0),

-- Carol P2: Reverse Linked List - C++ 迭代
('sub-c02', 2, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    ListNode* reverseList(ListNode* head) {\n        ListNode *prev = nullptr, *curr = head;\n        while (curr) {\n            ListNode *next = curr->next;\n            curr->next = prev;\n            prev = curr;\n            curr = next;\n        }\n        return prev;\n    }\n};',
'Accepted', 0, 11.8, 'C++ 指针操作，0ms',
DATE_SUB(NOW(), INTERVAL 13 DAY), 99.5, 97.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 10.5)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
JSON_ARRAY(0,0,0,1,1,1,2,2,3,4),
0),

-- Carol P3: Valid Parentheses - C++ 栈
('sub-c03', 3, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    bool isValid(string s) {\n        stack<char> st;\n        for (char c : s) {\n            if (c == ''('') st.push('')'');\n            else if (c == ''{'') st.push(''}'');\n            else if (c == ''['') st.push('']'');\n            else if (st.empty() || st.top() != c) return false;\n            else st.pop();\n        }\n        return st.empty();\n    }\n};',
'Accepted', 0, 8.2, 'C++ 栈，0ms 内存最优',
DATE_SUB(NOW(), INTERVAL 11 DAY), 99.9, 98.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 7.5)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- Carol P4: Binary Tree Inorder - C++ Morris 遍历
('sub-c04', 4, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> inorderTraversal(TreeNode* root) {\n        vector<int> res;\n        TreeNode *curr = root;\n        while (curr) {\n            if (!curr->left) {\n                res.push_back(curr->val);\n                curr = curr->right;\n            } else {\n                TreeNode *pred = curr->left;\n                while (pred->right && pred->right != curr)\n                    pred = pred->right;\n                if (!pred->right) {\n                    pred->right = curr;\n                    curr = curr->left;\n                } else {\n                    pred->right = nullptr;\n                    res.push_back(curr->val);\n                    curr = curr->right;\n                }\n            }\n        }\n        return res;\n    }\n};',
'Accepted', 0, 10.0, 'Morris 遍历 O(1) 空间，竞赛技巧',
DATE_SUB(NOW(), INTERVAL 9 DAY), 99.9, 99.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 9.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- Carol P5: Merge Two Sorted Lists - C++ 迭代
('sub-c05', 5, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    ListNode* mergeTwoLists(ListNode* list1, ListNode* list2) {\n        ListNode dummy(0);\n        ListNode *curr = &dummy;\n        while (list1 && list2) {\n            if (list1->val <= list2->val) {\n                curr->next = list1;\n                list1 = list1->next;\n            } else {\n                curr->next = list2;\n                list2 = list2->next;\n            }\n            curr = curr->next;\n        }\n        curr->next = list1 ? list1 : list2;\n        return dummy.next;\n    }\n};',
'Accepted', 0, 11.0, '栈上 dummy 节点，避免 new',
DATE_SUB(NOW(), INTERVAL 7 DAY), 99.5, 97.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 10.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- Carol P10: Climbing Stairs - C++ O(1) 空间
('sub-c06', 10, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    int climbStairs(int n) {\n        if (n <= 2) return n;\n        int a = 1, b = 2;\n        for (int i = 3; i <= n; ++i) {\n            int c = a + b;\n            a = b;\n            b = c;\n        }\n        return b;\n    }\n};',
'Accepted', 0, 7.5, '滚动变量，极致空间优化',
DATE_SUB(NOW(), INTERVAL 5 DAY), 99.9, 99.2,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 6.8)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,0,1,1,1,2,2),
0),

-- Carol P20: Best Time to Buy and Sell Stock - C++ 一次遍历
('sub-c07', 20, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    int maxProfit(vector<int>& prices) {\n        int minPrice = INT_MAX, maxProfit = 0;\n        for (int price : prices) {\n            minPrice = min(minPrice, price);\n            maxProfit = max(maxProfit, price - minPrice);\n        }\n        return maxProfit;\n    }\n};',
'Accepted', 4, 18.0, 'C++ 标准写法',
DATE_SUB(NOW(), INTERVAL 3 DAY), 98.0, 94.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 16.5)),
JSON_ARRAY(0,1,2,3,4,5,7,9,11,14),
JSON_ARRAY(0,2,4,6,8,10,14,18,22,26),
0),

-- Carol P1: Two Sum - C++ 暴力（教学用途，展示对比）
('sub-c08', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        for (int i = 0; i < nums.size(); ++i)\n            for (int j = i + 1; j < nums.size(); ++j)\n                if (nums[i] + nums[j] == target)\n                    return {i, j};\n        return {};\n    }\n};',
'Accepted', 280, 10.8, '暴力对比用，展示哈希表优势',
DATE_SUB(NOW(), INTERVAL 14 DAY) - INTERVAL 1 HOUR, 12.3, 98.1,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 275, 'memory', 10.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0),

-- ============================================================
-- David: 前端转全栈，JS/Python，偶尔犯错但进步明显
-- ============================================================

-- David P1: Two Sum - JavaScript 哈希表
('sub-d01', 1, 'user-david-004', 'javascript',
'var twoSum = function(nums, target) {\n    const map = new Map();\n    for (let i = 0; i < nums.length; i++) {\n        const complement = target - nums[i];\n        if (map.has(complement)) {\n            return [map.get(complement), i];\n        }\n        map.set(nums[i], i);\n    }\n    return [];\n};',
'Accepted', 56, 48.2, 'JS Map 写法',
DATE_SUB(NOW(), INTERVAL 14 DAY), 85.4, 62.1,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 50, 'memory', 46.0)),
JSON_ARRAY(0,4,8,12,16,20,28,36,44,52),
JSON_ARRAY(0,10,20,30,40,50,60,80,100,140),
0),

-- David P1: Two Sum - Python 排序后索引丢失（WA）
('sub-d02', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    nums_sorted = sorted(nums)\n    left, right = 0, len(nums_sorted) - 1\n    while left < right:\n        s = nums_sorted[left] + nums_sorted[right]\n        if s == target:\n            return [left, right]\n        elif s < target:\n            left += 1\n        else:\n            right -= 1',
'Wrong Answer', 45, 15.2, '排序后索引丢失，未映射回原数组',
DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- David P2: Reverse Linked List - JavaScript 迭代
('sub-d03', 2, 'user-david-004', 'javascript',
'var reverseList = function(head) {\n    let prev = null, curr = head;\n    while (curr) {\n        const next = curr.next;\n        curr.next = prev;\n        prev = curr;\n        curr = next;\n    }\n    return prev;\n};',
'Accepted', 52, 46.5, 'JS 迭代法',
DATE_SUB(NOW(), INTERVAL 10 DAY), 86.0, 65.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 48, 'memory', 44.0)),
JSON_ARRAY(0,4,8,12,16,20,28,36,44,52),
JSON_ARRAY(0,10,20,30,40,50,60,80,100,140),
0),

-- David P3: Valid Parentheses - Python 栈
('sub-d04', 3, 'user-david-004', 'python3',
'class Solution:\n    def isValid(self, s):\n        stack = []\n        pairs = {''}'': ''{'', '')'': ''('', '']'': ''[''}\n        for c in s:\n            if c in pairs.values():\n                stack.append(c)\n            elif c in pairs:\n                if not stack or stack[-1] != pairs[c]:\n                    return False\n                stack.pop()\n        return len(stack) == 0',
'Accepted', 32, 15.5, 'Python 标准写法',
DATE_SUB(NOW(), INTERVAL 8 DAY), 90.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 14.5)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- David P1: Two Sum - Java 空指针（Runtime Error）
('sub-d05', 1, 'user-david-004', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = null;\n        for (int i = 0; i < nums.length; i++) {\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Runtime Error', 0, 0.0, 'NullPointerException - map 未初始化',
DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- David P10: Climbing Stairs - Python（首次尝试 TLE）
('sub-d06', 10, 'user-david-004', 'python3',
'class Solution:\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        return self.climbStairs(n-1) + self.climbStairs(n-2)',
'Time Limit Exceeded', 2000, 14.0, '递归无记忆化，指数级复杂度',
DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- David P10: Climbing Stairs - Python DP（修复后 AC）
('sub-d07', 10, 'user-david-004', 'python3',
'class Solution:\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        dp = [0] * (n + 1)\n        dp[1], dp[2] = 1, 2\n        for i in range(3, n + 1):\n            dp[i] = dp[i-1] + dp[i-2]\n        return dp[n]',
'Accepted', 30, 15.8, '改用 DP，从 TLE 到 AC',
DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 20 MINUTE, 89.0, 87.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 14.8)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- David P5: Merge Two Sorted Lists - Python 迭代
('sub-d08', 5, 'user-david-004', 'python3',
'class Solution:\n    def mergeTwoLists(self, list1, list2):\n        dummy = ListNode(0)\n        curr = dummy\n        while list1 and list2:\n            if list1.val <= list2.val:\n                curr.next = list1\n                list1 = list1.next\n            else:\n                curr.next = list2\n                list2 = list2.next\n            curr = curr.next\n        curr.next = list1 or list2\n        return dummy.next',
'Accepted', 35, 16.0, 'dummy head 迭代法',
DATE_SUB(NOW(), INTERVAL 5 DAY), 91.0, 86.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- David P20: Best Time to Buy and Sell Stock - Python
('sub-d09', 20, 'user-david-004', 'python3',
'class Solution:\n    def maxProfit(self, prices):\n        min_price = prices[0]\n        max_profit = 0\n        for price in prices[1:]:\n            min_price = min(min_price, price)\n            max_profit = max(max_profit, price - min_price)\n        return max_profit',
'Accepted', 72, 22.5, '一次遍历，注意初始化',
DATE_SUB(NOW(), INTERVAL 2 DAY), 90.0, 85.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 5, 'memory', 21.0)),
JSON_ARRAY(0,5,12,20,30,42,55,68,80,100),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- David P1: Two Sum - Python（最终简洁版）
('sub-d10', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    h = {}\n    for i in range(len(nums)):\n        n = nums[i]\n        m = target - n\n        if m in h:\n            return [h[m], i]\n        h[n] = i',
'Accepted', 42, 16.1, '简洁写法，进步明显',
DATE_SUB(NOW(), INTERVAL 1 DAY), 92.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,8,16,24,32,42,55,70,100,150),
0),

-- ============================================================
-- Eva: Python 达人，AI 初学者，代码 Pythonic
-- ============================================================

-- Eva P1: Two Sum - Python 一次通过
('sub-e01', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    d = {}\n    for i, v in enumerate(nums):\n        if target - v in d:\n            return [d[target - v], i]\n        d[v] = i',
'Accepted', 40, 16.0, 'Pythonic 写法，简洁',
DATE_SUB(NOW(), INTERVAL 14 DAY), 93.0, 89.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 35, 'memory', 15.2)),
JSON_ARRAY(0,2,4,7,10,14,17,19,21,23),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
0),

-- Eva P1: Two Sum - Python 边界条件错误（WA）
('sub-e02', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]\n    return [-1, -1]',
'Wrong Answer', 320, 14.5, '返回值格式错误，应为 [] 而非 [-1,-1]',
DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Eva P2: Reverse Linked List - Python 递归
('sub-e03', 2, 'user-eva-005', 'python3',
'class Solution:\n    def reverseList(self, head):\n        if not head or not head.next:\n            return head\n        new_head = self.reverseList(head.next)\n        head.next.next = head\n        head.next = None\n        return new_head',
'Accepted', 35, 18.0, '递归解法，Pythonic',
DATE_SUB(NOW(), INTERVAL 11 DAY), 92.0, 84.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 17.0)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P3: Valid Parentheses - Python（过于简洁导致 WA）
('sub-e04', 3, 'user-eva-005', 'python3',
'class Solution:\n    def isValid(self, s):\n        while ''()'' in s or ''{}'' in s or ''[]'' in s:\n            s = s.replace(''()'', '''').replace(''{}'', '''').replace(''[]'', '''')\n        return s == ''''',
'Time Limit Exceeded', 2000, 15.0, 'replace 循环法，O(n^2) 超时',
DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Eva P3: Valid Parentheses - Python 栈（修复后 AC）
('sub-e05', 3, 'user-eva-005', 'python3',
'class Solution:\n    def isValid(self, s):\n        stack = []\n        m = {")":"(", "}":"{", "]":"["}\n        for c in s:\n            if c in m:\n                if not stack or stack[-1] != m[c]:\n                    return False\n                stack.pop()\n            else:\n                stack.append(c)\n        return not stack',
'Accepted', 28, 15.2, '改用栈，从 TLE 到 AC',
DATE_SUB(NOW(), INTERVAL 9 DAY) + INTERVAL 15 MINUTE, 94.0, 89.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 14.0)),
JSON_ARRAY(0,1,3,5,8,11,14,17,20,24),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P10: Climbing Stairs - Python 递归（TLE）
('sub-e06', 10, 'user-eva-005', 'python3',
'class Solution:\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        return self.climbStairs(n-1) + self.climbStairs(n-2)',
'Time Limit Exceeded', 2000, 14.0, '递归无记忆化',
DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Eva P10: Climbing Stairs - Python functools.lru_cache
('sub-e07', 10, 'user-eva-005', 'python3',
'from functools import lru_cache\n\nclass Solution:\n    @lru_cache(maxsize=None)\n    def climbStairs(self, n):\n        if n <= 2:\n            return n\n        return self.climbStairs(n-1) + self.climbStairs(n-2)',
'Accepted', 32, 16.5, 'Python 装饰器记忆化，优雅',
DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 10 MINUTE, 87.0, 85.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.5)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P20: Best Time to Buy and Sell Stock - Python
('sub-e08', 20, 'user-eva-005', 'python3',
'class Solution:\n    def maxProfit(self, prices):\n        buy = prices[0]\n        profit = 0\n        for p in prices[1:]:\n            if p < buy:\n                buy = p\n            profit = max(profit, p - buy)\n        return profit',
'Accepted', 68, 21.5, '简洁直观',
DATE_SUB(NOW(), INTERVAL 4 DAY), 92.0, 87.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 5, 'memory', 20.0)),
JSON_ARRAY(0,5,12,20,30,42,55,68,80,100),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P1: Two Sum - Python 数组越界（Runtime Error）
('sub-e09', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    n = len(nums)\n    for i in range(n):\n        complement = target - nums[i]\n        for j in range(i + 1, n + 1):\n            if nums[j] == complement:\n                return [i, j]',
'Runtime Error', 5, 13.0, 'IndexError - range 越界 n+1',
DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Eva P5: Merge Two Sorted Lists - Python 递归
('sub-e10', 5, 'user-eva-005', 'python3',
'class Solution:\n    def mergeTwoLists(self, list1, list2):\n        if not list1 or not list2:\n            return list1 or list2\n        if list1.val <= list2.val:\n            list1.next = self.mergeTwoLists(list1.next, list2)\n            return list1\n        list2.next = self.mergeTwoLists(list1, list2.next)\n        return list2',
'Accepted', 30, 16.8, '递归法，Pythonic',
DATE_SUB(NOW(), INTERVAL 3 DAY), 93.0, 86.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.8)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P4: Binary Tree Inorder - Python 递归
('sub-e11', 4, 'user-eva-005', 'python3',
'class Solution:\n    def inorderTraversal(self, root):\n        res = []\n        def dfs(node):\n            if not node:\n                return\n            dfs(node.left)\n            res.append(node.val)\n            dfs(node.right)\n        dfs(root)\n        return res',
'Accepted', 22, 15.5, '简洁递归',
DATE_SUB(NOW(), INTERVAL 2 DAY), 94.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 14.5)),
JSON_ARRAY(0,1,2,4,6,8,10,12,14,16),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Eva P1: Two Sum - Python 死循环输出（Output Limit）
('sub-e12', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            while True:\n                print(i, seen[complement])\n        seen[num] = i',
'Output Limit Exceeded', 150, 25.0, '死循环导致输出超限',
DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- Frank: C++ 高性能追求者，从暴力到优化的学习曲线
-- ============================================================

-- Frank P1: Two Sum - C++ 暴力（首次尝试）
('sub-f01', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        for (int i = 0; i < nums.size(); ++i)\n            for (int j = i + 1; j < nums.size(); ++j)\n                if (nums[i] + nums[j] == target)\n                    return {i, j};\n        return {};\n    }\n};',
'Accepted', 280, 10.8, '暴力 O(n^2)，勉强通过',
DATE_SUB(NOW(), INTERVAL 15 DAY), 12.3, 98.1,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 275, 'memory', 10.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0),

-- Frank P1: Two Sum - C++ 哈希表（优化版）
('sub-f02', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> seen;\n        for (int i = 0; i < nums.size(); ++i) {\n            int comp = target - nums[i];\n            if (seen.find(comp) != seen.end())\n                return {seen[comp], i};\n            seen[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 4, 13.2, '从暴力到哈希表的进化',
DATE_SUB(NOW(), INTERVAL 14 DAY), 98.5, 95.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 12.0)),
JSON_ARRAY(0,1,3,5,8,10,12,14,16,20),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
0),

-- Frank P2: Reverse Linked List - C++ 迭代
('sub-f03', 2, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    ListNode* reverseList(ListNode* head) {\n        ListNode *prev = nullptr, *curr = head;\n        while (curr) {\n            ListNode *next = curr->next;\n            curr->next = prev;\n            prev = curr;\n            curr = next;\n        }\n        return prev;\n    }\n};',
'Accepted', 0, 11.5, 'C++ 指针操作',
DATE_SUB(NOW(), INTERVAL 12 DAY), 99.0, 97.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 10.5)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
JSON_ARRAY(0,0,0,1,1,1,2,2,3,4),
0),

-- Frank P3: Valid Parentheses - C++ 栈
('sub-f04', 3, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    bool isValid(string s) {\n        stack<char> st;\n        for (char c : s) {\n            if (c == ''('') st.push('')'');\n            else if (c == ''{'') st.push(''}'');\n            else if (c == ''['') st.push('']'');\n            else if (st.empty() || st.top() != c) return false;\n            else st.pop();\n        }\n        return st.empty();\n    }\n};',
'Accepted', 0, 8.5, 'C++ 栈，内存控制好',
DATE_SUB(NOW(), INTERVAL 10 DAY), 99.8, 98.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 7.8)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- Frank P1: Two Sum - C++ 大数组复制（MLE）
('sub-f05', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        vector<int> copy = nums;\n        map<int, vector<int>> mp;\n        for (int i = 0; i < nums.size(); ++i)\n            mp[nums[i]].push_back(i);\n        for (int i = 0; i < nums.size(); ++i) {\n            int comp = target - nums[i];\n            if (mp.count(comp)) {\n                for (int j : mp[comp])\n                    if (j != i) return {i, j};\n            }\n        }\n        return {};\n    }\n};',
'Memory Limit Exceeded', 180, 512.0, '不必要的数组复制导致内存超限',
DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Frank P10: Climbing Stairs - C++ O(1) 空间
('sub-f06', 10, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    int climbStairs(int n) {\n        if (n <= 2) return n;\n        int a = 1, b = 2;\n        for (int i = 3; i <= n; ++i) {\n            b = a + b;\n            a = b - a;\n        }\n        return b;\n    }\n};',
'Accepted', 0, 7.2, '极致空间优化，无临时变量',
DATE_SUB(NOW(), INTERVAL 8 DAY), 99.9, 99.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 6.5)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,0,1,1,1,2,2),
0),

-- Frank P5: Merge Two Sorted Lists - C++ 迭代
('sub-f07', 5, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    ListNode* mergeTwoLists(ListNode* list1, ListNode* list2) {\n        ListNode dummy(0);\n        ListNode *curr = &dummy;\n        while (list1 && list2) {\n            if (list1->val <= list2->val) {\n                curr->next = list1;\n                list1 = list1->next;\n            } else {\n                curr->next = list2;\n                list2 = list2->next;\n            }\n            curr = curr->next;\n        }\n        curr->next = list1 ? list1 : list2;\n        return dummy.next;\n    }\n};',
'Accepted', 0, 10.8, '栈上 dummy，避免堆分配',
DATE_SUB(NOW(), INTERVAL 6 DAY), 99.5, 97.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 10.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- Frank P20: Best Time to Buy and Sell Stock - C++（WA 首次）
('sub-f08', 20, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    int maxProfit(vector<int>& prices) {\n        int buy = 0, sell = prices.size() - 1;\n        for (int i = 0; i < prices.size(); ++i)\n            if (prices[i] < prices[buy]) buy = i;\n        for (int i = buy; i < prices.size(); ++i)\n            if (prices[i] > prices[sell]) sell = i;\n        return prices[sell] - prices[buy];\n    }\n};',
'Wrong Answer', 8, 19.0, '卖出必须在买入之后，逻辑有误',
DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, NULL, NULL, NULL, 0),

-- Frank P20: Best Time to Buy and Sell Stock - C++ 修复 AC
('sub-f09', 20, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    int maxProfit(vector<int>& prices) {\n        int minPrice = INT_MAX, maxProfit = 0;\n        for (int price : prices) {\n            minPrice = min(minPrice, price);\n            maxProfit = max(maxProfit, price - minPrice);\n        }\n        return maxProfit;\n    }\n};',
'Accepted', 4, 18.2, '正确的一次遍历',
DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 30 MINUTE, 98.0, 94.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 16.5)),
JSON_ARRAY(0,1,2,3,4,5,7,9,11,14),
JSON_ARRAY(0,2,4,6,8,10,14,18,22,26),
0),

-- Frank P4: Binary Tree Inorder - C++ 迭代（栈模拟）
('sub-f10', 4, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> inorderTraversal(TreeNode* root) {\n        vector<int> res;\n        stack<TreeNode*> st;\n        TreeNode *curr = root;\n        while (curr || !st.empty()) {\n            while (curr) {\n                st.push(curr);\n                curr = curr->left;\n            }\n            curr = st.top(); st.pop();\n            res.push_back(curr->val);\n            curr = curr->right;\n        }\n        return res;\n    }\n};',
'Accepted', 0, 10.2, '迭代法，显式栈管理',
DATE_SUB(NOW(), INTERVAL 2 DAY), 99.0, 97.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 9.5)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- ============================================================
-- Admin: 管理员测试提交
-- ============================================================

('sub-x01', 1, 'u-admin-001', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]',
'Accepted', 285, 14.0, '管理员暴力测试',
DATE_SUB(NOW(), INTERVAL 20 DAY), 10.0, 97.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 280, 'memory', 13.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0),

('sub-x02', 1, 'u-admin-001', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> m;\n        for (int i = 0; i < nums.size(); i++) {\n            if (m.count(target - nums[i]))\n                return {m[target - nums[i]], i};\n            m[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 3, 11.5, '管理员 C++ 测试',
DATE_SUB(NOW(), INTERVAL 20 DAY) + INTERVAL 20 MINUTE, 98.0, 97.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 10.5)),
JSON_ARRAY(0,1,2,4,6,8,10,11,12,13),
JSON_ARRAY(0,0,1,2,3,3,4,5,6,8),
0),

-- ============================================================
-- 最近 24 小时内的提交（测试 last24h 统计）
-- ============================================================

-- Alice: 今天的提交
('sub-r01', 4, 'user-alice-001', 'python3',
'class Solution:\n    def inorderTraversal(self, root):\n        res, stack = [], []\n        curr = root\n        while curr or stack:\n            while curr:\n                stack.append(curr)\n                curr = curr.left\n            curr = stack.pop()\n            res.append(curr.val)\n            curr = curr.right\n        return res',
'Accepted', 22, 15.8, '迭代版本，补充递归解法',
DATE_SUB(NOW(), INTERVAL 3 HOUR), 95.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 14.8)),
JSON_ARRAY(0,1,2,4,6,8,10,12,14,16),
JSON_ARRAY(0,5,10,15,20,25,30,35,40,45),
0),

-- Bob: 今天的错误提交
('sub-r02', 20, 'user-bob-002', 'java',
'class Solution {\n    public int maxProfit(int[] prices) {\n        Arrays.sort(prices);\n        return prices[prices.length - 1] - prices[0];\n    }\n}',
'Wrong Answer', 5, 42.0, '排序后不一定能买到最低点',
DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, NULL, NULL, NULL, NULL, 0),

-- Carol: 今天的提交（新题挑战）
('sub-r03', 4, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> inorderTraversal(TreeNode* root) {\n        vector<int> res;\n        TreeNode *curr = root;\n        while (curr) {\n            if (!curr->left) {\n                res.push_back(curr->val);\n                curr = curr->right;\n            } else {\n                TreeNode *pred = curr->left;\n                while (pred->right && pred->right != curr)\n                    pred = pred->right;\n                if (!pred->right) {\n                    pred->right = curr;\n                    curr = curr->left;\n                } else {\n                    pred->right = nullptr;\n                    res.push_back(curr->val);\n                    curr = curr->right;\n                }\n            }\n        }\n        return res;\n    }\n};',
'Accepted', 0, 9.8, 'Morris 遍历再战',
DATE_SUB(NOW(), INTERVAL 1 HOUR), 99.9, 99.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 9.0)),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,9),
JSON_ARRAY(0,0,0,0,1,1,1,2,2,3),
0),

-- David: 今天的提交
('sub-r04', 3, 'user-david-004', 'javascript',
'var isValid = function(s) {\n    const stack = [];\n    const map = {")":"(", "}":"{", "]":"["};\n    for (const c of s) {\n        if (c in map) {\n            if (!stack.length || stack[stack.length-1] !== map[c]) return false;\n            stack.pop();\n        } else {\n            stack.push(c);\n        }\n    }\n    return stack.length === 0;\n};',
'Accepted', 48, 45.0, 'JS 版本，前端选手的倔强',
DATE_SUB(NOW(), INTERVAL 30 MINUTE), 88.0, 68.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 42, 'memory', 43.0)),
JSON_ARRAY(0,4,8,12,16,20,28,36,44,52),
JSON_ARRAY(0,10,20,30,40,50,60,80,100,140),
0),

-- Eva: Pending 提交
('sub-r05', 20, 'user-eva-005', 'python3',
'class Solution:\n    def maxProfit(self, prices):\n        # AI 建议的解法，待验证\n        return max((max(prices[i:]) - p for i, p in enumerate(prices)), default=0)',
'Pending', 0, 0.0, NULL,
DATE_SUB(NOW(), INTERVAL 2 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

-- Frank: Judging 提交
('sub-r06', 10, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    int climbStairs(int n) {\n        if (n <= 2) return n;\n        int a = 1, b = 2;\n        for (int i = 3; i <= n; ++i) {\n            int c = a + b;\n            a = b;\n            b = c;\n        }\n        return b;\n    }\n};',
'Judging', 0, 0.0, NULL,
DATE_SUB(NOW(), INTERVAL 1 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- 重新评测提交（retry_count > 0）
-- ============================================================

('sub-rt01', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []',
'Accepted', 34, 16.0, '系统错误后重新评测通过',
DATE_SUB(NOW(), INTERVAL 16 DAY), 96.0, 89.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,19,21,24),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
2),

-- System Error 提交
('sub-se01', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    return [0, 1]',
'System Error', 0, 0.0, '评测机异常，需重新提交',
DATE_SUB(NOW(), INTERVAL 16 DAY) - INTERVAL 1 HOUR, NULL, NULL, NULL, NULL, NULL, 1),

-- Compile Error 提交
('sub-ce01', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>()\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Compile Error', 0, 0.0, '缺少分号',
DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, NULL, NULL, NULL, 0),

('sub-ce02', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> mp;\n        for (int i = 0; i < nums.size(); ++i) {\n            if (mp.count(target - nums[i]))\n                return {mp[target - nums[i]], i};\n            mp[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Compile Error', 0, 0.0, '未包含头文件',
DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 15 MINUTE, NULL, NULL, NULL, NULL, NULL, 0),

-- Presentation Error
('sub-pe01', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            print(seen[complement], i)\n            return [seen[complement], i]\n        seen[num] = i',
'Presentation Error', 38, 15.5, '多余的 print 输出导致格式错误',
DATE_SUB(NOW(), INTERVAL 12 DAY) - INTERVAL 3 HOUR, NULL, NULL, NULL, NULL, NULL, 0);
