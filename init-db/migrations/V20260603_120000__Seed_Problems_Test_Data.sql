-- Seed Test Data: Problems
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Problems)
-- 维护指南: 修改 problems / problem_details / problem_examples /
--          problem_tags / problem_tag_relations / problem_languages /
--          按 tag 自动生成的题单 (list-tag-*) 时, 仅编辑本文件
--
-- 设计原则: 每个难度 (Easy/Medium/Hard) 恰好 2 道题, 共 6 道
--   Easy   : id=1 (两数之和) / id=6 (反转链表)
--   Medium : id=2 (两数相加) / id=3 (无重复字符的最长子串)
--   Hard   : id=4 (寻找两个正序数组的中位数) / id=7 (合并K个升序链表)
--
-- 设计原则 2: 每种题目类型 (tag) 对应 1 个题单, 便于 /problem-lists 页面
--   按类型浏览; list-tag-* 11 个题单与 problem_tags 一一对应。
--
-- 字符集说明: 后端 JDBC URL 已包含 useUnicode=true&characterEncoding=UTF-8,
--   Flyway 走应用连接字符正常; 若手动 docker exec mysql 写入中文,
--   必须加 --default-character-set=utf8mb4
-- ------------------------------------------------------------

-- 幂等清理: 删除历史脚本中已存在但不在新保留集合中的 problem (id=5, 8, 9, 10)
DELETE FROM `problem_details` WHERE `problem_id` IN (5, 8, 9, 10);
DELETE FROM `problem_list_problem_relations` WHERE `problem_id` IN (5, 8, 9, 10);
DELETE FROM `problems` WHERE `id` IN (5, 8, 9, 10);

-- ============================================================
-- 1. problems (题目列表摘要)
-- ============================================================
INSERT INTO `problems` (`id`, `slug`, `title`, `difficulty`, `acceptance_rate`, `status`, `is_premium`, `has_solution`, `is_published`, `published_at`, `created_at`, `updated_at`)
VALUES
(1, 'two-sum', '两数之和', 'Easy', 53.50, 'todo', 0, 1, 1, NOW(3), NOW(3), NOW(3)),
(2, 'add-two-numbers', '两数相加', 'Medium', 41.20, 'todo', 0, 1, 1, NOW(3), NOW(3), NOW(3)),
(3, 'longest-substring-without-repeating-characters', '无重复字符的最长子串', 'Medium', 38.80, 'todo', 0, 1, 1, NOW(3), NOW(3), NOW(3)),
(4, 'median-of-two-sorted-arrays', '寻找两个正序数组的中位数', 'Hard', 35.50, 'todo', 0, 0, 1, NOW(3), NOW(3), NOW(3)),
(6, 'reverse-linked-list', '反转链表', 'Easy', 73.20, 'todo', 0, 1, 1, NOW(3), NOW(3), NOW(3)),
(7, 'merge-k-sorted-lists', '合并K个升序链表', 'Hard', 28.40, 'todo', 0, 0, 1, NOW(3), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  `slug` = VALUES(`slug`),
  `title` = VALUES(`title`),
  `difficulty` = VALUES(`difficulty`),
  `acceptance_rate` = VALUES(`acceptance_rate`),
  `is_published` = VALUES(`is_published`),
  `updated_at` = NOW(3);

-- ============================================================
-- 2. problem_details (题目详情正文 + 约束 + 提示)
-- ============================================================
INSERT INTO `problem_details` (`id`, `problem_id`, `slug`, `summary`, `content`, `constraints_json`, `hints`, `updated_at`)
VALUES
('pd-001', 1, 'two-sum', '在数组中找出和为目标值的两个整数',
'给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，并且不能使用同一个元素两次。\n\n你可以按任意顺序返回答案。',
'{"constraints": ["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9", "-10^9 <= target <= 10^9"]}',
'["考虑使用哈希表来减少时间复杂度", "遍历数组时同时查找 target - nums[i] 是否已经出现过"]', NOW(3)),

('pd-002', 2, 'add-two-numbers', '两个非负整数按逆序存储在链表中，求它们的和',
'给你两个非空的链表，表示两个非负的整数。它们每位数字都是按照逆序的方式存储的，并且每个节点只能存储一位数字。\n\n请你将两个数相加，并以相同形式返回一个表示和的链表。\n\n你可以假设除了数字 0 之外，这两个数都不会以 0 开头。',
'{"constraints": ["每个链表中的节点数在范围 [1, 100] 内", "0 <= Node.val <= 9", "题目数据保证列表表示的数字不含前导零"]}',
'["注意处理进位 carry", "链表长度不同时需要补零", "最后可能仍需处理一次进位"]', NOW(3)),

('pd-003', 3, 'longest-substring-without-repeating-characters', '找出不含重复字符的最长子串的长度',
'给定一个字符串 s，请你找出其中不含有重复字符的最长子串的长度。',
'{"constraints": ["0 <= s.length <= 5 * 10^4", "s 由英文字母、数字、符号和空格组成"]}',
'["滑动窗口是经典解法", "使用哈希表记录字符最近出现的位置", "右指针每次右移一格, 左指针跳跃式收缩"]', NOW(3)),

('pd-004', 4, 'median-of-two-sorted-arrays', '寻找两个正序数组的中位数', '给定两个大小分别为 m 和 n 的正序（从小到大）数组 nums1 和 nums2。请你找出并返回这两个正序数组的中位数。\n\n要求算法的时间复杂度应该为 O(log (m+n))。',
'{"constraints": ["0 <= m, n <= 1000", "1 <= m + n <= 2000", "-10^6 <= nums1[i], nums2[i] <= 10^6"]}',
'["二分查找是关键", "可对较短的数组进行切分寻找分割点", "中位数左侧元素数量满足特定关系"]', NOW(3)),

('pd-006', 6, 'reverse-linked-list', '反转单链表', '给你单链表的头节点 head，请你反转链表，并返回反转后的链表。\n\n进阶：链表可以选用迭代或递归两种方式实现，你能用两种方法解决这道题吗？',
'{"constraints": ["链表中节点的数目范围是 [0, 5000]", "-5000 <= Node.val <= 5000"]}',
'["迭代法: 使用 prev / curr / next 三个指针", "递归法: 先反转后续节点再处理当前节点", "递归解法空间复杂度为 O(n)"]', NOW(3)),

('pd-007', 7, 'merge-k-sorted-lists', '合并K个升序链表', '给你一个链表数组，每个链表都已经按升序排列。请你将所有链表合并到一个升序链表中，返回合并后的链表。',
'{"constraints": ["k == lists.length", "0 <= k <= 10^4", "0 <= lists[i].length <= 500", "-10^4 <= lists[i][j].val <= 10^4", "lists[i] 按升序排列", "lists[i].length 总和不超过 10^4"]}',
'["考虑使用最小堆优化到 O(N log k)", "也可以使用分治法两两合并", "顺序合并时间复杂度为 O(kN)"]', NOW(3))
ON DUPLICATE KEY UPDATE
  `slug` = VALUES(`slug`),
  `summary` = VALUES(`summary`),
  `content` = VALUES(`content`),
  `constraints_json` = VALUES(`constraints_json`),
  `hints` = VALUES(`hints`),
  `updated_at` = NOW(3);

-- ============================================================
-- 3. problem_tags (标签字典, 跨题目共享)
--    slug 唯一, 颜色按主题分类
-- ============================================================
INSERT INTO `problem_tags` (`id`, `label`, `slug`, `color`, `description`, `usage_count`)
VALUES
('tag-array',           '数组',       'array',           '#3b82f6', '数组相关的题目, 涉及遍历、双指针、二分等', 0),
('tag-hash-table',      '哈希表',     'hash-table',      '#8b5cf6', '使用哈希表 / Map / Set 降低时间复杂度',   0),
('tag-linked-list',     '链表',       'linked-list',     '#10b981', '单链表、双链表操作相关',                  0),
('tag-string',          '字符串',     'string',          '#f59e0b', '字符串处理、解析相关',                    0),
('tag-sliding-window',  '滑动窗口',   'sliding-window',  '#ec4899', '固定/可变窗口, 子串/子数组相关',           0),
('tag-two-pointers',    '双指针',     'two-pointers',    '#06b6d4', '左右指针、快慢指针等技巧',                0),
('tag-binary-search',   '二分查找',   'binary-search',   '#f43f5e', '二分查找与变体',                          0),
('tag-divide-conquer',  '分治',       'divide-and-conquer', '#a855f7', '分治思想, 归并 / 快速排序变体等',       0),
('tag-heap',            '堆',         'heap',            '#14b8a6', '优先队列 / 最小堆 / 最大堆',              0),
('tag-math',            '数学',       'math',            '#6366f1', '数学推导、算术运算相关',                  0),
('tag-recursion',       '递归',       'recursion',       '#84cc16', '递归 / 回溯算法相关',                     0)
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `color` = VALUES(`color`),
  `description` = VALUES(`description`);

-- ============================================================
-- 4. problem_tag_relations (题目与标签的多对多关系)
-- ============================================================
INSERT INTO `problem_tag_relations` (`problem_id`, `tag_id`)
VALUES
-- 1. 两数之和 (Easy) — 数组 + 哈希表
(1, 'tag-array'),
(1, 'tag-hash-table'),
-- 2. 两数相加 (Medium) — 链表 + 数学 + 递归
(2, 'tag-linked-list'),
(2, 'tag-math'),
(2, 'tag-recursion'),
-- 3. 无重复字符的最长子串 (Medium) — 字符串 + 滑动窗口 + 哈希表
(3, 'tag-string'),
(3, 'tag-sliding-window'),
(3, 'tag-hash-table'),
-- 4. 寻找两个正序数组的中位数 (Hard) — 数组 + 二分 + 分治
(4, 'tag-array'),
(4, 'tag-binary-search'),
(4, 'tag-divide-conquer'),
-- 6. 反转链表 (Easy) — 链表 + 递归
(6, 'tag-linked-list'),
(6, 'tag-recursion'),
-- 7. 合并K个升序链表 (Hard) — 链表 + 堆 + 分治
(7, 'tag-linked-list'),
(7, 'tag-heap'),
(7, 'tag-divide-conquer')
ON DUPLICATE KEY UPDATE
  -- 关系表无额外字段, 此 UPDATE 让 ON DUPLICATE 静默成功
  `problem_id` = VALUES(`problem_id`);

-- 同步标签使用计数
UPDATE `problem_tags` pt
LEFT JOIN (
  SELECT `tag_id`, COUNT(*) AS cnt
  FROM `problem_tag_relations`
  GROUP BY `tag_id`
) rel ON pt.id = rel.tag_id
SET pt.`usage_count` = COALESCE(rel.cnt, 0),
    pt.`updated_at` = NOW(3);

-- ============================================================
-- 5. problem_examples (题目示例: 输入/输出/解释 + 结构化 inputs)
-- ============================================================
INSERT INTO `problem_examples` (`id`, `problem_id`, `example_order`, `input_text`, `output_text`, `explanation`, `inputs`)
VALUES
-- 1. 两数之和
('pe-001-1', 1, 1, 'nums = [2,7,11,15], target = 9', '[0,1]', '因为 nums[0] + nums[1] == 9，返回下标 [0, 1]。',
 '[{"name":"nums","value":[2,7,11,15]},{"name":"target","value":9}]'),
('pe-001-2', 1, 2, 'nums = [3,2,4], target = 6', '[1,2]', '因为 nums[1] + nums[2] == 6，返回下标 [1, 2]。',
 '[{"name":"nums","value":[3,2,4]},{"name":"target","value":6}]'),

-- 2. 两数相加
('pe-002-1', 2, 1, 'l1 = [2,4,3], l2 = [5,6,4]', '[7,0,8]', '342 + 465 = 807, 链表表示为 [7,0,8]。',
 '[{"name":"l1","value":[2,4,3]},{"name":"l2","value":[5,6,4]}]'),
('pe-002-2', 2, 2, 'l1 = [0], l2 = [0]', '[0]', '0 + 0 = 0。',
 '[{"name":"l1","value":[0]},{"name":"l2","value":[0]}]'),

-- 3. 无重复字符的最长子串
('pe-003-1', 3, 1, 's = "abcabcbb"', '3', '无重复字符的最长子串是 "abc"，长度为 3。',
 '[{"name":"s","value":"abcabcbb"}]'),
('pe-003-2', 3, 2, 's = "bbbbb"', '1', '最长无重复子串是 "b"，长度为 1。',
 '[{"name":"s","value":"bbbbb"}]'),

-- 4. 寻找两个正序数组的中位数
('pe-004-1', 4, 1, 'nums1 = [1,3], nums2 = [2]', '2.00000', '合并数组 = [1,2,3]，中位数 2。',
 '[{"name":"nums1","value":[1,3]},{"name":"nums2","value":[2]}]'),
('pe-004-2', 4, 2, 'nums1 = [1,2], nums2 = [3,4]', '2.50000', '合并数组 = [1,2,3,4]，中位数 (2+3)/2 = 2.5。',
 '[{"name":"nums1","value":[1,2]},{"name":"nums2","value":[3,4]}]'),

-- 6. 反转链表
('pe-006-1', 6, 1, 'head = [1,2,3,4,5]', '[5,4,3,2,1]', '整条链表反序。',
 '[{"name":"head","value":[1,2,3,4,5]}]'),
('pe-006-2', 6, 2, 'head = [1,2]', '[2,1]', '交换两个节点即可。',
 '[{"name":"head","value":[1,2]}]'),

-- 7. 合并K个升序链表
('pe-007-1', 7, 1, 'lists = [[1,4,5],[1,3,4],[2,6]]', '[1,1,2,3,4,4,5,6]', '三链表合并后升序。',
 '[{"name":"lists","value":[[1,4,5],[1,3,4],[2,6]]}]'),
('pe-007-2', 7, 2, 'lists = []', '[]', '空输入返回空链表。',
 '[{"name":"lists","value":[]}]')
ON DUPLICATE KEY UPDATE
  `input_text` = VALUES(`input_text`),
  `output_text` = VALUES(`output_text`),
  `explanation` = VALUES(`explanation`),
  `inputs` = VALUES(`inputs`);

-- ============================================================
-- 6. problem_languages (各语言 starter code)
--    每道题提供 Java / Python / C++ 三种 starter, 链表题额外带 ListNode 定义注释
-- ============================================================

-- 1. 两数之和
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-001-java',    1, 'Java',    'java',    'java',     'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // TODO: 请在此实现, 返回两个下标的数组\n        return new int[0];\n    }\n}\n'),
('pl-001-python',  1, 'Python',  'python',  'python',   'class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        """返回两个下标 i, j, 使得 nums[i] + nums[j] == target"""\n        # TODO: 请在此实现\n        return []\n'),
('pl-001-cpp',     1, 'C++',     'cpp',     'cpp',      'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        // TODO: 请在此实现\n        return {};\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- 2. 两数相加
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-002-java',    2, 'Java',    'java',    'java',     '/**\n * Definition for singly-linked list.\n * public class ListNode {\n *     int val;\n *     ListNode next;\n *     ListNode(int x) { val = x; }\n * }\n */\nclass Solution {\n    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {\n        // TODO: 请在此实现, 注意进位处理\n        return null;\n    }\n}\n'),
('pl-002-python',  2, 'Python',  'python',  'python',   '# Definition for singly-linked list.\n# class ListNode:\n#     def __init__(self, val=0, next=None):\n#         self.val = val\n#         self.next = next\nclass Solution:\n    def addTwoNumbers(self, l1: Optional[ListNode], l2: Optional[ListNode]) -> Optional[ListNode]:\n        """返回表示两数之和的链表头节点"""\n        # TODO: 请在此实现\n        return None\n'),
('pl-002-cpp',     2, 'C++',     'cpp',     'cpp',      '/**\n * Definition for singly-linked list.\n * struct ListNode {\n *     int val;\n *     ListNode *next;\n *     ListNode(int x) : val(x), next(NULL) {}\n * };\n */\nclass Solution {\npublic:\n    ListNode* addTwoNumbers(ListNode* l1, ListNode* l2) {\n        // TODO: 请在此实现\n        return nullptr;\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- 3. 无重复字符的最长子串
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-003-java',    3, 'Java',    'java',    'java',     'class Solution {\n    public int lengthOfLongestSubstring(String s) {\n        // TODO: 滑动窗口, 哈希表记录字符最近出现的位置\n        return 0;\n    }\n}\n'),
('pl-003-python',  3, 'Python',  'python',  'python',   'class Solution:\n    def lengthOfLongestSubstring(self, s: str) -> int:\n        """返回不含重复字符的最长子串长度"""\n        # TODO: 滑动窗口\n        return 0\n'),
('pl-003-cpp',     3, 'C++',     'cpp',     'cpp',      'class Solution {\npublic:\n    int lengthOfLongestSubstring(string s) {\n        // TODO: 滑动窗口\n        return 0;\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- 4. 寻找两个正序数组的中位数
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-004-java',    4, 'Java',    'java',    'java',     'class Solution {\n    public double findMedianSortedArrays(int[] nums1, int[] nums2) {\n        // TODO: 二分查找, 时间复杂度 O(log(m+n))\n        return 0.0;\n    }\n}\n'),
('pl-004-python',  4, 'Python',  'python',  'python',   'class Solution:\n    def findMedianSortedArrays(self, nums1: List[int], nums2: List[int]) -> float:\n        """返回两个正序数组的中位数"""\n        # TODO: 二分查找\n        return 0.0\n'),
('pl-004-cpp',     4, 'C++',     'cpp',     'cpp',      'class Solution {\npublic:\n    double findMedianSortedArrays(vector<int>& nums1, vector<int>& nums2) {\n        // TODO: 二分查找\n        return 0.0;\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- 6. 反转链表
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-006-java',    6, 'Java',    'java',    'java',     '/**\n * Definition for singly-linked list.\n * public class ListNode {\n *     int val;\n *     ListNode next;\n *     ListNode(int x) { val = x; }\n * }\n */\nclass Solution {\n    public ListNode reverseList(ListNode head) {\n        // TODO: 迭代 (prev / curr / next) 或递归\n        return null;\n    }\n}\n'),
('pl-006-python',  6, 'Python',  'python',  'python',   '# Definition for singly-linked list.\n# class ListNode:\n#     def __init__(self, val=0, next=None):\n#         self.val = val\n#         self.next = next\nclass Solution:\n    def reverseList(self, head: Optional[ListNode]) -> Optional[ListNode]:\n        """返回反转后链表的头节点"""\n        # TODO: 迭代或递归\n        return None\n'),
('pl-006-cpp',     6, 'C++',     'cpp',     'cpp',      '/**\n * Definition for singly-linked list.\n * struct ListNode {\n *     int val;\n *     ListNode *next;\n *     ListNode(int x) : val(x), next(NULL) {}\n * };\n */\nclass Solution {\npublic:\n    ListNode* reverseList(ListNode* head) {\n        // TODO: 迭代或递归\n        return nullptr;\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- 7. 合并K个升序链表
INSERT INTO `problem_languages` (`id`, `problem_id`, `label`, `value`, `style`, `starter_code`)
VALUES
('pl-007-java',    7, 'Java',    'java',    'java',     '/**\n * Definition for singly-linked list.\n * public class ListNode {\n *     int val;\n *     ListNode next;\n *     ListNode(int x) { val = x; }\n * }\n */\nclass Solution {\n    public ListNode mergeKLists(ListNode[] lists) {\n        // TODO: 优先队列(最小堆) 或 分治法\n        return null;\n    }\n}\n'),
('pl-007-python',  7, 'Python',  'python',  'python',   '# Definition for singly-linked list.\n# class ListNode:\n#     def __init__(self, val=0, next=None):\n#         self.val = val\n#         self.next = next\nimport heapq\n\nclass Solution:\n    def mergeKLists(self, lists: List[Optional[ListNode]]) -> Optional[ListNode]:\n        """将 k 个升序链表合并为一个升序链表"""\n        # TODO: 优先队列\n        return None\n'),
('pl-007-cpp',     7, 'C++',     'cpp',     'cpp',      '/**\n * Definition for singly-linked list.\n * struct ListNode {\n *     int val;\n *     ListNode *next;\n *     ListNode(int x) : val(x), next(NULL) {}\n * };\n */\nclass Solution {\npublic:\n    ListNode* mergeKLists(vector<ListNode*>& lists) {\n        // TODO: 优先队列或分治\n        return nullptr;\n    }\n};\n')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `style` = VALUES(`style`),
  `starter_code` = VALUES(`starter_code`);

-- ============================================================
-- 7. problem_lists (按 tag 一对一建题单, 共 11 个)
--    与 problem_tags.id 一一对应, 命名 list-tag-<slug>
--    is_featured=0 (基础题单, 不进 banner)
-- ============================================================
INSERT INTO `problem_lists` (`id`, `name`, `description`, `author_id`, `is_public`, `is_featured`, `banner_tag`, `banner_icon`, `banner_theme`, `banner_order`, `created_at`, `updated_at`, `version`)
VALUES
('list-tag-array',          '数组 专题',           '数组遍历、双指针、二分等数组类题目合集',                 'user-sara',  1, 0, '数组',   'Braces',       'sky',     10, NOW(3), NOW(3), 1),
('list-tag-hash-table',     '哈希表 专题',         '哈希表 / Map / Set 优化时间复杂度的经典题目',           'user-sara',  1, 0, '哈希',   'Hash',         'violet',  11, NOW(3), NOW(3), 1),
('list-tag-linked-list',    '链表 专题',           '单链表、双链表操作相关题目',                            'user-david', 1, 0, '链表',   'Link2',        'emerald', 12, NOW(3), NOW(3), 1),
('list-tag-string',         '字符串 专题',         '字符串处理、解析与匹配',                                'user-david', 1, 0, '字符串', 'Type',         'amber',   13, NOW(3), NOW(3), 1),
('list-tag-sliding-window', '滑动窗口 专题',       '固定/可变窗口的子串与子数组题',                          'user-chen',  1, 0, '窗口',   'Square',       'pink',    14, NOW(3), NOW(3), 1),
('list-tag-two-pointers',   '双指针 专题',         '左右指针、快慢指针等技巧合集',                          'user-chen',  1, 0, '双指针', 'ArrowLeftRight','cyan',  15, NOW(3), NOW(3), 1),
('list-tag-binary-search',  '二分查找 专题',       '二分查找及其变体',                                       'user-alex',  1, 0, '二分',   'ChevronsUpDown','rose',  16, NOW(3), NOW(3), 1),
('list-tag-divide-and-conquer', '分治 专题',        '分治思想, 归并 / 快速排序变体 (slug 拼接兼容)',          'user-petr',  1, 0, '分治',   'Split',        'purple',  17, NOW(3), NOW(3), 1),
('list-tag-heap',           '堆 专题',             '优先队列 / 最小堆 / 最大堆 相关题目',                    'user-petr',  1, 0, '堆',     'Layers',       'teal',    18, NOW(3), NOW(3), 1),
('list-tag-math',           '数学 专题',           '数学推导、算术运算相关题目',                              'user-alex',  1, 0, '数学',   'Sigma',        'indigo',  19, NOW(3), NOW(3), 1),
('list-tag-recursion',      '递归 专题',           '递归 / 回溯算法相关题目',                                'user-chen',  1, 0, '递归',   'Repeat',       'lime',    20, NOW(3), NOW(3), 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `is_public` = VALUES(`is_public`),
  `updated_at` = NOW(3);

-- ============================================================
-- 8. problem_list_problem_relations (tag 题单 ↔ 题目 多对多)
--    来源: 复用上面的 problem_tag_relations 数据,
--    通过 JOIN problems / problem_tags 自动生成 (list_tag + problem_id) 笛卡尔积
--    用 INSERT IGNORE 避免与 V120200 既有 relations 冲突
-- ============================================================
INSERT IGNORE INTO `problem_list_problem_relations` (`list_id`, `problem_id`, `sort_order`, `added_at`)
SELECT
  CONCAT('list-tag-', pt.slug) AS list_id,
  ptr.problem_id,
  ROW_NUMBER() OVER (PARTITION BY ptr.problem_id ORDER BY ptr.tag_id) AS sort_order,
  NOW(3) AS added_at
FROM `problem_tag_relations` ptr
JOIN `problem_tags` pt ON pt.id = ptr.tag_id;

-- ============================================================
-- Verify:
--   SELECT difficulty, COUNT(*) FROM problems GROUP BY difficulty;
--   期望: Easy=2, Medium=2, Hard=2
--   SELECT problem_id, COUNT(*) FROM problem_examples GROUP BY problem_id;
--   期望: 每题 2 个示例
--   SELECT problem_id, COUNT(*) FROM problem_languages GROUP BY problem_id;
--   期望: 每题 3 种语言 (Java/Python/C++)
--   SELECT id, label, usage_count FROM problem_tags ORDER BY id;
--   期望: usage_count 与 relations 实际计数一致
--   SELECT id, name, (SELECT COUNT(*) FROM problem_list_problem_relations
--                      WHERE list_id = pl.id) AS problems
--   FROM problem_lists pl WHERE id LIKE 'list-tag-%' ORDER BY id;
--   期望: 11 个 list-tag-*, 大部分含 1+ 题, list-tag-two-pointers 关联 0 题
--   注: divide-and-conquer 走 slug 拼接 (list-tag-divide-and-conquer),
--       与 list-tag-divide-conquer 不可并存, 保留与 slug 一致的命名
-- ============================================================
